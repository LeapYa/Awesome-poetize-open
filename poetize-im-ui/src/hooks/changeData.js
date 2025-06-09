import {useStore} from 'vuex';

import {useDialog} from 'naive-ui';

import {nextTick} from 'vue';

import {ElMessage} from "element-plus";

import {reactive, getCurrentInstance, onMounted, onBeforeUnmount, watchEffect, toRefs} from 'vue';

export default function (friendData, groupData) {
  const globalProperties = getCurrentInstance().appContext.config.globalProperties;
  const $common = globalProperties.$common;
  const $http = globalProperties.$http;
  const $constant = globalProperties.$constant;
  const store = useStore();
  const dialog = useDialog();

  let changeDataData = reactive({
    //修改信息
    changeData: '',
    changeType: null,
    changeModal: false,

    avatarType: null,
    avatarPrefix: '',
    showAvatarDialog: false
  })

  function closeModal() {
    changeDataData.avatarType = null;
    changeDataData.avatarPrefix = '';

    changeDataData.changeData = '';
    changeDataData.changeType = null;
  }

  function changeAvatar(type) {
    if (type === 1 || (type === 2 && groupData.groups[groupData.currentGroupId].masterFlag)) {
      closeModal();
      changeDataData.showAvatarDialog = true;
      changeDataData.avatarType = type;
      if (type === 1) {
        changeDataData.avatarPrefix = 'userAvatar';
      } else if (type === 2) {
        changeDataData.avatarPrefix = 'im/groupAvatar';
      }
    }
  }

  function changeDataType(type) {
    closeModal();
    changeDataData.changeType = type;
    changeDataData.changeModal = true;
  }

  function submitAvatar(avatar) {
    if ($common.isEmpty(avatar)) {
      ElMessage({
        message: "请上传头像！",
        type: 'warning'
      });
      return;
    }
    if (changeDataData.avatarType === 1) {
      let user = {
        avatar: avatar
      };
      $http.post($constant.baseURL + "/user/updateUserInfo", user)
        .then((res) => {
          if (!$common.isEmpty(res.data)) {
            ElMessage({
              message: "修改成功！",
              type: 'success'
            });
            store.commit("loadCurrentUser", res.data);
            closeModal();
            changeDataData.showAvatarDialog = false;
          }
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    } else if (changeDataData.avatarType === 2) {
      $http.post($constant.baseURL + "/imChatGroup/updateGroup", {
        id: groupData.currentGroupId,
        avatar: avatar
      })
        .then((res) => {
          ElMessage({
            message: "修改成功！",
            type: 'success'
          });
          groupData.groups[groupData.currentGroupId].avatar = avatar;
          closeModal();
          changeDataData.showAvatarDialog = false;
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    }
  }

  function submitChange() {
    if (changeDataData.changeType === 1) {
      if ($common.isEmpty(changeDataData.changeData)) {
        ElMessage({
          message: "请输入备注！",
          type: 'warning'
        });
        return;
      }
      $http.get($constant.baseURL + "/imChatUserFriend/changeFriend", {
        friendId: friendData.currentFriendId,
        remark: changeDataData.changeData
      })
        .then((res) => {
          ElMessage({
            message: "修改成功！",
            type: 'success'
          });
          friendData.friends[friendData.currentFriendId].remark = changeDataData.changeData;
          closeModal();
          changeDataData.changeModal = false;
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    } else if (changeDataData.changeType === 2) {
      if ($common.isEmpty(changeDataData.changeData)) {
        ElMessage({
          message: "请输入群名称！",
          type: 'warning'
        });
        return;
      }
      $http.post($constant.baseURL + "/imChatGroup/updateGroup", {
        id: groupData.currentGroupId,
        groupName: changeDataData.changeData
      })
        .then((res) => {
          ElMessage({
            message: "修改成功！",
            type: 'success'
          });
          groupData.groups[groupData.currentGroupId].groupName = changeDataData.changeData;
          closeModal();
          changeDataData.changeModal = false;
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    } else if (changeDataData.changeType === 3) {
      if ($common.isEmpty(changeDataData.changeData)) {
        ElMessage({
          message: "请输入群公告！",
          type: 'warning'
        });
        return;
      }
      $http.post($constant.baseURL + "/imChatGroup/updateGroup", {
        id: groupData.currentGroupId,
        notice: changeDataData.changeData
      })
        .then((res) => {
          ElMessage({
            message: "修改成功！",
            type: 'success'
          });
          groupData.groups[groupData.currentGroupId].notice = changeDataData.changeData;
          closeModal();
          changeDataData.changeModal = false;
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    } else if (changeDataData.changeType === 4) {
      if ($common.isEmpty(changeDataData.changeData)) {
        ElMessage({
          message: "请输入群简介！",
          type: 'warning'
        });
        return;
      }
      $http.post($constant.baseURL + "/imChatGroup/updateGroup", {
        id: groupData.currentGroupId,
        introduction: changeDataData.changeData
      })
        .then((res) => {
          ElMessage({
            message: "修改成功！",
            type: 'success'
          });
          groupData.groups[groupData.currentGroupId].introduction = changeDataData.changeData;
          closeModal();
          changeDataData.changeModal = false;
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
    }
  }

  return {
    changeDataData,
    changeAvatar,
    changeDataType,
    submitAvatar,
    submitChange
  }
}
