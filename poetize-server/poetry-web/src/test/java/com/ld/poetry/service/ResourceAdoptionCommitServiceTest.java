package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ld.poetry.dao.ResourceAdoptionItemMapper;
import com.ld.poetry.dao.ResourceAdoptionTaskMapper;
import com.ld.poetry.dao.ResourceAliasMapper;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceAdoptionItem;
import com.ld.poetry.entity.ResourceAdoptionTask;
import com.ld.poetry.entity.ResourceAlias;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceAdoptionItemStatus;
import com.ld.poetry.enums.ResourceAdoptionTaskStatus;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAdoptionCommitServiceTest {

    private static final String SOURCE_URL = "https://cdn.example.com/images/cover.png";
    private static final String VERIFIED_HASH = "b".repeat(64);
    private static final long VERIFIED_SIZE = 1234L;

    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private ResourceLocationMapper locationMapper;
    @Mock
    private ResourceAliasMapper aliasMapper;
    @Mock
    private ResourceAdoptionTaskMapper taskMapper;
    @Mock
    private ResourceAdoptionItemMapper itemMapper;
    @Mock
    private ResourceReferenceService referenceService;
    @Mock
    private ResourceLocationService resourceLocationService;

    private ResourceAdoptionCommitService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Resource.class);
        TableInfoHelper.initTableInfo(assistant, ResourceAdoptionItem.class);
        service = new ResourceAdoptionCommitService(
                resourceMapper,
                locationMapper,
                aliasMapper,
                taskMapper,
                itemMapper,
                referenceService,
                resourceLocationService
        );
    }

    @Test
    void unverifiedLegacyHashIsReplacedByCompleteReadBackBaseline() {
        ResourceAdoptionItem item = readingItem();
        ResourceAdoptionTask task = runningTask();
        Resource resource = legacyResource("legacy-md5", null);
        ResourceLocation location = legacyLocation("legacy-md5", null);
        ResourceAlias alias = alias(resource.getId());

        when(itemMapper.selectById(11L)).thenReturn(item);
        when(taskMapper.findByTaskIdForUpdate(item.getTaskId())).thenReturn(task);
        when(itemMapper.selectByIdForUpdate(11L)).thenReturn(item);
        when(referenceService.countReferences(SOURCE_URL)).thenReturn(1, 0);
        when(aliasMapper.findActiveByAliasUrlForUpdate(SOURCE_URL)).thenReturn(alias);
        when(locationMapper.findByStoreAndAccessPathForUpdate("qiniu", SOURCE_URL)).thenReturn(location);
        when(resourceMapper.findByPathForUpdate(SOURCE_URL)).thenReturn(resource);
        when(resourceMapper.selectByIdForUpdate(resource.getId())).thenReturn(resource);
        when(locationMapper.selectByIdForUpdate(location.getId())).thenReturn(location);
        when(locationMapper.updateById(location)).thenReturn(1);
        when(resourceLocationService.stablePath(resource.getPublicId()))
                .thenReturn("/media/" + resource.getPublicId());
        when(resourceMapper.update(any(), any())).thenReturn(1);
        when(referenceService.replaceReferences(SOURCE_URL, "/media/" + resource.getPublicId()))
                .thenReturn(new ResourceReferenceService.ReplacementResult(1, java.util.Set.of(9), java.util.Set.of(), java.util.Set.of("ARTICLE")));
        when(itemMapper.update(any(), any())).thenReturn(1);

        ResourceAdoptionCommitService.CommitResult result = service.commit(adoption());

        assertThat(result.hashBaselined()).isTrue();
        assertThat(result.resource().getResourceHash()).isEqualTo(VERIFIED_HASH);
        assertThat(result.resource().getHashVerifiedAt()).isEqualTo(adoption().verifiedAt());
        assertThat(result.location().getContentHash()).isEqualTo(VERIFIED_HASH);
        assertThat(result.location().getSize()).isEqualTo(VERIFIED_SIZE);
        assertThat(result.location().getVerifiedAt()).isEqualTo(adoption().verifiedAt());
        verify(referenceService).replaceReferences(SOURCE_URL, "/media/" + resource.getPublicId());
    }

    @Test
    void verifiedSha256ConflictIsRejectedBeforeLocationOrReferenceMutation() {
        ResourceAdoptionItem item = readingItem();
        ResourceAdoptionTask task = runningTask();
        Resource resource = legacyResource("a".repeat(64), LocalDateTime.now().minusDays(1));
        ResourceLocation location = legacyLocation("a".repeat(64), LocalDateTime.now().minusDays(1));
        ResourceAlias alias = alias(resource.getId());

        when(itemMapper.selectById(11L)).thenReturn(item);
        when(taskMapper.findByTaskIdForUpdate(item.getTaskId())).thenReturn(task);
        when(itemMapper.selectByIdForUpdate(11L)).thenReturn(item);
        when(referenceService.countReferences(SOURCE_URL)).thenReturn(1);
        when(aliasMapper.findActiveByAliasUrlForUpdate(SOURCE_URL)).thenReturn(alias);
        when(locationMapper.findByStoreAndAccessPathForUpdate("qiniu", SOURCE_URL)).thenReturn(location);
        when(resourceMapper.findByPathForUpdate(SOURCE_URL)).thenReturn(resource);
        when(resourceMapper.selectByIdForUpdate(resource.getId())).thenReturn(resource);

        assertThatThrownBy(() -> service.commit(adoption()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已登记资源哈希");

        verify(locationMapper, never()).updateById(any(ResourceLocation.class));
        verify(referenceService, never()).replaceReferences(any(), any());
        verify(itemMapper, never()).update(any(), any());
    }

    private ResourceAdoptionCommitService.VerifiedAdoption adoption() {
        return new ResourceAdoptionCommitService.VerifiedAdoption(
                11L,
                7,
                SOURCE_URL,
                "qiniu",
                "images/cover.png",
                SOURCE_URL,
                "cover.png",
                VERIFIED_HASH,
                VERIFIED_SIZE,
                "image/png",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
    }

    private ResourceAdoptionItem readingItem() {
        ResourceAdoptionItem item = new ResourceAdoptionItem();
        item.setId(11L);
        item.setTaskId("0123456789abcdef0123456789abcdef");
        item.setSourceUrl(SOURCE_URL);
        item.setReferenceCount(1);
        item.setStatus(ResourceAdoptionItemStatus.READING.name());
        return item;
    }

    private ResourceAdoptionTask runningTask() {
        ResourceAdoptionTask task = new ResourceAdoptionTask();
        task.setTaskId("0123456789abcdef0123456789abcdef");
        task.setCreatedBy(7);
        task.setStatus(ResourceAdoptionTaskStatus.RUNNING.name());
        return task;
    }

    private Resource legacyResource(String hash, LocalDateTime verifiedAt) {
        Resource resource = new Resource();
        resource.setId(21);
        resource.setPublicId("abcdef0123456789abcdef0123456789");
        resource.setUserId(7);
        resource.setType("articleCover");
        resource.setPath(SOURCE_URL);
        resource.setStatus(true);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        resource.setResourceHash(hash);
        resource.setHashVerifiedAt(verifiedAt);
        resource.setActiveLocationId(31L);
        resource.setLocationVersion(0);
        return resource;
    }

    private ResourceLocation legacyLocation(String hash, LocalDateTime verifiedAt) {
        ResourceLocation location = new ResourceLocation();
        location.setId(31L);
        location.setResourceId(21);
        location.setStoreType("qiniu");
        location.setStorageKey("images/cover.png");
        location.setAccessPath(SOURCE_URL);
        location.setContentHash(hash);
        location.setSize(999L);
        location.setMimeType("image/png");
        location.setStatus(ResourceLocationStatus.ACTIVE.name());
        location.setVerifiedAt(verifiedAt);
        return location;
    }

    private ResourceAlias alias(Integer resourceId) {
        ResourceAlias alias = new ResourceAlias();
        alias.setResourceId(resourceId);
        alias.setAliasUrl(SOURCE_URL);
        alias.setStatus(true);
        return alias;
    }
}