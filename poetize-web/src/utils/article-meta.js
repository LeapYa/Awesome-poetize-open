export function setDefaultMetaTags() {
  if (!this.article) return

  const title = this.article.articleTitle || ''
  const keywords =
    this.article.seoMeta?.keywords ||
    [
      this.article.sortName || this.article.sort?.sortName,
      this.article.labelName || this.article.label?.labelName,
      title,
    ]
      .filter(Boolean)
      .join(',')

  this.metaTags = {
    title,
    description: this.article.summary || title,
    keywords,
    author: this.article.username || '',
    'og:url': window.location.href,
    'og:image': this.article.articleCover || '',
    'twitter:card': 'summary',
    'article:published_time': this.article.createTime || '',
    'article:modified_time': this.article.updateTime || '',
  }
  this.updateMetaTags()
}

export function updateMetaTags() {
  if (!this.metaTags) return

  document.title = this.metaTags.title || ''
  window.OriginTitile = document.title

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
