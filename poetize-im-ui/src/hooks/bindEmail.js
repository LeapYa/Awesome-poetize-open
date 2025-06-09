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

  let bindEmailData = reactive({
    emailVisible: false,
    email: '',
    code: '',
    password: '',
    codeString: "验证码"
  })

  let intervalCode = null;

  onMounted(() => {
    showEmail();
  })

  function showEmail() {
    if (!$common.isEmpty(store.state.currentUser) && $common.isEmpty(store.state.currentUser.email)) {
      //没有绑定邮箱的用户会弹框
      //bindEmailData.emailVisible = true;
    }
  }

  function getCode() {
    if (bindEmailData.codeString === "验证码") {
      // 获取验证码
      if ($common.isEmpty(bindEmailData.email)) {
        ElMessage({
          message: "请输入邮箱！",
          type: 'error'
        });
        return;
      }
      if (!(/^\w+@[a-zA-Z0-9]{2,10}(?:\.[a-z]{2,4}){1,3}$/.test(bindEmailData.email))) {
        ElMessage({
          message: "邮箱格式有误！",
          type: 'error'
        });
        return;
      }

      $http.get($constant.baseURL + "/user/getCodeForBind", {
        flag: 2,
        place: bindEmailData.email
      })
        .then((res) => {
          ElMessage({
            message: "验证码已发送，请注意查收！",
            type: 'success'
          });
        })
        .catch((error) => {
          ElMessage({
            message: error.message,
            type: 'error'
          });
        });
      bindEmailData.codeString = "30";
      intervalCode = setInterval(() => {
        if (bindEmailData.codeString === "0") {
          clearInterval(intervalCode)
          bindEmailData.codeString = "验证码";
        } else {
          bindEmailData.codeString = (parseInt(bindEmailData.codeString) - 1) + "";
        }
      }, 1000);
    } else {
      ElMessage({
        message: "请稍后再试！",
        type: 'error'
      });
    }
  }

  function submitDialog() {
    if ($common.isEmpty(bindEmailData.email)) {
      ElMessage({
        message: "请输入邮箱！",
        type: 'error'
      });
      return;
    }
    if (!(/^\w+@[a-zA-Z0-9]{2,10}(?:\.[a-z]{2,4}){1,3}$/.test(bindEmailData.email))) {
      ElMessage({
        message: "邮箱格式有误！",
        type: 'error'
      });
      return;
    }
    if ($common.isEmpty(bindEmailData.code)) {
      ElMessage({
        message: "请输入验证码！",
        type: 'error'
      });
      return;
    }
    if ($common.isEmpty(bindEmailData.password)) {
      ElMessage({
        message: "请输入密码！",
        type: 'error'
      });
      return;
    }

    let params = {
      code: bindEmailData.code.trim(),
      flag: 2,
      place: bindEmailData.email.trim(),
      password: $common.encrypt(bindEmailData.password.trim())
    };
    $http.post($constant.baseURL + "/user/updateSecretInfo", params, false)
      .then((res) => {
        if (!$common.isEmpty(res.data)) {
          ElMessage({
            message: "保存成功！",
            type: 'success'
          });
          store.commit("loadCurrentUser", res.data);
          clearEmailDialog();
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  function clearEmailDialog() {
    bindEmailData.password = "";
    bindEmailData.codeString = "验证码";
    if (intervalCode != null) {
      clearInterval(intervalCode);
      intervalCode = null;
    }
    bindEmailData.email = "";
    bindEmailData.code = "";
    bindEmailData.emailVisible = false;
  }

  return {
    bindEmailData,
    getCode,
    submitDialog
  }
}
