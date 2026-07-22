package com.ld.poetry.service;

import com.google.zxing.WriterException;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.SeoConfig;
import com.ld.poetry.utils.ArticleUrlUtil;
import com.ld.poetry.utils.QRCodeUtil;
import com.ld.poetry.utils.RedisUtil;
import com.ld.poetry.utils.mail.MailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 二维码服务
 * 功能说明：
 * 1. 仅支持通过文章ID生成二维码，防止API被盗用
 * 2. 使用统一的网站URL获取方法（数据库配置 > 环境变量 > 默认值）
 * 3. 使用Redis缓存二维码，避免重复生成
 * 
 * @author LeapYa
 * @since 2025-10-06
 */
@Service
@Slf4j
public class QRCodeService {

    @Autowired
    private SeoConfigService seoConfigService;
    
    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private RedisUtil redisUtil;
    
    @Autowired
    private MailUtil mailUtil;

    /**
     * 获取或生成文章分享二维码（带缓存）
     * 
     * @param articleId 文章ID
     * @return 二维码图片字节数组
     * @throws IllegalArgumentException 如果文章不存在
     */
    public byte[] getOrGenerateArticleQRCode(Integer articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("文章ID无效");
        }

        // 1. 尝试从Redis缓存获取
        String cacheKey = CacheConstants.buildArticleQRCodeKey(articleId);
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof byte[]) {
            return (byte[]) cached;
        }

        // 2. 验证文章是否存在
        Article article = articleService.getById(articleId);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在：ID=" + articleId);
        }

        // 3. 生成二维码
        try {
            byte[] qrCode = generateArticleQRCode(article);
            
            // 4. 存入Redis缓存（永久缓存）
            redisUtil.set(cacheKey, qrCode, CacheConstants.QRCODE_EXPIRE_TIME);
            log.info("生成并永久缓存文章二维码：文章ID [{}]，大小 [{}] bytes", articleId, qrCode.length);
            
            return qrCode;
        } catch (Exception e) {
            log.error("生成文章二维码失败：文章ID [{}]，错误: {}", articleId, e.getMessage(), e);
            throw new RuntimeException("生成二维码失败", e);
        }
    }

    /**
     * 生成文章分享二维码
     * 
     * @param articleId 文章ID
     * @return 二维码图片字节数组
     */
    private byte[] generateArticleQRCode(Article article) throws WriterException, IOException {
        // 1. 获取网站基础URL（复用 MailUtil 的方法）
        String siteUrl = mailUtil.getSiteUrl();
        
        // 2. 构建文章URL
        String articleUrl = siteUrl + ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug());

        // 3. 获取网站Logo
        String logoUrl = getLogoUrl(siteUrl);

        // 4. 生成带Logo的二维码
        return QRCodeUtil.generateQRCodeWithLogo(articleUrl, logoUrl);
    }

    /**
     * 获取网站Logo URL
     * 优先级：SEO配置的Logo > 网站默认Logo (siteUrl/static/assets/poetize.jpg)
     * 
     * @param siteUrl 网站基础URL
     * @return Logo URL
     */
    private String getLogoUrl(String siteUrl) {
        try {
            // 尝试从SEO配置获取Logo
            SeoConfig seoConfig = seoConfigService.getFullSeoConfig();
            if (seoConfig != null && StringUtils.hasText(seoConfig.getSiteLogo())) {
                String logoUrl = seoConfig.getSiteLogo();
                // 相对路径（如 /media/xxx）补全为完整 URL，QRCodeUtil 仅支持 http(s)
                if (logoUrl.startsWith("/")) {
                    logoUrl = siteUrl + logoUrl;
                }
                return logoUrl;
            }
        } catch (Exception e) {
            log.warn("获取SEO配置Logo失败，将使用默认Logo：{}", e.getMessage());
        }
        
        // 使用默认Logo
        String defaultLogoUrl = siteUrl + "/static/assets/poetize.jpg";
        return defaultLogoUrl;
    }

    /**
     * 清除文章二维码缓存
     * 用于文章更新或删除时清理缓存
     * 
     * @param articleId 文章ID
     */
    public void evictArticleQRCode(Integer articleId) {
        if (articleId != null && articleId > 0) {
            String cacheKey = CacheConstants.buildArticleQRCodeKey(articleId);
            redisUtil.del(cacheKey);
        }
    }
    
    /**
     * 预生成文章二维码并缓存
     * 用于文章保存时预先生成二维码
     * 
     * @param articleId 文章ID
     */
    public void preGenerateArticleQRCode(Integer articleId) {
        try {
            getOrGenerateArticleQRCode(articleId);
            log.info("预生成文章二维码成功：文章ID [{}]", articleId);
        } catch (Exception e) {
            // 预生成失败不影响主流程，仅记录日志
            log.warn("预生成文章二维码失败：文章ID [{}]，错误: {}", articleId, e.getMessage());
        }
    }
}
