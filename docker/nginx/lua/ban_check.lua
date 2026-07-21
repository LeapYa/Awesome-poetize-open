-- 封禁检查脚本（IP 黑名单 / CIDR 网段 / UA / 地区）
-- 在 Nginx access 阶段执行，命中任一规则直接返回 403，请求不会到达 Java 后端
-- 检查顺序：IP 黑名单 → CIDR 网段 → UA 封禁 → 地区封禁
--
-- 设计要点：
--   1. fail-open：Redis 不可用时放行，避免误伤全站
--      说明：Redis 在 Docker 内网/宝塔本地部署，外部攻击者无法直接打到。
--      fail-closed 会让 Redis 故障 = 全站对所有用户 403，等于给攻击者送 DDoS 放大器。
--      已封禁 IP 短暂恢复访问的风险，远小于全站瘫痪。
--   2. 连接池复用：一个请求内获取一次 Redis 连接，IP 检查 / 规则刷新复用
--      地区查询不再依赖 Redis，改用 xdb_searcher 直接查 xdb 文件
--   3. 短超时：连接 100ms，读取 200ms，避免阻塞 Nginx
--   4. 真实 IP：使用 Nginx 已计算好的 $real_client_ip 变量
--   5. 健康检查豁免：HEAD / 或 /actuator/health 等不拦截
--   6. 规则快照缓存：shared dict 缓存 15 秒，减少 Redis 读取
--   7. 地区封禁：直接查 xdb 文件（ip2region 官方 lua_c 扩展），不依赖 Redis 地区缓存
--      xdb 文件由 Java 端每月自动更新到共享卷，Nginx 每 60s 检测文件大小热重载
--      xdb_searcher 模块加载失败时地区封禁 fail-open，其它封禁类型不受影响
--      查询模式：vectorIndex cache（512KiB/worker），数据段通过文件 IO 读取，内核页缓存多 worker 共享

local redis = require "resty.redis"
local bit = require "bit"
local cjson = require "cjson.safe"

-- xdb_searcher：ip2region 官方 lua_c 扩展（兼容 Lua 5.1 / LuaJIT）
-- 编译产物 xdb_searcher.so 由 Dockerfile 复制到 /usr/local/openresty/lualib/
-- pcall 保护：模块缺失或加载失败时地区封禁 fail-open，不影响其它封禁类型
local xdb_ok, xdb_mod = pcall(require, "xdb_searcher")
if not xdb_ok then
    ngx.log(ngx.WARN, "ban_check: xdb_searcher 模块加载失败，地区封禁将 fail-open: ", xdb_mod)
    xdb_mod = nil
end

-- ========== Redis 连接参数（密码从环境变量读取，避免明文写在配置文件中） ==========
local REDIS_HOST = "redis"
local REDIS_PORT = 6379
local REDIS_PASSWORD = os.getenv("REDIS_PASSWORD")
-- 必须与 Java 端 spring.data.redis.database 保持一致，否则 Lua 层查不到 Java 写入的 key
local REDIS_DB = tonumber(os.getenv("REDIS_DB") or "0") or 0

-- 连接超时（毫秒）
local CONNECT_TIMEOUT = 100
local READ_TIMEOUT = 200

-- 连接池配置
local POOL_SIZE = 50
local POOL_MAX_IDLE = 10000  -- 毫秒

-- ========== 键前缀（与 Java CacheConstants 一致） ==========
-- IP 黑名单：直接拼 IP，EXISTS 检查
local BLACKLIST_KEY_PREFIX = "poetize:security:blacklist:"
-- 规则快照：GET 一次拿所有 UA/CIDR/Region 规则
local BAN_RULES_SNAPSHOT_KEY = "poetize:security:ban_rules_snapshot"

-- 规则缓存刷新间隔（秒）
local RULES_REFRESH_INTERVAL = 15
-- Redis 故障退避间隔（秒）：读取失败后短期冷却，避免每请求重连风暴
local RULES_REFRESH_BACKOFF = 5

-- ========== xdb 文件路径与 searcher 状态 ==========
-- 优先用共享卷里 Java 更新的版本（docker-compose: ip2region_data 卷，Nginx 只读挂载 /data/ip2region）
-- 共享卷不存在或为空时，回退到镜像自带的 fallback 版本（Dockerfile COPY 到 /usr/local/openresty/xdb/）
local XDB_V4_SHARED = "/data/ip2region/ip2region_v4.xdb"
local XDB_V6_SHARED = "/data/ip2region/ip2region_v6.xdb"
local XDB_V4_FALLBACK = "/usr/local/openresty/xdb/ip2region_v4.xdb"
local XDB_V6_FALLBACK = "/usr/local/openresty/xdb/ip2region_v6.xdb"

-- xdb 热重载检查间隔（秒）：Java 端每月更新共享卷里的 xdb，
-- Nginx 每 60s 检测文件大小变化，变化时重载 vectorIndex
local XDB_CHECK_INTERVAL = 60

