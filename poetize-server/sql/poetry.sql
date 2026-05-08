SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS poetize DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================
-- 数据库迁移版本记录表（必须在最前面）
-- ============================================
DROP TABLE IF EXISTS `poetize`.`db_migrations`;

CREATE TABLE `poetize`.`db_migrations` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `version` varchar(20) NOT NULL COMMENT '版本号（迁移脚本文件名，不含.sql后缀）',
  `description` varchar(200) DEFAULT NULL COMMENT '迁移说明',
  `executed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  `execution_time_ms` int DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `success` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否执行成功 [0:失败, 1:成功]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version` (`version`) COMMENT '版本号唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库迁移版本记录表';


DROP TABLE IF EXISTS `poetize`.`user`;

CREATE TABLE `poetize`.`user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `username` varchar(32) DEFAULT NULL COMMENT '用户名',
  `password` varchar(128) DEFAULT NULL COMMENT '密码',
  `phone_number` varchar(16) DEFAULT NULL COMMENT '手机号',
  `email` varchar(32) DEFAULT NULL COMMENT '用户邮箱',
  `user_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用[0:否，1:是]',
  `gender` tinyint(2) DEFAULT NULL COMMENT '性别[1:男，2:女，0:保密]',
  `open_id` varchar(128) DEFAULT NULL COMMENT 'openId',
  `platform_type` varchar(32) DEFAULT NULL COMMENT '第三方平台类型[wx,qq,weibo等]',
  `uid` varchar(128) DEFAULT NULL COMMENT '第三方平台用户唯一标识',
  `avatar` varchar(256) DEFAULT NULL COMMENT '头像',
  `admire` varchar(32) DEFAULT NULL COMMENT '赞赏',
  `subscribe` text DEFAULT NULL COMMENT '订阅',
  `introduction` varchar(4096) DEFAULT NULL COMMENT '简介',
  `user_type` tinyint(2) NOT NULL DEFAULT 2 COMMENT '用户类型[0:admin，1:管理员，2:普通用户]',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最终修改时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '最终修改人',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用[0:未删除，1:已删除]',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

DROP TABLE IF EXISTS `poetize`.`article`;

CREATE TABLE `poetize`.`article` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int NOT NULL COMMENT '用户ID',
  `sort_id` int NOT NULL COMMENT '分类ID',
  `label_id` int NOT NULL COMMENT '标签ID',
  `article_cover` varchar(256) DEFAULT NULL COMMENT '封面',
  `article_title` varchar(500) NOT NULL COMMENT '博文标题',
  `article_content` text NOT NULL COMMENT '博文内容',
  `summary` varchar(500) DEFAULT NULL COMMENT '文章摘要',
  `video_url` varchar(1024) DEFAULT NULL COMMENT '视频链接',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览量',
  `view_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可见[0:否，1:是]',
  `password` varchar(128) DEFAULT NULL COMMENT '密码',
  `tips` varchar(128) DEFAULT NULL COMMENT '提示',
  `recommend_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否推荐[0:否，1:是]',
  `comment_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用评论[0:否，1:是]',
  `submit_to_search_engine` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否推送至搜索引擎[0:否，1:是]',
  `pay_type` tinyint(1) DEFAULT 0 COMMENT '付费类型 [0:免费, 1:按文章付费, 2:会员专属, 3:赞赏解锁, 4:固定金额解锁]',
  `pay_amount` decimal(10,2) DEFAULT NULL COMMENT '付费金额(元)',
  `free_percent` int DEFAULT 30 COMMENT '免费预览百分比(0-100)',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime  DEFAULT CURRENT_TIMESTAMP COMMENT '最终修改时间',
  `update_by` varchar(32) DEFAULT NULL COMMENT '最终修改人',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用[0:未删除，1:已删除]',

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

DROP TABLE IF EXISTS `poetize`.`comment`;

CREATE TABLE `poetize`.`comment` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `source` int NOT NULL COMMENT '评论来源标识',
  `type` varchar(32) NOT NULL COMMENT '评论来源类型',
  `parent_comment_id` int NOT NULL DEFAULT 0 COMMENT '父评论ID',
  `user_id` int NOT NULL COMMENT '发表用户ID',
  `floor_comment_id` int DEFAULT NULL COMMENT '楼层评论ID',
  `parent_user_id` int DEFAULT NULL COMMENT '父发表用户名ID',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  `comment_content` varchar(1024) NOT NULL COMMENT '评论内容',
  `comment_info` varchar(256) DEFAULT NULL COMMENT '评论额外信息',
  `ip_address` varchar(45) DEFAULT NULL COMMENT 'IP地址',
  `location` varchar(100) DEFAULT NULL COMMENT '地理位置',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  KEY `source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章评论表';

DROP TABLE IF EXISTS `poetize`.`sort`;

CREATE TABLE `poetize`.`sort` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `sort_name` varchar(32) NOT NULL COMMENT '分类名称',
  `sort_description` varchar(256) NOT NULL COMMENT '分类描述',
  `sort_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '分类类型[0:导航栏分类，1:普通分类]',
  `priority` int DEFAULT NULL COMMENT '分类优先级：数字小的在前面',

  PRIMARY KEY (`id`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='分类';

DROP TABLE IF EXISTS `poetize`.`label`;

CREATE TABLE `poetize`.`label` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `sort_id` int NOT NULL COMMENT '分类ID',
  `label_name` varchar(32) NOT NULL COMMENT '标签名称',
  `label_description` varchar(256) NOT NULL COMMENT '标签描述',

  PRIMARY KEY (`id`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='标签';

DROP TABLE IF EXISTS `poetize`.`tree_hole`;

CREATE TABLE `poetize`.`tree_hole` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `avatar` varchar(256) DEFAULT NULL COMMENT '头像',
  `message` varchar(64) NOT NULL COMMENT '留言',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='树洞';

DROP TABLE IF EXISTS `poetize`.`wei_yan`;

CREATE TABLE `poetize`.`wei_yan` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int NOT NULL COMMENT '用户ID',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  `content` varchar(1024) NOT NULL COMMENT '内容',
  `type` varchar(32) NOT NULL COMMENT '类型',
  `source` int DEFAULT NULL COMMENT '来源标识',
  `is_public` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否公开[0:仅自己可见，1:所有人可见]',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微言表';

DROP TABLE IF EXISTS `poetize`.`web_info`;

CREATE TABLE `poetize`.`web_info` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `web_name` varchar(16) NOT NULL COMMENT '网站名称',
  `web_title` varchar(512) NOT NULL COMMENT '网站信息',
  `site_address` varchar(255) DEFAULT NULL COMMENT '网站地址（完整URL）',
  `notices` varchar(512) DEFAULT NULL COMMENT '公告',
  `footer` varchar(256) NOT NULL COMMENT '页脚',
  `background_image` varchar(256) DEFAULT NULL COMMENT '背景',
  `avatar` varchar(256) NOT NULL COMMENT '头像',
  `random_avatar` text DEFAULT NULL COMMENT '随机头像',
  `random_name` varchar(4096) DEFAULT NULL COMMENT '随机名称',
  `random_cover` text DEFAULT NULL COMMENT '随机封面',
  `waifu_json` text DEFAULT NULL COMMENT '看板娘消息',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用[0:否，1:是]',
  `home_page_pull_up_height` int(11) NULL DEFAULT -1 COMMENT '首页上拉高度',
  `api_enabled` tinyint(1) DEFAULT 0 COMMENT 'API是否启用[0:否，1:是]',
  `api_key` varchar(255) DEFAULT NULL COMMENT 'API密钥',
  `api_ip_whitelist` text DEFAULT NULL COMMENT 'API IP白名单，支持IP和CIDR，逗号或换行分隔',
  `nav_config` text DEFAULT NULL COMMENT '导航栏配置JSON',
  `enable_waifu` tinyint(1) DEFAULT 0 COMMENT '看板娘是否启用[0:否，1:是]',
  `waifu_display_mode` varchar(20) DEFAULT 'live2d' COMMENT '看板娘显示模式 [live2d:Live2D看板娘, button:简洁按钮]',
  `footer_background_image` varchar(256) DEFAULT NULL COMMENT '页脚背景图片',
  `footer_background_config` text DEFAULT NULL COMMENT '页脚背景图片位置配置(JSON格式)',
  `email` varchar(255) DEFAULT NULL COMMENT '联系邮箱',
  `minimal_footer` tinyint(1) DEFAULT 0 COMMENT '极简页脚开关[0:否，1:是]',
  `enable_auto_night` tinyint(1) DEFAULT 0 COMMENT '自动夜间开关[0:否，1:是]',
  `auto_night_start` int DEFAULT 23 COMMENT '夜间开始时间(小时)',
  `auto_night_end` int DEFAULT 7 COMMENT '夜间结束时间(小时)',
  `enable_gray_mode` tinyint(1) DEFAULT 0 COMMENT '灰色模式开关[0:否，1:是]',
  `enable_dynamic_title` tinyint(1) DEFAULT 1 COMMENT '动态标题开关[0:否，1:是]',
  `mobile_drawer_config` TEXT NULL COMMENT '移动端侧边栏配置(JSON格式)',
  `mouse_click_effect` VARCHAR(20) DEFAULT 'none' COMMENT '鼠标点击效果类型 [none:无, text:文字, firework:烟花]',
  `mouse_click_effect_config` TEXT COMMENT '鼠标点击特效配置JSON',

  PRIMARY KEY (`id`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='网站信息表';

DROP TABLE IF EXISTS `poetize`.`resource_path`;

CREATE TABLE `poetize`.`resource_path` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `title` varchar(64) NOT NULL COMMENT '标题',
  `classify` varchar(32) DEFAULT NULL COMMENT '分类',
  `cover` varchar(256) DEFAULT NULL COMMENT '封面',
  `url` varchar(256) DEFAULT NULL COMMENT '链接',
  `introduction` varchar(1024) DEFAULT NULL COMMENT '简介',
  `type` varchar(32) NOT NULL COMMENT '资源类型',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用[0:否，1:是]',
  `remark` text DEFAULT NULL COMMENT '备注',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='资源聚合';

DROP TABLE IF EXISTS `poetize`.`resource`;

CREATE TABLE `poetize`.`resource` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int NOT NULL COMMENT '用户ID',
  `type` varchar(32) NOT NULL COMMENT '资源类型',
  `path` varchar(256) NOT NULL COMMENT '资源路径',
  `size` int DEFAULT NULL COMMENT '资源内容的大小，单位：字节',
  `original_name` varchar(512) DEFAULT NULL COMMENT '文件名称',
  `mime_type` varchar(256) DEFAULT NULL COMMENT '资源的 MIME 类型',
  `resource_hash` varchar(64) DEFAULT NULL COMMENT '资源内容哈希（SHA-256）',
  `width` int DEFAULT NULL COMMENT '图片宽度（像素）',
  `height` int DEFAULT NULL COMMENT '图片高度（像素）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用[0:否，1:是]',
  `store_type` varchar(16) DEFAULT NULL COMMENT '存储平台',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源信息';

DROP TABLE IF EXISTS `poetize`.`history_info`;

CREATE TABLE `poetize`.`history_info` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int DEFAULT NULL COMMENT '用户ID',
  `ip` varchar(128) NOT NULL COMMENT 'ip',
  `nation` varchar(64) DEFAULT NULL COMMENT '国家',
  `province` varchar(64) DEFAULT NULL COMMENT '省份',
  `city` varchar(64) DEFAULT NULL COMMENT '城市',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=RocksDB DEFAULT CHARSET=utf8mb4 COMMENT='历史信息';

DROP TABLE IF EXISTS `poetize`.`sys_config`;

CREATE TABLE `poetize`.`sys_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `config_name` varchar(128) NOT NULL COMMENT '名称',
  `config_key` varchar(64) NOT NULL COMMENT '键名',
  `config_value` text DEFAULT NULL COMMENT '键值',
  `config_type` char(1) NOT NULL COMMENT '1 私用 2 公开',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key_type` (`config_key`,`config_type`) COMMENT '配置键和类型联合唯一索引'
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='参数配置表';

DROP TABLE IF EXISTS `poetize`.`sys_plugin`;

CREATE TABLE `poetize`.`sys_plugin` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '插件ID',
  `plugin_type` varchar(50) NOT NULL COMMENT '插件类型 [mouse_click_effect: 鼠标点击效果]',
  `plugin_key` varchar(50) NOT NULL COMMENT '插件唯一标识符',
  `plugin_name` varchar(100) NOT NULL COMMENT '插件名称',
  `plugin_description` varchar(500) DEFAULT NULL COMMENT '插件描述',
  `version` varchar(20) DEFAULT NULL COMMENT '插件版本号',
  `author` varchar(64) DEFAULT NULL COMMENT '插件作者',
  `plugin_config` text DEFAULT NULL COMMENT '插件配置(JSON格式)',
  `manifest` json DEFAULT NULL COMMENT '插件 manifest.json 内容',
  `plugin_code` text DEFAULT NULL COMMENT '插件代码(JavaScript)',
  `backend_code` mediumtext DEFAULT NULL COMMENT '后端 Groovy 脚本源码',
  `frontend_css` text DEFAULT NULL COMMENT '前端 CSS 样式',
  `install_sql` text DEFAULT NULL COMMENT '安装 SQL（备份用）',
  `uninstall_sql` text DEFAULT NULL COMMENT '卸载 SQL（备份用）',
  `has_backend` tinyint(1) DEFAULT 0 COMMENT '是否有后端 Groovy 脚本',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用 [0:禁用, 1:启用]',
  `is_system` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否系统内置 [0:用户创建, 1:系统内置]',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序顺序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_type_key` (`plugin_type`, `plugin_key`) COMMENT '插件类型和标识符联合唯一索引',
  KEY `idx_plugin_type` (`plugin_type`) COMMENT '插件类型索引',
  KEY `idx_enabled` (`enabled`) COMMENT '启用状态索引'
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='插件配置表';

