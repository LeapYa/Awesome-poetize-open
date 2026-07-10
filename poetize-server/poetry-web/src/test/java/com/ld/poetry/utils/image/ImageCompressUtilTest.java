package com.ld.poetry.utils.image;

import com.ld.poetry.service.SysConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 图片压缩工具测试
 * 测试各种图片格式的压缩功能
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("图片压缩工具测试")
class ImageCompressUtilTest {

    // Mock the SysConfigService
    @Mock
    private SysConfigService sysConfigService;

    private static final int TEST_WIDTH = 800;
    private static final int TEST_HEIGHT = 600;
    private static final float TEST_QUALITY = 0.85f;
    private static final long TEST_TARGET_SIZE = 500 * 1024; // 500KB

    @BeforeEach
    void setUp() {
        // 初始化配置
        // 默认启用压缩，但禁用WebP转换以简化测试
        when(sysConfigService.getConfigValueByKey("image.compress.enabled")).thenReturn("true");
        when(sysConfigService.getConfigValueByKey("image.compress.mode")).thenReturn("lossy");
        when(sysConfigService.getConfigValueByKey("image.webp.enabled")).thenReturn("false"); // 禁用WebP转换
        when(sysConfigService.getConfigValueByKey("image.webp.min-size")).thenReturn("50");
        when(sysConfigService.getConfigValueByKey("image.webp.min-saving-ratio")).thenReturn("10");

        // 注入模拟的SysConfigService
        ImageCompressUtil.setTestSysConfigService(sysConfigService);
    }

    @AfterEach
    void tearDown() {
        // 清除模拟的SysConfigService
        ImageCompressUtil.clearTestSysConfigService();
    }