-- worker 级 vectorIndex 缓存（每个 Nginx worker 独立持有 v_index 实例）
-- vectorIndex cache 模式：xdb.load_vector_index(path) → 每请求 xdb.new_with_vector_index(version, path, v_index) → searcher:search(ip) → searcher:close()
-- v_index 全局共享（512KiB），searcher 每请求创建/销毁（lua_c 文档要求每协程独立 searcher）
-- 数据段通过文件 IO 读取，内核页缓存多 worker 共享，内存占用极低
local v4_vindex = nil   -- vectorIndex 缓存（512KiB），worker 级共享
local v6_vindex = nil
local v4_path = nil     -- v_index 对应的 xdb 路径（创建 searcher 时需要）
local v6_path = nil
local v4_size = 0       -- 上次加载时的文件大小，用于热重载检测
local v6_size = 0
local v4_last_check = 0 -- 上次检查时间戳
local v6_last_check = 0

-- AI 爬虫硬名单（与 Java CacheConstants.BUILTIN_AI_CRAWLER_UA 保持同步）
-- 阻断训练数据采集，允许搜索/用户引用爬虫
-- 管理员可在后台单独删除某个硬名单，删除后其小写值会出现在快照的 disabled_ai_ua 中，匹配时跳过
local BLOCKED_AI_CRAWLER_UA = {
    "claudebot", "claude-web", "anthropic-ai", "gptbot",
}

-- 自动化工具 UA 关键词兜底常量：仅在快照未加载时使用。
-- 运行时从 ban_rules_snapshot.builtin_automation_ua 读取（由 Java CacheConstants.BUILTIN_AUTOMATION_UA 写入），
-- 消除双端硬编码同步问题。快照刷新成功后此常量不再生效。
local AUTOMATION_UA_KEYWORDS_FALLBACK = {
    "headlesschrome", "playwright", "puppeteer", "selenium", "webdriver", "phantomjs",
}

-- 健康检查路径豁免（避免监控探针被误拦截导致服务假死）
local HEALTH_CHECK_PATHS_ANY_METHOD = {
    -- Actuator 健康检查可能用 GET 或 HEAD，均豁免
    ["/actuator/health"] = true,
    ["/actuator/health/liveness"] = true,
    ["/actuator/health/readiness"] = true,
}

local HEALTH_CHECK_PATHS_HEAD_ONLY = {
    -- 首页 HEAD 是健康探测，GET 是正常访问，仅豁免 HEAD
    ["/"] = true,
}

-- shared dict：缓存规则快照 + 刷新时间戳（在 nginx.conf 中声明）
local rules_cache = ngx.shared.ban_rules_cache

-- 模块级规则缓存（per-worker）：refresh_rules_if_needed 成功后更新，
-- 每请求直接读内存，避免重复 shared dict get + cjson.decode 开销。
-- nil 表示尚未初始化，load_* 函数会 fallback 到 shared dict。
local cached_ua_rules = nil
local cached_cidr_rules = nil
local cached_region_rules = nil
local cached_disabled_ai_ua = nil
local cached_disabled_automation_ua = nil
-- 内置自动化工具 UA 硬名单（从快照读取，nil 时使用 FALLBACK 常量）
local cached_builtin_automation_ua = nil

-- ========== 工具函数 ==========

-- 获取客户端真实 IP
-- Nginx.conf 已通过 map 计算好 $real_client_ip，直接复用，回退到 $remote_addr
local function get_client_ip()
    local ip = ngx.var.real_client_ip
    if ip and ip ~= "" and ip ~= "unknown" then
        return ip
    end
    return ngx.var.remote_addr
end

-- IPv4 解析为 uint32
local function ipv4_to_uint32(ip)
    local a, b, c, d = ip:match("^(%d+)%.(%d+)%.(%d+)%.(%d+)$")
    if not a then return nil end
    a, b, c, d = tonumber(a), tonumber(b), tonumber(c), tonumber(d)
    if not a or not b or not c or not d then return nil end
    if a > 255 or b > 255 or c > 255 or d > 255 then return nil end
    return a * 16777216 + b * 65536 + c * 256 + d
end

