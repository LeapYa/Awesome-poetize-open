package com.ld.poetry.utils.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 文件安全验证工具类
 * 通过基础文件名检查和图片、视频、音频魔数检测，确保上传文件的安全性。
 * 其他文件类型允许作为附件上传，服务端只保存并提供下载，不执行文件。
 */
@Slf4j
@Component
public class FileSecurityValidator {

    // 文件类型枚举
    public enum FileType {
        IMAGE("image", 100) { // 增加到100字节以支持SVG的XML声明
            @Override
            public boolean validateMagicNumber(byte[] header) {
                return isJpeg(header) || isPng(header) || isGif(header) || isBmp(header) || isWebp(header) || isTiff(header) || isSvg(header) || isIco(header);
            }

            @Override
            public String getValidationFailedMessage() {
                return "图片文件格式验证失败";
            }
        },
        VIDEO("video", 16) {
            @Override
            public boolean validateMagicNumber(byte[] header) {
                return isMp4(header) || isAvi(header) || isMov(header) || isWmv(header) || isFlv(header) || isWebm(header);
            }

            @Override
            public String getValidationFailedMessage() {
                return "视频文件格式验证失败";
            }
        },
        AUDIO("audio", 12) {
            @Override
            public boolean validateMagicNumber(byte[] header) {
                return isMp3(header) || isWav(header) || isOgg(header) || isAac(header) || isFlac(header);
            }

            @Override
            public String getValidationFailedMessage() {
                return "音频文件格式验证失败";
            }
        };

        private final String typeName;
        private final int minHeaderSize;

        FileType(String typeName, int minHeaderSize) {
            this.typeName = typeName;
            this.minHeaderSize = minHeaderSize;
        }

        public abstract boolean validateMagicNumber(byte[] header);
        public abstract String getValidationFailedMessage();

        // 图片魔数
        private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47};
        private static final byte[] GIF_HEADER = {0x47, 0x49, 0x46, 0x38};
        private static final byte[] BMP_HEADER = {0x42, 0x4D};
        private static final byte[] WEBP_HEADER = {0x52, 0x49, 0x46, 0x46};
        private static final byte[] ICO_HEADER = {0x00, 0x00, 0x01, 0x00};
        // SVG - 支持两种格式：直接以<svg开头，或以<?xml开头
        private static final byte[] SVG_HEADER = {0x3C, 0x73, 0x76, 0x67}; // "<svg"
        private static final byte[] SVG_XML_HEADER = {0x3C, 0x3F, 0x78, 0x6D, 0x6C}; // "<?xml"
        // TIFF
        private static final byte[] TIFF_BE_HEADER = {0x4D, 0x4D, 0x00, 0x2A}; // TIFF Big Endian
        private static final byte[] TIFF_LE_HEADER = {0x49, 0x49, 0x2A, 0x00}; // TIFF Little Endian

        // 视频魔数
        private static final byte[] MP4_HEADER = {0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70};
        private static final byte[] AVI_HEADER = {0x52, 0x49, 0x46, 0x46};
        private static final byte[] MOV_HEADER = {0x66, 0x74, 0x79, 0x70};

        // 音频魔数
        private static final byte[] MP3_HEADER = {0x49, 0x44, 0x33};
        private static final byte[] WAV_HEADER = {0x52, 0x49, 0x46, 0x46};
        private static final byte[] OGG_HEADER = {0x4F, 0x67, 0x67, 0x53};
        private static final byte[] FLAC_HEADER = {0x66, 0x4C, 0x61, 0x43};

        private static boolean matches(byte[] fileHeader, byte[] pattern) {
            if (fileHeader.length < pattern.length) {
                return false;
            }
            return Arrays.equals(Arrays.copyOf(fileHeader, pattern.length), pattern);
        }

