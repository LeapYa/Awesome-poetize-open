package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.dao.ResourceAliasMapper;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.dao.ResourceRedirectMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceAlias;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.entity.ResourceRedirect;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResourceDeletionStateService {

    private final ResourceMapper resourceMapper;
    private final ResourceLocationMapper resourceLocationMapper;
    private final ResourceAliasMapper resourceAliasMapper;
    private final ResourceRedirectMapper resourceRedirectMapper;
    private final ResourceReferenceService referenceService;

    public Resource requireResource(Integer resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("资源ID不能为空");
        }
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        return resource;
    }

    public List<ResourceLocation> listLocations(Integer resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("资源ID不能为空");
        }
        return resourceLocationMapper.findByResourceId(resourceId);
    }

    public List<String> listActiveAliases(Integer resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("资源ID不能为空");
        }
        return resourceAliasMapper.selectList(
                        Wrappers.<ResourceAlias>lambdaQuery()
                                .eq(ResourceAlias::getResourceId, resourceId)
                                .eq(ResourceAlias::getStatus, true)
                ).stream()
                .map(ResourceAlias::getAliasUrl)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public LogicalDeletionClaim claim(LogicalDeletionPlan plan) {
        validatePlan(plan);
        Resource resource = resourceMapper.selectByIdForUpdate(plan.resourceId());
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + plan.resourceId());
        }
        if (!plan.expectedPath().equals(resource.getPath())) {
            throw new ConcurrentModificationException("资源路径已变化");
        }

        boolean pending = ResourceContentState.DELETION_PENDING.name().equals(resource.getContentState());
        if (pending) {
            if (Boolean.TRUE.equals(resource.getStatus())) {
                throw new IllegalStateException("逻辑资源删除状态不一致");
            }
        } else {
            if (!Boolean.TRUE.equals(resource.getStatus())
                    || !ResourceContentState.isActive(resource.getContentState())) {
                throw new IllegalStateException("资源当前状态不允许删除");
            }
        }

        // 即使资源已处于 DELETION_PENDING（重试场景），非 force 也必须复验引用，
        // 防止删除声明后新增业务引用被静默放行；force 删除每次都需要明确确认。
        if (!plan.forceReferenced()) {
            int referenceCount = countReferences(resource);
            if (referenceCount > 0) {
                throw new ConcurrentModificationException(
                        "删除声明时检测到稳定地址或历史别名仍有 " + referenceCount + " 处引用"
                );
            }
        }

        List<ResourceLocation> locations = resourceLocationMapper.findByResourceIdForUpdate(resource.getId());
        if (locations.isEmpty()) {
            throw new IllegalStateException("逻辑资源尚未登记任何物理副本");
        }
        Map<Long, LocationDisposition> dispositions = indexDispositions(plan.dispositions());
        validateCoverage(locations, dispositions);

        if (!pending) {
            int version = resource.getLocationVersion() == null ? 0 : resource.getLocationVersion();
            var update = Wrappers.<Resource>lambdaUpdate()
                    .eq(Resource::getId, resource.getId())
                    .eq(Resource::getPath, plan.expectedPath())
                    .eq(Resource::getStatus, true)
                    .eq(Resource::getLocationVersion, version);
            if (StringUtils.hasText(resource.getContentState())) {
                update.eq(Resource::getContentState, resource.getContentState());
            } else {
                update.and(wrapper -> wrapper.isNull(Resource::getContentState)
                        .or()
                        .eq(Resource::getContentState, ""));
            }
            int updated = resourceMapper.update(
                    null,
                    update
                            .set(Resource::getStatus, false)
                            .set(Resource::getContentState, ResourceContentState.DELETION_PENDING.name())
                            .set(Resource::getLocationVersion, version + 1)
            );
            if (updated != 1) {
                throw new ConcurrentModificationException("逻辑资源删除声明期间状态已变化");
            }
            resource.setStatus(false);
            resource.setContentState(ResourceContentState.DELETION_PENDING.name());
            resource.setLocationVersion(version + 1);
        }

        List<ResourceLocationService.LocationDeletionClaim> claimed = new ArrayList<>();
        List<Long> inProgress = new ArrayList<>();
        for (ResourceLocation location : locations) {
            ResourceLocationStatus status = requireStatus(location);
            if (isTerminal(status)) {
                continue;
            }
            LocationDisposition disposition = dispositions.get(location.getId());
            if (status == ResourceLocationStatus.DELETING) {
                if (disposition == LocationDisposition.MARK_MISSING) {
                    transitionLocation(location, ResourceLocationStatus.MISSING, null);
                } else if (disposition == LocationDisposition.DETACH) {
                    transitionLocation(location, ResourceLocationStatus.DETACHED, null);
                } else {
                    inProgress.add(location.getId());
                }
                continue;
            }

            if (disposition == LocationDisposition.MARK_MISSING) {
                transitionLocation(location, ResourceLocationStatus.MISSING, null);
                continue;
            }
            if (disposition == LocationDisposition.DETACH) {
                transitionLocation(location, ResourceLocationStatus.DETACHED, null);
                continue;
            }

            LocalDateTime claimedAt = nextClaimTime(location.getUpdateTime());
            transitionLocation(location, ResourceLocationStatus.DELETING, claimedAt);
            claimed.add(new ResourceLocationService.LocationDeletionClaim(
                    resource,
                    location,
                    true,
                    status.name(),
                    claimedAt
            ));
        }
        return new LogicalDeletionClaim(resource, claimed, inProgress);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean finalizeDeletion(Integer resourceId, String expectedPath, boolean forceReferenced) {
        if (resourceId == null || !StringUtils.hasText(expectedPath)) {
            throw new IllegalArgumentException("资源ID和期望路径不能为空");
        }
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            return true;
        }
        if (!expectedPath.equals(resource.getPath())) {
            throw new ConcurrentModificationException("资源路径已变化");
        }
        if (Boolean.TRUE.equals(resource.getStatus())
                || !ResourceContentState.DELETION_PENDING.name().equals(resource.getContentState())) {
            throw new IllegalStateException("逻辑资源尚未进入删除状态");
        }

        List<ResourceLocation> locations = resourceLocationMapper.findByResourceIdForUpdate(resourceId);
        boolean allTerminal = !locations.isEmpty() && locations.stream()
                .map(this::requireStatus)
                .allMatch(this::isTerminal);
        if (!allTerminal) {
            return false;
        }

        // 最终删除资源行前的最后一道防线：副本清理期间若新增了业务引用（说明业务层未遵守
        // DELETION_PENDING 阻断约束），非 force 必须拒绝删除资源行，保留 DELETION_PENDING
        // 空壳让管理员发现并处理；副本事务已提交无法回滚，但至少避免连资源行一起消失。
        if (!forceReferenced) {
            int referenceCount = countReferences(resource);
            if (referenceCount > 0) {
                throw new ConcurrentModificationException(
                        "最终删除前检测到稳定地址或历史别名仍有 " + referenceCount + " 处引用"
                );
            }
        }

        resourceRedirectMapper.delete(
                Wrappers.<ResourceRedirect>lambdaQuery()
                        .eq(ResourceRedirect::getResourceId, resourceId)
        );
        resourceAliasMapper.delete(
                Wrappers.<ResourceAlias>lambdaQuery()
                        .eq(ResourceAlias::getResourceId, resourceId)
        );
        resourceLocationMapper.delete(
                Wrappers.<ResourceLocation>lambdaQuery()
                        .eq(ResourceLocation::getResourceId, resourceId)
        );

        int deleted = resourceMapper.delete(
                Wrappers.<Resource>lambdaQuery()
                        .eq(Resource::getId, resourceId)
                        .eq(Resource::getPath, expectedPath)
                        .eq(Resource::getStatus, false)
                        .eq(Resource::getContentState, ResourceContentState.DELETION_PENDING.name())
        );
        if (deleted != 1) {
            throw new ConcurrentModificationException("逻辑资源删除收尾期间状态已变化");
        }
        return true;
    }

    private int countReferences(Resource resource) {
        Set<String> identities = new HashSet<>();
        identities.add(resource.getPath());
        resourceAliasMapper.selectList(
                        Wrappers.<ResourceAlias>lambdaQuery()
                                .eq(ResourceAlias::getResourceId, resource.getId())
                                .eq(ResourceAlias::getStatus, true)
                ).stream()
                .map(ResourceAlias::getAliasUrl)
                .filter(StringUtils::hasText)
                .forEach(identities::add);
        return identities.stream()
                .filter(StringUtils::hasText)
                .mapToInt(referenceService::countReferences)
                .sum();
    }

    private void validatePlan(LogicalDeletionPlan plan) {
        if (plan == null
                || plan.resourceId() == null
                || !StringUtils.hasText(plan.expectedPath())
                || plan.dispositions() == null) {
            throw new IllegalArgumentException("逻辑资源删除计划不完整");
        }
    }

    private Map<Long, LocationDisposition> indexDispositions(
            List<LocationDeletionDisposition> dispositions) {
        Map<Long, LocationDisposition> indexed = new HashMap<>();
        for (LocationDeletionDisposition disposition : dispositions) {
            if (disposition == null
                    || disposition.locationId() == null
                    || disposition.disposition() == null) {
                throw new IllegalArgumentException("物理副本删除决策不完整");
            }
            if (indexed.putIfAbsent(disposition.locationId(), disposition.disposition()) != null) {
                throw new IllegalArgumentException("物理副本删除决策重复");
            }
        }
        return indexed;
    }

    private void validateCoverage(List<ResourceLocation> locations,
                                  Map<Long, LocationDisposition> dispositions) {
        Set<Long> actualIds = new HashSet<>();
        for (ResourceLocation location : locations) {
            actualIds.add(location.getId());
            ResourceLocationStatus status = requireStatus(location);
            if (!isTerminal(status)
                    && status != ResourceLocationStatus.DELETING
                    && !dispositions.containsKey(location.getId())) {
                throw new ConcurrentModificationException("物理副本集合在删除声明期间发生变化");
            }
        }
        if (!actualIds.containsAll(dispositions.keySet())) {
            throw new ConcurrentModificationException("删除计划包含不属于当前资源的物理副本");
        }
    }

    private void transitionLocation(ResourceLocation location,
                                    ResourceLocationStatus target,
                                    LocalDateTime claimedAt) {
        var update = Wrappers.<ResourceLocation>lambdaUpdate()
                .eq(ResourceLocation::getId, location.getId())
                .eq(ResourceLocation::getResourceId, location.getResourceId())
                .eq(ResourceLocation::getStatus, location.getStatus())
                .set(ResourceLocation::getStatus, target.name());
        if (claimedAt != null) {
            update.set(ResourceLocation::getUpdateTime, claimedAt);
        }
        if (resourceLocationMapper.update(null, update) != 1) {
            throw new ConcurrentModificationException("物理副本删除声明期间状态已变化");
        }
        location.setStatus(target.name());
        if (claimedAt != null) {
            location.setUpdateTime(claimedAt);
        }
    }

    private ResourceLocationStatus requireStatus(ResourceLocation location) {
        try {
            return ResourceLocationStatus.valueOf(location.getStatus());
        } catch (RuntimeException e) {
            throw new IllegalStateException("物理副本状态不合法：" + location.getStatus(), e);
        }
    }

    private boolean isTerminal(ResourceLocationStatus status) {
        return status == ResourceLocationStatus.DELETED
                || status == ResourceLocationStatus.MISSING
                || status == ResourceLocationStatus.DETACHED;
    }

    private LocalDateTime nextClaimTime(LocalDateTime previous) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return previous == null || now.isAfter(previous) ? now : previous.plusSeconds(1);
    }

    public enum LocationDisposition {
        DELETE,
        MARK_MISSING,
        DETACH
    }

    public record LocationDeletionDisposition(
            Long locationId,
            LocationDisposition disposition
    ) {
    }

    public record LogicalDeletionPlan(
            Integer resourceId,
            String expectedPath,
            boolean forceReferenced,
            List<LocationDeletionDisposition> dispositions
    ) {
    }

    public record LogicalDeletionClaim(
            Resource resource,
            List<ResourceLocationService.LocationDeletionClaim> claimedLocations,
            List<Long> inProgressLocationIds
    ) {
    }
}