-- IPv6 解析为 4 个 uint32（8 组 16bit 合并为 4 组 32bit）
-- 处理 :: 缩写，返回 {g0, g1, g2, g3} 或 nil
local function ipv6_to_uint32_groups(ip)
    if not ip then return nil end

    -- 检测并拆分 ::
    local left_str, right_str
    local dc_start, dc_end = ip:find("::")
    if dc_start then
        -- 仅允许一个 ::
        if ip:find("::", dc_end + 1) then
            return nil
        end
        left_str = ip:sub(1, dc_start - 1)
        right_str = ip:sub(dc_end + 1)
    else
        left_str = ip
        right_str = ""
    end

    -- 按冒号切分（忽略空段）
    local function split_groups(s)
        local t = {}
        if s and s ~= "" then
            for g in s:gmatch("([^:]+)") do
                table.insert(t, g)
            end
        end
        return t
    end

    local left_groups = split_groups(left_str)
    local right_groups = split_groups(right_str)
    local total = #left_groups + #right_groups

    if dc_start then
        -- 含 ::，:: 至少展开一组零，非零段最多 7 个
        if total > 7 then return nil end
    else
        -- 不含 ::，必须是 8 组
        if total ~= 8 then return nil end
    end

    -- 中间填零展开为 8 组
    local zeros_count = 8 - total
    local groups = {}
    for _, g in ipairs(left_groups) do table.insert(groups, g) end
    for _ = 1, zeros_count do table.insert(groups, "0") end
    for _, g in ipairs(right_groups) do table.insert(groups, g) end

    if #groups ~= 8 then return nil end

    -- 每 2 组 16bit 合并为 1 个 uint32
    local uint32_groups = {}
    for i = 1, 8, 2 do
        local hi = tonumber(groups[i], 16)
        local lo = tonumber(groups[i + 1], 16)
        if not hi or not lo then return nil end
        if hi > 0xFFFF or lo > 0xFFFF then return nil end
        table.insert(uint32_groups, hi * 65536 + lo)
    end

    return uint32_groups
end

-- CIDR 匹配（支持 IPv4 和 IPv6）
-- cidr_str: "IP/prefix"，例如 "192.168.1.0/24" 或 "2001:db8::/32"
-- ip: 客户端 IP
-- 返回 true 表示命中
-- 注意：LuaJIT bit 模块为 32 位有符号整数运算，band/rshift/bnot 对 32 位模式仍正确
local function cidr_match(cidr_str, ip)
    local slash = cidr_str:find("/")
    if not slash then return false end
    local cidr_ip = cidr_str:sub(1, slash - 1)
    local prefix_len = tonumber(cidr_str:sub(slash + 1))
    if not prefix_len or prefix_len < 0 then return false end

    if cidr_ip:find(":") then
        -- IPv6
        if not ip:find(":") then return false end  -- 类型不匹配
        if prefix_len > 128 then return false end
        local cidr_groups = ipv6_to_uint32_groups(cidr_ip)
        local ip_groups = ipv6_to_uint32_groups(ip)
        if not cidr_groups or not ip_groups then return false end

        if prefix_len == 0 then return true end  -- 匹配所有 IPv6

        -- 分组比较：每组 32 位，>=32 精确比较，<32 用掩码，==0 跳过
        local remaining = prefix_len
        for i = 1, 4 do
            if remaining <= 0 then break end
            local group_prefix
            if remaining >= 32 then
                group_prefix = 32
            else
                group_prefix = remaining
            end

            if group_prefix == 32 then
                -- 整组精确比较
                if cidr_groups[i] ~= ip_groups[i] then return false end
            elseif group_prefix == 0 then
                -- 跳过该组（保险分支，正常流程不会到达）
            else
                local mask = bit.bnot(bit.rshift(0xFFFFFFFF, group_prefix))
                if bit.band(cidr_groups[i], mask) ~= bit.band(ip_groups[i], mask) then
                    return false
                end
            end
            remaining = remaining - 32
        end
        return true
    else
        -- IPv4
        if ip:find(":") then return false end  -- 类型不匹配
        if prefix_len > 32 then return false end
        local cidr_uint = ipv4_to_uint32(cidr_ip)
        local ip_uint = ipv4_to_uint32(ip)
        if not cidr_uint or not ip_uint then return false end
        if prefix_len == 0 then return true end          -- 匹配所有 IPv4
        if prefix_len >= 32 then return ip_uint == cidr_uint end  -- /32 精确匹配
        local mask = bit.bnot(bit.rshift(0xFFFFFFFF, prefix_len))
        return bit.band(ip_uint, mask) == bit.band(cidr_uint, mask)
    end
end

-- UA 匹配（大小写不敏感）
-- 返回命中的 pattern（用于日志），未命中返回 nil
local function ua_match(ua_rules, ua)
    if not ua or ua == "" then return nil end
    if not ua_rules then return nil end
    local ua_lower = string.lower(ua)
    for _, rule in ipairs(ua_rules) do
        local pattern = rule.value
        if pattern and pattern ~= "" then
            local pattern_lower = string.lower(pattern)
            local mode = rule.matchMode
            if mode == "equals" then
                if ua_lower == pattern_lower then
                    return pattern
                end
            else
                -- contains（默认），plain=true 避免 Lua pattern 注入
                if string.find(ua_lower, pattern_lower, 1, true) then
                    return pattern
                end
            end
        end
    end
    return nil
end

-- 获取文件大小（字节），文件不存在返回 0
-- 用于 xdb 热重载检测：Java 端更新 xdb 后文件大小会变化
local function get_file_size(path)
    local f = io.open(path, "rb")
    if not f then return 0 end
    local size = f:seek("end")
    f:close()
    return size
