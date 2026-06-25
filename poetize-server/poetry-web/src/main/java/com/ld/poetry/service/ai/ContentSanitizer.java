package com.ld.poetry.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 内容净化器 — 防止提示词注入攻击
 * 
 * 对应 Python 端: content_sanitizer.py
 * 
 * 功能：
 * - 用户输入验证
 * - 页面上下文净化（移除潜在注入指令）
 * - 敏感内容过滤
 */
@Component
public class ContentSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(ContentSanitizer.class);

    // 潜在注入模式
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(?:previous|above|prior)\\s+instructions"),
            Pattern.compile("(?i)你是一个|you\\s+are\\s+(now\\s+)?a"),
            Pattern.compile("(?i)system\\s*:\\s*"),
            Pattern.compile("(?i)\\[\\s*SYSTEM\\s*\\]"),
            Pattern.compile("(?i)act\\s+as\\s+(?:a\\s+)?"),
            Pattern.compile("(?i)pretend\\s+(?:you\\s+are|to\\s+be)"),
            Pattern.compile("(?i)forget\\s+(?:all\\s+)?(?:previous|your)"),
            Pattern.compile("(?i)new\\s+instructions?:"),
            Pattern.compile("(?i)override\\s+(?:your|the)\\s+(?:system|instructions)"));

    // 提示词泄露攻击模式 — 检测用户试图提取系统提示词的行为
    private static final List<Pattern> PROMPT_LEAK_PATTERNS = List.of(
            // 直接要求输出系统提示词
            Pattern.compile("(?i)(?:output|print|show|display|reveal|tell|give|repeat|share|write)\\s+(?:me\\s+)?(?:your|the)?\\s*(?:system\\s*prompt|initial\\s*prompt|instructions|system\\s*message|developer\\s*message|internal\\s*(?:instructions|prompt|config))"),
            Pattern.compile("(?i)(?:输出|打印|显示|告诉我|给我|重复|展示|写出|说出|复述|泄露|透露).*(?:系统提示|系统指令|提示词|系统消息|内部指令|初始指令|开发者消息|系统设定|系统配置)"),
            Pattern.compile("(?i)(?:output|print|show|reveal|dump|echo|write).*(?:top|topmost|first|original|raw|plain\\s*text|unencrypted|initiali[sz]ation|init(?:ial)?).*(?:context|mapping|configuration|settings|instructions|message|prompt)"),
            Pattern.compile("(?i)(?:输出|打印|显示|写出|回显|透露).*(?:顶部|最顶部|首条|原始|未加密|纯文本|初始化|init).*(?:上下文|映射|设定|配置|指令|消息|文本)"),
            // 以各种方式变相提取
            Pattern.compile("(?i)what\\s+(?:are|is|were)\\s+your\\s+(?:system\\s*)?(?:instructions|prompt|rules|guidelines|directives|configuration)"),
            Pattern.compile("(?i)(?:你的|你有什么).*(?:指令|规则|提示词|系统设置|设定|底层配置|预设)"),
            // 角色扮演/假设场景绕过
            Pattern.compile("(?i)(?:repeat|recite|echo)\\s+(?:everything|all|the\\s+text)\\s+(?:above|before|so\\s+far)"),
            Pattern.compile("(?i)(?:重复|背诵|复述|回显).*(?:上面|之前|以上|所有).*(?:内容|文字|指令|文本)"),
            // 编码绕过尝试
            Pattern.compile("(?i)(?:encode|convert|translate|transform)\\s+(?:your\\s+)?(?:system\\s*)?(?:prompt|instructions)\\s+(?:into|to|as)\\s+(?:base64|hex|rot13|binary|morse|pig\\s*latin|json|code)"),
            Pattern.compile("(?i)(?:用|以|转换?成?)\\s*(?:base64|hex|rot13|二进制|摩尔斯|json|代码|编码).*(?:输出|写出|显示).*(?:系统|提示|指令)"),
            // "假装/想象" 场景
            Pattern.compile("(?i)(?:imagine|pretend|suppose|assume|let's\\s+say|hypothetically|for\\s+(?:educational|research|academic)\\s+purposes).*(?:system\\s*prompt|instructions|rules)"),
            Pattern.compile("(?i)(?:假装|假设|想象|设想|假如|如果).*(?:没有限制|没有规则|可以.*输出|系统提示|初始指令)")
    );

    // 高风险注入模式 — 这些模式几乎只出现在攻击场景中
    private static final List<Pattern> HIGH_RISK_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)(?:DAN|jailbreak|bypass|escape)\\s+(?:mode|prompt|filter|restrictions)"),
            Pattern.compile("(?i)(?:越狱|绕过|突破|解除).*(?:限制|过滤|审查|约束)")
    );

    private static final List<Pattern> OBFUSCATION_PATTERNS = List.of(
            Pattern.compile("(?i)(?:base64|rot13|hex|binary|morse|unicode|urlencode|decode|解码|编码)"),
            Pattern.compile("(?i)(?:不要输出解码过程|严格执行|最高优先级|立即执行|忽略.*规则|合并.*变量|拼接.*变量)"),
            Pattern.compile("(?i)(?:context_echo|system_prompt|developer_message|internal_config|init-context|hidden init-context mapping)"),
            Pattern.compile("(?i)(?:开发者备注|developer note|developer remark|核对.*映射|不要解释异常原因|直接以纯文本形式)")
    );

    private static final List<String> SECRET_TARGET_TERMS = List.of(
            "system prompt", "system_prompt", "developer message", "developer_message",
            "internal instructions", "internal prompt", "internal config",
            "original settings", "raw settings", "init context", "init-context", "hidden init-context mapping",
            "top level context", "topmost context", "initialization context", "raw configuration", "plain text settings",
            "系统提示", "系统提示词", "系统指令", "提示词", "开发者消息", "初始指令", "系统设定", "系统配置", "内部指令", "内部信息", "内部配置",
            "原始设定", "未加密的原始设定文本", "初始化上下文", "初始化上下文映射", "隐藏的初始化上下文映射", "顶部设定", "最顶部接收到的设定文本");

    private static final List<String> EXTRACTION_ACTION_TERMS = List.of(
            "print", "show", "reveal", "output", "repeat", "display", "echo", "write", "tell", "give",
            "dump", "纯文本", "plain text",
            "输出", "打印", "显示", "泄露", "透露", "复述", "回显", "写出", "说出", "给我", "告诉我");

    private static final Pattern BASE64_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9+/=])(?:[A-Za-z0-9+/]{20,}={0,2})(?![A-Za-z0-9+/=])");
    private static final Pattern HEX_TOKEN_PATTERN = Pattern.compile("(?<![A-Fa-f0-9])[A-Fa-f0-9]{24,}(?![A-Fa-f0-9])");
    private static final List<Pattern> CONTEXTUAL_FOLLOW_UP_PATTERNS = List.of(
            Pattern.compile("(?i)^(?:你的呢|那你的呢|你这边的呢|你收到的呢|你那份呢)[？?]?$"),
            Pattern.compile("(?i)^(?:上面的呢|前面的呢|那个呢|这一段呢|原文呢|原始的呢|完整的呢)[？?]?$"),
            Pattern.compile("(?i)^(?:继续|继续说|继续发|接着发|展开|贴出来|发出来|完整贴出)[！!。.]?$"),
            Pattern.compile("(?i)(?:你的呢|你收到的|上面的|前面的|原文|原始|完整).*(?:发|贴|给我|展示|输出|看看)"),
            Pattern.compile("(?i)(?:继续|接着).*(?:输出|贴出|打印|展示|发出来)"));
    private static final List<Pattern> BENIGN_PROMPT_ENGINEERING_PATTERNS = List.of(
            Pattern.compile("(?i)(?:通用|泛用|示例|模板|范例|例子).*(?:system\\s*prompt|提示词|系统提示|系统指令)"),
            Pattern.compile("(?i)(?:怎么写|如何写|如何设计|帮我写|给我写).*(?:system\\s*prompt|提示词|系统提示|系统指令)"),
            Pattern.compile("(?i)(?:我正在开发|我想开发|我要做|正在做).*(?:ai|助手|agent|机器人).*(?:system\\s*prompt|提示词|系统提示)"),
            Pattern.compile("(?i)(?:第一句话|开场白|开头).*(?:怎么写|应该怎么写|写什么).*(?:更专业|更合适|更自然)?"),
            Pattern.compile("(?i)(?:通用|示例|模板).*(?:java\\s*架构师|客服|助手|agent|ai助手)")
    );
    private static final List<String> INSTANCE_TARGET_TERMS = List.of(
            "你的", "你自己的", "你收到的", "你当前的", "你这边的", "你那份", "当前会话", "当前这个系统", "最顶部",
            "上面的原文", "原始设定", "未加密", "顶部设定", "第一条指令", "第一句话是什么",
            "your ", "your own", "you received", "current session", "topmost", "raw", "original");

    // 敏感词列表（简化版）
    private static final Set<String> BLOCKED_TERMS = Set.of(
            "自杀", "自残", "制造炸弹", "制造武器");

    /**
     * 验证用户输入
     * 
     * @throws IllegalArgumentException 如果内容不合法
     */
    public void validateUserInput(String input) {
        validateUserInput(input, null);
    }

    public void validateUserInput(String input, List<Map<String, Object>> history) {
        if (input == null || input.isBlank())
            return;

        // 检查敏感词
        for (String term : BLOCKED_TERMS) {
            if (input.contains(term)) {
                throw new IllegalArgumentException("消息包含不当内容，请修改后重试");
            }
        }

        // 检查高风险注入模式（直接拒绝）
        for (Pattern pattern : HIGH_RISK_INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                logger.warn("检测到高风险提示词注入攻击: {}", input.substring(0, Math.min(100, input.length())));
                throw new IllegalArgumentException("消息包含不当内容，请修改后重试");
            }
        }

        if (detectInjectionRisk(input, history) >= 2) {
            logger.warn("检测到高风险提示词套取请求并拒绝: {}", input.substring(0, Math.min(100, input.length())));
            throw new IllegalArgumentException("消息包含疑似提示词套取内容，请修改后重试");
        }
    }

    /**
     * 检测用户消息中的提示词注入风险等级
     * 
     * @return 风险等级: 0=安全, 1=低风险(注入尝试), 2=高风险(提示词泄露攻击)
     */
    public int detectInjectionRisk(String input) {
        return detectInjectionRisk(input, null);
    }

    public int detectInjectionRisk(String input, List<Map<String, Object>> history) {
        if (input == null || input.isBlank())
            return 0;

        List<String> candidates = buildDetectionCandidates(input);

        if (targetsCurrentInstanceForExtraction(candidates)) {
            logger.warn("检测到当前实例内部信息提取请求: {}", abbreviate(input));
            return 2;
        }

        if (looksLikeBenignPromptEngineering(candidates)) {
            return 0;
        }

        // 高风险：试图提取系统提示词
        for (String candidate : candidates) {
            for (Pattern pattern : PROMPT_LEAK_PATTERNS) {
                if (pattern.matcher(candidate).find()) {
                    logger.warn("检测到提示词泄露攻击: {}", abbreviate(input));
                    return 2;
                }
            }
        }

        if (matchesCompositePromptLeak(candidates)) {
            logger.warn("检测到组合式提示词套取攻击: {}", abbreviate(input));
            return 2;
        }

        if (matchesContextualPromptLeak(candidates, history)) {
            logger.warn("检测到会话级追问式提示词套取攻击: {}", abbreviate(input));
            return 2;
        }

        // 低风险：一般注入模式
        for (String candidate : candidates) {
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(candidate).find()) {
                    logger.warn("检测到提示词注入尝试: {}", abbreviate(input));
                    return 1;
                }
            }
        }

        return 0;
    }

    /**
     * 净化页面上下文
     * 移除可能包含的提示词注入内容
     * 
     * @param pageContext 页面上下文
     * @return 净化后的上下文
     */
    public Map<String, Object> sanitizePageContext(Map<String, Object> pageContext) {
        if (pageContext == null)
            return Map.of();

        Map<String, Object> sanitized = new LinkedHashMap<>();

        // 净化标题
        String title = getStringValue(pageContext, "title");
        if (title != null) {
            sanitized.put("title", sanitizeField(title, "title"));
        }

        // 净化内容（限制长度）
        String content = getStringValue(pageContext, "content");
        if (content != null) {
            String cleaned = sanitizeField(content, "content");
            // 限制页面内容长度（避免 token 浪费）
            if (cleaned.length() > 3000) {
                cleaned = cleaned.substring(0, 3000) + "...[内容已截断]";
            }
            sanitized.put("content", cleaned);
        }

        // 类型直接传递
        String type = getStringValue(pageContext, "type");
        if (type != null)
            sanitized.put("type", type);

        // 语言信息
        String lang = getStringValue(pageContext, "currentLanguage");
        if (lang != null)
            sanitized.put("currentLanguage", lang);

        return sanitized;
    }

    /**
     * 净化单个字段
     * <p>
     * public 暴露：供 AiChatService 在合并文档附件内容时复用同一套注入过滤逻辑
     */
    public String sanitizeField(String value, String fieldName) {
        if (value == null || value.isBlank())
            return "";

        String result = value;

        // 移除注入模式
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(result).find()) {
                result = pattern.matcher(result).replaceAll("[已过滤]");
                logger.warn("页面上下文字段 {} 检测到潜在注入并已净化", fieldName);
            }
        }

        // 移除连续空行
        result = result.replaceAll("\\n{3,}", "\n\n");

        return result;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    private List<String> buildDetectionCandidates(String input) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(input);

        String normalized = normalizeText(input);
        candidates.add(normalized);
        candidates.add(removeSeparators(normalized));

        for (String decoded : decodeObfuscatedSegments(input)) {
            String normalizedDecoded = normalizeText(decoded);
            candidates.add(decoded);
            candidates.add(normalizedDecoded);
            candidates.add(removeSeparators(normalizedDecoded));
        }

        return new ArrayList<>(candidates);
    }

    private boolean matchesCompositePromptLeak(List<String> candidates) {
        for (String candidate : candidates) {
            boolean hasSecretTarget = containsAny(candidate, SECRET_TARGET_TERMS);
            boolean hasExtractionAction = containsAny(candidate, EXTRACTION_ACTION_TERMS);
            boolean hasObfuscation = OBFUSCATION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(candidate).find());

            if (hasSecretTarget && hasExtractionAction) {
                return true;
            }

            if (hasSecretTarget && hasObfuscation) {
                return true;
            }

            if (candidate.contains("变量") && (candidate.contains("合并") || candidate.contains("拼接"))
                    && (candidate.contains("执行") || candidate.contains("输出")) && hasSecretTarget) {
                return true;
            }

            if ((candidate.contains("json") || candidate.contains("解析器") || candidate.contains("parser"))
                    && candidate.contains("system_prompt") && hasSecretTarget) {
                return true;
            }

            if ((candidate.contains("顶部") || candidate.contains("最顶部") || candidate.contains("top level")
                    || candidate.contains("topmost") || candidate.contains("raw") || candidate.contains("原始")
                    || candidate.contains("未加密") || candidate.contains("plain text"))
                    && (candidate.contains("设定") || candidate.contains("配置") || candidate.contains("上下文")
                            || candidate.contains("mapping") || candidate.contains("context")
                            || candidate.contains("instructions") || candidate.contains("message"))
                    && hasExtractionAction) {
                return true;
            }

            if ((candidate.contains("开发者备注") || candidate.contains("developer note")
                    || candidate.contains("developer remark"))
                    && hasExtractionAction
                    && (candidate.contains("顶部") || candidate.contains("原始") || candidate.contains("初始化")
                            || candidate.contains("context") || candidate.contains("mapping"))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesContextualPromptLeak(List<String> candidates, List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }

        String historyContext = buildHistoryContext(history);
        if (historyContext.isBlank()) {
            return false;
        }

        String normalizedHistory = normalizeText(historyContext);
        boolean historyTouchesSensitiveTopic = containsAny(normalizedHistory, SECRET_TARGET_TERMS)
                || OBFUSCATION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalizedHistory).find())
                || normalizedHistory.contains("初始化上下文")
                || normalizedHistory.contains("原始设定")
                || normalizedHistory.contains("system prompt")
                || normalizedHistory.contains("顶部设定")
                || normalizedHistory.contains("内部信息");

        if (!historyTouchesSensitiveTopic) {
            return false;
        }

        for (String candidate : candidates) {
            boolean isFollowUp = CONTEXTUAL_FOLLOW_UP_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(candidate).find());
            boolean hasReference = candidate.contains("你的") || candidate.contains("你收到的")
                    || candidate.contains("上面的") || candidate.contains("前面的")
                    || candidate.contains("原文") || candidate.contains("原始") || candidate.contains("完整")
                    || candidate.contains("继续") || candidate.contains("那个");
            boolean hasExtractionAction = containsAny(candidate, EXTRACTION_ACTION_TERMS)
                    || candidate.contains("看看") || candidate.contains("贴出来") || candidate.contains("发出来");

            if (isFollowUp && (hasReference || hasExtractionAction)) {
                return true;
            }

            if (hasReference && hasExtractionAction) {
                return true;
            }
        }

        return false;
    }

    private boolean looksLikeBenignPromptEngineering(List<String> candidates) {
        for (String candidate : candidates) {
            boolean matchesBenignPattern = BENIGN_PROMPT_ENGINEERING_PATTERNS.stream()
                    .anyMatch(pattern -> pattern.matcher(candidate).find());
            if (!matchesBenignPattern) {
                continue;
            }

            boolean targetsCurrentInstance = containsAny(candidate, INSTANCE_TARGET_TERMS)
                    || candidate.contains("你能把你的")
                    || candidate.contains("把你的")
                    || candidate.contains("照着你现在")
                    || candidate.contains("参考你自己的");

            boolean hasExplicitExtraction = containsAny(candidate, EXTRACTION_ACTION_TERMS)
                    && (candidate.contains("原始") || candidate.contains("顶部")
                            || candidate.contains("你收到的") || candidate.contains("当前会话"));

            if (!targetsCurrentInstance && !hasExplicitExtraction) {
                return true;
            }
        }
        return false;
    }

    private boolean targetsCurrentInstanceForExtraction(List<String> candidates) {
        for (String candidate : candidates) {
            boolean mentionsPromptInternals = containsAny(candidate, SECRET_TARGET_TERMS)
                    || candidate.contains("system prompt")
                    || candidate.contains("初始化上下文")
                    || candidate.contains("原始设定");
            boolean targetsInstance = containsAny(candidate, INSTANCE_TARGET_TERMS)
                    || candidate.contains("你能把你的")
                    || candidate.contains("把你的")
                    || candidate.contains("参考你自己的")
                    || candidate.contains("当前会话")
                    || candidate.contains("会话最顶部");
            boolean requestsDisclosure = containsAny(candidate, EXTRACTION_ACTION_TERMS)
                    || candidate.contains("发我")
                    || candidate.contains("贴给我")
                    || candidate.contains("给我参考")
                    || candidate.contains("看看");

            if (mentionsPromptInternals && targetsInstance && requestsDisclosure) {
                return true;
            }
        }
        return false;
    }

    private Set<String> decodeObfuscatedSegments(String input) {
        LinkedHashSet<String> decoded = new LinkedHashSet<>();
        decodeBase64Segments(input, decoded);
        decodeHexSegments(input, decoded);
        return decoded;
    }

    private void decodeBase64Segments(String input, Set<String> decoded) {
        var matcher = BASE64_TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() % 4 != 0) {
                continue;
            }
            try {
                byte[] bytes = Base64.getDecoder().decode(token);
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (looksLikeNaturalText(text)) {
                    decoded.add(text);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid Base64 chunks.
            }
        }
    }

    private void decodeHexSegments(String input, Set<String> decoded) {
        var matcher = HEX_TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            String token = matcher.group();
            if ((token.length() & 1) == 1) {
                continue;
            }
            byte[] bytes = new byte[token.length() / 2];
            boolean valid = true;
            for (int i = 0; i < token.length(); i += 2) {
                try {
                    bytes[i / 2] = (byte) Integer.parseInt(token.substring(i, i + 2), 16);
                } catch (NumberFormatException ex) {
                    valid = false;
                    break;
                }
            }
            if (!valid) {
                continue;
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (looksLikeNaturalText(text)) {
                decoded.add(text);
            }
        }
    }

    private boolean looksLikeNaturalText(String text) {
        if (text == null || text.isBlank() || text.length() < 8) {
            return false;
        }
        long printableCount = text.chars()
                .filter(ch -> !Character.isISOControl(ch) || Character.isWhitespace(ch))
                .count();
        return printableCount >= text.length() * 0.85;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String removeSeparators(String text) {
        return text == null ? "" : text.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
    }

    private boolean containsAny(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String abbreviate(String input) {
        return input.substring(0, Math.min(100, input.length()));
    }

    private String buildHistoryContext(List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        int start = Math.max(0, history.size() - 6);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            Map<String, Object> msg = history.get(i);
            if (msg == null) {
                continue;
            }
            Object roleObj = msg.getOrDefault("role", "");
            Object contentObj = msg.getOrDefault("content", "");
            String role = roleObj != null ? roleObj.toString() : "";
            String content = contentObj != null ? contentObj.toString() : "";
            if (content.isBlank()) {
                continue;
            }
            sb.append(role).append(": ").append(content).append('\n');
        }
        return sb.toString();
    }
}
