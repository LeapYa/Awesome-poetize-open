<template>
  <div>
    <!-- 框 -->
    <textarea
      class="comment-textarea"
      v-model="commentContent"
      placeholder="写下点什么..."
      maxlength="1000"/>
    <!-- 按钮 -->
    <div class="myBetween" style="margin-bottom: 10px">
      <div style="display: flex">
        <div @click="showEmoji = !showEmoji" class="icon-btn">
          <svg viewBox="0 0 1024 1024" width="22" height="22">
            <path
              d="M523.9 511.98m-419.5 0a419.5 419.5 0 1 0 839 0 419.5 419.5 0 1 0-839 0Z"
              fill="#FFD629"></path>
            <path
              d="M885.2 298.58c-1.6-0.6 37.1 68.4 49.5 128.9 46.4 227-99.9 448.6-326.9 495.1-37.9 7.8-75.7 10.1-112.5 7.7-63.2-4.2-123.6-22.8-177.3-53 0 0 348.9-269.7 567.2-578.7z"
              fill="#FF9A2C" opacity=".1"></path>
            <path
              d="M922.4 383.38c73.9 216.8-52.8 456-264 525.8-36.7 12.1-108.5 28.3-184.9 19.4 0-0.5 166.62-15.7 313.54-190.74C942.88 552.16 920.2 376.98 922.4 383.38z"
              fill="#FF9A2C" opacity=".2"></path>
            <path
              d="M616.9 326.38m-52.7 0a52.7 52.7 0 1 0 105.4 0 52.7 52.7 0 1 0-105.4 0Z"
              fill="#211715"></path>
            <path
              d="M381.7 438.38l-77.6 125.3-15.5-8c-16-8.2-22.3-27.9-14.1-43.9l26.2-47.3-45.6-19.6c-15.7-8.1-21.9-27.4-13.8-43.1l8.2-16 132.2 52.6z"
              fill="#211715"></path>
            <path
              d="M603 479.68c27.4 56.5 14.1 119.5-29.7 140.8s-101.6-7.2-129-63.7l30.6-8c37.4-9.7 72.1-26.6 102.9-50l25.2-19.1z"
              fill="#F94616"></path>
          </svg>
        </div>
        <div @click="openPicture()" class="icon-btn">
          <svg viewBox="0 0 1024 1024" width="22" height="22">
            <path
              d="M512.649 512.632m-447.382 0a447.382 447.382 0 1 0 894.764 0 447.382 447.382 0 1 0-894.764 0Z"
              fill="#E0995E"></path>
            <path d="M736.34 428.748V316.902H288.958v447.382H736.34z"
                  fill="#231F20"></path>
            <path
              d="M736.34 260.979H288.958c-30.886 0-55.923 25.037-55.923 55.923V764.284c0 30.885 25.037 55.923 55.923 55.923H736.34c30.886 0 55.923-25.038 55.923-55.923V316.902c0-30.886-25.037-55.923-55.923-55.923z"
              fill="#231F20"></path>
            <path
              d="M288.958 764.284c-15.421 0-27.961-12.547-27.961-27.961V288.941c0-15.414 12.54-27.961 27.961-27.961H736.34c15.421 0 27.961 12.547 27.961 27.961v447.382c0 15.414-12.54 27.961-27.961 27.961H288.958z"
              fill="#77B3D4"></path>
            <path
              d="M736.34 288.941v447.383H288.958V288.941H736.34m0-55.923H288.958c-30.886 0-55.923 25.037-55.923 55.923v447.383c0 30.885 25.037 55.923 55.923 55.923H736.34c30.886 0 55.923-25.038 55.923-55.923V288.941c0-30.886-25.037-55.923-55.923-55.923z"
              fill="#FFFFFF"></path>
            <path d="M288.958 680.4H736.34v55.923H288.958z" fill="#FFFFFF"></path>
            <path
              d="M260.851 680.4h487.868v-40.837s-23.845-41.624-45.947-67.706c-16.44-19.4-40.989-24.891-62.407-1.059l-38.481 36.957-83.869-106.652c-31.234-39.718-82.344-39.718-113.578 0L260.851 680.4z"
              fill="#4F5D73"></path>
            <path
              d="M624.495 400.786m-41.942 0a41.942 41.942 0 1 0 83.884 0 41.942 41.942 0 1 0-83.884 0Z"
              fill="#F5CF87"></path>
          </svg>
        </div>
      </div>
      <div style="display: flex">
        <proButton :info="'提交'"
                   @click="submitComment()"
                   :before="$constant.before_color_2"
                   :after="$constant.after_color_2">
        </proButton>
      </div>
    </div>
    <!-- 表情 -->
    <emoji @addEmoji="addEmoji" :showEmoji="showEmoji"></emoji>
    <teleport to="body">
      <el-dialog class="comment-picture-dialog" v-model="showPicture" width="30%" :show-close="true" :append-to-body="true" :close-on-click-modal="true" align-center>
        <div class="comment-picture">
          <div style="margin: 0 0 25px;text-align: center;font-size: 18px;font-weight: bold;color: var(--fontColor)">上传图片</div>
          <uploadPicture :prefix="'commentPicture'" @addPicture="addPicture" :maxSize="5"
                         :maxNumber="1"></uploadPicture>
        </div>
      </el-dialog>
    </teleport>
  </div>
