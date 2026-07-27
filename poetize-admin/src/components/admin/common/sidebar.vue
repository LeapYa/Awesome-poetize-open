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
             ref="elMenu"
             :key="collapsed ? 'menu-collapsed' : 'menu-expanded'"
             :background-color="isAdminDark ? '#2d2d2d' : '#ebf1f6'"
             :text-color="isAdminDark ? '#b0b0b0' : '#606266'"
             active-text-color="#20a0ff"
             unique-opened
             :collapse="collapsed"
             :collapse-transition="false"
             :default-active="$route.path"
             router
             @select="handleMenuSelect">
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
            <el-menu-item :index="item.index" :key="item.index" :id="'menu-' + item.index.replace('/', '')">
              <i :class="item.icon"></i>
              <!-- 折叠模式下 Element 仅隐藏 slot=title 内容，文字必须包进 span -->
              <span slot="title">{{ item.title }}</span>
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
        // 是否处于折叠态（驱动 el-menu 原生 collapse 模式）
        collapsed: false,
        items: [{
          icon: "el-icon-s-home",
          index: "/welcome",
          title: "系统首页"  // 所有后台用户均可访问（登录后默认落地页）
        }, {
          icon: "el-icon-s-data",
          index: "/main",
          title: "数据统计",
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
            index: "/webWaifu",
            title: "看板娘与AI"
          }, {
            index: "/webLoginStyle",
            title: "登录页样式"
          }, {
            index: "/webNotice",
            title: "通知与邮件"
          }, {
            index: "/webSecurity",
            title: "安全与登录"
          }, {
            index: "/webNavApi",
            title: "导航与侧边栏"
          }, {
            index: "/webFooter",
            title: "页脚设置"
          }, {
            index: "/webStorage",
            title: "存储与图床"
          }, {
            index: "/webApi",
            title: "API 接口"
          }, {
            index: "/seoConfig",
            title: "SEO优化"
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
          icon: "el-icon-document-checked",
          index: "/systemLog",
          title: "系统日志",
          requiredUserType: 0  // 仅站长可访问
        }, {
          icon: "el-icon-notebook-1",
          index: "/configList",
          title: "高级配置",
          requiredUserType: 0  // 仅站长可访问
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
      // 移动端默认折叠：130px 侧边栏在小屏上过宽，进入即收窄为图标栏
      if (window.innerWidth <= 768 && !this.collapsed) {
        this.collapse();
      }
    },

    beforeDestroy() {
      clearTimeout(this.popupCloseTimer);
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
        // 切换 el-menu 原生折叠模式：图标态窄栏，子菜单悬停弹出（弹层挂 body 不受容器裁剪）
        this.collapsed = !this.collapsed;
        const width = this.collapsed ? '45px' : '130px';
        document.querySelectorAll('.sidebar').forEach(element => {
          element.style.width = width;
        });
        document.querySelectorAll('.content-box').forEach(element => {
          element.style.left = width;
        });
      },

      handleMenuSelect() {
        // 折叠态弹层由 hover 驱动，触屏设备没有“移开”事件，点选后需手动收起；
        // 且触屏合成的 mouseenter 会在 300ms 后再次打开弹层（el-submenu 的 showTimeout），
        // 所以除立即收起外，延迟再补一次，掩掉迟到的重开定时器
        if (!this.collapsed) {
          return;
        }
        const closeAllPopups = () => {
          if (!this.$refs.elMenu) {
            return;
          }
          this.items.forEach(item => {
            if (item.subs) {
              this.$refs.elMenu.close(item.index);
            }
          });
        };
        closeAllPopups();
        clearTimeout(this.popupCloseTimer);
        this.popupCloseTimer = setTimeout(closeAllPopups, 400);
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

  /* 原生折叠态：窄栏宽度与侧边栏一致，箭头/文字由 Element 自动隐藏 */
  .sidebar-el-menu.el-menu--collapse {
    width: 45px;
  }

  /* 图标严格居中：清除项内边距与图标外边距，菜单项与子菜单标题对齐 */
  .sidebar-el-menu.el-menu--collapse >>> .el-menu-item,
  .sidebar-el-menu.el-menu--collapse >>> .el-submenu__title {
    padding: 0 !important;
    text-align: center;
  }

  /* 折叠后菜单项被 tooltip 包裹，内层 div 带内联 padding: 0 20px，必须覆盖掉否则图标偏右 */
  .sidebar-el-menu.el-menu--collapse >>> .el-menu-item > div {
    padding: 0 !important;
    text-align: center;
  }

  .sidebar-el-menu.el-menu--collapse >>> .el-menu-item i,
  .sidebar-el-menu.el-menu--collapse >>> .el-submenu__title i {
    margin: 0;
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

<style>
  /* 折叠态子菜单弹层挂在 body 下，scoped 样式无法触及；
     矮屏上子菜单项多时会超出视口导致无法点击，
     限高并允许弹层内部滚动 */
  .el-menu--vertical .el-menu--popup {
    max-height: calc(100vh - 90px);
    overflow-y: auto;
    overflow-x: hidden;
    -webkit-overflow-scrolling: touch;
  }

  .el-menu--vertical .el-menu--popup::-webkit-scrollbar {
    width: 4px;
  }

  .el-menu--vertical .el-menu--popup::-webkit-scrollbar-thumb {
    background-color: rgba(144, 147, 153, 0.4);
    border-radius: 2px;
  }
</style>
