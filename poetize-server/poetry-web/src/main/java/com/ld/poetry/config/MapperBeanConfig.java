package com.ld.poetry.config;

import com.ld.poetry.dao.AiKnowledgeDocumentMapper;
import com.ld.poetry.dao.ArticleDraftCollaboratorMapper;
import com.ld.poetry.dao.ArticleDraftMapper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.ArticlePaymentMapper;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.dao.CommentMapper;
import com.ld.poetry.dao.FamilyMapper;
import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.dao.ResourcePathMapper;
import com.ld.poetry.dao.SeoConfigMapper;
import com.ld.poetry.dao.SeoNotificationConfigMapper;
import com.ld.poetry.dao.SeoPwaConfigMapper;
import com.ld.poetry.dao.SeoSearchEnginePushMapper;
import com.ld.poetry.dao.SeoSiteVerificationMapper;
import com.ld.poetry.dao.SeoSocialMediaMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.dao.SysAiConfigMapper;
import com.ld.poetry.dao.SysCaptchaConfigMapper;
import com.ld.poetry.dao.SysConfigMapper;
import com.ld.poetry.dao.SysMailConfigMapper;
import com.ld.poetry.dao.SysPluginActiveMapper;
import com.ld.poetry.dao.SysPluginMapper;
import com.ld.poetry.dao.ThirdPartyOauthConfigMapper;
import com.ld.poetry.dao.TreeHoleMapper;
import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.dao.WeiYanMapper;
import com.ld.poetry.im.http.dao.ImChatGroupMapper;
import com.ld.poetry.im.http.dao.ImChatGroupUserMapper;
import com.ld.poetry.im.http.dao.ImChatLastReadMapper;
import com.ld.poetry.im.http.dao.ImChatUserFriendMapper;
import com.ld.poetry.im.http.dao.ImChatUserGroupMessageMapper;
import com.ld.poetry.im.http.dao.ImChatUserMessageMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperBeanConfig {

    static final Class<?>[] MAPPER_TYPES = {
            ArticleDraftMapper.class,
            ArticleDraftCollaboratorMapper.class,
            AiKnowledgeDocumentMapper.class,
            WebInfoMapper.class,
            ArticleMapper.class,
            ArticleTranslationMapper.class,
            ArticlePaymentMapper.class,
            HistoryInfoMapper.class,
            WeiYanMapper.class,
            ThirdPartyOauthConfigMapper.class,
            TreeHoleMapper.class,
            UserMapper.class,
            SysPluginActiveMapper.class,
            SysPluginMapper.class,
            SysConfigMapper.class,
            SysMailConfigMapper.class,
            SysCaptchaConfigMapper.class,
            SeoSocialMediaMapper.class,
            SortMapper.class,
            SysAiConfigMapper.class,
            SeoPwaConfigMapper.class,
            SeoSearchEnginePushMapper.class,
            SeoSiteVerificationMapper.class,
            ResourcePathMapper.class,
            SeoConfigMapper.class,
            SeoNotificationConfigMapper.class,
            LabelMapper.class,
            ResourceMapper.class,
            CommentMapper.class,
            FamilyMapper.class,
            ImChatUserMessageMapper.class,
            ImChatUserFriendMapper.class,
            ImChatUserGroupMessageMapper.class,
            ImChatLastReadMapper.class,
            ImChatGroupMapper.class,
            ImChatGroupUserMapper.class
    };

    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("com.ld.poetry.__disabled_mapper_scan__");
        return configurer;
    }

    @Bean
    public ArticleDraftMapper articleDraftMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ArticleDraftMapper.class); }
    @Bean
    public ArticleDraftCollaboratorMapper articleDraftCollaboratorMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ArticleDraftCollaboratorMapper.class); }
    @Bean
    public AiKnowledgeDocumentMapper aiKnowledgeDocumentMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, AiKnowledgeDocumentMapper.class); }
    @Bean
    public WebInfoMapper webInfoMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, WebInfoMapper.class); }
    @Bean
    public ArticleMapper articleMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ArticleMapper.class); }
    @Bean
    public ArticleTranslationMapper articleTranslationMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ArticleTranslationMapper.class); }
    @Bean
    public ArticlePaymentMapper articlePaymentMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ArticlePaymentMapper.class); }
    @Bean
    public HistoryInfoMapper historyInfoMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, HistoryInfoMapper.class); }
    @Bean
    public WeiYanMapper weiYanMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, WeiYanMapper.class); }
    @Bean
    public ThirdPartyOauthConfigMapper thirdPartyOauthConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ThirdPartyOauthConfigMapper.class); }
    @Bean
    public TreeHoleMapper treeHoleMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, TreeHoleMapper.class); }
    @Bean
    public UserMapper userMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, UserMapper.class); }
    @Bean
    public SysPluginActiveMapper sysPluginActiveMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SysPluginActiveMapper.class); }
    @Bean
    public SysPluginMapper sysPluginMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SysPluginMapper.class); }
    @Bean
    public SysConfigMapper sysConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SysConfigMapper.class); }
    @Bean
    public SysMailConfigMapper sysMailConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SysMailConfigMapper.class); }
    @Bean
    public SysCaptchaConfigMapper sysCaptchaConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SysCaptchaConfigMapper.class); }
    @Bean
    public SeoSocialMediaMapper seoSocialMediaMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SeoSocialMediaMapper.class); }
    @Bean
    public SortMapper sortMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SortMapper.class); }
    @Bean
    public SysAiConfigMapper sysAiConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SysAiConfigMapper.class); }
    @Bean
    public SeoPwaConfigMapper seoPwaConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SeoPwaConfigMapper.class); }
    @Bean
    public SeoSearchEnginePushMapper seoSearchEnginePushMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SeoSearchEnginePushMapper.class); }
    @Bean
    public SeoSiteVerificationMapper seoSiteVerificationMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SeoSiteVerificationMapper.class); }
    @Bean
    public ResourcePathMapper resourcePathMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ResourcePathMapper.class); }
    @Bean
    public SeoConfigMapper seoConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SeoConfigMapper.class); }
    @Bean
    public SeoNotificationConfigMapper seoNotificationConfigMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, SeoNotificationConfigMapper.class); }
    @Bean
    public LabelMapper labelMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, LabelMapper.class); }
    @Bean
    public ResourceMapper resourceMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ResourceMapper.class); }
    @Bean
    public CommentMapper commentMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, CommentMapper.class); }
    @Bean
    public FamilyMapper familyMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, FamilyMapper.class); }
    @Bean
    public ImChatUserMessageMapper imChatUserMessageMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ImChatUserMessageMapper.class); }
    @Bean
    public ImChatUserFriendMapper imChatUserFriendMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ImChatUserFriendMapper.class); }
    @Bean
    public ImChatUserGroupMessageMapper imChatUserGroupMessageMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ImChatUserGroupMessageMapper.class); }
    @Bean
    public ImChatLastReadMapper imChatLastReadMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ImChatLastReadMapper.class); }
    @Bean
    public ImChatGroupMapper imChatGroupMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ImChatGroupMapper.class); }
    @Bean
    public ImChatGroupUserMapper imChatGroupUserMapper(SqlSessionTemplate sqlSessionTemplate) { return mapper(sqlSessionTemplate, ImChatGroupUserMapper.class); }

    private <T> T mapper(SqlSessionTemplate sqlSessionTemplate, Class<T> mapperType) {
        if (!sqlSessionTemplate.getConfiguration().hasMapper(mapperType)) {
            sqlSessionTemplate.getConfiguration().addMapper(mapperType);
        }
        return sqlSessionTemplate.getMapper(mapperType);
    }
}
