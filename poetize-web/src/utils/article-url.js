export function getArticleToken(article) {
  if (!article) {
    return ''
  }
  return article.articleSlug || article.articlePathToken || article.id || ''
}

export function getArticlePath(article, language) {
  const token = getArticleToken(article)
  if (!token) {
    return '/article'
  }
  return language ? `/article/${language}/${token}` : `/article/${token}`
}
