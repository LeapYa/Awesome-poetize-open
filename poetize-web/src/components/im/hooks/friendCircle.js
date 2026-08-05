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

  let friendCircleData = reactive({
    showFriendCircle: false,
    treeHoleList: [],
    weiYanDialogVisible: false,
    isPublic: true,
    weiYanAvatar: '',
    weiYanUsername: '',
    pagination: {
      current: 1,
      size: 10,
      total: 0,
      userId: null
    }
  })
  function launch() {
    friendCircleData.weiYanDialogVisible = true;
  }
  function openFriendCircle(userId, avatar, username) {
    friendCircleData.pagination.userId = userId;
    friendCircleData.weiYanAvatar = avatar;
    friendCircleData.weiYanUsername = username;
    getWeiYan();
  }
  function deleteTreeHole(id) {
    ElMessageBox.confirm('确定删除?', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      $http.get($constant.baseURL + "/weiYan/deleteWeiYan", { id: id })
        .then((res) => {
          ElMessage({
            message: "删除成功！",
            type: 'success'
          });
          friendCircleData.pagination.current = 1;
          friendCircleData.pagination.size = 10;
          friendCircleData.treeHoleList = [];
          getWeiYan();
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    }).catch(() => {
      // 用户取消删除
    });
  }
  function getWeiYan() {
    $http.post($constant.baseURL + "/weiYan/listWeiYan", friendCircleData.pagination)
      .then((res) => {
        if (!$common.isEmpty(res.data)) {
          // 内容交由 CommentMarkdownRenderer 安全渲染（换行/表情/图片统一处理）
          friendCircleData.treeHoleList = friendCircleData.treeHoleList.concat(res.data.records);
          friendCircleData.pagination.total = res.data.total;
          friendCircleData.showFriendCircle = true;
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }
  function submitWeiYan(content) {
    // 树洞只允许一行内容，含换行或 <br> 等标签时拦截并提示
    const violation = $common.checkTreeHoleSingleLine(content);
    if (violation) {
      ElMessage({
        message: violation,
        type: 'warning'
      });
      return;
    }
    let weiYan = {
      content: content,
      isPublic: friendCircleData.isPublic
    };
    $http.post($constant.baseURL + "/weiYan/saveWeiYan", weiYan)
      .then((res) => {
        friendCircleData.pagination.current = 1;
        friendCircleData.pagination.size = 10;
        friendCircleData.treeHoleList = [];
        friendCircleData.weiYanDialogVisible = false;
        getWeiYan();
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }
  function cleanFriendCircle() {
    friendCircleData.pagination = {
      current: 1,
      size: 10,
      total: 0,
      userId: null
    };
    friendCircleData.weiYanAvatar = '';
    friendCircleData.weiYanUsername = '';
    friendCircleData.treeHoleList = [];
    friendCircleData.showFriendCircle = false;
  }
  function pageWeiYan() {
    friendCircleData.pagination.current = friendCircleData.pagination.current + 1;
    getWeiYan();
  }
  function addFriend() {
    ElMessageBox.confirm('确认提交好友申请，添加 ' + friendCircleData.weiYanUsername + ' 为好友？', '好友申请', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }).then(() => {
      $http.get($constant.baseURL + "/imChatUserFriend/addFriend", { friendId: friendCircleData.pagination.userId })
        .then((res) => {
          ElMessage({
            message: "提交成功！",
            type: 'success'
          });
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    }).catch(() => {
      // 用户取消
    });
  }
  return {
    friendCircleData,
    launch,
    openFriendCircle,
    deleteTreeHole,
    submitWeiYan,
    pageWeiYan,
    cleanFriendCircle,
    addFriend
  }
}
