<template>
  <div class="shadow-box-mini user-info" style="display: flex">
    <div class="user-left">
      <div>
        <el-avatar
          class="user-avatar"
          @click="$emit('change-dialog', '修改头像')"
          :size="60"
          :src="$common.getAvatarUrl(user.avatar)"
        >
          <img :src="$getDefaultAvatar()" />
        </el-avatar>
      </div>
      <div class="myCenter" style="margin-top: 12px">
        <div class="user-title">
          <div>用户名：</div>
          <div>手机号：</div>
          <div>邮箱：</div>
          <div v-if="!isThirdPartyUser">密码：</div>
          <div>性别：</div>
          <div>简介：</div>
        </div>
        <div class="user-content">
          <div>
            <el-input maxlength="30" v-model="user.username"></el-input>
          </div>
          <div>
            <div v-if="!$common.isEmpty(user.phoneNumber)">
              {{ user.phoneNumber }}
              <span class="changeInfo" @click="$emit('change-dialog', '修改手机号')"
                >修改（功能未接入）</span
              >
            </div>
            <div v-else>
              <span class="changeInfo" @click="$emit('change-dialog', '绑定手机号')"
                >绑定手机号（功能未接入）</span
              >
            </div>
          </div>
          <div>
            <div v-if="!$common.isEmpty(user.email)">
              {{ user.email }}
              <span class="changeInfo" @click="$emit('change-dialog', '修改邮箱')"
                >修改</span
              >
            </div>
            <div v-else>
              <span class="changeInfo" @click="$emit('change-dialog', '绑定邮箱')"
                >绑定邮箱</span
              >
            </div>
          </div>
          <div v-if="!isThirdPartyUser">
            <div>
              <span>******</span>
              <span class="changeInfo" @click="$emit('change-dialog', '修改密码')" style="margin-left: 10px;"
                >修改</span
              >
            </div>
          </div>
          <div>
            <el-radio-group v-model="user.gender">
              <el-radio :label="0" style="margin-right: 10px">薛定谔的猫</el-radio>
              <el-radio :label="1" style="margin-right: 10px">男</el-radio>
              <el-radio :label="2">女</el-radio>
            </el-radio-group>
          </div>
          <div>
            <el-input
              v-model="user.introduction"
              maxlength="60"
              type="textarea"
              show-word-limit
            ></el-input>
          </div>
        </div>
      </div>
      <div style="margin-top: 20px">
        <proButton
          :info="'提交'"
          @click="$emit('submit')"
          :before="$constant.before_color_2"
          :after="'var(--loginAccent, var(--gradualRed))'"
        >
        </proButton>
      </div>
    </div>
    <div class="user-right"></div>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue'

export default {
  name: 'UserProfile',
  components: {
    proButton: defineAsyncComponent(() => import('../common/proButton')),
  },
  props: {
    // 当前用户对象（用户名/性别/简介直接双向编辑，由父组件负责提交）
    user: {
      type: Object,
      required: true,
    },
  },
  emits: ['change-dialog', 'submit'],
  computed: {
    // 第三方登录用户没有密码，不展示密码行
    isThirdPartyUser() {
      return this.user && this.user.platformType
    },
  },
}
</script>

<style scoped>
.user-info {
  width: 80%;
  z-index: 10;
  margin-top: 70px;
  height: calc(100vh - 90px);
  margin-bottom: 20px;
  border-radius: 10px;
  overflow: hidden;
}
.user-left {
  width: 50%;
  background: var(--maxMaxWhiteMask);
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow-y: auto;
  padding: 20px;
}
.user-right {
  width: 50%;
  background: var(--maxWhiteMask);
  padding: 20px;
}
.user-title {
  text-align: right;
  user-select: none;
}
.user-content {
  text-align: left;
}
.user-title div {
  min-height: 55px;
  line-height: 55px;
  text-align: center;
}
.user-content > div {
  min-height: 55px;
  display: flex;
  align-items: center;
}
.user-content :deep(.el-input__wrapper),
.user-content :deep(.el-textarea__inner){
  border: none;
  background: var(--whiteMask) !important;
  color: var(--fontColor);
  box-shadow: none !important;
}

