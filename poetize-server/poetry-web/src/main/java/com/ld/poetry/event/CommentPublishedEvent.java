package com.ld.poetry.event;

/**
 * 评论发布事件。
 * 只由用户评论发布流程触发，系统/AI 自动回复不会再次发布该事件。
 *
 * @param commenterIp 评论者客户端 IP（发布线程捕获，供审计与 AI 回复上下文）
 * @param commenterLocation 评论者归属地（评论保存时已解析，供 AI 回复自然问候）
 */
public record CommentPublishedEvent(
        Integer commentId,
        Integer source,
        String type,
        Integer userId,
        Integer parentCommentId,
        Integer parentUserId,
        Integer floorCommentId,
        String commentContent,
        String commentInfo,
        String commenterIp,
        String commenterLocation) {
}
