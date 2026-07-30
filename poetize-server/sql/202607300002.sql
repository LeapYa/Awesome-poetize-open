-- ============================================================
-- 功能说明：修复 SeznamBot 旧访问记录的 UA 分类
-- 变更内容：
--   SeznamBot（捷克 Seznam.cz 搜索引擎）此前不在搜索引擎关键字列表中，
--   历史记录被兜底归类为 crawler/Bot；本脚本按 UA 关键字回改为 search_engine。
--   历史记录无法回溯反向 DNS 验证，验证状态统一补录为 unknown，
--   后续新访问由 SearchEngineVerifier 走 PTR + 正向回指验证（seznam.cz 后缀）。
-- 日期：2026-07-30
-- 本脚本可安全重复执行（幂等性）
-- ============================================================

-- SeznamBot：UA 含 seznambot 且被归类为爬虫的记录 → search_engine（未验证）
UPDATE `history_info`
SET `ua_type`           = 'search_engine',
    `ua_name`           = 'SeznamBot',
    `bot_verify_status` = 'unknown',
    `bot_verify_reason` = '历史记录未执行搜索引擎IP验证（迁移补录）'
WHERE LOWER(`user_agent`) LIKE '%seznambot%'
  AND `ua_type` != 'search_engine';
