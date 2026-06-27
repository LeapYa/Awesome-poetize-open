package com.ld.poetry.service.ai;

import com.ld.poetry.entity.SysAiConfig;

import java.util.Map;

/**
 * AI 生图服务。
 *
 * <p>主要职责：
 * <ul>
 *   <li>从文章标题与内容提炼生图 prompt（可选 LLM 提炼）</li>
 *   <li>调度对应 provider 的生图客户端生成图片</li>
 *   <li>下载/转换图片字节并入库存储，返回可访问 URL</li>
 *   <li>提供连接测试能力，供后台「测试」按钮调用</li>
 * </ul>
 */
public interface AiImageService {

    /**
     * 根据文章标题与正文 HTML 生成一张封面图，并落库存储。
     *
     * @param articleTitle      文章标题（可空，但建议非空以获得更好效果）
     * @param articleContentHtml 文章正文（HTML / Markdown 混合）
     * @return 最终存储后的可访问图片 URL
     * @throws Exception 生图或存储失败时抛出
     */
    String generateCoverFromArticle(String articleTitle, String articleContentHtml) throws Exception;

    /**
     * 测试 AI 生图连接是否可用。
     *
     * <p>用于后台「测试」按钮，传入完整 {@link SysAiConfig}（含 imageConfig JSON）。
     * 不做敏感字段保留，调用方需自行处理。
     *
     * <p>当 title/content 非空时，走完整生图流程（含 LLM prompt 提炼），
     * 用于评估模型对真实文章内容的生图效果；两者均为空时使用固定测试 prompt。
     *
     * @param config  完整 AI 配置
     * @param title   测试文章标题（可空）
     * @param content 测试文章正文（可空，HTML/Markdown 均可）
     * @return 测试结果，至少包含 success / message，可能包含 url / prompt / durationMs
     */
    Map<String, Object> testImageGeneration(SysAiConfig config, String title, String content);
}
