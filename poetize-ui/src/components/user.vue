<template>
  <div>
    <!-- 登陆和注册 -->
    <div v-if="$common.isEmpty(currentUser)"
         class="myCenter in-up-container my-animation-hideToShow">
      <!-- 背景图片 -->
      <el-image class="my-el-image"
                style="position: absolute"
                v-once
                lazy
                :src="$store.state.webInfo.randomCover && $store.state.webInfo.randomCover.length > 0 
                      ? $store.state.webInfo.randomCover[Math.floor(Math.random() * $store.state.webInfo.randomCover.length)]
                      : '/assets/backgroundPicture.jpg'"
                fit="cover">
        <div slot="error" class="image-slot"></div>
      </el-image>
      <div class="in-up" id="loginAndRegist">
        <div class="form-container sign-up-container">
          <div class="myCenter">
            <h1>注册</h1>
            <input v-model="username" type="text" maxlength="30" placeholder="用户名">
            <input v-model="password" type="password" maxlength="30" placeholder="密码">
            <input v-model="email" type="email" placeholder="邮箱">
            <input v-model="code" type="text" placeholder="验证码" disabled>
            <a style="margin: 0" href="#" @click="changeDialog('邮箱验证码')">获取验证码</a>
            <button @click="showRegistVerify()" style="width: 100%; border-radius: 3px; background: var(--gradualRed); box-shadow: 3px 3px 6px var(--miniMask), -1px -1px 4px var(--miniWhiteMask); border: none; color: var(--white);">注册</button>
          </div>
        </div>
        <div class="form-container sign-in-container">
          <div class="myCenter">
            <h1>登录</h1>
            <input v-model="account" type="text" placeholder="用户名/邮箱/手机号">
            <input v-model="password" type="password" placeholder="密码">
            <a href="#" @click="changeDialog('找回密码')">忘记密码？</a>
            <el-button type="primary" round @click="showLoginVerify()" style="border-radius:8px; width:90%; background: var(--gradualRed); border: none; box-shadow: 3px 3px 6px var(--miniMask), -1px -1px 4px var(--miniWhiteMask); transition: all 0.3s ease; padding: 12px 30px; font-weight: 600; letter-spacing: 1px;">登 录</el-button>
            
            <p style="text-align:center; margin-top:20px;margin-bottom: 10px; font-size:14px; color:var(--articleGreyFontColor);">第三方账号登录</p>
            
            <div style="padding:0; position:relative; height:50px; width:100%; text-align:center; overflow:visible;">
              <a href="javascript:void(0)" @click="showThirdPartyLoginVerify('github')" style="display:inline-block; width:40px; height:40px; margin:0 10px; border-radius:50%; vertical-align:middle; position:relative; transition:all 0.3s ease;">
                <img src="/static/svg/github.svg" alt="GitHub" height="25" style=" position:absolute; top:50%; left:50%; transform:translate(-50%, -50%);">
              </a>
              <a href="javascript:void(0)" @click="showThirdPartyLoginVerify('google')" style="display:inline-block; width:40px; height:40px; margin:0 10px; border-radius:50%; vertical-align:middle; position:relative; transition:all 0.3s ease;">
                <img src="/static/svg/google.svg" alt="Google" height="25" style="position:absolute; top:50%; left:50%; transform:translate(-50%, -50%);">
              </a>
              <a href="javascript:void(0)" @click="showThirdPartyLoginVerify('x')" style="display:inline-block; width:40px; height:40px; margin:0 10px; border-radius:50%; vertical-align:middle; position:relative; transition:all 0.3s ease;">
                <img src="/static/svg/x.svg" alt="Twitter" height="25" style=" position:absolute; top:50%; left:50%; transform:translate(-50%, -50%);">
              </a>
              <a href="javascript:void(0)" @click="showThirdPartyLoginVerify('yandex')" style="display:inline-block; width:40px; height:40px; margin:0 10px; border-radius:50%; vertical-align:middle; position:relative; transition:all 0.3s ease;">
                <img src="/static/svg/yandex.svg" alt="Yandex" height="25" style=" position:absolute; top:50%; left:50%; transform:translate(-50%, -50%);">
              </a>
              <a href="javascript:void(0)" @click="showThirdPartyLoginVerify('gitee')" style="display:inline-block; width:40px; height:40px; margin:0 10px; border-radius:50%; vertical-align:middle; position:relative; transition:all 0.3s ease;">
                <img src="/static/svg/gitee.png" alt="Gitee" height="25" style="position:absolute; top:50%; left:50%; transform:translate(-50%, -50%);">
              </a>
            </div>
          </div>
        </div>
        <div class="overlay-container">
          <div class="overlay">
            <div class="overlay-panel myCenter overlay-left">
              <h1>已有帐号？</h1>
              <p>请登录🚀</p>
              <button class="ghost" @click="signIn()">登录</button>
            </div>
            <div class="overlay-panel myCenter overlay-right">
              <h1>没有帐号？</h1>
              <p>立即注册吧😃</p>
              <button class="ghost" @click="signUp()">注册</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 用户信息 -->
    <div v-else class="user-container myCenter my-animation-hideToShow">
      <!-- 背景图片 -->
      <el-image class="my-el-image"
                style="position: absolute"
                v-once
                lazy
                :src="$store.state.webInfo.randomCover && $store.state.webInfo.randomCover.length > 0 
                      ? $store.state.webInfo.randomCover[Math.floor(Math.random() * $store.state.webInfo.randomCover.length)]
                      : '/assets/backgroundPicture.jpg'"
                fit="cover">
        <div slot="error" class="image-slot"></div>
      </el-image>
      <div class="shadow-box-mini user-info" style="display: flex">
        <div class="user-left">
          <div>
            <el-avatar class="user-avatar" @click.native="changeDialog('修改头像')" :size="60"
                       :src="currentUser.avatar"></el-avatar>
          </div>
          <div class="myCenter" style="margin-top: 12px">
            <div class="user-title">
              <div>用户名：</div>
              <div>手机号：</div>
              <div>邮箱：</div>
              <div>性别：</div>
              <div>简介：</div>
            </div>
            <div class="user-content">
              <div>
                <el-input maxlength="30" v-model="currentUser.username"></el-input>
              </div>
              <div>
                <div v-if="!$common.isEmpty(currentUser.phoneNumber)">
                  {{ currentUser.phoneNumber }} <span class="changeInfo" @click="changeDialog('修改手机号')">修改（功能未接入）</span>
                </div>
                <div v-else><span class="changeInfo" @click="changeDialog('绑定手机号')">绑定手机号（功能未接入）</span></div>
              </div>
              <div>
                <div v-if="!$common.isEmpty(currentUser.email)">
                  {{ currentUser.email }} <span class="changeInfo" @click="changeDialog('修改邮箱')">修改</span>
                </div>
                <div v-else><span class="changeInfo" @click="changeDialog('绑定邮箱')">绑定邮箱</span></div>
              </div>
              <div>
                <el-radio-group v-model="currentUser.gender">
                  <el-radio :label="0" style="margin-right: 10px">薛定谔的猫</el-radio>
                  <el-radio :label="1" style="margin-right: 10px">男</el-radio>
                  <el-radio :label="2">女</el-radio>
                </el-radio-group>
              </div>
              <div>
                <el-input v-model="currentUser.introduction"
                          maxlength="60"
                          type="textarea"
                          show-word-limit></el-input>
              </div>
            </div>
          </div>
          <div style="margin-top: 20px">
            <proButton :info="'提交'"
                       @click.native="submitUserInfo()"
                       :before="'var(--gradualRed)'"
                       :after="'var(--gradualRed)'">
            </proButton>
          </div>
        </div>
        <div class="user-right">
          
        </div>
      </div>
    </div>

    <el-dialog :title="dialogTitle"
               :visible.sync="showDialog"
               width="30%"
               :before-close="clearDialog"
               :append-to-body="true"
               :close-on-click-modal="false"
               center>
      <div class="myCenter" style="flex-direction: column">
        <div>
          <div v-if="dialogTitle === '修改手机号' || dialogTitle === '绑定手机号'">
            <div style="margin-bottom: 5px">手机号：</div>
            <el-input v-model="phoneNumber"></el-input>
            <div style="margin-top: 10px;margin-bottom: 5px">验证码：</div>
            <el-input v-model="code"></el-input>
            <div style="margin-top: 10px;margin-bottom: 5px">密码：</div>
            <el-input v-model="password"></el-input>
          </div>
          <div v-else-if="dialogTitle === '修改邮箱' || dialogTitle === '绑定邮箱'">
            <div style="margin-bottom: 5px">邮箱：</div>
            <el-input v-model="email"></el-input>
            <div style="margin-top: 10px;margin-bottom: 5px">验证码：</div>
            <el-input v-model="code"></el-input>
            <div style="margin-top: 10px;margin-bottom: 5px">密码：</div>
            <el-input v-model="password"></el-input>
          </div>
          <div v-else-if="dialogTitle === '修改头像'">
            <uploadPicture :prefix="'userAvatar'" @addPicture="addPicture" :maxSize="1"
                           :maxNumber="1"></uploadPicture>
          </div>
          <div v-else-if="dialogTitle === '找回密码'">
            <div class="myCenter" style="margin-bottom: 12px">
              <el-radio-group v-model="passwordFlag">
                <el-radio :label="1" style="margin-right: 10px">手机号</el-radio>
                <el-radio :label="2">邮箱</el-radio>
              </el-radio-group>
            </div>
            <div v-if="passwordFlag === 1">
              <div style="margin-bottom: 5px">手机号：</div>
              <el-input v-model="phoneNumber"></el-input>
              <div style="margin-top: 10px;margin-bottom: 5px">验证码：</div>
              <el-input v-model="code"></el-input>
              <div style="margin-top: 10px;margin-bottom: 5px">新密码：</div>
              <el-input maxlength="30" v-model="password"></el-input>
            </div>
            <div v-else-if="passwordFlag === 2">
              <div style="margin-bottom: 5px">邮箱：</div>
              <el-input v-model="email"></el-input>
              <div style="margin-top: 10px;margin-bottom: 5px">验证码：</div>
              <el-input v-model="code"></el-input>
              <div style="margin-top: 10px;margin-bottom: 5px">新密码：</div>
              <el-input maxlength="30" v-model="password"></el-input>
            </div>
          </div>
          <div v-else-if="dialogTitle === '邮箱验证码'">
            <div>
              <div style="margin-bottom: 5px">邮箱：</div>
              <el-input v-model="email"></el-input>
              <div style="margin-top: 10px;margin-bottom: 5px">验证码：</div>
              <el-input v-model="code"></el-input>
            </div>
          </div>
        </div>
        <div style="display: flex;margin-top: 30px" v-show="dialogTitle !== '修改头像'">
          <proButton :info="codeString"
                     v-show="dialogTitle === '修改手机号' || dialogTitle === '绑定手机号' || dialogTitle === '修改邮箱' || dialogTitle === '绑定邮箱' || dialogTitle === '找回密码' || dialogTitle === '邮箱验证码'"
                     @click.native="getCode()"
                     :before="'var(--gradualRed)'"
                     :after="'var(--gradualRed)'"
                     style="margin-right: 20px">
          </proButton>
          <proButton :info="'提交'"
                     @click.native="submitDialog()"
                     :before="'var(--gradualRed)'"
                     :after="'var(--gradualRed)'">
          </proButton>
        </div>
      </div>
    </el-dialog>

    <!-- 添加滑动验证组件 -->
    <CaptchaWrapper
      :visible="showCaptchaWrapper"
      :action="verifyAction"
      :force-slide="false"
      @success="onVerifySuccess"
      @fail="closeVerify"
      @refresh="$emit('refresh')"
      @close="closeVerify"
    ></CaptchaWrapper>
  </div>
