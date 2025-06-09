import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'
import mavonEditor from 'mavon-editor'
//引入js
// 使用try-catch包裹live2d加载，防止加载失败
try {
  // 确保CSS文件加载
  const loadCss = (url) => {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = url;
    document.head.appendChild(link);
  };
  
  // 确保基本的看板娘样式已加载
  loadCss(constant.live2d_path + 'waifu.css');
  
  // 延迟加载live2d，确保核心内容优先加载
  window.addEventListener('load', () => {
    setTimeout(() => {
      // 检查看板娘是否启用
      const checkWaifuEnabled = () => {
        try {
          // 从本地存储获取配置
          const webInfoStr = localStorage.getItem('webInfo');
          if (webInfoStr) {
            const webInfoData = JSON.parse(webInfoStr);
            // 处理两种可能的数据格式
            if (webInfoData.data && webInfoData.data.enableWaifu !== undefined) {
              return webInfoData.data.enableWaifu === true;
            } else if (webInfoData.enableWaifu !== undefined) {
              return webInfoData.enableWaifu === true;
            }
          }
          return store.state.webInfo.enableWaifu === true;
        } catch (e) {
          console.error('检查看板娘状态出错:', e);
          return false;
        }
      };
      
      // 只在启用状态下加载看板娘
      if (checkWaifuEnabled()) {
        import('./utils/live2d').then(() => {
          console.log('看板娘加载成功');
          
          // 注册刷新页面时的事件处理
          window.addEventListener('pageshow', (event) => {
            // 如果是从缓存加载的页面，检查看板娘状态
            if (event.persisted) {
              console.log('页面从缓存恢复，检查看板娘状态');
              // 触发看板娘检查事件
              document.dispatchEvent(new Event('checkWaifu'));
            }
          });
          
          // 监听路由变化，确保在切换页面时看板娘正常显示
          const checkWaifuOnRouteChange = () => {
            setTimeout(() => {
              document.dispatchEvent(new Event('checkWaifu'));
            }, 1000);
          };
          
          // 监听路由变化和历史记录变化
          window.addEventListener('popstate', checkWaifuOnRouteChange);
          
          // 监听vue-router的路由变化
          if (router.afterEach) {
            router.afterEach((to, from) => {
              if (to.path !== from.path) {
                checkWaifuOnRouteChange();
              }
            });
          }
        }).catch(error => {
          console.warn('看板娘加载失败，但不影响主要功能:', error);
        });
      } else {
        console.log('看板娘功能已禁用，跳过加载');
      }
    }, 2000); // 延迟2秒加载
  });
} catch (err) {
  console.warn('看板娘初始化失败，但不影响主要功能:', err);
}
import './utils/title'
//引入css
import './assets/css/animation.css'
import './assets/css/index.css'
import './assets/css/tocbot.css'
import './assets/css/color.css'
import './assets/css/markdown-highlight.css'
import './assets/css/font-awesome.min.css'
import 'mavon-editor/dist/css/index.css'

import {vueBaberrage} from 'vue-baberrage'

Vue.use(ElementUI)
Vue.use(vueBaberrage)
Vue.use(mavonEditor)

Vue.prototype.$http = http
Vue.prototype.$common = common
Vue.prototype.$constant = constant

// 创建事件总线
Vue.prototype.$bus = new Vue();

// 添加全局错误处理
Vue.config.errorHandler = (err, vm, info) => {
  console.error('Vue错误:', err);
  // 对特定错误进行处理
  if (err.message && err.message.includes('live2d')) {
    console.warn('看板娘相关错误已捕获，不影响系统使用');
  }
};

// 捕获未处理的Promise错误
window.addEventListener('unhandledrejection', event => {
  console.warn('未处理的Promise错误:', event.reason);
  // 忽略特定错误
  if (event.reason && event.reason.toString().includes('live2d')) {
    console.warn('看板娘相关Promise错误已捕获，不影响系统使用');
    event.preventDefault();
  }
});

Vue.config.productionTip = false

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')
