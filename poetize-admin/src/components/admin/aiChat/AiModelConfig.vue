<template>
  <div class="ai-model-config">
    <el-form :model="modelConfig" label-width="120px">
      <el-form-item id="field-ai-provider" label="AI服务商">
        <el-select 
          v-model="modelConfig.provider" 
          placeholder="请选择AI服务商" 
          @change="onProviderChange">
          <el-option label="OpenAI / ChatGPT API" value="openai"></el-option>
          <el-option label="Claude (Anthropic)" value="anthropic"></el-option>
          <el-option label="DeepSeek" value="deepseek"></el-option>
          <el-option label="硅基流动" value="siliconflow"></el-option>
          <el-option label="OpenRouter" value="openrouter"></el-option>
          <el-option label="WorldRouter" value="worldrouter"></el-option>
          <el-option label="自定义API" value="custom"></el-option>
        </el-select>
        <small class="help-text">ChatGPT 请使用 OpenAI API Key 接入；当前不支持 Codex 接入。列表外服务商请使用"自定义API"选项</small>
      </el-form-item>

      <el-form-item id="field-ai-api-key" label="API密钥">
        <el-input 
          v-model="modelConfig.apiKey" 
          type="password" 
          show-password
          placeholder="请输入API密钥"
          @input="onApiKeyInput">
        </el-input>
        <div v-if="isApiKeyMasked" class="api-key-status">
          <i class="el-icon-success"></i>
          <span>密钥已保存（出于安全考虑部分隐藏）</span>
          <el-button type="text" size="small" @click="showFullApiKey" v-if="!showingFullKey">重新输入密钥</el-button>
        </div>
        <div v-else class="help-text" style="margin-top: 5px;">
          API密钥保存后会自动隐藏敏感信息，这是正常的安全保护措施
        </div>
      </el-form-item>

      <el-form-item id="field-ai-model-name" label="模型名称">
        <el-select 
          v-model="modelConfig.model" 
          placeholder="请输入模型名称（如：qwen-3.7-plus、glm-5、gpt-5.5、deepseek-v4-flash等）"
          filterable 
          allow-create
          class="custom-model-select">
          <el-option 
            v-for="model in availableModels" 
            :key="model.value" 
            :label="model.label" 
            :value="model.value">
          </el-option>
        </el-select>
        <small class="help-text">
          支持任何模型名称，请根据您选择的服务商输入对应的模型标识符
        </small>
        <small class="help-text thinking-hint" v-if="isThinkingModelSelected">
          此模型支持思考参数，可在下方配置思考程度
        </small>
      </el-form-item>

      <el-form-item id="field-ai-enable-thinking" label="启用思考参数">
        <el-switch v-model="thinkingConfig.enableThinking"></el-switch>
        <small class="help-text">{{ thinkingHelpText }}</small>
      </el-form-item>

      <el-form-item id="field-ai-reasoning-effort" label="思考程度" v-if="thinkingConfig.enableThinking">
        <el-select v-model="thinkingConfig.reasoningEffort" placeholder="请选择思考程度">
          <el-option label="低" value="low"></el-option>
          <el-option label="中" value="medium"></el-option>
          <el-option label="高" value="high"></el-option>
          <el-option label="超高" value="xhigh"></el-option>
        </el-select>
        <small class="help-text">会按接口平台转换为 reasoning_effort、thinking_budget 或 reasoning 对象。</small>
      </el-form-item>

      <el-form-item id="field-ai-base-url" label="API基础URL" v-if="!['openai', 'anthropic'].includes(modelConfig.provider)">
        <el-input 
          v-model="modelConfig.baseUrl" 
          placeholder="例如: https://api.example.com/v1">
        </el-input>
      </el-form-item>

      <el-form-item
        v-if="['custom', 'openrouter', 'worldrouter'].includes(modelConfig.provider)"
        id="field-ai-thinking-profile"
        label="接口平台">
        <el-select v-model="thinkingConfig.thinkingProfile" placeholder="自动识别">
          <el-option label="自动识别" value="auto"></el-option>
          <el-option label="OpenRouter" value="openrouter"></el-option>
          <el-option label="WorldRouter" value="worldrouter"></el-option>
          <el-option label="硅基流动" value="siliconflow"></el-option>
          <el-option label="DeepSeek 官方" value="deepseek_official"></el-option>
          <el-option label="OpenAI" value="openai"></el-option>
          <el-option label="Anthropic" value="anthropic"></el-option>
          <el-option label="通用 OpenAI 兼容" value="generic_openai_compatible"></el-option>
        </el-select>
        <small class="help-text">自动识别会根据 API 基础 URL 匹配；不准时可手动选择接口平台。</small>
      </el-form-item>

      <el-form-item
        v-if="['custom', 'openrouter', 'worldrouter'].includes(modelConfig.provider)"
        id="field-ai-thinking-extra-body"
        label="自定义请求参数">
        <el-input
          v-model="thinkingConfig.thinkingExtraBodyText"
          type="textarea"
          :rows="4"
          placeholder='例如：{"include_reasoning":true}'>
        </el-input>
        <small class="help-text">JSON 对象。系统生成的平台参数会覆盖同名字段，避免接口协议被误改。</small>
      </el-form-item>

      <el-form-item id="field-ai-temperature" label="温度参数">
        <el-slider 
          v-model="modelConfig.temperature" 
          :min="0" 
          :max="2" 
          :step="0.1"
          show-tooltip>
        </el-slider>
        <small class="help-text">控制回复的随机性，0表示最确定，2表示最随机</small>
      </el-form-item>

      <el-form-item id="field-ai-max-tokens" label="最大令牌数">
        <el-input
          v-model="modelConfig.maxTokens"
          inputmode="numeric"
          placeholder="请输入最大令牌数"
          @input="onMaxTokensInput"
          @blur="normalizeMaxTokens">
          <template slot="append">tokens</template>
        </el-input>
        <small class="help-text">单次回复的最大长度</small>
      </el-form-item>

      <el-form-item id="field-ai-max-input-tokens" label="最大输入令牌">
        <el-input
          v-model="modelConfig.maxInputTokens"
          inputmode="numeric"
          placeholder="131072 (128K)"
          @input="onMaxInputTokensInput"
          @blur="normalizeMaxInputTokens">
          <template slot="append">tokens</template>
        </el-input>
        <small class="help-text">模型输入上下文窗口大小，不填默认128K（131072）。如 DeepSeek V4 Pro 支持 1M</small>
      </el-form-item>

      <el-form-item label="Top P">
        <el-slider 
          v-model="modelConfig.topP" 
          :min="0" 
          :max="1" 
          :step="0.01"
          show-tooltip>
        </el-slider>
        <small class="help-text">核采样参数，控制输出多样性（0-1），默认1.0</small>
      </el-form-item>

      <el-form-item label="频率惩罚">
        <el-slider 
          v-model="modelConfig.frequencyPenalty" 
          :min="0" 
          :max="2" 
          :step="0.1"
          show-tooltip>
        </el-slider>
        <small class="help-text">降低重复词汇的频率（0-2），默认0</small>
      </el-form-item>

      <el-form-item label="存在惩罚">
        <el-slider 
          v-model="modelConfig.presencePenalty" 
          :min="0" 
          :max="2" 
          :step="0.1"
          show-tooltip>
        </el-slider>
        <small class="help-text">鼓励谈论新话题（0-2），默认0</small>
      </el-form-item>

      <el-form-item id="field-ai-enable" label="启用AI聊天">
        <el-switch v-model="modelConfig.enabled"></el-switch>
      </el-form-item>

      <el-form-item id="field-ai-streaming" label="启用流式响应">
        <el-switch v-model="modelConfig.enableStreaming"></el-switch>
        <small class="help-text">启用后AI回复将实时显示，提供更流畅的对话体验，包括工具调用过程可视化</small>
      </el-form-item>

      <el-form-item id="field-ai-vision-supported" label="原生视觉能力">
        <el-switch v-model="localVisionSupported" @change="emitVisionSupportedChange"></el-switch>
        <small class="help-text">
          开启表示当前主模型原生具备多模态视觉理解能力（如 Qwen-3.7-Plus、GLM-5V-Turbo、MiniMax-M3、GPT-5.5 等），用户上传图片将以多模态消息直接交由主模型处理；
          关闭时若在「AI扩展工具」中配置了独立视觉模型，则改由 analyze_image 工具按需调用。
        </small>
      </el-form-item>

      <el-form-item label="连接测试">
        <el-button @click="testConnection" :loading="testing">测试连接</el-button>
        <span v-if="isApiKeyMasked" class="help-text" style="margin-left: 10px;">
          将使用已保存的配置进行测试
        </span>
        <span v-else class="help-text" style="margin-left: 10px;">
          将使用当前输入的配置进行测试
        </span>
        <span v-if="testResult" :class="testResult.success ? 'test-success' : 'test-error'">
          {{ testResult.message }}
        </span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
