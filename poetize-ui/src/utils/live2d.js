/*
 * Live2D Widget
 * https://github.com/stevenjoezhang/live2d-widget
 */


import constant from "./constant";


// 注意：live2d_path 参数应使用绝对路径
const live2d_path = constant.live2d_path;

// 添加错误处理函数
function safeExec(fn, defaultValue) {
  try {
    return fn();
  } catch (error) {
    console.warn('看板娘执行错误(可忽略):', error);
    return defaultValue;
  }
}

// 检查是否启用看板娘
function shouldLoadLive2D() {
  // 屏幕尺寸过小不加载
  if (screen.width <= 768) return false;
  
  // 尝试获取配置
  try {
    // 检查本地存储是否禁用了看板娘
    if (localStorage.getItem("waifu-display") && 
        Date.now() - localStorage.getItem("waifu-display") <= 86400000) {
      return false;
    }
    return true;
  } catch (e) {
    console.warn('看板娘配置检查错误(可忽略):', e);
    return false;
  }
}

// 加载 waifu.css live2d.min.js
if (shouldLoadLive2D()) {
  Promise.all([
    loadExternalResource(live2d_path + "waifu.css", "css"),
    loadExternalResource(live2d_path + "live2d.min.js", "js"),
    // 加载拖拽功能相关文件
    loadExternalResource(live2d_path + "waifu-drag.css", "css"),
    loadExternalResource(live2d_path + "waifu-drag.js", "js")
  ]).then(() => {
    // 看板娘API是由Java后端实现
    initWidget({
      waifuPath: constant.baseURL + constant.waifuPath,
      cdnPath: constant.cdnPath
    });
  }).catch(error => {
    console.warn('看板娘资源加载失败，不影响系统主要功能:', error);
  });
}

// 封装异步加载资源的方法
function loadExternalResource(url, type) {
  return new Promise((resolve, reject) => {
    // 检查是否已加载过此资源
    const existingResource = Array.from(document.head.children).find(el => {
      if (type === "css" && el.tagName === "LINK" && el.href && el.href.includes(url)) return true;
      if (type === "js" && el.tagName === "SCRIPT" && el.src && el.src.includes(url)) return true;
      return false;
    });
    
    // 如果已存在，则检查是否有效
    if (existingResource) {
      console.log(`资源已加载过: ${url}`);
      
      // 如果是js并且是live2d.min.js，验证loadlive2d函数是否可用
      if (type === "js" && url.includes("live2d.min.js") && typeof window.loadlive2d !== "function") {
        console.warn(`已加载的${url}无效，尝试移除并重新加载`);
        document.head.removeChild(existingResource);
      } else {
        return resolve(url);
      }
    }
    
    // 尝试首先使用fetch检查资源是否存在且有效
    if (type === "js") {
      fetch(url, { cache: "no-store" })
        .then(response => {
          if (!response.ok) {
            throw new Error(`资源响应错误: ${response.status}`);
          }
          return response.text();
        })
        .then(content => {
          // 检查内容是否是HTML而不是JS (通常出现在404或500错误页面)
          if (content.trim().startsWith("<!DOCTYPE") || content.trim().startsWith("<html")) {
            throw new Error("资源内容无效，返回了HTML而不是JavaScript");
          }
          
          // 检查通过，继续加载资源
          loadResourceElement();
        })
        .catch(error => {
          console.error(`预检查资源失败: ${url}`, error);
          
          // 尝试使用备用资源URL
          const backupUrl = getBackupResourceUrl(url);
          if (backupUrl && backupUrl !== url) {
            console.log(`尝试使用备用资源: ${backupUrl}`);
            loadExternalResource(backupUrl, type)
              .then(resolve)
              .catch(reject);
          } else {
            // 如果没有备用或备用也失败，继续尝试原始方法加载
            loadResourceElement();
          }
        });
    } else {
      // CSS文件直接加载
      loadResourceElement();
    }
    
    // 获取备用资源URL的函数
    function getBackupResourceUrl(originalUrl) {
      // 这里可以根据originalUrl返回一个备用URL
      if (originalUrl.includes("live2d.min.js")) {
        // 优先尝试从CDN加载
        return "https://cdn.jsdelivr.net/gh/stevenjoezhang/live2d-widget@latest/live2d.min.js";
      }
      return null;
    }
    
    // 加载资源元素
    function loadResourceElement() {
    let tag;

    if (type === "css") {
      tag = document.createElement("link");
      tag.rel = "stylesheet";
        tag.href = url + "?t=" + new Date().getTime(); // 添加时间戳防止缓存
    } else if (type === "js") {
      tag = document.createElement("script");
        tag.src = url + "?t=" + new Date().getTime(); // 添加时间戳防止缓存
    }

    if (tag) {
        tag.onload = () => {
          console.log(`资源加载成功: ${url}`);
          
          // 对于live2d.min.js，验证加载成功后函数是否可用
          if (url.includes('live2d.min.js')) {
            // 等待一点时间让脚本真正执行完
            setTimeout(() => {
              if (typeof window.loadlive2d !== 'function') {
                console.warn('live2d.min.js加载完成但loadlive2d函数未定义');
                
                // 尝试使用备用资源
                const backupUrl = getBackupResourceUrl(url);
                if (backupUrl && backupUrl !== url) {
                  console.log(`尝试使用备用CDN资源: ${backupUrl}`);
                  // 移除失败的标签
                  if (tag.parentNode) {
                    tag.parentNode.removeChild(tag);
                  }
                  
                  loadExternalResource(backupUrl, type)
                    .then(resolve)
                    .catch(reject);
                  return;
                }
                
                // 如果无备用源，尝试使用内联脚本注入最基本的loadlive2d实现
                injectFallbackLive2d();
                resolve(url);
              } else {
                resolve(url);
              }
            }, 300);
          } else {
            resolve(url);
          }
        };
        
        tag.onerror = () => {
          console.warn(`资源加载失败: ${url}`);
          
          // 移除失败的标签
          if (tag.parentNode) {
            tag.parentNode.removeChild(tag);
          }
          
          // 尝试使用备用URL
          const backupUrl = getBackupResourceUrl(url);
          if (backupUrl && backupUrl !== url) {
            console.log(`加载失败，尝试备用资源: ${backupUrl}`);
            loadExternalResource(backupUrl, type)
              .then(resolve)
              .catch(reject);
          } else {
            // 如果是live2d.min.js加载失败，注入一个基本实现
            if (url.includes('live2d.min.js')) {
              injectFallbackLive2d();
              resolve(url); // 即使使用了备用实现，也算作成功
            } else {
              // 其他资源，添加延时并重试原始URL
              setTimeout(() => {
                loadExternalResource(url, type)
                  .then(resolve)
                  .catch(reject);
              }, 1000);
            }
          }
        };
        
      document.head.appendChild(tag);
      }
    }
    
    // 注入基本的loadlive2d实现
    function injectFallbackLive2d() {
      console.log('注入备用live2d实现');
      const script = document.createElement('script');
      script.textContent = `
        // 基本的loadlive2d实现，提供最低限度的功能以避免错误
        window.loadlive2d = function(id, modelPath) {
          console.log('使用备用loadlive2d实现', id, modelPath);
          const element = document.getElementById(id);
          if (element) {
            // 显示一个消息，说明看板娘加载失败
            const parent = element.parentNode;
            if (parent && parent.id === 'waifu') {
              const tips = document.getElementById('waifu-tips');
              if (tips) {
                tips.innerHTML = '看板娘加载失败，请尝试刷新页面...';
                tips.style.fontSize = '14px';
                tips.style.color = '#f00';
                tips.style.background = 'rgba(255,255,255,0.8)';
                tips.style.padding = '10px';
                tips.style.borderRadius = '8px';
                tips.style.marginBottom = '10px';
                tips.style.display = 'block';
              }
            }
            return true;
          }
          return false;
        };
      `;
      document.head.appendChild(script);
    }
  });
}

// 添加一个函数用于检查是否启用看板娘
async function checkWaifuEnabled() {
  try {
    // 首先从后端获取状态
    const response = await fetch(constant.pythonBaseURL + "/webInfo/getWaifuStatus");
    const result = await response.json();
    
    if (result.code === 200) {
      console.log('从后端获取看板娘状态:', result.data.enableWaifu);
      return result.data.enableWaifu === true;
    }
    
    // 如果后端请求失败，尝试从本地存储获取
    const webInfoStr = localStorage.getItem('webInfo');
    if (webInfoStr) {
      try {
        const webInfoData = JSON.parse(webInfoStr);
        // 处理两种可能的数据格式
        if (webInfoData.data && webInfoData.data.enableWaifu !== undefined) {
          console.log('从本地存储获取看板娘状态(新格式):', webInfoData.data.enableWaifu);
          return webInfoData.data.enableWaifu === true;
        } else if (webInfoData.enableWaifu !== undefined) {
          console.log('从本地存储获取看板娘状态(旧格式):', webInfoData.enableWaifu);
          return webInfoData.enableWaifu === true;
        }
      } catch (e) {
        console.error('解析本地存储的webInfo失败:', e);
      }
    }
    
    // 默认值
    console.log('未能获取看板娘状态，使用默认值: false');
    return false;
  } catch (e) {
    console.error('获取看板娘状态失败:', e);
    return false;
  }
}

// 添加一个用于移除看板娘元素的函数
function removeWaifuElements() {
  console.log('移除看板娘元素');
  const toggle = document.getElementById("waifu-toggle");
  if (toggle) toggle.remove();
  
  const waifu = document.getElementById("waifu");
  if (waifu) waifu.remove();
}

async function initWidget(config) {
  // 首先检查是否启用
  const enabled = await checkWaifuEnabled();
  if (!enabled) {
    console.log('看板娘功能已禁用，跳过初始化');
    removeWaifuElements();
    return;
  }
  
  console.log('开始初始化看板娘');
  document.body.insertAdjacentHTML("beforeend", `<div id="waifu-toggle">
			<span>看板娘</span>
		</div>`);
  const toggle = document.getElementById("waifu-toggle");
  toggle.addEventListener("click", () => {
    toggle.classList.remove("waifu-toggle-active");
    if (toggle.getAttribute("first-time")) {
      loadWidget(config);
      toggle.removeAttribute("first-time");
    } else {
      localStorage.removeItem("waifu-display");
      document.getElementById("waifu").style.display = "";
      setTimeout(() => {
        document.getElementById("waifu").style.bottom = 0;
      }, 0);
    }
  });
  if (localStorage.getItem("waifu-display") && Date.now() - localStorage.getItem("waifu-display") <= 86400000) {
    toggle.setAttribute("first-time", true);
    setTimeout(() => {
      toggle.classList.add("waifu-toggle-active");
    }, 0);
  } else {
    loadWidget(config);
  }
}

