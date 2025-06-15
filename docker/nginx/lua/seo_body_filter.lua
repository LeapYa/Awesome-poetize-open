
local chunk = ngx.arg[1]
if not chunk then return end

-- 确保只处理HTML内容
local content_type = ngx.header.content_type
if not content_type or not string.find(content_type, "text/html", 1, true) then
    return
end

-- 处理HTML lang属性
if not ngx.ctx.lang_injected then
    local lang = ngx.var.arg_lang or ""
    if lang ~= "" then
        -- 动态设置HTML标签的lang属性
        chunk = string.gsub(chunk, "<html([^>]*)>", function(attrs)
            -- 检查是否已存在lang属性
            if string.find(attrs, "lang=", 1, true) then
                -- 替换现有的lang属性
                attrs = string.gsub(attrs, 'lang="[^"]*"', 'lang="' .. lang .. '"')
                attrs = string.gsub(attrs, "lang='[^']*'", "lang='" .. lang .. "'")
            else
                -- 添加新的lang属性
                attrs = ' lang="' .. lang .. '"' .. attrs
            end
            return "<html" .. attrs .. ">"
        end)
        ngx.ctx.lang_injected = true
    end
end

-- 改进的SEO数据检查函数
local function has_seo_meta(html)
    -- 检查是否已有关键的SEO meta标签
    local patterns = {
        '<meta[^>]*name=["\']description["\'][^>]*>',
        '<meta[^>]*name=["\']keywords["\'][^>]*>',
        '<meta[^>]*property=["\']og:title["\'][^>]*>',
        '<meta[^>]*property=["\']og:description["\'][^>]*>'
    }
    
    local found_count = 0
    for _, pattern in ipairs(patterns) do
        if string.find(html, pattern) then
            found_count = found_count + 1
        end
    end
    
    -- 如果找到2个或以上的关键meta标签，认为已有SEO数据
    return found_count >= 2
end

-- 注入SEO数据
if ngx.ctx.seo_data and type(ngx.ctx.seo_data) == "string" and ngx.ctx.seo_data ~= "" and not ngx.ctx.seo_injected then
    -- 检查是否已存在SEO数据
    if has_seo_meta(chunk) then
        -- ngx.log(ngx.INFO, "检测到页面已有SEO数据，跳过插入")
        ngx.ctx.seo_injected = true
    else
        -- 查找占位符并替换
        local placeholder_exist = string.find(chunk, "<!-- SEO_META_PLACEHOLDER -->", 1, true)
        if placeholder_exist then
            -- 替换占位符
            local clean_seo = string.gsub(ngx.ctx.seo_data, "\\\"", "\"")
            chunk = string.gsub(chunk, "<!-- SEO_META_PLACEHOLDER -->", clean_seo)
            -- ngx.log(ngx.INFO, "占位符替换成功")
            ngx.ctx.seo_injected = true
        else
            -- 在head标签后插入
            local head_tag = string.find(chunk, "<head>", 1, true)
            if head_tag then
                local clean_seo = string.gsub(ngx.ctx.seo_data, "\\\"", "\"")
                chunk = string.gsub(chunk, "<head>", "<head>" .. clean_seo, 1)
                -- ngx.log(ngx.INFO, "在head标签后插入SEO数据")
                ngx.ctx.seo_injected = true
            end
        end
    end
end

-- 注入title标签
if ngx.ctx.title and ngx.ctx.title ~= "" and not ngx.ctx.title_injected then
    -- 查找现有title标签及其内容
    local title_pattern = "<title[^>]*>(.-)</title>"
    local existing_title_content = nil
    
    for content in string.gmatch(chunk, title_pattern) do
        existing_title_content = content
        break
    end
    
    if existing_title_content then
        -- ngx.log(ngx.INFO, "现有title内容: " .. existing_title_content)
        
        -- 检查是否需要修复重复或替换标题
        if existing_title_content ~= ngx.ctx.title then
            -- 检查是否包含重复模式
            local repeated_pattern = ngx.ctx.title .. ngx.ctx.title
            if existing_title_content == repeated_pattern or string.find(existing_title_content, repeated_pattern, 1, true) then
                -- 修复重复的标题
                chunk = string.gsub(chunk, "<title[^>]*>" .. existing_title_content .. "</title>", "<title>" .. ngx.ctx.title .. "</title>", 1)
                -- ngx.log(ngx.INFO, "修复重复的title内容")
            else
                -- 替换不同的标题
                chunk = string.gsub(chunk, "<title[^>]*>" .. existing_title_content .. "</title>", "<title>" .. ngx.ctx.title .. "</title>", 1)
                -- ngx.log(ngx.INFO, "替换不同的title内容")
            end
            ngx.ctx.title_injected = true
        else
            -- ngx.log(ngx.INFO, "现有title已是目标title，无需修改")
            ngx.ctx.title_injected = true
        end
    else
        -- 没有找到title标签，添加一个
        local title_count = select(2, string.gsub(chunk, "<title", ""))
        
        if title_count > 1 then
            -- 移除所有title标签并添加一个新的
            chunk = string.gsub(chunk, "<title[^>]*>.-</title>", "")
            chunk = string.gsub(chunk, "<head>", "<head><title>" .. ngx.ctx.title .. "</title>", 1)
            -- ngx.log(ngx.INFO, "移除重复title标签并添加新标签")
        elseif title_count == 1 then
            -- 替换空的title标签
            chunk = string.gsub(chunk, "<title[^>]*>.-</title>", "<title>" .. ngx.ctx.title .. "</title>", 1)
            -- ngx.log(ngx.INFO, "替换空的title标签")
        else
            -- 添加新的title标签
            local head_exists = string.find(chunk, "<head>", 1, true)
            if head_exists then
                chunk = string.gsub(chunk, "<head>", "<head><title>" .. ngx.ctx.title .. "</title>", 1)
                -- ngx.log(ngx.INFO, "添加新的title标签")
            end
        end
        ngx.ctx.title_injected = true
    end
end

-- 去重meta标签
local seen_meta = {}
chunk = ngx.re.gsub(chunk, [[<meta[^>]*(name|property)="([^"]+)"[^>]*content="([^"]*)"[^>]*>]], function(m)
    local key = m[1] .. ":" .. m[2]
    if seen_meta[key] then 
        -- ngx.log(ngx.INFO, "移除重复的meta标签: " .. key)
        return "" 
    end
    seen_meta[key] = true
    return m[0]
end, "jo")

ngx.arg[1] = chunk