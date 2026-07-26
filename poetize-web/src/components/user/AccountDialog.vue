<template>
  <el-dialog
    :title="title"
    :model-value="visible"
    width="30%"
    :before-close="(done) => $emit('close', done)"
    :append-to-body="true"
    class="centered-dialog"
    :close-on-click-modal="false"
    center
  >
    <div class="myCenter" style="flex-direction: column">
      <div>
        <div v-if="title === '修改手机号' || title === '绑定手机号'">
          <div style="margin-bottom: 5px">手机号：</div>
          <el-input v-model="localPhoneNumber" placeholder="请输入手机号"></el-input>
          <div style="margin-top: 10px; margin-bottom: 5px">验证码：</div>
          <el-input v-model="localCode" placeholder="请输入验证码"></el-input>
          <!-- 只有普通注册用户才需要输入密码，第三方登录用户没有密码 -->
          <div v-if="!isThirdPartyUser">
            <div style="margin-top: 10px; margin-bottom: 5px">密码：</div>
            <el-input
              type="password"
              v-model="localPassword"
              show-password
              placeholder="请输入当前密码"
            ></el-input>
          </div>
        </div>
        <div v-else-if="title === '修改邮箱' || title === '绑定邮箱'">
          <div style="margin-bottom: 5px">邮箱：</div>
          <el-input v-model="localEmail" placeholder="请输入邮箱"></el-input>
          <div style="margin-top: 10px; margin-bottom: 5px">验证码：</div>
          <el-input v-model="localCode" placeholder="请输入验证码"></el-input>
          <!-- 只有普通注册用户才需要输入密码，第三方登录用户没有密码 -->
          <div v-if="!isThirdPartyUser">
            <div style="margin-top: 10px; margin-bottom: 5px">密码：</div>
            <el-input
              type="password"
              v-model="localPassword"
              show-password
              placeholder="请输入当前密码"
            ></el-input>
          </div>
        </div>
        <div v-else-if="title === '修改密码'">
          <div style="margin-bottom: 5px">旧密码：</div>
          <el-input
            type="password"
            v-model="localOldPassword"
            show-password
            placeholder="请输入旧密码"
          ></el-input>
          <div style="margin-top: 10px; margin-bottom: 5px">新密码：</div>
          <el-input
            type="password"
            v-model="localNewPassword"
            show-password
            placeholder="请输入新密码"
          ></el-input>
          <div style="margin-top: 10px; margin-bottom: 5px">确认新密码：</div>
          <el-input
            type="password"
            v-model="localConfirmPassword"
            show-password
            placeholder="请在此输入新密码"
          ></el-input>
          <div style="margin-top: 10px; margin-bottom: 5px">验证码：</div>
          <el-input v-model="localCode" placeholder="请输入验证码"></el-input>
        </div>
        <div v-else-if="title === '修改头像'">
          <uploadPicture
            :prefix="'userAvatar'"
            @addPicture="$emit('add-picture', $event)"
            :maxSize="1"
            :maxNumber="1"
          ></uploadPicture>
        </div>
        <div v-else-if="title === '找回密码'">
          <div class="myCenter" style="margin-bottom: 12px">
            <el-radio-group v-model="localPasswordFlag">
              <el-radio :label="1" style="margin-right: 10px">手机号</el-radio>
              <el-radio :label="2">邮箱</el-radio>
            </el-radio-group>
          </div>
          <div v-if="passwordFlag === 1">
            <div style="margin-bottom: 5px">用户名：</div>
            <el-input v-model="localUsername"></el-input>
            <div style="margin-top: 10px; margin-bottom: 5px">手机号：</div>
            <el-input v-model="localPhoneNumber"></el-input>
            <div style="margin-top: 10px; margin-bottom: 5px">验证码：</div>
            <el-input v-model="localCode"></el-input>
            <div style="margin-top: 10px; margin-bottom: 5px">新密码：</div>
            <el-input maxlength="30" type="password" show-password v-model="localPassword"></el-input>
          </div>
          <div v-else-if="passwordFlag === 2">
            <div style="margin-bottom: 5px">用户名：</div>
            <el-input v-model="localUsername"></el-input>
            <div style="margin-top: 10px; margin-bottom: 5px">邮箱：</div>
            <el-input v-model="localEmail"></el-input>
            <div style="margin-top: 10px; margin-bottom: 5px">验证码：</div>
            <el-input v-model="localCode"></el-input>
            <div style="margin-top: 10px; margin-bottom: 5px">新密码：</div>
            <el-input maxlength="30" type="password" show-password v-model="localPassword"></el-input>
          </div>
        </div>
        <div v-else-if="title === '邮箱验证码'">
          <div>
            <div style="margin-bottom: 5px">邮箱：</div>
            <el-input v-model="localEmail" placeholder="请输入邮箱"></el-input>
            <div style="margin-top: 10px; margin-bottom: 5px">验证码：</div>
            <el-input v-model="localCode" placeholder="请输入验证码"></el-input>
          </div>
        </div>
      </div>
      <div
        style="display: flex; margin-top: 30px"
        v-show="title !== '修改头像'"
      >
        <proButton
          :info="codeString"
          v-show="
            title === '修改手机号' ||
            title === '绑定手机号' ||
            title === '修改邮箱' ||
            title === '绑定邮箱' ||
            title === '修改密码' ||
            title === '找回密码' ||
            title === '邮箱验证码'
          "
          @click="$emit('get-code')"
          :before="$constant.before_color_1"
          :after="'var(--loginAccent, var(--gradualRed))'"
          style="margin-right: 20px"
        >
        </proButton>
        <proButton
          :info="'提交'"
          @click="$emit('submit')"
          :before="$constant.before_color_2"
          :after="'var(--loginAccent, var(--gradualRed))'"
        >
        </proButton>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { defineAsyncComponent } from 'vue'