</template>
<script>
  import proButton from "./proButton";
  import emoji from "./emoji";
  import uploadPicture from "./uploadPicture";
  import { message as ElMessage } from '@/utils/element-command-services';
  import { useMainStore } from '../../../stores/main';
  
  export default {
    components: {
      emoji,
      proButton,
      uploadPicture
    },
    emits: ['submitComment'],
    setup() {
      const mainStore = useMainStore();
      return { mainStore };
    },
    data() {
      return {
        commentContent: "",
        showEmoji: false,
        showPicture: false,
        picture: {
          name: "",
          url: ""
        }
      };
    },
    created() {
      // 在组件创建时设置 picture.name，此时 mainStore 已可用
      if (this.mainStore.currentUser) {
        this.picture.name = this.mainStore.currentUser.username || "";
      }
    },
    methods: {
      openPicture() {
        this.showPicture = true
      },
      addPicture(res) {
        this.picture.url = res;
        this.savePicture();
      },
      savePicture() {
        let img = "[" + this.picture.name + "," + this.picture.url + "]";
        this.commentContent += img;
        this.picture.url = "";
        this.showPicture = false;
      },
      addEmoji(key) {
        this.commentContent += key;
      },
      submitComment() {
        if (this.commentContent.trim() === "") {
          ElMessage({
            message: "你还没写呢~",
            type: 'warning'
          });
          return;
        }
        this.$emit("submitComment", this.commentContent.trim());
        this.commentContent = "";
      }
    }
  }
</script>
<style scoped>
  .comment-textarea {
    border: 1px solid var(--lightGray);
    width: calc(100% - 40px);
    font-size: 14px;
    padding: 15px;
    min-height: 180px;
    /* 不改变大小 */
    resize: none;
    /* 不改变边框 */
    outline: none;
    border-radius: 4px;
    background-image: var(--commentURL);
    background-size: contain;
    background-repeat: no-repeat;
    background-position: 100%;
    margin-bottom: 10px;
  }
  .comment-textarea:focus {
    border-color: var(--themeBackground);
  }
  .icon-btn {
    cursor: pointer;
    margin-right: 12px;
    transition: all 0.3s;
    display: flex;
    align-items: center;
  }
  .icon-btn:hover {
    transform: scale(1.15);
  }
  .comment-picture {
    background-color: var(--background);
    border-radius: 10px;
    padding: 30px;
  }
</style>