DROP TABLE IF EXISTS `poetize`.`sys_plugin_active`;

CREATE TABLE `poetize`.`sys_plugin_active` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `plugin_type` varchar(50) NOT NULL COMMENT '插件类型',
  `plugin_key` varchar(50) NOT NULL COMMENT '当前激活的插件标识符',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_type` (`plugin_type`) COMMENT '每种插件类型只能有一个激活项'
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='插件激活状态表';

DROP TABLE IF EXISTS `poetize`.`sys_mail_config`;

CREATE TABLE `poetize`.`sys_mail_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `host` varchar(255) NOT NULL COMMENT '邮箱服务器地址',
  `port` int NOT NULL DEFAULT 25 COMMENT '邮箱服务器端口',
  `username` varchar(255) NOT NULL COMMENT '邮箱账号',
  `password` varchar(500) DEFAULT NULL COMMENT '邮箱密码或授权码（加密存储）',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发件人名称',
  `use_ssl` tinyint(1) DEFAULT 0 COMMENT '是否启用SSL (0:否 1:是)',
  `use_starttls` tinyint(1) DEFAULT 0 COMMENT '是否启用STARTTLS (0:否 1:是)',
  `auth` tinyint(1) DEFAULT 1 COMMENT '是否需要认证 (0:否 1:是)',
  `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用 (0:禁用 1:启用)',
  `is_default` tinyint(1) DEFAULT 0 COMMENT '是否为默认配置 (0:否 1:是)',
  `connection_timeout` int DEFAULT 25000 COMMENT '连接超时(毫秒)',
  `timeout` int DEFAULT 25000 COMMENT '读取超时(毫秒)',
  `trust_all_certs` tinyint(1) DEFAULT 0 COMMENT '是否信任所有证书 (0:否 1:是)',
  `protocol` varchar(50) DEFAULT 'smtp' COMMENT '协议(smtp, smtps等)',
  `auth_mechanism` varchar(50) DEFAULT NULL COMMENT '认证机制(LOGIN, PLAIN, DIGEST-MD5等)',
  `debug` tinyint(1) DEFAULT 0 COMMENT '是否启用调试模式 (0:否 1:是)',
  `use_proxy` tinyint(1) DEFAULT 0 COMMENT '是否使用代理 (0:否 1:是)',
  `proxy_host` varchar(255) DEFAULT NULL COMMENT '代理服务器地址',
  `proxy_port` int DEFAULT NULL COMMENT '代理服务器端口',
  `proxy_user` varchar(100) DEFAULT NULL COMMENT '代理服务器用户名',
  `proxy_password` varchar(500) DEFAULT NULL COMMENT '代理服务器密码',
  `custom_properties` text DEFAULT NULL COMMENT '自定义属性(JSON格式)',
  `sort_order` int DEFAULT 0 COMMENT '排序顺序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_is_default` (`is_default`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='邮件配置表';

DROP TABLE IF EXISTS `poetize`.`sys_captcha_config`;

CREATE TABLE `poetize`.`sys_captcha_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `enable` tinyint(1) DEFAULT 0 COMMENT '是否启用验证码 (0:否 1:是)',
  `login` tinyint(1) DEFAULT 1 COMMENT '登录时启用 (0:否 1:是)',
  `register` tinyint(1) DEFAULT 1 COMMENT '注册时启用 (0:否 1:是)',
  `comment` tinyint(1) DEFAULT 0 COMMENT '评论时启用 (0:否 1:是)',
  `reset_password` tinyint(1) DEFAULT 1 COMMENT '重置密码时启用 (0:否 1:是)',
  `screen_size_threshold` int DEFAULT 768 COMMENT '屏幕宽度阈值(px)',
  `force_slide_for_mobile` tinyint(1) DEFAULT 1 COMMENT '移动端强制滑动 (0:否 1:是)',
  `slide_accuracy` int DEFAULT 5 COMMENT '滑动验证码精确度',
  `slide_success_threshold` decimal(3,2) DEFAULT 0.95 COMMENT '滑动成功阈值',
  `checkbox_track_sensitivity` decimal(3,2) DEFAULT 0.90 COMMENT '勾选轨迹敏感度',
  `checkbox_min_track_points` int DEFAULT 2 COMMENT '勾选最少轨迹点数',
  `checkbox_reply_sensitivity` decimal(3,2) DEFAULT 0.85 COMMENT '回复评论敏感度',
  `checkbox_max_retry_count` int DEFAULT 5 COMMENT '最大重试次数',
  `checkbox_retry_decrement` decimal(3,2) DEFAULT 0.02 COMMENT '重试降低敏感度',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='验证码配置表';

DROP TABLE IF EXISTS `poetize`.`third_party_oauth_config`;

CREATE TABLE `poetize`.`third_party_oauth_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform_type` varchar(32) NOT NULL COMMENT '平台类型（github, google, twitter, yandex, gitee等）',
  `platform_name` varchar(64) DEFAULT NULL COMMENT '平台名称',
  `client_id` varchar(256) DEFAULT NULL COMMENT '客户端ID',
  `client_secret` varchar(512) DEFAULT NULL COMMENT '客户端密钥',
  `client_key` varchar(256) DEFAULT NULL COMMENT '客户端Key（Twitter使用）',
  `redirect_uri` varchar(512) DEFAULT NULL COMMENT '重定向URI',
  `scope` varchar(256) DEFAULT NULL COMMENT '授权范围',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用该平台[0:否，1:是]',
  `global_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '全局是否启用第三方登录[0:否，1:是]',
  `sort_order` int DEFAULT 0 COMMENT '排序顺序',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除[0:未删除，1:已删除]',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_type` (`platform_type`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_global_enabled` (`global_enabled`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='第三方OAuth登录配置表';

DROP TABLE IF EXISTS `poetize`.`family`;

CREATE TABLE `poetize`.`family` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int NOT NULL COMMENT '用户ID',
  `bg_cover` varchar(256) NOT NULL COMMENT '背景封面',
  `man_cover` varchar(256) NOT NULL COMMENT '男生头像',
  `woman_cover` varchar(256) NOT NULL COMMENT '女生头像',
  `man_name` varchar(32) NOT NULL COMMENT '男生昵称',
  `woman_name` varchar(32) NOT NULL COMMENT '女生昵称',
  `timing` varchar(32) NOT NULL COMMENT '计时',
  `countdown_title` varchar(32) DEFAULT NULL COMMENT '倒计时标题',
  `countdown_time` varchar(32) DEFAULT NULL COMMENT '倒计时时间',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用[0:否，1:是]',
  `family_info` varchar(1024) DEFAULT NULL COMMENT '额外信息',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最终修改时间',

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭信息';


DROP TABLE IF EXISTS `poetize`.`im_chat_user_friend`;

CREATE TABLE `poetize`.`im_chat_user_friend` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int NOT NULL COMMENT '用户ID',
  `friend_id` int NOT NULL COMMENT '好友ID',
  `friend_status` tinyint(2) NOT NULL COMMENT '朋友状态[0:未审核，1:审核通过]',
  `remark` varchar(32) DEFAULT NULL COMMENT '备注',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友';

DROP TABLE IF EXISTS `poetize`.`im_chat_group`;

CREATE TABLE `poetize`.`im_chat_group` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `group_name` varchar(32) NOT NULL COMMENT '群名称',
  `master_user_id` int NOT NULL COMMENT '群主用户ID',
  `avatar` varchar(256) DEFAULT NULL COMMENT '群头像',
  `introduction` varchar(128) DEFAULT NULL COMMENT '简介',
  `notice` varchar(1024) DEFAULT NULL COMMENT '公告',
  `in_type` tinyint(1) NOT NULL DEFAULT 1 COMMENT '进入方式[0:无需验证，1:需要群主或管理员同意]',
  `group_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '类型[1:聊天群，2:话题]',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天群';

DROP TABLE IF EXISTS `poetize`.`im_chat_group_user`;

CREATE TABLE `poetize`.`im_chat_group_user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `group_id` int NOT NULL COMMENT '群ID',
  `user_id` int NOT NULL COMMENT '用户ID',
  `verify_user_id` int DEFAULT NULL COMMENT '审核用户ID',
  `remark` varchar(1024) DEFAULT NULL COMMENT '备注',
  `admin_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否管理员[0:否，1:是]',
  `user_status` tinyint(2) NOT NULL COMMENT '用户状态[0:未审核，1:审核通过，2:禁言]',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天群成员';

DROP TABLE IF EXISTS `poetize`.`im_chat_user_message`;

CREATE TABLE `poetize`.`im_chat_user_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `from_id` int NOT NULL COMMENT '发送ID',
  `to_id` int NOT NULL COMMENT '接收ID',
  `content` varchar(1024) NOT NULL COMMENT '内容',
  `message_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读[0:未读，1:已读]',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  KEY `union_index` (`to_id`,`message_status`)
) ENGINE=RocksDB DEFAULT CHARSET=utf8mb4 COMMENT='单聊记录';

DROP TABLE IF EXISTS `poetize`.`im_chat_user_group_message`;

CREATE TABLE `poetize`.`im_chat_user_group_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `group_id` int NOT NULL COMMENT '群ID',
  `from_id` int NOT NULL COMMENT '发送ID',
  `to_id` int DEFAULT NULL COMMENT '接收ID',
  `content` varchar(1024) NOT NULL COMMENT '内容',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`)
) ENGINE=RocksDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊记录';

DROP TABLE IF EXISTS `poetize`.`im_chat_last_read`;

CREATE TABLE `poetize`.`im_chat_last_read` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int NOT NULL COMMENT '用户ID',
  `chat_type` tinyint NOT NULL COMMENT '聊天类型[1:私聊，2:群聊]',
  `chat_id` int NOT NULL COMMENT '聊天ID（私聊为friendId，群聊为groupId）',
  `last_read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后查看时间',
  `is_hidden` tinyint NOT NULL DEFAULT 0 COMMENT '是否隐藏[0:否，1:是]',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_chat` (`user_id`, `chat_type`, `chat_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_chat` (`chat_type`, `chat_id`),
  KEY `idx_hidden` (`user_id`, `is_hidden`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天最后查看时间（私聊+群聊）';

DROP TABLE IF EXISTS `poetize`.`article_payment`;

CREATE TABLE `poetize`.`article_payment` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` INT NOT NULL COMMENT '博客用户ID',
    `article_id` INT NOT NULL COMMENT '文章ID（0=全站会员）',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `platform` VARCHAR(32) NOT NULL DEFAULT 'afdian' COMMENT '支付平台',
    `platform_order_id` VARCHAR(128) DEFAULT NULL COMMENT '平台订单号',
    `platform_user_id` VARCHAR(128) DEFAULT NULL COMMENT '平台用户ID',
    `custom_order_id` VARCHAR(128) DEFAULT NULL COMMENT '自定义订单ID',
    `payment_status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态 [0:待确认, 1:已支付, 2:已退款]',
    `pay_type` TINYINT(1) NOT NULL COMMENT '付费类型',
    `remark` VARCHAR(256) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `expire_time` DATETIME DEFAULT NULL COMMENT '会员过期时间（article_id=0 时使用，NULL=永久）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_order` (`platform_order_id`, `platform`),
    INDEX `idx_user_article` (`user_id`, `article_id`),
    INDEX `idx_custom_order` (`custom_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章付费记录表';

DROP TABLE IF EXISTS `poetize`.`article_translation`;

CREATE TABLE `poetize`.`article_translation` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `article_id` int NOT NULL COMMENT '文章ID',
  `language` varchar(10) NOT NULL COMMENT '语言代码',
  `title` varchar(500) DEFAULT NULL COMMENT '翻译后的标题',
  `content` text DEFAULT NULL COMMENT '翻译后的内容',
  `summary` text DEFAULT NULL COMMENT '文章摘要',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_language` (`article_id`, `language`),
  KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章翻译内容表';

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
  `og_site_name` varchar(128) DEFAULT 'POETIZE' COMMENT 'Open Graph站点名称',
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

-- AI配置统一管理表
CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_type` varchar(50) NOT NULL COMMENT '配置类型 (ai_chat:AI聊天 ai_api:AI接口 article_ai:文章AI助手)',
  `config_name` varchar(100) DEFAULT 'default' COMMENT '配置名称/标识',
  `enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用 (0:否 1:是)',
  
  -- ========== 通用AI配置字段 ==========
  `provider` varchar(50) DEFAULT NULL COMMENT 'AI服务提供商 (openai/anthropic/custom/deepseek/siliconflow等)',
  `api_key` varchar(500) DEFAULT NULL COMMENT 'API密钥(加密存储)',
  `api_base` varchar(500) DEFAULT NULL COMMENT 'API基础地址',
  `model` varchar(100) DEFAULT NULL COMMENT '模型名称',
  
  -- ========== AI聊天参数配置 ==========
  `temperature` decimal(3,2) DEFAULT 0.70 COMMENT '温度参数(0.0-2.0)',
  `max_tokens` int DEFAULT 1000 COMMENT '最大生成令牌数',
  `top_p` decimal(3,2) DEFAULT 1.00 COMMENT 'Top-p采样参数(0.0-1.0)',
  `frequency_penalty` decimal(3,2) DEFAULT 0.00 COMMENT '频率惩罚(-2.0到2.0)',
  `presence_penalty` decimal(3,2) DEFAULT 0.00 COMMENT '存在惩罚(-2.0到2.0)',
  
  -- ========== AI聊天外观设置 ==========
  `chat_name` varchar(50) DEFAULT 'AI助手' COMMENT '聊天助手名称',
  `chat_avatar` varchar(500) DEFAULT '' COMMENT '聊天助手头像URL',
  `welcome_message` varchar(500) DEFAULT '你好！我是你的AI助手，有什么可以帮助你的吗？' COMMENT '欢迎消息',
  `placeholder_text` varchar(200) DEFAULT '输入你想说的话...' COMMENT '输入框占位文本',
  `theme_color` varchar(20) DEFAULT '#4facfe' COMMENT '主题颜色',
  
  -- ========== AI聊天功能设置 ==========
  `max_conversation_length` int DEFAULT 20 COMMENT '对话历史最大长度',
  `enable_context` tinyint(1) DEFAULT 1 COMMENT '启用上下文 (0:否 1:是)',
  `enable_typing_indicator` tinyint(1) DEFAULT 1 COMMENT '启用输入指示器 (0:否 1:是)',
  `show_timestamp` tinyint(1) DEFAULT 1 COMMENT '显示时间戳 (0:否 1:是)',
  `response_delay` int DEFAULT 1000 COMMENT '响应延迟(毫秒)',
  `enable_quick_actions` tinyint(1) DEFAULT 1 COMMENT '启用快捷操作 (0:否 1:是)',
  `enable_chat_history` tinyint(1) DEFAULT 1 COMMENT '启用聊天历史 (0:否 1:是)',
  `enable_streaming` tinyint(1) DEFAULT 0 COMMENT '启用流式响应 (0:否 1:是)',
  `rate_limit` int DEFAULT 20 COMMENT '速率限制(每分钟消息数)',
  `max_message_length` int DEFAULT 500 COMMENT '单条消息最大长度',
  `require_login` tinyint(1) DEFAULT 1 COMMENT '需要登录 (0:否 1:是)',
  `enable_content_filter` tinyint(1) DEFAULT 1 COMMENT '启用内容过滤 (0:否 1:是)',
  
  -- ========== AI聊天高级功能 ==========
  `custom_instructions` text DEFAULT NULL COMMENT '自定义指令/系统提示词',
  `enable_thinking` tinyint(1) DEFAULT 0 COMMENT '启用思考模式 (0:否 1:是)',
  `enable_tools` tinyint(1) DEFAULT 1 COMMENT '启用MCP工具 (0:否 1:是)',
  
  -- ========== 记忆管理功能 ==========
  `enable_memory` tinyint(1) DEFAULT 0 COMMENT '启用Mem0记忆功能 (0:否 1:是)',
  `mem0_api_key` varchar(500) DEFAULT NULL COMMENT 'Mem0 API密钥(加密存储)',
  `memory_auto_save` tinyint(1) DEFAULT 1 COMMENT '自动保存对话记忆 (0:否 1:是)',
  `memory_auto_recall` tinyint(1) DEFAULT 1 COMMENT '自动检索相关记忆 (0:否 1:是)',
  `memory_recall_limit` int DEFAULT 3 COMMENT '检索记忆数量限制',
  
  -- ========== 文章AI助手配置字段 ==========
  `translation_type` varchar(20) DEFAULT 'none' COMMENT '翻译实现方式 (none:不翻译 baidu:百度翻译 custom:自定义API llm:使用全局AI模型 dedicated_llm:使用翻译独立AI模型)',
  `default_source_lang` varchar(10) DEFAULT 'zh' COMMENT '默认源语言',
  `default_target_lang` varchar(10) DEFAULT 'en' COMMENT '默认目标语言',
  
  -- ========== AI API配置字段 ==========
  `include_articles` tinyint(1) DEFAULT 0 COMMENT '包含文章数据 (0:否 1:是)',
  
  -- ========== JSON扩展字段 ==========
  `baidu_config` json DEFAULT NULL COMMENT '百度翻译配置 {app_id, app_secret}',
  `custom_config` json DEFAULT NULL COMMENT '自定义API配置 {api_url, api_key, app_secret}',
  `llm_config` json DEFAULT NULL COMMENT 'LLM配置 {model, api_url, api_key, prompt, interface_type, timeout}',
  `translation_llm_config` json DEFAULT NULL COMMENT '翻译独立AI配置 {model, api_url, api_key, prompt, interface_type, timeout}',
  `summary_config` json DEFAULT NULL COMMENT '摘要生成配置 {summaryMode, style, max_length, prompt, dedicated_llm}',
  `extra_config` json DEFAULT NULL COMMENT '其他扩展配置(JSON格式)',
  
  -- ========== 元数据字段 ==========
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_type_name` (`config_type`, `config_name`) COMMENT '配置类型和名称联合唯一索引',
  KEY `idx_config_type` (`config_type`) COMMENT '配置类型索引',
  KEY `idx_enabled` (`enabled`) COMMENT '启用状态索引'
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='AI配置统一管理表';

-- ============================================================
-- 插入默认配置数据
-- ============================================================

-- 1. AI聊天配置默认值
INSERT INTO `sys_ai_config` (
  `config_type`, `config_name`, `enabled`,
  `provider`, `api_key`, `api_base`, `model`,
  `temperature`, `max_tokens`, `top_p`, `frequency_penalty`, `presence_penalty`,
  `chat_name`, `chat_avatar`, `welcome_message`, `placeholder_text`, `theme_color`,
  `max_conversation_length`, `enable_context`, `enable_typing_indicator`, `show_timestamp`,
  `response_delay`, `enable_quick_actions`, `enable_chat_history`, `enable_streaming`,
  `rate_limit`, `max_message_length`, `require_login`, `enable_content_filter`,
  `custom_instructions`, `enable_thinking`, `enable_tools`,
  `enable_memory`, `mem0_api_key`, `memory_auto_save`, `memory_auto_recall`, `memory_recall_limit`,
  `remark`
) VALUES (
  'ai_chat', 'default', 0,
  'openai', '', '', 'gpt-3.5-turbo',
  0.70, 1000, 1.00, 0.00, 0.00,
  'AI助手', '', '你好！我是你的AI助手，有什么可以帮助你的吗？', '输入你想说的话...', '#4facfe',
  20, 1, 1, 1,
  1000, 1, 1, 0,
  20, 500, 1, 1,
  '', 0, 1,
  0, '', 1, 1, 3,
  'AI聊天默认配置'
) ON DUPLICATE KEY UPDATE id=id;

-- 2. AI API配置默认值
INSERT INTO `sys_ai_config` (
  `config_type`, `config_name`, `enabled`,
  `provider`, `api_key`, `api_base`, `model`,
  `include_articles`,
  `remark`
) VALUES (
  'ai_api', 'default', 0,
  'openai', '', '', 'gpt-3.5-turbo',
  0,
  'AI API默认配置'
) ON DUPLICATE KEY UPDATE id=id;

-- 3. 文章AI助手配置默认值
INSERT INTO `sys_ai_config` (
  `config_type`, `config_name`, `enabled`,
  `translation_type`, `default_source_lang`, `default_target_lang`,
  `llm_config`, `summary_config`,
  `remark`
) VALUES (
  'article_ai', 'default', 0,
  'none', 'zh', 'en',
  JSON_OBJECT(
    'model', 'gpt-3.5-turbo',
    'api_url', '',
    'api_key', '',
    'prompt', '将以下TOON格式数据从{source_lang}翻译为{target_lang}。\n\n规则：\n1. 保持TOON格式结构不变（2个空格缩进）\n2. 翻译title和content的值\n3. 保持Markdown格式\n4. 只返回TOON格式数据，不添加任何解释\n\n输入TOON数据：\n{toon_data}\n\n请返回翻译后的TOON数据，格式如下：\narticle:\n  title: (翻译后的{target_lang}标题)\n  content: (翻译后的{target_lang}内容)',
    'interface_type', 'openai',
    'timeout', 240
  ),
  JSON_OBJECT(
    'summaryMode', 'global',
    'style', 'concise',
    'max_length', 150,
    'prompt', '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 保持TOON格式结构不变（2个空格缩进）\n5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请返回TOON格式的摘要，格式如下：\n{toon_example}'
  ),
  '文章AI助手配置默认值'
) ON DUPLICATE KEY UPDATE id=id;


-- ============================================================
-- 字段说明和使用指南
-- ============================================================

-- 配置类型说明：
-- 1. ai_chat: AI聊天配置
--    - 主要字段：provider, api_key, api_base, model, temperature等聊天参数
--    - 外观字段：chat_name, chat_avatar, welcome_message, theme_color
--    - 功能字段：enable_streaming, rate_limit, enable_memory等
--
-- 2. ai_api: AI接口配置
--    - 主要字段：provider, api_key, api_base, model
--    - 特殊字段：include_articles
--
-- 3. article_ai: 文章AI助手配置（包含翻译、智能摘要等文章相关AI功能）
--    - 主要字段：translation_type, default_source_lang, default_target_lang
--    - JSON字段：baidu_config, custom_config, llm_config, translation_llm_config, summary_config
--    - translation_type决定翻译功能使用哪个实现方式：
--      * none: 不翻译（默认值，不生成translation表记录）
--      * baidu: 百度翻译API
--      * custom: 自定义翻译API
--      * llm: 使用全局AI模型（llm_config）
--      * dedicated_llm: 使用翻译独立AI模型（translation_llm_config）
--    - summary_config.summaryMode决定摘要生成方式（global/dedicated/textrank）
--
-- 安全注意事项：
-- - api_key 和 mem0_api_key 字段在应用层需要加密存储
-- - 查询时需要在应用层解密后使用
-- - 前端展示时需要脱敏处理（仅显示前4位和后4位）
--
-- 扩展性说明：
-- - extra_config 字段用于存储未来可能新增的配置项
-- - JSON字段支持灵活的嵌套结构
-- - 可通过 config_name 支持同类型多套配置
--
-- 语言映射说明：
-- - 语言代码到自然语言的映射直接定义在Java代码中（SysAiConfigService）
-- - 提供两套映射：前台展示用（原生语言）、后台管理用（中文）
-- - 前后端通过API接口统一获取，避免重复定义


INSERT INTO `poetize`.`user`(`id`, `username`, `password`, `phone_number`, `email`, `user_status`, `gender`, `open_id`, `admire`, `subscribe`, `avatar`, `introduction`, `user_type`, `update_by`, `deleted`) VALUES (1, 'Sara', '$2a$12$hQ2N3HlDqxdVrsJ4SKGY1.D6F/I1/dUBFVkckaq1DnJH693mPZS5S', '', '', 1, 1, '', '', '', '', '', 0, 'Sara', 0);

INSERT INTO `poetize`.`web_info`(`id`, `web_name`, `web_title`, `notices`, `footer`, `background_image`, `avatar`, `random_avatar`, `random_name`, `random_cover`, `waifu_json`, `status`, `api_enabled`, `api_key`, `nav_config`, `minimal_footer`, `mobile_drawer_config`) VALUES (1, 'Sara', 'POETIZE', '[]', '云想衣裳花想容， 春风拂槛露华浓。', '', '', '[]', '[]', '["/static/assets/backgroundPicture.jpg"]', '{
    "waifuPath": "/static/live2d-widget/waifu-tips.json",
    "cdnPath": "https://fastly.jsdelivr.net/gh/fghrsh/live2d_api/",
    "tools": ["hitokoto", "asteroids", "switch-model", "switch-texture", "photo", "info", "quit"]
}', 1, 0, NULL, '[{"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":true},{"name":"分类","icon":"📑","link":"#","type":"dropdown","order":2,"enabled":true},{"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":true},{"name":"友人帐","icon":"🤝","link":"/friends","type":"internal","order":4,"enabled":true},{"name":"曲乐","icon":"🎵","link":"/music","type":"internal","order":5,"enabled":true},{"name":"收藏夹","icon":"📁","link":"/favorites","type":"internal","order":6,"enabled":true},{"name":"留言","icon":"📪","link":"/message","type":"internal","order":7,"enabled":true},{"name":"联系我","icon":"💬","link":"#chat","type":"special","order":8,"enabled":true}]', 0, '{"titleType":"text","titleText":"欢迎光临","avatarSize":100,"backgroundType":"image","backgroundImage":"/assets/toolbar.jpg","backgroundColor":"#000000","backgroundGradient":"linear-gradient(60deg, #ffd7e4, #c8f1ff 95%)","maskOpacity":0.7,"menuFontColor":"#ffffff","showBorder":true,"borderColor":"rgba(255, 255, 255, 0.15)","showSnowflake":true}');

INSERT INTO `poetize`.`family` (`id`, `user_id`, `bg_cover`, `man_cover`, `woman_cover`, `man_name`, `woman_name`, `timing`, `countdown_title`, `countdown_time`, `status`, `family_info`, `like_count`, `create_time`, `update_time`) VALUES (1, 1, '背景封面', '男生头像', '女生头像', 'Sara', 'Abby', '2000-01-01 00:00:00', '春节倒计时', '2025-01-29 00:00:00', 1, '', 0, '2000-01-01 00:00:00', '2000-01-01 00:00:00');

INSERT INTO `poetize`.`im_chat_group` (`id`, `group_name`, `master_user_id`, `introduction`, `notice`, `in_type`) VALUES(-1, '公共聊天室', 1, '公共聊天室', '欢迎光临！', 0);

insert into `poetize`.`im_chat_group_user` (`id`, `group_id`, `user_id`, `admin_flag`, `user_status`) values(1, -1, 1, 1, 1);

INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (1, '邮箱验证码模板', 'user.code.format', '【POETIZE】%s为本次验证的验证码，请在5分钟内完成验证。为保证账号安全，请勿泄漏此验证码。', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (2, '邮箱订阅模板', 'user.subscribe.format', '【POETIZE】您订阅的专栏【%s】新增一篇文章：%s。', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (3, '默认存储平台（local:本地，qiniu:七牛云，lsky:兰空图床，easyimage:简单图床）', 'store.type', 'local', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (4, '本地存储启用状态', 'local.enable', 'true', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (5, '本地存储上传文件根目录', 'local.uploadUrl', '/app/static/', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (6, '本地存储下载前缀', 'local.downloadUrl', '/static/', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (7, '七牛云存储启用状态', 'qiniu.enable', 'false', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (8, '七牛云-accessKey', 'qiniu.accessKey', '', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (9, '七牛云-secretKey', 'qiniu.secretKey', '', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (10, '七牛云-bucket', 'qiniu.bucket', '', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (11, '七牛云-域名', 'qiniu.downloadUrl', '仿照：【https://file.poetize.cn/】，将域名换成自己的七牛云ip或域名', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (12, '七牛云上传地址', 'qiniuUrl', 'https://upload.qiniup.com', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (13, '兰空图床存储启用状态', 'lsky.enable', 'false', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (14, '兰空图床-API地址', 'lsky.url', 'http://your-lsky-instance.com/api/v1', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (15, '兰空图床-Token', 'lsky.token', '', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (16, '兰空图床-存储策略ID', 'lsky.strategy_id', '', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (17, '简单图床启用状态', 'easyimage.enable', 'false', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (18, '简单图床-API地址', 'easyimage.url', 'https://your-easyimage-instance.com/api/upload', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (19, '简单图床-Token', 'easyimage.token', '', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (20, 'IM-聊天室启用状态', 'im.enable', 'true', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (21, '备案号', 'beian', '', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (22, '公安备案号', 'policeBeian', '', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (23, '前端静态资源路径前缀', 'webStaticResourcePrefix', '/static/', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (24, 'WebP图片转换启用状态', 'image.webp.enabled', 'true', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (25, 'WebP转换最小文件大小(KB)', 'image.webp.min-size', '50', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (26, 'WebP转换最小节省比例(%)', 'image.webp.min-saving-ratio', '10', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (27, '图片压缩模式(lossy:有损,lossless:无损)', 'image.compress.mode', 'lossy', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (28, '图片压缩启用状态', 'image.compress.enabled', 'true', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (29, '字体文件CDN基础路径(末尾必须有/)', 'font.cdn.base-url', '/static/assets/font_chunks/', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (30, '是否使用单一字体文件', 'font.use.single', 'false', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (31, '单一字体文件名称', 'font.single.filename', 'font.woff2', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (32, '是否从远程加载字体Unicode范围', 'font.unicode.remote', 'true', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (33, '字体Unicode范围JSON文件路径', 'font.unicode.path', '/static/assets/font_chunks/unicode_ranges.json', '2');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (34, '腾讯位置服务Key', 'tencent.lbs.key', '', '1');
INSERT INTO `poetize`.`sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`) VALUES (35, '全局评论开关', 'enableComment', 'true', '2');

-- 插入默认的看板娘模型插件（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `enabled`, `is_system`, `sort_order`) VALUES
('waifu_model', 'pio', 'Pio酱', '来自 Potion Maker 的可爱女孩',
'{"modelPath": "Potion-Maker/Pio", "textures": ["Potion-Maker/Pio"], "scale": 1.0, "thumbnailUrl": "/static/waifu_previews/pio.png", "messages": {"greeting": ["你好呀~", "欢迎光临！"], "idle": ["无聊了...", "点点我嘛~"]}}',
1, 1, 0),
('waifu_model', 'tia', 'Tia酱', '来自 Potion Maker 的元气少女',
'{"modelPath": "Potion-Maker/Tia", "textures": ["Potion-Maker/Tia"], "scale": 1.0, "thumbnailUrl": "/static/waifu_previews/tia.png", "messages": {"greeting": ["嗨！", "今天也要加油哦！"], "idle": ["在想什么呢？", "陪我聊聊天吧~"]}}',
1, 1, 1),
('waifu_model', 'bilibili_22', 'Bilibili 22娘', '来自 Bilibili Live 的22号看板娘',
'{"modelPath": "bilibili-live/22", "textures": ["bilibili-live/22"], "scale": 1.0, "thumbnailUrl": "/static/waifu_previews/bilibili_22.png", "messages": {"greeting": ["22娘来啦~", "bilibili干杯！"], "idle": ["去看看番剧吧~", "今天投币了吗？"]}}',
1, 1, 2),
('waifu_model', 'bilibili_33', 'Bilibili 33娘', '来自 Bilibili Live 的33号看板娘',
'{"modelPath": "bilibili-live/33", "textures": ["bilibili-live/33"], "scale": 1.0, "thumbnailUrl": "/static/waifu_previews/bilibili_33.png", "messages": {"greeting": ["33娘驾到！", "素质三连走起！"], "idle": ["一键三连哦~", "关注一下呗？"]}}',
1, 1, 3),
('waifu_model', 'shizuku', 'Shizuku', 'Shizuku Talk 系列角色',
'{"modelPath": "ShizukuTalk/shizuku-48", "textures": ["ShizukuTalk/shizuku-48", "ShizukuTalk/shizuku-pajama"], "scale": 1.0, "thumbnailUrl": "/static/waifu_previews/shizuku.png", "messages": {"greeting": ["这里是Shizuku~", "有什么我能帮你的吗？"], "idle": ["换件衣服看看？", "今天天气真好~"]}}',
1, 1, 4),
('waifu_model', 'neptune', '海王星系列', '超次元游戏海王星角色合集',
'{"modelPath": "HyperdimensionNeptunia/neptune_classic", "textures": ["HyperdimensionNeptunia/neptune_classic", "HyperdimensionNeptunia/nepnep", "HyperdimensionNeptunia/neptune_santa", "HyperdimensionNeptunia/nepmaid", "HyperdimensionNeptunia/nepswim"], "scale": 1.0, "thumbnailUrl": "/static/waifu_previews/neptune.png", "messages": {"greeting": ["Nep! Nep!", "Neptune参上！"], "idle": ["我是主角哦~", "来玩游戏吧！"]}}',
1, 1, 5),
('waifu_model', 'murakumo', '叢雲', '舰队Collection - 叢雲',
'{"modelPath": "KantaiCollection/murakumo", "textures": ["KantaiCollection/murakumo"], "scale": 1.0, "thumbnailUrl": "/static/waifu_previews/murakumo.png", "messages": {"greeting": ["叢雲です", "あなたが提督？"], "idle": ["別に...", "何か用？"]}}',
1, 1, 6);

-- 设置默认激活的看板娘模型
INSERT IGNORE INTO `sys_plugin_active` (`plugin_type`, `plugin_key`) VALUES
('waifu_model', 'neptune');

-- 初始化编辑器插件
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('editor', 'vditor', 'Vditor（功能完整）', '功能最全的 Markdown 编辑器，支持即时渲染、分屏预览、所见即所得三种模式，适合追求完整编辑体验的用户', 
'{"editorKey":"vditor"}', NULL, 1, 1, 0),
('editor', 'split_preview', '分屏预览编辑器', '左侧编辑、右侧实时预览的 Markdown 编辑器，功能完善，覆盖绝大多数写作场景', 
'{"editorKey":"split_preview"}', NULL, 1, 1, 1),
('editor', 'ir', 'IR 即时渲染', '自研的即时渲染编辑器，光标行显示源码、其他行显示渲染效果，类似 Typora 体验，完全自定义样式，无闪烁问题', 
'{"editorKey":"ir"}', NULL, 1, 1, 2),
('editor', 'wysiwyg', 'WYSIWYG 所见即所得', '自研的所见即所得编辑器，全程显示渲染效果，编辑体验类似 Word，支持查看源码，适合不熟悉 Markdown 语法的用户', 
'{"editorKey":"wysiwyg"}', NULL, 1, 1, 3);

-- 设置默认激活的编辑器
INSERT IGNORE INTO `sys_plugin_active` (`plugin_type`, `plugin_key`) VALUES
('editor', 'ir');

-- 初始化文章主题插件
-- 用户视角的 H1~H5 对应实际 HTML 的 H2~H6
-- JSON中的 h1~h5 是用户视角的标题级别

-- 默认主题（与现有硬编码样式一致）
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('article_theme', 'default', '默认主题', '经典装饰风格，保留原始设计',
'{"headings":{"h1":{"emoji":"📑","color":null,"show":true,"paddingLeft":"40px"},"h2":{"emoji":"#","color":"#ff6d6d","show":true,"paddingLeft":"25px"},"h3":{"emoji":"▌","color":"#ff6d6d","show":true,"paddingLeft":"20px"},"h4":{"emoji":"🌷","color":null,"show":true,"paddingLeft":"28px"},"h5":{"emoji":"","color":null,"show":false,"paddingLeft":""}},"toc":{"emoji":"🏖️","show":true}}',
NULL, 1, 1, 0);

-- 简约主题
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('article_theme', 'minimal', '简约主题', '低调的灰色装饰，适合专注阅读',
'{"headings":{"h1":{"emoji":"","color":null,"show":false,"paddingLeft":""},"h2":{"emoji":"#","color":"#909399","show":true,"paddingLeft":"25px"},"h3":{"emoji":"▌","color":"#909399","show":true,"paddingLeft":"20px"},"h4":{"emoji":"","color":null,"show":false,"paddingLeft":""},"h5":{"emoji":"","color":null,"show":false,"paddingLeft":""}},"toc":{"emoji":"📖","show":true}}',
NULL, 1, 1, 1);

-- 无装饰主题
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('article_theme', 'plain', '无装饰', '移除所有标题装饰，纯净阅读体验',
'{"headings":{"h1":{"emoji":"","color":null,"show":false,"paddingLeft":""},"h2":{"emoji":"","color":null,"show":false,"paddingLeft":""},"h3":{"emoji":"","color":null,"show":false,"paddingLeft":""},"h4":{"emoji":"","color":null,"show":false,"paddingLeft":""},"h5":{"emoji":"","color":null,"show":false,"paddingLeft":""}},"toc":{"emoji":"","show":false}}',
NULL, 1, 1, 2);

-- 花园主题
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('article_theme', 'garden', '花园主题', '清新自然的植物装饰风格',
'{"headings":{"h1":{"emoji":"🌿","color":null,"show":true,"paddingLeft":"40px"},"h2":{"emoji":"🌱","color":null,"show":true,"paddingLeft":"36px"},"h3":{"emoji":"🍃","color":null,"show":true,"paddingLeft":"32px"},"h4":{"emoji":"🌷","color":null,"show":true,"paddingLeft":"32px"},"h5":{"emoji":"🌼","color":null,"show":true,"paddingLeft":"28px"}},"toc":{"emoji":"🌺","show":true}}',
NULL, 1, 1, 3);

-- 学术主题
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('article_theme', 'academic', '学术主题', '专业严谨的学术排版风格',
'{"headings":{"h1":{"emoji":"§","color":null,"show":true,"paddingLeft":"24px"},"h2":{"emoji":"¶","color":null,"show":true,"paddingLeft":"20px"},"h3":{"emoji":"▸","color":null,"show":true,"paddingLeft":"18px"},"h4":{"emoji":"•","color":null,"show":true,"paddingLeft":"16px"},"h5":{"emoji":"◦","color":null,"show":true,"paddingLeft":"16px"}},"toc":{"emoji":"📚","show":true}}',
NULL, 1, 1, 4);

-- 星空主题
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('article_theme', 'space', '星空主题', '梦幻星空装饰风格',
'{"headings":{"h1":{"emoji":"🌟","color":null,"show":true,"paddingLeft":"40px"},"h2":{"emoji":"⭐","color":null,"show":true,"paddingLeft":"33px"},"h3":{"emoji":"💫","color":null,"show":true,"paddingLeft":"31px"},"h4":{"emoji":"✨","color":null,"show":true,"paddingLeft":"31px"},"h5":{"emoji":"·","color":null,"show":true,"paddingLeft":"16px"}},"toc":{"emoji":"🛸","show":true}}',
NULL, 1, 1, 5);

-- 设置默认激活的文章主题
INSERT IGNORE INTO `sys_plugin_active` (`plugin_type`, `plugin_key`) VALUES
('article_theme', 'default');

-- 初始化动态粒子特效插件
SET @particle_effect_sakura_plugin_config = '{"imageUrl":"","quantity":50,"speed":1,"drift":1,"minScale":0.4,"maxScale":1,"spawnMode":"mixed","zIndex":9999999}';
SET @particle_effect_sakura_default_image = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAUgAAAEwCAYAAADVZeifAAAACXBIWXMAAACYAAAAmAGiyIKYAAAHG2lUWHRYTUw6Y29tLmFkb2JlLnhtcAAAAAAAPD94cGFja2V0IGJlZ2luPSLvu78iIGlkPSJXNU0wTXBDZWhpSHpyZVN6TlRjemtjOWQiPz4gPHg6eG1wbWV0YSB4bWxuczp4PSJhZG9iZTpuczptZXRhLyIgeDp4bXB0az0iQWRvYmUgWE1QIENvcmUgNS42LWMxNDIgNzkuMTYwOTI0LCAyMDE3LzA3LzEzLTAxOjA2OjM5ICAgICAgICAiPiA8cmRmOlJERiB4bWxuczpyZGY9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkvMDIvMjItcmRmLXN5bnRheC1ucyMiPiA8cmRmOkRlc2NyaXB0aW9uIHJkZjphYm91dD0iIiB4bWxuczp4bXBSaWdodHM9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9yaWdodHMvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtbG5zOnN0RXZ0PSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VFdmVudCMiIHhtbG5zOnhtcD0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wLyIgeG1sbnM6ZGM9Imh0dHA6Ly9wdXJsLm9yZy9kYy9lbGVtZW50cy8xLjEvIiB4bWxuczpwaG90b3Nob3A9Imh0dHA6Ly9ucy5hZG9iZS5jb20vcGhvdG9zaG9wLzEuMC8iIHhtcFJpZ2h0czpNYXJrZWQ9IkZhbHNlIiB4bXBNTTpPcmlnaW5hbERvY3VtZW50SUQ9InhtcC5kaWQ6NDFDMjQxQjYyNjIwNjgxMTgwODNEMjE2MDAzOTU1NDQiIHhtcE1NOkRvY3VtZW50SUQ9ImFkb2JlOmRvY2lkOnBob3Rvc2hvcDozNDVjOWViOC04NDc4LTFkNDctOGRjMi0yZDkyOGNhYTYxZWQiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6YjAzN2ZiMGItNTU5Mi0xYjRkLWJjZGQtOWU4NGExMDJiMGM2IiB4bXA6Q3JlYXRvclRvb2w9IkFkb2JlIFBob3Rvc2hvcCBDQyAoV2luZG93cykiIHhtcDpDcmVhdGVEYXRlPSIyMDE4LTA1LTA5VDE0OjQ5OjM3KzA4OjAwIiB4bXA6TW9kaWZ5RGF0ZT0iMjAxOC0wNS0wOVQxNDo1MToyNSswODowMCIgeG1wOk1ldGFkYXRhRGF0ZT0iMjAxOC0wNS0wOVQxNDo1MToyNSswODowMCIgZGM6Zm9ybWF0PSJpbWFnZS9wbmciIHBob3Rvc2hvcDpDb2xvck1vZGU9IjMiIHBob3Rvc2hvcDpJQ0NQcm9maWxlPSJzUkdCIElFQzYxOTY2LTIuMSI+IDx4bXBNTTpEZXJpdmVkRnJvbSBzdFJlZjppbnN0YW5jZUlEPSJ4bXAuaWlkOjEyMjVlZWE3LTEyY2QtMTY0NC04ZDAzLWFjOTE2ZTAxZDQ1YyIgc3RSZWY6ZG9jdW1lbnRJRD0idXVpZDoxRDIwNUFGNjZCRDlFNTExOUM5REMwMzg2RjlEQjFGNyIvPiA8eG1wTU06SGlzdG9yeT4gPHJkZjpTZXE+IDxyZGY6bGkgc3RFdnQ6YWN0aW9uPSJzYXZlZCIgc3RFdnQ6aW5zdGFuY2VJRD0ieG1wLmlpZDphYmMzNjIzMy1hOWNkLWNiNDQtODViYi0zZTgyMjEwYmIxMjYiIHN0RXZ0OndoZW49IjIwMTgtMDUtMDlUMTQ6NTE6MjUrMDg6MDAiIHN0RXZ0OnNvZnR3YXJlQWdlbnQ9IkFkb2JlIFBob3Rvc2hvcCBDQyAyMDE4IChXaW5kb3dzKSIgc3RFdnQ6Y2hhbmdlZD0iLyIvPiA8cmRmOmxpIHN0RXZ0OmFjdGlvbj0ic2F2ZWQiIHN0RXZ0Omluc3RhbmNlSUQ9InhtcC5paWQ6YjAzN2ZiMGItNTU5Mi0xYjRkLWJjZGQtOWU4NGExMDJiMGM2IiBzdEV2dDp3aGVuPSIyMDE4LTA1LTA5VDE0OjUxOjI1KzA4OjAwIiBzdEV2dDpzb2Z0d2FyZUFnZW50PSJBZG9iZSBQaG90b3Nob3AgQ0MgMjAxOCAoV2luZG93cykiIHN0RXZ0OmNoYW5nZWQ9Ii8iLz4gPC9yZGY6U2Vx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';
SET @particle_effect_sakura_plugin_code = CONCAT('(function(){"use strict";if(typeof window==="undefined"||typeof document==="undefined"){return}var K="sakura",ID="poetize-particle-effect-sakura",DEF="', @particle_effect_sakura_default_image, '";if(document.getElementById(ID)){return}function g(k,d){return window.PoetizePlugin&&window.PoetizePlugin.api&&typeof window.PoetizePlugin.api.getConfig==="function"?window.PoetizePlugin.api.getConfig(K,k,d):d}function n(v,d,min,max){v=Number(v);if(!isFinite(v)){v=d}if(min!==null&&min!==undefined&&v<min){v=min}if(max!==null&&max!==undefined&&v>max){v=max}return v}function r(a,b){return a+Math.random()*(b-a)}var q=Math.round(n(g("quantity",50),50,1,200)),sp=n(g("speed",1),1,0.1,5),dr=n(g("drift",1),1,0,5),minS=n(g("minScale",0.4),0.4,0.05,5),maxS=n(g("maxScale",1),1,minS,5),mode=String(g("spawnMode","mixed")||"mixed").toLowerCase(),z=Math.round(n(g("zIndex",9999999),9999999,0,2147483647)),src=String(g("imageUrl","")||"").trim();if(mode!=="top"&&mode!=="right"&&mode!=="mixed"){mode="mixed"}var canvas=document.createElement("canvas"),ctx=canvas.getContext("2d",{alpha:true,desynchronized:true}),img=new Image(),items=[],raf=0,w=1,h=1;if(!ctx){return}canvas.id=ID;canvas.setAttribute("style","position:fixed;left:0;top:0;width:100vw;height:100vh;pointer-events:none;z-index:"+z+";");(document.body||document.documentElement).appendChild(canvas);function resize(){w=window.innerWidth||document.documentElement.clientWidth||1;h=window.innerHeight||document.documentElement.clientHeight||1;canvas.width=w;canvas.height=h}function reset(p){var side=mode==="right"||(mode==="mixed"&&Math.random()>0.5);p.s=r(minS,maxS);p.r=r(0,Math.PI*2);p.vr=r(-0.02,0.02);p.a=r(0.55,1);p.vx=r(-0.6,0.6)*dr*sp;p.vy=r(1.2,2.4)*sp;if(side){p.x=w+r(20,140);p.y=r(-40,h+40)}else{p.x=r(0,w);p.y=-r(20,140)}}function draw(p){var iw=25*p.s,ih=30*p.s;ctx.save();ctx.globalAlpha=p.a;ctx.translate(p.x,p.y);ctx.rotate(p.r);ctx.drawImage(img,-iw/2,-ih/2,iw,ih);ctx.restore()}function tick(){ctx.clearRect(0,0,w,h);for(var i=0;i<items.length;i++){var p=items[i];p.x+=p.vx;p.y+=p.vy;p.r+=p.vr;if(p.y>h+160||p.x<-160||p.x>w+160){reset(p)}draw(p)}raf=(window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.msRequestAnimationFrame||function(fn){return setTimeout(fn,16)})(tick)}function start(){if(raf){return}resize();items=[];for(var i=0;i<q;i++){var p={};reset(p);items.push(p)}tick()}window.addEventListener("resize",resize,{passive:true});img.onload=start;img.onerror=function(){if(img.src!==DEF){img.src=DEF}};img.src=src||DEF})();');

-- 支付平台插件
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('particle_effect', 'sakura', '樱花飘落', '全屏樱花飘落粒子特效，支持图片、速度、数量和生成位置配置', @particle_effect_sakura_plugin_config, @particle_effect_sakura_plugin_code, 1, 1, 0);

INSERT IGNORE INTO `sys_plugin_active` (`plugin_type`, `plugin_key`) VALUES
('particle_effect', 'sakura');
INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`, `manifest`) VALUES
('payment', 'afdian', '爱发电', '通过爱发电平台实现付费阅读和会员订阅',
'{"userId":"","apiToken":"","planId":"","defaultPayType":1,"fixedAmount":5.00,"freePercent":30,"enableMembership":false,"memberPlanId":"","memberDurationDays":30}',
NULL, 0, 1, 0,
'{"name":"afdian","displayName":"爱发电","version":"1.0.0","author":"LeapYa","pluginType":"payment","description":"通过爱发电平台实现付费阅读和会员订阅","configSchema":{"userId":{"type":"string","label":"用户ID","description":"爱发电用户ID（在个人设置中查看）","defaultValue":""},"apiToken":{"type":"string","label":"API Token","description":"爱发电 API Token（在开发者设置中获取）","defaultValue":""},"planId":{"type":"string","label":"方案ID","description":"用于单篇文章付费的发电方案ID","defaultValue":""},"defaultPayType":{"type":"number","label":"默认付费类型","description":"1=按文章付费, 2=会员专属, 3=赞赏解锁, 4=固定金额","defaultValue":1},"fixedAmount":{"type":"number","label":"固定金额（元）","description":"单篇文章解锁金额","defaultValue":5.00},"freePercent":{"type":"number","label":"免费预览比例(%)","description":"付费文章可免费预览的内容比例","defaultValue":30},"enableMembership":{"type":"boolean","label":"启用会员功能","description":"是否启用全站会员功能","defaultValue":false},"memberPlanId":{"type":"string","label":"会员方案ID","description":"用于全站会员的发电方案ID","defaultValue":""},"memberDurationDays":{"type":"number","label":"会员有效期（天）","description":"购买会员后的有效天数","defaultValue":30}}}');

INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`, `manifest`) VALUES
('payment', 'epay', '易支付(V2)', '第三方聚合支付V2接口，SHA256WithRSA签名，支持支付宝/微信/QQ钱包',
'{"apiUrl":"","pid":"","privateKey":"","epayPublicKey":"","notifyUrl":"","returnUrl":"","defaultPayType":1,"fixedAmount":5.00,"freePercent":30,"memberDurationDays":30}',
NULL, 0, 1, 2,
'{"name":"epay","displayName":"易支付(V2)","version":"1.0.0","author":"LeapYa","pluginType":"payment","description":"第三方聚合支付V2接口，SHA256WithRSA签名，支持支付宝/微信/QQ钱包","configSchema":{"apiUrl":{"type":"string","label":"接口地址","description":"易支付API地址，如 https://pay.example.com","defaultValue":""},"pid":{"type":"string","label":"商户ID","description":"易支付商户号","defaultValue":""},"privateKey":{"type":"string","label":"商户私钥","description":"RSA私钥（PKCS#8格式，不含头尾）","defaultValue":""},"epayPublicKey":{"type":"string","label":"平台公钥","description":"易支付平台RSA公钥（用于验签）","defaultValue":""},"notifyUrl":{"type":"string","label":"异步通知URL","description":"支付成功后的回调地址","defaultValue":""},"returnUrl":{"type":"string","label":"同步跳转URL","description":"支付完成后跳转的页面地址","defaultValue":""},"defaultPayType":{"type":"number","label":"默认付费类型","description":"1=按文章付费, 2=会员专属, 3=赞赏解锁, 4=固定金额","defaultValue":1},"fixedAmount":{"type":"number","label":"固定金额（元）","description":"单篇文章解锁金额","defaultValue":5.00},"freePercent":{"type":"number","label":"免费预览比例(%)","description":"付费文章可免费预览的内容比例","defaultValue":30},"memberDurationDays":{"type":"number","label":"会员有效期（天）","description":"购买会员后的有效天数","defaultValue":30}}}');

INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`, `manifest`) VALUES
('payment', 'mianbaoduo', '面包多Pay', '面包多内容变现平台支付',
'{"appId":"","appSecret":"","callbackUrl":"","defaultPayType":1,"fixedAmount":5.00,"freePercent":30,"memberDurationDays":30}',
NULL, 0, 1, 3,
'{"name":"mianbaoduo","displayName":"面包多Pay","version":"1.0.0","author":"LeapYa","pluginType":"payment","description":"面包多内容变现平台支付","configSchema":{"appId":{"type":"string","label":"应用ID","description":"面包多应用ID","defaultValue":""},"appSecret":{"type":"string","label":"应用密钥","description":"面包多应用密钥","defaultValue":""},"callbackUrl":{"type":"string","label":"回调地址","description":"支付成功后的回调URL","defaultValue":""},"defaultPayType":{"type":"number","label":"默认付费类型","description":"1=按文章付费, 2=会员专属, 3=赞赏解锁, 4=固定金额","defaultValue":1},"fixedAmount":{"type":"number","label":"固定金额（元）","description":"单篇文章解锁金额","defaultValue":5.00},"freePercent":{"type":"number","label":"免费预览比例(%)","description":"付费文章可免费预览的内容比例","defaultValue":30},"memberDurationDays":{"type":"number","label":"会员有效期（天）","description":"购买会员后的有效天数","defaultValue":30}}}');