async function loadWidget(config) {
  // 首先检查是否启用
  const enabled = await checkWaifuEnabled();
  if (!enabled) {
    console.log('看板娘功能已禁用，跳过加载');
    removeWaifuElements();
    return;
  }
  
  console.log('开始加载看板娘');
  
  // 在加载其他内容之前，先恢复保存的主题
  console.log('正在恢复保存的主题设置...');
  loadThemeFromStorage();
  
  // 快速加载本地库文件用于聊天功能
  try {
    // 加载KaTeX CSS（本地文件）
    if (!document.querySelector('link[href*="katex"]')) {
      await loadExternalResource("/libs/css/katex.min.css", "css");
    }
    
    // 加载KaTeX JS（本地文件）
    if (typeof window.katex === 'undefined') {
      await loadExternalResource("/libs/js/katex.min.js", "js");
    }
    
    // 加载markdown-it（本地文件）
    if (typeof window.markdownit === 'undefined') {
      await loadExternalResource("/libs/js/markdown-it.min.js", "js");
    }
  } catch (error) {
    console.warn('本地Markdown或数学公式库加载失败，将使用简单渲染:', error);
  }
  
  // 配置路径
  let {waifuPath, cdnPath} = config;
  if (!cdnPath.endsWith("/")) cdnPath += "/";
  let modelList, idx = 0;

  // 插入html
  localStorage.removeItem("waifu-display");
  localStorage.removeItem("waifu-text");
  document.body.insertAdjacentHTML("beforeend", `<div id="waifu" style="bottom: -500px; transition: bottom 1s ease-in-out;">
			<div id="waifu-tips"></div>
			<canvas id="live2d" width="800" height="800"></canvas>
      <!-- 工具 -->
			<div id="waifu-tool">
				<span class="fa fa-lg fa-comment"></span>
				<span class="fa fa-lg fa-street-view"></span>
				<span class="fa fa-lg fa-mouse-pointer"></span>
				<span class="fa fa-lg fa-times"></span>
			</div>
		</div>`);
  
  setTimeout(() => {
    document.getElementById("waifu").style.bottom = 0;
    
    // 应用拖拽样式
    const waifu = document.getElementById("waifu");
    if (waifu) {
      waifu.style.cursor = 'default'; // 改为默认光标
      waifu.style.touchAction = 'none';
      waifu.style.zIndex = '1000';
      // 不再从localStorage读取位置

      // 看板娘点击事件
      const canvas = document.getElementById('live2d');
      if (canvas) {
        canvas.addEventListener('click', function(e) {
          // 只有在canvas区域点击才触发消息显示
          console.log('Canvas被点击了');
          // 随机显示不同的诗意消息
          const messages = [
            "好开心你注意到我了！我们可以在聊天功能里畅所欲言呢",
            "感谢你的互动！如果想聊更多，记得使用聊天功能找我哦",
            "你好呀！很高兴认识你，我们去聊天室里继续对话吧♪",
            "哇，你点我了！想要更深入的交流，就来聊天功能里找我吧",
            "感谢你的关注！在聊天里我可以更好地陪伴你呢",
            "你好！很开心能和你打招呼，聊天功能里见哦～",
            "点击成功！如果想要聊得更尽兴，聊天功能在等着我们呢"
          ];
          const randomMessage = messages[Math.floor(Math.random() * messages.length)];
          showMessage(randomMessage, 5000, 1);
        });
      }
      
      // ===== 阻止默认右键菜单 =====
      waifu.addEventListener('contextmenu', function(e) {
          e.preventDefault();
      });
      
      // 为提示框添加专门的事件处理
      const tips = document.getElementById("waifu-tips");
      if (tips) {
        // 阻止提示框的拖拽事件传播
        tips.addEventListener("mousedown", (e) => {
          e.stopPropagation();
        });
        
        tips.addEventListener("touchstart", (e) => {
          e.stopPropagation();
        });

        tips.addEventListener("mousemove", (e) => {
          e.stopPropagation();
        });
        
        tips.addEventListener("touchmove", (e) => {
          e.stopPropagation();
        });

        // 确保tips区域的文字选择功能
        tips.addEventListener("selectstart", (e) => {
          e.stopPropagation(); // 允许文字选择，但阻止事件传播
        });

        tips.addEventListener("click", (e) => {
          e.stopPropagation(); // 允许点击，但阻止事件传播
        });
      }
      
      // 添加整个看板娘元素的点击事件作为备用
      waifu.addEventListener("click", (e) => {
        // 如果点击的是提示框，不处理
        if (e.target.closest('#waifu-tips')) {
          return;
        }
        
        // 如果点击的不是工具栏和聊天窗口，显示互动消息
        if (!e.target.closest('#waifu-tool') && !e.target.closest('#waifu-chat')) {
          console.log("看板娘被点击");
        }
      });
    }
  }, 500);

  // 检测用户活动状态，并在空闲时显示消息
  let userAction = false,
    userActionTimer,
    messageTimer,
    messageArray = [
      "欢迎来到这个温馨的小站呢～🌟",
      "今天也要保持好心情哦！",
      "今天又学到了什么新知识吗？",
      "不如写篇博客记录一下今天的想法吧～",
      "记得多喝水，保护好眼睛哦！",
      "你的每一次访问都让我很开心呢 ✨",
      "发现了什么有趣的内容吗？",
      "要不要试试和我聊天呢？我很乐意陪你～",
      "这个博客真是个宝藏网站呢！",
      "如果我突然消失了，可能是被广告拦截器误伤了哦～",
      "点击我可以切换不同的造型哦～",
      "静静地陪伴是我最喜欢的事情了 💕",
      "今天心情怎么样？要不要分享给我听？",
      "学而时习之，不亦说乎～",
      "偶尔放松一下也是很重要的哦！"
    ];
  window.addEventListener("mousemove", () => userAction = true);
  window.addEventListener("keydown", () => userAction = true);
  setInterval(() => {
    if (userAction) {
      userAction = false;
      clearInterval(userActionTimer);
      userActionTimer = null;
    } else if (!userActionTimer) {
      userActionTimer = setInterval(() => {
        showMessage(randomSelection(messageArray), 6000, 9);
      }, 20000);
    }
  }, 1000);

  // 监听器
  (function registerEventListener() {
    document.querySelector("#waifu-tool .fa-comment").addEventListener("click", showAIChat);
    document.querySelector("#waifu-tool .fa-street-view").addEventListener("click", loadRandModel);
    document.querySelector("#waifu-tool .fa-mouse-pointer").addEventListener("click", changeMouseAnimation);
    document.querySelector("#waifu-tool .fa-times").addEventListener("click", () => {
      localStorage.setItem("waifu-display", Date.now());
      showMessage("愿你有一天能与重要的人重逢。", 2000, 11);
      document.getElementById("waifu").style.bottom = "-500px";
      setTimeout(() => {
        document.getElementById("waifu").style.display = "none";
        document.getElementById("waifu-toggle").classList.add("waifu-toggle-active");
      }, 3000);
    });
    window.addEventListener("copy", () => {
      showMessage("复制成功！希望这些内容对你有帮助～", 6000, 9);
    });
    window.addEventListener("visibilitychange", () => {
      if (!document.hidden) showMessage("欢迎回来！我一直在这里等你哦～", 6000, 9);
    });

    localStorage.setItem("showMouseAnimation", "1");
    document.querySelector("body").addEventListener("click", mouseAnimation);
  })();

  // 欢迎页
  (function welcomeMessage() {
    let text;
    if (location.pathname === "/") { // 如果是主页
      const now = new Date().getHours();
      if (now > 5 && now <= 7) text = "早上好！一日之计在于晨，美好的一天就要开始了。";
      else if (now > 7 && now <= 11) text = "上午好！工作顺利嘛，不要久坐，多起来走动走动哦！";
      else if (now > 11 && now <= 13) text = "中午了，工作了一个上午，现在是午餐时间！";
      else if (now > 13 && now <= 17) text = "午后很容易犯困呢，今天的运动目标完成了吗？";
      else if (now > 17 && now <= 19) text = "傍晚了！窗外夕阳的景色很美丽呢，最美不过夕阳红～";
      else if (now > 19 && now <= 21) text = "晚上好，今天过得怎么样？";
      else if (now > 21 && now <= 23) text = ["已经这么晚了呀，早点休息吧，晚安～", "深夜时要爱护眼睛呀！"];
      else text = "你是夜猫子呀？这么晚还不睡觉，明天起的来嘛？";
    } else if (document.referrer !== "") {
      const referrer = new URL(document.referrer),
        domain = referrer.hostname.split(".")[1];
      if (location.hostname === referrer.hostname) text = `欢迎阅读<span>「${document.title.split(" - ")[0]}」</span>`;
      else if (domain === "baidu") text = `Hello！来自 百度搜索 的朋友<br>你是搜索 <span>${referrer.search.split("&wd=")[1].split("&")[0]}</span> 找到的我吗？`;
      else if (domain === "so") text = `Hello！来自 360搜索 的朋友<br>你是搜索 <span>${referrer.search.split("&q=")[1].split("&")[0]}</span> 找到的我吗？`;
      else if (domain === "google") text = `Hello！来自 谷歌搜索 的朋友<br>欢迎阅读<span>「${document.title.split(" - ")[0]}」</span>`;
      else text = `Hello！来自 <span>${referrer.hostname}</span> 的朋友`;
    } else {
      text = `欢迎阅读<span>「${document.title.split(" - ")[0]}」</span>`;
    }
    showMessage(text, 7000, 8);
  })();

  // 初始化模型
  (function initModel() {
    let modelId = localStorage.getItem("modelId");
    if (modelId === null) {
      // 首次访问加载 指定模型 的 指定材质
      modelId = 5; // 模型 ID
    }
    loadModel(modelId);
    
    // 安全地获取配置
    try {
      fetch(waifuPath)
        .then(response => response.json())
        .then(result => {
          // 安全检查结果
          if (!result) {
            console.warn('看板娘配置为空，将使用默认配置');
            result = {
              mouseover: [],
              click: [],
              seasons: []
            };
          }
          
          // 确保所有配置项都存在
          result.mouseover = result.mouseover || [];
          result.click = result.click || [];
          result.seasons = result.seasons || [];
          
          // 添加事件监听器
          window.addEventListener("mouseover", event => {
            if (!result.mouseover || !Array.isArray(result.mouseover)) return;
            
            safeExec(() => {
              for (let {selector, text} of result.mouseover) {
                if (!event.target.matches(selector)) continue;
                text = randomSelection(text);
                text = text.replace("{text}", event.target.innerText);
                showMessage(text, 4000, 8);
                return;
              }
            });
          });
          
          window.addEventListener("click", event => {
            if (!result.click || !Array.isArray(result.click)) return;
            
            safeExec(() => {
              for (let {selector, text} of result.click) {
                if (!event.target.matches(selector)) continue;
                text = randomSelection(text);
                text = text.replace("{text}", event.target.innerText);
                showMessage(text, 4000, 8);
                return;
              }
            });
          });
          
          if (result.seasons && Array.isArray(result.seasons)) {
            safeExec(() => {
              result.seasons.forEach(({date, text}) => {
                const now = new Date(),
                  after = date.split("-")[0],
                  before = date.split("-")[1] || after;
                if ((after.split("/")[0] <= now.getMonth() + 1 && now.getMonth() + 1 <= before.split("/")[0]) && (after.split("/")[1] <= now.getDate() && now.getDate() <= before.split("/")[1])) {
                  text = randomSelection(text);
                  text = text.replace("{year}", now.getFullYear());
                  messageArray.push(text);
                }
              });
            });
          }
        })
        .catch(error => {
          console.warn('获取看板娘配置失败(可忽略):', error);
        });
    } catch (error) {
      console.warn('看板娘初始化错误(可忽略):', error);
    }
  })();

  // 模型集合
  async function loadModelList() {
    // 检查本地缓存
    const cacheKey = 'model-list-cache';
    const cacheTimeKey = 'model-list-cache-time';
    const cacheDuration = 24 * 60 * 60 * 1000; // 缓存1天
    
    const cachedTime = localStorage.getItem(cacheTimeKey);
    const now = Date.now();
    
    // 如果缓存有效且未过期
    if (cachedTime && (now - parseInt(cachedTime)) < cacheDuration) {
      try {
        const cachedData = localStorage.getItem(cacheKey);
        if (cachedData) {
          console.log('从本地缓存加载模型列表');
          modelList = JSON.parse(cachedData);
          return;
        }
      } catch (e) {
        console.error('缓存解析失败', e);
      }
    }
    
    try {
      // 从服务器加载，添加缓存破坏参数防止浏览器缓存
      console.log('从服务器加载模型列表');
      
      // 先使用fetch检查model_list.json是否有效
      try {
        const checkResponse = await fetch(`${cdnPath}model_list.json?t=${now}`, {
        headers: {
            'Cache-Control': 'no-cache'
          }
        });
        
        if (!checkResponse.ok) {
          throw new Error(`模型列表响应错误: ${checkResponse.status}`);
        }
        
        const contentType = checkResponse.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
          throw new Error(`模型列表内容类型错误: ${contentType}`);
        }
        
        const textContent = await checkResponse.text();
        if (textContent.trim().startsWith('<!DOCTYPE') || textContent.trim().startsWith('<html')) {
          throw new Error('模型列表返回了HTML而不是JSON');
        }
        
        // 确认是有效的JSON
        modelList = JSON.parse(textContent);
      } catch (error) {
        console.error('检查模型列表失败，尝试使用备用模型列表', error);
        // 尝试使用CDN备用
        try {
          const backupResponse = await fetch('https://cdn.jsdelivr.net/gh/stevenjoezhang/live2d-widget@latest/waifu-tips.json');
          // 使用一个简单的模型列表作为备用
          modelList = {
            models: [
              ["HyperdimensionNeptunia/neptune_classic"],
              ["HyperdimensionNeptunia/nepgear"],
              ["HyperdimensionNeptunia/histoire"],
              ["HyperdimensionNeptunia/blanc_swimwear"]
            ],
            messages: [
              "我是Neptune，今天也要元气满满地工作！",
              "大家好，我是Nepgear～",
              "Histoire准备好了帮助各位！",
              "我是Blanc，希望能和你愉快地度过这段时间。"
            ]
          };
        } catch (backupError) {
          console.error('加载备用模型列表也失败', backupError);
          // 使用固定的备用数据
          modelList = {
            models: [["HyperdimensionNeptunia/blanc_swimwear"]],
            messages: ["我是备用模型"]
          };
        }
      }
      
      // 无论如何都更新本地缓存
      try {
      localStorage.setItem(cacheKey, JSON.stringify(modelList));
      localStorage.setItem(cacheTimeKey, now.toString());
      } catch (cacheError) {
        console.warn('缓存模型列表失败', cacheError);
      }
    } catch (error) {
      console.error('模型列表加载失败', error);
      // 使用固定的备用数据
      modelList = {
        models: [["HyperdimensionNeptunia/blanc_swimwear"]],
        messages: ["我是备用模型"]
      };
    }
  }

  // 载入模型
  async function loadModel(modelId, message) {
    localStorage.setItem("modelId", modelId);
    if (message) showMessage(message, 4000, 10);
    
    try {
      // 确保模型列表已加载
      if (!modelList) await loadModelList();
      
      // 确保live2d库已加载
      if (!checkLive2dLoaded()) {
        console.log('live2d库未加载，尝试加载');
        const loaded = await ensureLive2dLoaded();
        
        if (!loaded) {
          throw new Error('无法加载live2d库，请刷新页面重试');
        }
      }
      
      // 从模型列表中选择正确的模型
      let texturesId = localStorage.getItem("modelTexturesId");
      // 如果没有保存的材质ID或材质ID无效，则使用默认材质
      if (!texturesId || texturesId < 0 || texturesId >= modelList.models[modelId].length) {
        texturesId = 0;
        localStorage.setItem("modelTexturesId", texturesId);
      }
      
      const target = modelList.models[modelId][texturesId];
      
      // 检查模型加载状态
      const modelLoadKey = `live2d-model-loaded-${target}`;
      
      if (!sessionStorage.getItem(modelLoadKey)) {
        console.log(`首次加载模型: ${target}`);
        document.getElementById("waifu-tips").innerHTML = "你好呀😊，很高兴见到你，今天你微笑了嘛？";
      }
      
      // 预检查模型文件是否可访问且是有效JSON
      try {
        const modelUrl = `${cdnPath}model/${target}/index.json`;
        const checkResponse = await fetch(modelUrl, {
          headers: {
            'Cache-Control': 'no-cache',
            'X-Request-Time': new Date().getTime()
          }
        });
        
        if (!checkResponse.ok) {
          throw new Error(`模型响应错误: ${checkResponse.status}`);
        }
        
        // 检查是否返回了HTML而不是JSON
        const textContent = await checkResponse.text();
        if (textContent.trim().startsWith('<!DOCTYPE') || textContent.trim().startsWith('<html')) {
          throw new Error('模型文件返回了HTML而不是JSON');
        }
        
        // 确认是有效的JSON
        JSON.parse(textContent);
        
        // 在验证成功后加载模型
        await loadModelFromURL(modelUrl);
      
      // 标记模型已加载
      sessionStorage.setItem(modelLoadKey, "true");
      } catch (modelCheckError) {
        console.error('检查模型文件失败，尝试使用备用CDN', modelCheckError);
        
        // 尝试从备用CDN加载
        try {
          // 使用备用CDN URL
          const backupUrl = `https://cdn.jsdelivr.net/gh/stevenjoezhang/live2d-widget/assets/hijiki.model.json`;
          await loadModelFromURL(backupUrl);
          showMessage("使用备用模型加载", 4000, 10);
        } catch (backupError) {
          console.error('备用CDN模型加载失败', backupError);
          throw backupError;
        }
      }
    } catch (error) {
      console.error('模型加载失败', error);
      document.getElementById("waifu-tips").innerHTML = "模型加载失败...尝试恢复";
      
      // 尝试恢复
      setTimeout(async () => {
        // 再次确保live2d库已加载
        if (!checkLive2dLoaded()) {
          await ensureLive2dLoaded();
        }
        
        if (checkLive2dLoaded()) {
          try {
            // 使用一个已知可用的固定模型URL作为最后的备用
            const fallbackModelUrl = "https://cdn.jsdelivr.net/gh/stevenjoezhang/live2d-widget/assets/hijiki.model.json";
            await loadModelFromURL(fallbackModelUrl);
            showMessage("模型已恢复", 4000, 10);
          } catch (e) {
            console.error('恢复失败', e);
            // 最后的备用方案：显示一个静态图片代替看板娘
            fallbackToStaticImage();
          }
        }
      }, 2000);
    }
  }
  
  // 加载模型的共用方法
  async function loadModelFromURL(modelUrl) {
    if (typeof window.loadlive2d !== 'function') {
      throw new Error('loadlive2d函数未定义');
    }
    
    // 添加时间戳防止缓存
    const urlWithTimestamp = `${modelUrl}${modelUrl.includes('?') ? '&' : '?'}t=${new Date().getTime()}`;
    return new Promise((resolve, reject) => {
      try {
        // 添加超时处理
        const timeout = setTimeout(() => {
          reject(new Error('模型加载超时'));
        }, 15000); // 15秒超时
        
        // 实际加载
        window.loadlive2d("live2d", urlWithTimestamp);
        
        // 取消超时
        clearTimeout(timeout);
        resolve();
      } catch (error) {
        reject(error);
      }
    });
  }
  
  // 最后的备用方案：显示静态图片
  function fallbackToStaticImage() {
    console.warn('使用静态图片作为最后的备用方案');
    const live2dElement = document.getElementById("live2d");
    if (live2dElement) {
      // 将canvas替换为img元素
      const wrapperDiv = live2dElement.parentElement;
      if (wrapperDiv) {
        const imgElement = document.createElement('img');
        imgElement.id = 'live2d-fallback';
        imgElement.src = 'https://cdn.jsdelivr.net/gh/stevenjoezhang/live2d-widget/assets/screenshot-1.png';
        imgElement.style.width = '200px'; 
        imgElement.style.height = 'auto';
        imgElement.style.bottom = '0';
        imgElement.style.margin = '0 auto';
        imgElement.style.display = 'block';
        
        // 移除canvas并添加img
        wrapperDiv.removeChild(live2dElement);
        wrapperDiv.insertBefore(imgElement, wrapperDiv.firstChild);
        
        const tipsElement = document.getElementById("waifu-tips");
        if (tipsElement) {
          tipsElement.innerHTML = "看板娘暂时无法加载，请稍后再试...";
        }
      }
    }
  }

  // 换肤
  async function loadRandModel() {
    const modelId = localStorage.getItem("modelId");
    if (!modelList) await loadModelList();
    const target = randomSelection(modelList.models[modelId]);
    
    // 保存选择的衣服到localStorage
    localStorage.setItem("modelTexturesId", modelList.models[modelId].indexOf(target));
    
    // 使用随机选择的新衣服
    loadlive2d("live2d", `${cdnPath}model/${target}/index.json`);
    showMessage("我的新衣服好看嘛？", 4000, 10);
  }

  // 换人
  async function loadOtherModel() {
    let modelId = localStorage.getItem("modelId");
    if (!modelList) await loadModelList();
    const index = (++modelId >= modelList.models.length) ? 0 : modelId;
    loadModel(index, modelList.messages[index]);
  }

  // 转换鼠标动画
  function changeMouseAnimation() {
    if (localStorage.getItem("showMouseAnimation") === "0") {
      localStorage.setItem("showMouseAnimation", "1");
      document.querySelector("body").addEventListener("click", mouseAnimation);
      showMessage("哈哈，要牢记社会主义核心价值观哦！", 6000, 9);
    } else {
      localStorage.setItem("showMouseAnimation", "0");
      document.querySelector("body").removeEventListener("click", mouseAnimation);
      showMessage("今天你爱国了吗？", 6000, 9);
    }
  }

  // 鼠标动画
  function mouseAnimation(e) {
    let list = new Array("富强", "民主", "文明", "和谐", "自由", "平等", "公正", "法治", "爱国", "敬业", "诚信", "友善");
    let span = $("<span>").text(list[idx]);
    idx = (idx + 1) % list.length;
    let x = e.pageX, y = e.pageY;
    span.css({
      "z-index": 1000,
      "top": y - 20,
      "left": x,
      "position": "absolute",
      "pointer-events": "none",
      "font-weight": "bold",
      "color": "#ff6651"
    });
    $("body").append(span);
    span.animate({"top": y - 180, "opacity": 0}, 1500, function () {
      span.remove();
    });
  }

  // 随机选择
  function randomSelection(obj) {
    return Array.isArray(obj) ? obj[Math.floor(Math.random() * obj.length)] : obj;
  }

  // 检查用户登录状态
  function checkUserLogin(showMessageOnFail = true) {
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
    if (!currentUser && showMessageOnFail) {
      showMessage("请先登录后再使用聊天功能哦～点击右上角登录", 4000, 9);
    }
    return currentUser;
  }

  // AI聊天系统
  async function initAIChat() {
    // 获取当前用户信息用于个性化欢迎
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
    
    // 获取AI聊天配置
    let aiConfig = null;
    try {
      const response = await fetch(`${constant.pythonBaseURL}/python/ai/chat/getConfig`);
      if (response.ok) {
        const result = await response.json();
        if (result.flag && result.data) {
          aiConfig = result.data;
        }
      }
    } catch (error) {
      console.warn('获取AI聊天配置失败:', error);
    }
    
    // 使用配置或默认值
    const chatName = aiConfig?.chat_name || 'AI助手';
    const welcomeMessage = aiConfig?.welcome_message || '你好！我是你的AI助手，有什么可以帮助你的吗？';
    const themeColor = aiConfig?.theme_color || '#4facfe';
    const enableTypingIndicator = aiConfig?.enable_typing_indicator !== false;

    // 创建聊天窗口HTML
    const chatHTML = `
      <div id="waifu-chat" class="waifu-chat-container" style="display: none;">
        <div class="chat-header">
          <span class="chat-title">💬 与${chatName}聊天</span>
          <div class="chat-header-actions">
            <span class="chat-clear" id="chat-clear-btn" title="清空聊天记录">🗑️</span>
            <span class="chat-close" id="chat-close-btn">×</span>
          </div>
        </div>
        <div class="chat-messages" id="chat-messages">
          <div class="message bot-message">
            <div class="message-content">${currentUser && currentUser.username ? 
              welcomeMessage.replace('你好', `你好，${currentUser.username}`) : 
              welcomeMessage}${currentUser ? ' 😊' : ''}</div>
            <div class="message-time">${new Date().toLocaleTimeString()}</div>
          </div>
        </div>
        <div class="chat-input-container">
          <input type="text" id="chat-input" placeholder="输入你想说的话..." maxlength="200">
          <button id="chat-send-btn">发送</button>
        </div>
        <div class="chat-quick-actions">
          <button class="quick-action-btn" data-action="页面信息">📄 页面信息</button>
          <button class="quick-action-btn" data-action="切换主题">🌓 切换主题</button>
          <button class="quick-action-btn" data-action="当前时间">⏰ 当前时间</button>
        </div>
        <div class="chat-tips">
          <small>💡 提示: 试试问我关于网站、技术或者日常话题</small>
        </div>
      </div>
    `;

    // 将聊天窗口添加到waifu容器中
    const waifuElement = document.getElementById("waifu");
    if (waifuElement) {
      waifuElement.insertAdjacentHTML('beforeend', chatHTML);
    }

    // 添加聊天窗口样式
    const chatStyles = `
      <style id="waifu-chat-styles">
        :root {
          /* 浅色模式变量 */
          --chat-bg-gradient: linear-gradient(-45deg, #e8d8b9, #eccec5, #a3e9eb, #bdbdf0, #eec1ea);
          --chat-header-bg: rgba(255,255,255,0.1);
          --chat-border: rgba(255,255,255,0.2);
          --chat-text-color: #2c3e50;
          --chat-message-bg: rgba(255,255,255,0.9);
          --chat-message-text: #333;
          --chat-user-message-bg: #4facfe;
          --chat-input-bg: rgba(255,255,255,0.9);
          --chat-input-focus-bg: white;
          --chat-btn-bg: #4facfe;
          --chat-btn-hover-bg: #00c6ff;
          --chat-quick-btn-bg: rgba(255,255,255,0.2);
          --chat-quick-btn-hover-bg: rgba(255,255,255,0.3);
          --chat-tips-bg: rgba(255,255,255,0.05);
          --chat-time-color: #666;
          --chat-scrollbar-track: rgba(255,255,255,0.1);
          --chat-scrollbar-thumb: rgba(255,255,255,0.3);
          --chat-typing-bg: rgba(255,255,255,0.9);
          --chat-typing-dot: #666;
          --chat-shadow: rgba(0,0,0,0.3);
        }

        .waifu-chat-container {
          position: absolute;
          left: 20px;
          bottom: 235px;
          width: 350px !important;
          height: 500px;
          background: var(--chat-bg-gradient);
          background-size: 400% 400%;
          animation: slideInUp 0.3s ease-out, gradientBG 10s ease infinite;
          border-radius: 15px 15px 15px 15px;
          box-shadow: 0 8px 32px var(--chat-shadow);
          backdrop-filter: blur(10px);
          border: 1px solid var(--chat-border);
          display: flex;
          flex-direction: column;
          z-index: 1002;
          transition: all 0.3s ease;
        }

        /* 深色模式样式 */
        .waifu-chat-container.dark-mode {
          --chat-bg-gradient: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
          --chat-header-bg: rgba(0,0,0,0.2);
          --chat-border: rgba(255,255,255,0.1);
          --chat-text-color: #ecf0f1;
          --chat-message-bg: rgba(44, 62, 80, 0.9);
          --chat-message-text: #ecf0f1;
          --chat-user-message-bg: #3498db;
          --chat-input-bg: rgba(44, 62, 80, 0.9);
          --chat-input-focus-bg: #34495e;
          --chat-btn-bg: #3498db;
          --chat-btn-hover-bg: #2980b9;
          --chat-quick-btn-bg: rgba(0,0,0,0.3);
          --chat-quick-btn-hover-bg: rgba(0,0,0,0.4);
          --chat-tips-bg: rgba(0,0,0,0.1);
          --chat-time-color: rgba(236,240,241,0.7);
          --chat-scrollbar-track: rgba(0,0,0,0.2);
          --chat-scrollbar-thumb: rgba(255,255,255,0.2);
          --chat-typing-bg: rgba(44, 62, 80, 0.9);
          --chat-typing-dot: #ecf0f1;
          --chat-shadow: rgba(0,0,0,0.5);
        }

        @keyframes slideInUp {
          from {
            transform: translateY(100%);
            opacity: 0;
          }
          to {
            transform: translateY(0);
            opacity: 1;
          }
        }

        .chat-header {
          background: var(--chat-header-bg);
          padding: 25px 20px;
          border-radius: 15px 15px 0 0;
          display: flex;
          justify-content: space-between;
          align-items: center;
          border-bottom: 1px solid var(--chat-border);
        }

        .chat-title {
          color: var(--chat-text-color);
          font-weight: bold;
          font-size: 16px;
        }

        .chat-header-actions {
          display: flex;
          align-items: center;
          gap: 10px;
        }

        .chat-clear {
          color: var(--chat-text-color);
          font-size: 18px;
          cursor: pointer;
          padding: 4px 8px;
          border-radius: 6px;
          transition: all 0.3s ease;
          opacity: 0.7;
        }

        .chat-clear:hover {
          background: var(--chat-quick-btn-hover-bg);
          opacity: 1;
          transform: scale(1.1);
        }

        .chat-close {
          color: var(--chat-text-color);
          font-size: 24px;
          cursor: pointer;
          padding: 0 5px;
          border-radius: 50%;
          transition: all 0.3s ease;
        }

        .chat-close:hover {
          background: var(--chat-quick-btn-hover-bg);
          transform: rotate(90deg);
        }

        .chat-messages {
          flex: 1;
          padding: 20px;
          overflow-y: auto;
          max-height: 320px;
          min-height: 200px;
        }

        @keyframes fadeInUp {
          from {
            opacity: 0;
            transform: translateY(10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        .bot-message {
          display: flex !important;
          flex-direction: column !important;
          align-items: flex-start !important;
          margin-bottom: 15px !important;
          animation: fadeInUp 0.3s ease-out;
          width: 100% !important;
        }

        .bot-message .message-content {
          background: var(--chat-message-bg) !important;
          color: var(--chat-message-text) !important;
          padding: 12px 16px !important;
          border-radius: 18px 18px 18px 4px !important;
          max-width: 85% !important;
          word-wrap: break-word !important;
          white-space: normal !important;
          overflow-wrap: break-word !important;
          word-break: break-word !important;
          line-height: 1.4 !important;
          display: block !important;
          box-shadow: 0 2px 8px rgba(0,0,0,0.1) !important;
        }

        .user-message {
          display: flex !important;
          flex-direction: column !important;
          align-items: flex-end !important;
          margin-bottom: 15px !important;
          animation: fadeInUp 0.3s ease-out;
          width: 100% !important;
        }

        .user-message .message-content {
          background: var(--chat-user-message-bg) !important;
          color: white !important;
          padding: 12px 16px !important;
          border-radius: 18px 18px 4px 18px !important;
          max-width: 85% !important;
          margin-left: auto !important;
          word-wrap: break-word !important;
          white-space: normal !important;
          overflow-wrap: break-word !important;
          word-break: break-word !important;
          line-height: 1.4 !important;
          display: block !important;
          box-shadow: 0 2px 8px rgba(0,0,0,0.1) !important;
        }

        .message-time {
          font-size: 11px;
          color: var(--chat-time-color);
          margin-top: 5px;
          text-align: right;
        }

        .user-message .message-time {
          text-align: right;
        }

        .bot-message .message-time {
          text-align: left;
        }

        .chat-input-container {
          padding: 8px 20px;
          background: var(--chat-header-bg);
          display: flex;
          gap: 10px;
          border-top: 1px solid var(--chat-border);
        }

        #chat-input {
          flex: 1;
          padding: 12px 16px;
          border: none;
          border-radius: 25px;
          background: var(--chat-input-bg);
          color: var(--chat-message-text);
          font-size: 14px;
          outline: none;
          transition: all 0.3s ease;
        }

        #chat-input:focus {
          background: var(--chat-input-focus-bg);
          box-shadow: 0 0 0 3px rgba(255,255,255,0.3);
        }

        #chat-input::placeholder {
          color: rgba(0,0,0,0.5);
        }

        .dark-mode #chat-input::placeholder {
          color: rgba(236,240,241,0.5);
        }

        #chat-send-btn {
          padding: 12px 20px;
          border: none;
          border-radius: 25px;
          background: var(--chat-btn-bg);
          color: white;
          font-weight: bold;
          cursor: pointer;
          transition: all 0.3s ease;
          white-space: nowrap;
        }

        #chat-send-btn:hover {
          background: var(--chat-btn-hover-bg);
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(79,172,254,0.4);
        }

        #chat-send-btn:active {
          transform: translateY(0);
        }

        .chat-tips {
          padding: 6px 20px;
          text-align: center;
          color: #666;
          background: var(--chat-tips-bg);
        }

        .chat-quick-actions {
          padding: 10px 15px;
          background: var(--chat-tips-bg);
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          justify-content: center;
          border-top: 1px solid var(--chat-border);
        }

        .quick-action-btn {
          padding: 6px 12px;
          border: none;
          border-radius: 15px;
          background: var(--chat-quick-btn-bg);
          color: #2c3e50;
          font-size: 12px;
          cursor: pointer;
          transition: all 0.3s ease;
          white-space: nowrap;
          backdrop-filter: blur(5px);
        }

        .quick-action-btn:hover {
          background: var(--chat-quick-btn-hover-bg);
          transform: translateY(-1px);
          box-shadow: 0 2px 8px rgba(0,0,0,0.2);
        }

        .quick-action-btn:active {
          transform: translateY(0);
        }

        .typing-indicator {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 12px 16px;
          background: var(--chat-typing-bg);
          border-radius: 18px 18px 18px 4px;
          max-width: 85%;
          margin-bottom: 15px;
          animation: fadeInUp 0.3s ease-out;
        }

        .typing-avatar {
          flex-shrink: 0;
          width: 32px;
          height: 32px;
          border-radius: 50%;
          background: linear-gradient(135deg, #74b9ff 0%, #a29bfe 50%, #6c5ce7 100%);
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;
          font-size: 16px;
          font-weight: bold;
          box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }

        .typing-content {
          display: flex;
          flex-direction: column;
          gap: 4px;
          min-width: 0;
          flex: 1;
        }

        .typing-text {
          font-size: 13px;
          color: var(--chat-message-text);
          opacity: 0.8;
          margin: 0;
          transition: opacity 0.3s ease;
        }

        .typing-dots {
          display: flex;
          gap: 4px;
          align-items: center;
        }

        .typing-dot {
          width: 8px;
          height: 8px;
          background: var(--chat-typing-dot);
          border-radius: 50%;
          animation: typingDot 1.4s infinite;
        }

        .typing-dot:nth-child(2) {
          animation-delay: 0.2s;
        }

        .typing-dot:nth-child(3) {
          animation-delay: 0.4s;
        }

        @keyframes typingDot {
          0%, 60%, 100% {
            transform: scale(1);
            opacity: 0.5;
          }
          30% {
            transform: scale(1.2);
            opacity: 1;
          }
        }

        @keyframes fadeInUp {
          from {
            opacity: 0;
            transform: translateY(10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        /* 移动端适配 */
        @media screen and (max-width: 768px) {
          .waifu-chat-container {
            width: 320px !important;
            height: 400px;
            left: 10px;
            bottom: 280px;
            right: auto; /* 重置right属性 */
          }
          
          .chat-messages {
            max-height: 250px;
          }
        }

        /* 自定义滚动条 */
        .chat-messages::-webkit-scrollbar {
          width: 6px;
        }

        .chat-messages::-webkit-scrollbar-track {
          background: var(--chat-scrollbar-track);
          border-radius: 3px;
        }

        .chat-messages::-webkit-scrollbar-thumb {
          background: var(--chat-scrollbar-thumb);
          border-radius: 3px;
        }

        .chat-messages::-webkit-scrollbar-thumb:hover {
          background: rgba(255,255,255,0.5);
        }

        .dark-mode #chat-input::placeholder {
          color: rgba(236,240,241,0.5);
        }

        .dark-mode .chat-tips {
          color: rgba(236,240,241,0.7);
        }

        .dark-mode .quick-action-btn {
          color: #ecf0f1;
        }

        /* 聊天消息中的Markdown样式 */
        .message-content pre {
          background: #2d3748;
          color: #e2e8f0;
          padding: 12px;
          border-radius: 8px;
          margin: 8px 0;
          overflow-x: auto;
          font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
          font-size: 13px;
          line-height: 1.5;
          border: 1px solid rgba(255,255,255,0.1);
        }

        .dark-mode .message-content pre {
          background: #1a202c;
          border: 1px solid rgba(255,255,255,0.05);
        }

        .message-content code {
          background: rgba(0,0,0,0.1);
          color: #e53e3e;
          padding: 2px 6px;
          border-radius: 4px;
          font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
          font-size: 0.9em;
        }

        .dark-mode .message-content code {
          background: rgba(255,255,255,0.1);
          color: #feb2b2;
        }

        .message-content pre code {
          background: transparent !important;
          color: inherit !important;
          padding: 0 !important;
          border-radius: 0 !important;
        }

        .message-content h1, .message-content h2, .message-content h3,
        .message-content h4, .message-content h5, .message-content h6 {
          margin: 12px 0 8px 0;
          font-weight: bold;
          line-height: 1.3;
        }

        .message-content h1 { font-size: 1.2em; }
        .message-content h2 { font-size: 1.15em; }
        .message-content h3 { font-size: 1.1em; }
        .message-content h4 { font-size: 1.05em; }
        .message-content h5 { font-size: 1em; }
        .message-content h6 { font-size: 0.95em; }

        .message-content strong {
          font-weight: bold;
          color: var(--chat-message-text);
        }

        .message-content em {
          font-style: italic;
        }

        .message-content a {
          color: #3182ce;
          text-decoration: underline;
        }

        .dark-mode .message-content a {
          color: #63b3ed;
        }

        .message-content ul, .message-content ol {
          margin: 8px 0;
          padding-left: 20px;
        }

        .message-content li {
          margin: 4px 0;
          line-height: 1.4;
        }

        .message-content blockquote {
          border-left: 3px solid #e2e8f0;
          margin: 8px 0;
          padding: 8px 16px;
          background: rgba(0,0,0,0.05);
          border-radius: 0 8px 8px 0;
        }

        .dark-mode .message-content blockquote {
          border-left-color: #4a5568;
          background: rgba(255,255,255,0.05);
        }

        .message-content table {
          border-collapse: collapse;
          margin: 8px 0;
          font-size: 0.9em;
        }

        .message-content th, .message-content td {
          border: 1px solid #e2e8f0;
          padding: 6px 12px;
          text-align: left;
        }

        .dark-mode .message-content th, .dark-mode .message-content td {
          border-color: #4a5568;
        }

        .message-content th {
          background: rgba(0,0,0,0.05);
          font-weight: bold;
        }

        .dark-mode .message-content th {
          background: rgba(255,255,255,0.05);
        }

        /* 数学公式样式 */
        .message-content .math-block {
          margin: 12px 0;
          text-align: center;
          background: rgba(248, 249, 250, 0.8);
          border-radius: 8px;
          padding: 12px;
          border: 1px solid rgba(0,0,0,0.1);
          overflow-x: auto;
        }

        .dark-mode .message-content .math-block {
          background: rgba(0,0,0,0.2);
          border: 1px solid rgba(255,255,255,0.1);
        }

        .message-content .math-inline {
          margin: 0 2px;
          padding: 2px 4px;
          background: rgba(248, 249, 250, 0.6);
          border-radius: 4px;
          display: inline-block;
        }

        .dark-mode .message-content .math-inline {
          background: rgba(0,0,0,0.15);
        }

        /* KaTeX字体大小调整 */
        .message-content .katex {
          font-size: 1.1em;
        }

        .message-content .katex-display {
          margin: 0.5em 0;
        }

        .dark-mode .quick-action-btn {
          color: #ecf0f1;
        }

        /* 重置AI消息中所有元素的margin，确保与用户消息内边距一致 */
        .bot-message .message-content * {
          margin: 0 !important;
        }

        /* 恢复必要的间距，但保持紧凑 */
        .bot-message .message-content p + p,
        .bot-message .message-content div + div {
          margin-top: 8px !important;
        }

        .bot-message .message-content br + br {
          margin-top: 4px !important;
        }

        /* 工具调用信息样式 */
        .tool-call-info {
          margin: 8px 0 !important;
          border-radius: 8px;
          overflow: hidden;
          box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }

        .tool-call {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 10px 12px;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;
          font-size: 13px;
          font-weight: 500;
          transition: all 0.3s ease;
        }

        .tool-call.completed {
          background: linear-gradient(135deg, #56ab2f 0%, #a8e6cf 100%);
          animation: toolCompleted 0.5s ease-out;
        }

        .tool-call i {
          font-size: 14px;
          flex-shrink: 0;
        }

        .tool-call span {
          flex: 1;
          line-height: 1.3;
        }

        /* 工具调用完成动画 */
        @keyframes toolCompleted {
          0% {
            transform: scale(1);
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          }
          50% {
            transform: scale(1.02);
          }
          100% {
            transform: scale(1);
            background: linear-gradient(135deg, #56ab2f 0%, #a8e6cf 100%);
          }
        }

        /* 深色模式下的工具调用样式 */
        .dark-mode .tool-call {
          background: linear-gradient(135deg, #4a5568 0%, #2d3748 100%);
          box-shadow: 0 2px 8px rgba(0,0,0,0.3);
        }

        .dark-mode .tool-call.completed {
          background: linear-gradient(135deg, #38a169 0%, #68d391 100%);
        }

        /* 强制应用打字指示器的新间距 - 清除缓存版本 */
        #chat-messages .typing-indicator {
          margin-top: 20px !important;
          margin-bottom: 25px !important;
          margin-left: 0 !important;
          margin-right: 0 !important;
        }
      </style>
    `;

    // 添加样式到head
    if (!document.getElementById('waifu-chat-styles')) {
      document.head.insertAdjacentHTML('beforeend', chatStyles);
    }

    // 绑定聊天事件
    bindChatEvents();
    
    // 延迟恢复聊天记录，确保DOM已完全渲染
        setTimeout(() => {
      restoreChatHistory();
    }, 100);
    
    // 缓存AI配置供其他函数使用
    if (aiConfig) {
      localStorage.setItem('ai_chat_config', JSON.stringify(aiConfig));
    }
  }

  // 绑定聊天事件
  function bindChatEvents() {
    const chatContainer = document.getElementById('waifu-chat');
    const chatInput = document.getElementById('chat-input');
    const sendBtn = document.getElementById('chat-send-btn');
    const closeBtn = document.getElementById('chat-close-btn');
    const clearBtn = document.getElementById('chat-clear-btn');

    if (!chatContainer || !chatInput || !sendBtn || !closeBtn || !clearBtn) return;

    // 初始化聊天主题
    initChatTheme();

    // 为整个聊天容器添加事件保护
    chatContainer.addEventListener('mousedown', function(e) {
      e.stopPropagation(); // 阻止事件冒泡到拖拽处理器
    });

    chatContainer.addEventListener('touchstart', function(e) {
      e.stopPropagation(); // 阻止事件冒泡到拖拽处理器
    });

    chatContainer.addEventListener('mousemove', function(e) {
      e.stopPropagation(); // 阻止事件冒泡
    });

    chatContainer.addEventListener('touchmove', function(e) {
      e.stopPropagation(); // 阻止事件冒泡
    });

    // 关闭聊天窗口
    closeBtn.addEventListener('click', () => {
      chatContainer.style.display = 'none';
      showMessage("有什么想聊的随时找我哦！", 3000, 8);
    });

    // 清空聊天记录
    clearBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (confirm('确定要清空所有聊天记录吗？此操作不可恢复。')) {
        const success = clearChatHistory();
        if (success) {
          showMessage("聊天记录已清空！", 2000, 8);
        }
      }
    });

    // 发送消息
    const sendMessage = async () => {
      // 从后端获取最新的AI聊天配置
      let aiConfig = null;
      let themeColor = '#4facfe';
      let enableTypingIndicator = true;
      
      try {
        const response = await fetch(`${constant.pythonBaseURL}/python/ai/chat/getConfig`);
        if (response.ok) {
          const result = await response.json();
          if (result.flag && result.data) {
            aiConfig = result.data;
            themeColor = aiConfig.theme_color || '#4facfe';
            enableTypingIndicator = aiConfig.enable_typing_indicator !== false;
          }
        }
      } catch (e) {
        console.warn('获取AI配置失败，尝试使用缓存配置:', e);
        // 如果API调用失败，回退到localStorage缓存
        try {
          const aiConfigCache = localStorage.getItem('ai_chat_config');
          if (aiConfigCache) {
            aiConfig = JSON.parse(aiConfigCache);
            themeColor = aiConfig.theme_color || '#4facfe';
            enableTypingIndicator = aiConfig.enable_typing_indicator !== false;
          }
        } catch (cacheError) {
          console.warn('获取缓存配置也失败，使用默认值:', cacheError);
        }
      }
      
      // 检查用户登录状态
      const currentUser = checkUserLogin(false); // 传入false避免显示系统消息
      
      // 检查是否需要登录 - 修复字段名和逻辑
      const requireLogin = aiConfig?.require_login || aiConfig?.requireLogin || false;
      console.log('🎯 登录检查:', {requireLogin, currentUser: !!currentUser, aiConfig});
      
      if (requireLogin && !currentUser) {
        addMessage('💡 提示：需要登录后才能使用聊天功能，请先<a href="/user" style="color: ' + themeColor + '; text-decoration: underline;">点击这里登录</a>哦～', 'bot');
        return;
      }
      
      // 如果不要求登录但用户未登录，给出友好提示（但不阻止聊天）
      if (!requireLogin && !currentUser) {
        // 检查是否已经显示过提示
        const hasShownLoginTip = sessionStorage.getItem('loginTipShown');
        if (!hasShownLoginTip) {
          addMessage('💡 登录后可以获得更好的聊天体验，<a href="/user" style="color: ' + themeColor + '; text-decoration: underline;">点击这里登录</a>试试吧！当然，未登录也可以继续聊天～', 'bot');
          sessionStorage.setItem('loginTipShown', 'true');
        }
      }

      const message = chatInput.value.trim();
      if (!message) return;
      
      // 检查消息长度限制
      const maxMessageLength = aiConfig?.max_message_length || 500;
      if (message.length > maxMessageLength) {
        addMessage(`消息太长了，请控制在${maxMessageLength}个字符以内哦～`, 'bot');
        return;
      }
      
      // 检查速率限制
      const rateLimit = aiConfig?.rate_limit || 20; // 默认每分钟20条
      const now = Date.now();
      const userId = getCurrentUserId();
      const rateLimitKey = `chat_rate_limit_${userId}`;
      
      let rateLimitData = JSON.parse(localStorage.getItem(rateLimitKey) || '{"count": 0, "resetTime": 0}');
      
      // 检查是否需要重置计数器（每分钟重置）
      if (now > rateLimitData.resetTime) {
        rateLimitData = { count: 0, resetTime: now + 60000 }; // 60秒后重置
      }
      
      // 检查是否超过速率限制
      if (rateLimitData.count >= rateLimit) {
        const remainingTime = Math.ceil((rateLimitData.resetTime - now) / 1000);
        addMessage(`发送频率太快了，请等待${remainingTime}秒后再试～`, 'bot');
        return;
      }
      
      // 更新速率限制计数
      rateLimitData.count++;
      localStorage.setItem(rateLimitKey, JSON.stringify(rateLimitData));
      
      // 简单的内容过滤（如果启用）
      if (aiConfig?.enable_content_filter) {
        const badWords = ['垃圾', '傻逼', '废物']; // 可以扩展更多敏感词
        if (badWords.some(word => message.includes(word))) {
          addMessage('请文明聊天，避免使用不当词汇哦～', 'bot');
          return;
        }
      }

      if (/(切换主题|换主题|深色|浅色|暗色|亮色|主题|🌓 切换主题|🌙 切换主题)/.test(message)) {
        // 添加用户消息
        addMessage(message, 'user');
        chatInput.value = '';
        
        // 显示打字指示器（如果启用）
        if (enableTypingIndicator) {
          showTypingIndicator();
        }
        
        // 模拟处理延迟，让用户感觉到系统在处理
        setTimeout(() => {
          // 执行主题切换
          try {
            const resultMessage = toggleTheme();
            
            // 隐藏打字指示器
            if (enableTypingIndicator) {
              hideTypingIndicator();
            }
            
            addMessage(resultMessage, 'bot');
          } catch (error) {
            console.error('主题切换失败:', error);
            // 隐藏打字指示器
            if (enableTypingIndicator) {
              hideTypingIndicator();
            }
            addMessage("主题切换功能暂时不可用，请手动切换主题～", 'bot');
          }
        }, 800 + Math.random() * 1000); // 0.8-1.8秒延迟，模拟处理时间
        return;
      }

      // 添加用户消息
      addMessage(message, 'user');
      chatInput.value = '';

      // 显示打字指示器（如果启用）
      if (enableTypingIndicator) {
        showTypingIndicator();
      }

      // 模拟AI回复延迟并生成回复
      setTimeout(async () => {
        try {
          // 首先检查是否使用流式响应
          const streamingConfig = await getStreamingConfig();
          const isUsingStreaming = streamingConfig.enabled && streamingConfig.streaming_enabled && streamingConfig.configured;
          
          // 生成AI回复
          const aiResponse = await generateAIResponse(message);
          
          // 只有在非流式响应时才隐藏打字指示器（流式响应会在开始时自动隐藏）
          if (!isUsingStreaming) {
            hideTypingIndicator();
          }
          
          // 检查是否是流式响应完成标识
          if (aiResponse === '[[STREAMING_COMPLETED]]') {
            console.log('🎯 检测到流式响应完成标识，跳过重复添加消息');
            return; // 流式响应已处理完毕，无需再次添加消息
          }
          
          // 只有在非流式模式下才添加消息（流式模式下消息已经在callBackendAIStreaming中处理了）
          if (!isUsingStreaming) {
            addMessage(aiResponse, 'bot');
          }
        } catch (error) {
          console.error('AI回复生成失败:', error);
          hideTypingIndicator();
          addMessage('抱歉，我现在有点累了，请稍后再试试吧～', 'bot');
        }
      }, 1500 + Math.random() * 2000); // 1.5-3.5秒随机延迟
    };

    // 发送按钮点击
    sendBtn.addEventListener('click', async () => {
      await sendMessage();
    });

    // 回车发送
    chatInput.addEventListener('keypress', async (e) => {
      if (e.key === 'Enter') {
        await sendMessage();
      }
    });

    // 聊天窗口焦点管理
    chatContainer.addEventListener('click', (e) => {
      e.stopPropagation();
    });

    // 快捷功能按钮事件处理
    const quickActionBtns = chatContainer.querySelectorAll('.quick-action-btn');
    quickActionBtns.forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        
        const action = btn.getAttribute('data-action');
        
        // 检查用户登录状态
        const currentUser = checkUserLogin(false); // 传入false避免显示系统消息
        if (!currentUser) {
          addMessage('💡 提示：这个功能需要登录后才能使用哦～ <a href="/user" style="color: #4facfe; text-decoration: underline;">点击这里登录</a> 就能体验所有功能啦！✨', 'bot');
          return;
        }

        // 根据不同的动作生成实际内容
        let actualMessage = action;
        let userDisplayMessage = action;
        
        if (action === '页面信息') {
          const title = document.title;
          const url = window.location.href;
          const pathname = window.location.pathname;
          const elements = {
            articles: document.querySelectorAll('article, .article, .post').length,
            images: document.querySelectorAll('img').length,
            links: document.querySelectorAll('a').length,
            buttons: document.querySelectorAll('button').length
          };
          
          actualMessage = `用户询问当前页面信息。页面详情如下：
- 页面标题：${title}
- 页面路径：${pathname}
- 完整URL：${url}
- 页面元素统计：文章${elements.articles}篇，图片${elements.images}张，链接${elements.links}个，按钮${elements.buttons}个
请基于这些信息为用户介绍当前页面。`;
          
        } else if (action === '当前时间') {
          const now = new Date();
          const timeString = now.toLocaleTimeString();
          const dateString = now.toLocaleDateString();
          const dayName = now.toLocaleDateString('zh-CN', { weekday: 'long' });
          
          actualMessage = `用户询问当前时间。时间信息如下：
- 当前时间：${timeString}
- 今天日期：${dateString}
- 星期：${dayName}
请友好地告诉用户当前时间，并可以根据时间给出一些温馨提示。`;
          
        } else if (action === '切换主题') {
          // 显示用户点击的消息
          addMessage(userDisplayMessage, 'user');
          
          // 显示打字指示器
          showTypingIndicator();
          
          // 模拟处理延迟，让用户感觉到系统在处理
          setTimeout(() => {
            // 直接执行主题切换，不发送给AI
            try {
              const resultMessage = toggleTheme();
              
              // 隐藏打字指示器
              hideTypingIndicator();
              
              // 直接显示切换结果
              addMessage(resultMessage, 'bot');
            } catch (error) {
              console.error('主题切换失败:', error);
              // 隐藏打字指示器
              hideTypingIndicator();
              addMessage("主题切换功能暂时不可用，请手动切换主题～", 'bot');
            }
          }, 800 + Math.random() * 1000); // 0.8-1.8秒延迟，模拟处理时间
          return; // 直接返回，不执行后面的AI调用逻辑
        }

        // 显示用户输入的消息（简洁版本）
        addMessage(userDisplayMessage, 'user');
        
        // 显示打字指示器
        showTypingIndicator();
        
        // 发送实际详细信息给AI
        setTimeout(async () => {
          try {
            // 生成AI回复（使用包含详细信息的消息）
            const aiResponse = await generateAIResponse(actualMessage);
            
            // 首先检查是否使用流式响应
            const streamingConfig = await getStreamingConfig();
            const isUsingStreaming = streamingConfig.enabled && streamingConfig.streaming_enabled && streamingConfig.configured;

            // 只有在非流式响应时才隐藏打字指示器（流式响应会在开始时自动隐藏）
            if (!isUsingStreaming) {
              hideTypingIndicator();
            }

            // 检查是否是流式响应完成标识
            if (aiResponse === '[[STREAMING_COMPLETED]]') {
              console.log('🎯 快捷操作检测到流式响应完成标识，跳过重复添加消息');
              return; // 流式响应已处理完毕，无需再次添加消息
            }

            // 只有在非流式模式下才添加消息
            if (!isUsingStreaming) {
              addMessage(aiResponse, 'bot');
            }
          } catch (error) {
            console.error('AI回复生成失败:', error);
            hideTypingIndicator();
            addMessage('抱歉，处理这个请求时出现了问题～', 'bot');
          }
        }, 1200 + Math.random() * 1500); // 1.2-2.7秒随机延迟
      });
    });
  }

  // 添加消息到聊天界面
  function addMessage(content, type, messageId = null) {
    const messagesContainer = document.getElementById('chat-messages');
    if (!messagesContainer) return;

    // 对AI回复进行markdown渲染，用户消息保持原样
    let renderedContent = content;
    if (type === 'bot' || type === 'ai') {
      // 动态导入markdown-it库进行渲染
      try {
        // 检查是否已加载markdown-it
        if (typeof window.markdownit !== 'undefined') {
          const md = window.markdownit({
            html: true,
            breaks: true,
            linkify: true
          });
          renderedContent = md.render(content);
          
          // 渲染数学公式
          renderedContent = renderMathFormulas(renderedContent);
          
          // 将hr标签转换回---文本，保持与用户期望的显示一致
          renderedContent = renderedContent.replace(/<hr\s*\/?>/gi, '---');
        } else {
          // 如果markdown-it未加载，尝试使用简单的渲染
          renderedContent = renderSimpleMarkdown(content);
          // 简单渲染后也尝试渲染数学公式
          renderedContent = renderMathFormulas(renderedContent);
          
          // 简单渲染也需要处理hr转换
          renderedContent = renderedContent.replace(/<hr\s*\/?>/gi, '---');
        }
      } catch (error) {
        console.warn('Markdown渲染失败，使用原始内容:', error);
        renderedContent = content;
      }
    } else {
      // 用户消息不需要markdown渲染，但需要转义HTML防止XSS
      renderedContent = escapeHtml(content);
    }

    // 生成消息ID（如果没有提供）
    const msgId = messageId || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    
    const messageHTML = `
      <div class="message ${type === 'ai' ? 'bot' : type}-message" data-message-id="${msgId}">
        <div class="message-content">${renderedContent}</div>
        <div class="message-time">${new Date().toLocaleTimeString()}</div>
      </div>
    `;

    messagesContainer.insertAdjacentHTML('beforeend', messageHTML);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    // 如果是AI消息且包含代码块，应用代码高亮
    if (type === 'bot' || type === 'ai') {
      setTimeout(() => {
        const lastMessage = messagesContainer.lastElementChild;
        if (lastMessage) {
          const codeBlocks = lastMessage.querySelectorAll('pre code');
          codeBlocks.forEach(block => {
            try {
              if (typeof window.hljs !== 'undefined') {
                window.hljs.highlightElement(block);
              }
            } catch (error) {
              console.warn('代码高亮失败:', error);
            }
          });
        }
      }, 50);
    }

    // 保存聊天记录到本地存储（保存原始内容，不保存渲染后的HTML）
    // 注意：流式响应的消息在完成后才保存，避免保存不完整的内容
    if (!messageId || !messageId.startsWith('ai-streaming-')) {
      saveChatHistory(content, type);
    }
    
    return msgId;
  }

  // 简单的markdown渲染函数（备用方案）
  function renderSimpleMarkdown(text) {
    if (!text) return '';
    
    return text
      // 转义HTML标签防止XSS
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      // 保护数学公式不被转义（临时替换）
      .replace(/\$\$([\s\S]*?)\$\$/g, '[[MATH_BLOCK_$1]]')
      .replace(/\$([^$\n]+?)\$/g, '[[MATH_INLINE_$1]]')
      // 保护三个连续的短横线，避免被误处理（如果有其他逻辑可能处理它们）
      .replace(/---/g, '[[TRIPLE_DASH]]')
      // 渲染代码块
      .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>')
      // 渲染行内代码
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      // 渲染加粗
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      // 渲染斜体
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      // 渲染链接
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
      // 渲染换行
      .replace(/\n/g, '<br>')
      // 恢复数学公式
      .replace(/\[\[MATH_BLOCK_([\s\S]*?)\]\]/g, '$$$$1$$')
      .replace(/\[\[MATH_INLINE_(.*?)\]\]/g, '$$1$')
      // 恢复三个连续的短横线
      .replace(/\[\[TRIPLE_DASH\]\]/g, '---');
  }

  // HTML转义函数
  function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // 保存聊天记录到本地存储
  function saveChatHistory(content, type) {
    try {
      const userId = getCurrentUserId();
      const chatKey = `waifu-chat-history-${userId}`;
      
      // 获取现有聊天记录
      let chatHistory = JSON.parse(localStorage.getItem(chatKey) || '[]');
      
      // 添加新消息
      const message = {
        content: content,
        type: type,
        timestamp: new Date().toISOString(),
        time: new Date().toLocaleTimeString()
      };
      
      chatHistory.push(message);
      
      // 获取配置的最大聊天记录数量
      let maxChatHistory = 100; // 默认值
      try {
        const aiConfigCache = localStorage.getItem('ai_chat_config');
        if (aiConfigCache) {
          const config = JSON.parse(aiConfigCache);
          maxChatHistory = config.max_conversation_length * 10 || 100; // 配置数量的10倍作为存储数量
        }
        } catch (e) {
        // 使用默认值
      }
      
      // 限制聊天记录数量
      if (chatHistory.length > maxChatHistory) {
        chatHistory = chatHistory.slice(-maxChatHistory);
      }
      
      // 保存到本地存储
      localStorage.setItem(chatKey, JSON.stringify(chatHistory));
    } catch (error) {
      console.warn('保存聊天记录失败:', error);
    }
  }

  // 恢复聊天记录
  function restoreChatHistory() {
    try {
      const userId = getCurrentUserId();
      const chatKey = `waifu-chat-history-${userId}`;
      
      const chatHistory = JSON.parse(localStorage.getItem(chatKey) || '[]');
      const messagesContainer = document.getElementById('chat-messages');
      
      if (!messagesContainer || chatHistory.length === 0) return;

      // 清空现有消息（保留欢迎消息）
      const existingMessages = messagesContainer.querySelectorAll('.message');
      existingMessages.forEach((msg, index) => {
        // 保留第一条（欢迎消息）和第二条（登录提示，如果存在）
        if (index > 1) {
          msg.remove();
        }
      });

      // 恢复聊天记录，对AI消息进行markdown渲染
      chatHistory.forEach(msg => {
        let renderedContent = msg.content;
        
        // 对AI回复进行markdown渲染
        if (msg.type === 'bot' || msg.type === 'ai') {
          try {
            if (typeof window.markdownit !== 'undefined') {
              const md = window.markdownit({
                html: true,
                breaks: true,
                linkify: true
              });
              renderedContent = md.render(msg.content);
              // 渲染数学公式
              renderedContent = renderMathFormulas(renderedContent);
              
              // 将hr标签转换回---文本，保持与正常聊天的一致性
              renderedContent = renderedContent.replace(/<hr\s*\/?>/gi, '---');
            } else {
              renderedContent = renderSimpleMarkdown(msg.content);
              // 简单渲染后也尝试渲染数学公式
              renderedContent = renderMathFormulas(renderedContent);
              
              // 简单渲染也需要处理hr转换
              renderedContent = renderedContent.replace(/<hr\s*\/?>/gi, '---');
            }
          } catch (error) {
            console.warn('恢复聊天记录时Markdown渲染失败:', error);
            renderedContent = msg.content;
          }
        } else {
          // 用户消息转义HTML
          renderedContent = escapeHtml(msg.content);
        }
        
        // 确保类名与addMessage函数一致：ai类型转换为bot-message
        const messageClass = msg.type === 'ai' ? 'bot' : msg.type;
        
        const messageHTML = `
          <div class="message ${messageClass}-message">
            <div class="message-content">${renderedContent}</div>
            <div class="message-time">${msg.time}</div>
          </div>
        `;
        messagesContainer.insertAdjacentHTML('beforeend', messageHTML);
      });

      // 应用代码高亮到恢复的消息
      setTimeout(() => {
        const codeBlocks = messagesContainer.querySelectorAll('pre code');
        codeBlocks.forEach(block => {
          try {
            if (typeof window.hljs !== 'undefined') {
              window.hljs.highlightElement(block);
            }
          } catch (error) {
            console.warn('恢复聊天记录时代码高亮失败:', error);
          }
        });
        
        // 确保在代码高亮完成后滚动到底部
        setTimeout(() => {
          messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }, 50);
      }, 100);

      // 多次尝试滚动到底部，确保成功
      const scrollToBottom = () => {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
      };
      
      // 立即滚动
      scrollToBottom();
      
      // 延迟滚动，确保DOM完全渲染
      setTimeout(scrollToBottom, 50);
      setTimeout(scrollToBottom, 200);
      setTimeout(scrollToBottom, 500);
      
      if (chatHistory.length > 0) {
        console.log(`恢复了 ${chatHistory.length} 条聊天记录`);
      }
    } catch (error) {
      console.warn('恢复聊天记录失败:', error);
    }
  }

  // 清空聊天记录
  function clearChatHistory() {
    try {
      const userId = getCurrentUserId();
      const chatKey = `waifu-chat-history-${userId}`;
      
      // 获取当前聊天记录数量
      const currentHistory = JSON.parse(localStorage.getItem(chatKey) || '[]');
      const recordCount = currentHistory.length;
      
      // 清空本地存储
      localStorage.removeItem(chatKey);
      
      // 清空聊天界面并重新初始化
      const messagesContainer = document.getElementById('chat-messages');
      if (messagesContainer) {
        // 完全清空所有消息
        messagesContainer.innerHTML = '';
        
        // 重新添加欢迎消息
        const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
        
        // 获取AI聊天配置
        let aiConfig = null;
        try {
          const aiConfigCache = localStorage.getItem('ai_chat_config');
          if (aiConfigCache) {
            aiConfig = JSON.parse(aiConfigCache);
          }
        } catch (e) {
          // 使用默认配置
        }
        
        const chatName = aiConfig?.chat_name || 'AI助手';
        const welcomeMessage = aiConfig?.welcome_message || '你好！我是你的AI助手，有什么可以帮助你的吗？';
        
        // 添加欢迎消息
        const welcomeHTML = `
          <div class="message bot-message">
            <div class="message-content">${currentUser && currentUser.username ? 
              welcomeMessage.replace('你好', `你好，${currentUser.username}`) : 
              welcomeMessage}${currentUser ? ' 😊' : ''}</div>
            <div class="message-time">${new Date().toLocaleTimeString()}</div>
          </div>
        `;
        messagesContainer.insertAdjacentHTML('beforeend', welcomeHTML);
        
        // // 如果用户未登录，添加登录提示
        // if (!currentUser) {
        //   const themeColor = aiConfig?.theme_color || '#4facfe';
        //   const loginTipHTML = `
        //     <div class="message bot-message">
        //       <div class="message-content">💡 提示：要和我愉快聊天，请先<a href="/user" style="color: ${themeColor}; text-decoration: underline;">点击这里登录</a>哦～登录后就可以使用所有功能啦！</div>
        //       <div class="message-time">${new Date().toLocaleTimeString()}</div>
        //     </div>
        //   `;
        //   messagesContainer.insertAdjacentHTML('beforeend', loginTipHTML);
        // }
        
        // 添加清空成功的消息（不保存到记录中）
        const successMessageHTML = `
          <div class="message bot-message">
            <div class="message-content">聊天记录已清空！${recordCount > 0 ? `刚才清空了 ${recordCount} 条记录。` : ''}让我们重新开始聊天吧～ 🆕</div>
            <div class="message-time">${new Date().toLocaleTimeString()}</div>
          </div>
        `;
        messagesContainer.insertAdjacentHTML('beforeend', successMessageHTML);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
      }
      
      console.log(`聊天记录已清空，共删除 ${recordCount} 条记录`);
      return true;
    } catch (error) {
      console.warn('清空聊天记录失败:', error);
      return false;
    }
  }

  // 显示打字指示器
  function showTypingIndicator() {
    const messagesContainer = document.getElementById('chat-messages');
    if (!messagesContainer) return;

    // 随机选择打字消息
    const typingMessages = [
      "正在组织语言中", 
      "让我想想怎么回答",
      "正在努力思考中",
      "在脑海中搜索答案",
      "正在准备回复中"
    ];
    
    const randomMessage = typingMessages[Math.floor(Math.random() * typingMessages.length)];

    const typingHTML = `
      <div class="typing-indicator" id="typing-indicator">
        <div class="typing-avatar"></div>
        <div class="typing-content">
          <span class="typing-text">${randomMessage}</span>
          <div class="typing-dots">
            <div class="typing-dot"></div>
            <div class="typing-dot"></div>
            <div class="typing-dot"></div>
          </div>
        </div>
      </div>
    `;

    messagesContainer.insertAdjacentHTML('beforeend', typingHTML);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    
    // 动态更新打字消息（可选，让体验更丰富）
    const typingTextElement = document.querySelector('#typing-indicator .typing-text');
    if (typingTextElement) {
      let messageIndex = 0;
      const updateInterval = setInterval(() => {
        messageIndex = (messageIndex + 1) % typingMessages.length;
        if (typingTextElement) {
          typingTextElement.textContent = typingMessages[messageIndex];
        } else {
          clearInterval(updateInterval);
        }
      }, 2000); // 每2秒更换一次消息
      
      // 将interval存储在元素上，以便后续清理
      const typingIndicator = document.getElementById('typing-indicator');
      if (typingIndicator) {
        typingIndicator.updateInterval = updateInterval;
      }
    }
  }

  // 隐藏打字指示器
  function hideTypingIndicator() {
    const typingIndicator = document.getElementById('typing-indicator');
    if (typingIndicator) {
      // 清理动态更新interval
      if (typingIndicator.updateInterval) {
        clearInterval(typingIndicator.updateInterval);
      }
      typingIndicator.remove();
    }
  }

  // 生成AI回复 (智能对话逻辑)
  async function generateAIResponse(userMessage) {
    try {
      console.log('🤖 正在请求AI回复...', userMessage);
      
      // 首先获取流式响应配置
      const streamingConfig = await getStreamingConfig();
      
      let result;
      
      // 根据配置决定使用哪种API
      if (streamingConfig.enabled && streamingConfig.streaming_enabled && streamingConfig.configured) {
        console.log('🚀 使用流式响应API');
        result = await callBackendAIStreaming(userMessage);
        
        // 流式响应成功完成，返回特殊标识表示消息已处理
        if (result.success) {
          return '[[STREAMING_COMPLETED]]'; // 特殊标识，告诉调用方不要再次添加消息
        }
      } else {
        console.log('📨 使用传统响应API');
        result = await callBackendAI(userMessage);
      }
      
      if (result.success) {
        console.log('✅ AI回复成功:', result.response.substring(0, 100) + '...');
        return result.response;
      } else {
        console.log('❌ 后端AI返回错误:', result.error);
        throw new Error(result.error);
      }
    } catch (error) {
      console.log('❌ 后端AI暂不可用，使用本地对话逻辑:', error);
    }
    
    // 备份方案：使用原有的本地对话逻辑
    console.log('📝 使用本地对话逻辑处理:', userMessage);
    return generateLocalAIResponse(userMessage);
  }
  
  // 获取流式响应配置
  async function getStreamingConfig() {
    try {
      const response = await fetch(`${constant.pythonBaseURL}/python/ai/chat/getStreamingConfig`);
      if (response.ok) {
        return await response.json();
      } else {
        console.warn('获取流式响应配置失败');
        return { enabled: false, streaming_enabled: false, configured: false };
      }
    } catch (error) {
      console.warn('获取流式响应配置出错:', error);
      return { enabled: false, streaming_enabled: false, configured: false };
    }
  }

  // 获取用户ID的统一函数
  function getCurrentUserId() {
    try {
      const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
      if (!currentUser) return 'guest';
      
      // 尝试多个可能的用户ID字段
      return currentUser.userId || 
             currentUser.id || 
             currentUser.user_id || 
             currentUser.userInfo?.userId || 
             currentUser.userInfo?.id || 
             'guest';
    } catch (error) {
      console.warn('获取用户ID失败:', error);
      return 'guest';
    }
  }

  // 调用后端AI流式响应API
  async function callBackendAIStreaming(message) {
    try {
      // 获取AI聊天配置
      let aiConfig = null;
      try {
        const aiConfigCache = localStorage.getItem('ai_chat_config');
        if (aiConfigCache) {
          aiConfig = JSON.parse(aiConfigCache);
        }
      } catch (e) {
        console.warn('获取AI配置失败:', e);
      }
      
      // 获取聊天历史 - 使用统一的用户ID获取函数
      const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
      const userId = getCurrentUserId();
      
      console.log('🔍 用户信息调试:', { 
        currentUser, 
        userId,
        userKeys: currentUser ? Object.keys(currentUser) : null
      });
      
      const chatKey = `waifu-chat-history-${userId}`;
      const chatHistory = JSON.parse(localStorage.getItem(chatKey) || '[]');
      
      // 使用固定的conversationId，确保对话连续性
      const conversationId = `waifu_chat_${userId}`;
      
      // 准备聊天历史上下文（使用配置的历史数量）
      const maxHistoryCount = aiConfig?.max_conversation_length || 10;
      const recentHistory = chatHistory.slice(-maxHistoryCount).map(msg => ({
        role: msg.type === 'user' ? 'user' : 'assistant',
        content: msg.content
      }));
      
      // 如果配置了系统提示词，添加到请求中
      if (aiConfig?.custom_instructions) {
        recentHistory.unshift({
          role: 'system',
          content: aiConfig.custom_instructions
        });
      }
      
      console.log('📤 准备发送流式请求:', {
        message: message.substring(0, 50) + '...',
        conversationId,
        historyCount: recentHistory.length
      });
      
      // 检测是否需要页面内容
      const needsPageContent = detectPageContentNeed(message);
      let enhancedMessage = message;
      
      if (needsPageContent) {
        const pageContent = extractPageContent();
        
        const webAccessHint = `
        
[网页访问提示]
如果你支持直接访问网页，可以访问：${window.location.href}
以下是我提取的页面信息作为备用：`;
        
        enhancedMessage = `${message}${webAccessHint}\n\n[页面上下文信息]\n${pageContent}`;
      }
      
      const apiUrl = `${constant.pythonBaseURL}/python/ai/chat/sendStreamMessage`;
      
      return new Promise((resolve, reject) => {
        // 创建 EventSource 连接
        const eventSource = new EventSource(`${apiUrl}?${new URLSearchParams({
          message: enhancedMessage,
          conversationId: conversationId,
          userId: userId,  // 添加用户ID作为单独参数
          history: JSON.stringify(recentHistory),
          context: JSON.stringify({
            userId: userId,
            timestamp: new Date().toISOString(),
            platform: '看板娘聊天',
            pageUrl: window.location.href,
            pageTitle: document.title,
            hasPageContent: needsPageContent
          })
        })}`);
        
        let fullResponse = '';
        let messageElement = null;
        let receivedMessages = 0;
        let toolCallInProgress = false;
        let toolCallResults = [];
        
        // 创建一个AI消息容器用于实时显示
        const messageId = 'ai-streaming-' + Date.now();
        
        // 立即隐藏打字指示器，避免与流式消息容器冲突
        hideTypingIndicator();
        
        // 创建带有思考指示器的消息容器
        addMessage('<span class="thinking-dots">正在思考<span class="dots">...</span></span>', 'ai', messageId);
        
        // 减少等待时间，更快地获取消息元素引用
        setTimeout(() => {
          // 确保只在聊天窗口内查找，避免与页面其他元素冲突
          const chatContainer = document.getElementById('chat-messages');
          if (chatContainer) {
            // 方法1：通过data-message-id精确查找
            messageElement = chatContainer.querySelector(`[data-message-id="${messageId}"] .message-content`);
            
            // 方法2：如果方法1失败，查找最后一个AI消息
            if (!messageElement) {
              const allAiMessages = chatContainer.querySelectorAll('.bot-message');
              if (allAiMessages.length > 0) {
                const lastAiMessage = allAiMessages[allAiMessages.length - 1];
                messageElement = lastAiMessage.querySelector('.message-content');
              }
            }
            
            // 方法3：如果还是失败，查找最后一个消息
            if (!messageElement) {
              const allMessages = chatContainer.querySelectorAll('.message');
              if (allMessages.length > 0) {
                const lastMessage = allMessages[allMessages.length - 1];
                if (lastMessage.classList.contains('ai-message') || lastMessage.classList.contains('bot-message')) {
                  messageElement = lastMessage.querySelector('.message-content');
                }
              }
            }
          }
        }, 50); // 减少等待时间从100ms到50ms
        
        eventSource.onmessage = function(event) {
          receivedMessages++;
          
          try {
            const data = JSON.parse(event.data);
            
            if (data.content) {
              // 实时更新消息内容
              fullResponse += data.content;
              
              // 添加详细的内容接收日志
              console.log('📝 收到内容片段:', data.content.substring(0, 50) + (data.content.length > 50 ? '...' : ''));
              console.log('📏 当前累积内容长度:', fullResponse.length);
              console.log('🔧 工具调用状态:', { inProgress: toolCallInProgress, toolCount: toolCallResults.length });
              
              // 安全地更新内容
              if (messageElement) {
                // 如果是第一次收到内容，清除思考指示器
                if (fullResponse === data.content && !toolCallInProgress) {
                  messageElement.innerHTML = '';
                }
                
                // 渲染主要内容
                let displayContent = renderSimpleMarkdown(fullResponse);
                
                // 如果有进行中的工具调用，在内容末尾显示状态
                if (toolCallResults.length > 0) {
                  const activeToolCalls = toolCallResults.filter(tool => !tool.completed);
                  if (activeToolCalls.length > 0) {
                    const toolCallsHtml = activeToolCalls.map(tool => `
                      <div class="tool-call-info">
                        <div class="tool-call">
                          <i class="fas fa-tools"></i> 
                          <span>正在调用工具：${tool.name}</span>
                        </div>
                      </div>
                    `).join('');
                    displayContent = displayContent + toolCallsHtml;
                  }
                }
                
                messageElement.innerHTML = displayContent;
                
                // 滚动到底部
                const chatMessages = document.querySelector('#chat-messages');
                if (chatMessages) {
                  chatMessages.scrollTop = chatMessages.scrollHeight;
                }
              } else {
                console.warn('⚠️ messageElement 未找到，无法更新内容');
                // 如果还没找到元素，再次尝试查找
                const chatContainer = document.getElementById('chat-messages');
                if (chatContainer) {
                  messageElement = chatContainer.querySelector(`[data-message-id="${messageId}"] .message-content`);
                  if (!messageElement) {
                    const allAiMessages = chatContainer.querySelectorAll('.bot-message');
                    if (allAiMessages.length > 0) {
                      const lastAiMessage = allAiMessages[allAiMessages.length - 1];
                      messageElement = lastAiMessage.querySelector('.message-content');
                    }
                  }
                  
                  if (messageElement) {
                    // 清除思考指示器并设置内容
                    messageElement.innerHTML = renderSimpleMarkdown(fullResponse);
                  }
                }
              }
            } else if (data.event === 'start') {
              console.log('🚀 流式响应开始');
              // 更新思考指示器为"正在生成..."
              if (messageElement) {
                messageElement.innerHTML = '<span class="thinking-dots">正在生成<span class="dots">...</span></span>';
              }
            } else if (data.event === 'tool_call') {
              console.log('🔧 工具调用开始:', data.data);
              toolCallInProgress = true;
              
              // 记录工具调用信息
              const toolInfo = {
                name: data.data ? data.data.tool_name : '未知工具',
                completed: false,
                id: data.data ? data.data.tool_id : Date.now()
              };
              toolCallResults.push(toolInfo);
              
              console.log('📊 更新工具调用列表:', toolCallResults);
              
              // 立即显示工具调用状态
              if (messageElement) {
                let displayContent = renderSimpleMarkdown(fullResponse);
                const toolCallsHtml = `
                  <div class="tool-call-info">
                    <div class="tool-call">
                      <i class="fas fa-tools"></i> 
                      <span>正在调用工具：${toolInfo.name}</span>
                    </div>
                  </div>
                `;
                displayContent = displayContent + toolCallsHtml;
                messageElement.innerHTML = displayContent;
              }
            } else if (data.event === 'tool_result') {
              console.log('✅ 工具调用完成:', data.data);
              
              // 更新对应的工具调用状态
              const toolId = data.data ? data.data.tool_id : null;
              const toolIndex = toolCallResults.findIndex(tool => 
                tool.id === toolId || tool.name === (data.data ? data.data.tool_name : '未知工具')
              );
              
              if (toolIndex !== -1) {
                toolCallResults[toolIndex].completed = true;
                console.log('📝 工具调用状态已更新:', toolCallResults[toolIndex]);
              }
              
              // 检查是否所有工具调用都完成了
              const allToolsCompleted = toolCallResults.every(tool => tool.completed);
              console.log('🎯 所有工具调用完成状态:', allToolsCompleted, '工具列表:', toolCallResults);
              
              if (allToolsCompleted) {
                console.log('🎯 所有工具调用已完成，等待AI生成基于工具结果的回答...');
                console.log('📝 当前累积内容长度:', fullResponse.length);
                // 注意：不要在这里设置 toolCallInProgress = false
                // 需要等待AI基于工具结果生成完整回答，只有收到complete事件才真正结束
              }
              
              // 工具调用完成后，只显示内容，移除工具状态
              if (messageElement) {
                let displayContent = renderSimpleMarkdown(fullResponse);
                console.log('🖥️ 工具调用完成后显示内容长度:', displayContent.length);
                // 不再显示工具调用状态，让用户专注于结果
                messageElement.innerHTML = displayContent;
              }
            } else if (data.event === 'complete') {
              // 收到完成事件，正式结束响应
              console.log('🏁 收到complete事件，结束流式响应');
              eventSource.close();
              toolCallInProgress = false; // 在这里才设置为false
              console.log('✅ 流式响应完成');
              console.log('📊 总共收到消息数:', receivedMessages);
              console.log('🔧 工具调用数量:', toolCallResults.length);
              
              // 使用后端发送的完整响应，如果有的话
              const finalResponse = data.fullResponse || fullResponse;
              console.log('📝 最终响应长度:', finalResponse ? finalResponse.length : 0);
              console.log('📝 最终响应内容预览:', finalResponse ? finalResponse.substring(0, 200) + '...' : 'empty');
              
              // 确保最终内容显示在界面上（移除工具调用信息，只显示文本内容）
              if (messageElement && finalResponse) {
                messageElement.innerHTML = renderSimpleMarkdown(finalResponse);
              }
              
              // 保存完整的AI回复到聊天记录
              if (finalResponse) {
                saveChatHistory(finalResponse, 'ai');
              }
              
              resolve({
                success: true,
                response: finalResponse
              });
            } else if (data.error) {
              console.error('❌ 流式响应错误:', data.error);
              eventSource.close();
              reject(new Error(data.error));
            } else {
              console.log('❓ 收到未知格式的数据:', data);
              console.log('📋 数据详情:', JSON.stringify(data, null, 2));
              
              // 检查是否是其他类型的有用数据
              if (data.delta && data.delta.content) {
                console.log('🔄 检测到delta格式的内容:', data.delta.content);
                fullResponse += data.delta.content;
                
                if (messageElement) {
                  let displayContent = renderSimpleMarkdown(fullResponse);
                  messageElement.innerHTML = displayContent;
                }
              } else if (data.choices && data.choices[0] && data.choices[0].delta && data.choices[0].delta.content) {
                console.log('🔄 检测到OpenAI格式的内容:', data.choices[0].delta.content);
                fullResponse += data.choices[0].delta.content;
                
                if (messageElement) {
                  let displayContent = renderSimpleMarkdown(fullResponse);
                  messageElement.innerHTML = displayContent;
                }
              }
            }
          } catch (parseError) {
            console.error('❌ 解析流式响应数据出错:', parseError);
            console.log('📄 原始数据:', event.data);
            
            // 如果解析失败，检查是否是[DONE]信号
            if (event.data && event.data.trim() === '[DONE]') {
              console.log('🏁 收到流式响应结束信号');
              eventSource.close();
              
              // 保存完整的AI回复到聊天记录
              if (fullResponse) {
                saveChatHistory(fullResponse, 'ai');
              }
              
              resolve({
                success: true,
                response: fullResponse
              });
            } else {
              // 如果是其他格式错误，但有内容，尝试继续
              if (fullResponse) {
                console.log('⚠️ 虽然解析出错，但已有内容，继续处理');
              }
            }
          }
        };
        
        eventSource.onerror = function(error) {
          console.error('💥 流式响应连接出错:', error);
          eventSource.close();
          
          // 清理已创建的空消息容器
          const emptyMessage = document.querySelector(`[data-message-id="${messageId}"]`);
          if (emptyMessage && (!fullResponse || fullResponse.trim() === '')) {
            emptyMessage.remove();
          }
          
          // 如果没有收到任何内容，回退到传统API
          if (!fullResponse) {
            callBackendAI(message).then(resolve).catch(reject);
          } else {
            resolve({
              success: true,
              response: fullResponse
            });
          }
        };
        
        
        // 设置超时处理 - 动态调整超时时间
        const baseTimeout = 30000; // 基础30秒
        const extendedTimeout = 60000; // 有工具调用时60秒
        
        setTimeout(() => {
          if (eventSource.readyState !== EventSource.CLOSED) {
            // 动态判断当前是否有工具调用
            const hasToolCalls = toolCallResults.length > 0;
            const actualTimeout = hasToolCalls ? extendedTimeout : baseTimeout;
            
            console.log('⏰ 流式响应超时，实际超时时间:', actualTimeout / 1000, '秒');
            console.log('📊 超时时状态 - 工具调用进行中:', toolCallInProgress, '工具数量:', toolCallResults.length, '响应内容长度:', fullResponse ? fullResponse.length : 0);
            eventSource.close();
            
            // 清理已创建的空消息容器
            const emptyMessage = document.querySelector(`[data-message-id="${messageId}"]`);
            if (emptyMessage && (!fullResponse || fullResponse.trim() === '')) {
              emptyMessage.remove();
              console.log('🗑️ 已清理超时的流式消息容器');
            }
            
            // 如果有工具调用但AI没有基于结果生成回答，尝试提供工具调用结果
            if (toolCallResults.length > 0 && (!fullResponse || fullResponse.trim() === '')) {
              console.log('🔧 检测到工具调用但无AI回答，尝试显示工具调用结果');
              let toolResultsText = '已为你执行了工具调用，结果如下：\n\n';
              toolCallResults.forEach((tool, index) => {
                toolResultsText += `${index + 1}. ${tool.name}: ${tool.completed ? '完成' : '进行中'}\n`;
              });
              
              if (messageElement) {
                messageElement.innerHTML = renderSimpleMarkdown(toolResultsText);
              }
              
              resolve({
                success: true,
                response: toolResultsText
              });
            } else if (!fullResponse || fullResponse.trim() === '') {
              console.log('🔄 流式响应超时且无内容，回退到传统API');
              callBackendAI(message).then(resolve).catch(reject);
            } else {
              console.log('✅ 流式响应超时但有部分内容，返回已有响应');
              resolve({
                success: true,
                response: fullResponse
              });
            }
          }
        }, extendedTimeout); // 直接使用较长的超时时间，避免在有工具调用时过早超时
      });
    } catch (error) {
      console.error('💥 调用流式响应API失败:', error);
      // 回退到传统API，但返回特殊标识避免重复添加消息
      const result = await callBackendAI(message);
      if (result.success) {
        // 如果传统API成功，直接添加消息并返回特殊标识
        addMessage(result.response, 'ai');
        return { success: true, response: '[[STREAMING_COMPLETED]]' };
      }
      return result;
    }
  }
  
  // 检测是否需要页面内容
  function detectPageContentNeed(message) {
    const pageKeywords = [
      '页面', '网页', '这里', '当前', '这个页面', '这个网站',
      '文章', '内容', '标题', '导航', '菜单', '链接',
      '图片', '按钮', '表单', '评论', '作者', '时间',
      '怎么操作', '如何使用', '在哪里', '怎么找到',
      '页面上', '网站上', '这上面', '界面',
      '功能', '操作', '使用方法'
    ];
    
    return pageKeywords.some(keyword => message.includes(keyword));
  }
  
  // 提取页面内容
  function extractPageContent() {
    try {
      const pageInfo = {
        url: window.location.href,
        title: document.title,
        description: '',
        mainContent: '',
        articleInfo: {},
        navigation: [],
        actions: [],
        metadata: {}
      };
      
      // 获取页面描述
      const descMeta = document.querySelector('meta[name="description"]');
      if (descMeta) pageInfo.description = descMeta.content;
      
      // 提取文章信息（如果是文章页面）
      const titleElement = document.querySelector('h1, .title, .post-title, .article-title');
      if (titleElement) {
        pageInfo.articleInfo.title = titleElement.textContent.trim();
      }
      
      // 提取作者信息
      const authorSelectors = ['.author', '.post-author', '.article-author', '[data-author]', '.by-author'];
      for (const selector of authorSelectors) {
        const authorElement = document.querySelector(selector);
        if (authorElement) {
          pageInfo.articleInfo.author = authorElement.textContent.trim();
          break;
        }
      }
      
      // 提取发布时间
      const timeSelectors = ['time', '.date', '.post-date', '.publish-date', '.created-time'];
      for (const selector of timeSelectors) {
        const timeElement = document.querySelector(selector);
        if (timeElement) {
          pageInfo.articleInfo.publishDate = timeElement.textContent.trim() || timeElement.getAttribute('datetime');
          break;
        }
      }
      
      // 提取主要内容
      const contentSelectors = [
        'main', '.main-content', '.content', '.post-content',
        'article', '.article', '.entry-content', '.post-body',
        '.blog-post', '.page-content', '.markdown-body',
        '.article-content', '.post-main'
      ];
      
      let mainElement = null;
      for (const selector of contentSelectors) {
        mainElement = document.querySelector(selector);
        if (mainElement) break;
      }
      
      if (mainElement) {
        // 清理内容，移除脚本、样式和广告
        const clonedElement = mainElement.cloneNode(true);
        clonedElement.querySelectorAll('script, style, .ad, .advertisement, .sidebar, nav, header, footer').forEach(el => el.remove());
        
        // 提取文本内容，保持基本结构
        let textContent = clonedElement.innerText || clonedElement.textContent || '';
        textContent = textContent.trim().replace(/\s+/g, ' ');
        
        // 限制内容长度，但保留重要段落
        if (textContent.length > 3000) {
          const paragraphs = textContent.split('\n').filter(p => p.trim().length > 50);
          let summary = paragraphs.slice(0, 5).join('\n');
          if (summary.length > 3000) {
            summary = summary.substring(0, 3000) + '...[内容已截断]';
          }
          pageInfo.mainContent = summary;
        } else {
          pageInfo.mainContent = textContent;
        }
      }
      
      // 提取可操作的元素（按钮、链接等）
      const actionElements = document.querySelectorAll('button, .btn, .button, input[type="submit"], .action-btn');
      pageInfo.actions = Array.from(actionElements).slice(0, 8).map(btn => {
        return {
          text: btn.textContent.trim() || btn.value || btn.getAttribute('aria-label') || '按钮',
          type: btn.tagName.toLowerCase(),
          disabled: btn.disabled,
          visible: btn.offsetParent !== null
        };
      }).filter(action => action.text && action.visible);
      
      // 提取导航信息
      const navElements = document.querySelectorAll('nav a, .nav a, .menu a, .navbar a, .breadcrumb a');
      pageInfo.navigation = Array.from(navElements).slice(0, 12).map(link => ({
        text: link.textContent.trim(),
        href: link.href,
        current: link.classList.contains('active') || link.getAttribute('aria-current') === 'page'
      })).filter(item => item.text && item.text.length < 50);
      
      // 提取表单信息
      const forms = document.querySelectorAll('form');
      const formInfo = Array.from(forms).map(form => {
        const inputs = form.querySelectorAll('input, textarea, select');
        return {
          action: form.action || '当前页面',
          method: form.method || 'GET',
          fields: Array.from(inputs).map(input => ({
            type: input.type || input.tagName.toLowerCase(),
            name: input.name || input.id,
            placeholder: input.placeholder,
            required: input.required
          })).slice(0, 5)
        };
      }).slice(0, 3);
      
      // 提取元数据
      pageInfo.metadata = {
        lang: document.documentElement.lang || 'zh-CN',
        charset: document.characterSet,
        viewport: document.querySelector('meta[name="viewport"]')?.content || '',
        keywords: document.querySelector('meta[name="keywords"]')?.content || '',
        isArticle: !!(titleElement && mainElement),
        hasComments: document.querySelectorAll('.comment, .comments, #comments').length > 0,
        hasSearch: document.querySelectorAll('input[type="search"], .search-input, .search-box').length > 0
      };
      
      // 页面元素统计
      const elements = {
        articles: document.querySelectorAll('article, .article, .post').length,
        images: document.querySelectorAll('img').length,
        links: document.querySelectorAll('a').length,
        buttons: document.querySelectorAll('button, .btn').length,
        forms: document.querySelectorAll('form').length,
        videos: document.querySelectorAll('video').length,
        comments: document.querySelectorAll('.comment, .comment-item').length
      };
      
      // 构建发送给AI的页面信息
      let pageInfoText = `页面基本信息：
标题：${pageInfo.title}
URL：${pageInfo.url}
描述：${pageInfo.description || '无'}
页面类型：${pageInfo.metadata.isArticle ? '文章页面' : '普通页面'}`;

      // 如果是文章页面，添加文章信息
      if (pageInfo.articleInfo.title) {
        pageInfoText += `\n\n文章信息：
文章标题：${pageInfo.articleInfo.title}`;
        if (pageInfo.articleInfo.author) {
          pageInfoText += `\n作者：${pageInfo.articleInfo.author}`;
        }
        if (pageInfo.articleInfo.publishDate) {
          pageInfoText += `\n发布时间：${pageInfo.articleInfo.publishDate}`;
        }
      }

      pageInfoText += `\n\n主要内容摘要：
${pageInfo.mainContent || '无法提取主要内容'}`;

      if (pageInfo.navigation.length > 0) {
        pageInfoText += `\n\n页面导航菜单：
${pageInfo.navigation.map(nav => `- ${nav.text}${nav.current ? ' (当前页面)' : ''}`).join('\n')}`;
      }

      if (pageInfo.actions.length > 0) {
        pageInfoText += `\n\n可用操作按钮：
${pageInfo.actions.map(action => `- ${action.text} (${action.type})`).join('\n')}`;
      }

      if (formInfo.length > 0) {
        pageInfoText += `\n\n页面表单：
${formInfo.map(form => `- 表单 (${form.method} ${form.action}): ${form.fields.map(f => f.name || f.type).join(', ')}`).join('\n')}`;
      }

      pageInfoText += `\n\n页面功能特性：
- ${pageInfo.metadata.hasComments ? '支持评论功能' : '无评论功能'}
- ${pageInfo.metadata.hasSearch ? '有搜索功能' : '无搜索功能'}
- 语言：${pageInfo.metadata.lang}`;

      pageInfoText += `\n\n页面元素统计：
- 文章：${elements.articles}个
- 图片：${elements.images}个  
- 链接：${elements.links}个
- 按钮：${elements.buttons}个
- 表单：${elements.forms}个
- 视频：${elements.videos}个
- 评论：${elements.comments}个`;

      return pageInfoText;
      
    } catch (error) {
      console.error('提取页面内容失败:', error);
      return `页面信息提取失败，但可以回答关于 ${document.title} 页面的基本问题。当前URL: ${window.location.href}`;
    }
  }
  
  // 本地AI回复逻辑 (备份方案)
  function generateLocalAIResponse(userMessage) {
    const message = userMessage.toLowerCase();
    
    // 处理来自快捷按钮的详细信息请求
    if (message.includes('用户询问当前页面信息')) {
      // 从消息中提取页面信息
      const titleMatch = message.match(/页面标题：(.+)/);
      const pathMatch = message.match(/页面路径：(.+)/);
      const statsMatch = message.match(/页面元素统计：(.+)/);
      
      const title = titleMatch ? titleMatch[1] : '未知';
      const path = pathMatch ? pathMatch[1] : '未知';
      const stats = statsMatch ? statsMatch[1] : '未知';
      
      return `📄 **当前页面信息**<br><br>🏷️ **页面标题**: ${title}<br>📂 **页面路径**: ${path}<br>📊 **页面内容**: ${stats}<br><br>这个页面看起来内容很丰富呢！你可以通过页面上的导航菜单浏览不同的内容，或者告诉我你想了解什么功能～`;
    }
    
    if (message.includes('用户询问当前时间')) {
      // 从消息中提取时间信息
      const timeMatch = message.match(/当前时间：(.+)/);
      const dateMatch = message.match(/今天日期：(.+)/);
      const dayMatch = message.match(/星期：(.+)/);
      
      const time = timeMatch ? timeMatch[1] : '未知';
      const date = dateMatch ? dateMatch[1] : '未知';
      const day = dayMatch ? dayMatch[1] : '未知';
      
      // 根据时间给出不同的问候和建议
      const hour = new Date().getHours();
      let greeting = "";
      let suggestion = "";
      
      if (hour >= 6 && hour < 12) {
        greeting = "早上好！";
        suggestion = "新的一天开始了，记得吃早餐哦～";
      } else if (hour >= 12 && hour < 14) {
        greeting = "中午好！";
        suggestion = "午餐时间到了，记得好好吃饭哦～";
      } else if (hour >= 14 && hour < 18) {
        greeting = "下午好！";
        suggestion = "下午时光，适合学习和工作呢～";
      } else if (hour >= 18 && hour < 22) {
        greeting = "晚上好！";
        suggestion = "晚餐时间，记得营养均衡哦～";
      } else {
        greeting = "夜深了！";
        suggestion = "这么晚还在学习吗？记得早点休息哦～";
      }
      
      return `⏰ **当前时间信息**<br><br>🕐 **现在时间**: ${time}<br>📅 **今天日期**: ${date}<br>📆 **星期**: ${day}<br><br>${greeting} ${suggestion}`;
    }
    
    if (message.includes('用户请求切换网站主题')) {
      // 执行主题切换
      const result = pageInteractions.toggleTheme();
      const currentMatch = message.match(/当前主题是：(.+)/);
      const currentTheme = currentMatch ? currentMatch[1] : '未知';
      
      return `🎨 **主题切换**<br><br>${result}<br><br>之前是 ${currentTheme}，现在已经为你切换了！喜欢这个新的视觉效果吗？你可以随时再次点击来切换回去哦～`;
    }
    
    // 页面互动功能
    const pageInteractions = {
      // 获取页面信息
      getCurrentPageInfo: () => {
        const title = document.title;
        const url = window.location.href;
        const pathname = window.location.pathname;
        return { title, url, pathname };
      },
      
      // 滚动到页面顶部
      scrollToTop: () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
        return "已为你滚动到页面顶部！";
      },
      
      // 滚动到页面底部
      scrollToBottom: () => {
        window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
        return "已为你滚动到页面底部！";
      },
      
      // 切换主题（调用网站现有的主题切换功能）
      toggleTheme: () => {
        try {
          // 查找主题切换按钮并点击
          const themeButton = document.querySelector('.el-icon-sunny, .fa-moon-o');
          if (themeButton) {
            themeButton.click();
            return "已为你切换主题！";
          }
          
          // 如果找不到按钮，直接调用主题切换逻辑
          const resultMessage = toggleTheme();
          return resultMessage;
        } catch (error) {
          console.error('主题切换失败:', error);
          return "主题切换功能暂时不可用，请手动切换主题～";
        }
      },
      
      // 获取页面元素信息
      getPageElements: () => {
        const elements = {
          articles: document.querySelectorAll('article, .article, .post').length,
          images: document.querySelectorAll('img').length,
          links: document.querySelectorAll('a').length,
          buttons: document.querySelectorAll('button').length
        };
        return elements;
      },
      
      // 查找页面中的特定内容
      findContentOnPage: (keyword) => {
        const bodyText = document.body.innerText.toLowerCase();
        const count = (bodyText.match(new RegExp(keyword.toLowerCase(), 'g')) || []).length;
        return count;
      },
      
      // 获取当前时间和页面停留时间
      getPageStats: () => {
        const now = new Date();
        const timeString = now.toLocaleTimeString();
        const dateString = now.toLocaleDateString();
        return { time: timeString, date: dateString };
      }
    };
    
    // 页面互动指令检测
    if (/页面|当前页面|这个页面/.test(message)) {
      const pageInfo = pageInteractions.getCurrentPageInfo();
      if (/信息|内容|是什么/.test(message)) {
        return `当前页面信息：<br>📄 标题：${pageInfo.title}<br>🔗 路径：${pageInfo.pathname}<br>你想了解页面的什么功能吗？`;
      }
    }
    
    if (/回到顶部|滚动到顶部|页面顶部|返回顶部/.test(message)) {
      return pageInteractions.scrollToTop();
    }
    
    if (/滚动到底部|页面底部|到底部/.test(message)) {
      return pageInteractions.scrollToBottom();
    }
    
    if (/切换主题|换主题|深色|浅色|暗色|亮色/.test(message)) {
      return pageInteractions.toggleTheme();
    }
    
    if (/页面元素|页面内容|有什么内容/.test(message)) {
      const elements = pageInteractions.getPageElements();
      return `页面内容统计：<br>📝 文章：${elements.articles}篇<br>🖼️ 图片：${elements.images}张<br>🔗 链接：${elements.links}个<br>🔘 按钮：${elements.buttons}个`;
    }
    
    if (/时间|现在几点|当前时间/.test(message)) {
      const stats = pageInteractions.getPageStats();
      return `⏰ 当前时间：${stats.time}<br>📅 今天是：${stats.date}<br>在这个页面陪伴你真开心～`;
    }
    
    if (/搜索|查找|找/.test(message)) {
      // 提取搜索关键词
      const searchMatch = message.match(/搜索(.+)|查找(.+)|找(.+)/);
      if (searchMatch) {
        const keyword = (searchMatch[1] || searchMatch[2] || searchMatch[3]).trim();
        if (keyword && keyword.length > 0) {
          const count = pageInteractions.findContentOnPage(keyword);
          return count > 0 ? 
            `在页面中找到"${keyword}"相关内容${count}处！` : 
            `在当前页面没有找到"${keyword}"相关内容哦～`;
        }
      }
      return "你想搜索什么内容呢？可以说'搜索关键词'来查找页面内容！";
    }
    
    // 导航相关
    if (/导航|菜单|功能/.test(message)) {
      const navElements = document.querySelectorAll('nav, .nav, .menu, .header-menu');
      if (navElements.length > 0) {
        return "我发现页面有导航菜单！你可以通过顶部菜单浏览不同内容，或者告诉我你想去哪里，我来帮你导航～";
      } else {
        return "这个页面的导航在顶部，你可以通过菜单浏览不同的内容哦！";
      }
    }
    
    // 页面操作建议
    if (/怎么|如何|怎样/.test(message)) {
      if (/浏览|查看|看/.test(message)) {
        return "你可以：<br>📖 滚动页面查看内容<br>🔍 使用搜索功能<br>🎨 切换主题模式<br>📱 点击菜单导航<br>有什么特别想了解的吗？";
      }
    }
    
    // 页面快捷操作
    if (/快捷键|快捷操作|键盘/.test(message)) {
      return "常用快捷操作：<br>⌨️ Ctrl+F：页面搜索<br>🔄 F5：刷新页面<br>⬆️ Home：回到顶部<br>⬇️ End：到达底部<br>还有什么想了解的吗？";
    }
    
    // 关键词匹配回复
    const responses = {
      greetings: [
        "你好呀！很高兴和你聊天😊 我可以帮你操作页面哦！",
        "嗨～今天过得怎么样？需要我帮你浏览页面吗？",
        "你好！有什么想聊的吗？我还能帮你操作页面功能呢！",
        "hi～欢迎来和我聊天！试试说'页面信息'看看我能做什么～"
      ],
      
      website: [
        "这个网站真的很棒呢！你可以说'页面元素'看看都有什么内容～",
        "我很喜欢这里，要不要我帮你'滚动到底部'看看更多内容？",
        "这个博客系统功能很丰富，试试说'切换主题'换个颜色～",
        "网站的设计很用心，我作为看板娘也很开心能在这里陪伴大家！想了解'页面信息'吗？"
      ],
      
      technology: [
        "技术真是个神奇的东西！每天都在进步呢～这个页面就用了很多先进技术！",
        "编程就像魔法一样，可以创造出很多amazing的东西！比如我现在就能帮你操作页面呢！",
        "我虽然是看板娘，但也对新技术很感兴趣呢！想看看这个页面的技术栈吗？",
        "前端技术发展好快，Vue、React这些框架都很厉害！这个页面也用了现代化技术～"
      ],
      
      help: [
        "有什么需要帮助的吗？我可以帮你：<br>🔍 搜索页面内容<br>📱 操作页面功能<br>🎨 切换主题<br>📊 查看页面信息",
        "虽然我只是个看板娘，但也想为你做点什么呢～试试说'页面功能'看看我能帮什么！",
        "遇到问题不要着急，我可以帮你操作页面！比如说'回到顶部'或'页面信息'～",
        "需要什么帮助随时告诉我哦！我能帮你浏览和操作页面呢！"
      ],
      
      compliment: [
        "谢谢夸奖！你也很棒呢～💕",
        "嘿嘿，被夸奖了好开心！",
        "你这么说我会害羞的～",
        "你人真好！和你聊天很愉快！"
      ],
      
      goodbye: [
        "拜拜！记得常来看我哦～👋",
        "再见！期待下次聊天！",
        "路上小心！我会在这里等你回来的～",
        "拜拜！愿你每天都开心！"
      ],
      
      mood: [
        "我今天心情很好呢！和你聊天让我更开心了😊",
        "每天能见到大家我就很快乐～",
        "心情不好的时候就来和我聊聊天吧！",
        "好心情是会传染的哦～"
      ],
      
      weather: [
        "不管天气如何，保持好心情最重要！",
        "记得根据天气增减衣物哦～",
        "每种天气都有它的美好呢！",
        "我希望你每天都像阳光一样温暖！"
      ],
      
      time: [
        "时间过得真快呢！要珍惜每一天哦～",
        "不管什么时候，都要记得照顾好自己！",
        "时间是最珍贵的礼物，和你一起度过真好！",
        "每个时刻都是特别的，就像现在和你聊天一样！"
      ],
      
      default: [
        "嗯嗯，我觉得你说得很有道理呢！试试说'页面功能'看看我能帮什么～",
        "这个话题很有意思，你还想聊什么？或者我帮你操作页面？",
        "我理解你的想法～还有什么要分享的吗？我还能帮你浏览页面呢！",
        "继续说说吧，我在认真听呢！对了，试试说'当前时间'？",
        "你的想法很独特呢！想了解这个页面的信息吗？",
        "有意思～还有什么想告诉我的吗？我还会页面操作哦！",
        "我也这么觉得！你真聪明～要不要试试'切换主题'？",
        "嗯哼～还有别的想聊的吗？或者让我帮你操作页面？"
      ]
    };

    // 关键词检测逻辑
    if (/^(你好|hi|hello|嗨|hey)/.test(message)) {
      return randomSelection(responses.greetings);
    }
    
    if (/网站|博客|页面|系统/.test(message)) {
      return randomSelection(responses.website);
    }
    
    if (/技术|编程|代码|开发|前端|后端|vue|react|javascript/.test(message)) {
      return randomSelection(responses.technology);
    }
    
    if (/心情|开心|快乐|高兴|难过|郁闷/.test(message)) {
      return randomSelection(responses.mood);
    }
    
    if (/帮助|帮忙|求助|怎么办/.test(message)) {
      return randomSelection(responses.help);
    }
    
    if (/可爱|漂亮|好看|棒|厉害|喜欢|爱/.test(message)) {
      return randomSelection(responses.compliment);
    }
    
    if (/拜拜|再见|goodbye|bye|走了/.test(message)) {
      return randomSelection(responses.goodbye);
    }
    
    if (/天气|晴天|下雨|雪|风/.test(message)) {
      return randomSelection(responses.weather);
    }
    
    if (/时间|现在|今天|明天|昨天/.test(message)) {
      return randomSelection(responses.time);
    }
    
    // 特殊问答
    if (/你是谁|你叫什么/.test(message)) {
      return "我是这个网站的看板娘！专门陪伴大家聊天的小助手～我还能帮你操作页面呢，试试说'页面功能'！";
    }
    
    if (/你会什么|你能做什么/.test(message)) {
      return "我会很多呢！<br>💬 陪你聊天<br>🎨 换装表演<br>📱 操作页面功能<br>🔍 搜索页面内容<br>🎯 滚动页面<br>🌓 切换主题<br>📊 查看页面信息<br>试试和我说话吧！";
    }
    
    if (/你几岁|年龄/.test(message)) {
      return "我是永远18岁的看板娘哦！嘻嘻～年龄什么的不重要，开心最重要！要不要试试'页面信息'？";
    }
    
    if (/作者|开发者|制作/.test(message)) {
      return "这个网站是由很棒的开发者制作的！他们让我能够在这里和大家见面，还能帮大家操作页面，真的很感谢呢～";
    }

    // 聊天记录相关功能
    if (/清空|删除.*记录|清理.*记录/.test(message)) {
      return "你可以点击下面的'🗑️ 清空记录'按钮来清空聊天记录哦～但是要注意，清空后就找不回来了！";
    }
    
    if (/聊天记录|历史记录|记录|保存/.test(message)) {
      try {
        const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
        const userId = currentUser ? currentUser.userId : 'guest';
        const chatKey = `waifu-chat-history-${userId}`;
        const chatHistory = JSON.parse(localStorage.getItem(chatKey) || '[]');
        const count = chatHistory.length;
        
        if (count === 0) {
          return "我们还没有聊天记录呢～从现在开始的对话都会保存下来，下次打开聊天窗口就能看到啦！💫";
        } else {
          return `我们已经聊了 ${count} 条消息了呢！所有的对话都保存在你的浏览器里，下次来还能看到～如果想清空的话可以点击'🗑️ 清空记录'按钮哦！`;
        }
      } catch (error) {
        return "聊天记录功能出了点小问题，不过没关系，我们继续聊天吧～";
      }
    }

    // 默认回复
    return randomSelection(responses.default);
  }

  // 显示AI聊天窗口 (替换原来的showHitokoto函数)
  async function showAIChat() {
    // 获取AI聊天配置
    let aiConfig = null;
    try {
      const response = await fetch(`${constant.pythonBaseURL}/python/ai/chat/getConfig`);
      if (response.ok) {
        aiConfig = await response.json();
      }
    } catch (error) {
      console.warn('获取AI聊天配置失败:', error);
    }
    
    // 使用配置或默认值
    const chatName = aiConfig?.chat_name || 'AI助手';
    const welcomeMessage = aiConfig?.welcome_message || '你好！我是你的AI助手，有什么可以帮助你的吗？';
    const themeColor = aiConfig?.theme_color || '#4facfe';
    const enableTypingIndicator = aiConfig?.enable_typing_indicator !== false;

    const chatContainer = document.getElementById('waifu-chat');
    
    // 如果聊天窗口不存在，先初始化
    if (!chatContainer) {
      await initAIChat();
      // 等待DOM更新后再显示
      setTimeout(() => {
        const newChatContainer = document.getElementById('waifu-chat');
        if (newChatContainer) {
          newChatContainer.style.display = 'flex';
          // 聚焦到输入框
          const chatInput = document.getElementById('chat-input');
          if (chatInput) {
            setTimeout(() => chatInput.focus(), 100);
          }
          // 确保主题监听器被初始化
          startThemeObserver();
          
          // 滚动到底部
          setTimeout(() => {
            const messagesContainer = document.getElementById('chat-messages');
            if (messagesContainer) {
              messagesContainer.scrollTop = messagesContainer.scrollHeight;
            }
          }, 200);
        }
      }, 100);
    } else {
      // 切换显示状态
      if (chatContainer.style.display === 'none' || chatContainer.style.display === '') {
        chatContainer.style.display = 'flex';
        // 聚焦到输入框
        const chatInput = document.getElementById('chat-input');
        if (chatInput) {
          setTimeout(() => chatInput.focus(), 100);
        }
        showMessage("来聊天吧！我很想听听你的想法～", 3000, 8);
        // 确保主题监听器被初始化
        if (!chatContainer.hasAttribute('data-theme-observer')) {
          startThemeObserver();
          chatContainer.setAttribute('data-theme-observer', 'true');
        }
        
        // 滚动到底部
        setTimeout(() => {
          const messagesContainer = document.getElementById('chat-messages');
          if (messagesContainer) {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
          }
        }, 100);
      } else {
        chatContainer.style.display = 'none';
        showMessage("聊天窗口已关闭，想聊天随时点我哦！", 3000, 8);
      }
    }
  }

  // 显示消息
  function showMessage(text, timeout, priority) {
    if (!text || (sessionStorage.getItem("waifu-text") && sessionStorage.getItem("waifu-text") > priority)) return;
    if (messageTimer) {
      clearTimeout(messageTimer);
      messageTimer = null;
    }
    text = randomSelection(text);
    sessionStorage.setItem("waifu-text", priority);
    const tips = document.getElementById("waifu-tips");
    
    if (tips) {
    tips.innerHTML = text;
    tips.classList.add("waifu-tips-active");
      
      // 确保文字可以被选择和复制
      tips.style.userSelect = 'text';
      tips.style.webkitUserSelect = 'text';
      tips.style.mozUserSelect = 'text';
      tips.style.msUserSelect = 'text';
      tips.style.webkitTouchCallout = 'default';
      tips.style.webkitUserModify = 'read-only';
      tips.style.pointerEvents = 'auto';
      tips.style.cursor = 'text';
    }
    
    messageTimer = setTimeout(() => {
      sessionStorage.removeItem("waifu-text");
      if (tips) {
      tips.classList.remove("waifu-tips-active");
      }
    }, timeout);
  }

  // 添加一个检查live2d库是否加载成功的函数
  function checkLive2dLoaded() {
    return typeof window.loadlive2d === 'function';
  }

  // 添加一个确保live2d库加载的函数
  async function ensureLive2dLoaded(maxRetries = 3) {
    if (checkLive2dLoaded()) {
      return true;
    }
    
    // 尝试重新加载
    let retries = 0;
    while (retries < maxRetries) {
      console.log(`尝试加载live2d库 (尝试 ${retries + 1}/${maxRetries})`);
      try {
        await loadExternalResource(live2d_path + "live2d.min.js", "js");
        
        // 等待加载完成
        await new Promise(resolve => setTimeout(resolve, 500));
        
        if (checkLive2dLoaded()) {
          console.log('live2d库加载成功');
          return true;
        }
      } catch (error) {
        console.warn('live2d库加载失败:', error);
      }
      retries++;
    }
    
    console.error('无法加载live2d库');
    return false;
  }

  // 初始化聊天主题
  function initChatTheme() {
    const chatContainer = document.getElementById('waifu-chat');
    if (!chatContainer) return;

    // 获取保存的主题偏好，如果没有则根据网站主题判断
    let savedTheme = localStorage.getItem('waifu-chat-theme');
    
    if (!savedTheme) {
      // 检测网站当前主题
      const root = document.querySelector(":root");
      const currentBg = getComputedStyle(root).getPropertyValue('--background').trim();
      savedTheme = (currentBg === '#272727' || currentBg === 'rgb(39, 39, 39)') ? 'dark' : 'light';
    }

    applyChatTheme(savedTheme);
    
    // 监听网站主题变化
    startThemeObserver();
  }

  // 监听网站主题变化
  function startThemeObserver() {
    // 创建一个 MutationObserver 来监听 :root 样式变化
    const observer = new MutationObserver(() => {
      const root = document.querySelector(":root");
      const currentBg = getComputedStyle(root).getPropertyValue('--background').trim();
      const websiteTheme = (currentBg === '#272727' || currentBg === 'rgb(39, 39, 39)') ? 'dark' : 'light';
      
      // 只有当网站主题与聊天框主题不一致时才同步
      const chatContainer = document.getElementById('waifu-chat');
      if (chatContainer) {
        const currentChatTheme = chatContainer.classList.contains('dark-mode') ? 'dark' : 'light';
        if (currentChatTheme !== websiteTheme) {
          applyChatTheme(websiteTheme);
          localStorage.setItem('waifu-chat-theme', websiteTheme);
        }
      }
    });

    // 监听 :root 元素的样式属性变化
    const rootElement = document.querySelector(":root");
    if (rootElement) {
      observer.observe(rootElement, {
        attributes: true,
        attributeFilter: ['style']
      });
    }

    // 也监听整个 document 的样式变化，以防主题是通过其他方式切换的
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['style', 'class']
    });

    // 定期检查主题变化（备用方案）
    setInterval(() => {
      const root = document.querySelector(":root");
      const currentBg = getComputedStyle(root).getPropertyValue('--background').trim();
      const websiteTheme = (currentBg === '#272727' || currentBg === 'rgb(39, 39, 39)') ? 'dark' : 'light';
      
      const chatContainer = document.getElementById('waifu-chat');
      if (chatContainer) {
        const currentChatTheme = chatContainer.classList.contains('dark-mode') ? 'dark' : 'light';
        if (currentChatTheme !== websiteTheme) {
          applyChatTheme(websiteTheme);
          localStorage.setItem('waifu-chat-theme', websiteTheme);
        }
      }
    }, 1000); // 每秒检查一次
  }

  // 应用聊天主题
  function applyChatTheme(theme) {
    const chatContainer = document.getElementById('waifu-chat');
    
    if (!chatContainer) return;

    if (theme === 'dark') {
      chatContainer.classList.add('dark-mode');
    } else {
      chatContainer.classList.remove('dark-mode');
    }
  }

  // 添加消息到聊天窗口
  function addMessageToChat(message) {
    const chatContainer = document.getElementById('waifu-chat');
    if (!chatContainer) return;

    const messageHTML = `
      <div class="message user-message">
        <div class="message-content">${message}</div>
        <div class="message-time">${new Date().toLocaleTimeString()}</div>
      </div>
    `;

    chatContainer.insertAdjacentHTML('beforeend', messageHTML);
    chatContainer.scrollTop = chatContainer.scrollHeight;
  }

  // 数学公式渲染函数
  function renderMathFormulas(html) {
    if (!html || typeof window.katex === 'undefined') {
      return html;
    }
    
    try {
      // 渲染块级数学公式 $$...$$
      html = html.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
        try {
          // 清理公式中的HTML标签，特别是<br>标签
          const cleanFormula = formula
            .replace(/<br\s*\/?>/gi, '\n')  // 将<br>转回换行
            .replace(/<[^>]*>/g, '')        // 移除其他HTML标签
            .trim();
          
          const rendered = window.katex.renderToString(cleanFormula, {
            displayMode: true,
            throwOnError: false
          });
          return `<div class="math-block">${rendered}</div>`;
        } catch (e) {
          console.warn('块级数学公式渲染失败:', e);
          return match;
        }
      });
      
      // 渲染行内数学公式 $...$
      html = html.replace(/\$([^$\n]+?)\$/g, (match, formula) => {
        try {
          // 清理公式中的HTML标签
          const cleanFormula = formula
            .replace(/<br\s*\/?>/gi, ' ')   // 将<br>转为空格
            .replace(/<[^>]*>/g, '')        // 移除其他HTML标签
            .trim();
          
          const rendered = window.katex.renderToString(cleanFormula, {
            displayMode: false,
            throwOnError: false
          });
          return `<span class="math-inline">${rendered}</span>`;
        } catch (e) {
          console.warn('行内数学公式渲染失败:', e);
          return match;
        }
      });
      
      return html;
    } catch (error) {
      console.warn('数学公式渲染失败:', error);
      return html;
    }
  }

  // 调用后端AI传统API（保留原有功能）
  async function callBackendAI(message) {
    try {
      // 获取聊天历史
      const userId = getCurrentUserId();
      const chatKey = `waifu-chat-history-${userId}`;
      const chatHistory = JSON.parse(localStorage.getItem(chatKey) || '[]');
      
      // 使用固定的conversationId，确保对话连续性
      const conversationId = `waifu_chat_${userId}`;
      
      // 准备聊天历史上下文（最近10条消息）
      const recentHistory = chatHistory.slice(-10).map(msg => ({
        role: msg.type === 'user' ? 'user' : 'assistant',
        content: msg.content
      }));
      
      // 检测是否需要页面内容
      const needsPageContent = detectPageContentNeed(message);
      let enhancedMessage = message;
      
      if (needsPageContent) {
        const pageContent = extractPageContent();
        
        // 为可能支持MCP/网页访问的模型提供URL
        const webAccessHint = `
        
[网页访问提示]
如果你支持直接访问网页，可以访问：${window.location.href}
以下是我提取的页面信息作为备用：`;
        
        enhancedMessage = `${message}${webAccessHint}\n\n[页面上下文信息]\n${pageContent}`;
      }
      
      const apiUrl = `${constant.pythonBaseURL}/python/ai/chat/sendMessage`;
      
      const response = await fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          message: enhancedMessage,
          conversationId: conversationId,
          history: recentHistory,  // 发送聊天历史上下文
          context: {
            userId: userId,
            timestamp: new Date().toISOString(),
            platform: '看板娘聊天',
            pageUrl: window.location.href,
            pageTitle: document.title,
            hasPageContent: needsPageContent
          }
        })
      });
      
      const result = await response.json();
      
      // 修复：正确检查后端API的响应格式
      if (result.flag && result.code === 200 && result.data && result.data.response) {
        return {
          success: true,
          response: result.data.response
        };
      } else {
        console.log('后端AI返回错误:', result.message || 'Unknown error');
        return { success: false, error: result.message || 'Unknown error' };
      }
    } catch (error) {
      console.error('调用后端AI失败:', error);
      return { success: false, error: error.message };
    }
  }

  // 主题保存和恢复功能
  function saveThemeToStorage(theme) {
    try {
      localStorage.setItem('poetize-theme', theme);
      console.log(`主题已保存到localStorage: ${theme}`);
    } catch (error) {
      console.error('保存主题到localStorage失败:', error);
    }
  }

  function loadThemeFromStorage() {
    try {
      const savedTheme = localStorage.getItem('poetize-theme');
      if (savedTheme) {
        console.log(`从localStorage加载主题: ${savedTheme}`);
        applyTheme(savedTheme);
        return savedTheme;
      }
    } catch (error) {
      console.error('从localStorage加载主题失败:', error);
    }
    return null;
  }

  function applyTheme(theme) {
    const root = document.querySelector(":root");
    
    if (theme === 'dark') {
      // 应用深色主题
      root.style.setProperty("--background", "#272727");
      root.style.setProperty("--fontColor", "white");
      root.style.setProperty("--borderColor", "#4F4F4F");
      root.style.setProperty("--borderHoverColor", "black");
      root.style.setProperty("--articleFontColor", "#E4E4E4");
      root.style.setProperty("--articleGreyFontColor", "#D4D4D4");
      root.style.setProperty("--commentContent", "#D4D4D4");
      root.style.setProperty("--favoriteBg", "#1e1e1e");
    } else {
      // 应用浅色主题
      root.style.setProperty("--background", "white");
      root.style.setProperty("--fontColor", "black");
      root.style.setProperty("--borderColor", "rgba(0, 0, 0, 0.5)");
      root.style.setProperty("--borderHoverColor", "rgba(110, 110, 110, 0.4)");
      root.style.setProperty("--articleFontColor", "#1F1F1F");
      root.style.setProperty("--articleGreyFontColor", "#616161");
      root.style.setProperty("--commentContent", "#F7F9FE");
      root.style.setProperty("--favoriteBg", "#f7f9fe");
    }
  }

  function getCurrentTheme() {
    const root = document.querySelector(":root");
    const currentBg = getComputedStyle(root).getPropertyValue('--background').trim();
    
    if (currentBg === '#272727' || currentBg === 'rgb(39, 39, 39)' || currentBg === 'rgba(39, 39, 39, 1)') {
      return 'dark';
    } else {
      return 'light';
    }
  }

  function toggleTheme() {
    const currentTheme = getCurrentTheme();
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    
    applyTheme(newTheme);
    saveThemeToStorage(newTheme);
    
    const resultMessage = newTheme === 'dark' 
      ? "🌙 已为你切换到深色主题！夜间模式更适合保护眼睛哦～"
      : "☀️ 已为你切换到浅色主题！明亮的界面让心情也变好了呢～";
    
    return resultMessage;
  }
}

