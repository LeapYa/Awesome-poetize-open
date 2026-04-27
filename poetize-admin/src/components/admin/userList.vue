<template>
  <div>
    <div>
      <div class="handle-box">
        <el-select v-model="pagination.userType" placeholder="权限类型" class="handle-select mrb10">
          <el-option key="1" label="站长" :value="0"></el-option>
          <el-option key="2" label="管理员" :value="1"></el-option>
          <el-option key="3" label="普通用户" :value="2"></el-option>
        </el-select>
        <el-select v-model="pagination.isThirdPartyUser" placeholder="登录方式" class="handle-select mrb10">
          <el-option key="1" label="普通注册" :value="false"></el-option>
          <el-option key="2" label="第三方登录" :value="true"></el-option>
        </el-select>
        <el-select v-model="pagination.userStatus" placeholder="用户状态" class="handle-select mrb10">
          <el-option key="1" label="启用" :value="true"></el-option>
          <el-option key="2" label="禁用" :value="false"></el-option>
        </el-select>
        <el-input v-model="pagination.searchKey" placeholder="用户名/手机号/邮箱" class="handle-input mrb10"></el-input>
        <el-button type="primary" icon="el-icon-search" @click="searchUser()">搜索</el-button>
        <el-button type="danger" @click="clearSearch()">清除参数</el-button>
        <el-button type="success" @click="openProfileDialog()">修改个人信息</el-button>
        <el-button type="warning" @click="openPasswordDialog()" v-if="mainStore.currentUser && !mainStore.currentUser.platformType">修改密码</el-button>
      </div>
      <el-table :data="users" border class="table" header-cell-class-name="table-header">
        <el-table-column prop="id" label="ID" width="55" align="center"></el-table-column>
        <el-table-column prop="username" label="用户名" align="center"></el-table-column>
        <el-table-column label="登录方式" align="center" width="100">
          <template slot-scope="scope">
            <el-tag :type="scope.row.isThirdPartyUser ? 'warning' : 'success'"
                    disable-transitions>
              {{ scope.row.isThirdPartyUser ? '第三方' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phoneNumber" label="手机号" align="center"></el-table-column>
        <el-table-column prop="email" label="邮箱" align="center"></el-table-column>
        <el-table-column label="赞赏" width="100" align="center">
          <template slot-scope="scope">
            <el-input size="medium" maxlength="30" v-model="scope.row.admire"
                      @blur="changeUserAdmire(scope.row)"></el-input>
          </template>
        </el-table-column>
        <el-table-column label="用户状态" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.userStatus === false ? 'danger' : 'success'"
                    disable-transitions>
              {{scope.row.userStatus === false ? '禁用' : '启用'}}
            </el-tag>
            <el-switch v-if="scope.row.id !== mainStore.currentAdmin.id"
                       v-model="scope.row.userStatus"
                       @change="handleUserStatusToggle(scope.row)"></el-switch>
          </template>
        </el-table-column>
        <el-table-column label="头像" align="center">
          <template slot-scope="scope">
            <el-image lazy class="table-td-thumb" :src="scope.row.avatar" fit="cover"></el-image>
          </template>
        </el-table-column>
        <el-table-column label="性别" align="center">
          <template slot-scope="scope">
            <el-tag type="success"
                    v-if="scope.row.gender === 1"
                    disable-transitions>
              男
            </el-tag>
            <el-tag type="success"
                    v-else-if="scope.row.gender === 2"
                    disable-transitions>
              女
            </el-tag>
            <el-tag type="success"
                    v-else
                    disable-transitions>
              保密
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="introduction" label="简介" align="center"></el-table-column>
        <el-table-column label="用户类型" width="100" align="center">
          <template slot-scope="scope">
            <el-tag type="success"
                    v-if="scope.row.userType === 0"
                    disable-transitions>
              站长
            </el-tag>
            <el-tag type="success"
                    v-else-if="scope.row.userType === 1"
                    style="cursor: pointer"
                    @click.native="editUser(scope.row)"
                    disable-transitions>
              管理员
            </el-tag>
            <el-tag type="success"
                    v-else
                    style="cursor: pointer"
                    @click.native="editUser(scope.row)"
                    disable-transitions>
              普通用户
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" align="center"></el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template slot-scope="scope">
            <el-button v-if="canDeleteUser(scope.row)"
                       type="text"
                       icon="el-icon-delete"
                       style="color: var(--orangeRed)"
                       @click="deleteUser(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination background layout="total, prev, pager, next"
                       :current-page="pagination.current"
                       :page-size="pagination.size"
                       :total="pagination.total"
                       @current-change="handlePageChange">
        </el-pagination>
      </div>
    </div>

    <!-- 编辑弹出框 -->
    <el-dialog title="修改用户类型"
               :visible.sync="editVisible"
               width="30%"
               custom-class="centered-dialog"
               :before-close="handleClose"
               :append-to-body="true"
               destroy-on-close
               center>
      <div class="myCenter">
        <el-radio-group v-model="changeUser.userType">
          <el-radio-button :label="1">管理员</el-radio-button>
          <el-radio-button :label="2">普通用户</el-radio-button>
        </el-radio-group>
      </div>

      <span slot="footer" class="dialog-footer">
          <el-button @click="handleClose()">取 消</el-button>
          <el-button type="primary" @click="saveEdit()">确 定</el-button>
        </span>
    </el-dialog>

    <el-dialog title="修改个人信息"
               :visible.sync="profileVisible"
               width="50%"
               custom-class="centered-dialog"
               :append-to-body="true"
               destroy-on-close
               center>
      <el-form label-width="80px" style="padding: 0 20px;">
        <el-form-item label="头像">
          <uploadPicture :prefix="'userAvatar'" @addPicture="addAvatar" :maxSize="1" :maxNumber="1"></uploadPicture>
          <el-image style="width: 60px; height: 60px; margin-top: 10px; border-radius: 5px;" 
                    v-if="myProfile.avatar" 
                    :src="myProfile.avatar" 
                    fit="cover"></el-image>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="myProfile.username" maxlength="30"></el-input>
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="myProfile.gender">
            <el-radio :label="0">薛定谔的猫</el-radio>
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="myProfile.introduction" type="textarea" maxlength="60" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="myProfile.email" style="width: 70%; margin-right: 10px;"></el-input>
          <el-button type="primary" :disabled="codeString !== '验证码'" @click="sendCode()">{{ codeString }}</el-button>
        </el-form-item>
        <el-form-item label="验证码" v-show="mailChanged">
          <el-input v-model="myProfile.code" placeholder="输入邮箱验证码"></el-input>
        </el-form-item>
        <el-form-item label="密码" v-show="mailChanged && mainStore.currentUser && !mainStore.currentUser.platformType">
          <el-input v-model="myProfile.password" type="password" placeholder="为了安全，修改邮箱需验证密码" show-password></el-input>
        </el-form-item>
      </el-form>

      <span slot="footer" class="dialog-footer">
          <el-button @click="profileVisible = false">取 消</el-button>
          <el-button type="primary" @click="saveProfile()">确 定</el-button>
      </span>
    </el-dialog>

    <!-- 修改密码弹出框 -->
    <el-dialog title="修改密码"
               :visible.sync="passwordVisible"
               width="40%"
               custom-class="centered-dialog"
               :append-to-body="true"
               destroy-on-close
               center>
      <el-form label-width="100px" style="padding: 0 20px;">
        <el-form-item label="旧密码">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入当前密码" show-password></el-input>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password></el-input>
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password></el-input>
        </el-form-item>
        <el-form-item label="邮箱验证码">
          <el-input v-model="passwordForm.code" placeholder="输入邮箱验证码" style="width: 65%; margin-right: 10px;"></el-input>
          <el-button type="primary" :disabled="pwdCodeString !== '验证码'" @click="sendPwdCode()">{{ pwdCodeString }}</el-button>
        </el-form-item>
      </el-form>

      <span slot="footer" class="dialog-footer">
          <el-button @click="passwordVisible = false">取 消</el-button>
          <el-button type="primary" @click="savePassword()">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>

    import { useMainStore } from '@/stores/main';
    const uploadPicture = () => import("../common/uploadPicture");

export default {
    components: {
      uploadPicture
    },
    data() {
      return {
        pagination: {
          current: 1,
          size: 10,
          total: 0,
          searchKey: "",
          userStatus: null,
          userType: null,
          isThirdPartyUser: null
        },
        users: [],
        changeUser: {
          id: null,
          userType: null
        },
        editVisible: false,
        profileVisible: false,
        passwordVisible: false,
        passwordForm: {
          oldPassword: '',
          newPassword: '',
          confirmPassword: '',
          code: ''
        },
        myProfile: {
          username: '',
          gender: null,
          introduction: '',
          avatar: '',
          email: '',
          password: '',
          code: ''
        },
        originalEmail: '',
        codeString: "验证码",
        pwdCodeString: "验证码"
      }
    },

    computed: {
      mainStore() {
        return useMainStore();
      },
      mailChanged() {
        return this.myProfile.email !== this.originalEmail;
      }
    },

    watch: {},

    created() {
      this.getUsers();
    },

    mounted() {
    },

    methods: {
      clearSearch() {
        this.pagination = {
          current: 1,
          size: 10,
          total: 0,
          searchKey: "",
          userStatus: null,
          userType: null,
          isThirdPartyUser: null
        }
        this.getUsers();
      },
      getUsers() {
        this.$http.post(this.$constant.baseURL + "/admin/user/list", this.pagination, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.users = res.data.records;
              this.pagination.total = res.data.total;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      handleUserStatusChange(user, newStatus) {
        // 先保存原始状态，以便失败时恢复
        const originalStatus = user.userStatus;

        // 立即更新UI显示
        user.userStatus = newStatus;

        // 调用API更新后端状态
        this.$http.get(this.$constant.baseURL + "/admin/user/changeUserStatus", {
          userId: user.id,
          flag: newStatus  // 传递新的状态值
        }, true)
          .then((res) => {
            this.$message({
              message: newStatus ? "用户已启用！" : "用户已禁用！",
              type: "success"
            });
          })
          .catch((error) => {
            // 如果API调用失败，恢复原来的状态
            user.userStatus = originalStatus;
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      handleUserStatusToggle(user) {
        // 当开关被切换时，userStatus已经是新值了
        // 我们需要调用API来同步后端状态
        const newStatus = user.userStatus;

        this.$http.get(this.$constant.baseURL + "/admin/user/changeUserStatus", {
          userId: user.id,
          flag: newStatus
        }, true)
          .then((res) => {
            this.$message({
              message: newStatus ? "用户已启用！" : "用户已禁用！",
              type: "success"
            });
          })
          .catch((error) => {
            // 如果API调用失败，恢复原来的状态
            user.userStatus = !newStatus;
            this.$message({
              message: "状态修改失败：" + error.message,
              type: "error"
            });
          });
      },
      changeUserStatus(user) {
        // 使用当前的userStatus值，因为@change事件在值改变后触发
        const newStatus = user.userStatus;

        this.$http.get(this.$constant.baseURL + "/admin/user/changeUserStatus", {
          userId: user.id,
          flag: newStatus  // 传递新的状态值
        }, true)
          .then((res) => {
            this.$message({
              message: newStatus ? "用户已启用！" : "用户已禁用！",
              type: "success"
            });
          })
          .catch((error) => {
            // 如果请求失败，恢复原来的状态
            user.userStatus = !newStatus;
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      changeUserAdmire(user) {
        if (!this.$common.isEmpty(user.admire)) {
          this.$confirm('确认保存？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'success',
            center: true
          }).then(() => {
            this.$http.get(this.$constant.baseURL + "/admin/user/changeUserAdmire", {
              userId: user.id,
              admire: user.admire
            }, true)
              .then((res) => {
                this.$message({
                  message: "修改成功！",
                  type: "success"
                });
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
        }
      },
      editUser(user) {
        this.changeUser.id = user.id;
        this.changeUser.userType = user.userType;
        this.editVisible = true;
      },
      handlePageChange(val) {
        this.pagination.current = val;
        this.getUsers();
      },
      searchUser() {
        this.pagination.total = 0;
        this.pagination.current = 1;
        this.getUsers();
      },
      handleClose() {
        this.changeUser = {
          id: null,
          userType: null
        };
        this.editVisible = false;
      },
      saveEdit() {
        this.$http.get(this.$constant.baseURL + "/admin/user/changeUserType", {
          userId: this.changeUser.id,
          userType: this.changeUser.userType
        }, true)
          .then((res) => {
            this.handleClose();
            this.getUsers();
            this.$message({
              message: "修改成功！",
              type: "success"
            });
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      canDeleteUser(user) {
        const currentAdmin = this.mainStore.currentAdmin;
        if (!user || user.userType === 0) {
          return false;
        }
        return !currentAdmin || user.id !== currentAdmin.id;
      },
      deleteUser(user) {
        if (!this.canDeleteUser(user)) {
          return;
        }

        this.$confirm('删除后该用户将无法登录，历史评论和聊天记录会显示为“用户已注销”。确认删除？', '删除用户', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning',
          center: true
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/admin/user/deleteUser", {
            userId: user.id
          }, true)
            .then(() => {
              this.$message({
                message: "删除成功！",
                type: "success"
              });
              if (this.users.length === 1 && this.pagination.current > 1) {
                this.pagination.current--;
              }
              this.getUsers();
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
            message: '已取消删除!'
          });
        });
      },
      openProfileDialog() {
        const u = this.mainStore.currentUser;
        if (!u) {
          this.$message.error("未获取到当前用户信息");
          return;
        }
        this.myProfile = {
          username: u.username,
          gender: u.gender,
          introduction: u.introduction,
          avatar: u.avatar,
          email: u.email || '',
          password: '',
          code: ''
        };
        this.originalEmail = u.email || '';
        this.codeString = "验证码";
        this.profileVisible = true;
      },
      addAvatar(res) {
        this.myProfile.avatar = res;
      },
      sendCode() {
        if (this.myProfile.email === '') {
          this.$message.error("请输入邮箱！");
          return;
        }
        if (!(/^\w+@[a-zA-Z0-9]{2,10}(?:\.[a-z]{2,4}){1,3}$/.test(this.myProfile.email))) {
          this.$message.error("邮箱格式有误！");
          return;
        }
        this.codeString = "发送中...";
        this.$http.get(this.$constant.baseURL + "/user/getCodeForBind", {
          place: this.myProfile.email,
          flag: 2
        })
          .then((res) => {
            this.$message.success("验证码已发送，请注意查收！");
            let time = 300;
            let timer = setInterval(() => {
              if (time === 0) {
                clearInterval(timer);
                this.codeString = "验证码";
              } else {
                this.codeString = time + " s";
                time--;
              }
            }, 1000);
          })
          .catch((error) => {
            this.codeString = "验证码";
            this.$message.error(error.message);
          });
      },
      async saveProfile() {
        if (this.$common.isEmpty(this.myProfile.username)) {
          this.$message.error("请输入用户名！");
          return;
        }

        let user = {
          username: this.myProfile.username,
          gender: this.myProfile.gender,
          introduction: this.myProfile.introduction,
          avatar: this.myProfile.avatar
        };

        // 更新基础信息
        try {
          let res = await this.$http.post(this.$constant.baseURL + "/user/updateUserInfo", user);
          if (!this.$common.isEmpty(res.data)) {
            this.mainStore.loadCurrentUser(res.data);
          }
        } catch (error) {
          this.$message.error(error.message);
          return;
        }

        // 修改邮箱
        if (this.mailChanged) {
          if (this.$common.isEmpty(this.myProfile.code)) {
            this.$message.error("请输入邮箱验证码！");
            return;
          }
          if (this.mainStore.currentUser && !this.mainStore.currentUser.platformType && this.$common.isEmpty(this.myProfile.password)) {
            this.$message.error("修改邮箱需要输入密码验证身份！");
            return;
          }
          try {
            let params = {
              code: this.myProfile.code.trim(),
              password: (this.mainStore.currentUser && this.mainStore.currentUser.platformType) ? '' : await this.$common.encrypt(this.myProfile.password.trim()),
              place: this.myProfile.email.trim(),
              flag: 2
            };
            let res2 = await this.$http.post(this.$constant.baseURL + "/user/updateSecretInfo", params, false, false);
            if (!this.$common.isEmpty(res2.data)) {
              this.mainStore.loadCurrentUser(res2.data);
            }
          } catch(error) {
            this.$message.error("邮箱修改失败：" + error.message);
            return;
          }
        }

        this.$message.success("保存成功！");
        this.profileVisible = false;
        this.getUsers(); // Refresh table
      },
      openPasswordDialog() {
        if (!this.mainStore.currentUser || this.$common.isEmpty(this.mainStore.currentUser.email)) {
          this.$message.error("安全起见，请先在【修改个人信息】中绑定邮箱后再修改密码！");
          return;
        }

        this.passwordForm = {
          oldPassword: '',
          newPassword: '',
          confirmPassword: '',
          code: ''
        };
        this.pwdCodeString = "验证码";
        this.passwordVisible = true;
      },
      sendPwdCode() {
        this.pwdCodeString = "发送中...";
        this.$http.get(this.$constant.baseURL + "/user/getCode", {
          flag: 2
        })
          .then((res) => {
            this.$message.success("验证码已发送，请注意查收！");
            let time = 300;
            let timer = setInterval(() => {
              if (time === 0) {
                clearInterval(timer);
                this.pwdCodeString = "验证码";
              } else {
                this.pwdCodeString = time + " s";
                time--;
              }
            }, 1000);
          })
          .catch((error) => {
            this.pwdCodeString = "验证码";
            this.$message.error(error.message);
          });
      },
      async savePassword() {
        if (this.$common.isEmpty(this.passwordForm.oldPassword)) {
          this.$message.error("请输入旧密码！");
          return;
        }
        if (this.$common.isEmpty(this.passwordForm.newPassword)) {
          this.$message.error("请输入新密码！");
          return;
        }
        if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
          this.$message.error("两次输入的新密码不一致！");
          return;
        }
        if (this.$common.isEmpty(this.passwordForm.code)) {
          this.$message.error("请输入邮箱验证码！");
          return;
        }

        try {
          let params = {
            place: await this.$common.encrypt(this.passwordForm.oldPassword.trim()),
            password: await this.$common.encrypt(this.passwordForm.newPassword.trim()),
            code: this.passwordForm.code.trim(),
            flag: 3
          };
          
          let res = await this.$http.post(this.$constant.baseURL + "/user/updateSecretInfo", params, false, false);
          
          if (!this.$common.isEmpty(res.data)) {
            this.$message.success("密码修改成功，请使用新密码重新登录！");
            this.passwordVisible = false;
            
            // 清理本地状态（token由后端通过cookie管理）
            this.mainStore.loadCurrentUser({});
            this.mainStore.loadCurrentAdmin({});
            
            // 刷新页面以触发路由守卫，在后台弹出登录框
            window.location.reload();
          }
        } catch(error) {
          this.$message.error("密码修改失败：" + error.message);
        }
      }
    }
  }
</script>

<style scoped>

  .handle-box {
    margin-bottom: 20px;
  }

  .handle-select {
    width: 120px;
  }

  .handle-input {
    width: 160px;
    display: inline-block;
  }

  .table {
    width: 100%;
    font-size: 14px;
  }

  .mrb10 {
    margin-right: 10px;
    margin-bottom: 10px;
  }

  .table-td-thumb {
    display: block;
    margin: auto;
    width: 40px;
    height: 40px;
  }

  .pagination {
    margin: 20px 0;
    text-align: right;
  }

  .el-switch {
    margin: 5px;
  }

  /* ===========================================
     表单移动端样式 - PC端和移动端响应式
     =========================================== */
  
  /* PC端样式 - 768px以上 */
  @media screen and (min-width: 769px) {
    ::v-deep .el-form-item__label {
      float: left !important;
    }
  }

  /* 移动端样式 - 768px及以下 */
  @media screen and (max-width: 768px) {
    /* 表单标签 - 垂直布局 */
    ::v-deep .el-form-item__label {
      float: none !important;
      width: 100% !important;
      text-align: left !important;
      margin-bottom: 8px !important;
      font-weight: 500 !important;
      font-size: 14px !important;
      padding-bottom: 0 !important;
      line-height: 1.5 !important;
    }

    ::v-deep .el-form-item__content {
      margin-left: 0 !important;
      width: 100% !important;
    }

    ::v-deep .el-form-item {
      margin-bottom: 20px !important;
    }

    /* 输入框移动端优化 */
    ::v-deep .el-input__inner {
      font-size: 16px !important;
      height: 44px !important;
      border-radius: 8px !important;
    }

    ::v-deep .el-textarea__inner {
      font-size: 16px !important;
      border-radius: 8px !important;
    }

    /* 选择器移动端优化 */
    ::v-deep .el-select {
      width: 100% !important;
    }

    ::v-deep .el-select .el-input__inner {
      height: 44px !important;
      line-height: 44px !important;
    }

    /* 按钮移动端优化 */
    ::v-deep .el-button {
      min-height: 40px !important;
      border-radius: 8px !important;
    }

    /* 对话框移动端优化 */
    ::v-deep .el-dialog {
      width: 95% !important;
      margin-top: 5vh !important;
    }

    ::v-deep .el-dialog__body {
      padding: 15px !important;
    }

    /* 搜索框移动端优化 */
    .handle-input {
      width: 100% !important;
      margin-bottom: 10px !important;
    }

    .handle-select {
      width: 100% !important;
      margin-bottom: 10px !important;
    }
  }

  /* 极小屏幕优化 - 480px及以下 */
  @media screen and (max-width: 480px) {
    ::v-deep .el-form-item__label {
      font-size: 13px !important;
    }

    ::v-deep .el-input__inner,
    ::v-deep .el-select .el-input__inner {
      height: 40px !important;
      line-height: 40px !important;
      font-size: 15px !important;
    }

    ::v-deep .el-button {
      min-height: 38px !important;
      font-size: 14px !important;
    }
  }
</style>
