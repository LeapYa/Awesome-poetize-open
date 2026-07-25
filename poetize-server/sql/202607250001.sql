-- ============================================================
-- 功能说明：修复 YisouSpider 旧访问记录的验证状态
-- 变更内容：
--   YisouSpider：IP 在官方阿里云 IP 段内的历史记录补录为 verified
--   （YisouSpider 的 IP 无 PTR 记录，旧版反向 DNS 验证必然超时失败，
--    新版改为内置官方 IP 段验证，跳过 DNS，与 360 Spider 同一方案）
--   IP 段来源：神马站长平台及公开抓取日志整理（42.120.x / 42.156.x / 106.11.15x）
-- 日期：2026-07-25
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- YisouSpider：官方 IP 段内的记录 → verified（含被误标为 spoofed 的记录）
UPDATE `history_info`
SET `bot_verify_status` = 'verified',
    `bot_verify_reason` = '官方IP段验证通过（迁移补录）',
    `ua_type`           = 'search_engine',
    `ua_name`           = 'YisouSpider'
WHERE `ua_name` IN ('YisouSpider', '疑似伪装 YisouSpider')
  AND (`bot_verify_status` != 'verified' OR `bot_verify_status` IS NULL)
  AND SUBSTRING_INDEX(`ip`, '.', 3) IN (
      '42.120.160', '42.120.161',
      '42.120.234', '42.120.235', '42.120.236',
      '42.156.136', '42.156.137', '42.156.138',
      '42.156.139', '42.156.254',
      '106.11.152', '106.11.153', '106.11.154',
      '106.11.155', '106.11.156', '106.11.157',
      '106.11.158', '106.11.159'
  );
