
-- 设置头部，允许后续修改
ngx.header.content_length = nil

-- 确保只处理HTML内容
local content_type = ngx.header.content_type
if not content_type or not string.find(content_type, "text/html", 1, true) then
    return
end
