<template>
  <div>
    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        基础信息
      </el-tag>
      <el-form :model="webInfo" :rules="rules" ref="ruleForm" label-width="100px"
               class="demo-ruleForm">
        <el-form-item id="field-web-name" label="站点名称" prop="webName">
          <el-input v-model="webInfo.webName"></el-input>
          <span class="tip">显示于前台导航栏左侧与侧边栏名片（头像下方），类似站长昵称；与用于浏览器标签页/SEO的「网站标题」不同。</span>
        </el-form-item>

        <el-form-item id="field-logo-image" label="站点Logo" prop="logoImage">
          <div style="display: flex">
            <el-input v-model="webInfo.logoImage" placeholder="Logo图片URL，留空则前台导航栏显示站点名称"></el-input>
            <el-image lazy class="table-td-thumb"
                      style="margin-left: 10px"
                      :preview-src-list="webInfo.logoImage ? [webInfo.logoImage] : []"
                      :src="webInfo.logoImage"
                      fit="contain"></el-image>
          </div>
          <uploadPicture :isAdmin="true" :prefix="'webLogo'" style="margin-top: 15px"
                         @addPicture="addLogoImage"
                         :maxSize="10"
                         :maxNumber="1"></uploadPicture>
          <span class="tip">配置后前台导航栏左侧优先显示Logo图片，留空则显示站点名称。建议使用透明背景的PNG或SVG图片。</span>
        </el-form-item>

        <el-form-item id="field-web-title" label="网站标题" prop="webTitle">
          <el-input v-model="webInfo.webTitle"></el-input>
        </el-form-item>

        <el-form-item id="field-home-title" label="首页标题" prop="homeTitle">
          <el-input v-model="webInfo.homeTitle" placeholder="为空时与网站标题一致；常用于 ICP 备案名展示，因网站首页标题须完整包含备案系统填报的网站名称"></el-input>
        </el-form-item>

        <el-form-item id="field-site-address" label="网站地址" prop="siteAddress">
          <div class="site-address-container">
            <el-input 
              v-model="webInfo.siteAddress" 
              placeholder="自动检测的网站地址"
              :readonly="!editingSiteAddress"
              class="site-address-input">
            </el-input>
            
            <!-- 简约地址操作按钮 -->
            <div class="simple-address-actions" v-if="!editingSiteAddress">
              <el-button 
                size="small" 
                @click="detectSiteAddress" 
                :loading="detectingAddress"
                class="simple-address-btn">
                {{ detectingAddress ? '检测中...' : '自动检测' }}
              </el-button>
              
              <el-button 
                size="small" 
                @click="startEditSiteAddress"
                class="simple-address-btn">
                手动编辑
              </el-button>
            </div>
            
            <!-- 编辑状态下的按钮 -->
            <div class="simple-address-actions" v-if="editingSiteAddress">
              <el-button 
                size="small" 
                type="primary" 
                @click="editingSiteAddress = false"
                class="simple-address-btn">
                确认
              </el-button>
              
              <el-button 
                size="small" 
                @click="cancelEditSiteAddress"
                class="simple-address-btn">
                取消
              </el-button>
            </div>
          </div>
          <span class="tip">
            网站的完整访问地址，用于生成站点地图、二维码和其他功能。
            <strong>推荐使用自动检测</strong>，系统会根据当前访问地址自动填写。
          </span>
        </el-form-item>

        <el-form-item id="field-web-status" label="状态" prop="status">
          <div style="display: flex; align-items: center;">
            <el-switch @change="changeWebStatus" v-model="webInfo.status"></el-switch>
            <span :style="{
                marginLeft: '10px',
                fontSize: '12px',
                color: webInfo.status ? '#67c23a' : '#f56c6c'
              }">
              {{ webInfo.status ? '已开启' : '已关闭' }}
            </span>
          </div>
        </el-form-item>

        <el-form-item id="field-background-image" label="背景" prop="backgroundImage">
          <div style="display: flex">
            <el-input v-model="webInfo.backgroundImage"></el-input>
            <el-image lazy class="table-td-thumb"
                      style="margin-left: 10px"
                      :preview-src-list="[webInfo.backgroundImage]"
                      :src="webInfo.backgroundImage"
                      fit="cover"></el-image>
          </div>
          <uploadPicture :isAdmin="true" :prefix="'webBackgroundImage'" style="margin-top: 15px"
                         @addPicture="addBackgroundImage"
                         :maxSize="10"
                         :maxNumber="1"></uploadPicture>
        </el-form-item>

        <el-form-item id="field-site-avatar" label="站长头像" prop="avatar">
          <div style="display: flex">
            <el-input v-model="webInfo.avatar"></el-input>
            <el-image lazy class="table-td-thumb"
                      style="margin-left: 10px"
                      :preview-src-list="[webInfo.avatar]"
                      :src="webInfo.avatar"
                      fit="cover"></el-image>
          </div>
          <uploadPicture :isAdmin="true" :prefix="'webAvatar'" style="margin-top: 15px" @addPicture="addAvatar"
                         :maxNumber="1"></uploadPicture>
          <span class="tip">站长个人头像，显示于前台侧边栏名片、文章作者署名等处；与浏览器标签页图标无关（后者在「SEO优化 → 网站图标」配置）。</span>
        </el-form-item>
        
        <!-- 极简页脚开关 -->
        <el-form-item label="极简页脚" prop="minimalFooter">
          <div style="display: flex; align-items: center;">
            <el-switch v-model="webInfo.minimalFooter"></el-switch>
            <span :style="{
                marginLeft: '10px',
                fontSize: '12px',
                color: webInfo.minimalFooter ? '#67c23a' : '#f56c6c'
              }">
              {{ webInfo.minimalFooter ? '已开启' : '已关闭' }}
            </span>
          </div>
        </el-form-item>

        <el-form-item id="field-footer" label="页脚文案" prop="footer">
          <el-input 
            v-model="webInfo.footer" 
            placeholder="页脚文案（极简页脚开启时不显示）"
            :disabled="webInfo.minimalFooter">
          </el-input>
        </el-form-item>

        <el-form-item id="field-footer-background" label="页脚背景" prop="footerBackgroundImage">
          <div style="display: flex">
            <el-input v-model="webInfo.footerBackgroundImage" placeholder="页脚背景图片URL（可选）"></el-input>
            <el-image lazy class="table-td-thumb"
                      style="margin-left: 10px"
                      v-if="webInfo.footerBackgroundImage"
                      :preview-src-list="[webInfo.footerBackgroundImage]"
                      :src="webInfo.footerBackgroundImage"
                      fit="cover"></el-image>
          </div>
          <uploadPicture :isAdmin="true" :prefix="'footerBackground'" style="margin-top: 15px"
                         @addPicture="addFooterBackgroundImage"
                         :maxSize="10"
                         :maxNumber="1"></uploadPicture>
          
          <!-- 背景图片配置选项 -->
          <div v-if="webInfo.footerBackgroundImage" style="margin-top: 15px;">
            <el-divider content-position="left">背景图片设置</el-divider>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="背景大小" label-width="80px">
                  <el-select v-model="footerBgConfig.backgroundSize" placeholder="选择背景大小">
                    <el-option label="覆盖 (cover)" value="cover"></el-option>
                    <el-option label="包含 (contain)" value="contain"></el-option>
                    <el-option label="自动 (auto)" value="auto"></el-option>
                    <el-option label="拉伸 (100% 100%)" value="100% 100%"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="背景位置" label-width="80px">
                  <el-select v-model="footerBgConfig.backgroundPosition" placeholder="选择背景位置">
                    <el-option label="居中" value="center center"></el-option>
                    <el-option label="顶部居中" value="center top"></el-option>
                    <el-option label="底部居中" value="center bottom"></el-option>
                    <el-option label="左上角" value="left top"></el-option>
                    <el-option label="右上角" value="right top"></el-option>
                    <el-option label="左下角" value="left bottom"></el-option>
                    <el-option label="右下角" value="right bottom"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="重复方式" label-width="80px">
                  <el-select v-model="footerBgConfig.backgroundRepeat" placeholder="选择重复方式">
                    <el-option label="不重复" value="no-repeat"></el-option>
                    <el-option label="重复" value="repeat"></el-option>
                    <el-option label="水平重复" value="repeat-x"></el-option>
                    <el-option label="垂直重复" value="repeat-y"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="透明度" label-width="80px">
                  <el-slider v-model="footerBgConfig.opacity"
                           :min="0"
                           :max="100"
                           :step="5"
                           :format-tooltip="val => val + '%'"
                           @input="handleOpacityChange">
                  </el-slider>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="文字阴影" label-width="80px">
                  <el-switch v-model="footerBgConfig.textShadow"></el-switch>
                  <span style="margin-left: 10px; color: #999; font-size: 12px;">增强文字可读性</span>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="遮罩颜色" label-width="80px">
                  <div style="display: flex; align-items: center; gap: 10px;">
                    <el-color-picker v-model="footerBgConfig.maskColor"
                                   :predefine="['#000000', '#1a1a1a', '#333333', '#444444', '#555555', '#666666', '#FFFFFF']"
                                   show-alpha
                                   color-format="rgba"
                                   @change="handleMaskColorChange">
                    </el-color-picker>
                    <span style="color: #999; font-size: 12px;">调整遮罩颜色和透明度</span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="效果预览" label-width="80px">
                  <div style="width: 100px; height: 30px; border: 1px solid #ddd; border-radius: 4px; position: relative; overflow: hidden;">
                    <div v-if="webInfo.footerBackgroundImage"
                         :style="{
                           position: 'absolute',
                           top: 0,
                           left: 0,
                           right: 0,
                           bottom: 0,
                           backgroundImage: 'url(' + webInfo.footerBackgroundImage + ')',
                           backgroundSize: footerBgConfig.backgroundSize || 'cover',
                           backgroundPosition: footerBgConfig.backgroundPosition || 'center center',
                           backgroundRepeat: footerBgConfig.backgroundRepeat || 'no-repeat'
                         }"></div>
                    <div v-else style="position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: #f0f0f0;"></div>
                    <div :style="{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      right: 0,
                      bottom: 0,
                      background: footerBgConfig.maskColor || 'rgba(0, 0, 0, 0.5)'
                    }"></div>
                    <span :style="{
                      position: 'relative',
                      zIndex: 10,
                      color: 'white',
                      fontSize: '11px',
                      display: 'block',
                      textAlign: 'center',
                      lineHeight: '30px',
                      textShadow: footerBgConfig.textShadow ? '0 2px 8px rgba(0, 0, 0, 0.8), 0 1px 3px rgba(0, 0, 0, 0.6)' : 'none'
                    }">样例文字</span>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-form-item>

        <!-- 页脚友链（服务提供商展示） -->
        <el-form-item id="field-footer-friend-links" label="页脚友链">
          <div style="width: 100%;">
            <p style="font-size: 12px; color: #909399; margin: 0 0 10px 0;">
              在页脚展示 CDN / 云服务等提供商链接（如"本站由 XX 提供加速"），留空则不显示。
            </p>
            <div v-for="(link, index) in footerFriendLinks" :key="index"
                 style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <el-input v-model="link.name" placeholder="名称（如：又拍云）" style="width: 25%;" size="small"></el-input>
              <el-input v-model="link.url" placeholder="链接（如：https://www.upyun.com）" style="width: 40%;" size="small"></el-input>
              <el-input v-model="link.logo" placeholder="Logo URL（可选）" style="width: 25%;" size="small"></el-input>
              <el-button type="danger" icon="el-icon-delete" size="small" circle
                         @click="footerFriendLinks.splice(index, 1)"></el-button>
            </div>
            <el-button type="primary" size="small" icon="el-icon-plus" plain
                       @click="footerFriendLinks.push({ name: '', url: '', logo: '' })">
              添加友链
            </el-button>
          </div>
        </el-form-item>

        <el-form-item id="field-contact-email" label="邮箱" prop="email">
          <el-input v-model="webInfo.email" placeholder="联系邮箱（用于隐私政策和侵权联系）"></el-input>
        </el-form-item>
      </el-form>
      <div class="myCenter" style="margin-bottom: 22px">
        <el-button type="primary" @click="submitForm('ruleForm')">保存基本信息</el-button>
      </div>
    </div>

    <div style="margin-top: 20px;">
      <el-button type="danger" @click="resetForm('ruleForm')">重置所有修改</el-button>
    </div>



  </div>
