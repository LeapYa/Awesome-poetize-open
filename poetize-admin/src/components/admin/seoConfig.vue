<template>
  <div class="seo-management">
    <!-- 页面标题 -->
    <div style="margin-bottom: 30px;">
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        SEO 配置
      </el-tag>
      
      <!-- 移动端优化提示 -->
      <div v-if="isMobileDevice" class="mobile-optimization-tip">
        <el-alert
          title="📱 移动端界面已优化"
          description="界面已针对移动设备优化，支持触摸操作和响应式布局"
          type="info"
          :closable="true"
          show-icon
          style="margin-top: 12px;">
        </el-alert>
      </div>
    </div>

    <el-card class="box-card" shadow="never">
      <el-form :model="seoConfig" :label-width="formLabelWidth" :size="buttonSize">
        <el-form-item label="启用SEO优化">
          <el-switch v-model="seoConfig.enable"></el-switch>
        </el-form-item>
        
        <div :class="{'disabled-section': !seoConfig.enable}">
        
        <el-form-item id="field-seo-description" label="网站描述">
          <el-input v-model="seoConfig.site_description" type="textarea" :rows="2" maxlength="200"></el-input>
          <span class="tip">描述应当简明扼要，不超过200字符。<b class="warning-tip">注意：为保持一致性，请优先在"网站设置"中修改网站描述，该处的设置将覆盖此处配置。</b></span>
        </el-form-item>
        
        <el-form-item id="field-seo-keywords" label="网站关键词">
          <el-input v-model="seoConfig.site_keywords" placeholder="多个关键词用逗号分隔" maxlength="100"></el-input>
          <span class="tip">关键词应当与网站内容相关，用逗号分隔，不超过10个。<b class="warning-tip">注意：为保持一致性，请优先在"网站设置"中修改网站关键词，该处的设置将覆盖此处配置。</b></span>
        </el-form-item>
        
        <el-form-item id="field-seo-author" label="默认作者">
          <el-input v-model="seoConfig.default_author" maxlength="30"></el-input>
        </el-form-item>
        
        <el-form-item label="智能图标生成">
          <div class="icon-generator-simple">
            <!-- 上传区域 -->
            <div class="upload-card">
              <el-upload
                ref="iconUpload"
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                :accept="'image/*'"
                :on-change="handleIconUpload"
                drag
                class="simple-upload">
                
                <div class="upload-content" v-if="!uploadedImage">
                  <i class="el-icon-upload2"></i>
                  <p>拖拽图片到此处或点击上传</p>
                  <span>建议512x512以上PNG/JPG，一键生成全套图标和封面图</span>
                </div>
                
                <div class="file-preview" v-else>
                  <img :src="getImagePreview(uploadedImage)" alt="预览">
                  <div class="file-name">{{ uploadedImage.name }}</div>
                </div>
              </el-upload>
              
              <!-- 操作按钮 -->
              <div class="actions" v-if="uploadedImage">
                <el-button 
                  type="primary" 
                  @click="batchGenerateIcons"
                  :loading="generatingIcons"
                  size="small">
                  {{ generatingIcons ? '生成中...' : '生成全套图标' }}
                </el-button>
                <el-button @click="clearUploadedImage" size="small">重选</el-button>
              </div>
            </div>

            <!-- 进度条 -->
            <div class="progress-card" v-if="generatingIcons">
              <el-progress :percentage="generationProgress" :show-text="false"></el-progress>
              <p>{{ generationStatus }}</p>
            </div>

            <!-- 结果 -->
            <div class="result-card" v-if="generationResults">
              <div class="result-info">
                <i class="el-icon-success"></i>
                <span v-if="autoApplySuccess">成功生成 {{ generationResults.processed_count }} 个图标并自动填入</span>
                <span v-else>成功生成 {{ generationResults.processed_count }} 个图标</span>
              </div>
              <div class="result-actions">
                <el-button 
                  v-if="!autoApplySuccess"
                  type="success" 
                  @click="applyGeneratedIcons" 
                  size="small"
                  :disabled="generationResults.processed_count === 0">
                  自动填入所有图标
                </el-button>
                <el-button @click="clearGenerationResults" size="small">{{ autoApplySuccess ? '完成' : '清除' }}</el-button>
              </div>
            </div>
          </div>
          <span class="tip">上传一张高清图片，自动生成并填入所有尺寸的网站图标和Logo</span>
        </el-form-item>

        <el-divider content-position="center">
          <span style="color: #909399; font-size: 13px;">如需单独调整，可使用下方配置</span>
        </el-divider>
        
        <el-divider content-position="left">
          社交媒体设置
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">配置网站在社交媒体分享时的显示方式，影响Facebook、Twitter等平台分享效果</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-tabs type="border-card">
          <el-tab-pane label="通用设置">
            <el-form-item id="field-seo-share-image" label="默认分享图片">
              <div class="icon-upload-container">
                <div style="display: flex">
                  <el-input v-model="seoConfig.og_image" placeholder="输入图片URL或点击上传"></el-input>
                  <el-image lazy class="table-td-thumb"
                            style="margin-left: 10px"
                            v-if="seoConfig.og_image"
                            :preview-src-list="[seoConfig.og_image]"
                            :src="seoConfig.og_image"
                            fit="cover"></el-image>
                </div>
                <uploadPicture 
                  :isAdmin="true" 
                  :prefix="'seoOgImage'" 
                  @addPicture="addOgImage"
                  :maxSize="2" 
                  :maxNumber="1" 
                  class="upload-btn">
                </uploadPicture>
              </div>
              <span class="tip">当文章无封面图时使用的默认图片，建议尺寸1200×630像素</span>
            </el-form-item>
            
            <el-form-item id="field-seo-logo" label="网站Logo">
              <div class="icon-upload-container">
                <div style="display: flex">
                  <el-input v-model="seoConfig.site_logo" placeholder="输入Logo URL或点击上传"></el-input>
                  <el-image lazy class="table-td-thumb"
                            style="margin-left: 10px"
                            v-if="seoConfig.site_logo"
                            :preview-src-list="[seoConfig.site_logo]"
                            :src="seoConfig.site_logo"
                            fit="cover"></el-image>
                </div>
                <uploadPicture 
                  :isAdmin="true" 
                  :prefix="'seoSiteLogo'" 
                  @addPicture="addSiteLogo"
                  :maxSize="2" 
                  :maxNumber="1" 
                  class="upload-btn">
                </uploadPicture>
              </div>
              <span class="tip">在某些社交平台上显示为网站标识，建议为正方形，至少300×300像素</span>
            </el-form-item>
          </el-tab-pane>
          
          <el-tab-pane label="网站图标">
            <div class="tab-description">
              配置网站在浏览器标签页、收藏夹、桌面快捷方式等场景下显示的图标
            </div>

            <div class="icon-manual-toggle">
              <el-switch
                v-model="showManualIcons"
                active-text="显示手动配置"
                inactive-text="隐藏手动配置">
              </el-switch>
              <span class="tip">推荐使用上方“智能图标生成”，一般无需逐个填写</span>
            </div>

            <template v-if="showManualIcons">
            <el-form-item id="field-favicon" label="网站标签页图标">
              <div class="icon-upload-container">
                <div style="display: flex">
                  <el-input v-model="seoConfig.site_icon" placeholder="输入图标URL或点击上传"></el-input>
                  <el-image lazy class="table-td-thumb"
                            style="margin-left: 10px"
                            v-if="seoConfig.site_icon"
                            :preview-src-list="[seoConfig.site_icon]"
                            :src="seoConfig.site_icon"
                            fit="cover"></el-image>
                </div>
                <uploadPicture 
                  :isAdmin="true" 
                  :prefix="'seoSiteIcon'" 
                  @addPicture="addSiteIcon"
                  :maxSize="1" 
                  :maxNumber="1" 
                  :accept="'image/ico,image/png,image/jpg,image/jpeg,image/webp,image/svg+xml'"
                  class="upload-btn">
                </uploadPicture>
              </div>
              <span class="tip">建议格式：ICO、PNG，尺寸：16x16, 32x32像素，用于浏览器标签页和收藏夹</span>
            </el-form-item>

            <el-form-item label="Apple Touch图标">
              <div class="icon-upload-container">
                <div style="display: flex">
                  <el-input v-model="seoConfig.apple_touch_icon" placeholder="输入图标URL或点击上传"></el-input>
                  <el-image lazy class="table-td-thumb"
                            style="margin-left: 10px"
                            v-if="seoConfig.apple_touch_icon"
                            :preview-src-list="[seoConfig.apple_touch_icon]"
                            :src="seoConfig.apple_touch_icon"
                            fit="cover"></el-image>
                </div>
                <uploadPicture 
                  :isAdmin="true" 
                  :prefix="'seoAppleTouchIcon'" 
                  @addPicture="addAppleTouchIcon"
                  :maxSize="1" 
                  :maxNumber="1" 
                  class="upload-btn">
                </uploadPicture>
              </div>
              <span class="tip">建议尺寸：180×180像素，用于iOS设备添加到主屏幕时显示</span>
            </el-form-item>

            <el-form-item label="PWA图标 (192x192)">
              <div class="icon-upload-container">
                <div style="display: flex">
                  <el-input v-model="seoConfig.site_icon_192" placeholder="输入图标URL或点击上传"></el-input>
                  <el-image lazy class="table-td-thumb"
                            style="margin-left: 10px"
                            v-if="seoConfig.site_icon_192"
                            :preview-src-list="[seoConfig.site_icon_192]"
                            :src="seoConfig.site_icon_192"
                            fit="cover"></el-image>
                </div>
                <uploadPicture 
                  :isAdmin="true" 
                  :prefix="'seoSiteIcon192'" 
                  @addPicture="addSiteIcon192"
                  :maxSize="1" 
                  :maxNumber="1" 
                  class="upload-btn">
                </uploadPicture>
              </div>
              <span class="tip">建议尺寸：192×192像素，用于PWA应用图标</span>
            </el-form-item>

            <el-form-item label="PWA图标 (512x512)">
              <div class="icon-upload-container">
                <div style="display: flex">
                  <el-input v-model="seoConfig.site_icon_512" placeholder="输入图标URL或点击上传"></el-input>
                  <el-image lazy class="table-td-thumb"
                            style="margin-left: 10px"
                            v-if="seoConfig.site_icon_512"
                            :preview-src-list="[seoConfig.site_icon_512]"
                            :src="seoConfig.site_icon_512"
                            fit="cover"></el-image>
                </div>
                <uploadPicture 
                  :isAdmin="true" 
                  :prefix="'seoSiteIcon512'" 
                  @addPicture="addSiteIcon512"
                  :maxSize="2" 
                  :maxNumber="1" 
                  class="upload-btn">
                </uploadPicture>
              </div>
              <span class="tip">建议尺寸：512×512像素，用于PWA应用启动屏幕和高分辨率显示</span>
            </el-form-item>
            </template>



             <el-form-item label="图标预览">
               <div class="icon-preview">
                 <div class="preview-item" v-if="seoConfig.site_icon">
                   <img :src="seoConfig.site_icon" class="icon-preview-16" alt="标签页图标">
                   <span>标签页图标</span>
                 </div>
                 <div class="preview-item" v-if="seoConfig.site_logo">
                   <img :src="seoConfig.site_logo" class="icon-preview-64" alt="网站Logo">
                   <span>网站Logo</span>
                 </div>
                 <div class="preview-item" v-if="seoConfig.apple_touch_icon">
                   <img :src="seoConfig.apple_touch_icon" class="icon-preview-48" alt="Apple Touch图标">
                   <span>Apple Touch</span>
                 </div>
                 <div class="preview-item" v-if="seoConfig.site_icon_192">
                   <img :src="seoConfig.site_icon_192" class="icon-preview-48" alt="PWA图标 192">
                   <span>PWA 192</span>
                 </div>
                 <div class="preview-item" v-if="seoConfig.site_icon_512">
                   <img :src="seoConfig.site_icon_512" class="icon-preview-64" alt="PWA图标 512">
                   <span>PWA 512</span>
                 </div>
                 <div class="preview-empty" v-if="!hasAnyIcon">
                   <span>暂无图标，请上传图标文件或使用智能生成功能</span>
                 </div>
               </div>
             </el-form-item>
                     </el-tab-pane>
            
            <el-tab-pane label="PWA应用">
              <div class="tab-description">
                配置Progressive Web App (PWA)功能，让网站可以像原生应用一样安装到用户设备
              </div>
              
              <el-form-item id="field-pwa" label="应用短名称">
                <el-input v-model="seoConfig.site_short_name" placeholder="应用的短名称，用于设备主屏幕显示">
                  <template slot="prepend">PWA</template>
                </el-input>
                <span class="tip">建议12个字符以内，用于设备主屏幕图标下方显示</span>
              </el-form-item>

              <el-form-item label="显示模式">
                <el-select v-model="seoConfig.pwa_display" placeholder="选择PWA显示模式">
                  <el-option label="独立应用 (推荐)" value="standalone"></el-option>
                  <el-option label="全屏显示" value="fullscreen"></el-option>
                  <el-option label="最小UI" value="minimal-ui"></el-option>
                  <el-option label="浏览器标签" value="browser"></el-option>
                </el-select>
                <span class="tip">standalone模式提供最佳的原生应用体验</span>
              </el-form-item>

              <el-form-item label="主题颜色">
                <el-input v-model="seoConfig.pwa_theme_color" placeholder="#1976d2">
                  <template slot="prepend">
                    <div class="color-preview" :style="{ backgroundColor: seoConfig.pwa_theme_color }"></div>
                  </template>
                </el-input>
                <span class="tip">影响状态栏和浏览器UI的颜色，建议使用品牌主色</span>
              </el-form-item>

              <el-form-item label="背景颜色">
                <el-input v-model="seoConfig.pwa_background_color" placeholder="#ffffff">
                  <template slot="prepend">
                    <div class="color-preview" :style="{ backgroundColor: seoConfig.pwa_background_color }"></div>
                  </template>
                </el-input>
                <span class="tip">应用启动时的背景颜色，建议与网站背景色保持一致</span>
              </el-form-item>

              <el-form-item label="屏幕方向">
                <el-select v-model="seoConfig.pwa_orientation" placeholder="选择屏幕方向">
                  <el-option label="竖屏 (推荐)" value="portrait-primary"></el-option>
                  <el-option label="横屏" value="landscape-primary"></el-option>
                  <el-option label="自动旋转" value="any"></el-option>
                  <el-option label="自然方向" value="natural"></el-option>
                </el-select>
                <span class="tip">大多数博客和内容类应用推荐使用竖屏模式</span>
              </el-form-item>

              <el-form-item id="field-pwa-screenshot" label="桌面端截图">
                <div class="icon-upload-container">
                  <div style="display: flex">
                    <el-input v-model="seoConfig.pwa_screenshot_desktop" placeholder="输入桌面端截图URL或点击上传">
                    </el-input>
                    <el-image lazy class="table-td-thumb"
                              style="margin-left: 10px"
                              v-if="seoConfig.pwa_screenshot_desktop"
                              :preview-src-list="[seoConfig.pwa_screenshot_desktop]"
                              :src="seoConfig.pwa_screenshot_desktop"
                              fit="cover"></el-image>
                  </div>
                  <uploadPicture 
                    :isAdmin="true" 
                    :prefix="'pwaDeskScreenshot'" 
                    @addPicture="addPwaDesktopScreenshot"
                    :maxSize="10" 
                    :maxNumber="1" 
                    class="upload-btn">
                  </uploadPicture>
                </div>
                <span class="tip">建议尺寸：1280×720像素，用于应用商店展示（可选）</span>
              </el-form-item>

              <el-form-item label="移动端截图">
                <div class="icon-upload-container">
                  <div style="display: flex">
                    <el-input v-model="seoConfig.pwa_screenshot_mobile" placeholder="输入移动端截图URL或点击上传">
                    </el-input>
                    <el-image lazy class="table-td-thumb"
                              style="margin-left: 10px"
                              v-if="seoConfig.pwa_screenshot_mobile"
                              :preview-src-list="[seoConfig.pwa_screenshot_mobile]"
                              :src="seoConfig.pwa_screenshot_mobile"
                              fit="cover"></el-image>
                  </div>
                  <uploadPicture 
                    :isAdmin="true" 
                    :prefix="'pwaMobileScreenshot'" 
                    @addPicture="addPwaMobileScreenshot"
                    :maxSize="10" 
                    :maxNumber="1" 
                    class="upload-btn">
                  </uploadPicture>
                </div>
                <span class="tip">建议尺寸：375×667像素，用于移动端应用商店展示（可选）</span>
              </el-form-item>

              <el-form-item label="原生应用关联">
                <div class="native-app-config">
                  <div class="app-config-item">
                    <label>Android应用ID：</label>
                    <el-input v-model="seoConfig.android_app_id" placeholder="com.example.app" size="small">
                    </el-input>
                  </div>
                  <div class="app-config-item">
                    <label>iOS应用ID：</label>
                    <el-input v-model="seoConfig.ios_app_id" placeholder="1234567890" size="small">
                    </el-input>
                  </div>
                  <div class="app-config-item">
                    <el-checkbox v-model="seoConfig.prefer_native_apps">
                      优先推荐原生应用
                    </el-checkbox>
                  </div>
                </div>
                <span class="tip">如果有对应的原生应用，可以配置应用ID来引导用户下载</span>
              </el-form-item>

              <el-form-item label="PWA功能预览">
                <div class="pwa-preview">
                  <div class="preview-phone">
                    <div class="phone-screen">
                      <div class="status-bar" :style="{ backgroundColor: seoConfig.pwa_theme_color }">
                        <span>9:41</span>
                        <span>📶 📶 🔋</span>
                      </div>
                      <div class="app-content" :style="{ backgroundColor: seoConfig.pwa_background_color }">
                        <div class="app-icon" v-if="seoConfig.site_icon_192">
                          <img :src="seoConfig.site_icon_192" alt="应用图标">
                        </div>
                        <div class="app-name">{{ seoConfig.site_short_name || seoConfig.site_name || 'POETIZE' }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="preview-info">
                    <h4>PWA效果预览</h4>
                    <p>• 显示模式：{{ getPwaDisplayText(seoConfig.pwa_display) }}</p>
                    <p>• 主题颜色：{{ seoConfig.pwa_theme_color }}</p>
                    <p>• 背景颜色：{{ seoConfig.pwa_background_color }}</p>
                    <p>• 图标配置：{{ hasValidPwaIcons ? '✅ 已配置' : '❌ 需要配置' }}</p>
                  </div>
                </div>
              </el-form-item>
            </el-tab-pane>
            
            <el-tab-pane label="Facebook/Meta">
            <el-form-item id="field-social-meta" label="Facebook App ID">
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
            <div slot="content">请粘贴搜索引擎站长平台提供的完整HTML验证标签，系统会原样输出到页面中。<br/>注意：搜索引擎对验证标签格式要求非常严格，请务必完整粘贴，不要修改任何内容。</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item id="field-verify" label="百度站点验证">
          <el-input v-model="seoConfig.baidu_site_verification" placeholder='请粘贴完整标签，如：<meta name="baidu-site-verification" content="codeva-xxxx" />'></el-input>
        </el-form-item>
        
        <el-form-item label="Google站点验证">
          <el-input v-model="seoConfig.google_site_verification" placeholder='请粘贴完整标签，如：<meta name="google-site-verification" content="xxxx" />'></el-input>
        </el-form-item>
        
        <el-form-item label="Bing(必应)站点验证">
          <el-input v-model="seoConfig.bing_site_verification" placeholder='请粘贴完整标签，如：<meta name="msvalidate.01" content="xxxx" />'></el-input>
        </el-form-item>
        
        <el-form-item label="Yandex站点验证">
          <el-input v-model="seoConfig.yandex_site_verification" placeholder='请粘贴完整标签，如：<meta name="yandex-verification" content="xxxx" />'></el-input>
        </el-form-item>
        
        <el-form-item label="搜狗站点验证">
          <el-input v-model="seoConfig.sogou_site_verification" placeholder='请粘贴完整标签，如：<meta name="sogou_site_verification" content="xxxx" />'></el-input>
        </el-form-item>
        
        <el-form-item label="神马站点验证">
          <el-input v-model="seoConfig.shenma_site_verification" placeholder='请粘贴完整标签，如：<meta name="shenma-site-verification" content="xxxx" />'></el-input>
        </el-form-item>
        
        <el-form-item label="360站点验证">
          <el-input v-model="seoConfig.so_site_verification" placeholder='请粘贴完整标签，如：<meta name="360-site-verification" content="xxxx" />'></el-input>
        </el-form-item>
        
        <el-form-item label="Yahoo(雅虎)站点验证">
          <el-input v-model="seoConfig.yahoo_site_verification" placeholder='请粘贴完整标签，如：<meta name="y_key" content="xxxx" />'></el-input>
        </el-form-item>
        
        <el-divider content-position="left">
          搜索引擎推送
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">配置后系统会在发布新文章时自动将URL提交给仍支持实时推送的搜索引擎，Google、Yahoo、360等已下线实时推送功能，请改用 Search Console 提交 Sitemap。</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item id="field-search-push" label="启用百度推送">
          <el-switch v-model="seoConfig.baidu_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL推送至百度搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="百度推送Token">
          <el-input v-model="seoConfig.baidu_push_token" placeholder="百度推送Token"></el-input>
          <span class="tip">
            在
            <a class="tip-link" :href="baiduPushSubmitUrl" target="_blank" rel="noopener noreferrer">百度搜索资源平台普通收录-资源提交</a>
            的“普通收录 → 资源提交”中获取
          </span>
        </el-form-item>
        
        <el-form-item label="启用Bing推送">
          <el-switch v-model="seoConfig.bing_push_enabled"></el-switch>
          <span class="tip">通过 IndexNow 自动将新文章URL提交至 Bing 等支持的搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="Bing(IndexNow) Key">
          <el-input v-model="seoConfig.bing_api_key" placeholder="IndexNow Key"></el-input>
          <span class="tip">
            在
            <a class="tip-link" href="https://www.bing.com/indexnow/IndexNowView/IndexNowGetStartedView" target="_blank" rel="noopener noreferrer">Bing Webmaster Tools / IndexNow</a>
            中生成 IndexNow Key，并确保站点根目录可访问该 key 对应的验证文件
          </span>
        </el-form-item>
        
        <el-form-item label="启用Yandex推送">
          <el-switch v-model="seoConfig.yandex_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至Yandex搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="Yandex API Key">
          <el-input v-model="seoConfig.yandex_api_key" placeholder="Yandex API密钥"></el-input>
          <span class="tip">
            参考
            <a class="tip-link" href="https://yandex.com/support/webmaster/en/indexnow/key" target="_blank" rel="noopener noreferrer">Yandex Webmaster IndexNow Key 指南</a>
            生成并配置
          </span>
        </el-form-item>
        
        <el-form-item label="启用搜狗推送">
          <el-switch v-model="seoConfig.sogou_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至搜狗搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="搜狗推送Token">
          <el-input v-model="seoConfig.sogou_push_token" placeholder="搜狗推送Token"></el-input>
          <span class="tip">
            在
            <a class="tip-link" href="https://zhanzhang.sogou.com/index.php/sitelink/index" target="_blank" rel="noopener noreferrer">搜狗资源平台链接提交通道</a>
            获取或提交
          </span>
        </el-form-item>
        
        <el-form-item label="启用神马推送">
          <el-switch v-model="seoConfig.shenma_push_enabled"></el-switch>
          <span class="tip">自动将新文章URL提交至神马搜索引擎</span>
        </el-form-item>
        
        <el-form-item label="神马推送Token">
          <el-input v-model="seoConfig.shenma_token" placeholder="神马推送Token"></el-input>
          <span class="tip">
            在
            <a class="tip-link" href="https://zhanzhang.sm.cn/" target="_blank" rel="noopener noreferrer">神马站长平台</a>
            获取
          </span>
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
          <span class="tip">文章推送到搜索引擎后，将结果发送邮件通知给文章作者</span>
        </el-form-item>
        
        <el-form-item label="仅在推送失败时通知">
          <el-switch v-model="seoConfig.notify_only_on_failure"></el-switch>
          <span class="tip">如果启用，则只有当推送出现错误时才发送邮件通知</span>
        </el-form-item>
        
        <div class="notification-info">
          <el-alert title="邮件通知说明" type="info" :closable="false" show-icon>
            <template slot="default">
              <p>系统将自动发送邮件通知给文章作者的邮箱地址，无需额外配置。</p>
              <p>请确保文章作者在个人资料中设置了有效的邮箱地址。</p>
            </template>
          </el-alert>
        </div>
        
        
        <el-divider content-position="left">
          网站地图设置
          <el-tooltip class="item" effect="dark" placement="top">
            <div slot="content">网站地图(Sitemap)帮助搜索引擎了解网站结构和页面更新频率，提高收录效率</div>
            <i class="el-icon-question help-icon"></i>
          </el-tooltip>
        </el-divider>
        
        <el-form-item id="field-sitemap" label="生成网站地图">
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
        
        <el-form-item label="robots.txt 内容" class="code-editor-form-item">
          <div class="code-editor-wrapper">
            <el-input 
              v-model="seoConfig.robots_txt" 
              type="textarea" 
              :rows="30" 
              class="code-textarea code-editor robots-editor"
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
        
        <el-form-item label="自定义头部代码" class="code-editor-form-item">
          <div class="code-editor-wrapper">
            <el-input 
              v-model="seoConfig.custom_head_code" 
              type="textarea" 
              :rows="10" 
              class="code-textarea code-editor custom-head-editor"
              spellcheck="false"
              resize="vertical"
            ></el-input>
            <div class="code-line-numbers">
              <div v-for="i in 10" :key="i" class="line-number">{{i}}</div>
            </div>
          </div>
          <span class="tip">可以添加额外的META标签、JS代码等，将插入到页面的&lt;head&gt;中。文本框可拖动调整高度。</span>
        </el-form-item>
        
        <!-- 简约按钮区域 -->
        <div class="seo-actions-container">
          <el-button 
            type="primary" 
            @click="isMobile ? saveSeoConfigMobile() : saveSeoConfig()" 
            :loading="loading"
            :size="buttonSize">
            {{ loading ? '保存中...' : '保存配置' }}
          </el-button>
          

          <el-button 
            @click="analyzeSite" 
            :loading="analyzeLoading"
            :size="buttonSize">
            {{ analyzeLoading ? '分析中...' : 'SEO分析' }}
          </el-button>
          
        </div>
        </div>
      </el-form>
    </el-card>
    
    <!-- SEO分析结果弹窗 -->
    <el-dialog
      title="SEO分析结果"
      :visible.sync="showAnalysisDialog"
      width="60%"
      custom-class="centered-dialog">
      <div v-if="seoAnalysis">
        <div class="analysis-score">
          <el-progress type="circle" :percentage="seoAnalysis.seo_score" :status="getSeoScoreStatus(seoAnalysis.seo_score)"></el-progress>
          <div class="score-label">SEO得分</div>
          <div v-if="seoAnalysis.issue_summary" class="analysis-summary">
            <span v-if="seoAnalysis.issue_summary.error_count > 0">严重问题 {{ seoAnalysis.issue_summary.error_count }}</span>
            <span v-if="seoAnalysis.issue_summary.warning_count > 0">警告 {{ seoAnalysis.issue_summary.warning_count }}</span>
            <span v-if="seoAnalysis.issue_summary.info_count > 0">提示 {{ seoAnalysis.issue_summary.info_count }}</span>
          </div>
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
        <el-button type="primary" @click="saveSeoConfig">保存当前配置</el-button>
      </span>
    </el-dialog>
    
    <!-- AI API配置弹窗 -->
    <el-dialog
      title="AI API配置"
      :visible.sync="showApiConfigDialog"
      width="50%"
      custom-class="centered-dialog">
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
      width="70%"
      custom-class="centered-dialog">
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

  import { useMainStore } from '@/stores/main';

const uploadPicture = () => import("../common/uploadPicture");

export default {
  components: {
    uploadPicture
  },
  data() {
    return {
      initialLoad: true,
      currentStoreType: null, // 添加当前存储类型属性
      isMobile: false, // 移动端检测
      isTouch: false, // 触摸设备检测
      showManualIcons: false,
      seoConfig: {
        enable: false,
        site_description: "POETIZE：作诗，有诗意地描写。个人博客，生活倒影，记录生活。",
        site_keywords: "POETIZE,博客,个人网站,生活笔记,记录生活",
        default_author: "poetize",
        og_image: "",
        site_logo: "",
        site_icon: "",
        site_icon_192: "",
        site_icon_512: "",
        apple_touch_icon: "",
        // PWA相关配置
        site_short_name: "",
        pwa_display: "standalone",
        pwa_background_color: "#ffffff",
        pwa_theme_color: "#1976d2",
        pwa_orientation: "portrait-primary",
        pwa_screenshot_desktop: "",
        pwa_screenshot_mobile: "",
        android_app_id: "",
        ios_app_id: "",
        prefer_native_apps: false,
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
        bing_push_enabled: false,
        bing_api_key: "",
        yandex_push_enabled: false,
        yandex_api_key: "",
        sogou_push_enabled: false,
        sogou_push_token: "",
        shenma_push_enabled: false,
        shenma_token: "",
        enable_push_notification: false,
        notify_only_on_failure: false,
        robots_txt: "User-agent: *\nAllow: /\nDisallow: /admin/\nSitemap: /sitemap.xml",
        generate_sitemap: true,
        sitemap_change_frequency: "weekly",
        sitemap_priority: "0.7",
        // 新增: 站点地图排除列表，逗号分隔路径
        sitemap_exclude: "/love",
        auto_generate_meta_tags: true,
        custom_head_code: ""
      },
      loading: false,

      analyzeLoading: false,
      aiAnalyzeLoading: false,
      apiConfigLoading: false,
      showAnalysisDialog: false,
      showAiAnalysisDialog: false,
      showApiConfigDialog: false,
      showHelpDialog: false,
      showAiMenu: false,
      seoAnalysis: null,
      aiSeoAnalysis: null,
      // 智能图标生成相关
      uploadedImage: null,
      generatingIcons: false,
      generationProgress: 0,
      generationStatus: '',
      generationResults: null,
      autoApplySuccess: false,
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
      this.getSeoConfig();
      
      // 初始化当前存储类型（安全访问）
      const mainStore = useMainStore();
      this.currentStoreType = mainStore.sysConfig?.['store.type'] || "local";
      
      // 监听系统配置更新事件
      this.$bus.$on('sysConfigUpdated', this.handleSysConfigUpdate);
    } catch (error) {
      console.error('SEO配置组件初始化失败', error);
      // 只记录错误，不显示用户提示（getSeoConfig内部已有错误处理）
      console.error('详细错误信息:', error.stack);
    }
  },
  
  mounted() {
    // 检测移动端和触摸设备
    this.detectMobileDevice();
    
    // 添加移动端特有的优化
    if (this.isMobile) {
      this.addMobileOptimizations();
    }
    
    
    // 添加文档点击事件监听器
    document.addEventListener('click', this.handleDocumentClick);
  },
  
  beforeDestroy() {
    // 移除文档点击事件监听器
    document.removeEventListener('click', this.handleDocumentClick);
    
    // 移除系统配置更新事件监听
    this.$bus.$off('sysConfigUpdated', this.handleSysConfigUpdate);
  },
  
  watch: {
    'seoConfig.enable': {
      handler(newVal, oldVal) {
        // 只有在非初始加载且值确实发生变化时才保存
        if (!this.initialLoad && oldVal !== undefined && newVal !== oldVal) {
          this.saveEnableStatus(newVal);
        }
      }
    }
  },

  computed: {
    // 移动端相关的计算属性
    isMobileDevice() {
      return this.isMobile;
    },
    
    // 表单项标签宽度
    formLabelWidth() {
      return this.isMobile ? '100px' : '140px';
    },
    
    // 对话框宽度
    dialogWidth() {
      if (this.isMobile) {
        return window.innerWidth <= 480 ? '98%' : '95%';
      }
      return '60%';
    },
    
    // 按钮大小
    buttonSize() {
      return this.isMobile ? 'medium' : 'small';
    },

    configuredSiteAddress() {
      try {
        const mainStore = useMainStore();
        return this.normalizeSiteAddress(mainStore?.webInfo?.siteAddress);
      } catch (error) {
        return '';
      }
    },

    baiduPushSubmitUrl() {
      const baseUrl = 'https://ziyuan.baidu.com/linksubmit/index';
      return this.configuredSiteAddress
        ? `${baseUrl}?site=${encodeURIComponent(this.configuredSiteAddress)}`
        : baseUrl;
    }
  },
  
  methods: {
    normalizeSiteAddress(siteAddress) {
      if (!siteAddress || typeof siteAddress !== 'string') {
        return '';
      }

      return siteAddress.trim().replace(/\/+$/, '');
    },

    // 检测移动端设备
    detectMobileDevice() {
      // 检测屏幕尺寸
      this.isMobile = window.innerWidth <= 768;
      
      // 检测触摸设备
      this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
      
      // 检测用户代理
      const userAgent = navigator.userAgent.toLowerCase();
      const mobileKeywords = ['mobile', 'android', 'iphone', 'ipad', 'ipod', 'blackberry', 'windows phone'];
      const isMobileUA = mobileKeywords.some(keyword => userAgent.includes(keyword));
      
      this.isMobile = this.isMobile || isMobileUA;
      
    },

    // 添加移动端优化
    addMobileOptimizations() {
      // 监听屏幕方向变化
      window.addEventListener('orientationchange', () => {
        setTimeout(() => {
          this.detectMobileDevice();
          this.$forceUpdate();
        }, 100);
      });

      // 监听窗口大小变化
      window.addEventListener('resize', () => {
        this.detectMobileDevice();
      });

      // 移动端特有的消息提示
      this.$message.closeAll();

      // 禁用移动端的双击缩放（在表单区域）
      this.$nextTick(() => {
        const formElements = document.querySelectorAll('.el-form-item__content');
        formElements.forEach(el => {
          el.addEventListener('touchstart', (e) => {
            if (e.touches.length > 1) {
              e.preventDefault();
            }
          });
        });
      });
    },

    // 移动端优化的保存方法
    saveSeoConfigMobile() {
      if (this.isMobile) {
        // 移动端显示加载遮罩
        const loading = this.$loading({
          lock: true,
          text: '正在保存SEO配置...',
          spinner: 'el-icon-loading',
          background: 'rgba(0, 0, 0, 0.7)'
        });
        
        this.saveSeoConfig().finally(() => {
          loading.close();
        });
      } else {
        this.saveSeoConfig();
      }
    },

    // 移动端友好的错误处理
    showMobileError(message) {
      if (this.isMobile) {
        this.$notify({
          title: '操作失败',
          message: message,
          type: 'error',
          position: 'top-right',
          duration: 4000
        });
      } else {
        this.$message.error(message);
      }
    },

    // 移动端友好的成功提示
    showMobileSuccess(message) {
      if (this.isMobile) {
        this.$notify({
          title: '操作成功',
          message: message,
          type: 'success',
          position: 'top-right',
          duration: 3000
        });
      } else {
        this.$message.success(message);
      }
    },

    // 处理系统配置更新事件
    handleSysConfigUpdate(config) {
      if (config && config['store.type']) {
        this.currentStoreType = config['store.type'];
      }
    },
    
    
    saveEnableStatus(status) {
      // 确保状态是布尔值
      const enableStatus = status === undefined ? false : !!status;
      
      this.$http.post(this.$constant.baseURL + '/admin/seo/updateEnableStatus', {
        enable: enableStatus
      }, true)
      .then(res => {
        if (res.code === 200) {
          this.showMobileSuccess('SEO开关状态已保存');
        } else {
          console.error('SEO开关状态保存失败, 错误信息:', res.message);
          this.showMobileError(res.message || 'SEO开关状态保存失败');
        }
      })
      .catch(error => {
        console.error('保存SEO开关状态时发生网络错误:', error);
        this.showMobileError('保存SEO开关状态时发生错误');
      });
    },
    
    getSeoConfig() {
      try {
        this.$http.get(this.$constant.baseURL + '/admin/seo/getSeoConfig', {}, true)
          .then((res) => {
            
            // 检查是否是标准的{code, data}格式
            if (res && res.code === 200 && res.data) {
              const config = res.data;
              
              // 确保enable字段存在，如果不存在设置为false
              if (config.enable === undefined) {
                config.enable = false;
              }
              
              // 使用Object.assign保持响应式，而不是直接替换对象
              Object.assign(this.seoConfig, config);
              this.$nextTick(() => {
                this.initialLoad = false;
              });
            } else if (res && typeof res === 'object' && !res.hasOwnProperty('code')) {
              // 直接返回配置对象的情况（兼容性处理）
              const config = res;
              
              if (config.enable === undefined) {
                config.enable = false;
              }
              
              Object.assign(this.seoConfig, config);
              this.$nextTick(() => {
                this.initialLoad = false;
              });
            } else {
              console.error('获取SEO配置失败，响应数据异常:', res);
              this.$message.error(res?.message || '获取SEO配置失败');
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
      this.$http.post(this.$constant.baseURL + '/admin/seo/updateSeoConfig', this.seoConfig, true)
        .then((res) => {
          this.loading = false;
          if (res && res.code === 200) {
            this.showMobileSuccess('保存SEO配置成功');
            this.showAnalysisDialog = false;
          } else {
            console.error('保存SEO配置失败，响应数据异常:', res);
            this.showMobileError(res ? res.message || '保存SEO配置失败' : '响应数据为空');
          }
        })
        .catch((error) => {
          this.loading = false;
          console.error('保存SEO配置失败:', error);
          this.showMobileError('保存SEO配置失败: ' + (error.message || '网络连接问题'));
        });
    },
    

    analyzeSite() {
      this.analyzeLoading = true;
      this.$http.get(this.$constant.baseURL + '/admin/seo/analyzeSite', {}, true)
        .then((res) => {
          this.analyzeLoading = false;
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
      
      this.$http.post(this.$constant.baseURL + '/seo/saveAiApiConfig', configToSave, true)
        .then((res) => {
          this.apiConfigLoading = false;
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
    // 关闭AI菜单
    closeAiMenu() {
      this.showAiMenu = false;
    },
    
    // 切换AI菜单显示状态
    toggleAiMenu(event) {
      event.stopPropagation();
      this.showAiMenu = !this.showAiMenu;
    },
    
    // 处理文档点击事件
    handleDocumentClick(event) {
      if (this.showAiMenu && this.$refs.aiActionRef && !this.$refs.aiActionRef.contains(event.target)) {
        this.showAiMenu = false;
      }
    },

    // 处理AI下拉菜单命令
    handleAiCommand(command) {
      switch (command) {
        case 'analyze':
          this.aiAnalyze();
          break;
        case 'config':
          this.showApiConfigDialog = true;
          break;
        case 'help':
          this.showHelpDialog = true;
          break;
      }
    },

    // 图片上传处理方法
    addOgImage(pictureUrl) {
      this.seoConfig.og_image = pictureUrl;
      this.$message.success('默认分享图片上传成功');
    },

    addSiteLogo(pictureUrl) {
      this.seoConfig.site_logo = pictureUrl;
      this.$message.success('网站Logo上传成功');
    },

    addSiteIcon(pictureUrl) {
      this.seoConfig.site_icon = pictureUrl;
      this.$message.success('网站标签页图标上传成功');
    },

    addAppleTouchIcon(pictureUrl) {
      this.seoConfig.apple_touch_icon = pictureUrl;
      this.$message.success('Apple Touch图标上传成功');
    },

    addSiteIcon192(pictureUrl) {
      this.seoConfig.site_icon_192 = pictureUrl;
      this.$message.success('PWA图标(192x192)上传成功');
    },

    addSiteIcon512(pictureUrl) {
      this.seoConfig.site_icon_512 = pictureUrl;
      this.$message.success('PWA图标(512x512)上传成功');
    },

    // PWA相关上传处理方法
    addPwaDesktopScreenshot(pictureUrl) {
      this.seoConfig.pwa_screenshot_desktop = pictureUrl;
      this.$message.success('PWA桌面端截图上传成功');
    },

    addPwaMobileScreenshot(pictureUrl) {
      this.seoConfig.pwa_screenshot_mobile = pictureUrl;
      this.$message.success('PWA移动端截图上传成功');
    },

    // 获取PWA显示模式文本
    getPwaDisplayText(mode) {
      const modeMap = {
        'standalone': '独立应用',
        'fullscreen': '全屏显示',
        'minimal-ui': '最小UI',
        'browser': '浏览器标签'
      };
      return modeMap[mode] || '独立应用';
    },

    // 智能图标生成相关方法
    handleIconUpload(file) {
      this.uploadedImage = file.raw;
      this.$message.success('图片上传成功，可以开始生成图标');
    },

    clearUploadedImage() {
      this.uploadedImage = null;
      this.generationResults = null;
      this.$refs.iconUpload.clearFiles();
      this.$message.info('已清除上传的图片');
    },

    async batchGenerateIcons() {
      if (!this.uploadedImage) {
        this.$message.error('请先上传图片');
        return;
      }

      this.generatingIcons = true;
      this.generationProgress = 0;
      this.generationStatus = '准备处理图片...';

      try {
        // 创建FormData
        const formData = new FormData();
        formData.append('image', this.uploadedImage);
        formData.append('iconTypes', 'favicon,apple-touch-icon,icon-192,icon-512,logo');

        // 打印调试信息

        // 更新进度
        this.generationProgress = 20;
        this.generationStatus = '正在上传图片...';

        // 发送请求 - 使用专门的文件上传方法
        const response = await this.$http.upload(
          this.$constant.baseURL + '/admin/seo/batchProcessIcons', 
          formData, 
          true  // isAdmin = true
        );

        this.generationProgress = 80;
        this.generationStatus = '处理完成，准备显示结果...';

        if (response && response.code === 200) {
          
          this.generationResults = response.data;
          this.generationProgress = 100;
          this.generationStatus = '生成完成，正在自动填入...';
          
          try {
            // 自动应用生成的图标
            await this.applyGeneratedIcons();
            this.autoApplySuccess = true;
          } catch (uploadError) {
            console.error('自动填入图标失败:', uploadError);
            this.$message.error('图标生成成功，但自动填入失败，请手动点击"自动填入所有图标"');
            this.autoApplySuccess = false;
            this.generationProgress = 100;
            this.generationStatus = '生成完成！';
          }
        } else {
          console.error('后端返回的完整响应:', response);
          throw new Error(response.message || '生成图标失败');
        }

      } catch (error) {
        console.error('批量生成图标失败:', error);
        console.error('错误对象详情:', {
          message: error.message,
          response: error.response,
          request: error.request,
          config: error.config
        });
        
        // 检查是否是HTTP响应错误
        if (error.response) {
          console.error('HTTP响应错误:', {
            status: error.response.status,
            data: error.response.data,
            headers: error.response.headers
          });
          this.$message.error('图标生成失败: HTTP ' + error.response.status + ' - ' + (error.response.data?.message || error.message));
        } else if (error.request) {
          console.error('网络请求错误:', error.request);
          this.$message.error('图标生成失败: 网络连接问题');
        } else {
          this.$message.error('图标生成失败: ' + (error.message || '未知错误'));
        }
        
        // 清理状态
        this.clearGenerationResults();
      } finally {
        this.generatingIcons = false;
        // 延迟清除进度状态
        setTimeout(() => {
          this.generationProgress = 0;
          this.generationStatus = '';
        }, 2000);
      }
    },

    async applyGeneratedIcons() {
      if (!this.generationResults || !this.generationResults.icons) {
        this.$message.error('没有可应用的图标结果');
        return;
      }

      const results = this.generationResults.icons;

      // 映射图标类型到配置字段
      const iconMapping = {
        'favicon': 'site_icon',
        'apple-touch-icon': 'apple_touch_icon',
        'icon-192': 'site_icon_192',
        'icon-512': 'site_icon_512',
        'logo': 'site_logo'
      };


      // 显示上传进度
      this.$message.info('正在上传生成的图标...');
      
      try {
        let uploadedCount = 0;
        const uploadPromises = [];

             for (const [iconType, result] of Object.entries(results)) {
         if (result && result.base64_data) {
           const configField = iconMapping[iconType];
           if (configField) {
              // 创建上传任务
              const uploadPromise = this.uploadIconToServer(result.base64_data, result.format, iconType, configField);
              uploadPromises.push(uploadPromise);
            } else {
            }
          } else {
          }
        }
        

        // 并行上传所有图标
        const uploadResults = await Promise.allSettled(uploadPromises);
        
        // 统计成功上传的数量
        const successDetails = [];
        const failureDetails = [];
        
        uploadResults.forEach((result, index) => {
          const iconType = Object.keys(results)[index];
          if (result.status === 'fulfilled') {
            uploadedCount++;
            successDetails.push(iconType);
          } else {
            failureDetails.push(iconType);
            console.error(`图标 ${iconType} 上传失败:`, result.reason);
               }
        });

        if (uploadedCount > 0) {
          const iconTypeMap = {
            'favicon': '网站标签页图标',
            'apple-touch-icon': 'Apple Touch图标',
            'icon-192': 'PWA图标(192x192)',
            'icon-512': 'PWA图标(512x512)',
            'logo': '网站Logo'
          };
          const successNames = successDetails.map(type => iconTypeMap[type] || type).join('、');
          
          this.$message.success(`已自动生成并填入 ${uploadedCount} 个图标：${successNames}，记得保存配置`);
          this.autoApplySuccess = true;
          
          // 如果有失败的图标，也要提示
          if (failureDetails.length > 0) {
            const failureNames = failureDetails.map(type => iconTypeMap[type] || type).join('、');
            setTimeout(() => {
              this.$message.warning(`以下图标填入失败：${failureNames}，请检查或手动上传`);
            }, 1000);
          }
          
          // 延迟清除结果，让用户看到成功状态
          setTimeout(() => {
        this.clearGenerationResults();
          }, 4000);
      } else {
          this.$message.error('所有图标上传都失败了，请检查网络或重试');
        }

      } catch (error) {
        console.error('批量上传图标失败:', error);
        this.$message.error('图标上传过程中出现错误: ' + error.message);
        this.clearGenerationResults();
      }
    },

    clearGenerationResults() {
      this.generationResults = null;
      this.autoApplySuccess = false;
      this.$message.info('已清除生成结果');
    },

    // 获取图片预览URL
    getImagePreview(file) {
      if (file) {
        return URL.createObjectURL(file);
      }
      return '';
    },

    // 将base64转换为Blob对象
    base64ToBlob(base64Data, format) {
      try {
        const byteCharacters = atob(base64Data);
        const byteNumbers = new Array(byteCharacters.length);
        
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        
        const byteArray = new Uint8Array(byteNumbers);
        
        // 确定MIME类型
        let mimeType = 'image/png'; // 默认
        if (format) {
          const formatLower = format.toLowerCase();
          if (formatLower === 'jpeg' || formatLower === 'jpg') {
            mimeType = 'image/jpeg';
          } else if (formatLower === 'webp') {
            mimeType = 'image/webp';
          } else if (formatLower === 'ico') {
            mimeType = 'image/x-icon';
          } else if (formatLower === 'png') {
            mimeType = 'image/png';
          }
        }
        
        return new Blob([byteArray], { type: mimeType });
      } catch (error) {
        console.error('base64转换失败:', error);
        throw new Error('图片数据格式错误');
      }
    },

    // 上传单个图标到服务器
    async uploadIconToServer(base64Data, format, iconType, configField) {
      try {
        // 转换base64为Blob
        const blob = this.base64ToBlob(base64Data, format);
        
        // 生成文件信息
        const fileExtension = format === 'ico' ? 'ico' : (format || 'png');
        const fileName = `generated_${iconType}.${fileExtension}`;
        const prefix = `seo${iconType.charAt(0).toUpperCase() + iconType.slice(1)}`;
        
        // 获取Store实例
        const mainStore = useMainStore();
        if (!mainStore.currentAdmin || !mainStore.currentAdmin.username) {
           throw new Error("无法获取当前管理员信息，请确保已登录");
        }

        // 生成key（参考uploadPicture组件的逻辑）
        const username = mainStore.currentAdmin.username.replace(/[^a-zA-Z]/g, '') + mainStore.currentAdmin.id;
        const key = prefix + "/" + username + new Date().getTime() + Math.floor(Math.random() * 1000) + "." + fileExtension;
        
        // 获取当前配置的存储类型，优先使用更新后的配置
        const storeType = this.currentStoreType || mainStore.sysConfig['store.type'] || "local";
        
        // 创建FormData（使用与uploadPicture相同的字段结构）
        const formData = new FormData();
        formData.append('file', blob, fileName);
        formData.append('originalName', fileName);
        formData.append('key', key);
        formData.append('relativePath', key);
        formData.append('type', prefix);
        formData.append('storeType', storeType);


        // 调用现有的上传接口
        const response = await this.$http.upload(
          this.$constant.baseURL + '/resource/upload',
          formData,
          true  // isAdmin = true
        );

        if (response && response.data) {
          // 上传成功，设置配置字段
          this.seoConfig[configField] = response.data;
          return response.data;
        } else {
          console.error(`图标上传失败: ${iconType}`, response);
          throw new Error(response.message || `${iconType} 上传失败`);
        }

      } catch (error) {
        console.error(`上传图标 ${iconType} 时出错:`, error);
        throw new Error(`${iconType} 上传失败: ${error.message}`);
      }
    }
  },

  computed: {
    // 检查是否有任何图标
    hasAnyIcon() {
      return !!(this.seoConfig.site_icon || 
                this.seoConfig.site_logo || 
                this.seoConfig.apple_touch_icon || 
                this.seoConfig.site_icon_192 || 
                this.seoConfig.site_icon_512);
    },

    // 检查是否有有效的PWA图标
    hasValidPwaIcons() {
      return !!(this.seoConfig.site_icon_192 || this.seoConfig.site_icon_512);
    }
  }
};
</script>

<style scoped>
  .my-tag {
    margin-bottom: 20px !important;
    width: 100%;
    text-align: left;
    background: var(--lightYellow);
    border: none;
    height: 40px;
    line-height: 40px;
    font-size: 16px;
    color: var(--black);
  }

  .el-tag {
    margin: 10px;
  }

  /* 页面容器 */
  .seo-management {
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
    border: none;
    background-color: transparent !important;
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    margin-bottom: 25px;
    overflow: hidden;
    /* 性能优化: 只监听高度和透明度变化 */
    transition: max-height 0.4s ease, opacity 0.4s ease;
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
    /* 性能优化: 只监听颜色变化 */
    transition: color 0.3s cubic-bezier(0.4, 0, 0.2, 1);
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
    font-size: 14px;
  }
  
  /* PC端确保左右布局 - 使用 Flex 布局 */
  @media (min-width: 769px) {
    ::v-deep .el-form-item {
      display: flex !important;
      align-items: flex-start !important;
    }
    
    ::v-deep .el-form-item__label {
      width: 140px !important; /* 固定宽度 */
      flex-shrink: 0 !important; /* 标签不缩小 */
      text-align: right !important; /* 文字右对齐 */
      padding-right: 12px !important;
      line-height: 40px !important;
    }
    
    ::v-deep .el-form-item__content {
      flex: 1 !important; /* 内容区域占满剩余空间 */
      line-height: 40px !important;
    }
  }
  
  ::v-deep .el-input__inner,
  ::v-deep .el-textarea__inner {
    border-radius: 10px;
    border: 1px solid rgba(0, 0, 0, 0.1);
    background-color: rgba(245, 247, 250, 0.7);
    /* 性能优化: 只监听边框和背景色，不需要GPU */
    transition: border-color 0.3s ease, background-color 0.3s ease;
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
    /* 性能优化: 只监听背景色和阴影 */
    transition: background-color 0.3s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.3s cubic-bezier(0.4, 0, 0.2, 1);
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

  .tip-link {
    color: #0071e3;
    text-decoration: none;
    font-weight: 500;
  }

  .tip-link:hover,
  .tip-link:focus {
    text-decoration: underline;
  }
  
  /* 帮助图标 */
  .help-icon {
    color: #86868b;
    cursor: pointer;
    margin-left: 5px;
    font-size: 14px;
    /* 性能优化: 只监听颜色变化，不需要GPU */
    transition: color 0.2s ease;
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
    /* 性能优化: 只监听背景色 */
    transition: background-color 0.3s ease, transform 0.3s ease;
    border: none;
    font-size: 14px;
    transform: translateZ(0);
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

  .analysis-summary {
    margin-top: 12px;
    display: flex;
    justify-content: center;
    flex-wrap: wrap;
    gap: 8px;
    color: #86868b;
    font-size: 13px;
  }

  .analysis-summary span {
    padding: 4px 10px;
    border-radius: 999px;
    background: rgba(0, 113, 227, 0.08);
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
    /* 性能优化: Vue过渡动画 */
    transition: max-height 0.3s ease, opacity 0.3s ease;
  }
  
  .advanced-config-enter,
  .advanced-config-leave-to {
    opacity: 0;
    transform: translateY(-10px);
  }

  /* 高级配置动画 */
  .advanced-config-enter-active, .advanced-config-leave-active {
    /* 性能优化: Vue过渡动画 */
    transition: max-height 0.3s ease, opacity 0.3s ease;
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
    /* 性能优化: 只监听边框和阴影，不需要GPU */
    transition: border-color 0.3s ease, box-shadow 0.3s ease;
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
  
  /* 卡片body去除padding */
  ::v-deep .el-card__body {
    padding: 0 !important;
  }
  
  .help-content .el-tabs__item {
    font-weight: 500;
  }

  .help-content .el-tabs__item.is-active {
    color: #409EFF;
  }
  

  
  ::v-deep .el-input.is-disabled .el-input__inner,
  ::v-deep .el-input__inner[readonly] {
    background-color: rgba(248, 249, 250, 0.8);
    color: #495057;
    cursor: default;
    border-color: rgba(0, 0, 0, 0.08);
  }
  
  /* 网站地址提示样式增强 */
  .tip strong {
    color: #0071e3;
    font-weight: 600;
  }
  
  /* 简约按钮区域样式 */
  .seo-actions-container {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin: 40px 0 20px 0;
    padding: 20px;
    background: rgba(248, 249, 250, 0.6);
    border-radius: 16px;
    border: 1px solid rgba(0, 0, 0, 0.05);
  }
  
  
  .el-dropdown-link-suffix {
    margin-left: 6px;
    font-size: 12px;
    transition: transform 0.3s ease;
  }
  
  /* 简约地址操作按钮样式 */
  .site-address-container {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    flex-wrap: wrap;
  }
  
  .site-address-input {
    flex: 1;
    min-width: 300px;
  }
  
  .simple-address-actions {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-top: 2px;
  }
  
  .simple-address-btn {
    min-width: 80px;
    height: 32px;
    border-radius: 16px;
    font-size: 13px;
    font-weight: 500;
    /* 性能优化: 只监听颜色变化，不需要GPU */
    transition: color 0.3s ease;
  }

  /* 图标上传容器样式 */
  .icon-upload-container {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  
  .icon-upload-container .el-input {
    width: 100%;
  }
  
  .icon-upload-container .upload-btn {
    align-self: flex-start;
  }
  
  /* 图标预览样式 */
  .icon-preview {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 16px;
    background: rgba(248, 249, 250, 0.8);
    border-radius: 12px;
    border: 1px solid rgba(0, 0, 0, 0.05);
    flex-wrap: wrap;
  }
  
  .preview-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 12px;
    background: white;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
    /* 性能优化: 只监听阴影和边框，不需要GPU */
    transition: box-shadow 0.3s ease, border-color 0.3s ease;
  }
  
  .preview-item:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
  
  .preview-item span {
    font-size: 12px;
    color: #666;
    font-weight: 500;
    text-align: center;
  }
  
  .icon-preview-16 {
    width: 16px;
    height: 16px;
    border-radius: 2px;
    object-fit: cover;
  }
  
  .icon-preview-48 {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    object-fit: cover;
  }
  
  .icon-preview-64 {
    width: 64px;
    height: 64px;
    border-radius: 8px;
    object-fit: cover;
  }
  
  .preview-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 32px;
    color: #999;
    font-style: italic;
    width: 100%;
  }
  
  /* 网站图标标签页描述样式 */
  .tab-description {
    background-color: rgba(0, 113, 227, 0.06);
    padding: 12px 18px;
    border-radius: 12px;
    margin-bottom: 20px;
    font-size: 13px;
    color: #1d1d1f;
    line-height: 1.5;
    letter-spacing: 0.2px;
  }

  .icon-manual-toggle {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 0 16px 0;
  }

  /* PWA配置样式 */
  .color-preview {
    width: 20px;
    height: 20px;
    border-radius: 4px;
    border: 1px solid #ddd;
    display: inline-block;
  }

  .native-app-config {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
    background: rgba(248, 249, 250, 0.8);
    border-radius: 12px;
    border: 1px solid rgba(0, 0, 0, 0.05);
  }

  .app-config-item {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .app-config-item label {
    min-width: 120px;
    font-weight: 500;
    color: #333;
  }

  .app-config-item .el-input {
    flex: 1;
  }

  /* PWA预览样式 */
  .pwa-preview {
    display: flex;
    gap: 24px;
    align-items: flex-start;
    padding: 20px;
    background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
    border-radius: 16px;
    border: 1px solid rgba(0, 0, 0, 0.05);
  }

  .preview-phone {
    flex-shrink: 0;
  }

  .phone-screen {
    width: 180px;
    height: 320px;
    background: #000;
    border-radius: 20px;
    padding: 8px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  }

  .status-bar {
    height: 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    color: white;
    font-size: 12px;
    font-weight: 600;
    padding: 0 12px;
    border-radius: 12px 12px 0 0;
  }

  .app-content {
    height: 288px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border-radius: 0 0 12px 12px;
    position: relative;
  }

  .app-icon {
    width: 64px;
    height: 64px;
    border-radius: 16px;
    overflow: hidden;
    margin-bottom: 12px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  .app-icon img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .app-name {
    font-size: 14px;
    font-weight: 600;
    color: #333;
    text-align: center;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .preview-info {
    flex: 1;
    min-width: 200px;
  }

  .preview-info h4 {
    margin: 0 0 16px 0;
    font-size: 16px;
    font-weight: 600;
    color: #1d1d1f;
  }

  .preview-info p {
    margin: 8px 0;
    font-size: 14px;
    color: #666;
    line-height: 1.5;
  }

  /* 智能图标生成器样式 - 极简版 */
  .icon-generator-simple {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .upload-card {
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    padding: 20px;
  }

  .simple-upload .el-upload-dragger {
    background: #fafbfc;
    border: 2px dashed #ddd;
    border-radius: 6px;
    width: 100%;
    height: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    /* 性能优化: 只监听背景色，不需要GPU */
    transition: background-color 0.3s ease, color 0.3s ease;
  }

  .simple-upload .el-upload-dragger:hover {
    border-color: #409EFF;
    background: #f0f9ff;
  }

  .upload-content {
    text-align: center;
  }

  .upload-content i {
    font-size: 28px;
    color: #909399;
    margin-bottom: 8px;
  }

  .upload-content p {
    margin: 8px 0 4px 0;
    color: #606266;
    font-size: 14px;
  }

  .upload-content span {
    color: #909399;
    font-size: 12px;
  }

  .file-preview {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px;
  }

  .file-preview img {
    width: 48px;
    height: 48px;
    border-radius: 4px;
    object-fit: cover;
  }

  .file-name {
    font-size: 14px;
    color: #606266;
  }

  .actions {
    display: flex;
    gap: 8px;
    justify-content: center;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #f0f0f0;
  }

  .progress-card, .result-card {
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    padding: 16px;
  }

  .progress-card p {
    text-align: center;
    margin: 8px 0 0 0;
    font-size: 13px;
    color: #909399;
  }

  .result-info {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  .result-info i {
    color: #67C23A;
    font-size: 16px;
  }

  .result-actions {
    display: flex;
    gap: 8px;
    justify-content: center;
  }

  /* 输入框旁边的图片预览样式 - 固定尺寸 */
  .table-td-thumb {
    width: 40px !important;
    height: 40px !important;
    border-radius: 6px;
    object-fit: cover;
    border: 1px solid #e4e7ed;
    flex-shrink: 0;
  }


  /* robots.txt和自定义头部代码表单项 - 标签独立占一行（PC端和移动端通用） */
  ::v-deep .code-editor-form-item {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }

  ::v-deep .code-editor-form-item .el-form-item__label {
    width: 100% !important;
    text-align: left !important;
    margin-bottom: 12px !important;
    margin-right: 0 !important;
    float: none !important;
    display: block !important;
    line-height: 1.5 !important;
    font-weight: 600 !important;
    color: #303133 !important;
  }

  ::v-deep .code-editor-form-item .el-form-item__content {
    width: 100% !important;
    margin-left: 0 !important;
    flex: 1;
  }

  /* PC端代码编辑器样式 - 保持原有高度 */
  @media (min-width: 769px) {
    ::v-deep .code-editor-form-item .el-textarea__inner {
      font-family: 'SF Mono', 'Monaco', 'Menlo', 'Consolas', monospace !important;
      border-radius: 6px;
      /* 不设置具体高度，让:rows属性生效 */
      height: auto !important; /* 确保:rows属性生效 */
      min-height: auto !important;
      max-height: none !important;
    }

    /* PC端确保行号正常显示 */
    .code-line-numbers {
      display: block !important;
    }
  }

  /* 响应式设计 - 移动端优化 */
  @media (max-width: 768px) {
    /* 主容器适配 */
    ::v-deep .el-card {
      margin: 8px !important;
      border-radius: 12px !important;
    }

    ::v-deep .el-card__body {
      padding: 12px !important;
    }

    /* 表单项移动端适配 */
    ::v-deep .el-form-item {
      margin-bottom: 16px !important;
    }

    ::v-deep .el-form-item__label {
      font-size: 14px !important;
      line-height: 1.4 !important;
      padding-bottom: 4px !important;
    }

    ::v-deep .el-form-item__content {
      margin-left: 0 !important;
    }

    /* 输入框移动端优化 */
    ::v-deep .el-input__inner {
      font-size: 16px !important; /* 防止iOS缩放 */
      height: 44px !important; /* 更好的触摸体验 */
    }

    ::v-deep .el-textarea__inner {
      font-size: 16px !important;
      min-height: 80px !important;
    }

    /* 按钮移动端适配 */
    .seo-actions-container {
      flex-direction: column;
      gap: 12px;
      padding: 16px 0;
    }

    .seo-actions-container .el-button {
      height: 44px !important;
      font-size: 16px !important;
      border-radius: 8px !important;
    }

    /* 选项卡移动端适配 */
    ::v-deep .el-tabs__nav-wrap {
      padding: 0 8px;
    }

    ::v-deep .el-tabs__item {
      font-size: 14px !important;
      padding: 0 12px !important;
    }

    /* 开关和复选框适配 */
    ::v-deep .el-switch {
      margin-right: 8px;
    }

    ::v-deep .el-checkbox {
      line-height: 1.4;
    }

    /* 提示文本适配 */
    .tip {
      font-size: 12px !important;
      line-height: 1.4 !important;
      margin-top: 4px !important;
    }

    /* 地址操作移动端适配 */
    .site-address-container {
      flex-direction: column;
      gap: 12px;
    }
    
    .site-address-input {
      min-width: auto;
    }
    
    .simple-address-actions {
      align-self: stretch;
      justify-content: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    
    .simple-address-btn {
      flex: 1;
      min-width: 120px; /* 确保按钮有足够宽度显示文字 */
      height: 44px !important; /* 符合触摸规范 */
      font-size: 14px !important;
      border-radius: 8px !important;
    }

    /* robots.txt和自定义头部代码输入框移动端适配 */
    .code-editor-wrapper {
      position: relative;
      width: 100%;
    }

    /* robots.txt编辑器移动端样式 */
    ::v-deep .robots-editor .el-textarea__inner {
      font-size: 14px !important;
      line-height: 1.5 !important;
      padding: 12px !important;
      border-radius: 8px !important;
      font-family: 'SF Mono', 'Monaco', 'Menlo', 'Consolas', monospace !important;
      resize: vertical !important;
      box-sizing: border-box !important;
      width: 100% !important;
      height: 200px !important; /* robots.txt固定200px高度 */
      overflow-y: auto !important;
    }

    /* 自定义头部代码编辑器移动端样式 */
    ::v-deep .custom-head-editor .el-textarea__inner {
      font-size: 14px !important;
      line-height: 1.5 !important;
      padding: 12px !important;
      border-radius: 8px !important;
      font-family: 'SF Mono', 'Monaco', 'Menlo', 'Consolas', monospace !important;
      resize: vertical !important;
      box-sizing: border-box !important;
      width: 100% !important;
      height: 140px !important; /* 自定义头部代码140px高度 */
      overflow-y: auto !important;
    }

    /* 行号在移动端隐藏 */
    .code-line-numbers {
      display: none !important; /* 移动端隐藏行号，节省空间 */
    }

    /* 代码编辑器容器适配 */
    .code-editor-wrapper {
      border-radius: 8px;
      overflow: hidden;
      width: 100%;
    }

    /* 图标上传移动端适配 */
    .icon-upload-container {
      gap: 8px;
    }

    .icon-preview {
      flex-wrap: wrap;
      justify-content: center;
      gap: 8px;
    }

    .preview-item {
      min-width: 60px;
      text-align: center;
    }

    .table-td-thumb {
      width: 40px !important;
      height: 40px !important;
    }

    /* PWA配置移动端适配 */
    .native-app-config {
      padding: 12px;
    }

    .app-config-item {
      flex-direction: column;
      align-items: flex-start;
      gap: 6px;
    }

    .app-config-item label {
      min-width: auto;
      font-size: 14px;
    }

    .pwa-preview {
      flex-direction: column;
      gap: 16px;
      padding: 16px;
    }

    .phone-screen {
      width: 120px;
      height: 213px;
      margin: 0 auto;
    }

    .preview-info {
      min-width: auto;
      text-align: center;
    }

    /* 搜索引擎配置移动端适配 */
    .search-engine-section {
      padding: 12px;
    }

    /* 社交媒体配置移动端适配 */
    .social-media-section {
      padding: 12px;
    }

    /* 对话框移动端适配 */

    /* 分析结果移动端适配 */
    .analysis-score {
      margin-bottom: 20px;
    }

    ::v-deep .el-progress-circle {
      width: 80px !important;
      height: 80px !important;
    }

    .analysis-suggestions {
      margin-top: 16px;
    }

    ::v-deep .el-alert {
      margin-bottom: 8px !important;
    }

    ::v-deep .el-alert__title {
      font-size: 14px !important;
      line-height: 1.4 !important;
    }
  }

  /* 超小屏幕适配 */
  @media (max-width: 480px) {
    ::v-deep .el-card {
      margin: 4px !important;
      border-radius: 8px !important;
    }

    ::v-deep .el-card__body {
      padding: 12px !important;
    }

    ::v-deep .el-form-item__label {
      font-size: 13px !important;
    }

    ::v-deep .el-input__inner {
      height: 40px !important;
    }

    .seo-actions-container .el-button {
      height: 40px !important;
      font-size: 14px !important;
    }

    /* 网站地址按钮超小屏适配 */
    .simple-address-btn {
      min-width: 100px !important;
      height: 40px !important;
      font-size: 13px !important;
      padding: 8px 12px !important;
    }

    .simple-address-actions {
      gap: 6px !important;
    }

    /* 代码编辑器超小屏适配 */
    ::v-deep .robots-editor .el-textarea__inner {
      height: 150px !important; /* robots.txt在超小屏适当减少高度 */
      font-size: 13px !important;
      padding: 10px !important;
      line-height: 1.4 !important;
    }

    ::v-deep .custom-head-editor .el-textarea__inner {
      height: 120px !important; /* 自定义头部代码在超小屏 */
      font-size: 13px !important;
      padding: 10px !important;
      line-height: 1.4 !important;
    }

    .tip {
      font-size: 11px !important;
    }

    /* 超小屏幕对话框 */

    .phone-screen {
      width: 100px;
      height: 178px;
    }

    ::v-deep .el-progress-circle {
      width: 60px !important;
      height: 60px !important;
    }
  }

  /* 平板适配 */
  @media (min-width: 769px) and (max-width: 1024px) {
    ::v-deep .el-card {
      margin: 12px;
    }

    ::v-deep .el-form-item__label {
      min-width: 140px;
    }

    .site-address-container {
      gap: 12px;
    }

    .icon-preview {
      gap: 16px;
    }

    .pwa-preview {
      padding: 20px;
    }

    /* 对话框布局由centered-dialog.css处理 */
  }

  /* 移动端特殊优化 */
  @media (max-width: 768px) {
    /* 表单标签适配 */
    ::v-deep .el-form-item--small .el-form-item__label {
      width: 100% !important;
      text-align: left !important;
      margin-bottom: 8px;
      font-weight: 500;
    }

    ::v-deep .el-form-item--small .el-form-item__content {
      width: 100% !important;
      margin-left: 0 !important;
    }

    /* 选择器移动端优化 */
    ::v-deep .el-select {
      width: 100% !important;
    }

    ::v-deep .el-select__caret {
      font-size: 18px;
    }

    /* 颜色选择器移动端适配 */
    ::v-deep .el-color-picker {
      height: 44px !important;
    }

    /* 开关移动端适配 */
    ::v-deep .el-switch__core {
      width: 50px !important;
      height: 24px !important;
    }

    ::v-deep .el-switch__core::after {
      width: 20px !important;
      height: 20px !important;
    }

    /* 文件上传移动端适配 */
    ::v-deep .el-upload-dragger {
      width: 100% !important;
      min-height: 120px !important;
    }

    /* 图标预览移动端适配 */
    .icon-preview {
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }

    .icon-preview > div {
      width: 100%;
      text-align: center;
    }

    /* 分割线移动端适配 */
    ::v-deep .el-divider {
      margin: 16px 0 !important;
    }

    /* 警告提示移动端适配 */
    ::v-deep .el-alert__content {
      padding-right: 8px !important;
    }

    /* 加载状态移动端优化 */
    ::v-deep .el-loading-spinner {
      margin-top: -20px;
    }

    /* 消息提示移动端适配 */
    ::v-deep .el-message {
      min-width: auto !important;
      width: 90% !important;
      left: 5% !important;
    }
  }

  /* 移动端横屏适配 */
  @media (max-width: 768px) and (orientation: landscape) {
    ::v-deep .el-dialog {
      max-height: 85vh !important;
    }

    ::v-deep .el-dialog__body {
      max-height: 65vh !important;
      overflow-y: auto;
    }

    .phone-screen {
      width: 80px;
      height: 142px;
    }
  }

  /* 移动端优化提示样式 */
  .mobile-optimization-tip {
    margin-top: 12px;
  }

  .mobile-optimization-tip ::v-deep .el-alert {
    border-radius: 8px;
    border: none;
    background: linear-gradient(135deg, #e3f2fd, #f0f8ff);
  }

  .mobile-optimization-tip ::v-deep .el-alert__icon {
    color: #2196f3;
  }

  .mobile-optimization-tip ::v-deep .el-alert__title {
    color: #1976d2;
    font-weight: 500;
  }

  .mobile-optimization-tip ::v-deep .el-alert__description {
    color: #666;
    font-size: 13px;
  }

  /* 邮件通知说明样式 */
  .notification-info {
    margin: 10px 0 20px 0;
  }

  .notification-info ::v-deep .el-alert {
    border-radius: 8px;
    border: none;
    background: linear-gradient(135deg, #e8f5e8, #f0f9ff);
  }

  .notification-info ::v-deep .el-alert__icon {
    color: #52c41a;
  }

  .notification-info ::v-deep .el-alert__title {
    color: #389e0d;
    font-weight: 500;
  }

  .notification-info ::v-deep .el-alert__description {
    color: #666;
  }

  .notification-info ::v-deep .el-alert__description p {
    margin: 4px 0;
    line-height: 1.5;
  }

  /* 触摸设备优化 */
  @media (hover: none) and (pointer: coarse) {
    /* 增加触摸目标大小 */
    ::v-deep .el-button {
      min-height: 44px !important;
      padding: 8px 16px !important;
    }

    ::v-deep .el-switch {
      min-height: 44px !important;
      display: flex !important;
      align-items: center !important;
    }

    /* 防止点击延迟 */
    ::v-deep .el-button,
    ::v-deep .el-switch,
    ::v-deep .el-checkbox,
    ::v-deep .el-radio {
      touch-action: manipulation;
    }

    /* 改善滚动性能 */
    ::v-deep .el-dialog__body,
    ::v-deep .el-tabs__content {
      -webkit-overflow-scrolling: touch;
    }

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
  }

  /* PC端样式 - 768px以上 */
  @media screen and (min-width: 769px) {
    ::v-deep .el-form-item__label {
      float: left !important;
    }
  }
</style>
