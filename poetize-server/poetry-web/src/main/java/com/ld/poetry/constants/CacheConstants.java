package com.ld.poetry.constants;

import java.util.List;

/**
 * 缓存键常量类
 * 统一管理Redis缓存键的命名规范
 * 
 * @author LeapYa
 * @since 2025-07-20
 */
public class CacheConstants {

    /**
     * 内置 AI 爬虫硬名单（阻断训练数据采集爬虫，保留搜索/用户引用爬虫）。
     * <p>作为唯一数据源被 {@link com.ld.poetry.config.SecurityFilter} 引用；
     * Nginx 端 {@code docker/nginx/lua/ban_check.lua} 保留一份等价常量，需手动同步。
     * <p>这些规则会以内置规则形式展示在封禁列表中（id 形如 {@code builtin:<ua>}），
     * 管理员可单独删除（删除即写入 {@link #DISABLED_AI_CRAWLER_UA_KEY} 禁用集合，匹配时跳过）。
     */
    public static final List<String> BUILTIN_AI_CRAWLER_UA = List.of(
            "claudebot", "claude-web", "anthropic-ai", "gptbot");

    /**
     * 已被管理员禁用的内置 AI 爬虫硬名单集合缓存键（存 JSON 字符串数组，永久有效）。
     * <p>命中该集合的内置硬名单在 Java / Nginx 匹配时跳过，并从封禁列表中隐藏，
     * 用于解决"内置 UA 弃用后无法删除"的问题。
     * 格式: poetize:security:disabled_ai_crawler_ua
     */
    public static final String DISABLED_AI_CRAWLER_UA_KEY = "poetize:security:disabled_ai_crawler_ua";

    /**
     * 缓存键前缀
     */
    public static final String CACHE_PREFIX = "poetize:";

    // ================================ 用户相关缓存 ================================
    
    /**
     * 用户信息缓存键前缀
     * 格式: poetize:user:{userId}
     */
    public static final String USER_CACHE_PREFIX = CACHE_PREFIX + "user:";
    
    /**
     * 用户会话缓存键前缀
     * 格式: poetize:session:{token}
     */
    public static final String USER_SESSION_PREFIX = CACHE_PREFIX + "session:";
    
    /**
     * 用户登录失败次数缓存键前缀
     * 格式: poetize:login:fail:{username}
     */
    public static final String LOGIN_FAIL_PREFIX = CACHE_PREFIX + "login:fail:";

    // ================================ 文章相关缓存 ================================
    
    /**
     * 文章信息缓存键前缀
     * 格式: poetize:article:{articleId}
     */
    public static final String ARTICLE_CACHE_PREFIX = CACHE_PREFIX + "article:";
    
    /**
     * 文章列表缓存键前缀
     * 格式: poetize:article:list:{sortId}:{page}:{size}
     */
    public static final String ARTICLE_LIST_PREFIX = CACHE_PREFIX + "article:list:";

    /**
     * 文章分页列表缓存键前缀(前端首页 listArticle 接口)
     * 格式: poetize:article:listpage:{sortId}:{labelId}:{page}:{size}:{recommendStatus}
     * 说明: 仅缓存无搜索关键词的常规分页查询, 带搜索关键词的请求直接查询数据库
     */
    public static final String ARTICLE_LIST_PAGE_PREFIX = CACHE_PREFIX + "article:listpage:";

    /**
     * 微言(文章最新进展)列表缓存键前缀
     * 格式: poetize:weiyan:news:{source}
     * 说明: 按文章 source 维度缓存, 文章动态增删改时 evict 对应 source
     */
    public static final String WEIYAN_NEWS_LIST_PREFIX = CACHE_PREFIX + "weiyan:news:";
    
    /**
     * 热门文章缓存键
     */
    public static final String HOT_ARTICLES_KEY = CACHE_PREFIX + "article:hot";
    
    /**
     * 文章浏览量缓存键前缀
     * 格式: poetize:article:view:{articleId}
     */
    public static final String ARTICLE_VIEW_PREFIX = CACHE_PREFIX + "article:view:";

