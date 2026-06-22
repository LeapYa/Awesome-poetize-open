<template>
  <div class="ai-tools-config">
    <!-- 原生内置工具组 -->
    <div class="pro-max-section">
      <div class="section-header">
        <div class="section-title">
          <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" class="title-icon">
            <polyline points="16 18 22 12 16 6"></polyline>
            <polyline points="8 6 2 12 8 18"></polyline>
          </svg>
          系统内置原生能力
        </div>
        <div class="section-subtitle">无需额外配置，已默认集成到大模型运行时中。</div>
      </div>
      
      <div class="tools-grid">
        <div
          v-for="(tool, index) in nativeTools"
          :id="tool.id === 'ai_memory' ? 'field-ai-tool-memory' : (tool.id === 'ai_rag' ? 'field-ai-tool-rag' : null)"
          :key="'native-' + index"
          class="glass-card tool-card"
        >
          <div class="tool-icon-wrapper native-icon-bg">
            <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" v-html="tool.svgIcon"></svg>
          </div>
          <div class="tool-info">
            <div class="tool-header">
              <div style="display: flex; align-items: center; gap: 6px;">
                <span class="tool-name">{{ tool.name }}</span>
                <span class="tool-badge native-badge">原生支持</span>
              </div>
              <el-button v-if="tool.configurable" size="mini" type="text" icon="el-icon-setting" style="padding: 0; margin-left: auto;" @click.stop="openConfigDialog(tool.id)">配置</el-button>
            </div>
            <div class="tool-desc">{{ tool.description }}</div>
            <div class="tool-features">
              <span v-for="feat in tool.features" :key="feat" class="feature-tag">{{ feat }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 动态扩展插件组 -->
    <div class="pro-max-section" style="margin-top: 20px;">
      <div class="section-header" style="display: flex; justify-content: space-between; align-items: flex-start;">
        <div>
          <div class="section-title">
            <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" class="title-icon">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
              <line x1="12" y1="22.08" x2="12" y2="12"></line>
            </svg>
            扩展插件 (第三方接口)
          </div>
          <div class="section-subtitle">需前往插件管理配置接口凭证后才能生效，例如联网搜索等 API。</div>
        </div>
        <el-button size="small" @click="navigateToPluginManager">
          插件管理
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" style="margin-left: 4px;">
            <line x1="5" y1="12" x2="19" y2="12"></line>
            <polyline points="12 5 19 12 12 19"></polyline>
          </svg>
        </el-button>
      </div>
      
      <div v-if="loading" class="loading-state">
        <i class="el-icon-loading"></i>
        <span>正在加载插件列表...</span>
      </div>
      
      <div v-else-if="externalTools.length === 0" class="empty-state glass-card">
        <svg viewBox="0 0 24 24" width="48" height="48" stroke="currentColor" stroke-width="1" fill="none" class="empty-icon text-slate-300">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
          <line x1="3" y1="9" x2="21" y2="9"></line>
          <line x1="9" y1="21" x2="9" y2="9"></line>
        </svg>
        <div class="empty-text">暂未安装或启用任何扩展工具插件</div>
        <el-button size="small" type="primary" plain class="mt-4" @click="navigateToPluginManager">去插件中心看看</el-button>
      </div>

      <div v-else class="tools-grid">
        <div v-for="tool in externalTools" :key="tool.id" class="glass-card tool-card interactive-card">
          <div class="tool-icon-wrapper ext-icon-bg">
            <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none">
              <!-- Default external API icon -->
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="16" x2="12" y2="12"></line>
              <line x1="12" y1="8" x2="12.01" y2="8"></line>
            </svg>
          </div>
          <div class="tool-info">
            <div class="tool-header">
              <span class="tool-name">{{ tool.pluginName }}</span>
              <span :class="['tool-badge', tool.enabled ? 'active-badge' : 'inactive-badge']">
                {{ tool.enabled ? '已启用' : '未启用' }}
              </span>
            </div>
            <div class="tool-desc">{{ tool.pluginDescription }}</div>
            <div class="tool-meta">
              <span class="meta-item"><i class="el-icon-info"></i> {{ tool.pluginKey }}</span>
            </div>
          </div>
        </div>
        
        <!-- 引导安装更多卡片 -->
        <div class="glass-card tool-card dashed-card" @click="navigateToPluginManager">
          <div class="tool-icon-wrapper add-icon-bg">
            <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
          </div>
          <div class="tool-info justify-center">
            <div class="tool-name text-primary">安装更多扩展</div>
            <div class="tool-desc">安装、配置或管理更多如联网搜索之类的外部 API 工具。</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 内置工具配置弹窗 -->
    <el-dialog
      title="配置 AI 上下文记忆 (Mem0)"
      :visible.sync="memoryDialogVisible"
      width="500px"
      custom-class="centered-dialog"
      :close-on-click-modal="false"
      append-to-body>
      
      <el-form v-if="memoryDialogVisible" label-width="120px" class="compact-form">
        <el-form-item id="field-ai-mem0-enable" label="开启记忆">
          <el-switch v-model="localConfig.enableMemory" @change="emitChange"></el-switch>
          <div class="form-tip">是否开启跨会话长期记忆能力。</div>
        </el-form-item>
        
        <el-form-item id="field-ai-mem0-key" label="Mem0 密钥" v-if="localConfig.enableMemory">
          <el-input v-model="localConfig.mem0ApiKey" @change="emitChange" placeholder="输入 Mem0 平台提供的 API Key" size="small" show-password></el-input>
          <div class="form-tip">
            Mem0 API Key，可前往 <a href="https://mem0.ai/" target="_blank" class="link-primary">mem0.ai</a> 获取。
          </div>
        </el-form-item>

        <el-form-item id="field-ai-mem0-autosave" label="自动保存记忆" v-if="localConfig.enableMemory">
          <el-switch v-model="localConfig.memoryAutoSave" @change="emitChange"></el-switch>
          <div class="form-tip">聊天过程中，自动总结并保存有用的知识点到记忆库。</div>
        </el-form-item>

        <el-form-item id="field-ai-mem0-autorecall" label="自动提取记忆" v-if="localConfig.enableMemory">
          <el-switch v-model="localConfig.memoryAutoRecall" @change="emitChange"></el-switch>
          <div class="form-tip">每次对话前，自动检索并注入相关的历史记忆。</div>
        </el-form-item>

        <el-form-item id="field-ai-mem0-limit" label="注入提取上限" v-if="localConfig.enableMemory && localConfig.memoryAutoRecall">
          <el-input-number v-model="localConfig.memoryRecallLimit" @change="emitChange" :min="1" :max="20" size="small" style="width: 120px;"></el-input-number>
          <div class="form-tip">单次对话最多检索并合并多少条相关记忆。</div>
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="memoryDialogVisible = false">关闭</el-button>
        <el-button size="small" type="primary" @click="memoryDialogVisible = false">完成</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="配置文章知识检索增强 (RAG)"
      :visible.sync="ragDialogVisible"
      width="720px"
      custom-class="centered-dialog"
      :close-on-click-modal="false"
      append-to-body>

      <div v-if="ragDialogVisible" v-loading="ragLoading">
        <el-form label-width="140px" class="compact-form">
          <el-form-item id="field-ai-rag-enable" label="启用 RAG">
            <el-switch v-model="localConfig.rag.enabled" :disabled="isRagForceDisabled" @change="emitChange"></el-switch>
            <div class="form-tip">启用后会在 AI 聊天前做 MariaDB 向量检索，并把命中的公开文章事实注入提示词。</div>
            <div class="form-tip">这里的修改只会更新当前页面表单；需要点击页面底部“保存外观与排版设置”后，重建与预览才会读取到新配置。</div>
            <div v-if="ragStatus.disabledReason" class="form-tip form-tip-warning">{{ ragStatus.disabledReason }}</div>
          </el-form-item>

          <el-form-item label="索引名称">
            <el-input v-model.trim="localConfig.rag.indexName" @change="emitChange" size="small" placeholder="poetize_ai_chat"></el-input>
            <div class="form-tip">用于区分当前知识库索引空间，默认保留一个聊天索引即可。</div>
          </el-form-item>

          <el-form-item label="Embedding 提供商">
            <el-input v-model.trim="localConfig.rag.embeddingProvider" @change="emitChange" size="small" placeholder="openai"></el-input>
          </el-form-item>

          <el-form-item label="Embedding Base URL">
            <el-input v-model.trim="localConfig.rag.embeddingApiBase" @change="emitChange" size="small" placeholder="可留空，默认跟随主模型配置"></el-input>
          </el-form-item>

          <el-form-item label="Embedding API Key">
            <el-input v-model.trim="localConfig.rag.embeddingApiKey" @change="emitChange" size="small" show-password placeholder="可留空，默认跟随主模型配置"></el-input>
            <div class="form-tip">如果这里留空，后端会回退到 AI 聊天主模型的 API Key。</div>
          </el-form-item>

          <el-form-item label="Embedding 模型">
            <el-input v-model.trim="localConfig.rag.embeddingModel" @change="emitChange" size="small" placeholder="text-embedding-3-small"></el-input>
          </el-form-item>

          <el-form-item label="向量维度">
            <el-input-number v-model="localConfig.rag.embeddingDimensions" @change="emitChange" :min="1" :max="16384" size="small" style="width: 180px;"></el-input-number>
          </el-form-item>

          <el-form-item label="Top K">
            <el-input-number v-model="localConfig.rag.topK" @change="emitChange" :min="1" :max="20" size="small" style="width: 180px;"></el-input-number>
          </el-form-item>

          <el-form-item label="相似度阈值">
            <el-input-number v-model="localConfig.rag.scoreThreshold" @change="emitChange" :min="0" :max="1" :step="0.05" :precision="2" size="small" style="width: 180px;"></el-input-number>
          </el-form-item>

          <el-form-item label="切片大小">
            <el-input-number v-model="localConfig.rag.chunkSize" @change="emitChange" :min="100" :max="4000" :step="50" size="small" style="width: 180px;"></el-input-number>
          </el-form-item>

          <el-form-item label="切片重叠">
            <el-input-number v-model="localConfig.rag.chunkOverlap" @change="emitChange" :min="0" :max="1000" :step="10" size="small" style="width: 180px;"></el-input-number>
          </el-form-item>
        </el-form>

        <div class="rag-status-card">
          <div class="rag-status-header">
            <div class="rag-status-title">同步状态</div>
            <div class="rag-status-actions">
              <el-button size="mini" @click="refreshRagStatus" :loading="ragLoading">刷新</el-button>
              <el-button size="mini" type="primary" @click="triggerRagRebuild" :loading="ragRebuilding" :disabled="isRagActionDisabled">重建知识库</el-button>
            </div>
          </div>
          <div class="rag-status-grid">
            <div class="rag-status-item">
              <span class="rag-status-label">已启用</span>
              <span class="rag-status-value">{{ ragStatus.enabled ? '是' : '否' }}</span>
            </div>
            <div class="rag-status-item">
              <span class="rag-status-label">可运行</span>
              <span class="rag-status-value">{{ ragStatus.runnable ? '是' : '否' }}</span>
            </div>
            <div class="rag-status-item">
              <span class="rag-status-label">数据库</span>
              <span class="rag-status-value">{{ ragStatus.databaseType || '未知' }}</span>
            </div>
            <div class="rag-status-item">
              <span class="rag-status-label">数据库版本</span>
              <span class="rag-status-value">{{ ragStatus.databaseVersion || '未知' }}</span>
            </div>
            <div class="rag-status-item">
              <span class="rag-status-label">向量检索支持</span>
              <span class="rag-status-value">{{ ragStatus.vectorSearchSupported === false ? '否' : '是' }}</span>
            </div>
            <div class="rag-status-item">
              <span class="rag-status-label">文档数</span>
              <span class="rag-status-value">{{ ragStatus.documentCount || 0 }}</span>
            </div>
            <div class="rag-status-item">
              <span class="rag-status-label">切片数</span>
              <span class="rag-status-value">{{ ragStatus.chunkCount || 0 }}</span>
            </div>
            <div class="rag-status-item">
              <span class="rag-status-label">RAG 版本</span>
              <span class="rag-status-value">{{ ragStatus.ragVersion || '0' }}</span>
            </div>
            <div class="rag-status-item rag-status-item-wide">
              <span class="rag-status-label">最后同步</span>
              <span class="rag-status-value">{{ ragStatus.lastSyncTime || '暂无' }}</span>
            </div>
            <div v-if="ragStatus.disabledReason" class="rag-status-item rag-status-item-wide">
              <span class="rag-status-label">禁用原因</span>
              <span class="rag-status-value">{{ ragStatus.disabledReason }}</span>
            </div>
          </div>
          <div v-if="Array.isArray(ragStatus.recentDocuments) && ragStatus.recentDocuments.length" class="rag-status-list">
            <div v-for="item in ragStatus.recentDocuments.slice(0, 5)" :key="item.documentId + '-' + item.sourceId" class="rag-status-row">
              <span class="rag-doc-title">{{ item.title || item.documentId }}</span>
              <span class="rag-doc-meta">{{ item.sourceType }} / {{ item.visibilityScope }}</span>
            </div>
          </div>
        </div>

        <div class="rag-preview-card" v-loading="previewLoading">
          <div class="rag-preview-header">
            <div>
              <div class="rag-status-title">检索预览</div>
              <div class="form-tip">输入问题后可查看实际 embedding query、原始命中和最终注入上下文。</div>
            </div>
            <el-button size="mini" type="primary" @click="runRagPreview" :loading="previewLoading" :disabled="!previewQuery.trim()">执行预览</el-button>
          </div>

          <el-input
            v-model.trim="previewQuery"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入一个用户问题，例如：这篇文章主要讲了什么？"
            @keyup.enter.native.ctrl="runRagPreview">
          </el-input>

          <div v-if="previewResult" class="rag-preview-result">
            <div class="rag-preview-block">
              <div class="rag-preview-label">Embedding Query</div>
              <div class="rag-preview-text">{{ previewResult.retrievalQuery || '无' }}</div>
            </div>

            <div class="rag-preview-block">
              <div class="rag-preview-label">命中文章</div>
              <div v-if="previewResult.rawHits && previewResult.rawHits.length" class="rag-preview-hit-list">
                <div v-for="(hit, index) in previewResult.rawHits" :key="hit.documentId + '-' + hit.chunkIndex + '-' + index" class="rag-preview-hit">
                  <div class="rag-preview-hit-header">
                    <span class="rag-doc-title">{{ hit.title || hit.documentId || '未命名文章' }}</span>
                    <span class="rag-doc-meta">ID {{ hit.sourceId || hit.documentId }} / {{ formatSimilarity(hit.similarity) }}</span>
                  </div>
                  <div class="rag-preview-hit-meta">chunk {{ hit.chunkIndex }} / {{ hit.chunkCount }}</div>
                  <div class="rag-preview-text">{{ hit.content || '无正文片段' }}</div>
                </div>
              </div>
              <div v-else class="rag-preview-empty">当前没有命中任何文章片段。</div>
            </div>

            <div class="rag-preview-block">
              <div class="rag-preview-label">最终注入上下文</div>
              <pre class="rag-preview-code">{{ previewResult.assembledContext || '无' }}</pre>
            </div>
          </div>
        </div>
      </div>

      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="ragDialogVisible = false">关闭</el-button>
        <el-button size="small" type="primary" @click="ragDialogVisible = false">知道了</el-button>
      </div>
    </el-dialog>

    <!-- 视觉模型配置弹窗 -->
    <el-dialog
      title="配置图像识别 (视觉模型)"
      :visible.sync="visionDialogVisible"
      width="560px"
      custom-class="centered-dialog"
      :close-on-click-modal="false"
      append-to-body>

      <el-form v-if="visionDialogVisible" label-width="140px" class="compact-form">
        <div class="form-tip" style="margin-bottom: 12px; color: #909399;">
          此处配置独立视觉模型，作为图像识别工具（analyze_image）的后端。
          仅当主模型不具备原生视觉能力时由主模型按需调用。若主模型已具备原生视觉能力，请在「AI模型配置」中开启对应开关。
        </div>

        <el-form-item id="field-ai-vision-provider" label="视觉模型服务商">
          <el-select v-model="visionConfig.visionProvider" placeholder="请选择视觉模型服务商" clearable @change="onVisionProviderChange">
            <el-option label="OpenAI / ChatGPT API" value="openai"></el-option>
            <el-option label="Claude (Anthropic)" value="anthropic"></el-option>
            <el-option label="DeepSeek" value="deepseek"></el-option>
            <el-option label="硅基流动" value="siliconflow"></el-option>
            <el-option label="OpenRouter" value="openrouter"></el-option>
            <el-option label="WorldRouter" value="worldrouter"></el-option>
            <el-option label="自定义API" value="custom"></el-option>
          </el-select>
          <div class="form-tip">作为图像识别工具的后端模型。留空则禁用图片识别能力。</div>
        </el-form-item>

        <el-form-item id="field-ai-vision-api-key" label="视觉模型API密钥" v-if="visionConfig.visionProvider">
          <el-input
            v-model="visionConfig.visionApiKey"
            type="password"
            show-password
            placeholder="请输入视觉模型API密钥"
            @input="onVisionApiKeyInput">
          </el-input>
          <div v-if="isVisionApiKeyMasked" class="form-tip" style="color: #67c23a;">
            密钥已保存（出于安全考虑部分隐藏）
            <el-button type="text" size="small" @click="showFullVisionApiKey" v-if="!showingFullVisionKey">重新输入密钥</el-button>
          </div>
          <div v-else class="form-tip">
            API密钥保存后会自动隐藏敏感信息，这是正常的安全保护措施
          </div>
        </el-form-item>

        <el-form-item id="field-ai-vision-base-url" label="视觉模型API基础URL" v-if="visionConfig.visionProvider">
          <el-input
            v-model="visionConfig.visionApiBase"
            placeholder="例如: https://api.example.com/v1"
            @change="emitVisionChange">
          </el-input>
        </el-form-item>

        <el-form-item id="field-ai-vision-model" label="视觉模型名称" v-if="visionConfig.visionProvider">
          <el-select
            v-model="visionConfig.visionModel"
            placeholder="请输入视觉模型名称（如：qwen-3.7-plus、glm-5v-turbo、gpt-5.5 等）"
            filterable
            allow-create
            style="width: 100%"
            @change="emitVisionChange">
            <el-option label="Qwen-3.7-Plus (通义千问)" value="qwen-3.7-plus"></el-option>
            <el-option label="Qwen3-VL-72B (硅基流动)" value="Qwen/Qwen3-VL-72B-Instruct"></el-option>
            <el-option label="GLM-5V-Turbo (智谱)" value="glm-5v-turbo"></el-option>
            <el-option label="MiniMax-M3 (MiniMax)" value="minimax-m3"></el-option>
            <el-option label="GPT-5.5 (OpenAI)" value="gpt-5.5"></el-option>
            <el-option label="GPT-5.5 mini (OpenAI)" value="gpt-5.5-mini"></el-option>
            <el-option label="Claude Opus 4.8 (Anthropic)" value="claude-opus-4-8"></el-option>
          </el-select>
          <div class="form-tip">支持任何模型名称，请根据您选择的服务商输入对应的视觉模型标识符</div>
        </el-form-item>

        <el-form-item label="当前状态">
          <div class="form-tip">
            <span v-if="visionConfig.visionProvider && visionConfig.visionModel && visionConfig.visionApiKey" style="color: #67c23a;">● 工具模式（analyze_image 工具已就绪）</span>
            <span v-else style="color: #909399;">○ 未配置独立视觉模型</span>
          </div>
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="visionDialogVisible = false">关闭</el-button>
        <el-button size="small" type="primary" @click="visionDialogVisible = false">完成</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>

export default {
  name: 'AiToolsConfig',
  props: {
    value: {
      type: Object,
      default: () => ({
        enableMemory: false,
        mem0ApiKey: '',
        memoryAutoSave: true,
        memoryAutoRecall: true,
        memoryRecallLimit: 5,
        rag: {
          enabled: false,
          indexName: 'poetize_ai_chat',
          embeddingProvider: 'openai',
          embeddingApiBase: '',
          embeddingApiKey: '',
          embeddingModel: 'text-embedding-3-small',
          embeddingDimensions: 1536,
          topK: 5,
          scoreThreshold: 0.2,
          chunkSize: 700,
          chunkOverlap: 120
        }
      })
    },
    visionConfigProp: {
      type: Object,
      default: () => ({
        visionSupported: false,
        visionProvider: '',
        visionApiKey: '',
        visionApiBase: '',
        visionModel: ''
      })
    }
  },
  data() {
    return {
      localConfig: this.normalizeConfig(this.value),
      loading: true,
      memoryDialogVisible: false,
      ragDialogVisible: false,
      ragLoading: false,
      ragRebuilding: false,
      ragStatus: {},
      previewLoading: false,
      previewQuery: '',
      previewResult: null,
      externalTools: [],
      visionDialogVisible: false,
      visionConfig: this.normalizeVisionConfig(this.visionConfigProp),
      isVisionApiKeyMasked: false,
      showingFullVisionKey: false,
      visionDefaultBaseUrls: {
        openai: 'https://api.openai.com/v1',
        anthropic: 'https://api.anthropic.com/v1/messages',
        deepseek: 'https://api.deepseek.com/v1',
        siliconflow: 'https://api.siliconflow.cn/v1',
        openrouter: 'https://openrouter.ai/api/v1',
        worldrouter: 'https://inference-api.worldrouter.ai/v1',
        custom: ''
      },
      // 硬编码的系统内置原生工具 (基于 ArticleTools.java、TimeTools.java、CalculatorTools.java 提取)
      nativeTools: [
        {
          id: 'article_search',
          name: '文章资源检索',
          description: '允许大语言模型搜索、阅读和归纳分析当前站点的博客文章资源。',
          svgIcon: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>',
          features: ['全文读取', '热门文章', '分类目录', '搜索摘要']
        },
        {
          id: 'time_wizard',
          name: '时间与节气向导',
          description: '赋予 AI 当前时间、农历/节气/节日、最近节日倒计时，以及中国法定放假/调休查询能力；法定安排会明确区分 official 与 predicted 口径。',
          svgIcon: '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>',
          features: ['当前时间/时区', '农历/节气/节日', '最近节日倒计时', '放假/调休']
        },
        {
          id: 'calculator',
          name: '数学计算器',
          description: '允许 AI 直接进行数学表达式计算，适合四则运算、括号表达式、幂运算，以及常见数学函数求值。',
          svgIcon: '<rect x="5" y="2" width="14" height="20" rx="2"></rect><line x1="8" y1="6" x2="16" y2="6"></line><line x1="8" y1="10" x2="8" y2="10"></line><line x1="12" y1="10" x2="12" y2="10"></line><line x1="16" y1="10" x2="16" y2="10"></line><line x1="8" y1="14" x2="8" y2="14"></line><line x1="12" y1="14" x2="12" y2="14"></line><line x1="16" y1="14" x2="16" y2="14"></line><line x1="8" y1="18" x2="16" y2="18"></line>',
          features: ['四则运算', '括号/负数', '幂与取模', 'sqrt/pow/max']
        },
        {
          id: 'ai_memory',
          name: 'AI 上下文记忆',
          description: '为 AI 提供长记忆能力，记住关键事实，并在未来对话中进行提取回忆。',
          svgIcon: '<path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"></path><path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"></path><path d="M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4"></path><path d="M17.599 6.5a3 3 0 0 0 .399-1.375"></path><path d="M6.003 5.125A3 3 0 0 0 6.401 6.5"></path><path d="M3.477 10.896a4 4 0 0 1 .585-.396"></path><path d="M19.938 10.5a4 4 0 0 1 .585.396"></path><path d="M6 18a4 4 0 0 1-1.967-.516"></path><path d="M19.967 17.484A4 4 0 0 1 18 18"></path>',
          features: ['跨会话记忆', '自动提炼', '长短期管理', '个性化事实'],
          configurable: true
        },
        {
          id: 'ai_rag',
          name: '文章知识检索增强',
          description: '为 AI 聊天接入公开文章向量检索，优先基于站内文章事实回答。',
          svgIcon: '<path d="M10 3H5a2 2 0 0 0-2 2v5"></path><path d="M14 21h5a2 2 0 0 0 2-2v-5"></path><path d="M21 10V5a2 2 0 0 0-2-2h-5"></path><path d="M3 14v5a2 2 0 0 0 2 2h5"></path><circle cx="12" cy="12" r="3"></circle><path d="M12 2v4"></path><path d="M12 18v4"></path><path d="M2 12h4"></path><path d="M18 12h4"></path>',
          features: ['标题/摘要/正文检索', '文章增量同步', 'MariaDB 向量搜索', '手动重建'],
          configurable: true
        },
        {
          id: 'ai_vision',
          name: '图像识别 (视觉模型)',
          description: '配置独立视觉模型作为 analyze_image 工具后端，让不具备原生视觉能力的主模型也能识别图片。主模型原生视觉能力请在「AI模型配置」中开启。',
          svgIcon: '<rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline>',
          features: ['独立视觉模型', 'analyze_image 工具', 'Function Calling', '多模态识别'],
          configurable: true
        }
      ]
    }
  },
  watch: {
    value: {
      handler(val) {
        this.localConfig = this.normalizeConfig(val);
      },
      deep: true
    },
    visionConfigProp: {
      handler(val) {
        const normalized = this.normalizeVisionConfig(val);
        if (JSON.stringify(normalized) !== JSON.stringify(this.visionConfig)) {
          this.visionConfig = normalized;
          this.isVisionApiKeyMasked = !!(this.visionConfig.visionApiKey && this.visionConfig.visionApiKey.includes('*'));
        }
      },
      deep: true
    }
  },
  mounted() {
    this.fetchExternalTools();
  },
  computed: {
    isRagForceDisabled() {
      return this.ragStatus.vectorSearchSupported === false || this.ragStatus.runtimeEnabled === false;
    },
    isRagActionDisabled() {
      return this.isRagForceDisabled || this.ragStatus.runnable === false;
    }
  },
  methods: {
    normalizeConfig(config = {}) {
      const rag = config.rag || {};
      return {
        enableMemory: config.enableMemory || false,
        mem0ApiKey: config.mem0ApiKey || '',
        memoryAutoSave: config.memoryAutoSave !== false && config.memoryAutosave !== false,
        memoryAutoRecall: config.memoryAutoRecall !== false && config.memoryAutorecall !== false,
        memoryRecallLimit: config.memoryRecallLimit || 5,
        rag: {
          enabled: rag.enabled || false,
          indexName: rag.indexName || 'poetize_ai_chat',
          embeddingProvider: rag.embeddingProvider || 'openai',
          embeddingApiBase: rag.embeddingApiBase || '',
          embeddingApiKey: rag.embeddingApiKey || '',
          embeddingModel: rag.embeddingModel || 'text-embedding-3-small',
          embeddingDimensions: rag.embeddingDimensions || 1536,
          topK: rag.topK || 5,
          scoreThreshold: typeof rag.scoreThreshold === 'number' ? rag.scoreThreshold : 0.2,
          chunkSize: rag.chunkSize || 700,
          chunkOverlap: typeof rag.chunkOverlap === 'number' ? rag.chunkOverlap : 120
        }
      };
    },
    fetchExternalTools() {
      this.loading = true;
      this.$http.get(this.$constant.baseURL + "/sysPlugin/listPlugins", { pluginType: 'ai_tool' }, true)
        .then(res => {
          if (res.code === 200 && res.data) {
            this.externalTools = res.data;
          }
        })
        .catch(err => {
          console.error("Failed to load ai tool plugins:", err);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    navigateToPluginManager() {
      this.$router.push({ path: '/pluginManager', query: { type: 'ai_tool' } });
      this.$emit('close-dialog'); // 如果在移动端弹窗中点击，则关闭弹窗
    },
    openConfigDialog(id) {
      if (id === 'ai_memory') {
        this.memoryDialogVisible = true;
        return;
      }
      if (id === 'ai_rag') {
        this.ragDialogVisible = true;
        this.previewResult = null;
        this.refreshRagStatus();
        return;
      }
      if (id === 'ai_vision') {
        this.visionDialogVisible = true;
      }
    },
    refreshRagStatus() {
      this.ragLoading = true;
      this.$http.get(this.$constant.baseURL + '/webInfo/ai/config/chat/rag/status', {}, true)
        .then(res => {
          if (res.code === 200 && res.data) {
            this.ragStatus = res.data;
            if (this.isRagForceDisabled && this.localConfig.rag.enabled) {
              this.localConfig.rag.enabled = false;
              this.emitChange();
            }
          }
        })
        .catch(err => {
          console.error('Failed to load rag status:', err);
          this.$message.error('获取 RAG 状态失败');
        })
        .finally(() => {
          this.ragLoading = false;
        });
    },
    triggerRagRebuild() {
      if (this.isRagForceDisabled) {
        this.$message.warning(this.ragStatus.disabledReason || '当前环境不支持向量检索');
        return;
      }
      this.ragRebuilding = true;
      this.$http.post(this.$constant.baseURL + '/webInfo/ai/config/chat/rag/rebuild', {}, true)
        .then(res => {
          if (res.code === 200) {
            this.$message.success('已触发知识库重建，请稍后刷新状态');
            this.refreshRagStatus();
          } else {
            this.$message.error(res.message || '触发重建失败');
          }
        })
        .catch(err => {
          console.error('Failed to rebuild rag knowledge base:', err);
          this.$message.error('触发知识库重建失败');
        })
        .finally(() => {
          this.ragRebuilding = false;
        });
    },
    runRagPreview() {
      const query = this.previewQuery.trim();
      if (!query) {
        this.$message.warning('请先输入要预览的提问');
        return;
      }
      this.previewLoading = true;
      this.$http.post(this.$constant.baseURL + '/webInfo/ai/config/chat/rag/preview', {
        query
      }, true)
        .then(res => {
          if (res.code === 200 && res.data) {
            this.previewResult = res.data;
            this.ragStatus = res.data.status || this.ragStatus;
          } else {
            this.$message.error(res.message || '执行 RAG 预览失败');
          }
        })
        .catch(err => {
          console.error('Failed to preview rag context:', err);
          this.$message.error('执行 RAG 预览失败');
        })
        .finally(() => {
          this.previewLoading = false;
        });
    },
    formatSimilarity(value) {
      return typeof value === 'number' ? value.toFixed(3) : '0.000';
    },
    emitChange() {
      this.$emit('input', { ...this.normalizeConfig(this.localConfig) });
    },
    normalizeVisionConfig(config = {}) {
      return {
        visionSupported: config.visionSupported === true,
        visionProvider: config.visionProvider || '',
        visionApiKey: config.visionApiKey || '',
        visionApiBase: config.visionApiBase || '',
        visionModel: config.visionModel || ''
      };
    },
    onVisionApiKeyInput() {
      if (this.visionConfig.visionApiKey && !this.visionConfig.visionApiKey.includes('*')) {
        this.isVisionApiKeyMasked = false;
        this.showingFullVisionKey = false;
      }
      if (!this.visionConfig.visionApiKey) {
        this.isVisionApiKeyMasked = false;
        this.showingFullVisionKey = false;
      }
      this.emitVisionChange();
    },
    showFullVisionApiKey() {
      this.$confirm('要重新输入视觉模型API密钥吗？当前密钥将被清空。', '重新输入密钥', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        this.isVisionApiKeyMasked = false;
        this.showingFullVisionKey = false;
        this.visionConfig.visionApiKey = '';
        this.$message.info('请重新输入您的视觉模型API密钥');
        this.emitVisionChange();
      }).catch(() => {
        // 用户取消操作
      });
    },
    onVisionProviderChange(newProvider) {
      const defaultUrls = this.visionDefaultBaseUrls;
      const allDefaultUrls = Object.values(defaultUrls).filter(Boolean);
      const currentUrl = this.visionConfig.visionApiBase;
      const defaultUrl = defaultUrls[newProvider] || '';

      if (newProvider === 'custom') {
        // 自定义API：清空基础URL，由用户自行填写
        this.visionConfig.visionApiBase = '';
      } else if (!currentUrl || allDefaultUrls.includes(currentUrl)) {
        // 当前为空或仍为某个默认URL时，自动填入新服务商的默认URL；用户可修改
        this.visionConfig.visionApiBase = defaultUrl;
      }

      this.emitVisionChange();
    },
    emitVisionChange() {
      // 仅 emit 独立视觉模型配置字段，visionSupported 由 AiModelConfig 管理
      const normalized = this.normalizeVisionConfig(this.visionConfig);
      this.$emit('update-vision-config', {
        visionProvider: normalized.visionProvider,
        visionApiKey: normalized.visionApiKey,
        visionApiBase: normalized.visionApiBase,
        visionModel: normalized.visionModel
      });
    }
  }
}
</script>

<style scoped>
.justify-center {
  justify-content: center;
}
.mt-auto {
  margin-top: auto;
}
.pt-3 {
  padding-top: 12px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
.form-tip-warning {
  color: #e6a23c;
}
.link-primary {
  color: #409EFF;
  text-decoration: none;
}
.link-primary:hover {
  text-decoration: underline;
}
.ai-tools-config {
  padding: 8px 10px 24px 10px;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* 块划分 */
.pro-max-section {
  display: flex;
  flex-direction: column;
}

.section-header {
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 4px 0;
  gap: 6px;
}

.title-icon {
  color: #64748b;
}

.section-subtitle {
  font-size: 12px;
  color: #64748b;
}

/* Grid 网格 */
.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

/* 基础卡片 */
.glass-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

/* 卡片容器 */
.tool-card {
  display: flex;
  padding: 12px;
  gap: 12px;
}

.interactive-card {
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.interactive-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

/* 图标容器区 */
.tool-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 6px;
  flex-shrink: 0;
}

.native-icon-bg {
  background-color: #f0fdf4;
  color: #16a34a;
  box-shadow: inset 0 0 0 1px rgba(22, 163, 74, 0.1);
}

.ext-icon-bg {
  background-color: #eff6ff;
  color: #2563eb;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.1);
}

.add-icon-bg {
  background-color: #f8fafc;
  color: #94a3b8;
  border: 1px dashed #cbd5e1;
}

/* 卡片内容区 */
.tool-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.tool-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 4px;
}

.tool-name {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.tool-desc {
  font-size: 12px;
  color: #475569;
  line-height: 1.4;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 徽章与标签 */
.tool-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
  transform: scale(0.9);
  transform-origin: right top;
}

.native-badge {
  background-color: #f0fdf4;
  color: #16a34a;
}

.active-badge {
  background-color: #eff6ff;
  color: #2563eb;
}

.inactive-badge {
  background-color: #f1f5f9;
  color: #64748b;
}

.tool-features {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: auto;
}

.feature-tag {
  font-size: 10px;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
  transform: scale(0.95);
  transform-origin: left bottom;
}

.tool-meta {
  font-size: 11px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  margin-top: auto;
}

/* 引导安装虚线卡片 */
.dashed-card {
  border-style: dashed;
  cursor: pointer;
  background: #f8fafc;
  transition: all 0.2s ease;
}

.dashed-card:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}

.dashed-card:hover .add-icon-bg {
  color: #3b82f6;
  border-color: #93c5fd;
}

.dashed-card:hover .text-primary {
  color: #2563eb;
}

/* 空状态与加载状态 */
.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #64748b;
  font-size: 14px;
}

.empty-state {
  background-color: #f8fafc;
  border-radius: 12px;
}

.empty-icon {
  margin-bottom: 16px;
  color: #cbd5e1;
}

.empty-text {
  color: #64748b;
  font-size: 13px;
}

.mt-4 {
  margin-top: 16px;
}

.rag-status-card {
  margin-top: 8px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.rag-status-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.rag-status-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.rag-status-actions {
  display: flex;
  gap: 8px;
}

.rag-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.rag-status-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.rag-preview-card {
  margin-top: 12px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
}

.rag-preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.rag-preview-result {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rag-preview-block {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.rag-preview-label {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.rag-preview-text {
  font-size: 12px;
  line-height: 1.6;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.rag-preview-hit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rag-preview-hit {
  padding: 10px 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.rag-preview-hit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.rag-preview-hit-meta {
  margin-bottom: 8px;
  font-size: 11px;
  color: #64748b;
}

.rag-preview-code {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #0f172a;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.rag-preview-empty {
  font-size: 12px;
  color: #64748b;
}

.rag-status-item-wide {
  grid-column: span 2;
}

.rag-status-label {
  font-size: 11px;
  color: #64748b;
}

.rag-status-value {
  font-size: 13px;
  color: #0f172a;
  word-break: break-all;
}

.rag-status-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rag-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.rag-doc-title {
  font-size: 12px;
  color: #0f172a;
  font-weight: 500;
}

.rag-doc-meta {
  font-size: 11px;
  color: #64748b;
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .tools-grid {
    grid-template-columns: 1fr;
  }

  .rag-status-header,
  .rag-status-row,
  .rag-preview-header,
  .rag-preview-hit-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .rag-status-grid {
    grid-template-columns: 1fr;
  }

  .rag-status-item-wide {
    grid-column: span 1;
  }
}
</style>
