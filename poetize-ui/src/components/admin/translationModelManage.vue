<template>
  <div class="translation-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title-section">
        <h1 class="page-title">
          <i class="el-icon-s-tools"></i>
          翻译管理
        </h1>
        <p class="page-description">配置和管理翻译服务，支持API翻译和AI大模型翻译</p>
      </div>
      </div>
      
    <!-- 主要配置区域 -->
    <div class="config-container">
      <el-form :model="apiConfig" label-width="120px" class="config-form">
        
        <!-- 翻译模式选择 -->
        <div class="config-section">
          <div class="section-header">
            <h2 class="section-title">
              <i class="el-icon-setting"></i>
              翻译模式配置
            </h2>
          </div>
          <div class="section-content">
        <el-form-item label="翻译模式">
              <div class="mode-selector">
                <div class="mode-options">
                  <div 
                    class="mode-option" 
                    :class="{ 'active': apiConfig.mode === 'api' }"
                    @click="apiConfig.mode = 'api'">
                    <div class="mode-icon">
                      <i class="el-icon-connection"></i>
                    </div>
                    <div class="mode-info">
                      <div class="mode-name">API翻译</div>
                      <div class="mode-desc">速度快，适合简单翻译</div>
                    </div>
                  </div>
                  <div 
                    class="mode-option" 
                    :class="{ 'active': apiConfig.mode === 'llm' }"
                    @click="apiConfig.mode = 'llm'">
                    <div class="mode-icon">
                      <i class="el-icon-cpu"></i>
                    </div>
                    <div class="mode-info">
                      <div class="mode-name">大模型翻译</div>
                      <div class="mode-desc">质量高，适合专业翻译</div>
                    </div>
                  </div>
                </div>
              </div>
        </el-form-item>
          </div>
        </div>

        <!-- 默认语言配置 -->
        <div class="config-section">
          <div class="section-header">
            <h2 class="section-title">
              <i class="el-icon-s-opportunity"></i>
              默认语言配置
            </h2>
          </div>
          <div class="section-content">
            <div class="language-config-row">
              <el-form-item label="默认源语言" class="language-item">
                <el-select v-model="apiConfig.defaultSourceLang" placeholder="请选择默认源语言" class="language-select">
                  <el-option label="自动检测" value="auto">
                    <span class="option-content">
                      <i class="el-icon-magic-stick"></i>
                      自动检测
                    </span>
                  </el-option>
                  <el-option label="中文" value="zh">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      中文
                    </span>
                  </el-option>
                  <el-option label="英文" value="en">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      英文
                    </span>
                  </el-option>
                  <el-option label="日文" value="ja">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      日文
                    </span>
                  </el-option>
                  <el-option label="韩文" value="ko">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      韩文
                    </span>
                  </el-option>
                  <el-option label="法文" value="fr">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      法文
                    </span>
                  </el-option>
                  <el-option label="德文" value="de">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      德文
                    </span>
                  </el-option>
                  <el-option label="西班牙文" value="es">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      西班牙文
                    </span>
                  </el-option>
                  <el-option label="俄文" value="ru">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      俄文
                    </span>
                  </el-option>
                </el-select>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  用于文章翻译时的默认源语言
                </div>
              </el-form-item>
              
              <div class="language-arrow">
                <i class="el-icon-right"></i>
              </div>
              
              <el-form-item label="默认目标语言" class="language-item">
                <el-select v-model="apiConfig.defaultTargetLang" placeholder="请选择默认目标语言" class="language-select">
                  <el-option label="中文" value="zh">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      中文
                    </span>
                  </el-option>
                  <el-option label="英文" value="en">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      英文
                    </span>
                  </el-option>
                  <el-option label="日文" value="ja">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      日文
                    </span>
                  </el-option>
                  <el-option label="韩文" value="ko">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      韩文
                    </span>
                  </el-option>
                  <el-option label="法文" value="fr">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      法文
                    </span>
                  </el-option>
                  <el-option label="德文" value="de">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      德文
                    </span>
                  </el-option>
                  <el-option label="西班牙文" value="es">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      西班牙文
                    </span>
                  </el-option>
                  <el-option label="俄文" value="ru">
                    <span class="option-content">
                      <i class="el-icon-location-outline"></i>
                      俄文
                    </span>
                  </el-option>
                </el-select>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  用于文章翻译时的默认目标语言
                </div>
              </el-form-item>
            </div>
          </div>
        </div>

        <!-- API翻译配置 -->
        <div class="config-section" v-if="apiConfig.mode === 'api'">
          <div class="section-header">
            <h2 class="section-title">
              <i class="el-icon-connection"></i>
              API翻译配置
            </h2>
          </div>
          <div class="section-content">
          <el-form-item label="翻译引擎">
              <el-select v-model="apiConfig.provider" placeholder="请选择翻译引擎" class="full-width">
                <el-option label="百度翻译" value="baidu">
                  <span class="option-content">
                    <i class="el-icon-s-platform"></i>
                    百度翻译
                  </span>
                </el-option>
                <el-option label="自定义API" value="custom">
                  <span class="option-content">
                    <i class="el-icon-s-custom"></i>
                    自定义API
                  </span>
                </el-option>
            </el-select>
          </el-form-item>
          
          <template v-if="apiConfig.provider === 'baidu'">
            <el-form-item label="APP ID">
                <el-input v-model="apiConfig.appId" placeholder="请输入百度翻译APP ID" class="input-field"></el-input>
            </el-form-item>
            <el-form-item label="密钥">
                <el-input v-model="apiConfig.appSecret" type="password" show-password placeholder="请输入百度翻译密钥" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.hasExistingBaiduSecret">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                API密钥将自动加密存储，确保您的数据安全
                  </template>
              </div>
            </el-form-item>
          </template>
          
          <template v-if="apiConfig.provider === 'custom'">
            <el-form-item label="API地址">
                <el-input v-model="apiConfig.customUrl" placeholder="请输入自定义翻译API地址" class="input-field"></el-input>
            </el-form-item>
            <el-form-item label="API密钥">
                <el-input v-model="apiConfig.customApiKey" type="password" show-password placeholder="请输入自定义API密钥" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.hasExistingCustomSecret">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                API密钥将自动加密存储，确保您的数据安全
                  </template>
              </div>
            </el-form-item>
            <el-form-item label="密钥2(可选)">
                <el-input v-model="apiConfig.appSecret" type="password" show-password placeholder="某些API需要第二个密钥参数" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.hasExistingBaiduSecret">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                API密钥将自动加密存储，确保您的数据安全
                  </template>
              </div>
            </el-form-item>
              <div class="info-panel">
                <div class="info-header">
                  <i class="el-icon-question"></i>
                  自定义API使用说明
                </div>
                <div class="info-content">
                  <div class="info-item">• API地址: 翻译服务的完整URL</div>
                  <div class="info-item">• API密钥: 用于身份验证的密钥</div>
                  <div class="info-item">• 密钥2: 某些API（如百度）需要第二个参数</div>
                  <div class="info-item">• 请确保API支持POST请求和JSON格式数据</div>
                </div>
              </div>
          </template>
          </div>
        </div>
        
        <!-- 大模型翻译配置 -->
        <div class="config-section" v-if="apiConfig.mode === 'llm'">
          <div class="section-header">
            <h2 class="section-title">
              <i class="el-icon-cpu"></i>
              大模型翻译配置
            </h2>
          </div>
          <div class="section-content">
          <el-form-item label="大模型类型">
              <el-select v-model="apiConfig.llmType" placeholder="请选择大模型类型" class="full-width">
                <el-option label="OpenAI (GPT)" value="openai">
                  <span class="option-content">
                    <i class="el-icon-cloudy"></i>
                    OpenAI (GPT)
                  </span>
                </el-option>
                <el-option label="Anthropic (Claude)" value="anthropic">
                  <span class="option-content">
                    <i class="el-icon-sunny"></i>
                    Anthropic (Claude)
                  </span>
                </el-option>
                <el-option label="Azure OpenAI" value="azure">
                  <span class="option-content">
                    <i class="el-icon-cloudy-and-sunny"></i>
                    Azure OpenAI
                  </span>
                </el-option>
                <el-option label="自定义/其他" value="custom">
                  <span class="option-content">
                    <i class="el-icon-s-custom"></i>
                    自定义/其他
                  </span>
                </el-option>
            </el-select>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                选择不同的大模型类型将自动调整请求格式
              </div>
          </el-form-item>
            
          <el-form-item label="模型名称">
            <el-select 
              v-model="apiConfig.llmModel" 
              :placeholder="getModelPlaceholder()" 
              filterable 
              allow-create
                :class="{'full-width': true, 'custom-model-select': apiConfig.llmType === 'custom'}">
              <el-option 
                v-for="model in availableModels" 
                :key="model.value" 
                :label="model.label" 
                :value="model.value">
              </el-option>
            </el-select>
              <div :class="{'form-tip': true, 'custom-model-tip': apiConfig.llmType === 'custom'}">
                <i class="el-icon-lightbulb"></i>
              {{ getModelTip() }}
            </div>
          </el-form-item>
            
            <!-- 自定义模型的接口类型选择 -->
            <el-form-item label="接口类型" v-if="apiConfig.llmType === 'custom'">
              <el-select v-model="apiConfig.llmInterfaceType" placeholder="请选择接口类型" class="full-width">
                <el-option label="自动检测" value="auto">
                  <span class="option-content">
                    <i class="el-icon-magic-stick"></i>
                    自动检测
                  </span>
                </el-option>
                <el-option label="OpenAI兼容接口" value="openai">
                  <span class="option-content">
                    <i class="el-icon-cloudy"></i>
                    OpenAI兼容接口
                  </span>
                </el-option>
                <el-option label="Anthropic兼容接口" value="anthropic">
                  <span class="option-content">
                    <i class="el-icon-sunny"></i>
                    Anthropic兼容接口
                  </span>
                </el-option>
                <el-option label="自定义HTTP接口" value="custom">
                  <span class="option-content">
                    <i class="el-icon-s-custom"></i>
                    自定义HTTP接口
                  </span>
                </el-option>
              </el-select>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                选择API接口的兼容格式。OpenAI格式适用于大多数模型，Anthropic格式适用于Claude类型模型，自定义适用于特殊格式
              </div>
          </el-form-item>
            
            <el-form-item label="API接口地址">
              <el-input v-model="apiConfig.llmUrl" placeholder="请输入大模型API接口地址" class="input-field"></el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                大模型API接口地址，请确保该接口支持翻译功能
              </div>
            </el-form-item>
            
            <el-form-item label="API密钥" v-if="needsApiKey">
              <el-input v-model="apiConfig.llmApiKey" type="password" show-password placeholder="请输入API密钥" class="input-field">
              <template slot="prefix">
                <i class="el-icon-lock"></i>
              </template>
            </el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="apiConfig.hasExistingLlmKey">
                  已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥。对于本地模型（如Ollama）可以不填写
                </template>
                <template v-else>
              API密钥将自动加密存储，确保您的数据安全。对于本地模型（如Ollama）可以不填写
                </template>
            </div>
          </el-form-item>
            
          <el-form-item label="翻译提示词">
              <el-input type="textarea" v-model="apiConfig.llmPrompt" :rows="3" placeholder="请输入翻译提示词，用于指导大模型如何进行翻译" class="textarea-field"></el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                可使用占位符：{source_lang}和{target_lang}表示语言，{format}表示文本格式(Markdown或纯文本)
              </div>
          </el-form-item>
            
          <el-form-item label="超时时间">
              <div class="timeout-group">
                <el-input v-model.number="apiConfig.llmTimeout" placeholder="请输入超时时间" class="timeout-input">
                  <template slot="append">秒</template>
                </el-input>
              </div>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                建议设置30-60秒，过短可能导致翻译失败
              </div>
          </el-form-item>
          </div>
        </div>
        
        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-button type="primary" @click="saveApiConfig" class="action-btn primary-btn">
            <i class="el-icon-check"></i>
            保存配置
          </el-button>
          <el-button @click="getApiConfig" class="action-btn">
            <i class="el-icon-refresh"></i>
            刷新配置
          </el-button>
          <el-button type="success" @click="testTranslation" class="action-btn success-btn">
            <i class="el-icon-s-promotion"></i>
            测试翻译
          </el-button>
        </div>
      </el-form>
    </div>
    
    <!-- 测试翻译对话框 -->
    <el-dialog title="测试翻译" :visible.sync="testTranslationDialogVisible" width="60%" class="test-dialog">
      <div class="dialog-content">
        <div class="test-form">
          <div class="language-info">
            <div class="lang-display">
              <span class="lang-text">{{ getLanguageName(apiConfig.defaultSourceLang) }}</span>
              <div class="arrow-separator">
                <i class="el-icon-right"></i>
              </div>
              <span class="lang-text">{{ getLanguageName(apiConfig.defaultTargetLang) }}</span>
            </div>
            <div class="lang-note">
              <i class="el-icon-info"></i>
              使用配置中的默认语言设置
            </div>
          </div>
          
          <div class="input-section">
            <label>源文本</label>
            <el-input type="textarea" v-model="testTranslationForm.sourceText" :rows="4" placeholder="请输入要翻译的文本" class="source-input"></el-input>
          </div>
          
          <div class="stream-mode">
            <label>翻译模式</label>
            <div class="stream-options">
              <el-radio v-model="testTranslationForm.useStream" :label="true" class="stream-radio">流式翻译（实时显示）</el-radio>
              <el-radio v-model="testTranslationForm.useStream" :label="false" class="stream-radio">普通翻译（完成后显示）</el-radio>
            </div>
          </div>
          
          <div class="translate-section">
            <el-button type="primary" @click="doTestTranslation" :loading="testTranslationLoading" class="translate-btn">
              <i class="el-icon-s-promotion"></i>
              {{ testTranslationLoading ? '翻译中...' : '开始翻译' }}
            </el-button>
          </div>
          
          <div class="result-section" v-if="testTranslationForm.translatedText">
            <label>翻译结果</label>
            <el-input type="textarea" v-model="testTranslationForm.translatedText" :rows="4" readonly class="result-output"></el-input>
            <div class="result-meta" v-if="testTranslationForm.processingTime">
              <el-tag size="small" type="info">
                <i class="el-icon-time"></i>
                用时: {{ testTranslationForm.processingTime.toFixed(2) }}秒
              </el-tag>
              <el-tag size="small" type="success" v-if="testTranslationForm.detectedLang">
                <i class="el-icon-search"></i>
                检测语言: {{ testTranslationForm.detectedLang === 'zh' ? '中文' : '英文' }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="testTranslationDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'TranslationModelManage',
  data() {
    return {
      apiConfig: {
        mode: 'llm',
        provider: 'baidu',
        appId: '',
        appSecret: '',
        customUrl: '',
        customApiKey: '',
        llmType: 'openai',
        llmModel: 'gpt-3.5-turbo',
        llmUrl: '',
        llmApiKey: '',
        llmPrompt: '',
        llmTimeout: 30,
        llmInterfaceType: 'auto',
        // 默认语言配置
        defaultSourceLang: 'zh',
        defaultTargetLang: 'en',
        // 密钥状态标记
        hasExistingBaiduSecret: false,
        hasExistingCustomKey: false,
        hasExistingCustomSecret: false,  // 添加自定义API第二密钥状态标记
        hasExistingLlmKey: false
      },
      testTranslationDialogVisible: false,
      testTranslationForm: {
        sourceText: '',
        translatedText: '',
        processingTime: null,
        detectedLang: null,
        useStream: false  // Python端暂不支持流式翻译
      },
      testTranslationLoading: false
    };
  },
  created() {
    this.getApiConfig();
  },
  computed: {
    needsApiKey() {
      // 所有大模型类型都可能需要API密钥
      return true;
    },
    availableModels() {
      const models = [];
      
      switch (this.apiConfig.llmType) {
        case 'openai':
          models.push(
            { label: 'GPT-4.1 (最新)', value: 'gpt-4.1' },
            { label: 'GPT-4o (强大)', value: 'gpt-4o' },
            { label: 'GPT-4o Mini (性价比)', value: 'gpt-4o-mini' },
            { label: 'GPT-4 Turbo (经典)', value: 'gpt-4-turbo' },
            { label: 'GPT-4 (稳定)', value: 'gpt-4' },
            { label: 'GPT-3.5 Turbo (快速)', value: 'gpt-3.5-turbo' },
            { label: 'GPT-3.5 Turbo 16k', value: 'gpt-3.5-turbo-16k' }
          );
          break;
        case 'anthropic':
          models.push(
            { label: 'Claude-4 (最新)', value: 'claude-4' },
            { label: 'Claude-3.7 Sonnet (强大)', value: 'claude-3-7-sonnet' },
            { label: 'Claude-3.5 Sonnet (稳定)', value: 'claude-3-5-sonnet-20241022' },
            { label: 'Claude-3.5 Haiku (快速)', value: 'claude-3-5-haiku-20241022' }
          );
          break;
        case 'azure':
          models.push(
            { label: 'Azure GPT-4.1', value: 'gpt-4.1' },
            { label: 'Azure GPT-4o', value: 'gpt-4o' },
            { label: 'Azure GPT-4o Mini', value: 'gpt-4o-mini' },
            { label: 'Azure GPT-4 Turbo', value: 'gpt-4-turbo' },
            { label: 'Azure GPT-4', value: 'gpt-4' },
            { label: 'Azure GPT-3.5 Turbo', value: 'gpt-35-turbo' },
            { label: 'Azure GPT-3.5 Turbo 16k', value: 'gpt-35-turbo-16k' }
          );
          break;
        case 'custom':
          models.push(
            // Ollama本地模型 - 最新版本
            { label: 'Qwen3:8B (通义千问最新)', value: 'qwen3:8b' },
            { label: 'Qwen3:14B (通义千问)', value: 'qwen3:14b' },
            { label: 'Qwen3:32B (通义千问)', value: 'qwen3:32b' },
            { label: 'Qwen2.5:7B (通义千问)', value: 'qwen2.5:7b' },
            { label: 'Qwen2.5:14B (通义千问)', value: 'qwen2.5:14b' },
            { label: 'Qwen2.5:32B (通义千问)', value: 'qwen2.5:32b' },
            { label: 'Llama3.2:3B (Meta)', value: 'llama3.2:3b' },
            { label: 'Llama3.1:8B (Meta)', value: 'llama3.1:8b' },
            { label: 'Gemma2:9B (Google)', value: 'gemma2:9b' },
            { label: 'Gemma2:27B (Google)', value: 'gemma2:27b' },
            { label: 'DeepSeek-Coder:6.7B', value: 'deepseek-coder:6.7b' },
            { label: 'CodeLlama:7B (Meta)', value: 'codellama:7b' },
            { label: 'Mistral:7B', value: 'mistral:7b' },
            { label: 'Yi:6B (零一万物)', value: 'yi:6b' },
            { label: 'Phi3:3.8B (Microsoft)', value: 'phi3:3.8b' },
            // 云端API模型
            { label: '通义千问 Turbo', value: 'qwen-turbo' },
            { label: '通义千问 Plus', value: 'qwen-plus' },
            { label: '通义千问 Max', value: 'qwen-max' },
            { label: '文心一言 4.0', value: 'ernie-4.0-8k' },
            { label: '文心一言 3.5', value: 'ernie-3.5-8k' },
            { label: '智谱清言 GLM-4', value: 'glm-4' },
            { label: '智谱清言 GLM-3 Turbo', value: 'glm-3-turbo' }
          );
          break;
      }
      
      return models;
    }
  },
  methods: {
    // API配置相关方法
    async getApiConfig() {
      try {
        this.loading = true;
        const res = await this.$http.get(this.$constant.pythonBaseURL + '/api/translation/config');
        
        console.log('获取到的配置数据:', res); // 添加调试日志
        
        if (res && res.code === 200 && res.data) {
          // 设置当前翻译类型
          this.apiConfig.mode = res.data.type === 'llm' ? 'llm' : 'api';
          
          console.log('设置模式为:', this.apiConfig.mode); // 添加调试日志
          
          // 处理百度翻译配置
          if (res.data.baidu) {
            this.apiConfig.provider = 'baidu';
            this.apiConfig.appId = res.data.baidu.app_id || '';
            // 检查是否有已配置的密钥（加密密钥会以特定格式返回）
            this.apiConfig.hasExistingBaiduSecret = !!(res.data.baidu.app_secret && res.data.baidu.app_secret !== '');
            this.apiConfig.appSecret = ''; // 不显示已有密钥内容，用户可选择覆盖
          }
          
          // 处理自定义API配置
          if (res.data.custom) {
            if (res.data.type === 'custom') {
              this.apiConfig.provider = 'custom';
            }
            this.apiConfig.customUrl = res.data.custom.api_url || '';
            this.apiConfig.hasExistingCustomKey = !!(res.data.custom.api_key && res.data.custom.api_key !== '');
            this.apiConfig.customApiKey = ''; // 不显示已有密钥内容
            // 处理自定义API的第二个密钥字段
            this.apiConfig.hasExistingCustomSecret = !!(res.data.custom.app_secret && res.data.custom.app_secret !== '');
            this.apiConfig.appSecret = ''; // 不显示已有密钥内容
          }
          
          // 处理LLM配置
          if (res.data.llm) {
            console.log('LLM配置数据:', res.data.llm); // 添加调试日志
            this.apiConfig.llmModel = res.data.llm.model || '';
            this.apiConfig.llmUrl = res.data.llm.api_url || '';
            this.apiConfig.hasExistingLlmKey = !!(res.data.llm.api_key && res.data.llm.api_key !== '' && res.data.llm.api_key !== 'null');
            this.apiConfig.llmApiKey = ''; // 不显示已有密钥内容
            this.apiConfig.llmPrompt = res.data.llm.prompt || '';
            this.apiConfig.llmInterfaceType = res.data.llm.interface_type || 'auto';  // 读取接口类型
            
            console.log('LLM配置读取结果:', {
              model: this.apiConfig.llmModel,
              url: this.apiConfig.llmUrl,
              hasKey: this.apiConfig.hasExistingLlmKey,
              prompt: this.apiConfig.llmPrompt,
              interfaceType: this.apiConfig.llmInterfaceType
            }); // 添加调试日志
            
            // 根据模型类型推断LLM类型
            if (res.data.llm.model) {
              const model = res.data.llm.model.toLowerCase();
              if (model.includes('gpt') || model.includes('openai')) {
                this.apiConfig.llmType = 'openai';
              } else if (model.includes('claude') || model.includes('anthropic')) {
                this.apiConfig.llmType = 'anthropic';
              } else if (model.includes('azure')) {
                this.apiConfig.llmType = 'azure';
          } else {
                this.apiConfig.llmType = 'custom';
              }
            }
          }
          
          // 处理默认语言配置
          if (res.data.default_source_lang) {
            this.apiConfig.defaultSourceLang = res.data.default_source_lang;
          }
          if (res.data.default_target_lang) {
            this.apiConfig.defaultTargetLang = res.data.default_target_lang;
          }
        } else {
          this.$message.error('获取配置失败：' + (res?.message || '未知错误'));
        }
      } catch (error) {
        console.error('获取API配置失败:', error);
        this.$message.error('获取配置失败，请检查网络连接');
      } finally {
        this.loading = false;
      }
    },
    async saveApiConfig() {
      try {
        this.loading = true;
        
        // 构建配置对象
        const config = {};
        
        // 根据模式设置不同的配置
        if (this.apiConfig.mode === 'api') {
          if (this.apiConfig.provider === 'baidu') {
            config.type = 'baidu';
            config.baidu = {
              app_id: this.apiConfig.appId
            };
            // 只有输入了新密钥才发送
            if (this.apiConfig.appSecret && this.apiConfig.appSecret.trim() !== '') {
              config.baidu.app_secret = this.apiConfig.appSecret;
            }
          } else if (this.apiConfig.provider === 'custom') {
            config.type = 'custom';
            config.custom = {
              api_url: this.apiConfig.customUrl
            };
            // 只有输入了新密钥才发送
            if (this.apiConfig.customApiKey && this.apiConfig.customApiKey.trim() !== '') {
              config.custom.api_key = this.apiConfig.customApiKey;
            }
            if (this.apiConfig.appSecret && this.apiConfig.appSecret.trim() !== '') {
              config.custom.app_secret = this.apiConfig.appSecret;
            }
          }
        } else if (this.apiConfig.mode === 'llm') {
          config.type = 'llm';
          config.llm = {
            model: this.apiConfig.llmModel,
            api_url: this.apiConfig.llmUrl,
            prompt: this.apiConfig.llmPrompt || '请将以下文本翻译成中文：',
            interface_type: this.apiConfig.llmInterfaceType || 'auto'  // 保存接口类型
          };
          // 只有输入了新密钥才发送
          if (this.apiConfig.llmApiKey && this.apiConfig.llmApiKey.trim() !== '') {
            config.llm.api_key = this.apiConfig.llmApiKey;
          }
        }
        
        // 添加默认语言配置
        config.default_source_lang = this.apiConfig.defaultSourceLang || 'zh';
        config.default_target_lang = this.apiConfig.defaultTargetLang || 'en';
        
        const res = await this.$http.post(this.$constant.pythonBaseURL + '/api/translation/config', config);
        
        if (res && res.code === 200) {
          this.$message.success('配置保存成功');
          // 重新加载配置以更新状态
          await this.getApiConfig();
        } else {
          this.$message.error('保存失败：' + (res?.message || '未知错误'));
        }
      } catch (error) {
        console.error('保存API配置失败:', error);
        this.$message.error('保存失败，请检查网络连接');
      } finally {
        this.loading = false;
      }
    },
    // 测试翻译相关方法
    testTranslation() {
      this.testTranslationForm = {
        sourceText: '',
        translatedText: '',
        processingTime: null,
        detectedLang: null,
        useStream: false  // Python端暂不支持流式翻译
      };
      this.testTranslationDialogVisible = true;
    },
    async doTestTranslation() {
      if (!this.testTranslationForm.sourceText) {
        this.$message.warning('请输入要翻译的文本');
        return;
      }
      
      this.testTranslationLoading = true;
      this.testTranslationForm.translatedText = ''; // 清空之前的结果
      
      try {
          await this.doNormalTranslation();
      } catch (error) {
        console.error('测试翻译失败:', error);
        if (error.name === 'AbortError') {
          this.$message.error('翻译超时，请重试');
        } else {
          this.$message.error('测试翻译失败: ' + (error.message || '未知错误'));
        }
      } finally {
        this.testTranslationLoading = false;
      }
    },
    
    async doNormalTranslation() {
      this.$message.info('正在翻译，请稍候...');
      const startTime = Date.now();
      
      try {
        // 使用Python端的翻译测试接口，后端会自动使用配置中的默认语言
        const response = await this.$http.post(this.$constant.pythonBaseURL + '/api/translation/test', {
          text: this.testTranslationForm.sourceText
        });
        
        if (response.code === 200 && response.data) {
          this.testTranslationForm.translatedText = response.data.translated_text;
          this.testTranslationForm.processingTime = (Date.now() - startTime) / 1000;
          this.$message.success(`翻译成功 (引擎: ${response.data.engine})`);
          
          // 如果后端返回了实际使用的语言信息，可以更新显示
          if (response.data.source_lang && response.data.target_lang) {
            console.log(`翻译语言: ${response.data.source_lang} -> ${response.data.target_lang}`);
          }
        } else {
          this.$message.error(response.message || '翻译失败');
        }
      } catch (error) {
        console.error('测试翻译失败:', error);
        this.$message.error('测试翻译失败: ' + (error.message || '未知错误'));
      }
    },
    formatTime(timeString) {
      if (!timeString) return '';
      const date = new Date(timeString);
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
    },
    getModelPlaceholder() {
      switch (this.apiConfig.llmType) {
        case 'openai':
          return '如 gpt-4.1, gpt-4o, gpt-4-turbo, gpt-3.5-turbo';
        case 'anthropic':
          return '如 claude-4, claude-3-7-sonnet, claude-3-5-sonnet-20241022';
        case 'azure':
          return '如 gpt-4.1, gpt-4o, gpt-4-turbo (Azure部署名称)';
        case 'custom':
          return '请输入自定义模型名称，如 qwen3:8b, qwen2.5:7b, llama3:8b, 或任何其他模型';
        default:
          return '请输入模型名称';
      }
    },
    getModelTip() {
      switch (this.apiConfig.llmType) {
        case 'openai':
          return '选择OpenAI模型：GPT-4.1最新版本功能最强，GPT-4o强大且稳定，GPT-4o Mini性价比高，GPT-3.5 Turbo速度快成本低';
        case 'anthropic':
          return '选择Anthropic Claude模型：Claude-4最新版本功能最强，Claude-3.7 Sonnet性能强大，Claude-3.5系列稳定可靠，Haiku速度快';
        case 'azure':
          return '输入Azure OpenAI部署的模型名称，需要与Azure门户中配置的部署名称完全一致。支持GPT-4.1等最新模型';
        case 'custom':
          return '支持多种模型和接口格式：本地模型（Ollama: qwen3、llama3等）、云端API（通义千问、文心一言等）。请选择对应的接口类型以确保正确通信';
        default:
          return '指定要使用的模型名称，确保与API服务提供商的模型名称一致';
      }
    },
    getLanguageName(langCode) {
      const languages = {
        zh: '中文',
        en: '英文',
        ja: '日文',
        ko: '韩文',
        fr: '法文',
        de: '德文',
        es: '西班牙文',
        ru: '俄文'
      };
      return languages[langCode] || langCode;
    }
  }
};
</script>