    /**
     * 用户文章列表缓存键前缀
     * 格式: poetize:user:article:list:{userId}
     */
    public static final String USER_ARTICLE_LIST_PREFIX = CACHE_PREFIX + "user:article:list:";

    /**
     * 文章搜索结果缓存键前缀
     * 格式: poetize:search:article:{hashCode}
     */
    public static final String SEARCH_ARTICLE_PREFIX = CACHE_PREFIX + "search:article:";

    /**
     * 文章页面存在性缓存键前缀
     * 格式: poetize:article:page-exists:{tokenType}:{token}
     */
    public static final String ARTICLE_PAGE_EXISTS_PREFIX = CACHE_PREFIX + "article:page-exists:";

    // ================================ 评论相关缓存 ================================
    
    /**
     * 评论列表缓存键前缀
     * 格式: poetize:comment:list:{source}:{type}
     */
    public static final String COMMENT_LIST_PREFIX = CACHE_PREFIX + "comment:list:";
    
    /**
     * 评论数量缓存键前缀
     * 格式: poetize:comment:count:{source}:{type}
     */
    public static final String COMMENT_COUNT_PREFIX = CACHE_PREFIX + "comment:count:";

    // ================================ 分类标签缓存 ================================
    


    /**
     * 分类文章列表缓存键
     */
    public static final String SORT_ARTICLE_LIST_KEY = CACHE_PREFIX + "sort:article:list";

    /**
     * 分类标签树缓存键（含每个分类/标签的文章数 countOfSort/countOfLabel）
     * <p>数据源 CommonQuery.getSortInfo()，/webInfo/bootstrap 高频读取。
     * 永久缓存 + 主动 evict，evict 复用 evictSortArticleList() 全部触发点（文章/分类/标签增删改）。
     */
    public static final String SORT_INFO_KEY = CACHE_PREFIX + "sort:info";

    /**
     * 标签信息缓存键前缀
     * 格式: poetize:label:list:{sortId}
     */
    public static final String LABEL_LIST_PREFIX = CACHE_PREFIX + "label:list:";

    // ================================ 系统配置缓存 ================================
    
    /**
     * 网站信息缓存键
     * 注意：网站信息使用永久缓存（不设置过期时间）
     * @see com.ld.poetry.service.CacheService#cacheWebInfo(WebInfo)
     */
    public static final String WEB_INFO_KEY = CACHE_PREFIX + "webinfo";

    /**
     * 系统配置缓存键前缀
     * 格式: poetize:config:{configKey}
     */
    public static final String SYS_CONFIG_PREFIX = CACHE_PREFIX + "config:";

    /**
     * 全量公开系统配置 Map 缓存键
     * <p>数据源 sys_config 表中 config_type=2(公开) 的全量记录聚合，
     * 供 /webInfo/bootstrap 等高频接口读取。永久缓存 + 主动 evict。
     * <p>evict 触发点: SysConfigController.saveOrUpdateConfig / deleteConfig 成功后
     */
    public static final String PUBLIC_SYS_CONFIG_MAP_KEY = CACHE_PREFIX + "config:public:map";

    // ================================ AI 配置相关缓存 ================================

    /**
     * AI 配置缓存键前缀
     * 格式: poetize:ai:config:{...}
     */
    public static final String AI_CONFIG_PREFIX = CACHE_PREFIX + "ai:config:";

    /**
     * 文章 AI 默认语言配置缓存键
     * 格式: poetize:ai:config:article_ai:default_lang
     * 说明: 数据源 sys_ai_config 表 article_ai 行，高读低写，永久缓存 + 主动 evict
     *       evict 触发点: SysAiConfigServiceImpl.saveArticleAiConfig 成功后
     */
    public static final String AI_ARTICLE_DEFAULT_LANG_KEY = AI_CONFIG_PREFIX + "article_ai:default_lang";

    /**
     * 语言映射表缓存键
     * 格式: poetize:ai:config:language_mapping
     * 说明: 数据源为 SysAiConfigServiceImpl.getLanguageMapping() 硬编码 Map，运行期不变，永久缓存
     */
    public static final String AI_LANGUAGE_MAPPING_KEY = AI_CONFIG_PREFIX + "language_mapping";