end

-- 选择 xdb 文件路径（共享卷优先，fallback 兜底）
-- 返回 path, size；都不存在返回 nil, 0
local function resolve_xdb_path(shared_path, fallback_path)
    local shared_size = get_file_size(shared_path)
    if shared_size > 1024 * 1024 then  -- 大于 1MB 视为有效 xdb
        return shared_path, shared_size
    end
    local fallback_size = get_file_size(fallback_path)
    if fallback_size > 1024 * 1024 then
        return fallback_path, fallback_size
    end
    return nil, 0
end

-- 解析 xdb 返回的地区字符串
-- xdb 格式：国家|区域|省份|城市|ISP（与 Java Ip2RegionProvider.resolveLocationDetail 对齐）
-- 返回 nation, province（省份已去除后缀，与 Java VisitRegionNormalizer 逻辑一致）
local function parse_xdb_region(region_str)
    if not region_str or region_str == "" then
        return nil, nil
    end
    -- xdb v4 格式：国家|区域|城市|ISP|国家代码
    -- 区域在中国是省份（如"福建省"），在国外是州/省（如"Queensland"）
    local nation_str, province_str = region_str:match("^([^|]*)|([^|]*)")
    if not nation_str then
        return nil, nil
    end

    local function clean(s)
        if not s or s == "0" or s == "" then return nil end
        return s
    end

    local nation = clean(nation_str)
    local province = clean(province_str)

    -- 省份后缀去除（顺序敏感：先去复合后缀，再去民族后缀，最后去简单后缀）
    -- 与 Java replaceAll("省|市|自治区|特别行政区|壮族|回族|维吾尔", "") 等效
    -- 例如：广西壮族自治区 → 广西壮族 → 广西；新疆维吾尔自治区 → 新疆维吾尔 → 新疆
    if province then
        for _, suffix in ipairs({"自治区", "特别行政区", "维吾尔", "回族", "壮族", "市", "省"}) do
            province = province:gsub(suffix .. "$", "")
        end
        if province == "" then province = nil end
    end

    return nation, province
end

-- 地区匹配（大小写不敏感，前缀容错）
-- 返回命中的 value（用于日志），未命中返回 nil
local function region_match(region_rules, nation, province)
    if not region_rules then return nil end
    for _, rule in ipairs(region_rules) do
        local value = rule.value
        local rtype = rule.regionType
        if value and value ~= "" then
            local target
            if rtype == "country" then
                target = nation
            elseif rtype == "province" then
                target = province
            else
                -- 未知类型跳过
                target = nil
            end
            if target and target ~= "" then
                local value_lower = string.lower(value)
                local target_lower = string.lower(target)
                -- 精确匹配 或 目标以 value 为前缀
                if target_lower == value_lower
                   or string.find(target_lower, value_lower, 1, true) == 1 then
                    return value
                end
            end
        end
    end
    return nil
end

-- ========== xdb vectorIndex 管理（惰性加载 + 热重载） ==========

-- 惰性加载 v4 vectorIndex，每 XDB_CHECK_INTERVAL 秒检测文件大小变化热重载
-- 返回 v_index, path；不可用返回 nil, nil（fail-open）
local function ensure_v4_vindex()
    if not xdb_mod then return nil, nil end

    local now = ngx.now()
    -- 未到检查间隔，直接返回现有 v_index
    if v4_vindex and (now - v4_last_check) < XDB_CHECK_INTERVAL then
        return v4_vindex, v4_path
    end
    v4_last_check = now

    -- 选择 xdb 路径（共享卷优先，fallback 兜底）
    local path, size = resolve_xdb_path(XDB_V4_SHARED, XDB_V4_FALLBACK)
    if not path then
        if v4_vindex then
            ngx.log(ngx.WARN, "ban_check: v4 xdb 文件不可用，保留旧 v_index")
            return v4_vindex, v4_path
        end
        ngx.log(ngx.WARN, "ban_check: v4 xdb 文件不存在，地区封禁对 IPv4 将 fail-open")
        return nil, nil
    end

    -- 文件大小和路径未变，无需重载
    if v4_vindex and size == v4_size and path == v4_path then
        return v4_vindex, v4_path
    end

    -- 加载新版本 vectorIndex（512KiB，比 content cache 的 11MB 省 95% 内存）
    local v_index, err = xdb_mod.load_vector_index(path)
    if not v_index then
        ngx.log(ngx.WARN, "ban_check: 加载 v4 xdb vectorIndex 失败: ", path, " err=", err or "unknown")
        return v4_vindex, v4_path  -- 保留旧 v_index
    end

    v4_vindex = v_index
    v4_path = path
    v4_size = size
    ngx.log(ngx.INFO, "ban_check: v4 vectorIndex 已加载/重载: ", path, " size=", size)
    return v4_vindex, v4_path
