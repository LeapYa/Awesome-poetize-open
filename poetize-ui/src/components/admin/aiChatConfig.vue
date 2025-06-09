<template>
  <div class="ai-chat-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title-section">
        <h1 class="page-title">
          <i class="el-icon-chat-dot-round"></i>
          AI聊天配置
        </h1>
        <p class="page-description">配置网站AI聊天功能的各项参数，包括模型设置、聊天功能和外观定制</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- AI模型配置 -->
      <el-tab-pane label="AI模型配置" name="model">
        <el-card class="config-card">
          <div slot="header" class="card-header">
            <span>AI模型设置</span>
            <el-button type="primary" @click="saveModelConfig" :loading="saving">保存配置</el-button>
          </div>
          
          <el-form :model="modelConfig" label-width="120px">
            <el-form-item label="AI服务商">
              <el-select v-model="modelConfig.provider" placeholder="请选择AI服务商" @change="onProviderChange">
                <el-option label="OpenAI" value="openai"></el-option>
                <el-option label="Claude (Anthropic)" value="anthropic"></el-option>
                <el-option label="Google Gemini" value="google"></el-option>
                <el-option label="百度文心" value="baidu"></el-option>
                <el-option label="阿里通义千问" value="alibaba"></el-option>
                <el-option label="腾讯混元" value="tencent"></el-option>
                <el-option label="自定义API" value="custom"></el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="API密钥">
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
                💡 API密钥保存后会自动隐藏敏感信息，这是正常的安全保护措施
              </div>
            </el-form-item>

            <el-form-item label="模型名称">
              <el-select 
                v-model="modelConfig.model" 
                :placeholder="modelConfig.provider === 'custom' ? '请输入自定义模型名称' : '请选择模型'" 
                filterable 
                allow-create
                :class="{'custom-model-select': modelConfig.provider === 'custom'}">
                <el-option 
                  v-for="model in availableModels" 
                  :key="model.value" 
                  :label="model.label" 
                  :value="model.value">
                </el-option>
              </el-select>
              <small class="help-text" v-if="modelConfig.provider === 'custom'">
                💡 自定义API：请输入您的模型名称，支持任何兼容OpenAI格式的模型
              </small>
              <small class="help-text" v-else>
                根据所选服务商自动显示可用模型
              </small>
              <small class="help-text thinking-hint" v-if="isThinkingModelSelected">
                🧠 此模型支持思考模式，可在高级设置中启用以获得更深入的分析
              </small>
            </el-form-item>

            <el-form-item label="API基础URL" v-if="modelConfig.provider === 'custom'">
              <el-input 
                v-model="modelConfig.baseUrl" 
                placeholder="例如: https://api.example.com/v1">
              </el-input>
            </el-form-item>

            <el-form-item label="温度参数">
              <el-slider 
                v-model="modelConfig.temperature" 
                :min="0" 
                :max="2" 
                :step="0.1"
                show-tooltip>
              </el-slider>
              <small class="help-text">控制回复的随机性，0表示最确定，2表示最随机</small>
            </el-form-item>

            <el-form-item label="最大令牌数">
              <el-input-number 
                v-model="modelConfig.maxTokens" 
                :min="100" 
                :max="8000" 
                :step="100">
              </el-input-number>
              <small class="help-text">单次回复的最大长度</small>
            </el-form-item>

            <el-form-item label="启用AI聊天">
              <el-switch v-model="modelConfig.enabled"></el-switch>
            </el-form-item>

            <el-form-item label="启用流式响应">
              <el-switch v-model="modelConfig.enableStreaming"></el-switch>
              <small class="help-text">启用后AI回复将实时显示，提供更流畅的对话体验，包括工具调用过程可视化</small>
            </el-form-item>

            <el-form-item label="连接测试">
              <el-button @click="testConnection" :loading="testing">测试连接</el-button>
              <span v-if="isApiKeyMasked" class="help-text" style="margin-left: 10px;">
                🔒 将使用已保存的配置进行测试
              </span>
              <span v-else class="help-text" style="margin-left: 10px;">
                🔧 将使用当前输入的配置进行测试
              </span>
              <span v-if="testResult" :class="testResult.success ? 'test-success' : 'test-error'">
                {{ testResult.message }}
              </span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 聊天设置 -->
      <el-tab-pane label="聊天设置" name="chat">
        <el-card class="config-card">
          <div slot="header" class="card-header">
            <span>聊天功能设置</span>
            <el-button type="primary" @click="saveChatConfig" :loading="saving">保存配置</el-button>
          </div>
          
          <el-form :model="chatConfig" label-width="120px">
            <el-form-item label="系统提示词">
              <el-input 
                v-model="chatConfig.systemPrompt" 
                type="textarea" 
                :rows="4"
                placeholder="请输入AI的角色设定和行为指导">
              </el-input>
              <small class="help-text">定义AI的角色和回复风格</small>
            </el-form-item>

            <el-form-item label="欢迎消息">
              <el-input 
                v-model="chatConfig.welcomeMessage" 
                type="textarea" 
                :rows="2"
                placeholder="AI首次对话时的欢迎消息">
              </el-input>
            </el-form-item>

            <el-form-item label="对话历史数">
              <el-input-number 
                v-model="chatConfig.historyCount" 
                :min="0" 
                :max="20">
              </el-input-number>
              <small class="help-text">保留多少条历史对话用于上下文理解</small>
            </el-form-item>

            <el-form-item label="速率限制">
              <el-input-number 
                v-model="chatConfig.rateLimit" 
                :min="1" 
                :max="100"
                :precision="0">
              </el-input-number>
              <small class="help-text">每分钟最多允许的消息数量</small>
            </el-form-item>

            <el-form-item label="最大消息长度">
              <el-input-number 
                v-model="chatConfig.maxMessageLength" 
                :min="100" 
                :max="2000"
                :precision="0">
              </el-input-number>
              <small class="help-text">用户单条消息的最大字符数限制</small>
            </el-form-item>

            <el-form-item label="需要登录">
              <el-switch v-model="chatConfig.requireLogin"></el-switch>
              <small class="help-text">是否需要用户登录后才能使用AI聊天</small>
            </el-form-item>

            <el-form-item label="保存聊天记录">
              <el-switch v-model="chatConfig.saveHistory"></el-switch>
              <small class="help-text">是否保存用户的聊天历史记录</small>
            </el-form-item>

            <el-form-item label="内容过滤">
              <el-switch v-model="chatConfig.contentFilter"></el-switch>
              <small class="help-text">启用内容安全过滤</small>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 外观设置 -->
      <el-tab-pane label="外观设置" name="appearance">
        <el-card class="config-card">
          <div slot="header" class="card-header">
            <span>聊天界面外观</span>
            <el-button type="primary" @click="saveAppearanceConfig" :loading="saving">保存配置</el-button>
          </div>
          
          <el-form :model="appearanceConfig" label-width="120px">
            <el-form-item label="机器人头像">
              <div class="avatar-upload">
                <el-upload
                  class="avatar-uploader"
                  :action="uploadUrl"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  :on-success="handleAvatarSuccess"
                  :before-upload="beforeAvatarUpload">
                  <img v-if="appearanceConfig.botAvatar" :src="appearanceConfig.botAvatar" class="avatar">
                  <i v-else class="el-icon-plus avatar-uploader-icon"></i>
                </el-upload>
              </div>
            </el-form-item>

            <el-form-item label="机器人名称">
              <el-input v-model="appearanceConfig.botName" placeholder="例如: 小助手"></el-input>
            </el-form-item>

            <el-form-item label="主题颜色">
              <el-color-picker v-model="appearanceConfig.themeColor"></el-color-picker>
            </el-form-item>

            <el-form-item label="聊天窗口位置">
              <el-radio-group v-model="appearanceConfig.position">
                <el-radio label="bottom-right">右下角</el-radio>
                <el-radio label="bottom-left">左下角</el-radio>
                <el-radio label="center">居中</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="聊天气泡样式">
              <el-radio-group v-model="appearanceConfig.bubbleStyle">
                <el-radio label="modern">现代风格</el-radio>
                <el-radio label="classic">经典风格</el-radio>
                <el-radio label="minimal">简约风格</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="显示打字动效">
              <el-switch v-model="appearanceConfig.typingAnimation"></el-switch>
            </el-form-item>

            <el-form-item label="显示时间戳">
              <el-switch v-model="appearanceConfig.showTimestamp"></el-switch>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 高级设置 -->
      <el-tab-pane label="高级设置" name="advanced">
        <el-card class="config-card">
          <div slot="header" class="card-header">
            <span>高级配置</span>
            <el-button type="primary" @click="saveAdvancedConfig" :loading="saving">保存配置</el-button>
          </div>
          
          <el-form :model="advancedConfig" label-width="120px">
            <el-form-item label="代理设置">
              <el-input v-model="advancedConfig.proxy" placeholder="例如: http://proxy.example.com:8080"></el-input>
            </el-form-item>

            <el-form-item label="超时时间(秒)">
              <el-input-number v-model="advancedConfig.timeout" :min="5" :max="300"></el-input-number>
            </el-form-item>

            <el-form-item label="重试次数">
              <el-input-number v-model="advancedConfig.retryCount" :min="0" :max="5"></el-input-number>
            </el-form-item>

            <el-form-item label="自定义Headers">
              <div v-for="(header, index) in advancedConfig.customHeaders" :key="index" class="header-item">
                <el-input v-model="header.key" placeholder="Header名称" style="width: 200px; margin-right: 10px;"></el-input>
                <el-input v-model="header.value" placeholder="Header值" style="width: 300px; margin-right: 10px;"></el-input>
                <el-button type="danger" icon="el-icon-delete" @click="removeHeader(index)"></el-button>
              </div>
              <el-button type="primary" icon="el-icon-plus" @click="addHeader">添加Header</el-button>
            </el-form-item>

            <el-form-item label="启用思考模式">
              <el-switch v-model="advancedConfig.enableThinking"></el-switch>
              <small class="help-text">启用后AI会先思考再回答，提供更深入的分析（仅部分模型支持，如o1系列）</small>
            </el-form-item>

            <el-form-item label="调试模式">
              <el-switch v-model="advancedConfig.debugMode"></el-switch>
              <small class="help-text">启用后会在控制台输出详细日志</small>
            </el-form-item>

            <el-form-item label="数据导出">
              <el-button @click="exportConfig">导出配置</el-button>
              <el-button @click="showImportDialog">导入配置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 导入配置对话框 -->
    <el-dialog title="导入配置" :visible.sync="importDialogVisible" width="500px">
      <el-upload
        drag
        :action="uploadUrl"
        :before-upload="beforeConfigUpload"
        :on-success="handleConfigImport"
        accept=".json">
        <i class="el-icon-upload"></i>
        <div class="el-upload__text">将配置文件拖到此处，或<em>点击上传</em></div>
        <div class="el-upload__tip" slot="tip">只能上传json格式的配置文件</div>
      </el-upload>
    </el-dialog>

    <!-- 聊天测试面板 -->
    <el-drawer
      title="聊天测试"
      :visible.sync="testChatVisible"
      direction="rtl"
      size="400px">
      <div class="chat-test-panel">
        <div class="chat-messages" ref="chatMessages">
          <div v-for="(message, index) in testMessages" :key="index" :class="['message', message.type]">
            <div class="message-content">{{ message.content }}</div>
            <div class="message-time">{{ message.time }}</div>
          </div>
        </div>
        <div class="chat-input">
          <el-input 
            v-model="testInput" 
            @keyup.enter="sendTestMessage"
            placeholder="输入测试消息..."
            :disabled="testSending">
          </el-input>
          <el-button @click="sendTestMessage" :loading="testSending">发送</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 浮动测试按钮 -->
    <el-button 
      type="primary" 
      class="test-chat-btn"
      @click="testChatVisible = true"
      v-if="modelConfig.enabled">
      <i class="el-icon-chat-line-round"></i>
      测试聊天
    </el-button>
  </div>
