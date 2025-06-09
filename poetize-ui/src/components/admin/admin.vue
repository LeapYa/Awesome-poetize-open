<template>
  <div>
    <myHeader></myHeader>
    <sidebar></sidebar>
    <div class="content-box">
      <div class="content">
        <router-view></router-view>
      </div>
    </div>
  </div>
</template>

<script>
  import myHeader from "./common/myHeader.vue";
  import sidebar from "./common/sidebar.vue";

  export default {
    components: {
      myHeader,
      sidebar
    },

    data() {
      return {}
    },

    computed: {},

    watch: {
      '$route'(to, from) {
        console.log('路由变化:', from.path, '->', to.path);
      }
    },

    created() {
      console.log('Admin组件初始化，当前路径:', this.$route.path);
      let sysConfig = this.$store.state.sysConfig;
      if (!this.$common.isEmpty(sysConfig) && !this.$common.isEmpty(sysConfig['webStaticResourcePrefix'])) {
        let root = document.querySelector(":root");
        let webStaticResourcePrefix = sysConfig['webStaticResourcePrefix'];
        root.style.setProperty("--backgroundPicture", "url(" + webStaticResourcePrefix + "assets/backgroundPicture.jpg)");
        const font = new FontFace("poetize-font", "url(" + webStaticResourcePrefix + "assets/font.woff2)");
        font.load();
        document.fonts.add(font);
      }
    },

    mounted() {
      console.log('Admin组件挂载完成，router-view已渲染');
    },

    methods: {}
  }
</script>

<style scoped>

  .content-box {
    position: absolute;
    left: 130px;
    right: 0;
    top: 70px;
    bottom: 0;
    transition: left .3s ease-in-out;
  }

  .content {
    width: auto;
    height: 100%;
    padding: 30px;
    overflow-y: scroll;
  }

</style>