body.dark-mode .user-content :deep(.el-input__wrapper),
body.dark-mode .user-content :deep(.el-textarea__inner){
  background: #2d2d2d !important;
}

.user-content :deep(.el-input__inner) {
  background: transparent !important;
  color: var(--fontColor);
  border: none;
}
.user-content :deep(.el-input__count){
  background: var(--transparent);
  user-select: none;
}
.changeInfo {
  color: var(--white);
  font-size: 0.75rem;
  cursor: pointer;
  background: var(--themeBackground);
  padding: 3px;
  border-radius: 0.2rem;
  user-select: none;
}
@media screen and (max-width: 920px) {
  .user-info {
    width: 90%;
  }
  .user-left {
    width: 100%;
  }
  .user-right {
    display: none;
  }
}
@media screen and (max-width: 480px) {
  .user-info {
    width: 95%;
    margin-top: 60px;
  }
  .user-left {
    padding: 15px;
  }
  .myCenter {
    flex-direction: column !important;
  }
  .user-title {
    display: none;
  }
  .user-content {
    width: 100%;
  }
  .user-content > div {
    margin-bottom: 15px;
    flex-direction: column;
    align-items: flex-start;
    height: auto;
    min-height: 40px;
  }
  .user-content > div:nth-child(1):before {
    content: '用户名：';
    font-size: 0.85rem;
    margin-bottom: 5px;
    color: var(--fontColor);
    font-weight: 500;
  }
  .user-content > div:nth-child(2):before {
    content: '手机号：';
    font-size: 0.85rem;
    margin-bottom: 5px;
    color: var(--fontColor);
    font-weight: 500;
  }
  .user-content > div:nth-child(3):before {
    content: '邮箱：';
    font-size: 0.85rem;
    margin-bottom: 5px;
    color: var(--fontColor);
    font-weight: 500;
  }
  .user-content > div:nth-child(4):before {
    content: '性别：';
    font-size: 0.85rem;
    margin-bottom: 5px;
    color: var(--fontColor);
    font-weight: 500;
  }
  .user-content > div:nth-child(5):before {
    content: '简介：';
    font-size: 0.85rem;
    margin-bottom: 5px;
    color: var(--fontColor);
    font-weight: 500;
  }
  .user-content :deep(.el-input__inner){
    font-size: 0.85rem;
    padding: 8px 10px;
  }
  .user-content :deep(.el-textarea__inner){
    font-size: 0.85rem;
    padding: 8px 10px;
  }
  .changeInfo {
    font-size: 0.7rem;
    padding: 2px 4px;
    white-space: nowrap;
    margin-left: 8px;
  }
  .user-content > div > div {
    word-break: break-all;
    overflow-wrap: break-word;
    line-height: 1.3;
    max-width: 100%;
  }
  .user-content :deep(.el-radio-group){
    flex-wrap: wrap;
  }
  .user-content :deep(.el-radio){
    margin-right: 8px;
    margin-bottom: 5px;
    font-size: 0.85rem;
  }
}
@media screen and (max-width: 360px) {
  .user-info {
    width: 98%;
    margin-top: 50px;
  }
  .user-left {
    padding: 10px;
  }
  .user-content > div:before {
    font-size: 0.8rem !important;
  }
  .user-content > div {
    margin-bottom: 12px;
    min-height: 40px;
  }
  .user-content :deep(.el-input__inner),
  .user-content :deep(.el-textarea__inner){
    font-size: 0.8rem;
    padding: 6px 8px;
  }
  .changeInfo {
    font-size: 0.65rem;
    padding: 1px 3px;
    margin-left: 6px;
  }
  .user-content :deep(.el-radio){
    font-size: 0.8rem;
    margin-right: 6px;
  }
  .user-avatar {
    width: 50px !important;
    height: 50px !important;
  }
}
</style>

<style>
/* 个人中心页面头像旋转动画 */
.user-info .el-avatar.user-avatar {
  cursor: pointer;
  transition: transform 0.6s ease;
  will-change: transform;
  transform: translateZ(0);
}

.user-info .el-avatar.user-avatar:hover {
  transform: rotate(360deg);
}
</style>