    /**
     * AI 聊天流式配置缓存键前缀
     * 格式: poetize:ai:config:streaming:{configName}
     * 说明: 数据源 sys_ai_config 表 ai_chat 行，高读低写，永久缓存 + 主动 evict
     *       evict 触发点: saveAiChatConfig / toggleEnabled / deleteConfig 成功后
     */
    public static final String AI_STREAMING_CONFIG_PREFIX = AI_CONFIG_PREFIX + "streaming:";

    /**
     * 构建 AI 聊天流式配置缓存键
     * @param configName 配置名称(如 "default")
     * @return 缓存键
     */
    public static String buildStreamingConfigKey(String configName) {
        return AI_STREAMING_CONFIG_PREFIX + (configName != null ? configName : "default");
    }

    /**
     * 管理员用户缓存键
     */
    public static final String ADMIN_CACHE_KEY = CACHE_PREFIX + "admin";

    /**
     * 点赞用户列表缓存键
     */
    public static final String ADMIRE_LIST_KEY = CACHE_PREFIX + "admire:list";

    /**
     * 家庭成员列表缓存键
     */
    public static final String FAMILY_LIST_KEY = CACHE_PREFIX + "family:list";

    // ================================ 安全相关缓存 ================================
    
    /**
     * IP攻击次数缓存键前缀
     * 格式: poetize:security:attack:{ip}
     */
    public static final String IP_ATTACK_PREFIX = CACHE_PREFIX + "security:attack:";
    
    /**
     * IP黑名单缓存键前缀
     * 格式: poetize:security:blacklist:{ip}
     */
    public static final String IP_BLACKLIST_PREFIX = CACHE_PREFIX + "security:blacklist:";

    /**
     * UA黑名单缓存键前缀
     * 格式: poetize:security:blacklist:ua:{id}
     */
    public static final String UA_BLACKLIST_PREFIX = CACHE_PREFIX + "security:blacklist:ua:";

    /**
     * CIDR网段黑名单缓存键前缀
     * 格式: poetize:security:blacklist:cidr:{id}
     */
    public static final String CIDR_BLACKLIST_PREFIX = CACHE_PREFIX + "security:blacklist:cidr:";

    /**
     * 地区黑名单缓存键前缀
     * 格式: poetize:security:blacklist:region:{id}
     */
    public static final String REGION_BLACKLIST_PREFIX = CACHE_PREFIX + "security:blacklist:region:";

    /**
     * 自动化浏览器拦截缓存键前缀
     * <p>
     * 探针上报判定为高置信度自动化（score &gt;= 70）时写入，
     * SecurityFilter 检查命中则返回 403。
     * 格式: poetize:security:automation_block:{ip}
     */
    public static final String AUTOMATION_BLOCK_PREFIX = CACHE_PREFIX + "security:automation_block:";

    /**
     * 封禁规则快照缓存键（字符串，不是前缀）
     * 说明：存放全部 UA/CIDR/Region 封禁规则的 JSON 快照，供 Nginx Lua 读取，永久有效
     */
    public static final String BAN_RULES_SNAPSHOT_KEY = CACHE_PREFIX + "security:ban_rules_snapshot";

    /**
     * 验证码缓存键前缀
     * 格式: poetize:captcha:{sessionId}
     */
    public static final String CAPTCHA_PREFIX = CACHE_PREFIX + "captcha:";

    /**
     * 用户验证码缓存键前缀
     * 格式: poetize:user:code:{userId}:{place}:{flag}
     */
    public static final String USER_CODE_PREFIX = CACHE_PREFIX + "user:code:";

    /**
     * 邮箱验证码发送次数缓存键前缀
     * 格式: poetize:code:mail:count:{email}
     */
    public static final String CODE_MAIL_COUNT_PREFIX = CACHE_PREFIX + "code:mail:count:";

    /**
     * 忘记密码验证码缓存键前缀
     * 格式: poetize:forget:password:{contact}:{flag}
     */
    public static final String FORGET_PASSWORD_PREFIX = CACHE_PREFIX + "forget:password:";

