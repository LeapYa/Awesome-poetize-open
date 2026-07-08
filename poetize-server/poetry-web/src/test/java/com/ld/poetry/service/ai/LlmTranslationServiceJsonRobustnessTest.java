package com.ld.poetry.service.ai;

import com.ld.poetry.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 LLM 翻译 JSON 响应解析对长文本、代码块的容错能力。
 */
class LlmTranslationServiceJsonRobustnessTest {

    @Test
    void testValidJsonWithCodeBlocks() throws Exception {
        Map<String, String> article = new LinkedHashMap<>();
        article.put("title", "示例标题");
        article.put("content", "下面是一段代码：\n```java\npublic class Hello {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello, World!\");\n" +
                "    }\n}\n```\n" +
                "以及 `console.log(\"done\")` 内联代码。");

        String response = JsonUtils.toJsonString(article);

        Map<String, String> result = invokeParse(response, "原始标题", "原始内容", "en");

        assertNotNull(result, "合法 JSON 应正常解析");
        assertEquals("示例标题", result.get("title"));
        assertTrue(result.get("content").contains("public class Hello"));
        assertEquals("en", result.get("language"));
    }

    @Test
    void testMalformedJsonWithUnescapedQuotes() throws Exception {
        // 模拟模型输出不合法 JSON：content 中的引号没有转义
        String response = """
                {"title":"翻译后的标题","content":"He said "Hello World" and then `console.log("ok")`."}
                """;

        Map<String, String> result = invokeParse(response, "原始标题", "原始内容", "en");

        assertNotNull(result, "字符串内未转义引号应能被兜底提取");
        assertEquals("翻译后的标题", result.get("title"));
        assertEquals("He said \"Hello World\" and then `console.log(\"ok\")`.",
                result.get("content"));
    }

    @Test
    void testMalformedJsonWithUnescapedNewlines() throws Exception {
        // 模拟 content 内出现未转义的换行
        String response = """
                {"title":"翻译标题","content":"第一行
                ```java
                public class Test {
                    // 代码
                }
                ```
                第二行"}
                """;

        Map<String, String> result = invokeParse(response, "原始标题", "原始内容", "en");

        assertNotNull(result, "字符串内未转义换行应能被兜底提取");
        assertEquals("翻译标题", result.get("title"));
        assertTrue(result.get("content").contains("public class Test"));
    }

    @Test
    void testLongArticleWithManyCodeBlocks() throws Exception {
        StringBuilder content = new StringBuilder();
        content.append("这是一篇很长的文章，包含大量代码块。\n\n");
        for (int i = 0; i < 20; i++) {
            content.append("### 第 ").append(i + 1).append(" 段\n\n");
            content.append("```javascript\n");
            content.append("function example").append(i).append("() {\n");
            content.append("    const msg = \"hello \" + ").append(i).append(";\n");
            content.append("    return { id: ").append(i).append(", text: msg };\n");
            content.append("}\n");
            content.append("```\n\n");
        }
        content.append("结尾。\n");

        Map<String, String> article = new LinkedHashMap<>();
        article.put("title", "长文翻译标题");
        article.put("content", content.toString());

        // 先生成合法 JSON，再把 content 中的 \" 替换为 "，模拟模型未转义内部引号
        String validResponse = JsonUtils.toJsonString(article);
        String malformedResponse = validResponse.replace("\\\"", "\"");

        Map<String, String> result = invokeParse(malformedResponse, "原始标题", "原始内容", "en");

        assertNotNull(result, "长文+大量代码块导致的非法 JSON 应能被兜底提取");
        assertEquals("长文翻译标题", result.get("title"));
        assertTrue(result.get("content").contains("function example19"));
    }

    @Test
    void testLongGeneratedArticle() throws Exception {
        String article = generateLongMockArticle();
        assertTrue(article.length() > 3000, "生成的长文内容应足够长");

        Map<String, String> articleMap = new LinkedHashMap<>();
        articleMap.put("title", "逆向实战：API 签名还原与 Python 调用");
        articleMap.put("content", article);

        // 模拟模型未转义 content 内部的双引号：生成合法 JSON 后把 \" 替换为 "
        String validResponse = JsonUtils.toJsonString(articleMap);
        String malformedResponse = validResponse.replace("\\\"", "\"");

        Map<String, String> result = invokeParse(malformedResponse,
                "原始标题", "原始内容", "en");

        assertNotNull(result, "程序生成长文的不合法 JSON 应能被兜底提取");
        assertEquals("逆向实战：API 签名还原与 Python 调用", result.get("title"));
        String extractedContent = result.get("content");
        assertNotNull(extractedContent);

        // 关键检查：内容不应被截断
        assertTrue(extractedContent.contains("本系列第一篇文章"),
                "应提取到文章开头");
        assertTrue(extractedContent.contains("requests.get"),
                "应提取到文章中的 Python 代码");
        assertTrue(extractedContent.contains("sign"),
                "应提取到 sign 相关内容");
        assertTrue(extractedContent.contains("总结"),
                "应提取到文章结尾");

        // 长度检查：提取结果应接近原文长度，不能严重截断
        double ratio = (double) extractedContent.length() / article.length();
        assertTrue(ratio > 0.8,
                "提取内容长度不应严重截断，实际比例: " + ratio);
    }

