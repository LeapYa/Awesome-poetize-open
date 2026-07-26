package com.ld.poetry.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.SecureRandom;

/**
 * AES加密工具类
 * 用于API密钥等敏感信息的加密存储
 * 
 * @author LeapYa
 * @since 2025-10-18
 */
@Slf4j
@Component
public class AESCryptoUtil {

    /**
     * AES加密密钥（从环境变量获取）
     */
    @Value("${poetize.aes.key:sarasarasarasara}")
    private String secretKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // GCM模式推荐12字节IV
    private static final int GCM_TAG_LENGTH = 128; // 128位认证标签

    /**
     * 加密数据
     *
     * @param data 原始数据
     * @return Base64编码的加密数据（格式：IV+密文），加密失败返回null
     */
    public String encrypt(String data) {
        if (!StringUtils.hasText(data)) {
            return data;
        }

        try {
            // 确保密钥长度为16字节（128位）
            byte[] keyBytes = get16ByteKey(secretKey);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // 初始化GCM参数
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            // 加密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec);

            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // 组合IV和密文
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("数据加密失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解密数据
     *
     * @param encryptedData Base64编码的加密数据（格式：IV+密文）
     * @return 解密后的原始数据，解密失败返回null
     */
    public String decrypt(String encryptedData) {
        if (!StringUtils.hasText(encryptedData)) {
            return encryptedData;
        }

        try {
            // 确保密钥长度为16字节（128位）
            byte[] keyBytes = get16ByteKey(secretKey);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            // 解码Base64，支持URL安全编码（替换'-'和'_'为标准Base64字符）
            String urlSafeEncoded = encryptedData.replace('-', '+').replace('_', '/');
            // 确保填充正确
            int padding = urlSafeEncoded.length() % 4;
            if (padding > 0) {
                urlSafeEncoded += "===".substring(0, 4 - padding);
            }

            byte[] combined;
            try {
                combined = Base64.getDecoder().decode(urlSafeEncoded);
            } catch (IllegalArgumentException e) {
                log.warn("数据非Base64格式，跳过解密: {}", encryptedData.substring(0, Math.min(encryptedData.length(), 10)) + "...");
                return null;
            }

            // 验证数据长度（至少需要IV 12字节 + GCM Tag 16字节 + 1字节密文）
            if (combined.length < GCM_IV_LENGTH + 17) {
                log.warn("数据长度不足，可能未加密: 长度={}", combined.length);
                return null;
            }

            // 提取IV（前12字节）
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            // 提取密文
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // 解密
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (javax.crypto.AEADBadTagException e) {
            log.warn("数据解密失败(Tag不匹配)，可能数据未加密或密钥不一致: {}", encryptedData.substring(0, Math.min(encryptedData.length(), 10)) + "...");
            return null;
        } catch (Exception e) {
            log.warn("数据解密失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 数据脱敏显示（仅显示前4位和后4位）
     *
     * @param sensitiveData 敏感数据
     * @return 脱敏后的数据
     */
    public String mask(String sensitiveData) {
        if (!StringUtils.hasText(sensitiveData)) {
            return "";
        }

        int length = sensitiveData.length();
        if (length <= 8) {
            // 长度不足8位，全部脱敏
            return "*".repeat(length);
        }

        String prefix = sensitiveData.substring(0, 4);
        String suffix = sensitiveData.substring(length - 4);
        int maskedLength = length - 8;

        return prefix + "*".repeat(maskedLength) + suffix;
    }

    /**
     * 将密钥转换为16字节（128位）
     * 如果密钥不足16位，则补齐；超过16位则截取
     *
     * @param key 原始密钥
     * @return 16字节密钥
     */
    private byte[] get16ByteKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[16];

        if (keyBytes.length >= 16) {
            // 密钥长度大于等于16，截取前16位
            System.arraycopy(keyBytes, 0, result, 0, 16);
        } else {
            // 密钥长度小于16，补齐到16位
            System.arraycopy(keyBytes, 0, result, 0, keyBytes.length);
            // 剩余部分用0填充
            for (int i = keyBytes.length; i < 16; i++) {
                result[i] = 0;
            }
        }

        return result;
    }

    /**
     * 验证加密解密功能
     *
     * @return 测试是否成功
     */
    public boolean testEncryption() {
        try {
            String testData = "test_api_key_12345";
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);

            boolean success = testData.equals(decrypted);
            if (success) {
                log.info("加密解密测试成功");
            } else {
                log.error("加密解密测试失败: 原始数据与解密数据不匹配");
            }

            return success;

        } catch (Exception e) {
            log.error("加密解密测试失败: {}", e.getMessage(), e);
            return false;
        }
    }
}

