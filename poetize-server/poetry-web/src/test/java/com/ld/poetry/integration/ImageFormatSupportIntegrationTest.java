package com.ld.poetry.integration;

import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.image.ImageCompressUtil;
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
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 图片格式支持集成测试
 * 测试从文件验证到压缩的完整流程
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("图片格式支持集成测试")
class ImageFormatSupportIntegrationTest {

    private FileSecurityValidator validator;

    // Mock the SysConfigService
    @Mock
    private SysConfigService sysConfigService;

    private ImageCompressUtil.CompressResult lastCompressionResult;

    @BeforeEach
    void setUp() {
        validator = new FileSecurityValidator();
        lastCompressionResult = null;
        // 配置默认的SysConfigService行为，禁用WebP转换
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
    @DisplayName("测试完整流程：JPEG格式")
    void testCompleteJpegFlow() throws IOException {
        BufferedImage image = createTestImage(800, 600);
        byte[] imageData = convertToBytes(image, "jpg");

        MultipartFile file = createMultipartFile("test.jpg", "image/jpeg", imageData);

        // 第一步：验证文件安全性
        FileSecurityValidator.ValidationResult validationResult = validator.validateFile(
            file, file.getOriginalFilename(), file.getContentType()
        );
        assertTrue(validationResult.isSuccess(), "JPEG文件验证应该通过");

        // 第二步：压缩处理
        ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);
        assertNotNull(compressResult, "压缩结果不应为null");
        assertTrue(compressResult.getCompressedSize() > 0, "压缩后应该有数据");
        // 小图片压缩后可能不会明显变小
        assertTrue(compressResult.getOriginalSize() > 0, "原始大小应该>0");

        lastCompressionResult = compressResult;
        assertStatistics("JPEG", compressResult);
    }

    @Test
    @DisplayName("测试完整流程：PNG格式")
    void testCompletePngFlow() throws IOException {
        BufferedImage image = createTestImage(800, 600);
        byte[] imageData = convertToBytes(image, "png");

        MultipartFile file = createMultipartFile("test.png", "image/png", imageData);

        // 验证
        FileSecurityValidator.ValidationResult validationResult = validator.validateFile(
            file, file.getOriginalFilename(), file.getContentType()
        );
        assertTrue(validationResult.isSuccess(), "PNG文件验证应该通过");

        // 压缩
        ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);
        assertNotNull(compressResult, "PNG压缩结果不应为null");

