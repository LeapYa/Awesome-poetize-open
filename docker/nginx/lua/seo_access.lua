local cjson = require "cjson"
local http = require "resty.http"

-- 使用ngx.ctx来存储数据，避免ngx.var的限制
ngx.ctx.seo_data = ""
ngx.ctx.title = ""
ngx.ctx.has_title = false
-- 添加注入标志，防止重复插入
ngx.ctx.seo_injected = false
ngx.ctx.title_injected = false

-- 使用HTTP客户端的API调用函数，支持HTTP/2
local function fetch_api(api_path, query_params)
    -- 尝试从缓存读取数据
    local seo_cache = ngx.shared.seo_cache
    local cache_key = api_path
    
    -- 如果有查询参数，将其添加到缓存键中
    if query_params then
        for k, v in pairs(query_params) do
            if type(v) == "string" and v ~= "" then
                cache_key = cache_key .. ":" .. k .. "=" .. v
            end
        end
    end
    
    -- 检查缓存中是否有数据
    local cached_data = seo_cache:get(cache_key)
    if cached_data then
        -- ngx.log(ngx.INFO, "从缓存读取SEO数据: " .. cache_key)
        return cached_data
    end
    
    -- ngx.log(ngx.INFO, "发起HTTP请求: " .. api_path)
    
    -- 创建HTTP客户端
    local httpc = http.new()
    httpc:set_timeout(5000) -- 5秒超时
    
    -- 构建请求URL
    local backend_url = "http://poetize-python:5000" .. api_path
    
    -- 构建查询参数
    local query_string = ""
    if query_params then
        local params = {}
        for k, v in pairs(query_params) do
            if type(v) == "string" and v ~= "" then
                table.insert(params, k .. "=" .. ngx.escape_uri(v))
            end
        end
        if #params > 0 then
            query_string = "?" .. table.concat(params, "&")
        end
    end
    
    -- 发起HTTP请求
    local res, err = httpc:request_uri(backend_url .. query_string, {
        method = "GET",
        headers = {
            ["Host"] = ngx.var.host,
            ["X-Real-IP"] = ngx.var.remote_addr,
            ["X-Forwarded-For"] = ngx.var.proxy_add_x_forwarded_for or ngx.var.remote_addr,
            ["X-Forwarded-Proto"] = ngx.var.scheme,
            ["User-Agent"] = "nginx-lua-seo-client"
        }
    })
    
    if not res then
        ngx.log(ngx.ERR, "HTTP请求失败: " .. (err or "未知错误"))
        return nil
    end
    
    -- ngx.log(ngx.INFO, "HTTP响应状态码: " .. res.status)
    if res.body then
        -- ngx.log(ngx.INFO, "HTTP响应前100个字符: " .. string.sub(res.body, 1, 100))
    end
    
    if res.status ~= 200 then
        ngx.log(ngx.ERR, "HTTP请求状态码异常: " .. res.status)
        return nil
    end
    
    if not res.body or res.body == "" then
        ngx.log(ngx.ERR, "HTTP返回内容为空")
        return nil
    end
    
    -- 尝试解析JSON
    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.log(ngx.ERR, "HTTP返回内容不是有效的JSON: " .. res.body)
        -- 尝试直接使用返回的内容作为字符串
        if type(res.body) == "string" then
            return res.body
        end
        return nil
    end
    
    -- 记录解析后的JSON数据结构
    -- ngx.log(ngx.INFO, "解析后的JSON数据类型: " .. type(data))
    if type(data) == "table" then
        if data.status then
            -- ngx.log(ngx.INFO, "JSON status字段: " .. data.status)
        end
        if data.data then
            -- ngx.log(ngx.INFO, "JSON data字段类型: " .. type(data.data))
            if type(data.data) == "string" then
                -- ngx.log(ngx.INFO, "JSON data字段前50个字符: " .. string.sub(data.data, 1, 50))
            end
        else
            ngx.log(ngx.WARN, "JSON中无data字段")
        end
    end
    
    -- 处理JSON格式的返回数据
    if not data then
        ngx.log(ngx.ERR, "解析的JSON数据为空")
        return nil
    end
    
    -- 检查JSON数据中的状态
    if data.status and data.status ~= "success" then
        ngx.log(ngx.ERR, "HTTP返回状态不成功: " .. data.status .. (data.message or ""))
        return nil
    end
    
    -- 检查data字段
    if not data.data then
        ngx.log(ngx.ERR, "HTTP返回中缺少data字段")
        -- 尝试直接返回JSON对象
        return cjson.encode(data)
    end
    
    -- 根据data的类型返回适当的值，并处理title标签
    local result
    if type(data.data) == "string" then
        -- 提取title标签中的内容
        local title_start = string.find(data.data, "<title>")
        local title_end = string.find(data.data, "</title>")
        
        if title_start and title_end and title_end > title_start then
            -- 设置title变量，但从返回数据中移除title标签
            ngx.ctx.title = string.sub(data.data, title_start + 7, title_end - 1)
            -- ngx.log(ngx.INFO, "已提取title并从SEO数据中移除: " .. ngx.ctx.title)
            
            -- 移除title标签
            local before_title = string.sub(data.data, 1, title_start - 1) or ""
            local after_title = string.sub(data.data, title_end + 8) or ""
            result = before_title .. after_title
        else
            result = data.data
        end
    elseif type(data.data) == "table" then
        -- 将JSON表转为字符串
        result = cjson.encode(data.data)
    else
        -- 转换为字符串
        result = tostring(data.data)
    end
    
    -- 缓存结果 (保存12小时)
    if result and type(result) == "string" then
        local success, err = seo_cache:set(cache_key, result, 43200)
        if not success then
            ngx.log(ngx.ERR, "缓存SEO数据失败: " .. (err or "未知错误"))
        else
            -- ngx.log(ngx.INFO, "成功缓存SEO数据: " .. cache_key)
        end
    end
    
    return result
