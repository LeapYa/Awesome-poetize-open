import {createRouter, createWebHistory} from 'vue-router'
import constant from "../utils/constant";

const routes = [
  {
    path: "/",
    meta: {requiresAuth: true},
    component: () => import('../components/index')
  }
]

const router = createRouter({
  history: createWebHistory(constant.webHistory),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return {left: 0, top: 0};
  }
})

export default router