        // 图片验证
        private static boolean isJpeg(byte[] header) { return matches(header, JPEG_HEADER); }
        private static boolean isPng(byte[] header) { return matches(header, PNG_HEADER); }
        private static boolean isGif(byte[] header) {
            return matches(header, GIF_HEADER) && header.length >= 4 &&
                    (header[3] == 0x38 || header[3] == 0x39);
        }
        private static boolean isBmp(byte[] header) { return matches(header, BMP_HEADER); }
        private static boolean isWebp(byte[] header) {
            return matches(header, WEBP_HEADER) && header.length >= 12 &&
                    header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
        }
        private static boolean isIco(byte[] header) { return matches(header, ICO_HEADER); }
        // TIFF验证
        private static boolean isTiff(byte[] header) {
            return matches(header, TIFF_BE_HEADER) || matches(header, TIFF_LE_HEADER);
        }
        // SVG验证 - 支持两种格式
        private static boolean isSvg(byte[] header) {
            // 检查是否以<svg开头
            if (matches(header, SVG_HEADER)) {
                return true;
            }
            // 检查是否以<?xml开头，并且后续包含<svg
            if (matches(header, SVG_XML_HEADER) && header.length >= 100) {
                // 在前100个字节中查找<svg标签（更宽泛的搜索）
                for (int i = 5; i < Math.min(100, header.length - 3); i++) {
                    if (header[i] == 0x3C && header[i+1] == 0x73 &&
                        header[i+2] == 0x76 && header[i+3] == 0x67) {
                        return true;
                    }
                }
            }
            return false;
        }

        // 视频验证
        private static boolean isMp4(byte[] header) { return matches(header, MP4_HEADER); }
        private static boolean isAvi(byte[] header) {
            return matches(header, AVI_HEADER) && header.length >= 12 &&
                    header[8] == 0x41 && header[9] == 0x56 && header[10] == 0x49 && header[11] == 0x20;
        }
        private static boolean isMov(byte[] header) { return matches(header, MOV_HEADER); }
        private static boolean isWmv(byte[] header) { return matches(header, MOV_HEADER); }
        private static boolean isFlv(byte[] header) { return header.length >= 4 && header[0] == 0x46 && header[1] == 0x4C && header[2] == 0x56 && header[3] == 0x01; }
        private static boolean isWebm(byte[] header) { return matches(header, MOV_HEADER); }

