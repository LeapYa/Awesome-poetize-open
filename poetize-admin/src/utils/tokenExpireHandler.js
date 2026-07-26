/**
 * Token过期处理工具
 * 统一处理token过期的逻辑，包括清除状态、跳转登录页等
 */

import { useMainStore } from '@/stores/main'
import router from '@/router'
import constant from '@/utils/constant'
import { resetSessionValidation } from '@/utils/sessionValidation'

/**
 * 清除所有认证相关的状态
 */
export function clearAuthState() {
  resetSessionValidation({ status: 'invalid' })

  // 清除localStorage中的用户信息（token由后端通过cookie管理）
  localStorage.removeItem("currentUser");
  localStorage.removeItem("currentAdmin");

  // 清除Pinia store中的状态
  const mainStore = useMainStore();
  mainStore.loadCurrentUser({});
  mainStore.loadCurrentAdmin({});

}

/**
 * 获取前台登录页面的URL
 * @param {string} redirectPath - 登录后要跳转的路径
 * @param {boolean} expired - 是否因token过期而跳转
 * @returns {string} - 登录页面URL
 */
function getLoginUrl(redirectPath, expired = false) {
  const expiredQuery = expired ? '&expired=true' : '';
  // 开发环境使用 adminLoginURL 跳转后台的模拟登录页，生产环境统一跳转前台的真实登录页
  const baseUrl = import.meta.env.DEV ? constant.adminLoginURL : constant.frontendURL;
  return `${baseUrl}/user?redirect=${encodeURIComponent(redirectPath)}${expiredQuery}`;
}

/**
 * 处理token过期的统一逻辑
 * @param {boolean} isAdmin - 是否为管理员token过期
 * @param {string} currentPath - 当前页面路径，用于登录后重定向
 * @param {Object} options - 额外选项
 */
export function handleTokenExpire(isAdmin = false, currentPath = null, options = {}) {

  // 清除认证状态
  clearAuthState();

  // 确定当前路径（完整路径，包含 /admin 前缀）
  // router 配置了 base: '/admin/'，currentRoute.fullPath 返回相对路径（如 /welcome），
  // 调用方传入的 currentPath 同样是相对路径，必须补上 /admin 前缀，
  // 否则生产环境登录后跳转到前台主站的 /welcome 会 404（主站无此路由）。
  let redirectPath = currentPath;
  if (!redirectPath || !redirectPath.startsWith('/admin')) {
    redirectPath = '/admin' + router.currentRoute.fullPath;
  }

  // 跳转到前台登录页
  const loginUrl = getLoginUrl(redirectPath, options.showMessage !== false);
  window.location.href = loginUrl;
}

/**
 * 检查token是否有效
 * @param {string} token - 要检查的token
 * @returns {boolean} - token是否有效
 */
export function isTokenValid(token) {
  if (!token || token === 'null' || token === 'undefined') {
    return false;
  }

  // 基本格式检查
  if (token.length < 10) {
    return false;
  }

  // 可以添加更多的token格式验证逻辑
  return true;
}

/**
 * 获取有效的token
 * @param {boolean} isAdmin - 是否获取管理员token
 * @returns {string|null} - 有效的token或null
 */
export function getValidToken(isAdmin = false) {
  const mainStore = useMainStore();
  const user = isAdmin ? mainStore.currentAdmin : mainStore.currentUser;
  return user && Object.keys(user).length > 0 ? '__cookie_session__' : null;
}

/**
 * 检查当前用户是否已登录
 * @param {boolean} isAdmin - 是否检查管理员登录状态
 * @returns {boolean} - 是否已登录
 */
export function isLoggedIn(isAdmin = false) {
  const token = getValidToken(isAdmin);
  if (!token) {
    return false;
  }

  // 检查store中的用户信息
  const mainStore = useMainStore();
  const user = isAdmin ? mainStore.currentAdmin : mainStore.currentUser;

  return user && Object.keys(user).length > 0;
}

/**
 * 统一的登录跳转处理函数
 * 用于所有需要登录的场景，确保正确保存当前页面URL并在登录后返回
 * @param {Object} router - Vue Router实例（保留参数以保持API兼容）
 * @param {Object} options - 配置选项
 * @param {string} options.currentPath - 当前页面路径，如果不提供则自动获取
 * @param {string} options.message - 提示消息
 */
export function redirectToLogin(router, options = {}) {
  const {
    currentPath = null,
    message = '请先登录！'
  } = options;

  // 获取当前页面路径（完整路径）
  let redirectPath = currentPath;
  if (!redirectPath) {
    redirectPath = window.location.pathname + window.location.search;
  }

  // 显示提示消息
  if (message && window.Vue && window.Vue.prototype && window.Vue.prototype.$message) {
    window.Vue.prototype.$message({
      message: message,
      type: 'info',
      duration: 2000
    });
  }

  // 跳转到前台登录页面（使用环境感知的URL）
  const loginUrl = getLoginUrl(redirectPath, false);
  window.location.href = loginUrl;
}

/**
 * 归一化后台应用内的重定向路径
 * redirect 参数携带 /admin 前缀（供生产环境主站登录页做跨应用跳转使用），
 * 但后台 router 配置了 base: '/admin/'，应用内 router 跳转必须剥掉该前缀，
 * 否则 vue-router 会再拼一次 base，产生 /admin/admin/xxx 的错误路径。
 * @param {string} path - 原始重定向路径
 * @returns {string} - 相对后台 base 的路径
 */
export function normalizeAdminRedirect(path) {
  if (!path) {
    return path;
  }
  if (path === '/admin' || path === '/admin/') {
    return '/';
  }
  if (path.startsWith('/admin/')) {
    return path.slice('/admin'.length);
  }
  return path;
}

/**
 * 处理登录成功后的重定向
 * @param {Object} route - 当前路由对象
 * @param {Object} router - 路由器对象
 * @param {Object} options - 额外选项
 */
export function handleLoginRedirect(route, router, options = {}) {
  const redirect = normalizeAdminRedirect(route.query.redirect);
  const hasComment = route.query.hasComment;
  const hasReplyAction = route.query.hasReplyAction;

  if (redirect && redirect !== '/user' && redirect !== '/verify') {
    // 保留特殊参数以触发相应的状态恢复
    const query = {};
    if (hasComment === 'true') query.hasComment = 'true';
    if (hasReplyAction === 'true') query.hasReplyAction = 'true';


    // 使用replace: true来避免在浏览器历史中留下登录页面
    router.replace({ path: redirect, query: query });
  } else {
    // 没有重定向参数，跳转到默认页面
    const defaultPath = options.defaultPath || '/';
    router.replace({ path: defaultPath });
  }
}

export default {
  clearAuthState,
  handleTokenExpire,
  isTokenValid,
  getValidToken,
  isLoggedIn,
  normalizeAdminRedirect,
  handleLoginRedirect,
  redirectToLogin
}
