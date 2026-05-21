package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SysAuditLog;
import com.ld.poetry.service.SysAuditLogService;
import com.ld.poetry.vo.SysAuditLogQueryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/log")
public class SysAuditLogController {

    @Autowired
    private SysAuditLogService sysAuditLogService;

    @PostMapping("/list")
    @LoginCheck(0)
    public PoetryResult<Page<SysAuditLog>> list(@RequestBody(required = false) SysAuditLogQueryVO queryVO) {
        return sysAuditLogService.listLogs(queryVO);
    }
}
