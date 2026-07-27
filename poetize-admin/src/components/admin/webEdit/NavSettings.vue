<template>
  <div id="field-nav">
    <SectionTag>导航栏配置</SectionTag>

    <el-card class="box-card" shadow="never" style="margin-top: 5px; border: none;">
      <!-- 响应式表格 -->
      <div class="responsive-table-container">
        <el-table
          :data="navItems"
          border
          style="width: 100%"
          :class="{'mobile-table': isMobileDevice}"
          size="small"
          @row-click="handleNavRowClick"
          @touchstart.native="handleTouchStart"
          @touchend.native="handleTouchEnd">

          <el-table-column label="排序" width="60" align="center">
            <template slot-scope="scope">
              <span style="color: #909399;">{{ scope.$index + 1 }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="name" label="名称" min-width="120" header-align="center">
            <template slot-scope="scope">
              <el-input v-model="scope.row.name" placeholder="导航项名称"></el-input>
            </template>
          </el-table-column>

          <el-table-column prop="icon" label="图标" min-width="100" header-align="center" :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
            <template slot-scope="scope">
              <el-input v-model="scope.row.icon" placeholder="🏡">
                <template slot="prepend">
                  <span style="font-size: 18px;">{{scope.row.icon}}</span>
                </template>
              </el-input>
            </template>
          </el-table-column>

          <el-table-column prop="link" label="链接" min-width="120" header-align="center" :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
            <template slot-scope="scope">
              <el-input v-model="scope.row.link" placeholder="/"></el-input>
            </template>
          </el-table-column>

          <el-table-column prop="type" label="类型" min-width="110" header-align="center" :class-name="isMobileDevice ? 'hidden-xs-only' : ''">
            <template slot-scope="scope">
              <el-select v-model="scope.row.type" placeholder="请选择" style="width: 100%">
                <el-option label="普通链接" value="internal"></el-option>
                <el-option label="下拉菜单" value="dropdown"></el-option>
                <el-option label="特殊功能" value="special"></el-option>
              </el-select>
            </template>
          </el-table-column>

          <el-table-column prop="enabled" label="启用" width="80" align="center">
            <template slot-scope="scope">
              <el-switch v-model="scope.row.enabled"></el-switch>
            </template>
          </el-table-column>

          <el-table-column prop="operation" label="操作" :width="isMobileDevice ? 84 : 176" header-align="center" fixed="right">
            <template slot-scope="scope">
              <div class="nav-op-cell">
                <el-button type="text" size="small" :disabled="scope.$index === 0" @click="moveNavItem(scope.$index, 'up')" class="nav-op-btn">
                  <i class="el-icon-top"></i> <span class="nav-op-text">上移</span>
                </el-button>
                <el-button type="text" size="small" :disabled="scope.$index === navItems.length - 1" @click="moveNavItem(scope.$index, 'down')" class="nav-op-btn">
                  <i class="el-icon-bottom"></i> <span class="nav-op-text">下移</span>
                </el-button>
                <el-button type="text" size="small" class="delete-btn nav-op-btn" @click="deleteNavItem(scope.$index)">
                  <i class="el-icon-delete"></i> <span class="nav-op-text">删除</span>
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 移动设备提示面板 -->
      <div v-if="isMobileDevice" class="mobile-view-notice">
        <div style="margin: 10px 0; padding: 8px 12px; background: #f0f9ff; border-radius: 3px; font-size: 13px;">
          <i class="el-icon-mobile" style="color: #409EFF; margin-right: 6px;"></i>
          <span style="color: #606266;">在移动设备上点击表格行可查看完整信息</span>
        </div>
      </div>

      <!-- 导航栏预览 -->
      <div style="margin-top: 20px; padding: 15px; background: #f5f7fa; border-radius: 4px;">
        <div style="margin-bottom: 10px; font-weight: bold; color: #606266;">
          <i class="el-icon-view"></i> 导航栏预览：
        </div>
        <div class="nav-preview-container">
          <div
            v-for="(item, index) in navItems.filter(i => i.enabled)"
            :key="index"
            class="nav-item-preview">
            <span class="nav-item-icon">{{item.icon}}</span>
            <span class="nav-item-name">{{item.name}}</span>
            <span v-if="item.type === 'dropdown'" class="nav-item-dropdown">▼</span>
          </div>
        </div>
      </div>

      <div style="margin-top: 37px; text-align: center;">
        <el-button type="success" size="small" @click="addNavItem">添加导航项</el-button>
      </div>
      <div style="margin-top: 10px; margin-bottom: 22px; text-align: center;">
        <el-button type="warning" @click="resetToDefaultNav">恢复默认</el-button>
        <el-button type="primary" @click="saveNavConfig" :loading="navLoading">保存导航栏配置</el-button>
      </div>

      <!-- 导航项配置详情对话框（移动端） -->
      <el-dialog
        title="导航项配置详情"
        :visible.sync="navDetailDialogVisible"
        width="90%"
        :close-on-click-modal="false">
        <el-form v-if="currentNavItem" :model="currentNavItem" label-width="80px">
          <el-form-item label="名称">
            <el-input v-model="currentNavItem.name" placeholder="导航项名称"></el-input>
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="currentNavItem.icon" placeholder="例如: 🏡">
              <template slot="prepend">
                <span style="font-size: 20px;">{{currentNavItem.icon}}</span>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="链接">
            <el-input v-model="currentNavItem.link" placeholder="例如: /"></el-input>
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="currentNavItem.type" placeholder="请选择" style="width: 100%">
              <el-option label="普通链接" value="internal"></el-option>
              <el-option label="下拉菜单" value="dropdown"></el-option>
              <el-option label="特殊功能" value="special"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="currentNavItem.enabled"></el-switch>
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="navDetailDialogVisible = false">确 定</el-button>
        </span>
      </el-dialog>
    </el-card>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';
import { useMainStore } from '@/stores/main';

export default {
  name: 'NavSettings',
  components: { SectionTag },
  props: {
    webInfoId: { type: [Number, String], default: null },
    navConfig: { type: String, default: '' }
  },
  data() {
    return {
      navItems: [],
      navLoading: false,
      navDetailDialogVisible: false,
      currentNavItem: null,
      currentNavItemIndex: -1,
      isMobileDevice: false,
      touchStartX: 0,
      touchStartY: 0,
      touchStartTime: 0,
      isSwipeGesture: false,
      defaultNavItems: [
        { name: "首页", icon: "🏡", link: "/", type: "internal" },
        { name: "分类", icon: "📑", link: "#", type: "dropdown" },
        { name: "家", icon: "❤️‍🔥", link: "/love", type: "internal" },
        { name: "友人帐", icon: "🤝", link: "/friends", type: "internal" },
        { name: "曲乐", icon: "🎵", link: "/music", type: "internal" },
        { name: "收藏夹", icon: "📁", link: "/favorites", type: "internal" },
        { name: "留言", icon: "📪", link: "/message", type: "internal" },
        { name: "联系我", icon: "💬", link: "#chat", type: "special" }
      ]
    }
  },
  computed: {
    mainStore() {
      return useMainStore();
    }
  },
  watch: {
    navConfig: {
      handler(val) {
        this.parseNavConfig(val);
      },
      immediate: true
    }
  },
  mounted() {
    this.checkDeviceType();
    window.addEventListener('resize', this.checkDeviceType);
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.checkDeviceType);
  },
  methods: {
    parseNavConfig(configStr) {
      if (configStr) {
        try {
          const parsed = JSON.parse(configStr);
          if (Array.isArray(parsed) && parsed.length > 0) {
            this.navItems = parsed;
            return;
          }
        } catch (e) {
          console.error('解析导航栏配置失败:', e);
        }
      }
      // 使用默认导航项
      this.navItems = this.defaultNavItems.map((item, index) => ({
        ...item,
        order: index + 1,
        enabled: true
      }));
    },
    saveNavConfig() {
      this.navLoading = true;
      const navItems = this.navItems.map((item, index) => ({
        ...item,
        order: index + 1
      }));
      const param = {
        id: this.webInfoId,
        navConfig: JSON.stringify(navItems)
      };
      this.$http.post(this.$constant.baseURL + "/webInfo/updateWebInfo", param, true)
        .then(() => {
          this.$message({ message: "导航栏配置保存成功", type: "success" });
          // 更新store中的配置
          this.mainStore.webInfo.navConfig = JSON.stringify(navItems);
          this.$emit('saved');
          this.mainStore.loadWebInfo(this.mainStore.webInfo);
        })
        .catch((error) => {
          this.$message({ message: "保存导航栏配置失败: " + error.message, type: "error" });
        })
        .finally(() => {
          this.navLoading = false;
        });
    },
    addNavItem() {
      this.navItems.push({
        name: "新导航",
        icon: "🔗",
        link: "/",
        type: "internal",
        enabled: true
      });
    },
    deleteNavItem(index) {
      this.$confirm('确定要删除这个导航项吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.navItems.splice(index, 1);
        this.$message({ message: '删除成功', type: 'success' });
      }).catch(() => {});
    },
    moveNavItem(index, direction) {
      if (direction === 'up' && index > 0) {
        const temp = this.navItems[index];
        this.$set(this.navItems, index, this.navItems[index - 1]);
        this.$set(this.navItems, index - 1, temp);
      } else if (direction === 'down' && index < this.navItems.length - 1) {
        const temp = this.navItems[index];
        this.$set(this.navItems, index, this.navItems[index + 1]);
        this.$set(this.navItems, index + 1, temp);
      }
    },
    resetToDefaultNav() {
      this.navItems = this.defaultNavItems.map((item, index) => ({
        ...item,
        order: index + 1,
        enabled: true
      }));
    },
    handleNavRowClick(row, column, event) {
      if (this.isMobileDevice && column.property !== 'operation') {
        const index = this.navItems.indexOf(row);
        this.currentNavItem = row;
        this.currentNavItemIndex = index;
        this.navDetailDialogVisible = true;
      }
    },
    handleTouchStart(event) {
      if (event.touches && event.touches.length > 0) {
        this.touchStartX = event.touches[0].clientX;
        this.touchStartY = event.touches[0].clientY;
        this.touchStartTime = Date.now();
        this.isSwipeGesture = false;
      }
    },
    handleTouchEnd(event) {
      if (event.changedTouches && event.changedTouches.length > 0) {
        const touchEndX = event.changedTouches[0].clientX;
        const touchEndY = event.changedTouches[0].clientY;
        const deltaX = Math.abs(touchEndX - this.touchStartX);
        const deltaY = Math.abs(touchEndY - this.touchStartY);
        const duration = Date.now() - this.touchStartTime;
        if (deltaX > 30 || deltaY > 30 || duration > 500) {
          this.isSwipeGesture = true;
        }
      }
    },
    checkDeviceType() {
      this.isMobileDevice = window.innerWidth <= 768 || this.isMobile();
    },
    isMobile() {
      return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
    }
  }
}
</script>

