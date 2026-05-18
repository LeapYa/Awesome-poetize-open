import axios from 'axios'

export function getArticleMeta() {
  this.isLoadingMeta = true
  const articlePath = this.articlePathToken || this.id
  const timeout = setTimeout(() => {
    if (this.isLoadingMeta) {
      this.isLoadingMeta = false
      this.setDefaultMetaTags()
    }
  }, 3000)

  this.$http
    .get(this.$constant.baseURL + '/article/getArticleByPathNoCount', {
      path: articlePath,
    })
    .then((articleRes) => {
      if (articleRes.code === 200 && articleRes.data) {
        axios
          .get(
            this.$constant.baseURL +
              `/seo/getArticleMeta?articlePath=${encodeURIComponent(articlePath)}&lang=${this.currentLang}`
          )
          .then((res) => {
            clearTimeout(timeout)
            this.isLoadingMeta = false

            if (res.data && res.data.code === 200 && res.data.data) {
              this.metaTags = res.data.data
              this.updateMetaTags()
            } else {
              console.error(
                '获取文章元标签失败, 服务返回错误:',
                res.data ? res.data.message || '未知错误' : '返回数据为空'
              )
              this.setDefaultMetaTags()
            }
          })
          .catch((error) => {
            clearTimeout(timeout)
            this.isLoadingMeta = false
            console.error('获取文章元标签失败:', error)

            if (!this.metaTagRetryCount || this.metaTagRetryCount < 2) {
              this.metaTagRetryCount = (this.metaTagRetryCount || 0) + 1
              setTimeout(() => {
                this.getArticleMeta()
              }, 1500)
            } else {
              this.setDefaultMetaTags()
            }
          })
      } else {
        clearTimeout(timeout)
        this.isLoadingMeta = false
        console.error('获取文章信息失败，无法获取元标签')
        this.setDefaultMetaTags()
      }
    })
    .catch((error) => {
      clearTimeout(timeout)
      this.isLoadingMeta = false
      console.error('获取文章信息失败:', error)
      this.setDefaultMetaTags()
    })
}

export function setDefaultMetaTags() {
  if (this.article) {
    this.metaTags = {
      title: this.article.articleTitle || 'POETIZE博客',
      description: this.article.articleTitle
        ? this.article.articleTitle + ' - POETIZE博客'
        : 'POETIZE博客',
      keywords: 'POETIZE,博客,个人网站',
      author: this.article.username || 'Admin',
      'og:url': window.location.href,
      'og:image': this.article.articleCover || '',
      'twitter:card': 'summary',
      'article:published_time': this.article.createTime || '',
      'article:modified_time': this.article.updateTime || '',
    }
    this.updateMetaTags()
  }
}

export function updateMetaTags() {
  if (!this.metaTags) return

  if (this.metaTags.title) {
    document.title = this.metaTags.title
    window.OriginTitile = this.metaTags.title
  }

  document
    .querySelectorAll('meta[data-vue-meta="true"]')
    .forEach((el) => el.remove())

  const addMetaTag = (name, content, isProperty = false) => {
    if (!content) return

    const meta = document.createElement('meta')
    if (isProperty) {
      meta.setAttribute('property', name)
    } else {
      meta.setAttribute('name', name)
    }
    meta.setAttribute('content', content)
    meta.setAttribute('data-vue-meta', 'true')
    if (
      meta &&
      meta.nodeType === Node.ELEMENT_NODE &&
      document.head &&
      typeof document.head.appendChild === 'function'
    ) {
      try {
        document.head.appendChild(meta)
      } catch (e) {}
    }
  }

  addMetaTag('description', this.metaTags.description)
  addMetaTag('keywords', this.metaTags.keywords)
  addMetaTag('author', this.metaTags.author)
  addMetaTag('og:title', this.metaTags.title, true)
  addMetaTag('og:description', this.metaTags.description, true)
  addMetaTag('og:type', 'article', true)
  addMetaTag('og:url', this.metaTags['og:url'], true)
  addMetaTag('og:image', this.metaTags['og:image'], true)
  addMetaTag('twitter:card', this.metaTags['twitter:card'])
  addMetaTag('twitter:title', this.metaTags.title)
  addMetaTag('twitter:description', this.metaTags.description)
  addMetaTag('twitter:image', this.metaTags['twitter:image'])
  addMetaTag(
    'article:published_time',
    this.metaTags['article:published_time'],
    true
  )
  addMetaTag(
    'article:modified_time',
    this.metaTags['article:modified_time'],
    true
  )
}

export function fetchArticleMeta() {
  return new Promise((resolve) => {
    this.isLoadingMeta = true
    const articlePath = this.articlePathToken || this.id
    const timeout = setTimeout(() => {
      if (this.isLoadingMeta) {
        this.isLoadingMeta = false
        this.setDefaultMetaTags()
        resolve()
      }
    }, 3000)

    axios
      .get(
        this.$constant.baseURL +
          `/seo/getArticleMeta?articlePath=${encodeURIComponent(articlePath)}&lang=${this.currentLang}`
      )
      .then((res) => {
        clearTimeout(timeout)
        this.isLoadingMeta = false

        if (res.data && res.data.code === 200 && res.data.data) {
          this.metaTags = res.data.data
          this.updateMetaTags()
        } else {
          console.error(
            '获取文章元标签失败, 服务返回错误:',
            res.data ? res.data.message || '未知错误' : '返回数据为空'
          )
          this.setDefaultMetaTags()
        }
        resolve()
      })
      .catch((error) => {
        clearTimeout(timeout)
        this.isLoadingMeta = false
        console.error('获取文章元标签失败:', error)
        this.setDefaultMetaTags()
        resolve()
      })
  })
}