// 监听检查事件
document.addEventListener('checkWaifu', function() {
  console.log('接收到检查看板娘事件');
  
  // 检查看板娘是否启用
  const checkEnabled = async () => {
    try {
      // 尝试从后端获取
      const response = await fetch(constant.pythonBaseURL + "/webInfo/getWaifuStatus");
      const result = await response.json();
      
      if (result.code === 200) {
        return result.data.enableWaifu === true;
      }
      
      // 回退到本地存储
      const webInfoStr = localStorage.getItem('webInfo');
      if (webInfoStr) {
        const webInfo = JSON.parse(webInfoStr);
        if (webInfo.data && webInfo.data.enableWaifu !== undefined) {
          return webInfo.data.enableWaifu === true;
        } else if (webInfo.enableWaifu !== undefined) {
          return webInfo.enableWaifu === true;
        }
      }
      
      return false;
    } catch (e) {
      console.warn('检查看板娘启用状态失败:', e);
      return false;
    }
  };
  
  // 检查看板娘是否加载
  const checkLoaded = () => {
    return (
      typeof window.loadlive2d === 'function' && 
      document.getElementById('waifu') && 
      document.getElementById('live2d')
    );
  };
  
  // 检查看板娘是否可见
  const checkVisible = () => {
    const waifu = document.getElementById('waifu');
    return waifu && 
           waifu.style.display !== 'none' && 
           waifu.style.bottom !== '-500px';
  };
  
  // 初始化
  setTimeout(async () => {
    // 检查是否应该启用看板娘
    const enabled = await checkEnabled();
    if (!enabled) {
      console.log('看板娘功能已禁用，不恢复');
      return;
    }
    
    // 检查是否已显示
    if (checkLoaded() && checkVisible()) {
      console.log('看板娘已经正常加载和显示');
      return;
    }
    
    // 需要恢复
    console.log('需要恢复看板娘');
    
    // 如果已加载过看板娘但不显示，尝试恢复
    if (checkLoaded() && !checkVisible()) {
      const waifu = document.getElementById('waifu');
      if (waifu) {
        waifu.style.display = '';
        waifu.style.bottom = '0';
        console.log('已恢复看板娘显示');
        return;
      }
    }
    
    // 如果没有加载，重新初始化
    if (!localStorage.getItem("waifu-display")) {
      console.log('重新初始化看板娘');
      localStorage.removeItem("waifu-display");
      // 尝试重新初始化
      initWidget({
        waifuPath: constant.baseURL + constant.waifuPath,
        cdnPath: constant.cdnPath
      });
    }
  }, 1000);
});