    /**
     * 登录失败尝试缓存键前缀
     * 格式: poetize:login:attempt:{account}
     */
    public static final String LOGIN_ATTEMPT_PREFIX = CACHE_PREFIX + "login:attempt:";

    /**
     * 用户保存频率限制缓存键前缀
     * 格式: poetize:save:count:user:{userId}
     */
    public static final String SAVE_COUNT_USER_PREFIX = CACHE_PREFIX + "save:count:user:";

    /**
     * IP保存频率限制缓存键前缀
     * 格式: poetize:save:count:ip:{ip}
     */
    public static final String SAVE_COUNT_IP_PREFIX = CACHE_PREFIX + "save:count:ip:";

    /**
     * 用户文件上传频率限制缓存键前缀
     * 格式: poetize:upload:count:user:{userId}
     */
    public static final String FILE_UPLOAD_COUNT_USER_PREFIX = CACHE_PREFIX + "upload:count:user:";

    /**
     * IP文件上传频率限制缓存键前缀
     * 格式: poetize:upload:count:ip:{ip}
     */
    public static final String FILE_UPLOAD_COUNT_IP_PREFIX = CACHE_PREFIX + "upload:count:ip:";

    /**
     * 管理员token缓存键前缀
     * 格式: poetize:admin:token:{userId}
     */
    public static final String ADMIN_TOKEN_PREFIX = CACHE_PREFIX + "admin:token:";

    /**
     * 用户token缓存键前缀
     * 格式: poetize:user:token:{userId}
     */
    public static final String USER_TOKEN_PREFIX = CACHE_PREFIX + "user:token:";

    /**
     * 管理员token间隔检查缓存键前缀
     * 格式: poetize:admin:token:interval:{userId}
     */
    public static final String ADMIN_TOKEN_INTERVAL_PREFIX = CACHE_PREFIX + "admin:token:interval:";

    /**
     * 用户token间隔检查缓存键前缀
     * 格式: poetize:user:token:interval:{userId}
     */
    public static final String USER_TOKEN_INTERVAL_PREFIX = CACHE_PREFIX + "user:token:interval:";

    // ================================ 统计相关缓存 ================================
    
    /**
     * 访问统计缓存键前缀
     * 格式: poetize:stats:visit:{date}
     */
    public static final String VISIT_STATS_PREFIX = CACHE_PREFIX + "stats:visit:";
    
    /**
     * 今日访问计数缓存键
     * 格式: poetize:visit:count:today:{date}
     */
    public static final String TODAY_VISIT_COUNT_PREFIX = CACHE_PREFIX + "visit:count:today:";
    
    /**
     * 每日访问记录缓存键前缀（Redis中存储当天的访问记录）
     * 格式: poetize:visit:records:{date}
     */
    public static final String DAILY_VISIT_RECORDS_PREFIX = CACHE_PREFIX + "visit:records:";

    /**
     * 搜索引擎IP真实性验证缓存键前缀
     * 格式: poetize:visit:bot-verify:{engine}:{ip}
     */
    public static final String SEARCH_BOT_VERIFY_PREFIX = CACHE_PREFIX + "visit:bot-verify:";

    /**
     * 伪装搜索引擎短窗口计数缓存键前缀
     * 格式: poetize:visit:search-bot-spoof:10m:{ip}
     */
    public static final String SEARCH_BOT_SPOOF_10M_PREFIX = CACHE_PREFIX + "visit:search-bot-spoof:10m:";

    /**
     * 伪装搜索引擎长窗口计数缓存键前缀
     * 格式: poetize:visit:search-bot-spoof:1h:{ip}
     */
    public static final String SEARCH_BOT_SPOOF_1H_PREFIX = CACHE_PREFIX + "visit:search-bot-spoof:1h:";

    /**
     * 页面访问5分钟去重缓存键前缀
     * 格式: poetize:visit:dedupe:{ipHash}:{uaHash}:{uriHash}
     */
    public static final String VISIT_DEDUPE_PREFIX = CACHE_PREFIX + "visit:dedupe:";

    /**
     * Nginx页面访问日志消费进度缓存键
     */
    public static final String NGINX_PAGE_VISIT_LOG_OFFSET_KEY = CACHE_PREFIX + "visit:nginx-log:offset";
    
