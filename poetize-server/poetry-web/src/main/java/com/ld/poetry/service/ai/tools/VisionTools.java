package com.ld.poetry.service.ai.tools;

import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.DynamicChatClientFactory;
import com.ld.poetry.service.ai.ImageMediaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;

/**
 * 图像识别工具 — 当主模型不支持视觉时，通过 Function Calling 调用用户配置的视觉模型分析图片。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>用户上传图片，前端将图片 URL 随消息发送</li>
 *   <li>主模型（文本模型）收到包含图片 URL 的用户消息</li>
 *   <li>主模型调用 {@code analyze_image} 工具，传入图片 URL</li>
 *   <li>本工具使用用户配置的视觉模型（vision_provider/vision_api_key/vision_model）识别图片</li>
 *   <li>返回图片描述文本，主模型据此回答用户问题</li>
 * </ol>
 */
@Service
public class VisionTools {

    private static final Logger log = LoggerFactory.getLogger(VisionTools.class);

    private final SysAiConfigService sysAiConfigService;
    private final DynamicChatClientFactory chatClientFactory;

    public VisionTools(SysAiConfigService sysAiConfigService, DynamicChatClientFactory chatClientFactory) {
        this.sysAiConfigService = sysAiConfigService;
        this.chatClientFactory = chatClientFactory;
    }

    @Tool(description = "分析图片内容并返回详细的文字描述。当用户消息中包含图片链接时，调用此工具获取图片的内容描述，然后基于描述回答用户问题。")
    public String analyzeImage(
            @ToolParam(description = "图片的完整URL地址") String imageUrl,
            @ToolParam(description = "用户关于图片的具体问题或关注点，可选。如未提供则返回图片的全面描述。", required = false) String question) {

        log.info("VisionTools.analyzeImage 被调用: imageUrl={}, question={}", imageUrl, question);

        if (!StringUtils.hasText(imageUrl)) {
            return "图片URL为空，无法分析。";
        }

        // SSRF 防护：拒绝内网/非 HTTP(S) 地址
        if (!ImageMediaUtils.isAllowedImageUrl(imageUrl)) {
            log.warn("图片URL被SSRF防护拦截: imageUrl={}", imageUrl);
            return "图片URL不合法，无法分析。";
        }

        // 获取已解密的 AI 配置（内部调用）
        SysAiConfig config = sysAiConfigService.getAiChatConfigInternal("default");
        if (config == null) {
            return "AI配置未找到，无法使用图片分析功能。";
        }

        // 检查视觉模型是否已配置
        if (!StringUtils.hasText(config.getVisionProvider())
                || !StringUtils.hasText(config.getVisionApiKey())
                || !StringUtils.hasText(config.getVisionModel())) {
            return "视觉模型未配置，无法分析图片。请联系管理员在后台配置视觉模型。";
        }

        try {
            // 构建视觉模型的临时配置
            SysAiConfig visionConfig = new SysAiConfig();
            visionConfig.setProvider(config.getVisionProvider());
            visionConfig.setApiKey(config.getVisionApiKey());
            visionConfig.setApiBase(config.getVisionApiBase());
            visionConfig.setModel(config.getVisionModel());
            visionConfig.setTemperature(java.math.BigDecimal.valueOf(0.3)); // 低温度以获得稳定的描述

            // 创建视觉模型 ChatModel
            ChatModel visionChatModel = chatClientFactory.createChatModel(visionConfig);

            // 构建多模态消息
            String prompt = StringUtils.hasText(question)
                    ? String.format("请仔细分析这张图片，重点回答以下问题：%s\n\n请提供详细、准确的描述。", question)
                    : "请详细描述这张图片的内容，包括其中的物体、人物、文字、数字、颜色、场景、布局等所有可见信息。描述应当全面且准确。";

            Media imageMedia = Media.builder()
                    .mimeType(ImageMediaUtils.resolveMimeType(imageUrl))
                    .data(URI.create(imageUrl))
                    .build();

            UserMessage visionMessage = UserMessage.builder()
                    .text(prompt)
                    .media(imageMedia)
                    .build();

            // 调用视觉模型
            log.info("调用视觉模型: provider={}, model={}", config.getVisionProvider(), config.getVisionModel());
            ChatResponse response = visionChatModel.call(new Prompt(List.of(visionMessage)));

            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return "视觉模型返回空结果，无法分析图片。";
            }

            String description = response.getResult().getOutput().getText();
            log.info("视觉模型分析完成，描述长度: {}", description != null ? description.length() : 0);

            return StringUtils.hasText(description) ? description : "视觉模型未返回有效描述。";

        } catch (Exception e) {
            log.error("视觉模型调用失败: imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return "图片分析失败: " + e.getMessage();
        }
    }
}
