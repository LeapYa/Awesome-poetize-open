<template>
  <div class="sidebar" :class="{ 'sidebar-dark': isAdminDark, 'sidebar-busy': isBusy }">
    <div @click="collapse()" class="collapse-btn" :class="{ 'collapse-dark': isAdminDark }">
      <i class="el-icon-menu" style="margin: 14px;font-size: 17px"></i>
      <div style="font-size: 15px;margin-top: 13px">折叠</div>
    </div>
    <div v-if="!isAuthReady" class="sidebar-placeholder">
      <i class="el-icon-loading"></i>
      <span>正在验证登录状态...</span>
    </div>
    <el-menu v-else class="sidebar-el-menu"
             :background-color="isAdminDark ? '#2d2d2d' : '#ebf1f6'"
             :text-color="isAdminDark ? '#b0b0b0' : '#606266'"
             active-text-color="#20a0ff"
             unique-opened
             :default-active="$route.path"
             router>
      <template v-for="item in items">
        <template v-if="hasPermission(item)">
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
                <el-menu-item v-else :index="subItem.index" :key="'item-'+subItem.index" :id="'menu-' + subItem.index.replace('/', '')">
                  {{ subItem.title }}
                </el-menu-item>
              </template>
            </el-submenu>
          </template>
          <template v-else>
            <el-menu-item :index="item.index" :key="item.index" :id="'menu-' + item.index.replace('/', '')" @click="item.title === 'SEO优化' ? goToSeoConfig() : null">
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
    import { useMainStore } from '@/stores/main';

export default {
    props: {
      isAdminDark: {
        type: Boolean,
        default: false
      },
      isAuthReady: {
        type: Boolean,
        default: false
      },
      isBusy: {
        type: Boolean,
        default: false
      }
    },
    
    data() {
      return {
        isCollapse: true,
        items: [{
          icon: "el-icon-s-home",
          index: "/main",
          title: "系统首页",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-s-tools",
          index: "web-settings",
          title: "网站设置",
          requiredUserType: 0,  // 仅站长可访问
          subs: [{
            index: "/webEdit",
            title: "基础设置"
          }, {
            index: "/webAppearance",
            title: "外观个性化"
          }, {
            index: "/webNotice",
            title: "通知与邮件"
          }, {
            index: "/webSecurity",
            title: "安全与登录"
          }, {
            index: "/webNavApi",
            title: "导航与接口"
          }]
        }, {
          icon: "el-icon-user-solid",
          index: "/userList",
          title: "用户管理",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-postcard",
          index: "/postList",
          title: "文章管理",
          requiredUserType: 1  // 管理员及以上可访问
        }, {
          icon: "el-icon-document",
          index: "/draftList",
          title: "草稿箱",
          requiredUserType: 1
        }, {
          icon: "el-icon-s-unfold",
          index: "/translationModel",
          title: "文章AI助手",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-notebook-2",
          index: "/sortList",
          title: "分类管理",
          requiredUserType: 1  // 管理员及以上可访问
        }, {
          icon: "el-icon-notebook-1",
          index: "/configList",
          title: "配置管理",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-s-operation",
          index: "/pluginManager",
          title: "插件管理",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-edit-outline",
          index: "/commentList",
          title: "评论管理",
          requiredUserType: 1  // 管理员及以上可访问
        }, {
          icon: "el-icon-s-comment",
          index: "/treeHoleList",
          title: "留言管理",
          requiredUserType: 1  // 管理员及以上可访问
        }, {
          icon: "el-icon-paperclip",
          index: "/resourceList",
          title: "资源管理",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-bank-card",
          index: "/resourcePathList",
          title: "资源聚合",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-sugar",
          index: "/loveList",
          title: "表白墙",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-search",
          index: "/seoConfig",
          title: "SEO优化",
          requiredUserType: 0,  // 仅站长可访问
          click: function() {
            this.goToSeoConfig();
          }
        }]
      }
    },

    computed: {
      mainStore() {
        return useMainStore();
      },
      // 响应式获取当前管理员信息
      currentAdmin() {
        return this.mainStore.currentAdmin;
      },

      // 响应式获取isBoss状态
      isBoss() {
        return this.currentAdmin.isBoss;
      },

      // 响应式获取用户类型
      userType() {
        return this.currentAdmin.userType;
      }
    },

    watch: {
      // 监听管理员信息变化，确保权限实时更新
      currentAdmin: {
        handler(newAdmin) {
        },
        deep: true
      }
    },

    created() {

    },

    mounted() {

    },

    methods: {
      // 权限判断方法
      hasPermission(item) {
        // 如果没有设置权限要求，默认允许访问
        if (item.requiredUserType === undefined) {
          return true;
        }

        // 基于userType的权限验证
        // userType越小权限越高：0(站长) > 1(管理员) > 2(普通用户)
        const hasUserTypePermission = this.userType <= item.requiredUserType;


        return hasUserTypePermission;
      },

      collapse() {
        const sidebarElements = document.querySelectorAll('.sidebar');
        const contentBoxElements = document.querySelectorAll('.content-box');
        
        if (this.isCollapse) {
          sidebarElements.forEach(element => {
            element.style.width = '45px';
          });
          contentBoxElements.forEach(element => {
            element.style.left = '45px';
          });
        } else {
          sidebarElements.forEach(element => {
            element.style.width = '130px';
          });
          contentBoxElements.forEach(element => {
            element.style.left = '130px';
          });
        }
        this.isCollapse = !this.isCollapse;
      },
      
      goToSeoConfig() {
        this.$router.push('/seoConfig').catch(err => {
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
    overflow-x: hidden;
    width: 130px;
    user-select: none;
  }

  .sidebar::-webkit-scrollbar {
    width: 0;
  }

  .sidebar > ul {
    height: 100%;
  }

  .sidebar-placeholder {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 18px 14px;
    color: #64748b;
    font-size: 13px;
    line-height: 1.5;
  }

  .sidebar-placeholder i {
    color: #409EFF;
  }

  .sidebar-el-menu .el-menu-item {
    padding: 0 10px !important;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* 子菜单标题缩进 */
  .sidebar-el-menu .el-submenu >>> .el-submenu__title {
    padding: 0 10px !important;
  }

  /* 子菜单展开项减少左侧缩进，防止撑宽侧边栏 */
  .sidebar-el-menu .el-submenu .el-menu-item {
    padding-left: 30px !important;
    padding-right: 5px !important;
    min-width: 0 !important;
  }

  /* 子菜单内嵌列表不设置最小宽度 */
  .sidebar-el-menu .el-submenu >>> .el-menu--inline {
    min-width: 0 !important;
  }
  
  /* 折叠按钮样式 */
  .collapse-btn {
    color: rgb(96, 98, 102);
    cursor: pointer;
    background-color: #ebf1f6;
    display: flex;
    transition: all 0.3s ease;
  }
  
  /* ========== 深色模式下的sidebar样式 ========== */
  .sidebar-dark {
    background-color: #2d2d2d;
  }

  .sidebar-dark .sidebar-el-menu {
    border-right: none !important;
  }

  .sidebar-dark .sidebar-el-menu >>> .el-menu {
    border-right: none !important;
  }
  
  .collapse-dark {
    background-color: #2d2d2d !important;
    color: #b0b0b0 !important;
  }

  .sidebar-dark .sidebar-placeholder {
    color: #cbd5e1;
  }

  .sidebar-busy .sidebar-el-menu,
  .sidebar-busy .collapse-btn {
    pointer-events: none;
  }

  .sidebar-busy .sidebar-el-menu {
    opacity: 0.72;
  }
</style>