<style scoped>
.translation-management {
  padding: 24px;
  background-color: #fafafa;
  min-height: calc(100vh - 60px);
}

/* 页面标题区域 */
.page-header {
  margin-bottom: 24px;
}

.title-section {
  background: #ffffff;
  padding: 24px;
  border-radius: 8px;
  border-left: 4px solid #2d3748;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #2d3748;
}

.page-title i {
  font-size: 24px;
  color: #4a5568;
}

.page-description {
  font-size: 14px;
  margin: 0;
  color: #718096;
  line-height: 1.5;
}

/* 配置容器 */
.config-container {
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e2e8f0;
}

.config-form {
  padding: 0;
}

/* 配置分组 */
.config-section {
  border-bottom: 1px solid #f7fafc;
}

.config-section:last-child {
  border-bottom: none;
}

.section-header {
  background: #f7fafc;
  padding: 16px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title i {
  font-size: 16px;
  color: #4a5568;
}

.section-content {
  padding: 24px;
}

/* 模式选择器 */
.mode-selector {
  width: 100%;
}

.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 8px;
}

.mode-option {
  padding: 20px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: center;
  background: #ffffff;
}

.mode-option:hover {
  border-color: #4a5568;
  background: #f7fafc;
}

.mode-option.active {
  border-color: #2d3748;
  background: #f7fafc;
  box-shadow: 0 0 0 1px #2d3748;
}