</template>

<script>
  const proButton = () => import( "./common/proButton");
  const uploadPicture = () => import( "./common/uploadPicture");
  const CaptchaWrapper = () => import("./common/CaptchaWrapper");
  import { checkCaptchaWithCache } from '@/utils/captchaUtil'

  export default {
    components: {
      proButton,
      uploadPicture,
      CaptchaWrapper
    },
    data() {
      return {
        currentUser: this.$store.state.currentUser,
        username: "",
        account: "",
        password: "",
        phoneNumber: "",
        email: "",
        avatar: "",
        showDialog: false,
        code: "",
        dialogTitle: "",
        codeString: "验证码",
        passwordFlag: null,
        intervalCode: null,
        showCaptchaWrapper: false,
        verifyAction: null,
        verifyParams: null
      }
    },
    computed: {},
    created() {

    },
    methods: {
      addPicture(res) {
        this.avatar = res;
        this.submitDialog()
      },
      signUp() {
        document.querySelector("#loginAndRegist").classList.add('right-panel-active');
      },
      signIn() {
        document.querySelector("#loginAndRegist").classList.remove('right-panel-active');
      },
      showLoginVerify() {
        if (this.$common.isEmpty(this.account) || this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请输入账号或密码！",
            type: "error"
          });
          return;
        }
        
        console.log("开始检查是否需要验证码...");
        // 检查是否需要验证码
        checkCaptchaWithCache('login').then(required => {
          console.log("验证码检查结果:", required);
          if (required) {
            console.log("需要验证码，设置状态...");
            this.verifyAction = 'login';
            this.showCaptchaWrapper = true;
            console.log("验证码状态已设置:", this.showCaptchaWrapper);
          } else {
            // 不需要验证码，直接登录
            this.login();
          }
        }).catch(err => {
          console.error("验证码检查出错:", err);
          // 出错时默认不使用验证码
          this.login();
        });
      },
      
      showRegistVerify() {
        if (this.$common.isEmpty(this.username) || this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请输入用户名或密码！",
            type: "error"
          });
          return;
        }

        if (this.$common.isEmpty(this.email)) {
          this.$message({
            message: "请输入邮箱！",
            type: "error"
          });
          return false;
        }

        if (this.$common.isEmpty(this.code)) {
          this.$message({
            message: "请输入验证码！",
            type: "error"
          });
          return;
        }

        if (this.username.indexOf(" ") !== -1 || this.password.indexOf(" ") !== -1) {
          this.$message({
            message: "用户名或密码不能包含空格！",
            type: "error"
          });
          return;
        }
        
        // 检查是否需要验证码
        checkCaptchaWithCache('register').then(required => {
          if (required) {
            this.verifyAction = 'regist';
            this.showCaptchaWrapper = true;
          } else {
            // 不需要验证码，直接注册
            this.regist();
          }
        });
      },
      
      showThirdPartyLoginVerify(provider) {
        // 检查是否需要验证码
        checkCaptchaWithCache('login').then(required => {
          if (required) {
            this.verifyAction = 'thirdPartyLogin';
            this.verifyParams = provider;
            this.showCaptchaWrapper = true;
          } else {
            // 不需要验证码，直接执行第三方登录
            this.thirdPartyLogin(provider);
          }
        });
      },
      
      onVerifySuccess(token) {
        console.log("验证码验证成功，令牌:", token);
        this.showCaptchaWrapper = false;
        
        // 根据当前操作类型继续相应流程
        if (this.verifyAction === 'login') {
          this.login(token);
        } else if (this.verifyAction === 'regist') {
          this.regist(token);
        } else if (this.verifyAction === 'thirdPartyLogin') {
          this.thirdPartyLogin(this.verifyParams, token);
        } else if (this.verifyAction === 'reset_password' || this.verifyAction === 'register') {
          // 滑动验证成功后发送验证码
          console.log("验证码验证成功，准备发送验证码，参数:", this.verifyParams);
          this.sendVerificationCode({
            ...this.verifyParams,
            verificationToken: token
          });
        }
      },
      
      closeVerify() {
        this.showCaptchaWrapper = false;
        
        // 如果是发送验证码操作，需要重新打开对话框
        if (this.verifyAction === 'reset_password' || this.verifyAction === 'register') {
          // 重新打开之前的对话框
          this.dialogTitle = this.verifyParams.dialogTitle;
          this.$nextTick(() => {
            this.showDialog = true;
          });
        }
        
        // 重置验证相关状态
        this.verifyAction = null;
        this.verifyParams = null;
      },
      /**
       * 登录
       */
      login(verificationToken = '') {
        if (this.$common.isEmpty(this.account) || this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请输入账号或密码！",
            type: "error"
          });
          return;
        }
        
        let user = {
          account: this.account.trim(),
          password: this.$common.encrypt(this.password.trim()),
          isAdmin: true  // 添加isAdmin参数
        };
        
        // 添加验证令牌
        if (verificationToken) {
          user.verificationToken = verificationToken;
          console.log("添加验证令牌到登录请求:", verificationToken);
        }
        
        this.$http.post(this.$constant.baseURL + "/user/login", user, true, false)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              console.log('登录返回的用户信息:', res.data);
              // 同时存储用户token和管理员token
              localStorage.setItem("userToken", res.data.accessToken);
              localStorage.setItem("adminToken", res.data.accessToken);
              this.$store.commit("loadCurrentUser", res.data);
              this.$store.commit("loadCurrentAdmin", res.data);
              this.account = "";
              this.password = "";
              
              // 检查是否有重定向URL
              const redirect = this.$route.query.redirect;
              const hasComment = this.$route.query.hasComment;
              
              if (redirect) {
                // 保留hasComment参数以触发评论恢复
                const query = hasComment === 'true' ? { hasComment: 'true' } : {};
                this.$router.push({ path: redirect, query: query });
              } else {
                this.$router.push({path: '/'});
              }
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      regist(verificationToken) {
        if (this.$common.isEmpty(this.username) || this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请输入用户名或密码！",
            type: "error"
          });
          return;
        }

        if (this.dialogTitle === "邮箱验证码" && this.$common.isEmpty(this.email)) {
          this.$message({
            message: "请输入邮箱！",
            type: "error"
          });
          return false;
        }

        if (this.$common.isEmpty(this.code)) {
          this.$message({
            message: "请输入验证码！",
            type: "error"
          });
          return;
        }

        if (this.username.indexOf(" ") !== -1 || this.password.indexOf(" ") !== -1) {
          this.$message({
            message: "用户名或密码不能包含空格！",
            type: "error"
          });
          return;
        }

        let user = {
          username: this.username.trim(),
          code: this.code.trim(),
          password: this.$common.encrypt(this.password.trim())
        };

        if (this.dialogTitle === "邮箱验证码") {
          user.email = this.email;
        }
        
        // 添加验证令牌
        if (verificationToken) {
          user.verificationToken = verificationToken;
        }

        this.$http.post(this.$constant.baseURL + "/user/regist", user)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              localStorage.setItem("userToken", res.data.accessToken);
              this.$store.commit("loadCurrentUser", res.data);
              this.username = "";
              this.password = "";
              this.email = "";
              this.code = "";
              
              // 检查是否有重定向URL
              const redirect = this.$route.query.redirect;
              const hasComment = this.$route.query.hasComment;
              
              if (redirect) {
                // 保留hasComment参数以触发评论恢复
                const query = hasComment === 'true' ? { hasComment: 'true' } : {};
                this.$router.push({ path: redirect, query: query });
              } else {
                // 如果没有重定向，则跳转首页并打开IM聊天室
                this.$router.push({path: '/'});
                let userToken = this.$common.encrypt(localStorage.getItem("userToken"));
                window.open(this.$constant.imBaseURL + "?userToken=" + userToken);
              }
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      submitUserInfo() {
        if (!this.checkParameters()) {
          return;
        }

        let user = {
          username: this.currentUser.username,
          gender: this.currentUser.gender
        };

        if (!this.$common.isEmpty(this.currentUser.introduction)) {
          user.introduction = this.currentUser.introduction.trim();
        }

        this.$confirm('确认保存？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          this.$http.post(this.$constant.baseURL + "/user/updateUserInfo", user)
            .then((res) => {
              if (!this.$common.isEmpty(res.data)) {
                this.$store.commit("loadCurrentUser", res.data);
                this.currentUser = this.$store.state.currentUser;
                this.$message({
                  message: "修改成功！",
                  type: "success"
                });
              }
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消保存!'
          });
        });
      },
      checkParams(params) {
        if (this.dialogTitle === "修改手机号" || this.dialogTitle === "绑定手机号" || (this.dialogTitle === "找回密码" && this.passwordFlag === 1)) {
          params.flag = 1;
          if (this.$common.isEmpty(this.phoneNumber)) {
            this.$message({
              message: "请输入手机号！",
              type: "error"
            });
            return false;
          }
          if (!(/^1[345789]\d{9}$/.test(this.phoneNumber))) {
            this.$message({
              message: "手机号格式有误！",
              type: "error"
            });
            return false;
          }
          params.place = this.phoneNumber;
          return true;
        } else if (this.dialogTitle === "修改邮箱" || this.dialogTitle === "绑定邮箱" || this.dialogTitle === "邮箱验证码" || (this.dialogTitle === "找回密码" && this.passwordFlag === 2)) {
          params.flag = 2;
          if (this.$common.isEmpty(this.email)) {
            this.$message({
              message: "请输入邮箱！",
              type: "error"
            });
            return false;
          }
          if (!(/^\w+@[a-zA-Z0-9]{2,10}(?:\.[a-z]{2,4}){1,3}$/.test(this.email))) {
            this.$message({
              message: "邮箱格式有误！",
              type: "error"
            });
            return false;
          }
          params.place = this.email;
          return true;
        }
        return false;
      },
      checkParameters() {
        if (this.$common.isEmpty(this.currentUser.username)) {
          this.$message({
            message: "请输入用户名！",
            type: "error"
          });
          return false;
        }

        if (this.currentUser.username.indexOf(" ") !== -1) {
          this.$message({
            message: "用户名不能包含空格！",
            type: "error"
          });
          return false;
        }
        return true;
      },
      changeDialog(value) {
        if (value === "邮箱验证码") {
          if (this.$common.isEmpty(this.email)) {
            this.$message({
              message: "请输入邮箱！",
              type: "error"
            });
            return false;
          }
          if (!(/^\w+@[a-zA-Z0-9]{2,10}(?:\.[a-z]{2,4}){1,3}$/.test(this.email))) {
            this.$message({
              message: "邮箱格式有误！",
              type: "error"
            });
            return false;
          }
        }

        this.dialogTitle = value;
        this.showDialog = true;
      },
      submitDialog() {
        if (this.dialogTitle === "修改头像") {
          if (this.$common.isEmpty(this.avatar)) {
            this.$message({
              message: "请上传头像！",
              type: "error"
            });
          } else {
            let user = {
              avatar: this.avatar.trim()
            };

            this.$http.post(this.$constant.baseURL + "/user/updateUserInfo", user)
              .then((res) => {
                if (!this.$common.isEmpty(res.data)) {
                  this.$store.commit("loadCurrentUser", res.data);
                  this.currentUser = this.$store.state.currentUser;
                  this.clearDialog();
                  this.$message({
                    message: "修改成功！",
                    type: "success"
                  });
                }
              })
              .catch((error) => {
                this.$message({
                  message: error.message,
                  type: "error"
                });
              });
          }
        } else if (this.dialogTitle === "修改手机号" || this.dialogTitle === "绑定手机号" || this.dialogTitle === "修改邮箱" || this.dialogTitle === "绑定邮箱") {
          this.updateSecretInfo();
        } else if (this.dialogTitle === "找回密码") {
          if (this.passwordFlag !== 1 && this.passwordFlag !== 2) {
            this.$message({
              message: "请选择找回方式！",
              type: "error"
            });
          } else {
            this.updateSecretInfo();
          }
        } else if (this.dialogTitle === "邮箱验证码") {
          this.showDialog = false;
        }
      },
      updateSecretInfo() {
        if (this.$common.isEmpty(this.code)) {
          this.$message({
            message: "请输入验证码！",
            type: "error"
          });
          return;
        }
        if (this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请输入密码！",
            type: "error"
          });
          return;
        }
        let params = {
          code: this.code.trim(),
          password: this.$common.encrypt(this.password.trim())
        };
        if (!this.checkParams(params)) {
          return;
        }

        if (this.dialogTitle === "找回密码") {
          this.$http.post(this.$constant.baseURL + "/user/updateForForgetPassword", params, false, false)
            .then((res) => {
              this.clearDialog();
              this.$message({
                message: "修改成功，请重新登陆！",
                type: "success"
              });
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        } else {
          this.$http.post(this.$constant.baseURL + "/user/updateSecretInfo", params, false, false)
            .then((res) => {
              if (!this.$common.isEmpty(res.data)) {
                this.$store.commit("loadCurrentUser", res.data);
                this.currentUser = this.$store.state.currentUser;
                this.clearDialog();
                this.$message({
                  message: "修改成功！",
                  type: "success"
                });
              }
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        }
      },
      getCode() {
        if (this.codeString === "验证码") {
          // 获取验证码前先进行参数检查
          let params = {};
          if (!this.checkParams(params)) {
            return;
          }
          
          // 确定操作类型
          let action = 'reset_password';
          if (this.dialogTitle !== "找回密码" && this.dialogTitle !== "邮箱验证码") {
            action = 'register';  // 或其他适当的操作类型
          }
          
          console.log("准备检查验证码，操作类型:", action, "对话框标题:", this.dialogTitle);
          
          // 检查是否需要验证码
          checkCaptchaWithCache(action).then(required => {
            console.log("验证码检查结果:", required);
            if (required) {
              // 保存当前对话框状态
              const currentDialogTitle = this.dialogTitle;
              
              console.log("需要验证码，保存对话框状态:", currentDialogTitle);
              
              // 先关闭对话框，避免遮挡验证组件
              this.showDialog = false;
              
              // 设置验证操作为发送验证码，同时保存当前对话框信息
              this.verifyAction = action;
              this.verifyParams = {
                ...params,
                dialogTitle: currentDialogTitle
              };
              
              console.log("设置验证参数:", this.verifyAction, this.verifyParams);
              
              // 显示滑块验证
              this.$nextTick(() => {
                this.showCaptchaWrapper = true;
              });
            } else {
              // 不需要验证码，直接发送验证码
              console.log("不需要验证码，直接发送");
              this.sendVerificationCode({
                ...params,
                dialogTitle: this.dialogTitle
              });
            }
          });
        } else {
          this.$message({
            message: "请稍后再试！",
            type: "warning"
          });
        }
      },
      /**
       * 发送验证码
       */
      sendVerificationCode(params) {
        console.log("开始发送验证码，参数:", params);
        
        // 提取出保存的对话框标题
        const savedDialogTitle = params.dialogTitle;
        console.log("保存的对话框标题:", savedDialogTitle);
        
        // 从params中移除我们添加的dialogTitle属性，避免发送到后端API
        delete params.dialogTitle;
        
        // 如果有验证令牌，添加到参数中
        if (params.verificationToken) {
          console.log("添加验证令牌到请求:", params.verificationToken);
        }
        
        let url;
        if (savedDialogTitle === "找回密码" || savedDialogTitle === "邮箱验证码") {
          url = "/user/getCodeForForgetPassword";
        } else {
          url = "/user/getCodeForBind";
        }
        
        console.log("发送验证码请求到:", url, "参数:", params);
        
        this.$http.get(this.$constant.baseURL + url, params)
          .then((res) => {
            console.log("验证码发送成功，响应:", res);
            this.$message({
              message: "验证码已发送，请注意查收！",
              type: "success"
            });
            
            // 重新打开之前的对话框
            this.dialogTitle = savedDialogTitle;
            console.log("重新打开对话框:", this.dialogTitle);
            this.$nextTick(() => {
              this.showDialog = true;
            });
          })
          .catch((error) => {
            console.error("验证码发送失败:", error);
            this.$message({
              message: error.message,
              type: "error"
            });
            
            // 发生错误也重新打开对话框
            this.dialogTitle = savedDialogTitle;
            this.$nextTick(() => {
              this.showDialog = true;
            });
          });
        
        // 开始倒计时
        this.codeString = "30";
        this.intervalCode = setInterval(() => {
          if (this.codeString === "0") {
            clearInterval(this.intervalCode)
            this.codeString = "验证码";
          } else {
            this.codeString = (parseInt(this.codeString) - 1) + "";
          }
        }, 1000);
      },
      clearDialog() {
        this.password = "";
        this.phoneNumber = "";
        this.email = "";
        this.avatar = "";
        this.showDialog = false;
        this.code = "";
        this.dialogTitle = "";
        this.passwordFlag = null;
      },
      thirdPartyLogin(provider, verificationToken) {
        if (!provider) return;
        
        // 添加检查第三方登录是否启用
        this.checkThirdPartyLoginEnabled(provider).then(enabled => {
          if (!enabled) {
            this.$message({
              message: `${provider.charAt(0).toUpperCase() + provider.slice(1)}第三方登录未启用，请联系管理员开启此功能`,
              type: "warning"
            });
            return;
          }
          
          const params = {
            provider: provider
          };
          
          // 添加验证令牌
          if (verificationToken) {
            params.verificationToken = verificationToken;
          }
          
          // Python服务配置
          const pythonServiceConfig = {
            baseUrl: process.env.VUE_APP_PYTHON_SERVICE_URL || 'http://localhost:5000',
            providers: {
              github: {
                icon: 'el-icon-s-platform',
                name: 'GitHub'
              },
              google: {
                icon: 'el-icon-s-promotion',
                name: 'Google'
              },
              x: {
                icon: 'el-icon-message',
                name: 'Twitter'
              },
              yandex: {
                icon: 'el-icon-s-custom',
                name: 'Yandex'
              },
              gitee: {
                icon: 'el-icon-s-custom',
                name: 'Gitee'
              }
            }
          };
          
          // 构建请求URL
          const loginUrl = `${pythonServiceConfig.baseUrl}/login/${provider}`;
          
          // 记录当前登录方式
          localStorage.setItem('thirdPartyLoginProvider', provider);
          
          // 使用window.open打开第三方登录授权页面
          window.open(loginUrl, '_self');
        }).catch(error => {
          console.error("检查第三方登录状态失败:", error);
          this.$message({
            message: "无法验证第三方登录状态，请稍后再试",
            type: "error"
          });
        });
      },
      
      // 检查第三方登录是否启用
      checkThirdPartyLoginEnabled(provider) {
        return new Promise((resolve, reject) => {
          this.$http.get(this.$constant.pythonBaseURL + "/webInfo/getThirdLoginStatus", { provider: provider })
            .then(res => {
              if (res.code === 200 && res.data) {
                // 检查整体启用状态及特定提供商状态
                const enabled = res.data.enable === true && 
                                res.data[provider] && 
                                res.data[provider].enabled === true;
                resolve(enabled);
              } else {
                // 如果接口返回错误或没有数据，假设未启用
                resolve(false);
              }
            })
            .catch(error => {
              console.error("获取第三方登录状态失败:", error);
              // 出错时默认允许尝试登录，避免错误阻止用户
              resolve(false);
            });
        });
      },
      testShowCaptcha() {
        this.showCaptchaWrapper = true;
      }
    }
  }
</script>

<style scoped>

  .in-up-container {
    height: 100vh;
    position: relative;
  }

  .in-up {
    opacity: 0.9;
    border-radius: 10px;
    box-shadow: 0 15px 30px var(--miniMask), 0 10px 10px var(--miniMask);
    position: relative;
    overflow: hidden;
    width: 750px;
    max-width: 100%;
    min-height: 450px;
    margin: 10px;
  }

  .in-up p {
    font-size: 14px;
    letter-spacing: 1px;
    margin: 20px 0 30px 0;
    color: var(--articleGreyFontColor);
  }

  .in-up a {
    color: var(--fontColor);
    font-size: 14px;
    text-decoration: none;
    margin: 15px 0;
  }

  .form-container {
    position: absolute;
    height: 100%;
    transition: all 0.5s ease-in-out;
  }

  .sign-in-container {
    left: 0;
    width: 50%;
  }

  .sign-up-container {
    left: 0;
    width: 50%;
    opacity: 0;
  }

  .form-container div {
    background: var(--background);
    flex-direction: column;
    padding: 0 20px;
    height: 100%;
    color: var(--fontColor);
  }

  .form-container input {
    background: var(--inputBackground);
    border-radius: 3px;
    border: none;
    padding: 12px 15px;
    margin: 10px 0;
    width: 90%;
    outline: none;
    color: var(--fontColor);
  }

  .form-container input::placeholder {
    color: var(--articleGreyFontColor);
  }

  .in-up button {
    border-radius: 2rem;
    border: none;
    background: var(--gradualRed);
    color: var(--white);
    font-size: 16px;
    font-weight: bold;
    padding: 12px 45px;
    letter-spacing: 2px;
    cursor: pointer;
    box-shadow: 3px 3px 6px var(--miniMask), -1px -1px 4px var(--miniWhiteMask);
    transition: all 0.3s ease;
  }

  .in-up button:hover {
    background: var(--gradualRed);
    box-shadow: 4px 4px 8px var(--mask), -2px -2px 6px var(--miniWhiteMask);
    transform: translateY(-3px);
  }

  @keyframes glow {
    0% {
      box-shadow: 0 0 5px rgba(255, 99, 71, 0.6);
    }
    50% {
      box-shadow: 0 0 20px rgba(255, 99, 71, 0.8);
    }
    100% {
      box-shadow: 0 0 5px rgba(255, 99, 71, 0.6);
    }
  }

  .in-up button:active {
    transform: translateY(1px);
    box-shadow: 2px 2px 4px var(--mask);
  }

  .in-up button.ghost {
    background: linear-gradient(145deg, var(--miniWhiteMask), var(--transparent));
    border: 1px solid var(--miniWhiteMask);
    box-shadow: 3px 3px 6px var(--mask), -1px -1px 4px var(--miniWhiteMask);
  }
  
  .in-up button.ghost:hover {
    background: linear-gradient(145deg, var(--whiteMask), var(--miniWhiteMask));
    box-shadow: 4px 4px 8px var(--translucent), -2px -2px 6px var(--miniWhiteMask);
    transform: translateY(-3px);
  }

  .in-up button.ghost:active {
    transform: translateY(1px);
    box-shadow: 2px 2px 4px var(--mask);
  }

  .sign-up-container button {
    margin-top: 20px;
  }

  .overlay-container {
    position: absolute;
    left: 50%;
    width: 50%;
    height: 100%;
    overflow: hidden;
    transition: all 0.5s ease-in-out;
  }

  .overlay {
    background: var(--gradualRed);
    color: var(--white);
    position: relative;
    left: -100%;
    height: 100%;
    width: 200%;
  }

  .overlay-panel {
    position: absolute;
    top: 0;
    flex-direction: column;
    height: 100%;
    width: 50%;
    transition: all 0.5s ease-in-out;
  }

  .overlay-right {
    right: 0;
    transform: translateY(0);
    background: var(--gradualRed);
    box-shadow: -4px 0 15px rgba(0, 0, 0, 0.1);
    border-left: 1px solid rgba(255, 255, 255, 0.2);
  }

  .overlay-left {
    transform: translateY(-20%);
  }

  .in-up.right-panel-active .sign-in-container {
    transform: translateY(100%);
  }

  .in-up.right-panel-active .overlay-container {
    transform: translateX(-100%);
  }

  .in-up.right-panel-active .sign-up-container {
    transform: translateX(100%);
    opacity: 1;
  }

  .in-up.right-panel-active .overlay {
    transform: translateX(50%);
  }

  .in-up.right-panel-active .overlay-left {
    transform: translateY(0);
  }

  .in-up.right-panel-active .overlay-right {
    transform: translateY(20%);
  }

  .user-container {
    width: 100vw;
    height: 100vh;
    position: relative;
  }

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
    height: 55px;
    line-height: 55px;
    text-align: center;
  }

  .user-content > div {
    height: 55px;
    display: flex;
    align-items: center;
  }

  .user-content >>> .el-input__inner, .user-content >>> .el-textarea__inner {
    border: none;
    background: var(--whiteMask);
  }

  .user-content >>> .el-input__count {
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

  .third-party-login {
    margin-top: 20px;
    width: 100%;
  }

  .divider {
    position: relative;
    margin: 15px 0;
    text-align: center;
  }

  .divider:before, .divider:after {
    content: "";
    position: absolute;
    top: 50%;
    width: 35%;
    height: 1px;
    background-color: var(--borderColor);
  }

  .divider:before {
    left: 0;
  }

  .divider:after {
    right: 0;
  }

  .divider span {
    display: inline-block;
    padding: 0 10px;
    background-color: var(--background);
    color: var(--articleGreyFontColor);
    position: relative;
    z-index: 1;
    font-size: 12px;
  }

  .login-buttons {
    display: flex;
    justify-content: center;
    flex-wrap: wrap;
    margin-top: 10px;
  }

  .login-button {
    margin: 5px;
    padding: 8px 12px;
    border: 1px solid var(--borderColor);
    border-radius: 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    transition: all 0.3s;
    background-color: var(--background);
    box-shadow: 0 2px 5px var(--miniMask);
    font-size: 12px;
  }

  .login-button:hover {
    box-shadow: 0 4px 12px var(--borderHoverColor);
    transform: translateY(-2px);
  }

  .login-button i {
    margin-right: 6px;
    font-size: 16px;
  }

  .login-button span {
    font-size: 12px;
  }


  /* 圆形图标登录按钮样式 */
  .login-circle-btn {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 45px;
    height: 45px;
    border-radius: 50%;
    border: none;
    padding: 0;
    cursor: pointer;
    transition: all 0.3s;
    box-shadow: 0 2px 5px var(--mask);
  }
  
  .login-circle-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 8px var(--borderHoverColor);
  }

  /* 第三方登录按钮悬停动画 */
  div > a[href="javascript:void(0)"] {
    overflow: hidden;
  }
  
  div > a[href="javascript:void(0)"]:hover {
    transform: scale(1.1);
    box-shadow: 0 0 10px var(--borderHoverColor);
  }
  
  div > a[href="javascript:void(0)"]:hover::before {
    content: '';
    position: absolute;
    top: -10px;
    left: -10px;
    right: -10px;
    bottom: -10px;
    border-radius: 50%;
    animation: pulse 1s infinite;
    z-index: -1;
  }
  
  @keyframes pulse {
    0% {
      box-shadow: 0 0 0 0 var(--borderHoverColor);
    }
    70% {
      box-shadow: 0 0 0 5px var(--transparent);
    }
    100% {
      box-shadow: 0 0 0 0 var(--transparent);
    }
  }

  .pro-btn {
    box-shadow: 3px 3px 6px var(--miniMask), -1px -1px 4px var(--miniWhiteMask) !important;
    border-radius: 2rem !important;
    font-weight: 600 !important;
    letter-spacing: 1px !important;
  }
  
  .pro-btn:hover {
    box-shadow: 4px 4px 8px var(--mask), -2px -2px 6px var(--miniWhiteMask) !important;
    transform: translateY(-3px) !important;
  }
  
  .pro-btn:active {
    box-shadow: 2px 2px 4px var(--miniMask) !important;
    transform: translateY(1px) !important;
  }

  .form-container .submit {
    /* 使用CSS变量替代硬编码颜色 */
    background: var(--gradualRed);
    border: none;
    border-radius: 4px;
    color: var(--white);
    width: 90%;
    padding: 15px 20px;
    margin: 15px 10px;
    cursor: pointer;
    font-size: 14px;
    transition: all 0.3s ease;
  }

  .form-container .submit:hover {
    background: var(--gradualRed);
    border: none;
  }
</style>
