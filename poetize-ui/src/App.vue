<template>
  <div id="app">
    <router-view/>
    <!-- 全局验证码容器 -->
    <captcha-container />
  </div>
</template>

<script>
import CaptchaContainer from '@/components/common/CaptchaContainer.vue';

export default {
  name: "App",
  components: {
    CaptchaContainer
  },
  data() {
    return {
      currentLang: 'zh' // 默认中文
    };
  },

  computed: {},

  watch: {
    '$route.path': function(newPath) {
      // 所有页面都使用网站标题
      if (this.$store.state.webInfo && this.$store.state.webInfo.webTitle) {
        // 直接使用webTitle字符串，不再需要从localStorage获取
        const webTitle = this.$store.state.webInfo.webTitle;
        document.title = webTitle;
        window.OriginTitile = webTitle;
      }
    }
  },

  created() {
    // 获取用户语言偏好
    const savedLang = localStorage.getItem('preferredLanguage');
    if (savedLang === 'en' || savedLang === 'zh') {
      this.currentLang = savedLang;
    } else {
      // 检测浏览器语言
      const browserLang = navigator.language || navigator.userLanguage;
      if (browserLang.toLowerCase().startsWith('en')) {
        this.currentLang = 'en';
      }
    }
    
    // 初始化时设置网站标题
    if (this.$store.state.webInfo && this.$store.state.webInfo.webTitle) {
      // 直接使用webTitle字符串
      document.title = this.$store.state.webInfo.webTitle;
      window.OriginTitile = this.$store.state.webInfo.webTitle;
    }
  },

  mounted() {
    // 确保字体加载
    document.body.style.fontFamily = "var(--globalFont), serif";
  },

  methods: {
    handleLanguageChange(lang) {
      if (this.currentLang === lang) return;
      
      this.currentLang = lang;
      localStorage.setItem('preferredLanguage', lang);
      
      // 更新URL参数并刷新页面以应用语言更改
      const url = new URL(window.location);
      url.searchParams.set('lang', lang);
      window.location.href = url.toString();
    }
  }
}
</script>

<style>
/* 全局样式 */
* {
  font-family: 'poetize-font', serif;
}

/* 提高消息通知的层级，使其显示在对话框之上 */
.el-message {
  z-index: 3000 !important;
}

.global-language-switch {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1000;
  background-color: rgba(255, 255, 255, 0.7);
  padding: 5px 10px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
.el-dropdown-link {
  cursor: pointer;
  color: #409EFF;
}
</style>
