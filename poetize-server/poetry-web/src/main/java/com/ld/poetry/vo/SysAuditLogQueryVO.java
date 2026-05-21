package com.ld.poetry.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.entity.SysAuditLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SysAuditLogQueryVO extends Page<SysAuditLog> {

    private String logType;

    private String action;

    private Boolean success;

    private String searchKey;

    private String ip;

    private String startTime;

    private String endTime;
}
