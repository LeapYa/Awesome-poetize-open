<template>
  <div>
    <div>
      <div v-if="routeSearchDisplayKeyword" style="margin-bottom: 12px; padding: 10px 14px; border-radius: 8px; background: #f4f8ff; color: #606266; display: flex; align-items: center; justify-content: space-between; gap: 12px;">
        <span>当前显示的是全局搜索结果：{{ routeSearchDisplayKeyword }}</span>
        <el-button type="text" @click="clearGlobalSearchFilter">清除全局筛选</el-button>
      </div>
      <div class="handle-box">
        <el-select clearable v-model="pagination.resourceType" placeholder="资源类型" class="handle-select mrb10">
          <el-option key="21" label="Video.Article" value="video/article"></el-option>
          <el-option key="20" label="公共资源" value="assets"></el-option>
          <el-option key="10" label="表情包" value="internetMeme"></el-option>
          <el-option key="1" label="用户头像" value="userAvatar"></el-option>
          <el-option key="2" label="文章封面" value="articleCover"></el-option>
          <el-option key="3" label="文章图片" value="articlePicture"></el-option>
          <el-option key="5" label="网站头像" value="webAvatar"></el-option>
          <el-option key="4" label="背景图片" value="webBackgroundImage"></el-option>
          <el-option key="6" label="随机头像" value="randomAvatar"></el-option>
          <el-option key="7" label="随机封面" value="randomCover"></el-option>
          <el-option key="8" label="画笔图片" value="graffiti"></el-option>
          <el-option key="9" label="评论图片" value="commentPicture"></el-option>
          <el-option key="11" label="聊天群头像" value="im/groupAvatar"></el-option>
          <el-option key="12" label="群聊天图片" value="im/groupMessage"></el-option>
          <el-option key="13" label="朋友聊天图片" value="im/friendMessage"></el-option>
          <el-option key="14" label="音乐声音" value="funnyUrl"></el-option>
          <el-option key="15" label="音乐封面" value="funnyCover"></el-option>
          <el-option key="16" label="Love.Cover" value="love/bgCover"></el-option>
          <el-option key="17" label="Love.Man" value="love/manCover"></el-option>
          <el-option key="18" label="Love.Woman" value="love/womanCover"></el-option>
          <el-option key="19" label="收藏夹封面" value="favoritesCover"></el-option>
        </el-select>
        <el-button type="primary" icon="el-icon-search" @click="search()">搜索</el-button>
        <el-button type="primary" @click="addResources()">新增资源</el-button>
      </div>
      <el-table :data="displayedResources" border class="table" header-cell-class-name="table-header">
        <el-table-column prop="id" label="ID" width="55" align="center"></el-table-column>
        <el-table-column prop="originalName" label="名称" align="center"></el-table-column>
        <el-table-column label="预览" width="90" align="center">
          <template slot-scope="scope">
            <el-image v-if="isImageResource(scope.row)"
                      lazy
                      :preview-src-list="[getResourceUrl(scope.row)]"
                      class="table-td-thumb"
                      :src="getResourceUrl(scope.row)"
                      fit="cover">
              <div slot="error" class="resource-preview-placeholder broken-image-fallback broken-image-thumb">
                <svg t="1777276969044" class="broken-image-svg" viewBox="0 0 1024 1024" version="1.1"
                     xmlns="http://www.w3.org/2000/svg" p-id="2591" width="200" height="200"
                     aria-hidden="true">
                  <path :d="brokenImagePath" p-id="2592"></path>
                </svg>
              </div>
            </el-image>
            <button v-else-if="isVideoResource(scope.row)"
                    type="button"
                    class="video-preview-thumb"
                    @click="previewMedia(getResourceUrl(scope.row), scope.row.mimeType, scope.row.originalName)">
              <video :src="getResourceUrl(scope.row)"
                     muted
                     playsinline
                     preload="metadata"
                     @loadedmetadata="handleVideoThumbnailLoaded">
              </video>
              <span class="video-preview-play">
                <i class="el-icon-caret-right"></i>
              </span>
            </button>
            <el-button v-else-if="isPreviewableResource(scope.row)"
                       type="text"
                       class="resource-preview-button"
                       @click="previewMedia(getResourceUrl(scope.row), scope.row.mimeType, scope.row.originalName)">
              <i :class="getPreviewIcon(scope.row)"></i>
            </el-button>
            <div v-else class="resource-preview-placeholder">
              <i class="el-icon-document"></i>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" align="center"></el-table-column>
        <el-table-column prop="type" label="资源类型" align="center"></el-table-column>
        <el-table-column label="状态" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === false ? 'danger' : 'success'"
                    disable-transitions>
              {{scope.row.status === false ? '禁用' : '启用'}}
            </el-tag>
            <el-switch @click.native="changeStatus(scope.row)" v-model="scope.row.status"></el-switch>
          </template>
        </el-table-column>
        <el-table-column label="资源URL" align="center">
          <template slot-scope="scope">
            <div style="display: flex; align-items: center; justify-content: center;">
              <el-tooltip :content="getResourceUrl(scope.row)" placement="top">
                <span style="max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                  {{getResourceUrl(scope.row)}}
                </span>
              </el-tooltip>
              <template v-if="isPreviewableResource(scope.row)">
                <el-button type="text" icon="el-icon-view" size="mini" style="margin-left: 5px;"
                           @click="previewMedia(getResourceUrl(scope.row), scope.row.mimeType, scope.row.originalName)">
                </el-button>
              </template>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="大小(KB)" align="center">
          <template slot-scope="scope">
            {{Math.round(scope.row.size / 1024)}}
          </template>
        </el-table-column>
        <el-table-column prop="mimeType" label="类型" align="center"></el-table-column>
        <el-table-column prop="storeType" label="存储平台" align="center"></el-table-column>
        <el-table-column label="创建时间" align="center">
          <template slot-scope="scope">
            {{ formatDateTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template slot-scope="scope">
            <el-button type="text" icon="el-icon-download"
                       @click="downloadResource(scope.row)">
              下载
            </el-button>
            <el-button type="text" icon="el-icon-delete" style="color: var(--orangeRed)"
                       @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!routeSearchDisplayKeyword" class="pagination">
        <el-pagination background layout="total, prev, pager, next"
                       :current-page="pagination.current"
                       :page-size="pagination.size"
                       :total="pagination.total"
                       @current-change="handlePageChange">
        </el-pagination>
      </div>
    </div>

    <el-dialog title="文件"
               :visible.sync="resourceDialog"
               width="25%"
               custom-class="centered-dialog"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <div style="display: flex;margin-bottom: 10px">
          <div style="line-height: 40px">存储平台：</div>
          <el-select v-model="storeType" placeholder="存储平台" style="width: 120px">
            <el-option
              v-for="(item, i) in storeTypes"
              :key="i"
              :label="item.label"
              :value="item.value">
            </el-option>
          </el-select>
        </div>
        <uploadPicture :isAdmin="true" :prefix="pagination.resourceType" @addPicture="addFile"
                       :storeType="storeType"
                       :listType="'text'" :accept="'image/*, video/*, audio/*'"
                       :maxSize="100" :maxNumber="10"></uploadPicture>
      </div>
    </el-dialog>

    <!-- 媒体预览对话框 -->
    <el-dialog :title="getPreviewTitle()"
               :visible.sync="previewVisible"
               :width="isPreviewingFont() ? '80%' : '60%'"
               custom-class="centered-dialog"
               :append-to-body="true"
               :close-on-click-modal="true"
               destroy-on-close
               :before-close="handlePreviewClose"
               center>
      <div style="text-align: center;">
        <!-- 图片预览（支持放大） -->
        <el-image v-if="previewMediaType.includes('image')" 
                  :src="previewMediaUrl" 
                  :preview-src-list="[previewMediaUrl]"
                  fit="contain"
                  style="max-width: 100%; max-height: 60vh; cursor: pointer;">
          <div slot="error" class="broken-image-fallback broken-image-dialog">
            <svg t="1777276969044" class="broken-image-svg" viewBox="0 0 1024 1024" version="1.1"
                 xmlns="http://www.w3.org/2000/svg" p-id="2591" width="200" height="200"
                 aria-hidden="true">
              <path :d="brokenImagePath" p-id="2592"></path>
            </svg>
            <div class="broken-image-text">图片加载失败</div>
          </div>
        </el-image>
        
        <!-- 视频预览 -->
        <video v-else-if="previewMediaType.includes('video')" 
               :src="previewMediaUrl" 
               controls 
               style="max-width: 100%; max-height: 60vh;">
          您的浏览器不支持视频播放
        </video>
        
        <!-- 字体预览 -->
        <div v-else-if="isPreviewingFont()" style="text-align: left;">
          <div class="font-info" style="margin-bottom: 20px; padding: 15px; background: #f5f7fa; border-radius: 4px;">
            <h3 style="margin: 0 0 10px 0; color: #409EFF;">{{ previewFileName }}</h3>
            <p style="margin: 0; color: #666;">字体文件加载完成后展示真实字体效果</p>
          </div>
          
          <div v-if="fontLoadFailed" style="padding: 40px; text-align: center; color: #909399;">
            <i class="el-icon-warning-outline" style="font-size: 24px; margin-bottom: 10px;"></i>
            <p>字体文件加载失败，无法预览</p>
            <p style="font-size: 12px; word-break: break-all;">{{ previewMediaUrl }}</p>
          </div>

          <div v-else-if="fontLoaded" class="font-preview-content">
            <div v-for="textGroup in fontPreviewTexts" :key="textGroup.label" style="margin-bottom: 25px;">
              <h4 style="color: #606266; margin: 0 0 10px 0; font-size: 14px;">{{ textGroup.label }}</h4>
              <div v-for="size in fontSizes" :key="size" 
                   :style="{ 
                     fontFamily: '\'' + loadedFontName + '\', Arial, sans-serif',
                     fontSize: size + 'px',
                     lineHeight: 1.4,
                     margin: '8px 0',
                     padding: '5px',
                     border: '1px solid #eee',
                     borderRadius: '3px',
                     background: '#fff'
                   }"
                   class="font-sample">
                <span style="font-size: 12px; color: #999; margin-right: 10px;">{{ size }}px:</span>
                {{ textGroup.content }}
              </div>
            </div>
          </div>
          
          <div v-else style="padding: 40px; text-align: center;">
            <i class="el-icon-loading" style="font-size: 24px; margin-bottom: 10px;"></i>
            <p>正在加载字体文件...</p>
          </div>
        </div>
        
        <!-- 其他文件类型提示 -->
        <div v-else style="padding: 20px; color: #666;">
          <i class="el-icon-document" style="font-size: 48px; margin-bottom: 10px;"></i>
          <p>暂不支持预览此文件类型</p>
          <p>文件路径：{{ previewMediaUrl }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { useMainStore } from '@/stores/main';

const uploadPicture = () => import('../common/uploadPicture');

function normalizeSearchText(value) {
  return ((value || '') + '').toLowerCase().replace(/\s+/g, '').trim();
}

const BROKEN_IMAGE_PATH = 'M467.29044878222226 567.057805767111c-19.514205677037037 18.187840777481483-40.698786853925924 34.18618690370371-63.55126044444444 48.00497193718519-22.85247237688889 13.81878503348148-50.29506192118518 22.987841839407405-82.32652708977777 27.508410747259255-20.759846001777774 2.9321607395555556-39.990896109037045 2.771953133037037-57.69314789451852-0.47565300622222223-17.69728439940741-3.2500904391111116-33.790015715555555-7.937077096296296-48.2645321197037-14.065926144-14.47699949037037-6.122640118518518-27.257543793777774-13.091023530666666-38.34287566696296-20.906392993185186-11.086572202666666-7.815369462518518-20.10039000177778-14.866961749333335-27.037725127111113-21.148565503999997l-44.983393393777774-318.6045286020741-0.12543347674074073-0.8867275662222222c-0.9215004823703703-6.526261778962963 0.6371021558518518-12.645176054518519 4.66835744237037-18.36170899911111 4.036222672592592-5.712808315259258 9.60993568237037-9.069703016296296 16.726107629037035-10.074412373333333l284.05446155377774-40.10515205688889 16.313791525925925-83.09030426548149L75.13973039407404 164.49239024829632c-19.576302250666664 2.7645026607407406-34.30665178074074 11.955913310814815-44.20471284622222 27.571748863999996-9.893092465777778 15.61956139614815-13.33319604148148 34.10546232888889-10.317827640888888 55.46391172740741l91.97868858785185 651.4513358696296c2.9321607395555556 20.766056144592593 12.415421402074074 37.58282312059259 28.44729890133333 50.44781784177778 16.035603342222224 12.862510421333333 33.24729874962962 18.00031194074074 51.63384710637037 15.402225815703703l300.8252780847407-42.47224198637037 67.39001829451851-263.35174231229627-75.07250259437038-109.86101873777778C479.70711294103705 555.2571314631111 473.5372796207407 561.2369516468149 467.29044878222226 567.057805767111zM316.26119911348155 287.9362292242963c-8.462406390518517-6.371022772148148-18.19653400651852-11.048073443555555-29.203624391111106-14.033636314074073-11.009573470814816-2.9818370275555557-22.150790561185186-3.6797930002962955-33.41992542814815-2.088900532148148-11.26789211022222 1.5908924681481482-21.631670196148146 5.3253303561481475-31.08885117155555 11.197103521185184-9.457180975407406 5.875499008-17.36693562785185 13.043830328888887-23.731748257185185 21.506236719407408-6.368538472296296 8.461164847407407-11.065459901629628 18.049988228740737-14.090764287999999 28.765228600888886-3.022820086518519 10.715240372148148-3.7418895739259255 21.70991168474074-2.150995892148148 32.98152963792593 1.6753428859259256 11.866495506962963 5.433377374814815 22.380545213629627 11.261683181037037 31.542150333629625 5.830790106074074 9.157879277037038 12.997879883851851 17.076327158518517 21.503753633185188 23.737958399999993 8.50090636325926 6.669081713777778 18.086003901629628 11.36724589985185 28.757776914962964 14.09200583111111 10.67053147022222 2.7309700740740737 21.642847725037033 3.29976794074074 32.91073983525926 1.7076327158518518 23.134387617185183-3.2662353540740745 41.72336696888889-14.058474458074073 55.77438974103704-32.38168469807407 14.051022772148146-18.323210239999998 19.442175544888887-39.04952486874074 16.174697434074073-62.191362958222214-1.5908924681481482-11.270376410074075-5.322846056296296-21.637880338962958-11.197103521185184-31.09630285748148C331.89069649540744 302.21700664888886 324.7248482607407 294.3022846103704 316.26119911348155 287.9362292242963zM985.1167278269629 138.8518186097778c-12.879898093037037-17.08005300148148-34.592292864-25.61697503762963-65.14463721244444-25.61697503762963L617.2963610927407 113.23484357214818l-27.77045522962963 79.99421129007408L897.5083377398519 193.2290548622222c7.189444835555555 0 13.328228655407408 2.5459255371851848 18.42007972977778 7.637775397925925 5.089366774518519 5.0955757037037035 7.637775397925925 10.937543338666666 7.637775397925925 17.52962632059259l0 186.9441815893333c-42.53309580325926 1.7995348195555556-79.67131299081481 8.24134618074074-111.42583030518517 19.325435297185187-31.747065628444442 11.086572202666666-60.052786138074076 24.570041078518518-84.91343568592592 40.44792111407408-24.858165247999995 15.874154192592592-47.77273419851852 32.95420719407407-68.7374954951111 51.22773993244445-14.259665123555555 12.426598930962964-28.729212928 24.142821603555554-43.40119415466666 35.18468611792593l58.97852973511111 119.27721544059258-103.54712393955556 251.34491117037032 358.43521710459254 0c10.782303118222222 0 20.666702354962965-2.1025611472592587 29.650713410370372-6.291539740444445 8.988978441481482-4.1964302791111106 16.926055537777778-9.588823381333333 23.81495713185185-16.178423277037034 6.891384680296296-6.593325738666667 12.278810396444443-14.233584222814814 16.175938977185186-22.922020636444444 3.8934027377777776-8.685952113777777 5.83824057837037-17.224115693037035 5.83824057837037-25.61449073777778L1004.4347114382222 205.81213434311115C1004.4371957380743 178.24535407881478 997.99662592 155.92566268207412 985.1167278269629 138.8518186097778z';

export default {
  components: {
    uploadPicture
  },
  data() {
    return {
      pagination: {
        current: 1,
        size: 10,
        total: 0,
        resourceType: '',
        searchKey: ''
      },
      resources: [],
      resourceDialog: false,
      storeTypes: [
        { label: '服务器', value: 'local' },
        { label: '七牛云', value: 'qiniu' },
        { label: '兰空图床', value: 'lsky' },
        { label: '简单图床', value: 'easyimage' }
      ],
      brokenImagePath: BROKEN_IMAGE_PATH,
      storeType: 'local',
      previewMediaUrl: '',
      previewMediaType: '',
      previewFileName: '',
      previewVisible: false,
      fontLoaded: false,
      fontLoadFailed: false,
      loadedFontName: '',
      loadedFontFace: null,
      fontPreviewTexts: [
        { label: '英文大写', content: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' },
        { label: '英文小写', content: 'abcdefghijklmnopqrstuvwxyz' },
        { label: '数字', content: '0123456789' },
        { label: '中文示例', content: '床前明月光，疑是地上霜。举头望明月，低头思故乡。' },
        { label: '符号', content: '!@#$%^&*()_+-=[]{}|;:,.<>?' },
        { label: '英文句子', content: 'The quick brown fox jumps over the lazy dog.' }
      ],
      fontSizes: [14, 18, 24, 32, 48]
    };
  },

  computed: {
    mainStore() {
      return useMainStore();
    },
    routeSearchDisplayKeyword() {
      return ((this.$route.query.search || '') + '').trim();
    },
    routeSearchKeyword() {
      return normalizeSearchText(this.routeSearchDisplayKeyword);
    },
    filteredResources() {
      if (!this.routeSearchKeyword) {
        return this.resources;
      }
      return this.filterResourcesByKeyword(this.resources, this.routeSearchKeyword);
    },
    displayedResources() {
      return this.routeSearchKeyword ? this.filteredResources : this.resources;
    }
  },

  watch: {
    '$route.query': {
      immediate: true,
      handler() {
        this.applyRouteQuery();
        this.getResources();
      }
    }
  },

  created() {
    if (this.mainStore && this.mainStore.sysConfig && this.mainStore.sysConfig['store.type']) {
      this.storeType = this.mainStore.sysConfig['store.type'];
    }
  },

  beforeDestroy() {
    this.cleanupFont();
  },

  methods: {
    applyRouteQuery() {
      const query = this.$route.query || {};
      this.pagination.resourceType = query.resourceType || '';
      this.pagination.searchKey = ((query.search || '') + '').trim();
      this.pagination.current = 1;
    },
    filterResourcesByKeyword(resources, keyword) {
      if (!keyword) {
        return resources || [];
      }
      return (resources || []).filter((item) => {
        return [item.originalName, item.type, item.path, this.getResourceUrl(item), item.mimeType, item.storeType, String(item.id || ''), String(item.userId || '')]
          .some((value) => normalizeSearchText(value).includes(keyword));
      });
    },
    clearGlobalSearchFilter() {
      const nextQuery = { ...this.$route.query };
      delete nextQuery.search;
      delete nextQuery.resourceType;
      this.$router.replace({ path: this.$route.path, query: nextQuery });
    },
    handleDelete(item) {
      this.$confirm('确认删除资源？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success',
        center: true,
        customClass: 'mobile-responsive-confirm'
      }).then(() => {
        this.$http.post(this.$constant.baseURL + '/resource/deleteResource', { path: item.path }, true, false)
          .then(() => {
            this.pagination.current = 1;
            this.getResources();
            this.$message({
              message: '删除成功！',
              type: 'success'
            });
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: 'error'
            });
          });
      }).catch(() => {
        this.$message({
          type: 'success',
          message: '已取消删除!'
        });
      });
    },
    downloadResource(item) {
      const url = this.getResourceUrl(item);
      if (this.$common.isEmpty(url)) {
        this.$message({
          message: '资源URL为空，无法下载！',
          type: 'error'
        });
        return;
      }

      const link = document.createElement('a');
      link.href = url;
      link.download = item.originalName || this.getResourceFileName(item) || 'resource';
      link.style.display = 'none';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    },
    addFile() {
    },
    addResources() {
      if (this.$common.isEmpty(this.pagination.resourceType)) {
        this.$message({
          message: '请选择资源类型！',
          type: 'error'
        });
        return;
      }
      this.resourceDialog = true;
    },
    search() {
      this.pagination.total = 0;
      this.pagination.current = 1;
      this.getResources();
    },
    getResources() {
      const requestPagination = {
        current: this.routeSearchKeyword ? 1 : this.pagination.current,
        size: this.routeSearchKeyword ? 500 : this.pagination.size,
        resourceType: this.pagination.resourceType,
        searchKey: ''
      };

      this.$http.post(this.$constant.baseURL + '/resource/listResource', requestPagination, true)
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            const records = res.data.records || [];
            this.resources = records;
            this.pagination.total = this.routeSearchKeyword
              ? this.filterResourcesByKeyword(records, this.routeSearchKeyword).length
              : res.data.total;
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error'
          });
        });
    },
    changeStatus(item) {
      this.$http.get(this.$constant.baseURL + '/resource/changeResourceStatus', {
        id: item.id,
        flag: item.status
      }, true)
        .then(() => {
          this.$message({
            message: '修改成功！',
            type: 'success'
          });
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: 'error'
          });
        });
    },
    handlePageChange(val) {
      this.pagination.current = val;
      this.getResources();
    },
    previewMedia(mediaPath, mimeType, fileName) {
      this.previewMediaUrl = mediaPath;
      this.previewMediaType = mimeType || '';
      this.previewFileName = fileName || '';

      if (this.isFont(mimeType, fileName || mediaPath)) {
        this.loadFont(mediaPath);
      } else {
        this.fontLoaded = false;
      }

      this.previewVisible = true;
    },
    handleVideoThumbnailLoaded(event) {
      const video = event && event.target;
      if (!video || video.dataset.previewFrameReady === 'true') {
        return;
      }

      video.dataset.previewFrameReady = 'true';
      try {
        video.currentTime = Math.min(0.1, video.duration || 0);
      } catch (e) {
      }
    },
    isImageResource(resource) {
      const mimeType = ((resource && resource.mimeType) || '') + '';
      const fileName = this.getResourceFileName(resource);
      return mimeType.includes('image') || /\.(png|jpe?g|gif|svg|webp|bmp|avif|ico)(\?.*)?$/i.test(fileName);
    },
    isVideoResource(resource) {
      const mimeType = ((resource && resource.mimeType) || '') + '';
      const fileName = this.getResourceFileName(resource);
      return mimeType.includes('video') || /\.(mp4|webm|ogg|mov|m4v)(\?.*)?$/i.test(fileName);
    },
    isPreviewableResource(resource) {
      return this.isImageResource(resource) ||
        this.isVideoResource(resource) ||
        this.isFont(((resource && resource.mimeType) || '') + '', this.getResourceFileName(resource));
    },
    getPreviewIcon(resource) {
      if (this.isVideoResource(resource)) {
        return 'el-icon-video-play';
      }
      if (this.isFont(((resource && resource.mimeType) || '') + '', this.getResourceFileName(resource))) {
        return 'el-icon-edit-outline';
      }
      return 'el-icon-view';
    },
    getResourceFileName(resource) {
      if (!resource) {
        return '';
      }
      return (resource.originalName || resource.path || '') + '';
    },
    getResourceUrl(resource) {
      const path = ((resource && resource.path) || resource || '') + '';
      if (!path) {
        return '';
      }
      if (/^(https?:)?\/\//i.test(path) || /^(data|blob):/i.test(path)) {
        return path.startsWith('//') ? window.location.protocol + path : path;
      }
      if (path.startsWith('/')) {
        return window.location.origin + path;
      }

      const prefix = (this.mainStore &&
        this.mainStore.sysConfig &&
        this.mainStore.sysConfig.webStaticResourcePrefix) || '/static/';
      if (/^(https?:)?\/\//i.test(prefix)) {
        const normalizedPrefix = prefix.endsWith('/') ? prefix : prefix + '/';
        return normalizedPrefix + path.replace(/^\/+/, '');
      }
      const normalizedPrefix = prefix.startsWith('/') ? prefix : '/' + prefix;
      return window.location.origin +
        (normalizedPrefix.endsWith('/') ? normalizedPrefix : normalizedPrefix + '/') +
        path.replace(/^\/+/, '');
    },
    isFont(mimeType, fileName) {
      const normalizedMimeType = (mimeType || '') + '';
      const normalizedFileName = (fileName || this.previewFileName || this.previewMediaUrl || '') + '';
      const fontMimeTypes = [
        'font/woff', 'font/woff2', 'font/ttf', 'font/otf',
        'application/font-woff', 'application/font-woff2',
        'application/x-font-ttf', 'application/x-font-otf',
        'application/font-sfnt', 'font/opentype'
      ];
      return fontMimeTypes.some(type => normalizedMimeType.includes(type)) ||
        /\.(woff2?|ttf|otf|eot)(\?.*)?$/i.test(normalizedFileName);
    },
    isPreviewingFont() {
      return this.isFont(this.previewMediaType, this.previewFileName || this.previewMediaUrl);
    },
    getFontFormat(fileName) {
      const normalizedFileName = (fileName || '') + '';
      const match = normalizedFileName.match(/\.([a-z0-9]+)(?:\?.*)?$/i);
      if (!match) {
        return '';
      }
      const extension = match[1].toLowerCase();
      if (extension === 'woff2' || extension === 'woff' || extension === 'truetype' || extension === 'opentype') {
        return extension;
      }
      if (extension === 'ttf') {
        return 'truetype';
      }
      if (extension === 'otf') {
        return 'opentype';
      }
      return '';
    },
    getCssFontUrl(fontUrl) {
      return (fontUrl || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    },
    async loadFont(fontUrl) {
      this.cleanupFont();
      this.fontLoadFailed = false;

      const fontName = 'preview-font-' + Date.now();
      const fontFormat = this.getFontFormat(fontUrl || this.previewFileName);
      const cssFontUrl = this.getCssFontUrl(fontUrl);
      const fontSource = 'url("' + cssFontUrl + '")' + (fontFormat ? ' format("' + fontFormat + '")' : '');
      this.loadedFontName = fontName;

      try {
        if (window.FontFace && document.fonts && typeof document.fonts.add === 'function') {
          const fontFace = new FontFace(fontName, fontSource);
          this.loadedFontFace = fontFace;
          document.fonts.add(fontFace);
          await fontFace.load();
        } else {
          const style = document.createElement('style');
          style.id = 'font-preview-style';
          style.innerHTML = "\n          @font-face {\n            font-family: '" + fontName + "';\n            src: " + fontSource + ";\n          }\n        ";

          if (style && style.nodeType === Node.ELEMENT_NODE && document.head && typeof document.head.appendChild === 'function') {
            document.head.appendChild(style);
          }

          if (document.fonts && typeof document.fonts.load === 'function') {
            await document.fonts.load('16px "' + fontName + '"', 'The quick brown fox jumps over the lazy dog.');
          } else {
            await new Promise((resolve) => setTimeout(resolve, 300));
          }
        }

        if (this.loadedFontName === fontName) {
          this.fontLoaded = true;
        }
      } catch (e) {
        if (this.loadedFontName === fontName) {
          this.fontLoadFailed = true;
        }
      }
    },
    cleanupFont() {
      if (this.loadedFontFace && document.fonts && typeof document.fonts.delete === 'function') {
        try {
          document.fonts.delete(this.loadedFontFace);
        } catch (e) {
        }
      }
      const existingStyle = document.getElementById('font-preview-style');
      if (existingStyle) {
        existingStyle.remove();
      }
      this.fontLoaded = false;
      this.fontLoadFailed = false;
      this.loadedFontName = '';
      this.loadedFontFace = null;
    },
    getPreviewTitle() {
      if (this.previewMediaType.includes('image')) {
        return '图片预览（点击图片可放大）';
      }
      if (this.previewMediaType.includes('video')) {
        return '视频预览';
      }
      if (this.isPreviewingFont()) {
        return '字体预览';
      }
      return '文件预览';
    },
    handlePreviewClose(done) {
      this.cleanupFont();
      done();
    },
    formatDateTime(dateTime) {
      if (!dateTime) return '-';
      const date = new Date(dateTime);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      return year + '-' + month + '-' + day + ' ' + hours + ':' + minutes + ':' + seconds;
    }
  }
};
</script>

