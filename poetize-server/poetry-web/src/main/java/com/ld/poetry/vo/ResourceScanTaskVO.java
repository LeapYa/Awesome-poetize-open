package com.ld.poetry.vo;

import lombok.Data;

import java.util.List;

/**
 * 资源检测任务状态VO
 * 用于异步检测任务的状态轮询
 */
@Data
public class ResourceScanTaskVO {

    /**
     * 任务状态枚举
     */
    public enum Status {
        /**
         * 排队中
         */
        PENDING,
        /**
         * 执行中
         */
        RUNNING,
        /**
         * 成功完成
         */
        SUCCESS,
        /**
         * 失败
         */
        FAILED,
        /**
         * 已取消
         */
        CANCELLED
    }

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private Status status;

    /**
     * 资源类型（invalid/orphan）
     */
    private String resourceType;

    /**
     * 总数
     */
    private int total;

    /**
     * 已处理数
     */
    private int processed;

    /**
     * 检测到的无效/孤儿资源数
     */
    private int hitCount;

    /**
     * 错误信息（FAILED时）
     */
    private String errorMessage;

    /**
     * 任务创建时间（毫秒时间戳）
     */
    private long createdAt;

    /**
     * 任务开始执行时间（毫秒时间戳）
     */
    private long startedAt;

    /**
     * 任务完成时间（毫秒时间戳）
     */
    private long finishedAt;

    /**
     * 命中的资源ID列表（SUCCESS时返回，供前端分页展示）
     */
    private List<Integer> hitResourceIds;

    public ResourceScanTaskVO() {
    }

    public ResourceScanTaskVO(String taskId, String resourceType) {
        this.taskId = taskId;
        this.resourceType = resourceType;
        this.status = Status.PENDING;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 计算进度百分比（0-100）
     */
    public int getProgressPercent() {
        if (total <= 0) {
            return status == Status.SUCCESS ? 100 : 0;
        }
        return Math.min(100, (int) ((long) processed * 100 / total));
    }
}