    /**
     * IP今日访问标记缓存键前缀
     * 格式: poetize:visit:ip:today:{date}:{ip}_{userId}
     */
    public static final String IP_TODAY_VISIT_PREFIX = CACHE_PREFIX + "visit:ip:today:";
    
    /**
     * 在线用户数缓存键
     */
    public static final String ONLINE_USERS_KEY = CACHE_PREFIX + "stats:online";

    /**
     * IP历史记录缓存键
     */
    public static final String IP_HISTORY_KEY = CACHE_PREFIX + "ip:history";

    /**
     * IP历史统计缓存键
     */
    public static final String IP_HISTORY_STATS_KEY = CACHE_PREFIX + "ip:history:statistics";

    // ================================ 第三方服务缓存 ================================
    
    /**
     * 第三方登录状态缓存键前缀
     * 格式: poetize:oauth:state:{state}
     */
    public static final String OAUTH_STATE_PREFIX = CACHE_PREFIX + "oauth:state:";
    
    /**
     * 翻译缓存键前缀
     * 格式: poetize:translate:{hash}
     */
    public static final String TRANSLATE_PREFIX = CACHE_PREFIX + "translate:";
    
    // ================================ SEO相关缓存 ================================
    
    /**
     * Sitemap缓存键
     */
    public static final String SITEMAP_KEY = CACHE_PREFIX + "seo:sitemap";

    /**
     * Sitemap最近更新时间缓存键
     */
    public static final String SITEMAP_LAST_UPDATE_KEY = CACHE_PREFIX + "seo:sitemap:last-update";
    
    /**
     * Sitemap过期时间（秒）- 1小时
     */
    public static final long SITEMAP_EXPIRE_TIME = 3600;
    
    /**
     * manifest.json 缓存键
     * 说明：PWA manifest内容缓存，网站名称修改时会立即清除
     */
    public static final String MANIFEST_JSON_KEY = CACHE_PREFIX + "seo:manifest";
    
    /**
     * robots.txt 缓存键
     */
    public static final String ROBOTS_TXT_KEY = CACHE_PREFIX + "seo:robots";
    
    /**
     * SEO静态文件缓存过期时间（秒）- 24小时（作为兜底，修改时会主动清除）
     */
    public static final long SEO_STATIC_EXPIRE_TIME = 86400;
    
    /**
     * 搜索引擎推送结果缓存键
     */
    public static final String SEARCH_ENGINE_PING_RESULT_KEY = CACHE_PREFIX + "seo:ping:result";
    
    /**
     * 搜索引擎推送结果缓存过期时间（秒）- 6小时
     */
    public static final long SEARCH_ENGINE_PING_RESULT_EXPIRE_TIME = 21600;
    
    // ================================ 二维码相关缓存 ================================
    
    /**
     * 文章二维码缓存键前缀
     * 格式: poetize:qrcode:article:{articleId}
     * 说明：存储文章分享二维码的字节数组，避免重复生成
     */
    public static final String ARTICLE_QRCODE_PREFIX = CACHE_PREFIX + "qrcode:article:";
    
    /**
     * 二维码缓存过期时间（秒）- 永久缓存
     * 说明：二维码内容基于文章ID固定不变，仅在文章更新/删除时主动清理缓存
     */
    public static final long QRCODE_EXPIRE_TIME = 0;

    // ================================ 缓存过期时间常量 ================================
    
    /**
     * 默认缓存过期时间（秒）- 10分钟
     */
    public static final long DEFAULT_EXPIRE_TIME = 600;
    
    /**
     * 短期缓存过期时间（秒）- 5分钟
     */
    public static final long SHORT_EXPIRE_TIME = 300;

    /**
     * 文章页面存在性短缓存时间（秒）
     */
    public static final long ARTICLE_PAGE_EXISTS_EXPIRE_TIME = SHORT_EXPIRE_TIME;
    
    /**
     * 长期缓存过期时间（秒）- 1小时
     */
    public static final long LONG_EXPIRE_TIME = 3600;
    
    /**
     * 超长期缓存过期时间（秒）- 24小时
     */
    public static final long VERY_LONG_EXPIRE_TIME = 86400;
    
