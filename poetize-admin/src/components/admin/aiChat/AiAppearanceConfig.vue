<template>
  <div class="ai-appearance-config">
    <el-form :model="appearanceConfig" label-width="120px">
      <el-form-item id="field-ai-bot-name" label="机器人名称">
        <el-input v-model="appearanceConfig.botName" placeholder="例如: 小助手"></el-input>
      </el-form-item>

      <el-form-item id="field-ai-bot-avatar" label="机器人头像">
        <div class="bot-avatar-setting">
          <el-avatar
            class="bot-avatar-preview"
            :class="{ 'bot-avatar-empty': !appearanceConfig.botAvatar }"
            :size="72"
            :src="botAvatarUrl"
          >
          </el-avatar>

          <div class="bot-avatar-content">
            <div class="bot-avatar-title">
              {{ appearanceConfig.botAvatar ? '已设置 AI 助手头像' : '使用默认 AI 助手头像' }}
            </div>
            <div class="bot-avatar-actions">
              <el-button
                size="mini"
                type="primary"
                plain
                @click="showAvatarUploader = !showAvatarUploader"
              >
                {{ appearanceConfig.botAvatar ? '更换头像' : '上传头像' }}
              </el-button>
              <el-button
                v-if="appearanceConfig.botAvatar"
                size="mini"
                type="text"
                @click="clearBotAvatar"
              >
                清除
              </el-button>
            </div>
          </div>
        </div>

        <transition name="avatar-upload-pop">
          <div v-show="showAvatarUploader" class="bot-avatar-upload-panel">
            <uploadPicture
              :isAdmin="true"
              :prefix="'aiBotAvatar'"
              :maxNumber="1"
              @addPicture="addBotAvatar"
            ></uploadPicture>
          </div>
        </transition>
      </el-form-item>

      <el-form-item id="field-ai-theme-color" label="主题颜色">
        <el-color-picker v-model="appearanceConfig.themeColor"></el-color-picker>
        <span style="margin-left: 10px; color: #909399; font-size: 12px;">用于用户消息气泡颜色</span>
      </el-form-item>

      <el-form-item id="field-ai-typing" label="显示打字动效">
        <el-switch v-model="appearanceConfig.typingAnimation"></el-switch>
      </el-form-item>

      <el-form-item id="field-ai-timestamp" label="显示时间戳">
        <el-switch v-model="appearanceConfig.showTimestamp"></el-switch>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
const uploadPicture = () => import('../../common/uploadPicture');

const DEFAULT_APPEARANCE_CONFIG = {
  botAvatar: '',
  botName: 'AI助手',
  themeColor: '#409EFF',
  typingAnimation: true,
  showTimestamp: true
};

function normalizeAppearanceConfig(value = {}) {
  return {
    ...DEFAULT_APPEARANCE_CONFIG,
    ...value
  };
}

export default {
  name: 'AiAppearanceConfig',
  components: {
    uploadPicture
  },
  props: {
    value: {
      type: Object,
      default: () => ({ ...DEFAULT_APPEARANCE_CONFIG })
    }
  },
  
  data() {
    return {
      appearanceConfig: normalizeAppearanceConfig(this.value),
      showAvatarUploader: false
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        const nextConfig = normalizeAppearanceConfig(newVal);
        if (JSON.stringify(nextConfig) !== JSON.stringify(this.appearanceConfig)) {
          this.appearanceConfig = nextConfig;
        }
      },
      deep: true
    },
    
    appearanceConfig: {
      handler(newVal) {
        if (JSON.stringify(newVal) !== JSON.stringify(this.value)) {
          this.$emit('input', newVal);
        }
      },
      deep: true
    }
  },

  computed: {
    botAvatarUrl() {
      return this.$common.getAiAvatarUrl(this.appearanceConfig.botAvatar);
    }
  },

  methods: {
    addBotAvatar(url) {
      this.appearanceConfig.botAvatar = url || '';
      this.showAvatarUploader = false;
    },

    clearBotAvatar() {
      this.appearanceConfig.botAvatar = '';
    }
  }
}
</script>

<style scoped>
.ai-appearance-config {
  max-height: 500px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 10px;
}

.bot-avatar-setting {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 76px;
}

.bot-avatar-preview {
  flex: 0 0 auto;
  background: #f8fbff;
  border: 1px solid rgba(64, 158, 255, 0.18);
  box-shadow: 0 8px 18px rgba(31, 45, 61, 0.08);
}

.bot-avatar-preview ::v-deep img {
  object-fit: cover;
  transform: scale(1.08);
}

.bot-avatar-empty {
  border-color: rgba(64, 158, 255, 0.3);
}

.bot-avatar-content {
  min-width: 0;
}

.bot-avatar-title {
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.2;
}

.bot-avatar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bot-avatar-upload-panel {
  max-width: 280px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.bot-avatar-upload-panel ::v-deep .el-upload,
.bot-avatar-upload-panel ::v-deep .el-upload-dragger {
  width: 100%;
}

.bot-avatar-upload-panel ::v-deep .el-upload-dragger {
  height: 92px;
  padding: 12px;
}

.bot-avatar-upload-panel ::v-deep .el-upload__text svg {
  width: 30px;
  height: 30px;
}

.bot-avatar-upload-panel ::v-deep .el-upload__text div {
  margin-top: 4px;
  font-size: 12px;
}

.bot-avatar-upload-panel ::v-deep .el-upload__tip {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.4;
}

.bot-avatar-upload-panel ::v-deep .el-button {
  padding: 7px 14px;
}

.avatar-upload-pop-enter-active,
.avatar-upload-pop-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.avatar-upload-pop-enter,
.avatar-upload-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 移动端对话框中不限制高度 */
@media screen and (max-width: 768px) {
  .ai-appearance-config {
    max-height: none;
    overflow-y: visible;
  }
}

/* PC端样式 - 768px以上 */
@media screen and (min-width: 769px) {
  ::v-deep .el-form-item__label {
    float: left !important;
  }
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .ai-appearance-config {
    padding: 0;
  }

  .ai-appearance-config .el-form-item {
    margin-bottom: 15px;
  }

  /* 标签适配 - 垂直布局 */
  .ai-appearance-config .el-form-item__label {
    float: none !important;
    width: 100% !important;
    text-align: left !important;
    font-size: 13px;
    line-height: 1.4;
    margin-bottom: 8px !important;
    padding-bottom: 0 !important;
  }

  .ai-appearance-config .el-form-item__content {
    margin-left: 0 !important;
    width: 100% !important;
  }

  /* 提示文本 */
  .ai-appearance-config .el-form-item__content span {
    font-size: 11px;
  }

  .bot-avatar-setting {
    align-items: flex-start;
  }

  .bot-avatar-upload-panel {
    max-width: 100%;
  }
}

@media screen and (max-width: 480px) {
  .ai-appearance-config .el-form-item {
    margin-bottom: 12px;
  }

  .ai-appearance-config .el-form-item__label {
    font-size: 12px;
  }

  .ai-appearance-config .el-form-item__content span {
    font-size: 10px;
  }
}
</style> 
