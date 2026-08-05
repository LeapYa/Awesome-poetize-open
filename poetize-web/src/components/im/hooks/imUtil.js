import { useMainStore } from '../../../stores/main'; import { useImStore } from '../../../stores/im';
import { confirm as ElMessageBoxConfirm, prompt as ElMessageBoxPrompt } from '@/utils/element-command-services';
const ElMessageBox = { confirm: ElMessageBoxConfirm, prompt: ElMessageBoxPrompt };
import { nextTick } from 'vue';
import { message as ElMessage } from '@/utils/element-command-services';
import { reactive, getCurrentInstance, onMounted, onBeforeUnmount, watchEffect, toRefs } from 'vue';
export default function () {
  const globalProperties = getCurrentInstance().appContext.config.globalProperties;
  const $common = globalProperties.$common;
  const $http = globalProperties.$http;
  const $constant = globalProperties.$constant;
  const mainStore = useMainStore(); const imStore = useImStore(); const store = { state: { get currentUser() { return mainStore.currentUser; }, get sysConfig() { return mainStore.sysConfig; } }, commit(m, p) { if (m === 'loadCurrentUser') mainStore.loadCurrentUser(p); else if (m === 'loadSysConfig') mainStore.loadSysConfig(p); } };

  const isMobile = () => {
    return $common.mobile() || window.innerWidth <= 850;
  }

  let imUtilData = reactive({
    //系统消息
    systemMessages: [],
    showBodyLeft: true,
    //表情包
    imageList: [],
    // 存储事件监听器引用，避免重复绑定
    bodyRightClickHandler: null
  })
  // 状态持久化相关函数 - 与后端Redis同步
  function saveShowBodyLeftState() {
    if (isMobile()) {
      const currentUser = mainStore.currentUser;
      if (currentUser && currentUser.id) {
        // 异步保存到后端Redis，不阻塞UI
        $http.post($constant.baseURL + "/im/saveUIState", {
          showBodyLeft: imUtilData.showBodyLeft
        }).catch(error => {
          console.warn('[UI状态同步] 保存失败:', error);
        });
      }
    }
  }
  async function loadShowBodyLeftState() {
    if (isMobile()) {
      const currentUser = mainStore.currentUser;
      if (currentUser && currentUser.id) {
        try {
          const res = await $http.get($constant.baseURL + "/im/getUIState");
          if (res.data && res.data.showBodyLeft !== undefined) {
            imUtilData.showBodyLeft = res.data.showBodyLeft;
            console.log('[UI状态同步] 加载状态:', imUtilData.showBodyLeft);
          }
        } catch (error) {
          console.warn('[UI状态同步] 加载失败:', error);
          // 降级到默认状态
          imUtilData.showBodyLeft = true;
        }
      }
    }
  }
  onMounted(async () => {
    if ($common.mobile()) {
      // 异步加载保存的状态
      await loadShowBodyLeftState();
      const friendAside = document.querySelector(".friend-aside");
      if (friendAside) {
        // 确保不重复绑定
        friendAside.removeEventListener('click', handleFriendAsideClick);
        friendAside.addEventListener('click', handleFriendAsideClick);
      }
      const bodyRight = document.querySelector(".body-right");
      if (bodyRight) {
        // 确保不重复绑定
        bodyRight.removeEventListener('click', handleInitialBodyRightClick);
        bodyRight.addEventListener('click', handleInitialBodyRightClick);
      }
    }
    // 应用加载的状态，跳过动画避免页面刷新时的闪烁
    mobileRight(true);
  })
  onBeforeUnmount(() => {
    if ($common.mobile()) {
      // 清理事件监听器
      const friendAside = document.querySelector(".friend-aside");
      if (friendAside) {
        friendAside.removeEventListener('click', handleFriendAsideClick);
      }
      const bodyRight = document.querySelector(".body-right");
      if (bodyRight) {
        bodyRight.removeEventListener('click', handleInitialBodyRightClick);
      }
      // 清理动态添加的事件监听器
      if (imUtilData.bodyRightClickHandler) {
        const bodyRightElements = document.querySelectorAll('.body-right');
        bodyRightElements.forEach(element => {
          element.removeEventListener('click', imUtilData.bodyRightClickHandler);
        });
      }
    }
  })
  function handleFriendAsideClick() {
    imUtilData.showBodyLeft = true;
    mobileRight();
  }
  function handleInitialBodyRightClick() {
    imUtilData.showBodyLeft = false;
    mobileRight();
  }
  function changeAside() {
    imUtilData.showBodyLeft = !imUtilData.showBodyLeft;
    console.log('[移动端] changeAside 调用 - showBodyLeft:', imUtilData.showBodyLeft);
    mobileRight();
    // 同步状态到服务器
    saveShowBodyLeftState();
  }
  function mobileRight(skipAnimation = false) {
    if (isMobile()) {
      const bodyLeft = document.querySelector(".body-left");
      const bodyRight = document.querySelector(".body-right");
      console.log(`[移动端] mobileRight 调用 - showBodyLeft: ${imUtilData.showBodyLeft}, skipAnimation: ${skipAnimation}`);

      if (imUtilData.showBodyLeft) {
        // 显示左侧面板（聊天列表）
        if (bodyLeft) {
          bodyLeft.classList.remove("hidden");
        }
        if (bodyRight) {
          bodyRight.classList.remove("full-width", "mobile-right");
        }
        console.log('[移动端] 显示左侧面板（聊天列表）');
      } else {
        // 隐藏左侧面板，显示右侧内容
        if (bodyLeft) {
          bodyLeft.classList.add("hidden");
        }
        if (bodyRight) {
          bodyRight.classList.add("full-width");
          bodyRight.classList.remove("mobile-right");
        }
        console.log('[移动端] 隐藏左侧面板，显示右侧内容');
      }
      // 保存状态到后端Redis
      saveShowBodyLeftState();
    }
  }
  // 确保侧边栏按钮状态正确更新
  function updateAsideActiveState(targetType) {
    // 清除所有侧边栏按钮的激活状态
    document.querySelectorAll('.friend-chat').forEach(btn => {
      btn.classList.remove('aside-active');
    });
    // 根据类型设置对应按钮为激活状态
    const buttons = document.querySelectorAll('.friend-chat');
    if (buttons.length >= 3) {
      if (targetType === 1) {
        buttons[0].classList.add('aside-active'); // 聊天按钮
      } else if (targetType === 2) {
        buttons[1].classList.add('aside-active'); // 好友按钮
      } else if (targetType === 3) {
        buttons[2].classList.add('aside-active'); // 群聊按钮
      }
    }
  }
  function getSystemMessages() {
    $http.get($constant.baseURL + "/imChatUserMessage/listSystemMessage")
      .then((res) => {
        if (!$common.isEmpty(res.data) && !$common.isEmpty(res.data.records)) {
          imUtilData.systemMessages = res.data.records;

          // 计算未读数
          const lastId = parseInt(localStorage.getItem('lastSystemMessageId') || '0');
          let unreadCount = 0;
          res.data.records.forEach(msg => {
            if (msg.id > lastId) {
              unreadCount++;
            }
          });
          if (unreadCount > 0) {
            imStore.updateSystemMessageBadge(unreadCount);
          }
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }
  function hiddenBodyLeft() {
    if (isMobile()) {
      const bodyRightElements = document.querySelectorAll('.body-right');
      // 如果已经有处理器，先移除
      if (imUtilData.bodyRightClickHandler) {
        bodyRightElements.forEach(element => {
          element.removeEventListener('click', imUtilData.bodyRightClickHandler);
        });
      }
      // 创建新的处理器
      imUtilData.bodyRightClickHandler = function (event) {
        // 检查点击的是否是返回按钮或其子元素
        const isBackButton = event.target.closest('.mobile-back-btn');
        // 检查点击的是否是聊天输入区域或消息区域
        const isChatArea = event.target.closest('.chat-container, .msg-container, .chat-input');
        console.log('[移动端] bodyRight 点击事件:', {
          isBackButton,
          isChatArea,
          showBodyLeft: imUtilData.showBodyLeft,
          targetClass: event.target.className
        });
        // 只有在显示左侧面板且不是点击返回按钮且不是聊天区域时才处理点击
        if (imUtilData.showBodyLeft && !isBackButton && !isChatArea) {
          console.log('[移动端] 隐藏左侧面板');
          imUtilData.showBodyLeft = false;
          mobileRight();
          saveShowBodyLeftState();
        }
      };
      // 添加事件监听器
      bodyRightElements.forEach(element => {
        element.addEventListener('click', imUtilData.bodyRightClickHandler);
      });
    }
  }
  function imgShow() {
    // 使用原生JavaScript替代jQuery
    const messageImages = document.querySelectorAll('.message img');
    messageImages.forEach(img => {
      // 移除之前的事件监听器，避免重复绑定
      img.removeEventListener('click', handleImageClick);
      // 添加新的事件监听器
      img.addEventListener('click', handleImageClick);
    });
  }
  function handleImageClick(event) {
    const src = event.target.getAttribute('src');
    const bigImg = document.getElementById('bigImg');
    if (bigImg) {
      bigImg.setAttribute('src', src);
    }
    // 获取当前点击图片的真实大小，并显示弹出层及大图
    const tempImg = new Image();
    tempImg.onload = function () {
      const windowW = window.innerWidth; // 获取当前窗口宽度
      const windowH = window.innerHeight; // 获取当前窗口高度
      const realWidth = this.width; // 获取图片真实宽度
      const realHeight = this.height; // 获取图片真实高度
      let imgWidth, imgHeight;
      const scale = 0.8; // 缩放尺寸
      if (realHeight > windowH * scale) {
        imgHeight = windowH * scale;
        imgWidth = imgHeight / realHeight * realWidth;
        if (imgWidth > windowW * scale) {
          imgWidth = windowW * scale;
        }
      } else if (realWidth > windowW * scale) {
        imgWidth = windowW * scale;
        imgHeight = imgWidth / realWidth * realHeight;
      } else {
        imgWidth = realWidth;
        imgHeight = realHeight;
      }
      if (bigImg) {
        bigImg.style.width = imgWidth + 'px';
      }
      const w = (windowW - imgWidth) / 2;
      const h = (windowH - imgHeight) / 2;
      const innerImg = document.getElementById('innerImg');
      if (innerImg) {
        innerImg.style.top = h + 'px';
        innerImg.style.left = w + 'px';
      }
      const outerImg = document.getElementById('outerImg');
      if (outerImg) {
        outerImg.style.display = 'block';
        outerImg.style.opacity = '1';
      }
    };
    tempImg.src = src;
    // 点击外层关闭图片预览
    const outerImg = document.getElementById('outerImg');
    if (outerImg) {
      outerImg.removeEventListener('click', handleOuterImgClick);
      outerImg.addEventListener('click', handleOuterImgClick);
    }
  }
  function handleOuterImgClick(event) {
    const outerImg = event.currentTarget;
    outerImg.style.display = 'none';
    outerImg.style.opacity = '0';
  }
  function getImageList() {
    $http.get($constant.baseURL + "/resource/getImageList")
      .then((res) => {
        if (!$common.isEmpty(res.data)) {
          imUtilData.imageList = res.data;
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }
  function parseMessage(content) {
    // 检查 content 是否存在，如果不存在返回空字符串
    if (!content || typeof content !== 'string') {
      console.warn('parseMessage: content is undefined or not a string', content);
      return '';
    }
    // 消息内容统一保持原始文本（含 \n、[表情]、[名称,图片] token），
    // 由 CommentMarkdownRenderer 安全渲染，禁止在此生成 HTML（防 XSS）
    return content;
  }
  function markSystemMessagesAsRead() {
    if (!$common.isEmpty(imUtilData.systemMessages)) {
      const maxId = Math.max(...imUtilData.systemMessages.map(m => m.id || 0));
      localStorage.setItem('lastSystemMessageId', maxId.toString());
      imStore.resetSystemMessageBadge();
    }
  }
  return {
    imUtilData,
    changeAside,
    mobileRight,
    getSystemMessages,
    markSystemMessagesAsRead,
    hiddenBodyLeft,
    imgShow,
    getImageList,
    parseMessage,
    updateAsideActiveState,
    saveShowBodyLeftState,
    loadShowBodyLeftState,
    isMobile
  }
}
