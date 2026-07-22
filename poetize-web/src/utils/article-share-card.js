export function openShareCardDialog() {
  this.shareCardDialogVisible = true
  this.preloadHtml2Canvas()

  this.$nextTick(() => {
    setTimeout(() => {
      this.generateQRCode()
    }, 300)
  })
}

export function preloadHtml2Canvas() {
  if (typeof html2canvas === 'undefined' && !window.html2canvasLoading) {
    window.html2canvasLoading = true
    const script = document.createElement('script')
    script.src =
      'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js'
    script.onload = () => {
      window.html2canvasLoading = false
    }
    script.onerror = () => {
      window.html2canvasLoading = false
    }
    document.head.appendChild(script)
  }
}

export function formatDate(dateStr) {
  if (!dateStr) return ''

  try {
    const date = new Date(dateStr)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')

    return `${year}年${month}月${day}日`
  } catch (error) {
    console.error('日期格式化失败:', error)
    return dateStr
  }
}

export function generateQRCode() {
  const qrcodeContainer = this.$refs.qrcode
  if (!qrcodeContainer) {
    console.error('二维码容器未找到')
    return
  }

  if (!this.article || !this.article.id) {
    console.error('文章ID不存在')
    qrcodeContainer.innerHTML =
      '<div style="width: 60px; height: 60px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #999;">无效文章</div>'
    return
  }

  qrcodeContainer.innerHTML = ''
  qrcodeContainer.innerHTML =
    '<div style="width: 60px; height: 60px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #999;">加载中...</div>'

  const qrcodeApiUrl = `${this.$constant.baseURL}/qrcode/article/${this.article.id}`
  const img = document.createElement('img')
  img.src = qrcodeApiUrl
  img.style.width = '60px'
  img.style.height = '60px'
  img.style.display = 'block'

  img.onload = () => {
    qrcodeContainer.innerHTML = ''
    qrcodeContainer.appendChild(img)
  }

  img.onerror = () => {
    console.error('二维码加载失败')
    qrcodeContainer.innerHTML =
      '<div style="width: 60px; height: 60px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #999;">加载失败</div>'
  }
}

export function downloadShareCard() {
  const shareCard = this.$refs.shareCard
  if (!shareCard) {
    this.$message.error('卡片元素未找到')
    return
  }

  if (typeof html2canvas === 'undefined') {
    if (window.html2canvasLoading) {
      this.$message({
        message: '正在加载必要组件，请稍候...',
        type: 'info',
        duration: 1500,
      })

      const checkInterval = setInterval(() => {
        if (typeof html2canvas !== 'undefined') {
          clearInterval(checkInterval)
          this.captureAndDownloadCard(shareCard)
        }
      }, 100)

      setTimeout(() => {
        clearInterval(checkInterval)
        if (typeof html2canvas === 'undefined') {
          this.$message.error('组件加载超时，请刷新页面重试')
        }
      }, 10000)
    } else {
      this.$message({
        message: '首次使用，正在加载组件...',
        type: 'info',
        duration: 2000,
      })

      const script = document.createElement('script')
      script.src =
        'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js'
      script.onload = () => {
        this.captureAndDownloadCard(shareCard)
      }
      script.onerror = () => {
        this.$message.error('组件加载失败，请检查网络连接')
      }
      document.head.appendChild(script)
    }
  } else {
    this.captureAndDownloadCard(shareCard)
  }
}

export function captureAndDownloadCard(element) {
  const loadingMsg = this.$message({
    message: '正在生成卡片图片...',
    type: 'info',
    duration: 0,
    showClose: false,
  })

  const renderCard = () => {
    html2canvas(element, {
      useCORS: true,
      allowTaint: true,
      backgroundColor: null,
      scale: 2,
      logging: false,
      ignoreElements: (target) => {
        return target.classList?.contains('el-loading-mask')
      },
      removeContainer: true,
      imageTimeout: 5000,
    })
      .then((canvas) => {
        loadingMsg.close()
        canvas.toBlob((blob) => {
          const url = URL.createObjectURL(blob)
          const link = document.createElement('a')
          link.href = url

          const fileName = `${
            this.article.articleTitle || '文章'
          }_分享卡片.png`
          link.download = fileName

          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link)
          URL.revokeObjectURL(url)

          this.$message.success('卡片已下载')
        }, 'image/png')
      })
      .catch((error) => {
        loadingMsg.close()
        console.error('生成卡片失败:', error)
        this.$message.error('生成卡片失败，请重试')
      })
  }

  if (window.requestIdleCallback) {
    requestIdleCallback(renderCard, { timeout: 1000 })
  } else {
    setTimeout(renderCard, 0)
  }
}
