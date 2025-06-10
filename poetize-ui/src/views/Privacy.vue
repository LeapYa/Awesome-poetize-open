<template>
  <div class="privacy-container common-container font">
    <div class="privacy-header">
      <h1>隐私政策</h1>
      <p>最后更新日期：{{ new Date().getFullYear() }}年{{ new Date().getMonth() + 1 }}月{{ new Date().getDate() }}日</p>
    </div>
    <div class="privacy-content">
      <section>
        <h2>1. 引言</h2>
        <p>欢迎访问{{ $store.state.webInfo.webTitle }}网站。我们非常重视您的隐私和个人信息保护。本隐私政策旨在向您说明我们如何收集、使用、存储和保护您的个人信息。</p>
      </section>
      
      <section>
        <h2>2. 我们收集的信息</h2>
        <p>当您使用我们的网站时，我们可能会收集以下类型的信息：</p>
        <ul>
          <li><strong>基本浏览信息：</strong>包括IP地址、浏览器类型、访问时间、访问页面等。</li>
          <li><strong>用户提供的信息：</strong>当您注册账户、发表评论或与我们联系时，您可能会提供姓名、电子邮件地址等个人信息。</li>
          <li><strong>Cookie信息：</strong>我们使用Cookie和类似技术来提升您的浏览体验。</li>
        </ul>
      </section>
      
      <section>
        <h2>3. 信息使用</h2>
        <p>我们收集的信息主要用于以下目的：</p>
        <ul>
          <li>提供、维护和改进我们的网站服务</li>
          <li>响应您的咨询和请求</li>
          <li>分析网站使用情况以优化用户体验</li>
          <li>防止欺诈和保障网站安全</li>
        </ul>
      </section>
      
      <section>
        <h2>4. 信息共享</h2>
        <p>我们不会出售或出租您的个人信息给第三方。但在以下情况下，我们可能会共享您的信息：</p>
        <ul>
          <li>经您明确同意</li>
          <li>为遵守法律法规的要求</li>
          <li>保护我们的权利、财产或安全</li>
        </ul>
      </section>
      
      <section>
        <h2>5. 信息安全</h2>
        <p>我们采取合理的安全措施来保护您的个人信息不被未经授权的访问、使用或泄露。但请注意，互联网传输不可能百分之百安全，我们无法保证信息传输的绝对安全性。</p>
      </section>
      
      <section>
        <h2>6. 您的权利</h2>
        <p>根据适用的数据保护法律，您可能拥有以下权利：</p>
        <ul>
          <li>访问您的个人信息</li>
          <li>更正不准确的信息</li>
          <li>在某些情况下删除您的信息</li>
          <li>反对或限制我们处理您的信息</li>
        </ul>
      </section>
      
      <section>
        <h2>7. 隐私政策更新</h2>
        <p>我们可能会不时更新本隐私政策。更新后的政策将在网站上发布，重大变更时我们会通过适当方式通知您。</p>
      </section>
      
      <section>
        <h2>8. 联系我们</h2>
        <p>如果您对本隐私政策有任何疑问或建议，请通过以下方式联系我们：</p>
        <p>邮箱：{{ $store.state.webInfo.email || 'admin@poetize.cn' }}</p>
      </section>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Privacy',
  metaInfo() {
    return {
      title: '隐私政策 - ' + this.$store.state.webInfo.webTitle
    }
  },
  created() {
    // 确保页面滚动到顶部
    window.scrollTo(0, 0);
    
    // 设置页面背景
    this.setPageBackground();
    
    // 添加RGB变量
    this.setupRgbVariables();
  },
  beforeDestroy() {
    // 移除页面背景，恢复原样
    document.body.style.backgroundImage = '';
    document.body.style.backgroundSize = '';
    document.body.style.backgroundPosition = '';
    document.body.style.backgroundRepeat = '';
    document.body.style.backgroundAttachment = '';
  },
  methods: {
    setPageBackground() {
      const webInfo = this.$store.state.webInfo;
      if (webInfo && webInfo.backgroundImage) {
        // 设置整个页面的背景
        document.body.style.backgroundImage = `url(${webInfo.backgroundImage})`;
        document.body.style.backgroundSize = 'cover';
        document.body.style.backgroundPosition = 'center center';
        document.body.style.backgroundRepeat = 'no-repeat';
        document.body.style.backgroundAttachment = 'fixed';
      }
    },
    setupRgbVariables() {
      // 如果没有定义--card-bg-rgb变量，计算一个合适的值
      const root = document.documentElement;
      const cardBg = getComputedStyle(root).getPropertyValue('--card-bg') || '#ffffff';
      
      if (!getComputedStyle(root).getPropertyValue('--card-bg-rgb')) {
        // 如果是十六进制颜色，转换为RGB
        if (cardBg.startsWith('#')) {
          const r = parseInt(cardBg.slice(1, 3), 16);
          const g = parseInt(cardBg.slice(3, 5), 16);
          const b = parseInt(cardBg.slice(5, 7), 16);
          root.style.setProperty('--card-bg-rgb', `${r}, ${g}, ${b}`);
        } else {
          // 默认白色
          root.style.setProperty('--card-bg-rgb', '255, 255, 255');
        }
      }
    }
  }
}
</script>

<style scoped>
.common-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  min-height: calc(100vh - 130px);
  transition: all 0.3s ease;
}

.privacy-container {
  background-color: rgba(var(--card-bg-rgb, 255, 255, 255), 0.85);
  color: var(--fontColor);
  border-radius: 8px;
  box-shadow: 0 2px 30px rgba(0, 0, 0, 0.3);
  margin-top: 20px;
  margin-bottom: 20px;
  padding: 30px;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.privacy-header {
  text-align: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.privacy-header h1 {
  color: var(--fontColor);
  font-size: 28px;
  margin-bottom: 10px;
}

.privacy-header p {
  color: var(--secondaryText);
  font-size: 14px;
}

.privacy-content {
  line-height: 1.8;
}

.privacy-content section {
  margin-bottom: 30px;
}

.privacy-content h2 {
  color: var(--fontColor);
  font-size: 22px;
  margin-bottom: 15px;
  font-weight: 600;
}

.privacy-content p, .privacy-content li {
  margin-bottom: 10px;
  color: var(--fontColor);
  font-size: 15px;
}

.privacy-content strong {
  font-weight: 600;
  color: var(--fontColor);
}

.privacy-content ul {
  padding-left: 20px;
  margin-bottom: 15px;
}

/* 媒体查询 - 移动设备适配 */
@media (max-width: 768px) {
  .privacy-container {
    padding: 20px 15px;
    margin-top: 10px;
    margin-bottom: 10px;
  }
  
  .privacy-header h1 {
    font-size: 24px;
  }
  
  .privacy-content h2 {
    font-size: 20px;
  }
  
  .privacy-content p, .privacy-content li {
    font-size: 14px;
  }
}
</style> 