    /**
     * 永久缓存标识（秒）- 0表示永不过期
     * 用于系统核心配置等需要永久保存的缓存
     */
    public static final long PERMANENT_EXPIRE_TIME = 0;
    
    /**
     * 用户会话过期时间（秒）- 7天
     * 注意：此值应与CommonConst.TOKEN_EXPIRE保持一致，避免认证状态不同步
     * 建议使用CommonConst.TOKEN_EXPIRE替代此常量
     */
    public static final long SESSION_EXPIRE_TIME = 604800;
    
    /**
     * 验证码过期时间（秒）- 5分钟
     */
    public static final long CAPTCHA_EXPIRE_TIME = 300;
    
    /**
     * IP攻击记录过期时间（秒）- 1小时
     */
    public static final long IP_ATTACK_EXPIRE_TIME = 3600;
    
    /**
     * IP黑名单过期时间（秒）- 24小时
     */
    public static final long IP_BLACKLIST_EXPIRE_TIME = 86400;

    /**
     * 自动化浏览器拦截过期时间（秒）- 2小时
     * <p>
     * 基于 JS 运行时信号的精确判定，误判率极低，但仍设置较短过期
     * 以便反检测浏览器更新后能自然恢复。
     */
    public static final long AUTOMATION_BLOCK_EXPIRE_TIME = 7200;

    // ================================ 工具方法 ================================
    
    /**
     * 构建用户缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildUserKey(Integer userId) {
        return USER_CACHE_PREFIX + userId;
    }
    
    /**
     * 构建用户会话缓存键
     * @param token 会话令牌
     * @return 缓存键
     */
    public static String buildSessionKey(String token) {
        return USER_SESSION_PREFIX + token;
    }
    
    /**
     * 构建文章缓存键
     * @param articleId 文章ID
     * @return 缓存键
     */
    public static String buildArticleKey(Integer articleId) {
        return ARTICLE_CACHE_PREFIX + articleId;
    }
    
    /**
     * 构建文章列表缓存键
     * @param sortId 分类ID
     * @param page 页码
     * @param size 页大小
     * @return 缓存键
     */
    public static String buildArticleListKey(Integer sortId, Integer page, Integer size) {
        return ARTICLE_LIST_PREFIX + sortId + ":" + page + ":" + size;
    }

    /**
     * 构建文章分页列表缓存键(前端首页 listArticle 接口)
     * <p>仅用于无搜索关键词的常规分页查询, 维度: sortId + labelId + page + size + recommendStatus
     * @param sortId 分类ID(可为null)
     * @param labelId 标签ID(可为null)
     * @param page 页码
     * @param size 页大小
     * @param recommendStatus 是否推荐(可为null)
     * @return 缓存键
     */
    public static String buildArticleListPageKey(Integer sortId, Integer labelId, long page, long size, Boolean recommendStatus) {
        return ARTICLE_LIST_PAGE_PREFIX
                + (sortId == null ? "_" : sortId) + ":"
                + (labelId == null ? "_" : labelId) + ":"
                + page + ":" + size + ":"
                + (recommendStatus == null ? "_" : recommendStatus);
    }

    /**
     * 构建微言(文章最新进展)列表缓存键
     * @param source 文章ID
     * @return 缓存键
     */
    public static String buildWeiYanNewsListKey(Integer source) {
        return WEIYAN_NEWS_LIST_PREFIX + source;
    }

    /**
     * 构建文章页面存在性缓存键
     * @param tokenType token类型（id/slug）
     * @param token 文章URL token
     * @return 缓存键
     */
    public static String buildArticlePageExistsKey(String tokenType, String token) {
        return ARTICLE_PAGE_EXISTS_PREFIX + tokenType + ":" + token;
    }
    
    /**
     * 构建评论列表缓存键
     * @param source 来源ID
     * @param type 类型
     * @return 缓存键
     */
    public static String buildCommentListKey(Integer source, String type) {
        return COMMENT_LIST_PREFIX + source + ":" + type;
    }
    