.mode-icon {
  margin-bottom: 12px;
}

.mode-icon i {
  font-size: 24px;
  color: #a0aec0;
}

.mode-option.active .mode-icon i {
  color: #2d3748;
}

.mode-info {
  text-align: center;
}

.mode-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
  color: #4a5568;
}

.mode-option.active .mode-name {
  color: #2d3748;
}

.mode-desc {
  font-size: 13px;
  color: #718096;
  line-height: 1.4;
}

/* 表单元素 */
.full-width {
  width: 100%;
}

.el-form-item {
  margin-bottom: 20px;
}

.el-form-item__label {
  font-weight: 500;
  color: #4a5568;
  font-size: 14px;
}

.input-field .el-input__inner,
.textarea-field .el-textarea__inner {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 14px;
  transition: border-color 0.2s ease;
  background: #ffffff;
}

.input-field .el-input__inner:focus,
.textarea-field .el-textarea__inner:focus {
  border-color: #2d3748;
  outline: none;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1);
}

.input-field .el-input__inner:hover,
.textarea-field .el-textarea__inner:hover {
  border-color: #a0aec0;
}

/* 选择框选项 */
.option-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-content i {
  color: #4a5568;
  opacity: 0.8;
}

/* 表单提示 */
.form-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  color: #718096;
  margin-top: 6px;
  padding: 8px 12px;
  background: #f7fafc;
  border-radius: 4px;
}

