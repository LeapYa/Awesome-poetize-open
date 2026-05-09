<template>
  <div class="ai-advanced-config">
    <el-form :model="advancedConfig" label-width="120px">
      <el-form-item id="field-ai-proxy" label="代理设置">
        <el-input v-model="advancedConfig.proxy" placeholder="例如: http://proxy.example.com:8080"></el-input>
      </el-form-item>

      <el-form-item id="field-ai-timeout" label="超时时间(秒)">
        <el-input-number v-model="advancedConfig.timeout" :min="5" :max="300"></el-input-number>
      </el-form-item>

      <el-form-item id="field-ai-retry" label="重试次数">
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

      <el-form-item id="field-ai-debug" label="调试模式">
        <el-switch v-model="advancedConfig.debugMode"></el-switch>
        <small class="help-text">启用后会在控制台输出详细日志</small>
      </el-form-item>

      <el-form-item label="数据导出">
        <el-button @click="exportConfig">导出配置</el-button>
        <el-button @click="triggerImport">导入配置</el-button>
      </el-form-item>
    </el-form>

    <!-- 隐藏的文件选择器 -->
    <input 
      ref="fileInput" 
      type="file" 
      accept=".json" 
      style="display: none;" 
      @change="handleFileImport" />
  </div>
</template>

<script>
export default {
  name: 'AiAdvancedConfig',
  props: {
    value: {
      type: Object,
      default: () => ({
        proxy: '',
        timeout: 30,
        retryCount: 3,
        customHeaders: [],
        debugMode: false,
        enableThinking: false,
        reasoningEffort: 'medium',
        thinkingProfile: 'auto',
        thinkingExtraBodyText: ''
      })
    }
  },
  
  data() {
    return {
      advancedConfig: {
        reasoningEffort: 'medium',
        thinkingProfile: 'auto',
        thinkingExtraBodyText: '',
        ...this.value
      }
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        if (JSON.stringify(newVal) !== JSON.stringify(this.advancedConfig)) {
          this.advancedConfig = {
            reasoningEffort: 'medium',
            thinkingProfile: 'auto',
            thinkingExtraBodyText: '',
            ...newVal
          };
        }
      },
      deep: true
    },
    
    advancedConfig: {
      handler(newVal) {
        if (JSON.stringify(newVal) !== JSON.stringify(this.value)) {
          this.$emit('input', newVal);
        }
      },
      deep: true
    }
  },
  
  methods: {
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
      // 触发父组件的导出事件
      this.$emit('export-config');
    },
    
    // 触发文件选择
    triggerImport() {
      this.$refs.fileInput.click();
    },
    
    // 处理文件导入
    handleFileImport(event) {
      const file = event.target.files[0];
      if (!file) return;
      
      // 验证文件类型
      if (!file.name.endsWith('.json')) {
        this.$message.error('只能导入JSON格式的配置文件！');
        return;
      }
      
      // 读取文件内容
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const config = JSON.parse(e.target.result);
          this.$emit('import-config', config);
          this.$message.success('配置导入成功');
        } catch (error) {
          this.$message.error('配置文件格式错误');
        }
      };
      reader.onerror = () => {
        this.$message.error('文件读取失败');
      };
      reader.readAsText(file);
      
      // 清空input，允许重复选择同一文件
      event.target.value = '';
    }
  }
}
</script>

<style scoped>
.ai-advanced-config {
  max-height: 500px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 10px;
}

/* 移动端对话框中不限制高度 */
@media screen and (max-width: 768px) {
  .ai-advanced-config {
    max-height: none;
    overflow-y: visible;
  }
}

.header-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
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
  .ai-advanced-config {
    padding: 0;
  }

  .ai-advanced-config .el-form-item {
    margin-bottom: 15px;
  }

  /* 标签适配 - 垂直布局 */
  .ai-advanced-config .el-form-item__label {
    float: none !important;
    width: 100% !important;
    text-align: left !important;
    font-size: 13px;
    line-height: 1.4;
    margin-bottom: 8px !important;
    padding-bottom: 0 !important;
  }

  .ai-advanced-config .el-form-item__content {
    margin-left: 0 !important;
    width: 100% !important;
  }

  /* 帮助文本字号优化 */
  .help-text {
    font-size: 11px;
    line-height: 1.3;
    margin-top: 3px;
  }

  /* Header项优化 */
  .header-item {
    margin-bottom: 8px;
  }

  .header-item .el-input {
    margin-right: 5px !important;
  }

  /* 对话框适配 */
  .ai-advanced-config .el-dialog {
    width: 90% !important;
  }

  .ai-advanced-config .el-dialog__body {
    padding: 15px !important;
  }

  .ai-advanced-config .el-upload__text {
    font-size: 13px;
  }

  .ai-advanced-config .el-upload__tip {
    font-size: 11px;
  }
}

@media screen and (max-width: 480px) {
  .ai-advanced-config .el-form-item {
    margin-bottom: 12px;
  }

  .ai-advanced-config .el-form-item__label {
    font-size: 12px;
  }

  .help-text {
    font-size: 10px;
  }

  .ai-advanced-config .el-dialog {
    width: 95% !important;
  }

  .ai-advanced-config .el-dialog__body {
    padding: 10px !important;
  }

  .ai-advanced-config .el-upload__text {
    font-size: 12px;
  }

  .ai-advanced-config .el-upload__tip {
    font-size: 10px;
  }
}
</style> 
