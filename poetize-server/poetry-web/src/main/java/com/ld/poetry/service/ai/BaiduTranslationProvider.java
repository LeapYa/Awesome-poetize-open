package com.ld.poetry.service.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 百度通用翻译 API。
 */
@Slf4j
@Service
public class BaiduTranslationProvider extends AbstractApiTranslationProvider {

    private static final String BAIDU_API_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Override
    public String providerKey() {
        return "baidu";
    }

    @Override
    public String displayName() {
        return "百度翻译";
    }

    /**
     * 兼容旧调用：从默认 article_ai 配置读取百度参数。
     */
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
            if (config == null || !StringUtils.hasText(config.getBaiduConfig())) {
                log.error("百度翻译配置未找到");
                return null;
            }
            return translate(text, sourceLang, targetLang, JSON.parseObject(config.getBaiduConfig()));
        } catch (Exception e) {
            log.error("百度翻译失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public String translateWithConfig(String text, String sourceLang, String targetLang,
            String appId, String appSecret) {
        JSONObject config = new JSONObject();
        config.put("app_id", appId);
        config.put("app_secret", appSecret);
        return translate(text, sourceLang, targetLang, config);
    }

    @Override
    protected String doTranslate(String text, String sourceLang, String targetLang, JSONObject config) {
        String appId = required(config, "app_id");
        String appSecret = required(config, "app_secret");
        String from = TranslationLanguageMapper.map(providerKey(), sourceLang, true);
        String to = TranslationLanguageMapper.map(providerKey(), targetLang, false);
        String salt = String.valueOf(ThreadLocalRandom.current().nextInt(32768, 65536));
        String sign = md5Hex(appId + text + salt + appSecret);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("q", text);
        formData.add("from", from);
        formData.add("to", to);
        formData.add("appid", appId);
        formData.add("salt", salt);
        formData.add("sign", sign);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = exchange(URI.create(BAIDU_API_URL), HttpMethod.POST, headers, formData);
        JSONObject body = parseObject(response.getBody());
        JSONArray results = body == null ? null : body.getJSONArray("trans_result");
        if (results != null && !results.isEmpty()) {
            return results.getJSONObject(0).getString("dst");
        }
        log.error("百度翻译 API 错误: code={}, msg={}",
                body == null ? null : body.getString("error_code"),
                body == null ? null : body.getString("error_msg"));
        return null;
    }
}
