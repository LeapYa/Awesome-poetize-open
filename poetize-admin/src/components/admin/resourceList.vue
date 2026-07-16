<template>
  <div>
    <div>
      <div v-if="routeSearchDisplayKeyword" style="margin-bottom: 12px; padding: 10px 14px; border-radius: 8px; background: #f4f8ff; color: #606266; display: flex; align-items: center; justify-content: space-between; gap: 12px;">
        <span>当前显示的是全局搜索结果：{{ routeSearchDisplayKeyword }}</span>
        <el-button type="text" @click="clearGlobalSearchFilter">清除全局筛选</el-button>
      </div>
      <div class="handle-box">
        <el-select clearable
                   filterable
                   v-model="pagination.resourceType"
                   placeholder="资源类型"
                   class="handle-select mrb10"
                   @change="handleResourceTypeChange">
          <el-option-group
            v-for="group in resourceTypeGroups"
            :key="group.label"
            :label="group.label">
            <el-option
              v-for="item in group.options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :title="item.description"
              style="height: auto; min-height: 40px; line-height: 18px; padding-top: 5px; padding-bottom: 5px;">
              <div class="resource-type-option">
                <span class="resource-type-option-label">{{ item.label }}</span>
                <span class="resource-type-option-desc">{{ item.description }}</span>
              </div>
            </el-option>
          </el-option-group>
        </el-select>
        <el-tooltip v-if="selectedResourceTypeDescription" :content="selectedResourceTypeDescription" placement="top">
          <i class="el-icon-question resource-type-help mrb10"></i>
        </el-tooltip>
        <el-button type="primary" icon="el-icon-search" @click="search()">搜索</el-button>
        <el-button type="primary" @click="addResources()">新增资源</el-button>
        <el-tooltip content="关闭后列表不自动加载缩略图或视频元数据，点击眼睛仍可按需预览" placement="top">
          <div class="resource-preview-toggle mrb10">
            <span>显示预览</span>
            <el-switch v-model="resourcePreviewEnabled"
                       @change="handleResourcePreviewChange">
            </el-switch>
          </div>
        </el-tooltip>
      </div>
      <ResourceMigrationDialog
        ref="resourceMigrationDialog"
        :selected-resources="selectedResources"
        :resource-type="loadedResourceType"
        :resource-type-label="loadedResourceType ? getResourceTypeLabel(loadedResourceType) : '全部资源'"
        :filter-scope-available="migrationFilterScopeAvailable"
        @task-created="handleMigrationTaskCreated"
        @task-finished="handleMigrationTaskFinished">
      </ResourceMigrationDialog>
      <ResourceBatchToolbar
        ref="resourceBatchToolbar"
        :selected-resources="selectedResources"
        :filter-scope-available="migrationFilterScopeAvailable"
        @clear="clearSelectedResources"
        @migrate="openMigrationDialog"
        @deleted="handleBatchDeleted">
      </ResourceBatchToolbar>
      <ResourceDetailDialog
        ref="resourceDetailDialog"
        @changed="handleResourceDetailChanged">
      </ResourceDetailDialog>
      <div v-if="scanTask.active"
           class="scan-progress-bar"
           :class="'scan-progress-bar--' + scanTask.status">
        <div class="scan-progress-bar__head">
          <i v-if="scanTask.status === 'PENDING' || scanTask.status === 'RUNNING'"
             class="el-icon-loading scan-progress-bar__icon"></i>
          <i v-else-if="scanTask.status === 'SUCCESS'" class="el-icon-success scan-progress-bar__icon"></i>
          <i v-else-if="scanTask.status === 'FAILED'" class="el-icon-error scan-progress-bar__icon"></i>
          <i v-else-if="scanTask.status === 'CANCELLED'" class="el-icon-warning scan-progress-bar__icon"></i>
          <span class="scan-progress-bar__title">
            {{ scanTaskTitle }}
          </span>
          <span class="scan-progress-bar__meta" v-if="scanTask.status === 'RUNNING' || scanTask.status === 'SUCCESS'">
            {{ scanTask.processed }} / {{ scanTask.total }}（{{ scanTask.progressPercent }}%）
            <span v-if="scanTask.status === 'SUCCESS'">· 命中 {{ scanTask.hitCount }} 条无效资源</span>
          </span>
          <span class="scan-progress-bar__meta" v-else-if="scanTask.status === 'FAILED'">
            检测失败：{{ scanTask.errorMessage || '未知错误' }}
          </span>
          <span class="scan-progress-bar__meta" v-else-if="scanTask.status === 'CANCELLED'">
            已取消
          </span>
          <el-button v-if="scanTask.status === 'PENDING' || scanTask.status === 'RUNNING'"
                     type="text"
                     size="mini"
                     class="scan-progress-bar__cancel"
                     @click="cancelScan">取消</el-button>
        </div>
        <el-progress v-if="scanTask.status === 'PENDING' || scanTask.status === 'RUNNING'"
                     :percentage="scanTask.progressPercent"
                     :status="scanTask.status === 'PENDING' ? 'warning' : undefined"
                     :show-text="false"
                     :stroke-width="6">
        </el-progress>
      </div>
      <div v-if="isOrphanResourceType" class="orphan-resource-warning">
        <i class="el-icon-warning"></i>
        <span>注意甄别：孤儿资源仅通过数据库引用关系检测，可能误报被前端代码、JSON 配置、默认素材占用的文件，也可能包含重新生成图标/封面后遗留的旧版本文件，删除前请仔细核对。</span>
      </div>
      <el-table ref="resourceTable"
                :data="displayedResources"
                row-key="id"
                border
                class="table"
                header-cell-class-name="table-header"
                v-loading="resourcesLoading"
                element-loading-text="正在检测资源，请稍候..."
                :default-sort="{ prop: pagination.order, order: pagination.desc ? 'descending' : 'ascending' }"
                @selection-change="handleSelectionChange"
                @sort-change="handleSortChange">
        <el-table-column
          type="selection"
          width="46"
          align="center"
          :reserve-selection="true"
          :selectable="canSelectResource">
        </el-table-column>
        <el-table-column prop="id" label="ID" width="52" align="center" sortable="custom" :sort-orders="tableSortOrders"></el-table-column>
        <el-table-column prop="originalName" label="名称" min-width="116" align="center" sortable="custom" :sort-orders="tableSortOrders"></el-table-column>
        <el-table-column label="预览" width="82" align="center">
          <template slot-scope="scope">
            <div v-if="!resourcePreviewEnabled" class="resource-preview-placeholder resource-preview-disabled">
              <i :class="getPreviewPlaceholderIcon(scope.row)"></i>
              <span v-if="getResourceDimensionText(scope.row)" class="resource-preview-dimension">
                {{ getResourceDimensionText(scope.row) }}
              </span>
            </div>
            <el-image v-else-if="isImageResource(scope.row)"
                      lazy
                      :preview-src-list="[getResourcePreviewUrl(scope.row)]"
                      class="table-td-thumb"
                      :src="getResourceListImageUrl(scope.row)"
                      fit="cover">
              <div slot="error" class="resource-preview-placeholder broken-image-fallback broken-image-thumb">
                <svg t="1777276969044" class="broken-image-svg" viewBox="0 0 1024 1024" version="1.1"
                     xmlns="http://www.w3.org/2000/svg" p-id="2591" width="200" height="200"
                     aria-hidden="true">
                  <path :d="brokenImagePath" p-id="2592"></path>
                </svg>
                <div class="broken-image-text">图片加载失败</div>
              </div>
            </el-image>
            <button v-else-if="isVideoResource(scope.row)"
                    type="button"
                    class="video-preview-thumb"
                    @click="previewMedia(getResourcePreviewUrl(scope.row), scope.row.mimeType, scope.row.originalName)">
              <video :src="getResourcePreviewUrl(scope.row)"
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
                       @click="previewMedia(getResourcePreviewUrl(scope.row), scope.row.mimeType, scope.row.originalName)">
              <i :class="getPreviewIcon(scope.row)"></i>
            </el-button>
            <div v-else class="resource-preview-placeholder">
              <i class="el-icon-document"></i>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="72" align="center" sortable="custom" :sort-orders="tableSortOrders"></el-table-column>
        <el-table-column prop="type" label="资源类型" width="92" align="center" sortable="custom" :sort-orders="tableSortOrders">
          <template slot-scope="scope">
            <el-tooltip
              :disabled="!getResourceTypeDescription(scope.row.type)"
              :content="getResourceTypeHelp(scope.row.type)"
              placement="top">
              <el-tag size="small" effect="plain">{{ getResourceTypeLabel(scope.row.type) }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="78" align="center" sortable="custom" :sort-orders="tableSortOrders">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === false ? 'danger' : 'success'"
                    disable-transitions>
              {{scope.row.status === false ? '禁用' : '启用'}}
            </el-tag>
            <el-switch @click.native="changeStatus(scope.row)" v-model="scope.row.status"></el-switch>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="资源URL" width="170" align="center" sortable="custom" :sort-orders="tableSortOrders">
          <template slot-scope="scope">
            <div class="resource-url-cell">
              <el-tooltip :content="getResourceUrl(scope.row)" placement="top">
                <span class="resource-url-text">
                  {{getResourceUrl(scope.row)}}
                </span>
              </el-tooltip>
              <template v-if="isPreviewableResource(scope.row)">
                <el-button type="text" icon="el-icon-view" size="mini" style="margin-left: 5px;"
                           @click="previewMedia(getResourcePreviewUrl(scope.row), scope.row.mimeType, scope.row.originalName)">
                </el-button>
              </template>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="size" label="大小(KB)" width="84" align="center" sortable="custom" :sort-orders="tableSortOrders">
          <template slot-scope="scope">
            {{Math.round(scope.row.size / 1024)}}
          </template>
        </el-table-column>
        <el-table-column prop="mimeType" label="类型" width="96" align="center" sortable="custom" :sort-orders="tableSortOrders"></el-table-column>
        <el-table-column prop="storeType" label="存储平台" width="88" align="center" sortable="custom" :sort-orders="tableSortOrders"></el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="146" align="center" sortable="custom" :sort-orders="tableSortOrders">
          <template slot-scope="scope">
            {{ formatDateTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center">
          <template slot-scope="scope">
            <el-button type="text" icon="el-icon-view"
                       @click="openResourceDetail(scope.row)">
              详情
            </el-button>
            <el-tooltip :disabled="canReplaceResource(scope.row)"
                        :content="getReplaceDisabledReason(scope.row)"
                        placement="top">
              <span>
                <el-button type="text"
                           icon="el-icon-refresh"
                           :loading="replaceResourceUploading && replaceResourceTarget && replaceResourceTarget.id === scope.row.id"
                           :disabled="replaceResourceUploading || !canReplaceResource(scope.row)"
                           @click="handleReplace(scope.row)">
                  替换
                </el-button>
              </span>
            </el-tooltip>
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
        <uploadPicture ref="resourceUploader"
                       :isAdmin="true"
                       :prefix="pagination.resourceType"
                       @addPicture="addFile"
                       @uploadStart="handleResourceUploadStart"
                       @uploadComplete="handleResourceUploadComplete"
                       :storeType="storeType"
                       :listType="'text'"
                       :accept="resourceUploadAccept"
                       :maxSize="100" :maxNumber="10"></uploadPicture>
      </div>
    </el-dialog>

    <el-dialog title="替换文件"
               :visible.sync="replaceResourceDialog"
               :width="replaceResourceDialogWidth"
               custom-class="centered-dialog"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               :before-close="handleReplaceDialogClose"
               center>
      <div>
        <div v-if="replaceResourceTarget" class="replace-resource-target">
          <div class="replace-resource-target-label">当前资源：</div>
          <el-tooltip :content="replaceResourceTarget.path" placement="top">
            <div class="replace-resource-target-path">{{ replaceResourceTarget.path }}</div>
          </el-tooltip>
        </div>
        <el-upload ref="replaceResourceUploader"
                   class="resource-replace-upload"
                   drag
                   action="#"
                   :auto-upload="false"
                   :http-request="noopReplaceUpload"
                   :file-list="replaceResourceFiles"
                   :limit="1"
                   :accept="replaceResourceAccept"
                   :disabled="replaceResourceUploading"
                   :before-upload="beforeReplaceUpload"
                   :on-change="handleReplaceUploadChange"
                   :on-remove="handleReplaceUploadRemove"
                   :on-exceed="handleReplaceUploadExceed">
          <div class="el-upload__text">
            <i class="el-icon-upload replace-upload-icon"></i>
            <div>拖拽上传 / 点击上传</div>
          </div>
          <div slot="tip" class="el-upload__tip">
            {{ replaceResourceUploadTip }}
          </div>
        </el-upload>

        <div v-if="replaceResourceVideoUrl" class="replace-video-frame-picker">
          <video ref="replaceFrameVideo"
                 :src="replaceResourceVideoUrl"
                 controls
                 preload="metadata"
                 @loadedmetadata="handleReplaceVideoLoadedMetadata"
                 @timeupdate="handleReplaceVideoTimeUpdate"
                 @seeked="handleReplaceVideoTimeUpdate"
                 @error="handleReplaceVideoError">
            您的浏览器不支持视频播放
          </video>
          <div class="replace-video-frame-meta">
            <span>当前时间：{{ formatReplaceFrameSecond(replaceResourceCurrentSecond) }}</span>
            <span v-if="replaceResourceFrameConfirmed" class="replace-video-frame-selected">
              已选择 {{ formatReplaceFrameSecond(replaceResourceFrameSecond) }}
            </span>
          </div>
          <el-button type="primary"
                     size="mini"
                     icon="el-icon-check"
                     :disabled="!replaceResourceVideoReady"
                     @click="useCurrentReplaceVideoFrame">
            使用当前帧
          </el-button>
        </div>

        <div class="replace-resource-actions">
          <el-button type="success"
                     style="font-size: 12px"
                     :loading="replaceResourceUploading"
                     :disabled="replaceResourceUploading"
                     @click="confirmReplaceResourceUpload">
            替换
          </el-button>
        </div>
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
const ResourceBatchToolbar = () => import('./ResourceBatchToolbar.vue');
const ResourceMigrationDialog = () => import('./ResourceMigrationDialog.vue');
const ResourceDetailDialog = () => import('./ResourceDetailDialog.vue');

const RESOURCE_TYPE_GROUPS = [
  {
    label: '筛选视图',
    options: [
      { label: '孤儿资源', value: 'orphanResource', description: '已入库但没有被页面或内容引用的文件；可能包含被前端代码、JSON 配置、默认素材占用的资源，或重新生成图标/封面后遗留的旧版本文件，删除前请仔细甄别' },
      { label: '无效资源', value: 'invalidResource', description: '路径无法访问或本地文件不存在的资源' }
    ]
  },
  {
    label: '文章内容',
    options: [
      { label: '文章封面', value: 'articleCover', description: '文章列表和详情页使用的封面图' },
      { label: '文章图片', value: 'articlePicture', description: '文章正文里插入的图片' },
      { label: '文章附件', value: 'articleFile', description: '文章正文里的下载附件或非图片文件' },
      { label: '文章视频', value: 'video/article', description: '文章页使用的视频文件' }
    ]
  },
  {
    label: '站点素材',
    options: [
      { label: '公共资源', value: 'assets', description: '站点内置或通用静态文件' },
      { label: '网站头像', value: 'webAvatar', description: '网站头像、站点标识相关图片' },
      { label: '背景图片', value: 'webBackgroundImage', description: '站点背景相关图片' },
      { label: '用户头像', value: 'userAvatar', description: '用户上传或使用的头像' },
      { label: '随机头像', value: 'randomAvatar', description: '随机头像池资源' },
      { label: '随机封面', value: 'randomCover', description: '随机文章封面池资源' },
      { label: '表情包', value: 'internetMeme', description: '评论或聊天使用的表情图' },
      { label: '画笔图片', value: 'graffiti', description: '涂鸦画笔相关图片' },
      { label: '评论图片', value: 'commentPicture', description: '评论区上传的图片' }
    ]
  },
  {
    label: '聊天素材',
    options: [
      { label: '聊天群头像', value: 'im/groupAvatar', description: '聊天群组头像' },
      { label: '群聊天图片', value: 'im/groupMessage', description: '群聊消息中的图片' },
      { label: '朋友聊天图片', value: 'im/friendMessage', description: '私聊消息中的图片' }
    ]
  },
  {
    label: '功能页面',
    options: [
      { label: '音乐声音', value: 'funnyUrl', description: '音乐页使用的音频文件' },
      { label: '音乐封面', value: 'funnyCover', description: '音乐页歌曲封面图' },
      { label: '表白墙背景', value: 'love/bgCover', description: '表白墙页面背景图' },
      { label: '表白墙男生封面', value: 'love/manCover', description: '表白墙男生卡片封面' },
      { label: '表白墙女生封面', value: 'love/womanCover', description: '表白墙女生卡片封面' },
      { label: '收藏夹封面', value: 'favoritesCover', description: '收藏夹卡片封面图' }
    ]
  }
];

const RESOURCE_TYPE_MAP = RESOURCE_TYPE_GROUPS
  .reduce((items, group) => items.concat(group.options), [])
  .reduce((map, item) => {
    map[item.value] = item;
    return map;
  }, {});

function normalizeSearchText(value) {
  return ((value || '') + '').toLowerCase().replace(/\s+/g, '').trim();
}

const APP_BASE_URL = import.meta.env.BASE_URL || '/';

function isRootPublicResourcePath(path) {
  return /^\/[^/?#]+\.[a-z0-9]+(?:[?#].*)?$/i.test(((path || '') + '').trim());
}

function getScopedPublicResourceUrl(path) {
  const normalizedBaseUrl = APP_BASE_URL.endsWith('/') ? APP_BASE_URL : APP_BASE_URL + '/';
  return new URL(path.replace(/^\/+/, ''), window.location.origin + normalizedBaseUrl).href;
}

const RESOURCE_UPLOAD_ACCEPT = [
  'image/*',
  'video/*',
  'audio/*',
  '.pdf',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.txt',
  '.md',
  '.markdown',
  '.json',
  '.csv',
  '.xml',
  '.zip',
  '.rar',
  '.7z',
  '.tar',
  '.gz',
  '.woff',
  '.woff2',
  '.ttf',
  '.otf',
  '.eot'
].join(',');

const BROKEN_IMAGE_PATH = 'M467.29044878222226 567.057805767111c-19.514205677037037 18.187840777481483-40.698786853925924 34.18618690370371-63.55126044444444 48.00497193718519-22.85247237688889 13.81878503348148-50.29506192118518 22.987841839407405-82.32652708977777 27.508410747259255-20.759846001777774 2.9321607395555556-39.990896109037045 2.771953133037037-57.69314789451852-0.47565300622222223-17.69728439940741-3.2500904391111116-33.790015715555555-7.937077096296296-48.2645321197037-14.065926144-14.47699949037037-6.122640118518518-27.257543793777774-13.091023530666666-38.34287566696296-20.906392993185186-11.086572202666666-7.815369462518518-20.10039000177778-14.866961749333335-27.037725127111113-21.148565503999997l-44.983393393777774-318.6045286020741-0.12543347674074073-0.8867275662222222c-0.9215004823703703-6.526261778962963 0.6371021558518518-12.645176054518519 4.66835744237037-18.36170899911111 4.036222672592592-5.712808315259258 9.60993568237037-9.069703016296296 16.726107629037035-10.074412373333333l284.05446155377774-40.10515205688889 16.313791525925925-83.09030426548149L75.13973039407404 164.49239024829632c-19.576302250666664 2.7645026607407406-34.30665178074074 11.955913310814815-44.20471284622222 27.571748863999996-9.893092465777778 15.61956139614815-13.33319604148148 34.10546232888889-10.317827640888888 55.46391172740741l91.97868858785185 651.4513358696296c2.9321607395555556 20.766056144592593 12.415421402074074 37.58282312059259 28.44729890133333 50.44781784177778 16.035603342222224 12.862510421333333 33.24729874962962 18.00031194074074 51.63384710637037 15.402225815703703l300.8252780847407-42.47224198637037 67.39001829451851-263.35174231229627-75.07250259437038-109.86101873777778C479.70711294103705 555.2571314631111 473.5372796207407 561.2369516468149 467.29044878222226 567.057805767111zM316.26119911348155 287.9362292242963c-8.462406390518517-6.371022772148148-18.19653400651852-11.048073443555555-29.203624391111106-14.033636314074073-11.009573470814816-2.9818370275555557-22.150790561185186-3.6797930002962955-33.41992542814815-2.088900532148148-11.26789211022222 1.5908924681481482-21.631670196148146 5.3253303561481475-31.08885117155555 11.197103521185184-9.457180975407406 5.875499008-17.36693562785185 13.043830328888887-23.731748257185185 21.506236719407408-6.368538472296296 8.461164847407407-11.065459901629628 18.049988228740737-14.090764287999999 28.765228600888886-3.022820086518519 10.715240372148148-3.7418895739259255 21.70991168474074-2.150995892148148 32.98152963792593 1.6753428859259256 11.866495506962963 5.433377374814815 22.380545213629627 11.261683181037037 31.542150333629625 5.830790106074074 9.157879277037038 12.997879883851851 17.076327158518517 21.503753633185188 23.737958399999993 8.50090636325926 6.669081713777778 18.086003901629628 11.36724589985185 28.757776914962964 14.09200583111111 10.67053147022222 2.7309700740740737 21.642847725037033 3.29976794074074 32.91073983525926 1.7076327158518518 23.134387617185183-3.2662353540740745 41.72336696888889-14.058474458074073 55.77438974103704-32.38168469807407 14.051022772148146-18.323210239999998 19.442175544888887-39.04952486874074 16.174697434074073-62.191362958222214-1.5908924681481482-11.270376410074075-5.322846056296296-21.637880338962958-11.197103521185184-31.09630285748148C331.89069649540744 302.21700664888886 324.7248482607407 294.3022846103704 316.26119911348155 287.9362292242963zM985.1167278269629 138.8518186097778c-12.879898093037037-17.08005300148148-34.592292864-25.61697503762963-65.14463721244444-25.61697503762963L617.2963610927407 113.23484357214818l-27.77045522962963 79.99421129007408L897.5083377398519 193.2290548622222c7.189444835555555 0 13.328228655407408 2.5459255371851848 18.42007972977778 7.637775397925925 5.089366774518519 5.0955757037037035 7.637775397925925 10.937543338666666 7.637775397925925 17.52962632059259l0 186.9441815893333c-42.53309580325926 1.7995348195555556-79.67131299081481 8.24134618074074-111.42583030518517 19.325435297185187-31.747065628444442 11.086572202666666-60.052786138074076 24.570041078518518-84.91343568592592 40.44792111407408-24.858165247999995 15.874154192592592-47.77273419851852 32.95420719407407-68.7374954951111 51.22773993244445-14.259665123555555 12.426598930962964-28.729212928 24.142821603555554-43.40119415466666 35.18468611792593l58.97852973511111 119.27721544059258-103.54712393955556 251.34491117037032 358.43521710459254 0c10.782303118222222 0 20.666702354962965-2.1025611472592587 29.650713410370372-6.291539740444445 8.988978441481482-4.1964302791111106 16.926055537777778-9.588823381333333 23.81495713185185-16.178423277037034 6.891384680296296-6.593325738666667 12.278810396444443-14.233584222814814 16.175938977185186-22.922020636444444 3.8934027377777776-8.685952113777777 5.83824057837037-17.224115693037035 5.83824057837037-25.61449073777778L1004.4347114382222 205.81213434311115C1004.4371957380743 178.24535407881478 997.99662592 155.92566268207412 985.1167278269629 138.8518186097778z';

const RESOURCE_PREVIEW_STORAGE_KEY = 'poetize.resource.preview.enabled';
const RESOURCE_THUMBNAIL_IMAGE_EXTENSIONS = ['jpg', 'png', 'bmp', 'gif'];
const RESOURCE_THUMBNAIL_MIME_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/bmp', 'image/gif'];

const REPLACE_IMAGE_UPLOAD_EXTENSIONS = ['jpg', 'png', 'webp', 'bmp', 'gif', 'apng', 'tif', 'tiff', 'svg'];
const REPLACE_ANIMATED_IMAGE_EXTENSIONS = ['gif', 'webp', 'apng'];
const REPLACE_VIDEO_EXTENSIONS = ['mp4', 'webm', 'mov', 'm4v', 'ogg', 'ogv'];
const REPLACE_WOFF2_UPLOAD_EXTENSIONS = ['woff2', 'ttf', 'otf'];
const REPLACE_IMAGE_FRAME_ACCEPT = [
  'image/*',
  'video/mp4',
  'video/webm',
  'video/quicktime',
  'video/ogg',
  '.jpg',
  '.jpeg',
  '.png',
  '.webp',
  '.bmp',
  '.gif',
  '.apng',
  '.tif',
  '.tiff',
  '.svg',
  '.mp4',
  '.webm',
  '.mov',
  '.m4v',
  '.ogg',
  '.ogv'
].join(',');
const REPLACE_MEDIA_ACCEPT = [
  'image/gif',
  'image/webp',
  'image/apng',
  'video/mp4',
  'video/webm',
  'video/quicktime',
  'video/ogg',
  '.gif',
  '.webp',
  '.apng',
  '.mp4',
  '.webm',
  '.mov',
  '.m4v',
  '.ogg',
  '.ogv'
].join(',');
const REPLACE_WOFF2_ACCEPT = [
  '.woff2',
  '.ttf',
  '.otf',
  'font/woff2',
  'font/ttf',
  'font/otf',
  'font/opentype',
  'application/font-sfnt',
  'application/x-font-ttf',
  'application/x-font-otf'
].join(',');

function readResourcePreviewEnabled() {
  try {
    const storedValue = window.localStorage.getItem(RESOURCE_PREVIEW_STORAGE_KEY);
    return storedValue === null ? true : storedValue === 'true';
  } catch (e) {
    return true;
  }
}

export default {
  components: {
    uploadPicture,
    ResourceBatchToolbar,
    ResourceMigrationDialog,
    ResourceDetailDialog
  },
  data() {
    return {
      pagination: {
        current: 1,
        size: 10,
        total: 0,
        resourceType: '',
        searchKey: '',
        order: 'createTime',
        desc: true
      },
      resources: [],
      resourceRequestSequence: 0,
      loadedResourceType: '',
      resourceContextLoaded: false,
      selectionContextKey: '',
      selectedResourcesById: new Map(),
      syncingTableSelection: false,
      resourceDialog: false,
      storeTypes: [
        { label: '服务器', value: 'local' },
        { label: '七牛云', value: 'qiniu' },
        { label: '兰空图床', value: 'lsky' },
        { label: '简单图床', value: 'easyimage' }
      ],
      resourceTypeGroups: RESOURCE_TYPE_GROUPS,
      brokenImagePath: BROKEN_IMAGE_PATH,
      tableSortOrders: ['descending', 'ascending'],
      resourcePreviewEnabled: readResourcePreviewEnabled(),
      storeType: 'local',
      resourceUploadTotal: 0,
      replaceResourceDialog: false,
      replaceResourceTarget: null,
      replaceResourceUploading: false,
      replaceResourceFiles: [],
      replaceResourceVideoUrl: '',
      replaceResourceVideoReady: false,
      replaceResourceVideoDuration: 0,
      replaceResourceCurrentSecond: 0,
      replaceResourceFrameSecond: null,
      replaceResourceFrameConfirmed: false,
      replaceResourceFrameFile: null,
      replaceResourceFileKind: '',
      replacePreviewCacheBusters: {},
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
      fontSizes: [14, 18, 24, 32, 48],
      resourcesLoading: false,
      scanTask: {
        active: false,
        taskId: '',
        status: '',
        resourceType: '',
        requestSequence: 0,
        total: 0,
        processed: 0,
        hitCount: 0,
        errorMessage: '',
        progressPercent: 0
      },
      scanPollTimer: null
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
    },
    selectedResources() {
      return Array.from(this.selectedResourcesById.values());
    },
    migrationFilterScopeAvailable() {
      return this.resourceContextLoaded &&
        !this.routeSearchKeyword &&
        this.loadedResourceType === this.pagination.resourceType;
    },
    selectedResourceTypeDescription() {
      return this.getResourceTypeDescription(this.pagination.resourceType);
    },
    isInvalidResourceType() {
      return this.pagination.resourceType === 'invalidResource';
    },
    isOrphanResourceType() {
      return this.pagination.resourceType === 'orphanResource';
    },
    scanTaskTitle() {
      if (this.scanTask.status === 'PENDING') {
        return '正在排队等待检测...';
      }
      if (this.scanTask.status === 'RUNNING') {
        return '正在检测无效资源（逐个探测可访问性，可能耗时）';
      }
      if (this.scanTask.status === 'SUCCESS') {
        return '检测完成';
      }
      if (this.scanTask.status === 'FAILED') {
        return '检测失败';
      }
      if (this.scanTask.status === 'CANCELLED') {
        return '检测已取消';
      }
      return '资源检测';
    },
    resourceUploadAccept() {
      return RESOURCE_UPLOAD_ACCEPT;
    },
    replaceResourceAccept() {
      return this.getReplaceAccept(this.replaceResourceTarget);
    },
    replaceResourceDialogWidth() {
      return this.replaceResourceVideoUrl ? '560px' : '25%';
    },
    replaceResourceUploadTip() {
      if (this.isReplaceImageConversionTarget(this.replaceResourceTarget)) {
        return '只能选择 1 个文件，可上传图片自动转换为 ' + this.getReplaceTargetFormatLabel(this.replaceResourceTarget) + '，GIF/WebP/APNG 会按静态首帧处理；也可上传 MP4/WebM/MOV 等浏览器可播放视频后选择一帧，且不超过 100M！';
      }
      if (this.isReplaceWoff2Target(this.replaceResourceTarget)) {
        return '只能选择 1 个文件，可上传 WOFF2 原样替换，或上传 TTF/OTF 自动转换为 WOFF2，且不超过 100M！';
      }
      if (this.isReplaceMediaTarget(this.replaceResourceTarget)) {
        return '只能选择 1 个文件，同格式直接替换；GIF/WebP/APNG 与 MP4/WebM 跨格式互转需服务器检测到媒体转换器，且不超过 100M！';
      }
      return '只能选择 1 个文件，文件扩展名需与原资源一致，且不超过 100M！';
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
    this.cleanupReplaceVideoFrame();
    this.stopScanPolling();
  },

  methods: {
    getSelectionContextKey(resourceType, searchKeyword) {
      return [resourceType || '', normalizeSearchText(searchKeyword)].join('::');
    },
    updateSelectionContext(options = {}) {
      const nextKey = this.getSelectionContextKey(
        this.pagination.resourceType,
        this.routeSearchKeyword
      );
      if (!this.selectionContextKey) {
        this.selectionContextKey = nextKey;
        return;
      }
      if (this.selectionContextKey === nextKey) {
        return;
      }

      const selectedCount = this.selectedResourcesById.size;
      this.selectionContextKey = nextKey;
      this.clearSelectedResources({ silent: true });
      if (selectedCount > 0 && options.notify !== false) {
        this.$message({
          message: '筛选条件已变化，已清空之前选择的 ' + selectedCount + ' 个资源',
          type: 'info'
        });
      }
    },
    handleResourceTypeChange() {
      this.resourceRequestSequence += 1;
      this.resourceContextLoaded = false;
      this.resetScanTask();
      this.updateSelectionContext();
    },
    canSelectResource() {
      return this.resourceContextLoaded &&
        this.loadedResourceType === this.pagination.resourceType &&
        this.selectionContextKey === this.getSelectionContextKey(
          this.pagination.resourceType,
          this.routeSearchKeyword
        );
    },
    handleSelectionChange(selection) {
      if (this.syncingTableSelection) {
        return;
      }

      const nextSelection = new Map(this.selectedResourcesById);
      (this.displayedResources || []).forEach((resource) => {
        if (resource && resource.id != null) {
          nextSelection.delete(String(resource.id));
        }
      });
      (selection || []).forEach((resource) => {
        if (resource && resource.id != null) {
          nextSelection.set(String(resource.id), resource);
        }
      });

      if (nextSelection.size > 500) {
        this.$message({
          message: '单次最多选择 500 个资源，请先处理当前选择',
          type: 'warning'
        });
        this.syncCurrentPageSelection();
        return;
      }
      this.selectedResourcesById = nextSelection;
    },
    syncCurrentPageSelection() {
      this.$nextTick(() => {
        const table = this.$refs.resourceTable;
        if (!table) {
          return;
        }
        this.syncingTableSelection = true;
        table.clearSelection();
        (this.displayedResources || []).forEach((resource) => {
          if (resource && this.selectedResourcesById.has(String(resource.id))) {
            table.toggleRowSelection(resource, true);
          }
        });
        this.$nextTick(() => {
          this.syncingTableSelection = false;
        });
      });
    },
    clearSelectedResources(options = {}) {
      const selectedCount = this.selectedResourcesById.size;
      this.selectedResourcesById = new Map();
      this.syncCurrentPageSelection();
      if (selectedCount > 0 && options.silent !== true) {
        this.$message({ message: '已清空选择', type: 'success' });
      }
    },
    openMigrationDialog() {
      if (this.$refs.resourceMigrationDialog) {
        this.$refs.resourceMigrationDialog.open();
      }
    },
    handleBatchDeleted(result) {
      const deletedIds = new Set(
        ((result && result.items) || [])
          .filter((item) => item && item.recordDeleted)
          .map((item) => String(item.resourceId))
      );
      if (deletedIds.size > 0) {
        const nextSelection = new Map(this.selectedResourcesById);
        deletedIds.forEach((id) => nextSelection.delete(id));
        this.selectedResourcesById = nextSelection;
      }
      this.pagination.current = 1;
      this.getResources();
    },
    handleMigrationTaskCreated(task) {
      if (task && task.scopeType === 'SELECTED') {
        this.clearSelectedResources({ silent: true });
      }
    },
    handleMigrationTaskFinished(view) {
      const task = view && view.task ? view.task : null;
      if (!task) {
        return;
      }
      if (Number(task.successCount) > 0) {
        this.getResources();
      }
    },
    applyRouteQuery() {
      const query = this.$route.query || {};
      const newType = query.resourceType || '';
      // 切换资源类型时重置检测任务状态
      if (newType !== this.pagination.resourceType) {
        this.resetScanTask();
      }
      this.pagination.resourceType = newType;
      this.pagination.searchKey = ((query.search || '') + '').trim();
      this.pagination.current = 1;
      this.resourceContextLoaded = false;
      this.updateSelectionContext();
    },
    handleResourcePreviewChange(value) {
      try {
        window.localStorage.setItem(RESOURCE_PREVIEW_STORAGE_KEY, value ? 'true' : 'false');
      } catch (e) {
      }
    },
    filterResourcesByKeyword(resources, keyword) {
      if (!keyword) {
        return resources || [];
      }
      return (resources || []).filter((item) => {
        return [item.originalName, item.type, this.getResourceTypeLabel(item.type), this.getResourceTypeDescription(item.type), item.path, this.getResourceUrl(item), item.mimeType, item.storeType, String(item.id || ''), String(item.userId || '')]
          .some((value) => normalizeSearchText(value).includes(keyword));
      });
    },
    clearGlobalSearchFilter() {
      const nextQuery = { ...this.$route.query };
      delete nextQuery.search;
      delete nextQuery.resourceType;
      this.$router.replace({ path: this.$route.path, query: nextQuery });
    },
    handleReplace(item) {
      const disabledReason = this.getReplaceDisabledReason(item);
      if (disabledReason) {
        this.$message({
          message: disabledReason,
          type: 'warning'
        });
        return;
      }

      this.replaceResourceTarget = item;
      this.replaceResourceFiles = [];
      this.replaceResourceFileKind = '';
      this.cleanupReplaceVideoFrame();
      this.replaceResourceDialog = true;
      this.$nextTick(() => {
        this.clearReplaceUploaderFiles();
      });
    },
    noopReplaceUpload() {
      return Promise.resolve();
    },
    beforeReplaceUpload(file) {
      return this.validateReplaceFile(file);
    },
    handleReplaceUploadChange(file, fileList) {
      const rawFile = file && file.raw ? file.raw : file;
      if (!this.validateReplaceFile(rawFile)) {
        this.replaceResourceFiles = [];
        this.replaceResourceFileKind = '';
        this.cleanupReplaceVideoFrame();
        this.$nextTick(() => this.clearReplaceUploaderFiles());
        return;
      }
      this.replaceResourceFiles = (fileList || []).slice(-1);
      this.prepareReplaceFileSelection(rawFile);
    },
    handleReplaceUploadRemove(file, fileList) {
      this.replaceResourceFiles = fileList || [];
      if (!this.replaceResourceFiles.length) {
        this.replaceResourceFileKind = '';
        this.cleanupReplaceVideoFrame();
      }
    },
    handleReplaceUploadExceed() {
      this.$message({
        message: '替换一次只能选择 1 个文件，请先移除已选文件。',
        type: 'warning'
      });
    },
    confirmReplaceResourceUpload() {
      const resource = this.replaceResourceTarget;
      const file = this.getReplaceSelectedRawFile();
      if (!resource) {
        this.$message({
          message: '请先选择要替换的资源。',
          type: 'warning'
        });
        return;
      }
      if (!file) {
        this.$message({
          message: '请先拖入或选择替换文件。',
          type: 'warning'
        });
        return;
      }
      if (!this.validateReplaceFile(file)) {
        return;
      }
      if (this.isSelectedReplaceVideoFile(file) && (!this.replaceResourceFrameConfirmed || !this.replaceResourceFrameFile)) {
        this.$message({
          message: '请先在视频预览中定位画面，并点击“使用当前帧”。',
          type: 'warning'
        });
        return;
      }

      const resourcePath = resource.path || '';
      const actionText = this.isSelectedReplaceVideoFile(file)
        ? '确认截取“' + file.name + '”的 ' + this.formatReplaceFrameSecond(this.replaceResourceFrameSecond) + ' 画面替换资源“' + resourcePath + '”？'
        : '确认用“' + file.name + '”替换资源“' + resourcePath + '”？';
      this.$confirm(actionText + '替换后 URL 保持不变，旧引用会直接使用新文件。', '确认替换资源', {
        confirmButtonText: '确定替换',
        cancelButtonText: '取消',
        type: 'warning',
        center: true,
        customClass: 'mobile-responsive-confirm'
      }).then(() => {
        const uploadFile = this.isSelectedReplaceVideoFile(file) ? this.replaceResourceFrameFile : file;
        this.submitReplaceResource(resource, uploadFile);
      }).catch(() => {});
    },
    validateReplaceFile(file, showMessage = true) {
      if (!file) {
        return false;
      }
      if (!this.replaceResourceTarget) {
        return false;
      }
      if (file.size > 100 * 1024 * 1024) {
        if (showMessage) {
          this.$message({
            message: '替换文件不能超过 100M！',
            type: 'error'
          });
        }
        return false;
      }

      if (this.isReplaceImageConversionTarget(this.replaceResourceTarget)) {
        if (this.isReplaceUploadImageFile(file) || this.isReplaceUploadVideoFile(file)) {
          return true;
        }
        if (showMessage) {
          this.$message({
            message: '图片资源只能选择图片文件，或选择视频后截取一帧；字体、文档等不能转换为图片。',
            type: 'error'
          });
        }
        return false;
      }

      if (this.isReplaceWoff2Target(this.replaceResourceTarget)) {
        if (this.isReplaceUploadWoff2File(file)) {
          return true;
        }
        if (showMessage) {
          this.$message({
            message: 'WOFF2 资源只能选择 WOFF2、TTF 或 OTF；TTF/OTF 会在服务器转换为 WOFF2。',
            type: 'error'
          });
        }
        return false;
      }

      if (this.isReplaceMediaTarget(this.replaceResourceTarget)) {
        if (this.isSameResourceExtension(this.replaceResourceTarget.path, file.name) || this.isReplaceUploadMediaFile(file)) {
          return true;
        }
        if (showMessage) {
          this.$message({
            message: '动图/视频资源只能选择同格式文件，或选择 GIF/WebP/APNG/MP4/WebM 等常见格式由服务器转换。',
            type: 'error'
          });
        }
        return false;
      }

      if (!this.isSameResourceExtension(this.replaceResourceTarget.path, file.name)) {
        if (showMessage) {
          this.$message({
            message: '替换文件扩展名必须与原资源一致（jpg 与 jpeg 视为一致）！',
            type: 'error'
          });
        }
        return false;
      }
      return true;
    },
    getReplaceSelectedRawFile() {
      const selectedFile = this.replaceResourceFiles && this.replaceResourceFiles.length
        ? this.replaceResourceFiles[0]
        : null;
      return selectedFile && selectedFile.raw ? selectedFile.raw : selectedFile;
    },
    handleReplaceDialogClose(done) {
      if (this.replaceResourceUploading) {
        return;
      }
      this.resetReplaceResourceDialog();
      done();
    },
    resetReplaceResourceDialog() {
      this.replaceResourceDialog = false;
      this.replaceResourceTarget = null;
      this.replaceResourceFiles = [];
      this.replaceResourceFileKind = '';
      this.cleanupReplaceVideoFrame();
      this.clearReplaceUploaderFiles();
    },
    clearReplaceUploaderFiles() {
      const uploader = this.$refs.replaceResourceUploader;
      if (uploader && typeof uploader.clearFiles === 'function') {
        uploader.clearFiles();
      }
    },
    submitReplaceResource(resource, file) {
      const formData = new FormData();
      formData.append('id', resource.id);
      formData.append('expectedPath', resource.path);
      formData.append('file', file, file.name || this.buildReplaceFrameFileName(this.replaceResourceFrameSecond || 0));

      this.replaceResourceUploading = true;
      this.$http.upload(this.$constant.baseURL + '/resource/replaceResource', formData, true)
        .then((res) => {
          const updatedResource = res && res.data ? res.data : resource;
          this.markResourcePreviewReplaced(updatedResource);
          this.getResources();
          this.$message({
            message: '替换成功！',
            type: 'success'
          });
          this.resetReplaceResourceDialog();
        })
        .catch((error) => {
          this.$message({
            message: error.message || '替换失败！',
            type: 'error'
          });
        })
        .finally(() => {
          this.replaceResourceUploading = false;
        });
    },
    canReplaceResource(resource) {
      return !this.getReplaceDisabledReason(resource);
    },
    getReplaceDisabledReason(resource) {
      if (!resource || !resource.id || !resource.path) {
        return '资源信息不完整，无法替换';
      }
      const storeType = ((resource.storeType || 'local') + '').toLowerCase();
      if (storeType && storeType !== 'local') {
        return '当前存储平台不支持原路径替换';
      }
      const path = (resource.path || '') + '';
      if (/^(https?:)?\/\//i.test(path) || /^(data|blob):/i.test(path)) {
        return '远程或临时资源不支持原路径替换';
      }
      if (!this.getPathExtension(path)) {
        return '资源路径缺少扩展名，无法安全替换';
      }
      return '';
    },
    getReplaceAccept(resource) {
      if (!resource) {
        return '';
      }
      const extension = this.normalizeResourceExtension(this.getPathExtension(resource.path));
      if (!extension) {
        return '';
      }
      if (this.isReplaceImageConversionTarget(resource)) {
        return REPLACE_IMAGE_FRAME_ACCEPT;
      }
      if (this.isReplaceWoff2Target(resource)) {
        return REPLACE_WOFF2_ACCEPT;
      }
      if (this.isReplaceMediaTarget(resource)) {
        return REPLACE_MEDIA_ACCEPT;
      }
      if (extension === 'jpg') {
        return '.jpg,.jpeg';
      }
      return '.' + extension;
    },
    isReplaceImageConversionTarget(resource) {
      if (!resource || !this.isImageResource(resource)) {
        return false;
      }
      const extension = this.normalizeResourceExtension(this.getPathExtension(resource.path));
      return extension === 'jpg' || extension === 'png';
    },
    isReplaceWoff2Target(resource) {
      return this.getReplaceTargetExtension(resource) === 'woff2';
    },
    isReplaceAnimatedImageTarget(resource) {
      return this.isReplaceAnimatedImageExtension(this.getReplaceTargetExtension(resource));
    },
    isReplaceMediaTarget(resource) {
      const extension = this.getReplaceTargetExtension(resource);
      return this.isReplaceAnimatedImageExtension(extension) || this.isReplaceVideoExtension(extension);
    },
    isReplaceAnimatedImageExtension(extension) {
      return REPLACE_ANIMATED_IMAGE_EXTENSIONS.includes(this.normalizeResourceExtension(extension));
    },
    isReplaceVideoExtension(extension) {
      return REPLACE_VIDEO_EXTENSIONS.includes(this.normalizeResourceExtension(extension));
    },
    getReplaceTargetFormatLabel(resource) {
      const extension = this.normalizeResourceExtension(this.getPathExtension(resource && resource.path));
      if (extension === 'jpg') {
        return 'JPG';
      }
      if (extension === 'png') {
        return 'PNG';
      }
      return extension ? extension.toUpperCase() : '原格式';
    },
    isReplaceUploadImageFile(file) {
      const mimeType = ((file && file.type) || '') + '';
      const extension = this.normalizeResourceExtension(this.getPathExtension(file && file.name));
      return mimeType.indexOf('image/') === 0 ||
        REPLACE_IMAGE_UPLOAD_EXTENSIONS.includes(extension);
    },
    isReplaceUploadAnimatedImageFile(file) {
      const mimeType = (((file && file.type) || '') + '').toLowerCase();
      const extension = this.normalizeResourceExtension(this.getPathExtension(file && file.name));
      return ['image/gif', 'image/webp', 'image/apng'].includes(mimeType) ||
        REPLACE_ANIMATED_IMAGE_EXTENSIONS.includes(extension);
    },
    isReplaceUploadVideoFile(file) {
      const mimeType = (((file && file.type) || '') + '').toLowerCase();
      const extension = this.normalizeResourceExtension(this.getPathExtension(file && file.name));
      const supportedMimeTypes = ['video/mp4', 'video/webm', 'video/quicktime', 'video/ogg'];
      return REPLACE_VIDEO_EXTENSIONS.includes(extension) || supportedMimeTypes.includes(mimeType);
    },
    isReplaceUploadMediaFile(file) {
      return this.isReplaceUploadVideoFile(file) || this.isReplaceUploadAnimatedImageFile(file);
    },
    isReplaceUploadWoff2File(file) {
      const extension = this.normalizeResourceExtension(this.getPathExtension(file && file.name));
      return REPLACE_WOFF2_UPLOAD_EXTENSIONS.includes(extension);
    },
    isSelectedReplaceVideoFile(file) {
      return this.replaceResourceFileKind === 'video' && this.isReplaceUploadVideoFile(file);
    },
    prepareReplaceFileSelection(file) {
      this.cleanupReplaceVideoFrame();
      if (this.isReplaceImageConversionTarget(this.replaceResourceTarget) && this.isReplaceUploadVideoFile(file)) {
        this.replaceResourceFileKind = 'video';
        this.replaceResourceVideoUrl = URL.createObjectURL(file);
        this.replaceResourceVideoReady = false;
        this.replaceResourceVideoDuration = 0;
        this.replaceResourceCurrentSecond = 0;
        this.replaceResourceFrameSecond = null;
        this.replaceResourceFrameConfirmed = false;
        this.replaceResourceFrameFile = null;
        return;
      }
      this.replaceResourceFileKind = this.isReplaceUploadImageFile(file) ? 'image' : 'file';
    },
    cleanupReplaceVideoFrame() {
      if (this.replaceResourceVideoUrl) {
        URL.revokeObjectURL(this.replaceResourceVideoUrl);
      }
      this.replaceResourceVideoUrl = '';
      this.replaceResourceVideoReady = false;
      this.replaceResourceVideoDuration = 0;
      this.replaceResourceCurrentSecond = 0;
      this.replaceResourceFrameSecond = null;
      this.replaceResourceFrameConfirmed = false;
      this.replaceResourceFrameFile = null;
    },
    handleReplaceVideoLoadedMetadata(event) {
      const video = event && event.target;
      if (!video) {
        return;
      }
      this.replaceResourceVideoReady = true;
      this.replaceResourceVideoDuration = Number.isFinite(video.duration) ? video.duration : 0;
      this.replaceResourceCurrentSecond = Number.isFinite(video.currentTime) ? video.currentTime : 0;
      this.replaceResourceFrameFile = null;
    },
    handleReplaceVideoTimeUpdate(event) {
      const video = event && event.target;
      if (!video) {
        return;
      }
      const currentSecond = Number.isFinite(video.currentTime) ? video.currentTime : 0;
      this.replaceResourceCurrentSecond = currentSecond;
      if (this.replaceResourceFrameConfirmed &&
        Math.abs(currentSecond - (this.replaceResourceFrameSecond || 0)) > 0.05) {
        this.replaceResourceFrameConfirmed = false;
        this.replaceResourceFrameFile = null;
      }
    },
    handleReplaceVideoError() {
      this.replaceResourceVideoReady = false;
      this.replaceResourceFrameConfirmed = false;
      this.replaceResourceFrameFile = null;
      this.$message({
        message: '当前浏览器无法解码这个视频，请换成 MP4/WebM，或先手动截图后上传图片。',
        type: 'error'
      });
    },
    async useCurrentReplaceVideoFrame() {
      const video = this.$refs.replaceFrameVideo;
      if (!video || !this.replaceResourceVideoReady) {
        this.$message({
          message: '视频还没有加载完成，请稍后再选择当前帧。',
          type: 'warning'
        });
        return;
      }
      if (!video.videoWidth || !video.videoHeight) {
        this.replaceResourceFrameConfirmed = false;
        this.replaceResourceFrameFile = null;
        this.$message({
          message: '浏览器没有读取到可用的视频画面，请换成 MP4/WebM，或先手动截图后上传图片。',
          type: 'error'
        });
        return;
      }
      const currentSecond = Number.isFinite(video.currentTime) ? video.currentTime : 0;
      try {
        const frameFile = await this.captureReplaceVideoFrame(video, currentSecond);
        this.replaceResourceCurrentSecond = currentSecond;
        this.replaceResourceFrameSecond = currentSecond;
        this.replaceResourceFrameFile = frameFile;
        this.replaceResourceFrameConfirmed = true;
      } catch (error) {
        this.replaceResourceFrameConfirmed = false;
        this.replaceResourceFrameFile = null;
        this.$message({
          message: (error && error.message) || '当前帧生成失败，请换成 MP4/WebM，或先手动截图后上传图片。',
          type: 'error'
        });
      }
    },
    captureReplaceVideoFrame(video, currentSecond) {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const context = canvas.getContext('2d');
      if (!context) {
        return Promise.reject(new Error('浏览器不支持视频抽帧，请直接上传图片。'));
      }

      const mimeType = this.getReplaceTargetMimeType(this.replaceResourceTarget);
      if (mimeType === 'image/jpeg') {
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
      }
      context.drawImage(video, 0, 0, canvas.width, canvas.height);

      return new Promise((resolve, reject) => {
        if (!canvas.toBlob) {
          reject(new Error('当前浏览器不支持生成图片文件，请直接上传图片。'));
          return;
        }
        canvas.toBlob((blob) => {
          if (!blob || blob.size === 0) {
            reject(new Error('当前帧生成失败，请换成 MP4/WebM，或先手动截图后上传图片。'));
            return;
          }
          resolve(this.createReplaceFrameFile(blob, currentSecond));
        }, mimeType, mimeType === 'image/jpeg' ? 0.92 : undefined);
      });
    },
    createReplaceFrameFile(blob, currentSecond) {
      const fileName = this.buildReplaceFrameFileName(currentSecond);
      const mimeType = this.getReplaceTargetMimeType(this.replaceResourceTarget);
      if (typeof File === 'function') {
        return new File([blob], fileName, { type: mimeType, lastModified: Date.now() });
      }
      return blob;
    },
    buildReplaceFrameFileName(currentSecond) {
      const resourcePath = (this.replaceResourceTarget && this.replaceResourceTarget.path) || '';
      const cleanPath = resourcePath.split(/[?#]/)[0].replace(/\\/g, '/');
      const slashIndex = cleanPath.lastIndexOf('/');
      const baseName = slashIndex >= 0 ? cleanPath.substring(slashIndex + 1) : cleanPath;
      if (baseName) {
        return baseName;
      }
      const extension = this.getReplaceTargetExtension(this.replaceResourceTarget) || 'jpg';
      return 'video-frame-' + Math.round(Math.max(0, currentSecond || 0) * 1000) + '.' + extension;
    },
    getReplaceTargetExtension(resource) {
      return this.normalizeResourceExtension(this.getPathExtension(resource && resource.path));
    },
    getReplaceTargetMimeType(resource) {
      return this.getReplaceTargetExtension(resource) === 'png' ? 'image/png' : 'image/jpeg';
    },
    formatReplaceFrameSecond(value) {
      const second = Number(value);
      if (!Number.isFinite(second)) {
        return '0.000s';
      }
      return Math.max(0, second).toFixed(3) + 's';
    },
    isSameResourceExtension(resourcePath, fileName) {
      const resourceExtension = this.normalizeResourceExtension(this.getPathExtension(resourcePath));
      const fileExtension = this.normalizeResourceExtension(this.getPathExtension(fileName));
      return !!resourceExtension && !!fileExtension && resourceExtension === fileExtension;
    },
    normalizeResourceExtension(extension) {
      const normalizedExtension = ((extension || '') + '').toLowerCase();
      return normalizedExtension === 'jpeg' ? 'jpg' : normalizedExtension;
    },
    getPathExtension(path) {
      const cleanPath = ((path || '') + '').split(/[?#]/)[0].replace(/\\/g, '/');
      const slashIndex = cleanPath.lastIndexOf('/');
      const dotIndex = cleanPath.lastIndexOf('.');
      if (dotIndex <= slashIndex || dotIndex === cleanPath.length - 1) {
        return '';
      }
      return cleanPath.substring(dotIndex + 1).toLowerCase();
    },
    markResourcePreviewReplaced(resource) {
      const cacheKey = this.getResourceCacheKey(resource);
      if (cacheKey) {
        this.$set(this.replacePreviewCacheBusters, cacheKey, Date.now());
      }
    },
    openResourceDetail(item) {
      if (!item || item.id == null) {
        this.$message({ message: '资源信息缺失，无法查看详情', type: 'warning' });
        return;
      }
      if (this.$refs.resourceDetailDialog) {
        this.$refs.resourceDetailDialog.open(item.id);
      }
    },
    handleResourceDetailChanged() {
      // 副本激活/删除/恢复后，列表中的活动存储、SHA-256 等元数据可能已变化，刷新当前页
      this.getResources();
    },
    handleDelete(item) {
      const toolbar = this.$refs.resourceBatchToolbar;
      if (!toolbar) {
        this.$message({ message: '批量删除组件尚未就绪，请稍后重试', type: 'warning' });
        return;
      }
      toolbar.openDelete([item]);
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
    handleResourceUploadStart(summary) {
      this.resourceUploadTotal = summary && summary.total ? summary.total : 0;
    },
    handleResourceUploadComplete(summary) {
      const success = summary && summary.success ? summary.success : 0;
      const fail = summary && summary.fail ? summary.fail : 0;
      const errors = summary && Array.isArray(summary.errors) ? summary.errors : [];
      this.resourceUploadTotal = 0;

      if (success > 0) {
        this.pagination.current = 1;
        this.getResources();
      }

      if (success > 0 && fail === 0) {
        this.resourceDialog = false;
        this.$message({
          message: '成功上传' + success + '个文件！',
          type: 'success'
        });
      } else if (success > 0) {
        this.resourceDialog = true;
        this.showResourceUploadFailureDetail(success, fail, errors, 'warning');
      } else if (fail > 0) {
        this.resourceDialog = true;
        this.showResourceUploadFailureDetail(success, fail, errors, 'error');
      }
    },
    showResourceUploadFailureDetail(success, fail, errors, type) {
      const storeLabel = this.getStoreTypeLabel(this.storeType);
      const detailHtml = this.buildResourceUploadFailureHtml(success, fail, errors, storeLabel);
      this.$alert(detailHtml, success > 0 ? '部分文件上传失败' : '上传失败', {
        confirmButtonText: '知道了',
        type,
        dangerouslyUseHTMLString: true,
        customClass: 'mobile-responsive-confirm'
      }).catch(() => {});
    },
    buildResourceUploadFailureHtml(success, fail, errors, storeLabel) {
      const safeStoreLabel = this.escapeHtml(storeLabel);
      const resultLine = success > 0
        ? '已成功上传 ' + success + ' 个文件，失败 ' + fail + ' 个。'
        : '本次 ' + fail + ' 个文件全部上传失败。';
      const retryTip = this.storeType === 'local'
        ? '请检查服务器本地存储目录、文件大小限制或后端日志后重试。'
        : '你可以检查该存储平台配置后重试，或直接切换为“服务器(local)”再上传。';
      const errorItems = (errors || []).slice(0, 5).map((item) => {
        const fileName = this.escapeHtml(item.fileName || '未知文件');
        const reason = this.escapeHtml(item.message || '上传失败');
        return '<li><strong>' + fileName + '</strong>：' + reason + '</li>';
      }).join('');
      const moreLine = errors && errors.length > 5
        ? '<div style="margin-top: 6px; color: #909399;">还有 ' + (errors.length - 5) + ' 个失败项未展示。</div>'
        : '';

      return '<div style="text-align: left; line-height: 1.7;">' +
        '<div>' + this.escapeHtml(resultLine) + '</div>' +
        '<div>当前存储平台：<strong>' + safeStoreLabel + '</strong></div>' +
        '<div style="margin-top: 8px;">建议：' + this.escapeHtml(retryTip) + '</div>' +
        (errorItems ? '<ul style="margin: 10px 0 0; padding-left: 18px;">' + errorItems + '</ul>' : '') +
        moreLine +
        '</div>';
    },
    getStoreTypeLabel(storeType) {
      const store = this.storeTypes.find((item) => item.value === storeType);
      return store ? store.label + '(' + store.value + ')' : (storeType || '-');
    },
    escapeHtml(value) {
      return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    },
    addResources() {
      if (this.isResourceFilterView()) {
        this.$message({
          message: this.getResourceFilterViewName() + '是筛选视图，不能直接上传，请选择具体资源类型！',
          type: 'warning'
        });
        return;
      }
      if (this.$common.isEmpty(this.pagination.resourceType)) {
        this.$message({
          message: '请选择资源类型！',
          type: 'error'
        });
        return;
      }
      this.resourceDialog = true;
    },
    isResourceFilterView() {
      return ['orphanResource', 'invalidResource'].includes(this.pagination.resourceType);
    },
    getResourceFilterViewName() {
      if (this.pagination.resourceType === 'invalidResource') {
        return '无效资源';
      }
      return '孤儿资源';
    },
    getResourceTypeLabel(resourceType) {
      const resourceTypeOption = RESOURCE_TYPE_MAP[resourceType];
      return resourceTypeOption ? resourceTypeOption.label : (resourceType || '-');
    },
    getResourceTypeDescription(resourceType) {
      const resourceTypeOption = RESOURCE_TYPE_MAP[resourceType];
      return resourceTypeOption ? resourceTypeOption.description : '';
    },
    getResourceTypeHelp(resourceType) {
      const description = this.getResourceTypeDescription(resourceType);
      if (!description) {
        return '';
      }
      return this.getResourceTypeLabel(resourceType) + '：' + description + '（' + resourceType + '）';
    },
    search() {
      this.pagination.total = 0;
      this.pagination.current = 1;
      this.getResources();
    },
    handleSortChange({ prop, order }) {
      const sortProp = prop || 'createTime';
      this.pagination.order = order ? sortProp : 'createTime';
      this.pagination.desc = order ? order === 'descending' : true;
      this.pagination.current = 1;
      this.getResources();
    },
    applyLoadedResourcePage(records, total, resourceType, searchKeyword) {
      this.resources = records || [];
      this.pagination.total = total || 0;
      this.loadedResourceType = resourceType || '';
      this.resourceContextLoaded = true;
      this.selectionContextKey = this.getSelectionContextKey(resourceType, searchKeyword);

      const nextSelection = new Map(this.selectedResourcesById);
      this.resources.forEach((resource) => {
        const id = resource && resource.id != null ? String(resource.id) : '';
        if (id && nextSelection.has(id)) {
          nextSelection.set(id, resource);
        }
      });
      this.selectedResourcesById = nextSelection;
      this.syncCurrentPageSelection();
    },
    getResources() {
      const requestSequence = ++this.resourceRequestSequence;
      const requestResourceType = this.pagination.resourceType;
      const requestSearchKeyword = this.routeSearchKeyword;

      // 无效资源走异步检测任务流程，带进度提示
      if (this.isInvalidResourceType && !requestSearchKeyword) {
        this.startInvalidScan(requestSequence, requestResourceType);
        return;
      }

      const requestPagination = {
        current: requestSearchKeyword ? 1 : this.pagination.current,
        size: requestSearchKeyword ? 500 : this.pagination.size,
        resourceType: requestResourceType,
        searchKey: '',
        order: this.pagination.order,
        desc: this.pagination.desc
      };

      this.resourcesLoading = true;
      this.$http.post(this.$constant.baseURL + '/resource/listResource', requestPagination, true)
        .then((res) => {
          if (requestSequence !== this.resourceRequestSequence || !res || !res.data) {
            return;
          }
          const records = res.data.records || [];
          const total = requestSearchKeyword
            ? this.filterResourcesByKeyword(records, requestSearchKeyword).length
            : res.data.total;
          this.applyLoadedResourcePage(records, total, requestResourceType, requestSearchKeyword);
        })
        .catch((error) => {
          if (requestSequence !== this.resourceRequestSequence) {
            return;
          }
          this.resourceContextLoaded = false;
          this.$message({
            message: error.message,
            type: 'error'
          });
        })
        .finally(() => {
          if (requestSequence === this.resourceRequestSequence) {
            this.resourcesLoading = false;
          }
        });
    },
    startInvalidScan(requestSequence, requestResourceType) {
      // 清理上一次轮询
      this.stopScanPolling();

      const payload = {
        current: this.pagination.current,
        size: this.pagination.size,
        resourceType: requestResourceType,
        searchKey: '',
        order: this.pagination.order,
        desc: this.pagination.desc
      };

      this.resourcesLoading = true;
      this.resourceContextLoaded = false;
      this.scanTask.active = true;
      this.scanTask.status = 'PENDING';
      this.scanTask.taskId = '';
      this.scanTask.resourceType = requestResourceType;
      this.scanTask.requestSequence = requestSequence;
      this.scanTask.total = 0;
      this.scanTask.processed = 0;
      this.scanTask.hitCount = 0;
      this.scanTask.errorMessage = '';
      this.scanTask.progressPercent = 0;

      this.$http.post(this.$constant.baseURL + '/resource/startInvalidScan', payload, true)
        .then((res) => {
          if (requestSequence !== this.resourceRequestSequence ||
            requestSequence !== this.scanTask.requestSequence) {
            return;
          }
          if (res && res.data && res.data.taskId) {
            this.scanTask.taskId = res.data.taskId;
            this.scanTask.status = res.data.status || 'PENDING';
            this.startScanPolling();
          } else {
            this.finishScanWithError('启动检测任务失败：返回数据异常');
          }
        })
        .catch((error) => {
          if (requestSequence === this.resourceRequestSequence &&
            requestSequence === this.scanTask.requestSequence) {
            this.finishScanWithError(error.message || '启动检测任务失败');
          }
        });
    },
    startScanPolling() {
      this.stopScanPolling();
      this.scanPollTimer = setInterval(() => {
        this.pollScanStatus();
      }, 1000);
    },
    pollScanStatus() {
      const taskId = this.scanTask.taskId;
      const requestSequence = this.scanTask.requestSequence;
      if (!taskId || requestSequence !== this.resourceRequestSequence) {
        this.stopScanPolling();
        return;
      }
      this.$http.get(this.$constant.baseURL + '/resource/scanStatus', {
        taskId
      }, true)
        .then((res) => {
          if (!res || !res.data ||
            taskId !== this.scanTask.taskId ||
            requestSequence !== this.resourceRequestSequence ||
            requestSequence !== this.scanTask.requestSequence) {
            return;
          }
          const data = res.data;
          this.scanTask.status = data.status || '';
          this.scanTask.total = data.total || 0;
          this.scanTask.processed = data.processed || 0;
          this.scanTask.hitCount = data.hitCount || 0;
          this.scanTask.errorMessage = data.errorMessage || '';
          this.scanTask.progressPercent = data.progressPercent || 0;

          if (data.status === 'SUCCESS' || data.status === 'FAILED' || data.status === 'CANCELLED') {
            this.stopScanPolling();
            this.resourcesLoading = false;
            if (data.status === 'SUCCESS') {
              this.loadInvalidResourceList(requestSequence, this.scanTask.resourceType);
            } else if (data.status === 'FAILED') {
              this.resourceContextLoaded = false;
              this.$message({
                message: '检测失败：' + (data.errorMessage || '未知错误'),
                type: 'error'
              });
            } else if (data.status === 'CANCELLED') {
              this.resourceContextLoaded = false;
              this.$message({
                message: '检测已取消',
                type: 'info'
              });
            }
          }
        })
        .catch(() => {
          // 轮询失败不中断，下次重试
        });
    },
    loadInvalidResourceList(requestSequence, requestResourceType) {
      const requestPagination = {
        current: this.pagination.current,
        size: this.pagination.size,
        resourceType: requestResourceType,
        searchKey: '',
        order: this.pagination.order,
        desc: this.pagination.desc
      };

      this.$http.post(this.$constant.baseURL + '/resource/listResource', requestPagination, true)
        .then((res) => {
          if (requestSequence !== this.resourceRequestSequence || !res || !res.data) {
            return;
          }
          const records = res.data.records || [];
          this.applyLoadedResourcePage(records, res.data.total, requestResourceType, '');
        })
        .catch((error) => {
          if (requestSequence !== this.resourceRequestSequence) {
            return;
          }
          this.resourceContextLoaded = false;
          this.$message({
            message: error.message,
            type: 'error'
          });
        });
    },
    cancelScan() {
      if (!this.scanTask.taskId) {
        return;
      }
      this.$http.get(this.$constant.baseURL + '/resource/cancelScan', {
        taskId: this.scanTask.taskId
      }, true)
        .then(() => {
          // 状态由轮询刷新，不立即关闭，等轮询确认 CANCELLED
        })
        .catch(() => {
          // 忽略取消请求失败
        });
    },
    finishScanWithError(message) {
      this.scanTask.status = 'FAILED';
      this.scanTask.errorMessage = message;
      this.resourcesLoading = false;
      this.stopScanPolling();
    },
    stopScanPolling() {
      if (this.scanPollTimer) {
        clearInterval(this.scanPollTimer);
        this.scanPollTimer = null;
      }
    },
    resetScanTask() {
      this.stopScanPolling();
      this.scanTask.active = false;
      this.scanTask.status = '';
      this.scanTask.taskId = '';
      this.scanTask.total = 0;
      this.scanTask.processed = 0;
      this.scanTask.hitCount = 0;
      this.scanTask.errorMessage = '';
      this.scanTask.progressPercent = 0;
      this.resourcesLoading = false;
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
      return mimeType.includes('image') || /\.(png|apng|jpe?g|gif|svg|webp|bmp|avif|ico)(\?.*)?$/i.test(fileName);
    },
    isVideoResource(resource) {
      const mimeType = ((resource && resource.mimeType) || '') + '';
      const fileName = this.getResourceFileName(resource);
      return mimeType.includes('video') || /\.(mp4|webm|ogg|ogv|mov|m4v)(\?.*)?$/i.test(fileName);
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
    getPreviewPlaceholderIcon(resource) {
      if (this.isImageResource(resource)) {
        return 'el-icon-picture-outline';
      }
      if (this.isVideoResource(resource)) {
        return 'el-icon-video-camera';
      }
      if (this.isFont(((resource && resource.mimeType) || '') + '', this.getResourceFileName(resource))) {
        return 'el-icon-edit-outline';
      }
      return 'el-icon-document';
    },
    getResourceDimensionText(resource) {
      if (!resource || !resource.width || !resource.height) {
        return '';
      }
      return resource.width + 'x' + resource.height;
    },
    getResourceFileName(resource) {
      if (!resource) {
        return '';
      }
      return (resource.originalName || resource.path || '') + '';
    },
    getResourceExtension(resource) {
      if (!resource) {
        return '';
      }
      return this.normalizeResourceExtension(
        this.getPathExtension(resource.path) ||
        this.getPathExtension(resource.originalName)
      );
    },
    canGenerateResourceThumbnail(resource) {
      if (!resource || !this.isImageResource(resource)) {
        return false;
      }
      const extension = this.getResourceExtension(resource);
      if (extension) {
        return RESOURCE_THUMBNAIL_IMAGE_EXTENSIONS.includes(extension);
      }
      const mimeType = (((resource && resource.mimeType) || '') + '').toLowerCase();
      return RESOURCE_THUMBNAIL_MIME_TYPES.includes(mimeType);
    },
    canUseResourceThumbnail(resource) {
      if (!resource || !resource.id || !this.isImageResource(resource)) {
        return false;
      }
      if (!this.canGenerateResourceThumbnail(resource)) {
        return false;
      }
      const storeType = ((resource.storeType || 'local') + '').toLowerCase();
      if (storeType && storeType !== 'local') {
        return false;
      }
      const path = ((resource.path || '') + '').trim();
      return !!path && !/^(https?:)?\/\//i.test(path) && !/^(data|blob):/i.test(path);
    },
    getResourceThumbnailUrl(resource) {
      if (!this.canUseResourceThumbnail(resource)) {
        return '';
      }

      const params = [
        'id=' + encodeURIComponent(resource.id),
        'w=120',
        'h=104'
      ];
      const version = this.getResourceVersion(resource);
      if (version) {
        params.push('v=' + encodeURIComponent(version));
      }

      const cacheKey = this.getResourceCacheKey(resource);
      const cacheBuster = cacheKey ? this.replacePreviewCacheBusters[cacheKey] : '';
      if (cacheBuster) {
        params.push('_replace=' + encodeURIComponent(cacheBuster));
      }
      return this.$constant.baseURL + '/resource/thumbnail?' + params.join('&');
    },
    getResourceListImageUrl(resource) {
      if (this.canUseResourceThumbnail(resource)) {
        return this.getResourceThumbnailUrl(resource);
      }
      return this.getResourcePreviewUrl(resource);
    },
    getResourceVersion(resource) {
      if (!resource) {
        return '';
      }
      return [
        resource.resourceHash || '',
        resource.size || '',
        resource.width || '',
        resource.height || '',
        resource.createTime || ''
      ].filter(Boolean).join('-');
    },
    getResourcePreviewUrl(resource) {
      const url = this.getResourceUrl(resource);
      const cacheKey = this.getResourceCacheKey(resource);
      const cacheBuster = cacheKey ? this.replacePreviewCacheBusters[cacheKey] : '';
      if (!url || !cacheBuster || /^(data|blob):/i.test(url)) {
        return url;
      }
      return this.appendCacheBuster(url, cacheBuster);
    },
    getResourceCacheKey(resource) {
      if (!resource) {
        return '';
      }
      if (resource.id) {
        return 'id:' + resource.id;
      }
      return resource.path ? 'path:' + resource.path : '';
    },
    appendCacheBuster(url, cacheBuster) {
      const hashIndex = url.indexOf('#');
      const hash = hashIndex >= 0 ? url.substring(hashIndex) : '';
      const baseUrl = hashIndex >= 0 ? url.substring(0, hashIndex) : url;
      return baseUrl + (baseUrl.includes('?') ? '&' : '?') + '_replace=' + encodeURIComponent(cacheBuster) + hash;
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
        if (import.meta.env.DEV && isRootPublicResourcePath(path)) {
          return getScopedPublicResourceUrl(path);
        }
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

  .scan-progress-bar {
    margin-bottom: 16px;
    padding: 12px 16px;
    border-radius: 8px;
    background: #f4f8ff;
    border: 1px solid #d6e4ff;
  }

  .scan-progress-bar--RUNNING,
  .scan-progress-bar--PENDING {
    background: #ecf5ff;
    border-color: #b3d8ff;
  }

  .scan-progress-bar--SUCCESS {
    background: #f0f9eb;
    border-color: #c2e7b0;
  }

  .scan-progress-bar--FAILED {
    background: #fef0f0;
    border-color: #fbc4c4;
  }

  .scan-progress-bar--CANCELLED {
    background: #fdf6ec;
    border-color: #f5dab1;
  }

  .scan-progress-bar__head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
    font-size: 13px;
    color: #606266;
  }

  .scan-progress-bar__icon {
    font-size: 16px;
  }

  .scan-progress-bar--RUNNING .scan-progress-bar__icon,
  .scan-progress-bar--PENDING .scan-progress-bar__icon {
    color: #409eff;
  }

  .scan-progress-bar--SUCCESS .scan-progress-bar__icon {
    color: #67c23a;
  }

  .scan-progress-bar--FAILED .scan-progress-bar__icon {
    color: #f56c6c;
  }

  .scan-progress-bar--CANCELLED .scan-progress-bar__icon {
    color: #e6a23c;
  }

  .scan-progress-bar__title {
    font-weight: 600;
    color: #303133;
  }

  .scan-progress-bar__meta {
    color: #909399;
    font-size: 12px;
  }

  .scan-progress-bar__cancel {
    margin-left: auto;
  }

  .scan-progress-bar .el-progress {
    margin-top: 2px;
  }

  .orphan-resource-warning {
    margin-bottom: 16px;
    padding: 10px 14px;
    border-radius: 8px;
    background: #fdf6ec;
    border: 1px solid #f5dab1;
    color: #e6a23c;
    font-size: 13px;
    display: flex;
    align-items: flex-start;
    gap: 8px;
    line-height: 1.6;
  }

  .orphan-resource-warning i {
    font-size: 16px;
    margin-top: 1px;
    flex-shrink: 0;
  }

  body.dark-mode .orphan-resource-warning {
    background: #3b2e1f;
    border-color: #7d5b30;
    color: #f0c98b;
  }

  .handle-box {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    margin-bottom: 20px;
  }

  .handle-box .mrb10 {
    margin-right: 0;
    margin-bottom: 0;
  }

  .handle-box .el-button {
    margin-left: 0;
  }

  .handle-select {
    width: 240px;
  }

  .resource-type-help {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 40px;
    color: #909399;
    cursor: help;
    vertical-align: top;
  }

  .resource-type-option {
    display: flex;
    flex-direction: column;
    justify-content: center;
    line-height: 18px;
  }

  .resource-type-option-label {
    color: #303133;
    font-size: 14px;
  }

  body.dark-mode .resource-type-option-label {
    color: #e0e0e0;
  }

  .resource-type-option-desc {
    max-width: 200px;
    overflow: hidden;
    color: #909399;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .resource-preview-toggle {
    display: inline-flex;
    align-items: center;
    height: 40px;
    gap: 6px;
    color: #606266;
    font-size: 13px;
    white-space: nowrap;
  }

  .resource-preview-toggle .el-switch {
    margin: 0;
  }

  .table {
    width: 100%;
    font-size: 14px;
  }

  .mrb10 {
    margin-right: 10px;
    margin-bottom: 10px;
  }

  .replace-resource-target {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 14px;
    color: #606266;
    font-size: 14px;
  }

  .replace-resource-target-label {
    flex: 0 0 auto;
    font-weight: 600;
  }

  .replace-resource-target-path {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  ::v-deep .resource-replace-upload .el-upload {
    display: block;
    width: 100%;
  }

  ::v-deep .resource-replace-upload .el-upload-dragger {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 176px;
  }

  ::v-deep .resource-replace-upload .el-upload__text {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #606266;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.6;
  }

  ::v-deep .resource-replace-upload .replace-upload-icon {
    margin: 0 0 10px;
    color: #8c7bfd;
    font-size: 42px;
    line-height: 1;
  }

  .replace-resource-actions {
    margin-top: 20px;
    text-align: center;
  }

  .replace-video-frame-picker {
    margin-top: 14px;
    padding: 12px;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    background: #f8f9fb;
    text-align: center;
  }

  .replace-video-frame-picker video {
    display: block;
    width: 100%;
    max-height: 280px;
    margin: 0 auto 10px;
    background: #000;
    border-radius: 4px;
  }

  .replace-video-frame-meta {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px 14px;
    margin-bottom: 10px;
    color: #606266;
    font-size: 13px;
    line-height: 1.5;
  }

  .replace-video-frame-selected {
    color: #67c23a;
    font-weight: 600;
  }

  .table-td-thumb {
    display: block;
    margin: auto;
    width: 52px;
    height: 48px;
    border-radius: 4px;
    background: #f5f7fa;
    overflow: hidden;
  }

  .resource-url-cell {
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 0;
  }

  .resource-url-text {
    max-width: 112px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .table-td-thumb >>> .broken-image-thumb {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    gap: 3px;
    color: #a8abb2;
    background: #f5f7fa;
  }

  .table-td-thumb >>> .broken-image-thumb .broken-image-svg {
    width: 20px;
    height: 20px;
    fill: currentColor;
  }

  .table-td-thumb >>> .broken-image-thumb .broken-image-text {
    margin-top: 0;
    font-size: 10px;
    line-height: 1;
    white-space: nowrap;
    color: #909399;
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

  .resource-preview-disabled {
    display: inline-flex;
    flex-direction: column;
    gap: 2px;
    width: 56px;
    height: 44px;
    margin: auto;
    box-sizing: border-box;
    font-size: 18px;
  }

  .resource-preview-dimension {
    display: block;
    max-width: 52px;
    overflow: hidden;
    color: #909399;
    font-size: 10px;
    line-height: 1;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .broken-image-fallback {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #a8abb2;
    background: #f5f7fa;
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