end

-- 获取文章SEO数据
local article_id = ngx.var.article_id
if article_id and article_id ~= "" then
    -- ngx.log(ngx.INFO, "检测到文章ID: ", article_id)
    local data = fetch_api("/python/seo/getArticleMeta", {
        id = article_id
    })
    
    if data then
        ngx.ctx.seo_data = data
        -- ngx.log(ngx.INFO, "成功获取文章SEO数据")
    else
        ngx.log(ngx.WARN, "获取文章SEO数据失败")
    end
end

-- 如果没有文章数据，尝试获取分类SEO数据
if not ngx.ctx.seo_data or ngx.ctx.seo_data == "" then
    local category_id = ngx.var.category_id
    if category_id and category_id ~= "" then
        -- ngx.log(ngx.INFO, "检测到分类ID: ", category_id)
        local data = fetch_api("/python/seo/getCategoryMeta", {
            id = category_id
        })
        
        if data then
            ngx.ctx.seo_data = data
            -- ngx.log(ngx.INFO, "成功获取分类SEO数据")
        else
            ngx.log(ngx.WARN, "获取分类SEO数据失败")
        end
    end
end

-- 如果仍然没有数据，获取站点默认SEO数据
if not ngx.ctx.seo_data or ngx.ctx.seo_data == "" then
    -- ngx.log(ngx.INFO, "尝试获取站点默认SEO数据")
    local data = fetch_api("/seo/getSiteMeta", {})
    
    if data then
        ngx.ctx.seo_data = data
        -- ngx.log(ngx.INFO, "成功获取站点SEO数据")
    else
        ngx.log(ngx.WARN, "获取站点SEO数据失败")
        -- 使用默认的SEO元数据，而非空字符串
        ngx.ctx.seo_data = '<meta name="description" content="Poetize - 一个优雅的博客平台">'
            .. '<meta name="keywords" content="poetize,blog,poetry,文章,博客">'
            .. '<meta property="og:title" content="Poetize">'
            .. '<meta property="og:description" content="一个优雅的博客平台">'
            .. '<meta property="og:type" content="website">'
        -- ngx.log(ngx.INFO, "使用默认硬编码的SEO数据")
    end
end

-- 确保seo_data是字符串类型
if ngx.ctx.seo_data and type(ngx.ctx.seo_data) ~= "string" then
    ngx.log(ngx.WARN, "SEO数据不是字符串，尝试转换")
    local ok, str_data = pcall(function() return tostring(ngx.ctx.seo_data) end)
    if ok and str_data then
        ngx.ctx.seo_data = str_data
    else
        ngx.log(ngx.ERR, "SEO数据转换为字符串失败")
        ngx.ctx.seo_data = ""
    end
end

-- 提取标题 - 假设seo_data是HTML格式
if ngx.ctx.seo_data and type(ngx.ctx.seo_data) == "string" and ngx.ctx.seo_data ~= "" then
    -- 安全地查找title
    local title_start = string.find(ngx.ctx.seo_data, "<title>")
    local title_end = string.find(ngx.ctx.seo_data, "</title>")
    
    if title_start and title_end and title_end > title_start then
        ngx.ctx.title = string.sub(ngx.ctx.seo_data, title_start + 7, title_end - 1)
        -- ngx.log(ngx.INFO, "提取到标题: ", ngx.ctx.title)
        
        -- 移除title标签
        local before_title = string.sub(ngx.ctx.seo_data, 1, title_start - 1) or ""
        local after_title = string.sub(ngx.ctx.seo_data, title_end + 8) or ""
        ngx.ctx.seo_data = before_title .. after_title
    else
        ngx.log(ngx.WARN, "SEO数据中未找到标题标签")
    end
end

-- 确保有默认值
if not ngx.ctx.title or ngx.ctx.title == "" then
    ngx.ctx.title = "POETIZE"
    -- ngx.log(ngx.INFO, "使用默认标题: POETIZE")
end