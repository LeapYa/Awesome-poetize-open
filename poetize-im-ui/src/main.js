import {createApp} from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import {
  create,
  NAvatar,
  NInput,
  NIcon,
  NTag,
  NDivider,
  NButton,
  NDrawer,
  NCard,
  NTabs,
  NTabPane,
  NSwitch,
  NModal,
  NBadge,
  NPopover,
  NImage,
  NPopconfirm
} from 'naive-ui'

import {
  ElUpload,
  ElButton,
  ElRadioGroup,
  ElRadioButton
} from 'element-plus'
import 'element-plus/dist/index.css'

import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'

import 'vfonts/FiraCode.css'
import './assets/css/index.css'
import './assets/css/color.css'
import './assets/css/animation.css'

const naive = create({
  components: [NAvatar, NInput, NIcon, NTag, NDivider, NButton,
    NDrawer, NCard, NTabs, NTabPane, NSwitch, NModal, NBadge,
    NPopover, NImage, NPopconfirm]
})

const app = createApp(App)
app.use(router)
app.use(store)
app.use(naive)

app.component(ElUpload.name, ElUpload)
app.component(ElButton.name, ElButton)
app.component(ElRadioGroup.name, ElRadioGroup)
app.component(ElRadioButton.name, ElRadioButton)

app.config.globalProperties.$http = http
app.config.globalProperties.$common = common
app.config.globalProperties.$constant = constant

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    if (to.path === "/") {
      if (typeof to.query.defaultStoreType !== "undefined") {
        localStorage.setItem("defaultStoreType", to.query.defaultStoreType);
      }
      if (typeof to.query.userToken !== "undefined") {
        let userToken = to.query.userToken;
        const xhr = new XMLHttpRequest();
        xhr.open('post', "/user/token", false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.send("userToken=" + userToken);
        let result = JSON.parse(xhr.responseText);
        if (!common.isEmpty(result) && result.code === 200) {
          store.commit("loadCurrentUser", result.data);
          localStorage.setItem("userToken", result.data.accessToken);
          window.location.href = constant.imURL;
          next();
        } else {
          window.location.href = constant.webBaseURL;
        }
      } else if (Boolean(localStorage.getItem("userToken"))) {
        next();
      } else {
        window.location.href = constant.webBaseURL;
      }
    } else {
      if (Boolean(localStorage.getItem("userToken"))) {
        next();
      } else {
        window.location.href = constant.webBaseURL;
      }
    }
  } else {
    next();
  }
})

app.mount('#app')