end

-- 惰性加载 v6 vectorIndex（逻辑同 ensure_v4_vindex）
local function ensure_v6_vindex()
    if not xdb_mod then return nil, nil end

    local now = ngx.now()
    if v6_vindex and (now - v6_last_check) < XDB_CHECK_INTERVAL then
        return v6_vindex, v6_path
    end
    v6_last_check = now

    local path, size = resolve_xdb_path(XDB_V6_SHARED, XDB_V6_FALLBACK)
    if not path then
        if v6_vindex then
            ngx.log(ngx.WARN, "ban_check: v6 xdb 文件不可用，保留旧 v_index")
            return v6_vindex, v6_path
        end
        ngx.log(ngx.WARN, "ban_check: v6 xdb 文件不存在，地区封禁对 IPv6 将 fail-open")
        return nil, nil
    end

    if v6_vindex and size == v6_size and path == v6_path then
        return v6_vindex, v6_path
    end

    local v_index, err = xdb_mod.load_vector_index(path)
    if not v_index then
        ngx.log(ngx.WARN, "ban_check: 加载 v6 xdb vectorIndex 失败: ", path, " err=", err or "unknown")
        return v6_vindex, v6_path
    end

    v6_vindex = v_index
    v6_path = path
    v6_size = size
    ngx.log(ngx.INFO, "ban_check: v6 vectorIndex 已加载/重载: ", path, " size=", size)
    return v6_vindex, v6_path
end

-- 解析 IP 地区：根据 IP 类型选 v_index，每请求创建临时 searcher 查 xdb，返回 nation, province
-- 查询失败或 IP 无效返回 nil, nil（fail-open）
-- vectorIndex 模式下 searcher 非并发安全，每请求创建/销毁（lua_c 文档要求每协程独立 searcher）
local function resolve_ip_region(ip)
    if not ip or ip == "" then return nil, nil end

    local is_v6 = ip:find(":") ~= nil
    local v_index, path
    if is_v6 then
        v_index, path = ensure_v6_vindex()
    else
        v_index, path = ensure_v4_vindex()
    end
    if not v_index or not path then return nil, nil end

    -- 每请求创建临时 searcher（vectorIndex 模式要求每协程独立 searcher）
    local version = is_v6 and xdb_mod.IPv6 or xdb_mod.IPv4
    local searcher, err = xdb_mod.new_with_vector_index(version, path, v_index)
    if not searcher then
        ngx.log(ngx.WARN, "ban_check: 创建临时 searcher 失败 IP=", ip, " err=", err or "unknown")
        return nil, nil
    end

    -- pcall 保护：xdb search 可能因内部错误抛出异常
    local region_str
    local ok, search_err = pcall(function()
        region_str = searcher:search(ip)
    end)

    -- 无论查询成功与否都关闭 searcher（避免 fd 泄漏）
    pcall(function() searcher:close() end)

    if not ok then
        ngx.log(ngx.WARN, "ban_check: xdb search 异常 IP=", ip, " err=", search_err)
        return nil, nil
    end

    -- IP 未找到时返回空字符串，视为无地区信息
    if not region_str or region_str == "" then return nil, nil end

    return parse_xdb_region(region_str)
end

-- ========== Redis 连接管理 ==========

-- 获取一个已认证并选库的 Redis 连接，失败返回 nil
local function get_redis_connection()
    local red = redis:new()
    red:set_timeout(CONNECT_TIMEOUT, READ_TIMEOUT, READ_TIMEOUT)

    local ok, err = red:connect(REDIS_HOST, REDIS_PORT)
    if not ok then
        ngx.log(ngx.WARN, "ban_check: Redis connect failed: ", err)
        return nil
    end

    -- 认证（密码可能为空，本地开发环境）
    if REDIS_PASSWORD and REDIS_PASSWORD ~= "" then
        local ok, err = red:auth(REDIS_PASSWORD)
        if not ok then
            ngx.log(ngx.ERR, "ban_check: Redis auth failed: ", err)
            red:close()
            return nil
        end
    end

    -- 选择数据库（默认 0）
    if REDIS_DB and REDIS_DB ~= 0 then
        local ok, err = red:select(REDIS_DB)
        if not ok then
            ngx.log(ngx.WARN, "ban_check: Redis select db failed: ", err)
            red:close()
            return nil
        end
    end

    return red
end

-- 放回连接池
local function release_redis(red)
    if red then
        red:set_keepalive(POOL_MAX_IDLE, POOL_SIZE)
    end
end

-- ========== 规则缓存刷新 ==========

