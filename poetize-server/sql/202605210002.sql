-- ============================================================
-- 功能说明：新增后台可审计系统日志
-- 变更内容：
--   1. sys_audit_log：保存登录、安全与关键后台操作日志
--   2. 查询索引：按时间、类型、结果、用户、IP 加速后台筛选
-- 日期：2026-05-21
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `log_type` varchar(32) NOT NULL COMMENT '日志类型 LOGIN/SECURITY/OPERATION',
  `action` varchar(64) NOT NULL COMMENT '操作动作',
  `success` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否成功[0:否,1:是]',
  `masked_account` varchar(128) DEFAULT NULL COMMENT '脱敏账号',
  `user_id` int DEFAULT NULL COMMENT '用户ID',
  `username` varchar(64) DEFAULT NULL COMMENT '用户名',
  `ip` varchar(128) DEFAULT NULL COMMENT 'IP地址',
  `location` varchar(128) DEFAULT NULL COMMENT '地理位置',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `request_uri` varchar(512) DEFAULT NULL COMMENT '请求路径',
  `target_type` varchar(64) DEFAULT NULL COMMENT '目标对象类型',
  `target_id` varchar(128) DEFAULT NULL COMMENT '目标对象ID',
  `summary` varchar(512) DEFAULT NULL COMMENT '摘要',
  `detail` json DEFAULT NULL COMMENT '脱敏详情JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_create_time` (`create_time`),
  KEY `idx_audit_type_time` (`log_type`, `create_time`),
  KEY `idx_audit_success_time` (`success`, `create_time`),
  KEY `idx_audit_user_time` (`user_id`, `create_time`),
  KEY `idx_audit_ip_time` (`ip`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台审计日志表';
