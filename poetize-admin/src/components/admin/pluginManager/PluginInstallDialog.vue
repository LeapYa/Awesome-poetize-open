<template>
  <el-dialog title="安装插件包" :visible.sync="dialogVisible" width="550px" custom-class="centered-dialog">
    <div
      class="plugin-drop-zone"
      :class="{ 'drag-over': isDragOver }"
      @dragenter.prevent="isDragOver = true"
      @dragover.prevent="isDragOver = true"
      @dragleave.prevent="isDragOver = false"
      @drop.prevent="handleDrop">
      <div v-if="!installFile">
        <i class="el-icon-upload" style="font-size: 48px; color: #c0c4cc;"></i>
        <p style="margin: 10px 0 5px; color: #606266;">拖拽 .zip 插件包到此处</p>
        <p style="color: #909399; font-size: 12px;">或者</p>
        <el-button type="primary" size="small" @click="$refs.pluginFileInput.click()" style="margin-top: 8px;">选择文件</el-button>
        <input ref="pluginFileInput" type="file" accept=".zip" style="display:none" @change="handleFileSelect" />
      </div>
      <div v-else>
        <i class="el-icon-document" style="font-size: 36px; color: #409EFF;"></i>
        <p style="margin: 8px 0; color: #303133; font-weight: 500;">{{ installFile.name }}</p>
        <p style="color: #909399; font-size: 12px;">{{ (installFile.size / 1024).toFixed(1) }} KB</p>
        <el-button type="text" size="small" @click="resetSelection">重新选择</el-button>
      </div>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="installing" :disabled="!installFile" @click="installPlugin">安装</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'PluginInstallDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      installFile: null,
      installing: false,
      isDragOver: false
    };
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible;
      },
      set(value) {
        this.$emit('update:visible', value);
      }
    }
  },
  methods: {
    resetSelection() {
      this.installFile = null;
    },
    handleClose() {
      this.resetSelection();
      this.dialogVisible = false;
    },
    handleDrop(e) {
      this.isDragOver = false;
      const file = e.dataTransfer.files[0];
      if (file) {
        this.handleIncomingFile(file);
      }
    },
    handleFileSelect(e) {
      const file = e.target.files[0];
      if (file) {
        this.handleIncomingFile(file);
      }
      e.target.value = '';
    },
    handleIncomingFile(file) {
      if (!file.name.endsWith('.zip')) {
        this.$message.error('只支持 .zip 格式的插件包');
        return;
      }
      this.installFile = file;
    },
    installPlugin() {
      if (!this.installFile) return;
      this.installing = true;
      const formData = new FormData();
      formData.append('file', this.installFile);
      this.$http.upload(this.$constant.baseURL + '/plugin/install', formData)
        .then((res) => {
          if (res.code === 200) {
            const data = res.data || {};
            this.$message.success(data.message || '插件安装成功！');
            if (data.warning) {
              this.$message({ type: 'warning', message: data.warning, duration: 8000, showClose: true });
            }
            this.$emit('installed');
            this.handleClose();
          } else {
            this.$message.error(res.message || '安装失败');
          }
        })
        .catch((err) => {
          this.$message.error(err.message || '安装失败');
        })
        .finally(() => {
          this.installing = false;
        });
    }
  }
}
</script>

<style scoped>
.plugin-drop-zone {
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  transition: border-color 0.3s, background 0.3s;
  cursor: pointer;
}
.plugin-drop-zone.drag-over {
  border-color: #409EFF;
  background: #ecf5ff;
}
.plugin-drop-zone:hover {
  border-color: #c0c4cc;
}
</style>