-- 检查并刷新规则快照到 shared dict
-- red: 已建立的 Redis 连接（复用同一连接）
-- 冷启动或距上次刷新超过 15 秒时同步从 Redis 读取
local function refresh_rules_if_needed(red)
    local now = ngx.now()
    local last_refresh = rules_cache:get("last_refresh") or 0

    if now - last_refresh < RULES_REFRESH_INTERVAL then
        return  -- 未到刷新时间
    end

    -- 同步从 Redis 读取规则快照
    local snapshot, err = red:get(BAN_RULES_SNAPSHOT_KEY)
    if not snapshot then
        -- Redis 操作失败，fail-open：保留旧缓存（若有）
        -- 设置短期退避（5s），避免 Redis 故障时每请求都重连重试造成连接风暴
        rules_cache:set("last_refresh", now - RULES_REFRESH_INTERVAL + RULES_REFRESH_BACKOFF)
        ngx.log(ngx.WARN, "ban_check: Redis get snapshot failed, backing off ", RULES_REFRESH_BACKOFF, "s: ", err)
        return
    end

    if snapshot == ngx.null then
        -- 快照不存在（Java 端尚未写入规则），清空缓存与模块级变量
        rules_cache:set("ua_rules", "")
        rules_cache:set("cidr_rules", "")
        rules_cache:set("region_rules", "")
        rules_cache:set("disabled_ai_ua", "")
        rules_cache:set("disabled_automation_ua", "")
        rules_cache:set("builtin_automation_ua", "")
        rules_cache:set("last_refresh", now)
        cached_ua_rules = {}
        cached_cidr_rules = {}
        cached_region_rules = {}
        cached_disabled_ai_ua = {}
        cached_disabled_automation_ua = {}
        cached_builtin_automation_ua = nil
        return
    end

    -- 解析 JSON 快照
    local rules = cjson.decode(snapshot)
    if not rules or type(rules) ~= "table" then
        -- decode 失败时设置退避，避免每请求重试导致日志风暴
        rules_cache:set("last_refresh", now - RULES_REFRESH_INTERVAL + RULES_REFRESH_BACKOFF)
        ngx.log(ngx.WARN, "ban_check: snapshot JSON decode failed, backing off ", RULES_REFRESH_BACKOFF, "s")
        return
    end

    -- 分别编码子规则集存入 shared dict
    local ua_rules = rules.ua or {}
    local cidr_rules = rules.cidr or {}
    local region_rules = rules.region or {}
    -- 被管理员禁用的内置 AI 爬虫硬名单（小写字符串数组）
    local disabled_ai_ua = rules.disabled_ai_ua or {}
    -- 被管理员禁用的内置自动化工具 UA 硬名单（小写字符串数组）
    local disabled_automation_ua = rules.disabled_automation_ua or {}
    -- 内置自动化工具 UA 硬名单（由 Java 单一数据源写入）
    local builtin_automation_ua = rules.builtin_automation_ua or {}

    rules_cache:set("ua_rules", cjson.encode(ua_rules))
    rules_cache:set("cidr_rules", cjson.encode(cidr_rules))
    rules_cache:set("region_rules", cjson.encode(region_rules))
    rules_cache:set("disabled_ai_ua", cjson.encode(disabled_ai_ua))
    rules_cache:set("disabled_automation_ua", cjson.encode(disabled_automation_ua))
    rules_cache:set("builtin_automation_ua", cjson.encode(builtin_automation_ua))
    rules_cache:set("last_refresh", now)

    -- 同步更新模块级缓存：disabled 列表转为 set（key=小写UA, value=true）
    cached_ua_rules = ua_rules
    cached_cidr_rules = cidr_rules
    cached_region_rules = region_rules
    local ai_set = {}
    for _, v in ipairs(disabled_ai_ua) do
        if type(v) == "string" then ai_set[string.lower(v)] = true end
    end
    cached_disabled_ai_ua = ai_set
    local automation_set = {}
    for _, v in ipairs(disabled_automation_ua) do
        if type(v) == "string" then automation_set[string.lower(v)] = true end
    end
    cached_disabled_automation_ua = automation_set
    -- 内置自动化工具 UA 列表转为 set（key=小写UA, value=true）
    local builtin_set = {}
    for _, v in ipairs(builtin_automation_ua) do
        if type(v) == "string" then builtin_set[string.lower(v)] = true end
    end
    cached_builtin_automation_ua = builtin_set
end

-- 从模块级缓存读取规则（fallback 到 shared dict）
-- 返回 ua_rules, cidr_rules, region_rules（可能为空 table）
local function load_rules_from_cache()
    if cached_ua_rules ~= nil then
        return cached_ua_rules, cached_cidr_rules, cached_region_rules
    end
    -- 冷启动 fallback：从 shared dict 解码
    local function decode(s)
        if not s or s == "" then return {} end
        local t = cjson.decode(s)
        if not t or type(t) ~= "table" then return {} end
        return t
    end
    local ua = decode(rules_cache:get("ua_rules"))
    local cidr = decode(rules_cache:get("cidr_rules"))
    local region = decode(rules_cache:get("region_rules"))
    cached_ua_rules = ua
    cached_cidr_rules = cidr
    cached_region_rules = region
    return ua, cidr, region
