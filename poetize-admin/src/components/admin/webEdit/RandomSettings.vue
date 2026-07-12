<template>
  <div class="random-settings-container">
    <SectionTag>随机设置</SectionTag>
    <el-tabs v-model="activeName" type="card" class="random-settings-tabs">
      <el-tab-pane label="随机名称" name="randomName">
        <div>
          <el-tag
            :key="i"
            effect="dark"
            v-for="(name, i) in localRandomName"
            closable
            :disable-transitions="false"
            :type="types[Math.floor(Math.random() * 5)]"
            @close="handleClose(localRandomName, name)">
            {{name}}
          </el-tag>
          <el-input
            class="input-new-tag"
            v-if="inputRandomNameVisible"
            v-model="inputRandomNameValue"
            ref="saveRandomNameInput"
            size="small"
            placeholder="请输入随机名称"
            @keyup.enter.native="handleInputRandomNameConfirm"
            @blur="handleInputRandomNameConfirm"
            @keydown.enter.native="handleInputRandomNameConfirm">
          </el-input>
          <el-button v-else class="button-new-tag" size="small" @click="showRandomNameInput">+ 随机名称</el-button>
          <div class="myCenter" style="margin-bottom: 22px">
            <el-button type="primary" @click="saveRandomName()" :loading="loadingName">保存随机名称</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="随机头像" name="randomAvatar">
        <div>
          <div :key="i"
               style="display: flex"
               v-for="(avatar, i) in localRandomAvatar">
            <el-tag
              style="white-space: normal;height: unset"
              closable
              :disable-transitions="false"
              @close="handleClose(localRandomAvatar, avatar)">
              {{avatar}}
            </el-tag>
            <div>
              <el-image lazy class="table-td-thumb"
                        style="margin: 10px"
                        :preview-src-list="[avatar]"
                        :src="avatar"
                        fit="cover"></el-image>
            </div>
          </div>
          <el-input
            class="input-new-tag"
            v-if="inputRandomAvatarVisible"
            v-model="inputRandomAvatarValue"
            ref="saveRandomAvatarInput"
            size="small"
            @keyup.enter.native="handleInputRandomAvatarConfirm"
            @blur="handleInputRandomAvatarConfirm">
          </el-input>
          <el-button v-else class="button-new-tag" size="small" @click="showRandomAvatarInput">+ 随机头像</el-button>
          <uploadPicture :isAdmin="true" :prefix="'randomAvatar'" style="margin: 10px" @addPicture="addRandomAvatar"
                         :maxSize="1"
                         :maxNumber="5"></uploadPicture>
          <div class="myCenter" style="margin-bottom: 22px">
            <el-button type="primary" @click="saveRandomAvatar()" :loading="loadingAvatar">保存随机头像</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="随机封面" name="randomCover">
        <div>
          <div :key="i"
               style="display: flex"
               v-for="(cover, i) in localRandomCover">
            <el-tag
              style="white-space: normal;height: unset"
              closable
              :disable-transitions="false"
              @close="handleClose(localRandomCover, cover)">
              {{cover}}
            </el-tag>
            <div>
              <el-image lazy class="table-td-thumb"
                        style="margin: 10px"
                        :preview-src-list="[cover]"
                        :src="cover"
                        fit="cover"></el-image>
            </div>
          </div>
          <el-input
            class="input-new-tag"
            v-if="inputRandomCoverVisible"
            v-model="inputRandomCoverValue"
            ref="saveRandomCoverInput"
            size="small"
            @keyup.enter.native="handleInputRandomCoverConfirm"
            @blur="handleInputRandomCoverConfirm">
          </el-input>
          <el-button v-else class="button-new-tag" size="small" @click="showRandomCoverInput">+ 随机封面</el-button>
          <uploadPicture :isAdmin="true" :prefix="'randomCover'" style="margin: 10px" @addPicture="addRandomCover"
                         :maxNumber="5"></uploadPicture>
          <div class="myCenter" style="margin-bottom: 40px">
            <el-button type="primary" @click="saveRandomCover()" :loading="loadingCover">保存随机封面</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';
const uploadPicture = () => import("../../common/uploadPicture");

