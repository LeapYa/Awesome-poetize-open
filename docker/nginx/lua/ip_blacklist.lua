-- IP 黑名单检查脚本
-- 在 Nginx access 阶段执行，查 Redis 黑名单，命中则直接返回 403
-- 设计要点：
--   1. fail-open：Redis 不可用时放行，避免误伤全站
--      说明：Redis 在 Docker 内网/宝塔本地部署，外部攻击者无法直接打到。
--      fail-closed 会让 Redis 故障 = 全站对所有用户 403，等于给攻击者送 DDoS 放大器。
--      已封禁 IP 短暂恢复访问的风险，远小于全站瘫痪。
--   2. 连接池复用：避免每次请求新建 Redis 连接
--   3. 短超时：连接 100ms，读取 200ms，避免阻塞 Nginx
--   4. 真实 IP：使用 Nginx 已计算好的 $real_client_ip 变量
--   5. 健康检查豁免：HEAD / 或 /actuator/health 等不拦截

local redis = require "resty.redis"

-- Redis 连接参数（密码从环境变量读取，避免明文写在配置文件中）
local REDIS_HOST = "redis"
local REDIS_PORT = 6379
local REDIS_PASSWORD = os.getenv("REDIS_PASSWORD")
-- 必须与 Java 端 spring.data.redis.database 保持一致，否则 Lua 层查不到 Java 写入的黑名单 key
local REDIS_DB = tonumber(os.getenv("REDIS_DB") or "0") or 0

-- 黑名单 key 前缀，必须与 Java 端 CacheConstants.IP_BLACKLIST_PREFIX 保持一致
local BLACKLIST_KEY_PREFIX = "poetize:security:blacklist:"

-- 连接超时（毫秒）
local CONNECT_TIMEOUT = 100
local READ_TIMEOUT = 200

-- 连接池配置
local POOL_SIZE = 50
local POOL_MAX_IDLE = 10000  -- 毫秒

-- 健康检查路径豁免（避免监控探针被误拦截导致服务假死）
local HEALTH_CHECK_PATHS = {
    ["/"] = true,
    ["/actuator/health"] = true,
    ["/actuator/health/liveness"] = true,
    ["/actuator/health/readiness"] = true,
}

-- 获取客户端真实 IP
-- Nginx.conf 已通过 map 计算好 $real_client_ip，直接复用
local function get_client_ip()
    local ip = ngx.var.real_client_ip
    if ip and ip ~= "" and ip ~= "unknown" then
        return ip
    end
    return ngx.var.remote_addr
end

-- 检查 IP 是否在黑名单中
-- 返回 true 表示被封禁，false 表示放行
local function is_blacklisted(ip)
    if not ip or ip == "" or ip == "unknown" then
        return false
    end

    local red = redis:new()
    red:set_timeout(CONNECT_TIMEOUT, READ_TIMEOUT, READ_TIMEOUT)

    local ok, err = red:connect(REDIS_HOST, REDIS_PORT)
    if not ok then
        -- Redis 不可用，fail-open 放行，记录错误
        ngx.log(ngx.WARN, "ip_blacklist: Redis connect failed: ", err)
        return false
    end

    -- 认证（密码可能为空，本地开发环境）
    if REDIS_PASSWORD and REDIS_PASSWORD ~= "" then
        local ok, err = red:auth(REDIS_PASSWORD)
        if not ok then
            ngx.log(ngx.ERR, "ip_blacklist: Redis auth failed: ", err)
            red:close()
            return false
        end
    end

    -- 选择数据库（默认 0）
    if REDIS_DB and REDIS_DB ~= 0 then
        local ok, err = red:select(REDIS_DB)
        if not ok then
            ngx.log(ngx.WARN, "ip_blacklist: Redis select db failed: ", err)
        end
    end

    -- 检查 key 是否存在（O(1) 操作）
    local key = BLACKLIST_KEY_PREFIX .. ip
    local res, err = red:exists(key)

    -- 放回连接池（成功时）或关闭（失败时）
    if res then
        red:set_keepalive(POOL_MAX_IDLE, POOL_SIZE)
    else
        red:close()
        ngx.log(ngx.WARN, "ip_blacklist: Redis exists failed: ", err)
        return false
    end

    -- res 为存在的 key 数量（1 表示存在）
    return res == 1
end

-- 主逻辑
local function main()
    -- 健康检查路径豁免
    local uri = ngx.var.uri
    if HEALTH_CHECK_PATHS[uri] and ngx.var.request_method == "HEAD" then
        return
    end

    local ip = get_client_ip()
    if is_blacklisted(ip) then
        ngx.log(ngx.WARN, "ip_blacklist: blocked IP=", ip, " uri=", uri)
        -- 返回 bare 403，不服务 SPA 源码，防止被封禁 IP 抓取网站内容
        ngx.header.content_type = "text/html"
        ngx.status = 403
        ngx.say('<!DOCTYPE html><html><head><meta charset="utf-8"><title>403 Forbidden</title></head><body><h1>403 Forbidden</h1><p>访问被拒绝。</p></body></html>')
        return ngx.exit(403)
    end
end

main()