    /**
     * 构建IP攻击缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildIpAttackKey(String ip) {
        return IP_ATTACK_PREFIX + ip;
    }
    
    /**
     * 构建IP黑名单缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildIpBlacklistKey(String ip) {
        return IP_BLACKLIST_PREFIX + ip;
    }

    /**
     * 构建UA黑名单缓存键
     * @param id 规则ID
     * @return 缓存键
     */
    public static String buildUaBlacklistKey(String id) {
        return UA_BLACKLIST_PREFIX + id;
    }

    /**
     * 构建CIDR网段黑名单缓存键
     * @param id 规则ID
     * @return 缓存键
     */
    public static String buildCidrBlacklistKey(String id) {
        return CIDR_BLACKLIST_PREFIX + id;
    }

    /**
     * 构建地区黑名单缓存键
     * @param id 规则ID
     * @return 缓存键
     */
    public static String buildRegionBlacklistKey(String id) {
        return REGION_BLACKLIST_PREFIX + id;
    }

    /**
     * 构建自动化浏览器拦截缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildAutomationBlockKey(String ip) {
        return AUTOMATION_BLOCK_PREFIX + ip;
    }

    /**
     * 构建系统配置缓存键
     * @param configKey 配置键
     * @return 缓存键
     */
    public static String buildSysConfigKey(String configKey) {
        return SYS_CONFIG_PREFIX + configKey;
    }

    /**
     * 构建用户文章列表缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildUserArticleListKey(Integer userId) {
        return USER_ARTICLE_LIST_PREFIX + userId;
    }

    /**
     * 构建文章搜索缓存键
     * @param searchText 搜索文本
     * @return 缓存键
     */
    public static String buildSearchArticleKey(String searchText) {
        return SEARCH_ARTICLE_PREFIX + (searchText != null ? searchText.hashCode() : "empty");
    }

    /**
     * 构建用户验证码缓存键
     * @param userId 用户ID
     * @param place 位置
     * @param flag 标志
     * @return 缓存键
     */
    public static String buildUserCodeKey(Integer userId, String place, String flag) {
        return USER_CODE_PREFIX + userId + ":" + place + ":" + flag;
    }

    /**
     * 构建邮箱验证码发送次数缓存键
     * @param email 邮箱
     * @return 缓存键
     */
    public static String buildCodeMailCountKey(String email) {
        return CODE_MAIL_COUNT_PREFIX + email;
    }

    /**
     * 构建忘记密码验证码缓存键
     * @param contact 联系方式（邮箱或手机号）
     * @param flag 标志（1-手机号，2-邮箱）
     * @return 缓存键
     */
    public static String buildForgetPasswordKey(String contact, String flag) {
        return FORGET_PASSWORD_PREFIX + contact + ":" + flag;
    }

    /**
     * 构建登录失败尝试缓存键
     * @param account 账号
     * @return 缓存键
     */
    public static String buildLoginAttemptKey(String account) {
        return LOGIN_ATTEMPT_PREFIX + account;
    }

    /**
     * 构建用户保存频率限制缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildSaveCountUserKey(Integer userId) {
        return SAVE_COUNT_USER_PREFIX + userId;
    }

    /**
     * 构建IP保存频率限制缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildSaveCountIpKey(String ip) {
        return SAVE_COUNT_IP_PREFIX + ip;
    }

    /**
     * 构建用户文件上传频率限制缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildFileUploadCountUserKey(Integer userId) {
        return FILE_UPLOAD_COUNT_USER_PREFIX + userId;
    }

    /**
     * 构建IP文件上传频率限制缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildFileUploadCountIpKey(String ip) {
        return FILE_UPLOAD_COUNT_IP_PREFIX + ip;
    }

    /**
     * 构建管理员token缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildAdminTokenKey(Integer userId) {
        return ADMIN_TOKEN_PREFIX + userId;
    }

    /**
     * 构建用户token缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildUserTokenKey(Integer userId) {
        return USER_TOKEN_PREFIX + userId;
    }

    /**
     * 构建管理员token间隔检查缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildAdminTokenIntervalKey(Integer userId) {
        return ADMIN_TOKEN_INTERVAL_PREFIX + userId;
    }

    /**
     * 构建用户token间隔检查缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildUserTokenIntervalKey(Integer userId) {
        return USER_TOKEN_INTERVAL_PREFIX + userId;
    }
    
    // ================================ 访问统计缓存键构建方法 ================================
    
    /**
     * 构建今日访问计数缓存键
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 缓存键
     */
    public static String buildTodayVisitCountKey(String date) {
        return TODAY_VISIT_COUNT_PREFIX + date;
    }
    
