<template>
  <div class="seo-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title-section">
        <h1 class="page-title">
          <i class="el-icon-search"></i>
          SEO 配置
        </h1>
        <p class="page-description">配置网站SEO优化设置，提升搜索引擎收录和排名效果</p>
      </div>
    </div>

    <el-card class="box-card" shadow="hover">
      <el-form :model="seoConfig" label-width="150px" size="small">
        <el-form-item label="启用SEO优化">
          <el-switch v-model="seoConfig.enable"></el-switch>
        </el-form-item>
        
        <div :class="{'disabled-section': !seoConfig.enable}">
        
        <el-form-item label="网站地址">
          <el-input v-model="seoConfig.site_address" placeholder="请输入完整网站地址，如：https://www.example.com"></el-input>
          <span class="tip">网站的完整访问地址，用于生成站点地图和其他SEO功能</span>
        </el-form-item>
        
        <el-form-item label="网站标题">
          <el-input v-model="seoConfig.site_title" placeholder="请输入网站标题" maxlength="60"></el-input>
          <span class="tip">网站的主要标题，显示在浏览器标题栏和搜索结果中，建议不超过60个字符。<b class="warning-tip">注意：为保持一致性，请优先在"网站设置"中修改网站标题，该处的设置将覆盖此处配置。</b></span>
        </el-form-item>
        
        <el-form-item label="网站描述">
          <el-input v-model="seoConfig.site_description" type="textarea" :rows="2" maxlength="200"></el-input>
          <span class="tip">描述应当简明扼要，不超过200字符。<b class="warning-tip">注意：为保持一致性，请优先在"网站设置"中修改网站描述，该处的设置将覆盖此处配置。</b></span>
        </el-form-item>
        
        <el-form-item label="网站关键词">
          <el-input v-model="seoConfig.site_keywords" placeholder="多个关键词用逗号分隔" maxlength="100"></el-input>
          <span class="tip">关键词应当与网站内容相关，用逗号分隔，不超过10个。<b class="warning-tip">注意：为保持一致性，请优先在"网站设置"中修改网站关键词，该处的设置将覆盖此处配置。</b></span>
        </el-form-item>
        
        <el-form-item label="默认作者">
          <el-input v-model="seoConfig.default_author" maxlength="30"></el-input>
        </el-form-item>
        
        <el-form-item label="默认封面图">
          <el-input v-model="seoConfig.og_image" placeholder="输入图片URL"></el-input>
          <span class="tip">用于在社交媒体分享时显示的默认图片</span>
        </el-form-item>
        
        <el-divider content-position="left">
          社交媒体设置
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">配置网站在社交媒体分享时的显示方式，影响Facebook、Twitter等平台分享效果</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-tabs type="border-card">
          <el-tab-pane label="通用设置">
            <el-form-item label="默认分享图片">
              <el-input v-model="seoConfig.og_image" placeholder="输入图片URL"></el-input>
              <span class="tip">当文章无封面图时使用的默认图片，建议尺寸1200×630像素</span>
            </el-form-item>
            
            <el-form-item label="网站Logo">
              <el-input v-model="seoConfig.site_logo" placeholder="输入Logo URL"></el-input>
              <span class="tip">在某些社交平台上显示为网站标识，建议为正方形，至少300×300像素</span>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="Facebook/Meta">
            <el-form-item label="Facebook App ID">
              <el-input v-model="seoConfig.fb_app_id" placeholder="输入App ID"></el-input>
              <span class="tip">关联Facebook应用，启用分享统计和深度链接功能</span>
            </el-form-item>
            
            <el-form-item label="Facebook Page URL">
              <el-input v-model="seoConfig.fb_page_url" placeholder="输入Facebook页面URL"></el-input>
              <span class="tip">您网站的官方Facebook页面链接</span>
            </el-form-item>
            
            <el-form-item label="Open Graph类型">
              <el-select v-model="seoConfig.og_type" placeholder="请选择">
                <el-option label="网站" value="website"></el-option>
                <el-option label="文章" value="article"></el-option>
                <el-option label="博客" value="blog"></el-option>
              </el-select>
              <span class="tip">定义您的内容类型，影响Facebook分享卡片的展示方式</span>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="Twitter">
            <el-form-item label="Twitter Card类型">
              <el-select v-model="seoConfig.twitter_card" placeholder="请选择">
                <el-option label="大图摘要" value="summary_large_image"></el-option>
                <el-option label="小图摘要" value="summary"></el-option>
                <el-option label="应用卡片" value="app"></el-option>
                <el-option label="播放器卡片" value="player"></el-option>
              </el-select>
              <span class="tip">控制Twitter分享时的预览卡片样式</span>
            </el-form-item>
            
            <el-form-item label="Twitter 站点账号">
              <el-input v-model="seoConfig.twitter_site" placeholder="@账号名" maxlength="30"></el-input>
              <span class="tip">您网站的官方Twitter账号，格式为@username</span>
            </el-form-item>
            
            <el-form-item label="Twitter 创作者账号">
              <el-input v-model="seoConfig.twitter_creator" placeholder="@账号名" maxlength="30"></el-input>
              <span class="tip">默认文章作者的Twitter账号，格式为@username</span>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="LinkedIn">
            <el-form-item label="LinkedIn 公司ID">
              <el-input v-model="seoConfig.linkedin_company_id" placeholder="输入公司ID"></el-input>
              <span class="tip">您的LinkedIn公司页面ID，用于内容归属</span>
            </el-form-item>
            
            <el-form-item label="LinkedIn 分享模式">
              <el-select v-model="seoConfig.linkedin_mode" placeholder="请选择">
                <el-option label="标准卡片" value="standard"></el-option>
                <el-option label="详细视图" value="detailed"></el-option>
              </el-select>
              <span class="tip">控制LinkedIn分享时的预览卡片样式</span>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="Pinterest">
            <el-form-item label="Pinterest 验证码">
              <el-input v-model="seoConfig.pinterest_verification" placeholder="输入验证码"></el-input>
              <span class="tip">Pinterest网站验证码，验证您对网站的所有权</span>
            </el-form-item>
            
            <el-form-item label="默认Pin描述">
              <el-input v-model="seoConfig.pinterest_description" type="textarea" :rows="2"></el-input>
              <span class="tip">当用户将您的内容保存到Pinterest时的默认描述</span>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="小程序关联">
            <div class="tab-description">
              <i class="el-icon-info"></i> 如果已开发了对应的微信/QQ小程序，可在此设置关联参数。用户在微信/QQ中分享您的网站链接时，将可选择"在小程序中查看"。
            </div>
            
            <el-form-item label="微信小程序AppID">
              <el-input v-model="seoConfig.wechat_miniprogram_id" placeholder="输入已有小程序的AppID"></el-input>
              <span class="tip">已开发的微信小程序的AppID，必须已在微信平台上线</span>
            </el-form-item>
            
            <el-form-item label="微信小程序路径">
              <el-input v-model="seoConfig.wechat_miniprogram_path" placeholder="如: pages/index/index"></el-input>
              <span class="tip">指定跳转到小程序中的页面路径，文章页将自动附加文章ID参数</span>
            </el-form-item>
            
            <el-form-item label="QQ小程序路径">
              <el-input v-model="seoConfig.qq_miniprogram_path" placeholder="如: pages/index/index"></el-input>
              <span class="tip">指定跳转到您QQ小程序中的页面路径，需确保您已有对应的QQ小程序</span>
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
        
        <el-divider content-position="left">
          搜索引擎验证
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">通过验证码证明您对网站的所有权，以便在各搜索引擎站长平台管理网站</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item label="百度站点验证">
          <el-input v-model="seoConfig.baidu_site_verification" placeholder="百度验证码"></el-input>
        </el-form-item>
        
        <el-form-item label="Google站点验证">
          <el-input v-model="seoConfig.google_site_verification" placeholder="Google验证码"></el-input>
        </el-form-item>
        
        <el-form-item label="Bing(必应)站点验证">
          <el-input v-model="seoConfig.bing_site_verification" placeholder="Bing(必应)验证码"></el-input>
        </el-form-item>
        
        <el-form-item label="Yandex站点验证">
          <el-input v-model="seoConfig.yandex_site_verification" placeholder="Yandex验证码"></el-input>
        </el-form-item>
        
        <el-form-item label="搜狗站点验证">
          <el-input v-model="seoConfig.sogou_site_verification" placeholder="搜狗验证码"></el-input>
        </el-form-item>
        
        <el-form-item label="神马站点验证">
          <el-input v-model="seoConfig.shenma_site_verification" placeholder="神马验证码"></el-input>
        </el-form-item>
        
        <el-form-item label="360站点验证">
          <el-input v-model="seoConfig.so_site_verification" placeholder="360验证码"></el-input>
        </el-form-item>
        
        <el-form-item label="Yahoo(雅虎)站点验证">
          <el-input v-model="seoConfig.yahoo_site_verification" placeholder="Yahoo(雅虎)验证码"></el-input>
        </el-form-item>
        
        <el-divider content-position="left">
          搜索引擎推送
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">配置后系统会在发布新文章时自动将URL提交给搜索引擎，加速收录和提高排名</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item label="启用百度推送">
          <el-switch v-model="seoConfig.baidu_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL推送至百度搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="百度推送Token">
          <el-input v-model="seoConfig.baidu_push_token" placeholder="百度推送Token"></el-input>
          <span class="tip">在百度搜索资源平台-普通收录-资源提交中获取</span>
        </el-form-item>
        
        <el-form-item label="启用Google索引">
          <el-switch v-model="seoConfig.google_index_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至Google索引</span>
        </el-form-item>
        
        <el-form-item label="Google API Key">
          <el-input v-model="seoConfig.google_api_key" placeholder="Google索引API密钥"></el-input>
          <span class="tip">需要在Google Search Console中获取索引API密钥</span>
        </el-form-item>
        
        <el-form-item label="启用Bing推送">
          <el-switch v-model="seoConfig.bing_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至Bing索引</span>
        </el-form-item>
        
        <el-form-item label="Bing(必应) API Key">
          <el-input v-model="seoConfig.bing_api_key" placeholder="Bing(必应)索引API密钥"></el-input>
          <span class="tip">需要在Bing Webmaster Tools中获取API密钥</span>
        </el-form-item>
        
        <el-form-item label="启用Yandex推送">
          <el-switch v-model="seoConfig.yandex_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至Yandex搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="Yandex API Key">
          <el-input v-model="seoConfig.yandex_api_key" placeholder="Yandex API密钥"></el-input>
          <span class="tip">需要在Yandex Webmaster中获取</span>
        </el-form-item>
        
        <el-form-item label="启用Yahoo(雅虎)推送">
          <el-switch v-model="seoConfig.yahoo_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至Yahoo搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="Yahoo(雅虎) API Key">
          <el-input v-model="seoConfig.yahoo_api_key" placeholder="Yahoo(雅虎) API密钥"></el-input>
          <span class="tip">需要在Yahoo Site Explorer中获取API密钥</span>
        </el-form-item>
        
        <el-form-item label="启用搜狗推送">
          <el-switch v-model="seoConfig.sogou_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至搜狗搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="搜狗推送Token">
          <el-input v-model="seoConfig.sogou_push_token" placeholder="搜狗推送Token"></el-input>
          <span class="tip">在搜狗站长平台获取</span>
        </el-form-item>
        
        <el-form-item label="启用360推送">
          <el-switch v-model="seoConfig.so_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至360搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="360推送Token">
          <el-input v-model="seoConfig.so_push_token" placeholder="360推送Token"></el-input>
          <span class="tip">在360站长平台获取</span>
        </el-form-item>
        
        <el-form-item label="启用神马推送">
          <el-switch v-model="seoConfig.shenma_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至神马搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="神马推送Token">
          <el-input v-model="seoConfig.shenma_token" placeholder="神马推送Token"></el-input>
          <span class="tip">在神马站长平台获取</span>
        </el-form-item>
        
        <el-divider content-position="left">
          邮件通知设置
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">配置推送结果通知，系统将在URL推送完成后向指定邮箱发送推送结果报告</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item label="启用推送结果通知">
          <el-switch v-model="seoConfig.enable_push_notification"></el-switch>
          <span class="tip">文章推送到搜索引擎后，将结果发送邮件通知给站长</span>
        </el-form-item>
        
        <el-form-item label="仅在推送失败时通知">
          <el-switch v-model="seoConfig.notify_only_on_failure"></el-switch>
          <span class="tip">如果启用，则只有当推送出现错误时才发送邮件通知</span>
        </el-form-item>
        
        <el-form-item label="通知邮箱">
          <el-input v-model="seoConfig.notification_email" placeholder="留空则使用文章作者邮箱"></el-input>
          <span class="tip">如果不填写，系统将使用文章作者的邮箱发送通知</span>
        </el-form-item>
        
        <el-divider content-position="left">
          URL 设置
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">配置网站URL结构，影响搜索引擎如何解析和索引您的内容页面</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item label="文章URL格式">
          <el-input v-model="seoConfig.article_url_format" placeholder="article/{id}"></el-input>
          <span class="tip">使用 {id} 作为文章ID的占位符</span>
        </el-form-item>
        
        <el-form-item label="分类URL格式">
          <el-input v-model="seoConfig.category_url_format" placeholder="category/{id}"></el-input>
          <span class="tip">使用 {id} 作为分类ID的占位符</span>
        </el-form-item>
        
        <el-form-item label="标签URL格式">
          <el-input v-model="seoConfig.tag_url_format" placeholder="tag/{id}"></el-input>
          <span class="tip">使用 {id} 作为标签ID的占位符</span>
        </el-form-item>
        
        <el-divider content-position="left">
          网站地图设置
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">网站地图(Sitemap)帮助搜索引擎了解网站结构和页面更新频率，提高收录效率</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item label="生成网站地图">
          <el-switch v-model="seoConfig.generate_sitemap"></el-switch>
        </el-form-item>
        
        <el-form-item label="更新频率">
          <el-select v-model="seoConfig.sitemap_change_frequency" placeholder="请选择">
            <el-option label="每天" value="daily"></el-option>
            <el-option label="每周" value="weekly"></el-option>
            <el-option label="每月" value="monthly"></el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="优先级">
          <el-select v-model="seoConfig.sitemap_priority" placeholder="请选择">
            <el-option label="0.1" value="0.1"></el-option>
            <el-option label="0.2" value="0.2"></el-option>
            <el-option label="0.3" value="0.3"></el-option>
            <el-option label="0.4" value="0.4"></el-option>
            <el-option label="0.5" value="0.5"></el-option>
            <el-option label="0.6" value="0.6"></el-option>
            <el-option label="0.7" value="0.7"></el-option>
            <el-option label="0.8" value="0.8"></el-option>
            <el-option label="0.9" value="0.9"></el-option>
            <el-option label="1.0" value="1.0"></el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="排除路径列表">
          <el-input v-model="seoConfig.sitemap_exclude" placeholder="/love,/admin/*,/*.html"></el-input>
          <span class="tip">支持通配符 * ，多个路径用英文逗号分隔，如：/love,/admin/*,/*.html；留空表示不排除任何页面</span>
        </el-form-item>
        
        <el-form-item label="自动生成META标签">
          <el-switch v-model="seoConfig.auto_generate_meta_tags"></el-switch>
          <span class="tip">自动为每篇文章生成META标签</span>
        </el-form-item>
        
        <el-divider content-position="left">
          robots.txt
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">robots.txt文件告诉搜索引擎爬虫哪些页面可以爬取，哪些不能爬取</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item label="robots.txt 内容">
          <div class="code-editor-wrapper">
            <el-input 
              v-model="seoConfig.robots_txt" 
              type="textarea" 
              :rows="30" 
              class="code-textarea code-editor"
              spellcheck="false"
              resize="vertical"
              placeholder="# 输入robots.txt内容
User-agent: *
Allow: /
Disallow: /admin/
Sitemap: /sitemap.xml"
            ></el-input>
            <div class="code-line-numbers">
              <div v-for="i in 30" :key="i" class="line-number">{{i}}</div>
            </div>
          </div>
          <span class="tip">robots.txt 文件内容，控制搜索引擎爬虫对网站的访问。文本框可拖动调整高度。</span>
        </el-form-item>
        
        <el-form-item label="自定义头部代码">
          <div class="code-editor-wrapper">
            <el-input 
              v-model="seoConfig.custom_head_code" 
              type="textarea" 
              :rows="10" 
              class="code-textarea code-editor"
              spellcheck="false"
              resize="vertical"
            ></el-input>
            <div class="code-line-numbers">
              <div v-for="i in 10" :key="i" class="line-number">{{i}}</div>
            </div>
          </div>
          <span class="tip">可以添加额外的META标签、JS代码等，将插入到页面的&lt;head&gt;中。文本框可拖动调整高度。</span>
        </el-form-item>
        
        <div style="text-align: center;">
          <el-button type="primary" @click="saveSeoConfig" :loading="loading">保存配置</el-button>
          <el-button type="success" @click="updateSeoData" :loading="updateLoading">更新SEO数据</el-button>
          <el-button type="info" @click="analyzeSite" :loading="analyzeLoading">SEO分析</el-button>
          <el-dropdown @command="handleAiCommand" split-button type="warning" @click="aiAnalyze" :loading="aiAnalyzeLoading">
            AI SEO分析
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="analyze">
                <i class="el-icon-data-analysis"></i> 立即分析
              </el-dropdown-item>
              <el-dropdown-item command="config">
                <i class="el-icon-setting"></i> 配置AI API
              </el-dropdown-item>
              <el-dropdown-item command="help" divided>
                <i class="el-icon-question"></i> 使用帮助
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
        </div>
      </el-form>
    </el-card>
    
    <!-- SEO分析结果弹窗 -->
    <el-dialog
      title="SEO分析结果"
      :visible.sync="showAnalysisDialog"
      width="60%">
      <div v-if="seoAnalysis">
        <div class="analysis-score">
          <el-progress type="circle" :percentage="seoAnalysis.seo_score" :status="getSeoScoreStatus(seoAnalysis.seo_score)"></el-progress>
          <div class="score-label">SEO得分</div>
        </div>
        
        <div class="analysis-suggestions" v-if="seoAnalysis.suggestions && seoAnalysis.suggestions.length > 0">
          <h3>优化建议:</h3>
          <el-alert
            v-for="(suggestion, index) in seoAnalysis.suggestions"
            :key="index"
            :title="suggestion.message"
            :type="getSuggestionType(suggestion.type)"
            :closable="false"
            show-icon
            style="margin-bottom: 10px">
          </el-alert>
        </div>
        
        <div class="analysis-suggestions" v-else>
          <el-alert
            title="恭喜！您的网站SEO设置已经很完善了"
            type="success"
            :closable="false"
            show-icon>
          </el-alert>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="showAnalysisDialog = false">关闭</el-button>
        <el-button type="primary" @click="saveSeoConfig">应用建议优化</el-button>
      </span>
    </el-dialog>
    
    <!-- AI SEO分析结果弹窗 -->
    <el-dialog
      title="AI SEO分析结果"
      :visible.sync="showAiAnalysisDialog"
      width="60%">
      <div v-if="aiSeoAnalysis">
        <div class="analysis-score">
          <el-progress type="circle" :percentage="aiSeoAnalysis.seo_score" :status="getSeoScoreStatus(aiSeoAnalysis.seo_score)"></el-progress>
          <div class="score-label">AI SEO评分</div>
        </div>
        
        <div class="ai-analysis-content" v-if="aiSeoAnalysis.analysis">
          <h3>AI分析结果:</h3>
          <div class="ai-analysis-text" v-html="aiSeoAnalysis.analysis"></div>
        </div>
        
        <div class="analysis-suggestions" v-if="aiSeoAnalysis.suggestions && aiSeoAnalysis.suggestions.length > 0">
          <h3>优化建议:</h3>
          <el-alert
            v-for="(suggestion, index) in aiSeoAnalysis.suggestions"
            :key="index"
            :title="suggestion.message"
            :type="getSuggestionType(suggestion.type)"
            :closable="false"
            show-icon
            style="margin-bottom: 10px">
            <div v-if="suggestion.detail" class="suggestion-detail">{{ suggestion.detail }}</div>
          </el-alert>
        </div>
        
        <div class="analysis-suggestions" v-else>
          <el-alert
            title="AI分析完成，未发现需要改进的问题！"
            type="success"
            :closable="false"
            show-icon>
          </el-alert>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="showAiAnalysisDialog = false">关闭</el-button>
        <el-button type="primary" @click="saveSeoConfig">应用AI建议</el-button>
      </span>
    </el-dialog>
    
    <!-- AI API配置弹窗 -->
    <el-dialog
      title="AI API配置"
      :visible.sync="showApiConfigDialog"
      width="50%">
      <el-form :model="aiApiConfig" label-width="120px" size="small">
        <el-form-item label="AI模型提供商">
          <el-select v-model="aiApiConfig.provider" placeholder="请选择AI服务提供商" @change="onProviderChange">
            <el-option label="OpenAI" value="openai"></el-option>
            <el-option label="DeepSeek" value="deepseek"></el-option>
            <el-option label="百度文心" value="baidu"></el-option>
            <el-option label="智谱AI" value="zhipu"></el-option>
            <el-option label="豆包" value="doubao"></el-option>
            <el-option label="Claude" value="claude"></el-option>
            <el-option label="自定义AI服务" value="custom" class="custom-option">🔧 自定义AI服务</el-option>
          </el-select>
          <span class="tip" v-if="aiApiConfig.provider === 'custom'">
            支持任何兼容OpenAI API格式的AI服务，如本地部署的模型、第三方API等
          </span>
        </el-form-item>
        
        <el-form-item label="API Key">
          <el-input v-model="aiApiConfig.api_key" placeholder="输入API密钥" show-password></el-input>
          <span class="tip">
            <template v-if="aiApiConfig.provider === 'baidu'">
              百度文心API密钥格式: client_id:client_secret
            </template>
            <template v-else-if="aiApiConfig.provider === 'zhipu'">
              智谱AI密钥格式: api_key_id.api_key_secret
            </template>
            <template v-else>
              API密钥不会保存在浏览器中，仅加密存储在服务器上供SEO分析使用
            </template>
          </span>
        </el-form-item>
        
        <!-- 自定义AI服务的额外配置 -->
        <template v-if="aiApiConfig.provider === 'custom'">
          <el-form-item label="API端点URL" required>
            <el-input v-model="aiApiConfig.custom_api_url" placeholder="https://your-ai-api.com/v1/chat/completions"></el-input>
            <span class="tip">
              完整的API端点地址，例如: https://api.openai.com/v1/chat/completions
            </span>
          </el-form-item>
          
          <el-form-item label="请求格式">
            <el-select v-model="aiApiConfig.request_format" placeholder="选择请求格式">
              <el-option label="OpenAI兼容格式" value="openai">
                <span>OpenAI兼容格式 (ChatGPT、GPT-4、DeepSeek、豆包、Gemini等大部分AI服务)</span>
              </el-option>
              <el-option label="Anthropic兼容格式" value="anthropic">
                <span>Anthropic兼容格式 (Claude系列模型)</span>
              </el-option>
            </el-select>
            <span class="tip">
              选择API的请求格式。大多数AI服务都兼容OpenAI格式，只有Claude使用Anthropic格式
            </span>
          </el-form-item>
          
          <!-- 自定义请求头 -->
          <el-form-item label="自定义请求头" v-if="aiApiConfig.request_format">
            <div class="custom-headers">
              <div 
                v-for="(header, index) in aiApiConfig.custom_headers_list" 
                :key="index" 
                class="header-item"
              >
                <el-input 
                  v-model="header.key" 
                  placeholder="Header名称" 
                  class="header-key"
                  @input="updateCustomHeaders"
                ></el-input>
                <el-input 
                  v-model="header.value" 
                  placeholder="Header值" 
                  class="header-value"
                  @input="updateCustomHeaders"
                ></el-input>
                <el-button 
                  icon="el-icon-delete" 
                  size="small" 
                  type="danger" 
                  @click="removeHeader(index)"
                  circle
                ></el-button>
              </div>
              <el-button 
                @click="addHeader" 
                size="small" 
                type="primary" 
                plain
                icon="el-icon-plus"
              >
                添加请求头
              </el-button>
            </div>
            <span class="tip">
              添加额外的HTTP请求头，如认证、版本等信息（大部分情况下不需要）
            </span>
          </el-form-item>
        </template>
        
        <el-form-item label="API Base URL" v-if="aiApiConfig.provider === 'openai'">
          <el-input v-model="aiApiConfig.api_base" placeholder="可选，如使用代理服务请填写"></el-input>
          <span class="tip">例如: https://api.openai-proxy.com/v1</span>
        </el-form-item>
        
        <el-form-item label="模型名称">
          <template v-if="aiApiConfig.provider === 'custom'">
            <el-input v-model="aiApiConfig.model" placeholder="输入模型名称，如: gpt-4o"></el-input>
            <span class="tip">输入您要使用的AI模型名称</span>
          </template>
          <template v-else>
            <el-select 
              v-model="aiApiConfig.model" 
              placeholder="请选择或输入模型名称"
              filterable
              allow-create
              default-first-option>
              <template v-if="aiApiConfig.provider === 'openai'">
                <el-option label="GPT-4o" value="gpt-4o"></el-option>
                <el-option label="GPT-4o mini" value="gpt-4o-mini"></el-option>
                <el-option label="GPT-4 Turbo" value="gpt-4-turbo"></el-option>
                <el-option label="GPT-4" value="gpt-4"></el-option>
                <el-option label="GPT-4.1" value="gpt-4.1"></el-option>
                <el-option label="o1-preview" value="o1-preview"></el-option>
                <el-option label="o1-mini" value="o1-mini"></el-option>
                <el-option label="o3" value="o3"></el-option>
              </template>
              <template v-else-if="aiApiConfig.provider === 'deepseek'">
                <el-option label="DeepSeek-Chat" value="deepseek-chat"></el-option>
                <el-option label="DeepSeek-Coder" value="deepseek-coder"></el-option>
                <el-option label="DeepSeek-V3" value="deepseek-v3"></el-option>
                <el-option label="DeepSeek-R1" value="deepseek-r1"></el-option>
              </template>
              <template v-else-if="aiApiConfig.provider === 'baidu'">
                <el-option label="文心一言4.0" value="ernie-bot-4"></el-option>
                <el-option label="文心一言" value="ernie-bot"></el-option>
                <el-option label="文心一言Turbo" value="ernie-bot-turbo"></el-option>
              </template>
              <template v-else-if="aiApiConfig.provider === 'zhipu'">
                <el-option label="智谱GLM-4" value="glm-4"></el-option>
                <el-option label="智谱GLM-4V" value="glm-4v"></el-option>
                <el-option label="智谱ChatGLM" value="chatglm_turbo"></el-option>
              </template>
              <template v-else-if="aiApiConfig.provider === 'doubao'">
                <el-option label="豆包Pro" value="doubao-pro"></el-option>
                <el-option label="豆包lite" value="doubao-lite"></el-option>
                <el-option label="混元大模型" value="hunyuan"></el-option>
              </template>
              <template v-else-if="aiApiConfig.provider === 'claude'">
                <el-option label="Claude 3.5 Sonnet" value="claude-3.5-sonnet"></el-option>
                <el-option label="Claude 3.5 Haiku" value="claude-3.5-haiku"></el-option>
                <el-option label="Claude 3.7 Sonnet" value="claude-3-7-sonnet"></el-option>
                <el-option label="Claude 4.0 Sonnet" value="claude-4-sonnet"></el-option>
              </template>
            </el-select>
            <span class="tip">可选择预设模型或输入自定义模型名称</span>
          </template>
        </el-form-item>
        
        <el-form-item label="分析选项">
          <el-checkbox v-model="aiApiConfig.include_articles">包含最近文章内容进行分析</el-checkbox>
          <span class="tip">
            分析最近发布的5篇文章内容，可获得更具体的SEO优化建议，但会使用更多Token
          </span>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="showApiConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="saveApiConfig" :loading="apiConfigLoading">保存配置</el-button>
      </span>
    </el-dialog>
    
    <!-- AI使用帮助弹窗 -->
    <el-dialog
      title="AI SEO分析使用帮助"
      :visible.sync="showHelpDialog"
      width="70%">
      <div class="help-content">
        <el-tabs type="border-card">
          <el-tab-pane label="📖 功能介绍">
            <div class="help-section">
              <h3>🤖 什么是AI SEO分析？</h3>
              <p>AI SEO分析是基于人工智能技术的网站SEO优化建议系统。它会分析您的网站配置、内容结构、搜索引擎设置等多个维度，并提供专业的SEO优化建议。</p>
              
              <h3>🎯 AI分析的优势</h3>
              <ul>
                <li><strong>智能化：</strong>基于大量SEO最佳实践训练，提供准确的优化建议</li>
                <li><strong>全面性：</strong>从技术SEO到内容SEO，覆盖多个优化维度</li>
                <li><strong>个性化：</strong>针对您的具体配置和内容，提供定制化建议</li>
                <li><strong>实时性：</strong>基于最新的SEO趋势和搜索引擎算法</li>
              </ul>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="🔧 支持的AI服务">
            <div class="help-section">
              <h3>🌐 官方AI服务</h3>
              <div class="ai-service-grid">
                <div class="ai-service-card">
                  <h4>OpenAI</h4>
                  <p>GPT-3.5-turbo、GPT-4等模型，功能强大，分析准确</p>
                  <span class="service-tag recommended">推荐</span>
                </div>
                <div class="ai-service-card">
                  <h4>DeepSeek</h4>
                  <p>专业的代码分析能力，适合技术SEO优化</p>
                  <span class="service-tag">专业</span>
                </div>
                <div class="ai-service-card">
                  <h4>百度文心</h4>
                  <p>本土化优势，更适合中文网站SEO分析</p>
                  <span class="service-tag">本土化</span>
                </div>
                <div class="ai-service-card">
                  <h4>智谱AI</h4>
                  <p>ChatGLM系列模型，平衡性能与成本</p>
                  <span class="service-tag">平衡</span>
                </div>
                <div class="ai-service-card">
                  <h4>豆包/Claude</h4>
                  <p>多样化选择，满足不同需求</p>
                  <span class="service-tag">多样化</span>
                </div>
              </div>
              
              <h3>🔧 自定义AI服务</h3>
              <p>支持任何兼容OpenAI API格式的AI服务，包括：</p>
              <ul>
                <li><strong>本地部署模型：</strong>如Ollama、LM Studio等本地AI服务</li>
                <li><strong>第三方API：</strong>各种AI服务商的兼容接口</li>
                <li><strong>企业内部API：</strong>公司内部部署的AI模型服务</li>
                <li><strong>代理服务：</strong>通过代理访问的AI API</li>
              </ul>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="⚙️ 配置说明">
            <div class="help-section">
              <h3>🔑 获取API密钥</h3>
              <div class="config-steps">
                <div class="step-item">
                  <div class="step-number">1</div>
                  <div class="step-content">
                    <h4>OpenAI</h4>
                    <p>访问 <a href="https://platform.openai.com" target="_blank">platform.openai.com</a> 注册账户并创建API密钥</p>
                  </div>
                </div>
                <div class="step-item">
                  <div class="step-number">2</div>
                  <div class="step-content">
                    <h4>百度文心</h4>
                    <p>格式：<code>client_id:client_secret</code>，在百度智能云创建应用获取</p>
                  </div>
                </div>
                <div class="step-item">
                  <div class="step-number">3</div>
                  <div class="step-content">
                    <h4>智谱AI</h4>
                    <p>格式：<code>api_key_id.api_key_secret</code>，在智谱开放平台获取</p>
                  </div>
                </div>
              </div>
              
              <h3>🔧 自定义配置示例</h3>
              <div class="config-example">
                <h4>示例1：本地Ollama服务</h4>
                <div class="code-block">
                  <pre>API端点：http://localhost:11434/v1/chat/completions
请求格式：OpenAI格式
模型名称：llama2, qwen等</pre>
                </div>
                
                <h4>示例2：自定义API服务</h4>
                <div class="code-block">
                  <pre>API端点：https://your-api.com/v1/chat
请求格式：完全自定义
自定义载荷：{"messages": "{prompt}", "model": "custom"}
响应路径：data.response.text</pre>
                </div>
              </div>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="💡 使用技巧">
            <div class="help-section">
              <h3>🎯 如何获得更好的分析结果</h3>
              <ul>
                <li><strong>完善基础配置：</strong>确保网站标题、描述、关键词等基础信息完整</li>
                <li><strong>启用文章内容分析：</strong>勾选"包含最近文章内容"选项，获得更具体的建议</li>
                <li><strong>定期分析：</strong>在网站内容或配置更新后重新进行AI分析</li>
                <li><strong>配合人工优化：</strong>AI建议需要结合实际情况进行调整</li>
              </ul>
              
              <h3>⚠️ 注意事项</h3>
              <ul>
                <li><strong>API成本：</strong>每次AI分析会消耗一定的API费用，建议合理使用</li>
                <li><strong>数据安全：</strong>API密钥采用加密存储，但建议使用专门的API密钥</li>
                <li><strong>网络连接：</strong>确保服务器能够访问选择的AI服务</li>
                <li><strong>结果参考：</strong>AI建议仅供参考，需要结合实际情况判断</li>
              </ul>
              
              <h3>🚀 优化建议</h3>
              <ul>
                <li><strong>模型选择：</strong>GPT-4更准确但成本更高，GPT-3.5-turbo性价比更好</li>
                <li><strong>分析频率：</strong>建议每周或内容更新后进行一次分析</li>
                <li><strong>结合工具：</strong>配合Google Search Console等工具验证优化效果</li>
              </ul>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="showHelpDialog = false">关闭</el-button>
        <el-button type="primary" @click="showApiConfigDialog = true; showHelpDialog = false">配置AI API</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>

export default {
  data() {
    return {
      initialLoad: true,
      seoConfig: {
        enable: false,
        site_address: "",
        site_title: "",
        site_description: "Poetize：作诗，有诗意地描写。个人博客，生活倒影，记录生活。",
        site_keywords: "Poetize,博客,个人网站,生活笔记,记录生活",
        default_author: "poetize",
        og_image: "",
        twitter_card: "summary_large_image",
        twitter_site: "",
        baidu_site_verification: "",
        google_site_verification: "",
        bing_site_verification: "",
        yandex_site_verification: "",
        sogou_site_verification: "",
        shenma_site_verification: "",
        so_site_verification: "",
        yahoo_site_verification: "",
        baidu_push_enabled: false,
        baidu_push_token: "",
        google_index_enabled: false,
        google_api_key: "",
        bing_push_enabled: false,
        bing_api_key: "",
        yandex_push_enabled: false,
        yandex_api_key: "",
        sogou_push_enabled: false,
        sogou_push_token: "",
        so_push_enabled: false,
        so_push_token: "",
        shenma_push_enabled: false,
        shenma_token: "",
        enable_push_notification: false,
        notify_only_on_failure: false,
        notification_email: "",
        robots_txt: "User-agent: *\nAllow: /\nDisallow: /admin/\nSitemap: /sitemap.xml",
        article_url_format: "article/{id}",
        category_url_format: "category/{id}",
        tag_url_format: "tag/{id}",
        generate_sitemap: true,
        sitemap_change_frequency: "weekly",
        sitemap_priority: "0.7",
        // 新增: 站点地图排除列表，逗号分隔路径
        sitemap_exclude: "/love",
        auto_generate_meta_tags: true,
        custom_head_code: ""
      },
      loading: false,
      updateLoading: false,
      analyzeLoading: false,
      aiAnalyzeLoading: false,
      apiConfigLoading: false,
      showAnalysisDialog: false,
      showAiAnalysisDialog: false,
      showApiConfigDialog: false,
      showHelpDialog: false,
      seoAnalysis: null,
      aiSeoAnalysis: null,
      aiApiConfig: {
        provider: 'openai',
        api_key: '',
        api_base: '',
        model: 'gpt-3.5-turbo',
        include_articles: false,
        custom_api_url: '',
        request_format: 'openai',
        custom_headers_list: []
      }
    };
  },
  
  created() {
    try {
      console.log('SEO配置组件初始化');
      this.getSeoConfig();
    } catch (error) {
      console.error('SEO配置组件初始化失败', error);
      this.$message.error('SEO配置加载失败，请刷新页面重试');
    }
  },
  
  watch: {
    'seoConfig.enable': {
      handler(newVal, oldVal) {
        if (!this.initialLoad) {
          this.saveEnableStatus(newVal);
        }
      }
    }
  },
  
  methods: {
    saveEnableStatus(status) {
      // 确保状态是布尔值
      const enableStatus = status === undefined ? false : !!status;
      console.log('开始保存SEO开关状态:', enableStatus);
      
      this.$http.post(this.$constant.pythonBaseURL + '/python/seo/updateEnableStatus', {
        enable: enableStatus
      }, true)
      .then(res => {
        console.log('SEO开关状态保存响应:', res);
        if (res.code === 200) {
          console.log('SEO开关状态保存成功, 新状态:', enableStatus);
          this.$message.success('SEO开关状态已保存');
        } else {
          console.error('SEO开关状态保存失败, 错误信息:', res.message);
          this.$message.error(res.message || 'SEO开关状态保存失败');
        }
      })
      .catch(error => {
        console.error('保存SEO开关状态时发生网络错误:', error);
        this.$message.error('保存SEO开关状态时发生错误');
      });
    },
    
    getSeoConfig() {
      console.log('开始获取SEO配置...');
      try {
        // 使用封装好的HTTP请求工具，设置isAdmin=true
        this.$http.get(this.$constant.pythonBaseURL + '/seo/getSeoConfig', {}, true)
          .then((res) => {
            console.log('获取SEO配置响应数据:', res);
            if (res && res.code === 200 && res.data) {
              console.log('获取SEO配置成功, 配置项数量:', Object.keys(res.data).length);
              // 确保enable字段存在，如果不存在设置为false
              const config = res.data;
              if (config.enable === undefined) {
                console.log('SEO开关状态不存在，设置默认值为false');
                config.enable = false;
              }
              console.log('SEO开关当前状态:', config.enable);
              
              this.seoConfig = config;
              this.$nextTick(() => {
                this.initialLoad = false;
              });
            } else {
              console.error('获取SEO配置失败，响应数据异常:', res);
              this.$message.error('获取SEO配置失败: ' + (res ? res.message || '未知错误' : '响应数据为空'));
              // 使用默认配置
              this.initialLoad = false;
            }
          })
          .catch((error) => {
            console.error('获取SEO配置网络请求失败:', error);
            console.error('错误详情:', error.response ? error.response.data : '无响应数据');
            this.$message.error('获取SEO配置失败: ' + (error.message || '网络连接问题'));
            // 使用默认配置
            this.initialLoad = false;
          });
      } catch (e) {
        console.error('调用SEO配置API时出现异常:', e);
        this.$message.error('获取SEO配置时遇到问题，请检查网络连接');
        // 使用默认配置
        this.initialLoad = false;
      }
    },
    
    saveSeoConfig() {
      this.loading = true;
      console.log('正在保存SEO配置...');
      this.$http.post(this.$constant.pythonBaseURL + '/python/seo/updateSeoConfig', this.seoConfig, true)
        .then((res) => {
          this.loading = false;
          console.log('保存SEO配置响应:', res);
          if (res && res.code === 200) {
            this.$message.success('保存SEO配置成功');
            this.showAnalysisDialog = false;
            this.showAiAnalysisDialog = false;
          } else {
            console.error('保存SEO配置失败，响应数据异常:', res);
            this.$message.error(res ? res.message || '保存SEO配置失败' : '响应数据为空');
          }
        })
        .catch((error) => {
          this.loading = false;
          console.error('保存SEO配置失败:', error);
          this.$message.error('保存SEO配置失败: ' + (error.message || '网络连接问题'));
        });
    },
    
    updateSeoData() {
      this.updateLoading = true;
      console.log('正在更新SEO数据...');
      this.$http.post(this.$constant.pythonBaseURL + '/python/seo/updateSeoData', {}, true)
        .then((res) => {
          this.updateLoading = false;
          console.log('更新SEO数据响应:', res);
          if (res && res.code === 200) {
            this.$message.success('更新SEO数据成功');
          } else {
            console.error('更新SEO数据失败，响应数据异常:', res);
            this.$message.error(res ? res.message || '更新SEO数据失败' : '响应数据为空');
          }
        })
        .catch((error) => {
          this.updateLoading = false;
          console.error('更新SEO数据失败:', error);
          this.$message.error('更新SEO数据失败: ' + (error.message || '网络连接问题'));
        });
    },
    
    analyzeSite() {
      this.analyzeLoading = true;
      console.log('正在进行SEO分析...');
      this.$http.get(this.$constant.pythonBaseURL + '/python/seo/analyzeSite', {}, true)
        .then((res) => {
          this.analyzeLoading = false;
          console.log('SEO分析响应:', res);
          if (res && res.code === 200) {
            this.seoAnalysis = res.data;
            this.showAnalysisDialog = true;
          } else {
            console.error('SEO分析失败，响应数据异常:', res);
            this.$message.error(res ? res.message || 'SEO分析失败' : '响应数据为空');
          }
        })
        .catch((error) => {
          this.analyzeLoading = false;
          console.error('SEO分析失败:', error);
          this.$message.error('SEO分析失败: ' + (error.message || '网络连接问题'));
        });
    },
    
    aiAnalyze() {
      this.aiAnalyzeLoading = true;
      console.log('准备进行AI SEO分析...');
      
      // 先检查AI API是否已配置
      this.$http.get(this.$constant.pythonBaseURL + '/python/seo/checkAiApiConfig', {}, true)
        .then((res) => {
          if (res && res.code === 200 && res.data && res.data.configured) {
            // API已配置，询问用户是否使用上次的配置
            this.aiAnalyzeLoading = false;
            this.$confirm('检测到已配置的AI API，是否使用上次的配置进行分析？', 'AI SEO分析', {
              confirmButtonText: '使用上次配置',
              cancelButtonText: '重新配置',
              type: 'info'
            }).then(() => {
              // 用户确认使用上次的配置
              this.aiAnalyzeLoading = true;
              this.executeAiAnalysis();
            }).catch(() => {
              // 用户选择重新配置
              this.showApiConfigDialog = true;
            });
          } else {
            // API未配置，显示配置弹窗
            this.aiAnalyzeLoading = false;
            this.showApiConfigDialog = true;
            this.$message.warning('请先配置AI API才能进行AI SEO分析');
          }
        })
        .catch((error) => {
          this.aiAnalyzeLoading = false;
          console.error('检查AI API配置失败:', error);
          this.$message.error('检查AI API配置失败: ' + (error.message || '网络连接问题'));
          this.showApiConfigDialog = true;
        });
    },
    
    executeAiAnalysis() {
      console.log('正在进行AI SEO分析...');
      this.$http.get(this.$constant.pythonBaseURL + '/python/seo/aiAnalyzeSite', {}, true)
        .then((res) => {
          this.aiAnalyzeLoading = false;
          console.log('AI SEO分析响应:', res);
          if (res && res.code === 200) {
            this.aiSeoAnalysis = res.data;
            this.showAiAnalysisDialog = true;
          } else {
            console.error('AI SEO分析失败，响应数据异常:', res);
            this.$message.error(res ? res.message || 'AI SEO分析失败' : '响应数据为空');
            
            // 如果错误是由于API配置问题，显示API配置弹窗
            if (res && res.code === 401) {
              this.showApiConfigDialog = true;
            }
          }
        })
        .catch((error) => {
          this.aiAnalyzeLoading = false;
          console.error('AI SEO分析失败:', error);
          this.$message.error('AI SEO分析失败: ' + (error.message || '网络连接问题'));
        });
    },
    
    getSeoScoreStatus(score) {
      if (score >= 90) return 'success';
      if (score >= 70) return 'warning';
      return 'exception';
    },
    
    getSuggestionType(type) {
      switch (type) {
        case 'error': return 'error';
        case 'warning': return 'warning';
        case 'info': return 'info';
        default: return 'info';
      }
    },
    
    onProviderChange() {
      // 当提供商更改时重置相关配置
      console.log('AI模型提供商已更改:', this.aiApiConfig.provider);
      
      // 根据不同提供商设置默认模型
      if (this.aiApiConfig.provider === 'openai') {
        this.aiApiConfig.model = 'gpt-4o';
      } else if (this.aiApiConfig.provider === 'deepseek') {
        this.aiApiConfig.model = 'deepseek-chat';
      } else if (this.aiApiConfig.provider === 'baidu') {
        this.aiApiConfig.model = 'ernie-bot';
      } else if (this.aiApiConfig.provider === 'zhipu') {
        this.aiApiConfig.model = 'chatglm_turbo';
      } else if (this.aiApiConfig.provider === 'doubao') {
        this.aiApiConfig.model = 'doubao-pro';
      } else if (this.aiApiConfig.provider === 'claude') {
        this.aiApiConfig.model = 'claude-3.5-sonnet';
      } else if (this.aiApiConfig.provider === 'custom') {
        this.aiApiConfig.model = '';
        this.aiApiConfig.request_format = 'openai';
        this.aiApiConfig.custom_api_url = '';
        this.aiApiConfig.custom_headers_list = [];
      }
    },
    
    updateCustomHeaders() {
      // 将custom_headers_list转换为后端需要的格式
      const headers = {};
      this.aiApiConfig.custom_headers_list.forEach(header => {
        if (header.key && header.value) {
          headers[header.key] = header.value;
        }
      });
      this.aiApiConfig.custom_headers = headers;
    },
    
    removeHeader(index) {
      this.aiApiConfig.custom_headers_list.splice(index, 1);
      this.updateCustomHeaders();
    },
    
    addHeader() {
      this.aiApiConfig.custom_headers_list.push({
        key: '',
        value: ''
      });
    },
    
    saveApiConfig() {
      this.apiConfigLoading = true;
      console.log('正在保存AI API配置...');
      
      // 准备发送给后端的配置数据
      const configToSave = { ...this.aiApiConfig };
      
      // 处理自定义AI服务的特殊配置
      if (this.aiApiConfig.provider === 'custom') {
        // 验证必填字段
        if (!this.aiApiConfig.custom_api_url) {
          this.apiConfigLoading = false;
          this.$message.error('请填写API端点URL');
          return;
        }
        
        // 处理自定义请求头
        this.updateCustomHeaders();
      }
      
      this.$http.post(this.$constant.pythonBaseURL + '/python/seo/saveAiApiConfig', configToSave, true)
        .then((res) => {
          this.apiConfigLoading = false;
          console.log('保存AI API配置响应:', res);
          if (res && res.code === 200) {
            this.$message.success('保存AI API配置成功');
            this.showApiConfigDialog = false;
            // 如果配置成功，可以询问用户是否立即进行AI分析
            this.$confirm('AI API配置已保存，是否立即进行AI SEO分析？', '配置成功', {
              confirmButtonText: '立即分析',
              cancelButtonText: '稍后分析',
              type: 'success'
            }).then(() => {
              this.executeAiAnalysis();
            }).catch(() => {
              // 用户选择稍后分析，不做任何操作
            });
          } else {
            console.error('保存AI API配置失败，响应数据异常:', res);
            this.$message.error(res ? res.message || '保存AI API配置失败' : '响应数据为空');
          }
        })
        .catch((error) => {
          this.apiConfigLoading = false;
          console.error('保存AI API配置失败:', error);
          this.$message.error('保存AI API配置失败: ' + (error.message || '网络连接问题'));
        });
    },
    handleAiCommand(command) {
      if (command === 'analyze') {
        this.aiAnalyze();
      } else if (command === 'config') {
        this.showApiConfigDialog = true;
      } else if (command === 'help') {
        this.showHelpDialog = true;
      }
    }
  }
};
</script>

<style scoped>
  /* 页面容器 */
  .seo-management {
    padding: 24px;
    background-color: #fafafa;
    min-height: calc(100vh - 60px);
  }

  /* 页面标题区域 */
  .page-header {
    margin-bottom: 24px;
  }

  .title-section {
    background: #ffffff;
    padding: 24px;
    border-radius: 8px;
    border-left: 4px solid #2d3748;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }

  .page-title {
    font-size: 24px;
    font-weight: 600;
    margin: 0 0 8px 0;
    display: flex;
    align-items: center;
    gap: 8px;
    color: #2d3748;
  }

  .page-title i {
    font-size: 24px;
    color: #4a5568;
  }

  .page-description {
    font-size: 14px;
    margin: 0;
    color: #718096;
    line-height: 1.5;
  }

  /* 苹果风格设计 */
  .box-card {
    border-radius: 20px;
    border: none;
    box-shadow: 0 4px 20px 0 rgba(0, 0, 0, 0.08);
    background-color: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    margin-bottom: 25px;
    overflow: hidden;
    transition: all 0.4s ease;
  }
  
  .box-card:hover {
    box-shadow: 0 5px 25px 0 rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
  }
  
  .box-card .el-card__header {
    background: linear-gradient(135deg, #f8f9fa, #f0f2f5);
    border-bottom: 1px solid rgba(0, 0, 0, 0.03);
    padding: 18px 24px;
  }
  
  .box-card .el-card__header span {
    font-weight: 600;
    font-size: 16px;
    color: #1d1d1f;
    letter-spacing: 0.5px;
  }
  
  .box-card .el-card__body {
    padding: 28px;
  }
  
  /* 标签页样式 */
  ::v-deep .el-tabs--border-card {
    border-radius: 16px;
    border: none;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.04);
    overflow: hidden;
    background-color: rgba(250, 250, 252, 0.8);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
  }
  
  ::v-deep .el-tabs__header {
    background: linear-gradient(to right, #f8f9fa, #f5f7fa);
    border-bottom: 1px solid rgba(0, 0, 0, 0.03);
    padding: 4px 0 0;
  }
  
  ::v-deep .el-tabs__item {
    height: 44px;
    line-height: 44px;
    font-weight: 500;
    color: #86868b;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    margin: 0 4px;
    padding: 0 18px;
  }
  
  ::v-deep .el-tabs__item.is-active {
    color: #0071e3;
  }
  
  ::v-deep .el-tabs__item:hover {
    color: #0071e3;
  }
  
  ::v-deep .el-tabs__nav-wrap::after {
    height: 1px;
    background-color: rgba(0, 0, 0, 0.03);
  }
  
  ::v-deep .el-tabs__active-bar {
    background-color: #0071e3;
    height: 3px;
    border-radius: 3px;
  }
  
  /* 表单元素样式 */
  ::v-deep .el-form-item__label {
    font-weight: 500;
    color: #1d1d1f;
    padding-bottom: 8px;
    font-size: 14px;
  }
  
  ::v-deep .el-input__inner,
  ::v-deep .el-textarea__inner {
    border-radius: 10px;
    border: 1px solid rgba(0, 0, 0, 0.1);
    background-color: rgba(245, 247, 250, 0.7);
    transition: all 0.3s;
    padding: 0 15px;
    height: 38px;
    color: #1d1d1f;
    font-size: 14px;
  }
  
  ::v-deep .el-textarea__inner {
    padding: 10px 15px;
  }
  
  ::v-deep .el-input__inner:hover,
  ::v-deep .el-textarea__inner:hover {
    border-color: rgba(0, 0, 0, 0.2);
    background-color: rgba(247, 248, 250, 0.9);
  }
  
  ::v-deep .el-input__inner:focus,
  ::v-deep .el-textarea__inner:focus {
    border-color: #0071e3;
    background-color: #fff;
    box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.15);
  }
  
  ::v-deep .el-input__count {
    background: transparent;
    font-size: 12px;
    color: #86868b;
  }
  
  /* 分割线样式 */
  ::v-deep .el-divider {
    margin: 32px 0;
  }
  
  ::v-deep .el-divider__text {
    background-color: transparent;
    font-weight: 600;
    font-size: 15px;
    color: #1d1d1f;
    padding: 0 14px;
  }
  
  /* 开关样式 */
  ::v-deep .el-switch {
    height: 24px;
  }
  
  ::v-deep .el-switch__core {
    border-radius: 16px;
    height: 24px;
    width: 44px !important;
    background-color: #e4e4e4;
    border: none;
  }
  
  ::v-deep .el-switch__core:after {
    height: 20px;
    width: 20px;
    top: 2px;
    left: 2px;
    border-radius: 50%;
    background-color: white;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }
  
  ::v-deep .el-switch.is-checked .el-switch__core {
    background-color: #34c759;
    border-color: #34c759;
  }
  
  ::v-deep .el-switch.is-checked .el-switch__core:after {
    left: 100%;
    margin-left: -22px;
  }
  
  /* 提示文本样式 */
  .tip {
    display: block;
    margin-top: 6px;
    font-size: 12px;
    color: #86868b;
    line-height: 1.4;
    letter-spacing: 0.2px;
  }
  
  /* 帮助图标 */
  .help-icon {
    color: #86868b;
    cursor: pointer;
    margin-left: 5px;
    font-size: 14px;
    transition: all 0.2s;
  }
  
  .help-icon:hover {
    color: #0071e3;
  }
  
  /* 标签页内部描述 */
  .tab-description {
    background-color: rgba(0, 113, 227, 0.06);
    padding: 12px 18px;
    border-radius: 12px;
    margin-bottom: 20px;
    font-size: 13px;
    color: #1d1d1f;
    line-height: 1.5;
    display: flex;
    align-items: center;
    letter-spacing: 0.2px;
  }
  
  .tab-description i {
    color: #0071e3;
    margin-right: 10px;
    font-size: 16px;
  }
  
  /* 表单项间距 */
  ::v-deep .el-form-item {
    margin-bottom: 24px;
  }
  
  /* 选择器样式 */
  ::v-deep .el-select .el-input.is-focus .el-input__inner {
    border-color: #0071e3;
  }
  
  ::v-deep .el-select-dropdown {
    border-radius: 12px;
    box-shadow: 0 5px 20px rgba(0, 0, 0, 0.15);
    border: none;
    padding: 8px 0;
  }
  
  ::v-deep .el-select-dropdown__item {
    height: 40px;
    line-height: 40px;
    padding: 0 18px;
    color: #1d1d1f;
    font-size: 14px;
  }
  
  ::v-deep .el-select-dropdown__item.hover,
  ::v-deep .el-select-dropdown__item:hover {
    background-color: rgba(0, 113, 227, 0.06);
  }
  
  ::v-deep .el-select-dropdown__item.selected {
    color: #0071e3;
    font-weight: 500;
    background-color: transparent;
  }
  
  /* 按钮样式 */
  ::v-deep .el-button {
    border-radius: 22px;
    padding: 10px 20px;
    font-weight: 500;
    transition: all 0.3s;
    border: none;
    font-size: 14px;
  }
  
  ::v-deep .el-button--primary {
    background: linear-gradient(to right, #0071e3, #42a4ff);
    color: white;
    box-shadow: 0 4px 10px rgba(0, 113, 227, 0.3);
  }
  
  ::v-deep .el-button--primary:hover {
    background: linear-gradient(to right, #0062c3, #3b96ea);
    box-shadow: 0 5px 12px rgba(0, 113, 227, 0.4);
    transform: translateY(-1px);
  }
  
  ::v-deep .el-button--success {
    background: linear-gradient(to right, #28cd41, #5ddd6e);
    box-shadow: 0 4px 10px rgba(52, 199, 89, 0.3);
  }
  
  ::v-deep .el-button--success:hover {
    background: linear-gradient(to right, #20b938, #4cc75d);
    box-shadow: 0 5px 12px rgba(52, 199, 89, 0.4);
    transform: translateY(-1px);
  }
  
  ::v-deep .el-button--info {
    background: linear-gradient(to right, #8e8e93, #aeaeb2);
    box-shadow: 0 4px 10px rgba(142, 142, 147, 0.3);
  }
  
  ::v-deep .el-button--info:hover {
    background: linear-gradient(to right, #7c7c82, #9d9da1);
    box-shadow: 0 5px 12px rgba(142, 142, 147, 0.4);
    transform: translateY(-1px);
  }
  
  ::v-deep .el-button--warning {
    background: linear-gradient(to right, #ff9500, #ffbd2e);
    box-shadow: 0 4px 10px rgba(255, 149, 0, 0.3);
  }
  
  ::v-deep .el-button--warning:hover {
    background: linear-gradient(to right, #e68600, #efb029);
    box-shadow: 0 5px 12px rgba(255, 149, 0, 0.4);
    transform: translateY(-1px);
  }
  
  /* 按钮区域 */
  ::v-deep .el-form > div[style="text-align: center;"] {
    margin-top: 40px;
    display: flex;
    justify-content: center;
    gap: 15px;
  }
  
  /* 对话框样式 */
  ::v-deep .el-dialog {
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  }
  
  ::v-deep .el-dialog__header {
    background: linear-gradient(135deg, #f8f9fa, #f0f2f5);
    padding: 18px 24px;
  }
  
  ::v-deep .el-dialog__title {
    font-weight: 600;
    color: #1d1d1f;
    font-size: 16px;
  }
  
  ::v-deep .el-dialog__body {
    padding: 28px 24px;
  }
  
  ::v-deep .el-dialog__footer {
    border-top: 1px solid rgba(0, 0, 0, 0.03);
    padding: 15px 24px;
  }
  
  /* 进度条样式 */
  ::v-deep .el-progress-circle {
    width: 120px !important;
    height: 120px !important;
  }
  
  /* 分析得分样式 */
  .analysis-score {
    text-align: center;
    margin-bottom: 30px;
    padding: 10px;
  }
  
  .score-label {
    font-weight: 500;
    font-size: 16px;
    margin-top: 15px;
    color: #1d1d1f;
  }
  
  /* 分析建议样式 */
  .analysis-suggestions {
    margin-top: 30px;
  }
  
  .analysis-suggestions h3 {
    margin-bottom: 18px;
    font-weight: 600;
    color: #1d1d1f;
    font-size: 16px;
  }
  
  ::v-deep .el-alert {
    border-radius: 12px;
    margin-bottom: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  }
  
  .suggestion-detail {
    font-size: 12px;
    color: #86868b;
    margin-top: 8px;
    line-height: 1.5;
  }
  
  /* AI分析内容样式 */
  .ai-analysis-content {
    margin-top: 30px;
    background-color: rgba(0, 113, 227, 0.03);
    padding: 20px;
    border-radius: 16px;
  }
  
  .ai-analysis-text {
    color: #1d1d1f;
    line-height: 1.6;
    font-size: 14px;
  }
  
  /* 代码编辑器样式 */
  .code-editor-wrapper {
    position: relative;
    border-radius: 12px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
    margin-bottom: 5px;
    background-color: rgba(240, 243, 248, 0.8);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
    border: 1px solid rgba(0, 0, 0, 0.1);
    overflow: hidden;
  }
  
  .code-line-numbers {
    position: absolute;
    top: 0;
    left: 0;
    width: 40px;
    height: 100%;
    padding: 15px 0;
    background-color: rgba(20, 30, 40, 0.03);
    border-right: 1px solid rgba(0, 0, 0, 0.05);
    user-select: none;
    pointer-events: none;
  }
  
  .line-number {
    text-align: right;
    padding-right: 8px;
    color: #86868b;
    font-size: 12px;
    line-height: 1.6;
    font-family: 'SF Mono', Monaco, Menlo, Consolas, 'Courier New', monospace;
  }
  
  ::v-deep .code-editor {
    width: 100%;
  }
  
  ::v-deep .code-editor .el-textarea__inner {
    font-family: 'SF Mono', Monaco, Menlo, Consolas, 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.6;
    color: #2c3e50;
    background-color: transparent;
    border: none;
    padding: 15px 15px 15px 50px;
    letter-spacing: 0.3px;
    min-height: 200px;
    resize: vertical;
  }
  
  ::v-deep .code-editor .el-textarea__inner:focus {
    border: none;
    box-shadow: none;
  }
  
  .code-editor-wrapper:focus-within {
    border-color: #0071e3;
    box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.15);
  }
  
  /* 添加禁用区域的样式 */
  .disabled-section {
    position: relative;
    opacity: 0.7;
    pointer-events: none;
  }
  
  .disabled-section::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: rgba(245, 245, 245, 0.4);
    z-index: 10;
  }
  
  /* 警告提示样式 */
  .warning-tip {
    color: #E6A23C;
    font-weight: 500;
    display: block;
    margin-top: 5px;
  }
  
  /* 自定义AI配置样式 */
  .custom-option {
    color: #ff6b35 !important;
    font-weight: 500 !important;
  }
  
  .custom-headers {
    margin-bottom: 10px;
  }
  
  .header-item {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
  }
  
  .header-key {
    flex: 1;
  }
  
  .header-value {
    flex: 2;
  }
  
  .header-item .el-button {
    flex-shrink: 0;
    width: 32px;
    height: 32px;
    padding: 0;
    margin-left: 5px;
  }
  
  /* 自定义配置提示框 */
  .custom-tip {
    background: linear-gradient(135deg, #e3f2fd, #bbdefb);
    border: 1px solid #2196f3;
    border-radius: 8px;
    padding: 12px;
    margin: 10px 0;
    font-size: 13px;
    color: #1976d2;
  }
  
  .custom-tip .el-icon-info {
    margin-right: 8px;
    color: #2196f3;
  }
  
  /* JSON编辑器样式增强 */
  ::v-deep .el-textarea.is-disabled .el-textarea__inner {
    background-color: rgba(245, 247, 250, 0.5);
  }
  
  ::v-deep .code-textarea .el-textarea__inner {
    font-family: 'SF Mono', Monaco, Menlo, Consolas, 'Courier New', monospace;
    tab-size: 2;
    white-space: pre;
  }
  
  /* 自定义配置标签样式 */
  .config-section {
    background: rgba(248, 249, 250, 0.8);
    border-radius: 12px;
    padding: 16px;
    margin: 15px 0;
    border: 1px solid rgba(0, 0, 0, 0.05);
  }
  
  .config-section-title {
    font-weight: 600;
    color: #1d1d1f;
    margin-bottom: 12px;
    font-size: 14px;
  }
  
  /* 高级配置展开动画 */
  .advanced-config-enter-active,
  .advanced-config-leave-active {
    transition: all 0.3s ease;
  }
  
  .advanced-config-enter,
  .advanced-config-leave-to {
    opacity: 0;
    transform: translateY(-10px);
  }

  /* 高级配置动画 */
  .advanced-config-enter-active, .advanced-config-leave-active {
    transition: all 0.3s ease;
  }

  .advanced-config-enter, .advanced-config-leave-to {
    opacity: 0;
    transform: translateY(-10px);
  }

  /* AI帮助弹窗样式 */
  .help-content {
    max-height: 70vh;
    overflow-y: auto;
  }

  .help-section {
    padding: 20px 0;
  }

  .help-section h3 {
    color: #409EFF;
    margin-bottom: 15px;
    font-size: 16px;
    font-weight: 600;
  }

  .help-section p {
    line-height: 1.6;
    margin-bottom: 15px;
    color: #606266;
  }

  .help-section ul {
    padding-left: 20px;
    margin-bottom: 15px;
  }

  .help-section li {
    line-height: 1.6;
    margin-bottom: 8px;
    color: #606266;
  }

  .help-section strong {
    color: #303133;
    font-weight: 600;
  }

  /* AI服务卡片网格 */
  .ai-service-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: 15px;
    margin: 20px 0;
  }

  .ai-service-card {
    border: 1px solid #EBEEF5;
    border-radius: 8px;
    padding: 15px;
    background: #FAFAFA;
    position: relative;
    transition: all 0.3s ease;
  }

  .ai-service-card:hover {
    border-color: #409EFF;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
  }

  .ai-service-card h4 {
    margin: 0 0 8px 0;
    color: #303133;
    font-size: 14px;
    font-weight: 600;
  }

  .ai-service-card p {
    margin: 0;
    font-size: 12px;
    color: #909399;
    line-height: 1.4;
  }

  .service-tag {
    position: absolute;
    top: 8px;
    right: 8px;
    background: #E4E7ED;
    color: #909399;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 500;
  }

  .service-tag.recommended {
    background: #67C23A;
    color: white;
  }

  /* 配置步骤 */
  .config-steps {
    margin: 20px 0;
  }

  .step-item {
    display: flex;
    align-items: flex-start;
    margin-bottom: 20px;
    padding: 15px;
    background: #F5F7FA;
    border-radius: 8px;
    border-left: 4px solid #409EFF;
  }

  .step-number {
    background: #409EFF;
    color: white;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    margin-right: 15px;
    flex-shrink: 0;
  }

  .step-content h4 {
    margin: 0 0 5px 0;
    color: #303133;
    font-size: 14px;
    font-weight: 600;
  }

  .step-content p {
    margin: 0;
    color: #606266;
    font-size: 13px;
    line-height: 1.5;
  }

  .step-content code {
    background: #F56C6C;
    color: white;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 12px;
  }

  .step-content a {
    color: #409EFF;
    text-decoration: none;
  }

  .step-content a:hover {
    text-decoration: underline;
  }

  /* 配置示例 */
  .config-example {
    margin: 20px 0;
  }

  .config-example h4 {
    color: #303133;
    margin: 15px 0 8px 0;
    font-size: 14px;
    font-weight: 600;
  }

  .code-block {
    background: #F5F7FA;
    border: 1px solid #EBEEF5;
    border-radius: 6px;
    margin-bottom: 15px;
  }

  .code-block pre {
    margin: 0;
    padding: 15px;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 12px;
    line-height: 1.5;
    color: #303133;
    white-space: pre-wrap;
    word-wrap: break-word;
  }

  /* 帮助弹窗标签页样式优化 */
  .help-content .el-tabs--border-card {
    border: none;
    box-shadow: none;
  }

  .help-content .el-tabs--border-card > .el-tabs__content {
    padding: 20px;
  }

  .help-content .el-tabs__item {
    font-weight: 500;
  }

  .help-content .el-tabs__item.is-active {
    color: #409EFF;
  }
</style>