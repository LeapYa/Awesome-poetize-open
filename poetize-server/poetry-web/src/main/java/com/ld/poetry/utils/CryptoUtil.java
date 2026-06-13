package com.ld.poetry.utils;
import com.ld.poetry.utils.JsonUtils;

import com.ld.poetry.constants.CommonConst;
import lombok.extern.slf4j.Slf4j;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 加密解密工具类
 */
@Slf4j
public class CryptoUtil {
    
    // 使用统一密钥，从CommonConst获取
    private static final String KEY = CommonConst.CRYPOTJS_KEY;
    
    /**
     * AES加密 - 使用GCM模式（更安全）
     * @param data 待加密的数据
     * @return 加密后的字符串（格式：IV:密文）
     */
    public static String encrypt(String data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");

            // 使用GCM模式，提供认证加密
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // 生成随机IV
            byte[] iv = new byte[12]; // GCM模式推荐12字节的IV
            java.security.SecureRandom random = new java.security.SecureRandom();
            random.nextBytes(iv);

            // 初始化GCM参数，认证标签长度为128位
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // 将IV和密文组合在一起，IV放在前面
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES加密失败", e);
            return null;
        }
    }
    
    /**
     * AES解密 - 使用GCM模式（更安全）
     * @param encryptedData 加密的数据（格式：IV:密文）
     * @return 解密后的字符串
     */
    public static String decrypt(String encryptedData) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");

            // 解码Base64，支持URL安全编码（替换'-'和'_'为标准Base64字符）
            String urlSafeEncoded = encryptedData.replace('-', '+').replace('_', '/');
            // 确保填充正确
            int padding = urlSafeEncoded.length() % 4;
            if (padding > 0) {
                urlSafeEncoded += "===".substring(0, 4 - padding);
            }
            byte[] decoded = Base64.getDecoder().decode(urlSafeEncoded);

            // 提取IV（前12字节）
            byte[] iv = new byte[12];
            System.arraycopy(decoded, 0, iv, 0, iv.length);

            // 提取密文
            byte[] encrypted = new byte[decoded.length - iv.length];
            System.arraycopy(decoded, iv.length, encrypted, 0, encrypted.length);

            // 使用GCM模式解密
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES解密失败: {}", e.getClass().getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 将Map转换为加密的响应格式
     * @param data 待加密的数据
     * @return 包含加密数据的Map
     */
    public static Map<String, Object> encryptResponse(Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("encrypted", encrypt(JsonUtils.toJsonString(data)));
        return response;
    }
    
    /**
     * 解密请求中的数据
     * @param encryptedData 加密的数据
     * @return 解密后的Map
     */
    public static Map<String, Object> decryptRequest(String encryptedData) {
        String decrypted = decrypt(encryptedData);
        if (decrypted != null) {
            return JsonUtils.parseObject(decrypted, Map.class);
        }
        return null;
    }
}
