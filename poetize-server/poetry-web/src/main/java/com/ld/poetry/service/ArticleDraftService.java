package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.ArticleDraft;
import com.ld.poetry.vo.ArticleDraftCollaboratorVO;
import com.ld.poetry.vo.ArticleDraftDetailVO;
import com.ld.poetry.vo.ArticleDraftInviteAcceptVO;
import com.ld.poetry.vo.ArticleDraftInviteVO;
import com.ld.poetry.vo.ArticleDraftPublishRequest;
import com.ld.poetry.vo.ArticleDraftSnapshotRequest;
import com.ld.poetry.vo.ArticleDraftSummaryVO;
import com.ld.poetry.vo.BaseRequestVO;

import java.util.List;

public interface ArticleDraftService extends IService<ArticleDraft> {

    PoetryResult<ArticleDraftDetailVO> createDraft();

    PoetryResult<ArticleDraftDetailVO> createOrGetRevisionDraft(Integer articleId);

    PoetryResult<ArticleDraftDetailVO> getDraftDetail(String draftId);

    PoetryResult<Page<ArticleDraftSummaryVO>> listDrafts(BaseRequestVO requestVO);

    PoetryResult<List<ArticleDraftCollaboratorVO>> listCollaboratorOptions();

    PoetryResult<List<ArticleDraftCollaboratorVO>> replaceCollaborators(String draftId, List<Integer> collaboratorIds);

    PoetryResult<Boolean> saveSnapshot(String draftId, ArticleDraftSnapshotRequest request);

    PoetryResult<Boolean> deleteDraft(String draftId);

    PoetryResult<ArticleDraftInviteVO> createInvite(String draftId);

    PoetryResult<Boolean> revokeInvite(String draftId);

    PoetryResult<ArticleDraftInviteAcceptVO> acceptInvite(String draftId, String inviteToken);

    PoetryResult<Integer> publishDraft(String draftId, ArticleDraftPublishRequest request);

    PoetryResult<String> publishDraftAsync(String draftId, ArticleDraftPublishRequest request);

    boolean hasDraftAccess(Integer userId, boolean isBoss, String draftId);
}