INSERT IGNORE INTO `sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`, `manifest`) VALUES
('payment', 'buymeacoffee', 'Buy Me a Coffee', '海外创作者赞助平台',
'{"accessToken":"","webhookSecret":"","defaultPayType":3,"fixedAmount":5.00,"freePercent":30,"memberDurationDays":30}',
NULL, 0, 1, 4,
'{"name":"buymeacoffee","displayName":"Buy Me a Coffee","version":"1.0.0","author":"LeapYa","pluginType":"payment","description":"海外创作者赞助平台","configSchema":{"accessToken":{"type":"string","label":"Access Token","description":"Buy Me a Coffee API访问令牌","defaultValue":""},"webhookSecret":{"type":"string","label":"Webhook密钥","description":"用于验证回调请求的密钥","defaultValue":""},"defaultPayType":{"type":"number","label":"默认付费类型","description":"1=按文章付费, 2=会员专属, 3=赞赏解锁, 4=固定金额","defaultValue":3},"fixedAmount":{"type":"number","label":"固定金额（美元）","description":"单篇文章解锁金额","defaultValue":5.00},"freePercent":{"type":"number","label":"免费预览比例(%)","description":"付费文章可免费预览的内容比例","defaultValue":30},"memberDurationDays":{"type":"number","label":"会员有效期（天）","description":"购买会员后的有效天数","defaultValue":30}}}');

