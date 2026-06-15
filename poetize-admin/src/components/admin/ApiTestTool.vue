<template>
  <div class="api-test-tool">
    <el-tabs v-model="activeTab" type="card">
      <el-tab-pane label="创建文章" name="create">
        <el-form :model="createForm" label-width="120px" size="small">
          <el-form-item label="文章标题">
            <el-input v-model="createForm.title"></el-input>
          </el-form-item>
          <el-form-item label="文章内容">
            <el-input v-model="createForm.content" type="textarea" :rows="5"></el-input>
          </el-form-item>
          <el-form-item label="分类名称">
            <el-input v-model="createForm.sortName"></el-input>
          </el-form-item>
          <el-form-item label="标签名称">
            <el-input v-model="createForm.labelName"></el-input>
          </el-form-item>
          <el-form-item label="封面URL">
            <el-input v-model="createForm.cover" :disabled="createForm.coverBlank"></el-input>
          </el-form-item>
          <el-form-item label="空封面占位">
            <el-switch v-model="createForm.coverBlank"></el-switch>
          </el-form-item>
          <el-form-item label="可见">
            <el-switch v-model="createForm.viewStatus"></el-switch>
          </el-form-item>
          <el-form-item label="评论">
            <el-switch v-model="createForm.commentStatus"></el-switch>
          </el-form-item>
          <el-form-item label="推荐">
            <el-switch v-model="createForm.recommendStatus"></el-switch>
          </el-form-item>
          <el-form-item label="推送搜索">
            <el-switch v-model="createForm.submitToSearchEngine"></el-switch>
          </el-form-item>
          <el-form-item label="付费类型">
            <el-select v-model="createForm.payType" style="width: 100%;">
              <el-option :value="0" label="免费"></el-option>
              <el-option :value="1" label="按文章付费"></el-option>
              <el-option :value="2" label="会员专属"></el-option>
              <el-option :value="3" label="赞赏解锁"></el-option>
              <el-option :value="4" label="固定金额解锁"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-if="createForm.payType === 4" label="金额">
            <el-input-number v-model="createForm.payAmount" :min="0.01" :precision="2"></el-input-number>
          </el-form-item>
          <el-form-item v-if="createForm.payType !== 0" label="免费预览%">
            <el-slider v-model="createForm.freePercent" :min="0" :max="100" :step="5" show-input></el-slider>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testCreateArticle">测试创建</el-button>
            <el-button :loading="loading" @click="testCreateArticleAsync">测试异步创建</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="支付插件" name="payment">
        <el-form label-width="140px" size="small">
          <el-form-item label="当前激活插件">
            <span v-if="paymentPluginActive">{{ paymentPluginName }} ({{ activePaymentPluginKey }})</span>
            <span v-else>未激活</span>
            <el-button size="mini" style="margin-left: 10px;" :loading="loading" @click="refreshPaymentPluginOverview(true)">刷新</el-button>
          </el-form-item>
          <el-form-item label="目标插件">
            <el-select v-model="selectedPaymentPluginKey" style="width: 320px;" @change="handlePaymentPluginChange">
              <el-option
                v-for="item in paymentPlugins"
                :key="item.pluginKey"
                :label="item.pluginName + ' (' + item.pluginKey + ')'"
                :value="item.pluginKey">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedPaymentPluginDetail && selectedPaymentPluginDetail.missingFields && selectedPaymentPluginDetail.missingFields.length" label="缺失字段">
            <span class="warning-text">{{ selectedPaymentPluginDetail.missingFields.join(', ') }}</span>
          </el-form-item>
          <el-form-item v-if="selectedPaymentPluginDetail" label="pluginConfig(JSON)">
            <el-input v-model="paymentRawConfigText" type="textarea" :rows="8"></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testSelectedPaymentPluginConnection">测试连接</el-button>
            <el-button type="success" :loading="loading" @click="saveSelectedPaymentPluginConfig">保存并激活</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="文章运营" name="articles">
        <el-form :inline="true" :model="queryForm" size="small">
          <el-form-item label="页码">
            <el-input-number v-model="queryForm.current" :min="1" style="width: 90px;"></el-input-number>
          </el-form-item>
          <el-form-item label="每页">
            <el-input-number v-model="queryForm.size" :min="1" :max="50" style="width: 90px;"></el-input-number>
          </el-form-item>
          <el-form-item label="搜索词">
            <el-input v-model="queryForm.searchKey" style="width: 160px;"></el-input>
          </el-form-item>
          <el-form-item label="分类ID">
            <el-input-number v-model="queryForm.sortId" :min="1" style="width: 120px;"></el-input-number>
          </el-form-item>
          <el-form-item label="标签ID">
            <el-input-number v-model="queryForm.labelId" :min="1" style="width: 120px;"></el-input-number>
          </el-form-item>
          <el-form-item label="精确标题">
            <el-input v-model="queryForm.exactTitle" style="width: 200px;"></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testQueryArticles">查询列表</el-button>
          </el-form-item>
        </el-form>

        <el-form :inline="true" :model="detailForm" size="small" style="margin-top: 15px;">
          <el-form-item label="文章ID">
            <el-input-number v-model="detailForm.id" :min="1" style="width: 150px;"></el-input-number>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testGetArticleDetail">查询详情</el-button>
          </el-form-item>
        </el-form>

        <el-form :inline="true" :model="taskForm" size="small" style="margin-top: 15px;">
          <el-form-item label="任务ID">
            <el-input v-model="taskForm.taskId" style="width: 280px;"></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testGetTaskStatus">查询任务</el-button>
          </el-form-item>
        </el-form>

        <el-divider content-position="left">更新 / 隐藏</el-divider>
        <el-form :model="updateForm" label-width="120px" size="small">
          <el-form-item label="文章ID">
            <el-input-number v-model="updateForm.id" :min="1" style="width: 180px;"></el-input-number>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="updateForm.title" placeholder="留空则保持不变"></el-input>
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="updateForm.content" type="textarea" :rows="4" placeholder="留空则保持不变"></el-input>
          </el-form-item>
          <el-form-item label="分类名称">
            <el-input v-model="updateForm.sortName" placeholder="留空则保持不变"></el-input>
          </el-form-item>
          <el-form-item label="标签名称">
            <el-input v-model="updateForm.labelName" placeholder="留空则保持不变"></el-input>
          </el-form-item>
          <el-form-item label="可见">
            <el-switch v-model="updateForm.viewStatus"></el-switch>
          </el-form-item>
          <el-form-item label="评论">
            <el-switch v-model="updateForm.commentStatus"></el-switch>
          </el-form-item>
          <el-form-item label="推荐">
            <el-switch v-model="updateForm.recommendStatus"></el-switch>
          </el-form-item>
          <el-form-item label="推送搜索">
            <el-switch v-model="updateForm.submitToSearchEngine"></el-switch>
          </el-form-item>
          <el-form-item v-if="updateForm.viewStatus === false" label="密码">
            <el-input v-model="updateForm.password"></el-input>
          </el-form-item>
          <el-form-item v-if="updateForm.viewStatus === false" label="提示">
            <el-input v-model="updateForm.tips"></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testUpdateArticle">异步更新</el-button>
            <el-button type="warning" :loading="loading" @click="testHideArticle">隐藏文章</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="文章主题" name="theme">
        <el-form label-width="120px" size="small">
          <el-form-item label="当前主题">
            <span>{{ activeThemePluginKey || '未激活' }}</span>
            <el-button size="mini" style="margin-left: 10px;" :loading="loading" @click="refreshThemeStatus(true)">刷新</el-button>
          </el-form-item>
          <el-form-item label="目标主题">
            <el-select v-model="selectedThemePluginKey" style="width: 320px;">
              <el-option
                v-for="item in themePlugins"
                :key="item.pluginKey"
                :label="item.pluginName + ' (' + item.pluginKey + ')'"
                :value="item.pluginKey">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="activateSelectedTheme">激活主题</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="数据回看" name="analytics">
        <el-form :inline="true" :model="analyticsForm" size="small">
          <el-form-item label="文章ID">
            <el-input-number v-model="analyticsForm.articleId" :min="1" style="width: 150px;"></el-input-number>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testArticleAnalytics">查询文章复盘</el-button>
          </el-form-item>
        </el-form>
        <el-form :inline="true" :model="siteVisitsForm" size="small" style="margin-top: 15px;">
          <el-form-item label="天数">
            <el-select v-model="siteVisitsForm.days" style="width: 120px;">
              <el-option :value="7" label="近7天"></el-option>
              <el-option :value="30" label="近30天"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="testSiteVisits">查询站点趋势</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="SEO" name="seo">
        <el-form :model="seoForm" label-width="150px" size="small">
          <el-form-item label="操作">
            <el-button type="primary" :loading="loading" @click="fetchSeoStatus(true)">获取状态</el-button>
            <el-button :loading="loading" @click="fetchSeoConfig(true)">获取受控配置</el-button>
            <el-button type="success" :loading="loading" @click="updateSitemap">触发 Sitemap 更新</el-button>
          </el-form-item>
          <el-form-item label="启用SEO">
            <el-switch v-model="seoForm.enable"></el-switch>
          </el-form-item>
          <el-form-item label="网站描述">
            <el-input v-model="seoForm.site_description" type="textarea" :rows="2"></el-input>
          </el-form-item>
          <el-form-item label="网站关键词">
            <el-input v-model="seoForm.site_keywords"></el-input>
          </el-form-item>
          <el-form-item label="默认作者">
            <el-input v-model="seoForm.default_author"></el-input>
          </el-form-item>
          <el-form-item label="OG图片">
            <el-input v-model="seoForm.og_image"></el-input>
          </el-form-item>
          <el-form-item label="站点Logo">
            <el-input v-model="seoForm.site_logo"></el-input>
          </el-form-item>
          <el-form-item label="OG类型">
            <el-input v-model="seoForm.og_type"></el-input>
          </el-form-item>
          <el-form-item label="Twitter Card">
            <el-input v-model="seoForm.twitter_card"></el-input>
          </el-form-item>
          <el-form-item label="Twitter Site">
            <el-input v-model="seoForm.twitter_site"></el-input>
          </el-form-item>
          <el-form-item label="Twitter Creator">
            <el-input v-model="seoForm.twitter_creator"></el-input>
          </el-form-item>
          <el-form-item label="百度推送">
            <el-switch v-model="seoForm.baidu_push_enabled"></el-switch>
          </el-form-item>
          <el-form-item label="Bing推送">
            <el-switch v-model="seoForm.bing_push_enabled"></el-switch>
          </el-form-item>
          <el-form-item label="百度验证">
            <el-input v-model="seoForm.baidu_site_verification"></el-input>
          </el-form-item>
          <el-form-item label="Google验证">
            <el-input v-model="seoForm.google_site_verification"></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="saveSeoConfig">保存受控配置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="分类和标签" name="taxonomy">
        <el-button type="primary" :loading="loading" @click="testGetCategories">获取所有分类</el-button>
        <el-button type="primary" :loading="loading" @click="testGetTags">获取所有标签</el-button>
      </el-tab-pane>
    </el-tabs>

    <div v-if="result">
      <el-divider>响应结果</el-divider>
      <pre class="result-pre">{{ result }}</pre>
    </div>
  </div>
