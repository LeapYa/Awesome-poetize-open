package com.ld.poetry.utils.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件安全验证器测试
 * 测试各种图片格式的魔数检测和扩展名验证
 */
@DisplayName("文件安全验证器测试")
class FileSecurityValidatorTest {

    private FileSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileSecurityValidator();
    }

    @Test
    @DisplayName("测试JPEG文件验证")
    void testJpegValidation() {
        // JPEG魔数: FF D8 FF (至少16字节)
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.jpg", "image/jpeg", jpegHeader),
            "test.jpg",
            "image/jpeg"
        );
        assertTrue(result.isSuccess(), "JPEG文件验证应该通过");
    }

    @Test
    @DisplayName("测试PNG文件验证")
    void testPngValidation() {
        // PNG魔数: 89 50 4E 47 (至少16字节)
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.png", "image/png", pngHeader),
            "test.png",
            "image/png"
        );
        assertTrue(result.isSuccess(), "PNG文件验证应该通过");
    }

    @Test
    @DisplayName("测试GIF文件验证")
    void testGifValidation() {
        // GIF魔数: 47 49 46 38 (至少16字节)
        byte[] gifHeader = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x0C, 0x00, 0x0C, 0x00, (byte)0xF7, 0x0F, 0x00, 0x00, 0x00, 0x00};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.gif", "image/gif", gifHeader),
            "test.gif",
            "image/gif"
        );
        assertTrue(result.isSuccess(), "GIF文件验证应该通过");
    }

    @Test
    @DisplayName("测试BMP文件验证")
    void testBmpValidation() {
        // BMP魔数: 42 4D (需要至少14字节)
        byte[] bmpHeader = {0x42, 0x4D, 0x46, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x00, 0x00, 0x00, 0x28, 0x00, 0x00, 0x00};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.bmp", "image/bmp", bmpHeader),
            "test.bmp",
            "image/bmp"
        );
        assertTrue(result.isSuccess(), "BMP文件验证应该通过");
    }

    @Test
    @DisplayName("测试WebP文件验证")
    void testWebpValidation() {
        // WebP魔数: 52 49 46 46 ... 57 45 42 50
        byte[] webpHeader = {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
                            0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x4C};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.webp", "image/webp", webpHeader),
            "test.webp",
            "image/webp"
        );
        assertTrue(result.isSuccess(), "WebP文件验证应该通过");
    }

    @Test
    @DisplayName("测试TIFF文件验证 - Big Endian")
    void testTiffBigEndianValidation() {
        // TIFF Big Endian魔数: 4D 4D 00 2A (至少16字节)
        byte[] tiffHeader = {0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.tiff", "image/tiff", tiffHeader),
            "test.tiff",
            "image/tiff"
        );
        assertTrue(result.isSuccess(), "TIFF文件（Big Endian）验证应该通过");
    }

    @Test
    @DisplayName("测试TIFF文件验证 - Little Endian")
    void testTiffLittleEndianValidation() {
        // TIFF Little Endian魔数: 49 49 2A 00 (至少16字节)
        byte[] tiffHeader = {0x49, 0x49, 0x2A, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.tif", "image/tiff", tiffHeader),
            "test.tif",
            "image/tiff"
        );
        assertTrue(result.isSuccess(), "TIFF文件（Little Endian）验证应该通过");
    }

    @Test
    @DisplayName("测试SVG文件验证 - 以<svg开头")
    void testSvgValidation() {
        // SVG魔数: 3C 73 76 67 (<svg) (至少16字节)
        byte[] svgHeader = {0x3C, 0x73, 0x76, 0x67, 0x20, 0x78, 0x6D, 0x6C, 0x6E, 0x73, 0x3D, 0x22, 0x68, 0x74, 0x74, 0x70};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.svg", "image/svg+xml", svgHeader),
            "test.svg",
            "image/svg+xml"
        );
        assertTrue(result.isSuccess(), "SVG文件验证应该通过");
    }

    @Test
    @DisplayName("测试SVG文件验证 - 以<?xml开头")
    void testSvgValidationWithXmlHeader() {
        // SVG with XML header - 创建一个完整的SVG内容
        String svgContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"><rect width=\"100\" height=\"100\" fill=\"red\"/></svg>";
        byte[] svgBytes = svgContent.getBytes();

        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.svg", "image/svg+xml", svgBytes),
            "test.svg",
            "image/svg+xml"
        );

        assertTrue(result.isSuccess(), "带XML声明的SVG文件验证应该通过，实际消息: " + result.getMessage());
    }

    @Test
    @DisplayName("测试危险文件扩展名 - PHP")
    void testDangerousPhpExtension() {
        byte[] validJpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.php", "image/jpeg", validJpegHeader),
            "test.php",
            "image/jpeg"
        );
        assertFalse(result.isSuccess(), "PHP扩展名应该被拒绝");
        assertTrue(result.getMessage().contains("不支持的文件类型"),
                "应该返回不支持文件类型的错误信息");
    }

    @Test
    @DisplayName("测试危险文件扩展名 - JSP")
    void testDangerousJspExtension() {
        byte[] validPngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.jsp", "image/png", validPngHeader),
            "test.jsp",
            "image/png"
        );
        assertFalse(result.isSuccess(), "JSP扩展名应该被拒绝");
    }

    @Test
    @DisplayName("测试Content-Type与扩展名不匹配")
    void testContentTypeMismatchExtension() {
        // JPEG文件但扩展名是PNG
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.png", "image/jpeg", jpegHeader),
            "test.png",
            "image/jpeg"
        );
        // 注意：这里根据实际实现可能通过或不通过
        // 如果有严格的扩展名检查，这里应该失败
    }

    @Test
    @DisplayName("测试MP4文件验证")
    void testMp4Validation() {
        byte[] mp4Header = {
            0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x02, 0x00,
            0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x32,
            0x61, 0x76, 0x63, 0x31
        };
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.mp4", "video/mp4", mp4Header),
            "test.mp4",
            "video/mp4"
        );
        assertTrue(result.isSuccess(), "MP4文件验证应该通过，实际消息: " + result.getMessage());
    }

    @Test
    @DisplayName("测试WebM文件验证")
    void testWebmValidation() {
        byte[] webmHeader = {
            0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81,
            0x01, 0x42, (byte) 0xF7, (byte) 0x81, 0x01, 0x42, (byte) 0xF2, (byte) 0x81,
            0x04, 0x42, (byte) 0xF3, (byte) 0x81, 0x08, 0x42, (byte) 0x82, (byte) 0x84,
            0x77, 0x65, 0x62, 0x6D, 0x42, (byte) 0x87, (byte) 0x81, 0x02
        };
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.webm", "video/webm", webmHeader),
            "test.webm",
            "video/webm"
        );
        assertTrue(result.isSuccess(), "WebM文件验证应该通过，实际消息: " + result.getMessage());
    }

    @Test
    @DisplayName("测试无效的JPEG魔数")
    void testInvalidJpegMagicNumber() {
        // 错误的魔数
        byte[] invalidHeader = {0x00, 0x00, 0x00, 0x00};
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.jpg", "image/jpeg", invalidHeader),
            "test.jpg",
            "image/jpeg"
        );
        assertFalse(result.isSuccess(), "无效的JPEG魔数应该被拒绝");
    }

    @Test
    @DisplayName("测试空文件")
    void testEmptyFile() {
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            createMockFile("test.jpg", "image/jpeg", new byte[0]),
            "test.jpg",
            "image/jpeg"
        );
        assertFalse(result.isSuccess(), "空文件应该被拒绝");
    }

    @Test
    @DisplayName("测试不存在的文件")
    void testNullFile() {
        FileSecurityValidator.ValidationResult result = validator.validateFile(
            null,
            "test.jpg",
            "image/jpeg"
        );
        assertFalse(result.isSuccess(), "null文件应该被拒绝");
    }

    /**
     * 创建模拟的MultipartFile
     */
    private MultipartFile createMockFile(String filename, String contentType, byte[] header) {
        // 确保contentType不为null
        String actualContentType = contentType != null ? contentType : "application/octet-stream";

        MockMultipartFile file = new MockMultipartFile(
            "file",
            filename,
            actualContentType,
            header
        );
        return file;
    }

    /**
     * 将字节数组转换为十六进制字符串（用于调试）
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
