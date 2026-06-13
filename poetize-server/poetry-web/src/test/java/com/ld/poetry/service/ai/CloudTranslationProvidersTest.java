package com.ld.poetry.service.ai;
import com.ld.poetry.utils.JsonUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudTranslationProvidersTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void tencentProvider_signsTc3RequestAndParsesResponse() {
        TencentTranslationProvider provider = provider(new TencentTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("secret_id", "sid");
        config.put("secret_key", "skey");
        config.put("region", "ap-guangzhou");
        config.put("project_id", 0);

        server.expect(requestTo("https://tmt.tencentcloudapi.com"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-TC-Action", "TextTranslate"))
                .andExpect(header("X-TC-Timestamp", "1704067200"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, containsString("TC3-HMAC-SHA256 Credential=sid/20240101/tmt/tc3_request")))
                .andExpect(content().json("""
                        {"SourceText":"Hello","Source":"en","Target":"zh","ProjectId":0}
                        """))
                .andRespond(withSuccess("{\"Response\":{\"TargetText\":\"你好\"}}", MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void aliyunProvider_signsRpcFormAndParsesResponse() {
        AliyunTranslationProvider provider = provider(new FixedNonceAliyunTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("access_key_id", "ak");
        config.put("access_key_secret", "sk");
        config.put("region", "cn-hangzhou");

        server.expect(requestTo("https://mt.cn-hangzhou.aliyuncs.com"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("Action=TranslateGeneral")))
                .andExpect(content().string(containsString("SignatureNonce=fixed-nonce")))
                .andExpect(content().string(containsString("AccessKeyId=ak")))
                .andExpect(content().string(containsString("Signature=")))
                .andRespond(withSuccess("{\"Data\":{\"Translated\":\"你好\"}}", MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void volcengineProvider_signsRequestAndParsesResponse() {
        VolcengineTranslationProvider provider = provider(new VolcengineTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("access_key_id", "ak");
        config.put("secret_key", "sk");
        config.put("region", "cn-north-1");

        server.expect(requestTo("https://translate.volcengineapi.com/?Action=TranslateText&Version=2020-06-01"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Date", "20240101T000000Z"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, containsString("HMAC-SHA256 Credential=ak/20240101/cn-north-1/translate/request")))
                .andExpect(content().json("""
                        {"TextList":["Hello"],"SourceLanguage":"en","TargetLanguage":"zh"}
                        """))
                .andRespond(withSuccess("{\"TranslationList\":[{\"Translation\":\"你好\"}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void huaweiProvider_signsAkSkRequestAndParsesResponse() {
        HuaweiTranslationProvider provider = provider(new HuaweiTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("endpoint", "https://nlp-ext.cn-north-4.myhuaweicloud.com");
        config.put("project_id", "project");
        config.put("auth_type", "aksk");
        config.put("access_key_id", "ak");
        config.put("access_key_secret", "sk");

        server.expect(requestTo("https://nlp-ext.cn-north-4.myhuaweicloud.com/v1/project/machine-translation/text-translation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sdk-Date", "20240101T000000Z"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, containsString("SDK-HMAC-SHA256 Access=ak")))
                .andExpect(content().json("""
                        {"text":"Hello","from":"en","to":"zh"}
                        """))
                .andRespond(withSuccess("{\"translated_text\":\"你好\"}", MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void googleProvider_postsRestFormAndParsesResponse() {
        GoogleTranslationProvider provider = provider(new GoogleTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("api_key", "key");

        server.expect(requestTo("https://translation.googleapis.com/language/translate/v2?key=key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("q=Hello")))
                .andExpect(content().string(containsString("target=zh-CN")))
                .andRespond(withSuccess("{\"data\":{\"translations\":[{\"translatedText\":\"你好\"}]}}",
                        MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void azureProvider_postsRestJsonAndParsesResponse() {
        AzureTranslatorProvider provider = provider(new AzureTranslatorProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("endpoint", "https://api.cognitive.microsofttranslator.com");
        config.put("subscription_key", "key");
        config.put("region", "eastasia");

        server.expect(requestTo("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=en&to=zh-Hans"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Ocp-Apim-Subscription-Key", "key"))
                .andExpect(header("Ocp-Apim-Subscription-Region", "eastasia"))
                .andExpect(content().json("[{\"Text\":\"Hello\"}]"))
                .andRespond(withSuccess("[{\"translations\":[{\"text\":\"你好\"}]}]", MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void deeplProvider_postsRestJsonAndParsesResponse() {
        DeepLTranslationProvider provider = provider(new DeepLTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("auth_key", "key");
        config.put("endpoint_type", "free");

        server.expect(requestTo("https://api-free.deepl.com/v2/translate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key key"))
                .andExpect(content().json("""
                        {"text":["Hello"],"source_lang":"EN","target_lang":"ZH"}
                        """))
                .andRespond(withSuccess("{\"translations\":[{\"text\":\"你好\"}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void awsProvider_signsSigV4RequestAndParsesResponse() {
        AwsTranslationProvider provider = provider(new AwsTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("access_key_id", "ak");
        config.put("secret_access_key", "sk");
        config.put("region", "us-east-1");

        server.expect(requestTo("https://translate.us-east-1.amazonaws.com/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Amz-Date", "20240101T000000Z"))
                .andExpect(header("X-Amz-Target", "AWSShineFrontendService_20170701.TranslateText"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, containsString("AWS4-HMAC-SHA256 Credential=ak/20240101/us-east-1/translate/aws4_request")))
                .andExpect(content().json("""
                        {"Text":"Hello","SourceLanguageCode":"en","TargetLanguageCode":"zh"}
                        """))
                .andRespond(withSuccess("{\"TranslatedText\":\"你好\"}", MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    @Test
    void yandexProvider_postsRestJsonAndParsesResponse() {
        YandexTranslationProvider provider = provider(new YandexTranslationProvider());
        JsonUtils.JsonObj config = new JsonUtils.JsonObj();
        config.put("api_key_or_iam_token", "key");
        config.put("folder_id", "folder");

        server.expect(requestTo("https://translate.api.cloud.yandex.net/translate/v2/translate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Api-Key key"))
                .andExpect(content().json("""
                        {"sourceLanguageCode":"en","targetLanguageCode":"zh","format":"PLAIN_TEXT","texts":["Hello"],"folderId":"folder"}
                        """))
                .andRespond(withSuccess("{\"translations\":[{\"text\":\"你好\"}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals("你好", provider.translate("Hello", "en", "zh", config));
        server.verify();
    }

    private <T extends AbstractApiTranslationProvider> T provider(T provider) {
        ReflectionTestUtils.setField(provider, "restTemplate", restTemplate);
        provider.clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        return provider;
    }

    private static class FixedNonceAliyunTranslationProvider extends AliyunTranslationProvider {
        @Override
        protected String nonce() {
            return "fixed-nonce";
        }
    }
}