        // 音频验证
        private static boolean isMp3(byte[] header) { return matches(header, MP3_HEADER); }
        private static boolean isWav(byte[] header) {
            return matches(header, WAV_HEADER) && header.length >= 12 &&
                    header[8] == 0x57 && header[9] == 0x41 && header[10] == 0x56 && header[11] == 0x45;
        }
        private static boolean isOgg(byte[] header) { return matches(header, OGG_HEADER); }
        private static boolean isAac(byte[] header) { return header.length >= 2 && (header[0] == 0xFF && (header[1] & 0xF0) == 0xF0); }
        private static boolean isFlac(byte[] header) { return matches(header, FLAC_HEADER); }
    }

    /**
     * 验证文件安全性
     * 通过魔数检测和扩展名验证，确保上传文件的安全性
     * 支持图片、视频、音频等多种文件类型
     *
     * @param file 上传的文件
     * @param originalFilename 原始文件名
     * @param contentType 文件的Content-Type
     * @return 验证结果
     */
    public ValidationResult validateFile(MultipartFile file, String originalFilename, String contentType) {
        try {
            // 1. 检查文件是否为空
            if (file == null || file.isEmpty()) {
                return ValidationResult.fail("文件不能为空");
            }

            // 2. 检查原始文件名
            if (!hasText(originalFilename)) {
                return ValidationResult.fail("文件名不能为空");
            }

            // 3. 提取文件扩展名
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!hasText(extension)) {
                return ValidationResult.fail("文件必须包含扩展名");
            }

            // 4. 自动识别文件类型并验证
            FileType detectedType = detectFileType(contentType);
            if (detectedType != null) {
                // 已知文件类型 - 执行严格验证
                if (!isContentTypeMatchExtension(contentType, extension, detectedType)) {
                    return ValidationResult.fail("Content-Type与文件扩展名不匹配");
                }

                // 读取文件头部进行魔数验证
                byte[] fileHeader = readFileHeader(file, detectedType.minHeaderSize);
                if (fileHeader == null || fileHeader.length < 4) {
                    return ValidationResult.fail("文件格式错误或文件损坏");
                }

                // 验证魔数
                if (!detectedType.validateMagicNumber(fileHeader)) {
                    return ValidationResult.fail(detectedType.getValidationFailedMessage());
                }

                log.info("文件验证通过: {}, 类型: {}, 大小: {} bytes",
                        originalFilename, detectedType.typeName, file.getSize());
            } else {
                // 未知或通用文件类型允许作为附件上传，下载侧统一控制是否强制下载。
                log.info("文件基础验证通过: {}, Content-Type: {}, 大小: {} bytes",
                        originalFilename, contentType, file.getSize());
            }

            return ValidationResult.success(extension);

        } catch (Exception e) {
            log.error("文件验证过程发生异常: " + originalFilename, e);
            return ValidationResult.fail("文件验证失败: " + e.getMessage());
        }
    }

    /**
     * 根据Content-Type检测文件类型
     */
    private FileType detectFileType(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (FileType type : FileType.values()) {
            if (contentType.startsWith(type.typeName + "/")) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查Content-Type与扩展名是否匹配
     */
    private boolean isContentTypeMatchExtension(String contentType, String extension, FileType type) {
        if (type == FileType.IMAGE) {
            if (contentType.startsWith("image/jpeg")) return extension.equals("jpg") || extension.equals("jpeg");
            if (contentType.startsWith("image/png")) return extension.equals("png");
            if (contentType.startsWith("image/gif")) return extension.equals("gif");
            if (contentType.startsWith("image/bmp")) return extension.equals("bmp");
            if (contentType.startsWith("image/webp")) return extension.equals("webp");
            if (contentType.startsWith("image/x-icon") || contentType.startsWith("image/vnd.microsoft.icon") || contentType.startsWith("image/ico")) return extension.equals("ico");
            if (contentType.startsWith("image/tiff")) return extension.equals("tiff") || extension.equals("tif");
            if (contentType.startsWith("image/x-photoshop")) return extension.equals("psd");
            if (contentType.startsWith("image/svg+xml")) return extension.equals("svg");
        } else if (type == FileType.VIDEO) {
            if (contentType.startsWith("video/mp4")) return extension.equals("mp4");
            if (contentType.startsWith("video/avi")) return extension.equals("avi");
            if (contentType.startsWith("video/mov")) return extension.equals("mov");
            if (contentType.startsWith("video/wmv")) return extension.equals("wmv");
            if (contentType.startsWith("video/flv")) return extension.equals("flv");
            if (contentType.startsWith("video/webm")) return extension.equals("webm");
        } else if (type == FileType.AUDIO) {
            if (contentType.startsWith("audio/mpeg") || contentType.startsWith("audio/mp3")) return extension.equals("mp3");
            if (contentType.startsWith("audio/wav")) return extension.equals("wav");
            if (contentType.startsWith("audio/ogg")) return extension.equals("ogg");
            if (contentType.startsWith("audio/aac")) return extension.equals("aac");
            if (contentType.startsWith("audio/flac")) return extension.equals("flac");
        }
        return true; // 未知类型默认通过
    }

    /**
     * 读取文件头部魔数
     */
    private byte[] readFileHeader(MultipartFile file, int requiredSize) {
        try (InputStream is = file.getInputStream()) {
            int actualSize = (int) Math.min(file.getSize(), requiredSize);
            byte[] buffer = new byte[actualSize];
            int bytesRead = is.read(buffer);
            if (bytesRead > 0) {
                return Arrays.copyOf(buffer, bytesRead);
            }
        } catch (IOException e) {
            log.error("读取文件头部失败", e);
        }
        return null;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }

    /**
     * 简单文本检查
     */
    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean success;
        private final String message;
        private final String extension;

        private ValidationResult(boolean success, String message, String extension) {
            this.success = success;
            this.message = message;
            this.extension = extension;
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message, null);
        }

        public static ValidationResult success(String extension) {
            return new ValidationResult(true, "验证通过", extension);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getExtension() {
            return extension;
        }
    }
}