export default {
  name: 'AccountDialog',
  components: {
    proButton: defineAsyncComponent(() => import('../common/proButton')),
    uploadPicture: defineAsyncComponent(() => import('../common/uploadPicture')),
  },
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    // 弹窗标题即业务类型：修改手机号/绑定手机号/修改邮箱/绑定邮箱/修改密码/修改头像/找回密码/邮箱验证码
    title: {
      type: String,
      default: '',
    },
    codeString: {
      type: String,
      default: '验证码',
    },
    isThirdPartyUser: {
      type: [Boolean, String, Number],
      default: false,
    },
    phoneNumber: {
      type: String,
      default: '',
    },
    email: {
      type: String,
      default: '',
    },
    code: {
      type: String,
      default: '',
    },
    password: {
      type: String,
      default: '',
    },
    oldPassword: {
      type: String,
      default: '',
    },
    newPassword: {
      type: String,
      default: '',
    },
    confirmPassword: {
      type: String,
      default: '',
    },
    username: {
      type: String,
      default: '',
    },
    passwordFlag: {
      type: Number,
      default: null,
    },
  },
  emits: [
    'update:phoneNumber',
    'update:email',
    'update:code',
    'update:password',
    'update:oldPassword',
    'update:newPassword',
    'update:confirmPassword',
    'update:username',
    'update:passwordFlag',
    'get-code',
    'submit',
    'close',
    'add-picture',
  ],
  computed: {
    // 与父组件共享的表单字段（父组件的业务方法直接读取这些字段）
    localPhoneNumber: {
      get() {
        return this.phoneNumber
      },
      set(value) {
        this.$emit('update:phoneNumber', value)
      },
    },
    localEmail: {
      get() {
        return this.email
      },
      set(value) {
        this.$emit('update:email', value)
      },
    },
    localCode: {
      get() {
        return this.code
      },
      set(value) {
        this.$emit('update:code', value)
      },
    },
    localPassword: {
      get() {
        return this.password
      },
      set(value) {
        this.$emit('update:password', value)
      },
    },
    localOldPassword: {
      get() {
        return this.oldPassword
      },
      set(value) {
        this.$emit('update:oldPassword', value)
      },
    },
    localNewPassword: {
      get() {
        return this.newPassword
      },
      set(value) {
        this.$emit('update:newPassword', value)
      },
    },
    localConfirmPassword: {
      get() {
        return this.confirmPassword
      },
      set(value) {
        this.$emit('update:confirmPassword', value)
      },
    },
    localUsername: {
      get() {
        return this.username
      },
      set(value) {
        this.$emit('update:username', value)
      },
    },
    localPasswordFlag: {
      get() {
        return this.passwordFlag
      },
      set(value) {
        this.$emit('update:passwordFlag', value)
      },
    },
  },
}
</script>
