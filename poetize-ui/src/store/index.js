import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

// 缓存版本控制机制
const CACHE_VERSION = '1.0.0';
const storedVersion = localStorage.getItem('cacheVersion');

// 当缓存版本不匹配时，清除相关缓存
if (storedVersion !== CACHE_VERSION) {
  console.log('缓存版本已更新，清除旧缓存');
  localStorage.removeItem('sortInfo');
  localStorage.removeItem('webInfo');
  localStorage.removeItem('articleList');
  localStorage.setItem('cacheVersion', CACHE_VERSION);
}

// 从localStorage获取数据，支持新的带时间戳格式和旧格式兼容
const getFromLocalStorage = (key, defaultValue, maxAge = 86400000) => { // 默认1天过期
  const stored = localStorage.getItem(key);
  if (!stored) return defaultValue;
  
  try {
    const parsed = JSON.parse(stored);
    
    // 检查是否是新格式(带时间戳)
    if (parsed && parsed.timestamp && parsed.data) {
      // 检查是否过期
      if (Date.now() - parsed.timestamp > maxAge) {
        console.log(`${key}缓存已过期，将获取新数据`);
        localStorage.removeItem(key);
        return defaultValue;
      }
      return parsed.data;
    }
    
    // 旧格式直接返回
    return parsed;
  } catch (e) {
    console.error(`解析${key}缓存出错:`, e);
    localStorage.removeItem(key);
    return defaultValue;
  }
};

export default new Vuex.Store({
  state: {
    toolbar: getFromLocalStorage("toolbar", {"visible": false, "enter": true}),
    sortInfo: getFromLocalStorage("sortInfo", []),
    currentUser: getFromLocalStorage("currentUser", {}),
    currentAdmin: getFromLocalStorage("currentAdmin", {}),
    sysConfig: getFromLocalStorage("sysConfig", {}),
    webInfo: getFromLocalStorage("webInfo", {
      "webName": "", 
      "webTitle": "", 
      "notices": [], 
      "randomCover": [], 
      "footer": "", 
      "backgroundImage": "", 
      "avatar": "",
      "minimalFooter": false,
      "navConfig": "[]"  // 初始为空数组字符串
    }),
    visitCounts: {},
    // 验证码相关状态
    captcha: {
      show: false,        // 是否显示验证码
      action: 'comment',  // 验证码操作类型
      params: null,       // 验证成功后的回调参数
      onSuccess: null     // 验证成功后的回调函数
    }
  },
  getters: {
    articleTotal: state => {
      if (state.sortInfo !== null && state.sortInfo.length !== 0) {
        if (state.sortInfo.length === 1) {
          return state.sortInfo[0].countOfSort;
        } else {
          return state.sortInfo.reduce((prev, curr) => {
            if (typeof prev === "number") {
              return prev + curr.countOfSort;
            } else {
              return prev.countOfSort + curr.countOfSort;
            }
          });
        }
      } else {
        return 0;
      }
    },
    navigationBar: state => {
      if (state.sortInfo !== null && state.sortInfo.length !== 0) {
        return state.sortInfo.filter(f => f.sortType === 0);
      } else {
        return [];
      }
    }
  },
  mutations: {
    changeToolbarStatus(state, toolbarState) {
      state.toolbar = toolbarState;
      localStorage.setItem("toolbar", JSON.stringify(toolbarState));
    },
    loadSortInfo(state, sortInfo) {
      if (sortInfo !== null && sortInfo.length !== 0) {
        const sortedData = sortInfo.sort((s1, s2) => s1.priority - s2.priority);
        
        // 存储带时间戳的数据
        const cacheData = {
          timestamp: Date.now(),
          data: sortedData
        };
        
        state.sortInfo = sortedData;
        localStorage.setItem("sortInfo", JSON.stringify(cacheData));
      }
    },
    loadCurrentUser(state, user) {
      state.currentUser = user;
      localStorage.setItem("currentUser", JSON.stringify(user));
    },
    loadSysConfig(state, sysConfig) {
      state.sysConfig = sysConfig;
      localStorage.setItem("sysConfig", JSON.stringify(sysConfig));
    },
    loadCurrentAdmin(state, user) {
      state.currentAdmin = user;
      localStorage.setItem("currentAdmin", JSON.stringify(user));
    },
    loadWebInfo(state, webInfo) {
      // 解析JSON格式的数据
      webInfo.notices = JSON.parse(webInfo.notices);
      webInfo.randomCover = JSON.parse(webInfo.randomCover);
      
      // 确保navConfig是有效的JSON字符串
      if (!webInfo.navConfig || webInfo.navConfig === "{}" || webInfo.navConfig === "") {
        webInfo.navConfig = "[]";
        console.log("Vuex store: 导航栏配置为空，设置为默认空数组");
      }
      
      // 不再将webTitle分割为数组，保持原始字符串格式
      // webInfo.webTitle = webInfo.webTitle.split('');
      
      // 存储带时间戳的数据，但将访问量数据提取出来单独存储
      const visitCounts = {
        historyAllCount: webInfo.historyAllCount,
        historyDayCount: webInfo.historyDayCount
      };
      
      // 存储网站信息时排除访问量
      const webInfoToCache = {...webInfo};
      delete webInfoToCache.historyAllCount;
      delete webInfoToCache.historyDayCount;
      
      const cacheData = {
        timestamp: Date.now(),
        data: webInfoToCache
      };
      
      // 合并数据展示
      state.webInfo = {...webInfoToCache, ...visitCounts};
      localStorage.setItem("webInfo", JSON.stringify(cacheData));
      
      // 单独存储访问量数据，不做持久化缓存
      state.visitCounts = visitCounts;
    },
    setWebInfo(state, webInfo) {
      // 存储带时间戳的数据
      const cacheData = {
        timestamp: Date.now(),
        data: webInfo
      };
      
      state.webInfo = webInfo;
      localStorage.setItem('webInfo', JSON.stringify(cacheData));
    },
    
    // 显示或隐藏验证码
    showCaptcha(state, show) {
      state.captcha.show = show;
      // 如果隐藏验证码，重置其他状态
      if (!show) {
        state.captcha.params = null;
      }
    },
    
    // 设置验证码参数
    setVerifyParams(state, params) {
      if (params) {
        state.captcha.action = params.action || 'comment';
        state.captcha.params = params;
        state.captcha.onSuccess = params.onSuccess || null;
      }
    },
    
    // 执行验证成功回调
    executeCaptchaCallback(state, token) {
      if (state.captcha.onSuccess && typeof state.captcha.onSuccess === 'function') {
        state.captcha.onSuccess(token);
      }
      // 重置状态
      state.captcha.show = false;
      state.captcha.params = null;
      state.captcha.onSuccess = null;
    }
  },
  actions: {},
  modules: {},
  plugins: []
})