</template>

<script>
function createDefaultSeoForm() {
  return {
    enable: true,
    site_description: '',
    site_keywords: '',
    default_author: '',
    og_image: '',
    site_logo: '',
    og_type: '',
    twitter_card: '',
    twitter_site: '',
    twitter_creator: '',
    baidu_push_enabled: false,
    bing_push_enabled: false,
    baidu_site_verification: '',
    google_site_verification: ''
  }
}

export default {
  name: 'ApiTestTool',
  props: {
    apiKey: { type: String, required: true },
    baseURL: { type: String, required: true }
  },
  data() {
    return {
      activeTab: 'create',
      loading: false,
      result: null,
      paymentPluginActive: false,
      paymentPluginReady: false,
      paymentPluginName: '',
      activePaymentPluginKey: '',
      paymentPlugins: [],
      selectedPaymentPluginKey: '',
      selectedPaymentPluginDetail: null,
      paymentRawConfigText: '{}',
      themePlugins: [],
      activeThemePluginKey: '',
      selectedThemePluginKey: '',
      seoForm: createDefaultSeoForm(),
      createForm: {
        title: '测试文章标题',
        content: '# 这是一个测试文章\n\n这是通过 API 测试工具创建的文章。',
        sortName: '测试分类',
        labelName: '测试标签',
        cover: '',
        coverBlank: false,
        viewStatus: true,
        commentStatus: true,
        recommendStatus: false,
        submitToSearchEngine: false,
        payType: 0,
        payAmount: null,
        freePercent: 30
      },
      queryForm: {
        current: 1,
        size: 10,
        searchKey: '',
        sortId: null,
        labelId: null,
        exactTitle: ''
      },
      detailForm: { id: null },
      taskForm: { taskId: '' },
      updateForm: {
        id: null,
        title: '',
        content: '',
        sortName: '',
        labelName: '',
        viewStatus: true,
        commentStatus: true,
        recommendStatus: false,
        submitToSearchEngine: false,
        password: '',
        tips: ''
      },
      analyticsForm: { articleId: null },
      siteVisitsForm: { days: 7 }
    }
  },
  created() {
    this.refreshPaymentPluginOverview(false)
    this.refreshThemeStatus(false)
    this.fetchSeoStatus(false)
  },
  methods: {
    async requestPublicApi(method, path, payload, params) {
      const url = new URL(this.baseURL + path, window.location.origin)
      if (params) {
        Object.keys(params).forEach((key) => {
          const value = params[key]
          if (value !== undefined && value !== null && value !== '') {
            url.searchParams.set(key, value)
          }
        })
      }

      const options = {
        method,
        headers: {
          Accept: 'application/json',
          'X-API-KEY': this.apiKey
        }
      }

      if (payload !== undefined) {
        options.headers['Content-Type'] = 'application/json;charset=UTF-8'
        options.body = JSON.stringify(payload)
      }

      const response = await fetch(url.toString(), options)
      const text = await response.text()
      let data = {}
      try {
        data = text ? JSON.parse(text) : {}
      } catch (error) {
        data = { code: response.status, message: text || response.statusText }
      }

      if (!response.ok || data.code !== 200) {
        throw data
      }
      return data
    },

    setResult(payload) {
      this.result = JSON.stringify(payload, null, 2)
    },

    buildCreatePayload() {
      const payload = {
        title: this.createForm.title,
        content: this.createForm.content,
        sortName: this.createForm.sortName,
        labelName: this.createForm.labelName,
        viewStatus: this.createForm.viewStatus,
        commentStatus: this.createForm.commentStatus,
        recommendStatus: this.createForm.recommendStatus,
        submitToSearchEngine: this.createForm.submitToSearchEngine,
        payType: this.createForm.payType
      }
      if (this.createForm.coverBlank) {
        payload.cover = ' '
      } else if (this.createForm.cover) {
        payload.cover = this.createForm.cover
      }
      if (this.createForm.payType === 4) {
        payload.payAmount = this.createForm.payAmount
      }
      if (this.createForm.payType !== 0) {
        payload.freePercent = this.createForm.freePercent
      }
      return payload
    },

    buildUpdatePayload() {
      const payload = {
        id: this.updateForm.id,
        viewStatus: this.updateForm.viewStatus,
        commentStatus: this.updateForm.commentStatus,
        recommendStatus: this.updateForm.recommendStatus,
        submitToSearchEngine: this.updateForm.submitToSearchEngine
      }
      if (this.updateForm.title) payload.title = this.updateForm.title
      if (this.updateForm.content) payload.content = this.updateForm.content
      if (this.updateForm.sortName) payload.sortName = this.updateForm.sortName
      if (this.updateForm.labelName) payload.labelName = this.updateForm.labelName
      if (this.updateForm.viewStatus === false) {
        payload.password = this.updateForm.password || `hidden-${this.updateForm.id}`
        payload.tips = this.updateForm.tips || '文章已隐藏，仅供受控预览'
      }
      return payload
    },

    async refreshPaymentPluginOverview(showResult) {
      try {
        const res = await this.requestPublicApi('GET', '/api/payment/plugin/status')
        const data = res.data || {}
        this.paymentPlugins = Array.isArray(data.plugins) ? data.plugins : []
        this.activePaymentPluginKey = data.activePluginKey || ''
        const activePlugin = this.paymentPlugins.find((item) => item.active) || null
        const targetPlugin = data.targetPlugin || null
        this.paymentPluginActive = !!activePlugin
        this.paymentPluginName = activePlugin ? (activePlugin.pluginName || activePlugin.pluginKey) : ''
        this.paymentPluginReady = !!(targetPlugin && targetPlugin.active && targetPlugin.enabled && targetPlugin.configured)
        if (!this.selectedPaymentPluginKey && this.paymentPlugins.length > 0) {
          this.selectedPaymentPluginKey = this.activePaymentPluginKey || this.paymentPlugins[0].pluginKey
        }
        if (targetPlugin && targetPlugin.pluginKey === this.selectedPaymentPluginKey) {
          this.selectedPaymentPluginDetail = targetPlugin
          this.paymentRawConfigText = JSON.stringify(targetPlugin.nonSecretConfigPreview || {}, null, 2)
        } else if (this.selectedPaymentPluginKey) {
          const selectedRes = await this.requestPublicApi('GET', '/api/payment/plugin/status', undefined, {
            pluginKey: this.selectedPaymentPluginKey
          })
          this.selectedPaymentPluginDetail = selectedRes.data ? selectedRes.data.targetPlugin : null
          this.paymentRawConfigText = JSON.stringify(
            this.selectedPaymentPluginDetail && this.selectedPaymentPluginDetail.nonSecretConfigPreview
              ? this.selectedPaymentPluginDetail.nonSecretConfigPreview
              : {},
            null,
            2
          )
        }
        if (showResult) this.setResult(res)
      } catch (error) {
        if (showResult) this.setResult(error)
      }
    },

    async handlePaymentPluginChange(pluginKey) {
      if (!pluginKey) return
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/payment/plugin/status', undefined, { pluginKey })
        this.selectedPaymentPluginDetail = res.data ? res.data.targetPlugin : null
        this.paymentRawConfigText = JSON.stringify(
          this.selectedPaymentPluginDetail && this.selectedPaymentPluginDetail.nonSecretConfigPreview
            ? this.selectedPaymentPluginDetail.nonSecretConfigPreview
            : {},
          null,
          2
        )
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    parsePaymentRawConfig() {
      try {
        const parsed = JSON.parse(this.paymentRawConfigText || '{}')
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          throw new Error('pluginConfig 必须是对象')
        }
        return parsed
      } catch (error) {
        this.$message.warning('pluginConfig 不是合法的 JSON 对象')
        return null
      }
    },

    async testSelectedPaymentPluginConnection() {
      if (!this.selectedPaymentPluginKey) {
        this.$message.warning('请先选择插件')
        return
      }
      const pluginConfig = this.parsePaymentRawConfig()
      if (!pluginConfig) return
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/payment/plugin/testConnection', {
          pluginKey: this.selectedPaymentPluginKey,
          pluginConfig
        })
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async saveSelectedPaymentPluginConfig() {
      if (!this.selectedPaymentPluginKey) {
        this.$message.warning('请先选择插件')
        return
      }
      const pluginConfig = this.parsePaymentRawConfig()
      if (!pluginConfig) return
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/payment/plugin/configure', {
          pluginKey: this.selectedPaymentPluginKey,
          pluginConfig,
          activate: true
        })
        this.setResult(res)
        await this.refreshPaymentPluginOverview(false)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testCreateArticle() {
      if (this.createForm.payType !== 0 && !this.paymentPluginReady) {
        this.$message.warning('当前没有可用的支付插件，请先完成 payment 配置')
        return
      }
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/article/create', this.buildCreatePayload())
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testCreateArticleAsync() {
      if (this.createForm.payType !== 0 && !this.paymentPluginReady) {
        this.$message.warning('当前没有可用的支付插件，请先完成 payment 配置')
        return
      }
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/article/createAsync', this.buildCreatePayload())
        this.setResult(res)
        if (res.data && res.data.taskId) this.taskForm.taskId = res.data.taskId
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testQueryArticles() {
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/article/list', undefined, {
          current: this.queryForm.current,
          size: this.queryForm.size,
          searchKey: this.queryForm.searchKey || undefined,
          sortId: this.queryForm.sortId || undefined,
          labelId: this.queryForm.labelId || undefined
        })
        if (this.queryForm.exactTitle && res.data && Array.isArray(res.data.records)) {
          const matched = res.data.records.filter((item) => (item.articleTitle || '').trim() === this.queryForm.exactTitle.trim())
          this.setResult({ code: 200, message: null, data: { matched: matched.length, records: matched } })
          return
        }
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    applyDetailToUpdateForm(article) {
      if (!article) return
      this.updateForm.id = article.id || null
      this.updateForm.title = article.articleTitle || ''
      this.updateForm.content = article.articleContent || ''
      this.updateForm.sortName = article.sortName || ''
      this.updateForm.labelName = article.labelName || ''
      this.updateForm.viewStatus = article.viewStatus !== false
      this.updateForm.commentStatus = article.commentStatus !== false
      this.updateForm.recommendStatus = article.recommendStatus === true
      this.updateForm.submitToSearchEngine = article.submitToSearchEngine === true
      this.updateForm.password = article.password || ''
      this.updateForm.tips = article.tips || ''
    },

    async testGetArticleDetail() {
      if (!this.detailForm.id) {
        this.$message.warning('文章ID不能为空')
        return
      }
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/article/' + this.detailForm.id)
        this.setResult(res)
        this.applyDetailToUpdateForm(res.data)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testGetTaskStatus() {
      if (!this.taskForm.taskId) {
        this.$message.warning('任务ID不能为空')
        return
      }
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/article/task/' + this.taskForm.taskId)
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testUpdateArticle() {
      if (!this.updateForm.id) {
        this.$message.warning('文章ID不能为空')
        return
      }
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/article/updateAsync', this.buildUpdatePayload())
        this.setResult(res)
        if (res.data && res.data.taskId) this.taskForm.taskId = res.data.taskId
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testHideArticle() {
      this.updateForm.viewStatus = false
      await this.testUpdateArticle()
    },

    async refreshThemeStatus(showResult) {
      try {
        const res = await this.requestPublicApi('GET', '/api/article-theme/status')
        const data = res.data || {}
        this.themePlugins = Array.isArray(data.plugins) ? data.plugins : []
        this.activeThemePluginKey = data.activePluginKey || ''
        if (!this.selectedThemePluginKey && this.themePlugins.length > 0) {
          this.selectedThemePluginKey = this.activeThemePluginKey || this.themePlugins[0].pluginKey
        }
        if (showResult) this.setResult(res)
      } catch (error) {
        if (showResult) this.setResult(error)
      }
    },

    async activateSelectedTheme() {
      if (!this.selectedThemePluginKey) {
        this.$message.warning('请先选择主题')
        return
      }
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/article-theme/activate', {
          pluginKey: this.selectedThemePluginKey
        })
        this.setResult(res)
        await this.refreshThemeStatus(false)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testArticleAnalytics() {
      if (!this.analyticsForm.articleId) {
        this.$message.warning('文章ID不能为空')
        return
      }
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/article/analytics/' + this.analyticsForm.articleId)
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testSiteVisits() {
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/analytics/site/visits', undefined, {
          days: this.siteVisitsForm.days
        })
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async fetchSeoStatus(showResult) {
      try {
        const res = await this.requestPublicApi('GET', '/api/seo/status')
        if (showResult) this.setResult(res)
      } catch (error) {
        if (showResult) this.setResult(error)
      }
    },

    async fetchSeoConfig(showResult) {
      try {
        const res = await this.requestPublicApi('GET', '/api/seo/config')
        this.seoForm = Object.assign(createDefaultSeoForm(), res.data || {})
        if (showResult) this.setResult(res)
      } catch (error) {
        if (showResult) this.setResult(error)
      }
    },

    async saveSeoConfig() {
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/seo/config', this.seoForm)
        this.setResult(res)
        this.seoForm = Object.assign(createDefaultSeoForm(), res.data || {})
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async updateSitemap() {
      this.loading = true
      try {
        const res = await this.requestPublicApi('POST', '/api/seo/sitemap/update', {})
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testGetCategories() {
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/categories')
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    },

    async testGetTags() {
      this.loading = true
      try {
        const res = await this.requestPublicApi('GET', '/api/tags')
        this.setResult(res)
      } catch (error) {
        this.setResult(error)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.api-test-tool {
  margin-top: 10px;
}

.warning-text {
  color: #e6a23c;
}

.result-pre {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