    @Test
    @DisplayName("测试JPEG图片压缩")
    void testJpegCompression() throws IOException {
        // 创建测试用的JPEG图片
        BufferedImage testImage = createTestImage(800, 600);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "test.jpg",
            "test.jpg",
            "image/jpeg",
            imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "压缩结果不应为null");
        assertTrue(result.getData().length > 0, "压缩后的数据不应为空");
        assertEquals("image/jpeg", result.getContentType(), "Content-Type应该是image/jpeg");
        // 注意：小图片压缩后可能不会明显变小，甚至可能因头部开销而变大
        assertTrue(result.getCompressedSize() > 0, "压缩后大小应该>0");
        assertTrue(result.getOriginalSize() > 0, "原始大小应该>0");
    }

    @Test
    @DisplayName("测试PNG图片压缩")
    void testPngCompression() throws IOException {
        BufferedImage testImage = createTestImage(800, 600);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "png", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "test.png",
            "test.png",
            "image/png",
            imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "压缩结果不应为null");
        assertTrue(result.getData().length > 0, "压缩后的数据不应为空");
        assertEquals("image/png", result.getContentType(), "Content-Type应该是image/png");
    }

    @Test
    @DisplayName("测试BMP图片压缩（通过TwelveMonkeys）")
    void testBmpCompression() throws IOException {
        // BMP格式需要TwelveMonkeys支持
        // 这里创建一个小尺寸的BMP图片进行测试
        BufferedImage testImage = createTestImage(400, 300);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "bmp", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "test.bmp",
            "test.bmp",
            "image/bmp",
            imageData
        );

        // BMP可能不被ImageIO直接支持，但应该能处理
        assertDoesNotThrow(() -> {
            ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);
            assertNotNull(result, "压缩结果不应为null");
        });
    }

    @Test
    @DisplayName("测试TIFF图片压缩")
    void testTiffCompression() throws IOException {
        // TIFF格式测试
        BufferedImage testImage = createTestImage(600, 400);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "tiff", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "test.tiff",
            "test.tiff",
            "image/tiff",
            imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "压缩结果不应为null");
    }

    @Test
    @DisplayName("测试WebP格式支持（使用cwebp工具）")
    void testWebpCompression() throws IOException {
        // WebP现在通过cwebp命令行工具实现，不依赖webp-imageio库
        // 创建测试图片数据
        BufferedImage testImage = createTestImage(500, 500);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);  // 先转为JPEG作为输入
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "test.webp",
            "test.webp",
            "image/webp",
            imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "WebP压缩结果不应为null");
        // 验证结果格式应该是webp
        assertTrue(result.getContentType().contains("webp"), 
                "结果应该是WebP格式");
    }

    @Test
    @DisplayName("测试SVG格式 - 应该直接存储不压缩")
    void testSvgDirectStorage() throws IOException {
        // SVG是文本格式，包含矢量图XML
        String svgContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\">" +
                "<circle cx=\"50\" cy=\"50\" r=\"40\" stroke=\"black\" stroke-width=\"3\" fill=\"red\" />" +
                "</svg>";

        byte[] svgBytes = svgContent.getBytes();

        MockMultipartFile file = new MockMultipartFile(
            "test.svg",
            "test.svg",
            "image/svg+xml",
            svgBytes
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "SVG压缩结果不应为null");
        assertEquals("image/svg+xml", result.getContentType(), "Content-Type应该是image/svg+xml");
        assertEquals(0, result.getCompressionRatio(), "SVG压缩率应该为0（不压缩）");
        assertEquals(svgBytes.length, result.getData().length, "SVG数据长度应该保持不变");
    }

    @Test
    @DisplayName("测试自定义压缩参数")
    void testCustomCompressionParameters() throws IOException {
        BufferedImage testImage = createTestImage(1000, 800);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "test.jpg",
            "test.jpg",
            "image/jpeg",
            imageData
        );

        // 使用自定义参数压缩
        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(
            file,
            400,  // maxWidth
            300,  // maxHeight
            0.7f, // quality
            300 * 1024 // targetSize 300KB
        );

        assertNotNull(result, "自定义压缩结果不应为null");
        assertTrue(result.getCompressedSize() <= result.getOriginalSize(),
                "压缩后大小应该小于等于原始大小");
    }

    @Test
    @DisplayName("测试大图片压缩 - 检查尺寸调整")
    void testLargeImageCompression() throws IOException {
        // 创建大尺寸图片
        BufferedImage largeImage = createTestImage(4000, 3000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(largeImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "large.jpg",
            "large.jpg",
            "image/jpeg",
            imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "大图片压缩结果不应为null");
        assertTrue(result.getData().length > 0, "大图片压缩后应该有数据");
        // 大图片应该会被显著压缩
        assertTrue(result.getOriginalSize() > result.getCompressedSize(),
                "大图片压缩后应该变小");
    }

    @Test
    @DisplayName("测试无效图片文件")
    void testInvalidImageFile() {
        // 创建无效的图像数据
        byte[] invalidData = "This is not an image".getBytes();

        MockMultipartFile file = new MockMultipartFile(
            "test.jpg",
            "test.jpg",
            "image/jpeg",
            invalidData
        );

        assertThrows(IOException.class, () -> {
            ImageCompressUtil.smartCompress(file);
        }, "无效图片应该抛出IOException");
    }

    @Test
    @DisplayName("测试空文件")
    void testEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
            "test.jpg",
            "test.jpg",
            "image/jpeg",
            new byte[0]
        );

        assertThrows(IOException.class, () -> {
            ImageCompressUtil.smartCompress(file);
        }, "空文件应该抛出IOException");
    }

    @Test
    @DisplayName("测试压缩结果统计信息")
    void testCompressionStatistics() throws IOException {
        BufferedImage testImage = createTestImage(800, 600);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();
        long originalSize = imageData.length;

        MockMultipartFile file = new MockMultipartFile(
            "test.jpg",
            "test.jpg",
            "image/jpeg",
            imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "结果不应为null");
        assertTrue(result.getOriginalSize() > 0, "原始大小应该大于0");
        assertTrue(result.getCompressedSize() > 0, "压缩后大小应该大于0");
        // 压缩率可能为负（如果压缩后变大），或者为正
        assertTrue(result.getCompressionRatio() <= 100, "压缩率应该<=100");
        // 允许压缩率为负（文件变大）或正（文件变小）
        assertTrue(result.getCompressionRatio() >= -100, "压缩率应该>=-100");

        // 计算实际的压缩率
        double expectedRatio = (1.0 - (double) result.getCompressedSize() / result.getOriginalSize()) * 100;
        assertEquals(expectedRatio, result.getCompressionRatio(), 0.01, "压缩率计算应该正确");
    }

    @Test
    @DisplayName("测试纵向长图压缩 - 短边不应被压扁")
    void testVerticalLongImageCompression() throws IOException {
        // 200x2000 纵向长图，长宽比 10:1，长边 2000 < MAX_LONG_EDGE(8000)，应保留原尺寸
        BufferedImage longImage = createTestImage(200, 2000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(longImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "long_v.jpg", "long_v.jpg", "image/jpeg", imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "长图压缩结果不应为null");
        BufferedImage compressedImage = ImageIO.read(new ByteArrayInputStream(result.getData()));
        assertNotNull(compressedImage, "压缩后图片应能正常读取");
        // 旧逻辑会把短边压到 108 像素左右导致糊图；新逻辑应保留原短边
        assertEquals(200, compressedImage.getWidth(),
            "纵向长图短边（宽度）应保留原尺寸，实际: " + compressedImage.getWidth());
        assertEquals(2000, compressedImage.getHeight(),
            "纵向长图长边未超限时应保留原尺寸，实际: " + compressedImage.getHeight());
    }

    @Test
    @DisplayName("测试横向长图压缩 - 短边不应被压扁")
    void testHorizontalLongImageCompression() throws IOException {
        // 2000x200 横向长图，长宽比 10:1，长边 2000 < MAX_LONG_EDGE(8000)，应保留原尺寸
        BufferedImage longImage = createTestImage(2000, 200);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(longImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "long_h.jpg", "long_h.jpg", "image/jpeg", imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "长图压缩结果不应为null");
        BufferedImage compressedImage = ImageIO.read(new ByteArrayInputStream(result.getData()));
        assertNotNull(compressedImage, "压缩后图片应能正常读取");
        assertEquals(2000, compressedImage.getWidth(),
            "横向长图长边未超限时应保留原尺寸，实际: " + compressedImage.getWidth());
        assertEquals(200, compressedImage.getHeight(),
            "横向长图短边（高度）应保留原尺寸，实际: " + compressedImage.getHeight());
    }

    @Test
    @DisplayName("测试超长图压缩 - 按长边等比缩放，短边维持比例")
    void testUltraLongImageCompression() throws IOException {
        // 100x9000 超长纵图，长宽比 90:1，长边 9000 > MAX_LONG_EDGE(8000)，按长边等比缩放
        BufferedImage longImage = createTestImage(100, 9000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(longImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "ultra_long.jpg", "ultra_long.jpg", "image/jpeg", imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "超长图压缩结果不应为null");
        BufferedImage compressedImage = ImageIO.read(new ByteArrayInputStream(result.getData()));
        assertNotNull(compressedImage, "压缩后图片应能正常读取");
        // 长边缩到 MAX_LONG_EDGE(8000)，短边按相同比例 0.889 缩到约 88
        assertEquals(8000, compressedImage.getHeight(),
            "超长图长边应缩到 MAX_LONG_EDGE，实际: " + compressedImage.getHeight());
        assertEquals(88, compressedImage.getWidth(),
            "超长图短边应按比例缩放，实际: " + compressedImage.getWidth());
    }

    @Test
    @DisplayName("测试长宽比正好等于阈值 - 按普通图逻辑处理")
    void testBoundaryAspectRatioImage() throws IOException {
        // 3240x1080 长宽比正好 3:1，等于阈值不算长图，按普通图逻辑（maxWidth=1920）
        BufferedImage boundaryImage = createTestImage(3240, 1080);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(boundaryImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
            "boundary.jpg", "boundary.jpg", "image/jpeg", imageData
        );

        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

        assertNotNull(result, "边界图压缩结果不应为null");
        BufferedImage compressedImage = ImageIO.read(new ByteArrayInputStream(result.getData()));
        assertNotNull(compressedImage, "压缩后图片应能正常读取");
        // 普通图逻辑：scaleWidth=1920/3240=0.593, scaleHeight=1080/1080=1.0, scale=0.593
        // 新尺寸 1920x640
        assertEquals(1920, compressedImage.getWidth(),
            "边界长宽比应按普通图逻辑缩放宽度到1920，实际: " + compressedImage.getWidth());
        assertEquals(640, compressedImage.getHeight(),
            "边界长宽比应按普通图逻辑等比缩放高度，实际: " + compressedImage.getHeight());
    }

    /**
     * 创建测试用的彩色图片
     */
    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // 创建一个简单的测试图案
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / width;
                int g = (y * 255) / height;
                int b = ((x + y) * 255) / (width + height);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }
}
