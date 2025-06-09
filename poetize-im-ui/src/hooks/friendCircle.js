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
    dialog.error({
      title: '警告',
      content: '确定删除?',
      positiveText: '确定',
      onPositiveClick: () => {
        $http.get($constant.baseURL + "/weiYan/deleteWeiYan", {id: id})
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
      }
    });
  }

  function getWeiYan() {
    $http.post($constant.baseURL + "/weiYan/listWeiYan", friendCircleData.pagination)
      .then((res) => {
        if (!$common.isEmpty(res.data)) {
          res.data.records.forEach(c => {
            c.content = c.content.replace(/\n{2,}/g, '<div style="height: 12px"></div>');
            c.content = c.content.replace(/\n/g, '<br/>');
            c.content = $common.faceReg(c.content);
            c.content = $common.pictureReg(c.content);
          });
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
    dialog.success({
      title: '好友申请',
      content: '确认提交好友申请，添加 ' + friendCircleData.weiYanUsername + ' 为好友？',
      positiveText: '确定',
      onPositiveClick: () => {
        $http.get($constant.baseURL + "/imChatUserFriend/addFriend", {friendId: friendCircleData.pagination.userId})
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
      }
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