end

-- ========== 各项检查（命中时记录日志并返回 true） ==========

-- IP 黑名单检查
local function check_ip_blacklist(red, ip, uri)
    local res, err = red:exists(BLACKLIST_KEY_PREFIX .. ip)
    if not res then
        -- Redis 操作失败，fail-open
        ngx.log(ngx.WARN, "ban_check: Redis exists failed: ", err)
        return false
    end
    if res == 1 then
        ngx.log(ngx.WARN, "ban_check: blocked IP=", ip, " uri=", uri)
        return true
    end
    return false
end

-- CIDR 网段检查
local function check_cidr(cidr_rules, ip)
    if not cidr_rules then return false end
    for _, rule in ipairs(cidr_rules) do
        local value = rule.value
        if value and value ~= "" then
            if cidr_match(value, ip) then
                ngx.log(ngx.WARN, "ban_check: blocked CIDR=", value, " IP=", ip)
                return true
            end
        end
    end
    return false
end

-- 读取被禁用的内置 AI 爬虫硬名单，返回 set（小写 => true）
-- 优先走模块级缓存；冷启动 fallback 到 shared dict
local function load_disabled_ai_ua()
    if cached_disabled_ai_ua ~= nil then
        return cached_disabled_ai_ua
    end
    local s = rules_cache:get("disabled_ai_ua")
    if not s or s == "" then
        cached_disabled_ai_ua = {}
        return cached_disabled_ai_ua
    end
    local t = cjson.decode(s)
    if not t or type(t) ~= "table" then
        cached_disabled_ai_ua = {}
        return cached_disabled_ai_ua
    end
    local set = {}
    for _, v in ipairs(t) do
        if type(v) == "string" then
            set[string.lower(v)] = true
        end
    end
    cached_disabled_ai_ua = set
    return set
end

-- 读取被禁用的内置自动化工具 UA 硬名单，返回 set（小写 => true）
-- 优先走模块级缓存；冷启动 fallback 到 shared dict
local function load_disabled_automation_ua()
    if cached_disabled_automation_ua ~= nil then
        return cached_disabled_automation_ua
    end
    local s = rules_cache:get("disabled_automation_ua")
    if not s or s == "" then
        cached_disabled_automation_ua = {}
        return cached_disabled_automation_ua
    end
    local t = cjson.decode(s)
    if not t or type(t) ~= "table" then
        cached_disabled_automation_ua = {}
        return cached_disabled_automation_ua
    end
    local set = {}
    for _, v in ipairs(t) do
        if type(v) == "string" then
            set[string.lower(v)] = true
        end
    end
    cached_disabled_automation_ua = set
    return set
end

-- AI 爬虫硬名单检查（token 边界匹配，与 Java isBlockedAiCrawler 逻辑一致）
-- 不需要 Redis，纯字符串匹配，放在 Redis 连接之前以节省资源
-- disabled：被管理员禁用的硬名单 set，命中则跳过（实现"内置 UA 弃用后可删除"）
local function is_ai_crawler(ua, disabled)
    if not ua or ua == "" then return false end
    local ua_lower = string.lower(ua)
    for _, pattern in ipairs(BLOCKED_AI_CRAWLER_UA) do
        -- 管理员已禁用（删除）的内置硬名单跳过
        if not (disabled and disabled[pattern]) then
            local idx = string.find(ua_lower, pattern, 1, true)
            while idx do
                local end_pos = idx + #pattern
                -- 关键词后必须是边界：字符串结束或非字母数字
                local is_boundary = end_pos > #ua_lower
                        or not string.match(string.sub(ua_lower, end_pos, end_pos), "%w")
                -- 关键词前也必须是边界，避免 "mygptbot" 误匹配
                local is_prefix_boundary = idx == 1
                        or not string.match(string.sub(ua_lower, idx - 1, idx - 1), "%w")
                if is_boundary and is_prefix_boundary then
                    return true
                end
                idx = string.find(ua_lower, pattern, end_pos, true)
            end
        end
    end
    return false
end

-- 获取生效的自动化工具 UA 关键词列表（优先快照，兜底 FALLBACK 常量）
local function get_automation_ua_keywords()
    if cached_builtin_automation_ua ~= nil then
        return cached_builtin_automation_ua
    end
    -- 冷启动 fallback：从 shared dict 解码
    local raw = rules_cache:get("builtin_automation_ua")
    if raw and raw ~= "" then
        local t = cjson.decode(raw)
        if t and type(t) == "table" then
            local set = {}
            for _, v in ipairs(t) do
                if type(v) == "string" then set[string.lower(v)] = true end
            end
            cached_builtin_automation_ua = set
            return set
        end
    end
    -- 快照未加载，使用本地兜底常量
    return AUTOMATION_UA_KEYWORDS_FALLBACK
end