-- 初始化插件数据
INSERT INTO `poetize`.`sys_plugin` (`plugin_type`, `plugin_key`, `plugin_name`, `plugin_description`, `plugin_config`, `plugin_code`, `enabled`, `is_system`, `sort_order`) VALUES
('mouse_click_effect', 'none', '无效果', '关闭鼠标点击效果', '{}', NULL, 1, 1, 0),
('mouse_click_effect', 'text', '社会主义核心价值观', '点击时显示社会主义核心价值观文字：富强、民主、文明、和谐等', 
'{"texts": ["富强", "民主", "文明", "和谐", "自由", "平等", "公正", "法治", "爱国", "敬业", "诚信", "友善"], "color": "#ff6651", "fontSize": 16, "duration": 1500, "moveDistance": 160}',
'const list = config.texts || [
  "富强", "民主", "文明", "和谐",
  "自由", "平等", "公正", "法治",
  "爱国", "敬业", "诚信", "友善"
];

if (typeof window._textEffectIdx === "undefined") {
  window._textEffectIdx = 0;
}

const span = document.createElement("span");
span.textContent = list[window._textEffectIdx];
window._textEffectIdx = (window._textEffectIdx + 1) % list.length;

Object.assign(span.style, {
  "z-index": "1000",
  top: y - 20 + "px",
  left: x + "px",
  position: "absolute",
  "pointer-events": "none",
  "font-weight": "bold",
  color: config.color || "#ff6651",
  transition: "all 1.5s ease-out"
});