<style scoped>
.responsive-table-container {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.delete-btn { color: #F56C6C !important; }
.delete-btn:hover { color: #f78989 !important; }

/* 导航栏预览样式 */
.nav-preview-section {
  margin-top: 15px;
  border-top: 1px solid #EBEEF5;
  padding-top: 15px;
}

.nav-preview-title {
  font-weight: bold;
  margin-bottom: 10px;
}

.nav-preview-container {
  background-color: rgba(0,0,0,0.7);
  padding: 8px 15px;
  border-radius: 4px;
  display: flex;
  /* 预览模拟的是PC横向导航栏（移动端前台是抽屉侧边栏），禁止换行竖排，窄屏横向滚动 */
  flex-wrap: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  min-height: 40px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  margin-bottom: 5px;
  align-items: center;
}

.nav-item-preview {
  margin-right: 15px;
  color: white;
  padding: 5px 0;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  white-space: nowrap;
  cursor: pointer;
  position: relative;
  transition: background-color 0.3s ease, border-color 0.3s ease, transform 0.3s ease;
  transform: translateZ(0);
}

.nav-item-preview:hover {
  color: var(--themeBackground, #409EFF);
  transform: translateY(-2px);
}

.nav-item-preview:hover::after {
  content: "";
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 2px;
  background-color: var(--themeBackground, #409EFF);
  animation: navItemHover 0.3s ease-in-out;
}

@keyframes navItemHover {
  from { width: 0; }
  to { width: 100%; }
}

.nav-item-icon {
  margin-right: 5px;
  font-size: 16px;
}

.nav-item-name {
  font-size: 14px;
}

.nav-item-dropdown {
  font-size: 12px;
  margin-left: 3px;
}

.mobile-view-notice {
  margin-top: 5px;
}

.nav-op-cell {
  white-space: nowrap;
}

.nav-op-btn {
  padding-left: 3px !important;
  padding-right: 3px !important;
}

.nav-op-btn + .nav-op-btn {
  margin-left: 2px !important;
}

@media screen and (max-width: 768px) {
  .mobile-table .hidden-xs-only { display: none !important; }
  .nav-op-btn .nav-op-text { display: none !important; }
}

@media screen and (max-width: 500px) {
  .nav-preview-container { gap: 8px; }
  .nav-item-preview { padding: 4px 8px; font-size: 12px; }
}
</style>