-- 自动化工具 UA 检查（简单 contains 匹配，与 Java isAutomationToolUa 逻辑一致）
-- 不需要 Redis，纯字符串匹配，放在 Redis 连接之前以节省资源
-- disabled：被管理员禁用的硬名单 set，命中则跳过（实现"内置 UA 弃用后可删除"）
local function is_automation_tool_ua(ua, disabled)
    if not ua or ua == "" then return false end
    local ua_lower = string.lower(ua)
    local keywords = get_automation_ua_keywords()
    -- keywords 为 set（key=关键词, value=true），直接遍历 key
    for keyword, _ in pairs(keywords) do
        if not (disabled and disabled[keyword]) then
            if string.find(ua_lower, keyword, 1, true) then
                return true
            end
        end
    end
    return false
end

-- UA 封禁检查
local function check_ua(ua_rules, ua, ip)
    local matched = ua_match(ua_rules, ua)
    if matched then
        ngx.log(ngx.WARN, "ban_check: blocked UA=", matched, " IP=", ip)
        return true
    end
    return false
end

-- 地区封禁检查（直接查 xdb 文件，不依赖 Redis 地区缓存）
-- xdb searcher 不可用时 fail-open，不影响其它封禁类型
local function check_region(region_rules, ip)
    if not region_rules then return false end
    local nation, province = resolve_ip_region(ip)
    if not nation and not province then return false end
    local matched = region_match(region_rules, nation, province)
    if matched then
        ngx.log(ngx.WARN, "ban_check: blocked Region=", matched, " IP=", ip)
        return true
    end
    return false
end

-- 返回 bare 403，不服务 SPA 源码，防止被封禁 IP 抓取网站内容
local function block_403()
    ngx.header.content_type = "text/html"
    ngx.status = 403
    ngx.say('<!DOCTYPE html><html><head><meta charset="utf-8"><title>403 Forbidden</title></head><body><h1>403 Forbidden</h1><p>访问被拒绝。</p></body></html>')
    return ngx.exit(403)
end

-- ========== 主逻辑 ==========

local function main()
    local uri = ngx.var.uri
    -- 健康检查路径豁免：Actuator 路径豁免 HEAD+GET，首页仅豁免 HEAD
    local method = ngx.var.request_method
    if HEALTH_CHECK_PATHS_ANY_METHOD[uri] and (method == "HEAD" or method == "GET") then
        return
    end
    if HEALTH_CHECK_PATHS_HEAD_ONLY[uri] and method == "HEAD" then
        return
    end

    local ip = get_client_ip()
    if not ip or ip == "" or ip == "unknown" then
        return  -- 无法识别 IP，放行
    end

    local ua = ngx.var.http_user_agent

    -- AI 爬虫硬名单检查（纯 UA 匹配，不需要 Redis，提前拦截节省连接资源）
    -- 从 shared dict 快照缓存读取被禁用的硬名单（非阻塞），管理员删除的内置 UA 会被跳过
    local disabled_ai_ua = load_disabled_ai_ua()
    if is_ai_crawler(ua, disabled_ai_ua) then
        ngx.log(ngx.WARN, "ban_check: blocked AI crawler UA=", ua, " IP=", ip)
        return block_403()
    end

    -- 自动化工具 UA 检查（纯 UA 匹配，不需要 Redis，提前拦截节省连接资源）
    -- 从 shared dict 快照缓存读取被禁用的硬名单（非阻塞），管理员删除的内置 UA 会被跳过
    local disabled_automation = load_disabled_automation_ua()
    if is_automation_tool_ua(ua, disabled_automation) then
        ngx.log(ngx.WARN, "ban_check: blocked automation tool UA=", ua, " IP=", ip)
        return block_403()
    end

    -- 获取 Redis 连接（一个请求内复用：IP 检查 / 规则刷新）
    -- 地区封禁已改用 xdb 直接查询，不依赖 Redis
    local red = get_redis_connection()
    if not red then
        -- Redis 不可用，fail-open
        return
    end

    -- 1. IP 黑名单检查
    if check_ip_blacklist(red, ip, uri) then
        release_redis(red)
        return block_403()
    end

    -- 刷新规则快照缓存（冷启动时同步加载，之后每 15 秒刷新一次）
    refresh_rules_if_needed(red)

    -- 从 shared dict 加载规则
    local ua_rules, cidr_rules, region_rules = load_rules_from_cache()

    -- 2. CIDR 网段检查
    if check_cidr(cidr_rules, ip) then
        release_redis(red)
        return block_403()
    end

    -- 3. UA 封禁检查
    if check_ua(ua_rules, ua, ip) then
        release_redis(red)
        return block_403()
    end

    -- 4. 地区封禁检查（直接查 xdb 文件，不需要 Redis 连接）
    if check_region(region_rules, ip) then
        release_redis(red)
        return block_403()
    end

    -- 全部通过，放回连接池
    release_redis(red)
end

main()