if (document.body && span && span.nodeType === Node.ELEMENT_NODE) {
  document.body.appendChild(span);
} else {
  return;
}

setTimeout(() => {
  span.style.top = y - 180 + "px";
  span.style.opacity = "0";
}, 10);

setTimeout(() => {
  if (span.parentNode) {
    span.parentNode.removeChild(span);
  }
}, 1500);', 1, 1, 1),
('mouse_click_effect', 'firework', '烟花粒子', '点击时产生彩色烟花粒子扩散效果',
'{"colors": ["#FF1461", "#18FF92", "#5A87FF", "#FBF38C"], "particleCount": 30, "minRadius": 16, "maxRadius": 32, "minDistance": 50, "maxDistance": 180}',
'if (!anime) { console.warn("anime.js未加载"); return; }

const colors = config.colors || ["#FF1461", "#18FF92", "#5A87FF", "#FBF38C"];
const numberOfParticules = config.particleCount || 30;

// 将页面坐标转换为视口坐标（因为canvas使用position:fixed）
const viewportX = x - window.scrollX;
const viewportY = y - window.scrollY;

// 获取或创建canvas
let canvas = document.getElementById("mousedown-effect");
if (!canvas) {
  canvas = document.createElement("canvas");
  canvas.id = "mousedown-effect";
  Object.assign(canvas.style, {
    position: "fixed",
    left: "0",
    top: "0",
    pointerEvents: "none",
    zIndex: "1000"
  });
  document.body.appendChild(canvas);
}

// 设置canvas尺寸
canvas.width = 2 * window.innerWidth;
canvas.height = 2 * window.innerHeight;
canvas.style.width = window.innerWidth + "px";
canvas.style.height = window.innerHeight + "px";

const ctx = canvas.getContext("2d", {willReadFrequently: true});
ctx.scale(2, 2);

// 粒子方向
function setParticuleDirection(p) {
  const t = anime.random(0, 360) * Math.PI / 180;
  const a = anime.random(50, 180);
  const n = [-1, 1][anime.random(0, 1)] * a;
  return {
    x: p.x + n * Math.cos(t),
    y: p.y + n * Math.sin(t)
  };
}

// 创建粒子
function createParticule(px, py) {
  const p = {
    x: px,
    y: py,
    color: colors[anime.random(0, colors.length - 1)],
    radius: anime.random(16, 32)
  };
  p.endPos = setParticuleDirection(p);
  p.draw = function() {
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.radius, 0, 2 * Math.PI, true);
    ctx.fillStyle = p.color;
    ctx.fill();
  };
  return p;
}

