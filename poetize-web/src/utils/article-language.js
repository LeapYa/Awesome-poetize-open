import { getArticlePath } from './article-url'

export function setupLanguageSwitchEventDelegation() {
  if (this.languageSwitchHandler) {
    document.removeEventListener('click', this.languageSwitchHandler, true)
    document.removeEventListener('touchend', this.languageSwitchHandler, true)
    document.removeEventListener('mousedown', this.languageSwitchHandler, true)
    document.removeEventListener('touchstart', this.languageSwitchHandler, true)
  }

  this.languageSwitchHandler = (event) => {
    const button = event.target.closest(
      '.article-language-switch .el-button[data-lang]'
    )
    if (button && !button.disabled) {
      event.preventDefault()
      event.stopPropagation()
      event.stopImmediatePropagation()

      const langCode = button.getAttribute('data-lang')
      if (langCode) {
        this.handleLanguageSwitch(langCode)
      }
      return false
    }
  }

  document.addEventListener('click', this.languageSwitchHandler, true)
  document.addEventListener('touchend', this.languageSwitchHandler, true)
  document.addEventListener('mousedown', this.languageSwitchHandler, true)
  document.addEventListener('touchstart', this.languageSwitchHandler, true)

  this.$nextTick(() => {
    const buttons = document.querySelectorAll(
      '.article-language-switch .el-button[data-lang]'
    )
    buttons.forEach((button) => {
      button.addEventListener(
        'click',
        (e) => {
          e.preventDefault()
          e.stopPropagation()
          const langCode = button.getAttribute('data-lang')
          if (langCode) {
            this.handleLanguageSwitch(langCode)
          }
        },
        true
      )
    })
  })
}

export function handleMouseDown(event) {
  event.preventDefault()
  event.stopPropagation()
  const langCode = event.target.closest('[data-lang]')?.getAttribute('data-lang')
  if (langCode) {
    this.handleLanguageSwitch(langCode)
  }
}

export function handleTouchStart(event) {
  event.preventDefault()
  event.stopPropagation()
  const langCode = event.target.closest('[data-lang]')?.getAttribute('data-lang')
  if (langCode) {
    this.handleLanguageSwitch(langCode)
  }
}

export async function handleLanguageSwitch(lang) {
  if (lang === this.currentLang) {
    return
  }

  const isLanguageAvailable = this.availableLanguageButtons.some(
    (btn) => btn.code === lang
  )
  if (!isLanguageAvailable) {
    this.$message.warning('该语言版本暂不可用')
    return
  }

  await this.switchLanguage(lang)
}

export async function switchLanguage(lang) {
  if (lang === this.currentLang) return

  const isLanguageAvailable = this.availableLanguageButtons.some(
    (btn) => btn.code === lang
  )
  if (!isLanguageAvailable) {
    this.$message.warning('该语言版本暂不可用')
    return
  }

  this.currentLang = lang
  this.tocbotRefreshed = false

  const articleLangKey = `article_${this.id}_preferredLanguage`
  if (lang !== this.sourceLanguage) {
    localStorage.setItem(articleLangKey, lang)
  } else {
    localStorage.removeItem(articleLangKey)
  }

  localStorage.removeItem('preferredLanguage')
  this.updateUrlWithLanguage(lang)
  document.documentElement.setAttribute('lang', lang)

  if (lang !== this.sourceLanguage) {
    if (this.translatedContent) {
      await this.renderArticleBody(this.translatedContent)
    } else {
      await this.fetchTranslation()
    }
  } else if (lang === this.sourceLanguage) {
    await this.renderArticleBody(this.article.articleContent)
  }
}

