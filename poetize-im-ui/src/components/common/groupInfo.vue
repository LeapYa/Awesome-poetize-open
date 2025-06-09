<template>
  <div>
    <!-- 群名称 -->
    <div style="height: 60px;background-color: var(--maxWhite)">
      <span style="line-height: 60px;margin-left: 20px;font-size: 18px">
        {{groups[currentGroupId].groupName}}
      </span>
    </div>

    <!-- 群信息 -->
    <div style="background: var(--midWhite);height: calc(100% - 60px)">
      <!-- 群头像 -->
      <div class="myCenter" style="padding: 50px 0">
        <n-avatar object-fit="cover"
                  :size="60"
                  lazy
                  :src="groups[currentGroupId].avatar"/>
      </div>

      <!-- 群信息 -->
      <div class="myCenter">
        <div style="width: 65%;font-size: 16px">
          <!-- 群名称 -->
          <div style="margin-bottom: 10px">
            <span class="friend-label">
              群名称
            </span>
            <span style="margin: 0 5px 0 0">
              {{groups[currentGroupId].groupName}}
            </span>
            <span @click="changeDataType(2)"
                  v-if="groups[currentGroupId].masterFlag"
                  style="display: inline-block;vertical-align: sub;cursor: pointer">
              <svg viewBox="0 0 1024 1024" width="20" height="20">
                <path
                  d="M929.909189 827.019236H93.990821c-16.598379 0-29.997071 13.398692-29.99707 29.997071s13.398692 29.997071 29.99707 29.997071h835.918368c16.598379 0 29.997071-13.398692 29.99707-29.997071 0-16.498389-13.398692-29.997071-29.99707-29.997071z"
                  fill="#FF6600" opacity=".502"></path>
                <path
                  d="M705.931061 198.080656c3.099697 0 8.999121 0.799922 14.098624 5.899424l28.297236 28.297237c5.099502 5.099502 5.899424 10.998926 5.899424 14.098623 0 3.099697-0.799922 8.999121-5.899424 14.098623L392.161703 616.739772l-86.991505 28.997168 27.597305-82.791915 358.964945-358.964945c5.099502-5.199492 11.098916-5.899424 14.198613-5.899424m0-59.994141c-20.497998 0-40.896006 7.799238-56.594473 23.397715L281.672493 529.148325l-0.699932-0.699931-70.693096 212.079289 212.079289-70.693097 0.699932 0.699932 367.664095-367.664095c31.196953-31.196953 31.196953-81.892003 0-113.088956l-28.297237-28.297237c-15.598477-15.598477-35.996485-23.397715-56.494483-23.397715z"
                  fill="#FF6600"></path>
                <path d="M578.626494 230.803461L621.049351 188.381603l141.40619 141.406191-42.421857 42.421857z"
                      fill="#FF6600"></path>
              </svg>
            </span>
          </div>

          <!-- 群公告 -->
          <div style="margin-bottom: 10px">
            <span class="friend-label">
              群公告
            </span>
            <span style="margin: 0 5px 0 0">
              {{groups[currentGroupId].notice}}
            </span>
            <span @click="changeDataType(3)"
                  v-if="groups[currentGroupId].masterFlag"
                  style="display: inline-block;vertical-align: sub;cursor: pointer">
              <svg viewBox="0 0 1024 1024" width="20" height="20">
                <path
                  d="M929.909189 827.019236H93.990821c-16.598379 0-29.997071 13.398692-29.99707 29.997071s13.398692 29.997071 29.99707 29.997071h835.918368c16.598379 0 29.997071-13.398692 29.99707-29.997071 0-16.498389-13.398692-29.997071-29.99707-29.997071z"
                  fill="#FF6600" opacity=".502"></path>
                <path
                  d="M705.931061 198.080656c3.099697 0 8.999121 0.799922 14.098624 5.899424l28.297236 28.297237c5.099502 5.099502 5.899424 10.998926 5.899424 14.098623 0 3.099697-0.799922 8.999121-5.899424 14.098623L392.161703 616.739772l-86.991505 28.997168 27.597305-82.791915 358.964945-358.964945c5.099502-5.199492 11.098916-5.899424 14.198613-5.899424m0-59.994141c-20.497998 0-40.896006 7.799238-56.594473 23.397715L281.672493 529.148325l-0.699932-0.699931-70.693096 212.079289 212.079289-70.693097 0.699932 0.699932 367.664095-367.664095c31.196953-31.196953 31.196953-81.892003 0-113.088956l-28.297237-28.297237c-15.598477-15.598477-35.996485-23.397715-56.494483-23.397715z"
                  fill="#FF6600"></path>
                <path d="M578.626494 230.803461L621.049351 188.381603l141.40619 141.406191-42.421857 42.421857z"
                      fill="#FF6600"></path>
              </svg>
            </span>
          </div>

          <!-- 群简介 -->
          <div style="margin-bottom: 40px">
            <span class="friend-label">群简介</span>
            <span style="margin: 0 5px 0 6px">
              {{groups[currentGroupId].introduction}}
            </span>
            <span @click="changeDataType(4)"
                  v-if="groups[currentGroupId].masterFlag"
                  style="display: inline-block;vertical-align: sub;cursor: pointer">
              <svg viewBox="0 0 1024 1024" width="20" height="20">
                <path
                  d="M929.909189 827.019236H93.990821c-16.598379 0-29.997071 13.398692-29.99707 29.997071s13.398692 29.997071 29.99707 29.997071h835.918368c16.598379 0 29.997071-13.398692 29.99707-29.997071 0-16.498389-13.398692-29.997071-29.99707-29.997071z"
                  fill="#FF6600" opacity=".502"></path>
                <path
                  d="M705.931061 198.080656c3.099697 0 8.999121 0.799922 14.098624 5.899424l28.297236 28.297237c5.099502 5.099502 5.899424 10.998926 5.899424 14.098623 0 3.099697-0.799922 8.999121-5.899424 14.098623L392.161703 616.739772l-86.991505 28.997168 27.597305-82.791915 358.964945-358.964945c5.099502-5.199492 11.098916-5.899424 14.198613-5.899424m0-59.994141c-20.497998 0-40.896006 7.799238-56.594473 23.397715L281.672493 529.148325l-0.699932-0.699931-70.693096 212.079289 212.079289-70.693097 0.699932 0.699932 367.664095-367.664095c31.196953-31.196953 31.196953-81.892003 0-113.088956l-28.297237-28.297237c-15.598477-15.598477-35.996485-23.397715-56.494483-23.397715z"
                  fill="#FF6600"></path>
                <path
                  d="M578.626494 230.803461L621.049351 188.381603l141.40619 141.406191-42.421857 42.421857z"
                  fill="#FF6600"></path>
              </svg>
            </span>
          </div>
        </div>
      </div>

      <!-- 群按钮 -->
      <div class="myCenter sendMsg">
        <n-button type="info" @click="sendGroupMessage()">
          发消息
        </n-button>
        <template
          v-if="groups[currentGroupId].groupType === 1 || (groups[currentGroupId].groupType === 2 && groups[currentGroupId].masterFlag)">
          &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
          <n-button type="primary" @click="groupSetting()">
            群设置
          </n-button>
        </template>
      </div>
    </div>

    <!-- 群设置 -->
    <n-drawer class="group-card"
              v-model:show="activeGroupSet"
              :width="300"
              placement="right"
              to="#body-group">
      <n-card style="margin-bottom: 20px">
        <n-tabs default-value="群设置" justify-content="space-evenly" type="line">
          <!-- 群设置 -->
          <n-tab-pane name="群设置" tab="群设置">
            <div>
              <div class="myCenter" style="margin: 15px 0">
                <n-avatar lazy object-fit="cover" @click="changeAvatar(2)" round class="group-avatar" :size="70"
                          :src="groups[currentGroupId].avatar"/>
              </div>

              <div class="group-set"
                   v-if="groups[currentGroupId].groupType === 1 && groups[currentGroupId].masterFlag">
                <div>
                  是否需要审核
                </div>
                <div>
                  <n-switch @update:value="updateInType(currentGroupId, !groups[currentGroupId].inType)"
                            v-model:value="groups[currentGroupId].inType"/>
                </div>
              </div>

              <div style="display: flex;justify-content: space-around;margin-top: 20px">
                <n-button type="warning" @click="exitGroup(currentGroupId)"
                          v-if="groups[currentGroupId].groupType === 1">
                  退出群
                </n-button>

                <template v-if="groups[currentGroupId].masterFlag">
                  <n-button type="error" @click="dissolveGroup(currentGroupId)">
                    解散群
                  </n-button>
                </template>
              </div>
            </div>
          </n-tab-pane>

          <!-- 成员设置 -->
          <n-tab-pane name="群成员" tab="群成员" v-if="groups[currentGroupId].groupType === 1">
            <div class="group-user"
                 v-for="(item, index) in groupUsers"
                 :key="index">
              <!-- 成员信息 -->
              <div style="display: flex;align-items: center">
                <div>
                  <n-avatar object-fit="cover"
                            :size="40"
                            lazy
                            :src="item.avatar"/>
                </div>

                <div style="margin-left: 20px;font-size: 16px">
                  {{item.username}}
                </div>
              </div>

              <!-- 成员设置 -->
              <div style="display: flex;align-items: center">
                <div v-if="item.adminFlag"
                     class="user-tag"
                     style="background-color: var(--themeBackground)">
                  管理员
                </div>

                <div v-if="!item.adminFlag && groups[currentGroupId].adminFlag && item.userStatus === 1"
                     class="user-tag"
                     @click="changeGroupUserStatus(currentGroupId, item, 1, 2)"
                     style="background-color: var(--red);cursor: pointer">
                  禁言
                </div>

                <div v-if="!item.adminFlag && groups[currentGroupId].adminFlag && item.userStatus === 2"
                     class="user-tag"
                     @click="changeGroupUserStatus(currentGroupId, item, 2, 1)"
                     style="background-color: var(--blue);cursor: pointer">
                  解禁
                </div>
              </div>
            </div>
          </n-tab-pane>
        </n-tabs>
      </n-card>
    </n-drawer>
  </div>