// 创建圆环
function createCircle(px, py) {
  const c = {
    x: px,
    y: py,
    color: "#F00",
    radius: 0.1,
    alpha: 0.5,
    lineWidth: 6
  };
  c.draw = function() {
    ctx.globalAlpha = c.alpha;
    ctx.beginPath();
    ctx.arc(c.x, c.y, c.radius, 0, 2 * Math.PI, true);
    ctx.lineWidth = c.lineWidth;
    ctx.strokeStyle = c.color;
    ctx.stroke();
    ctx.globalAlpha = 1;
  };
  return c;
}

// 创建粒子和圆环（使用视口坐标）
const circle = createCircle(viewportX, viewportY);
const particules = [];
for (let i = 0; i < numberOfParticules; i++) {
  particules.push(createParticule(viewportX, viewportY));
}

// 所有动画目标
const allTargets = [...particules, circle];

// 渲染函数 - 绘制所有元素
function renderAll() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  for (let i = 0; i < allTargets.length; i++) {
    allTargets[i].draw();
  }
}

// 启动动画
anime.timeline().add({
  targets: particules,
  x: function(p) { return p.endPos.x; },
  y: function(p) { return p.endPos.y; },
  radius: 0.1,
  duration: anime.random(1200, 1800),
  easing: "easeOutExpo",
  update: renderAll
}).add({
  targets: circle,
  radius: anime.random(80, 160),
  lineWidth: 0,
  alpha: {
    value: 0,
    easing: "linear",
    duration: anime.random(600, 800)
  },
  duration: anime.random(1200, 1800),
  easing: "easeOutExpo",
  offset: 0
});', 1, 1, 2);

