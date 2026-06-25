package com.ld.poetry.service.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章相关 @Tool 工具
 * 
 * 对应 Python 端 MCP 工具：search_and_summarize, get_article_content,
 * get_hot_articles, list_categories
 */
@Service
public class ArticleTools {

    private static final Logger logger = LoggerFactory.getLogger(ArticleTools.class);

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private SortMapper sortMapper;

    @Tool(description = "按关键词定位候选文章，返回文章ID、标题和摘要，主要用于导航定位，不是文章事实问答的默认路径")
    public String searchArticles(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "返回数量，默认5") int limit) {
        try {
            if (limit <= 0)
                limit = 5;
            if (limit > 20)
                limit = 20;

            LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(Article::getArticleTitle, keyword)
                    .or().like(Article::getArticleContent, keyword)
                    .eq(Article::getViewStatus, 1) // 公开文章
                    .orderByDesc(Article::getCreateTime)
                    .last("LIMIT " + limit);

            List<Article> articles = articleMapper.selectList(wrapper);

            if (articles.isEmpty()) {
                return "未找到与 \"" + keyword + "\" 相关的文章。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(articles.size()).append(" 篇相关文章：\n\n");
            for (int i = 0; i < articles.size(); i++) {
                Article a = articles.get(i);
                sb.append(i + 1).append(". [ID=").append(a.getId()).append("] 《").append(a.getArticleTitle()).append("》\n");
                // 提取摘要：先转图片标记 → 去其余 HTML → 再截断，
                // 避免先截断导致 <img> 标签被从中间切断、正则删不干净残留乱码。
                String content = a.getArticleContent();
                if (content != null) {
                    // 摘要阶段只保留"有图"信号，不带 URL（URL 留给 getArticleContent 取全文时再看）
                    content = content.replaceAll("(?i)<img[^>]+src=[\"'][^\"']+[\"'][^>]*>", " [图片] ");
                    content = content.replaceAll("<[^>]+>", "").trim();
                    if (content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    sb.append("   摘要: ").append(content).append("\n\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("搜索文章失败", e);
            return "搜索文章时出错: " + e.getMessage();
        }
    }

    @Tool(description = "获取指定文章的完整内容")
    public String getArticleContent(
            @ToolParam(description = "文章ID") int articleId) {
        try {
            Article article = articleMapper.selectById(articleId);
            if (article == null) {
                return "未找到ID为 " + articleId + " 的文章。";
            }
            if (!Integer.valueOf(1).equals(article.getViewStatus())) {
                return "该文章不可公开访问。";
            }

            String content = article.getArticleContent();
            if (content != null) {
                // 将图片标签转为文本标记，让 AI 知道文章中图片的位置和 URL；
                // 当用户询问图片内容时，可调用 analyzeImage(url) 按需识别。
                // data URI 单独处理为短标记，避免 base64 膨胀 token。
                content = content.replaceAll("(?i)<img[^>]+src=[\"']data:[^\"']+[\"'][^>]*>", "\n[图片: 内联图片]\n");
                content = content.replaceAll("(?i)<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", "\n[图片: $1]\n");
                // 去除其余 HTML 标签
                content = content.replaceAll("<[^>]+>", "").trim();
                // 限制长度
                if (content.length() > 3000) {
                    content = content.substring(0, 3000) + "...[内容已截断]";
                }
            }

            return "《" + article.getArticleTitle() + "》\n\n" + (content != null ? content : "（无内容）");
        } catch (Exception e) {
            logger.error("获取文章内容失败", e);
            return "获取文章内容时出错: " + e.getMessage();
        }
    }

    @Tool(description = "获取热门文章列表")
    public String getHotArticles(
            @ToolParam(description = "返回数量，默认5") int limit) {
        try {
            if (limit <= 0)
                limit = 5;
            if (limit > 20)
                limit = 20;

            LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Article::getViewStatus, 1)
                    .orderByDesc(Article::getViewCount)
                    .last("LIMIT " + limit);

            List<Article> articles = articleMapper.selectList(wrapper);

            if (articles.isEmpty()) {
                return "暂无文章。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("热门文章 Top ").append(articles.size()).append("：\n\n");
            for (int i = 0; i < articles.size(); i++) {
                Article a = articles.get(i);
                sb.append(i + 1).append(". 《").append(a.getArticleTitle()).append("》")
                        .append(" (浏览量: ").append(a.getViewCount()).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取热门文章失败", e);
            return "获取热门文章时出错: " + e.getMessage();
        }
    }

    @Tool(description = "获取文章分类列表，包括每个分类的名称、描述和文章数量")
    public String listCategories() {
        try {
            // 查询所有分类
            List<Sort> sorts = sortMapper.selectList(
                    new LambdaQueryWrapper<Sort>().orderByAsc(Sort::getPriority));

            if (sorts.isEmpty()) {
                return "暂无文章分类。";
            }

            // 统计每个分类下的公开文章数
            LambdaQueryWrapper<Article> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(Article::getViewStatus, 1);
            List<Article> publicArticles = articleMapper.selectList(
                    countWrapper.select(Article::getSortId));

            Map<Integer, Long> countMap = publicArticles.stream()
                    .filter(a -> a.getSortId() != null)
                    .collect(Collectors.groupingBy(Article::getSortId, Collectors.counting()));

            StringBuilder sb = new StringBuilder();
            sb.append("文章分类列表（共 ").append(sorts.size()).append(" 个分类）：\n\n");
            for (int i = 0; i < sorts.size(); i++) {
                Sort s = sorts.get(i);
                long count = countMap.getOrDefault(s.getId(), 0L);
                sb.append(i + 1).append(". ").append(s.getSortName());
                if (s.getSortDescription() != null && !s.getSortDescription().isBlank()) {
                    sb.append(" - ").append(s.getSortDescription());
                }
                sb.append(" (").append(count).append(" 篇文章)\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取文章分类失败", e);
            return "获取文章分类时出错: " + e.getMessage();
        }
    }
}
