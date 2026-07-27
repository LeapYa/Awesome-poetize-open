<template>
  <div id="field-mobile-drawer">
    <SectionTag>移动端侧边栏</SectionTag>

    <el-card class="box-card" shadow="never" style="margin-top: 5px; border: none;">
      <div style="margin-bottom: 15px; font-size: 12px; color: #909399;">
        移动端导航栏的抽屉式侧边栏外观（标题、背景、字体颜色、分隔线等），与上方导航菜单共同组成移动端导航体验
      </div>

      <el-form label-width="100px" class="drawer-config-form">
        <!-- 标题类型 -->
        <el-form-item label="标题类型">
          <el-radio-group v-model="drawerConfig.titleType">
            <el-radio label="text">文字</el-radio>
            <el-radio label="avatar">头像</el-radio>
          </el-radio-group>
          <div style="margin-top: 5px; font-size: 12px; color: #909399;">
            选择显示文字标题或博客头像
          </div>
        </el-form-item>

        <!-- 标题文字 -->
        <el-form-item label="标题文字" v-if="drawerConfig.titleType === 'text'">
          <el-input v-model="drawerConfig.titleText" placeholder="欢迎光临"></el-input>
        </el-form-item>

        <!-- 头像大小 -->
        <el-form-item label="头像大小" v-if="drawerConfig.titleType === 'avatar'">
          <el-slider
            v-model="drawerConfig.avatarSize"
            :min="60"
            :max="150"
            :step="5"
            style="width: 300px;">
          </el-slider>
          <span style="margin-left: 10px;">{{ drawerConfig.avatarSize }}px</span>
        </el-form-item>

        <!-- 背景类型 -->
        <el-form-item label="背景类型">
          <el-radio-group v-model="drawerConfig.backgroundType">
            <el-radio label="image">背景图片</el-radio>
            <el-radio label="color">纯色</el-radio>
            <el-radio label="gradient">渐变色</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 背景图片 -->
        <el-form-item label="背景图片" v-if="drawerConfig.backgroundType === 'image'">
          <el-input v-model="drawerConfig.backgroundImage" placeholder="图片URL"></el-input>
          <uploadPicture
            :isAdmin="true"
            :prefix="'mobileDrawerBg'"
            style="margin-top: 10px"
            @addPicture="addDrawerBackgroundImage"
            :maxSize="5"
            :maxNumber="1">
          </uploadPicture>
          <div v-if="drawerConfig.backgroundImage" style="margin-top: 10px;">
            <el-image
              :src="drawerConfig.backgroundImage"
              style="width: 200px; height: 150px;"
              fit="cover">
            </el-image>
          </div>
        </el-form-item>

        <!-- 纯色背景 -->
        <el-form-item label="背景颜色" v-if="drawerConfig.backgroundType === 'color'">
          <el-color-picker v-model="drawerConfig.backgroundColor"></el-color-picker>
          <span style="margin-left: 10px;">{{ drawerConfig.backgroundColor }}</span>
        </el-form-item>

        <!-- 渐变背景 -->
        <el-form-item label="渐变背景" v-if="drawerConfig.backgroundType === 'gradient'">
          <el-select v-model="drawerConfig.backgroundGradient" placeholder="选择渐变样式">
            <el-option
              v-for="(gradient, index) in gradientPresets"
              :key="index"
              :label="gradient.name"
              :value="gradient.value">
              <div style="display: flex; align-items: center;">
                <div :style="{
                  width: '100px',
                  height: '20px',
                  background: gradient.value,
                  marginRight: '10px',
                  borderRadius: '3px'
                }"></div>
                <span>{{ gradient.name }}</span>
              </div>
            </el-option>
          </el-select>
          <div style="margin-top: 10px;">
            <div :style="{
              width: '100%',
              height: '80px',
              background: drawerConfig.backgroundGradient,
              borderRadius: '8px'
            }"></div>
          </div>
        </el-form-item>

        <!-- 遮罩透明度 -->
        <el-form-item label="遮罩透明度">
          <el-slider
            v-model="drawerConfig.maskOpacity"
            :min="0"
            :max="1"
            :step="0.05"
            :format-tooltip="formatOpacity"
            style="width: 300px;">
          </el-slider>
          <span style="margin-left: 10px;">{{ (drawerConfig.maskOpacity * 100).toFixed(0) }}%</span>
        </el-form-item>

        <!-- 菜单字体颜色 -->
        <el-form-item label="字体颜色">
          <el-color-picker v-model="drawerConfig.menuFontColor"></el-color-picker>
          <span style="margin-left: 10px;">{{ drawerConfig.menuFontColor }}</span>
          <div style="margin-top: 5px; font-size: 12px; color: #909399;">
            设置标题和菜单项的字体颜色
          </div>
        </el-form-item>

        <!-- 显示边框 -->
        <el-form-item label="显示分隔线">
          <el-switch v-model="drawerConfig.showBorder"></el-switch>
        </el-form-item>

        <!-- 显示雪花装饰 -->
        <el-form-item label="雪花装饰" v-if="drawerConfig.titleType === 'avatar'">
          <el-switch v-model="drawerConfig.showSnowflake"></el-switch>
          <div style="margin-top: 5px; font-size: 12px; color: #909399;">
            在头像和菜单之间的分隔线上显示雪花装饰
          </div>
        </el-form-item>

        <!-- 边框颜色 -->
        <el-form-item label="分隔线颜色" v-if="drawerConfig.showBorder">
          <el-input v-model="drawerConfig.borderColor" placeholder="rgba(255, 255, 255, 0.15)">
            <template slot="prepend">
              <el-color-picker
                v-model="borderColorPicker"
                show-alpha
                @change="updateBorderColor">
              </el-color-picker>
            </template>
          </el-input>
        </el-form-item>

        <!-- 预览 -->
        <el-form-item label="效果预览">
          <div class="drawer-preview" :style="getDrawerPreviewStyle()">
            <div class="drawer-preview-header">
              <!-- 文字标题 -->
              <div v-if="drawerConfig.titleType === 'text'" class="preview-title" :style="{ color: drawerConfig.menuFontColor }">
                {{ drawerConfig.titleText || '欢迎光临' }}
              </div>
              <!-- 头像 -->
              <div v-else-if="drawerConfig.titleType === 'avatar'" class="preview-avatar">
                <el-image :src="avatar || '/assets/avatar.jpg'" fit="cover">
                  <div slot="error" class="image-slot">
                    <i class="el-icon-picture-outline"></i>
                  </div>
                </el-image>
              </div>
            </div>
            <!-- 头像模式下的分隔线 -->
            <hr v-if="drawerConfig.titleType === 'avatar'"
                :class="['preview-divider', { 'show-snowflake': drawerConfig.showSnowflake }]" />
            <div class="drawer-preview-menu">
              <div class="preview-menu-item" :style="getMenuItemStyle()">
                <span :style="{ color: drawerConfig.menuFontColor }">🏡 首页</span>
              </div>
              <div class="preview-menu-item" :style="getMenuItemStyle()">
                <span :style="{ color: drawerConfig.menuFontColor }">📑 分类</span>
              </div>
              <div class="preview-menu-item" :style="getMenuItemStyle()">
                <span :style="{ color: drawerConfig.menuFontColor }">❤️‍🔥 家</span>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <div class="drawer-config-footer">
        <el-button @click="resetDrawerConfig" class="footer-btn">重置为默认</el-button>
        <el-button type="primary" @click="saveDrawerConfig" class="footer-btn">保存侧边栏配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';