export async function fetchTranslation() {
  if (!this.article || !this.article.id) {
    return
  }

  this.isLoading = true
  try {
    const response = await this.$http.get(
      this.$constant.baseURL + '/article/getTranslation',
      {
        id: this.article.id,
        language: this.currentLang,
      }
    )

    if (
      response.code === 200 &&
      response.data &&
      response.data.status === 'not_found'
    ) {
      this.currentLang = this.sourceLanguage
      const articleLangKey = `article_${this.id}_preferredLanguage`
      localStorage.removeItem(articleLangKey)
      this.updateUrlWithLanguage(this.sourceLanguage)
      await this.renderArticleBody(this.article.articleContent)
      this.$message.info('该语言版本不存在，已切换到原文显示')
    } else if (response.code === 200 && response.data) {
      this.translatedTitle = response.data.title
      this.translatedContent = response.data.content
      await this.renderArticleBody(this.translatedContent)
    } else {
      console.error('获取翻译失败，服务器返回:', response)
      this.currentLang = this.sourceLanguage
      const articleLangKey = `article_${this.id}_preferredLanguage`
      localStorage.removeItem(articleLangKey)
      this.updateUrlWithLanguage(this.sourceLanguage)
      await this.renderArticleBody(this.article.articleContent)
      this.$message.error('翻译加载失败，已切换到原文显示')
    }
  } catch (error) {
    console.error('Translation error:', error)
    this.currentLang = this.sourceLanguage
    const articleLangKey = `article_${this.id}_preferredLanguage`
    localStorage.removeItem(articleLangKey)
    this.updateUrlWithLanguage(this.sourceLanguage)
    await this.renderArticleBody(this.article.articleContent)
    this.$message.error('翻译加载失败，已切换到原文显示')
  } finally {
    this.isLoading = false
    this.$nextTick(() => {
      this.normalizeTaskListCheckboxes()
    })
  }
}

export function updateUrlWithLanguage(lang) {
  let newPath
  const articleForPath = this.article && this.article.id
    ? this.article
    : { id: this.id, articleSlug: this.articlePathToken }

  if (lang === this.sourceLanguage) {
    newPath = getArticlePath(articleForPath)
  } else {
    newPath = getArticlePath(articleForPath, lang)
  }

  const query = { ...this.$route.query }

  this.$router
    .replace({
      path: newPath,
      query: query,
    })
    .catch((err) => {
      if (err.name !== 'NavigationDuplicated') {
      }
    })
}

export async function initializeLanguageSettings() {
  try {
    await this.getDefaultTargetLanguage()

    const langParam = this.$route.params.lang
    const articleLangKey = `article_${this.id}_preferredLanguage`
    const savedLang = localStorage.getItem(articleLangKey)

    this.currentLang = this.sourceLanguage || 'zh'

    if (langParam && this.languageMap[langParam]) {
      this.currentLang = langParam
    } else if (
      savedLang &&
      this.languageMap[savedLang] &&
      savedLang !== this.sourceLanguage
    ) {
      this.currentLang = savedLang
    } else {
      this.currentLang = this.sourceLanguage
    }

    document.documentElement.setAttribute('lang', this.currentLang)
  } catch (error) {
    console.error('语言设置初始化失败:', error)
    this.currentLang = 'zh'
    this.sourceLanguage = 'zh'
    this.targetLanguage = 'en'
    document.documentElement.setAttribute('lang', this.currentLang)
  }
}

export async function getDefaultTargetLanguage() {
  try {
    const response = await this.$http.get(
      this.$constant.baseURL + '/webInfo/ai/config/articleAi/defaultLang'
    )

    if (response.code === 200 && response.data) {
      this.targetLanguage = response.data.default_target_lang || 'en'
      this.targetLanguageName =
        this.languageMap[this.targetLanguage] || 'English'
      this.sourceLanguage = response.data.default_source_lang || 'zh'
      this.sourceLanguageName = this.languageMap[this.sourceLanguage] || '中文'
    } else {
      this.targetLanguage = 'en'
      this.targetLanguageName = 'English'
      this.sourceLanguage = 'zh'
      this.sourceLanguageName = '中文'
    }
  } catch (error) {
    console.error('获取默认语言配置出错:', error)
    this.targetLanguage = 'en'
    this.targetLanguageName = 'English'
    this.sourceLanguage = 'zh'
    this.sourceLanguageName = '中文'
  }
}

export function generateLanguageButtons() {
  this.availableLanguageButtons = []

  this.availableLanguageButtons.push({
    code: this.sourceLanguage,
    name: this.sourceLanguageName,
  })

  if (this.availableLanguages && this.availableLanguages.length > 0) {
    this.availableLanguages.forEach((langCode) => {
      if (langCode !== this.sourceLanguage) {
        const langName = this.languageMap[langCode] || langCode
        this.availableLanguageButtons.push({
          code: langCode,
          name: langName,
        })
      }
    })
  }

  const currentLangAvailable = this.availableLanguageButtons.some(
    (btn) => btn.code === this.currentLang
  )
  if (!currentLangAvailable) {
    this.currentLang = this.sourceLanguage
    const articleLangKey = `article_${this.id}_preferredLanguage`
    localStorage.removeItem(articleLangKey)
    this.updateUrlWithLanguage(this.sourceLanguage)
  }
}