export default {
  name: 'RandomSettings',
  components: { SectionTag, uploadPicture },
  props: {
    randomName: { type: Array, default: () => [] },
    randomAvatar: { type: Array, default: () => [] },
    randomCover: { type: Array, default: () => [] },
    webInfoId: { type: [Number, String], default: null }
  },
  data() {
    return {
      activeName: 'randomName',
      types: ['', 'success', 'info', 'danger', 'warning'],
      localRandomName: [...this.randomName],
      localRandomAvatar: [...this.randomAvatar],
      localRandomCover: [...this.randomCover],
      inputRandomNameVisible: false,
      inputRandomNameValue: '',
      inputRandomAvatarVisible: false,
      inputRandomAvatarValue: '',
      inputRandomCoverVisible: false,
      inputRandomCoverValue: '',
      loadingName: false,
      loadingAvatar: false,
      loadingCover: false
    }
  },
  watch: {
    randomName: {
      handler(val) { this.localRandomName = [...(val || [])]; },
      deep: true
    },
    randomAvatar: {
      handler(val) { this.localRandomAvatar = [...(val || [])]; },
      deep: true
    },
    randomCover: {
      handler(val) { this.localRandomCover = [...(val || [])]; },
      deep: true
    }
  },
  methods: {
    handleClose(array, item) {
      array.splice(array.indexOf(item), 1);
    },
    // ---- 随机名称 ----
    handleInputRandomNameConfirm() {
      if (!Array.isArray(this.localRandomName)) {
        this.localRandomName = [];
      }
      if (this.inputRandomNameValue && this.inputRandomNameValue.trim()) {
        this.localRandomName.push(this.inputRandomNameValue.trim());
      }
      this.inputRandomNameVisible = false;
      this.inputRandomNameValue = '';
    },
    showRandomNameInput() {
      this.inputRandomNameVisible = true;
      this.$nextTick(() => {
        this.$refs.saveRandomNameInput.$refs.input.focus();
      });
    },
    saveRandomName() {
      if (!this.webInfoId) {
        this.$message.error('网站信息ID不存在，请刷新页面重试');
        return;
      }
      if (!Array.isArray(this.localRandomName)) {
        this.localRandomName = [];
      }
      this.localRandomName = this.localRandomName.filter(name => name && name.trim());
      this.loadingName = true;
      this.$http.post(this.$constant.baseURL + "/webInfo/updateRandomName", {
        id: this.webInfoId,
        randomName: JSON.stringify(this.localRandomName)
      }, true)
        .then((res) => {
          if (res.code === 200) {
            this.$emit('saved');
            this.$message({ message: "随机名称保存成功！", type: "success" });
          } else {
            this.$message({ message: "保存失败: " + res.message, type: "error" });
          }
        })
        .catch((error) => {
          console.error('保存随机名称失败:', error);
          this.$message({ message: "保存失败: " + (error.response?.data?.message || error.message), type: "error" });
        })
        .finally(() => { this.loadingName = false; });
    },
    // ---- 随机头像 ----
    handleInputRandomAvatarConfirm() {
      if (!Array.isArray(this.localRandomAvatar)) {
        this.localRandomAvatar = [];
      }
      if (this.inputRandomAvatarValue) {
        this.localRandomAvatar.push(this.inputRandomAvatarValue);
      }
      this.inputRandomAvatarVisible = false;
      this.inputRandomAvatarValue = '';
    },
    showRandomAvatarInput() {
      this.inputRandomAvatarVisible = true;
      this.$nextTick(() => {
        this.$refs.saveRandomAvatarInput.$refs.input.focus();
      });
    },
    addRandomAvatar(res) {
      this.localRandomAvatar.push(res);
    },
    saveRandomAvatar() {
      if (!this.webInfoId) {
        this.$message.error('网站信息ID不存在，请刷新页面重试');
        return;
      }
      this.loadingAvatar = true;
      this.$http.post(this.$constant.baseURL + "/webInfo/updateRandomAvatar", {
        id: this.webInfoId,
        randomAvatar: JSON.stringify(this.localRandomAvatar)
      }, true)
        .then((res) => {
          if (res.code === 200) {
            this.$emit('saved');
            this.$message({ message: "随机头像保存成功！", type: "success" });
          } else {
            this.$message({ message: "保存失败: " + res.message, type: "error" });
          }
        })
        .catch((error) => {
          console.error('保存随机头像失败:', error);
          this.$message({ message: "保存失败: " + (error.response?.data?.message || error.message), type: "error" });
        })
        .finally(() => { this.loadingAvatar = false; });
    },
    // ---- 随机封面 ----
    handleInputRandomCoverConfirm() {
      if (this.inputRandomCoverValue) {
        this.localRandomCover.push(this.inputRandomCoverValue);
      }
      this.inputRandomCoverVisible = false;
      this.inputRandomCoverValue = '';
    },
    showRandomCoverInput() {
      this.inputRandomCoverVisible = true;
      this.$nextTick(() => {
        this.$refs.saveRandomCoverInput.$refs.input.focus();
      });
    },
    addRandomCover(res) {
      this.localRandomCover.push(res);
    },
    saveRandomCover() {
      if (!this.webInfoId) {
        this.$message.error('网站信息ID不存在，请刷新页面重试');
        return;
      }
      this.loadingCover = true;
      this.$http.post(this.$constant.baseURL + "/webInfo/updateRandomCover", {
        id: this.webInfoId,
        randomCover: JSON.stringify(this.localRandomCover)
      }, true)
        .then((res) => {
          if (res.code === 200) {
            this.$emit('saved');
            this.$message({ message: "随机封面保存成功！", type: "success" });
          } else {
            this.$message({ message: "保存失败: " + res.message, type: "error" });
          }
        })
        .catch((error) => {
          console.error('保存随机封面失败:', error);
          this.$message({ message: "保存失败: " + (error.response?.data?.message || error.message), type: "error" });
        })
        .finally(() => { this.loadingCover = false; });
    }
  }
}
</script>

<style scoped>
.button-new-tag {
  margin: 10px;
  height: 32px;
  line-height: 32px;
  padding-top: 0;
  padding-bottom: 0;
}
.input-new-tag {
  width: 200px;
  margin: 10px;
}
.table-td-thumb {
  border-radius: 2px;
  width: 40px;
  height: 40px;
}
.random-settings-tabs {
  background-color: #fff;
}
::v-deep .el-tabs__content {
  padding: 15px;
  padding-top: 0px;
}
</style>
