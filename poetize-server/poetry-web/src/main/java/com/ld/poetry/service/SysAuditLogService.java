package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SysAuditLog;
import com.ld.poetry.vo.SysAuditLogQueryVO;

import java.util.Map;

public interface SysAuditLogService extends IService<SysAuditLog> {

    PoetryResult<Page<SysAuditLog>> listLogs(SysAuditLogQueryVO queryVO);

    void recordLogin(String action, boolean success, String account, Integer userId, String username,
                     String summary, Map<String, Object> detail);

    void recordSecurity(String action, boolean success, String account, Integer userId, String username,
                        String summary, Map<String, Object> detail);

    void recordOperation(String logType, String action, boolean success, String targetType, String targetId,
                         String summary, Map<String, Object> detail);

    int cleanExpiredLogs(int retentionDays);
}
