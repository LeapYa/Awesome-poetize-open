package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.ArticleDraftCollaboratorMapper;
import com.ld.poetry.dao.ArticleDraftMapper;
import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleDraft;
import com.ld.poetry.entity.ArticleDraftCollaborator;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.ArticleDraftService;
import com.ld.poetry.utils.RedisUtil;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.ArticleDraftCollaboratorVO;
import com.ld.poetry.vo.ArticleDraftDetailVO;
import com.ld.poetry.vo.ArticleDraftInviteAcceptVO;
import com.ld.poetry.vo.ArticleDraftInviteVO;
import com.ld.poetry.vo.ArticleDraftPublishRequest;
import com.ld.poetry.vo.ArticleDraftSnapshotRequest;
import com.ld.poetry.vo.ArticleDraftSummaryVO;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ArticleDraftServiceImpl extends ServiceImpl<ArticleDraftMapper, ArticleDraft> implements ArticleDraftService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PUBLISHING = "PUBLISHING";
    private static final String DRAFT_TYPE_CREATE = "CREATE";
    private static final String DRAFT_TYPE_REVISION = "REVISION";
    private static final String DEFAULT_DRAFT_TITLE = "未命名草稿";
    private static final String DRAFT_INVITE_KEY_PREFIX = "article:draft:invite:";
    private static final String DRAFT_ACTIVE_INVITE_KEY_PREFIX = "article:draft:activeInvite:";
    private static final long DRAFT_INVITE_EXPIRE_SECONDS = 24 * 60 * 60;

    @Autowired
    private ArticleDraftCollaboratorMapper collaboratorMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<ArticleDraftDetailVO> createDraft() {
        User currentUser = PoetryUtil.getCurrentUserRequired();
        if (currentUser.getUserType() == null || currentUser.getUserType() > 1) {
            return PoetryResult.fail("仅后台管理员可以创建文章草稿");
        }

        ArticleDraft draft = new ArticleDraft();
        draft.setId(UUID.randomUUID().toString().replace("-", ""));
        draft.setOwnerUserId(currentUser.getId());
        draft.setDraftType(DRAFT_TYPE_CREATE);
        draft.setArticleId(null);
        draft.setStatus(STATUS_ACTIVE);
        draft.setTitleCache(DEFAULT_DRAFT_TITLE);
        draft.setCrdtSnapshotBase64("");
        draft.setLastEditorId(currentUser.getId());
        draft.setDeleted(false);
        save(draft);
        return getDraftDetail(draft.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<ArticleDraftDetailVO> createOrGetRevisionDraft(Integer articleId) {
        if (articleId == null) {
            return PoetryResult.fail("文章ID不能为空");
        }

        User currentUser = PoetryUtil.getCurrentUserRequired();
        Article article = articleService.getById(articleId);
        if (article == null || Boolean.TRUE.equals(article.getDeleted())) {
            return PoetryResult.fail("文章不存在");
        }
        if (!PoetryUtil.isBoss() && !currentUser.getId().equals(article.getUserId())) {
            return PoetryResult.fail("无权限为该文章创建修订草稿");
        }

        ArticleDraft existingDraft = lambdaQuery()
                .eq(ArticleDraft::getDraftType, DRAFT_TYPE_REVISION)
                .eq(ArticleDraft::getArticleId, articleId)
                .one();
        if (existingDraft != null) {
            return PoetryResult.success(buildDetail(existingDraft));
        }

        ArticleDraft revisionDraft = new ArticleDraft();
        revisionDraft.setId(UUID.randomUUID().toString().replace("-", ""));
        revisionDraft.setOwnerUserId(article.getUserId());
        revisionDraft.setDraftType(DRAFT_TYPE_REVISION);
        revisionDraft.setArticleId(articleId);
        revisionDraft.setStatus(STATUS_ACTIVE);
        revisionDraft.setTitleCache(StringUtils.hasText(article.getArticleTitle()) ? article.getArticleTitle() : DEFAULT_DRAFT_TITLE);
        revisionDraft.setCrdtSnapshotBase64("");
        revisionDraft.setLastEditorId(currentUser.getId());
        revisionDraft.setDeleted(false);

        try {
            save(revisionDraft);
        } catch (DuplicateKeyException e) {
            ArticleDraft concurrentDraft = lambdaQuery()
                    .eq(ArticleDraft::getDraftType, DRAFT_TYPE_REVISION)
                    .eq(ArticleDraft::getArticleId, articleId)
                    .one();
            if (concurrentDraft != null) {
                return PoetryResult.success(buildDetail(concurrentDraft));
            }
            return PoetryResult.fail("创建修订草稿失败");
        }
        return PoetryResult.success(buildDetail(revisionDraft));
    }

    @Override
    public PoetryResult<ArticleDraftDetailVO> getDraftDetail(String draftId) {
        if (!StringUtils.hasText(draftId)) {
            return PoetryResult.fail("草稿ID不能为空");
        }

        User currentUser = PoetryUtil.getCurrentUserRequired();
        ArticleDraft draft = getById(draftId);
        if (draft == null || Boolean.TRUE.equals(draft.getDeleted())) {
            return PoetryResult.fail("草稿不存在");
        }
        if (!hasDraftAccess(currentUser.getId(), PoetryUtil.isBoss(), draftId)) {
            return PoetryResult.fail("无权限访问该草稿");
        }
        return PoetryResult.success(buildDetail(draft));
    }

    @Override
    public PoetryResult<Page<ArticleDraftSummaryVO>> listDrafts(BaseRequestVO requestVO) {
        User currentUser = PoetryUtil.getCurrentUserRequired();
        boolean isBoss = PoetryUtil.isBoss();

        List<ArticleDraft> drafts;
        if (isBoss) {
            drafts = lambdaQuery().orderByDesc(ArticleDraft::getUpdateTime).list();
        } else {
            Set<String> accessibleDraftIds = new LinkedHashSet<>();
            accessibleDraftIds.addAll(lambdaQuery()
                    .eq(ArticleDraft::getOwnerUserId, currentUser.getId())
                    .list()
                    .stream()
                    .map(ArticleDraft::getId)
                    .collect(Collectors.toSet()));
            accessibleDraftIds.addAll(collaboratorMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDraftCollaborator>()
                            .eq(ArticleDraftCollaborator::getUserId, currentUser.getId()))
                    .stream()
                    .map(ArticleDraftCollaborator::getDraftId)
                    .collect(Collectors.toSet()));

            if (accessibleDraftIds.isEmpty()) {
                Page<ArticleDraftSummaryVO> emptyPage = new Page<>(requestVO.getCurrent(), requestVO.getSize());
                emptyPage.setRecords(Collections.emptyList());
                emptyPage.setTotal(0);
                return PoetryResult.success(emptyPage);
            }

            drafts = lambdaQuery().in(ArticleDraft::getId, accessibleDraftIds).list();
        }

        String searchKey = requestVO.getSearchKey();
        if (StringUtils.hasText(searchKey)) {
            drafts = drafts.stream()
                    .filter(item -> StringUtils.hasText(item.getTitleCache()) && item.getTitleCache().contains(searchKey))
                    .collect(Collectors.toList());
        }

        drafts.sort(Comparator.comparing(ArticleDraft::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        List<ArticleDraftSummaryVO> summaries = drafts.stream().map(this::buildSummary).collect(Collectors.toList());

        long current = requestVO.getCurrent() <= 0 ? 1 : requestVO.getCurrent();
        long size = requestVO.getSize() <= 0 ? 10 : requestVO.getSize();
        int fromIndex = (int) Math.min((current - 1) * size, summaries.size());
        int toIndex = (int) Math.min(fromIndex + size, summaries.size());

        Page<ArticleDraftSummaryVO> page = new Page<>(current, size);
        page.setTotal(summaries.size());
        page.setRecords(summaries.subList(fromIndex, toIndex));
        return PoetryResult.success(page);
    }

    @Override
    public PoetryResult<List<ArticleDraftCollaboratorVO>> listCollaboratorOptions() {
        List<User> users = userMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .in(User::getUserType, 0, 1)
                .eq(User::getUserStatus, true)
                .orderByAsc(User::getUserType)
                .orderByAsc(User::getUsername));
        return PoetryResult.success(users.stream().map(this::toCollaboratorVO).collect(Collectors.toList()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<List<ArticleDraftCollaboratorVO>> replaceCollaborators(String draftId, List<Integer> collaboratorIds) {
        ArticleDraft draft = requireManageDraft(draftId);
        if (draft == null) {
            return PoetryResult.fail("草稿不存在或无权限管理");
        }

        collaboratorMapper.physicalDeleteByDraftId(draftId);

        Set<Integer> sanitizedIds = collaboratorIds == null ? Collections.emptySet() : collaboratorIds.stream()
                .filter(id -> id != null && !id.equals(draft.getOwnerUserId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!sanitizedIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(sanitizedIds);
            Map<Integer, User> allowedUsers = users.stream()
                    .filter(user -> user.getUserType() != null && user.getUserType() <= 1)
                    .collect(Collectors.toMap(User::getId, user -> user));

            for (Integer userId : sanitizedIds) {
                if (!allowedUsers.containsKey(userId)) {
                    continue;
                }
                ArticleDraftCollaborator collaborator = new ArticleDraftCollaborator();
                collaborator.setDraftId(draftId);
                collaborator.setUserId(userId);
                collaborator.setDeleted(false);
                collaboratorMapper.insert(collaborator);
            }
        }

        return PoetryResult.success(loadCollaborators(draftId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<Boolean> saveSnapshot(String draftId, ArticleDraftSnapshotRequest request) {
        if (request == null) {
            return PoetryResult.fail("快照数据不能为空");
        }

        User currentUser = PoetryUtil.getCurrentUserRequired();
        boolean isBoss = PoetryUtil.isBoss();
        if (!hasDraftAccess(currentUser.getId(), isBoss, draftId)) {
            return PoetryResult.fail("无权限编辑该草稿");
        }

        ArticleDraft draft = getById(draftId);
        if (draft == null || Boolean.TRUE.equals(draft.getDeleted())) {
            return PoetryResult.fail("草稿不存在");
        }
        if (!STATUS_ACTIVE.equals(draft.getStatus())) {
            return PoetryResult.fail("草稿当前不可编辑");
        }

        draft.setLastEditorId(currentUser.getId());
        if (StringUtils.hasText(request.getTitleCache())) {
            draft.setTitleCache(request.getTitleCache());
        }
        if (request.getSnapshotBase64() != null) {
            draft.setCrdtSnapshotBase64(request.getSnapshotBase64());
        }
        updateById(draft);
        return PoetryResult.success(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<Boolean> deleteDraft(String draftId) {
        ArticleDraft draft = requireManageDraft(draftId);
        if (draft == null) {
            return PoetryResult.fail("草稿不存在或无权限删除");
        }

        baseMapper.physicalDeleteById(draftId);
        collaboratorMapper.physicalDeleteByDraftId(draftId);
        clearDraftInvite(draftId);
        return PoetryResult.success(true);
    }

    @Override
    public PoetryResult<ArticleDraftInviteVO> createInvite(String draftId) {
        ArticleDraft draft = requireManageDraft(draftId);
        if (draft == null) {
            return PoetryResult.fail("草稿不存在或无权限邀请协作者");
        }
        clearDraftInvite(draftId);
        String token = UUID.randomUUID().toString().replace("-", "");
        redisUtil.set(DRAFT_INVITE_KEY_PREFIX + token, draftId, DRAFT_INVITE_EXPIRE_SECONDS);
        redisUtil.set(DRAFT_ACTIVE_INVITE_KEY_PREFIX + draftId, token, DRAFT_INVITE_EXPIRE_SECONDS);
        ArticleDraftInviteVO inviteVO = new ArticleDraftInviteVO();
        inviteVO.setDraftId(draftId);
        inviteVO.setInviteToken(token);
        inviteVO.setExpireSeconds(DRAFT_INVITE_EXPIRE_SECONDS);
        return PoetryResult.success(inviteVO);
    }

    @Override
    public PoetryResult<Boolean> revokeInvite(String draftId) {
        ArticleDraft draft = requireManageDraft(draftId);
        if (draft == null) {
            return PoetryResult.fail("草稿不存在或无权限撤销邀请");
        }
        clearDraftInvite(draftId);
        return PoetryResult.success(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<ArticleDraftInviteAcceptVO> acceptInvite(String draftId, String inviteToken) {
        if (!StringUtils.hasText(draftId) || !StringUtils.hasText(inviteToken)) {
            return PoetryResult.fail("邀请参数不能为空");
        }
        ArticleDraft draft = getById(draftId);
        if (draft == null || Boolean.TRUE.equals(draft.getDeleted())) {
            return PoetryResult.fail("草稿不存在");
        }
        Object cachedDraftId = redisUtil.get(DRAFT_INVITE_KEY_PREFIX + inviteToken);
        if (cachedDraftId == null || !draftId.equals(String.valueOf(cachedDraftId))) {
            return PoetryResult.fail("邀请链接已失效");
        }

        User currentUser = PoetryUtil.getCurrentUserRequired();
        if (currentUser.getUserType() == null || currentUser.getUserType() > 1) {
            return PoetryResult.fail("仅后台管理员可以加入协作");
        }

        ArticleDraftInviteAcceptVO acceptVO = new ArticleDraftInviteAcceptVO();

        if (currentUser.getId().equals(draft.getOwnerUserId()) || PoetryUtil.isBoss()) {
            acceptVO.setJoined(false);
            acceptVO.setReason("owner");
            return PoetryResult.success(acceptVO);
        }

        Long exists = collaboratorMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDraftCollaborator>()
                .eq(ArticleDraftCollaborator::getDraftId, draftId)
                .eq(ArticleDraftCollaborator::getUserId, currentUser.getId()));
        if (exists == null || exists == 0) {
            collaboratorMapper.physicalDeleteByDraftIdAndUserId(draftId, currentUser.getId());
            ArticleDraftCollaborator collaborator = new ArticleDraftCollaborator();
            collaborator.setDraftId(draftId);
            collaborator.setUserId(currentUser.getId());
            collaborator.setDeleted(false);
            collaboratorMapper.insert(collaborator);
            acceptVO.setJoined(true);
            acceptVO.setReason("joined");
            return PoetryResult.success(acceptVO);
        }
        acceptVO.setJoined(false);
        acceptVO.setReason("already_joined");
        return PoetryResult.success(acceptVO);
    }

    private void clearDraftInvite(String draftId) {
        Object activeToken = redisUtil.get(DRAFT_ACTIVE_INVITE_KEY_PREFIX + draftId);
        if (activeToken != null) {
            redisUtil.del(DRAFT_INVITE_KEY_PREFIX + activeToken);
        }
        redisUtil.del(DRAFT_ACTIVE_INVITE_KEY_PREFIX + draftId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<Integer> publishDraft(String draftId, ArticleDraftPublishRequest request) {
        ArticleDraft draft = requireManageDraft(draftId);
        if (draft == null) {
            return PoetryResult.fail("草稿不存在或无权限发布");
        }
        if (!STATUS_ACTIVE.equals(draft.getStatus())) {
            return PoetryResult.fail("草稿当前不可发布");
        }

        ArticleVO articleVO = normalizePublishArticle(request, draft);
        if (articleVO == null) {
            return PoetryResult.fail("发布参数不完整");
        }

        Map<String, String> pendingTranslation = buildPendingTranslation(articleVO);
        boolean skipAiTranslation = Boolean.TRUE.equals(articleVO.getSkipAiTranslation());

        PoetryResult<?> result = DRAFT_TYPE_REVISION.equals(draft.getDraftType())
                ? articleService.updateArticle(articleVO, skipAiTranslation, pendingTranslation, draft.getOwnerUserId())
                : articleService.saveArticle(articleVO, skipAiTranslation, pendingTranslation);
        if (result.getCode() != 200) {
            return PoetryResult.fail(result.getMessage());
        }

        Integer articleId = DRAFT_TYPE_REVISION.equals(draft.getDraftType())
                ? draft.getArticleId()
                : result.getData() instanceof Integer ? (Integer) result.getData() : articleVO.getId();
        cleanupPublishedDraft(draftId);
        return PoetryResult.success(articleId);
    }

    @Override
    public PoetryResult<String> publishDraftAsync(String draftId, ArticleDraftPublishRequest request) {
        ArticleDraft draft = requireManageDraft(draftId);
        if (draft == null) {
            return PoetryResult.fail("草稿不存在或无权限发布");
        }
        if (!STATUS_ACTIVE.equals(draft.getStatus())) {
            return PoetryResult.fail("草稿当前不可发布");
        }

        ArticleVO articleVO = normalizePublishArticle(request, draft);
        if (articleVO == null) {
            return PoetryResult.fail("发布参数不完整");
        }

        User currentUser = PoetryUtil.getCurrentUserRequired();
        String actorUsername = currentUser != null && StringUtils.hasText(currentUser.getUsername())
                ? currentUser.getUsername()
                : "System";
        draft.setStatus(STATUS_PUBLISHING);
        draft.setLastEditorId(PoetryUtil.getUserId());
        updateById(draft);

        PoetryResult<String> result;
        try {
            Map<String, String> pendingTranslation = buildPendingTranslation(articleVO);
            boolean skipAiTranslation = Boolean.TRUE.equals(articleVO.getSkipAiTranslation());
            result = DRAFT_TYPE_REVISION.equals(draft.getDraftType())
                    ? articleService.updateArticleAsync(articleVO,
                    skipAiTranslation,
                    pendingTranslation,
                    draft.getOwnerUserId(),
                    actorUsername,
                    articleId -> cleanupPublishedDraft(draftId),
                    articleId -> handleAsyncDraftPublishFailure(draftId, articleId))
                    : articleService.saveArticleAsync(articleVO,
                    skipAiTranslation,
                    pendingTranslation,
                    draft.getOwnerUserId(),
                    actorUsername,
                    articleId -> cleanupPublishedDraft(draftId),
                    articleId -> handleAsyncDraftPublishFailure(draftId, articleId));
        } catch (Exception e) {
            handleAsyncDraftPublishFailure(draftId, null);
            return PoetryResult.fail("异步发布启动失败：" + e.getMessage());
        }
        if (result.getCode() != 200) {
            handleAsyncDraftPublishFailure(draftId, null);
            return result;
        }
        return result;
    }

    @Override
    public boolean hasDraftAccess(Integer userId, boolean isBoss, String draftId) {
        if (userId == null || !StringUtils.hasText(draftId)) {
            return false;
        }
        ArticleDraft draft = getById(draftId);
        if (draft == null || Boolean.TRUE.equals(draft.getDeleted())) {
            return false;
        }
        if (isBoss || userId.equals(draft.getOwnerUserId())) {
            return true;
        }
        return collaboratorMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDraftCollaborator>()
                .eq(ArticleDraftCollaborator::getDraftId, draftId)
                .eq(ArticleDraftCollaborator::getUserId, userId)) > 0;
    }

    private ArticleDraft requireManageDraft(String draftId) {
        User currentUser = PoetryUtil.getCurrentUserRequired();
        ArticleDraft draft = getById(draftId);
        if (draft == null || Boolean.TRUE.equals(draft.getDeleted())) {
            return null;
        }
        if (PoetryUtil.isBoss() || currentUser.getId().equals(draft.getOwnerUserId())) {
            return draft;
        }
        return null;
    }

    private ArticleVO normalizePublishArticle(ArticleDraftPublishRequest request, ArticleDraft draft) {
        if (request == null || request.getArticle() == null) {
            return null;
        }
        ArticleVO articleVO = request.getArticle();
        if (DRAFT_TYPE_REVISION.equals(draft.getDraftType())) {
            if (draft.getArticleId() == null) {
                return null;
            }
            articleVO.setId(draft.getArticleId());
        } else {
            articleVO.setId(null);
        }
        articleVO.setUserId(draft.getOwnerUserId());
        return articleVO;
    }

    private void cleanupPublishedDraft(String draftId) {
        if (!StringUtils.hasText(draftId)) {
            return;
        }
        baseMapper.physicalDeleteById(draftId);
        collaboratorMapper.physicalDeleteByDraftId(draftId);
        clearDraftInvite(draftId);
    }

    private void handleAsyncDraftPublishFailure(String draftId, Integer articleId) {
        if (articleId != null) {
            cleanupPublishedDraft(draftId);
            return;
        }
        ArticleDraft draft = getById(draftId);
        if (draft == null || Boolean.TRUE.equals(draft.getDeleted())) {
            return;
        }
        draft.setStatus(STATUS_ACTIVE);
        updateById(draft);
    }

    private Map<String, String> buildPendingTranslation(ArticleVO articleVO) {
        if (!StringUtils.hasText(articleVO.getPendingTranslationTitle())
                || !StringUtils.hasText(articleVO.getPendingTranslationContent())
                || !StringUtils.hasText(articleVO.getPendingTranslationLanguage())) {
            return null;
        }
        Map<String, String> pendingTranslation = new LinkedHashMap<>();
        pendingTranslation.put("title", articleVO.getPendingTranslationTitle());
        pendingTranslation.put("content", articleVO.getPendingTranslationContent());
        pendingTranslation.put("language", articleVO.getPendingTranslationLanguage());
        return pendingTranslation;
    }

    private ArticleDraftDetailVO buildDetail(ArticleDraft draft) {
        ArticleDraftDetailVO detailVO = new ArticleDraftDetailVO();
        BeanUtils.copyProperties(draft, detailVO);
        Map<Integer, User> users = loadUsers(buildRelatedUserIds(draft, draft.getId()));
        User owner = users.get(draft.getOwnerUserId());
        User lastEditor = users.get(draft.getLastEditorId());
        Article sourceArticle = loadSourceArticle(draft);
        detailVO.setOwnerUsername(owner != null ? owner.getUsername() : null);
        detailVO.setLastEditorUsername(lastEditor != null ? lastEditor.getUsername() : null);
        detailVO.setSourceArticleTitle(sourceArticle != null ? sourceArticle.getArticleTitle() : null);
        detailVO.setSourceArticle(buildSourceArticleVO(sourceArticle));
        detailVO.setCollaborators(loadCollaborators(draft.getId(), users));
        return detailVO;
    }

    private ArticleDraftSummaryVO buildSummary(ArticleDraft draft) {
        ArticleDraftSummaryVO summaryVO = new ArticleDraftSummaryVO();
        BeanUtils.copyProperties(draft, summaryVO);
        Map<Integer, User> users = loadUsers(buildRelatedUserIds(draft, draft.getId()));
        User owner = users.get(draft.getOwnerUserId());
        User lastEditor = users.get(draft.getLastEditorId());
        summaryVO.setOwnerUsername(owner != null ? owner.getUsername() : null);
        summaryVO.setLastEditorUsername(lastEditor != null ? lastEditor.getUsername() : null);
        summaryVO.setSourceArticleTitle(loadSourceArticleTitle(draft));
        summaryVO.setCollaborators(loadCollaborators(draft.getId(), users));
        return summaryVO;
    }

    private Article loadSourceArticle(ArticleDraft draft) {
        if (draft == null || !DRAFT_TYPE_REVISION.equals(draft.getDraftType()) || draft.getArticleId() == null) {
            return null;
        }
        return articleService.getById(draft.getArticleId());
    }

    private String loadSourceArticleTitle(ArticleDraft draft) {
        Article sourceArticle = loadSourceArticle(draft);
        return sourceArticle != null ? sourceArticle.getArticleTitle() : null;
    }

    private ArticleVO buildSourceArticleVO(Article sourceArticle) {
        if (sourceArticle == null) {
            return null;
        }
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(sourceArticle, articleVO);
        return articleVO;
    }

    private Set<Integer> buildRelatedUserIds(ArticleDraft draft, String draftId) {
        Set<Integer> userIds = new LinkedHashSet<>();
        if (draft.getOwnerUserId() != null) {
            userIds.add(draft.getOwnerUserId());
        }
        if (draft.getLastEditorId() != null) {
            userIds.add(draft.getLastEditorId());
        }
        userIds.addAll(collaboratorMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDraftCollaborator>()
                        .eq(ArticleDraftCollaborator::getDraftId, draftId))
                .stream()
                .map(ArticleDraftCollaborator::getUserId)
                .collect(Collectors.toSet()));
        return userIds;
    }

    private List<ArticleDraftCollaboratorVO> loadCollaborators(String draftId) {
        return loadCollaborators(draftId, loadUsers(collaboratorMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDraftCollaborator>()
                        .eq(ArticleDraftCollaborator::getDraftId, draftId))
                .stream()
                .map(ArticleDraftCollaborator::getUserId)
                .collect(Collectors.toSet())));
    }

    private List<ArticleDraftCollaboratorVO> loadCollaborators(String draftId, Map<Integer, User> users) {
        List<ArticleDraftCollaborator> collaborators = collaboratorMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDraftCollaborator>()
                        .eq(ArticleDraftCollaborator::getDraftId, draftId));
        List<ArticleDraftCollaboratorVO> result = new ArrayList<>();
        for (ArticleDraftCollaborator collaborator : collaborators) {
            User user = users.get(collaborator.getUserId());
            if (user == null) {
                continue;
            }
            result.add(toCollaboratorVO(user));
        }
        return result;
    }

    private Map<Integer, User> loadUsers(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private ArticleDraftCollaboratorVO toCollaboratorVO(User user) {
        ArticleDraftCollaboratorVO collaboratorVO = new ArticleDraftCollaboratorVO();
        collaboratorVO.setUserId(user.getId());
        collaboratorVO.setUsername(user.getUsername());
        collaboratorVO.setAvatar(user.getAvatar());
        collaboratorVO.setUserType(user.getUserType());
        return collaboratorVO;
    }
}
