-- ============================================================
-- 功能说明：清理 sys_config 中废弃的"访问统计忽略IP列表"配置
-- 变更内容：
--   删除 sys_config 中 config_key = 'visit.ignore.ips' 的记录。
--   访问统计忽略IP列表已迁移到 Redis 持久化存储（key: poetize:visit:ignore_ips），
--   由后台管理接口维护，不再依赖 sys_config 表。
--   执行前请确认已升级到使用 Redis 存储的版本，否则会丢失旧忽略列表。
-- 日期：2026-07-21
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

DELETE FROM `poetize`.`sys_config`
WHERE `config_key` = 'visit.ignore.ips'
  AND `config_type` = '1';
