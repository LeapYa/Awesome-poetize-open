<template>
  <div>
    <div class="section-header">
      <el-tag effect="dark" class="my-tag draft-inline-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -3px;">
          <path d="M0 0h1024v1024H0V0z" fill="#202425" opacity=".01"></path>
          <path
            d="M682.666667 204.8h238.933333a34.133333 34.133333 0 0 1 34.133333 34.133333v648.533334a68.266667 68.266667 0 0 1-68.266666 68.266666h-204.8V204.8z"
            fill="#FFAA44"></path>
          <path
            d="M68.266667 921.6a34.133333 34.133333 0 0 0 34.133333 34.133333h785.066667a68.266667 68.266667 0 0 1-68.266667-68.266666V102.4a34.133333 34.133333 0 0 0-34.133333-34.133333H102.4a34.133333 34.133333 0 0 0-34.133333 34.133333v819.2z"
            fill="#11AA66"></path>
          <path
            d="M238.933333 307.2a34.133333 34.133333 0 0 0 0 68.266667h136.533334a34.133333 34.133333 0 1 0 0-68.266667H238.933333z m0 204.8a34.133333 34.133333 0 1 0 0 68.266667h409.6a34.133333 34.133333 0 1 0 0-68.266667H238.933333z m0 204.8a34.133333 34.133333 0 1 0 0 68.266667h204.8a34.133333 34.133333 0 1 0 0-68.266667H238.933333z"
            fill="#FFFFFF"></path>
        </svg>
        文章信息
        <template v-if="isDraftMode">
          <span class="draft-inline-spacer"></span>
          <span class="draft-inline-divider"></span>
          <span class="draft-inline-chip" :data-type="draftStatusType">{{ compactDraftStatusText }}</span>
          <span v-if="draftId || draftLastSyncedAt" class="draft-inline-divider"></span>
          <span v-if="draftId" class="draft-inline-meta">草稿ID：{{ shortDraftId }}</span>
          <span v-if="draftLastSyncedAt" class="draft-inline-meta">{{ draftLastSyncedAt }}</span>
          <span class="draft-inline-divider"></span>
          <el-popover
            placement="bottom-end"
            width="260"
            trigger="click"
            popper-class="draft-collaborator-popper"
          >
            <div class="draft-collaborator-panel">
              <div class="draft-collaborator-title">协作者</div>
              <div v-if="draftOnlineUsers.length" class="draft-online-users">
                在线：{{ draftOnlineUsers.map(item => item.username).join('、') }}
              </div>
              <div v-if="draftEditingUsersText.length" class="draft-editing-users">
                正在编辑：{{ draftEditingUsersText.join('、') }}
              </div>
              <div v-if="canManageCurrentDraft" class="draft-invite-actions">
                <el-button
                  size="mini"
                  type="text"
                  class="draft-invite-link-button"
                  @click="copyDraftInviteLink"
                >
                  复制邀请链接
                </el-button>
                <el-button
                  size="mini"
                  type="text"
                  class="draft-invite-link-button"
                  @click="revokeDraftInviteLink"
                >
                  撤销链接
                </el-button>
              </div>
              <div class="draft-invite-tip">
                {{ canManageCurrentDraft ? '邀请链接 24 小时内有效，重新生成会覆盖旧链接' : '仅作者或站长可管理协作者与邀请链接' }}
              </div>
              <el-select
                v-if="canManageCurrentDraft"
                v-model="draftCollaboratorIds"
                multiple
                collapse-tags
                filterable
                placeholder="选择协作者"
                size="mini"
                class="draft-collaborator-select"
                :no-data-text="draftCollaboratorEmptyText"
                @change="saveDraftCollaborators"
              >
                <el-option
                  v-for="item in filteredDraftCollaboratorOptions"
                  :key="item.userId"
                  :label="item.username"
                  :value="item.userId"
                ></el-option>
              </el-select>
            </div>
            <el-button slot="reference" size="mini" plain class="draft-collaborator-button draft-collaborator-inline-button">
              协作者{{ draftCollaboratorIds.length ? ` ${draftCollaboratorIds.length}` : '' }}
            </el-button>
          </el-popover>
        </template>
      </el-tag>
    </div>
    <el-form :model="article" :rules="rules" ref="ruleForm" label-width="120px"
             class="demo-ruleForm mobile-responsive-form">
      <el-form-item label="标题" prop="articleTitle">
        <el-input v-model="article.articleTitle" maxlength="500" show-word-limit @focus.native="updateDraftEditingField('title', true)" @blur.native="updateDraftEditingField('title', false)" @keydown.native.capture="handleDraftTextShortcut('title', $event)"></el-input>
      </el-form-item>

      <el-form-item label="URL别名" prop="articleSlug">
        <el-input
          v-model="article.articleSlug"
          maxlength="160"
          show-word-limit
          placeholder="可留空，留空后使用数字ID作为文章URL"
          @input="handleArticleSlugInput"
        >
          <template slot="prepend">/article/</template>
        </el-input>
        <div class="tip-text">
          <i class="el-icon-info"></i>
          仅支持小写英文、数字和短横线，不能是纯数字；例如 spring-boot-seo，留空则使用数字ID
        </div>
      </el-form-item>

      <el-form-item label="视频链接" prop="videoUrl">
        <el-input maxlength="1000" v-model="article.videoUrl"></el-input>
      </el-form-item>

      <el-form-item label="内容" prop="articleContent">
        <div v-if="!editorReady" class="editor-loading-wrapper">
          <div class="editor-skeleton">
            <div class="skeleton-toolbar"></div>
            <div class="skeleton-content">
              <i class="el-icon-loading"></i>
              <p>编辑器加载中...</p>
            </div>
          </div>
        </div>
        
        <!-- 延迟渲染编辑器，数据加载完成后再初始化 -->
        <ArticleEditor 
          v-if="shouldRenderEditor"
          ref="md" 
          v-model="article.articleContent"
          :height="600"
          mode="ir"
          placeholder="请输入文章内容..."
          @image-add="imgAdd"
          @change="handleEditorChange"
          @ready="onMainEditorReady"
          @focus="updateDraftEditingField('content', true)"
          @blur="updateDraftEditingField('content', false)"
          @shortcut="handleDraftEditorShortcut('content', $event)"
        />
      </el-form-item>

      <el-form-item label="自动摘要">
        <div class="summary-switch-row">
          <el-switch
            v-model="article.autoSummary"
            :disabled="summaryAutoDisabledByConfig"
            active-text="开启"
            inactive-text="手动摘要"
            active-color="#13CE66"
            inactive-color="#909399">
          </el-switch>
          <el-tooltip :content="summarySwitchTip" placement="top">
            <i class="el-icon-question" style="color: #909399; cursor: help;"></i>
          </el-tooltip>
        </div>
        <div v-if="summaryAutoDisabledByConfig" class="tip-text">
          <i class="el-icon-info"></i>
          文章AI助手已关闭自动摘要，可在下方填写手动摘要；留空时使用纯文本内容摘录作为展示与SEO描述
        </div>
      </el-form-item>

      <el-form-item v-if="summaryInputVisible" label="手动摘要" prop="summary">
        <el-input
          v-model="article.summary"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="请输入文章摘要，用于列表、SEO描述和分享预览">
        </el-input>
      </el-form-item>

      <!-- 翻译编辑按钮和跳过开关 -->
      <el-form-item>
        <div style="display: flex; align-items: center; gap: 20px;">
          <div>
            <el-button type="info" icon="el-icon-edit" @click="openTranslationEditor">编辑翻译</el-button>
            <span style="margin-left: 10px; color: #909399; font-size: 12px;">
              编辑文章的翻译版本
            </span>
          </div>

          <div style="display: flex; align-items: center;">
            <el-switch
              v-model="skipAiTranslation"
              active-text="跳过AI自动翻译"
              inactive-text="启用AI自动翻译"
              active-color="#F56C6C"
              inactive-color="#13CE66"
              class="skip-translation-switch"
              style="margin-right: 10px;">
            </el-switch>
            <el-tooltip content="开启后保存文章时不会执行AI自动翻译" placement="top">
              <i class="el-icon-question" style="color: #909399; cursor: help;"></i>
            </el-tooltip>
          </div>
        </div>

        <!-- 暂存翻译提示 -->
        <div v-if="hasPendingTranslation" style="margin-top: 8px;">
          <el-tag type="warning" size="mini">
            <i class="el-icon-edit"></i>
            有未保存的翻译内容 ({{ getLanguageName(pendingTranslation.language) }})
          </el-tag>
        </div>
      </el-form-item>

      <el-form-item label="是否启用评论" prop="commentStatus">
        <el-tag :type="article.commentStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.commentStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.commentStatus"></el-switch>
      </el-form-item>

      <el-form-item label="是否推荐" prop="recommendStatus">
        <el-tag :type="article.recommendStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.recommendStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.recommendStatus"></el-switch>
      </el-form-item>

      <el-form-item label="是否可见" prop="viewStatus">
        <el-tag :type="article.viewStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.viewStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.viewStatus"></el-switch>
      </el-form-item>

      <el-form-item label="推送至搜索引擎" prop="submitToSearchEngine">
        <el-tag :type="searchPushSwitchDisabled || article.submitToSearchEngine === false ? 'info' : 'success'"
                disable-transitions>
          {{searchPushSwitchDisabled || article.submitToSearchEngine === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.submitToSearchEngine" :disabled="searchPushSwitchDisabled"></el-switch>
        <div class="tip-text" :class="{ 'tip-text-warning': searchPushSwitchDisabled }">
          <i :class="searchPushSwitchDisabled ? 'el-icon-warning-outline' : 'el-icon-info'"></i>
          <template v-if="searchPushConfigLoading">
            正在加载搜索引擎推送配置...
          </template>
          <template v-else-if="searchPushSwitchDisabled">
            当前没有可用的搜索引擎推送配置，请先前往 SEO 配置页启用并填写至少一个推送通道
            <el-button type="text" class="tip-action-link" @click="goToSeoConfig">前往SEO配置</el-button>
          </template>
          <template v-else>
            保存后将自动推送文章到 {{ configuredSearchPushEnginesText }} 等已配置渠道
          </template>
        </div>
      </el-form-item>

      <el-form-item v-if="article.viewStatus === false" label="不可见时的访问密码" prop="password">
        <el-input maxlength="30" v-model="article.password"></el-input>
      </el-form-item>

      <el-form-item v-if="article.viewStatus === false" label="密码提示" prop="tips">
        <el-input maxlength="60" v-model="article.tips"></el-input>
      </el-form-item>

      <el-form-item label="封面" prop="articleCover">
        <div class="cover-input-container">
          <el-input 
            v-model="article.articleCover" 
            placeholder="请输入图片链接或使用下方上传功能，如果不想设置封面可以留空"></el-input>
          <el-image 
            class="table-td-thumb"
            lazy
            :preview-src-list="[article.articleCover]"
            :src="article.articleCover"
            fit="cover">
            <div slot="error" class="image-slot">
              <i class="el-icon-picture-outline"></i>
              <div class="image-placeholder-text">封面预览</div>
            </div>
          </el-image>
        </div>
        <uploadPicture :isAdmin="true" :prefix="'articleCover'" class="cover-upload" @addPicture="addArticleCover"
                       :maxSize="5"
                       :maxNumber="1"></uploadPicture>
      </el-form-item>
      <el-form-item label="分类" prop="sortId">
        <el-select v-model="article.sortId" placeholder="请选择分类" @change="handleSortChange">
          <el-option
            v-for="item in sorts"
            :key="item.id"
            :label="item.sortName"
            :value="item.id">
          </el-option>
          <el-option
            key="new-sort"
            label="+ 新建分类"
            value="new-sort"
            style="color: #409EFF; font-weight: bold;">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="标签" prop="labelId">
        <el-select v-model="article.labelId" placeholder="请选择标签" @change="handleLabelChange">
          <el-option
            v-for="item in labelsTemp"
            :key="item.id"
            :label="item.labelName"
            :value="item.id">
          </el-option>
          <el-option
            v-if="article.sortId && article.sortId !== 'new-sort'"
            key="new-label"
            label="+ 新建标签"
            value="new-label"
            style="color: #409EFF; font-weight: bold;">
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 文章付费设置 -->
      <template v-if="paymentPluginActive">
        <el-divider content-position="left">
          <span style="font-size: 14px; color: #606266;">
            <i class="el-icon-money"></i> 文章付费设置
          </span>
        </el-divider>

        <el-form-item label="付费类型">
          <el-select v-model="article.payType" placeholder="请选择付费类型">
            <el-option :value="0" label="免费"></el-option>
            <el-option :value="1" label="按文章付费"></el-option>
            <el-option :value="2" label="会员专属"></el-option>
            <el-option :value="3" label="赞赏解锁"></el-option>
            <el-option :value="4" label="固定金额解锁"></el-option>
          </el-select>
          <div class="tip-text">
            <i class="el-icon-info"></i>
            当前已启用付费插件：{{ paymentPluginName }}
          </div>
        </el-form-item>

        <el-form-item v-if="article.payType === 4" label="付费金额（元）">
          <el-input-number
            v-model="article.payAmount"
            :min="0.01"
            :max="99999"
            :precision="2"
            :step="1"
            placeholder="请输入金额">
          </el-input-number>
        </el-form-item>

        <el-form-item v-if="article.payType && article.payType !== 0" label="免费预览比例(%)">
          <el-slider
            v-model="article.freePercent"
            :min="0"
            :max="100"
            :step="5"
            show-input
            style="max-width: 500px;">
          </el-slider>
          <div class="tip-text">
            <i class="el-icon-info"></i>
            读者可免费阅读文章前 {{ article.freePercent || 30 }}% 的内容，其余部分需付费解锁。也可在文章内容中插入 <code>&lt;!--paywall--&gt;</code> 标记来精确控制截断位置。
          </div>
        </el-form-item>
      </template>

      <div v-else class="payment-not-enabled-tip" style="margin: 10px 0 20px 120px; color: #909399; font-size: 12px;">
        <i class="el-icon-info"></i>
        如需设置文章付费，请先在 <b>插件管理 → 文章付费</b> 中启用并配置付费插件。
      </div>
    </el-form>
    <div class="myCenter" style="margin-bottom: 22px">
      <template v-if="!isDraftMode || canManageCurrentDraft">
        <el-button type="primary" @click="submitForm('ruleForm')">{{ submitWaitText }}</el-button>
        <el-button type="success" @click="submitFormAsync('ruleForm')" :loading="asyncSaveLoading">
          <i class="el-icon-loading" v-if="asyncSaveLoading"></i>
          <i class="el-icon-check" v-else></i>
          {{ submitAsyncText }}
        </el-button>
        <el-button v-if="isDraftMode" type="danger" @click="deleteCurrentDraft">{{ draftDeleteButtonText }}</el-button>
        <el-button v-else type="danger" @click="resetForm('ruleForm')">重置所有修改</el-button>
      </template>
    </div>
    <div v-if="isDraftMode && !canManageCurrentDraft" class="tip-text" style="margin: -8px 0 20px; text-align: center;">
      <i class="el-icon-info"></i>
      协作者只能编辑和自动保存，发布、邀请和删除仅作者或站长可操作。
    </div>

    <!-- 新建分类对话框 -->
    <el-dialog title="新建分类" :visible.sync="newSortDialog" width="500px" :close-on-click-modal="false" custom-class="centered-dialog">
      <el-form ref="newSortForm" :model="newSortForm" :rules="newSortRules" label-width="100px">
        <el-form-item label="分类类型" prop="sortType">
          <el-radio-group v-model="newSortForm.sortType">
            <el-radio-button :label="0">导航栏分类</el-radio-button>
            <el-radio-button :label="1">普通分类</el-radio-button>
          </el-radio-group>
          <div class="tip-text">
            <i class="el-icon-info"></i> 
            导航栏分类会显示在侧边栏"速览"模块中
          </div>
        </el-form-item>
        <el-form-item label="分类名称" prop="sortName">
          <el-input v-model="newSortForm.sortName" placeholder="请输入分类名称" maxlength="32" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="分类描述" prop="sortDescription">
          <el-input v-model="newSortForm.sortDescription" placeholder="请输入分类描述" maxlength="256" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="newSortForm.priority" :min="1" :max="999" placeholder="数字越小越靠前"></el-input-number>
          <div class="tip-text">
            <i class="el-icon-info"></i> 
            数字越小的分类在前端显示时越靠前
          </div>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="cancelNewSort">取 消</el-button>
        <el-button type="primary" @click="createNewSort" :loading="newSortLoading">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 新建标签对话框 -->
    <el-dialog title="新建标签" :visible.sync="newLabelDialog" width="500px" :close-on-click-modal="false" custom-class="centered-dialog">
      <el-form ref="newLabelForm" :model="newLabelForm" :rules="newLabelRules" label-width="100px">
        <el-form-item label="所属分类">
          <el-input :value="getCurrentSortName()" disabled></el-input>
        </el-form-item>
        <el-form-item label="标签名称" prop="labelName">
          <el-input v-model="newLabelForm.labelName" placeholder="请输入标签名称" maxlength="32" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="标签描述" prop="labelDescription">
          <el-input v-model="newLabelForm.labelDescription" placeholder="请输入标签描述" maxlength="256" show-word-limit></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="createNewLabel" :loading="newLabelLoading">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 翻译编辑弹窗 -->
    <el-dialog
      title="编辑文章翻译"
      :visible.sync="translationDialogVisible"
      width="80%"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :append-to-body="true"
      custom-class="centered-dialog translation-dialog"
    >
      <el-form :model="translationForm" ref="translationForm" label-width="120px">
        <!-- 目标语言选择 -->
        <el-form-item label="目标语言">
          <el-select v-model="translationForm.targetLanguage" @change="onTargetLanguageChange">
            <el-option label="English (英文)" value="en"></el-option>
            <el-option label="日本語 (日文)" value="ja"></el-option>
            <el-option label="繁體中文 (繁体中文)" value="zh-TW"></el-option>
            <el-option label="한국어 (韩文)" value="ko"></el-option>
            <el-option label="Français (法文)" value="fr"></el-option>
            <el-option label="Deutsch (德文)" value="de"></el-option>
            <el-option label="Español (西班牙文)" value="es"></el-option>
            <el-option label="Русский (俄文)" value="ru"></el-option>
          </el-select>
          <span style="margin-left: 10px; color: #909399; font-size: 12px;">
            修改后将同时更新系统默认目标语言
          </span>
        </el-form-item>

        <!-- 翻译标题 -->
        <el-form-item label="翻译标题" prop="translatedTitle">
          <el-input
            v-model="translationForm.translatedTitle"
            maxlength="500"
            show-word-limit
            @focus.native="updateDraftEditingField('translationTitle', true)"
            @blur.native="updateDraftEditingField('translationTitle', false)"
            @keydown.native.capture="handleDraftTextShortcut('translationTitle', $event)"
            placeholder="请输入翻译后的文章标题">
          </el-input>
        </el-form-item>

        <!-- 翻译内容 -->
        <el-form-item label="翻译内容" prop="translatedContent">
          <div v-if="translationDialogVisible && !shouldRenderTranslationEditor" class="editor-loading-wrapper">
            <div class="editor-skeleton">
              <div class="skeleton-toolbar"></div>
              <div class="skeleton-content">
                <i class="el-icon-loading"></i>
                <p>翻译编辑器加载中...</p>
              </div>
            </div>
          </div>
          <ArticleEditor
            v-if="shouldRenderTranslationEditor"
            ref="translationMd"
            class="translation-editor"
            v-model="translationForm.translatedContent"
            :height="500"
            mode="ir"
            placeholder="请输入翻译后的文章内容"
            @change="handleTranslationEditorChange"
            @focus="updateDraftEditingField('translationContent', true)"
            @blur="updateDraftEditingField('translationContent', false)"
            @shortcut="handleDraftEditorShortcut('translation', $event)"
          />
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button @click="closeTranslationDialog">取 消</el-button>
        <el-button type="primary" @click="saveTranslation" :loading="translationSaving">
          保 存
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
    import { useMainStore } from '@/stores/main';
    import * as Y from 'yjs';
    import { IndexeddbPersistence } from 'y-indexeddb';
    import { DRAFT_META_FIELDS, applyTextDiff, base64ToUint8Array, buildDraftWebSocketUrl, uint8ArrayToBase64 } from '@/utils/articleDraftCrdt';
    import { createAttachmentMarkdown } from '@/utils/attachmentCard';

const uploadPicture = () => import("../common/uploadPicture");
  const ArticleEditor = () => import('@/components/ArticleEditor.vue');
  import { getAdminLanguageName } from '@/utils/languageUtils';

  const ARTICLE_SLUG_PATTERN = /^[a-z0-9](?:[a-z0-9-]{0,158}[a-z0-9])?$/;

  const normalizeArticleSlug = (value) => {
    return String(value || '')
      .trim()
      .toLowerCase()
      .replace(/[\s_]+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-+|-+$/g, '');
  };

  const validateArticleSlug = (rule, value, callback) => {
    const slug = normalizeArticleSlug(value);
    if (!slug) {
      callback();
      return;
    }
    if (/^\d+$/.test(slug)) {
      callback(new Error('URL别名不能是纯数字'));
      return;
    }
    if (!ARTICLE_SLUG_PATTERN.test(slug)) {
      callback(new Error('URL别名仅支持小写英文、数字和短横线，长度1-160'));
      return;
    }
    callback();
  };

  const createDefaultArticle = () => ({
    articleTitle: "",
    articleSlug: "",
    articleContent: "",
    summary: "",
    autoSummary: true,
    commentStatus: true,
    recommendStatus: false,
    viewStatus: true,
    submitToSearchEngine: true,
    password: "",
    tips: "",
    articleCover: "",
    videoUrl: "",
    sortId: null,
    labelId: null,
    payType: 0,
    payAmount: null,
    freePercent: 30
  });

  export default {
    components: {
      uploadPicture,
      ArticleEditor
    },
    data() {
      return {
        id: this.$route.query.id ? parseInt(this.$route.query.id) : null,
        draftId: this.$route.query.draftId || null,
        loading: null,
        asyncSaveLoading: false,
        currentTaskId: null,
        newSortLoading: false,
        newLabelLoading: false,
        currentStoreType: null, // 添加currentStoreType属性
        // 编辑器加载优化相关
        editorReady: false, // 编辑器是否准备好
        shouldRenderEditor: false, // 是否应该渲染编辑器
        shouldRenderTranslationEditor: false,
        mainEditor: null,
        deferredTaskHandles: [],
        searchPushConfigLoading: true,
        searchPushConfiguredEngines: [],
        summaryConfigLoading: true,
        summaryMode: '',
        article: createDefaultArticle(),
        // 付费插件状态
        paymentPluginActive: false,
        paymentPluginName: '',
        sorts: [],
        labels: [],
        labelsTemp: [],
        // 新建分类对话框
        newSortDialog: false,
        newSortForm: {
          sortName: '',
          sortDescription: '',
          priority: 1,
          sortType: 0
        },
        newSortRules: {
          sortName: [
            { required: true, message: '请输入分类名称', trigger: 'blur' },
            { max: 32, message: '分类名称长度不能超过32个字符', trigger: 'blur' }
          ],
          sortDescription: [
            { required: true, message: '请输入分类描述', trigger: 'blur' },
            { max: 256, message: '分类描述长度不能超过256个字符', trigger: 'blur' }
          ],
          priority: [
            { required: true, message: '请输入优先级', trigger: 'blur' },
            { type: 'number', message: '优先级必须为数字值', trigger: 'blur' }
          ]
        },
        // 新建标签对话框
        newLabelDialog: false,
        newLabelForm: {
          labelName: '',
          labelDescription: '',
          sortId: null
        },
        newLabelRules: {
          labelName: [
            { required: true, message: '请输入标签名称', trigger: 'blur' },
            { max: 32, message: '标签名称长度不能超过32个字符', trigger: 'blur' }
          ],
          labelDescription: [
            { required: true, message: '请输入标签描述', trigger: 'blur' },
            { max: 256, message: '标签描述长度不能超过256个字符', trigger: 'blur' }
          ]
        },
        // 翻译编辑相关数据
        translationDialogVisible: false,
        translationSaving: false,
        translationForm: {
          targetLanguage: 'en',
          translatedTitle: '',
          translatedContent: ''
        },
        // 跳过AI翻译开关
        skipAiTranslation: false,
        // 暂存的翻译数据
        pendingTranslation: {
          title: '',
          content: '',
          language: ''
        },
        // 响应式布局相关
        resizeTimer: null,
        draftStatusText: '草稿初始化中',
        draftStatusType: 'info',
        draftLastSyncedAt: '',
        draftOnlineCount: 1,
        draftOnlineUsers: [],
        draftEditingUsers: {},
        draftCollaboratorIds: [],
        draftCollaboratorOptions: [],
        draftInviteAccepting: false,
        draftReady: false,
        draftSnapshotDirty: false,
        suppressNextDraftRouteReload: false,
        suppressNextIdRouteReload: false,
        skipDraftSync: false,
        draftSnapshotTimer: null,
        draftPageHideHandler: null,
        draftType: null,
        draftOwnerUserId: null,
        sourceArticleId: null,
        sourceArticleTitle: '',
        ydoc: null,
        yPersistence: null,
        draftWs: null,
        draftMetaMap: null,
        draftTitleText: null,
        draftContentText: null,
        draftTranslationTitleText: null,
        draftTranslationContentText: null,
        draftOrigins: null,
        draftUndoManager: null,
        draftTranslationUndoManager: null,
        rules: {
          articleTitle: [
            {required: true, message: '请输入标题', trigger: 'change'},
            {max: 500, message: '标题长度不能超过500个字符', trigger: 'change'}
          ],
          articleSlug: [
            { validator: validateArticleSlug, trigger: 'blur' }
          ],
          articleContent: [
            {required: true, message: '请输入内容', trigger: 'change'}
          ],
          commentStatus: [
            {required: true, message: '是否启用评论', trigger: 'change'}
          ],
          recommendStatus: [
            {required: true, message: '是否推荐', trigger: 'change'}
          ],
          viewStatus: [
            {required: true, message: '是否可见', trigger: 'change'}
          ],
          articleCover: [
            {required: true, message: '封面', trigger: 'change'}
          ],
          sortId: [
            {required: true, message: '分类', trigger: 'change'}
          ],
          labelId: [
            {required: true, message: '标签', trigger: 'blur'}
          ]
        }
      };
    },

      computed: {
      mainStore() {
        return useMainStore();
      },
      searchPushSwitchDisabled() {
        return this.searchPushConfigLoading || this.searchPushConfiguredEngines.length === 0;
      },
      summaryAutoDisabledByConfig() {
        return !this.summaryConfigLoading && this.summaryMode === 'disabled';
      },
      summaryInputVisible() {
        return this.summaryAutoDisabledByConfig || this.article.autoSummary === false;
      },
      summarySwitchTip() {
        if (this.summaryConfigLoading) {
          return '正在加载文章AI助手摘要配置';
        }
        if (this.summaryAutoDisabledByConfig) {
          return '文章AI助手已关闭自动摘要，保存时不会生成AI摘要';
        }
        return '开启后保存时按后台摘要配置生成，关闭后使用手动摘要';
      },
      isDraftMode() {
        return !!this.draftId || this.$common.isEmpty(this.id);
      },
      isRevisionDraft() {
        return this.draftType === 'REVISION';
      },
      effectiveArticleId() {
        return this.isRevisionDraft ? this.sourceArticleId : this.id;
      },
      canManageCurrentDraft() {
        if (!this.isDraftMode || !this.draftId) {
          return true;
        }
        const currentAdmin = this.mainStore && this.mainStore.currentAdmin ? this.mainStore.currentAdmin : {};
        if (currentAdmin.isBoss) {
          return true;
        }
        return this.draftOwnerUserId !== null && String(currentAdmin.id) === String(this.draftOwnerUserId);
      },
      submitWaitText() {
        if (this.isRevisionDraft) {
          return '发布修订并等待';
        }
        return this.isDraftMode ? '发布并等待' : '保存并等待';
      },
      submitAsyncText() {
        if (this.isRevisionDraft) {
          return '发布修订并离开';
        }
        return this.isDraftMode ? '发布并离开' : '保存并离开';
      },
      draftDeleteButtonText() {
        return this.isRevisionDraft ? '放弃修订' : '删除草稿';
      },
      compactDraftStatusText() {
        if (this.draftStatusText === '协同连接已建立') {
          return '协同中';
        }
        if (this.draftStatusText === '草稿已保存') {
          return '已保存';
        }
        if (this.draftStatusText === '同步中') {
          return '同步中';
        }
        if (this.draftStatusText === '协同连接已断开，仍会保存在本地') {
          return '已断开';
        }
        if (this.draftStatusText === '协同连接失败，仍会保存在本地') {
          return '连接失败';
        }
        if (this.draftStatusText === '草稿已加载') {
          return '草稿中';
        }
        return this.draftStatusText;
      },
      shortDraftId() {
        if (!this.draftId) {
          return '';
        }
        return this.draftId.slice(0, 10);
      },
      filteredDraftCollaboratorOptions() {
        const excludedIds = this.getExcludedCollaboratorIds();
        if (excludedIds.length === 0) {
          return this.draftCollaboratorOptions;
        }
        return this.draftCollaboratorOptions.filter(item => !excludedIds.includes(String(item.userId)));
      },
      draftCollaboratorEmptyText() {
        return '暂无其他可选协作者';
      },
      draftMetaSyncKey() {
        const metaState = {};
        DRAFT_META_FIELDS.forEach((field) => {
          if (field === 'skipAiTranslation') {
            metaState[field] = this.skipAiTranslation;
          } else if (field === 'translationLanguage') {
            metaState[field] = this.translationForm.targetLanguage || 'en';
          } else {
            metaState[field] = this.article[field];
          }
        });
        return JSON.stringify(metaState);
      },
      configuredSearchPushEnginesText() {
        return this.searchPushConfiguredEngines.join('、');
      },
      draftEditingUsersText() {
        return Object.values(this.draftEditingUsers || {}).map(item => `${item.username}(${this.getDraftFieldLabel(item.field)})`);
      },
      // 检查是否有暂存的翻译数据
      hasPendingTranslation() {
        return this.pendingTranslation.title &&
               this.pendingTranslation.content &&
               this.pendingTranslation.language;
      }
    },

    watch: {
      'article.sortId'(newVal, oldVal) {
        if (oldVal !== null) {
          this.article.labelId = null;
        }
        if (!this.$common.isEmpty(newVal) && !this.$common.isEmpty(this.labels)) {
          this.labelsTemp = this.labels.filter(l => l.sortId === newVal);
        }
      },
      
      // 监听路由变化，更新文章 ID
      '$route.query.id'(newId, oldId) {
        if (newId === oldId) {
          return;
        }
        this.id = newId ? parseInt(newId) : null;
        if (this.suppressNextIdRouteReload) {
          this.suppressNextIdRouteReload = false;
          return;
        }
        this.initializePageData();
      },
      '$route.query.draftId'(newDraftId, oldDraftId) {
        if (newDraftId === oldDraftId) {
          return;
        }
        this.draftId = newDraftId || null;
        if (this.suppressNextDraftRouteReload && newDraftId === this.draftId) {
          this.suppressNextDraftRouteReload = false;
          return;
        }
        if (this.isDraftMode) {
          this.initializePageData();
        }
      },
      '$route.query.inviteToken'(newToken, oldToken) {
        if (newToken === oldToken) {
          return;
        }
        if (this.isDraftMode && this.draftId) {
          this.initializePageData();
        }
      },
      'article.articleTitle'() {
        this.syncDraftTextField('title');
      },
      'article.articleContent'() {
        this.syncDraftTextField('content');
      },
      'translationForm.translatedTitle'() {
        this.syncDraftTextField('translationTitle');
        this.syncPendingTranslationFromForm();
      },
      'translationForm.translatedContent'() {
        this.syncDraftTextField('translationContent');
        this.syncPendingTranslationFromForm();
      },
      'translationForm.targetLanguage'() {
        this.syncDraftMetaFields();
        this.syncPendingTranslationFromForm();
      },
      skipAiTranslation() {
        this.syncDraftMetaFields();
      },
      'article.autoSummary'(enabled) {
        if (this.summaryAutoDisabledByConfig && enabled) {
          this.article.autoSummary = false;
          return;
        }
        if (enabled && this.$refs.ruleForm) {
          this.$refs.ruleForm.clearValidate('summary');
        }
        this.syncDraftMetaFields();
      },
      draftMetaSyncKey() {
        this.syncDraftMetaFields();
      }
    },

    created() {
      // 优化加载流程：先加载分类标签，再延迟初始化编辑器
      this.initializePageData();
      
      // 监听系统配置更新事件
      this.$bus.$on('sysConfigUpdated', this.handleSysConfigUpdate);
      
      // 初始化移动端表单适配
      this.initMobileFormLayout();
      
      // 监听窗口大小变化
      window.addEventListener('resize', this.handleWindowResize);
      this.draftPageHideHandler = () => {
        this.persistDraftSnapshot(true);
      };
      window.addEventListener('pagehide', this.draftPageHideHandler);
    },
    
    mounted() {
      // 编辑器将在数据加载完成后延迟初始化，提升用户体验
    },
    
    beforeDestroy() {
      // 移除事件监听，避免内存泄漏
      this.$bus.$off('sysConfigUpdated', this.handleSysConfigUpdate);
      
      // 移除窗口大小变化监听
      window.removeEventListener('resize', this.handleWindowResize);
      this.clearDeferredTasks();
      window.removeEventListener('pagehide', this.draftPageHideHandler);
      this.destroyDraftSession();
    },



    methods: {
      resetDraftContext() {
        this.draftType = null;
        this.draftOwnerUserId = null;
        this.sourceArticleId = null;
        this.sourceArticleTitle = '';
      },
      syncMainEditorContent() {
        const editor = this.mainEditor || this.$refs.md
        if (editor && typeof editor.getValue === 'function') {
          this.article.articleContent = editor.getValue()
        }
      },
      handleArticleSlugInput(value) {
        const normalized = normalizeArticleSlug(value);
        if (this.article.articleSlug !== normalized) {
          this.article.articleSlug = normalized;
        }
      },
      // 初始化页面数据（优化后的加载流程）
      async initializePageData() {
        this.editorReady = false;
        this.shouldRenderEditor = false;
        this.mainEditor = null;
        this.resetDraftContext();
        this.clearDeferredTasks();
        this.searchPushConfigLoading = true;
        this.summaryConfigLoading = true;
        try {
          const tasks = [this.getSortAndLabel(false)];
          if (this.draftId) {
            tasks.push(this.ensureDraftSessionCreated());
          } else if (this.$common.isEmpty(this.id)) {
            this.article = createDefaultArticle();
            tasks.push(this.ensureDraftSessionCreated());
          } else {
            this.destroyDraftSession();
            tasks.push(this.ensureRevisionDraftSessionCreated());
          }
          tasks.push(this.loadSearchPushAvailability());
          tasks.push(this.loadSummaryGenerationMode());

          await Promise.all(tasks);
          if (this.searchPushConfiguredEngines.length === 0) {
            this.article.submitToSearchEngine = false;
          }
          this.applySummaryModeToArticle();
          this.scheduleEditorMount();
          this.scheduleNonCriticalStartupTasks();
        } catch (error) {
          console.error('初始化页面数据失败:', error);
          // 即使失败也要显示编辑器
          this.scheduleEditorMount();
        }
      },

      scheduleEditorMount() {
        this.$nextTick(() => {
          const mountEditor = () => {
            this.shouldRenderEditor = true;
          };

          if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
            window.requestAnimationFrame(mountEditor);
          } else {
            setTimeout(mountEditor, 16);
          }
        });
      },

      scheduleDeferredTask(task, delay = 300) {
        const removeHandle = (handle) => {
          this.deferredTaskHandles = this.deferredTaskHandles.filter((item) => item !== handle);
        };
        let handle = null;

        if (typeof window !== 'undefined' && typeof window.requestIdleCallback === 'function') {
          const idleId = window.requestIdleCallback(() => {
            removeHandle(handle);
            task();
          }, { timeout: Math.max(800, delay) });
          handle = { type: 'idle', id: idleId };
          this.deferredTaskHandles.push(handle);
          return;
        }

        const timeoutId = window.setTimeout(() => {
          removeHandle(handle);
          task();
        }, delay);
        handle = { type: 'timeout', id: timeoutId };
        this.deferredTaskHandles.push(handle);
      },

      clearDeferredTasks() {
        this.deferredTaskHandles.forEach((handle) => {
          if (handle.type === 'idle' && typeof window !== 'undefined' && typeof window.cancelIdleCallback === 'function') {
            window.cancelIdleCallback(handle.id);
            return;
          }
          clearTimeout(handle.id);
        });
        this.deferredTaskHandles = [];
      },

      scheduleNonCriticalStartupTasks() {
        this.scheduleDeferredTask(() => {
          this.checkPaymentPlugin();
        }, 400);

        if (!this.$common.isEmpty(this.effectiveArticleId)) {
          this.scheduleDeferredTask(() => {
            this.checkAndSetTranslationMode();
          }, 900);
        }
      },
      async ensureDraftSessionCreated() {
        let detail = null;
        if (this.draftId) {
          await this.acceptDraftInviteIfNeeded();
          const res = await this.$http.get(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}`, {}, true);
          if (res.code !== 200 || !res.data) {
            throw new Error(res.message || '加载草稿失败');
          }
          detail = res.data;
        } else {
          const res = await this.$http.post(this.$constant.baseURL + '/admin/articleDraft/create', {}, true);
          if (res.code !== 200 || !res.data) {
            throw new Error(res.message || '创建草稿失败');
          }
          detail = res.data;
          this.draftId = detail.id;
          this.suppressNextDraftRouteReload = true;
          this.$router.replace({ path: '/postEdit', query: { draftId: detail.id } });
        }
        await this.initializeDraftSession(detail);
      },
      async ensureRevisionDraftSessionCreated() {
        const res = await this.$http.post(this.$constant.baseURL + `/admin/articleDraft/revision/${this.id}`, {}, true);
        if (res.code !== 200 || !res.data) {
          throw new Error(res.message || '创建修订草稿失败');
        }
        const detail = res.data;
        this.draftId = detail.id;
        this.suppressNextIdRouteReload = true;
        this.suppressNextDraftRouteReload = true;
        this.$router.replace({ path: '/postEdit', query: { draftId: detail.id } });
        await this.initializeDraftSession(detail);
      },
      async acceptDraftInviteIfNeeded() {
        const inviteToken = this.$route.query.inviteToken;
        if (!this.draftId || !inviteToken || this.draftInviteAccepting) {
          return;
        }
        this.draftInviteAccepting = true;
        try {
          const res = await this.$http.post(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}/acceptInvite`, {
            inviteToken
          }, true);
          if (res.code !== 200) {
            throw new Error(res.message || '接受邀请失败');
          }
          if (res.data && res.data.joined) {
            this.$message.success('已加入草稿协作');
          }
          const nextQuery = { ...this.$route.query };
          delete nextQuery.inviteToken;
          this.$router.replace({ path: this.$route.path, query: nextQuery });
        } finally {
          this.draftInviteAccepting = false;
        }
      },
      async initializeDraftSession(detail) {
        this.destroyDraftSession();
        this.draftId = detail.id;
        this.draftType = detail.draftType || 'CREATE';
        this.draftOwnerUserId = detail.ownerUserId !== undefined && detail.ownerUserId !== null ? detail.ownerUserId : null;
        this.sourceArticleId = detail.articleId || null;
        this.sourceArticleTitle = detail.sourceArticleTitle || '';
        this.draftCollaboratorIds = (detail.collaborators || []).map(item => item.userId);
        this.article = detail.sourceArticle ? {
          ...createDefaultArticle(),
          ...detail.sourceArticle
        } : createDefaultArticle();
        this.skipAiTranslation = false;
        this.resetTranslationForm();
        this.clearPendingTranslation();
        await this.loadDraftCollaboratorOptions();
        this.draftStatusText = '草稿已加载';
        this.draftStatusType = 'info';
        this.draftOnlineCount = 1;
        this.ydoc = new Y.Doc();
        this.yPersistence = new IndexeddbPersistence(`poetize-article-draft-${this.draftId}`, this.ydoc);
        this.bindDraftDoc();

        if (detail.crdtSnapshotBase64) {
          Y.applyUpdate(this.ydoc, base64ToUint8Array(detail.crdtSnapshotBase64), 'remote');
        }

        await this.yPersistence.whenSynced;
        if (this.isDraftDocEmpty()) {
          this.syncDraftDocFromForm();
        } else {
          this.applyDraftDocToForm();
        }
        this.draftSnapshotTimer = window.setInterval(() => {
          this.persistDraftSnapshot(false);
        }, 5000);
        this.openDraftWebSocket();
        this.draftReady = true;
      },
      bindDraftDoc() {
        this.draftOrigins = {
          title: { key: 'title' },
          content: { key: 'content' },
          translationTitle: { key: 'translationTitle' },
          translationContent: { key: 'translationContent' },
          meta: { key: 'meta' }
        };
        this.draftMetaMap = this.ydoc.getMap('articleMeta');
        this.draftTitleText = this.ydoc.getText('articleTitle');
        this.draftContentText = this.ydoc.getText('articleContent');
        this.draftTranslationTitleText = this.ydoc.getText('translationTitle');
        this.draftTranslationContentText = this.ydoc.getText('translationContent');
        this.draftUndoManager = new Y.UndoManager([this.draftTitleText, this.draftContentText], {
          trackedOrigins: new Set([this.draftOrigins.title, this.draftOrigins.content])
        });
        this.draftTranslationUndoManager = new Y.UndoManager([this.draftTranslationTitleText, this.draftTranslationContentText], {
          trackedOrigins: new Set([this.draftOrigins.translationTitle, this.draftOrigins.translationContent])
        });
        this.ydoc.on('update', this.handleDraftDocUpdate);
        this.draftTitleText.observe(this.handleDraftTitleTextChange);
        this.draftContentText.observe(this.handleDraftContentTextChange);
        this.draftTranslationTitleText.observe(this.handleDraftTranslationTitleTextChange);
        this.draftTranslationContentText.observe(this.handleDraftTranslationContentTextChange);
        this.draftMetaMap.observe(this.handleDraftMetaMapChange);
      },
      destroyDraftSession() {
        this.draftReady = false;
        this.draftEditingUsers = {};
        if (this.draftSnapshotTimer) {
          clearInterval(this.draftSnapshotTimer);
          this.draftSnapshotTimer = null;
        }
        if (this.ydoc) {
          this.ydoc.off('update', this.handleDraftDocUpdate);
        }
        if (this.draftTitleText) {
          this.draftTitleText.unobserve(this.handleDraftTitleTextChange);
        }
        if (this.draftContentText) {
          this.draftContentText.unobserve(this.handleDraftContentTextChange);
        }
        if (this.draftTranslationTitleText) {
          this.draftTranslationTitleText.unobserve(this.handleDraftTranslationTitleTextChange);
        }
        if (this.draftTranslationContentText) {
          this.draftTranslationContentText.unobserve(this.handleDraftTranslationContentTextChange);
        }
        if (this.draftMetaMap) {
          this.draftMetaMap.unobserve(this.handleDraftMetaMapChange);
        }
        if (this.draftUndoManager) {
          this.draftUndoManager.destroy();
        }
        if (this.draftTranslationUndoManager) {
          this.draftTranslationUndoManager.destroy();
        }
        if (this.draftWs) {
          this.draftWs.close();
          this.draftWs = null;
        }
        if (this.yPersistence && typeof this.yPersistence.destroy === 'function') {
          this.yPersistence.destroy();
        }
        if (this.ydoc) {
          this.ydoc.destroy();
        }
        this.yPersistence = null;
        this.ydoc = null;
        this.draftMetaMap = null;
        this.draftTitleText = null;
        this.draftContentText = null;
        this.draftTranslationTitleText = null;
        this.draftTranslationContentText = null;
        this.draftOrigins = null;
        this.draftUndoManager = null;
        this.draftTranslationUndoManager = null;
      },
      isDraftDocEmpty() {
        return this.draftTitleText.length === 0 &&
          this.draftContentText.length === 0 &&
          this.draftTranslationTitleText.length === 0 &&
          this.draftTranslationContentText.length === 0 &&
          this.draftMetaMap.size === 0;
      },
      syncDraftDocFromForm() {
        if (!this.isDraftMode || !this.draftMetaMap || this.skipDraftSync) {
          return;
        }
        this.syncDraftTextField('title');
        this.syncDraftTextField('content');
        this.syncDraftTextField('translationTitle');
        this.syncDraftTextField('translationContent');
        this.syncDraftMetaFields();
      },
      applyDraftDocToForm() {
        if (!this.isDraftMode || !this.draftMetaMap) {
          return;
        }
        this.skipDraftSync = true;
        const nextArticle = {
          ...this.article,
          articleTitle: this.draftTitleText.toString(),
          articleContent: this.draftContentText.toString()
        };
        DRAFT_META_FIELDS.forEach((field) => {
          const value = this.draftMetaMap.get(field);
          if (field === 'skipAiTranslation') {
            this.skipAiTranslation = value === null || value === undefined ? false : value;
            return;
          }
          if (field === 'translationLanguage') {
            this.translationForm.targetLanguage = value || 'en';
            return;
          }
          if (field === 'autoSummary') {
            nextArticle.autoSummary = value === null || value === undefined ? true : value;
            return;
          }
          if (field === 'summary') {
            nextArticle.summary = value || '';
            return;
          }
          if (value !== undefined) {
            nextArticle[field] = value;
          }
        });
        this.article = nextArticle;
        this.translationForm.translatedTitle = this.draftTranslationTitleText.toString();
        this.translationForm.translatedContent = this.draftTranslationContentText.toString();
        this.pendingTranslation = {
          title: this.translationForm.translatedTitle || '',
          content: this.translationForm.translatedContent || '',
          language: this.translationForm.targetLanguage || 'en'
        };
        this.$nextTick(() => {
          this.skipDraftSync = false;
        });
      },
      replaceYText(target, value) {
        applyTextDiff(target, value || '');
      },
      syncDraftTextField(field) {
        if (!this.isDraftMode || !this.draftReady || this.skipDraftSync) {
          return;
        }
        if (field === 'title') {
          applyTextDiff(this.draftTitleText, this.article.articleTitle || '', this.draftOrigins && this.draftOrigins.title);
          return;
        }
        if (field === 'content') {
          applyTextDiff(this.draftContentText, this.article.articleContent || '', this.draftOrigins && this.draftOrigins.content);
          return;
        }
        if (field === 'translationTitle') {
          applyTextDiff(this.draftTranslationTitleText, this.translationForm.translatedTitle || '', this.draftOrigins && this.draftOrigins.translationTitle);
          return;
        }
        if (field === 'translationContent') {
          applyTextDiff(this.draftTranslationContentText, this.translationForm.translatedContent || '', this.draftOrigins && this.draftOrigins.translationContent);
        }
      },
      syncDraftMetaFields() {
        if (!this.isDraftMode || !this.draftReady || !this.draftMetaMap || this.skipDraftSync) {
          return;
        }
        this.ydoc.transact(() => {
          DRAFT_META_FIELDS.forEach((field) => {
            let value = null;
            if (field === 'skipAiTranslation') {
              value = this.skipAiTranslation;
            } else if (field === 'translationLanguage') {
              value = this.translationForm.targetLanguage || 'en';
            } else {
              value = this.article[field];
            }
            this.draftMetaMap.set(field, value === undefined ? null : value);
          });
        }, this.draftOrigins && this.draftOrigins.meta);
      },
      syncPendingTranslationFromForm() {
        if (!this.isDraftMode || !this.draftReady || this.skipDraftSync) {
          return;
        }
        this.pendingTranslation = {
          title: this.translationForm.translatedTitle || '',
          content: this.translationForm.translatedContent || '',
          language: this.translationForm.targetLanguage || 'en'
        };
      },
      applyDraftTextField(field, value) {
        this.skipDraftSync = true;
        if (field === 'title' && this.article.articleTitle !== value) {
          this.article.articleTitle = value;
        }
        if (field === 'content' && this.article.articleContent !== value) {
          this.article.articleContent = value;
        }
        if (field === 'translationTitle' && this.translationForm.translatedTitle !== value) {
          this.translationForm.translatedTitle = value;
        }
        if (field === 'translationContent' && this.translationForm.translatedContent !== value) {
          this.translationForm.translatedContent = value;
        }
        this.$nextTick(() => {
          this.skipDraftSync = false;
        });
      },
      handleDraftTitleTextChange() {
        this.applyDraftTextField('title', this.draftTitleText.toString());
      },
      handleDraftContentTextChange() {
        this.applyDraftTextField('content', this.draftContentText.toString());
      },
      handleDraftTranslationTitleTextChange() {
        this.applyDraftTextField('translationTitle', this.draftTranslationTitleText.toString());
        this.syncPendingTranslationFromForm();
      },
      handleDraftTranslationContentTextChange() {
        this.applyDraftTextField('translationContent', this.draftTranslationContentText.toString());
        this.syncPendingTranslationFromForm();
      },
      handleDraftMetaMapChange() {
        if (!this.isDraftMode || !this.draftMetaMap) {
          return;
        }
        this.skipDraftSync = true;
        DRAFT_META_FIELDS.forEach((field) => {
          const value = this.draftMetaMap.get(field);
          if (field === 'skipAiTranslation') {
            this.skipAiTranslation = value === null || value === undefined ? false : value;
            return;
          }
          if (field === 'translationLanguage') {
            this.translationForm.targetLanguage = value || 'en';
            return;
          }
          if (field === 'autoSummary') {
            const nextAutoSummary = value === null || value === undefined ? true : value;
            if (this.article.autoSummary !== nextAutoSummary) {
              this.article.autoSummary = nextAutoSummary;
            }
            return;
          }
          if (field === 'summary') {
            const nextSummary = value || '';
            if (this.article.summary !== nextSummary) {
              this.article.summary = nextSummary;
            }
            return;
          }
          if (value !== undefined && this.article[field] !== value) {
            this.article[field] = value;
          }
        });
        this.$nextTick(() => {
          this.skipDraftSync = false;
        });
      },
      handleDraftEditorShortcut(target, event) {
        if (!this.isDraftMode || !event || !event.originalEvent) {
          return;
        }
        const isUndo = event.key === 'z' && !event.shiftKey;
        const isRedo = event.key === 'y' || (event.key === 'z' && event.shiftKey);
        if (!isUndo && !isRedo) {
          return;
        }
        const manager = target === 'translation' ? this.draftTranslationUndoManager : this.draftUndoManager;
        if (!manager) {
          return;
        }
        event.originalEvent.preventDefault();
        event.originalEvent.stopPropagation();
        if (isUndo) {
          manager.undo();
        } else {
          manager.redo();
        }
      },
      handleDraftTextShortcut(target, event) {
        if (!this.isDraftMode || !event) {
          return;
        }
        const key = String(event.key || '').toLowerCase();
        const isMetaPressed = event.ctrlKey || event.metaKey;
        if (!isMetaPressed || (key !== 'z' && key !== 'y')) {
          return;
        }
        const isUndo = key === 'z' && !event.shiftKey;
        const isRedo = key === 'y' || (key === 'z' && event.shiftKey);
        if (!isUndo && !isRedo) {
          return;
        }
        const manager = target === 'translationTitle' ? this.draftTranslationUndoManager : this.draftUndoManager;
        if (!manager) {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        if (isUndo) {
          manager.undo();
        } else {
          manager.redo();
        }
      },
      handleDraftDocUpdate(update, origin) {
        this.draftSnapshotDirty = true;
        if (origin !== 'remote' && this.draftWs && this.draftWs.readyState === WebSocket.OPEN) {
          this.draftWs.send(JSON.stringify({
            type: 'state_update',
            payload: uint8ArrayToBase64(update),
            draftId: this.draftId
          }));
        }
        if (origin !== 'remote') {
          this.draftStatusText = '同步中';
          this.draftStatusType = 'warning';
        }
      },
      async openDraftWebSocket() {
        try {
          const tokenRes = await this.$http.get(this.$constant.baseURL + '/im/getWsToken', {}, true);
          if (tokenRes.code !== 200 || !tokenRes.data) {
            throw new Error(tokenRes.message || '获取协同连接令牌失败');
          }
          const socketUrl = buildDraftWebSocketUrl(this.$constant.baseURL, this.draftId, tokenRes.data);
          this.draftWs = new WebSocket(socketUrl);
          this.draftWs.onmessage = this.handleDraftWsMessage;
          this.draftWs.onopen = () => {
            this.draftStatusText = '协同连接已建立';
            this.draftStatusType = 'success';
          };
          this.draftWs.onclose = () => {
            this.draftStatusText = '协同连接已断开，仍会保存在本地';
            this.draftStatusType = 'warning';
          };
        } catch (error) {
          this.draftStatusText = '协同连接失败，仍会保存在本地';
          this.draftStatusType = 'warning';
        }
      },
      handleDraftWsMessage(event) {
        try {
          const payload = JSON.parse(event.data);
          if (payload.type === 'state_update' && payload.payload) {
            Y.applyUpdate(this.ydoc, base64ToUint8Array(payload.payload), 'remote');
            return;
          }
          if (payload.type === 'awareness') {
            if (payload.mode === 'editing') {
              this.applyDraftEditingAwareness(payload);
              return;
            }
            this.draftOnlineCount = payload.onlineCount || 1;
            this.draftOnlineUsers = Array.isArray(payload.onlineUsers) ? payload.onlineUsers : [];
            this.pruneDraftEditingUsers();
          }
        } catch (error) {
        }
      },
      updateDraftEditingField(field, active) {
        if (!this.isDraftMode || !this.draftWs || this.draftWs.readyState !== WebSocket.OPEN) {
          return;
        }
        this.draftWs.send(JSON.stringify({
          type: 'awareness',
          mode: 'editing',
          field,
          active
        }));
      },
      applyDraftEditingAwareness(payload) {
        if (!payload || !payload.userId) {
          return;
        }
        const userId = String(payload.userId);
        if (payload.active) {
          this.$set(this.draftEditingUsers, userId, {
            userId,
            username: payload.username || '未知用户',
            field: payload.field || 'content'
          });
          return;
        }
        this.$delete(this.draftEditingUsers, userId);
      },
      pruneDraftEditingUsers() {
        const onlineUserIds = new Set((this.draftOnlineUsers || []).map(item => String(item.userId)));
        Object.keys(this.draftEditingUsers || {}).forEach((userId) => {
          if (!onlineUserIds.has(userId)) {
            this.$delete(this.draftEditingUsers, userId);
          }
        });
      },
      getDraftFieldLabel(field) {
        const fieldMap = {
          title: '标题',
          content: '正文',
          translationTitle: '翻译标题',
          translationContent: '翻译正文'
        };
        return fieldMap[field] || '正文';
      },
      async persistDraftSnapshot(force = false) {
        if (!this.isDraftMode || !this.draftId || !this.ydoc || (!force && !this.draftSnapshotDirty)) {
          return;
        }
        try {
          const snapshotBase64 = uint8ArrayToBase64(Y.encodeStateAsUpdate(this.ydoc));
          const res = await this.$http.post(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}/snapshot`, {
            titleCache: this.article.articleTitle || '未命名草稿',
            snapshotBase64
          }, true);
          if (res.code === 200) {
            this.draftSnapshotDirty = false;
            this.draftLastSyncedAt = new Date().toLocaleTimeString('zh-CN', { hour12: false });
            this.draftStatusText = '草稿已保存';
            this.draftStatusType = 'success';
          }
        } catch (error) {
          this.draftStatusText = '草稿保存失败，仅保留本地副本';
          this.draftStatusType = 'danger';
        }
      },
      async loadDraftCollaboratorOptions() {
        if (!this.isDraftMode) {
          return;
        }
        try {
          const res = await this.$http.get(this.$constant.baseURL + '/admin/articleDraft/collaborators/options', {}, true);
          if (res.code === 200 && Array.isArray(res.data)) {
            const excludedIds = this.getExcludedCollaboratorIds();
            this.draftCollaboratorOptions = res.data.filter(item => !excludedIds.includes(String(item.userId)));
          }
        } catch (error) {
        }
      },
      getExcludedCollaboratorIds() {
        const ids = [];
        const currentAdminId = this.mainStore && this.mainStore.currentAdmin && this.mainStore.currentAdmin.id;
        const currentUserId = this.mainStore && this.mainStore.currentUser && this.mainStore.currentUser.id;
        if (this.draftOwnerUserId !== undefined && this.draftOwnerUserId !== null && this.draftOwnerUserId !== '') {
          ids.push(String(this.draftOwnerUserId));
        }
        if (currentAdminId !== undefined && currentAdminId !== null && currentAdminId !== '') {
          ids.push(String(currentAdminId));
        }
        if (currentUserId !== undefined && currentUserId !== null && currentUserId !== '') {
          ids.push(String(currentUserId));
        }
        return Array.from(new Set(ids));
      },
      async saveDraftCollaborators() {
        if (!this.isDraftMode || !this.draftId || !this.canManageCurrentDraft) {
          return;
        }
        try {
          const res = await this.$http.put(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}/collaborators`, {
            collaboratorIds: this.draftCollaboratorIds
          }, true);
          if (res.code === 200 && Array.isArray(res.data)) {
            this.draftCollaboratorIds = res.data.map(item => item.userId);
          }
        } catch (error) {
          this.showError('保存协作者失败', error);
        }
      },
      async copyDraftInviteLink() {
        if (!this.draftId) {
          this.$message.warning('请先等待草稿创建完成');
          return;
        }
        if (!this.canManageCurrentDraft) {
          this.$message.warning('仅作者或站长可管理邀请链接');
          return;
        }
        try {
          const res = await this.$http.post(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}/invite`, {}, true);
          if (res.code !== 200 || !res.data || !res.data.inviteToken) {
            throw new Error(res.message || '生成邀请链接失败');
          }
          const inviteUrl = `${window.location.origin}/admin/postEdit?draftId=${this.draftId}&inviteToken=${res.data.inviteToken}`;
          await this.copyText(inviteUrl);
          this.$message.success('邀请链接已复制');
        } catch (error) {
          this.showError('复制邀请链接失败', error);
        }
      },
      async revokeDraftInviteLink() {
        if (!this.draftId || !this.canManageCurrentDraft) {
          return;
        }
        try {
          const res = await this.$http.delete(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}/invite`, {}, true);
          if (res.code !== 200) {
            throw new Error(res.message || '撤销邀请链接失败');
          }
          this.$message.success('邀请链接已撤销');
        } catch (error) {
          this.showError('撤销邀请链接失败', error);
        }
      },
      async copyText(text) {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text);
          return;
        }
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        const success = document.execCommand('copy');
        document.body.removeChild(textarea);
        if (!success) {
          throw new Error('浏览器不支持自动复制');
        }
      },
      async deleteCurrentDraft() {
        if (!this.draftId || !this.canManageCurrentDraft) {
          return;
        }
        const confirmTitle = this.isRevisionDraft ? '放弃修订' : '删除草稿';
        const confirmMessage = this.isRevisionDraft
          ? '放弃后修订草稿内容和协作关系都会被清空，原文章不会受影响，确认继续？'
          : '删除后草稿内容和协作关系都会被清空，确认继续？';
        this.$confirm(confirmMessage, confirmTitle, {
          confirmButtonText: this.isRevisionDraft ? '放弃修订' : '删除',
          cancelButtonText: '取消',
          type: 'warning',
          center: true
        }).then(async () => {
          await this.persistDraftSnapshot(true);
          const res = await this.$http.delete(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}`, {}, true);
          if (res.code === 200) {
            this.destroyDraftSession();
            this.$message.success(this.isRevisionDraft ? '修订草稿已放弃' : '草稿已删除');
            this.$router.push({ path: '/postList' });
          }
        }).catch(() => {});
      },
      async publishDraftAndWait(article) {
        if (!this.canManageCurrentDraft) {
          throw new Error('仅作者或站长可发布当前草稿');
        }
        await this.persistDraftSnapshot(true);
        const payload = {
          article: this.buildArticleRequestPayload(article)
        };
        return this.$http.post(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}/publish`, payload, true);
      },
      async publishDraftAsyncRequest(article) {
        if (!this.canManageCurrentDraft) {
          throw new Error('仅作者或站长可发布当前草稿');
        }
        await this.persistDraftSnapshot(true);
        const payload = {
          article: this.buildArticleRequestPayload(article)
        };
        return this.$http.post(this.$constant.baseURL + `/admin/articleDraft/${this.draftId}/publishAsync`, payload, true);
      },

      async loadSearchPushAvailability() {
        try {
          const res = await this.$http.get(this.$constant.baseURL + '/admin/seo/getSeoConfig', {}, true);
          const config = res && res.code === 200 && res.data ? res.data : {};
          const configuredEngines = [];

          if (config.enable === true) {
            if (config.baidu_push_enabled === true && !this.$common.isEmpty(config.baidu_push_token)) {
              configuredEngines.push('百度');
            }
            if (config.bing_push_enabled === true && !this.$common.isEmpty(config.bing_api_key)) {
              configuredEngines.push('Bing(IndexNow)');
            }
            if (config.yandex_push_enabled === true && !this.$common.isEmpty(config.yandex_api_key)) {
              configuredEngines.push('Yandex');
            }
            if (config.sogou_push_enabled === true && !this.$common.isEmpty(config.sogou_push_token)) {
              configuredEngines.push('搜狗');
            }
            if (config.shenma_push_enabled === true && !this.$common.isEmpty(config.shenma_token)) {
              configuredEngines.push('神马');
            }
          }

          this.searchPushConfiguredEngines = configuredEngines;
          if (configuredEngines.length === 0) {
            this.article.submitToSearchEngine = false;
          }
        } catch (error) {
          console.error('获取搜索引擎推送配置失败:', error);
          this.searchPushConfiguredEngines = [];
          this.article.submitToSearchEngine = false;
        } finally {
          this.searchPushConfigLoading = false;
        }
      },

      async loadSummaryGenerationMode() {
        try {
          const res = await this.$http.get(this.$constant.baseURL + '/webInfo/ai/config/articleAi/get', {}, true);
          const config = res && res.code === 200 && res.data ? res.data : {};
          const summaryConfig = this.parseSummaryConfig(config.summaryConfig);
          this.summaryMode = summaryConfig.summaryMode || 'disabled';
        } catch (error) {
          console.error('获取文章摘要配置失败:', error);
          this.summaryMode = '';
        } finally {
          this.summaryConfigLoading = false;
          this.applySummaryModeToArticle();
        }
      },

      parseSummaryConfig(summaryConfig) {
        if (!summaryConfig) {
          return {};
        }
        if (typeof summaryConfig === 'object') {
          return summaryConfig;
        }
        try {
          return JSON.parse(summaryConfig);
        } catch (error) {
          return {};
        }
      },

      applySummaryModeToArticle() {
        if (this.summaryAutoDisabledByConfig && this.article) {
          this.article.autoSummary = false;
        }
      },

      goToSeoConfig() {
        this.$router.push({ path: '/seoConfig' }).catch(() => {});
      },
      
      // 检查是否有已启用的付费插件
      checkPaymentPlugin() {
        this.$http.get(this.$constant.baseURL + "/sysPlugin/getActivePlugin", { pluginType: 'payment' }, true)
          .then((res) => {
            if (res.data && res.data.enabled) {
              this.paymentPluginActive = true;
              this.paymentPluginName = res.data.pluginName || res.data.pluginKey || '未知';
            } else {
              this.paymentPluginActive = false;
              this.paymentPluginName = '';
            }
          })
          .catch(() => {
            this.paymentPluginActive = false;
            this.paymentPluginName = '';
          });
      },

      // 主编辑器就绪回调
      onMainEditorReady(editor) {
        this.mainEditor = editor;
        this.editorReady = true;
      },
      
      // 主编辑器内容变化处理
      handleEditorChange(value) {
        // Vditor 内置了 Mermaid 支持，无需手动渲染
      },
      
      // 翻译编辑器内容变化处理
      handleTranslationEditorChange(value) {
        // Vditor 内置了 Mermaid 支持，无需手动渲染
      },

      resetTranslationForm() {
        this.translationForm.translatedTitle = '';
        this.translationForm.translatedContent = '';
      },

      restorePendingTranslation(language = this.translationForm.targetLanguage) {
        if (!this.hasPendingTranslation || this.pendingTranslation.language !== language) {
          return false;
        }

        this.translationForm.translatedTitle = this.pendingTranslation.title || '';
        this.translationForm.translatedContent = this.pendingTranslation.content || '';
        return true;
      },

      buildArticleRequestPayload(article) {
        const autoSummary = !this.summaryAutoDisabledByConfig && article.autoSummary !== false;
        const payload = {
          ...article,
          articleSlug: normalizeArticleSlug(article.articleSlug),
          summary: String(article.summary || '').trim(),
          autoSummary,
          skipAiTranslation: this.skipAiTranslation
        };

        if (this.hasPendingTranslation) {
          payload.pendingTranslationTitle = this.pendingTranslation.title;
          payload.pendingTranslationContent = this.pendingTranslation.content;
          payload.pendingTranslationLanguage = this.pendingTranslation.language;
        } else {
          payload.pendingTranslationTitle = null;
          payload.pendingTranslationContent = null;
          payload.pendingTranslationLanguage = null;
        }

        return payload;
      },
      
      // 打开翻译编辑器对话框
      async openTranslationEditor() {
        try {
          // 加载默认目标语言
          await this.loadDefaultTargetLanguage();

          if (this.hasPendingTranslation) {
            this.translationForm.targetLanguage = this.pendingTranslation.language;
          }

          if (!this.restorePendingTranslation()) {
            // 只有在文章已保存（有ID）时才加载已有翻译
            if (!this.$common.isEmpty(this.effectiveArticleId)) {
              await this.loadExistingTranslation();
            } else if (!this.isDraftMode) {
              // 新文章，以空白状态打开
              this.resetTranslationForm();
            }
          }

          // 显示弹窗
          this.shouldRenderTranslationEditor = false;
          this.translationDialogVisible = true;
          this.$nextTick(() => {
            this.shouldRenderTranslationEditor = true;
          });
          
          // Vditor 编辑器将在 ready 事件中自动初始化
        } catch (error) {
          console.error('打开翻译编辑器失败:', error);
          this.$message.error('打开翻译编辑器失败: ' + error.message);
        }
      },
      

      async loadDefaultTargetLanguage() {
        try {
          // 从Java API获取默认语言
          const response = await this.$http.get(this.$constant.baseURL + "/webInfo/ai/config/articleAi/defaultLang");
          if (response.code === 200 && response.data) {
            this.translationForm.targetLanguage = response.data.default_target_lang || 'en';
          }
        } catch (error) {
          this.translationForm.targetLanguage = 'en';
        }
      },

      async loadExistingTranslation() {
        const articleId = this.effectiveArticleId;
        // 确保有文章ID才执行数据库查询
        if (this.$common.isEmpty(articleId)) {
          this.resetTranslationForm();
          return;
        }

        try {
          const response = await this.$http.get(this.$constant.baseURL + "/article/getTranslation", {
            id: articleId,
            language: this.translationForm.targetLanguage
          });

          if (response.code === 200 && response.data && response.data.status === 'success') {
            this.translationForm.translatedTitle = response.data.title || '';
            this.translationForm.translatedContent = response.data.content || '';
          } else {
            // 该语言没有翻译内容，清空表单
            this.resetTranslationForm();
          }
        } catch (error) {
          // 加载失败，清空表单
          this.resetTranslationForm();
        }
      },

      async onTargetLanguageChange(newLanguage) {
        try {
          // 更新系统默认目标语言
          await this.updateDefaultTargetLanguage(newLanguage);

          if (this.restorePendingTranslation(newLanguage)) {
            return;
          }

          // 只有在文章已保存（有ID）时才加载翻译内容
          if (!this.$common.isEmpty(this.effectiveArticleId)) {
            await this.loadExistingTranslation();
          } else {
            // 新文章或无ID，清空翻译表单
            this.resetTranslationForm();
          }
        } catch (error) {
          console.error('切换目标语言失败:', error);
          this.$message.error('切换目标语言失败: ' + error.message);
        }
      },

      async updateDefaultTargetLanguage(targetLanguage) {
        try {
          // 获取当前完整配置
          const getResponse = await this.$http.get(this.$constant.baseURL + "/webInfo/ai/config/translation/get");
          if (getResponse.code !== 200 || !getResponse.data) {
            this.$message.warning('获取翻译配置失败');
            return;
          }
          
          // 更新目标语言
          const config = getResponse.data;
          config.defaultTargetLang = targetLanguage;
          
          // 保存配置
          const response = await this.$http.post(this.$constant.baseURL + "/webInfo/ai/config/translation/save", config);

          if (response.code === 200) {
            this.$message.success('默认目标语言已更新为: ' + this.getLanguageName(targetLanguage));
          }
        } catch (error) {
        }
      },

      // 使用统一的后台管理语言映射工具（中文）
      getLanguageName: getAdminLanguageName,

      async saveTranslation() {
        // 验证表单
        if (!this.translationForm.translatedTitle.trim()) {
          this.$message.warning('请输入翻译标题');
          return;
        }

        if (!this.translationForm.translatedContent.trim()) {
          this.$message.warning('请输入翻译内容');
          return;
        }

        // 暂存翻译数据
        this.pendingTranslation = {
          title: this.translationForm.translatedTitle.trim(),
          content: this.translationForm.translatedContent.trim(),
          language: this.translationForm.targetLanguage
        };

        // 自动开启跳过AI翻译开关
        this.skipAiTranslation = true;

        // 显示成功消息
        this.$message.success('翻译内容已暂存，请保存文章以应用翻译');

        // 关闭弹窗
        this.closeTranslationDialog();
      },

      closeTranslationDialog() {
        this.translationDialogVisible = false;
        this.shouldRenderTranslationEditor = false;
        if (!this.isDraftMode) {
          this.resetTranslationForm();
        }
      },

      // 清空暂存的翻译数据
      clearPendingTranslation() {
        this.pendingTranslation = {
          title: '',
          content: '',
          language: ''
        };
      },

      // 移动端表单布局适配相关方法
      initMobileFormLayout() {
        this.$nextTick(() => {
          this.updateFormLabelPosition();
        });
      },

      handleWindowResize() {
        // 防抖处理
        if (this.resizeTimer) {
          clearTimeout(this.resizeTimer);
        }
        this.resizeTimer = setTimeout(() => {
          this.updateFormLabelPosition();
        }, 300);
      },

      updateFormLabelPosition() {
        const form = this.$refs.ruleForm;
        if (!form || !form.$el) return;

        const isMobile = window.innerWidth <= 768;
        
        if (isMobile) {
          form.$el.classList.add('el-form--label-top');
          form.$el.classList.remove('el-form--label-left');
        } else {
          form.$el.classList.add('el-form--label-left');
          form.$el.classList.remove('el-form--label-top');
        }
      },
      
      // 获取分类和标签
      // reloadArticle: 是否重新加载文章（默认true，创建分类/标签时传false）
      getSortAndLabel(reloadArticle = true) {
        return this.$http.get(this.$constant.baseURL + "/webInfo/listSortAndLabel")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.sorts = res.data.sorts;
              this.labels = res.data.labels;
              
              // 只在初始加载时重新获取文章，创建分类/标签时不重新加载
              if (reloadArticle && !this.$common.isEmpty(this.id)) {
                this.getArticleById();
              }
              return res.data;
            }
          })
          .catch((error) => {
            this.showError("获取分类和标签失败", error);
            throw error;
          });
      },
      
      // 获取当前分类名称
      getCurrentSortName() {
        if (this.article.sortId && this.sorts.length > 0) {
          const sort = this.sorts.find(s => s.id === this.article.sortId);
          return sort ? sort.sortName : '';
        }
        return '';
      },
      
      // 根据ID获取文章
      getArticleById(options = {}) {
        const { checkTranslationStatus = true } = options;
        return this.$http.get(this.$constant.baseURL + "/admin/article/getArticleById", {id: this.id})
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.article = {
                ...createDefaultArticle(),
                ...res.data,
                articleSlug: res.data.articleSlug || '',
                summary: res.data.summary || '',
                autoSummary: this.summaryAutoDisabledByConfig ? false : res.data.autoSummary !== false
              };
              this.applySummaryModeToArticle();
              // 检查文章是否有手动编辑的翻译，如果有则自动进入编辑翻译模式
              if (checkTranslationStatus) {
                this.checkAndSetTranslationMode();
              }
            }
            return res.data;
          })
          .catch((error) => {
            this.showError("获取文章失败", error);
            throw error;
          });
      },
      
      // 检查并设置翻译模式
      checkAndSetTranslationMode() {
        const articleId = this.effectiveArticleId;
        if (this.$common.isEmpty(articleId)) {
          return;
        }
        // 检查文章是否有可用的翻译语言
        this.$http.get(this.$constant.baseURL + "/article/getAvailableLanguages", {id: articleId})
          .then((res) => {
            if (res.code === 200 && res.data && res.data.length > 0) {
              // 如果文章有翻译，自动开启跳过AI翻译
              this.skipAiTranslation = true;
              
              // 显示提示信息
              this.$message({
                message: `检测到文章已有翻译版本（${res.data.join(', ')}），已自动开启跳过AI翻译`,
                type: 'info',
                duration: 3000
              });
            }
          })
          .catch((error) => {
            // 检查失败不影响正常编辑
          });
      },
      
      // 保存并等待（同步版本，阻塞等待所有任务完成）
      submitForm(formName) {
        this.syncMainEditorContent()
        this.$refs[formName].validate((valid) => {
          if (valid) {
            if (!this.$common.isEmpty(this.id)) {
              this.article.id = this.id;
            }
            
            // 使用同步接口，在当前页面等待所有任务完成
            this.saveArticleAndWait(this.article);
          }
        });
      },
      
      // 保存并离开（异步版本）
      submitFormAsync(formName) {
        this.syncMainEditorContent()
        this.$refs[formName].validate((valid) => {
          if (valid) {
            if (!this.$common.isEmpty(this.id)) {
              this.article.id = this.id;
            }
            
            this.saveArticleAsync(this.article);
          }
        });
      },
      
      // 同步保存文章
      saveArticle(article, url) {
        const actionText = this.isRevisionDraft ? '发布修订' : this.isDraftMode ? '发布' : '保存';
        const successText = this.isRevisionDraft ? '修订发布' : `文章${actionText}`;
        this.$confirm(`确认${actionText}文章？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          // 显示加载中
          this.startLoading(`${actionText}文章中...`);
          const payload = this.buildArticleRequestPayload(article);

          // 发送保存请求
          const publishRequest = this.isDraftMode
            ? this.publishDraftAndWait(article)
            : this.$http.post(this.$constant.baseURL + url, payload, true)
          publishRequest.then(async res => {
              this.stopLoading();
              
              // 记录完整响应用于调试
              
              // 检查保存是否成功
              if (res.code === 200 || res.data === true) {
                // 显示成功通知
                this.$message({
                  message: `${successText}成功，翻译将在后台自动完成`,
                  type: 'success',
                  duration: 3000,
                  offset: 20
                });
                
                // 发布全局事件，通知首页刷新文章列表
                this.$root.$emit('articleSaved');
                
                // SEO推送提示（现在由后端异步处理）
                if (article.viewStatus && article.submitToSearchEngine) {
                  this.$message({
                    message: `${successText}成功，搜索引擎推送将在后台自动处理`,
                    type: 'info',
                    duration: 3000,
                    offset: 80
                  });
                }
                
                // 清空暂存翻译数据
                this.clearPendingTranslation();

                // 更新ID以便后续编辑
                if (!this.id && res.data) {
                  this.id = res.data;
                  this.$router.replace({
                    path: "/postEdit",
                    query: {id: this.id}
                  });
                }
              } else {
                // 处理保存失败的情况
                this.handleSaveError(res);
              }
            })
            .catch(error => {
              this.stopLoading();
              console.error(`${actionText}文章网络请求失败:`, error);
              this.showError(`${actionText}失败`, error.message || "网络请求错误");
            });
        }).catch(() => {
          // 用户取消保存，无需任何操作
        });
      },
      
      // 保存文章并等待完成（使用同步接口）
      saveArticleAndWait(article) {
        const actionText = this.isRevisionDraft ? '发布修订' : this.isDraftMode ? '发布' : '保存';
        const actionAndWaitText = this.isRevisionDraft ? '发布修订并等待' : this.isDraftMode ? '发布并等待' : '保存并等待';
        const successText = this.isRevisionDraft ? '修订发布' : `文章${actionText}`;
        const confirmMessage = this.isRevisionDraft
          ? '修订草稿将发布并覆盖正式文章，请等待所有处理完成后再进行其他操作。'
          : this.isDraftMode
            ? '草稿将被发布为文章，请等待所有处理完成后再进行其他操作。'
            : '文章将被保存，请等待所有处理完成后再进行其他操作。';
        this.$confirm(confirmMessage, `确认${actionAndWaitText}`, {
          confirmButtonText: actionText,
          cancelButtonText: '取消',
          type: 'info',
          center: true
        }).then(() => {
          // 显示加载状态
          this.startLoading(`正在${actionText}文章...`);
          
          // 记录保存请求数据
          
          // 根据是否有id选择不同的同步接口
          let url = this.$common.isEmpty(this.id)
            ? "/article/saveArticle"
            : "/article/updateArticle";
          const payload = this.buildArticleRequestPayload(article);

          // 发送同步保存请求
          const publishRequest = this.isDraftMode
            ? this.publishDraftAndWait(article)
            : this.$http.post(this.$constant.baseURL + url, payload, true);
          publishRequest
            .then(async res => {
              // 记录响应
              
              // 检查保存是否成功
              if (res.code === 200) {
                this.stopLoading();
                
                // 显示成功通知
                this.$message({
                  message: `${successText}成功！所有任务已完成（翻译、摘要生成等）`,
                  type: 'success',
                  duration: 3000,
                  offset: 20
                });
                
                // 发布全局事件，通知首页刷新文章列表
                this.$root.$emit('articleSaved');
                
                // 清空暂存翻译数据
                this.clearPendingTranslation();

                if (this.isDraftMode) {
                  this.destroyDraftSession();
                }

                // 延迟跳转到文章列表，给用户时间看到成功提示
                setTimeout(() => {
                  this.$router.push({path: "/postList"});
                }, 1500);
              } else {
                this.stopLoading();
                // 处理保存失败的情况
                console.error(`${actionText}失败:`, res);
                this.handleSaveError(res, `${actionText}失败`);
              }
            })
            .catch(error => {
              this.stopLoading();
              console.error(`${actionText}请求失败:`, error);
              this.showError(`${actionText}失败`, error.message || "网络请求错误");
            });
        }).catch(() => {
          // 用户取消
        });
      },
      
      // 更新 loading 提示文本
      updateLoadingText(text) {
        if (this.loading) {
          this.loading.text = text;
        }
      },
      
      // 显示 loading
      startLoading(text = '加载中...') {
        if (this.loading) {
          this.loading.close();
        }
        this.loading = this.$loading({
          lock: true,
          text,
          spinner: 'el-icon-loading',
          background: 'rgba(0, 0, 0, 0.7)'
        });
      },
      
      // 停止 loading
      stopLoading() {
        if (this.loading) {
          this.loading.close();
          this.loading = null;
        }
      },
      
      // 显示错误消息
      showError(title, error) {
        const errorMessage = typeof error === 'string'
          ? error
          : (error && error.message ? error.message : '未知错误');

        console.error(`${title}:`, error);

        this.$message({
          message: `${title}: ${errorMessage}`,
          type: 'error',
          duration: 5000,
          offset: 50
        });
      },
      
      // 处理保存错误
      handleSaveError(res, title = '保存失败') {
        console.error(`${title}，响应:`, res);

        const errorMessage = res && (res.message || res.msg)
          ? (res.message || res.msg)
          : typeof (res && res.data) === 'string'
            ? res.data
            : res && res.data && res.data.message
              ? res.data.message
              : '服务器返回未知错误';

        this.showError(title, errorMessage);
      },
      
      // 异步保存文章
      saveArticleAsync(article) {
        const actionText = this.isRevisionDraft ? '发布修订' : this.isDraftMode ? '发布' : '保存';
        const actionAndLeaveText = this.isRevisionDraft ? '发布修订并离开' : this.isDraftMode ? '发布并离开' : '保存并离开';
        const confirmMessage = this.isRevisionDraft
          ? '修订草稿将在后台发布并更新正式文章，您可以立即返回文章列表，发布状态会显示在右侧通知中。'
          : this.isDraftMode
            ? '草稿将在后台发布为文章，您可以立即返回文章列表，发布状态会显示在右侧通知中。'
            : '文章将在后台保存，您可以立即返回文章列表，保存状态会显示在右侧通知中。';
        this.$confirm(confirmMessage, `确认异步${actionText}`, {
          confirmButtonText: actionAndLeaveText,
          cancelButtonText: '取消',
          type: 'info',
          center: true
        }).then(() => {
          this.asyncSaveLoading = true;
          
          // 记录保存请求数据
          
          // 根据是否有id选择不同的异步接口
          let url = this.$common.isEmpty(this.id)
            ? "/article/saveArticleAsync"
            : "/article/updateArticleAsync";
          const payload = this.buildArticleRequestPayload(article);

          // 发送异步保存请求
          const publishRequest = this.isDraftMode
            ? this.publishDraftAsyncRequest(article)
            : this.$http.post(this.$constant.baseURL + url, payload, true)
          publishRequest.then(async res => {
              this.asyncSaveLoading = false;
              
              // 记录响应
              
              if (res.code === 200 && res.data) {
                // 获取任务ID
                this.currentTaskId = res.data;
                
                // 发布全局事件，通知首页刷新文章列表
                this.$root.$emit('articleSaved');
                
                // 添加通知（会自动启动轮询）
                this.$notify.loading(`${actionText}文章`, `正在${actionText}文章，请稍候...`, this.currentTaskId);

                // 清空暂存翻译数据
                this.clearPendingTranslation();

                if (this.isDraftMode) {
                  this.destroyDraftSession();
                }

                // 延迟跳转，确保全局通知组件已接管轮询
                setTimeout(() => {
                  this.$router.push({path: "/postList"});
                }, 1000);
              } else {
                console.error(`异步${actionText}失败:`, res);
                this.handleSaveError(res, `${actionText}失败`);
              }
            })
            .catch(error => {
              this.asyncSaveLoading = false;
              console.error(`异步${actionText}请求失败:`, error);
              this.showError(`启动异步${actionText}失败`, error.message || "网络请求错误");
            });
        }).catch(() => {
          // 用户取消
        });
      },
      
      // 文件上传处理（适配 Vditor 和自研编辑器）
      imgAdd(payload) {
        try {
          const uploadPayload = payload && payload.file ? payload : { file: payload, uploadId: null };
          const file = uploadPayload.file;
          const uploadId = uploadPayload.uploadId || null;
          const privateAttachment = !!uploadPayload.privateAttachment;
          const forceAttachment = !!uploadPayload.forceAttachment || privateAttachment;
          if (!file) {
            this.showError("文件上传准备失败", "未获取到上传文件");
            return;
          }

          const isImage = !forceAttachment && this.isImageFile(file);
          const isVideo = !forceAttachment && this.isVideoFile(file);
          // 显示上传中提示
          const loadingMessage = this.$message({
            message: privateAttachment ? '正在上传私有附件...' : (isImage ? '正在上传图片...' : (isVideo ? '正在上传视频...' : '正在上传文件...')),
            type: 'info',
            iconClass: 'el-icon-loading',
            duration: 0, // 不自动关闭
            showClose: true
          });

          let suffix = file.name.lastIndexOf('.') !== -1 ? file.name.substring(file.name.lastIndexOf('.')) : "";
          const resourceType = isImage ? "articlePicture" : (isVideo ? "video/article" : "articleFile");
          let key = resourceType + "/" + this.mainStore.currentAdmin.username.replace(/[^a-zA-Z]/g, '')
                    + this.mainStore.currentAdmin.id + new Date().getTime() 
                    + Math.floor(Math.random() * 1000) + suffix;

          // 获取当前存储类型，优先使用更新后的配置
          // 图床类存储通常只接受图片；文章附件和视频统一走本地存储，私有附件仅通过前端 u/ 标记拦截。
          let storeType = isImage ? (this.currentStoreType || this.mainStore.sysConfig['store.type'] || "local") : "local";
          const uploadOptions = {
            privateAttachment,
            forceAttachment
          };

          let fd = new FormData();
          fd.append("file", file);
          fd.append("originalName", file.name);
          fd.append("key", key);
          fd.append("relativePath", key);
          fd.append("type", resourceType);
          fd.append("storeType", storeType);

          if (storeType === "local") {
            this.saveLocal(fd, loadingMessage, uploadId, uploadOptions);
          } else if (storeType === "qiniu") {
            this.saveQiniu(fd, loadingMessage, uploadId, uploadOptions);
          } else if (storeType === "lsky") {
            this.saveLsky(fd, loadingMessage, uploadId, uploadOptions);
          } else if (storeType === "easyimage") {
            this.saveLsky(fd, loadingMessage, uploadId, uploadOptions);
          }
        } catch (error) {
          this.showError("文件上传准备失败", error);
        }
      },
      
      // 本地保存文件
      async saveLocal(fd, loadingMessage, uploadId, uploadOptions = {}) {
        const file = fd.get("file");
        const isVideo = !uploadOptions.forceAttachment && this.isVideoFile(file);
        try {
          const res = isVideo
            ? await this.saveLocalVideoInChunks(fd, loadingMessage)
            : await this.$http.upload(this.$constant.baseURL + "/resource/upload", fd, true);

          if (loadingMessage) loadingMessage.close();

          if (!this.$common.isEmpty(res.data)) {
            this.insertUploadedFile(res.data, file, uploadId, uploadOptions);
            const isImage = !uploadOptions.forceAttachment && this.isImageFile(file);
            this.$message.success(isImage ? '图片上传成功' : (isVideo ? '视频上传成功' : '文件上传成功'));
          } else {
            this.rejectImageUpload(uploadId);
            this.showError("文件上传失败", "服务器未返回有效的文件URL");
          }
        } catch (error) {
          if (loadingMessage) loadingMessage.close();
          this.rejectImageUpload(uploadId);
          this.showError(isVideo ? "视频分片上传失败" : "文件本地上传失败", error);
        }
      },

      async saveLocalVideoInChunks(fd, loadingMessage) {
        const file = fd.get("file");
        const chunkSize = this.getVideoChunkSize(file.size || 0);
        const totalChunks = Math.max(1, Math.ceil((file.size || 0) / chunkSize));
        const uploadId = this.createChunkUploadId();

        for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
          const start = chunkIndex * chunkSize;
          const end = Math.min(start + chunkSize, file.size);
          const chunk = file.slice(start, end);
          const chunkFd = new FormData();
          chunkFd.append("chunk", chunk, file.name + ".part" + chunkIndex);
          chunkFd.append("uploadId", uploadId);
          chunkFd.append("chunkIndex", String(chunkIndex));
          chunkFd.append("totalChunks", String(totalChunks));

          const chunkResult = await this.$http.upload(this.$constant.baseURL + "/resource/uploadChunk", chunkFd, true, {
            timeout: 60000,
            onProgress: () => {}
          });
          this.ensureUploadSuccess(chunkResult, "视频分片上传失败");

          if (loadingMessage) {
            loadingMessage.message = "视频上传中 " + (chunkIndex + 1) + "/" + totalChunks;
          }
        }

        const mergeFd = new FormData();
        mergeFd.append("uploadId", uploadId);
        mergeFd.append("totalChunks", String(totalChunks));
        mergeFd.append("originalName", fd.get("originalName") || file.name);
        mergeFd.append("relativePath", fd.get("relativePath"));
        mergeFd.append("type", fd.get("type"));
        mergeFd.append("contentType", this.getVideoMimeType(file) || file.type || "application/octet-stream");

        if (loadingMessage) {
          loadingMessage.message = "视频处理中，请稍候";
        }

        const mergeResult = await this.$http.upload(this.$constant.baseURL + "/resource/mergeChunks", mergeFd, true, {
          timeout: 300000,
          onProgress: () => {}
        });
        this.ensureUploadSuccess(mergeResult, "视频合并失败");
        return mergeResult;
      },

      getVideoChunkSize(fileSize) {
        if (fileSize <= 2 * 1024 * 1024) {
          return 8 * 1024;
        }
        if (fileSize <= 30 * 1024 * 1024) {
          return 64 * 1024;
        }
        return 256 * 1024;
      },

      createChunkUploadId() {
        return "v_" + Date.now().toString(36) + "_" + Math.random().toString(36).slice(2, 12);
      },

      ensureUploadSuccess(result, fallbackMessage) {
        if (!result || (result.code && result.code !== 200) || result.success === false) {
          throw new Error((result && result.message) || fallbackMessage || "上传失败");
        }
      },
      
      isImageFile(file) {
        if (!file) {
          return false;
        }
        return String(file.type || '').startsWith('image/') || /\.(jpg|jpeg|png|gif|bmp|webp|tiff|tif|psd|svg|ico)$/i.test(file.name || '');
      },

      isVideoFile(file) {
        if (!file) {
          return false;
        }
        return String(file.type || '').startsWith('video/') || /\.(mp4|webm|ogg|ogv|mov|m4v)$/i.test(file.name || '');
      },

      createUploadedFileMarkdown(url, file, uploadOptions = {}) {
        const isImage = !uploadOptions.forceAttachment && this.isImageFile(file);
        const isVideo = !uploadOptions.forceAttachment && this.isVideoFile(file);
        let fullUrl = url;
        if ((isImage || isVideo) && url.startsWith('/')) {
          // 开发环境（非生产模式）：前后端端口不同，需要完整URL
          // 生产环境：前后端同域，使用相对路径由Nginx代理
          if (!import.meta.env.PROD) {
            fullUrl = this.$constant.baseURL + url;
          }
        }
        
        // 过滤文件名中的括号，防止破坏Markdown语法
        const safeFilename = (file && file.name ? file.name : '文件').replace(/[\[\]\(\)]/g, '');
        if (isImage) {
          return `![${safeFilename}](${fullUrl})\n`;
        }
        if (isVideo) {
          return this.createUploadedVideoHtml(fullUrl, safeFilename, file);
        }
        return createAttachmentMarkdown(safeFilename, url, {
          privateAttachment: !!uploadOptions.privateAttachment
        });
      },

      createUploadedVideoHtml(url, fileName, file) {
        const safeUrl = this.escapeHtmlAttribute(url);
        const safeTitle = this.escapeHtmlAttribute(fileName || '视频');
        const type = this.getVideoMimeType(file);
        const typeAttr = type ? ` type="${this.escapeHtmlAttribute(type)}"` : '';
        return `<div class="poetize-video-card" data-title="${safeTitle}">
  <video class="poetize-video-player" src="${safeUrl}"${typeAttr} controls preload="metadata" playsinline></video>
  <span class="poetize-video-play-overlay" aria-hidden="true"></span>
</div>\n`;
      },

      getVideoMimeType(file) {
        const mimeType = String((file && file.type) || '').toLowerCase();
        if (mimeType.startsWith('video/')) {
          return mimeType;
        }
        const name = String((file && file.name) || '').toLowerCase();
        if (name.endsWith('.webm')) return 'video/webm';
        if (name.endsWith('.ogv') || name.endsWith('.ogg')) return 'video/ogg';
        if (name.endsWith('.mov')) return 'video/quicktime';
        if (name.endsWith('.m4v')) return 'video/x-m4v';
        if (name.endsWith('.mp4')) return 'video/mp4';
        return '';
      },

      escapeHtmlAttribute(value) {
        return String(value || '')
          .replace(/&/g, '&amp;')
          .replace(/"/g, '&quot;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;');
      },

      // 插入上传结果到编辑器
      insertUploadedFile(url, file, uploadId = null, uploadOptions = {}) {
        const markdown = this.createUploadedFileMarkdown(url, file, uploadOptions);
        
        if (this.$refs.md) {
          if (uploadId && typeof this.$refs.md.resolveImageUpload === 'function') {
            const inserted = this.$refs.md.resolveImageUpload(uploadId, markdown);
            if (inserted) {
              return;
            }
          }
          this.$refs.md.insertValue(markdown);
        }
      },
      rejectImageUpload(uploadId) {
        if (!uploadId || !this.$refs.md || typeof this.$refs.md.rejectImageUpload !== 'function') {
          return;
        }
        this.$refs.md.rejectImageUpload(uploadId);
      },
      
      // 七牛云保存图片
      saveQiniu(fd, loadingMessage, uploadId, uploadOptions = {}) {
        this.$http.get(this.$constant.baseURL + "/qiniu/getUpToken", {key: fd.get("key")}, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              fd.append("token", res.data);

              this.$http.uploadQiniu(this.mainStore.sysConfig.qiniuUrl, fd)
                .then((res) => {
                  // 关闭上传中提示
                  if (loadingMessage) loadingMessage.close();
                  
                  if (!this.$common.isEmpty(res.key)) {
                    let url = this.mainStore.sysConfig['qiniu.downloadUrl'] + res.key;
                    let file = fd.get("file");
                    let resourceType = fd.get("type") || (this.isImageFile(file) ? "articlePicture" : (this.isVideoFile(file) ? "video/article" : "articleFile"));
                    this.$common.saveResource(this, resourceType, url, file.size, file.type, file.name, "qiniu", true);
                    this.insertUploadedFile(url, file, uploadId, uploadOptions);
                    const isImage = !uploadOptions.forceAttachment && this.isImageFile(file);
                    const isVideo = !uploadOptions.forceAttachment && this.isVideoFile(file);
                    this.$message.success(isImage ? '图片上传成功' : (isVideo ? '视频上传成功' : '文件上传成功'));
                  } else {
                    this.rejectImageUpload(uploadId);
                    this.showError("七牛云上传失败", "未返回有效的文件密钥");
                  }
                })
                .catch((error) => {
                  // 关闭上传中提示
                  if (loadingMessage) loadingMessage.close();
                  this.rejectImageUpload(uploadId);
                  this.showError("七牛云上传请求失败", error);
                });
            } else {
              // 关闭上传中提示
              if (loadingMessage) loadingMessage.close();
              this.rejectImageUpload(uploadId);
              this.showError("获取七牛云上传Token失败", "服务器未返回有效的Token");
            }
          })
          .catch((error) => {
            // 关闭上传中提示
            if (loadingMessage) loadingMessage.close();
            this.rejectImageUpload(uploadId);
            this.showError("获取七牛云上传Token失败", error);
          });
      },
      
      // 兰空图床保存图片
      saveLsky(fd, loadingMessage, uploadId, uploadOptions = {}) {
        this.$http.post(this.$constant.baseURL + "/resource/upload", fd, true)
          .then((res) => {
            // 关闭上传中提示
            if (loadingMessage) loadingMessage.close();
            
            if (!this.$common.isEmpty(res.data)) {
              // 获取返回的图片URL
              let url = res.data;
              let file = fd.get("file");
              let storeType = fd.get("storeType") || "lsky";
              let resourceType = fd.get("type") || (this.isImageFile(file) ? "articlePicture" : (this.isVideoFile(file) ? "video/article" : "articleFile"));
              this.$common.saveResource(this, resourceType, url, file.size, file.type, file.name, storeType, true);
              this.insertUploadedFile(url, file, uploadId, uploadOptions);
              const isImage = !uploadOptions.forceAttachment && this.isImageFile(file);
              const isVideo = !uploadOptions.forceAttachment && this.isVideoFile(file);
              this.$message.success(isImage ? '图片上传成功' : (isVideo ? '视频上传成功' : '文件上传成功'));
            } else {
              this.rejectImageUpload(uploadId);
              this.showError("图床上传失败", "服务器未返回有效的文件URL");
            }
          })
          .catch((error) => {
            // 关闭上传中提示
            if (loadingMessage) loadingMessage.close();
            this.rejectImageUpload(uploadId);
            this.showError("图床上传失败", error);
          });
      },
      
      // 添加文章封面（兼容两种命名）
      addCover() {
        this.$refs.uploadPicture.change(1);
      },
      
      addArticleCover(res) {
        this.article.articleCover = res;
      },
      
      // 重置表单
      resetForm(formName) {
        this.$refs[formName].resetFields();
        if (!this.$common.isEmpty(this.id)) {
          this.getArticleById();
        }
      },
      
      // 处理系统配置更新事件
      handleSysConfigUpdate(config) {
        if (config && config['store.type']) {
          this.currentStoreType = config['store.type'];
        }
      },
      
      // 创建新分类
      createNewSort() {
        this.$refs.newSortForm.validate((valid) => {
          if (valid) {
            this.newSortLoading = true;
            this.$http.post(this.$constant.baseURL + "/webInfo/saveSort", this.newSortForm)
              .then((res) => {
                this.newSortLoading = false;
                if (res.code === 200) {
                  this.$message.success('分类创建成功');
                  
                  // 保存新分类名称
                  const newSortName = this.newSortForm.sortName;
                  
                  // 重新获取分类列表并自动选中新创建的分类
                  // 传入false，不重新加载文章
                  this.getSortAndLabel(false).then(() => {
                    // 自动选中新创建的分类
                    const newSort = this.sorts.find(sort => sort.sortName === newSortName);
                    if (newSort) {
                      // 使用$nextTick确保在下一个tick中设置，避免watch干扰
                      this.$nextTick(() => {
                        this.article.sortId = newSort.id;
                        // 手动更新labelsTemp
                        this.labelsTemp = this.labels.filter(l => l.sortId === newSort.id);
                      });
                    } else {
                    }
                  });
                  
                  // 关闭对话框
                  this.cancelNewSort();
                }
              })
              .catch((error) => {
                this.newSortLoading = false;
                this.showError("创建分类失败", error);
              });
          }
        });
      },
      
      // 创建新标签
      createNewLabel() {
        this.$refs.newLabelForm.validate((valid) => {
          if (valid) {
            this.newLabelLoading = true;
            const labelData = {
              ...this.newLabelForm,
              sortId: this.article.sortId
            };
            
            // 保存标签名称，用于后续查找（防止对话框关闭后数据丢失）
            const createdLabelName = labelData.labelName;
            const createdSortId = labelData.sortId;
            
            this.$http.post(this.$constant.baseURL + "/webInfo/saveLabel", labelData)
              .then((res) => {
                this.newLabelLoading = false;
                if (res.code === 200) {
                  this.$message.success('标签创建成功');
                  
                  // 关闭对话框（在重新加载数据之前关闭）
                  this.cancelNewLabel();
                  
                  // 重新获取分类和标签列表并自动选中新创建的标签
                  // 传入false，不重新加载文章
                  this.getSortAndLabel(false).then(() => {
                    
                    // 使用$nextTick确保DOM更新完成
                    this.$nextTick(() => {
                      // 强制刷新labelsTemp，确保下拉框显示新标签
                      this.labelsTemp = this.labels.filter(l => l.sortId === createdSortId);
                      
                      // 自动选中新创建的标签（使用保存的值）
                      const newLabel = this.labels.find(label => 
                        label.labelName === createdLabelName && 
                        label.sortId === createdSortId
                      );
                      
                      if (newLabel) {
                        // 延迟设置，确保不被watch干扰
                        setTimeout(() => {
                          this.article.labelId = newLabel.id;
                        }, 100);
                      } else {
                      }
                    });
                  });
                }
              })
              .catch((error) => {
                this.newLabelLoading = false;
                this.showError("创建标签失败", error);
              });
          }
        });
      },
      
      // 辅助方法：显示成功通知
      showSuccess(title, message) {
        this.$message({
          message: message,
          type: 'success',
          offset: 50
        });
      },
      
      // 处理分类选择变化
      handleSortChange(value) {
        if (value === 'new-sort') {
          // 重置分类选择
          this.article.sortId = null;
          // 打开新建分类对话框
          this.openNewSortDialog();
        }
      },
      
      // 处理标签选择变化
      handleLabelChange(value) {
        if (value === 'new-label') {
          // 重置标签选择
          this.article.labelId = null;
          // 打开新建标签对话框
          this.openNewLabelDialog();
        }
      },
      
      // 打开新建分类对话框
      openNewSortDialog() {
        this.newSortForm = {
          sortName: '',
          sortDescription: '',
          priority: 1,
          sortType: 0  // 默认为导航栏分类，会显示在侧边栏"速览"中
        };
        this.newSortDialog = true;
        // 清除表单验证
        this.$nextTick(() => {
          if (this.$refs.newSortForm) {
            this.$refs.newSortForm.clearValidate();
          }
        });
      },
      
      // 取消新建分类
      cancelNewSort() {
        this.newSortDialog = false;
        this.newSortForm = {
          sortName: '',
          sortDescription: '',
          priority: 1,
          sortType: 0
        };
      },
      
      // 打开新建标签对话框
      openNewLabelDialog() {
        if (!this.article.sortId) {
          this.$message({
            message: '请先选择分类',
            type: 'warning'
          });
          return;
        }
        
        this.newLabelForm = {
          labelName: '',
          labelDescription: '',
          sortId: this.article.sortId
        };
        this.newLabelDialog = true;
        // 清除表单验证
        this.$nextTick(() => {
          if (this.$refs.newLabelForm) {
            this.$refs.newLabelForm.clearValidate();
          }
        });
      },
      
      // 取消新建标签
      cancelNewLabel() {
        this.newLabelDialog = false;
        this.newLabelForm = {
          labelName: '',
          labelDescription: '',
          sortId: null
        };
      },
      
      
    }
  }
</script>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  margin: 12px 0 18px;
}

.draft-inline-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  max-width: 100%;
}

:deep(.draft-inline-tag.el-tag) {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  min-height: 40px;
  line-height: 1.5;
  overflow: visible;
  white-space: normal;
}

.draft-inline-spacer {
  flex: 1 1 auto;
  min-width: 12px;
}

@media (max-width: 768px) {
  :deep(.draft-inline-tag.el-tag) {
    min-height: 36px;
    padding-top: 8px;
    padding-bottom: 8px;
  }

  .draft-inline-tag {
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 6px;
    line-height: 1.6;
  }

  .draft-inline-spacer {
    flex-basis: 100%;
    width: 100%;
    min-width: 0;
    height: 0;
  }

  .draft-inline-divider:first-of-type {
    display: none;
  }

  .draft-inline-chip,
  .draft-inline-meta,
  :deep(.draft-collaborator-inline-button.el-button--mini.is-plain) {
    margin-left: 0;
  }

  .draft-inline-meta {
    max-width: 100%;
    word-break: break-all;
  }
}

@media (max-width: 480px) {
  :deep(.draft-inline-tag.el-tag) {
    min-height: 32px;
    padding-top: 7px;
    padding-bottom: 7px;
  }
}

.draft-inline-divider {
  width: 1px;
  height: 14px;
  background: rgba(0, 0, 0, 0.16);
}

.draft-inline-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 20px;
  color: var(--black);
  background: rgba(0, 0, 0, 0.06);
}

.draft-inline-chip[data-type='success'] {
  background: rgba(103, 194, 58, 0.18);
}

.draft-inline-chip[data-type='warning'] {
  background: rgba(230, 162, 60, 0.2);
}

.draft-inline-chip[data-type='danger'] {
  background: rgba(245, 108, 108, 0.18);
}

.draft-inline-chip[data-type='info'] {
  background: rgba(144, 147, 153, 0.18);
}

.draft-inline-meta {
  color: rgba(0, 0, 0, 0.72);
  font-size: 12px;
  line-height: 1;
}

.draft-collaborator-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.draft-collaborator-title {
  font-size: 12px;
  color: #606266;
}

.draft-online-users {
  font-size: 12px;
  color: #67c23a;
  line-height: 1.4;
}

.draft-editing-users {
  font-size: 12px;
  color: #e6a23c;
  line-height: 1.4;
}

.draft-invite-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.draft-invite-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.draft-collaborator-select {
  width: 100%;
}

.draft-collaborator-button {
  padding: 5px 10px;
}

:deep(.draft-collaborator-inline-button.el-button--mini.is-plain) {
  height: 22px;
  padding: 0 8px;
  border-color: rgba(0, 0, 0, 0.18);
  background: rgba(0, 0, 0, 0.06);
  color: var(--black);
}

:deep(.draft-collaborator-inline-button.el-button--mini.is-plain:hover),
:deep(.draft-collaborator-inline-button.el-button--mini.is-plain:focus) {
  border-color: rgba(0, 0, 0, 0.28);
  background: rgba(0, 0, 0, 0.1);
  color: var(--black);
}

:deep(.draft-collaborator-inline-button.el-button--mini.is-plain:active) {
  border-color: rgba(0, 0, 0, 0.32);
  background: rgba(0, 0, 0, 0.14);
  color: var(--black);
}

.tip-text-warning {
  color: #e6a23c;
}

.tip-action-link {
  margin-left: 6px;
  padding: 0;
  font-size: 12px;
  vertical-align: baseline;
}

.summary-switch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}
</style>
<style scoped src="@/assets/css/postedit.css"></style>
<style src="@/assets/css/postedit-global.css"></style>