.form-tip i {
  color: #4a5568;
  margin-top: 1px;
  opacity: 0.8;
}

.custom-model-tip {
  background: #edf2f7;
  color: #2d3748;
}

.custom-model-tip i {
  color: #4a5568;
}

/* 自定义模型选择 */
.custom-model-select .el-input__inner {
  border-color: #2d3748 !important;
  background: #edf2f7 !important;
}

/* 超时设置组 */
.timeout-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeout-input {
  width: 150px;
}

.timeout-input .el-input__inner {
  border: 1px solid #e2e8f0;
  border-radius: 6px 0 0 6px;
  background: #ffffff;
  transition: border-color 0.2s ease;
}

.timeout-input .el-input__inner:focus {
  border-color: #2d3748;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1);
}

.timeout-input .el-input-group__append {
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-left: none;
  border-radius: 0 6px 6px 0;
  color: #718096;
  font-weight: 500;
  padding: 0 12px;
}

/* 信息面板 */
.info-panel {
  margin-top: 16px;
  padding: 16px;
  background: #f7fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.info-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.info-header i {
  color: #4a5568;
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item {
  font-size: 13px;
  color: #718096;
  line-height: 1.4;
}

/* 操作按钮区域 */
.action-bar {
  padding: 20px 24px;
  background: #f7fafc;
  border-top: 1px solid #e2e8f0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.action-btn {
  padding: 10px 20px;
  border-radius: 6px;
  font-weight: 500;
  font-size: 14px;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.action-btn i {
  margin-right: 6px;
}

.primary-btn {
  background: #2d3748;
  border-color: #2d3748;
  color: white;
}

.primary-btn:hover {
  background: #1a202c;
  border-color: #1a202c;
}

.success-btn {
  background: #2f855a;
  border-color: #2f855a;
  color: white;
}

.success-btn:hover {
  background: #276749;
  border-color: #276749;
}

.action-btn:not(.primary-btn):not(.success-btn) {
  background: white;
  border-color: #e2e8f0;
  color: #718096;
}

.action-btn:not(.primary-btn):not(.success-btn):hover {
  border-color: #a0aec0;
  color: #4a5568;
}

/* 测试对话框 */
.test-dialog .el-dialog {
  border-radius: 8px;
}

.test-dialog .el-dialog__header {
  background: #2d3748;
  color: white;
  padding: 20px 24px;
}

.test-dialog .el-dialog__title {
  font-size: 18px;
  font-weight: 600;
}

.dialog-content {
  padding: 24px;
}

.test-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 语言配置 */
.language-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  background: #f7fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.lang-display {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.lang-text {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  background: #ffffff;
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  min-width: 60px;
  text-align: center;
}

.arrow-separator {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #4299e1;
  border-radius: 50%;
  color: white;
  font-size: 14px;
  font-weight: bold;
}

.lang-note {
  font-size: 12px;
  color: #718096;
  line-height: 1.4;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.lang-note i {
  color: #4299e1;
}

/* 输入部分 */
.input-section,
.stream-mode,
.translate-section,
.result-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-section label,
.stream-mode label,
.result-section label {
  font-size: 14px;
  font-weight: 500;
  color: #4a5568;
}

/* 流式模式选择 */
.stream-options {
  display: flex;
  gap: 20px;
  margin-top: 8px;
}

.stream-radio .el-radio__label {
  font-weight: 400;
  color: #718096;
}

/* 翻译按钮 */
.translate-btn {
  width: 100%;
  padding: 12px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px;
  background: #2d3748;
  border: none;
  color: white;
  transition: background-color 0.2s ease;
}

.translate-btn:hover {
  background: #1a202c;
}

.translate-btn i {
  margin-right: 6px;
}

/* 结果输出 */
.result-output .el-textarea__inner {
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
}

.result-meta {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  justify-content: flex-start;
}

.result-meta .el-tag {
  font-size: 12px;
  border-radius: 4px;
}

.result-meta .el-tag i {
  margin-right: 4px;
}

/* 对话框底部 */
.dialog-footer {
  padding: 16px 24px;
  background: #f7fafc;
  text-align: right;
  border-top: 1px solid #e2e8f0;
}

.dialog-footer .el-button {
  padding: 8px 16px;
  border-radius: 6px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .translation-management {
    padding: 16px;
  }
  
  .title-section {
    padding: 20px;
  }
  
  .section-content {
    padding: 20px;
  }
  
  .mode-options {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  
  .language-info {
    flex-direction: column;
    gap: 12px;
  }
  
  .arrow-separator {
    transform: rotate(90deg);
  }
  
  .action-bar {
    padding: 16px;
    flex-direction: column;
  }
  
  .action-btn {
    width: 100%;
  }
}

/* 覆盖Element UI样式 */
.el-form-item__label {
  padding-bottom: 6px !important;
}

.el-select .el-input__inner {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
}

.el-select .el-input__inner:focus {
  border-color: #2d3748 !important;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1) !important;
}

.el-input-number .el-input__inner {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
}

.el-input-number .el-input__inner:focus {
  border-color: #2d3748 !important;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1) !important;
}

.el-radio__input.is-checked .el-radio__inner {
  border-color: #2d3748 !important;
  background: #2d3748 !important;
}

.el-radio__input.is-checked + .el-radio__label {
  color: #2d3748 !important;
}

/* 聊天测试弹窗相关样式 */
.el-dialog__wrapper {
  padding: 0;
}

.test-form .el-form-item {
  margin-bottom: 16px;
}

.test-form .el-form-item__label {
  font-weight: 500;
  color: #2d3748;
}

.test-translation-content {
  max-height: 300px;
  overflow-y: auto;
}

.test-translation-result {
  background-color: #f8f9fa;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  min-height: 60px;
  color: #2d3748;
  line-height: 1.6;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.test-translation-result.empty {
  color: #a0aec0;
  font-style: italic;
}

.translation-meta {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #718096;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .translation-management {
    padding: 16px;
  }
  
  .mode-options {
    flex-direction: column;
  }
  
  .mode-option {
    flex-direction: column;
    text-align: center;
    padding: 16px;
  }
  
  .mode-icon {
    margin-bottom: 8px;
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .action-button {
    margin: 4px 0;
  }
}

/* 语言配置相关样式 */
.language-config-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
}

.language-item {
  flex: 1;
  min-width: 200px;
}

.language-item .el-form-item__label {
  font-weight: 500;
  color: #2d3748;
}

.language-select {
  width: 100%;
}

.language-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 30px;
  color: #4299e1;
  font-size: 16px;
  min-width: 30px;
}

.language-arrow i {
  font-weight: bold;
  transform: scaleX(1.2);
}

/* 响应式语言配置 */
@media (max-width: 768px) {
  .language-config-row {
    flex-direction: column;
    gap: 16px;
  }
  
  .language-arrow {
    padding-top: 0;
    transform: rotate(90deg);
    height: 20px;
  }
  
  .language-item {
    min-width: 100%;
  }
}
</style> 