export default {
  name: 'AiModelConfig',
  props: {
    value: {
      type: Object,
      default: () => ({
        provider: 'openai',
        apiKey: '',
        model: 'gpt-5.5',
        baseUrl: '',
        temperature: 0.7,
        maxTokens: 1000,
        maxInputTokens: 131072,
        topP: 1.0,
        frequencyPenalty: 0,
        presencePenalty: 0,
        enabled: false,
        enableStreaming: false,
        enableThinking: false,
        reasoningEffort: 'medium',
        thinkingProfile: 'auto',
        thinkingExtraBodyText: ''
      })
    },
    advancedConfig: {
      type: Object,
      default: () => ({})
    },
    visionSupported: {
      type: Boolean,
      default: false
    }
  },
  
  data() {
    return {
      modelConfig: { ...this.value },
      testing: false,
      testResult: null,
      isApiKeyMasked: true,
      showingFullKey: false,
      originalMaskedKey: '',
      localVisionSupported: this.visionSupported === true,
      thinkingConfig: {
        enableThinking: false,
        reasoningEffort: 'medium',
        thinkingProfile: 'auto',
        thinkingExtraBodyText: '',
        ...this.advancedConfig
      }
    }
  },
  
  computed: {
    availableModels() {
      // 返回空数组，允许用户自由输入任何模型名称
      return [];
    },
    
    isThinkingModelSelected() {
      const model = (this.modelConfig.model || '').toLowerCase();
      const thinkingModels = ['o1-preview', 'o1-mini', 'deepseek-reasoner', 'deepseek-v4-pro'];
      return thinkingModels.includes(model) ||
             model.includes('o1') ||
             model.includes('o3') ||
             model.includes('o4') ||
             model.includes('gpt-5') ||
             model.includes('claude-4') ||
             model.includes('reasoner') ||
             model.includes('deepseek-r1') ||
             model.includes('deepseek-v4') ||
             model.includes('thinking');
    },

    thinkingHelpText() {
      if (this.modelConfig.provider === 'openrouter') {
        return 'OpenRouter 会使用 reasoning 对象；具体是否返回思考取决于模型。';
      }
      if (this.modelConfig.provider === 'worldrouter') {
        return 'WorldRouter 按 OpenAI 兼容接口转发 reasoning_effort；是否返回思考取决于模型。';
      }
      if (this.modelConfig.provider === 'siliconflow') {
        return '硅基流动会使用 thinking_budget，并读取 reasoning_content。';
      }
      if (this.modelConfig.provider === 'deepseek') {
        return 'DeepSeek 官方会启用 thinking，并读取 reasoning_content。';
      }
      if (this.modelConfig.provider === 'openai') {
        return 'OpenAI reasoning_effort 会控制推理强度，但官方模型通常不返回可展示思考。';
      }
      return '仅对支持思考参数的平台和模型生效。';
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        // 避免无限循环：只在值真正变化时更新
        if (JSON.stringify(newVal) !== JSON.stringify(this.modelConfig)) {
          this.modelConfig = { ...newVal };
          this.isApiKeyMasked = this.modelConfig.apiKey && this.modelConfig.apiKey.includes('*');
          this.originalMaskedKey = this.isApiKeyMasked ? this.modelConfig.apiKey : '';
        }
      },
      deep: true
    },
    
    modelConfig: {
      handler(newVal) {
        // 避免无限循环：只在值真正变化时 emit
        if (JSON.stringify(newVal) !== JSON.stringify(this.value)) {
          this.$emit('input', newVal);
        }
      },
      deep: true
    },

    advancedConfig: {
      handler(newVal) {
        if (JSON.stringify(newVal) !== JSON.stringify(this.thinkingConfig)) {
          this.thinkingConfig = {
            enableThinking: false,
            reasoningEffort: 'medium',
            thinkingProfile: 'auto',
            thinkingExtraBodyText: '',
            ...newVal
          };
        }
      },
      deep: true
    },

    thinkingConfig: {
      handler(newVal) {
        if (JSON.stringify(newVal) !== JSON.stringify(this.advancedConfig)) {
          this.$emit('update-advanced-config', { ...newVal });
        }
      },
      deep: true
    },

    visionSupported: {
      handler(newVal) {
        const normalized = newVal === true;
        if (normalized !== this.localVisionSupported) {
          this.localVisionSupported = normalized;
        }
      }
    }
  },
  
  methods: {
    emitVisionSupportedChange() {
      this.$emit('update-vision-supported', this.localVisionSupported === true);
    },

    onMaxTokensInput(value) {
      const normalized = String(value == null ? '' : value).replace(/[^\d]/g, '');
      if (value !== normalized) {
        this.modelConfig.maxTokens = normalized;
      }
    },

    normalizeMaxTokens() {
      this.modelConfig.maxTokens = this.toIntegerInRange(this.modelConfig.maxTokens, 1000, 100, 8000);
    },

    toIntegerInRange(value, fallback, min, max) {
      const parsed = parseInt(value, 10);
      if (Number.isNaN(parsed)) {
        return fallback;
      }
      return Math.min(Math.max(parsed, min), max);
    },

    onMaxInputTokensInput(value) {
      const normalized = String(value == null ? '' : value).replace(/[^\d]/g, '');
      if (value !== normalized) {
        this.modelConfig.maxInputTokens = normalized;
      }
    },

    normalizeMaxInputTokens() {
      // 范围：4096 ~ 1048576 (4K ~ 1M)，默认 131072 (128K)
      this.modelConfig.maxInputTokens = this.toIntegerInRange(this.modelConfig.maxInputTokens, 131072, 4096, 1048576);
    },

    onProviderChange() {
      // 清除测试结果
      this.testResult = null;
      const defaultBaseUrls = {
        deepseek: 'https://api.deepseek.com/v1',
        siliconflow: 'https://api.siliconflow.cn/v1',
        openrouter: 'https://openrouter.ai/api/v1',
        worldrouter: 'https://inference-api.worldrouter.ai/v1'
      };
      const defaultModels = {
        deepseek: 'deepseek-v4-flash',
        worldrouter: 'gpt-5.5'
      };
      if (defaultBaseUrls[this.modelConfig.provider]
          && (!this.modelConfig.baseUrl || this.modelConfig.provider === 'worldrouter')) {
        this.modelConfig.baseUrl = defaultBaseUrls[this.modelConfig.provider];
      }
      if (this.modelConfig.provider === 'deepseek' && !(this.modelConfig.model || '').toLowerCase().includes('deepseek')) {
        this.modelConfig.model = defaultModels.deepseek;
      } else if (defaultModels[this.modelConfig.provider] && !this.modelConfig.model) {
        this.modelConfig.model = defaultModels[this.modelConfig.provider];
      }
      if (this.modelConfig.provider === 'openrouter' && this.thinkingConfig.thinkingProfile === 'auto') {
        this.thinkingConfig.thinkingProfile = 'openrouter';
      }
      if (this.modelConfig.provider === 'worldrouter' && this.thinkingConfig.thinkingProfile === 'auto') {
        this.thinkingConfig.thinkingProfile = 'worldrouter';
      }
    },
    
    async testConnection() {
      this.testing = true;
      this.testResult = null;
      const extraBody = this.parseThinkingExtraBody();
      if (this.thinkingConfig.thinkingExtraBodyText && !extraBody.valid) {
        this.testing = false;
        this.testResult = { success: false, message: extraBody.message };
        this.$message.error(extraBody.message);
        return;
      }

      const usingSavedConfig = this.isApiKeyMasked || (this.modelConfig.apiKey && this.modelConfig.apiKey.includes('*'));
      const testData = {
        configType: 'ai_chat',
        configName: 'default',
        provider: this.modelConfig.provider,
        apiBase: this.modelConfig.baseUrl,
        model: this.modelConfig.model,
        enableThinking: this.thinkingConfig.enableThinking,
        reasoningEffort: this.thinkingConfig.enableThinking
          ? (this.thinkingConfig.reasoningEffort || 'medium')
          : '',
        extraConfig: JSON.stringify({
          thinkingProfile: this.thinkingConfig.thinkingProfile || 'auto',
          thinkingExtraBody: extraBody.value
        })
      };

      if (!usingSavedConfig && this.modelConfig.apiKey) {
        testData.apiKey = this.modelConfig.apiKey;
      }

      try {
        const response = await this.$http.post(this.$constant.baseURL + '/webInfo/ai/config/chat/test', testData, true);
        const result = this.normalizeTestResponse(response, usingSavedConfig);

        this.testResult = result;
        this.$message[result.success ? 'success' : 'error'](result.message);
      } catch (error) {
        const result = this.normalizeTestError(error);
        this.testResult = result;
        this.$message.error(result.message);
      } finally {
        this.testing = false;
      }
    },

    normalizeTestResponse(response, usingSavedConfig) {
      const data = response && response.data ? response.data : {};
      const success = typeof data.success === 'boolean'
        ? data.success
        : Boolean(response && (response.success === true || response.code === 200));
      const defaultMessage = success
        ? (usingSavedConfig ? '连接测试成功（使用已保存的配置）' : '连接测试成功')
        : '连接测试失败';
      const profileName = data.thinkingProfileName || data.thinkingProfile;
      const details = profileName ? `；接口平台：${profileName}` : '';

      return {
        success,
        message: (data.message || response.message || defaultMessage) + details
      };
    },

    normalizeTestError(error) {
      const dataMessage = error && error.data && error.data.message;
      const responseMessage = error && error.responseData && error.responseData.message;

      return {
        success: false,
        message: dataMessage || responseMessage || error.message || '连接测试失败，请检查配置和网络连接'
      };
    },

    parseThinkingExtraBody() {
      const text = this.thinkingConfig.thinkingExtraBodyText;
      if (!text || !text.trim()) {
        return { valid: true, value: {} };
      }
      try {
        const parsed = JSON.parse(text);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          return { valid: false, value: {}, message: '自定义请求参数必须是 JSON 对象' };
        }
        return { valid: true, value: parsed };
      } catch (error) {
        return { valid: false, value: {}, message: '自定义请求参数 JSON 格式错误' };
      }
    },
    
    onApiKeyInput() {
      if (this.modelConfig.apiKey && !this.modelConfig.apiKey.includes('*')) {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
      }
      if (!this.modelConfig.apiKey) {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
      }
    },

    async showFullApiKey() {
      this.$confirm('要重新输入API密钥吗？当前密钥将被清空。', '重新输入密钥', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
        this.modelConfig.apiKey = '';
        this.$message.info('请重新输入您的API密钥');
      }).catch(() => {
        // 用户取消操作
      });
}
  }
}
</script>