    /**
     * 生成一篇不依赖外部文件的长文本技术文章 mock，包含大量代码块、引号、花括号。
     */
    private String generateLongMockArticle() {
        StringBuilder sb = new StringBuilder();
        sb.append("本系列第一篇文章结尾留了个预告：下一篇会用一个真实的站点，把「发现签名」到「还原算法」的完整流程走一遍。今天我们就拿一个示例站 `api.example.com` 的「实时快报」接口来实战。还没看过第一篇的可以先[回去看](/article/1)，再回来继续。\n\n");

        sb.append("## 一、抓到目标接口\n\n");
        sb.append("打开目标页面，F12 切到 Network 面板，过滤 Fetch/XHR。刷新后能看到一个发往 `/api/feed` 的请求，Response 是一堆 JSON 数据。右键 Copy as cURL，在终端跑一下：\n\n");
        sb.append("```bash\n");
        sb.append("curl 'https://api.example.com/api/feed?app=DemoWeb&page=1&size=20&sign=a1b2c3d4e5f6'\n");
        sb.append("```\n\n");
        sb.append("第一次能拿到数据，但过几分钟再用同样的 `sign` 请求，就会返回 `{\"code\":401,\"msg\":\"sign expired\"}`。这说明 sign 是带时效的，必须动态生成。\n\n");

        sb.append("## 二、定位 sign 的生成位置\n\n");
        sb.append("在 Sources 面板全局搜索 `sign:`，很快就能定位到一个打包后的 JS 文件。关键代码如下：\n\n");
        sb.append("```javascript\n");
        sb.append("function makeSign(params) {\n");
        sb.append("    const sorted = Object.keys(params).sort().map(k => k + '=' + params[k]).join('&');\n");
        sb.append("    const secret = window.__APP_SECRET__ || 'default_secret';\n");
        sb.append("    return CryptoJS.MD5(sorted + secret).toString();\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("这里有几个重点：`Object.keys(params).sort()` 说明参数要按字母序拼接；`CryptoJS.MD5` 说明用的是 MD5；`secret` 来自全局变量，说明密钥是前端内嵌的。\n\n");

        sb.append("## 三、用 Python 复现签名\n\n");
        sb.append("知道了规则，就可以用 Python 写一个生成 sign 的函数。注意 Python 的 `sorted(dict)` 默认就是按 key 排序，和 JS 一致：\n\n");
        sb.append("```python\n");
        sb.append("import hashlib\n");
        sb.append("import requests\n\n");
        sb.append("def make_sign(params, secret='default_secret'):\n");
        sb.append("    payload = '&'.join(f\"{k}={v}\" for k, v in sorted(params.items()))\n");
        sb.append("    return hashlib.md5((payload + secret).encode()).hexdigest()\n\n");
        sb.append("params = {\n");
        sb.append("    \"app\": \"DemoWeb\",\n");
        sb.append("    \"page\": 1,\n");
        sb.append("    \"size\": 20,\n");
        sb.append("}\n");
        sb.append("params['sign'] = make_sign(params)\n");
        sb.append("resp = requests.get('https://api.example.com/api/feed', params=params)\n");
        sb.append("print(resp.json())\n");
        sb.append("```\n\n");
        sb.append("跑通后就能看到正常返回的数据了。这里的 `{\"app\": \"DemoWeb\"}` 只是示例，真实站点里参数会更多，但思路完全一样。\n\n");

        for (int i = 0; i < 10; i++) {
            sb.append("## 四、常见坑点第 ").append(i + 1).append(" 讲\n\n");
            sb.append("有些接口会把 `timestamp` 也参与签名，这时要注意服务器端的时间窗口。如果本地时间偏差太大，会报 `\"timestamp invalid\"`。解决方案有两种：一是同步 NTP，二是先从服务器取一次时间再计算差值。\n\n");
            sb.append("另外，参数值如果是对象，比如 `{\"filter\":{\"type\":\"news\"}}`，不同站点序列化方式可能不同。有的用 `JSON.stringify`，有的直接拼接成 `filter[type]=news`，这需要在 Call Stack 里跟清楚。\n\n");
            sb.append("```javascript\n");
            sb.append("const payload = { id: ").append(i).append(", text: \"demo\" };\n");
            sb.append("console.log(\"sign for item \" + ").append(i).append(" + \" is ready\");\n");
            sb.append("```\n\n");
        }

        sb.append("## 五、总结\n\n");
        sb.append("整个流程可以概括为：抓包 -> 定位 sign -> 跟 Call Stack 找算法 -> 用 Python 复现 -> 处理时间戳/编码等细节。下次再遇到类似接口，按这个套路来就行。记住，不要在生产环境滥用这类脚本，仅用于学习和合法授权的数据采集。\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> invokeParse(String response, String originalTitle,
            String originalContent, String targetLang) throws Exception {
        LlmTranslationService service = new LlmTranslationService();
        Method method = LlmTranslationService.class.getDeclaredMethod(
                "parseArticleTranslationResponse",
                String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(service, response, originalTitle, originalContent, targetLang);
    }

}
