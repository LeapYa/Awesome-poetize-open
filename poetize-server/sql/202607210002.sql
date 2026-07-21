-- ============================================================
-- 功能说明：修复 360 Spider / YisouSpider 旧访问记录的验证状态
-- 变更内容：
--   1. 360 Spider：IP 在官方 24 个 /24 段内的历史记录补录为 verified
--      （旧版使用 DNS 反向查询验证，但 360 官方不支持 nslookup，导致全部失败）
--   2. YisouSpider：旧记录重置为 unknown（新访问时走带超时控制的 DNS 验证）
--   来源：https://www.so.com/help/spider_ip.html
-- 日期：2026-07-21
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- 1. 360 Spider：官方 IP 段内的记录 → verified（含被误标为 spoofed 的记录）
UPDATE `history_info`
SET `bot_verify_status` = 'verified',
    `bot_verify_reason` = '官方IP段验证通过（迁移补录）',
    `ua_type`           = 'search_engine',
    `ua_name`           = '360 Spider'
WHERE `ua_name` IN ('360 Spider', '疑似伪装 360 Spider')
  AND (`bot_verify_status` != 'verified' OR `bot_verify_status` IS NULL)
  AND SUBSTRING_INDEX(`ip`, '.', 3) IN (
      '180.153.232', '180.153.234', '180.153.236',
      '180.163.220',
      '42.236.10', '42.236.12', '42.236.13', '42.236.14',
      '42.236.15', '42.236.16', '42.236.17', '42.236.46',
      '42.236.48', '42.236.49', '42.236.50', '42.236.51',
      '42.236.52', '42.236.53', '42.236.54', '42.236.55',
      '42.236.99', '42.236.101', '42.236.102', '42.236.103'
  );

-- 2. YisouSpider：非 verified 的旧记录重置为 unknown（等待新访问重新验证）
UPDATE `history_info`
SET `bot_verify_status` = 'unknown',
    `bot_verify_reason` = '等待重新验证（迁移重置）',
    `ua_type`           = 'search_engine',
    `ua_name`           = 'YisouSpider'
WHERE `ua_name` IN ('YisouSpider', '疑似伪装 YisouSpider')
  AND (`bot_verify_status` NOT IN ('verified', 'unknown') OR `bot_verify_status` IS NULL);
