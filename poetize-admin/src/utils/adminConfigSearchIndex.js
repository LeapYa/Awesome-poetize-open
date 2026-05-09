export const adminConfigSearchIndex = [
  {
    id: 'page-main',
    type: 'page',
    route: '/main',
    pageTitle: '系统首页',
    sectionTitle: '统计信息与访问信息',
    fieldLabel: '系统首页',
    keywords: ['系统首页', '后台首页', '管理首页'],
    aliases: ['main', '统计信息', '访问信息', '总览', '访问量历史趋势', '封禁IP']
  },
  {
    id: 'field-main-stats',
    type: 'field',
    route: '/main',
    pageTitle: '系统首页',
    sectionTitle: '统计信息',
    fieldLabel: '统计信息',
    keywords: ['统计信息', '数据统计', '总览数据'],
    aliases: ['访问统计', '用户统计', '内容统计', '互动统计', '总访问量', '总用户', '总文章数', '总评论数']
  },
  {
    id: 'field-main-visit',
    type: 'field',
    route: '/main',
    pageTitle: '系统首页',
    sectionTitle: '访问信息',
    fieldLabel: '访问信息',
    keywords: ['访问信息', '访问数据', '访问记录'],
    aliases: ['总访问', '今日访问', '昨日访问', '省份访问', 'IP访问', '今日访问用户', '昨日访问用户']
  },
  {
    id: 'page-welcome',
    type: 'page',
    route: '/welcome',
    pageTitle: 'Welcome页面',
    sectionTitle: '欢迎页与配置向导',
    fieldLabel: 'Welcome页面',
    keywords: ['welcome页面', '欢迎页', '欢迎页面'],
    aliases: ['welcome', '系统欢迎页', '配置向导', '新手引导', '开启配置向导']
  }, {
    id: 'field-welcome-entry',
    type: 'field',
    route: '/welcome',
    pageTitle: 'Welcome页面',
    sectionTitle: '欢迎页与配置向导',
    fieldLabel: '欢迎页',
    keywords: ['欢迎页', '欢迎页面', 'welcome页面'],
    aliases: ['welcome', '系统欢迎页', '配置向导', '新手引导', '开启配置向导']
  },
  {
    id: 'page-visit-stats',
    type: 'page',
    route: '/visitStats',
    pageTitle: '访问统计',
    sectionTitle: '访问量趋势与统计图表',
    fieldLabel: '访问统计',
    keywords: ['访问统计', '访问量趋势', '统计图表'],
    aliases: ['visitStats', '访问量历史趋势', '趋势图', '访问图表']
  },
  {
    id: 'page-web-edit',
    type: 'page',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '网站基础信息',
    fieldLabel: '基础设置',
    keywords: ['基础设置', '网站设置', '站点设置'],
    aliases: ['网站名称', '网站标题', '网站地址', '背景图', '网站头像', '页脚', '页脚背景', '网站状态', '极简页脚', '联系邮箱']
  },
  {
    id: 'page-web-appearance',
    type: 'page',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '外观与个性化',
    fieldLabel: '外观个性化',
    keywords: ['外观个性化', '外观设置', '主题设置'],
    aliases: ['看板娘', 'Live2D', '夜间模式', '灰色模式', '动态标题', '移动端侧边栏', '鼠标点击效果', '首页横幅高度', '字体优化']
  },
  {
    id: 'page-web-notice',
    type: 'page',
    route: '/webNotice',
    pageTitle: '通知与邮件',
    sectionTitle: '公告与邮箱配置',
    fieldLabel: '通知与邮件',
    keywords: ['通知与邮件', '邮件设置', '公告'],
    aliases: ['SMTP', '发信', '邮箱配置', '测试邮箱', 'SMTP服务器', 'SMTP端口', '发件邮箱', '公告内容']
  },
  {
    id: 'page-web-security',
    type: 'page',
    route: '/webSecurity',
    pageTitle: '安全与登录',
    sectionTitle: '验证码与第三方登录',
    fieldLabel: '安全与登录',
    keywords: ['安全与登录', '登录配置', '验证码'],
    aliases: ['滑动验证', '第三方登录', 'GitHub登录', 'Google登录', 'Gitee登录', 'OAuth']
  },
  {
    id: 'page-web-nav-api',
    type: 'page',
    route: '/webNavApi',
    pageTitle: '导航与接口',
    sectionTitle: '导航栏与 API 配置',
    fieldLabel: '导航与接口',
    keywords: ['导航与接口', '导航配置', '接口配置'],
    aliases: ['导航栏', '首页菜单', '菜单', 'API']
  },
  {
    id: 'page-seo',
    type: 'page',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: 'SEO 与搜索引擎配置',
    fieldLabel: 'SEO优化',
    keywords: ['SEO', 'SEO优化', '搜索引擎'],
    aliases: ['robots', 'sitemap', 'favicon', 'PWA', '站点验证', '百度推送', 'IndexNow', 'Google Search Console', '默认分享图片', '自定义头部代码', '网站Logo']
  },
  {
    id: 'page-ai-chat',
    type: 'page',
    route: '/webAppearance',
    pageTitle: 'AI聊天配置',
    sectionTitle: 'AI 模型与聊天配置',
    fieldLabel: 'AI聊天配置',
    keywords: ['AI聊天', 'AI配置', '模型配置'],
    aliases: ['API密钥', '模型名称', '提示词', 'Mem0', '聊天记录', '思考程度']
  },
  {
    id: 'page-config-list',
    type: 'page',
    route: '/configList',
    pageTitle: '配置管理',
    sectionTitle: '系统配置表',
    fieldLabel: '配置管理',
    keywords: ['配置管理', '系统配置', '配置表'],
    aliases: ['备案号', 'configKey', '键名', '键值', '动态配置', 'SQL配置']
  },
  {
    id: 'page-plugin-manager',
    type: 'page',
    route: '/pluginManager',
    pageTitle: '插件管理',
    sectionTitle: '插件配置',
    fieldLabel: '插件管理',
    keywords: ['插件管理', '插件配置'],
    aliases: ['AI工具', '编辑器插件', '文章主题', '看板娘模型', '鼠标点击效果', '全屏飘落特效', '全局飘落特效', '全局飘落', '全屏特效', '全屏粒子特效', '动态粒子特效', '樱花飘落', '插件包', '文章付费']
  },
  {
    id: 'field-web-title',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '网站标题',
    keywords: ['网站标题', '站点标题', '页面标题'],
    aliases: ['webTitle', 'title']
  },
  {
    id: 'field-web-name',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '网站名称',
    keywords: ['网站名称', '站点名称'],
    aliases: ['webName']
  },
  {
    id: 'field-site-address',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '网站地址',
    keywords: ['网站地址', '域名', '站点地址'],
    aliases: ['siteAddress', '自动检测地址', '站点URL']
  },
  {
    id: 'field-web-status',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '网站状态',
    keywords: ['网站状态', '站点状态', '启用状态'],
    aliases: ['status', '网站开关', '站点开关']
  },
  {
    id: 'field-background-image',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '背景图片',
    keywords: ['背景图片', '背景图', '站点背景'],
    aliases: ['backgroundImage', '封面背景']
  },
  {
    id: 'field-site-avatar',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '网站头像',
    keywords: ['网站头像', '站点头像', '头像'],
    aliases: ['avatar', '站点头像']
  },
  {
    id: 'field-footer',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '页脚文案',
    keywords: ['页脚文案', '页脚', 'footer'],
    aliases: ['minimalFooter', 'footer', '极简页脚']
  },
  {
    id: 'field-footer-background',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '页脚背景',
    fieldLabel: '页脚背景',
    keywords: ['页脚背景', '页脚背景图片', 'Footer背景'],
    aliases: ['footerBackgroundImage', '背景大小', '背景位置', '重复方式', '遮罩颜色']
  },
  {
    id: 'field-contact-email',
    type: 'field',
    route: '/webEdit',
    pageTitle: '基础设置',
    sectionTitle: '基础信息',
    fieldLabel: '联系邮箱',
    keywords: ['联系邮箱', '站点邮箱', '邮箱'],
    aliases: ['email', '侵权联系邮箱', '隐私政策邮箱']
  },
  {
    id: 'field-mouse-click-effect',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '外观设置',
    fieldLabel: '鼠标点击效果',
    keywords: ['鼠标点击效果', '点击特效', '点击动画'],
    aliases: ['mouseClickEffect', '点击效果', '鼠标特效']
  },
  {
    id: 'field-banner-height',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '外观设置',
    fieldLabel: '首页横幅高度',
    keywords: ['首页横幅高度', '横幅高度', 'Banner高度'],
    aliases: ['bannerHeight', '首页高度', '首页banner']
  },
  {
    id: 'field-waifu',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '外观开关',
    fieldLabel: '看板娘/AI',
    keywords: ['看板娘', 'Live2D', 'AI助手'],
    aliases: ['enableWaifu', 'waifu', '看板娘显示模式', '按钮模式']
  },
  {
    id: 'field-auto-night',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '外观开关',
    fieldLabel: '自动夜间',
    keywords: ['自动夜间', '夜间模式', '深色模式'],
    aliases: ['enableAutoNight', '夜间开始', '夜间结束']
  },
  {
    id: 'field-gray-mode',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '外观开关',
    fieldLabel: '灰色模式',
    keywords: ['灰色模式', '灰度模式'],
    aliases: ['enableGrayMode']
  },
  {
    id: 'field-dynamic-title',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '外观开关',
    fieldLabel: '动态标题',
    keywords: ['动态标题', '页面标题动效', '切屏标题'],
    aliases: ['titleChange', '标题动画']
  },
  {
    id: 'field-mobile-drawer',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '移动端侧边栏',
    fieldLabel: '移动端侧边栏',
    keywords: ['移动端侧边栏', '手机侧边栏', '抽屉菜单'],
    aliases: ['drawerConfig', '背景图片', '渐变背景', '字体颜色', '雪花装饰']
  },
  {
    id: 'field-font-optimization',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观个性化',
    sectionTitle: '字体性能优化',
    fieldLabel: '字体性能优化',
    keywords: ['字体性能优化', '字体优化', '字体分片'],
    aliases: ['子集字体', '字体切片', 'WOFF2', '上传中文字体']
  },
  {
    id: 'field-email-settings',
    type: 'field',
    route: '/webNotice',
    pageTitle: '通知与邮件',
    sectionTitle: '邮箱配置',
    fieldLabel: '邮箱配置',
    keywords: ['邮箱配置', 'SMTP', '邮件配置'],
    aliases: ['发件邮箱', '邮件服务器', '授权码', '测试邮箱', 'SMTP服务器', 'SMTP端口', '邮箱Host']
  },
  {
    id: 'field-notice',
    type: 'field',
    route: '/webNotice',
    pageTitle: '通知与邮件',
    sectionTitle: '公告',
    fieldLabel: '公告',
    keywords: ['公告', '通知', '站点公告'],
    aliases: ['notices', '公告内容']
  },
  {
    id: 'field-captcha',
    type: 'field',
    route: '/webSecurity',
    pageTitle: '安全与登录',
    sectionTitle: '智能验证码配置',
    fieldLabel: '启用智能验证码',
    keywords: ['验证码', '智能验证码', '人机验证'],
    aliases: ['滑动验证', '勾选验证', '验证码防护']
  },
  {
    id: 'field-third-login',
    type: 'field',
    route: '/webSecurity',
    pageTitle: '安全与登录',
    sectionTitle: '第三方登录配置',
    fieldLabel: '启用第三方登录',
    keywords: ['第三方登录', 'OAuth登录', '社交登录'],
    aliases: ['GitHub登录', 'Google登录', 'Gitee登录', 'Client ID', 'Client Secret']
  },
  {
    id: 'field-nav',
    type: 'field',
    route: '/webNavApi',
    pageTitle: '导航与接口',
    sectionTitle: '导航栏配置',
    fieldLabel: '导航栏配置',
    keywords: ['导航栏', '导航配置', '首页菜单'],
    aliases: ['navConfig', '菜单', '导航项']
  },
  {
    id: 'field-api',
    type: 'field',
    route: '/webNavApi',
    pageTitle: '导航与接口',
    sectionTitle: 'API 配置',
    fieldLabel: '启用API',
    keywords: ['API配置', '开放接口', '接口设置'],
    aliases: ['API Key', '获取文章列表 API']
  },
  {
    id: 'field-seo-description',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: 'SEO 配置',
    fieldLabel: '网站描述',
    keywords: ['网站描述', 'SEO描述', 'description'],
    aliases: ['site_description', 'meta description']
  },
  {
    id: 'field-seo-keywords',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: 'SEO 配置',
    fieldLabel: '网站关键词',
    keywords: ['网站关键词', 'SEO关键词', '关键词'],
    aliases: ['site_keywords', 'meta keywords']
  },
  {
    id: 'field-seo-author',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: 'SEO 配置',
    fieldLabel: '默认作者',
    keywords: ['默认作者', '作者信息', 'SEO作者'],
    aliases: ['default_author', 'author']
  },
  {
    id: 'field-seo-share-image',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '通用设置',
    fieldLabel: '默认分享图片',
    keywords: ['默认分享图片', '分享图', '社交分享图片'],
    aliases: ['og_image', 'Open Graph图片', '缩略图']
  },
  {
    id: 'field-seo-logo',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '通用设置',
    fieldLabel: '网站Logo',
    keywords: ['网站Logo', 'Logo', '站点Logo'],
    aliases: ['site_logo', 'logo图片']
  },
  {
    id: 'field-favicon',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '网站图标',
    fieldLabel: '网站标签页图标',
    keywords: ['favicon', '网站图标', '标签页图标'],
    aliases: ['site_icon', 'Apple Touch图标']
  },
  {
    id: 'field-pwa',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: 'PWA应用',
    fieldLabel: 'PWA应用',
    keywords: ['PWA', '渐进式应用', '应用短名称'],
    aliases: ['pwa_display', 'pwa_theme_color', 'pwa_background_color', 'pwa_orientation']
  },
  {
    id: 'field-pwa-screenshot',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: 'PWA应用',
    fieldLabel: 'PWA截图',
    keywords: ['PWA截图', '桌面端截图', '移动端截图'],
    aliases: ['pwa_screenshot_desktop', 'pwa_screenshot_mobile']
  },
  {
    id: 'field-social-meta',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '社交媒体配置',
    fieldLabel: '社交媒体卡片',
    keywords: ['社交媒体配置', 'Open Graph', 'Twitter Card'],
    aliases: ['Facebook App ID', 'Twitter账号', 'LinkedIn', 'Pinterest', '小程序关联']
  },
  {
    id: 'field-verify',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '站点验证',
    fieldLabel: '百度站点验证',
    keywords: ['站点验证', '收录验证', '搜索引擎验证'],
    aliases: ['Google站点验证', 'Bing验证', '百度验证', '搜狗验证', '360站点验证']
  },
  {
    id: 'field-search-push',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '搜索引擎推送',
    fieldLabel: '搜索引擎推送',
    keywords: ['搜索引擎推送', '百度推送', 'IndexNow'],
    aliases: ['baidu_push_token', 'bing_api_key', 'sogou_push_token', 'shenma_token']
  },
  {
    id: 'field-sitemap',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '站点地图与 robots',
    fieldLabel: '网站地图',
    keywords: ['网站地图', 'sitemap', '站点地图'],
    aliases: ['sitemap_change_frequency', 'sitemap_priority', 'sitemap_exclude', '自动生成META标签']
  },
  {
    id: 'field-robots',
    type: 'field',
    route: '/seoConfig',
    pageTitle: 'SEO优化',
    sectionTitle: '站点地图与 robots',
    fieldLabel: 'robots.txt 内容',
    keywords: ['robots', 'robots.txt', '爬虫规则'],
    aliases: ['自定义头部代码', 'custom_head_code', 'robots_txt']
  },
  {
    id: 'field-config-list',
    type: 'field',
    route: '/configList',
    pageTitle: '配置管理',
    sectionTitle: '系统配置表',
    fieldLabel: '系统配置搜索',
    keywords: ['系统配置表', '配置键名', '配置键值'],
    aliases: ['configKey', 'configValue', 'configName', '备案号']
  },
  {
    id: 'field-plugin-mouse-effect',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'mouse_click_effect' } },
    pageTitle: '插件管理',
    sectionTitle: '鼠标点击效果',
    fieldLabel: '鼠标点击效果插件',
    keywords: ['鼠标点击效果', '点击特效插件', '点击动画插件'],
    aliases: ['mouse_click_effect', '特效插件']
  },
  {
    id: 'field-plugin-waifu-model',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'waifu_model' } },
    pageTitle: '插件管理',
    sectionTitle: '看板娘模型',
    fieldLabel: '看板娘模型',
    keywords: ['看板娘模型', 'Live2D模型', '角色模型'],
    aliases: ['waifu_model', '模型路径', '材质列表', '问候语', '闲置消息']
  },
  {
    id: 'field-plugin-editor',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'editor' } },
    pageTitle: '插件管理',
    sectionTitle: '文章编辑器',
    fieldLabel: '文章编辑器插件',
    keywords: ['文章编辑器', '编辑器插件', 'Markdown编辑器'],
    aliases: ['editor', '使用此编辑器']
  },
  {
    id: 'field-plugin-article-theme',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'article_theme' } },
    pageTitle: '插件管理',
    sectionTitle: '文章主题',
    fieldLabel: '文章主题插件',
    keywords: ['文章主题', '阅读主题', '文章样式'],
    aliases: ['article_theme', '使用此主题', '主题样式']
  },
  {
    id: 'field-plugin-payment',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'payment' } },
    pageTitle: '插件管理',
    sectionTitle: '文章付费',
    fieldLabel: '文章付费插件',
    keywords: ['文章付费', '付费阅读', '内容变现'],
    aliases: ['payment', '使用此付费插件']
  },
  {
    id: 'field-plugin-ai-tool',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'ai_tool' } },
    pageTitle: '插件管理',
    sectionTitle: 'AI工具',
    fieldLabel: 'AI工具',
    keywords: ['AI工具', 'HTTP工具', '工具参数Schema'],
    aliases: ['工具配置', '插件', '请求地址', '请求头模板', '结果路径']
  },
  {
    id: 'field-plugin-particle-effect',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'particle_effect' } },
    pageTitle: '插件管理',
    sectionTitle: '全屏飘落特效',
    fieldLabel: '全屏飘落特效插件',
    keywords: ['全屏飘落特效', '全局飘落特效', '全局飘落', '全屏特效', '全屏粒子特效', '动态粒子特效', '樱花飘落', '背景动画'],
    aliases: ['particle_effect', '樱花', '雪花', '飘落特效', '全局特效']
  },
  {
    id: 'field-plugin-install',
    type: 'field',
    route: '/pluginManager',
    pageTitle: '插件管理',
    sectionTitle: '插件包安装',
    fieldLabel: '安装插件包',
    keywords: ['安装插件包', '上传插件包', '插件安装'],
    aliases: ['zip插件包', '拖拽安装', 'manifest']
  },
  {
    id: 'page-post-list',
    type: 'page',
    route: '/postList',
    pageTitle: '文章管理',
    sectionTitle: '文章列表与导入导出',
    fieldLabel: '文章管理',
    keywords: ['文章管理', '博客管理', '文章列表'],
    aliases: ['postList', '文章', '博客', '导入文章', '导出文章', '新增文章']
  },
  {
    id: 'field-post-create',
    type: 'field',
    route: '/postEdit',
    pageTitle: '文章管理',
    sectionTitle: '文章编辑',
    fieldLabel: '新增文章',
    keywords: ['新增文章', '新建文章', '创建文章', '发布文章'],
    aliases: ['postEdit', '写文章', '文章编辑器', '写博客']
  },
  {
    id: 'field-post-import',
    type: 'field',
    route: '/postList',
    pageTitle: '文章管理',
    sectionTitle: '文章导入导出',
    fieldLabel: '导入文章',
    keywords: ['导入文章', '批量导入文章', '文章导入'],
    aliases: ['导入Markdown', '导入JSON', 'openImportDialog']
  },
  {
    id: 'field-post-export',
    type: 'field',
    route: '/postList',
    pageTitle: '文章管理',
    sectionTitle: '文章导入导出',
    fieldLabel: '导出所有文章',
    keywords: ['导出文章', '导出所有文章', '文章导出', '备份文章'],
    aliases: ['exportAllArticles', '导出Markdown', '导出JSON']
  },
  {
    id: 'field-plugin-create',
    type: 'field',
    route: '/pluginManager',
    pageTitle: '插件管理',
    sectionTitle: '插件操作',
    fieldLabel: '新增插件',
    keywords: ['新增插件', '新建插件', '创建插件'],
    aliases: ['handleCreate', '添加插件', '插件新增']
  },
  {
    id: 'field-plugin-create-ai-tool',
    type: 'field',
    route: { path: '/pluginManager', query: { type: 'ai_tool' } },
    pageTitle: '插件管理',
    sectionTitle: 'AI工具管理',
    fieldLabel: '新增AI工具插件',
    keywords: ['新增AI工具插件', '新建AI工具', '创建AI工具插件'],
    aliases: ['ai_tool', 'AI工具插件', '联网搜索插件', '工具插件']
  },
  {
    id: 'page-resource-path-list',
    type: 'page',
    route: '/resourcePathList',
    pageTitle: '资源聚合',
    sectionTitle: '资源聚合与本站信息',
    fieldLabel: '资源聚合',
    keywords: ['资源聚合', '资源管理', '聚合资源'],
    aliases: ['resourcePathList', 'siteInfo', '本站信息', '联系方式', '快捷入口', '侧边栏背景', '友链', '收藏夹']
  },
  {
    id: 'page-resource-list',
    type: 'page',
    route: '/resourceList',
    pageTitle: '资源管理',
    sectionTitle: '文件资源与媒体管理',
    fieldLabel: '资源管理',
    keywords: ['资源管理', '文件管理', '媒体资源'],
    aliases: ['resourceList', '公共资源', '文章封面', '文章图片', '文章附件', '文章视频', '视频文章', '网站头像', '背景图片', '表情包', '随机头像', '随机封面', '收藏夹封面', '孤儿资源', '未引用资源', '无引用资源', '无效资源', '失效资源', '无法加载资源', '加载失败资源']
  },
  {
    id: 'field-resource-orphan-resource',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'orphanResource' } },
    pageTitle: '资源管理',
    sectionTitle: '孤儿资源',
    fieldLabel: '孤儿资源',
    keywords: ['孤儿资源', '未引用资源', '无引用资源'],
    aliases: ['orphanResource', '未使用资源', '无引用文件']
  },
  {
    id: 'field-resource-invalid-resource',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'invalidResource' } },
    pageTitle: '资源管理',
    sectionTitle: '无效资源',
    fieldLabel: '无效资源',
    keywords: ['无效资源', '失效资源', '无法加载资源', '加载失败资源'],
    aliases: ['invalidResource', 'brokenResource', '失效文件', '加载失败文件']
  },
  {
    id: 'field-resource-assets',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'assets' } },
    pageTitle: '资源管理',
    sectionTitle: '公共资源',
    fieldLabel: '公共资源',
    keywords: ['公共资源', '资源文件', '文件资源'],
    aliases: ['assets', '静态资源', '文件管理']
  },
  {
    id: 'field-resource-article-cover',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'articleCover' } },
    pageTitle: '资源管理',
    sectionTitle: '文章封面',
    fieldLabel: '文章封面',
    keywords: ['文章封面', '封面图片', '博客封面'],
    aliases: ['articleCover', '封面资源']
  },
  {
    id: 'field-resource-web-avatar',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'webAvatar' } },
    pageTitle: '资源管理',
    sectionTitle: '网站头像',
    fieldLabel: '网站头像资源',
    keywords: ['网站头像', '站点头像', '头像资源'],
    aliases: ['webAvatar', '网站图标资源']
  },
  {
    id: 'field-resource-web-background',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'webBackgroundImage' } },
    pageTitle: '资源管理',
    sectionTitle: '背景图片',
    fieldLabel: '背景图片资源',
    keywords: ['背景图片', '站点背景', '背景资源'],
    aliases: ['webBackgroundImage', '背景图', '背景素材']
  },
  {
    id: 'field-resource-user-avatar',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'userAvatar' } },
    pageTitle: '资源管理',
    sectionTitle: '用户头像',
    fieldLabel: '用户头像资源',
    keywords: ['用户头像', '头像资源', '用户头像图片'],
    aliases: ['userAvatar', '头像库']
  },
  {
    id: 'field-resource-article-picture',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'articlePicture' } },
    pageTitle: '资源管理',
    sectionTitle: '文章图片',
    fieldLabel: '文章图片资源',
    keywords: ['文章图片', '正文图片', '博客配图'],
    aliases: ['articlePicture', '文章配图']
  },
  {
    id: 'field-resource-article-file',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'articleFile' } },
    pageTitle: '资源管理',
    sectionTitle: '文章附件',
    fieldLabel: '文章附件资源',
    keywords: ['文章附件', '附件资源', '下载附件', '正文文件'],
    aliases: ['articleFile', '文章文件']
  },
  {
    id: 'field-resource-internet-meme',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'internetMeme' } },
    pageTitle: '资源管理',
    sectionTitle: '表情包',
    fieldLabel: '表情包资源',
    keywords: ['表情包', '梗图', 'meme'],
    aliases: ['internetMeme', '表情图']
  },
  {
    id: 'field-resource-random-avatar',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'randomAvatar' } },
    pageTitle: '资源管理',
    sectionTitle: '随机头像',
    fieldLabel: '随机头像资源',
    keywords: ['随机头像', '头像池', '随机头像图片'],
    aliases: ['randomAvatar', '头像随机池']
  },
  {
    id: 'field-resource-random-cover',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'randomCover' } },
    pageTitle: '资源管理',
    sectionTitle: '随机封面',
    fieldLabel: '随机封面资源',
    keywords: ['随机封面', '封面池', '随机封面图片'],
    aliases: ['randomCover', '封面随机池']
  },
  {
    id: 'field-resource-graffiti',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'graffiti' } },
    pageTitle: '资源管理',
    sectionTitle: '画笔图片',
    fieldLabel: '画笔图片资源',
    keywords: ['画笔图片', '涂鸦图片', '画笔素材'],
    aliases: ['graffiti', '涂鸦资源']
  },
  {
    id: 'field-resource-comment-picture',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'commentPicture' } },
    pageTitle: '资源管理',
    sectionTitle: '评论图片',
    fieldLabel: '评论图片资源',
    keywords: ['评论图片', '评论配图', '评论素材'],
    aliases: ['commentPicture', '评论资源']
  },
  {
    id: 'field-resource-group-avatar',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'im/groupAvatar' } },
    pageTitle: '资源管理',
    sectionTitle: '聊天群头像',
    fieldLabel: '聊天群头像资源',
    keywords: ['聊天群头像', '群头像', 'IM群头像'],
    aliases: ['im/groupAvatar', '群聊头像']
  },
  {
    id: 'field-resource-group-message',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'im/groupMessage' } },
    pageTitle: '资源管理',
    sectionTitle: '群聊天图片',
    fieldLabel: '群聊天图片资源',
    keywords: ['群聊天图片', '群消息图片', 'IM群聊天图片'],
    aliases: ['im/groupMessage', '群聊图片']
  },
  {
    id: 'field-resource-friend-message',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'im/friendMessage' } },
    pageTitle: '资源管理',
    sectionTitle: '朋友聊天图片',
    fieldLabel: '朋友聊天图片资源',
    keywords: ['朋友聊天图片', '私聊图片', 'IM朋友聊天图片'],
    aliases: ['im/friendMessage', '好友聊天图片']
  },
  {
    id: 'field-resource-funny-url',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'funnyUrl' } },
    pageTitle: '资源管理',
    sectionTitle: '音乐声音',
    fieldLabel: '音乐声音资源',
    keywords: ['音乐声音', '音频资源', '音乐文件'],
    aliases: ['funnyUrl', '歌曲资源']
  },
  {
    id: 'field-resource-funny-cover',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'funnyCover' } },
    pageTitle: '资源管理',
    sectionTitle: '音乐封面',
    fieldLabel: '音乐封面资源',
    keywords: ['音乐封面', '歌曲封面', '专辑封面'],
    aliases: ['funnyCover', '音乐图片']
  },
  {
    id: 'field-resource-love-bg-cover',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'love/bgCover' } },
    pageTitle: '资源管理',
    sectionTitle: '表白墙背景',
    fieldLabel: '表白墙背景资源',
    keywords: ['表白墙背景', 'Love背景', '情侣页背景'],
    aliases: ['love/bgCover', 'Love.Cover']
  },
  {
    id: 'field-resource-love-man-cover',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'love/manCover' } },
    pageTitle: '资源管理',
    sectionTitle: '表白墙男生封面',
    fieldLabel: '男生封面资源',
    keywords: ['表白墙男生封面', '男生封面', 'Love.Man'],
    aliases: ['love/manCover', '男生图片']
  },
  {
    id: 'field-resource-love-woman-cover',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'love/womanCover' } },
    pageTitle: '资源管理',
    sectionTitle: '表白墙女生封面',
    fieldLabel: '女生封面资源',
    keywords: ['表白墙女生封面', '女生封面', 'Love.Woman'],
    aliases: ['love/womanCover', '女生图片']
  },
  {
    id: 'field-resource-favorites-cover',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'favoritesCover' } },
    pageTitle: '资源管理',
    sectionTitle: '收藏夹封面',
    fieldLabel: '收藏夹封面资源',
    keywords: ['收藏夹封面', '收藏封面', 'favorites封面'],
    aliases: ['favoritesCover', '收藏夹图片']
  },
  {
    id: 'field-resource-video-article',
    type: 'field',
    route: { path: '/resourceList', query: { resourceType: 'video/article' } },
    pageTitle: '资源管理',
    sectionTitle: '文章视频',
    fieldLabel: '文章视频资源',
    keywords: ['文章视频', '视频文章', '视频资源', 'Video.Article'],
    aliases: ['video/article', '视频素材']
  },
  {
    id: 'field-resource-site-info',
    type: 'field',
    route: { path: '/resourcePathList', query: { resourceType: 'siteInfo' } },
    pageTitle: '资源聚合',
    sectionTitle: '本站信息',
    fieldLabel: '本站信息',
    keywords: ['本站信息', 'siteInfo', '网站名片'],
    aliases: ['网站描述', '网站封面', '站点简介']
  },
  {
    id: 'field-resource-contact',
    type: 'field',
    route: { path: '/resourcePathList', query: { resourceType: 'contact' } },
    pageTitle: '资源聚合',
    sectionTitle: '联系方式',
    fieldLabel: '联系方式',
    keywords: ['联系方式', '社交媒体', '联系图标'],
    aliases: ['contact', '联系方式链接', '社交入口']
  },
  {
    id: 'field-resource-quick-entry',
    type: 'field',
    route: { path: '/resourcePathList', query: { resourceType: 'quickEntry' } },
    pageTitle: '资源聚合',
    sectionTitle: '快捷入口',
    fieldLabel: '快捷入口',
    keywords: ['快捷入口', '快速入口', '快捷跳转'],
    aliases: ['quickEntry', '按钮入口', '侧边按钮']
  },
  {
    id: 'field-resource-aside-background',
    type: 'field',
    route: { path: '/resourcePathList', query: { resourceType: 'asideBackground' } },
    pageTitle: '资源聚合',
    sectionTitle: '侧边栏背景',
    fieldLabel: '侧边栏背景',
    keywords: ['侧边栏背景', '右侧背景', '边栏背景'],
    aliases: ['asideBackground', '额外背景', '渐变背景']
  },
  {
    id: 'page-love-list',
    type: 'page',
    route: '/loveList',
    pageTitle: '表白墙',
    sectionTitle: '表白墙与家庭祝福',
    fieldLabel: '表白墙',
    keywords: ['表白墙', '家庭祝福', '恋爱页面'],
    aliases: ['loveList', '情侣页', '恋爱墙', '祝福墙']
  },
  {
    id: 'field-love-records',
    type: 'field',
    route: '/loveList',
    pageTitle: '表白墙',
    sectionTitle: '表白墙记录',
    fieldLabel: '表白墙记录',
    keywords: ['表白墙记录', '情侣记录', '祝福记录'],
    aliases: ['男生昵称', '女生昵称', '倒计时标题', '家庭祝福']
  },
  {
    id: 'field-plugin-low-perf',
    type: 'field',
    route: '/pluginManager',
    pageTitle: '插件管理',
    sectionTitle: '鼠标点击效果插件',
    fieldLabel: '低性能设备自动关闭特效',
    keywords: ['低性能设备自动关闭特效', '性能优化', '卡顿优化'],
    aliases: ['FPS检测', '手机优化']
  },
  {
    id: 'field-plugin-disable-admin',
    type: 'field',
    route: '/pluginManager',
    pageTitle: '插件管理',
    sectionTitle: '鼠标点击效果插件',
    fieldLabel: '后台管理系统关闭特效',
    keywords: ['后台管理系统关闭特效', '后台特效', '关闭特效'],
    aliases: ['禁用特效']
  },
  {
    id: 'page-translation-model-manage',
    type: 'page',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '翻译功能与智能摘要',
    fieldLabel: '翻译模型配置',
    keywords: ['翻译模型配置', '翻译设置', '多语言设置', '智能摘要'],
    aliases: ['自动翻译', '摘要生成', '文章翻译']
  },
  {
    id: 'field-translation-global-llm-type',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '全局AI模型配置',
    fieldLabel: '大模型类型',
    keywords: ['翻译模型', '大模型类型', '翻译AI'],
    aliases: ['OpenAI', 'Claude', '硅基流动']
  },
  {
    id: 'field-translation-global-llm-model',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '全局AI模型配置',
    fieldLabel: '模型名称',
    keywords: ['翻译模型名称'],
    aliases: []
  },
  {
    id: 'field-translation-global-llm-url',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '全局AI模型配置',
    fieldLabel: 'API接口地址',
    keywords: ['接口地址', '翻译接口', '全局接口地址'],
    aliases: []
  },
  {
    id: 'field-translation-global-llm-key',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '全局AI模型配置',
    fieldLabel: 'API密钥',
    keywords: ['翻译API密钥', '全局AI密钥'],
    aliases: []
  },
  {
    id: 'field-translation-global-llm-reasoning',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '全局AI模型配置',
    fieldLabel: '思考程度',
    keywords: ['思考程度', '推理强度', 'reasoning effort'],
    aliases: ['低', '中', '高', '超高', 'reasoning_effort']
  },
  {
    id: 'field-translation-mode',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '翻译功能配置',
    fieldLabel: '翻译实现方式',
    keywords: ['翻译实现方式', '翻译引擎'],
    aliases: ['不翻译', 'API翻译', '全局AI', '独立AI']
  },
  {
    id: 'field-translation-source-lang',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '翻译功能配置',
    fieldLabel: '默认源语言',
    keywords: ['默认源语言', '文章原语言'],
    aliases: []
  },
  {
    id: 'field-translation-target-lang',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '翻译功能配置',
    fieldLabel: '默认目标语言',
    keywords: ['默认目标语言', '翻译目标语'],
    aliases: []
  },
  {
    id: 'field-translation-api-provider',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: 'API翻译配置',
    fieldLabel: '翻译引擎',
    keywords: ['翻译引擎', '百度翻译', '有道云翻译', '自定义HTTP接口'],
    aliases: []
  },
  {
    id: 'field-translation-summary-mode',
    type: 'field',
    route: '/translationModelManage',
    pageTitle: '翻译模型配置',
    sectionTitle: '智能摘要功能配置',
    fieldLabel: '摘要生成方式',
    keywords: ['摘要生成方式', '文章摘要', '内容提取'],
    aliases: ['TextRank', '大模型摘要']
  },
  {
    id: 'field-ai-mem0-enable',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观与个性化',
    sectionTitle: 'AI聊天配置 (扩展工具)',
    fieldLabel: '开启AI记忆',
    keywords: ['开启AI记忆', 'Mem0', '跨会话增强'],
    aliases: ['启用记忆', '上下文记忆']
  },
  {
    id: 'field-ai-mem0-key',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观与个性化',
    sectionTitle: 'AI聊天配置 (扩展工具)',
    fieldLabel: 'Mem0 密钥',
    keywords: ['Mem0 密钥', 'Mem0 API Key'],
    aliases: ['记忆API']
  },
  {
    id: 'field-ai-mem0-autosave',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观与个性化',
    sectionTitle: 'AI聊天配置 (扩展工具)',
    fieldLabel: '自动保存记忆',
    keywords: ['自动保存记忆', '自动提炼', '长记忆保存'],
    aliases: ['记忆存储']
  },
  {
    id: 'field-ai-mem0-autorecall',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观与个性化',
    sectionTitle: 'AI聊天配置 (扩展工具)',
    fieldLabel: '自动提取记忆',
    keywords: ['自动提取记忆', '长记忆读取', '语境提取'],
    aliases: ['历史注入']
  },
  {
    id: 'field-ai-mem0-limit',
    type: 'field',
    route: '/webAppearance',
    pageTitle: '外观与个性化',
    sectionTitle: 'AI聊天配置 (扩展工具)',
    fieldLabel: '注入提取上限',
    keywords: ['注入提取上限', '记忆提取上限', '召回限制'],
    aliases: ['记忆数量']
  }
];