-- 初始化插件激活状态
INSERT INTO `poetize`.`sys_plugin_active` (`plugin_type`, `plugin_key`) VALUES
('mouse_click_effect', 'none');

-- 初始化验证码配置数据
INSERT INTO `poetize`.`sys_captcha_config` (`id`, `enable`, `login`, `register`, `comment`, `reset_password`, `screen_size_threshold`, `force_slide_for_mobile`, `slide_accuracy`, `slide_success_threshold`, `checkbox_track_sensitivity`, `checkbox_min_track_points`, `checkbox_reply_sensitivity`, `checkbox_max_retry_count`, `checkbox_retry_decrement`) VALUES (1, 1, 1, 1, 1, 1, 768, 1, 5, 0.95, 0.99, 3, 0.85, 5, 0.02);

-- 初始化第三方OAuth登录配置数据
INSERT INTO `poetize`.`third_party_oauth_config` (`platform_type`, `platform_name`, `scope`, `enabled`, `global_enabled`, `sort_order`, `remark`, `deleted`) VALUES
('github', 'GitHub', 'user:email', 0, 0, 1, 'GitHub OAuth登录配置，需要在GitHub开发者设置中创建OAuth应用', 0),
('google', 'Google', 'openid email profile', 0, 0, 2, 'Google OAuth登录配置，需要在Google Cloud Console中创建OAuth客户端', 0),
('twitter', 'Twitter', 'tweet.read users.read', 0, 0, 3, 'Twitter OAuth登录配置，需要在Twitter Developer Portal中创建应用', 0),
('yandex', 'Yandex', 'login:email login:info', 0, 0, 4, 'Yandex OAuth登录配置，需要在Yandex OAuth中创建应用', 0),
('gitee', 'Gitee', 'user_info emails', 0, 0, 5, 'Gitee OAuth登录配置，需要在Gitee第三方应用中创建应用', 0),
('qq', 'QQ', 'get_user_info', 0, 0, 6, 'QQ OAuth登录配置，需要在QQ互联平台中创建应用', 0),
('baidu', 'Baidu', 'basic', 0, 0, 7, 'Baidu OAuth登录配置，需要在Baidu开发者平台中创建应用', 0),
('afdian', '爱发电', 'basic', 0, 0, 8, '爱发电 OAuth 登录配置，需要在 afdian.com 开发者设置中获取 client_id 和 client_secret', 0);