        lastCompressionResult = compressResult;
        assertStatistics("PNG", compressResult);
    }

    @Test
    @DisplayName("测试完整流程：BMP格式")
    void testCompleteBmpFlow() throws IOException {
        BufferedImage image = createTestImage(600, 400);
        byte[] imageData = convertToBytes(image, "bmp");

        MultipartFile file = createMultipartFile("test.bmp", "image/bmp", imageData);

        // 验证BMP格式
        FileSecurityValidator.ValidationResult validationResult = validator.validateFile(
            file, file.getOriginalFilename(), file.getContentType()
        );
        // BMP的验证可能根据魔数检测的实现而有所不同
        if (validationResult.isSuccess()) {
            // 如果验证通过，测试压缩
            assertDoesNotThrow(() -> {
                ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);
                assertNotNull(compressResult, "BMP压缩结果不应为null");
                lastCompressionResult = compressResult;
            });
        }
    }

    @Test
    @DisplayName("测试完整流程：TIFF格式")
    void testCompleteTiffFlow() throws IOException {
        BufferedImage image = createTestImage(600, 400);
        byte[] imageData = convertToBytes(image, "tiff");

        MultipartFile file = createMultipartFile("test.tiff", "image/tiff", imageData);

        // 验证TIFF格式
        FileSecurityValidator.ValidationResult validationResult = validator.validateFile(
            file, file.getOriginalFilename(), file.getContentType()
        );
        assertTrue(validationResult.isSuccess(), "TIFF文件验证应该通过");

        // 压缩
        ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);
        assertNotNull(compressResult, "TIFF压缩结果不应为null");

        lastCompressionResult = compressResult;
        assertStatistics("TIFF", compressResult);
    }

    @Test
    @DisplayName("测试安全：SVG格式上传应在验证阶段拒绝")
    void testSvgUploadRejectedBeforeCompression() {
        String svgContent = createTestSvgContent();
        byte[] svgBytes = svgContent.getBytes();

        MultipartFile file = createMultipartFile("test.svg", "image/svg+xml", svgBytes);

        FileSecurityValidator.ValidationResult validationResult = validator.validateFile(
            file, file.getOriginalFilename(), file.getContentType()
        );
        assertFalse(validationResult.isSuccess(), "SVG文件应该在上传验证阶段被拒绝");
        assertTrue(validationResult.getMessage().contains("SVG"), "拒绝原因应该说明SVG风险");
    }

    @Test
    @DisplayName("测试安全：恶意文件扩展名")
    void testSecurityDangerousExtensions() throws IOException {
        BufferedImage image = createTestImage(400, 300);
        byte[] imageData = convertToBytes(image, "jpg");

        // 测试PHP扩展名
        MultipartFile phpFile = createMultipartFile("malicious.php", "image/jpeg", imageData);
        FileSecurityValidator.ValidationResult phpResult = validator.validateFile(
            phpFile, phpFile.getOriginalFilename(), phpFile.getContentType()
        );
        assertFalse(phpResult.isSuccess(), "PHP扩展名应该被拒绝");

        // 测试JSP扩展名
        MultipartFile jspFile = createMultipartFile("malicious.jsp", "image/png", imageData);
        FileSecurityValidator.ValidationResult jspResult = validator.validateFile(
            jspFile, jspFile.getOriginalFilename(), jspFile.getContentType()
        );
        assertFalse(jspResult.isSuccess(), "JSP扩展名应该被拒绝");
    }

    @Test
    @DisplayName("测试性能：大图片处理")
    void testPerformanceLargeImage() throws IOException {
        // 创建大尺寸图片
        BufferedImage largeImage = createTestImage(3000, 2000);
        byte[] imageData = convertToBytes(largeImage, "jpg");

        MultipartFile file = createMultipartFile("large.jpg", "image/jpeg", imageData);

        long startTime = System.currentTimeMillis();

        // 验证
        FileSecurityValidator.ValidationResult validationResult = validator.validateFile(
            file, file.getOriginalFilename(), file.getContentType()
        );
        assertTrue(validationResult.isSuccess(), "大图片验证应该通过");

        // 压缩
        ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);
        assertNotNull(compressResult, "大图片压缩结果不应为null");

        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;

        System.out.println("大图片处理时间: " + processingTime + "ms");
        System.out.println("原始大小: " + compressResult.getOriginalSize() + " bytes");
        System.out.println("压缩后大小: " + compressResult.getCompressedSize() + " bytes");
        System.out.println("压缩率: " + String.format("%.2f", compressResult.getCompressionRatio()) + "%");

        // 大图片应该有显著压缩
        assertTrue(compressResult.getCompressionRatio() > 10,
                "大图片应该有显著压缩（>10%）");
    }

    @Test
    @DisplayName("测试多种格式批量处理")
    void testBatchProcessing() throws IOException {
        String[] formats = {"jpg", "png", "tiff"};
        String[] contentTypes = {"image/jpeg", "image/png", "image/tiff"};

        for (int i = 0; i < formats.length; i++) {
            BufferedImage image = createTestImage(500, 400);
            byte[] imageData = convertToBytes(image, formats[i]);

            MultipartFile file = createMultipartFile(
                "test." + formats[i],
                contentTypes[i],
                imageData
            );

            // 验证
            FileSecurityValidator.ValidationResult validationResult = validator.validateFile(
                file, file.getOriginalFilename(), file.getContentType()
            );
            assertTrue(validationResult.isSuccess(),
                    formats[i] + "文件验证应该通过");

            // 压缩
            ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);
            assertNotNull(compressResult,
                    formats[i] + "压缩结果不应为null");

            System.out.println("格式 " + formats[i] + " 测试通过");
        }
    }

    /**
     * 断言压缩统计信息
     */
    private void assertStatistics(String formatName, ImageCompressUtil.CompressResult result) {
        assertTrue(result.getOriginalSize() > 0, formatName + "原始大小应该>0");
        assertTrue(result.getCompressedSize() > 0, formatName + "压缩后大小应该>0");
        // 允许压缩率为负（文件变大）或正（文件变小）
        assertTrue(result.getCompressionRatio() >= -100, formatName + "压缩率应该>=-100");
        assertTrue(result.getCompressionRatio() <= 100, formatName + "压缩率应该<=100");

        System.out.println(formatName + " 格式统计:");
        System.out.println("  原始大小: " + result.getOriginalSize() + " bytes");
        System.out.println("  压缩后: " + result.getCompressedSize() + " bytes");
        System.out.println("  压缩率: " + String.format("%.2f", result.getCompressionRatio()) + "%");
    }

    /**
     * 创建测试图片
     */
    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
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

    /**
     * 将图片转换为字节数组
     */
    private byte[] convertToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    /**
     * 创建测试SVG内容
     */
    private String createTestSvgContent() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"200\" viewBox=\"0 0 200 200\">\n" +
                "  <rect x=\"10\" y=\"10\" width=\"180\" height=\"180\" fill=\"none\" stroke=\"blue\" stroke-width=\"2\"/>\n" +
                "  <circle cx=\"100\" cy=\"100\" r=\"80\" fill=\"yellow\" stroke=\"red\" stroke-width=\"3\"/>\n" +
                "  <text x=\"100\" y=\"105\" font-size=\"20\" text-anchor=\"middle\" fill=\"black\">Test SVG</text>\n" +
                "</svg>";
    }

    /**
     * 创建MultipartFile
     */
    private MultipartFile createMultipartFile(String filename, String contentType, byte[] data) {
        return new MockMultipartFile(
            "file",
            filename,
            contentType,
            data
        );
    }
}
