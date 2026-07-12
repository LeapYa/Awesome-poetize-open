<!-- eslint-disable vue/no-mutating-props -->
<template>
  <div>
    <el-divider content-position="left">模型配置</el-divider>
    <el-form-item label="模型路径">
      <el-input v-model="waifuConfig.modelPath" placeholder="例如: Potion-Maker/Pio"></el-input>
      <div class="sub-title">相对于 Live2D API 的 model/ 目录，本地包和 CDN 使用同一套路径</div>
    </el-form-item>
    <el-form-item label="材质列表">
      <el-input type="textarea" :rows="3" v-model="waifuConfig.texturesStr" placeholder="每行一个材质路径，例如：&#10;Potion-Maker/Pio&#10;Potion-Maker/Pio-2"></el-input>
      <div class="sub-title">模型的不同材质/服装，每行一个</div>
    </el-form-item>
    <el-form-item label="缩放比例">
      <el-slider v-model="waifuConfig.scale" :min="0.5" :max="2" :step="0.1" show-input></el-slider>
    </el-form-item>
    <el-form-item label="预览图">
      <div class="thumbnail-upload-container">
        <el-avatar
          v-if="waifuConfig.thumbnailUrl"
          :size="80"
          :src="waifuConfig.thumbnailUrl"
          style="border: 2px solid #e0e0e0; margin-right: 15px;">
        </el-avatar>
        <el-avatar
          v-else
          :size="80"
          :src="previewImage"
          style="background: #f5f7fa; border: 1px solid #dcdfe6; color: #909399; margin-right: 15px;"
          icon="el-icon-s-custom">
        </el-avatar>
        <el-button type="primary" icon="el-icon-upload2" @click="uploadPictureDialog = true">选择/上传图片</el-button>
      </div>
      <div class="sub-title upload-tip">建议尺寸: 200x200px，支持 JPG/PNG 格式</div>
    </el-form-item>

    <el-dialog title="上传/选择图片" :visible.sync="uploadPictureDialog" width="45%" append-to-body>
      <upload-picture :maxNumber="1" :isAdmin="true" @addPicture="handleThumbnailSuccess"></upload-picture>
    </el-dialog>

    <el-divider content-position="left">对话配置</el-divider>
    <el-form-item label="问候语">
      <el-input type="textarea" :rows="2" v-model="waifuConfig.greetingStr" placeholder="每行一句，随机选择显示"></el-input>
    </el-form-item>
    <el-form-item label="闲置消息">
      <el-input type="textarea" :rows="2" v-model="waifuConfig.idleStr" placeholder="每行一句，随机选择显示"></el-input>
    </el-form-item>
  </div>
</template>

<script>
/* eslint-disable vue/no-mutating-props */
import UploadPicture from '@/components/common/uploadPicture.vue';

export default {
  name: 'WaifuPluginEditor',
  components: {
    UploadPicture
  },
  props: {
    waifuConfig: { type: Object, required: true },
    previewImage: { type: String, default: '' }
  },
  data() {
    return {
      uploadPictureDialog: false
    };
  },
  methods: {
    handleThumbnailSuccess(response) {
      if (response.code === 200) {
        this.waifuConfig.thumbnailUrl = response.data;
        this.$message.success('预览图上传成功');
      } else {
        this.$message.error(response.message || '上传失败');
      }
    }
  }
}
</script>

<style scoped>
.sub-title {
  font-size: 12px;
  color: #999;
}
.thumbnail-upload-container {
  display: flex;
  align-items: center;
}
.upload-tip {
  margin-top: 8px;
}
</style>