    /**
     * 构建每日访问记录缓存键
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 缓存键
     */
    public static String buildDailyVisitRecordsKey(String date) {
        return DAILY_VISIT_RECORDS_PREFIX + date;
    }

    /**
     * 构建搜索引擎IP真实性验证缓存键
     * @param engine 搜索引擎名称
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildSearchBotVerifyKey(String engine, String ip) {
        return SEARCH_BOT_VERIFY_PREFIX + engine + ":" + ip;
    }

    /**
     * 构建伪装搜索引擎短窗口计数缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildSearchBotSpoof10mKey(String ip) {
        return SEARCH_BOT_SPOOF_10M_PREFIX + ip;
    }

    /**
     * 构建伪装搜索引擎长窗口计数缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildSearchBotSpoof1hKey(String ip) {
        return SEARCH_BOT_SPOOF_1H_PREFIX + ip;
    }

    /**
     * 构建页面访问去重缓存键
     * @param ipHash IP哈希
     * @param uaHash UA哈希
     * @param uriHash 页面URI哈希
     * @return 缓存键
     */
    public static String buildVisitDedupeKey(String ipHash, String uaHash, String uriHash) {
        return VISIT_DEDUPE_PREFIX + ipHash + ":" + uaHash + ":" + uriHash;
    }
    
    /**
     * 构建IP今日访问标记缓存键
     * @param date 日期（格式：yyyy-MM-dd）
     * @param ip IP地址
     * @param userId 用户ID（可为null）
     * @return 缓存键
     */
    public static String buildIpTodayVisitKey(String date, String ip, Integer userId) {
        String userSuffix = userId != null ? "_" + userId : "";
        return IP_TODAY_VISIT_PREFIX + date + ":" + ip + userSuffix;
    }
    
    // ================================ 二维码缓存键构建方法 ================================

    /**
     * 构建文章二维码缓存键
     * @param articleId 文章ID
     * @return 缓存键
     */
    public static String buildArticleQRCodeKey(Integer articleId) {
        return ARTICLE_QRCODE_PREFIX + articleId;
    }

    // ================================ 资源检测任务缓存 ================================

    /**
     * 资源检测任务状态缓存键前缀
     * 格式: poetize:resource:scan:task:{taskId}
     */
    public static final String RESOURCE_SCAN_TASK_PREFIX = CACHE_PREFIX + "resource:scan:task:";

    /**
     * 资源检测结果缓存键前缀（按资源类型区分：invalid/orphan）
     * 格式: poetize:resource:scan:result:{type}
     */
    public static final String RESOURCE_SCAN_RESULT_PREFIX = CACHE_PREFIX + "resource:scan:result:";

    /**
     * 资源检测任务过期时间（秒）- 30分钟
     * 说明：任务状态保留30分钟供前端轮询，超时自动清理
     */
    public static final long RESOURCE_SCAN_TASK_EXPIRE_TIME = 1800;

    /**
     * 资源检测结果缓存过期时间（秒）- 10分钟
     * 说明：检测结果缓存10分钟，避免短时间内重复检测
     */
    public static final long RESOURCE_SCAN_RESULT_EXPIRE_TIME = 600;

    /**
     * 构建资源检测任务状态缓存键
     * @param taskId 任务ID
     * @return 缓存键
     */
    public static String buildResourceScanTaskKey(String taskId) {
        return RESOURCE_SCAN_TASK_PREFIX + taskId;
    }

    /**
     * 构建资源检测结果缓存键
     * @param type 资源类型（invalid/orphan）
     * @return 缓存键
     */
    public static String buildResourceScanResultKey(String type) {
        return RESOURCE_SCAN_RESULT_PREFIX + type;
    }
}
