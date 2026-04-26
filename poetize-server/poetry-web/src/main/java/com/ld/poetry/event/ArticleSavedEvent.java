package com.ld.poetry.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文章保存事件
 */
@Data
@AllArgsConstructor
public class ArticleSavedEvent {
    
    /**
     * 文章ID
     */
    private Integer articleId;
    
    /**
     * 分类ID
     */
    private Integer sortId;

    /**
     * 标签ID
     */
    private Integer labelId;

    /**
     * 变更前分类ID
     */
    private Integer previousSortId;

    /**
     * 变更前标签ID
     */
    private Integer previousLabelId;
    
    /**
     * 异步任务ID（用于状态回传）
     */
    private String taskId;

    /**
     * 是否可见
     */
    private Boolean viewStatus;
    
    /**
     * 操作类型：CREATE, UPDATE, DELETE
     */
    private String operationType;
    
    /**
     * 是否提交到搜索引擎
     */
    private Boolean submitToSearchEngine;
}
