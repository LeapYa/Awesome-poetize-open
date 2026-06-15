-- SEO配置相关数据表创建脚本

-- 主SEO配置表
CREATE TABLE IF NOT EXISTS `poetize`.`seo_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `enable` tinyint(1) DEFAULT 1 COMMENT 'SEO功能总开关',
  `site_description` text COMMENT '网站描述',
  `site_keywords` text COMMENT '网站关键词',
  `site_logo` varchar(512) COMMENT '网站Logo',
  `site_icon` varchar(512) COMMENT '网站图标',
  `site_icon_192` varchar(512) COMMENT '网站图标192x192',
  `site_icon_512` varchar(512) COMMENT '网站图标512x512',
  `apple_touch_icon` varchar(512) COMMENT 'Apple Touch图标',
  `site_short_name` varchar(64) COMMENT '网站短名称',
  `default_author` varchar(128) DEFAULT 'Admin' COMMENT '默认作者',
  `custom_head_code` text COMMENT '自定义头部代码',
  `robots_txt` text COMMENT 'robots.txt内容',
  `auto_generate_meta_tags` tinyint(1) DEFAULT 1 COMMENT '自动生成元标签',
  `generate_sitemap` tinyint(1) DEFAULT 1 COMMENT '生成站点地图',
  `sitemap_change_frequency` varchar(32) DEFAULT 'weekly' COMMENT '站点地图更新频率',
  `sitemap_priority` varchar(8) DEFAULT '0.7' COMMENT '站点地图优先级',
  `sitemap_exclude` varchar(512) COMMENT '站点地图排除路径',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by` varchar(32) DEFAULT 'system' COMMENT '更新人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SEO主配置表';

-- 搜索引擎推送配置表
CREATE TABLE IF NOT EXISTS `poetize`.`seo_search_engine_push` (
  `id` int NOT NULL AUTO_INCREMENT,
  `seo_config_id` int NOT NULL COMMENT 'SEO配置ID',
  `engine_name` varchar(32) NOT NULL COMMENT '搜索引擎名称(baidu,google,bing等)',
  `engine_display_name` varchar(64) COMMENT '搜索引擎显示名称',
  `push_enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用推送',
  `api_key` varchar(512) COMMENT 'API密钥(加密存储)',
  `api_token` varchar(512) COMMENT 'API令牌(加密存储)',
  `push_url` varchar(512) COMMENT '推送URL',
  `push_delay_seconds` int DEFAULT 300 COMMENT '推送延迟秒数',
  `last_push_time` datetime COMMENT '最后推送时间',
  `push_count` int DEFAULT 0 COMMENT '推送次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seo_engine` (`seo_config_id`, `engine_name`),
  KEY `idx_engine_name` (`engine_name`),
  FOREIGN KEY (`seo_config_id`) REFERENCES `seo_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索引擎推送配置表';

-- 网站验证配置表
CREATE TABLE IF NOT EXISTS `poetize`.`seo_site_verification` (
  `id` int NOT NULL AUTO_INCREMENT,
  `seo_config_id` int NOT NULL COMMENT 'SEO配置ID',
  `platform` varchar(32) NOT NULL COMMENT '平台名称(baidu,google,bing等)',
  `platform_display_name` varchar(64) COMMENT '平台显示名称',
  `verification_code` varchar(512) COMMENT '验证代码',
  `verification_method` varchar(32) DEFAULT 'meta_tag' COMMENT '验证方式(meta_tag,html_file,dns)',
  `is_verified` tinyint(1) DEFAULT 0 COMMENT '是否已验证',
  `verified_time` datetime COMMENT '验证时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seo_platform` (`seo_config_id`, `platform`),
  FOREIGN KEY (`seo_config_id`) REFERENCES `seo_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网站验证配置表';

-- 社交媒体配置表
CREATE TABLE IF NOT EXISTS `poetize`.`seo_social_media` (
  `id` int NOT NULL AUTO_INCREMENT,
  `seo_config_id` int NOT NULL COMMENT 'SEO配置ID',
  `twitter_card` varchar(32) DEFAULT 'summary_large_image' COMMENT 'Twitter卡片类型',
  `twitter_site` varchar(128) COMMENT 'Twitter站点账号',
  `twitter_creator` varchar(128) COMMENT 'Twitter创建者账号',
  `og_type` varchar(32) DEFAULT 'article' COMMENT 'Open Graph类型',
  `og_image` varchar(512) COMMENT 'Open Graph图片',
  `fb_app_id` varchar(128) COMMENT 'Facebook应用ID',
  `fb_page_url` varchar(512) COMMENT 'Facebook页面URL',
  `linkedin_company_id` varchar(128) COMMENT 'LinkedIn公司ID',
  `linkedin_mode` varchar(32) DEFAULT 'standard' COMMENT 'LinkedIn模式',
  `pinterest_verification` varchar(512) COMMENT 'Pinterest验证码',
  `pinterest_description` varchar(512) COMMENT 'Pinterest描述',
  `wechat_miniprogram_path` varchar(512) COMMENT '微信小程序路径',
  `wechat_miniprogram_id` varchar(128) COMMENT '微信小程序ID',
  `qq_miniprogram_path` varchar(512) COMMENT 'QQ小程序路径',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seo_social` (`seo_config_id`),
  FOREIGN KEY (`seo_config_id`) REFERENCES `seo_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社交媒体配置表';

-- PWA配置表
CREATE TABLE IF NOT EXISTS `poetize`.`seo_pwa_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `seo_config_id` int NOT NULL COMMENT 'SEO配置ID',
  `pwa_display` varchar(32) DEFAULT 'standalone' COMMENT 'PWA显示模式',
  `pwa_background_color` varchar(16) DEFAULT '#ffffff' COMMENT 'PWA背景颜色',
  `pwa_theme_color` varchar(16) DEFAULT '#1976d2' COMMENT 'PWA主题颜色',
  `pwa_orientation` varchar(32) DEFAULT 'portrait-primary' COMMENT 'PWA屏幕方向',
  `pwa_screenshot_desktop` varchar(512) COMMENT 'PWA桌面截图',
  `pwa_screenshot_mobile` varchar(512) COMMENT 'PWA移动端截图',
  `android_app_id` varchar(128) COMMENT 'Android应用ID',
  `ios_app_id` varchar(128) COMMENT 'iOS应用ID',
  `prefer_native_apps` tinyint(1) DEFAULT 0 COMMENT '是否优先使用原生应用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seo_pwa` (`seo_config_id`),
  FOREIGN KEY (`seo_config_id`) REFERENCES `seo_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PWA配置表';

-- 通知配置表
CREATE TABLE IF NOT EXISTS `poetize`.`seo_notification_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `seo_config_id` int NOT NULL COMMENT 'SEO配置ID',
  `push_delay_seconds` int DEFAULT 300 COMMENT '推送延迟秒数',
  `enable_push_notification` tinyint(1) DEFAULT 0 COMMENT '启用推送通知',
  `notify_only_on_failure` tinyint(1) DEFAULT 0 COMMENT '仅失败时通知',
  `notification_email` varchar(256) COMMENT '通知邮箱',
  `notification_webhook` varchar(512) COMMENT '通知Webhook',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seo_notification` (`seo_config_id`),
  FOREIGN KEY (`seo_config_id`) REFERENCES `seo_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知配置表';


