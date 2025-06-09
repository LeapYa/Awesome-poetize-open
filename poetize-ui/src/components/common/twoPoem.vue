<template>
  <div class="poem-container myCenter my-animation-hideToShow"
       v-if="!$common.isEmpty(guShi.origin) || !$common.isEmpty(hitokoto.hitokoto)">
    <!-- 背景图片 -->
    <el-image class="my-el-image poem-image"
              style="position: absolute;margin-top: -50px"
              v-once
              lazy
              :src="$store.state.webInfo.randomCover[Math.floor(Math.random() * $store.state.webInfo.randomCover.length)]"
              fit="cover">
      <div slot="error" class="image-slot"></div>
    </el-image>
    <div class="poem-wrap">
      <div v-if="isShehui"><span>鬼畜全明星</span></div>
      <div v-else><span>{{isHitokoto?hitokoto.from:guShi.origin}}</span></div>
      <p class="poem">{{isHitokoto?hitokoto.hitokoto:guShi.content}}</p>
      <p class="info" v-if="!isShehui && (!isHitokoto || (isHitokoto && !$common.isEmpty(hitokoto.from_who)))">
        {{isHitokoto?hitokoto.from_who:guShi.author}}
      </p>
    </div>
  </div>
</template>
<script>
  import apiFallback from '@/utils/api-fallback';
  
  export default {
    props: {
      isHitokoto: {
        type: Boolean,
        default: true
      },
      isShehui: {
        type: Boolean,
        default: false
      }
    },
    data() {
      return {
        guShi: {
          "content": "...",
          "origin": "...",
          "author": "...",
          "category": "..."
        },
        hitokoto: {
          "hitokoto": "...",
          "from": "...",
          "from_who": "..."
        }
      };
    },
    created() {
      if (!this.isShehui) {
        if (this.isHitokoto) {
          this.getHitokoto();
        } else {
          this.getGuShi();
        }
      } else {
        this.hitokoto.from = "";
        this.hitokoto.from_who = "";
        this.sendShehui();
      }
    },

    methods: {
      sendShehui() {
        let that = this;
        let xhr = new XMLHttpRequest();
        xhr.open('get', this.$constant.shehui);
        xhr.timeout = 5000; // 设置5秒超时
        xhr.onreadystatechange = function () {
          if (xhr.readyState === 4) {
            if (xhr.status === 200) {
              let shehui = xhr.responseText;
              that.hitokoto.hitokoto = shehui.substring(1, shehui.length - 1);
            } else {
              // 请求失败，使用本地备用数据
              const fallbackData = apiFallback.shehui[Math.floor(Math.random() * apiFallback.shehui.length)];
              that.hitokoto.hitokoto = fallbackData.content;
            }
          }
        };
        xhr.ontimeout = function() {
          // 请求超时，使用本地备用数据
          const fallbackData = apiFallback.shehui[Math.floor(Math.random() * apiFallback.shehui.length)];
          that.hitokoto.hitokoto = fallbackData.content;
        };
        xhr.onerror = function() {
          // 请求错误，使用本地备用数据
          const fallbackData = apiFallback.shehui[Math.floor(Math.random() * apiFallback.shehui.length)];
          that.hitokoto.hitokoto = fallbackData.content;
        };
        xhr.send();
      },
      getGuShi() {
        let that = this;
        let xhr = new XMLHttpRequest();
        xhr.open('get', this.$constant.jinrishici);
        xhr.timeout = 5000; // 设置5秒超时
        xhr.onreadystatechange = function () {
          if (xhr.readyState === 4) {
            if (xhr.status === 200) {
              that.guShi = JSON.parse(xhr.responseText);
            } else {
              // 请求失败，使用本地备用数据
              that.guShi = apiFallback.jinrishici[Math.floor(Math.random() * apiFallback.jinrishici.length)];
            }
          }
        };
        xhr.ontimeout = function() {
          // 请求超时，使用本地备用数据
          that.guShi = apiFallback.jinrishici[Math.floor(Math.random() * apiFallback.jinrishici.length)];
        };
        xhr.onerror = function() {
          // 请求错误，使用本地备用数据
          that.guShi = apiFallback.jinrishici[Math.floor(Math.random() * apiFallback.jinrishici.length)];
        };
        xhr.send();
      },
      getHitokoto() {
        let that = this;
        let xhr = new XMLHttpRequest();
        xhr.open('get', this.$constant.hitokoto);
        xhr.timeout = 5000; // 设置5秒超时
        xhr.onreadystatechange = function () {
          if (xhr.readyState === 4) {
            if (xhr.status === 200) {
              that.hitokoto = JSON.parse(xhr.responseText);
            } else {
              // 请求失败，使用本地备用数据
              that.hitokoto = apiFallback.hitokoto[Math.floor(Math.random() * apiFallback.hitokoto.length)];
            }
          }
        };
        xhr.ontimeout = function() {
          // 请求超时，使用本地备用数据
          that.hitokoto = apiFallback.hitokoto[Math.floor(Math.random() * apiFallback.hitokoto.length)];
        };
        xhr.onerror = function() {
          // 请求错误，使用本地备用数据
          that.hitokoto = apiFallback.hitokoto[Math.floor(Math.random() * apiFallback.hitokoto.length)];
        };
        xhr.send();
      }
    }
  };
</script>
<style scoped>

  .poem-container {
    padding: 90px 0 40px;
    position: relative;
  }

  .poem-wrap {
    border-radius: 10px;
    z-index: 10;
    text-align: center;
    letter-spacing: 4px;
    font-weight: 300;
    width: 100%;
    max-width: 800px;
  }

  .poem-wrap div span {
    padding: 5px 10px;
    color: var(--white);
    font-size: 2em;
    border-radius: 5px;
  }

  .poem-wrap p {
    width: 100%;
    max-width: 800px;
    color: var(--white);
  }

  .poem-wrap p.poem {
    margin: 40px auto;
    font-size: 1.5em;
  }

  .poem-wrap p.info {
    margin: 20px auto 40px;
    font-size: 1.1em;
  }
</style>