import { useMainStore } from '@/stores/main';
const uploadPicture = () => import('../../common/uploadPicture');

export default {
  name: 'MobileDrawerSettings',
  components: {
    SectionTag,
    uploadPicture
  },
  props: {
    webInfoId: {
      type: [Number, String],
      default: null
    },
    // 移动端侧边栏配置 JSON 字符串（来自 web_info.mobile_drawer_config）
    mobileDrawerConfig: {
      type: String,
      default: ''
    },
    // 头像预览用
    avatar: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      mainStore: useMainStore(),
      drawerConfig: {
        titleType: 'text',
        titleText: '欢迎光临',
        avatarSize: 100,
        backgroundType: 'image',
        backgroundImage: '/assets/toolbar.jpg',
        backgroundColor: '#000000',
        backgroundGradient: 'linear-gradient(60deg, #ffd7e4, #c8f1ff 95%)',
        maskOpacity: 0.7,
        menuFontColor: '#ffffff',
        showBorder: true,
        borderColor: 'rgba(255, 255, 255, 0.15)',
        showSnowflake: true
      },
      borderColorPicker: '#ffffff',
      gradientPresets: [
        { name: '粉蓝渐变（默认）', value: 'linear-gradient(60deg, #ffd7e4, #c8f1ff 95%)' },
        { name: '紫色梦幻', value: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
        { name: '海洋蓝', value: 'linear-gradient(135deg, #0093E9 0%, #80D0C7 100%)' },
        { name: '日落橙', value: 'linear-gradient(135deg, #FDBB2D 0%, #22C1C3 100%)' },
        { name: '粉色浪漫', value: 'linear-gradient(135deg, #F093FB 0%, #F5576C 100%)' },
        { name: '绿色清新', value: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' },
        { name: '深空紫', value: 'linear-gradient(135deg, #434343 0%, #000000 100%)' },
        { name: '炫彩渐变', value: 'linear-gradient(to right, #ee7752, #e73c7e, #23a6d5, #23d5ab)' },
        { name: '夜空蓝', value: 'linear-gradient(135deg, #1e3c72 0%, #2a5298 100%)' },
      ]
    };
  },
  watch: {
    // 父组件加载完 webInfo 后回填抽屉配置
    mobileDrawerConfig: {
      handler(val) {
        if (val) {
          try {
            this.drawerConfig = JSON.parse(val);
          } catch (e) {
            console.error('解析移动端侧边栏配置失败:', e);
          }
        }
      },
      immediate: true
    }
  },
  methods: {
    addDrawerBackgroundImage(res) {
      this.drawerConfig.backgroundImage = res;
    },

    formatOpacity(val) {
      return `${(val * 100).toFixed(0)}%`;
    },

    updateBorderColor(color) {
      if (color) {
        this.drawerConfig.borderColor = color;
      }
    },

    getDrawerPreviewStyle() {
      let background = '';
      if (this.drawerConfig.backgroundType === 'image' && this.drawerConfig.backgroundImage) {
        background = `url(${this.drawerConfig.backgroundImage}) center center / cover no-repeat`;
      } else if (this.drawerConfig.backgroundType === 'color') {
        background = this.drawerConfig.backgroundColor;
      } else if (this.drawerConfig.backgroundType === 'gradient') {
        background = this.drawerConfig.backgroundGradient;
      }
      return {
        background: background,
        position: 'relative',
        '--drawer-mask-opacity': this.drawerConfig.maskOpacity
      };
    },

    getMenuItemStyle() {
      return {
        borderBottom: this.drawerConfig.showBorder ? `1px solid ${this.drawerConfig.borderColor}` : 'none'
      };
    },

    resetDrawerConfig() {
      this.drawerConfig = {
        titleType: 'text',
        titleText: '欢迎光临',
        avatarSize: 100,
        backgroundType: 'image',
        backgroundImage: '/assets/toolbar.jpg',
        backgroundColor: '#000000',
        backgroundGradient: 'linear-gradient(60deg, #ffd7e4, #c8f1ff 95%)',
        maskOpacity: 0.7,
        menuFontColor: '#ffffff',
        showBorder: true,
        borderColor: 'rgba(255, 255, 255, 0.15)',
        showSnowflake: true
      };
      this.$message.success('已重置为默认配置');
    },

    saveDrawerConfig() {
      const configJson = JSON.stringify(this.drawerConfig);
      this.$http.post(this.$constant.baseURL + '/webInfo/updateWebInfo', {
        id: this.webInfoId,
        mobileDrawerConfig: configJson
      }, true)
        .then(() => {
          this.$message.success('移动端侧边栏配置保存成功！');
          this.$emit('saved');
          this.mainStore.getWebsitConfig();
        })
        .catch((error) => {
          this.$message.error('保存失败: ' + (error.response?.data?.message || error.message));
        });
    }
  }
};
</script>

<style scoped>
/* 移动端侧边栏预览 */
.drawer-preview {
  width: 100%;
  max-width: 420px;
  min-height: 300px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  --drawer-mask-opacity: 0.7;
}

.drawer-preview::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, var(--drawer-mask-opacity));
  z-index: 1;
}

