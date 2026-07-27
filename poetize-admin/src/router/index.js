import Vue from 'vue'
import VueRouter from 'vue-router'
import { beginSessionValidation, isSessionFresh, setAdminNavigationPending } from '../utils/sessionValidation'

const originalPush = VueRouter.prototype.push;
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => err);
}

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    redirect: '/welcome',
    meta: { requiresAuth: true },
    component: () => import('../components/admin/admin'),
    children: [{
      path: 'welcome',
      name: 'welcome',
      component: () => import('../components/admin/welcome')
    }, {
      path: 'main',
      name: 'main',
      component: () => import('../components/admin/main')
    }, {
      path: 'webEdit',
      name: 'webEdit',
      component: () => import('../components/admin/webEdit')
    }, {
      path: 'webNotice',
      name: 'webNotice',
      component: () => import('../components/admin/webNotice')
    }, {
      path: 'webSecurity',
      name: 'webSecurity',
      component: () => import('../components/admin/webSecurity')
    }, {
      path: 'webAppearance',
      name: 'webAppearance',
      component: () => import('../components/admin/webAppearance')
    }, {
      path: 'webWaifu',
      name: 'webWaifu',
      component: () => import('../components/admin/webWaifu')
    }, {
      path: 'webLoginStyle',
      name: 'webLoginStyle',
      component: () => import('../components/admin/webLoginStyle')
    }, {
      path: 'webNavApi',
      name: 'webNavApi',
      component: () => import('../components/admin/webNavApi')
    }, {
      path: 'webFooter',
      name: 'webFooter',
      component: () => import('../components/admin/webFooter')
    }, {
      path: 'webStorage',
      name: 'webStorage',
      component: () => import('../components/admin/webStorage')
    }, {
      path: 'webApi',
      name: 'webApi',
      component: () => import('../components/admin/webApi')
    }, {
      path: 'userList',
      name: 'userList',
      component: () => import('../components/admin/userList')
    }, {
      path: 'postList',
      name: 'postList',
      component: () => import('../components/admin/postList')
    }, {
      path: 'postEdit',
      name: 'postEdit',
      component: () => import('../components/admin/postEdit')
    }, {
      path: 'draftList',
      name: 'draftList',
      component: () => import('../components/admin/draftList')
    }, {
      path: 'sortList',
      name: 'sortList',
      component: () => import('../components/admin/sortList')
    }, {
      path: 'configList',
      name: 'configList',
      component: () => import('../components/admin/configList')
    }, {
      path: 'commentList',
      name: 'commentList',
      component: () => import('../components/admin/commentList')
    }, {
      path: 'treeHoleList',
      name: 'treeHoleList',
      component: () => import('../components/admin/treeHoleList')
    }, {
      path: 'resourceList',
      name: 'resourceList',
      component: () => import('../components/admin/resourceList')
    }, {
      path: 'loveList',
      name: 'loveList',
      component: () => import('../components/admin/loveList')
    }, {
      path: 'resourcePathList',
      name: 'resourcePathList',
      component: () => import('../components/admin/resourcePathList')
    }, {
      path: 'visitStats',
      name: 'visitStats',
      component: () => import('../components/admin/visitStats')
    }, {
      path: 'seoConfig',
      name: 'seoConfig',
      component: () => import('../components/admin/seoConfig')
    }, {
      path: 'pluginManager',
      name: 'pluginManager',
      component: () => import('../components/admin/pluginManager')
    }, {
      path: 'systemLog',
      name: 'systemLog',
      component: () => import('../components/admin/systemLog')
    }, {
      path: 'translationModel',
      name: 'translationModel',
      component: () => import('../components/admin/translationModelManage.vue'),
      meta: {
        requireAuth: true,
        isAdmin: true
      }
    }]
  },
  // 仅在开发环境开启 /user 路由（生产环境请访问主站）
  ...(import.meta.env.DEV ? [{
    path: '/user',
    name: 'user',
    component: () => import('../components/user')
  }] : []),
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('../components/Forbidden')
  },
  {
    path: '/404',
    name: 'notFound',
    component: () => import('../components/NotFound')
  },
  {
    path: '*',
    name: 'catchAll',
    component: () => import('../components/NotFound')
  }
]

const router = new VueRouter({
  mode: "history",
  base: '/admin/',
  routes: routes,
  scrollBehavior(to, from, savedPosition) {
    return { x: 0, y: 0 }
  }
})

router.beforeEach((to, from, next) => {
  // 检查是否需要重定向到403页面
  if (to.query.redirect === '403') {
    setAdminNavigationPending(false)
    next('/403');
    return;
  }

  const requiresAuth = to.matched.some(record => record.meta && record.meta.requiresAuth)
  setAdminNavigationPending(requiresAuth)

  if (requiresAuth && !isSessionFresh()) {
    beginSessionValidation()
  }

  next();
})

router.afterEach(() => {
  setAdminNavigationPending(false)
})

router.onError(() => {
  setAdminNavigationPending(false)
})

export default router
