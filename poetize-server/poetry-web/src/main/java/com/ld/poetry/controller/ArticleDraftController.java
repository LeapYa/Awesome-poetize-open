package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.ArticleDraftService;
import com.ld.poetry.vo.ArticleDraftCollaboratorVO;
import com.ld.poetry.vo.ArticleDraftDetailVO;
import com.ld.poetry.vo.ArticleDraftInviteAcceptVO;
import com.ld.poetry.vo.ArticleDraftInviteVO;
import com.ld.poetry.vo.ArticleDraftPublishRequest;
import com.ld.poetry.vo.ArticleDraftSnapshotRequest;
import com.ld.poetry.vo.ArticleDraftSummaryVO;
import com.ld.poetry.vo.BaseRequestVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/articleDraft")
public class ArticleDraftController {

    @Autowired
    private ArticleDraftService articleDraftService;

    @PostMapping("/create")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_CREATE", targetType = "ARTICLE_DRAFT", summary = "创建文章草稿")
    public PoetryResult<ArticleDraftDetailVO> create() {
        return articleDraftService.createDraft();
    }

    @PostMapping("/revision/{articleId}")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_REVISION", targetType = "ARTICLE", targetIdParam = "articleId", summary = "创建文章修订草稿")
    public PoetryResult<ArticleDraftDetailVO> revision(@PathVariable Integer articleId) {
        return articleDraftService.createOrGetRevisionDraft(articleId);
    }

    @GetMapping("/{draftId}")
    @LoginCheck(1)
    public PoetryResult<ArticleDraftDetailVO> detail(@PathVariable String draftId) {
        return articleDraftService.getDraftDetail(draftId);
    }

    @PostMapping("/list")
    @LoginCheck(1)
    public PoetryResult<Page<ArticleDraftSummaryVO>> list(@RequestBody BaseRequestVO requestVO) {
        return articleDraftService.listDrafts(requestVO);
    }

    @GetMapping("/collaborators/options")
    @LoginCheck(1)
    public PoetryResult<List<ArticleDraftCollaboratorVO>> collaboratorOptions() {
        return articleDraftService.listCollaboratorOptions();
    }

    @PutMapping("/{draftId}/collaborators")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_COLLABORATORS_REPLACE", targetType = "ARTICLE_DRAFT", targetIdParam = "draftId", summary = "更新草稿协作者")
    public PoetryResult<List<ArticleDraftCollaboratorVO>> replaceCollaborators(@PathVariable String draftId,
                                                                              @RequestBody ReplaceCollaboratorsRequest request) {
        return articleDraftService.replaceCollaborators(draftId, request == null ? null : request.getCollaboratorIds());
    }

    @PostMapping("/{draftId}/snapshot")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_SNAPSHOT_SAVE", targetType = "ARTICLE_DRAFT", targetIdParam = "draftId", summary = "保存草稿快照")
    public PoetryResult<Boolean> saveSnapshot(@PathVariable String draftId,
                                              @RequestBody ArticleDraftSnapshotRequest request) {
        return articleDraftService.saveSnapshot(draftId, request);
    }

    @PostMapping("/{draftId}/invite")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_INVITE_CREATE", targetType = "ARTICLE_DRAFT", targetIdParam = "draftId", summary = "创建草稿邀请")
    public PoetryResult<ArticleDraftInviteVO> createInvite(@PathVariable String draftId) {
        return articleDraftService.createInvite(draftId);
    }

    @DeleteMapping("/{draftId}/invite")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_INVITE_REVOKE", targetType = "ARTICLE_DRAFT", targetIdParam = "draftId", summary = "撤销草稿邀请")
    public PoetryResult<Boolean> revokeInvite(@PathVariable String draftId) {
        return articleDraftService.revokeInvite(draftId);
    }

    @PostMapping("/{draftId}/acceptInvite")
    @LoginCheck(1)
    public PoetryResult<ArticleDraftInviteAcceptVO> acceptInvite(@PathVariable String draftId,
                                                                 @RequestBody AcceptInviteRequest request) {
        return articleDraftService.acceptInvite(draftId, request == null ? null : request.getInviteToken());
    }

    @PostMapping("/{draftId}/publish")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_PUBLISH", targetType = "ARTICLE_DRAFT", targetIdParam = "draftId", summary = "发布文章草稿")
    public PoetryResult<Integer> publish(@PathVariable String draftId,
                                         @RequestBody ArticleDraftPublishRequest request) {
        return articleDraftService.publishDraft(draftId, request);
    }

    @PostMapping("/{draftId}/publishAsync")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_PUBLISH_ASYNC", targetType = "ARTICLE_DRAFT", targetIdParam = "draftId", summary = "异步发布文章草稿")
    public PoetryResult<String> publishAsync(@PathVariable String draftId,
                                             @RequestBody ArticleDraftPublishRequest request) {
        return articleDraftService.publishDraftAsync(draftId, request);
    }

    @DeleteMapping("/{draftId}")
    @LoginCheck(1)
    @AuditLog(action = "ARTICLE_DRAFT_DELETE", targetType = "ARTICLE_DRAFT", targetIdParam = "draftId", summary = "删除文章草稿")
    public PoetryResult<Boolean> delete(@PathVariable String draftId) {
        return articleDraftService.deleteDraft(draftId);
    }

    @Data
    public static class ReplaceCollaboratorsRequest {
        private List<Integer> collaboratorIds;
    }

    @Data
    public static class AcceptInviteRequest {
        private String inviteToken;
    }
}
