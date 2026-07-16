package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceContentReplacementServiceTest {

    private static final String OLD_HASH = "a".repeat(64);
    private static final String NEW_HASH = "b".repeat(64);
    private static final String UNKNOWN_HASH = "c".repeat(64);
    private static final LocalDateTime ORIGINAL_VERIFIED_AT = LocalDateTime.of(2026, 7, 14, 1, 2, 3);

    @Mock
    private ResourceMapper resourceMapper;

    @Mock
    private ResourceLocationMapper resourceLocationMapper;

    @Mock
    private ResourceContentReplacementMapper replacementMapper;

    @Mock
    private ResourceContentReplacementTargetMapper targetMapper;

    private ResourceContentReplacementService service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Resource.class);
        TableInfoHelper.initTableInfo(assistant, ResourceLocation.class);
        TableInfoHelper.initTableInfo(assistant, ResourceContentReplacement.class);
        TableInfoHelper.initTableInfo(assistant, ResourceContentReplacementTarget.class);
    }

    @BeforeEach
    void setUp() {
        service = new ResourceContentReplacementService(
                resourceMapper,
                resourceLocationMapper,
                replacementMapper,
                targetMapper
        );
    }

    @Test
    void beginShouldFreezeResourceAndPersistRecoveryTargetsBeforeClaimingPendingState() {
        Resource expected = activeResource();
        Resource current = activeResource();
        Resource replacement = replacementResource();
        ResourceLocation activeLocation = activeLocation();
        AtomicReference<Wrapper<Resource>> resourceUpdate = new AtomicReference<>();

        when(resourceMapper.selectByIdForUpdate(expected.getId())).thenReturn(current);
        when(resourceLocationMapper.selectByIdForUpdate(current.getActiveLocationId()))
                .thenReturn(activeLocation);
        when(replacementMapper.insert(any(ResourceContentReplacement.class))).thenAnswer(invocation -> {
            ResourceContentReplacement operation = invocation.getArgument(0);
            operation.setId(101L);
            return 1;
        });
        when(targetMapper.insert(any(ResourceContentReplacementTarget.class))).thenAnswer(invocation -> {
            ResourceContentReplacementTarget target = invocation.getArgument(0);
            target.setId(201L);
            return 1;
        });
        when(resourceMapper.update(isNull(), any())).thenAnswer(invocation -> {
            resourceUpdate.set(invocation.getArgument(1));
            return 1;
        });

        ResourceContentReplacementService.ReplacementClaim claim = service.begin(
                expected,
                replacement,
                OLD_HASH.toUpperCase(),
                List.of(targetPlan("/srv/static/image.png"))
        );

        ResourceContentReplacement operation = claim.operation();
        assertThat(operation.getId()).isEqualTo(101L);
        assertThat(operation.getResourceId()).isEqualTo(expected.getId());
        assertThat(operation.getActiveLocationId()).isEqualTo(activeLocation.getId());
        assertThat(operation.getOriginalLocationVersion()).isEqualTo(3);
        assertThat(operation.getClaimedLocationVersion()).isEqualTo(4);
        assertThat(operation.getSourceHash()).isEqualTo(OLD_HASH);
        assertThat(operation.getNewHash()).isEqualTo(NEW_HASH);
        assertThat(operation.getSourceLocationStoreType()).isEqualTo("local");
        assertThat(operation.getSourceLocationAccessPath()).isEqualTo("/static/assets/image.png");
        assertThat(operation.getStatus()).isEqualTo(ResourceReplacementStatus.PENDING.name());

        assertThat(claim.targets()).singleElement().satisfies(target -> {
            assertThat(target.getId()).isEqualTo(201L);
            assertThat(target.getReplacementId()).isEqualTo(101L);
            assertThat(target.getTargetPath()).isEqualTo("/srv/static/image.png");
            assertThat(target.getSourceHash()).isEqualTo(OLD_HASH);
            assertThat(target.getNewHash()).isEqualTo(NEW_HASH);
            assertThat(target.getStatus()).isEqualTo(ResourceReplacementTargetStatus.PLANNED.name());
        });
        assertThat(sqlSet(resourceUpdate.get()))
                .contains("content_state", "hash_verified_at", "location_version");
        assertThat(values(resourceUpdate.get()))
                .contains(ResourceContentState.REPLACEMENT_PENDING.name(), 4);
    }

    @Test
    void beginShouldRejectResourceAlreadyBlockedByAnotherReplacement() {
        Resource expected = activeResource();
        expected.setContentState(ResourceContentState.REPLACEMENT_PENDING.name());
        Resource current = activeResource();
        current.setContentState(ResourceContentState.REPLACEMENT_PENDING.name());

        when(resourceMapper.selectByIdForUpdate(expected.getId())).thenReturn(current);

        assertThatThrownBy(() -> service.begin(
                expected,
                replacementResource(),
                OLD_HASH,
                List.of(targetPlan("/srv/static/image.png"))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未完成的内容替换");

        verify(resourceLocationMapper, never()).selectByIdForUpdate(any());
        verify(replacementMapper, never()).insert(any(ResourceContentReplacement.class));
        verify(targetMapper, never()).insert(any(ResourceContentReplacementTarget.class));
        verify(resourceMapper, never()).update(any(), any());
    }

    @Test
    void commitShouldPublishNewMetadataWithSharedStrictVerificationTime() {
        ResourceContentReplacement operation = openOperation();
        Resource claimed = claimedResource();
        ResourceLocation location = activeLocation();
        ResourceContentReplacementTarget target = target(201L, "/srv/static/image.png");
        Resource persisted = committedResource();
        AtomicReference<Wrapper<Resource>> resourceUpdate = new AtomicReference<>();
        AtomicReference<Wrapper<ResourceLocation>> locationUpdate = new AtomicReference<>();
        AtomicReference<Wrapper<ResourceContentReplacement>> operationUpdate = new AtomicReference<>();
        List<Wrapper<ResourceContentReplacementTarget>> targetUpdates = new ArrayList<>();

        stubOpenOperation(operation, claimed, location, List.of(target));
        when(resourceMapper.update(isNull(), any())).thenAnswer(invocation -> {
            resourceUpdate.set(invocation.getArgument(1));
            return 1;
        });
        when(resourceLocationMapper.update(isNull(), any())).thenAnswer(invocation -> {
            locationUpdate.set(invocation.getArgument(1));
            return 1;
        });
        when(replacementMapper.update(isNull(), any())).thenAnswer(invocation -> {
            operationUpdate.set(invocation.getArgument(1));
            return 1;
        });
        when(targetMapper.update(isNull(), any())).thenAnswer(invocation -> {
            targetUpdates.add(invocation.getArgument(1));
            return 1;
        });
        when(resourceMapper.selectById(operation.getResourceId())).thenReturn(persisted);

        LocalDateTime beforeCommit = LocalDateTime.now();
        Resource result = service.commit(
                operation.getOperationId(),
                List.of(new ResourceContentReplacementService.TargetEvidence(201L, NEW_HASH.toUpperCase()))
        );
        LocalDateTime afterCommit = LocalDateTime.now();

        assertThat(result).isSameAs(persisted);
        assertThat(sqlSet(resourceUpdate.get()))
                .contains("resource_hash", "hash_source", "hash_verified_at", "content_state", "location_version");
        assertThat(values(resourceUpdate.get())).contains(
                NEW_HASH,
                "REPLACEMENT_WRITE",
                ResourceContentState.ACTIVE.name(),
                5
        );
        assertThat(sqlSet(locationUpdate.get()))
                .contains("content_hash", "size", "mime_type", "verified_at");
        assertThat(values(locationUpdate.get())).contains(NEW_HASH, 256L, "image/png");

        LocalDateTime resourceVerifiedAt = onlyTimestamp(resourceUpdate.get());
        LocalDateTime locationVerifiedAt = onlyTimestamp(locationUpdate.get());
        assertThat(resourceVerifiedAt).isEqualTo(locationVerifiedAt);
        assertThat(resourceVerifiedAt).isBetween(beforeCommit, afterCommit);
        assertThat(values(operationUpdate.get())).contains(ResourceReplacementStatus.COMMITTED.name());
        assertThat(targetUpdates).hasSize(2);
        assertThat(targetUpdates.stream().flatMap(wrapper -> values(wrapper).stream()).toList())
                .contains(NEW_HASH, ResourceReplacementTargetStatus.NEW_VERIFIED.name());
    }

    @Test
    void abortShouldRestoreOriginalHashEvidenceWithoutChangingLocationMetadata() {
        ResourceContentReplacement operation = openOperation();
        Resource claimed = claimedResource();
        ResourceLocation location = activeLocation();
        ResourceContentReplacementTarget target = target(201L, "/srv/static/image.png");
        AtomicReference<Wrapper<Resource>> resourceUpdate = new AtomicReference<>();
        AtomicReference<Wrapper<ResourceContentReplacement>> operationUpdate = new AtomicReference<>();
        List<Wrapper<ResourceContentReplacementTarget>> targetUpdates = new ArrayList<>();

        stubOpenOperation(operation, claimed, location, List.of(target));
        when(resourceMapper.update(isNull(), any())).thenAnswer(invocation -> {
            resourceUpdate.set(invocation.getArgument(1));
            return 1;
        });
        when(replacementMapper.update(isNull(), any())).thenAnswer(invocation -> {
            operationUpdate.set(invocation.getArgument(1));
            return 1;
        });
        when(targetMapper.update(isNull(), any())).thenAnswer(invocation -> {
            targetUpdates.add(invocation.getArgument(1));
            return 1;
        });
        when(resourceMapper.selectById(operation.getResourceId())).thenReturn(activeResource());

        Resource result = service.abort(
                operation.getOperationId(),
                List.of(new ResourceContentReplacementService.TargetEvidence(201L, OLD_HASH))
        );

        assertThat(result.getResourceHash()).isEqualTo(OLD_HASH);
        assertThat(values(resourceUpdate.get())).contains(
                OLD_HASH,
                "UPLOAD",
                ORIGINAL_VERIFIED_AT,
                ResourceContentState.ACTIVE.name(),
                5
        );
        assertThat(values(operationUpdate.get())).contains(ResourceReplacementStatus.ABORTED.name());
        assertThat(targetUpdates).hasSize(2);
        assertThat(targetUpdates.stream().flatMap(wrapper -> values(wrapper).stream()).toList())
                .contains(OLD_HASH, ResourceReplacementTargetStatus.OLD_VERIFIED.name());
        verify(resourceLocationMapper, never()).update(any(), any());
    }

    @Test
    void recoverShouldKeepResourceBlockedWhenTargetsContainMixedVersions() {
        ResourceContentReplacement operation = openOperation();
        Resource claimed = claimedResource();
        ResourceLocation location = activeLocation();
        List<ResourceContentReplacementTarget> targets = List.of(
                target(201L, "/srv/static/image.png"),
                target(202L, "/srv/static/image-copy.png")
        );
        AtomicReference<Wrapper<ResourceContentReplacement>> operationUpdate = new AtomicReference<>();
        List<Wrapper<ResourceContentReplacementTarget>> targetUpdates = new ArrayList<>();

        when(replacementMapper.selectOne(any())).thenReturn(operation);
        stubOpenOperation(operation, claimed, location, targets);
        when(replacementMapper.update(isNull(), any())).thenAnswer(invocation -> {
            operationUpdate.set(invocation.getArgument(1));
            return 1;
        });
        when(targetMapper.update(isNull(), any())).thenAnswer(invocation -> {
            targetUpdates.add(invocation.getArgument(1));
            return 1;
        });

        ResourceContentReplacementService.RecoveryResult result = service.recover(
                operation.getOperationId(),
                List.of(
                        new ResourceContentReplacementService.TargetEvidence(201L, NEW_HASH),
                        new ResourceContentReplacementService.TargetEvidence(202L, OLD_HASH)
                )
        );

        assertThat(result.resolution()).isEqualTo(ResourceReplacementResolution.KEEP_BLOCKED);
        assertThat(result.resource()).isNull();
        assertThat(values(operationUpdate.get()))
                .contains(ResourceReplacementStatus.RECOVERY_REQUIRED.name());
        assertThat(targetUpdates).hasSize(2);
        assertThat(targetUpdates.stream().flatMap(wrapper -> values(wrapper).stream()).toList())
                .contains(
                        ResourceReplacementTargetStatus.NEW_VERIFIED.name(),
                        ResourceReplacementTargetStatus.OLD_VERIFIED.name()
                );
        verify(resourceMapper, never()).update(any(), any());
        verify(resourceLocationMapper, never()).update(any(), any());
    }

    @Test
    void commitShouldRejectIncompleteFullReadEvidenceBeforePublishingMetadata() {
        ResourceContentReplacement operation = openOperation();
        Resource claimed = claimedResource();
        ResourceLocation location = activeLocation();
        List<ResourceContentReplacementTarget> targets = List.of(
                target(201L, "/srv/static/image.png"),
                target(202L, "/srv/static/image-copy.png")
        );

        stubOpenOperation(operation, claimed, location, targets);

        assertThatThrownBy(() -> service.commit(
                operation.getOperationId(),
                List.of(new ResourceContentReplacementService.TargetEvidence(201L, NEW_HASH))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完整回读证据不完整");

        verify(resourceMapper, never()).update(any(), any());
        verify(resourceLocationMapper, never()).update(any(), any());
        verify(replacementMapper, never()).update(any(), any());
        verify(targetMapper, never()).update(any(), any());
    }

    private void stubOpenOperation(ResourceContentReplacement operation,
                                   Resource claimed,
                                   ResourceLocation location,
                                   List<ResourceContentReplacementTarget> targets) {
        when(replacementMapper.selectByOperationIdForUpdate(operation.getOperationId()))
                .thenReturn(operation);
        when(resourceMapper.selectByIdForUpdate(operation.getResourceId())).thenReturn(claimed);
        when(resourceLocationMapper.selectByIdForUpdate(operation.getActiveLocationId()))
                .thenReturn(location);
        when(targetMapper.selectList(any())).thenReturn(targets);
    }

    private Resource activeResource() {
        Resource resource = new Resource();
        resource.setId(1);
        resource.setPublicId("0123456789abcdef0123456789abcdef");
        resource.setPath("/media/0123456789abcdef0123456789abcdef");
        resource.setStoreType("local");
        resource.setStorageKey("assets/image.png");
        resource.setActiveLocationId(21L);
        resource.setLocationVersion(3);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        resource.setResourceHash(OLD_HASH);
        resource.setHashSource("UPLOAD");
        resource.setHashVerifiedAt(ORIGINAL_VERIFIED_AT);
        resource.setOriginalName("image.png");
        resource.setMimeType("image/png");
        resource.setSize(128);
        resource.setWidth(20);
        resource.setHeight(10);
        return resource;
    }

    private Resource replacementResource() {
        Resource replacement = activeResource();
        replacement.setResourceHash(NEW_HASH);
        replacement.setOriginalName("replacement.png");
        replacement.setMimeType("image/png");
        replacement.setSize(256);
        replacement.setWidth(40);
        replacement.setHeight(20);
        return replacement;
    }

    private Resource claimedResource() {
        Resource resource = activeResource();
        resource.setLocationVersion(4);
        resource.setContentState(ResourceContentState.REPLACEMENT_PENDING.name());
        return resource;
    }

    private Resource committedResource() {
        Resource resource = replacementResource();
        resource.setLocationVersion(5);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        resource.setHashSource("REPLACEMENT_WRITE");
        resource.setHashVerifiedAt(LocalDateTime.now());
        return resource;
    }

    private ResourceLocation activeLocation() {
        ResourceLocation location = new ResourceLocation();
        location.setId(21L);
        location.setResourceId(1);
        location.setStoreType("local");
        location.setStorageKey("assets/image.png");
        location.setAccessPath("/static/assets/image.png");
        location.setContentHash(OLD_HASH);
        location.setSize(128L);
        location.setMimeType("image/png");
        location.setStatus(ResourceLocationStatus.ACTIVE.name());
        location.setVerifiedAt(ORIGINAL_VERIFIED_AT);
        return location;
    }

    private ResourceContentReplacement openOperation() {
        ResourceContentReplacement operation = new ResourceContentReplacement();
        operation.setId(101L);
        operation.setOperationId("operation-1");
        operation.setResourceId(1);
        operation.setActiveLocationId(21L);
        operation.setExpectedPath("/media/0123456789abcdef0123456789abcdef");
        operation.setOriginalLocationVersion(3);
        operation.setClaimedLocationVersion(4);
        operation.setOriginalResourceHash(OLD_HASH);
        operation.setSourceHash(OLD_HASH);
        operation.setOriginalHashSource("UPLOAD");
        operation.setOriginalHashVerifiedAt(ORIGINAL_VERIFIED_AT);
        operation.setSourceLocationStoreType("local");
        operation.setSourceLocationStorageKey("assets/image.png");
        operation.setSourceLocationAccessPath("/static/assets/image.png");
        operation.setSourceLocationHash(OLD_HASH);
        operation.setSourceLocationStatus(ResourceLocationStatus.ACTIVE.name());
        operation.setNewHash(NEW_HASH);
        operation.setNewSize(256);
        operation.setNewOriginalName("replacement.png");
        operation.setNewMimeType("image/png");
        operation.setNewWidth(40);
        operation.setNewHeight(20);
        operation.setStatus(ResourceReplacementStatus.PENDING.name());
        return operation;
    }

    private ResourceContentReplacementTarget target(Long id, String path) {
        ResourceContentReplacementTarget target = new ResourceContentReplacementTarget();
        target.setId(id);
        target.setReplacementId(101L);
        target.setTargetPath(path);
        target.setTempPath(path + ".replacing-operation-1");
        target.setBackupPath(path + ".backup-operation-1");
        target.setSourceHash(OLD_HASH);
        target.setNewHash(NEW_HASH);
        target.setStatus(ResourceReplacementTargetStatus.PLANNED.name());
        return target;
    }

    private ResourceContentReplacementService.TargetPlan targetPlan(String path) {
        return new ResourceContentReplacementService.TargetPlan(
                path,
                path + ".replacing-operation-1",
                path + ".backup-operation-1",
                OLD_HASH.toUpperCase(),
                NEW_HASH.toUpperCase()
        );
    }

    private String sqlSet(Wrapper<?> wrapper) {
        assertThat(wrapper).isInstanceOf(LambdaUpdateWrapper.class);
        return ((LambdaUpdateWrapper<?>) wrapper).getSqlSet();
    }

    private List<Object> values(Wrapper<?> wrapper) {
        assertThat(wrapper).isInstanceOf(LambdaUpdateWrapper.class);
        Map<String, Object> parameters = ((LambdaUpdateWrapper<?>) wrapper).getParamNameValuePairs();
        return new ArrayList<>(parameters.values());
    }

    private LocalDateTime onlyTimestamp(Wrapper<?> wrapper) {
        List<LocalDateTime> timestamps = values(wrapper).stream()
                .filter(LocalDateTime.class::isInstance)
                .map(LocalDateTime.class::cast)
                .toList();
        assertThat(timestamps).hasSize(1);
        return timestamps.getFirst();
    }
}