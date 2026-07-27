package com.ld.poetry.dao;

import com.ld.poetry.entity.WebInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 网站信息表 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Mapper
public interface WebInfoMapper extends BaseMapper<WebInfo> {

    /**
     * 更新完整网站信息（基本信息保存）
     */
    int updateWebInfoById(@Param("id") Integer id,
                         @Param("webName") String webName,
                         @Param("webTitle") String webTitle,
                         @Param("logoImage") String logoImage,
                         @Param("siteAddress") String siteAddress,
                         @Param("footer") String footer,
                         @Param("backgroundImage") String backgroundImage,
                         @Param("avatar") String avatar,
                         @Param("waifuJson") String waifuJson,
                         @Param("status") Boolean status,
                         @Param("enableWaifu") Boolean enableWaifu,
                         @Param("waifuDisplayMode") String waifuDisplayMode,
                         @Param("homePagePullUpHeight") Integer homePagePullUpHeight,
                         @Param("apiEnabled") Boolean apiEnabled,
                         @Param("apiKey") String apiKey,
                         @Param("apiIpWhitelist") String apiIpWhitelist,
                         @Param("navConfig") String navConfig,
                         @Param("footerBackgroundImage") String footerBackgroundImage,
                         @Param("footerBackgroundConfig") String footerBackgroundConfig,
                         @Param("email") String email,
                         @Param("minimalFooter") Boolean minimalFooter,
                         @Param("enableAutoNight") Boolean enableAutoNight,
                         @Param("autoNightStart") Integer autoNightStart,
                         @Param("autoNightEnd") Integer autoNightEnd,
                         @Param("enableGrayMode") Boolean enableGrayMode,
                         @Param("enableDynamicTitle") Boolean enableDynamicTitle,
                         @Param("mouseClickEffectConfig") String mouseClickEffectConfig,
                         @Param("mobileDrawerConfig") String mobileDrawerConfig,
                         @Param("homeTitle") String homeTitle,
                         @Param("homeTitleProvided") boolean homeTitleProvided,
                         @Param("loginStyle") String loginStyle,
                         @Param("loginAccentColor") String loginAccentColor,
                         @Param("loginThirdPosition") String loginThirdPosition);

    /**
     * 只更新公告
     */
    int updateNoticesOnly(@Param("id") Integer id, @Param("notices") String notices);

    /**
     * 只更新随机名称
     */
    int updateRandomNameOnly(@Param("id") Integer id, @Param("randomName") String randomName);

    /**
     * 只更新随机头像
     */
    int updateRandomAvatarOnly(@Param("id") Integer id, @Param("randomAvatar") String randomAvatar);

    /**
     * 只更新随机封面
     */
    int updateRandomCoverOnly(@Param("id") Integer id, @Param("randomCover") String randomCover);

}