<style scoped>
.ai-model-config {
  max-height: 500px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 10px;
}

/* 移动端对话框中不限制高度 */
@media screen and (max-width: 768px) {
  .ai-model-config {
    max-height: none;
    overflow-y: visible;
  }
}

.help-text {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 5px;
}

.api-key-status {
  margin-top: 5px;
  color: #67c23a;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.thinking-hint {
  color: #e6a23c;
}

.test-success {
  color: #67c23a;
  margin-left: 10px;
  font-size: 12px;
}

.test-error {
  color: #f56c6c;
  margin-left: 10px;
  font-size: 12px;
}

.custom-model-select {
  width: 100%;
}

/* PC端样式 - 768px以上 */
@media screen and (min-width: 769px) {
  ::v-deep .el-form-item__label {
    float: left !important;
  }
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .ai-model-config {
    padding: 0;
  }

  .ai-model-config .el-form-item {
    margin-bottom: 15px;
  }

  /* 标签适配 - 垂直布局 */
  .ai-model-config .el-form-item__label {
    float: none !important;
    width: 100% !important;
    text-align: left !important;
    font-size: 13px;
    line-height: 1.4;
    margin-bottom: 8px !important;
    padding-bottom: 0 !important;
  }

  .ai-model-config .el-form-item__content {
    margin-left: 0 !important;
    width: 100% !important;
  }

  /* 帮助文本字号优化 */
  .help-text {
    font-size: 11px;
    line-height: 1.3;
    margin-top: 3px;
  }

  .api-key-status {
    font-size: 11px;
  }

  .test-success,
  .test-error {
    font-size: 11px;
  }

  /* 滑块容器 */
  .ai-model-config .el-slider {
    padding: 0 10px;
  }

}

@media screen and (max-width: 480px) {
  .ai-model-config .el-form-item {
    margin-bottom: 12px;
  }

  .ai-model-config .el-form-item__label {
    font-size: 12px;
  }

  .help-text,
  .api-key-status,
  .test-success,
  .test-error {
    font-size: 10px;
  }
}
</style>
