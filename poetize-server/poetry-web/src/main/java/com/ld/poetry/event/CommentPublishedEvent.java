package com.ld.poetry.event;

/**
 * 评论发布事件。
 * 只由用户评论发布流程触发，系统/AI 自动回复不会再次发布该事件。
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
        String commentInfo) {
}
