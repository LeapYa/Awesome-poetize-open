package com.ld.poetry.enums;

public enum ResourceMigrationItemStatus {
    PENDING,
    SNAPSHOTTING,
    SNAPSHOT_READY,
    WRITING,
    TARGET_WRITTEN,
    VERIFYING,
    VERIFIED,
    SWITCHED,
    SUCCESS,
    SKIPPED,
    SOURCE_CHANGED,
    FAILED,

    // 兼容升级前尚未完成的任务，恢复时会映射到新的状态链。
    UPLOADING,
    UPLOADED;

    public boolean isTerminal() {
        return this == SUCCESS || this == SKIPPED || this == SOURCE_CHANGED || this == FAILED;
    }
}