<style scoped>

  .handle-box {
    margin-bottom: 20px;
  }

  .handle-select {
    width: 200px;
  }

  .table {
    width: 100%;
    font-size: 14px;
  }

  .mrb10 {
    margin-right: 10px;
    margin-bottom: 10px;
  }

  .table-td-thumb {
    display: block;
    margin: auto;
    width: 40px;
    height: 40px;
    border-radius: 4px;
    background: #f5f7fa;
    overflow: hidden;
  }

  .resource-preview-placeholder,
  .resource-preview-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    padding: 0;
    border-radius: 4px;
    background: #f5f7fa;
    color: #909399;
    font-size: 20px;
  }

  .resource-preview-button {
    border: 1px solid #ebeef5;
  }

  .resource-preview-button:hover {
    color: #409EFF;
    border-color: #c6e2ff;
    background: #ecf5ff;
  }

  .broken-image-fallback {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #a8abb2;
    background: #f5f7fa;
  }

  .broken-image-thumb {
    width: 40px;
    height: 40px;
  }

  .broken-image-dialog {
    width: min(360px, 100%);
    min-height: 220px;
    margin: auto;
    border: 1px dashed #dcdfe6;
    border-radius: 6px;
  }

  .broken-image-svg {
    width: 28px;
    height: 28px;
    fill: currentColor;
  }

  .broken-image-dialog .broken-image-svg {
    width: 72px;
    height: 72px;
  }

  .broken-image-text {
    margin-top: 10px;
    font-size: 14px;
    color: #909399;
  }

  .video-preview-thumb {
    position: relative;
    display: block;
    width: 56px;
    height: 40px;
    margin: auto;
    padding: 0;
    overflow: hidden;
    cursor: pointer;
    background: #303133;
    border: 1px solid #ebeef5;
    border-radius: 4px;
  }

  .video-preview-thumb video {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .video-preview-play {
    position: absolute;
    top: 50%;
    left: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    color: #fff;
    background: rgba(0, 0, 0, 0.55);
    border-radius: 50%;
    transform: translate(-50%, -50%);
  }

  .video-preview-play i {
    margin-left: 2px;
    font-size: 16px;
  }

  .pagination {
    margin: 20px 0;
    text-align: right;
  }

  .el-switch {
    margin: 5px;
  }

  .font-sample {
    /* 性能优化: 只监听颜色变化 */
    transition: color 0.2s ease, background-color 0.2s ease;
  }

  .font-sample:hover {
    border-color: #409EFF !important;
    box-shadow: 0 0 5px rgba(64, 158, 255, 0.3);
  }

  .font-preview-content {
    max-height: 70vh;
    overflow-y: auto;
  }

  .font-info h3 {
    display: flex;
    align-items: center;
  }

  .font-info h3::before {
    content: "🔤";
    margin-right: 8px;
    font-size: 18px;
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
    }

    /* 输入框移动端优化 */
    ::v-deep .el-input__inner {
      font-size: 16px !important;
      height: 44px !important;
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

    /* 对话框移动端优化 */
    ::v-deep .el-dialog {
      width: 95% !important;
      margin-top: 5vh !important;
    }

    ::v-deep .el-dialog__body {
      padding: 15px !important;
    }

    /* 搜索框移动端优化 */
    .handle-select {
      width: 100% !important;
      margin-bottom: 10px !important;
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
</style>