INSERT INTO `poetize`.`resource_path` (`title`, `cover`, `introduction`, `type`, `status`) VALUES ('POETIZE', 'https://s1.ax1x.com/2022/11/10/z9VlHs.png', '这是一个 Vue2 Vue3 与 SpringBoot 结合的产物～', 'siteInfo', 1);

-- ========== 导入静态资源到resource表 ==========
-- 将public/assets目录下的静态文件录入到数据库，使其在后台资源管理中可见

-- 图片资源
INSERT INTO `poetize`.`resource` (`user_id`, `type`, `path`, `size`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`) VALUES 
(1, 'assets', '/static/assets/admireImage.jpg', 8192, 'admireImage.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/backgroundPicture.jpg', 915456, 'backgroundPicture.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/cloud.png', 67227, 'cloud.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/love.jpg', 222208, 'love.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/loveMessage.jpg', 112640, 'loveMessage.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/lovePhoto.jpg', 99328, 'lovePhoto.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/loveWeiYan.jpg', 109568, 'loveWeiYan.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/friendLetterMiddle.jpg', 116736, 'friendLetterMiddle.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/toolbar.jpg', 292864, 'toolbar.jpg', 'image/jpeg', 1, 'local', NOW()),
(1, 'assets', '/static/assets/bannerWave1.png', 5120, 'bannerWave1.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/bannerWave2.png', 4915, 'bannerWave2.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/commentURL.png', 68234, 'commentURL.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/friendLetterBiLi.png', 13312, 'friendLetterBiLi.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/friendLetterBottom.png', 158720, 'friendLetterBottom.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/friendLetterTop.png', 63488, 'friendLetterTop.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/springBg.png', 122880, 'springBg.png', 'image/png', 1, 'local', NOW()),
(1, 'assets', '/static/assets/toTop.png', 8192, 'toTop.png', 'image/png', 1, 'local', NOW());

-- SVG文件
INSERT INTO `poetize`.`resource` (`user_id`, `type`, `path`, `size`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`) VALUES 
(1, 'assets', '/static/assets/loveLike.svg', 8601, 'loveLike.svg', 'image/svg+xml', 1, 'local', NOW());

-- 站点根目录图片资源
INSERT INTO `poetize`.`resource` (`user_id`, `type`, `path`, `size`, `resource_hash`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`) VALUES
(1, 'assets', '/poetize.jpg', 30312, '7460056A7CE3125039B7F35604CA5BCE867E3022B79F698A21308827E3791213', 'poetize.jpg', 'image/jpeg', 1, 'local', NOW());

-- 视频文件
INSERT INTO `poetize`.`resource` (`user_id`, `type`, `path`, `size`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`) VALUES 
(1, 'assets', '/static/assets/backgroundVideo.mp4', 1955207, 'backgroundVideo.mp4', 'video/mp4', 1, 'local', NOW());

-- 字体文件
INSERT INTO `poetize`.`resource` (`user_id`, `type`, `path`, `size`, `original_name`, `mime_type`, `status`, `store_type`, `create_time`) VALUES 
(1, 'assets', '/static/assets/font_chunks/font.base.woff2', 8956, 'font.base.woff2', 'font/woff2', 1, 'local', NOW()),
(1, 'assets', '/static/assets/font_chunks/font.level1.woff2', 830860, 'font.level1.woff2', 'font/woff2', 1, 'local', NOW()),
(1, 'assets', '/static/assets/font_chunks/font.level2.woff2', 756328, 'font.level2.woff2', 'font/woff2', 1, 'local', NOW()),
(1, 'assets', '/static/assets/font_chunks/font.other.woff2', 198920, 'font.other.woff2', 'font/woff2', 1, 'local', NOW());

-- 优化 `article` 表
-- 为用户ID添加索引，加速查询某个用户的所有文章
ALTER TABLE `poetize`.`article` ADD INDEX `idx_user_id` (`user_id`);
-- 为分类和标签创建复合索引，加速按分类和标签筛选文章
ALTER TABLE `poetize`.`article` ADD INDEX `idx_sort_label` (`sort_id`, `label_id`);
-- 为推荐状态添加索引，加速查询推荐文章
ALTER TABLE `poetize`.`article` ADD INDEX `idx_recommend_status` (`recommend_status`);
-- 为可见状态添加索引，确保查询时能快速过滤不可见文章
ALTER TABLE `poetize`.`article` ADD INDEX `idx_view_status` (`view_status`);

-- 优化 `comment` 表
-- 为用户ID添加索引，加速查询某个用户的所有评论
ALTER TABLE `poetize`.`comment` ADD INDEX `idx_user_id` (`user_id`);
-- 为父评论ID添加索引，加速构建评论楼层
ALTER TABLE `poetize`.`comment` ADD INDEX `idx_parent_comment_id` (`parent_comment_id`);
-- 优化已有的 `source` 索引，改为复合索引，提高查询特定来源评论的效率
ALTER TABLE `poetize`.`comment` DROP INDEX `source`;
ALTER TABLE `poetize`.`comment` ADD INDEX `idx_source_type` (`source`, `type`);

-- 优化 `label` 表
-- 为分类ID添加索引，加速查询某个分类下的所有标签
ALTER TABLE `poetize`.`label` ADD INDEX `idx_sort_id` (`sort_id`);

-- 优化 `im_chat_user_friend` 表
-- 添加唯一复合索引，防止重复的好友关系，并加速双向查询
ALTER TABLE `poetize`.`im_chat_user_friend` ADD UNIQUE INDEX `uk_user_friend` (`user_id`, `friend_id`);
-- 为好友ID单独添加索引，加速反向查询（例如查询"谁加了我为好友"）
ALTER TABLE `poetize`.`im_chat_user_friend` ADD INDEX `idx_friend_id` (`friend_id`);

-- 优化 `im_chat_group_user` 表
-- 添加唯一复合索引，防止用户重复加入同一个群
ALTER TABLE `poetize`.`im_chat_group_user` ADD UNIQUE INDEX `uk_group_user` (`group_id`, `user_id`);
-- 为用户ID单独添加索引，加速查询某个用户加入了哪些群
ALTER TABLE `poetize`.`im_chat_group_user` ADD INDEX `idx_user_id` (`user_id`);

-- 优化 `wei_yan` 表
-- 为 `source` 和 `type` 添加复合索引，加速查询特定来源的微言
ALTER TABLE `poetize`.`wei_yan` ADD INDEX `idx_source_type` (`source`, `type`);
-- 为 `is_public` 和 `create_time` 添加复合索引，加速查询公开的、并按时间排序的微言
ALTER TABLE `poetize`.`wei_yan` ADD INDEX `idx_public_create` (`is_public`, `create_time`);

-- 优化 `resource` 表
-- 为 `user_id` 和 `type` 添加复合索引，加速查询某个用户的特定类型资源
ALTER TABLE `poetize`.`resource` ADD INDEX `idx_user_type` (`user_id`, `type`);
-- 为本地资源内容哈希添加复合索引，加速上传前复用检测
ALTER TABLE `poetize`.`resource` ADD INDEX `idx_store_hash` (`store_type`, `resource_hash`);

-- RAG 知识文档元数据表（MariaDB 向量检索）
CREATE TABLE IF NOT EXISTS `poetize`.`ai_knowledge_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `index_name` VARCHAR(128) NOT NULL DEFAULT 'poetize_ai_chat',
  `document_id` VARCHAR(191) NOT NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `source_id` VARCHAR(128) NOT NULL,
  `title` VARCHAR(255) DEFAULT NULL,
  `content` LONGTEXT NOT NULL,
  `visibility_scope` VARCHAR(64) NOT NULL,
  `metadata_json` LONGTEXT DEFAULT NULL,
  `chunk_index` INT NOT NULL DEFAULT 0,
  `chunk_count` INT NOT NULL DEFAULT 1,
  `content_hash` VARCHAR(64) DEFAULT NULL,
  `embedding_text` LONGTEXT NOT NULL,
  `sync_status` VARCHAR(32) NOT NULL DEFAULT 'SYNCED',
  `last_error` TEXT DEFAULT NULL,
  `last_sync_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_chunk` (`index_name`, `document_id`, `chunk_index`),
  KEY `idx_ai_knowledge_source` (`index_name`, `source_type`, `source_id`),
  KEY `idx_ai_knowledge_scope` (`index_name`, `visibility_scope`),
  KEY `idx_ai_knowledge_sync` (`index_name`, `last_sync_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `article_draft` (
    `id` VARCHAR(64) NOT NULL COMMENT '草稿ID',
    `owner_user_id` INT NOT NULL COMMENT '草稿归属用户ID',
    `draft_type` VARCHAR(32) NOT NULL DEFAULT 'CREATE' COMMENT '草稿类型 [CREATE:新建草稿, REVISION:修订草稿]',
    `article_id` INT DEFAULT NULL COMMENT '关联原文章ID，仅修订草稿使用',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '草稿状态',
    `title_cache` VARCHAR(500) DEFAULT NULL COMMENT '标题缓存',
    `crdt_snapshot_base64` MEDIUMTEXT DEFAULT NULL COMMENT 'CRDT 快照 Base64',
    `last_editor_id` INT DEFAULT NULL COMMENT '最后编辑人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by` VARCHAR(32) DEFAULT NULL COMMENT '修改人',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_draft_revision` (`draft_type`, `article_id`),
    KEY `idx_owner_status` (`owner_user_id`, `status`),
    KEY `idx_article_draft_article_id` (`article_id`),
    KEY `idx_last_editor` (`last_editor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章草稿表';

CREATE TABLE IF NOT EXISTS `article_draft_collaborator` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `draft_id` VARCHAR(64) NOT NULL COMMENT '草稿ID',
    `user_id` INT NOT NULL COMMENT '协作者用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_draft_user` (`draft_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章草稿协作者表';
