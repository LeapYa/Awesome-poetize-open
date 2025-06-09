import {useStore} from 'vuex';

import {useDialog} from 'naive-ui';

import {nextTick} from 'vue';

import {ElMessage} from "element-plus";

import {reactive, getCurrentInstance, onMounted, onBeforeUnmount, watchEffect, toRefs} from 'vue';

export default function () {
  const globalProperties = getCurrentInstance().appContext.config.globalProperties;
  const $common = globalProperties.$common;
  const $http = globalProperties.$http;
  const $constant = globalProperties.$constant;
  const store = useStore();
  const dialog = useDialog();

  let friendData = reactive({
    //好友请求
    friendRequests: [],
    //好友列表
    friends: {},
    //当前朋友信息
    currentFriendId: null
  })

  async function getImFriend() {
    await $http.get($constant.baseURL + "/imChatUserFriend/getFriend", {friendStatus: 1})
      .then((res) => {
        if (!$common.isEmpty(res.data)) {
          res.data.forEach(friend => {
            friendData.friends[friend.friendId] = friend;
          });
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  function removeFriend(currentFriendId) {
    dialog.error({
      title: '警告',
      content: '你确定删除' + friendData.friends[currentFriendId].remark + '?',
      positiveText: '确定',
      onPositiveClick: () => {
        $http.get($constant.baseURL + "/imChatUserFriend/changeFriend", {
          friendId: currentFriendId,
          friendStatus: -1
        })
          .then((res) => {
            delete friendData.friends[currentFriendId];
            friendData.currentFriendId = null;
            ElMessage({
              message: "删除成功！",
              type: 'success'
            });
          })
          .catch((error) => {
            ElMessage({
              message: error.message,
              type: 'error'
            });
          });
      }
    });
  }

  function getFriendRequests() {
    $http.get($constant.baseURL + "/imChatUserFriend/getFriend", {friendStatus: 0})
      .then((res) => {
        if (!$common.isEmpty(res.data)) {
          friendData.friendRequests = res.data;
          ElMessage({
            message: "您有好友申请待处理！",
            showClose: true,
            type: 'success',
            duration: 0
          });
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  function changeFriendStatus(friendId, status, index) {
    $http.get($constant.baseURL + "/imChatUserFriend/changeFriend", {friendId: friendId, friendStatus: status})
      .then((res) => {
        friendData.friendRequests.splice(index, 1);
        ElMessage({
          message: "修改成功！",
          type: 'success'
        });
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  return {
    friendData,
    getImFriend,
    removeFriend,
    getFriendRequests,
    changeFriendStatus
  }
}
