package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.dao.ResourceContentReplacementMapper;
import com.ld.poetry.dao.ResourceContentReplacementTargetMapper;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceContentReplacement;
import com.ld.poetry.entity.ResourceContentReplacementTarget;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.enums.ResourceReplacementResolution;
import com.ld.poetry.enums.ResourceReplacementStatus;
import com.ld.poetry.enums.ResourceReplacementTargetStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceContentReplacementService {

    private final ResourceMapper resourceMapper;
    private final ResourceLocationMapper resourceLocationMapper;
    private final ResourceContentReplacementMapper replacementMapper;
    private final ResourceContentReplacementTargetMapper targetMapper;

    @Transactional(rollbackFor = Exception.class)
    public ReplacementClaim begin(Resource expectedResource,
                                  Resource replacement,
                                  String sourceHash,
                                  List<TargetPlan> targetPlans) {
        validateBeginArguments(expectedResource, replacement, targetPlans);
        String normalizedSourceHash = requireHash(sourceHash, "替换前文件SHA-256不合法");
        String normalizedNewHash = requireHash(replacement.getResourceHash(), "替换后文件SHA-256不合法");
        if (normalizedSourceHash.equals(normalizedNewHash)) {
            throw new IllegalArgumentException("替换文件内容与当前文件完全相同");
        }
        for (TargetPlan plan : targetPlans) {
            if (!normalizedSourceHash.equals(requireHash(plan.sourceHash(), "替换目标旧文件SHA-256不合法"))
                    || !normalizedNewHash.equals(requireHash(plan.newHash(), "替换目标新文件SHA-256不合法"))) {
                throw new IllegalArgumentException("替换目标哈希与替换事务不一致");
            }
        }

        Resource current = resourceMapper.selectByIdForUpdate(expectedResource.getId());
        if (current == null) {
            throw new ConcurrentModificationException("逻辑资源在替换声明前被删除");
        }
        assertExpectedResource(current, expectedResource);
        requireActiveContent(current, "资源存在未完成的内容替换，请先恢复或核验");

        ResourceLocation activeLocation = lockAndValidateActiveLocation(current, normalizedSourceHash);
        validateKnownHash(current.getResourceHash(), normalizedSourceHash, "资源内容哈希与当前文件不一致");

        int originalVersion = normalizeVersion(current.getLocationVersion());
        int claimedVersion = originalVersion + 1;
        String operationId = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);

        ResourceContentReplacement operation = new ResourceContentReplacement();
        operation.setOperationId(operationId);
        operation.setResourceId(current.getId());
        operation.setActiveLocationId(current.getActiveLocationId());
        operation.setExpectedPath(current.getPath());
        operation.setOriginalLocationVersion(originalVersion);
        operation.setClaimedLocationVersion(claimedVersion);
        operation.setOriginalResourceHash(current.getResourceHash());
        operation.setSourceHash(normalizedSourceHash);
        operation.setOriginalHashSource(current.getHashSource());
        operation.setOriginalHashVerifiedAt(current.getHashVerifiedAt());
        if (activeLocation != null) {
            operation.setSourceLocationStoreType(activeLocation.getStoreType());
            operation.setSourceLocationStorageKey(activeLocation.getStorageKey());
            operation.setSourceLocationAccessPath(activeLocation.getAccessPath());
            operation.setSourceLocationHash(activeLocation.getContentHash());
            operation.setSourceLocationStatus(activeLocation.getStatus());
        }
        operation.setNewHash(normalizedNewHash);
        operation.setNewSize(replacement.getSize());
        operation.setNewOriginalName(replacement.getOriginalName());
        operation.setNewMimeType(replacement.getMimeType());
        operation.setNewWidth(replacement.getWidth());
        operation.setNewHeight(replacement.getHeight());
        operation.setStatus(ResourceReplacementStatus.PENDING.name());
        replacementMapper.insert(operation);

        List<ResourceContentReplacementTarget> targets = new ArrayList<>(targetPlans.size());
        for (TargetPlan plan : targetPlans) {
            ResourceContentReplacementTarget target = new ResourceContentReplacementTarget();
            target.setReplacementId(operation.getId());
            target.setTargetPath(plan.targetPath());
            target.setTempPath(plan.tempPath());
            target.setBackupPath(plan.backupPath());
            target.setSourceHash(normalizedSourceHash);
            target.setNewHash(normalizedNewHash);
            target.setStatus(ResourceReplacementTargetStatus.PLANNED.name());
            targetMapper.insert(target);
            targets.add(target);
        }

        var update = Wrappers.<Resource>lambdaUpdate()
                .eq(Resource::getId, current.getId())
                .eq(Resource::getPath, current.getPath())
                .eq(Resource::getLocationVersion, originalVersion);
        appendNullableResourceEquality(update, Resource::getActiveLocationId, current.getActiveLocationId());
        appendNullableResourceEquality(update, Resource::getResourceHash, current.getResourceHash());
        appendActiveContentState(update, current.getContentState());
        int updated = resourceMapper.update(
                null,
                update.set(Resource::getContentState, ResourceContentState.REPLACEMENT_PENDING.name())
                        .set(Resource::getHashVerifiedAt, null)
                        .set(Resource::getLocationVersion, claimedVersion)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("资源替换声明期间发生并发修改");
        }
        return new ReplacementClaim(operation, List.copyOf(targets));
    }

    @Transactional(rollbackFor = Exception.class)
    public Resource commit(String operationId, List<TargetEvidence> evidence) {
        return resolve(operationId, ResourceReplacementResolution.COMMIT_NEW, evidence, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Resource abort(String operationId, List<TargetEvidence> evidence) {
        return resolve(operationId, ResourceReplacementResolution.RESTORE_OLD, evidence, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public RecoveryResult recover(String operationId, List<TargetEvidence> evidence) {
        ReplacementClaim claim = requireClaim(operationId);
        ResourceReplacementResolution resolution = classifyRecovery(claim, evidence);
        if (resolution == ResourceReplacementResolution.COMMIT_NEW) {
            return new RecoveryResult(resolution, commit(operationId, evidence));
        }
        if (resolution == ResourceReplacementResolution.RESTORE_OLD) {
            return new RecoveryResult(resolution, abort(operationId, evidence));
        }
        markRecoveryRequired(
                operationId,
                evidence,
                "替换目标内容不一致，资源继续保持阻塞并等待人工核验"
        );
        return new RecoveryResult(ResourceReplacementResolution.KEEP_BLOCKED, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRecoveryRequired(String operationId,
                                     List<TargetEvidence> evidence,
                                     String message) {
        ResourceContentReplacement operation = requireOpenOperation(operationId);
        Resource resource = lockClaimedResource(operation);
        validateClaimedLocation(operation, resource);
        updateTargetEvidence(operation, evidence, null, false);
        int updated = replacementMapper.update(
                null,
                Wrappers.<ResourceContentReplacement>lambdaUpdate()
                        .eq(ResourceContentReplacement::getId, operation.getId())
                        .in(ResourceContentReplacement::getStatus,
                                ResourceReplacementStatus.PENDING.name(),
                                ResourceReplacementStatus.RECOVERY_REQUIRED.name())
                        .set(ResourceContentReplacement::getStatus,
                                ResourceReplacementStatus.RECOVERY_REQUIRED.name())
                        .set(ResourceContentReplacement::getErrorMessage, truncate(message))
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("替换恢复状态已变化");
        }
    }

    public ResourceContentReplacement find(String operationId) {
        if (!StringUtils.hasText(operationId)) {
            return null;
        }
        return replacementMapper.selectOne(
                Wrappers.<ResourceContentReplacement>lambdaQuery()
                        .eq(ResourceContentReplacement::getOperationId, operationId)
                        .last("limit 1")
        );
    }

    public List<ReplacementClaim> listOpenClaims() {
        List<ResourceContentReplacement> operations = replacementMapper.selectList(
                Wrappers.<ResourceContentReplacement>lambdaQuery()
                        .in(ResourceContentReplacement::getStatus,
                                ResourceReplacementStatus.PENDING.name(),
                                ResourceReplacementStatus.RECOVERY_REQUIRED.name())
                        .orderByAsc(ResourceContentReplacement::getId)
        );
        return toClaims(operations);
    }

    public List<ReplacementClaim> listTerminalClaimsWithArtifacts() {
        List<ResourceContentReplacement> operations = replacementMapper.selectList(
                Wrappers.<ResourceContentReplacement>lambdaQuery()
                        .in(ResourceContentReplacement::getStatus,
                                ResourceReplacementStatus.COMMITTED.name(),
                                ResourceReplacementStatus.ABORTED.name())
                        .isNull(ResourceContentReplacement::getArtifactsCleanedAt)
                        .orderByAsc(ResourceContentReplacement::getId)
        );
        return toClaims(operations);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markArtifactsCleaned(String operationId) {
        if (!StringUtils.hasText(operationId)) {
            throw new IllegalArgumentException("内容替换事务ID不能为空");
        }
        ResourceContentReplacement operation = replacementMapper.selectByOperationIdForUpdate(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("内容替换事务不存在");
        }
        ResourceReplacementStatus status = ResourceReplacementStatus.valueOf(operation.getStatus());
        if (status.isOpen()) {
            throw new IllegalStateException("内容替换事务尚未决议，不能清理恢复材料");
        }
        if (operation.getArtifactsCleanedAt() != null) {
            return;
        }
        int updated = replacementMapper.update(
                null,
                Wrappers.<ResourceContentReplacement>lambdaUpdate()
                        .eq(ResourceContentReplacement::getId, operation.getId())
                        .in(ResourceContentReplacement::getStatus,
                                ResourceReplacementStatus.COMMITTED.name(),
                                ResourceReplacementStatus.ABORTED.name())
                        .isNull(ResourceContentReplacement::getArtifactsCleanedAt)
                        .set(ResourceContentReplacement::getArtifactsCleanedAt, LocalDateTime.now())
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("替换恢复材料清理状态已变化");
        }
    }

    public ReplacementClaim requireClaim(String operationId) {
        ResourceContentReplacement operation = find(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("内容替换事务不存在");
        }
        return new ReplacementClaim(operation, listTargets(operation.getId()));
    }

    private Resource resolve(String operationId,
                             ResourceReplacementResolution resolution,
                             List<TargetEvidence> evidence,
                             String message) {
        ResourceContentReplacement operation = requireOpenOperation(operationId);
        Resource resource = lockClaimedResource(operation);
        ResourceLocation activeLocation = validateClaimedLocation(operation, resource);
        String expectedHash = resolution == ResourceReplacementResolution.COMMIT_NEW
                ? operation.getNewHash()
                : operation.getSourceHash();
        ResourceReplacementTargetStatus targetStatus = resolution == ResourceReplacementResolution.COMMIT_NEW
                ? ResourceReplacementTargetStatus.NEW_VERIFIED
                : ResourceReplacementTargetStatus.OLD_VERIFIED;
        updateTargetEvidence(operation, evidence, expectedHash, true);

        var resourceUpdate = Wrappers.<Resource>lambdaUpdate()
                .eq(Resource::getId, operation.getResourceId())
                .eq(Resource::getPath, operation.getExpectedPath())
                .eq(Resource::getLocationVersion, operation.getClaimedLocationVersion())
                .eq(Resource::getContentState, ResourceContentState.REPLACEMENT_PENDING.name());
        appendNullableResourceEquality(
                resourceUpdate,
                Resource::getActiveLocationId,
                operation.getActiveLocationId()
        );
        appendNullableResourceEquality(
                resourceUpdate,
                Resource::getResourceHash,
                operation.getOriginalResourceHash()
        );

        ResourceReplacementStatus finalStatus;
        if (resolution == ResourceReplacementResolution.COMMIT_NEW) {
            LocalDateTime verifiedAt = LocalDateTime.now();
            resourceUpdate.set(Resource::getSize, operation.getNewSize())
                    .set(Resource::getOriginalName, operation.getNewOriginalName())
                    .set(Resource::getMimeType, operation.getNewMimeType())
                    .set(Resource::getResourceHash, operation.getNewHash())
                    .set(Resource::getHashSource, "REPLACEMENT_WRITE")
                    .set(Resource::getHashVerifiedAt, verifiedAt)
                    .set(Resource::getWidth, operation.getNewWidth())
                    .set(Resource::getHeight, operation.getNewHeight());
            updateActiveLocationForCommit(operation, activeLocation, verifiedAt);
            finalStatus = ResourceReplacementStatus.COMMITTED;
        } else {
            resourceUpdate.set(Resource::getResourceHash, operation.getOriginalResourceHash())
                    .set(Resource::getHashSource, operation.getOriginalHashSource())
                    .set(Resource::getHashVerifiedAt, operation.getOriginalHashVerifiedAt());
            finalStatus = ResourceReplacementStatus.ABORTED;
        }

        int resourceUpdated = resourceMapper.update(
                null,
                resourceUpdate.set(Resource::getContentState, ResourceContentState.ACTIVE.name())
                        .set(Resource::getLocationVersion, operation.getClaimedLocationVersion() + 1)
        );
        if (resourceUpdated != 1) {
            throw new ConcurrentModificationException("资源替换决议期间发生并发修改");
        }

        int operationUpdated = replacementMapper.update(
                null,
                Wrappers.<ResourceContentReplacement>lambdaUpdate()
                        .eq(ResourceContentReplacement::getId, operation.getId())
                        .in(ResourceContentReplacement::getStatus,
                                ResourceReplacementStatus.PENDING.name(),
                                ResourceReplacementStatus.RECOVERY_REQUIRED.name())
                        .set(ResourceContentReplacement::getStatus, finalStatus.name())
                        .set(ResourceContentReplacement::getErrorMessage, truncateNullable(message))
                        .set(ResourceContentReplacement::getFinishedAt, LocalDateTime.now())
        );
        if (operationUpdated != 1) {
            throw new ConcurrentModificationException("替换事务决议状态已变化");
        }

        targetMapper.update(
                null,
                Wrappers.<ResourceContentReplacementTarget>lambdaUpdate()
                        .eq(ResourceContentReplacementTarget::getReplacementId, operation.getId())
                        .set(ResourceContentReplacementTarget::getStatus, targetStatus.name())
                        .set(ResourceContentReplacementTarget::getObservedHash, expectedHash)
        );
        Resource result = resourceMapper.selectById(operation.getResourceId());
        if (result == null) {
            throw new ConcurrentModificationException("替换决议后资源不存在");
        }
        return result;
    }

    private ResourceContentReplacement requireOpenOperation(String operationId) {
        if (!StringUtils.hasText(operationId)) {
            throw new IllegalArgumentException("内容替换事务ID不能为空");
        }
        ResourceContentReplacement operation = replacementMapper.selectByOperationIdForUpdate(operationId);
        if (operation == null) {
            throw new IllegalArgumentException("内容替换事务不存在");
        }
        ResourceReplacementStatus status = ResourceReplacementStatus.valueOf(operation.getStatus());
        if (!status.isOpen()) {
            throw new ConcurrentModificationException("内容替换事务已经完成");
        }
        return operation;
    }

    private Resource lockClaimedResource(ResourceContentReplacement operation) {
        Resource resource = resourceMapper.selectByIdForUpdate(operation.getResourceId());
        if (resource == null) {
            throw new ConcurrentModificationException("逻辑资源在替换期间被删除");
        }
        if (!Objects.equals(resource.getPath(), operation.getExpectedPath())
                || !Objects.equals(resource.getActiveLocationId(), operation.getActiveLocationId())
                || normalizeVersion(resource.getLocationVersion())
                != normalizeVersion(operation.getClaimedLocationVersion())
                || !ResourceContentState.REPLACEMENT_PENDING.name().equals(resource.getContentState())
                || !Objects.equals(
                normalizeHash(resource.getResourceHash()),
                normalizeHash(operation.getOriginalResourceHash())
        )) {
            throw new ConcurrentModificationException("资源替换声明已变化");
        }
        return resource;
    }

    private ResourceLocation validateClaimedLocation(ResourceContentReplacement operation,
                                                     Resource resource) {
        if (operation.getActiveLocationId() == null) {
            return null;
        }
        ResourceLocation location = resourceLocationMapper.selectByIdForUpdate(operation.getActiveLocationId());
        if (location == null
                || !resource.getId().equals(location.getResourceId())
                || !Objects.equals(location.getStoreType(), operation.getSourceLocationStoreType())
                || !Objects.equals(location.getStorageKey(), operation.getSourceLocationStorageKey())
                || !Objects.equals(location.getAccessPath(), operation.getSourceLocationAccessPath())
                || !Objects.equals(normalizeHash(location.getContentHash()),
                normalizeHash(operation.getSourceLocationHash()))
                || !Objects.equals(location.getStatus(), operation.getSourceLocationStatus())) {
            throw new ConcurrentModificationException("活动物理副本在内容替换期间发生变化");
        }
        return location;
    }

    private ResourceLocation lockAndValidateActiveLocation(Resource resource, String sourceHash) {
        if (resource.getActiveLocationId() == null) {
            return null;
        }
        ResourceLocation location = resourceLocationMapper.selectByIdForUpdate(resource.getActiveLocationId());
        if (location == null
                || !resource.getId().equals(location.getResourceId())
                || !ResourceLocationStatus.ACTIVE.name().equals(location.getStatus())) {
            throw new ConcurrentModificationException("资源活动物理副本状态不一致");
        }
        validateKnownHash(location.getContentHash(), sourceHash, "活动物理副本哈希与当前文件不一致");
        return location;
    }

    private void updateActiveLocationForCommit(ResourceContentReplacement operation,
                                               ResourceLocation activeLocation,
                                               LocalDateTime verifiedAt) {
        if (activeLocation == null) {
            return;
        }
        var update = Wrappers.<ResourceLocation>lambdaUpdate()
                .eq(ResourceLocation::getId, activeLocation.getId())
                .eq(ResourceLocation::getResourceId, operation.getResourceId())
                .eq(ResourceLocation::getStoreType, operation.getSourceLocationStoreType())
                .eq(ResourceLocation::getAccessPath, operation.getSourceLocationAccessPath())
                .eq(ResourceLocation::getStatus, operation.getSourceLocationStatus());
        appendNullableLocationEquality(
                update,
                ResourceLocation::getStorageKey,
                operation.getSourceLocationStorageKey()
        );
        appendNullableLocationEquality(
                update,
                ResourceLocation::getContentHash,
                operation.getSourceLocationHash()
        );
        int updated = resourceLocationMapper.update(
                null,
                update.set(ResourceLocation::getContentHash, operation.getNewHash())
                        .set(ResourceLocation::getSize, operation.getNewSize().longValue())
                        .set(ResourceLocation::getMimeType, operation.getNewMimeType())
                        .set(ResourceLocation::getVerifiedAt, verifiedAt)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("活动物理副本在替换提交期间发生变化");
        }
    }

    private ResourceReplacementResolution classifyRecovery(ReplacementClaim claim,
                                                            List<TargetEvidence> evidence) {
        Map<Long, String> observedById = evidenceByTarget(evidence);
        var expectedIds = claim.targets().stream()
                .map(ResourceContentReplacementTarget::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!expectedIds.equals(observedById.keySet())) {
            return ResourceReplacementResolution.KEEP_BLOCKED;
        }
        boolean allNew = claim.targets().stream().allMatch(target ->
                hashEquals(target.getNewHash(), observedById.get(target.getId()))
        );
        if (allNew) {
            return ResourceReplacementResolution.COMMIT_NEW;
        }
        boolean allOld = claim.targets().stream().allMatch(target ->
                hashEquals(target.getSourceHash(), observedById.get(target.getId()))
        );
        return allOld
                ? ResourceReplacementResolution.RESTORE_OLD
                : ResourceReplacementResolution.KEEP_BLOCKED;
    }

    private void updateTargetEvidence(ResourceContentReplacement operation,
                                      List<TargetEvidence> evidence,
                                      String requiredHash,
                                      boolean requireComplete) {
        List<ResourceContentReplacementTarget> targets = listTargets(operation.getId());
        Map<Long, String> observedById = evidenceByTarget(evidence);
        var targetIds = targets.stream()
                .map(ResourceContentReplacementTarget::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!targetIds.containsAll(observedById.keySet())) {
            throw new IllegalArgumentException("替换目标证据包含未知目标");
        }
        if (requireComplete && !observedById.keySet().equals(targetIds)) {
            throw new IllegalArgumentException("替换目标完整回读证据不完整");
        }
        for (ResourceContentReplacementTarget target : targets) {
            String observedHash = observedById.get(target.getId());
            if (requireComplete && !hashEquals(requiredHash, observedHash)) {
                throw new IllegalStateException("替换目标完整回读SHA-256与决议不一致");
            }
            if (observedHash != null) {
                ResourceReplacementTargetStatus status = hashEquals(target.getNewHash(), observedHash)
                        ? ResourceReplacementTargetStatus.NEW_VERIFIED
                        : hashEquals(target.getSourceHash(), observedHash)
                        ? ResourceReplacementTargetStatus.OLD_VERIFIED
                        : ResourceReplacementTargetStatus.UNKNOWN;
                targetMapper.update(
                        null,
                        Wrappers.<ResourceContentReplacementTarget>lambdaUpdate()
                                .eq(ResourceContentReplacementTarget::getId, target.getId())
                                .eq(ResourceContentReplacementTarget::getReplacementId, operation.getId())
                                .set(ResourceContentReplacementTarget::getObservedHash, observedHash)
                                .set(ResourceContentReplacementTarget::getStatus, status.name())
                );
            }
        }
    }

    private Map<Long, String> evidenceByTarget(List<TargetEvidence> evidence) {
        Map<Long, String> observedById = new HashMap<>();
        if (evidence == null) {
            return observedById;
        }
        for (TargetEvidence item : evidence) {
            if (item == null || item.targetId() == null) {
                throw new IllegalArgumentException("替换目标证据缺少目标ID");
            }
            if (observedById.containsKey(item.targetId())) {
                throw new IllegalArgumentException("替换目标证据重复");
            }
            String observedHash = normalizeHash(item.observedHash());
            if (observedHash != null && !observedHash.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("替换目标观测SHA-256不合法");
            }
            observedById.put(item.targetId(), observedHash);
        }
        return observedById;
    }

    private List<ReplacementClaim> toClaims(List<ResourceContentReplacement> operations) {
        return operations.stream()
                .map(operation -> new ReplacementClaim(operation, listTargets(operation.getId())))
                .toList();
    }

    private List<ResourceContentReplacementTarget> listTargets(Long replacementId) {
        return targetMapper.selectList(
                Wrappers.<ResourceContentReplacementTarget>lambdaQuery()
                        .eq(ResourceContentReplacementTarget::getReplacementId, replacementId)
                        .orderByAsc(ResourceContentReplacementTarget::getId)
        );
    }

    private void validateBeginArguments(Resource expectedResource,
                                        Resource replacement,
                                        List<TargetPlan> targetPlans) {
        if (expectedResource == null || expectedResource.getId() == null || replacement == null) {
            throw new IllegalArgumentException("替换前后资源元数据不能为空");
        }
        if (!expectedResource.getId().equals(replacement.getId())) {
            throw new IllegalArgumentException("替换资源ID不一致");
        }
        if (replacement.getSize() == null || replacement.getSize() <= 0) {
            throw new IllegalArgumentException("替换资源大小必须大于0");
        }
        if (targetPlans == null || targetPlans.isEmpty()) {
            throw new IllegalArgumentException("替换事务必须包含至少一个物理目标");
        }
        for (TargetPlan plan : targetPlans) {
            if (plan == null
                    || !StringUtils.hasText(plan.targetPath())
                    || !StringUtils.hasText(plan.tempPath())
                    || !StringUtils.hasText(plan.backupPath())) {
                throw new IllegalArgumentException("替换目标路径信息不完整");
            }
        }
        long uniquePaths = targetPlans.stream().map(TargetPlan::targetPath).distinct().count();
        if (uniquePaths != targetPlans.size()) {
            throw new IllegalArgumentException("替换事务包含重复物理目标");
        }
    }

    private void assertExpectedResource(Resource current, Resource expected) {
        if (!Objects.equals(current.getPath(), expected.getPath())
                || !Objects.equals(current.getActiveLocationId(), expected.getActiveLocationId())
                || normalizeVersion(current.getLocationVersion())
                != normalizeVersion(expected.getLocationVersion())
                || !Objects.equals(normalizeHash(current.getResourceHash()), normalizeHash(expected.getResourceHash()))
                || !Objects.equals(current.getHashSource(), expected.getHashSource())
                || !sameContentState(current.getContentState(), expected.getContentState())) {
            throw new ConcurrentModificationException("资源在替换声明前发生变化");
        }
    }

    private void requireActiveContent(Resource resource, String message) {
        if (!ResourceContentState.isActive(resource.getContentState())) {
            throw new IllegalStateException(message);
        }
    }

    private void validateKnownHash(String knownHash, String observedHash, String message) {
        String normalizedKnown = normalizeHash(knownHash);
        if (normalizedKnown != null && !hashEquals(normalizedKnown, observedHash)) {
            throw new ConcurrentModificationException(message);
        }
    }

    private void appendActiveContentState(
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Resource> update,
            String contentState) {
        if (StringUtils.hasText(contentState)) {
            update.eq(Resource::getContentState, contentState);
        } else {
            update.and(wrapper -> wrapper.isNull(Resource::getContentState)
                    .or()
                    .eq(Resource::getContentState, ""));
        }
    }

    private void appendNullableResourceEquality(
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Resource> update,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<Resource, ?> column,
            Object value) {
        if (value instanceof String text && !StringUtils.hasText(text)) {
            update.and(wrapper -> wrapper.isNull(column).or().eq(column, ""));
        } else if (value == null) {
            update.isNull(column);
        } else {
            update.eq(column, value);
        }
    }

    private void appendNullableLocationEquality(
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ResourceLocation> update,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<ResourceLocation, String> column,
            String value) {
        if (StringUtils.hasText(value)) {
            update.eq(column, value);
        } else {
            update.and(wrapper -> wrapper.isNull(column).or().eq(column, ""));
        }
    }

    private boolean sameContentState(String left, String right) {
        return ResourceContentState.isActive(left) && ResourceContentState.isActive(right)
                || Objects.equals(left, right);
    }

    private String requireHash(String hash, String message) {
        String normalized = normalizeHash(hash);
        if (normalized == null || !normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeHash(String hash) {
        return StringUtils.hasText(hash) ? hash.toLowerCase(Locale.ROOT) : null;
    }

    private boolean hashEquals(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.equalsIgnoreCase(right);
    }

    private int normalizeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    private String truncate(String message) {
        String value = StringUtils.hasText(message) ? message : "内容替换需要恢复";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String truncateNullable(String message) {
        return StringUtils.hasText(message) ? truncate(message) : null;
    }

    public record TargetPlan(
            String targetPath,
            String tempPath,
            String backupPath,
            String sourceHash,
            String newHash
    ) {
    }

    public record TargetEvidence(Long targetId, String observedHash) {
    }

    public record ReplacementClaim(
            ResourceContentReplacement operation,
            List<ResourceContentReplacementTarget> targets
    ) {
    }

    public record RecoveryResult(
            ResourceReplacementResolution resolution,
            Resource resource
    ) {
    }
}