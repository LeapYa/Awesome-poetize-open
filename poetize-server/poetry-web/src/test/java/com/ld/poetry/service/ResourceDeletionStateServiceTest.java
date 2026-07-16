package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ConcurrentModificationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceDeletionStateServiceTest {

    private static final String HASH = "a".repeat(64);
    private static final String STABLE_PATH = "/media/0123456789abcdef0123456789abcdef";

    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private ResourceLocationMapper resourceLocationMapper;
    @Mock
    private ResourceAliasMapper resourceAliasMapper;
    @Mock
    private ResourceRedirectMapper resourceRedirectMapper;
    @Mock
    private ResourceReferenceService referenceService;

    private ResourceDeletionStateService service;

    /**
     * claim()/finalizeDeletion() 内部直接构造 {@link com.baomidou.mybatisplus.core.toolkit.Wrappers#lambdaUpdate()} /
     * {@link com.baomidou.mybatisplus.core.toolkit.Wrappers#lambdaQuery()}，这些操作需要 MyBatis-Plus 的
     * 实体 lambda cache（TableInfo）。纯 Mockito 单元测试未启动 Spring，需要手动初始化涉及的实体表信息，
     * 否则 {@code Wrappers.lambdaUpdate().set(Resource::getStatus, ...)} 会因找不到 lambda cache 而抛
     * {@code MybatisPlusException: can not find lambda cache for this entity}。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Resource.class);
        TableInfoHelper.initTableInfo(assistant, ResourceLocation.class);
        TableInfoHelper.initTableInfo(assistant, ResourceAlias.class);
        TableInfoHelper.initTableInfo(assistant, ResourceRedirect.class);
    }

    @BeforeEach
    void setUp() {
        service = new ResourceDeletionStateService(
                resourceMapper,
                resourceLocationMapper,
                resourceAliasMapper,
                resourceRedirectMapper,
                referenceService
        );
    }

    @Test
    void claimShouldRejectPathChanged() {
        Resource resource = activeResource(1, 11L);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);

        assertThatThrownBy(() -> service.claim(new ResourceDeletionStateService.LogicalDeletionPlan(
                1, "/media/changed", false, List.of()
        )))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("资源路径已变化");
    }

    @Test
    void claimShouldRejectReplacementPendingResource() {
        Resource resource = activeResource(1, 11L);
        resource.setContentState(ResourceContentState.REPLACEMENT_PENDING.name());
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);

        assertThatThrownBy(() -> service.claim(plan(1, false, List.of(
                disposition(11L, ResourceDeletionStateService.LocationDisposition.DELETE)
        ))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许删除");
    }

    @Test
    void claimShouldRejectReferencedWithoutForce() {
        Resource resource = activeResource(1, 11L);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(2);

        assertThatThrownBy(() -> service.claim(plan(1, false, List.of(
                disposition(11L, ResourceDeletionStateService.LocationDisposition.DELETE)
        ))))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("2 处引用");
    }

    @Test
    void claimShouldDeclareActiveAndRetainedLocations() {
        Resource resource = activeResource(1, 11L);
        ResourceLocation active = location(11L, ResourceLocationStatus.ACTIVE);
        ResourceLocation retained = location(12L, ResourceLocationStatus.RETAINED);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(0);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(active, retained));
        when(resourceMapper.update(any(), any())).thenReturn(1);
        when(resourceLocationMapper.update(any(), any())).thenReturn(1);

        ResourceDeletionStateService.LogicalDeletionClaim claim = service.claim(plan(1, false, List.of(
                disposition(11L, ResourceDeletionStateService.LocationDisposition.DELETE),
                disposition(12L, ResourceDeletionStateService.LocationDisposition.DELETE)
        )));

        assertThat(claim.claimedLocations()).hasSize(2);
        assertThat(claim.inProgressLocationIds()).isEmpty();
        assertThat(claim.claimedLocations())
                .extracting(cl -> cl.location().getId())
                .containsExactlyInAnyOrder(11L, 12L);
        assertThat(claim.claimedLocations())
                .extracting(ResourceLocationService.LocationDeletionClaim::originalStatus)
                .containsExactlyInAnyOrder(
                        ResourceLocationStatus.ACTIVE.name(),
                        ResourceLocationStatus.RETAINED.name()
                );
        assertThat(claim.resource().getLocationVersion()).isEqualTo(1);
        assertThat(claim.resource().getStatus()).isFalse();
        assertThat(claim.resource().getContentState())
                .isEqualTo(ResourceContentState.DELETION_PENDING.name());
        verify(resourceMapper).update(any(), any());
    }

    @Test
    void claimShouldRetryPendingWithoutRedeclaring() {
        Resource resource = pendingResource(1, 11L);
        ResourceLocation deleting = location(11L, ResourceLocationStatus.DELETING);
        ResourceLocation retained = location(12L, ResourceLocationStatus.RETAINED);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(0);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(deleting, retained));
        when(resourceLocationMapper.update(any(), any())).thenReturn(1);

        ResourceDeletionStateService.LogicalDeletionClaim claim = service.claim(plan(1, false, List.of(
                disposition(12L, ResourceDeletionStateService.LocationDisposition.DELETE)
        )));

        assertThat(claim.inProgressLocationIds()).containsExactly(11L);
        assertThat(claim.claimedLocations()).hasSize(1);
        assertThat(claim.claimedLocations().getFirst().location().getId()).isEqualTo(12L);
        assertThat(claim.resource().getLocationVersion()).isEqualTo(1);
        verify(resourceMapper, never()).update(any(), any());
    }

    @Test
    void claimShouldRejectPendingWithReferencesWithoutForce() {
        Resource resource = pendingResource(1, 11L);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(1);

        assertThatThrownBy(() -> service.claim(plan(1, false, List.of(
                disposition(11L, ResourceDeletionStateService.LocationDisposition.DELETE)
        ))))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("1 处引用");
    }

    @Test
    void claimShouldRejectMissingDispositionsForNonTerminalLocation() {
        Resource resource = activeResource(1, 11L);
        ResourceLocation active = location(11L, ResourceLocationStatus.ACTIVE);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(0);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(active));

        assertThatThrownBy(() -> service.claim(plan(1, false, List.of())))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("发生变化");
    }

    @Test
    void claimShouldMarkMissingAndDetachInsteadOfPhysicalDelete() {
        Resource resource = activeResource(1, 11L);
        ResourceLocation missing = location(11L, ResourceLocationStatus.ACTIVE);
        ResourceLocation unsupported = location(12L, ResourceLocationStatus.RETAINED);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(0);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(missing, unsupported));
        when(resourceMapper.update(any(), any())).thenReturn(1);
        when(resourceLocationMapper.update(any(), any())).thenReturn(1);

        ResourceDeletionStateService.LogicalDeletionClaim claim = service.claim(plan(1, false, List.of(
                disposition(11L, ResourceDeletionStateService.LocationDisposition.MARK_MISSING),
                disposition(12L, ResourceDeletionStateService.LocationDisposition.DETACH)
        )));

        assertThat(claim.claimedLocations()).isEmpty();
        assertThat(claim.inProgressLocationIds()).isEmpty();
        assertThat(missing.getStatus()).isEqualTo(ResourceLocationStatus.MISSING.name());
        assertThat(unsupported.getStatus()).isEqualTo(ResourceLocationStatus.DETACHED.name());
    }

    @Test
    void finalizeDeletionShouldReturnFalseWhenLocationsNotAllTerminal() {
        Resource resource = pendingResource(1, 11L);
        ResourceLocation deleting = location(11L, ResourceLocationStatus.DELETING);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(deleting));

        boolean finalized = service.finalizeDeletion(1, STABLE_PATH, false);

        assertThat(finalized).isFalse();
        verify(resourceMapper, never()).delete(any());
    }

    @Test
    void finalizeDeletionShouldRejectReferencesWithoutForce() {
        Resource resource = pendingResource(1, 11L);
        ResourceLocation deleted = location(11L, ResourceLocationStatus.DELETED);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(deleted));
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(1);

        assertThatThrownBy(() -> service.finalizeDeletion(1, STABLE_PATH, false))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("1 处引用");

        verify(resourceMapper, never()).delete(any());
    }

    @Test
    void finalizeDeletionShouldDeleteResourceWhenAllTerminalAndUnreferenced() {
        Resource resource = pendingResource(1, 11L);
        ResourceLocation deleted = location(11L, ResourceLocationStatus.DELETED);
        ResourceLocation missing = location(12L, ResourceLocationStatus.MISSING);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(deleted, missing));
        when(resourceAliasMapper.selectList(any())).thenReturn(List.of());
        when(referenceService.countReferences(STABLE_PATH)).thenReturn(0);
        when(resourceMapper.delete(any())).thenReturn(1);

        boolean finalized = service.finalizeDeletion(1, STABLE_PATH, false);

        assertThat(finalized).isTrue();
        verify(resourceRedirectMapper).delete(any());
        verify(resourceAliasMapper).delete(any());
        verify(resourceLocationMapper).delete(any());
        verify(resourceMapper).delete(any());
    }

    @Test
    void finalizeDeletionShouldSkipReferenceCheckWhenForced() {
        Resource resource = pendingResource(1, 11L);
        ResourceLocation deleted = location(11L, ResourceLocationStatus.DELETED);
        when(resourceMapper.selectByIdForUpdate(1)).thenReturn(resource);
        when(resourceLocationMapper.findByResourceIdForUpdate(1)).thenReturn(List.of(deleted));
        when(resourceMapper.delete(any())).thenReturn(1);

        boolean finalized = service.finalizeDeletion(1, STABLE_PATH, true);

        assertThat(finalized).isTrue();
        verify(referenceService, never()).countReferences(any());
    }

    private Resource activeResource(Integer id, Long activeLocationId) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setPath(STABLE_PATH);
        resource.setStatus(true);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        resource.setLocationVersion(0);
        resource.setActiveLocationId(activeLocationId);
        resource.setResourceHash(HASH);
        return resource;
    }

    private Resource pendingResource(Integer id, Long activeLocationId) {
        Resource resource = activeResource(id, activeLocationId);
        resource.setStatus(false);
        resource.setContentState(ResourceContentState.DELETION_PENDING.name());
        resource.setLocationVersion(1);
        return resource;
    }

    private ResourceLocation location(Long id, ResourceLocationStatus status) {
        ResourceLocation location = new ResourceLocation();
        location.setId(id);
        location.setResourceId(1);
        location.setStoreType("local");
        location.setStorageKey("assets/" + id + ".png");
        location.setAccessPath("/physical/" + id + ".png");
        location.setContentHash(HASH);
        location.setSize(128L);
        location.setMimeType("image/png");
        location.setStatus(status.name());
        return location;
    }

    private ResourceDeletionStateService.LocationDeletionDisposition disposition(
            Long locationId, ResourceDeletionStateService.LocationDisposition disposition) {
        return new ResourceDeletionStateService.LocationDeletionDisposition(locationId, disposition);
    }

    private ResourceDeletionStateService.LogicalDeletionPlan plan(
            Integer resourceId,
            boolean forceReferenced,
            List<ResourceDeletionStateService.LocationDeletionDisposition> dispositions) {
        return new ResourceDeletionStateService.LogicalDeletionPlan(
                resourceId, STABLE_PATH, forceReferenced, dispositions
        );
    }
}
