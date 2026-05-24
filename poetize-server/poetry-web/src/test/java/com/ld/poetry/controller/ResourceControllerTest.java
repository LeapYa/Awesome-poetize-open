package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.service.ResourceAvailabilityService;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.FileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class ResourceControllerTest {

    @Mock
    private ResourceService resourceService;

    @Mock
    private ResourceMapper resourceMapper;

    @Mock
    private ResourceAvailabilityService resourceAvailabilityService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileSecurityValidator fileSecurityValidator;

    private ResourceController controller;

    @BeforeEach
    void setUp() {
        controller = new ResourceController();
        ReflectionTestUtils.setField(controller, "resourceService", resourceService);
        ReflectionTestUtils.setField(controller, "resourceMapper", resourceMapper);
        ReflectionTestUtils.setField(controller, "resourceAvailabilityService", resourceAvailabilityService);
        ReflectionTestUtils.setField(controller, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(controller, "fileSecurityValidator", fileSecurityValidator);
    }

    @Test
    void listResourceShouldUseOrphanResourceQueryForOrphanFilter() {
        BaseRequestVO request = new BaseRequestVO();
        request.setCurrent(1);
        request.setSize(10);
        request.setResourceType(CommonConst.PATH_TYPE_ORPHAN_RESOURCE);

        Resource orphan = new Resource();
        orphan.setId(12);
        orphan.setPath("/static/assets/orphan.png");

        Page<Resource> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of(orphan));
        mapperPage.setTotal(1);
        when(resourceMapper.selectOrphanResources(ArgumentMatchers.<Page<Resource>>any(),
                ArgumentMatchers.anyList(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())).thenReturn(mapperPage);

        PoetryResult<Page> result = controller.listResource(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords()).containsExactly(orphan);
        assertThat(result.getData().getTotal()).isEqualTo(1);
        verify(resourceMapper).selectOrphanResources(ArgumentMatchers.<Page<Resource>>any(),
                ArgumentMatchers.eq(List.of(CommonConst.PATH_TYPE_ASSETS)), ArgumentMatchers.eq("create_time"), ArgumentMatchers.eq(false));
        verifyNoInteractions(resourceService);
        verifyNoInteractions(resourceAvailabilityService);
    }

    @Test
    void listResourceShouldUseAvailabilityQueryForInvalidFilter() {
        BaseRequestVO request = new BaseRequestVO();
        request.setCurrent(1);
        request.setSize(10);
        request.setResourceType(CommonConst.PATH_TYPE_INVALID_RESOURCE);

        Resource invalid = new Resource();
        invalid.setId(15);
        invalid.setPath("/static/assets/missing.png");

        Page<Resource> availabilityPage = new Page<>(1, 10);
        availabilityPage.setRecords(List.of(invalid));
        availabilityPage.setTotal(1);
        when(resourceAvailabilityService.listInvalidResources(ArgumentMatchers.<Page<Resource>>any(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())).thenReturn(availabilityPage);

        PoetryResult<Page> result = controller.listResource(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords()).containsExactly(invalid);
        assertThat(result.getData().getTotal()).isEqualTo(1);
        verify(resourceAvailabilityService).listInvalidResources(ArgumentMatchers.<Page<Resource>>any(),
                ArgumentMatchers.eq("createTime"), ArgumentMatchers.eq(false));
        verifyNoInteractions(resourceService);
        verifyNoInteractions(resourceMapper);
    }

    @Test
    void saveResourceShouldRejectOrphanResourceType() {
        Resource resource = new Resource();
        resource.setType(CommonConst.PATH_TYPE_ORPHAN_RESOURCE);
        resource.setPath("/static/assets/orphan.png");

        PoetryResult<?> result = controller.saveResource(resource);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("孤儿资源是筛选视图");
        verify(resourceService, never()).save(any(Resource.class));
    }

    @Test
    void saveResourceShouldRejectInvalidResourceType() {
        Resource resource = new Resource();
        resource.setType(CommonConst.PATH_TYPE_INVALID_RESOURCE);
        resource.setPath("/static/assets/missing.png");

        PoetryResult<?> result = controller.saveResource(resource);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("无效资源是筛选视图");
        verify(resourceService, never()).save(any(Resource.class));
    }

    @Test
    void uploadShouldRejectInvalidResourceType() {
        MockMultipartFile file = new MockMultipartFile("file", "missing.png", "image/png", new byte[]{1});
        FileVO fileVO = new FileVO();
        fileVO.setType(CommonConst.PATH_TYPE_INVALID_RESOURCE);
        fileVO.setRelativePath("assets/missing.png");

        PoetryResult<String> result = controller.upload(file, fileVO);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("无效资源是筛选视图");
        verifyNoInteractions(fileSecurityValidator);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void uploadImageWithCompressShouldRejectInvalidResourceType() {
        MockMultipartFile file = new MockMultipartFile("file", "missing.png", "image/png", new byte[]{1});
        FileVO fileVO = new FileVO();
        fileVO.setType(CommonConst.PATH_TYPE_INVALID_RESOURCE);
        fileVO.setRelativePath("assets/missing.png");

        PoetryResult<Object> result = controller.uploadImageWithCompress(file, fileVO, 1920, 1080, 0.85F, 512000L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("无效资源是筛选视图");
        verifyNoInteractions(fileSecurityValidator);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void uploadWaifuPreviewShouldRejectSvgThroughSecurityValidator() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "preview.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes());
        when(fileSecurityValidator.validateFile(file, "preview.svg", "image/svg+xml"))
                .thenReturn(FileSecurityValidator.ValidationResult.fail("不支持的文件类型: SVG图片存在脚本执行风险"));

        PoetryResult<String> result = controller.uploadWaifuPreview(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("图片安全校验失败").contains("SVG");
        verify(fileSecurityValidator).validateFile(file, "preview.svg", "image/svg+xml");
        verifyNoInteractions(fileStorageService);
    }
}
