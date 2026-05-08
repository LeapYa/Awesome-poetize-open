package com.ld.poetry.service;

import com.ld.poetry.entity.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceThumbnailServiceTest {

    @TempDir
    private Path tempDir;

    @Mock
    private ResourceService resourceService;

    private ResourceAvailabilityService resourceAvailabilityService;

    private ResourceThumbnailService service;

    @BeforeEach
    void setUp() {
        resourceAvailabilityService = new ResourceAvailabilityService();
        ReflectionTestUtils.setField(resourceAvailabilityService, "localUploadUrl", tempDir.resolve("uploads").toString() + "/");
        ReflectionTestUtils.setField(resourceAvailabilityService, "localDownloadUrl", "/static/");
        ReflectionTestUtils.setField(resourceAvailabilityService, "staticResourceRoots", "");

        service = new ResourceThumbnailService();
        ReflectionTestUtils.setField(service, "resourceService", resourceService);
        ReflectionTestUtils.setField(service, "resourceAvailabilityService", resourceAvailabilityService);
    }

    @Test
    void createThumbnailShouldReturnResizedImageForLocalResource() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/existing.png");
        byte[] imageBytes = imageBytes("png", 320, 180, Color.BLUE);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, imageBytes);

        Resource resource = resource(1, "/static/assets/existing.png", "local", "image/png");
        resource.setResourceHash("hash-one");
        resource.setSize(imageBytes.length);
        when(resourceService.getById(1)).thenReturn(resource);

        ResourceThumbnailService.Thumbnail thumbnail = service.createThumbnail(1, 120, 104);

        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(thumbnail.getBytes()));
        assertThat(thumbnail.getContentType()).isIn("image/jpeg", "image/png");
        assertThat(thumbnail.getETag()).contains("hash-one");
        assertThat(rendered.getWidth()).isEqualTo(120);
        assertThat(rendered.getHeight()).isEqualTo(104);
    }

    @Test
    void createThumbnailShouldResolveRootPublicResourceFromStaticRoots() throws Exception {
        Path publicRoot = tempDir.resolve("poetize-web/public");
        Path targetFile = publicRoot.resolve("poetize.jpg");
        byte[] imageBytes = imageBytes("jpg", 200, 240, Color.RED);
        Files.createDirectories(publicRoot);
        Files.write(targetFile, imageBytes);
        ReflectionTestUtils.setField(resourceAvailabilityService, "staticResourceRoots", publicRoot.toString());

        Resource resource = resource(2, "/poetize.jpg", "local", "image/jpeg");
        resource.setResourceHash("hash-public");
        resource.setSize(imageBytes.length);
        when(resourceService.getById(2)).thenReturn(resource);

        ResourceThumbnailService.Thumbnail thumbnail = service.createThumbnail(2, 80, 80);

        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(thumbnail.getBytes()));
        assertThat(rendered.getWidth()).isEqualTo(80);
        assertThat(rendered.getHeight()).isEqualTo(80);
        assertThat(thumbnail.getETag()).contains("hash-public");
    }

    @Test
    void createThumbnailShouldRejectRemoteResource() {
        Resource resource = resource(3, "https://cdn.example.com/a.png", "local", "image/png");
        when(resourceService.getById(3)).thenReturn(resource);

        assertThatThrownBy(() -> service.createThumbnail(3, 120, 104))
                .isInstanceOf(ResourceThumbnailService.ThumbnailException.class)
                .extracting("statusCode")
                .isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void createThumbnailShouldRejectNonLocalStoreType() {
        Resource resource = resource(4, "/static/assets/a.png", "qiniu", "image/png");
        when(resourceService.getById(4)).thenReturn(resource);

        assertThatThrownBy(() -> service.createThumbnail(4, 120, 104))
                .isInstanceOf(ResourceThumbnailService.ThumbnailException.class)
                .extracting("statusCode")
                .isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void createThumbnailShouldRejectNonImageResource() {
        Resource resource = resource(5, "/static/articleFile/readme.txt", "local", "text/plain");
        when(resourceService.getById(5)).thenReturn(resource);

        assertThatThrownBy(() -> service.createThumbnail(5, 120, 104))
                .isInstanceOf(ResourceThumbnailService.ThumbnailException.class)
                .extracting("statusCode")
                .isEqualTo(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void createThumbnailETagShouldChangeWhenResourceHashChanges() throws Exception {
        Path targetFile = tempDir.resolve("uploads/assets/replaced.png");
        byte[] imageBytes = imageBytes("png", 160, 160, Color.GREEN);
        Files.createDirectories(targetFile.getParent());
        Files.write(targetFile, imageBytes);

        Resource beforeReplace = resource(6, "/static/assets/replaced.png", "local", "image/png");
        beforeReplace.setResourceHash("hash-before");
        beforeReplace.setSize(imageBytes.length);
        Resource afterReplace = resource(6, "/static/assets/replaced.png", "local", "image/png");
        afterReplace.setResourceHash("hash-after");
        afterReplace.setSize(imageBytes.length);
        when(resourceService.getById(6)).thenReturn(beforeReplace, afterReplace);

        ResourceThumbnailService.Thumbnail before = service.createThumbnail(6, 120, 104);
        ResourceThumbnailService.Thumbnail after = service.createThumbnail(6, 120, 104);

        assertThat(before.getETag()).contains("hash-before");
        assertThat(after.getETag()).contains("hash-after");
        assertThat(before.getETag()).isNotEqualTo(after.getETag());
    }

    private Resource resource(Integer id, String path, String storeType, String mimeType) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setPath(path);
        resource.setStoreType(storeType);
        resource.setMimeType(mimeType);
        resource.setOriginalName(path);
        return resource;
    }

    private byte[] imageBytes(String format, int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}
