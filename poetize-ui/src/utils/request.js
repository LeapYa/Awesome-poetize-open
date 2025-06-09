import axios from "axios";
import constant from "./constant";
//处理url参数
import qs from "qs";

import store from "../store";
import router from "../router";

// 设置请求基本配置
axios.defaults.baseURL = constant.baseURL;
axios.defaults.timeout = 60000; // 设置60秒超时，从15秒改为60秒

// 添加请求拦截器
axios.interceptors.request.use(function (config) {
  // 对Python API请求特殊处理，增加重试次数和超时设置
  if (config.url && config.url.includes('/python/seo/')) {
    config.timeout = 30000; // 增加SEO API超时时间
    config.retry = 3; // 最大重试次数
    config.retryDelay = 1000; // 重试间隔时间
    
    // 添加防止请求卡住的处理
    const source = axios.CancelToken.source();
    config.cancelToken = source.token;
    
    // 设置更长时间的取消令牌，确保请求不会无限挂起
    setTimeout(() => {
      source.cancel('SEO请求超时自动取消');
    }, 35000); // 比timeout稍长，给重试留时间
  }
  
  // 对文章保存和编辑接口设置更长的超时时间
  if (config.url && (config.url.includes('/article/saveArticle') || config.url.includes('/article/updateArticle'))) {
    config.timeout = 120000; // 文章保存请求设置2分钟超时
    console.log('文章保存请求设置更长超时时间: 120秒');
  }

  // 如果是验证码相关的请求，不需要token
  if (config.url && config.url.includes('/captcha/')) {
    return config;
  }

  // 处理token
  const isAdmin = config.isAdmin || false;
  const token = isAdmin ? localStorage.getItem("adminToken") : localStorage.getItem("userToken");
  
  if (token) {
    // 确保Authorization头存在
    if (!config.headers) {
      config.headers = {};
    }
    // 添加Bearer前缀
    config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    console.log(`发送请求 ${config.url} 使用${isAdmin ? '管理员' : '用户'}token: ${token.substring(0, 10)}...`);
  } else {
    console.warn(`请求 ${config.url} 未找到${isAdmin ? '管理员' : '用户'}token`);
  }

  return config;
}, function (error) {
  console.error('请求拦截器错误:', error);
  return Promise.reject(error);
});

// 添加响应拦截器
axios.interceptors.response.use(function (response) {
  if (response.data !== null && response.data.hasOwnProperty("code") && response.data.code !== 200) {
    if (response.data.code === 300 || response.data.code === 401) {
      // token失效，清除token并跳转到登录页
      console.log('Token失效，清除token并跳转到登录页');
      localStorage.removeItem("userToken");
      localStorage.removeItem("adminToken");
      store.commit("loadCurrentUser", {});
      store.commit("loadCurrentAdmin", {});
      
      // 如果是管理员token失效，跳转到管理员登录页
      if (response.config.isAdmin) {
        console.log('管理员token失效，跳转到管理员登录页');
        router.push({
          path: '/verify',
          query: { redirect: router.currentRoute.fullPath }
        });
      } else {
        console.log('用户token失效，跳转到首页');
        router.push('/');
      }
    }
    return Promise.reject(new Error(response.data.message || '请求失败'));
  }
  return response;
}, function (error) {
  // 处理网络错误
  if (error.response) {
    // 服务器返回错误状态码
    console.error('响应错误:', error.response.status, error.response.data);
    if (error.response.status === 401 || error.response.status === 403) {
      // token相关错误，清除token并跳转
      localStorage.removeItem("userToken");
      localStorage.removeItem("adminToken");
      store.commit("loadCurrentUser", {});
      store.commit("loadCurrentAdmin", {});
      router.push('/');
    }
  } else if (error.request) {
    // 请求发出但没有收到响应
    console.error('网络错误:', error.request);
  } else {
    // 请求配置出错
    console.error('请求错误:', error.message);
  }
  return Promise.reject(error);
});

// 当data为URLSearchParams对象时设置为application/x-www-form-urlencoded;charset=utf-8
// 当data为普通对象时，会被设置为application/json;charset=utf-8

export default {
  post(url, params = {}, isAdmin = false, json = true) {
    let config = {
      isAdmin: isAdmin
    };
    
    const token = isAdmin ? localStorage.getItem("adminToken") : localStorage.getItem("userToken");
    
    if (token) {
      config.headers = {
        "Authorization": token.startsWith('Bearer ') ? token : `Bearer ${token}`
      };
    }

    // 如果不是json格式，将参数转换为URLSearchParams
    const data = json ? params : new URLSearchParams(params);

    return new Promise((resolve, reject) => {
      axios
        .post(url, data, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  get(url, params = {}, isAdmin = false) {
    let headers;
    const token = isAdmin ? localStorage.getItem("adminToken") : localStorage.getItem("userToken");
    
    if (token) {
      headers = {
        "Authorization": token.startsWith('Bearer ') ? token : `Bearer ${token}`
      };
    }

    return new Promise((resolve, reject) => {
      axios.get(url, {
        params: params,
        headers: headers,
        isAdmin: isAdmin
      }).then(res => {
        resolve(res.data);
      }).catch(err => {
        reject(err)
      })
    });
  },

  upload(url, param, isAdmin = false, option) {
    let config;
    if (isAdmin) {
      config = {
        headers: {"Authorization": localStorage.getItem("adminToken"), "Content-Type": "multipart/form-data"},
        timeout: 60000
      };
    } else {
      config = {
        headers: {"Authorization": localStorage.getItem("userToken"), "Content-Type": "multipart/form-data"},
        timeout: 60000
      };
    }
    if (typeof option !== "undefined") {
      config.onUploadProgress = progressEvent => {
        if (progressEvent.total > 0) {
          progressEvent.percent = progressEvent.loaded / progressEvent.total * 100;
        }
        option.onProgress(progressEvent);
      };
    }

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  },

  uploadQiniu(url, param) {
    let config = {
      headers: {"Content-Type": "multipart/form-data"},
      timeout: 60000
    };

    return new Promise((resolve, reject) => {
      axios
        .post(url, param, config)
        .then(res => {
          resolve(res.data);
        })
        .catch(err => {
          reject(err);
        });
    });
  }
}
