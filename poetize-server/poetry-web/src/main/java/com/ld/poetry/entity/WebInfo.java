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

    @TableField(exist = false)
    private String historyAllCount;

    @TableField(exist = false)
    private String historyDayCount;

    @TableField(exist = false)
    private String defaultStoreType;

    /**
     * API相关配置
     */
    @TableField(value = "api_enabled", exist = true)
    private Boolean apiEnabled = false;

    @TableField(value = "api_key", exist = true)
    private String apiKey;

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
}
