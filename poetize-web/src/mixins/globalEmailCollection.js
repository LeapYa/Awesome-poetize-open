import { mapStores } from 'pinia'
import { useMainStore } from '../stores/main'

export default {
  data() {
    return {
      showGlobalEmailCollection: false,
      tempUserData: null,
      emailCollectionProvider: '',
    }
  },
  computed: {
    ...mapStores(useMainStore),
  },
  created() {
    // 检查是否需要显示邮箱收集模态框
    this.checkEmailCollectionNeeded()
  },
  methods: {
    checkEmailCollectionNeeded() {
      // 检查URL参数
      if (this.$route.query.showEmailCollection === 'true') {
        const tempUserDataStr = localStorage.getItem('tempUserData')

        if (tempUserDataStr) {
          try {
            this.tempUserData = JSON.parse(tempUserDataStr)

            if (this.tempUserData.needsEmailCollection) {
              this.emailCollectionProvider =
                this.tempUserData.provider || 'unknown'
              this.showGlobalEmailCollection = true

              // 清除URL参数
              this.$router.replace({
                path: this.$route.path,
                query: { ...this.$route.query, showEmailCollection: undefined },
              })
            }
          } catch (error) {
            console.error('解析临时用户数据失败:', error)
            localStorage.removeItem('tempUserData')
          }
        }
      }
    },

    async handleEmailCollectionComplete(result) {
      try {
        // 隐藏模态框
        this.showGlobalEmailCollection = false

        // 如果用户提供了邮箱，合并到store中的完整用户信息（登录时已由/user/token填充）
        if (result.email && !result.skipped) {
          this.mainStore.loadCurrentUser({
            ...this.mainStore.currentUser,
            email: result.email,
          })
        }

        // 清除临时数据
        localStorage.removeItem('tempUserData')

        // 显示欢迎消息
        const platformName = this.getPlatformName(this.emailCollectionProvider)
        if (result.skipped) {
          this.$message.success(
            `欢迎通过 ${platformName} 登录！您可以稍后在个人设置中添加邮箱。`
          )
        } else {
          this.$message.success(`欢迎通过 ${platformName} 登录！邮箱已保存。`)
        }

        // 获取原始重定向路径并跳转
        const redirectPath =
          this.$route.query.redirect ||
          sessionStorage.getItem('oauthRedirectPath') ||
          '/'
        sessionStorage.removeItem('oauthRedirectPath')

        if (redirectPath !== this.$route.path) {
          this.$router.replace({
            path: redirectPath,
            query: {},
            replace: true,
          })
        }
      } catch (error) {
        console.error('完成邮箱收集流程时出错:', error)
        this.$message.error('登录过程中出现问题，但您已成功登录')

        // 即使出错也要清理临时数据，登录态已由store中的完整用户信息保障
        localStorage.removeItem('tempUserData')
      }
    },

    getPlatformName(provider) {
      const platformNames = {
        gitee: 'Gitee',
        github: 'GitHub',
        google: 'Google',
        yandex: 'Yandex',
        x: 'Twitter',
        qq: 'QQ',
        baidu: 'Baidu',
      }
      return platformNames[provider] || provider
    },
  },
}