</template>

<script>
    import { useMainStore } from '@/stores/main';
    import { setAdminContentLoading } from '@/utils/sessionValidation';

const uploadPicture = () => import( "../common/uploadPicture");

  export default {
    components: {
      uploadPicture
    },
    data() {
      return {
        disabled: true,
        types: ['', 'success', 'info', 'danger', 'warning'],
        webInfo: {
          id: null,
          webName: "",
          webTitle: "",
          logoImage: "",
          homeTitle: "",
          siteAddress: "",
          footer: "",
          backgroundImage: "/assets/backgroundPicture.jpg",
          avatar: "",
          waifuJson: "",
          status: false,
          navConfig: "",
          footerBackgroundImage: "",
          footerBackgroundConfig: "",
          email: "",
          minimalFooter: false,
        },
        // 网站地址编辑状态
        editingSiteAddress: false,
        detectingAddress: false,
        originalSiteAddress: "",


        rules: {
          webName: [
            {required: true, message: '请输入站点名称', trigger: 'blur'},
            {min: 1, max: 10, message: '长度在 1 到 10 个字符', trigger: 'change'}
          ],
          webTitle: [
            {required: true, message: '请输入网站标题', trigger: 'blur'}
          ],
          siteAddress: [
            {required: true, message: '请输入网站地址或点击自动检测', trigger: 'blur'},
            {pattern: /^https?:\/\/.+/, message: '请输入完整的网站地址（http://或https://开头）', trigger: 'blur'}
          ],
          footer: [
            // 移除长度限制，页脚内容完全自由
          ],
          backgroundImage: [
            {required: true, message: '请输入背景', trigger: 'change'}
          ],
          email: [
            {required: true, message: '请输入联系邮箱', trigger: 'blur'}
          ],
          status: [
            {required: true, message: '请设置网站状态', trigger: 'change'}
          ],
          avatar: [
            {required: true, message: '请上传站长头像', trigger: 'change'}
          ]
        },
        isMobileDevice: false,
        loading: false,

        footerBgConfig: {
          backgroundSize: 'cover',
          backgroundPosition: 'center center',
          backgroundRepeat: 'no-repeat',
          opacity: 100,
          textShadow: false,
          maskColor: 'rgba(0, 0, 0, 0.5)'
        },
        // 页脚友链配置
        footerFriendLinks: [],
        footerFriendLinksConfig: {
          id: null,
          configName: '页脚友链（服务提供商展示）',
          configKey: 'footer.friendLinks',
          configValue: '',
          configType: '2'
        },

      }
    },

    computed: {
      mainStore() {
        return useMainStore();
      }
    },

    created() {
      // 并行执行所有初始化请求
      this.initializeData();
    },

    mounted() {
      if (this.isMobile()) {
        this.isMobileDevice = true;
      }
      
      // 检测设备类型
      this.checkDeviceType();
      window.addEventListener('resize', this.checkDeviceType);
    },

    beforeDestroy() {
      this.setContentLoading(false);
      // 移除监听器
      window.removeEventListener('resize', this.checkDeviceType);
    },

    methods: {
      setContentLoading(loading) {
        if (this.loading === loading) {
          return;
        }
        this.loading = loading;
        setAdminContentLoading(loading);
      },

      // 新增：并行初始化所有数据
      async initializeData() {
        this.setContentLoading(true);
        try {
          await Promise.all([
            this.getWebInfo(),
            this.loadFooterFriendLinks()
          ]);
        } catch (error) {
          console.error("初始化数据时出错:", error);
        } finally {
          this.setContentLoading(false);
        }
      },

      // 处理透明度变化
      handleOpacityChange(val) {
        // 获取当前遮罩颜色
        let maskColor = this.footerBgConfig.maskColor;
        if (!maskColor) {
          maskColor = 'rgba(0, 0, 0, 0.5)';
        }

        // 如果是 rgba 格式，直接替换 alpha 值
        const rgbaMatch = maskColor.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+),?\s*([\d.]+)?\)$/);
        if (rgbaMatch) {
          const r = parseInt(rgbaMatch[1]);
          const g = parseInt(rgbaMatch[2]);
          const b = parseInt(rgbaMatch[3]);
          const alpha = (val / 100).toFixed(2);
          this.footerBgConfig.maskColor = `rgba(${r}, ${g}, ${b}, ${alpha})`;
        } else {
          // 如果是其他格式（hex等），转为 rgba
          const hex = maskColor.replace('#', '');
          let r, g, b;
          if (hex.length === 3) {
            r = parseInt(hex[0] + hex[0], 16);
            g = parseInt(hex[1] + hex[1], 16);
            b = parseInt(hex[2] + hex[2], 16);
          } else {
            r = parseInt(hex.substring(0, 2), 16);
            g = parseInt(hex.substring(2, 4), 16);
            b = parseInt(hex.substring(4, 6), 16);
          }
          const alpha = (val / 100).toFixed(2);
          this.footerBgConfig.maskColor = `rgba(${r}, ${g}, ${b}, ${alpha})`;
        }
      },

      // 处理遮罩颜色变化
      handleMaskColorChange(val) {
        // 从新的 maskColor 中提取 alpha 值，更新透明度滑块
        if (val) {
          const rgbaMatch = val.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+),?\s*([\d.]+)?\)$/);
          if (rgbaMatch && rgbaMatch[4]) {
            const alpha = parseFloat(rgbaMatch[4]);
            this.footerBgConfig.opacity = Math.round(alpha * 100);
          }
        }
      },

      addBackgroundImage(res) {
        this.webInfo.backgroundImage = res;
      },
      addAvatar(res) {
        this.webInfo.avatar = res;
      },