.drawer-preview-header {
  position: relative;
  z-index: 2;
  padding: 20px;
  text-align: center;
}

.preview-title {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 2px;
}

.preview-avatar {
  width: 70px;
  height: 70px;
  border-radius: 50%;
  overflow: hidden;
  margin: auto;
}

.preview-avatar .el-image {
  width: 100%;
  height: 100%;
}

.preview-divider {
  position: relative;
  margin: 30px auto 20px;
  border: 0;
  border-top: 1px dashed var(--lightGreen);
  overflow: visible;
  z-index: 2;
}

.preview-divider::before {
  position: absolute;
  top: 50%;
  left: 5%;
  transform: translateY(-50%);
  color: var(--lightGreen);
  content: "";
  font-size: 28px;
  line-height: 1;
}

.preview-divider.show-snowflake::before {
  content: "❄";
}

.drawer-preview-menu {
  position: relative;
  z-index: 2;
  padding: 10px 0;
}

.preview-menu-item {
  padding: 15px 20px;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.preview-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.drawer-config-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.drawer-config-footer .footer-btn {
  min-width: 120px;
}

@media screen and (max-width: 768px) {
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
  ::v-deep .el-slider {
    width: 100% !important;
  }
  .drawer-config-footer {
    flex-direction: column;
  }
  .drawer-config-footer .footer-btn {
    width: 100%;
    margin: 0;
  }
}
</style>
