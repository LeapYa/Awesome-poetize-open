<template>
  <div class="sidebar">
    <div @click="collapse()" style="color: rgb(96, 98, 102);cursor: pointer;background-color: #ebf1f6;display: flex">
      <i class="el-icon-menu" style="margin: 14px;font-size: 17px"></i>
      <div style="font-size: 15px;margin-top: 13px">折叠</div>
    </div>
    <el-menu class="sidebar-el-menu"
             background-color="#ebf1f6"
             text-color="#606266"
             active-text-color="#20a0ff"
             unique-opened
             :default-active="$router.currentRoute.path"
             router>
      <template v-for="item in items">
        <template v-if="isBoss || !item.isBoss">
          <template v-if="item.subs">
            <el-submenu :index="item.index" :key="item.index">
              <template slot="title">
                <i :class="item.icon"></i>
                <span>{{ item.title }}</span>
              </template>
              <template v-for="subItem in item.subs">
                <el-submenu v-if="subItem.subs" :index="subItem.index" :key="subItem.index">
                  <template slot="title">
                    {{ subItem.title }}
                  </template>
                  <el-menu-item v-for="threeItem in subItem.subs" :key="threeItem.index" :index="threeItem.index">
                    {{ threeItem.title }}
                  </el-menu-item>
                </el-submenu>
                <el-menu-item v-else :index="subItem.index" :key="'item-'+subItem.index">
                  {{ subItem.title }}
                </el-menu-item>
              </template>
            </el-submenu>
          </template>
          <template v-else>
            <el-menu-item :index="item.index" :key="item.index" @click="item.title === 'SEO优化' ? goToSeoConfig() : null">
              <i :class="item.icon"></i>
              {{ item.title }}
            </el-menu-item>
          </template>
        </template>
      </template>
    </el-menu>
  </div>
</template>

<script>
  export default {
    data() {
      return {
        isCollapse: true,
        isBoss: this.$store.state.currentAdmin.isBoss,
        items: [{
          icon: "el-icon-s-home",
          index: "/main",
          title: "系统首页",
          isBoss: true
        }, {
          icon: "el-icon-s-tools",
          index: "/webEdit",
          title: "网站设置",
          isBoss: true
        }, {
          icon: "el-icon-user-solid",
          index: "/userList",
          title: "用户管理",
          isBoss: true
        }, {
          icon: "el-icon-postcard",
          index: "/postList",
          title: "文章管理",
          isBoss: false
        }, {
          icon: "el-icon-notebook-2",
          index: "/sortList",
          title: "分类管理",
          isBoss: true
        }, {
          icon: "el-icon-notebook-1",
          index: "/configList",
          title: "配置管理",
          isBoss: true
        }, {
          icon: "el-icon-edit-outline",
          index: "/commentList",
          title: "评论管理",
          isBoss: false
        }, {
          icon: "el-icon-s-comment",
          index: "/treeHoleList",
          title: "留言管理",
          isBoss: true
        }, {
          icon: "el-icon-paperclip",
          index: "/resourceList",
          title: "资源管理",
          isBoss: true
        }, {
          icon: "el-icon-bank-card",
          index: "/resourcePathList",
          title: "资源聚合",
          isBoss: true
        }, {
          icon: "el-icon-sugar",
          index: "/loveList",
          title: "表白墙",
          isBoss: true
        }, {
          icon: "el-icon-s-data",
          index: "/visitStats",
          title: "访问统计",
          isBoss: true
        }, {
          icon: "el-icon-search",
          index: "/seoConfig",
          title: "SEO优化",
          isBoss: true,
          click: function() {
            console.log('SEO配置点击，通过click属性');
            this.goToSeoConfig();
          }
        }, {
          icon: "el-icon-chat-line-round",
          index: "/aiChatConfig",
          title: "AI聊天配置",
          isBoss: true
        }, {
          icon: "el-icon-s-unfold",
          index: "/admin/translationModel",
          title: "翻译管理",
          isBoss: true
        }]
      }
    },

    computed: {},

    watch: {},

    created() {

    },

    mounted() {

    },

    methods: {
      collapse() {
        if (this.isCollapse) {
          $(".sidebar").css("width", "45px");
          $(".content-box").css("left", "45px");
        } else {
          $(".sidebar").css("width", "130px");
          $(".content-box").css("left", "130px");
        }
        this.isCollapse = !this.isCollapse;
      },
      
      goToSeoConfig() {
        console.log('点击SEO配置菜单项');
        this.$router.push('/seoConfig').catch(err => {
          console.log('路由跳转错误:', err);
          this.$router.push({path: '/admin/seoConfig'}).catch(e => {
            console.error('嵌套路由也失败:', e);
          });
        });
      }
    }
  }
</script>

<style scoped>

  .sidebar {
    display: block;
    position: absolute;
    left: 0;
    top: 70px;
    bottom: 0;
    overflow-y: scroll;
    width: 130px;
    user-select: none;
  }

  .sidebar::-webkit-scrollbar {
    width: 0;
  }

  .sidebar > ul {
    height: 100%;
  }

  .sidebar-el-menu .el-menu-item {
    padding: 0 10px !important;
  }
</style>