</template>

<script>
  import {useStore} from 'vuex';

  import {useDialog} from 'naive-ui';

  import {nextTick} from 'vue';

  import {ElMessage} from "element-plus";

  import {reactive, getCurrentInstance, onMounted, onBeforeUnmount, watchEffect, toRefs} from 'vue';

  export default {
    props: {
      currentGroupId: {
        type: Number
      },
      groups: {
        type: Object
      }
    },
    setup(props, context) {
      const globalProperties = getCurrentInstance().appContext.config.globalProperties;
      const $common = globalProperties.$common;
      const $http = globalProperties.$http;
      const $constant = globalProperties.$constant;
      const store = useStore();
      const dialog = useDialog();

      let data = reactive({
        //群成员
        groupUsers: [],
        //组设置
        activeGroupSet: false
      })

      function updateInType(currentGroupId, inType) {
        $http.post($constant.baseURL + "/imChatGroup/updateGroup", {
          id: props.groups[currentGroupId].id,
          inType: inType
        })
          .then((res) => {
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

      function changeDataType(type) {
        context.emit("changeDataType", type);
      }

      function sendGroupMessage() {
        context.emit("sendGroupMessage");
      }

      function groupSetting() {
        data.activeGroupSet = true;
        if (props.groups[props.currentGroupId].groupType === 1) {
          getGroupUser(props.currentGroupId);
        }
      }

      function getGroupUser(groupId, current = 1, size = 9999) {
        $http.get($constant.baseURL + "/imChatGroupUser/getGroupUser", {groupId: groupId, current: current, size: size})
          .then((res) => {
            if (!$common.isEmpty(res.data) && !$common.isEmpty(res.data.records)) {
              data.groupUsers = res.data.records;
            }
          })
          .catch((error) => {
            ElMessage({
              message: error.message,
              type: 'error'
            });
          });
      }

      function exitGroup(currentGroupId) {
        context.emit("exitGroup", currentGroupId);
      }

      function dissolveGroup(currentGroupId) {
        context.emit("dissolveGroup", currentGroupId);
      }

      function changeGroupUserStatus(groupId, item, oldUserStatus, userStatus) {
        $http.get($constant.baseURL + "/imChatGroupUser/changeUserStatus", {
          groupId: groupId,
          userId: item.userId,
          oldUserStatus: oldUserStatus,
          userStatus: userStatus
        })
          .then((res) => {
            item.userStatus = userStatus;
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

      function changeAvatar(type) {
        context.emit("changeAvatar", type);
      }

      return {
        ...toRefs(data),
        changeDataType,
        sendGroupMessage,
        groupSetting,
        updateInType,
        exitGroup,
        dissolveGroup,
        changeGroupUserStatus,
        changeAvatar
      }
    }
  }
</script>

<style scoped>

  .friend-label {
    color: var(--greyFont);
    margin-right: 30px;
  }

  .sendMsg .n-button {
    height: 35px;
    padding: 15px 25px;
  }

  .group-set {
    display: flex;
    justify-content: space-between;
    padding: 10px;
  }

  .group-set:first-child {
    font-size: 16px;
  }

  .user-tag {
    color: var(--white);
    border-radius: 3px;
    font-size: 12px;
    padding: 0 6px;
    margin: 0 6px;
    height: 22px;
    line-height: 22px;
    letter-spacing: 2px;
  }

  .group-user {
    display: flex;
    padding: 10px;
    height: 60px;
    justify-content: space-between;
    box-sizing: border-box;
  }

  .group-avatar {
    cursor: pointer;
    transition: all 0.3s;
    user-select: none;
  }

  .group-avatar:hover {
    transform: rotate(360deg);
  }
</style>
