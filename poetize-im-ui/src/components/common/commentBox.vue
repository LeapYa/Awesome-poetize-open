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
        <div @click="showEmoji = !showEmoji">
          <span class="myEmoji">🌕</span>
        </div>
        <div @click="openPicture()">
          <span class="myPicture">🌫️</span>
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

    <n-modal v-model:show="showPicture">
      <div class="comment-picture">
        <uploadPicture :prefix="'commentPicture'" @addPicture="addPicture" :maxSize="2"
                       :maxNumber="1"></uploadPicture>
      </div>
    </n-modal>
  </div>
</template>

<script>
  import proButton from "./proButton";
  import emoji from "./emoji";
  import uploadPicture from "./uploadPicture";
  import {ElMessage} from "element-plus";

  export default {
    components: {
      emoji,
      proButton,
      uploadPicture
    },
    data() {
      return {
        commentContent: "",
        showEmoji: false,
        showPicture: false,
        picture: {
          name: this.$store.state.currentUser.username,
          url: ""
        }
      };
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

  .myEmoji {
    font-size: 19px;
    cursor: pointer;
    margin-right: 12px;
  }

  .myPicture {
    font-size: 18px;
    cursor: pointer;
  }

  .comment-picture {
    background-color: var(--background);
    border-radius: 10px;
    padding: 30px;
  }
</style>
