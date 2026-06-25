<template>
  <div class="ai-chat-settings">
    <el-form :model="chatConfig" label-width="120px">
      <el-form-item id="field-ai-system-prompt" label="系统提示词">
        <el-input 
          v-model="chatConfig.systemPrompt" 
          type="textarea" 
          :rows="4"
          placeholder="请输入AI的角色设定和行为指导">
        </el-input>
        <small class="help-text">定义AI的角色和回复风格</small>
      </el-form-item>

      <el-form-item id="field-ai-comment-skill" label="评论区Skill">
        <span style="color: #909399; font-size: 13px;">
          请在「Skill 管理」面板中管理评论区 Skill
        </span>
      </el-form-item>

      <el-form-item id="field-ai-welcome" label="欢迎消息">
        <el-input 
          v-model="chatConfig.welcomeMessage" 
          type="textarea" 
          :rows="2"
          placeholder="AI首次对话时的欢迎消息">
        </el-input>
      </el-form-item>

      <el-form-item id="field-ai-history-count" label="对话历史数">
        <el-input-number 
          v-model="chatConfig.historyCount" 
          :min="0" 
          :max="20">
        </el-input-number>
        <small class="help-text">保留多少条历史对话用于上下文理解</small>
      </el-form-item>

      <el-form-item id="field-ai-rate-limit" label="速率限制">
        <el-input-number 
          v-model="chatConfig.rateLimit" 
          :min="1" 
          :max="100"
          :precision="0">
        </el-input-number>
        <small class="help-text">每分钟最多允许的消息数量</small>
      </el-form-item>

      <el-form-item id="field-ai-max-length" label="最大消息长度">
        <el-input-number 
          v-model="chatConfig.maxMessageLength" 
          :min="100" 
          :max="2000"
          :precision="0">
        </el-input-number>
        <small class="help-text">用户单条消息的最大字符数限制</small>
      </el-form-item>

      <el-form-item id="field-ai-require-login" label="需要登录">
        <el-switch v-model="chatConfig.requireLogin"></el-switch>
        <small class="help-text">是否需要用户登录后才能使用AI聊天</small>
      </el-form-item>

      <el-form-item id="field-ai-save-history" label="保存聊天记录">
        <el-switch v-model="chatConfig.saveHistory"></el-switch>
        <small class="help-text">是否保存用户的聊天历史记录</small>
      </el-form-item>

      <el-form-item id="field-ai-content-filter" label="内容过滤">
        <el-switch v-model="chatConfig.contentFilter"></el-switch>
        <small class="help-text">启用内容安全过滤</small>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
export default {
  name: 'AiChatSettings',
  props: {
    value: {
      type: Object,
      default: () => ({
        systemPrompt: "AI assistant. Respond in Chinese naturally.",
        welcomeMessage: "你好！有什么可以帮助你的吗？",
        historyCount: 10,
        rateLimit: 20,
        requireLogin: false,
        saveHistory: true,
        contentFilter: true,
        maxMessageLength: 500
      })
    }
  },
  
  data() {
    return {
      chatConfig: { ...this.value }
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        if (JSON.stringify(newVal) !== JSON.stringify(this.chatConfig)) {
          this.chatConfig = { ...newVal };
        }
      },
      deep: true
    },
    
    chatConfig: {
      handler(newVal) {
        if (JSON.stringify(newVal) !== JSON.stringify(this.value)) {
          this.$emit('input', newVal);
        }
      },
      deep: true
    }
  }
}
</script>

<style scoped>
.ai-chat-settings {
  max-height: 500px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 10px;
}

/* 移动端对话框中不限制高度 */
@media screen and (max-width: 768px) {
  .ai-chat-settings {
    max-height: none;
    overflow-y: visible;
  }
}

.help-text {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 5px;
  display: block;
}

/* PC端样式 - 768px以上 */
@media screen and (min-width: 769px) {
  ::v-deep .el-form-item__label {
    float: left !important;
  }
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .ai-chat-settings {
    padding: 0;
  }

  .ai-chat-settings .el-form-item {
    margin-bottom: 15px;
  }

  /* 标签适配 - 垂直布局 */
  .ai-chat-settings .el-form-item__label {
    float: none !important;
    width: 100% !important;
    text-align: left !important;
    font-size: 13px;
    line-height: 1.4;
    margin-bottom: 8px !important;
    padding-bottom: 0 !important;
  }

  .ai-chat-settings .el-form-item__content {
    margin-left: 0 !important;
    width: 100% !important;
  }

  /* 帮助文本字号优化 */
  .help-text {
    font-size: 11px;
    line-height: 1.3;
    margin-top: 3px;
  }

  /* 文本域 */
  .ai-chat-settings .el-textarea__inner {
    font-size: 13px;
  }
}

@media screen and (max-width: 480px) {
  .ai-chat-settings .el-form-item {
    margin-bottom: 12px;
  }

  .ai-chat-settings .el-form-item__label {
    font-size: 12px;
  }

  .help-text {
    font-size: 10px;
  }

  .ai-chat-settings .el-textarea__inner {
    font-size: 12px;
  }
}
</style> 