addLogoImage(res) {
        this.webInfo.logoImage = res;
      },
      changeWebStatus() {
        this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", {
          id: this.webInfo.id,
          status: this.webInfo.status
        }, true)
          .then((res) => {
            this.getWebInfo();
            this.$message({
              message: "保存成功！",
              type: "success"
            });
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      
      // 自动检测网站地址
      async detectSiteAddress() {
        this.detectingAddress = true;
        
        try {
          const currentOrigin = window.location.origin;
          let finalUrl = currentOrigin;
          
          // 处理标准端口
          if (!currentOrigin.includes(':3000') && !currentOrigin.includes(':8080')) {
            if (currentOrigin.includes(':80') && currentOrigin.startsWith('http://')) {
              finalUrl = currentOrigin.replace(':80', '');
            } else if (currentOrigin.includes(':443') && currentOrigin.startsWith('https://')) {
              finalUrl = currentOrigin.replace(':443', '');
            }
          }
          
          this.webInfo.siteAddress = finalUrl;
          
        } catch (error) {
          console.error('自动检测网站地址失败:', error);
          this.$message({
            type: 'error',
            message: '自动检测失败，请手动输入网站地址'
          });
        } finally {
          this.detectingAddress = false;
        }
      },
      
      // 开始编辑网站地址
      startEditSiteAddress() {
        this.originalSiteAddress = this.webInfo.siteAddress;
        this.editingSiteAddress = true;
      },
      
      // 取消编辑网站地址
      cancelEditSiteAddress() {
        this.webInfo.siteAddress = this.originalSiteAddress;
        this.editingSiteAddress = false;
      },
      // 优化：将getWebInfo改为异步方法
      async getWebInfo() {
        try {
          const res = await this.$http.get(this.$constant.baseURL + "/admin/webInfo/getAdminWebInfoDetails", {}, true);
            if (!this.$common.isEmpty(res.data)) {
              this.webInfo.id = res.data.id;
              this.webInfo.webName = res.data.webName;
              this.webInfo.webTitle = res.data.webTitle;
              this.webInfo.logoImage = res.data.logoImage || "";
              this.webInfo.homeTitle = res.data.homeTitle;
              this.webInfo.siteAddress = res.data.siteAddress || "";
              this.webInfo.footer = res.data.footer;
              this.webInfo.backgroundImage = res.data.backgroundImage;
              this.webInfo.avatar = res.data.avatar;
              this.webInfo.waifuJson = res.data.waifuJson;
              this.webInfo.status = res.data.status;
              this.webInfo.navConfig = res.data.navConfig || "[]";
              this.webInfo.footerBackgroundImage = res.data.footerBackgroundImage || "";
              this.webInfo.footerBackgroundConfig = res.data.footerBackgroundConfig || "";
              this.webInfo.email = res.data.email || "";
              
              // 加载页脚背景配置
              if (this.webInfo.footerBackgroundConfig) {
                try {
                  this.footerBgConfig = JSON.parse(this.webInfo.footerBackgroundConfig);
                } catch (e) {
                  console.error("解析页脚背景配置失败:", e);
                  // 使用默认配置
                  this.footerBgConfig = {
                    backgroundSize: 'cover',
                    backgroundPosition: 'center center',
                    backgroundRepeat: 'no-repeat',
                    opacity: 100,
                    textShadow: false,
                    maskColor: 'rgba(0, 0, 0, 0.5)'
                  };
                }
              }
              
              // 更新mainStore中的webInfo，确保Live2D组件能立即响应变化
              this.mainStore.setWebInfo({...this.mainStore.webInfo, ...this.webInfo});
            }
        } catch (error) {
            this.$message({
              message: error.message,
              type: "error"
            });
          throw error; // 重新抛出错误，让Promise.allSettled能够捕获
        }
      },
      submitForm(formName) {
        this.$refs[formName].validate((valid) => {
          if (valid) {
            // 只发送基本信息字段，不包括公告、随机名称等专门管理的字段
            const basicInfoToUpdate = {
              id: this.webInfo.id,
              webName: this.webInfo.webName,
              webTitle: this.webInfo.webTitle,
              logoImage: this.webInfo.logoImage,
              homeTitle: this.webInfo.homeTitle,
              siteAddress: this.webInfo.siteAddress,
              footer: this.webInfo.footer,
              backgroundImage: this.webInfo.backgroundImage,
              avatar: this.webInfo.avatar,
              waifuJson: this.webInfo.waifuJson,
              status: this.webInfo.status,
              apiEnabled: this.webInfo.apiEnabled,
              apiKey: this.webInfo.apiKey,
              footerBackgroundImage: this.webInfo.footerBackgroundImage,
              footerBackgroundConfig: JSON.stringify(this.footerBgConfig),
              email: this.webInfo.email,
              minimalFooter: this.webInfo.minimalFooter
            };

            this.updateWebInfo(basicInfoToUpdate);
          } else {
            this.$message({
              message: "请完善必填项！",
              type: "error"
            });
          }
        });
      },
      resetForm(formName) {
        this.$refs[formName].resetFields();
        this.getWebInfo();
      },
      updateWebInfo(value) {
        this.$confirm('确认保存？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          // 统一更新逻辑：将看板娘状态包含在主更新请求中
          // 这样可以避免并发更新导致的缓存竞态条件
          const updateData = { ...value };


          // 使用单一请求更新所有信息，避免并发问题
          const promises = [
            this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", updateData, true)
          ];

          // 保存页脚友链配置
          const validLinks = this.footerFriendLinks.filter(l => l.name && l.url);
          this.footerFriendLinksConfig.configValue = validLinks.length > 0 ? JSON.stringify(validLinks) : '';
          promises.push(
            this.$http.post(this.$constant.baseURL + '/sysConfig/saveOrUpdateConfig', this.footerFriendLinksConfig, true)
          );

          // 处理所有请求完成
          Promise.all(promises)
            .then(async () => {
              this.getWebInfo();
              // 刷新 sysConfig store
              try {
                const sysConfRes = await this.$http.get(this.$constant.baseURL + '/sysConfig/listSysConfig');
                if (sysConfRes && sysConfRes.data) {
                  this.mainStore.loadSysConfig(sysConfRes.data);
                }
              } catch (_) {}
              // 更新mainStore中的webInfo，确保组件能立即响应变化
              this.mainStore.setWebInfo({...this.mainStore.webInfo, ...this.webInfo});
              this.$message({
                message: "保存成功！",
                type: "success"
              });
            })
            .catch((error) => {
              this.$message({
                message: error.message || '部分保存失败，请检查',
                type: 'error'
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消保存!'
          });
        });
      },
      // 检测设备类型
      checkDeviceType() {
        this.isMobileDevice = window.innerWidth <= 768 || this.isMobile();
      },
      isMobile() {
        return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
      },
      addFooterBackgroundImage(res) {
        this.webInfo.footerBackgroundImage = res;
      },
      // 加载页脚友链配置
      async loadFooterFriendLinks() {
        try {
          const res = await this.$http.get(this.$constant.baseURL + '/sysConfig/listConfig', {}, true);
          if (res && res.data) {
            const list = Array.isArray(res.data) ? res.data : (res.data.data || []);
            const item = list.find(c => c.configKey === 'footer.friendLinks');
            if (item) {
              this.footerFriendLinksConfig.id = item.id;
              this.footerFriendLinksConfig.configValue = item.configValue || '';
              try {
                const parsed = JSON.parse(item.configValue);
                this.footerFriendLinks = Array.isArray(parsed) ? parsed : [];
              } catch (_) {
                this.footerFriendLinks = [];
              }
            }
          }
        } catch (e) {
          console.warn('加载页脚友链配置失败:', e);
        }
      },

    }
  }
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
  
  /* 网站地址容器样式 */
  .site-address-container {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  
  .site-address-input {
    flex: 1;
  }
  
  .simple-address-actions {
    display: flex;
    gap: 8px;
  }
  
  .simple-address-btn {
    min-width: 90px;
  }
  
  .tip {
    display: block;
    margin-top: 8px;
    font-size: 12px;
    color: #999;
    line-height: 1.5;
  }
  
  .tip strong {
    color: #0071e3;
    font-weight: 600;
  }

  /* 网站地址移动端适配 */
  @media screen and (max-width: 768px) {
    .site-address-container {
      flex-direction: column;
      align-items: stretch;
      gap: 10px;
    }
    
    .site-address-input {
      width: 100%;
    }
    
    .simple-address-actions {
      width: 100%;
      justify-content: stretch;
    }
    
    .simple-address-btn {
      flex: 1;
      min-width: unset;
    }
  }

  @media screen and (max-width: 480px) {
    .site-address-container {
      gap: 8px;
    }
    
    .simple-address-actions {
      gap: 6px;
    }
    
    .simple-address-btn {
      font-size: 13px;
      padding: 8px 12px;
    }
  }

  /* ===========================================
     表单移动端样式 - PC端和移动端响应式
     =========================================== */
  
  /* PC端样式 - 768px以上 */
  @media screen and (min-width: 769px) {
    ::v-deep .el-form-item__label {
      float: left !important;
    }
  }

  /* 移动端样式 - 768px及以下 */
  @media screen and (max-width: 768px) {
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
      padding: 0 10px !important;
    }

    /* 输入框移动端优化 */
    ::v-deep .el-input__inner {
      font-size: 16px !important;
      height: 44px !important;
      border-radius: 8px !important;
    }

    ::v-deep .el-textarea__inner {
      font-size: 16px !important;
      border-radius: 8px !important;
    }

    /* 选择器移动端优化 */
    ::v-deep .el-select {
      width: 100% !important;
    }

    ::v-deep .el-select .el-input__inner {
      height: 44px !important;
      line-height: 44px !important;
    }

    /* 按钮移动端优化 */
    ::v-deep .el-button {
      min-height: 40px !important;
      border-radius: 8px !important;
    }
  }

  /* 极小屏幕优化 - 480px及以下 */
  @media screen and (max-width: 480px) {
    ::v-deep .el-form-item__label {
      font-size: 13px !important;
    }

    ::v-deep .el-input__inner,
    ::v-deep .el-select .el-input__inner {
      height: 40px !important;
      line-height: 40px !important;
      font-size: 15px !important;
    }

    ::v-deep .el-button {
      min-height: 38px !important;
      font-size: 14px !important;
    }
  }

  .el-tag {
    margin: 10px;
  }

  .table-td-thumb {
    border-radius: 2px;
    width: 40px;
    height: 40px;
  }

</style>

<style>
/* el-message-box 移动端适配 - 非scoped样式，作用于全局动态元素 */
@media screen and (max-width: 768px) {
  /* 通用confirm对话框适配 */
  .mobile-responsive-confirm {
    width: 90% !important;
    max-width: 400px !important;
  }

  .mobile-responsive-confirm .el-message-box__header {
    padding: 15px !important;
  }

  .mobile-responsive-confirm .el-message-box__title {
    font-size: 16px !important;
  }

  .mobile-responsive-confirm .el-message-box__content {
    padding: 15px !important;
    font-size: 14px !important;
  }

  .mobile-responsive-confirm .el-message-box__btns {
    padding: 10px 15px 15px !important;
  }

  .mobile-responsive-confirm .el-message-box__btns button {
    padding: 10px 20px !important;
    font-size: 14px !important;
  }

  /* 预设对话框适配 */
  .preset-dialog {
    width: 90% !important;
    max-width: 500px !important;
  }

  .preset-dialog .el-message-box__header {
    padding: 15px !important;
  }

  .preset-dialog .el-message-box__title {
    font-size: 16px !important;
  }

  .preset-dialog .el-message-box__content {
    padding: 15px !important;
  }

  .preset-dialog .preset-btn {
    width: calc(50% - 10px) !important;
    margin: 5px !important;
    padding: 12px 8px !important;
    font-size: 13px !important;
  }
}

@media screen and (max-width: 480px) {
  .mobile-responsive-confirm {
    width: 95% !important;
  }

  .mobile-responsive-confirm .el-message-box__header {
    padding: 12px !important;
  }

  .mobile-responsive-confirm .el-message-box__title {
    font-size: 15px !important;
  }

  .mobile-responsive-confirm .el-message-box__content {
    padding: 12px !important;
    font-size: 13px !important;
  }

  .mobile-responsive-confirm .el-message-box__btns {
    padding: 8px 12px 12px !important;
    display: flex;
    flex-direction: column-reverse;
    gap: 8px;
  }

  .mobile-responsive-confirm .el-message-box__btns button {
    width: 100% !important;
    margin: 0 !important;
    padding: 12px 20px !important;
    font-size: 15px !important;
  }

  /* 预设对话框超小屏幕适配 */
  .preset-dialog {
    width: 95% !important;
  }

  .preset-dialog .el-message-box__header {
    padding: 12px !important;
  }

  .preset-dialog .el-message-box__title {
    font-size: 15px !important;
  }

  .preset-dialog .el-message-box__content {
    padding: 12px !important;
    max-height: 60vh;
    overflow-y: auto;
  }

  .preset-dialog .preset-btn {
    width: 100% !important;
    margin: 5px 0 !important;
    padding: 14px 10px !important;
    font-size: 14px !important;
  }
}

</style>
