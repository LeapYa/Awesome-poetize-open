import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '../store'
import translationModelManage from "../components/admin/translationModelManage";

const originalPush = VueRouter.prototype.push;
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => err);
}

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    component: () => import('../components/home'),
    children: [{
      path: "/",
      name: "index",
      component: () => import('../components/index')
    }, {
      path: "/sort",
      name: "sort",
      component: () => import('../components/sort')
    }, {
      path: "/article/:id",
      name: "article",
      component: () => import('../components/article')
    }, {
      path: "/weiYan",
      name: "weiYan",
      component: () => import('../components/weiYan')
    }, {
      path: "/love",
      name: "love",
      component: () => import('../components/love')
    }, {
      path: "/favorite",
      name: "favorite",
      component: () => import('../components/favorite')
    }, {
      path: "/travel",
      name: "travel",
      component: () => import('../components/travel')
    }, {
      path: "/message",
      name: "message",
      component: () => import('../components/message')
    }, {
      path: "/about",
      name: "about",
      component: () => import('../components/about')
    }, {
      path: "/user",
      name: "user",
      component: () => import('../components/user')
    }, {
      path: "/letter",
      name: "letter",
      component: () => import('../components/letter')
    }, {
      path: "/privacy",
      name: "privacy",
      component: () => import('../views/Privacy')
    }]
  },
  {
    path: '/admin',
    redirect: '/welcome',
    meta: {requiresAuth: true},
    component: () => import('../components/admin/admin'),
    children: [{
      path: '/welcome',
      name: 'welcome',
      component: () => import('../components/admin/welcome')
    }, {
      path: '/main',
      name: 'main',
      component: () => import('../components/admin/main')
    }, {
      path: '/webEdit',
      name: 'webEdit',
      component: () => import('../components/admin/webEdit')
    }, {
      path: '/userList',
      name: 'userList',
      component: () => import('../components/admin/userList')
    }, {
      path: '/postList',
      name: 'postList',
      component: () => import('../components/admin/postList')
    }, {
      path: '/postEdit',
      name: 'postEdit',
      component: () => import('../components/admin/postEdit')
    }, {
      path: '/sortList',
      name: 'sortList',
      component: () => import('../components/admin/sortList')
    }, {
      path: '/configList',
      name: 'configList',
      component: () => import('../components/admin/configList')
    }, {
      path: '/commentList',
      name: 'commentList',
      component: () => import('../components/admin/commentList')
    }, {
      path: '/treeHoleList',
      name: 'treeHoleList',
      component: () => import('../components/admin/treeHoleList')
    }, {
      path: '/resourceList',
      name: 'resourceList',
      component: () => import('../components/admin/resourceList')
    }, {
      path: '/loveList',
      name: 'loveList',
      component: () => import('../components/admin/loveList')
    }, {
      path: '/resourcePathList',
      name: 'resourcePathList',
      component: () => import('../components/admin/resourcePathList')
    }, {
      path: '/visitStats',
      name: 'visitStats',
      component: () => import('../components/admin/visitStats')
    }, {
      path: '/seoConfig',
      name: 'seoConfig',
      component: () => import('../components/admin/seoConfig')
    }, {
      path: '/aiChatConfig',
      name: 'aiChatConfig',
      component: () => import('../components/admin/aiChatConfig')
    }, 
    // 下面两个组件暂不需要，已注释
    // {
    //   path: '/admin/userManage',
    //   component: () => import('../components/admin/userManage.vue'),
    //   meta: {
    //     requireAuth: true,
    //     isAdmin: true
    //   }
    // }, 
    {
      path: '/admin/translationModel',
      component: () => import('../components/admin/translationModelManage.vue'),
      meta: {
        requireAuth: true,
        isAdmin: true
      }
    }
    // {
    //   path: '/admin/dataStatistics',
    //   component: () => import('../components/admin/dataStatistics.vue'),
    //   meta: {
    //     requireAuth: true,
    //     isAdmin: true
    //   }
    // }
    ]
  },
  {
    path: '/verify',
    name: 'verify',
    component: () => import('../components/admin/verify')
  }
]

const router = new VueRouter({
  mode: "history",
  routes: routes,
  scrollBehavior(to, from, savedPosition) {
    return {x: 0, y: 0}
  }
})

router.beforeEach((to, from, next) => {
  // 检查是否需要管理员权限
  if (to.matched.some(record => record.meta.requiresAdmin)) {
    // 检查是否有管理员token
    const adminToken = localStorage.getItem("adminToken");
    if (!adminToken) {
      // 如果没有管理员token，跳转到管理员登录页
      next({
        path: '/verify',
        query: { redirect: to.fullPath }
      });
    } else {
      // 如果有管理员token，检查用户类型
      const currentAdmin = store.state.currentAdmin;
      if (currentAdmin && (currentAdmin.userType === 0 || currentAdmin.userType === 1)) {
        next();
      } else {
        // 如果用户不是管理员，跳转到首页
        next('/');
      }
    }
  } else if (to.path === '/user') {
    // 用户路由特殊处理，允许未登录用户访问
    next();
  } else {
    next();
  }
})

export default router