// 添加全局函数，用于重置看板娘状态
window.resetWaifuState = function() {
  const waifuElement = document.getElementById("waifu");
  if (waifuElement) {
    waifuElement.classList.remove("dragging");
    console.log("看板娘状态已重置");
  }
};

// 添加键盘快捷键，按R键重置看板娘状态
document.addEventListener('keydown', (e) => {
  if (e.key === 'r' || e.key === 'R') {
    if (e.ctrlKey || e.metaKey) {
      // Ctrl+R 或 Cmd+R 不拦截，让页面正常刷新
      return;
    }
    
    const activeElement = document.activeElement;
    // 如果当前不是在输入框中，才执行重置
    if (activeElement.tagName !== 'INPUT' && 
        activeElement.tagName !== 'TEXTAREA' && 
        !activeElement.isContentEditable) {
      e.preventDefault();
      window.resetWaifuState();
    }
  }
});

// 页面初始化时恢复主题（即使看板娘功能未启用）
document.addEventListener('DOMContentLoaded', function() {
  console.log('页面加载完成，正在恢复主题设置...');
  loadThemeFromStorage();
});

// 如果DOM已经加载完成，立即恢复主题
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', function() {
    console.log('页面加载完成，正在恢复主题设置...');
    loadThemeFromStorage();
  });
} else {
  console.log('页面已加载，立即恢复主题设置...');
  loadThemeFromStorage();
}
