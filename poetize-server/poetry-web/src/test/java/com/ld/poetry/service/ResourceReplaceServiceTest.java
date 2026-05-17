package com.ld.poetry.service;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.utils.security.FileSecurityValidator;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceReplaceServiceTest {

    @TempDir
    private Path tempDir;

    @Mock
    private ResourceService resourceService;

    @Mock
    private FileSecurityValidator fileSecurityValidator;

    private ResourceReplaceService service;

    @BeforeEach
    void setUp() {
        service = new ResourceReplaceService();
        ReflectionTestUtils.setField(service, "resourceService", resourceService);
        ReflectionTestUtils.setField(service, "fileSecurityValidator", fileSecurityValidator);
        ReflectionTestUtils.setField(service, "localUploadUrl", tempDir.resolve("uploads").toString() + "/");
        ReflectionTestUtils.setField(service, "localDownloadUrl", "/static/");
        ReflectionTestUtils.setField(service, "staticResourceRoots", "");
        ReflectionTestUtils.setField(service, "autoStaticRootDiscovery", false);
    }

    @Test
    void replaceResourceShouldOverwriteLocalUploadFileAndUpdateMetadata() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/existing.png");
        byte[] oldBytes = imageBytes("png", 1, 1);
        byte[] newBytes = imageBytes("png", 2, 3);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(1, "/static/assets/existing.png", "local");
        when(resourceService.getById(1)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq("existing.png"), eq("image/png")))
                .thenReturn(FileSecurityValidator.ValidationResult.success("png"));
        when(resourceService.updateById(any(Resource.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "existing.png", "image/png", newBytes);
        PoetryResult<Resource> result = service.replaceResource(1, "/static/assets/existing.png", file);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(newBytes);
        assertThat(result.getData().getPath()).isEqualTo("/static/assets/existing.png");
        assertThat(result.getData().getSize()).isEqualTo(newBytes.length);
        assertThat(result.getData().getWidth()).isEqualTo(2);
        assertThat(result.getData().getHeight()).isEqualTo(3);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceService).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1);
        assertThat(captor.getValue().getPath()).isNull();
        assertThat(captor.getValue().getOriginalName()).isEqualTo("existing.png");
        assertThat(captor.getValue().getMimeType()).isEqualTo("image/png");
        assertThat(captor.getValue().getResourceHash()).hasSize(64);
        assertThat(captor.getValue().getWidth()).isEqualTo(2);
        assertThat(captor.getValue().getHeight()).isEqualTo(3);
    }

    @Test
    void replaceResourceShouldSyncPublicStaticResourceAcrossConfiguredStaticRoots() throws Exception {
        Path webStaticRoot = tempDir.resolve("poetize-web/public/static");
        Path adminStaticRoot = tempDir.resolve("poetize-admin/public/static");
        Path webFile = webStaticRoot.resolve("assets/poetize.jpg");
        Path adminFile = adminStaticRoot.resolve("assets/poetize.jpg");
        byte[] oldBytes = imageBytes("jpg", 1, 1);
        byte[] newBytes = imageBytes("jpg", 4, 5);
        Files.createDirectories(webFile.getParent());
        Files.createDirectories(adminFile.getParent());
        Files.write(webFile, oldBytes);
        Files.write(adminFile, oldBytes);
        ReflectionTestUtils.setField(service, "staticResourceRoots", webStaticRoot + "," + adminStaticRoot);

        Resource resource = resource(2, "/static/assets/poetize.jpg", "local");
        when(resourceService.getById(2)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq("poetize.jpeg"), eq("image/jpeg")))
                .thenReturn(FileSecurityValidator.ValidationResult.success("jpeg"));
        when(resourceService.updateById(any(Resource.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "poetize.jpeg", "image/jpeg", newBytes);
        PoetryResult<Resource> result = service.replaceResource(2, "/static/assets/poetize.jpg", file);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readAllBytes(webFile)).isEqualTo(newBytes);
        assertThat(Files.readAllBytes(adminFile)).isEqualTo(newBytes);
        assertThat(result.getData().getPath()).isEqualTo("/static/assets/poetize.jpg");
        assertThat(result.getData().getWidth()).isEqualTo(4);
        assertThat(result.getData().getHeight()).isEqualTo(5);
    }

    @Test
    void replaceImageResourceShouldConvertJpegUploadToPng() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/existing.png");
        byte[] oldBytes = imageBytes("png", 1, 1);
        byte[] uploadedJpeg = imageBytes("jpg", 2, 3);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(3, "/static/assets/existing.png", "local");
        when(resourceService.getById(3)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq("uploaded.jpg"), eq("image/jpeg")))
                .thenReturn(FileSecurityValidator.ValidationResult.success("jpg"));
        when(resourceService.updateById(any(Resource.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "uploaded.jpg", "image/jpeg", uploadedJpeg);
        PoetryResult<Resource> result = service.replaceResource(3, "/static/assets/existing.png", file);

        assertThat(result.isSuccess()).isTrue();
        byte[] replacedBytes = Files.readAllBytes(targetFile);
        assertThat(replacedBytes).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        BufferedImage replacedImage = ImageIO.read(new java.io.ByteArrayInputStream(replacedBytes));
        assertThat(replacedImage.getWidth()).isEqualTo(2);
        assertThat(replacedImage.getHeight()).isEqualTo(3);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceService).updateById(captor.capture());
        assertThat(captor.getValue().getOriginalName()).isEqualTo("existing.png");
        assertThat(captor.getValue().getMimeType()).isEqualTo("image/png");
    }

    @Test
    void replaceImageResourceShouldConvertTransparentPngToWhiteBackedJpeg() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/existing.jpg");
        byte[] oldBytes = imageBytes("jpg", 1, 1);
        byte[] transparentPng = transparentImageBytes("png", 2, 2);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(30, "/static/assets/existing.jpg", "local");
        when(resourceService.getById(30)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq("transparent.png"), eq("image/png")))
                .thenReturn(FileSecurityValidator.ValidationResult.success("png"));
        when(resourceService.updateById(any(Resource.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "transparent.png", "image/png", transparentPng);
        PoetryResult<Resource> result = service.replaceResource(30, "/static/assets/existing.jpg", file);

        assertThat(result.isSuccess()).isTrue();
        byte[] replacedBytes = Files.readAllBytes(targetFile);
        assertThat(replacedBytes[0]).isEqualTo((byte) 0xFF);
        assertThat(replacedBytes[1]).isEqualTo((byte) 0xD8);
        BufferedImage replacedImage = ImageIO.read(new java.io.ByteArrayInputStream(replacedBytes));
        Color pixel = new Color(replacedImage.getRGB(0, 0));
        assertThat(pixel.getRed()).isGreaterThan(240);
        assertThat(pixel.getGreen()).isGreaterThan(240);
        assertThat(pixel.getBlue()).isGreaterThan(240);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceService).updateById(captor.capture());
        assertThat(captor.getValue().getOriginalName()).isEqualTo("existing.jpg");
        assertThat(captor.getValue().getMimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void replaceWoff2ResourceShouldConvertTtfUploadToWoff2() throws Exception {
        ResourceReplaceService spyService = spy(service);
        Path targetFile = tempDir.resolve("uploads/assets/font.woff2");
        byte[] oldBytes = "old-woff2".getBytes(StandardCharsets.UTF_8);
        byte[] uploadedTtf = new byte[]{0, 1, 2, 3};
        byte[] convertedWoff2 = "converted-woff2".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(34, "/static/assets/font.woff2", "local");
        when(resourceService.getById(34)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq("source.ttf"), eq("font/ttf")))
                .thenReturn(FileSecurityValidator.ValidationResult.success("ttf"));
        doReturn(convertedWoff2).when(spyService).encodeWoff2Bytes(any());
        when(resourceService.updateById(any(Resource.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "source.ttf", "font/ttf", uploadedTtf);
        PoetryResult<Resource> result = spyService.replaceResource(34, "/static/assets/font.woff2", file);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(convertedWoff2);
        assertThat(result.getData().getPath()).isEqualTo("/static/assets/font.woff2");
        assertThat(result.getData().getSize()).isEqualTo(convertedWoff2.length);
        assertThat(result.getData().getOriginalName()).isEqualTo("font.woff2");
        assertThat(result.getData().getMimeType()).isEqualTo("font/woff2");
        assertThat(result.getData().getResourceHash()).isEqualTo(DigestUtils.sha256Hex(convertedWoff2));

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceService).updateById(captor.capture());
        assertThat(captor.getValue().getOriginalName()).isEqualTo("font.woff2");
        assertThat(captor.getValue().getMimeType()).isEqualTo("font/woff2");
        assertThat(captor.getValue().getSize()).isEqualTo(convertedWoff2.length);
        assertThat(captor.getValue().getResourceHash()).isEqualTo(DigestUtils.sha256Hex(convertedWoff2));
    }

    @Test
    void replaceWoff2ResourceShouldRejectVideoAndGifUploadsWithoutTouchingFile() throws Exception {
        assertWoff2UploadRejected(35, "clip.mp4", "video/mp4");
        assertWoff2UploadRejected(36, "anim.gif", "image/gif");

        verify(resourceService, never()).updateById(any(Resource.class));
        verifyNoInteractions(fileSecurityValidator);
    }

    @Test
    void replaceMediaConversionShouldFailWhenConverterUnavailableWithoutTouchingFile() throws Exception {
        ReflectionTestUtils.setField(service, "mediaConverterPath", "__poetize_missing_ffmpeg__");
        assertMediaConversionUnavailable(37, "clip.mp4", "anim.gif", "image/gif");
        assertMediaConversionUnavailable(38, "anim.gif", "clip.mp4", "video/mp4");

        verify(resourceService, never()).updateById(any(Resource.class));
    }

    @Test
    void replaceMediaResourceShouldConvertWhenConverterAvailable() throws Exception {
        ResourceReplaceService spyService = spy(service);
        Path targetFile = tempDir.resolve("uploads/assets/clip.mp4");
        byte[] oldBytes = "old-mp4".getBytes(StandardCharsets.UTF_8);
        byte[] convertedMp4 = "converted-mp4".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(39, "/static/assets/clip.mp4", "local");
        when(resourceService.getById(39)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq("anim.gif"), eq("image/gif")))
                .thenReturn(FileSecurityValidator.ValidationResult.success("gif"));
        doReturn(convertedMp4).when(spyService).convertMediaBytes(any(), eq("mp4"));
        when(resourceService.updateById(any(Resource.class))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "anim.gif", "image/gif", "gif".getBytes(StandardCharsets.UTF_8));
        PoetryResult<Resource> result = spyService.replaceResource(39, "/static/assets/clip.mp4", file);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(convertedMp4);
        assertThat(result.getData().getOriginalName()).isEqualTo("clip.mp4");
        assertThat(result.getData().getMimeType()).isEqualTo("video/mp4");
        assertThat(result.getData().getSize()).isEqualTo(convertedMp4.length);
        assertThat(result.getData().getResourceHash()).isEqualTo(DigestUtils.sha256Hex(convertedMp4));

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceService).updateById(captor.capture());
        assertThat(captor.getValue().getOriginalName()).isEqualTo("clip.mp4");
        assertThat(captor.getValue().getMimeType()).isEqualTo("video/mp4");
        assertThat(captor.getValue().getSize()).isEqualTo(convertedMp4.length);
        assertThat(captor.getValue().getResourceHash()).isEqualTo(DigestUtils.sha256Hex(convertedMp4));
    }

    @Test
    void replaceMediaResourceShouldRollbackConvertedBytesWhenMetadataUpdateFails() throws Exception {
        ResourceReplaceService spyService = spy(service);
        Path targetFile = tempDir.resolve("uploads/assets/clip.mp4");
        byte[] oldBytes = "old-mp4".getBytes(StandardCharsets.UTF_8);
        byte[] convertedMp4 = "converted-mp4".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(40, "/static/assets/clip.mp4", "local");
        when(resourceService.getById(40)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq("anim.gif"), eq("image/gif")))
                .thenReturn(FileSecurityValidator.ValidationResult.success("gif"));
        doReturn(convertedMp4).when(spyService).convertMediaBytes(any(), eq("mp4"));
        when(resourceService.updateById(any(Resource.class))).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "anim.gif", "image/gif", "gif".getBytes(StandardCharsets.UTF_8));
        PoetryResult<Resource> result = spyService.replaceResource(40, "/static/assets/clip.mp4", file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("资源元数据更新失败");
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(oldBytes);
    }

    @Test
    void replaceImageResourceShouldRejectFontUploadWithoutTouchingFile() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/existing.jpg");
        byte[] oldBytes = imageBytes("jpg", 1, 1);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(31, "/static/assets/existing.jpg", "local");
        when(resourceService.getById(31)).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", "font.ttf", "font/ttf", new byte[]{0, 1, 2, 3});
        PoetryResult<Resource> result = service.replaceResource(31, "/static/assets/existing.jpg", file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("图片资源只能使用图片文件");
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(oldBytes);
        verify(resourceService, never()).updateById(any(Resource.class));
        verifyNoInteractions(fileSecurityValidator);
    }

    @Test
    void replaceImageResourceShouldRejectVideoUploadWithoutTouchingFile() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/existing.jpg");
        byte[] oldBytes = imageBytes("jpg", 1, 1);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(32, "/static/assets/existing.jpg", "local");
        when(resourceService.getById(32)).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{0, 0, 0, 0});
        PoetryResult<Resource> result = service.replaceResource(32, "/static/assets/existing.jpg", file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("图片资源只能使用图片文件");
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(oldBytes);
        verify(resourceService, never()).updateById(any(Resource.class));
        verifyNoInteractions(fileSecurityValidator);
    }

    @Test
    void replaceNonImageResourceShouldRejectExtensionMismatchWithoutTouchingFile() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/existing.pdf");
        byte[] oldBytes = "%PDF-old".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        Resource resource = resource(33, "/static/assets/existing.pdf", "local");
        when(resourceService.getById(33)).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", "existing.jpg", "image/jpeg", imageBytes("jpg", 2, 2));
        PoetryResult<Resource> result = service.replaceResource(33, "/static/assets/existing.pdf", file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("扩展名");
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(oldBytes);
        verify(resourceService, never()).updateById(any(Resource.class));
        verifyNoInteractions(fileSecurityValidator);
    }

    @Test
    void replaceResourceShouldRejectUnsupportedStoreType() throws Exception {
        Resource resource = resource(4, "https://cdn.example.com/assets/existing.png", "qiniu");
        when(resourceService.getById(4)).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", "existing.png", "image/png", imageBytes("png", 1, 1));
        PoetryResult<Resource> result = service.replaceResource(4, "https://cdn.example.com/assets/existing.png", file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("存储平台");
        verify(resourceService, never()).updateById(any(Resource.class));
        verifyNoInteractions(fileSecurityValidator);
    }

    @Test
    void replaceResourceShouldRejectRemoteLocalPath() throws Exception {
        Resource resource = resource(5, "https://cdn.example.com/assets/existing.png", "local");
        when(resourceService.getById(5)).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", "existing.png", "image/png", imageBytes("png", 1, 1));
        PoetryResult<Resource> result = service.replaceResource(5, "https://cdn.example.com/assets/existing.png", file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("路径不支持");
        verify(resourceService, never()).updateById(any(Resource.class));
        verifyNoInteractions(fileSecurityValidator);
    }

    @Test
    void replaceResourceShouldRejectStaleExpectedPath() throws Exception {
        Resource resource = resource(6, "/static/assets/current.png", "local");
        when(resourceService.getById(6)).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", "current.png", "image/png", imageBytes("png", 1, 1));
        PoetryResult<Resource> result = service.replaceResource(6, "/static/assets/old.png", file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("路径已变化");
        verify(resourceService, never()).updateById(any(Resource.class));
        verifyNoInteractions(fileSecurityValidator);
    }

    private Resource resource(Integer id, String path, String storeType) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setPath(path);
        resource.setStoreType(storeType);
        resource.setOriginalName(path);
        return resource;
    }

    private void assertWoff2UploadRejected(Integer id, String uploadedName, String contentType) throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/font-" + id + ".woff2");
        byte[] oldBytes = ("old-woff2-" + id).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        String resourcePath = "/static/assets/font-" + id + ".woff2";
        Resource resource = resource(id, resourcePath, "local");
        when(resourceService.getById(id)).thenReturn(resource);

        MockMultipartFile file = new MockMultipartFile("file", uploadedName, contentType, new byte[]{0, 1, 2, 3});
        PoetryResult<Resource> result = service.replaceResource(id, resourcePath, file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("WOFF2 资源");
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(oldBytes);
    }

    private void assertMediaConversionUnavailable(Integer id,
                                                  String targetName,
                                                  String uploadedName,
                                                  String contentType) throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/" + targetName);
        byte[] oldBytes = ("old-media-" + id).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, oldBytes);

        String resourcePath = "/static/assets/" + targetName;
        Resource resource = resource(id, resourcePath, "local");
        when(resourceService.getById(id)).thenReturn(resource);
        when(fileSecurityValidator.validateFile(any(), eq(uploadedName), eq(contentType)))
                .thenReturn(FileSecurityValidator.ValidationResult.success(this.getPathExtension(uploadedName)));

        MockMultipartFile file = new MockMultipartFile("file", uploadedName, contentType, new byte[]{0, 1, 2, 3});
        PoetryResult<Resource> result = service.replaceResource(id, resourcePath, file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("媒体转换器不可用");
        assertThat(Files.readAllBytes(targetFile)).isEqualTo(oldBytes);
    }

    private String getPathExtension(String path) {
        int dotIndex = path.lastIndexOf('.');
        return dotIndex >= 0 ? path.substring(dotIndex + 1) : "";
    }

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }

    private byte[] transparentImageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, new Color(255, 0, 0, 0).getRGB());
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}
