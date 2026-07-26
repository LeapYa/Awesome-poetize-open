package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 网站信息表
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("web_info")
public class WebInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 网站名称
     */
    @TableField("web_name")
    private String webName;

    /**
     * 网站信息
     */
    @TableField("web_title")
    private String webTitle;

    /**
     * 网站Logo图片URL（前台导航栏优先显示Logo，为空时显示网站名称）
     */
    @TableField(value = "logo_image", exist = true)
    private String logoImage;

    /**
     * 首页标题（为空时回退到 webTitle，常用于 ICP 备案名展示）
     */
    @TableField(value = "home_title", exist = true)
    private String homeTitle;

    /**
     * 网站地址（完整URL）
     */
    @TableField(value = "site_address", exist = true)
    private String siteAddress;

    /**
     * 公告
     */
    @TableField("notices")
    private String notices;

    /**
     * 页脚
     */
    @TableField("footer")
    private String footer;

    /**
     * 背景
     */
    @TableField("background_image")
    private String backgroundImage;

    /**
     * 头像
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 随机头像
     */
    @TableField("random_avatar")
    private String randomAvatar;

    /**
     * 随机名称
     */
    @TableField("random_name")
    private String randomName;

    /**
     * 随机封面
     */
    @TableField("random_cover")
    private String randomCover;

    /**
     * 看板娘消息
     */
    @TableField("waifu_json")
    private String waifuJson;

    /**
     * 是否启用[0:否，1:是]
     */
    @TableField("status")
    private Boolean status;

    /**
     * 是否启用看板娘[0:否，1:是]
     */
    @TableField("enable_waifu")
    private Boolean enableWaifu;

    /**
     * 看板娘显示模式 [live2d:Live2D看板娘, button:简洁按钮]
     */
    @TableField(value = "waifu_display_mode", exist = true)
    private String waifuDisplayMode;

    /**
     * 首页上拉高度
     */
    @TableField("home_page_pull_up_height")
    private Integer homePagePullUpHeight;

    @TableField(exist = false)
    private String historyAllCount;

    @TableField(exist = false)
    private String historyDayCount;

    @TableField(exist = false)
    private String defaultStoreType;

    /**
     * 文章总数（动态计算，不存储在数据库）
     */
    @TableField(exist = false)
    private Integer articleCount;

    /**
     * API相关配置
     */
    @TableField(value = "api_enabled", exist = true)
    private Boolean apiEnabled = false;

    @TableField(value = "api_key", exist = true)
    private String apiKey;

    @TableField(value = "api_ip_whitelist", exist = true)
    private String apiIpWhitelist;

    /**
     * 导航栏配置JSON
     */
    @TableField(value = "nav_config", exist = true)
    private String navConfig;

    /**
     * 页脚背景图片
     */
    @TableField(value = "footer_background_image", exist = true)
    private String footerBackgroundImage;

    /**
     * 页脚背景图片位置配置(JSON格式)
     */
    @TableField(value = "footer_background_config", exist = true)
    private String footerBackgroundConfig;

    /**
     * 联系邮箱
     */
    @TableField(value = "email", exist = true)
    private String email;

    /**
     * 极简页脚开关 [0:关闭,1:开启]
     */
    @TableField(value = "minimal_footer", exist = true)
    private Boolean minimalFooter;

    /**
     * 自动夜间功能
     */
    @TableField(value = "enable_auto_night", exist = true)
    private Boolean enableAutoNight;

    /**
     * 夜间开始时间 (小时 0-23)
     */
    @TableField(value = "auto_night_start", exist = true)
    private Integer autoNightStart;

    /**
     * 夜间结束时间 (小时 0-23)
     */
    @TableField(value = "auto_night_end", exist = true)
    private Integer autoNightEnd;

    /**
     * 灰色模式开关
     */
    @TableField(value = "enable_gray_mode", exist = true)
    private Boolean enableGrayMode;

    /**
     * 动态标题开关 [0:关闭,1:开启]
     */
    @TableField(value = "enable_dynamic_title", exist = true)
    private Boolean enableDynamicTitle;

    @TableField(value = "mouse_click_effect", exist = true)
    private String mouseClickEffect;

    /**
     * 鼠标点击特效配置JSON
     */
    @TableField(value = "mouse_click_effect_config", exist = true)
    private String mouseClickEffectConfig;

    /**
     * 移动端侧边栏配置(JSON格式)
     */
    @TableField(value = "mobile_drawer_config", exist = true)
    private String mobileDrawerConfig;
}
