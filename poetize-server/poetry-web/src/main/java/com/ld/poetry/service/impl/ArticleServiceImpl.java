package com.ld.poetry.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.aop.ResourceCheck;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.enums.CommentTypeEnum;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import com.ld.poetry.service.TranslationService;
import java.util.Map;
import java.util.HashMap;
import com.ld.poetry.service.ai.LlmTranslationService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.ld.poetry.service.SummaryService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.event.ArticleSavedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * <p>
 * 文章表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private UserService userService;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private SummaryService summaryService;

    @Autowired
    private LlmTranslationService llmTranslationService;

    @Autowired
    private SeoService seoService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LockManager lockManager;

    @Autowired
    private com.ld.poetry.service.payment.PaymentService paymentService;

    @Autowired
    private com.ld.poetry.plugin.PluginHookManager pluginHookManager;

    @Override
    public PoetryResult saveArticle(ArticleVO articleVO) {
        // 调用重载方法，使用默认参数（不跳过AI翻译，无暂存翻译）
        return saveArticle(articleVO, false, null);
    }

    @Override
    public PoetryResult saveArticle(ArticleVO articleVO, boolean skipAiTranslation,
            Map<String, String> pendingTranslation) {
        log.info("开始保存文章");

        // 参数验证
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus()
                && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }

        // ========== 步骤1：在短事务中保存文章原文 ==========
        Integer savedArticleId = saveArticleInTransaction(articleVO);
        if (savedArticleId == null) {
            log.error("数据库保存失败");
            return PoetryResult.fail("保存文章失败");
        }
        log.info("文章原文保存成功，文章ID: {}，事务已提交，数据库连接已释放", savedArticleId);

        // 将文章ID回填到VO对象
        articleVO.setId(savedArticleId);

        // ========== 步骤2：事务外执行AI翻译（串行等待，但不占用数据库连接）==========
        Map<String, String> translationResult;
        try {
            translationResult = translationService.translateArticleOnly(
                    articleVO.getArticleTitle(),
                    articleVO.getArticleContent(),
                    skipAiTranslation,
                    pendingTranslation);
            applyTranslationSummary(translationResult, pendingTranslation, articleVO.getSummary(),
                    shouldAutoGenerateSummary(articleVO), !skipAiTranslation && !hasPendingTranslation(pendingTranslation));
        } catch (Exception e) {
            log.warn("翻译任务失败（继续后续流程）", e);
            translationResult = null;
        }

        // ========== 步骤3：在新事务中保存翻译结果 ==========
        if (translationResult != null && !translationResult.isEmpty()) {
            try {
                saveTranslationInNewTransaction(
                        savedArticleId,
                        translationResult.get("title"),
                        translationResult.get("content"),
                        translationResult.get("language"),
                        translationResult.get("summary"),
                        translationResult.containsKey("summary"));
                log.info("翻译结果保存成功，新事务已提交");
            } catch (Exception e) {
                log.error("翻译结果保存失败（继续执行后续流程）", e);
            }
        }

        try {
            // ========== 步骤4：生成多语言摘要（基于原文+翻译）==========
            if (shouldAutoGenerateSummary(articleVO)) {
                try {
                    summaryService.generateAndSaveSummary(savedArticleId);
                } catch (Exception e) {
                    log.error("摘要生成失败，预渲染将使用文章开头作为降级方案", e);
                    // 摘要生成失败不影响主流程，继续执行
                }
            } else {
                log.info("文章{}使用手动摘要，跳过自动摘要生成", savedArticleId);
            }

            // 异步发送订阅邮件
            if (articleVO.getViewStatus()) {
                final Integer finalLabelId = articleVO.getLabelId();
                final String finalArticleTitle = articleVO.getArticleTitle();
                // 使用虚拟线程异步发送邮件，不阻塞主流程
                Thread.ofVirtual().start(() -> {
                    try {
                        sendSubscriptionEmails(finalLabelId, finalArticleTitle);
                    } catch (Exception e) {
                        log.error("订阅邮件发送失败", e);
                    }
                });
            }

            // 清除缓存
            try {
                cacheService.evictSortArticleList();
            } catch (Exception e) {
                log.error("清除缓存失败: {}", e.getMessage(), e);
            }

            // ========== 步骤5：发布文章保存事件 ==========
            try {
                if (eventPublisher == null) {
                    log.error("eventPublisher为空，无法发布事件");
                } else {
                    eventPublisher.publishEvent(new ArticleSavedEvent(savedArticleId, articleVO.getSortId(), articleVO.getLabelId(),
                            null, null, null, articleVO.getViewStatus(), "CREATE",
                            articleVO.getSubmitToSearchEngine(), null));
                    log.info("已发布文章保存事件，文章ID: {}, 可见: {}", savedArticleId, articleVO.getViewStatus());
                }
            } catch (Exception e) {
                log.error("发布文章保存事件失败: {}", e.getMessage(), e);
            }

            log.info("文章保存流程全部完成，文章ID: {}", savedArticleId);

            // 触发插件钩子：文章保存完成
            pluginHookManager.onArticleSave(
                    Long.valueOf(savedArticleId),
                    articleVO.getArticleTitle(),
                    Long.valueOf(PoetryUtil.getUserId()));

            // 核心任务完成后立即返回
            return PoetryResult.success(savedArticleId);

        } catch (Exception e) {
            log.error("后台任务执行失败，文章ID: {}", savedArticleId, e);
            return PoetryResult.fail("部分操作失败：" + e.getMessage() + "，但文章已保存，文章ID: " + savedArticleId);
        }
    }

    /**
     * 异步保存文章（快速响应版本）
     */
    @Override
    public PoetryResult<String> saveArticleAsync(ArticleVO articleVO) {
        // 调用重载方法，使用默认参数
        return saveArticleAsync(articleVO, false, null);
    }

    /**
     * 异步保存文章（快速响应版本，支持翻译参数）
     */
    @Override
    public PoetryResult<String> saveArticleAsync(ArticleVO articleVO, boolean skipAiTranslation,
            Map<String, String> pendingTranslation) {
        return saveArticleAsync(articleVO, skipAiTranslation, pendingTranslation, null, null, null, null);
    }

    @Override
    public PoetryResult<String> saveArticleAsync(ArticleVO articleVO, boolean skipAiTranslation,
            Map<String, String> pendingTranslation, Integer actorUserId, String actorUsername,
            Consumer<Integer> successCallback, Consumer<Integer> failureCallback) {
        // 基础验证
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus()
                && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }

        Integer userId = actorUserId != null ? actorUserId : resolveAsyncActorUserId(articleVO);
        if (userId == null) {
            return PoetryResult.fail("无法确定文章作者，请重新登录后再试");
        }
        // 在主线程中获取用户信息，避免异步线程中无法访问RequestContext
        String currentUsername = StringUtils.hasText(actorUsername)
                ? actorUsername
                : resolveAsyncActorUsername(articleVO);
        final String finalUsername = currentUsername;
        String newTaskId = generateAsyncTaskId("article_save");
        String taskId = registerOrReuseAsyncTask(
                buildAsyncCreateTaskKey(articleVO, userId),
                newTaskId);
        if (!newTaskId.equals(taskId)) {
            log.info("复用进行中的异步保存任务，任务ID: {}", taskId);
            return PoetryResult.success(taskId);
        }

        // 初始化保存状态
        ArticleSaveStatus initialStatus = new ArticleSaveStatus(taskId, "processing", "正在保存文章...", null);
        initialStatus.setStage("queued");
        initialStatus.setSeoPushRequired(Boolean.TRUE.equals(articleVO.getViewStatus())
                && Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine()));
        ARTICLE_SAVE_STATUS.put(taskId, initialStatus);
        log.info("初始化异步保存任务，任务ID: {}", taskId);

        // 使用虚拟线程异步执行保存
        Thread.ofVirtual().name("article-save-" + taskId).start(() -> {
            Integer savedArticleId = null;
            try {

                // 设置用户ID（虚拟线程中无法访问RequestContext）
                articleVO.setUserId(userId);
                articleVO.setUpdateBy(finalUsername);

                // 更新状态：正在保存到数据库
                updateSaveStatus(taskId, "processing", "正在保存到数据库...");
                updateTranslationStage(taskId, "db_saved", "pending", "正在保存到数据库...", 0, false);

                // ========== 步骤1：使用短事务方法保存文章 ==========
                savedArticleId = saveArticleInTransaction(articleVO);
                if (savedArticleId == null) {
                    log.error("数据库保存失败，任务ID: {}", taskId);
                    updateSaveStatus(taskId, "failed", "数据库保存失败");
                    notifyAsyncSaveFailure(failureCallback, null);
                    return;
                }
                log.info("文章保存成功，任务ID: {}, 文章ID: {}，短事务已提交", taskId, savedArticleId);
                String articleReadyMessage = "文章已保存";
                updateSaveStatus(taskId, "processing", articleReadyMessage + "，正在准备后续任务...", savedArticleId);

                // ========== 步骤2：处理翻译（AI / 手动翻译 / 跳过）==========
                boolean autoSummary = shouldAutoGenerateSummary(articleVO);
                AsyncTranslationOutcome translationOutcome = processAsyncTranslation(
                        taskId,
                        savedArticleId,
                        articleVO.getArticleTitle(),
                        articleVO.getArticleContent(),
                        skipAiTranslation,
                        pendingTranslation,
                        articleReadyMessage,
                        autoSummary,
                        articleVO.getSummary());

                // ========== 步骤4：生成多语言摘要（基于原文+翻译）==========
                SummaryService.SummaryTaskResult summaryOutcome;
                if (autoSummary && summaryService.isAutoSummaryEnabled()) {
                    String summaryMessage = buildSummaryProgressMessage(
                            articleReadyMessage,
                            translationOutcome.translationStatus());
                    updateSaveStatus(taskId, "processing", summaryMessage, savedArticleId);
                    updateTaskStage(taskId, "generating_summary", translationOutcome.translationStatus(), "pending",
                            summaryMessage, null, false);
                    summaryOutcome = summaryService.generateAndSaveSummary(
                            savedArticleId, buildSummaryProgressListener(taskId));
                } else if (!autoSummary) {
                    updateTaskStage(taskId, "manual_summary_saved", translationOutcome.translationStatus(), "manual_saved",
                            "手动摘要已保存", null, false);
                    summaryOutcome = manualSummaryResult();
                } else {
                    summaryOutcome = summaryService.generateAndSaveSummary(savedArticleId, null);
                }
                applySummaryOutcome(taskId, summaryOutcome);

                // 清除缓存
                try {
                    cacheService.evictSortArticleList();
                } catch (Exception e) {
                    log.error("清除缓存失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }

                // 异步发送订阅邮件
                if (articleVO.getViewStatus()) {
                    final Integer finalLabelId = articleVO.getLabelId();
                    final String finalArticleTitle = articleVO.getArticleTitle();
                    final String finalTaskId = taskId;
                    // 使用虚拟线程异步发送邮件，不阻塞主流程
                    Thread.ofVirtual().start(() -> {
                        try {
                            sendSubscriptionEmails(finalLabelId, finalArticleTitle);
                        } catch (Exception e) {
                            log.error("订阅邮件发送失败，任务ID: {}", finalTaskId, e);
                        }
                    });
                }

                // ========== 步骤5：发布文章保存事件 ==========
                try {
                    if (eventPublisher == null) {
                        log.error("eventPublisher为空，无法发布事件，任务ID: {}", taskId);
                    } else {
                        eventPublisher.publishEvent(new ArticleSavedEvent(savedArticleId, articleVO.getSortId(), articleVO.getLabelId(),
                                null, null, taskId, articleVO.getViewStatus(), "CREATE",
                                articleVO.getSubmitToSearchEngine(), null));
                        log.info("已发布文章保存事件，任务ID: {}, 文章ID: {}, 可见: {}", taskId, savedArticleId,
                                articleVO.getViewStatus());
                    }
                } catch (Exception e) {
                    log.error("发布文章保存事件失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }

                // 最终状态（SEO推送将在预渲染完成后自动执行）
                String finalTaskStatus = translationOutcome.failed() || isSummaryTaskFailed(taskId) ? "partial_success" : "success";
                String finalMessage = buildFinalAsyncMessage("保存", finalTaskStatus,
                        translationOutcome.translationStatus(), getSummaryStatus(taskId));
                updateSaveStatus(taskId, finalTaskStatus, finalMessage, savedArticleId);
                updateTaskStage(taskId, "complete", translationOutcome.translationStatus(), getSummaryStatus(taskId), finalMessage, null,
                        false);
                emitTaskEvent(taskId, "complete", buildFinalTaskPayload(
                        taskId,
                        finalTaskStatus,
                        savedArticleId,
                        finalMessage,
                        translationOutcome.translationStatus(),
                        getSummaryStatus(taskId),
                        getSummaryMessage(taskId),
                        initialStatus.getSeoPushRequired(),
                        initialStatus.getSeoPushStatus(),
                        initialStatus.getSeoPushMessage()));
                log.info("异步文章保存流程全部完成，任务ID: {}, 文章ID: {}", taskId, savedArticleId);
                notifyAsyncSaveSuccess(successCallback, savedArticleId);

            } catch (Exception e) {
                log.error("文章保存失败，任务ID: {}", taskId, e);
                updateSaveStatus(taskId, "failed", "保存失败：" + e.getMessage());
                updateTranslationStage(taskId, "failed", "failed", "保存失败：" + e.getMessage(), null, false);
                emitTaskEvent(taskId, "error", Map.of(
                        "status", "failed",
                        "message", "保存失败：" + e.getMessage(),
                        "retryable", false));
                notifyAsyncSaveFailure(failureCallback, savedArticleId);
            } finally {
                releaseAsyncTaskGuard(taskId);
            }
        });

        return PoetryResult.success(taskId);
    }

    /**
     * 查询文章保存状态
     */
    @Override
    public PoetryResult<ArticleSaveStatus> getArticleSaveStatus(String taskId) {

        ArticleSaveStatus status = ARTICLE_SAVE_STATUS.get(taskId);
        if (status == null) {
            log.warn("任务不存在，任务ID: {}", taskId);
            return PoetryResult.fail("任务不存在或已过期");
        }

        // 如果任务完成（成功或失败），10分钟后自动清理
        if (("success".equals(status.getStatus()) || "failed".equals(status.getStatus())
                || "partial_success".equals(status.getStatus()))
                && System.currentTimeMillis() - status.getLastUpdateTime() > 10 * 60 * 1000) {
            ARTICLE_SAVE_STATUS.remove(taskId);
            ARTICLE_SAVE_EMITTERS.remove(taskId);
            clearTaskTransientState(taskId);
            releaseAsyncTaskGuard(taskId);
            return PoetryResult.fail("任务已过期");
        }

        return PoetryResult.success(status);
    }

    @Override
    public SseEmitter streamArticleSaveStatus(String taskId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        ArticleSaveStatus status = ARTICLE_SAVE_STATUS.get(taskId);
        if (status == null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "任务不存在或已过期")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        AtomicBoolean active = new AtomicBoolean(true);
        ARTICLE_SAVE_EMITTERS.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            active.set(false);
            removeEmitter(taskId, emitter);
        });
        emitter.onTimeout(() -> {
            active.set(false);
            removeEmitter(taskId, emitter);
        });
        emitter.onError(error -> {
            active.set(false);
            removeEmitter(taskId, emitter);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("stage")
                    .data(buildStagePayload(status)));
        } catch (Exception e) {
            removeEmitter(taskId, emitter);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
            return emitter;
        }

        startEmitterHeartbeat(emitter, active, "heartbeat");
        return emitter;
    }

    @Override
    public SseEmitter streamArticleSaveStatusBatch(List<String> taskIds) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        List<String> validTaskIds = taskIds == null ? List.of()
                : taskIds.stream().filter(StringUtils::hasText).distinct().toList();
        if (validTaskIds.isEmpty()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("task_error")
                        .data(Map.of("message", "未提供有效任务ID")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        AtomicBoolean active = new AtomicBoolean(true);
        validTaskIds.forEach(taskId -> ARTICLE_SAVE_EMITTERS
                .computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>())
                .add(emitter));

        emitter.onCompletion(() -> {
            active.set(false);
            removeBatchEmitter(validTaskIds, emitter);
        });
        emitter.onTimeout(() -> {
            active.set(false);
            removeBatchEmitter(validTaskIds, emitter);
        });
        emitter.onError(error -> {
            active.set(false);
            removeBatchEmitter(validTaskIds, emitter);
        });

        try {
            for (String taskId : validTaskIds) {
                ArticleSaveStatus status = ARTICLE_SAVE_STATUS.get(taskId);
                if (status == null) {
                    emitter.send(SseEmitter.event()
                            .name("task_error")
                            .data(Map.of(
                                    "taskId", taskId,
                                    "status", "failed",
                                    "message", "任务不存在或已过期")));
                } else {
                    emitter.send(SseEmitter.event()
                            .name("task_stage")
                            .data(buildStagePayload(status)));
                }
            }
        } catch (Exception e) {
            active.set(false);
            removeBatchEmitter(validTaskIds, emitter);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
            return emitter;
        }

        startEmitterHeartbeat(emitter, active, "batch_heartbeat", () -> removeBatchEmitter(validTaskIds, emitter));

        return emitter;
    }

    private void startEmitterHeartbeat(SseEmitter emitter, AtomicBoolean active, String eventName) {
        startEmitterHeartbeat(emitter, active, eventName, null);
    }

    private void startEmitterHeartbeat(SseEmitter emitter, AtomicBoolean active, String eventName,
            Runnable cleanupAction) {
        Thread.ofVirtual().name("article-save-" + eventName).start(() -> {
            while (active.get()) {
                try {
                    Thread.sleep(10000);
                    if (!active.get()) {
                        break;
                    }
                    emitter.send(SseEmitter.event()
                            .name(eventName)
                            .data(Map.of(
                                    "message", eventName,
                                    "timestamp", System.currentTimeMillis())));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    active.set(false);
                    if (cleanupAction != null) {
                        cleanupAction.run();
                    }
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        });
    }

    // 文章保存状态缓存（内存级别，重启后清空）
    private static final Map<String, ArticleSaveStatus> ARTICLE_SAVE_STATUS = new ConcurrentHashMap<>();
    private static final Map<String, CopyOnWriteArrayList<SseEmitter>> ARTICLE_SAVE_EMITTERS = new ConcurrentHashMap<>();
    private static final Map<String, Long> TASK_EVENT_THROTTLE_TIMES = new ConcurrentHashMap<>();
    private static final Map<String, String> ACTIVE_ASYNC_ARTICLE_TASKS = new ConcurrentHashMap<>();
    private static final Map<String, String> ASYNC_TASK_DEDUP_KEYS = new ConcurrentHashMap<>();
    private static final SummaryService.SummaryTaskResult MANUAL_SUMMARY_RESULT =
            new SummaryService.SummaryTaskResult("manual_saved", "手动摘要已保存", false);

    /**
     * 更新保存状态
     */
    private void updateSaveStatus(String taskId, String status, String message) {
        updateSaveStatus(taskId, status, message, null);
    }

    private void updateSaveStatus(String taskId, String status, String message, Integer articleId) {
        mutateSaveStatus(taskId, saveStatus -> {
            saveStatus.setStatus(status);
            saveStatus.setMessage(message);
            saveStatus.setArticleId(articleId);
        });
    }

    private void mutateSaveStatus(String taskId, Consumer<ArticleSaveStatus> mutator) {
        mutateSaveStatus(taskId, mutator, true);
    }

    private void mutateSaveStatus(String taskId, Consumer<ArticleSaveStatus> mutator, boolean emitStageEvent) {
        ArticleSaveStatus saveStatus = ARTICLE_SAVE_STATUS.get(taskId);
        if (saveStatus == null) {
            log.warn("状态更新失败 - 任务ID不存在: {}", taskId);
            return;
        }

        mutator.accept(saveStatus);
        saveStatus.setLastUpdateTime(System.currentTimeMillis());
        if (emitStageEvent) {
            emitTaskEvent(taskId, "stage", buildStagePayload(saveStatus));
        }
    }

    private void updateTranslationStage(String taskId, String stage, String translationStatus,
            String message, Integer attempt, boolean streaming) {
        updateTaskStage(taskId, stage, translationStatus, null, message, attempt, streaming);
    }

    private void updateTaskStage(String taskId, String stage, String translationStatus, String summaryStatus,
            String message, Integer attempt, boolean streaming) {
        mutateSaveStatus(taskId, saveStatus -> {
            saveStatus.setStage(stage);
            saveStatus.setTranslationStatus(translationStatus);
            if (summaryStatus != null) {
                saveStatus.setSummaryStatus(summaryStatus);
            }
            saveStatus.setMessage(message);
            saveStatus.setTranslationAttempt(attempt);
            saveStatus.setStreaming(streaming);
        });
    }

    private void appendTranslationPreview(String taskId, String field, String delta, Integer currentLength,
            Integer receivedLength) {
        mutateSaveStatus(taskId, saveStatus -> {
            int displayLength = updateTranslationReceivedChars(saveStatus, receivedLength != null ? receivedLength : currentLength);
            if ("title".equals(field)) {
                saveStatus.setTranslatedTitlePreview(appendWithLimit(saveStatus.getTranslatedTitlePreview(), delta));
                saveStatus.setMessage("正在流式翻译标题... 已接收 " + displayLength + " 字");
            } else {
                saveStatus.setTranslatedContentPreview(appendWithLimit(saveStatus.getTranslatedContentPreview(), delta));
                saveStatus.setMessage("正在流式翻译正文... 已接收 " + displayLength + " 字");
            }
        }, false);
    }

    private void updateTranslationRawProgress(String taskId, Integer currentLength) {
        mutateSaveStatus(taskId, saveStatus -> {
            int displayLength = updateTranslationReceivedChars(saveStatus, currentLength);
            saveStatus.setMessage("正在接收AI翻译响应... 已接收 " + displayLength + " 字");
            saveStatus.setStreaming(true);
        }, false);
    }

    private int updateTranslationReceivedChars(ArticleSaveStatus saveStatus, Integer receivedLength) {
        int safeReceivedLength = receivedLength == null ? 0 : Math.max(0, receivedLength);
        int previousLength = saveStatus.getTranslationReceivedChars() == null
                ? 0
                : saveStatus.getTranslationReceivedChars();
        int displayLength = Math.max(previousLength, safeReceivedLength);
        saveStatus.setTranslationReceivedChars(displayLength);
        return displayLength;
    }

    private void appendSummaryPreview(String taskId, String delta, Integer currentLength, String preview) {
        mutateSaveStatus(taskId, saveStatus -> {
            saveStatus.setSummaryStatus("streaming");
            saveStatus.setSummaryReceivedChars(currentLength);
            saveStatus.setSummaryPreview(StringUtils.hasText(preview)
                    ? preview
                    : appendWithLimit(saveStatus.getSummaryPreview(), delta));
            saveStatus.setSummaryMessage("正在流式生成摘要... 已接收 " + currentLength + " 字");
            saveStatus.setMessage(saveStatus.getSummaryMessage());
            saveStatus.setStreaming(true);
        }, false);
    }

    private void applySummaryOutcome(String taskId, SummaryService.SummaryTaskResult summaryOutcome) {
        String resolvedStatus = summaryOutcome == null ? "failed" : summaryOutcome.status();
        String resolvedMessage = summaryOutcome == null ? "摘要生成失败" : summaryOutcome.message();
        mutateSaveStatus(taskId, saveStatus -> {
            saveStatus.setSummaryStatus(resolvedStatus);
            saveStatus.setSummaryMessage(resolvedMessage);
            saveStatus.setStreaming(false);
        });
    }

    private SummaryService.SummaryProgressListener buildSummaryProgressListener(String taskId) {
        return (eventName, payload) -> {
            if (eventName == null) {
                return;
            }

            Map<String, Object> safePayload = payload == null ? Collections.emptyMap() : payload;
            switch (eventName) {
                case "start" -> {
                    mutateSaveStatus(taskId, saveStatus -> {
                        saveStatus.setSummaryStatus("streaming");
                        saveStatus.setSummaryMessage(readString(safePayload.get("message"), "开始流式生成摘要..."));
                        saveStatus.setMessage(saveStatus.getSummaryMessage());
                        saveStatus.setStreaming(true);
                    });
                    emitTaskEvent(taskId, "summary_start", enrichTaskPayload(taskId, safePayload));
                }
                case "summary_delta" -> {
                    Integer currentLength = readInteger(safePayload.get("currentLength"), 0);
                    String delta = readString(safePayload.get("delta"), "");
                    String preview = readString(safePayload.get("preview"), "");
                    appendSummaryPreview(taskId, delta, currentLength, preview);
                    emitTaskEvent(taskId, "summary_delta", enrichTaskPayload(taskId, safePayload));
                }
                case "complete" -> {
                    mutateSaveStatus(taskId, saveStatus -> {
                        saveStatus.setSummaryStatus("success");
                        saveStatus.setSummaryMessage(readString(safePayload.get("message"), "摘要已生成"));
                        saveStatus.setStreaming(false);
                    });
                    emitTaskEvent(taskId, "summary_complete", enrichTaskPayload(taskId, safePayload));
                }
                case "error" -> {
                    mutateSaveStatus(taskId, saveStatus -> {
                        saveStatus.setSummaryStatus("failed");
                        saveStatus.setSummaryMessage(readString(safePayload.get("message"), "摘要生成失败"));
                        saveStatus.setMessage(saveStatus.getSummaryMessage());
                        saveStatus.setStreaming(false);
                    });
                    emitTaskEvent(taskId, "summary_error", enrichTaskPayload(taskId, safePayload));
                }
                default -> {
                }
            }
        };
    }

    private String getSummaryStatus(String taskId) {
        ArticleSaveStatus saveStatus = ARTICLE_SAVE_STATUS.get(taskId);
        return saveStatus != null ? saveStatus.getSummaryStatus() : "pending";
    }

    private String getSummaryMessage(String taskId) {
        ArticleSaveStatus saveStatus = ARTICLE_SAVE_STATUS.get(taskId);
        return saveStatus != null ? saveStatus.getSummaryMessage() : "";
    }

    private boolean isSummaryTaskFailed(String taskId) {
        String summaryStatus = getSummaryStatus(taskId);
        return "timeout".equals(summaryStatus) || "failed".equals(summaryStatus);
    }

    private Map<String, Object> enrichTaskPayload(String taskId, Map<String, Object> payload) {
        Map<String, Object> enrichedPayload = new HashMap<>();
        if (payload != null) {
            enrichedPayload.putAll(payload);
        }
        enrichedPayload.put("taskId", taskId);
        return enrichedPayload;
    }

    private String appendWithLimit(String original, String delta) {
        String safeOriginal = original == null ? "" : original;
        String safeDelta = delta == null ? "" : delta;
        String combined = safeOriginal + safeDelta;
        return combined.length() <= 4000 ? combined : combined.substring(combined.length() - 4000);
    }

    private void emitTaskEvent(String taskId, String eventName, Map<String, ?> payload) {
        CopyOnWriteArrayList<SseEmitter> emitters = ARTICLE_SAVE_EMITTERS.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        if (shouldThrottleTaskEvent(taskId, eventName)) {
            return;
        }

        Map<String, Object> emittedPayload = new HashMap<>();
        if (payload != null) {
            emittedPayload.putAll(payload);
        }
        emittedPayload.put("taskId", taskId);

        for (SseEmitter emitter : emitters) {
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event();
                if (eventName != null) {
                    builder.name(eventName);
                }
                builder.data(emittedPayload);
                emitter.send(builder);
            } catch (Exception e) {
                removeEmitter(taskId, emitter);
            }
        }
    }

    private boolean shouldThrottleTaskEvent(String taskId, String eventName) {
        if (!StringUtils.hasText(taskId) || !isHighFrequencyTaskEvent(eventName)) {
            return false;
        }

        String throttleKey = taskId + ":" + eventName;
        long now = System.currentTimeMillis();
        Long lastEmitTime = TASK_EVENT_THROTTLE_TIMES.get(throttleKey);
        if (lastEmitTime != null && now - lastEmitTime < 500L) {
            return true;
        }
        TASK_EVENT_THROTTLE_TIMES.put(throttleKey, now);
        return false;
    }

    private boolean isHighFrequencyTaskEvent(String eventName) {
        return "translation_delta".equals(eventName)
                || "title_delta".equals(eventName)
                || "content_delta".equals(eventName)
                || "summary_delta".equals(eventName);
    }

    private void clearTaskTransientState(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return;
        }
        String prefix = taskId + ":";
        TASK_EVENT_THROTTLE_TIMES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String generateAsyncTaskId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    private String buildAsyncCreateTaskKey(ArticleVO articleVO, Integer userId) {
        String fingerprintSource = String.join("|",
                Objects.toString(userId, ""),
                Objects.toString(articleVO.getArticleTitle(), ""),
                SecureUtil.md5(Objects.toString(articleVO.getArticleContent(), "")),
                Objects.toString(ArticleUrlUtil.normalizeSlug(articleVO.getArticleSlug()), ""),
                Objects.toString(articleVO.getSortId(), ""),
                Objects.toString(articleVO.getLabelId(), ""),
                Objects.toString(articleVO.getViewStatus(), ""),
                Objects.toString(articleVO.getSubmitToSearchEngine(), ""));
        return "article_create:" + userId + ":" + SecureUtil.md5(fingerprintSource);
    }

    private String buildAsyncUpdateTaskKey(Integer articleId, Integer userId) {
        return "article_update:" + userId + ":" + articleId;
    }

    private String registerOrReuseAsyncTask(String dedupKey, String newTaskId) {
        while (true) {
            String existingTaskId = ACTIVE_ASYNC_ARTICLE_TASKS.putIfAbsent(dedupKey, newTaskId);
            if (existingTaskId == null) {
                ASYNC_TASK_DEDUP_KEYS.put(newTaskId, dedupKey);
                return newTaskId;
            }

            if (isAsyncTaskStillActive(existingTaskId)) {
                return existingTaskId;
            }

            ACTIVE_ASYNC_ARTICLE_TASKS.remove(dedupKey, existingTaskId);
            ASYNC_TASK_DEDUP_KEYS.remove(existingTaskId, dedupKey);
        }
    }

    private boolean isAsyncTaskStillActive(String taskId) {
        ArticleSaveStatus saveStatus = ARTICLE_SAVE_STATUS.get(taskId);
        if (saveStatus == null) {
            return ASYNC_TASK_DEDUP_KEYS.containsKey(taskId);
        }
        return isAsyncTaskStillActive(saveStatus);
    }

    private boolean isAsyncTaskStillActive(ArticleSaveStatus saveStatus) {
        if (saveStatus == null) {
            return false;
        }
        if (!isTerminalTaskStatus(saveStatus.getStatus())) {
            return true;
        }
        return isSeoPushPending(saveStatus);
    }

    private boolean isTerminalTaskStatus(String status) {
        return "success".equals(status) || "failed".equals(status) || "partial_success".equals(status);
    }

    private boolean isSeoPushPending(ArticleSaveStatus saveStatus) {
        if (saveStatus == null || !saveStatus.getSeoPushRequired()) {
            return false;
        }
        String seoPushStatus = saveStatus.getSeoPushStatus();
        return !StringUtils.hasText(seoPushStatus)
                || "pending".equals(seoPushStatus)
                || "pushing".equals(seoPushStatus);
    }

    private void releaseAsyncTaskGuard(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return;
        }

        ArticleSaveStatus saveStatus = ARTICLE_SAVE_STATUS.get(taskId);
        if (isAsyncTaskStillActive(saveStatus)) {
            return;
        }

        String dedupKey = ASYNC_TASK_DEDUP_KEYS.remove(taskId);
        if (!StringUtils.hasText(dedupKey)) {
            return;
        }

        ACTIVE_ASYNC_ARTICLE_TASKS.remove(dedupKey, taskId);
        clearTaskTransientState(taskId);
    }

    private void removeEmitter(String taskId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = ARTICLE_SAVE_EMITTERS.get(taskId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            ARTICLE_SAVE_EMITTERS.remove(taskId);
        }
    }

    private void removeBatchEmitter(List<String> taskIds, SseEmitter emitter) {
        if (taskIds == null) {
            return;
        }
        for (String taskId : taskIds) {
            removeEmitter(taskId, emitter);
        }
    }

    private Map<String, Object> buildStagePayload(ArticleSaveStatus status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", status.getTaskId());
        payload.put("status", status.getStatus());
        payload.put("stage", status.getStage());
        payload.put("message", status.getMessage());
        payload.put("articleId", status.getArticleId());
        payload.put("translationStatus", status.getTranslationStatus());
        payload.put("translationAttempt", status.getTranslationAttempt());
        payload.put("translationReceivedChars", status.getTranslationReceivedChars());
        payload.put("streaming", status.getStreaming());
        payload.put("translatedTitlePreview", status.getTranslatedTitlePreview());
        payload.put("translatedContentPreview", status.getTranslatedContentPreview());
        payload.put("summaryStatus", status.getSummaryStatus());
        payload.put("summaryMessage", status.getSummaryMessage());
        payload.put("summaryReceivedChars", status.getSummaryReceivedChars());
        payload.put("summaryPreview", status.getSummaryPreview());
        payload.put("seoPushRequired", status.getSeoPushRequired());
        payload.put("seoPushStatus", status.getSeoPushStatus());
        payload.put("seoPushMessage", status.getSeoPushMessage());
        payload.put("lastUpdateTime", status.getLastUpdateTime());
        return payload;
    }

    /**
     * 更新SEO推送状态（供 ArticleEventListener 回写推送结果）
     * 
     * @param taskId 异步任务ID
     * @param seoPushStatus 推送状态: pending, pushing, success, partial, failed
     * @param seoPushMessage 推送结果描述
     */
    @Override
    public void updateSeoPushStatus(String taskId, String seoPushStatus, String seoPushMessage) {
        ArticleSaveStatus saveStatus = ARTICLE_SAVE_STATUS.get(taskId);
        if (saveStatus == null) {
            // 任务可能已过期被清理，仅记录日志
            releaseAsyncTaskGuard(taskId);
            log.debug("SEO推送状态更新跳过 - 任务ID不存在或已过期: {}", taskId);
            return;
        }
        saveStatus.setSeoPushStatus(seoPushStatus);
        saveStatus.setSeoPushMessage(seoPushMessage);
        saveStatus.setLastUpdateTime(System.currentTimeMillis());
        emitTaskEvent(taskId, "seo_push", buildStagePayload(saveStatus));
        releaseAsyncTaskGuard(taskId);
    }

    private AsyncTranslationOutcome processAsyncTranslation(String taskId, Integer articleId, String title,
            String content, boolean skipAiTranslation, Map<String, String> pendingTranslation,
            String articleReadyMessage, boolean autoSummary, String sourceSummary) {
        if (hasPendingTranslation(pendingTranslation)) {
            updateTranslationStage(taskId, "saving_translation", "manual_saved",
                    articleReadyMessage + "，正在保存手动翻译...", null, false);
            try {
                saveTranslationInNewTransaction(
                        articleId,
                        pendingTranslation.get("title"),
                        pendingTranslation.get("content"),
                        pendingTranslation.get("language"),
                        pendingTranslation.get("summary"),
                        pendingTranslation.containsKey("summary"));
                updateTranslationStage(taskId, "saving_translation", "manual_saved",
                        "手动翻译已保存，" + getAfterTranslationSummaryMessage(autoSummary), null, false);
                emitTaskEvent(taskId, "saved", Map.of("articleId", articleId, "message", "手动翻译保存成功"));
                return new AsyncTranslationOutcome("manual_saved", false);
            } catch (Exception e) {
                log.error("手动翻译保存失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                updateTranslationStage(taskId, "saving_translation", "failed",
                        "手动翻译保存失败：" + e.getMessage() + "，" + getAfterTranslationSummaryMessage(autoSummary), null, false);
                return new AsyncTranslationOutcome("failed", true);
            }
        }

        if (shouldSkipAsyncTranslation(skipAiTranslation)) {
            updateTranslationStage(taskId, "translation_skipped", "skipped",
                    articleReadyMessage + "，已跳过AI翻译，" + getAfterTranslationSummaryMessage(autoSummary), null, false);
            return new AsyncTranslationOutcome("skipped", false);
        }

        updateTranslationStage(taskId, "translating", "pending",
                articleReadyMessage + "，正在进行AI翻译...", 0, false);

        Map<String, String> translationResult;
        try {
            translationResult = translationService.translateArticleOnly(
                    title,
                    content,
                    false,
                    null,
                    buildTranslationProgressListener(taskId));
        } catch (Exception e) {
            log.warn("翻译任务失败，任务ID: {}", taskId, e);
            translationResult = null;
        }

        if (translationResult == null || translationResult.isEmpty()) {
            updateTranslationStage(taskId, "translating", "failed",
                    articleReadyMessage + "，AI翻译失败，" + getAfterTranslationSummaryMessage(autoSummary), null, false);
            return new AsyncTranslationOutcome("failed", true);
        }

        updateTranslationStage(taskId, "saving_translation", "streaming",
                articleReadyMessage + "，正在保存翻译结果...", null, false);
        try {
            applyTranslationSummary(translationResult, pendingTranslation, sourceSummary, autoSummary, true);
            saveTranslationInNewTransaction(
                    articleId,
                    translationResult.get("title"),
                    translationResult.get("content"),
                    translationResult.get("language"),
                    translationResult.get("summary"),
                    translationResult.containsKey("summary"));
            updateTranslationStage(taskId, "saving_translation", "saved",
                    "翻译已保存，" + getAfterTranslationSummaryMessage(autoSummary), null, false);
            emitTaskEvent(taskId, "saved", Map.of("articleId", articleId, "message", "翻译保存成功"));
            return new AsyncTranslationOutcome("saved", false);
        } catch (Exception e) {
            log.error("翻译结果保存失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
            updateTranslationStage(taskId, "saving_translation", "failed",
                    "翻译保存失败：" + e.getMessage() + "，" + getAfterTranslationSummaryMessage(autoSummary), null, false);
            return new AsyncTranslationOutcome("failed", true);
        }
    }

    private boolean hasPendingTranslation(Map<String, String> pendingTranslation) {
        return pendingTranslation != null
                && StringUtils.hasText(pendingTranslation.get("title"))
                && StringUtils.hasText(pendingTranslation.get("content"))
                && StringUtils.hasText(pendingTranslation.get("language"));
    }

    private void applyTranslationSummary(Map<String, String> translationResult, Map<String, String> pendingTranslation,
            String sourceSummary, boolean autoSummary, boolean allowSourceSummaryTranslation) {
        if (translationResult == null || translationResult.isEmpty()) {
            return;
        }
        if (pendingTranslation != null && StringUtils.hasText(pendingTranslation.get("summary"))) {
            translationResult.put("summary", pendingTranslation.get("summary"));
            return;
        }
        if (!allowSourceSummaryTranslation || autoSummary || !StringUtils.hasText(sourceSummary)) {
            return;
        }
        String targetLanguage = translationResult.get("language");
        if (!StringUtils.hasText(targetLanguage)) {
            return;
        }
        try {
            String normalizedSourceSummary = ArticleSummaryTextUtil.toPlainText(sourceSummary, 500);
            String translatedSummary = translationService.translateText(normalizedSourceSummary, null, targetLanguage);
            if (StringUtils.hasText(translatedSummary)) {
                translationResult.put("summary", translatedSummary);
            }
        } catch (Exception e) {
            log.warn("手动摘要翻译失败，将仅保存翻译正文，目标语言: {}, 错误: {}", targetLanguage, e.getMessage());
        }
    }

    private boolean shouldSkipAsyncTranslation(boolean skipAiTranslation) {
        return skipAiTranslation || isTranslationDisabledByConfig();
    }

    private boolean isTranslationDisabledByConfig() {
        try {
            SysAiConfig articleAiConfig = sysAiConfigService.getArticleAiConfigInternal("default");
            return articleAiConfig != null && "none".equals(articleAiConfig.getTranslationType());
        } catch (Exception e) {
            log.warn("读取文章翻译配置失败，按需继续尝试AI翻译: {}", e.getMessage());
            return false;
        }
    }

    private String buildSummaryProgressMessage(String articleReadyMessage, String translationStatus) {
        String summaryTaskLabel = getSummaryTaskLabel();
        return switch (translationStatus) {
            case "saved" -> "翻译已保存，正在生成多语言" + summaryTaskLabel + "...";
            case "manual_saved" -> "手动翻译已保存，正在生成多语言" + summaryTaskLabel + "...";
            case "skipped" -> articleReadyMessage + "，已跳过AI翻译，正在生成多语言" + summaryTaskLabel + "...";
            case "failed" -> articleReadyMessage + "，AI翻译失败，正在继续生成多语言" + summaryTaskLabel + "...";
            default -> articleReadyMessage + "，正在生成多语言" + summaryTaskLabel + "...";
        };
    }

    private String buildFinalAsyncMessage(String actionText, String finalStatus, String translationStatus,
            String summaryStatus) {
        if ("manual_saved".equals(summaryStatus)) {
            return switch (translationStatus) {
                case "saved" -> "文章" + actionText + "成功！翻译已生成，手动摘要已保存";
                case "manual_saved" -> "文章" + actionText + "成功！手动翻译与手动摘要已保存";
                case "failed" -> "文章" + actionText + "成功，但翻译失败，手动摘要已保存";
                case "skipped" -> "文章" + actionText + "成功！已跳过AI翻译，手动摘要已保存";
                default -> "文章" + actionText + "成功！手动摘要已保存";
            };
        }

        if ("skipped".equals(summaryStatus)) {
            return switch (translationStatus) {
                case "saved" -> "文章" + actionText + "成功！翻译已生成，未自动生成摘要";
                case "manual_saved" -> "文章" + actionText + "成功！手动翻译已保存，未自动生成摘要";
                case "failed" -> "文章" + actionText + "成功，但翻译失败，未自动生成摘要";
                default -> "文章" + actionText + "成功！未自动生成摘要";
            };
        }

        String summaryTaskLabel = getSummaryTaskLabel();
        if ("partial_success".equals(finalStatus)) {
            boolean translationFailed = "failed".equals(translationStatus);
            boolean summaryFailed = "timeout".equals(summaryStatus) || "failed".equals(summaryStatus);
            if (translationFailed && summaryFailed) {
                return "文章" + actionText + "成功，但翻译失败，" + summaryTaskLabel + "未完成";
            }
            if (summaryFailed) {
                return "文章" + actionText + "成功，但" + summaryTaskLabel + "生成超时或失败";
            }
            return "文章" + actionText + "成功，" + summaryTaskLabel + "已生成，但翻译失败";
        }

        return switch (translationStatus) {
            case "saved" -> "文章" + actionText + "成功！翻译与" + summaryTaskLabel + "已生成";
            case "manual_saved" -> "文章" + actionText + "成功！手动翻译与" + summaryTaskLabel + "已生成";
            case "skipped" -> "文章" + actionText + "成功！已跳过AI翻译，" + summaryTaskLabel + "已生成";
            default -> "文章" + actionText + "成功！" + summaryTaskLabel + "已生成";
        };
    }

    private String getSummaryTaskLabel() {
        return "textrank".equalsIgnoreCase(summaryService.getSummaryGenerationMode()) ? "本地摘录" : "AI摘要";
    }

    private boolean shouldAutoGenerateSummary(ArticleVO articleVO) {
        return articleVO == null || !Boolean.FALSE.equals(articleVO.getAutoSummary());
    }

    private String normalizeManualSummary(ArticleVO articleVO) {
        if (articleVO == null || articleVO.getSummary() == null) {
            return "";
        }
        return ArticleSummaryTextUtil.toPlainText(articleVO.getSummary(), 500);
    }

    private SummaryService.SummaryTaskResult manualSummaryResult() {
        return MANUAL_SUMMARY_RESULT;
    }

    private String getAfterTranslationSummaryMessage(boolean autoSummary) {
        if (!autoSummary) {
            return "将使用手动摘要";
        }
        return summaryService.isAutoSummaryEnabled()
                ? "正在生成多语言" + getSummaryTaskLabel() + "..."
                : "未自动生成摘要";
    }

    private Map<String, Object> buildFinalTaskPayload(String taskId, String finalStatus, Integer articleId, String message,
            String translationStatus, String summaryStatus, String summaryMessage,
            boolean seoPushRequired, String seoPushStatus, String seoPushMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", finalStatus);
        payload.put("stage", "complete");
        payload.put("articleId", articleId);
        payload.put("message", message);
        payload.put("translationStatus", translationStatus);
        payload.put("summaryStatus", summaryStatus);
        payload.put("summaryMessage", summaryMessage);
        payload.put("seoPushRequired", seoPushRequired);
        payload.put("seoPushStatus", seoPushStatus);
        payload.put("seoPushMessage", seoPushMessage);
        return payload;
    }

    private record AsyncTranslationOutcome(String translationStatus, boolean failed) {
    }

    private TranslationService.TranslationProgressListener buildTranslationProgressListener(String taskId) {
        return (eventName, payload) -> {
            if (eventName == null) {
                return;
            }

            Map<String, Object> safePayload = payload == null ? Collections.emptyMap() : payload;
            String forwardedEventName = eventName;
            switch (eventName) {
                case "start" -> {
                    Integer attempt = readInteger(safePayload.get("attempt"), 1);
                    String message = readString(safePayload.get("message"), "开始流式翻译文章...");
                    updateTranslationStage(taskId, "translating", "streaming", message, attempt, true);
                }
                case "retry" -> {
                    Integer attempt = readInteger(safePayload.get("attempt"), 1);
                    String message = readString(safePayload.get("message"), "正在重试整篇流式翻译...");
                    updateTranslationStage(taskId, "translation_retry", "retrying", message, attempt, true);
                }
                case "title_delta" -> appendTranslationPreview(
                        taskId, "title", readString(safePayload.get("delta"), ""),
                        readInteger(safePayload.get("currentLength"), 0),
                        readInteger(safePayload.get("receivedLength"),
                                readInteger(safePayload.get("currentLength"), 0)));
                case "content_delta" -> appendTranslationPreview(
                        taskId, "content", readString(safePayload.get("delta"), ""),
                        readInteger(safePayload.get("currentLength"), 0),
                        readInteger(safePayload.get("receivedLength"),
                                readInteger(safePayload.get("currentLength"), 0)));
                case "translation_delta" -> updateTranslationRawProgress(
                        taskId, readInteger(safePayload.get("receivedLength"),
                                readInteger(safePayload.get("currentLength"), 0)));
                case "complete" -> {
                    updateTranslationStage(
                            taskId, "translating", "streaming",
                            readString(safePayload.get("message"), "流式翻译完成，等待保存"), null, false);
                    forwardedEventName = "translation_complete";
                }
                case "error" -> {
                    String message = readString(safePayload.get("message"), "流式翻译失败");
                    boolean retryable = Boolean.TRUE.equals(safePayload.get("retryable"));
                    updateTranslationStage(taskId, retryable ? "translation_retry" : "translating",
                            retryable ? "retrying" : "failed",
                            message, readInteger(safePayload.get("attempt"), 0), false);
                }
                default -> {
                }
            }

            emitTaskEvent(taskId, forwardedEventName, safePayload);
        };
    }

    private String readString(Object value, String defaultValue) {
        return value instanceof String stringValue && StringUtils.hasText(stringValue) ? stringValue : defaultValue;
    }

    private Integer readInteger(Object value, Integer defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * 发送订阅邮件（从原方法提取）
     */
    private void sendSubscriptionEmails(Integer labelId, String articleTitle) {
        try {
            List<User> users = userService.lambdaQuery().select(User::getEmail, User::getSubscribe)
                    .eq(User::getUserStatus, PoetryEnum.STATUS_ENABLE.getCode()).list();
            List<String> emails = users.stream().filter(u -> {
                List<Integer> sub = JSON.parseArray(u.getSubscribe(), Integer.class);
                return !CollectionUtils.isEmpty(sub) && sub.contains(labelId);
            }).map(User::getEmail).collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(emails)) {
                LambdaQueryChainWrapper<Label> wrapper = new LambdaQueryChainWrapper<>(labelMapper);
                Label label = wrapper.select(Label::getLabelName).eq(Label::getId, labelId).one();
                String text = getSubscribeMail(label.getLabelName(), articleTitle);
                WebInfo webInfo = cacheService.getCachedWebInfo();
                mailUtil.sendMailMessage(emails,
                        "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                log.info("订阅邮件发送完成，发送给{}个用户", emails.size());
            }
        } catch (Exception e) {
            log.error("订阅邮件发送失败", e);
        }
    }

    /**
     * 文章保存状态类
     */
    public static class ArticleSaveStatus {
        private String taskId;
        private String status; // processing, success, failed, partial_success
        private String stage;
        private String message;
        private Integer articleId;
        private String translationStatus;
        private String summaryStatus;
        private String summaryMessage;
        private Integer summaryReceivedChars;
        private String summaryPreview;
        private Integer translationAttempt;
        private Integer translationReceivedChars;
        private Boolean streaming;
        private String translatedTitlePreview;
        private String translatedContentPreview;
        private boolean seoPushRequired;
        private String seoPushStatus;   // pending, pushing, success, partial, failed
        private String seoPushMessage;
        private long lastUpdateTime;

        public ArticleSaveStatus(String taskId, String status, String message, Integer articleId) {
            this.taskId = taskId;
            this.status = status;
            this.message = message;
            this.articleId = articleId;
            this.stage = "queued";
            this.translationStatus = "pending";
            this.summaryStatus = "pending";
            this.summaryMessage = "";
            this.summaryReceivedChars = 0;
            this.summaryPreview = "";
            this.translationAttempt = 0;
            this.translationReceivedChars = 0;
            this.streaming = false;
            this.translatedTitlePreview = "";
            this.translatedContentPreview = "";
            this.seoPushRequired = false;
            this.seoPushStatus = "pending";
            this.seoPushMessage = "";
            this.lastUpdateTime = System.currentTimeMillis();
        }

        // getters and setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public Integer getArticleId() {
            return articleId;
        }

        public void setArticleId(Integer articleId) {
            this.articleId = articleId;
        }

        public String getTranslationStatus() {
            return translationStatus;
        }

        public void setTranslationStatus(String translationStatus) {
            this.translationStatus = translationStatus;
        }

        public Integer getTranslationAttempt() {
            return translationAttempt;
        }

        public void setTranslationAttempt(Integer translationAttempt) {
            this.translationAttempt = translationAttempt;
        }

        public Integer getTranslationReceivedChars() {
            return translationReceivedChars;
        }

        public void setTranslationReceivedChars(Integer translationReceivedChars) {
            this.translationReceivedChars = translationReceivedChars;
        }

        public String getSummaryStatus() {
            return summaryStatus;
        }

        public void setSummaryStatus(String summaryStatus) {
            this.summaryStatus = summaryStatus;
        }

        public String getSummaryMessage() {
            return summaryMessage;
        }

        public void setSummaryMessage(String summaryMessage) {
            this.summaryMessage = summaryMessage;
        }

        public Integer getSummaryReceivedChars() {
            return summaryReceivedChars;
        }

        public void setSummaryReceivedChars(Integer summaryReceivedChars) {
            this.summaryReceivedChars = summaryReceivedChars;
        }

        public String getSummaryPreview() {
            return summaryPreview;
        }

        public void setSummaryPreview(String summaryPreview) {
            this.summaryPreview = summaryPreview;
        }

        public Boolean getStreaming() {
            return streaming;
        }

        public void setStreaming(Boolean streaming) {
            this.streaming = streaming;
        }

        public String getTranslatedTitlePreview() {
            return translatedTitlePreview;
        }

        public void setTranslatedTitlePreview(String translatedTitlePreview) {
            this.translatedTitlePreview = translatedTitlePreview;
        }

        public String getTranslatedContentPreview() {
            return translatedContentPreview;
        }

        public void setTranslatedContentPreview(String translatedContentPreview) {
            this.translatedContentPreview = translatedContentPreview;
        }

        public boolean getSeoPushRequired() {
            return seoPushRequired;
        }

        public void setSeoPushRequired(boolean seoPushRequired) {
            this.seoPushRequired = seoPushRequired;
        }

        public String getSeoPushStatus() {
            return seoPushStatus;
        }

        public void setSeoPushStatus(String seoPushStatus) {
            this.seoPushStatus = seoPushStatus;
        }

        public String getSeoPushMessage() {
            return seoPushMessage;
        }

        public void setSeoPushMessage(String seoPushMessage) {
            this.seoPushMessage = seoPushMessage;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }
    }

    private String getSubscribeMail(String labelName, String articleTitle) {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        String webName = (webInfo == null ? "POETIZE" : webInfo.getWebName());

        // 从数据库获取订阅模板
        String subscribeTemplate = sysConfigService.getConfigValueByKey("user.subscribe.format");
        if (subscribeTemplate == null || subscribeTemplate.trim().isEmpty()) {
            // 如果数据库中没有配置，使用默认模板
            subscribeTemplate = "【POETIZE】您订阅的专栏【%s】新增一篇文章：%s。";
            log.warn("数据库中未找到订阅模板配置，使用默认模板");
        }

        log.info("使用订阅邮件模板: {}", subscribeTemplate); // 添加日志记录使用的模板

        User adminUser = PoetryUtil.getAdminUser();
        String adminUsername = adminUser != null ? adminUser.getUsername() : "站长";

        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.notificationMail, adminUsername),
                adminUsername,
                String.format(subscribeTemplate, labelName, articleTitle),
                "",
                webName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult deleteArticle(Integer id) {
        Integer userId = PoetryUtil.getUserId();

        // 检查文章是否存在
        Article article = lambdaQuery().eq(Article::getId, id).one();
        if (article == null) {
            return PoetryResult.fail("文章不存在！");
        }

        // 如果是文章作者或管理员，允许删除
        boolean canDelete = article.getUserId().equals(userId) || PoetryUtil.isBoss();
        if (!canDelete) {
            return PoetryResult.fail("没有权限删除此文章！");
        }

        // 删除文章（事务保护）
        removeById(id);

        // 使用Redis缓存清理替换PoetryCache
        cacheService.evictArticleRelatedCache(id);

        return PoetryResult.success();
    }

    @Override
    public PoetryResult updateArticle(ArticleVO articleVO) {
        // 调用重载方法，使用默认参数（不跳过AI翻译，无暂存翻译）
        return updateArticle(articleVO, false, null);
    }

    @Override
    public PoetryResult updateArticle(ArticleVO articleVO, boolean skipAiTranslation,
            Map<String, String> pendingTranslation) {
        return updateArticle(articleVO, skipAiTranslation, pendingTranslation, null);
    }

    @Override
    public PoetryResult updateArticle(ArticleVO articleVO, boolean skipAiTranslation,
            Map<String, String> pendingTranslation, Integer actorUserId) {
        log.info("开始更新文章，ID: {}", articleVO.getId());

        if (articleVO.getId() == null) {
            return PoetryResult.fail("文章ID不能为空");
        }

        // 验证数据合法性
        if (StringUtils.hasText(articleVO.getArticleTitle()) && articleVO.getArticleTitle().trim().isEmpty()) {
            return PoetryResult.fail("文章标题为空");
        }

        // 参数验证
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus()
                && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }

        Integer userId = actorUserId != null ? actorUserId : PoetryUtil.getUserId();
        if (userId == null) {
            return PoetryResult.fail("无法确定文章作者，请重新登录后再试");
        }
        Article existingArticle = getById(articleVO.getId());
        Integer previousSortId = existingArticle == null ? null : existingArticle.getSortId();
        Integer previousLabelId = existingArticle == null ? null : existingArticle.getLabelId();
        String previousArticleSlug = existingArticle == null ? null : existingArticle.getArticleSlug();
        String articleSlug = resolveArticleSlugForSave(articleVO.getArticleSlug(), articleVO.getId());
        String updateBy = resolveAsyncActorUsername(articleVO);
        articleVO.setUserId(userId);
        articleVO.setUpdateBy(updateBy);
        articleVO.setArticleSlug(articleSlug);
        final Integer updatedArticleId = articleVO.getId();
        final String updatedContent = articleVO.getArticleContent();

        // 构建更新链式包装器
        LambdaUpdateChainWrapper<Article> updateChainWrapper = lambdaUpdate()
                .eq(Article::getId, articleVO.getId())
                .eq(Article::getUserId, userId)
                .set(Article::getLabelId, articleVO.getLabelId())
                .set(Article::getSortId, articleVO.getSortId())
                .set(Article::getArticleTitle, articleVO.getArticleTitle())
                .set(Article::getArticleSlug, articleSlug)
                .set(Article::getUpdateBy, updateBy)
                .set(Article::getUpdateTime, LocalDateTime.now())
                .set(Article::getVideoUrl,
                        StringUtils.hasText(articleVO.getVideoUrl()) ? articleVO.getVideoUrl() : null)
                .set(Article::getArticleContent, articleVO.getArticleContent())
                .set(Article::getPayType, articleVO.getPayType())
                .set(Article::getPayAmount, articleVO.getPayAmount())
                .set(Article::getFreePercent, articleVO.getFreePercent());

        if (articleVO.getArticleCover() != null) {
            updateChainWrapper.set(Article::getArticleCover, articleVO.getArticleCover());
        }
        if (articleVO.getCommentStatus() != null) {
            updateChainWrapper.set(Article::getCommentStatus, articleVO.getCommentStatus());
        }
        if (articleVO.getRecommendStatus() != null) {
            updateChainWrapper.set(Article::getRecommendStatus, articleVO.getRecommendStatus());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus()
                && StringUtils.hasText(articleVO.getPassword())) {
            updateChainWrapper.set(Article::getPassword, articleVO.getPassword());
            updateChainWrapper.set(StringUtils.hasText(articleVO.getTips()), Article::getTips, articleVO.getTips());
        }
        if (Boolean.TRUE.equals(articleVO.getViewStatus())) {
            updateChainWrapper.set(Article::getPassword, null);
            updateChainWrapper.set(Article::getTips, null);
        }
        if (articleVO.getViewStatus() != null) {
            updateChainWrapper.set(Article::getViewStatus, articleVO.getViewStatus());
        }
        if (articleVO.getSubmitToSearchEngine() != null) {
            updateChainWrapper.set(Article::getSubmitToSearchEngine, articleVO.getSubmitToSearchEngine());
        }
        if (!shouldAutoGenerateSummary(articleVO)) {
            updateChainWrapper.set(Article::getSummary, normalizeManualSummary(articleVO));
        }

        // ========== 步骤1：在短事务中更新文章 ==========
        boolean updateResult = updateArticleInTransaction(updateChainWrapper);
        if (!updateResult) {
            log.error("数据库更新失败");
            return PoetryResult.fail("更新文章失败");
        }
        log.info("文章更新成功，文章ID: {}，事务已提交，数据库连接已释放", updatedArticleId);

        // ========== 步骤2：事务外执行AI翻译（串行等待，但不占用数据库连接）==========
        Map<String, String> translationResult;
        try {
            translationResult = translationService.translateArticleOnly(
                    articleVO.getArticleTitle(),
                    articleVO.getArticleContent(),
                    skipAiTranslation,
                    pendingTranslation);
            applyTranslationSummary(translationResult, pendingTranslation, articleVO.getSummary(),
                    shouldAutoGenerateSummary(articleVO), !skipAiTranslation && !hasPendingTranslation(pendingTranslation));
        } catch (Exception e) {
            log.warn("翻译任务失败（继续后续流程）", e);
            translationResult = null;
        }

        // ========== 步骤3：在新事务中保存翻译结果 ==========
        if (translationResult != null && !translationResult.isEmpty()) {
            try {
                saveTranslationInNewTransaction(
                        updatedArticleId,
                        translationResult.get("title"),
                        translationResult.get("content"),
                        translationResult.get("language"),
                        translationResult.get("summary"),
                        translationResult.containsKey("summary"));
                log.info("翻译结果保存成功，新事务已提交");
            } catch (Exception e) {
                log.error("翻译结果保存失败（继续执行后续流程）", e);
            }
        }

        try {
            // ========== 步骤4：更新多语言摘要（基于原文+翻译）==========
            if (shouldAutoGenerateSummary(articleVO)) {
                if (StringUtils.hasText(updatedContent)) {
                    try {
                        summaryService.updateSummary(updatedArticleId, updatedContent);
                    } catch (Exception e) {
                        log.error("摘要更新失败，预渲染将使用原有摘要或文章开头", e);
                        // 摘要更新失败不影响主流程，继续执行
                    }
                }
            } else {
                log.info("文章{}使用手动摘要，跳过自动摘要更新", updatedArticleId);
            }

            // 清除缓存
            try {
                cacheService.evictSortArticleList();
            } catch (Exception e) {
                log.error("清除缓存失败: {}", e.getMessage(), e);
            }

            // ========== 步骤5：发布文章更新事件 ==========
            try {
                eventPublisher.publishEvent(new ArticleSavedEvent(updatedArticleId, articleVO.getSortId(), articleVO.getLabelId(),
                        previousSortId, previousLabelId, null, articleVO.getViewStatus(), "UPDATE",
                        articleVO.getSubmitToSearchEngine(), previousArticleSlug));
            } catch (Exception e) {
                log.error("发布文章更新事件失败: {}", e.getMessage(), e);
            }

            log.info("文章更新流程全部完成，文章ID: {}", updatedArticleId);

            // 核心任务完成后立即返回
            return PoetryResult.success();

        } catch (Exception e) {
            log.error("后台任务执行失败，文章ID: {}", updatedArticleId, e);
            return PoetryResult.fail("部分操作失败：" + e.getMessage() + "，但文章已更新");
        }
    }

    @Override
    public PoetryResult<Page> listArticle(BaseRequestVO baseRequestVO) {
        List<Integer> ids = null;
        List<List<Integer>> idList = null;
        if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
            idList = commonQuery.getArticleIds(baseRequestVO.getArticleSearch());
            ids = idList.stream().flatMap(Collection::stream).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                baseRequestVO.setRecords(new ArrayList<>());
                return PoetryResult.success(baseRequestVO);
            }
        }

        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.in(!CollectionUtils.isEmpty(ids), Article::getId, ids);
        lambdaQuery.like(StringUtils.hasText(baseRequestVO.getSearchKey()), Article::getArticleTitle,
                baseRequestVO.getSearchKey());
        lambdaQuery.eq(baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus(),
                Article::getRecommendStatus, PoetryEnum.STATUS_ENABLE.getCode());

        // 添加对可见文章的过滤，确保预渲染和前端只获取可见的文章
        lambdaQuery.eq(Article::getViewStatus, true);

        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        } else if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        lambdaQuery.orderByDesc(Article::getCreateTime);

        Page<Article> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.page(page);

        List<Article> records = page.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> articles = new ArrayList<>();
            List<ArticleVO> titles = new ArrayList<>();
            List<ArticleVO> contents = new ArrayList<>();

            for (Article article : records) {
                // 保存原始内容用于显示前的高亮处理
                String originalContent = article.getArticleContent();
                String originalTitle = article.getArticleTitle();

                ArticleVO articleVO = buildArticleVO(article, false);

                // 直接使用数据库中存储的摘要（仅在非搜索场景下设置）
                if (!StringUtils.hasText(baseRequestVO.getArticleSearch())
                        && StringUtils.hasText(article.getSummary())) {
                    articleVO.setSummary(article.getSummary());
                }

                articleVO.setHasVideo(StringUtils.hasText(articleVO.getVideoUrl()));
                articleVO.setPassword(null);
                articleVO.setVideoUrl(null);

                // 如果是搜索结果，进行高亮处理
                if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
                    String searchText = baseRequestVO.getArticleSearch();

                    // 检测是否为正则表达式搜索
                    boolean isRegexSearch = searchText.startsWith("/") && searchText.endsWith("/")
                            && searchText.length() > 2;
                    String actualSearchText = isRegexSearch ? searchText.substring(1, searchText.length() - 1)
                            : searchText;

                    // 使用高亮标签进行处理
                    String highlightStart = "<span class='search-highlight' style='color: var(--lightGreen); font-weight: bold;'>";
                    String highlightEnd = "</span>";

                    // 检查原文是否匹配
                    boolean originalTitleMatches = false;
                    boolean originalContentMatches = false;

                    if (isRegexSearch) {
                        Pattern pattern = Pattern.compile(actualSearchText, Pattern.CASE_INSENSITIVE);
                        originalTitleMatches = originalTitle != null && pattern.matcher(originalTitle).find();
                        originalContentMatches = originalContent != null && pattern.matcher(originalContent).find();
                    } else {
                        originalTitleMatches = originalTitle != null
                                && originalTitle.toLowerCase().contains(searchText.toLowerCase());
                        originalContentMatches = originalContent != null
                                && originalContent.toLowerCase().contains(searchText.toLowerCase());
                    }

                    boolean originalMatches = originalTitleMatches || originalContentMatches;

                    // 检查翻译是否匹配
                    String matchedLanguage = commonQuery.getMatchedTranslationLanguage(articleVO.getId(), searchText);
                    boolean translationMatches = matchedLanguage != null;

                    if (originalMatches && translationMatches) {
                        // 原文和翻译都匹配：优先显示原文，但标记翻译也匹配
                        articleVO.setIsTranslationMatch(false); // 优先显示原文
                        articleVO.setMatchedLanguage(matchedLanguage); // 保存匹配的翻译语言信息

                        // 可以添加一个字段标识翻译也匹配了
                        articleVO.setHasTranslationMatch(true);

                    } else if (originalMatches) {
                        // 只有原文匹配
                        articleVO.setIsTranslationMatch(false);

                    } else if (translationMatches) {
                        // 只有翻译匹配
                        Map<String, String> matchedTranslation = commonQuery.getMatchedTranslation(articleVO.getId(),
                                searchText, matchedLanguage);
                        if (matchedTranslation != null) {
                            articleVO.setIsTranslationMatch(true);
                            articleVO.setMatchedLanguage(matchedLanguage);

                            // 高亮翻译标题和内容，并替换原文显示
                            String translatedTitle = matchedTranslation.get("title");
                            String translatedContent = matchedTranslation.get("content");

                            if (translatedTitle != null) {
                                String highlightedTitle;
                                if (isRegexSearch) {
                                    highlightedTitle = StringUtil.highlightTextWithRegex(translatedTitle,
                                            actualSearchText, highlightStart, highlightEnd);
                                } else {
                                    highlightedTitle = StringUtil.highlightText(translatedTitle, searchText,
                                            highlightStart, highlightEnd);
                                }
                                articleVO.setArticleTitle(highlightedTitle); // 替换显示的标题
                            }
                            if (translatedContent != null) {
                                // 智能截取包含搜索关键词的内容片段
                                String contentSnippet = getContentSnippetWithKeyword(translatedContent, searchText,
                                        CommonConst.SUMMARY);
                                String highlightedContent;
                                if (isRegexSearch) {
                                    highlightedContent = StringUtil.highlightTextWithRegex(contentSnippet,
                                            actualSearchText, highlightStart, highlightEnd);
                                } else {
                                    highlightedContent = StringUtil.highlightText(contentSnippet, searchText,
                                            highlightStart, highlightEnd);
                                }
                                articleVO.setArticleContent(highlightedContent); // 替换显示的内容
                            }

                        }
                    } else {
                        // 原文和翻译都不匹配（理论上不应该出现）
                        articleVO.setIsTranslationMatch(false);
                        log.warn("文章ID {} 在搜索结果中但原文和翻译都不匹配搜索词: {}", articleVO.getId(), searchText);
                    }

                    // 对标题和内容进行高亮处理（原有逻辑）
                    if (idList.get(0).contains(articleVO.getId())) {
                        // 标题匹配的文章
                        if (!Boolean.TRUE.equals(articleVO.getIsTranslationMatch())) {
                            String highlightedTitle;
                            if (isRegexSearch) {
                                highlightedTitle = StringUtil.highlightTextWithRegex(originalTitle, actualSearchText,
                                        highlightStart, highlightEnd);
                            } else {
                                highlightedTitle = StringUtil.highlightText(originalTitle, searchText, highlightStart,
                                        highlightEnd);
                            }
                            articleVO.setArticleTitle(highlightedTitle);
                        }
                        titles.add(articleVO);
                    } else if (idList.get(1).contains(articleVO.getId())) {
                        // 内容匹配的文章
                        if (!Boolean.TRUE.equals(articleVO.getIsTranslationMatch())) {
                            // 智能截取包含搜索关键词的原文内容片段（使用原始内容）
                            String contentSnippet = getContentSnippetWithKeyword(originalContent, searchText,
                                    CommonConst.SUMMARY);
                            String highlightedContent;
                            if (isRegexSearch) {
                                highlightedContent = StringUtil.highlightTextWithRegex(contentSnippet, actualSearchText,
                                        highlightStart, highlightEnd);
                            } else {
                                highlightedContent = StringUtil.highlightText(contentSnippet, searchText,
                                        highlightStart, highlightEnd);
                            }
                            articleVO.setArticleContent(highlightedContent);
                        }
                        contents.add(articleVO);
                    } else if (Boolean.TRUE.equals(articleVO.getIsTranslationMatch())) {
                        // 翻译匹配的文章，统一添加到内容匹配列表
                        contents.add(articleVO);
                    }
                } else {
                    // 非搜索情况下，对内容进行默认截断处理
                    if (originalContent.length() > CommonConst.SUMMARY) {
                        String truncatedContent = originalContent.substring(0, CommonConst.SUMMARY)
                                .replace("`", "").replace("#", "").replace(">", "") + "...";
                        articleVO.setArticleContent(truncatedContent);
                    }
                    articles.add(articleVO);
                }

                // 搜索场景下，确保summary为空，强制前端使用articleContent
                if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
                    articleVO.setSummary(null);
                }
            }

            List<ArticleVO> collect = new ArrayList<>();
            collect.addAll(articles);
            collect.addAll(titles);
            collect.addAll(contents);
            baseRequestVO.setRecords(collect);
        }
        return PoetryResult.success(baseRequestVO);
    }

    @Override
    @ResourceCheck(CommonConst.RESOURCE_ARTICLE_DOC)
    public PoetryResult<ArticleVO> getArticleById(Integer id, String password) {
        return getArticleById(id, password, true);
    }

    @Override
    public PoetryResult<ArticleVO> getArticleByPath(String path, String password) {
        return getArticleByPath(path, password, true);
    }

    @Override
    public PoetryResult<ArticleVO> getArticleByPath(String path, String password, boolean incrementViewCount) {
        Integer articleId = resolveArticleIdByPath(path);
        if (articleId == null) {
            return PoetryResult.success();
        }
        return getArticleById(articleId, password, incrementViewCount);
    }

    @Override
    public Integer resolveArticleIdByPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        String token = path.trim();
        if (ArticleUrlUtil.isNumericToken(token)) {
            try {
                return Integer.valueOf(token);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String slug = ArticleUrlUtil.normalizeSlug(token);
        if (!ArticleUrlUtil.isValidSlug(slug)) {
            return null;
        }

        Article article = lambdaQuery()
                .select(Article::getId)
                .eq(Article::getArticleSlug, slug)
                .last("limit 1")
                .one();
        return article == null ? null : article.getId();
    }

    /**
     * 获取文章详情，可以选择是否增加浏览量
     * 
     * @param id                 文章ID
     * @param password           密码
     * @param incrementViewCount 是否增加浏览量
     * @return 文章详情
     */
    public PoetryResult<ArticleVO> getArticleById(Integer id, String password, boolean incrementViewCount) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id);

        Article article = lambdaQuery.one();
        if (article == null) {
            return PoetryResult.success();
        }
        if (!article.getViewStatus() && (!StringUtils.hasText(password) || !password.equals(article.getPassword()))) {
            return PoetryResult
                    .fail("密码错误" + (StringUtils.hasText(article.getTips()) ? article.getTips() : "请联系作者获取密码"));
        }

        // 作者本人访问自己的文章时不计入浏览量，避免热度被作者自己抬高。
        if (shouldIncrementViewCount(article, incrementViewCount)) {
            int updatedRows = articleMapper.updateViewCount(id);
            if (updatedRows > 0) {
                int latestViewCount = (article.getViewCount() == null ? 0 : article.getViewCount()) + 1;
                article.setViewCount(latestViewCount);
            }
        }

        article.setPassword(null);
        if (StringUtils.hasText(article.getVideoUrl())) {
            article.setVideoUrl(CryptoUtil.encrypt(article.getVideoUrl()));
        }

        ArticleVO articleVO = buildArticleVO(article, false);

        // 直接使用数据库中存储的摘要
        if (StringUtils.hasText(article.getSummary())) {
            articleVO.setSummary(article.getSummary());
        }

        // ========== 付费墙截断逻辑 ==========
        applyPaywall(article, articleVO);

        return PoetryResult.success(articleVO);
    }

    private boolean shouldIncrementViewCount(Article article, boolean incrementViewCount) {
        if (!incrementViewCount) {
            return false;
        }

        Integer currentUserId = PoetryUtil.getUserId();
        return currentUserId == null || !currentUserId.equals(article.getUserId());
    }

    /**
     * 应用付费墙截断逻辑
     * <p>
     * 混合模式：优先查找 <!--paywall--> 标记截断；未找到则按 freePercent 百分比在段落边界截断。
     * 已登录且已付费/文章作者/管理员 不截断。
     * </p>
     */
    private void applyPaywall(Article article, ArticleVO articleVO) {
        Integer payType = article.getPayType();
        // payType 为 null 或 0 表示免费文章
        if (payType == null || payType == 0) {
            articleVO.setPaywalled(false);
            return;
        }

        // 填充付费信息
        articleVO.setPayType(payType);
        articleVO.setPayAmount(article.getPayAmount());
        articleVO.setFreePercent(article.getFreePercent());
        articleVO.setPaidCount(paymentService.getPaidCount(article.getId()));

        // 检查当前用户是否有权查看全文
        Integer currentUserId = PoetryUtil.getUserId();
        if (currentUserId != null) {
            // 文章作者可以看到全文
            if (currentUserId.equals(article.getUserId())) {
                articleVO.setPaywalled(false);
                return;
            }
            // 管理员可以看到全文
            if (PoetryUtil.isBoss()) {
                articleVO.setPaywalled(false);
                return;
            }
            // 已付费可以看到全文
            if (paymentService.hasPaid(currentUserId, article.getId())) {
                articleVO.setPaywalled(false);
                return;
            }
            // 会员专属文章，检查会员状态
            if (payType == 2 && paymentService.isMember(currentUserId)) {
                articleVO.setPaywalled(false);
                return;
            }
        }

        // 需要截断
        articleVO.setPaywalled(true);
        String content = articleVO.getArticleContent();
        if (content == null || content.isEmpty()) {
            return;
        }

        // 优先使用 <!--paywall--> 标记
        String paywallMarker = "<!--paywall-->";
        int markerIndex = content.indexOf(paywallMarker);
        if (markerIndex >= 0) {
            articleVO.setArticleContent(content.substring(0, markerIndex));
            return;
        }

        // 回退到百分比截断（在段落边界）
        int freePercent = article.getFreePercent() != null ? article.getFreePercent() : 30;
        int targetLength = (int) (content.length() * freePercent / 100.0);

        // 在目标长度附近寻找段落边界（\n\n 或 \n）
        int cutPoint = targetLength;
        // 向前搜索最近的段落分隔
        int paragraphBreak = content.lastIndexOf("\n\n", targetLength);
        if (paragraphBreak > targetLength * 0.5) {
            cutPoint = paragraphBreak;
        } else {
            // 退而求其次，找单独换行
            int lineBreak = content.lastIndexOf("\n", targetLength);
            if (lineBreak > targetLength * 0.5) {
                cutPoint = lineBreak;
            }
        }

        articleVO.setArticleContent(content.substring(0, cutPoint));
    }

    @Override
    public PoetryResult<Page> listAdminArticle(BaseRequestVO baseRequestVO, Boolean isBoss) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.select(Article.class, a -> !a.getColumn().equals("article_content"));
        if (!isBoss) {
            lambdaQuery.eq(Article::getUserId, PoetryUtil.getUserId());
        } else {
            if (baseRequestVO.getUserId() != null) {
                lambdaQuery.eq(Article::getUserId, baseRequestVO.getUserId());
            }
        }
        if (StringUtils.hasText(baseRequestVO.getSearchKey())) {
            lambdaQuery.like(Article::getArticleTitle, baseRequestVO.getSearchKey());
        }
        if (baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus()) {
            lambdaQuery.eq(Article::getRecommendStatus, PoetryEnum.STATUS_ENABLE.getCode());
        }

        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        }

        if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        Page<Article> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.orderByDesc(Article::getCreateTime).page(page);

        List<Article> records = page.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> collect = records.stream().map(article -> {
                article.setPassword(null);
                ArticleVO articleVO = buildArticleVO(article, true);
                return articleVO;
            }).collect(Collectors.toList());
            baseRequestVO.setRecords(collect);
        }
        return PoetryResult.success(baseRequestVO);
    }

    @Override
    public PoetryResult<ArticleVO> getArticleByIdForUser(Integer id) {
        // 先检查当前用户是否创建了这篇文章
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id).eq(Article::getUserId, PoetryUtil.getUserId());
        Article article = lambdaQuery.one();

        // 如果当前用户不是文章创建者，检查是否为管理员访问API创建的文章
        if (article == null) {
            // 检查当前用户是否有权限(Boss角色)
            if (PoetryUtil.isBoss()) {
                // 尝试直接通过ID获取文章
                article = lambdaQuery().eq(Article::getId, id).one();
                if (article != null) {
                    ArticleVO articleVO = buildArticleVO(article, true);
                    return PoetryResult.success(articleVO);
                }
            }
            return PoetryResult.fail("文章不存在！");
        }

        ArticleVO articleVO = buildArticleVO(article, true);
        return PoetryResult.success(articleVO);
    }

    @Override
    public PoetryResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        // 使用Redis缓存替换PoetryCache
        Map<Integer, List<Article>> cachedResult = cacheService.getCachedSortArticleList();
        if (cachedResult != null) {
            Map<Integer, Integer> latestViewCountMap = loadLatestViewCountMap(cachedResult);
            // 转换为ArticleVO
            Map<Integer, List<ArticleVO>> result = new HashMap<>();
            for (Map.Entry<?, List<Article>> entry : cachedResult.entrySet()) {
                // 安全地转换键类型，处理String到Integer的转换
                Integer sortId = convertToInteger(entry.getKey());
                if (sortId != null) {
                    List<ArticleVO> articleVOList = entry.getValue().stream().map(article -> {
                        applyLatestViewCount(article, latestViewCountMap);
                        ArticleVO vo = buildArticleVO(article, false);
                        if (StringUtils.hasText(article.getSummary())) {
                            vo.setSummary(article.getSummary());
                        }
                        vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                        vo.setPassword(null);
                        vo.setVideoUrl(null);
                        return vo;
                    }).collect(Collectors.toList());
                    result.put(sortId, articleVOList);
                } else {
                    log.warn("无法转换分类ID: {}, 类型: {}", entry.getKey(),
                            entry.getKey() != null ? entry.getKey().getClass().getSimpleName() : "null");
                }
            }
            return PoetryResult.success(result);
        }

        // 缓存未命中，使用写锁更新缓存
        return lockManager.executeWithWriteLock("cache:" + CommonConst.SORT_ARTICLE_LIST, () -> {
            // 双重检查锁定
            Map<?, List<Article>> finalCachedResult = cacheService.getCachedSortArticleList();
            if (finalCachedResult == null) {
                Map<Integer, List<Article>> articleMap = new HashMap<>();
                Map<Integer, List<ArticleVO>> resultMap = new HashMap<>();

                List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper).select(Sort::getId).list();
                for (Sort sort : sorts) {
                    LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery()
                            .eq(Article::getSortId, sort.getId())
                            .eq(Article::getViewStatus, true) // 添加对可见文章的过滤
                            .orderByDesc(Article::getCreateTime)
                            .last("limit 6");
                    List<Article> articleList = lambdaQuery.list();
                    if (CollectionUtils.isEmpty(articleList)) {
                        continue;
                    }

                    // 处理文章内容用于缓存
                    List<Article> processedArticles = articleList.stream().map(article -> {
                        Article processedArticle = new Article();
                        BeanUtils.copyProperties(article, processedArticle);
                        // 如果内容太长，截取用于缓存
                        if (processedArticle.getArticleContent().length() > CommonConst.SUMMARY) {
                            processedArticle.setArticleContent(
                                    processedArticle.getArticleContent().substring(0, CommonConst.SUMMARY)
                                            .replace("`", "").replace("#", "").replace(">", "") + "...");
                        }
                        return processedArticle;
                    }).collect(Collectors.toList());

                    List<ArticleVO> articleVOList = processedArticles.stream().map(article -> {
                        ArticleVO vo = buildArticleVO(article, false);

                        // 直接使用数据库中存储的摘要
                        if (StringUtils.hasText(article.getSummary())) {
                            vo.setSummary(article.getSummary());
                        }

                        vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                        vo.setPassword(null);
                        vo.setVideoUrl(null);
                        return vo;
                    }).collect(Collectors.toList());

                    articleMap.put(sort.getId(), processedArticles);
                    resultMap.put(sort.getId(), articleVOList);
                }

                // 缓存到Redis
                cacheService.cacheSortArticleList(articleMap);
                return PoetryResult.success(resultMap);
            } else {
                Map<Integer, Integer> latestViewCountMap = loadLatestViewCountMap(finalCachedResult);
                // 转换缓存结果为ArticleVO
                Map<Integer, List<ArticleVO>> resultMap = new HashMap<>();
                for (Map.Entry<?, List<Article>> entry : finalCachedResult.entrySet()) {
                    // 安全地转换键类型，处理String到Integer的转换
                    Integer sortId = convertToInteger(entry.getKey());
                    if (sortId != null) {
                        List<ArticleVO> articleVOList = entry.getValue().stream().map(article -> {
                            applyLatestViewCount(article, latestViewCountMap);
                            ArticleVO vo = buildArticleVO(article, false);
                            if (StringUtils.hasText(article.getSummary())) {
                                vo.setSummary(article.getSummary());
                            }
                            vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                            vo.setPassword(null);
                            vo.setVideoUrl(null);
                            return vo;
                        }).collect(Collectors.toList());
                        resultMap.put(sortId, articleVOList);
                    } else {
                        log.warn("无法转换分类ID: {}, 类型: {}", entry.getKey(),
                                entry.getKey() != null ? entry.getKey().getClass().getSimpleName() : "null");
                    }
                }
                return PoetryResult.success(resultMap);
            }
        });
    }

    private Map<Integer, Integer> loadLatestViewCountMap(Map<?, List<Article>> articleMap) {
        if (articleMap == null || articleMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Integer> articleIds = articleMap.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(Article::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (articleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return lambdaQuery()
                .select(Article::getId, Article::getViewCount)
                .in(Article::getId, articleIds)
                .list()
                .stream()
                .collect(Collectors.toMap(
                        Article::getId,
                        article -> article.getViewCount() == null ? 0 : article.getViewCount(),
                        (left, right) -> right));
    }

    private void applyLatestViewCount(Article article, Map<Integer, Integer> latestViewCountMap) {
        if (article == null || latestViewCountMap == null || latestViewCountMap.isEmpty()) {
            return;
        }

        Integer latestViewCount = latestViewCountMap.get(article.getId());
        if (latestViewCount != null) {
            article.setViewCount(latestViewCount);
        }
    }

    private ArticleVO buildArticleVO(Article article, Boolean isAdmin) {
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        if (!isAdmin) {
            if (!StringUtils.hasText(articleVO.getArticleCover())) {
                articleVO.setArticleCover(PoetryUtil.getRandomCover(articleVO.getId().toString()));
            }
        }

        // 生成文章访问链接
        try {
            String siteUrl = mailUtil.getSiteUrl();
            if (StringUtils.hasText(siteUrl)) {
                articleVO.setArticleUrl(siteUrl + ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug()));
            }
        } catch (Exception e) {
        }

        // 并行获取关联数据（用户信息、评论数、分类信息）
        try (var scope = StructuredTaskScope.open()) {
            // Fork 用户信息查询
            Subtask<User> userTask = scope.fork(() -> commonQuery.getUser(articleVO.getUserId()));

            // Fork 评论数查询（仅当评论开启时）
            Subtask<Integer> commentCountTask = articleVO.getCommentStatus()
                    ? scope.fork(() -> commonQuery.getCommentCount(articleVO.getId(),
                            CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode()))
                    : null;

            // Fork 分类信息查询
            Subtask<List<Sort>> sortInfoTask = scope.fork(() -> commonQuery.getSortInfo());

            // 等待所有查询完成
            scope.join();

            // 处理用户信息
            if (userTask.state() == Subtask.State.SUCCESS) {
                User user = userTask.get();
                if (user != null && StringUtils.hasText(user.getUsername())) {
                    articleVO.setUsername(user.getUsername());
                } else if (!isAdmin) {
                    articleVO.setUsername(PoetryUtil.getRandomName(articleVO.getUserId().toString()));
                }
            } else if (!isAdmin) {
                articleVO.setUsername(PoetryUtil.getRandomName(articleVO.getUserId().toString()));
            }

            // 处理评论数
            if (commentCountTask != null && commentCountTask.state() == Subtask.State.SUCCESS) {
                articleVO.setCommentCount(commentCountTask.get());
            } else {
                articleVO.setCommentCount(0);
            }

            // 处理分类和标签信息
            if (sortInfoTask.state() == Subtask.State.SUCCESS) {
                List<Sort> sortInfo = sortInfoTask.get();
                if (!CollectionUtils.isEmpty(sortInfo)) {
                    for (Sort s : sortInfo) {
                        if (s.getId().intValue() == articleVO.getSortId().intValue()) {
                            Sort sort = new Sort();
                            BeanUtils.copyProperties(s, sort);
                            sort.setLabels(null);
                            articleVO.setSort(sort);
                            // 同时设置sortName字段，方便API直接使用
                            articleVO.setSortName(s.getSortName());
                            if (!CollectionUtils.isEmpty(s.getLabels())) {
                                for (int j = 0; j < s.getLabels().size(); j++) {
                                    Label l = s.getLabels().get(j);
                                    if (l.getId().intValue() == articleVO.getLabelId().intValue()) {
                                        Label label = new Label();
                                        BeanUtils.copyProperties(l, label);
                                        articleVO.setLabel(label);
                                        // 同时设置labelName字段，方便API直接使用
                                        articleVO.setLabelName(l.getLabelName());
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("构建文章VO时并行查询被中断，使用降级数据", e);
            // 降级处理：使用默认值
            if (!isAdmin) {
                articleVO.setUsername(PoetryUtil.getRandomName(articleVO.getUserId().toString()));
            }
            articleVO.setCommentCount(0);
        } catch (Exception e) {
            log.error("构建文章VO时并行查询失败，使用降级数据", e);
            // 降级处理
            if (!isAdmin) {
                articleVO.setUsername(PoetryUtil.getRandomName(articleVO.getUserId().toString()));
            }
            articleVO.setCommentCount(0);
        }

        return articleVO;
    }

    /**
     * 为Python端提供的摘要生成API
     */
    @Override
    public PoetryResult<String> generateSummary(String content, Integer maxLength) {
        try {
            if (!StringUtils.hasText(content)) {
                return PoetryResult.fail("内容不能为空");
            }

            int targetLength = (maxLength != null && maxLength > 0) ? maxLength : 150;
            String summary = SmartSummaryGenerator.generateAdvancedSummary(content, targetLength);

            if (StringUtils.hasText(summary)) {
                return PoetryResult.success(summary);
            } else {
                return PoetryResult.fail("摘要生成失败");
            }

        } catch (Exception e) {
            log.error("摘要生成失败", e);
            return PoetryResult.fail("摘要生成异常: " + e.getMessage());
        }
    }

    /**
     * 安全地将对象转换为Integer类型
     * 处理Redis序列化导致的类型转换问题
     * 
     * @param obj 要转换的对象
     * @return 转换后的Integer，转换失败返回null
     */
    private Integer convertToInteger(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Integer) {
            return (Integer) obj;
        }

        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }

        if (obj instanceof String) {
            try {
                return Integer.valueOf((String) obj);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Integer: {}", obj);
                return null;
            }
        }

        log.warn("不支持的类型转换: {} -> Integer", obj.getClass().getSimpleName());
        return null;
    }

    /**
     * 智能摘要生成（仅使用AI，失败后交给展示层使用文章开头兜底）
     */
    private String generateArticleSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }

        try {
            String aiSummary = llmTranslationService.generateSummary(content, 150);
            if (StringUtils.hasText(aiSummary)) {
                log.info("使用AI生成文章摘要成功，长度: {}", aiSummary.length());
                return aiSummary;
            }
        } catch (Exception e) {
            log.warn("AI摘要生成失败，不再回退到本地摘录算法: {}", e.getMessage());
        }

        return "";
    }

    @Override
    public PoetryResult<List<ArticleVO>> getArticlesByLikesTop() {
        try {
            // 查询可见的文章，获取所有需要计算热度的字段
            LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery()
                    .select(Article::getId, Article::getUserId, Article::getSortId, Article::getLabelId,
                            Article::getArticleCover, Article::getArticleTitle, Article::getArticleSlug, Article::getArticleContent,
                            Article::getSummary, Article::getViewCount,
                            Article::getCommentStatus, Article::getRecommendStatus, Article::getViewStatus,
                            Article::getCreateTime, Article::getUpdateTime, Article::getVideoUrl)
                    .eq(Article::getViewStatus, true) // 只查询可见的文章
                    .orderByDesc(Article::getCreateTime); // 先按时间排序，后面会重新排序

            List<Article> articles = lambdaQuery.list();

            if (CollectionUtils.isEmpty(articles)) {
                return PoetryResult.success(new ArrayList<>());
            }

            // 使用并行流转换为ArticleVO（利用虚拟线程的优势）
            List<ArticleVO> articleVOList = articles.parallelStream().map(article -> {
                // 如果内容太长，截取用于显示
                if (StringUtils.hasText(article.getArticleContent())
                        && article.getArticleContent().length() > CommonConst.SUMMARY) {
                    article.setArticleContent(article.getArticleContent().substring(0, CommonConst.SUMMARY)
                            .replace("`", "").replace("#", "").replace(">", "") + "...");
                }

                ArticleVO articleVO = buildArticleVO(article, false);

                // 使用数据库中存储的摘要
                if (StringUtils.hasText(article.getSummary())) {
                    articleVO.setSummary(article.getSummary());
                }

                // 设置视频标识
                articleVO.setHasVideo(StringUtils.hasText(article.getVideoUrl()));

                // 清空敏感信息
                articleVO.setPassword(null);
                articleVO.setVideoUrl(null);

                return articleVO;
            }).collect(Collectors.toList());

            // 计算每篇文章的热度分数并排序
            articleVOList = articleVOList.stream()
                    .sorted((a1, a2) -> {
                        double score1 = calculateHotScore(a1);
                        double score2 = calculateHotScore(a2);
                        return Double.compare(score2, score1); // 降序排列
                    })
                    .limit(10) // 限制返回前10篇
                    .collect(Collectors.toList());

            log.info("获取热门文章成功，返回{}篇文章", articleVOList.size());
            return PoetryResult.success(articleVOList);

        } catch (Exception e) {
            log.error("获取热门文章失败", e);
            return PoetryResult.fail("获取热门文章失败: " + e.getMessage());
        }
    }

    /**
     * 计算文章热度分数的智能算法
     * 综合考虑浏览量、评论数、发布时间等因素
     * 
     * @param articleVO 文章VO对象
     * @return 热度分数（越高越热门）
     */
    private double calculateHotScore(ArticleVO articleVO) {
        // 基础数据
        int viewCount = articleVO.getViewCount() != null ? articleVO.getViewCount() : 0;
        int commentCount = articleVO.getCommentCount() != null ? articleVO.getCommentCount() : 0;
        LocalDateTime createTime = articleVO.getCreateTime();

        // 1. 浏览量权重 (60%) - 标准化处理，权重提升
        double viewScore = Math.log10(Math.max(viewCount, 1)) * 60;

        // 2. 评论数权重 (30%) - 评论表示深度参与，权重提升
        double commentScore = Math.log10(Math.max(commentCount, 1)) * 30 * 6; // 评论权重更高

        // 3. 时间衰减因子 (10%) - 新文章有加成，但不会完全压倒旧的热门文章
        double timeScore = 0;
        if (createTime != null) {
            long daysSinceCreation = java.time.Duration.between(createTime, LocalDateTime.now()).toDays();

            // 使用指数衰减，但设置一个底线
            if (daysSinceCreation <= 7) {
                // 一周内的文章有时间加成
                timeScore = 10 * Math.exp(-daysSinceCreation / 7.0);
            } else if (daysSinceCreation <= 30) {
                // 一个月内的文章保持一定分数
                timeScore = 5 * Math.exp(-(daysSinceCreation - 7) / 23.0);
            } else {
                // 超过一个月的文章，时间分数较低但不为0
                timeScore = 1;
            }
        }

        // 4. 互动比率加成 - 评论率高的文章额外加分
        double engagementBonus = 0;
        if (viewCount > 0) {
            double commentRate = (double) commentCount / viewCount;

            // 评论率超过0.5%的文章加分
            if (commentRate > 0.005) {
                engagementBonus += Math.min(commentRate * 2000, 20); // 最多加20分
            }
        }

        // 5. 推荐文章额外加分
        double recommendBonus = 0;
        if (Boolean.TRUE.equals(articleVO.getRecommendStatus())) {
            recommendBonus = 25; // 被推荐的文章额外加25分
        }

        // 计算最终热度分数
        double finalScore = viewScore + commentScore + timeScore + engagementBonus + recommendBonus;

        // 调试日志

        return finalScore;
    }

    /**
     * 异步更新文章（快速响应版本）
     */
    @Override
    public PoetryResult<String> updateArticleAsync(ArticleVO articleVO) {
        // 调用重载方法，使用默认参数
        return updateArticleAsync(articleVO, false, null);
    }

    /**
     * 异步更新文章（快速响应版本，支持翻译参数）
     */
    @Override
    public PoetryResult<String> updateArticleAsync(ArticleVO articleVO, boolean skipAiTranslation,
            Map<String, String> pendingTranslation) {
        return updateArticleAsync(articleVO, skipAiTranslation, pendingTranslation, null, null, null, null);
    }

    @Override
    public PoetryResult<String> updateArticleAsync(ArticleVO articleVO, boolean skipAiTranslation,
            Map<String, String> pendingTranslation, Integer actorUserId, String actorUsername,
            Consumer<Integer> successCallback, Consumer<Integer> failureCallback) {
        // 基础验证
        if (articleVO.getId() == null) {
            return PoetryResult.fail("文章ID不能为空！");
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus()
                && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }

        Integer userId = actorUserId != null ? actorUserId : resolveAsyncActorUserId(articleVO);
        if (userId == null) {
            return PoetryResult.fail("无法确定文章作者，请重新登录后再试");
        }
        Article existingArticle = getById(articleVO.getId());
        Integer previousSortId = existingArticle == null ? null : existingArticle.getSortId();
        Integer previousLabelId = existingArticle == null ? null : existingArticle.getLabelId();
        String previousArticleSlug = existingArticle == null ? null : existingArticle.getArticleSlug();
        String articleSlug = resolveArticleSlugForSave(articleVO.getArticleSlug(), articleVO.getId());
        articleVO.setArticleSlug(articleSlug);

        // 在主线程中获取用户信息，避免异步线程中无法访问RequestContext
        String currentUsername = StringUtils.hasText(actorUsername)
                ? actorUsername
                : resolveAsyncActorUsername(articleVO);
        final String finalUsername = currentUsername;
        String newTaskId = generateAsyncTaskId("article_update");
        String taskId = registerOrReuseAsyncTask(
                buildAsyncUpdateTaskKey(articleVO.getId(), userId),
                newTaskId);
        if (!newTaskId.equals(taskId)) {
            log.info("复用进行中的异步更新任务，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());
            return PoetryResult.success(taskId);
        }

        // 初始化更新状态
        ArticleSaveStatus initialStatus = new ArticleSaveStatus(taskId, "processing", "正在更新文章...", articleVO.getId());
        initialStatus.setStage("queued");
        initialStatus.setSeoPushRequired(Boolean.TRUE.equals(articleVO.getViewStatus())
                && Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine()));
        ARTICLE_SAVE_STATUS.put(taskId, initialStatus);
        log.info("初始化异步更新任务，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());

        // 使用虚拟线程异步执行更新
        Thread.ofVirtual().name("article-update-" + taskId).start(() -> {
            Integer updatedArticleId = null;
            try {
                articleVO.setUserId(userId);
                articleVO.setUpdateBy(finalUsername);

                // 构建更新条件
                LambdaUpdateChainWrapper<Article> updateChainWrapper = lambdaUpdate()
                        .eq(Article::getId, articleVO.getId())
                        .eq(Article::getUserId, userId)
                        .set(Article::getLabelId, articleVO.getLabelId())
                        .set(Article::getSortId, articleVO.getSortId())
                        .set(Article::getArticleTitle, articleVO.getArticleTitle())
                        .set(Article::getArticleSlug, articleSlug)
                        .set(Article::getUpdateBy, finalUsername)
                        .set(Article::getUpdateTime, LocalDateTime.now())
                        .set(Article::getVideoUrl,
                                StringUtils.hasText(articleVO.getVideoUrl()) ? articleVO.getVideoUrl() : null)
                        .set(Article::getArticleContent, articleVO.getArticleContent())
                        .set(Article::getPayType, articleVO.getPayType())
                        .set(Article::getPayAmount, articleVO.getPayAmount())
                        .set(Article::getFreePercent, articleVO.getFreePercent());

                if (articleVO.getArticleCover() != null) {
                    updateChainWrapper.set(Article::getArticleCover, articleVO.getArticleCover());
                }
                if (articleVO.getCommentStatus() != null) {
                    updateChainWrapper.set(Article::getCommentStatus, articleVO.getCommentStatus());
                }
                if (articleVO.getRecommendStatus() != null) {
                    updateChainWrapper.set(Article::getRecommendStatus, articleVO.getRecommendStatus());
                }
                if (articleVO.getViewStatus() != null && !articleVO.getViewStatus()
                        && StringUtils.hasText(articleVO.getPassword())) {
                    updateChainWrapper.set(Article::getPassword, articleVO.getPassword());
                    updateChainWrapper.set(StringUtils.hasText(articleVO.getTips()), Article::getTips,
                            articleVO.getTips());
                }
                if (Boolean.TRUE.equals(articleVO.getViewStatus())) {
                    updateChainWrapper.set(Article::getPassword, null);
                    updateChainWrapper.set(Article::getTips, null);
                }
                if (articleVO.getViewStatus() != null) {
                    updateChainWrapper.set(Article::getViewStatus, articleVO.getViewStatus());
                }
                if (articleVO.getSubmitToSearchEngine() != null) {
                    updateChainWrapper.set(Article::getSubmitToSearchEngine, articleVO.getSubmitToSearchEngine());
                }
                if (!shouldAutoGenerateSummary(articleVO)) {
                    updateChainWrapper.set(Article::getSummary, normalizeManualSummary(articleVO));
                }

                // 更新状态：正在更新数据库
                updateSaveStatus(taskId, "processing", "正在更新数据库...");
                updateTranslationStage(taskId, "db_saved", "pending", "正在更新数据库...", 0, false);

                // ========== 步骤1：使用短事务方法更新文章 ==========
                boolean updateResult = updateArticleInTransaction(updateChainWrapper);
                if (!updateResult) {
                    log.error("数据库更新失败，任务ID: {}", taskId);
                    updateSaveStatus(taskId, "failed", "数据库更新失败");
                    notifyAsyncSaveFailure(failureCallback, null);
                    return;
                }
                updatedArticleId = articleVO.getId();
                log.info("文章更新成功，任务ID: {}, 文章ID: {}，短事务已提交", taskId, articleVO.getId());
                String articleReadyMessage = "文章已更新";
                updateSaveStatus(taskId, "processing", articleReadyMessage + "，正在准备后续任务...", articleVO.getId());

                // ========== 步骤2：处理翻译（AI / 手动翻译 / 跳过）==========
                boolean autoSummary = shouldAutoGenerateSummary(articleVO);
                AsyncTranslationOutcome translationOutcome = processAsyncTranslation(
                        taskId,
                        articleVO.getId(),
                        articleVO.getArticleTitle(),
                        articleVO.getArticleContent(),
                        skipAiTranslation,
                        pendingTranslation,
                        articleReadyMessage,
                        autoSummary,
                        articleVO.getSummary());

                // ========== 步骤4：更新多语言摘要（基于原文+翻译）==========
                if (!autoSummary) {
                    updateTaskStage(taskId, "manual_summary_saved", translationOutcome.translationStatus(), "manual_saved",
                            "手动摘要已保存", null, false);
                    applySummaryOutcome(taskId, manualSummaryResult());
                } else if (StringUtils.hasText(articleVO.getArticleContent())) {
                    SummaryService.SummaryTaskResult summaryOutcome;
                    if (summaryService.isAutoSummaryEnabled()) {
                        String summaryMessage = buildSummaryProgressMessage(
                                articleReadyMessage,
                                translationOutcome.translationStatus());
                        updateSaveStatus(taskId, "processing", summaryMessage, articleVO.getId());
                        updateTaskStage(taskId, "generating_summary", translationOutcome.translationStatus(), "pending",
                                summaryMessage, null, false);
                        summaryOutcome = summaryService.updateSummary(
                                articleVO.getId(), articleVO.getArticleContent(), buildSummaryProgressListener(taskId));
                    } else {
                        summaryOutcome = summaryService.updateSummary(articleVO.getId(), articleVO.getArticleContent(), null);
                    }
                    applySummaryOutcome(taskId, summaryOutcome);
                }

                // 清除缓存
                try {
                    cacheService.evictSortArticleList();
                } catch (Exception e) {
                    log.error("清除缓存失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }

                // ========== 步骤5：发布文章更新事件 ==========
                try {
                    eventPublisher.publishEvent(new ArticleSavedEvent(articleVO.getId(), articleVO.getSortId(), articleVO.getLabelId(),
                            previousSortId, previousLabelId, taskId, articleVO.getViewStatus(), "UPDATE",
                            articleVO.getSubmitToSearchEngine(), previousArticleSlug));
                } catch (Exception e) {
                    log.error("发布文章更新事件失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }

                // 最终状态（SEO推送将在预渲染完成后自动执行）
                String finalTaskStatus = translationOutcome.failed() || isSummaryTaskFailed(taskId) ? "partial_success" : "success";
                String finalMessage = buildFinalAsyncMessage("更新", finalTaskStatus,
                        translationOutcome.translationStatus(), getSummaryStatus(taskId));
                updateSaveStatus(taskId, finalTaskStatus, finalMessage, articleVO.getId());
                updateTaskStage(taskId, "complete", translationOutcome.translationStatus(), getSummaryStatus(taskId), finalMessage, null,
                        false);
                emitTaskEvent(taskId, "complete", buildFinalTaskPayload(
                        taskId,
                        finalTaskStatus,
                        articleVO.getId(),
                        finalMessage,
                        translationOutcome.translationStatus(),
                        getSummaryStatus(taskId),
                        getSummaryMessage(taskId),
                        initialStatus.getSeoPushRequired(),
                        initialStatus.getSeoPushStatus(),
                        initialStatus.getSeoPushMessage()));
                log.info("异步文章更新流程全部完成，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());
                notifyAsyncSaveSuccess(successCallback, articleVO.getId());

            } catch (Exception e) {
                log.error("文章更新失败，任务ID: {}, 文章ID: {}, 错误: {}", taskId, articleVO.getId(), e.getMessage(), e);
                updateSaveStatus(taskId, "failed", "更新失败：" + e.getMessage());
                updateTranslationStage(taskId, "failed", "failed", "更新失败：" + e.getMessage(), null, false);
                emitTaskEvent(taskId, "error", Map.of(
                        "status", "failed",
                        "message", "更新失败：" + e.getMessage(),
                        "retryable", false));
                notifyAsyncSaveFailure(failureCallback, updatedArticleId);
            } finally {
                releaseAsyncTaskGuard(taskId);
            }
        });

        return PoetryResult.success(taskId);
    }

    private Integer resolveAsyncActorUserId(ArticleVO articleVO) {
        Integer userId = PoetryUtil.getUserId();
        if (userId != null) {
            return userId;
        }
        return articleVO != null ? articleVO.getUserId() : null;
    }

    private void notifyAsyncSaveSuccess(Consumer<Integer> callback, Integer articleId) {
        notifyAsyncSaveCallback(callback, articleId, "成功");
    }

    private void notifyAsyncSaveFailure(Consumer<Integer> callback, Integer articleId) {
        notifyAsyncSaveCallback(callback, articleId, "失败");
    }

    private void notifyAsyncSaveCallback(Consumer<Integer> callback, Integer articleId, String statusText) {
        if (callback == null) {
            return;
        }
        try {
            callback.accept(articleId);
        } catch (Exception e) {
            log.error("异步文章保存{}回调执行失败，文章ID: {}", statusText, articleId, e);
        }
    }

    private String resolveAsyncActorUsername(ArticleVO articleVO) {
        try {
            String username = PoetryUtil.getUsername();
            if (StringUtils.hasText(username)) {
                return username;
            }
        } catch (Exception e) {
            log.warn("无法获取当前用户名，尝试使用请求载荷中的操作者信息: {}", e.getMessage());
        }

        if (articleVO != null && StringUtils.hasText(articleVO.getUpdateBy())) {
            return articleVO.getUpdateBy();
        }

        return "System";
    }

    private String resolveArticleSlugForSave(String rawSlug, Integer currentArticleId) {
        String slug = ArticleUrlUtil.normalizeSlug(rawSlug);
        if (!StringUtils.hasText(slug)) {
            return null;
        }

        if (!ArticleUrlUtil.isValidSlug(slug)) {
            throw new IllegalArgumentException("URL别名仅支持小写英文、数字和短横线，长度1-160，且不能是纯数字");
        }

        Article existing = lambdaQuery()
                .select(Article::getId)
                .eq(Article::getArticleSlug, slug)
                .ne(currentArticleId != null, Article::getId, currentArticleId)
                .last("limit 1")
                .one();
        if (existing != null) {
            throw new IllegalArgumentException("URL别名已被其他文章使用");
        }
        return slug;
    }

    /**
     * 智能截取包含搜索关键词的内容片段
     * 
     * @param content   原始内容
     * @param keyword   搜索关键词
     * @param maxLength 最大长度
     * @return 包含关键词的内容片段
     */
    private String getContentSnippetWithKeyword(String content, String keyword, int maxLength) {
        if (content == null || keyword == null || content.length() <= maxLength) {
            // 如果内容不长，直接返回并添加省略号（如果需要）
            return content != null && content.length() > maxLength ? content.substring(0, maxLength) + "..." : content;
        }

        // 查找关键词位置（忽略大小写）
        int keywordIndex = content.toLowerCase().indexOf(keyword.toLowerCase());

        if (keywordIndex == -1) {
            // 如果没找到关键词，返回开头部分
            return content.substring(0, maxLength) + "...";
        }

        // 计算截取的起始位置，尽量让关键词居中
        int keywordLength = keyword.length();
        int halfLength = (maxLength - keywordLength) / 2;

        int startIndex = Math.max(0, keywordIndex - halfLength);
        int endIndex = Math.min(content.length(), startIndex + maxLength);

        // 如果从中间开始，调整起始位置确保不超过最大长度
        if (endIndex - startIndex < maxLength && startIndex > 0) {
            startIndex = Math.max(0, endIndex - maxLength);
        }

        String snippet = content.substring(startIndex, endIndex);

        // 添加省略号
        if (startIndex > 0) {
            snippet = "..." + snippet;
        }
        if (endIndex < content.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }

    @Override
    public ArticleVO getTranslationContent(Integer id, String searchKey, String language) {
        try {
            // 获取原文章
            Article article = this.getById(id);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 如果没有指定语言，尝试获取第一个可用的翻译语言
            if (language == null || language.trim().isEmpty()) {
                List<String> availableLanguages = translationService.getArticleAvailableLanguages(id);
                if (availableLanguages.isEmpty()) {
                    throw new RuntimeException("该文章没有可用的翻译");
                }
                language = availableLanguages.get(0);
            }

            // 获取翻译内容
            Map<String, String> translation = translationService.getArticleTranslation(id, language);
            if (translation == null || translation.isEmpty()) {
                throw new RuntimeException("翻译内容不存在");
            }

            String translatedTitle = translation.get("title");
            String translatedContent = translation.get("content");

            if (translatedTitle == null || translatedContent == null) {
                throw new RuntimeException("翻译内容不完整");
            }

            // 对翻译内容进行搜索高亮处理和智能截取
            if (searchKey != null && !searchKey.trim().isEmpty()) {
                // 检测是否为正则表达式搜索
                boolean isRegexSearch = searchKey.startsWith("/") && searchKey.endsWith("/") && searchKey.length() > 2;
                String actualSearchText = isRegexSearch ? searchKey.substring(1, searchKey.length() - 1) : searchKey;

                // 使用高亮标签进行处理
                String highlightStart = "<span class='search-highlight' style='color: var(--lightGreen); font-weight: bold;'>";
                String highlightEnd = "</span>";

                if (isRegexSearch) {
                    translatedTitle = StringUtil.highlightTextWithRegex(translatedTitle, actualSearchText,
                            highlightStart, highlightEnd);
                    // 对翻译内容进行智能截取和正则高亮
                    translatedContent = getContentSnippetWithKeyword(translatedContent, actualSearchText, 80);
                    translatedContent = StringUtil.highlightTextWithRegex(translatedContent, actualSearchText,
                            highlightStart, highlightEnd);
                } else {
                    translatedTitle = StringUtil.highlightText(translatedTitle, searchKey, highlightStart,
                            highlightEnd);
                    // 对翻译内容进行智能截取和高亮
                    translatedContent = getContentSnippetWithKeyword(translatedContent, searchKey, 80);
                    translatedContent = StringUtil.highlightText(translatedContent, searchKey, highlightStart,
                            highlightEnd);
                }
            } else {
                // 如果没有搜索关键词，也要进行内容截取（显示前80个字符）
                translatedContent = truncateContent(translatedContent, 80);
            }

            // 构建返回的 ArticleVO
            ArticleVO articleVO = new ArticleVO();
            articleVO.setId(article.getId());
            articleVO.setArticleTitle(translatedTitle);
            articleVO.setArticleContent(translatedContent);
            if (StringUtils.hasText(translation.get("summary"))) {
                articleVO.setSummary(translation.get("summary"));
            }

            return articleVO;
        } catch (Exception e) {
            throw new RuntimeException("获取翻译内容失败: " + e.getMessage());
        }
    }

    /**
     * 截取内容到指定长度
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }

        // 移除HTML标签进行长度计算
        String plainText = content.replaceAll("<[^>]*>", "");
        if (plainText.length() <= maxLength) {
            return content;
        }

        // 截取到指定长度，尽量在句号、感叹号、问号处截断
        String truncated = plainText.substring(0, maxLength);
        int lastSentenceEnd = Math.max(
                Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('！')),
                Math.max(truncated.lastIndexOf('？'), truncated.lastIndexOf('.')));

        if (lastSentenceEnd > maxLength * 0.7) {
            truncated = plainText.substring(0, lastSentenceEnd + 1);
        }

        return truncated + "...";
    }

    /**
     * 在独立事务中保存文章原文（短事务）
     * 
     * @param articleVO 文章VO对象
     * @return 保存成功返回文章ID，失败返回null
     */
    @Transactional(rollbackFor = Exception.class)
    private Integer saveArticleInTransaction(ArticleVO articleVO) {
        // 验证数据合法性
        if (!StringUtils.hasText(articleVO.getArticleTitle()) || articleVO.getArticleTitle().trim().isEmpty()) {
            log.error("保存文章失败：文章标题为空");
            return null;
        }

        Article article = new Article();

        if (articleVO.getArticleCover() != null) {
            article.setArticleCover(articleVO.getArticleCover());
        }
        if (StringUtils.hasText(articleVO.getVideoUrl())) {
            article.setVideoUrl(articleVO.getVideoUrl());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus()
                && StringUtils.hasText(articleVO.getPassword())) {
            article.setPassword(articleVO.getPassword());
            article.setTips(articleVO.getTips());
        }

        article.setViewStatus(articleVO.getViewStatus());
        article.setCommentStatus(articleVO.getCommentStatus());
        article.setRecommendStatus(articleVO.getRecommendStatus());
        article.setSubmitToSearchEngine(articleVO.getSubmitToSearchEngine());
        article.setArticleTitle(articleVO.getArticleTitle());
        String articleSlug = resolveArticleSlugForSave(articleVO.getArticleSlug(), null);
        article.setArticleSlug(articleSlug);
        articleVO.setArticleSlug(articleSlug);
        article.setArticleContent(articleVO.getArticleContent());
        article.setSummary(shouldAutoGenerateSummary(articleVO) ? "" : normalizeManualSummary(articleVO));
        article.setSortId(articleVO.getSortId());
        article.setLabelId(articleVO.getLabelId());

        // 设置用户ID
        Integer userId = null;
        if (articleVO.getUserId() != null) {
            userId = articleVO.getUserId();
        } else {
            userId = PoetryUtil.getUserId();
            if (userId == null) {
                log.error("保存文章失败：无法获取用户ID");
                return null;
            }
        }
        article.setUserId(userId);

        // 设置付费字段
        article.setPayType(articleVO.getPayType() != null ? articleVO.getPayType() : 0);
        article.setPayAmount(articleVO.getPayAmount());
        article.setFreePercent(articleVO.getFreePercent() != null ? articleVO.getFreePercent() : 30);

        // 保存到数据库
        boolean result = save(article);
        if (!result) {
            log.error("数据库保存失败");
            return null;
        }

        return article.getId();
    }

    /**
     * 在新事务中保存翻译结果
     * 
     * @param articleId 文章ID
     * @param title     翻译后的标题
     * @param content   翻译后的内容
     * @param language  目标语言
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    private void saveTranslationInNewTransaction(Integer articleId, String title, String content, String language) {
        saveTranslationInNewTransaction(articleId, title, content, language, null, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    private void saveTranslationInNewTransaction(Integer articleId, String title, String content, String language,
            String summary, boolean summaryProvided) {
        try {
            if (summaryProvided) {
                translationService.saveTranslationResult(articleId, title, content, language, summary);
            } else {
                translationService.saveTranslationResult(articleId, title, content, language);
            }
        } catch (Exception e) {
            log.error("翻译结果保存失败，文章ID: {}, 语言: {}", articleId, language, e);
            throw e; // 抛出异常以触发事务回滚
        }
    }

    /**
     * 在独立事务中更新文章（短事务）
     * 
     * @param updateChainWrapper 更新链式包装器
     * @return 更新成功返回true，失败返回false
     */
    @Transactional(rollbackFor = Exception.class)
    private boolean updateArticleInTransaction(LambdaUpdateChainWrapper<Article> updateChainWrapper) {
        boolean result = updateChainWrapper.update();
        return result;
    }

}