</template>

<script>
export default {
  name: 'AiChatConfig',
  data() {
    return {
      activeTab: 'model',
      saving: false,
      testing: false,
      testResult: null,
      importDialogVisible: false,
      testChatVisible: false,
      testSending: false,
      testInput: '',
      testMessages: [],
      
      // AI模型配置
      modelConfig: {
        provider: 'openai',
        apiKey: '',
        model: 'gpt-3.5-turbo',
        baseUrl: '',
        temperature: 0.7,
        maxTokens: 1000,
        enabled: false,
        enableStreaming: false
      },
      
      // 聊天设置
      chatConfig: {
        systemPrompt: "你是一个友善的AI助手，请用中文回答问题。",
        welcomeMessage: "你好！有什么可以帮助你的吗？",
        historyCount: 10,
        rateLimit: 20,
        requireLogin: false,
        saveHistory: true,
        contentFilter: true,
        maxMessageLength: 500
      },
      
      // 外观设置
      appearanceConfig: {
        botAvatar: '',
        botName: 'AI助手',
        themeColor: '#409EFF',
        position: 'bottom-right',
        bubbleStyle: 'modern',
        typingAnimation: true,
        showTimestamp: true
      },
      
      // 高级设置
      advancedConfig: {
        proxy: '',
        timeout: 30,
        retryCount: 3,
        customHeaders: [],
        debugMode: false,
        enableThinking: false
      },
      
      isApiKeyMasked: true,
      showingFullKey: false,
      originalMaskedKey: ''
    }
  },
  
  computed: {
    uploadUrl() {
      return this.$constant.baseURL + "/admin/upload";
    },
    
    uploadHeaders() {
      return {
        'Authorization': 'Bearer ' + localStorage.getItem('adminToken')
      };
    },
    
    availableModels() {
      const modelMap = {
        openai: [
          { label: 'GPT-4', value: 'gpt-4' },
          { label: 'GPT-4 Turbo', value: 'gpt-4-turbo-preview' },
          { label: 'GPT-4o', value: 'gpt-4o' },
          { label: 'o1-preview (思考模式)', value: 'o1-preview' },
          { label: 'o1-mini (思考模式)', value: 'o1-mini' },
          { label: 'GPT-3.5 Turbo', value: 'gpt-3.5-turbo' },
          { label: 'GPT-3.5 Turbo 16K', value: 'gpt-3.5-turbo-16k' }
        ],
        anthropic: [
          { label: 'Claude-3 Opus', value: 'claude-3-opus-20240229' },
          { label: 'Claude-3 Sonnet', value: 'claude-3-sonnet-20240229' },
          { label: 'Claude-3 Haiku', value: 'claude-3-haiku-20240307' }
        ],
        google: [
          { label: 'Gemini Pro', value: 'gemini-pro' },
          { label: 'Gemini Pro Vision', value: 'gemini-pro-vision' }
        ],
        baidu: [
          { label: '文心一言', value: 'ernie-bot' },
          { label: '文心一言 Turbo', value: 'ernie-bot-turbo' }
        ],
        alibaba: [
          { label: '通义千问', value: 'qwen-turbo' },
          { label: '通义千问 Plus', value: 'qwen-plus' }
        ],
        tencent: [
          { label: '混元大模型', value: 'hunyuan' }
        ],
        custom: [
          { label: 'GPT-3.5 Turbo (OpenAI兼容)', value: 'gpt-3.5-turbo' },
          { label: 'GPT-4 (OpenAI兼容)', value: 'gpt-4' },
          { label: 'GPT-4o (OpenAI兼容)', value: 'gpt-4o' },
          { label: 'o1-preview (思考模式)', value: 'o1-preview' },
          { label: 'o1-mini (思考模式)', value: 'o1-mini' },
          { label: 'Claude-3 Sonnet (兼容)', value: 'claude-3-sonnet-20240229' },
          { label: 'DeepSeek Chat', value: 'deepseek-chat' },
          { label: 'DeepSeek Coder', value: 'deepseek-coder' },
          { label: 'Qwen2.5-72B-Instruct (ModelScope)', value: 'Qwen/Qwen2.5-72B-Instruct' },
          { label: 'Qwen2.5-32B-Instruct (ModelScope)', value: 'Qwen/Qwen2.5-32B-Instruct' },
          { label: 'Moonshot v1', value: 'moonshot-v1-8k' },
          { label: 'GLM-4', value: 'glm-4' },
          { label: 'Qwen Turbo', value: 'qwen-turbo' },
          { label: '自定义模型', value: 'custom-model' }
        ]
      };
      return modelMap[this.modelConfig.provider] || [];
    },
    
    isThinkingModelSelected() {
      // 检查是否选择了支持思考模式的模型
      const thinkingModels = ['o1-preview', 'o1-mini'];
      return thinkingModels.includes(this.modelConfig.model) || 
             this.modelConfig.model.includes('o1') ||
             this.modelConfig.model.includes('thinking');
    }
  },
  
  created() {
    this.loadConfigs();
  },
  
  methods: {
    // 加载所有配置
    async loadConfigs() {
      try {
        const response = await this.$http.get(this.$constant.pythonBaseURL + "/python/ai/chat/getConfig", {}, true);
        if (response.flag && response.data) {
          // 从后端API获取配置数据
          const config = response.data;
          
          // 映射基础配置
          this.modelConfig.provider = config.provider || 'openai';
          this.modelConfig.apiKey = config.api_key || '';
          this.modelConfig.model = config.model || 'gpt-3.5-turbo';
          this.modelConfig.baseUrl = config.api_base || '';
          this.modelConfig.temperature = config.temperature || 0.7;
          this.modelConfig.maxTokens = config.max_tokens || 1000;
          this.modelConfig.enabled = config.enabled || false;
          this.modelConfig.enableStreaming = config.enable_streaming || false;
          
          // 检查API密钥是否被隐藏（包含星号表示已保存但被隐藏）
          this.isApiKeyMasked = this.modelConfig.apiKey && this.modelConfig.apiKey.includes('*');
          this.originalMaskedKey = this.isApiKeyMasked ? this.modelConfig.apiKey : '';
          
          // 映射聊天配置
          this.chatConfig = {
            systemPrompt: config.custom_instructions || "你是一个友善的AI助手，请用中文回答问题。",
            welcomeMessage: config.welcome_message || "你好！有什么可以帮助你的吗？",
            historyCount: config.max_conversation_length || 10,
            rateLimit: config.rate_limit || 20,
            requireLogin: config.require_login || false,
            saveHistory: config.enable_chat_history !== false,
            contentFilter: config.enable_content_filter !== false,
            maxMessageLength: config.max_message_length || 500
          };
          
          // 映射外观配置
          this.appearanceConfig.botAvatar = config.chat_avatar || '';
          this.appearanceConfig.botName = config.chat_name || 'AI助手';
          this.appearanceConfig.themeColor = config.theme_color || '#409EFF';
          this.appearanceConfig.typingAnimation = config.enable_typing_indicator || true;
          this.appearanceConfig.showTimestamp = true; // 固定值
          
          // 映射高级配置
          this.advancedConfig.timeout = 30; // 固定值
          this.advancedConfig.retryCount = 3; // 固定值
          this.advancedConfig.debugMode = false; // 固定值
          this.advancedConfig.customHeaders = []; // 固定值
          this.advancedConfig.enableThinking = config.enable_thinking || false; // 从后端加载
        }
      } catch (error) {
        console.error('加载AI配置失败:', error);
        this.$message.error('加载AI配置失败: ' + (error.message || '未知错误'));
      }
    },
    
    // 服务商变更处理
    onProviderChange() {
      const models = this.availableModels;
      if (models.length > 0) {
        // 如果是自定义API且当前已有模型名称，保持不变
        if (this.modelConfig.provider === 'custom' && this.modelConfig.model) {
          // 保持现有模型名称不变
        } else {
          // 其他情况选择第一个可用模型
          this.modelConfig.model = models[0].value;
        }
      } else if (this.modelConfig.provider === 'custom') {
        // 如果是自定义API但没有预设模型，设置一个常用的默认值
        if (!this.modelConfig.model) {
          this.modelConfig.model = 'gpt-3.5-turbo';
        }
      }
      this.testResult = null;
    },
    
    // 保存模型配置
    async saveModelConfig() {
      try {
        // 构建保存请求数据
        const saveData = {
          provider: this.modelConfig.provider,
          api_base: this.modelConfig.baseUrl,
          model: this.modelConfig.model,
          temperature: this.modelConfig.temperature,
          max_tokens: this.modelConfig.maxTokens,
          enabled: this.modelConfig.enabled,
          enable_streaming: this.modelConfig.enableStreaming,
          // 聊天配置
          custom_instructions: this.chatConfig.systemPrompt,
          welcome_message: this.chatConfig.welcomeMessage,
          max_conversation_length: this.chatConfig.historyCount,
          rate_limit: this.chatConfig.rateLimit,
          require_login: this.chatConfig.requireLogin,
          enable_chat_history: this.chatConfig.saveHistory,
          enable_content_filter: this.chatConfig.contentFilter,
          max_message_length: this.chatConfig.maxMessageLength || 500,
          // 外观配置
          chat_avatar: this.appearanceConfig.botAvatar,
          chat_name: this.appearanceConfig.botName,
          theme_color: this.appearanceConfig.themeColor,
          enable_typing_indicator: this.appearanceConfig.typingAnimation,
          // 高级配置
          enable_thinking: this.advancedConfig.enableThinking
        };

        // 只有当API密钥不是隐藏格式时才发送
        if (this.modelConfig.apiKey && !this.modelConfig.apiKey.includes('*')) {
          saveData.api_key = this.modelConfig.apiKey;
        }
        // 如果API密钥是隐藏格式，不发送api_key字段，让后端保持原有密钥不变

        const response = await this.$http.post(this.$constant.pythonBaseURL + '/python/ai/chat/saveConfig', saveData, true);
        
        if (response.flag) {
          this.$message.success('配置保存成功');
          // 保存成功后重新加载配置，获取最新的隐藏密钥格式
          await this.loadConfig();
        } else {
          this.$message.error(response.message || '保存失败');
        }
      } catch (error) {
        console.error('保存配置失败:', error);
        this.$message.error('保存失败，请检查网络连接');
      }
    },
    
    // 保存聊天设置
    async saveChatConfig() {
      // 复用模型配置保存方法，因为后端是统一保存的
      await this.saveModelConfig();
    },
    
    // 保存外观配置
    async saveAppearanceConfig() {
      // 复用模型配置保存方法，因为后端是统一保存的
      await this.saveModelConfig();
    },
    
    // 保存高级配置
    async saveAdvancedConfig() {
      // 复用模型配置保存方法，因为后端是统一保存的
      await this.saveModelConfig();
    },

    // 测试连接
    async testConnection() {
      this.testing = true;
      this.testResult = '';

      try {
        // 检查是否使用的是隐藏的密钥
        if (this.isApiKeyMasked || (this.modelConfig.apiKey && this.modelConfig.apiKey.includes('*'))) {
          // 如果密钥被隐藏，使用保存的配置进行测试（不发送密钥）
          const response = await this.$http.post(this.$constant.pythonBaseURL + '/python/ai/chat/testConnection', {
            provider: this.modelConfig.provider,
            api_base: this.modelConfig.baseUrl,
            model: this.modelConfig.model,
            use_saved_config: true  // 告诉后端使用已保存的配置
          }, true);

          if (response.flag) {
            this.testResult = {
              success: true,
              message: response.message || '连接测试成功（使用已保存的配置）'
            };
            this.$message.success('连接测试成功（使用已保存的配置）');
          } else {
            this.testResult = {
              success: false,
              message: response.message || '连接测试失败'
            };
            this.$message.error('连接测试失败: ' + response.message);
          }
        } else {
          // 使用当前输入的密钥进行测试
          const testData = {
            provider: this.modelConfig.provider,
            api_key: this.modelConfig.apiKey,
            api_base: this.modelConfig.baseUrl,
            model: this.modelConfig.model
          };

          const response = await this.$http.post(this.$constant.pythonBaseURL + '/python/ai/chat/testConnection', testData, true);

          if (response.flag) {
            this.testResult = {
              success: true,
              message: response.message || '连接测试成功'
            };
            this.$message.success('连接测试成功');
          } else {
            this.testResult = {
              success: false,
              message: response.message || '连接测试失败'
            };
            this.$message.error('连接测试失败: ' + response.message);
          }
        }
      } catch (error) {
        this.testResult = {
          success: false,
          message: error.message
        };
        this.$message.error('连接测试失败: ' + error.message);
      } finally {
        this.testing = false;
      }
    },
    
    // 头像上传成功
    handleAvatarSuccess(res) {
      if (res.flag) {
        this.appearanceConfig.botAvatar = res.data;
        this.$message.success('头像上传成功');
      } else {
        this.$message.error('头像上传失败');
      }
    },
    
    // 头像上传前验证
    beforeAvatarUpload(file) {
      const isImage = file.type.indexOf('image/') === 0;
      const isLt2M = file.size / 1024 / 1024 < 2;
      
      if (!isImage) {
        this.$message.error('只能上传图片文件!');
        return false;
      }
      if (!isLt2M) {
        this.$message.error('图片大小不能超过 2MB!');
        return false;
      }
      return true;
    },
    
    // 添加自定义Header
    addHeader() {
      this.advancedConfig.customHeaders.push({ key: '', value: '' });
    },
    
    // 移除自定义Header
    removeHeader(index) {
      this.advancedConfig.customHeaders.splice(index, 1);
    },
    
    // 导出配置
    exportConfig() {
      const config = {
        model: this.modelConfig,
        chat: this.chatConfig,
        appearance: this.appearanceConfig,
        advanced: this.advancedConfig
      };
      
      const blob = new Blob([JSON.stringify(config, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'ai-chat-config.json';
      link.click();
      URL.revokeObjectURL(url);
    },
    
    // 显示导入对话框
    showImportDialog() {
      this.importDialogVisible = true;
    },
    
    // 配置文件上传前验证
    beforeConfigUpload(file) {
      const isJson = file.type === 'application/json' || file.name.endsWith('.json');
      if (!isJson) {
        this.$message.error('只能上传JSON格式的配置文件!');
        return false;
      }
      return true;
    },
    
    // 处理配置导入
    handleConfigImport(res) {
      if (res.flag) {
        try {
          const config = JSON.parse(res.data);
          Object.assign(this.modelConfig, config.model || {});
          Object.assign(this.chatConfig, config.chat || {});
          Object.assign(this.appearanceConfig, config.appearance || {});
          Object.assign(this.advancedConfig, config.advanced || {});
          this.$message.success('配置导入成功');
          this.importDialogVisible = false;
        } catch (error) {
          this.$message.error('配置文件格式错误');
        }
      } else {
        this.$message.error('配置导入失败');
      }
    },
    
    // 发送测试消息
    async sendTestMessage() {
      if (!this.testInput.trim()) return;
      
      const userMessage = {
        type: 'user',
        content: this.testInput,
        time: new Date().toLocaleTimeString()
      };
      
      this.testMessages.push(userMessage);
      this.testSending = true;
      
      try {
        // 使用正确的API端点进行测试
        const response = await this.$http.post(this.$constant.pythonBaseURL + "/python/ai/chat/sendMessage", {
          message: this.testInput,
          conversationId: `test_${Date.now()}`
        }, false); // 聊天API不需要管理员权限
        
        const botMessage = {
          type: 'bot',
          content: response.data ? response.data.response : '测试回复',
          time: new Date().toLocaleTimeString()
        };
        
        this.testMessages.push(botMessage);
      } catch (error) {
        const errorMessage = {
          type: 'bot',
          content: '发送失败: ' + error.message + '（提示：请先保存AI配置并确保配置正确）',
          time: new Date().toLocaleTimeString()
        };
        this.testMessages.push(errorMessage);
      }
      
      this.testInput = '';
      this.testSending = false;
      
      // 滚动到底部
      this.$nextTick(() => {
        const chatMessages = this.$refs.chatMessages;
        if (chatMessages) {
          chatMessages.scrollTop = chatMessages.scrollHeight;
        }
      });
    },

    // 加载配置
    async loadConfig() {
      try {
        const response = await this.$http.get(this.$constant.pythonBaseURL + "/python/ai/chat/getConfig", {}, true);
        if (response.flag && response.data) {
          // 更新各个配置对象
          if (response.data.provider) this.modelConfig.provider = response.data.provider;
          if (response.data.api_key) this.modelConfig.apiKey = response.data.api_key;
          if (response.data.api_base) this.modelConfig.baseUrl = response.data.api_base;
          if (response.data.model) this.modelConfig.model = response.data.model;
          if (response.data.temperature !== undefined) this.modelConfig.temperature = response.data.temperature;
          if (response.data.max_tokens) this.modelConfig.maxTokens = response.data.max_tokens;
          if (response.data.enabled !== undefined) this.modelConfig.enabled = response.data.enabled;
          if (response.data.enable_streaming !== undefined) this.modelConfig.enableStreaming = response.data.enable_streaming;
          if (response.data.custom_instructions) this.chatConfig.systemPrompt = response.data.custom_instructions;
          if (response.data.welcome_message) this.chatConfig.welcomeMessage = response.data.welcome_message;
          if (response.data.max_conversation_length) this.chatConfig.historyCount = response.data.max_conversation_length;
          if (response.data.enable_chat_history !== undefined) this.chatConfig.saveHistory = response.data.enable_chat_history;
          if (response.data.enable_content_filter !== undefined) this.chatConfig.contentFilter = response.data.enable_content_filter;
          if (response.data.chat_avatar) this.appearanceConfig.botAvatar = response.data.chat_avatar;
          if (response.data.chat_name) this.appearanceConfig.botName = response.data.chat_name;
          if (response.data.theme_color) this.appearanceConfig.themeColor = response.data.theme_color;
          if (response.data.enable_typing_indicator !== undefined) this.appearanceConfig.typingAnimation = response.data.enable_typing_indicator;
          if (response.data.enable_thinking !== undefined) this.advancedConfig.enableThinking = response.data.enable_thinking;
          
          console.log('AI聊天配置加载成功');
        } else {
          console.warn('加载配置失败:', response.message);
        }
      } catch (error) {
        console.error('加载配置失败:', error);
      }
    },

    onApiKeyInput() {
      // 当用户修改API密钥时，重置隐藏状态
      if (this.modelConfig.apiKey && !this.modelConfig.apiKey.includes('*')) {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
      }
      // 如果输入框被清空，也重置状态
      if (!this.modelConfig.apiKey) {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
      }
    },

    async showFullApiKey() {
      // 直接清空输入框，让用户重新输入
      this.$confirm('要重新输入API密钥吗？当前密钥将被清空。', '重新输入密钥', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
        this.modelConfig.apiKey = ''; // 清空输入框，让用户重新输入
        this.$message.info('请重新输入您的API密钥');
      }).catch(() => {
        // 用户取消操作
      });
    },

    hideFullApiKey() {
      this.isApiKeyMasked = true;
      this.showingFullKey = false;
      this.modelConfig.apiKey = this.originalMaskedKey;
    }
  },
  
  // 组件挂载时加载配置
  async mounted() {
    await this.loadConfig();
  }
}
</script>

<style scoped>
/* 页面容器 */
.ai-chat-management {
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

.config-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.help-text {
  color: #909399;
  margin-left: 10px;
}

.test-success {
  color: #67C23A;
  margin-left: 10px;
}

.test-error {
  color: #F56C6C;
  margin-left: 10px;
}

.avatar-upload .avatar-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.avatar-upload .avatar-uploader:hover {
  border-color: #409EFF;
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 100px;
  height: 100px;
  line-height: 100px;
  text-align: center;
}

.avatar {
  width: 100px;
  height: 100px;
  display: block;
}

.header-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.test-chat-btn {
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 1000;
  border-radius: 50px;
  padding: 15px 20px;
}

.chat-test-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  background: #f5f5f5;
}

.message {
  margin-bottom: 15px;
}

.message.user {
  text-align: right;
}

.message.bot {
  text-align: left;
}

.message-content {
  display: inline-block;
  padding: 10px 15px;
  border-radius: 10px;
  max-width: 80%;
  word-wrap: break-word;
}

.message.user .message-content {
  background: #409EFF;
  color: white;
}

.message.bot .message-content {
  background: white;
  color: #333;
  border: 1px solid #e4e7ed;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}

.chat-input {
  display: flex;
  padding: 10px;
  border-top: 1px solid #e4e7ed;
}

.chat-input .el-input {
  margin-right: 10px;
}

.custom-model-select {
  border: 2px dashed #67C23A !important;
}

.custom-model-select .el-input__inner {
  border-color: #67C23A;
  background-color: #f0f9ff;
}

.thinking-hint {
  color: #E6A23C !important;
  font-weight: 500;
  margin-top: 5px;
  display: block;
}

.api-key-status {
  margin-top: 8px;
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #67C23A;
}

.api-key-status .el-icon-success {
  margin-right: 5px;
  color: #67C23A;
}

.api-key-status span {
  margin-right: 10px;
}

.api-key-status .el-button {
  padding: 0;
  font-size: 12px;
  color: #409EFF;
}
</style> 