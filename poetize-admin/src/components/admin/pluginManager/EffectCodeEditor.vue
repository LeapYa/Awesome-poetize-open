<!-- eslint-disable vue/no-mutating-props -->
<template>
  <div>
    <el-form-item label="JS代码" prop="pluginCode">
      <el-input
        type="textarea"
        :rows="10"
        v-model="form.pluginCode"
        :placeholder="pluginCodeEditorMeta.placeholder"
        style="font-family: 'Consolas', 'Monaco', monospace;"></el-input>
      <div class="sub-title">
        <span v-html="pluginCodeEditorMeta.description"></span><br/>
        <template v-if="pluginCodeEditorMeta.showAnimeDoc">
          <strong>anime</strong> 为 anime.js 动画库，可用于创建复杂动画效果。
        </template>
        <template v-else>
          建议自行做好重复挂载保护、窗口尺寸变化处理和 DOM 清理逻辑。
        </template>
        <a href="https://animejs.com/documentation/" target="_blank" style="color: #409EFF;">查看anime.js文档</a><br/>
        不会写代码？<a href="javascript:void(0)" @click="$emit('open-ai-prompt')" style="color: #409EFF;">使用AI生成效果代码</a>
      </div>
    </el-form-item>

    <el-form-item>
      <el-button type="primary" plain icon="el-icon-view" @click="openPreview">
        预览效果
      </el-button>
      <span class="sub-title" style="margin-left: 10px;">在独立预览窗口中实时查看当前代码和配置的运行效果</span>
    </el-form-item>

    <!-- AI 提示词弹窗 -->
    <el-dialog :title="aiPromptDialog.title" :visible.sync="dialogVisible" width="600px" custom-class="centered-dialog" append-to-body>
      <p style="margin-bottom: 15px; color: #666;">{{ aiPromptDialog.intro }}</p>
      <pre class="prompt-box">{{ aiPromptDialog.prompt }}</pre>
      <div style="margin-top: 15px;">
        <strong style="color: #409EFF;">{{ aiPromptDialog.examplesTitle }}</strong>
        <p style="margin: 8px 0; color: #909399; font-size: 12px;">{{ aiPromptDialog.examplesHint }}</p>
        <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
          <thead>
            <tr style="background: #f5f7fa;">
              <th style="padding: 10px; border: 1px solid #ebeef5; text-align: left; width: 100px;">想要的效果</th>
              <th style="padding: 10px; border: 1px solid #ebeef5; text-align: left;">描述</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="example in aiPromptDialog.examples" :key="example.name">
              <td style="padding: 8px 10px; border: 1px solid #ebeef5;">{{ example.name }}</td>
              <td style="padding: 8px 10px; border: 1px solid #ebeef5;">{{ example.description }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">关 闭</el-button>
        <el-button type="primary" @click="copyAiPrompt">复制提示词</el-button>
      </span>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog
      title="粒子特效预览"
      :visible.sync="previewVisible"
      width="90%"
      top="3vh"
      append-to-body
      :before-close="closePreview"
      custom-class="plugin-preview-dialog">
      <div class="preview-toolbar">
        <span class="preview-tip">正在预览：<strong>{{ form.pluginName || form.pluginKey || '未命名' }}</strong>（此预览不会保存或应用到前台，仅用于查看效果）</span>
        <el-button size="mini" type="primary" plain icon="el-icon-refresh" @click="openPreview">重新预览</el-button>
      </div>
      <div class="preview-frame-wrapper">
        <iframe
          v-if="previewVisible && previewSrcdoc"
          ref="previewFrame"
          :srcdoc="previewSrcdoc"
          class="preview-frame"
          sandbox="allow-scripts allow-same-origin"
          @load="onPreviewLoad"></iframe>
        <div v-else class="preview-placeholder">点击"重新预览"加载效果</div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
/* eslint-disable vue/no-mutating-props */
// 导入 SDK 源码字符串，注入预览 iframe，确保执行环境与前台等价
import { sdkCode } from '@/utils/plugin-sdk-source'

export default {
  name: 'EffectCodeEditor',
  props: {
    form: { type: Object, required: true },
    pluginCodeEditorMeta: { type: Object, required: true },
    aiPromptDialog: { type: Object, required: true },
    aiPromptVisible: { type: Boolean, default: false }
  },
  data() {
    return {
      previewVisible: false,
      previewSrcdoc: ''
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.aiPromptVisible;
      },
      set(value) {
        this.$emit('update:ai-prompt-visible', value);
      }
    }
  },
  methods: {
    copyAiPrompt() {
      navigator.clipboard.writeText(this.aiPromptDialog.prompt).then(() => {
        this.$message.success(this.aiPromptDialog.copySuccessMessage);
      }).catch(() => {
        this.$message.error('复制失败，请手动选择复制');
      });
    },

    /**
     * 构造预览 HTML 并打开预览弹窗。
     * 在独立 iframe 中执行插件代码，与后台主界面 DOM 隔离，关闭即清理。
     */
    openPreview() {
      const pluginCode = this.form.pluginCode || ''
      if (!pluginCode.trim()) {
        this.$message.warning('请先填写 JS 代码再预览')
        return
      }

      // 解析配置 JSON
      let configObj = {}
      if (this.form.pluginConfig) {
        try {
          configObj = JSON.parse(this.form.pluginConfig)
        } catch (e) {
          this.$message.warning('插件配置 JSON 解析失败，将使用空配置预览')
        }
      }

      const pluginKey = this.form.pluginKey || 'preview'
      const frontendCss = this.form.frontendCss || ''
      const configJsonStr = JSON.stringify(configObj)

      // 构造完整 HTML：渐变背景模拟前台 + SDK + 插件代码执行
      // 注意：SFC 解析器会把字面量的 script 闭合标签当作 SFC script 块结束标记，
      // 因此用字符串拼接构造闭合标签，避免在源码中出现该字面量。
      const scriptClose = '<' + '/script>'
      const html = `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }
  body {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  ${frontendCss}
</style>
</head>
<body>
<script>
${sdkCode}
${scriptClose}
<script>
(function() {
  try {
    var pluginKey = ${JSON.stringify(pluginKey)};
    var config = ${configJsonStr};
    var pluginCode = ${JSON.stringify(pluginCode)};
    window.PoetizePlugin._internal.setPluginConfig(pluginKey, config);
    window.PoetizePlugin._internal.loadPluginCode(pluginKey, pluginCode, config);
  } catch (e) {
    document.body.innerHTML = '<pre style="color:#fff;background:rgba(0,0,0,0.7);padding:20px;margin:20px;border-radius:8px;font-size:14px;">执行错误: ' + (e && e.message ? e.message : String(e)) + '</pre>';
    console.error('[预览] 插件代码执行失败:', e);
  }
})();
${scriptClose}
</body>
</html>`

      this.previewSrcdoc = html
      this.previewVisible = true
    },

    /**
     * 关闭预览弹窗，清空 srcdoc 以释放 iframe 资源。
     */
    closePreview() {
      this.previewSrcdoc = ''
      this.previewVisible = false
    },

    onPreviewLoad() {
      // iframe 加载完成，可用于后续调试钩子（暂无操作）
    }
  }
}
</script>

<style scoped>
.sub-title {
  font-size: 12px;
  color: #999;
  line-height: 20px;
}
.prompt-box {
  white-space: pre-wrap;
  background: #f5f5f5;
  padding: 15px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.7;
  max-height: 350px;
  overflow-y: auto;
  border: 1px solid #e0e0e0;
}
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 0 4px;
}
.preview-tip {
  font-size: 13px;
  color: #606266;
}
.preview-frame-wrapper {
  width: 100%;
  height: calc(90vh - 120px);
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
}
.preview-frame {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}
.preview-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #909399;
  font-size: 14px;
}
</style>
