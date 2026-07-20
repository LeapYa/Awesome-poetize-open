<template>
  <div>
    <div v-if="globalSearchKeyword" style="margin-bottom: 12px; padding: 10px 14px; border-radius: 8px; background: #f4f8ff; color: #606266; display: flex; align-items: center; justify-content: space-between; gap: 12px;">
      <span>当前显示的是全局搜索结果：{{ globalSearchKeyword }}</span>
      <el-button type="text" @click="clearGlobalSearchFilter">清除全局筛选</el-button>
    </div>
    <div class="handle-box">
      <el-select v-model="pagination.recommendStatus" placeholder="是否推荐" style="width: 120px" class="mrb10">
        <el-option key="1" label="是" :value="true"></el-option>
        <el-option key="2" label="否" :value="false"></el-option>
      </el-select>
      <el-select style="width: 140px" class="mrb10" v-model="pagination.sortId" placeholder="请选择分类">
        <el-option
          v-for="item in sorts"
          :key="item.id"
          :label="item.sortName"
          :value="item.id">
        </el-option>
      </el-select>
      <el-select style="width: 140px" class="mrb10" v-model="pagination.labelId" placeholder="请选择标签">
        <el-option
          v-for="item in labelsTemp"
          :key="item.id"
          :label="item.labelName"
          :value="item.id">
        </el-option>
      </el-select>
      <el-input v-model="pagination.searchKey" placeholder="文章标题" class="handle-input mrb10"></el-input>
      <el-button type="primary" icon="el-icon-search" @click="searchArticles()">搜索</el-button>
      <el-button type="danger" @click="clearSearch()">清除参数</el-button>
      <el-button type="primary" @click="$router.push({path: '/postEdit'})">新增文章</el-button>
      <el-button type="info" @click="$router.push({path: '/draftList'})">草稿箱</el-button>
      <el-button type="success" icon="el-icon-upload2" @click="openImportDialog">导入文章</el-button>
      <el-button type="warning" icon="el-icon-download" @click="exportAllArticles" :loading="exportAllLoading">导出所有文章</el-button>
    </div>
    <el-table :data="articles" border class="table" header-cell-class-name="table-header" empty-text="暂无文章">
      <el-table-column prop="id" label="ID" width="55" align="center"></el-table-column>
      <el-table-column prop="username" label="作者" align="center"></el-table-column>
      <el-table-column prop="articleTitle" label="文章标题" align="center"></el-table-column>
      <el-table-column prop="sort.sortName" label="分类" align="center"></el-table-column>
      <el-table-column prop="label.labelName" label="标签" align="center"></el-table-column>
      <el-table-column prop="viewCount" label="浏览量" align="center"></el-table-column>
      <el-table-column label="是否可见" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.viewStatus === false ? 'danger' : 'success'"
                  disable-transitions>
            {{scope.row.viewStatus === false ? '不可见' : '可见'}}
          </el-tag>
          <el-switch @click.native="changeStatus(scope.row, 1)" v-model="scope.row.viewStatus"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="封面" align="center">
        <template slot-scope="scope">
          <el-image
            lazy
            class="table-td-thumb"
            :src="getArticleCoverUrl(scope.row)"
            :preview-src-list="getArticleCoverPreviewList(scope.row)"
            fit="cover">
            <div slot="error" class="article-cover-fallback">
              <span>{{ getArticleCoverFallbackText(scope.row) }}</span>
            </div>
          </el-image>
        </template>
      </el-table-column>
      <el-table-column label="是否启用评论" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.commentStatus === false ? 'danger' : 'success'"
                  disable-transitions>
            {{scope.row.commentStatus === false ? '否' : '是'}}
          </el-tag>
          <el-switch @click.native="changeStatus(scope.row, 2)" v-model="scope.row.commentStatus"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="是否推荐" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.recommendStatus === false ? 'danger' : 'success'"
                  disable-transitions>
            {{scope.row.recommendStatus === false ? '否' : '是'}}
          </el-tag>
          <el-switch @click.native="changeStatus(scope.row, 3)" v-model="scope.row.recommendStatus"></el-switch>
        </template>
      </el-table-column>
      <el-table-column prop="commentCount" label="评论数" align="center"></el-table-column>
      <el-table-column prop="createTime" label="创建时间" align="center"></el-table-column>
      <el-table-column prop="updateTime" label="最终修改时间" align="center"></el-table-column>
      <el-table-column label="操作" width="220" align="center">
        <template slot-scope="scope">
          <div>
            <el-button type="text" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="text" icon="el-icon-view" style="color: var(--blue)" @click="handleView(scope.row)">查看</el-button>
            <el-button type="text" icon="el-icon-download" style="color: #E6A23C" @click="exportSingleArticle(scope.row)">导出</el-button>
          </div>
          <div style="margin-top: 4px">
            <el-button type="text" icon="el-icon-s-tools" style="color: var(--blue)" @click="handleDeleteTranslation(scope.row)">删除翻译</el-button>
            <el-button type="text" icon="el-icon-delete" style="color: var(--orangeRed)" @click="handleDelete(scope.row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination background layout="total, prev, pager, next"
                     :current-page="pagination.current"
                     :page-size="pagination.size"
                     :total="pagination.total"
                     @current-change="handlePageChange">
      </el-pagination>
    </div>

    <!-- 删除翻译对话框 -->
    <el-dialog title="删除翻译" :visible.sync="deleteTranslationDialog" width="500px" custom-class="centered-dialog">
      <div v-if="availableLanguages.length === 0" style="text-align: center; padding: 20px;">
        <p style="margin-top: 15px; color: #606266;">该文章暂无翻译版本</p>
      </div>
      
      <div v-else-if="availableLanguages.length === 1" style="text-align: center; padding: 20px;">
        <i class="el-icon-warning" style="font-size: 48px; color: #E6A23C;"></i>
        <p style="margin-top: 15px; color: #606266;">
          确认删除该文章的 <strong>{{ getLanguageName(availableLanguages[0]) }}</strong> 翻译版本吗？
        </p>
        <p style="color: #909399; font-size: 12px;">删除后将无法恢复，用户访问时会自动显示原文</p>
      </div>
      
      <div v-else>
        <p style="margin-bottom: 15px; color: #606266;">该文章有以下翻译版本，请选择要删除的语言：</p>
        <el-checkbox-group v-model="selectedLanguages">
          <div v-for="lang in availableLanguages" :key="lang" style="margin-bottom: 10px;">
            <el-checkbox :label="lang">{{ getLanguageName(lang) }}</el-checkbox>
          </div>
        </el-checkbox-group>
        <div style="margin-top: 15px;">
          <el-button type="text" @click="selectAllLanguages" style="color: #409EFF;">全选</el-button>
          <el-button type="text" @click="clearSelectedLanguages" style="color: #909399;">清空</el-button>
        </div>
      </div>
      
      <div slot="footer" class="dialog-footer">
        <el-button @click="deleteTranslationDialog = false">取消</el-button>
        <el-button 
          v-if="availableLanguages.length === 1" 
          type="danger" 
          @click="confirmDeleteSingleTranslation"
          :loading="deleteLoading">
          确认删除
        </el-button>
        <el-button 
          v-else-if="availableLanguages.length > 1" 
          type="danger" 
          @click="confirmDeleteMultipleTranslations"
          :disabled="selectedLanguages.length === 0"
          :loading="deleteLoading">
          删除选中的翻译 ({{ selectedLanguages.length }})
        </el-button>
      </div>
    </el-dialog>

    <!-- 导入文章对话框 -->
    <el-dialog title="导入文章" :visible.sync="importDialogVisible" width="720px"
               :close-on-click-modal="false" custom-class="centered-dialog" @close="resetImportDialog">

      <!-- 步骤1：上传文件 -->
      <div v-if="importStep === 1">
        <el-upload
          ref="importUpload"
          drag
          action=""
          multiple
          :auto-upload="false"
          :on-change="handleImportFileChange"
          :on-remove="handleImportFileRemove"
          :file-list="importFileList"
          accept=".md,.markdown,.json,.txt">
          <i class="el-icon-upload"></i>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <div class="el-upload__tip" slot="tip">
            支持 .md / .markdown / .txt / .json 格式，单个文件最大 10MB，最多 50 个文件
          </div>
        </el-upload>
      </div>

      <!-- 步骤2：预览和配置 -->
      <div v-if="importStep === 2">
        <!-- 默认配置区域 -->
        <el-card shadow="never" style="margin-bottom: 15px;">
          <div slot="header" style="font-size: 14px; font-weight: bold;">
            <i class="el-icon-setting"></i> 默认配置（应用于未指定分类/标签的文章）
          </div>
          <el-form :inline="true" label-width="80px" size="small">
            <el-form-item label="分类">
              <el-select v-model="importConfig.sortId" placeholder="请选择分类"
                         clearable style="width: 160px" @change="handleImportSortChange">
                <el-option v-for="item in sorts" :key="item.id"
                           :label="item.sortName" :value="item.id"></el-option>
                <el-option key="new-sort" label="+ 新建分类" value="new-sort"
                           style="color: #409EFF; font-weight: bold;"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="标签">
              <el-select v-model="importConfig.labelId" placeholder="请选择标签"
                         clearable style="width: 160px" @change="handleImportLabelChange">
                <el-option v-for="item in importLabelsTemp" :key="item.id"
                           :label="item.labelName" :value="item.id"></el-option>
                <el-option v-if="importConfig.sortId && importConfig.sortId !== 'new-sort'"
                           key="new-label" label="+ 新建标签" value="new-label"
                           style="color: #409EFF; font-weight: bold;"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="可见">
              <el-switch v-model="importConfig.viewStatus"></el-switch>
            </el-form-item>
            <el-form-item label="评论">
              <el-switch v-model="importConfig.commentStatus"></el-switch>
            </el-form-item>
            <el-form-item label="推荐">
              <el-switch v-model="importConfig.recommendStatus"></el-switch>
            </el-form-item>
            <el-form-item v-if="!importConfig.viewStatus" label="密码">
              <el-input v-model="importConfig.password" placeholder="请设置访问密码" clearable style="width: 160px"></el-input>
            </el-form-item>
            <el-form-item v-if="!importConfig.viewStatus" label="密码提示">
              <el-input v-model="importConfig.tips" placeholder="请设置密码提示" clearable style="width: 160px"></el-input>
            </el-form-item>
          </el-form>
          <div v-if="!importConfig.viewStatus" style="margin-top: 4px; font-size: 12px; color: #E6A23C;">
            <i class="el-icon-warning"></i> 不可见文章需要同时设置密码和密码提示，否则无法导入
          </div>
        </el-card>

        <!-- 文章预览列表 -->
        <div style="margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center;">
          <span style="font-size: 14px; font-weight: bold;">
            <i class="el-icon-document"></i> 待导入文章（{{ importArticles.length }} 篇）
          </span>
          <el-button type="text" icon="el-icon-back" @click="importStep = 1">重新选择文件</el-button>
        </div>
        <el-table :data="importArticles" border size="small" max-height="360"
                  class="table" header-cell-class-name="table-header">
          <el-table-column type="index" label="#" width="45" align="center"></el-table-column>
          <el-table-column prop="articleTitle" label="标题" min-width="180" show-overflow-tooltip>
            <template slot-scope="scope">
              <el-input v-model="scope.row.articleTitle" size="mini"
                        placeholder="请输入标题"
                        :class="{ 'need-title-input': scope.row.needManualTitle && !scope.row.articleTitle }"></el-input>
              <span v-if="scope.row.needManualTitle" style="color: #E6A23C; font-size: 11px; line-height: 1.2; display: block; margin-top: 2px;">
                <i class="el-icon-warning"></i> 检测到多个一级标题，请手动输入文章标题
              </span>
              <span v-if="scope.row.h1ExtractedAsTitle" style="font-size: 11px; line-height: 1.2; display: block; margin-top: 2px;">
                <span style="color: #909399;">
                  <i class="el-icon-info"></i> 已将一级标题提取为文章标题
                </span>
                <el-button type="text" size="mini" style="padding: 0; margin-left: 4px; font-size: 11px; color: #E6A23C;"
                           @click="undoH1Extract(scope.$index)">撤销提取</el-button>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="140">
            <template slot-scope="scope">
              <el-select v-model="scope.row.sortId" size="mini" placeholder="使用默认"
                         clearable @change="val => handleArticleSortChange(scope.$index, val)">
                <el-option v-for="item in sorts" :key="item.id"
                           :label="item.sortName" :value="item.id"></el-option>
                <el-option key="new-sort" label="+ 新建" value="new-sort"
                           style="color: #409EFF; font-weight: bold;"></el-option>
              </el-select>
              <span v-if="!scope.row.sortId && scope.row.sortName" style="font-size: 11px; color: #909399; display: block; margin-top: 2px;">
                <i class="el-icon-info"></i> 文件指定: {{ scope.row.sortName }}
              </span>
              <span v-if="!scope.row.sortId && !scope.row.sortName" style="font-size: 11px; color: #C0C4CC; display: block; margin-top: 2px;">跟随默认</span>
            </template>
          </el-table-column>
          <el-table-column label="标签" width="140">
            <template slot-scope="scope">
              <el-select v-model="scope.row.labelId" size="mini" placeholder="使用默认"
                         clearable @change="val => handleArticleLabelChange(scope.$index, val)">
                <el-option v-for="item in getLabelsForArticle(scope.row)" :key="item.id"
                           :label="item.labelName" :value="item.id"></el-option>
                <el-option v-if="scope.row.sortId && scope.row.sortId !== 'new-sort'"
                           key="new-label" label="+ 新建" value="new-label"
                           style="color: #409EFF; font-weight: bold;"></el-option>
              </el-select>
              <span v-if="!scope.row.labelId && scope.row.labelName" style="font-size: 11px; color: #909399; display: block; margin-top: 2px;">
                <i class="el-icon-info"></i> 文件指定: {{ scope.row.labelName }}
              </span>
              <span v-if="!scope.row.labelId && !scope.row.labelName" style="font-size: 11px; color: #C0C4CC; display: block; margin-top: 2px;">跟随默认</span>
            </template>
          </el-table-column>
          <el-table-column label="可见" width="80" align="center">
            <template slot-scope="scope">
              <el-switch :value="scope.row.viewStatus !== null && scope.row.viewStatus !== undefined ? scope.row.viewStatus : importConfig.viewStatus"
                         @input="val => $set(scope.row, 'viewStatus', val)"
                         size="mini"
                         :active-value="true" :inactive-value="false"></el-switch>
              <span v-if="scope.row.viewStatus === null || scope.row.viewStatus === undefined"
                    style="color: #909399; font-size: 11px; display: block;">跟随默认</span>
            </template>
          </el-table-column>
          <el-table-column label="密码" width="120">
            <template slot-scope="scope">
              <el-input v-if="(scope.row.viewStatus !== null && scope.row.viewStatus !== undefined ? scope.row.viewStatus : importConfig.viewStatus) === false"
                        v-model="scope.row.password" size="mini" placeholder="使用默认"
                        clearable></el-input>
              <span v-else style="color: #C0C4CC; font-size: 12px;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="密码提示" width="120">
            <template slot-scope="scope">
              <el-input v-if="(scope.row.viewStatus !== null && scope.row.viewStatus !== undefined ? scope.row.viewStatus : importConfig.viewStatus) === false"
                        v-model="scope.row.tips" size="mini" placeholder="使用默认"
                        clearable></el-input>
              <span v-else style="color: #C0C4CC; font-size: 12px;">—</span>
            </template>
          </el-table-column>
          <el-table-column label="推荐" width="80" align="center">
            <template slot-scope="scope">
              <el-switch :value="scope.row.recommendStatus !== null && scope.row.recommendStatus !== undefined ? scope.row.recommendStatus : importConfig.recommendStatus"
                         @input="val => $set(scope.row, 'recommendStatus', val)"
                         size="mini"
                         :active-value="true" :inactive-value="false"></el-switch>
              <span v-if="scope.row.recommendStatus === null || scope.row.recommendStatus === undefined"
                    style="color: #909399; font-size: 11px; display: block;">跟随默认</span>
            </template>
          </el-table-column>
          <el-table-column label="内容长度" width="85" align="center">
            <template slot-scope="scope">
              <span style="font-size: 12px;">{{ (scope.row.articleContent || '').length }} 字</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template slot-scope="scope">
              <el-button type="text" icon="el-icon-delete" style="color: var(--orangeRed)"
                         @click="removeImportArticle(scope.$index)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 步骤3：导入进度 -->
      <div v-if="importStep === 3" style="padding: 20px 0;">
        <div style="text-align: center; margin-bottom: 20px;">
          <i v-if="!importFinished" class="el-icon-loading" style="font-size: 32px; color: #409EFF;"></i>
          <i v-else class="el-icon-circle-check" style="font-size: 32px; color: #67C23A;"></i>
          <p style="margin-top: 10px; font-size: 16px; color: #303133;">
            {{ importFinished ? '导入完成' : '正在并发导入（虚拟线程加速）...' }}
          </p>
          <p v-if="!importFinished" style="font-size: 12px; color: #909399; margin-top: 4px;">
            {{ importStreamConnecting ? '正在建立批量任务流连接...' : '所有文章已并发提交至后端，正在等待翻译与摘要实时进度...' }}
          </p>
          <p v-if="!importFinished && importStreamError" style="font-size: 12px; color: #E6A23C; margin-top: 4px;">
            {{ importStreamError }}
          </p>
        </div>
        <el-progress :percentage="importProgressPercent" :status="importProgressStatus"
                     :stroke-width="18" text-inside style="margin-bottom: 15px;"></el-progress>
        <p style="text-align: center; color: #606266; font-size: 14px;">
          {{ importProgress.current }} / {{ importProgress.total }}
          <span v-if="importProgress.partial > 0" style="color: #E6A23C;">
            （{{ importProgress.partial }} 篇部分完成）
          </span>
          <span v-if="importProgress.fail > 0" style="color: #F56C6C;">
            （{{ importProgress.fail }} 篇失败）
          </span>
        </p>
        <div v-if="importProgress.partialDetails.length > 0" style="margin-top: 15px;">
          <el-collapse>
            <el-collapse-item title="部分完成详情" name="partialDetails">
              <div v-for="(detail, idx) in importProgress.partialDetails" :key="'partial-' + idx"
                   style="padding: 5px 0; font-size: 12px; color: #E6A23C; border-bottom: 1px solid #EBEEF5;">
                <strong>{{ detail.title }}</strong>：{{ detail.message }}
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <div v-if="importTaskRows.length > 0" style="margin-top: 15px;">
          <el-collapse value="taskList">
            <el-collapse-item title="实时任务进度" name="taskList">
              <div v-for="task in importTaskRows" :key="task.taskId"
                   style="padding: 8px 0; border-bottom: 1px solid #EBEEF5;">
                <div style="display: flex; justify-content: space-between; gap: 12px;">
                  <strong style="color: #303133;">{{ task.title }}</strong>
                  <span :style="{ color: task.statusColor }">{{ task.statusLabel }}</span>
                </div>
                <div style="display: flex; align-items: center; gap: 10px; margin-top: 6px;">
                  <el-progress
                    :percentage="task.progress"
                    :status="task.progressStatus"
                    :stroke-width="10"
                    :show-text="false"
                    style="flex: 1;"
                  ></el-progress>
                  <span style="font-size: 12px; color: #909399; min-width: 86px; text-align: right;">
                    {{ task.stageLabel }}
                  </span>
                </div>
                <div style="font-size: 12px; color: #606266; margin-top: 4px;">{{ task.message }}</div>
                <div v-if="task.translationText" style="font-size: 12px; color: #909399; margin-top: 4px;">
                  {{ task.translationText }}
                </div>
                <div v-if="task.summaryText" style="font-size: 12px; color: #909399; margin-top: 4px;">
                  {{ task.summaryText }}
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <!-- 失败详情 -->
        <div v-if="importProgress.failDetails.length > 0" style="margin-top: 15px;">
          <el-collapse>
            <el-collapse-item title="失败详情" name="failDetails">
              <div v-for="(detail, idx) in importProgress.failDetails" :key="idx"
                   style="padding: 5px 0; font-size: 12px; color: #F56C6C; border-bottom: 1px solid #EBEEF5;">
                <strong>{{ detail.title }}</strong>：{{ detail.error }}
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>

      <div slot="footer" class="dialog-footer">
        <el-button @click="importDialogVisible = false" v-if="importStep !== 3 || importFinished">
          {{ importStep === 3 && importFinished ? '关闭' : '取消' }}
        </el-button>
        <el-button v-if="importStep === 1" type="primary" @click="parseImportFiles"
                   :disabled="importFileList.length === 0" :loading="importParsing">
          下一步：解析文件
        </el-button>
        <el-button v-if="importStep === 2" type="primary" @click="confirmImport"
                   :disabled="importArticles.length === 0">
          开始导入（{{ importArticles.length }} 篇）
        </el-button>
        <el-button v-if="importStep === 3 && importFinished" type="primary" @click="finishImport">
          完成
        </el-button>
      </div>
    </el-dialog>

    <!-- 导入-新建分类对话框 -->
    <el-dialog title="新建分类" :visible.sync="importNewSortDialog" width="500px"
               :close-on-click-modal="false" custom-class="centered-dialog" append-to-body>
      <el-form ref="importNewSortForm" :model="importNewSortForm" :rules="importNewSortRules" label-width="100px">
        <el-form-item label="分类类型" prop="sortType">
          <el-radio-group v-model="importNewSortForm.sortType">
            <el-radio-button :label="0">导航栏分类</el-radio-button>
            <el-radio-button :label="1">普通分类</el-radio-button>
          </el-radio-group>
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">
            <i class="el-icon-info"></i> 导航栏分类会显示在侧边栏"速览"模块中
          </div>
        </el-form-item>
        <el-form-item label="分类名称" prop="sortName">
          <el-input v-model="importNewSortForm.sortName" placeholder="请输入分类名称" maxlength="32" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="分类描述" prop="sortDescription">
          <el-input v-model="importNewSortForm.sortDescription" placeholder="请输入分类描述" maxlength="256" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="importNewSortForm.priority" :min="1" :max="999"></el-input-number>
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">
            <i class="el-icon-info"></i> 数字越小越靠前
          </div>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="importNewSortDialog = false">取消</el-button>
        <el-button type="primary" @click="importCreateNewSort" :loading="importNewSortLoading">确定</el-button>
      </div>
    </el-dialog>

    <!-- 导入-新建标签对话框 -->
    <el-dialog title="新建标签" :visible.sync="importNewLabelDialog" width="500px"
               :close-on-click-modal="false" custom-class="centered-dialog" append-to-body>
      <el-form ref="importNewLabelForm" :model="importNewLabelForm" :rules="importNewLabelRules" label-width="100px">
        <el-form-item label="所属分类">
          <el-input :value="importNewLabelSortName" disabled></el-input>
        </el-form-item>
        <el-form-item label="标签名称" prop="labelName">
          <el-input v-model="importNewLabelForm.labelName" placeholder="请输入标签名称" maxlength="32" show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="标签描述" prop="labelDescription">
          <el-input v-model="importNewLabelForm.labelDescription" placeholder="请输入标签描述" maxlength="256" show-word-limit></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="importNewLabelDialog = false">取消</el-button>
        <el-button type="primary" @click="importCreateNewLabel" :loading="importNewLabelLoading">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
    import { useMainStore } from '@/stores/main';
    import { setAdminContentLoading } from '@/utils/sessionValidation';
    import { getAdminLanguageMapping, getAdminLanguageName } from '@/utils/languageUtils';
    import { parseMarkdownFile, parseJsonFile, readFileAsText, getFileExtension, validateImportFile, smartDowngradeHeadings } from '@/utils/markdownImport';
    import { downgradeMarkdownHeadings } from '@/utils/markdownHeadingUtils';

  export default {
    data() {
      return {
        pagination: {
          current: 1,
          size: 10,
          total: 0,
          searchKey: "",
          recommendStatus: null,
          sortId: null,
          labelId: null
        },
        articles: [],
        // 页面内容加载状态（驱动全局"页面加载中"遮罩）
        loading: false,
        sorts: [],
        labels: [],
        labelsTemp: [],
        // 删除翻译相关数据
        deleteTranslationDialog: false,
        availableLanguages: [],
        selectedLanguages: [],
        currentArticle: null,
        deleteLoading: false,
        // 语言映射（从数据库统一配置读取，这里只是初始占位）
        languageMap: {
          'zh': '中文',
          'en': 'English'
        },
        // 导入文章相关数据
        importDialogVisible: false,
        importStep: 1,
        importFileList: [],
        importArticles: [],
        importParsing: false,
        importFinished: false,
        importStreamConnecting: false,
        importStreamError: '',
        importBatchRetryCount: 0,
        importBatchController: null,
        importBatchHeartbeatTimer: null,
        importBatchHeartbeatTimeoutMs: 25000,
        importTaskMap: {},
        importTaskOrder: [],
        importConfig: {
          sortId: null,
          labelId: null,
          viewStatus: true,
          commentStatus: true,
          recommendStatus: false,
          password: '',
          tips: ''
        },
        importLabelsTemp: [],
        importProgress: {
          current: 0,
          total: 0,
          success: 0,
          fail: 0,
          partial: 0,
          partialDetails: [],
          failDetails: []
        },
        // 导入-新建分类/标签相关
        importNewSortDialog: false,
        importNewSortLoading: false,
        importNewSortForm: {
          sortName: '',
          sortDescription: '',
          priority: 1,
          sortType: 0
        },
        importNewSortRules: {
          sortName: [
            { required: true, message: '请输入分类名称', trigger: 'blur' },
            { max: 32, message: '长度不能超过32个字符', trigger: 'blur' }
          ],
          sortDescription: [
            { required: true, message: '请输入分类描述', trigger: 'blur' },
            { max: 256, message: '长度不能超过256个字符', trigger: 'blur' }
          ]
        },
        importNewLabelDialog: false,
        importNewLabelLoading: false,
        importNewLabelForm: {
          labelName: '',
          labelDescription: '',
          sortId: null
        },
        importNewLabelRules: {
          labelName: [
            { required: true, message: '请输入标签名称', trigger: 'blur' },
            { max: 32, message: '长度不能超过32个字符', trigger: 'blur' }
          ]
        },
        // 记录新建标签时的来源：'config' 或文章索引号
        importNewSortSource: null,
        importNewLabelSource: null,
        // 导出相关
        exportAllLoading: false
      }
    },

    computed: {
      mainStore() {
        return useMainStore();
      },
      // 使用computed属性确保isBoss值能响应Store变化
      isBoss() {
        return this.mainStore.currentAdmin.isBoss;
      },
      /** 导入进度百分比 */
      importProgressPercent() {
        if (this.importProgress.total === 0) return 0;
        return Math.round((this.importProgress.current / this.importProgress.total) * 100);
      },
      /** 导入进度条状态 */
      importProgressStatus() {
        if (!this.importFinished) return '';
        if (this.importProgress.partial > 0) return 'warning';
        if (this.importProgress.fail === 0) return 'success';
        if (this.importProgress.success === 0) return 'exception';
        return 'warning';
      },
      importTaskRows() {
        return this.importTaskOrder.map(function(taskId) {
          return this.importTaskMap[taskId];
        }.bind(this)).filter(Boolean);
      },
      /** 需要密码的文章列表 */
      articlesNeedingPassword() {
        return this.importArticles.filter(article => article.password && article.password.trim() !== '');
      },
      /** 新建标签对话框中显示的分类名称 */
      importNewLabelSortName() {
        if (!this.importNewLabelForm.sortId) return '';
        var sort = this.sorts.find(function(s) { return s.id === this.importNewLabelForm.sortId; }.bind(this));
        return sort ? sort.sortName : '';
      },
      globalSearchKeyword() {
        return ((this.$route.query.search || '') + '').trim();
      }
    },

    watch: {
      'pagination.sortId'(newVal) {
        this.pagination.labelId = null;
        if (!this.$common.isEmpty(newVal) && !this.$common.isEmpty(this.labels)) {
          this.labelsTemp = this.labels.filter(l => l.sortId === newVal);
        }
      },
      // 监听Store中currentAdmin的变化
      'mainStore.currentAdmin': {
        handler(newAdmin, oldAdmin) {
          // 当管理员信息更新时，重新获取文章数据
          if (newAdmin && newAdmin.isBoss !== oldAdmin?.isBoss) {
            this.getArticles();
          }
        },
        deep: true
      },
      '$route.query.search': {
        immediate: true,
        handler(value) {
          this.pagination.searchKey = (value || '').toString().trim();
        }
      }
    },

    async created() {
      this.setContentLoading(true);
      // 加载后台管理用语言映射（中文）
      this.languageMap = await getAdminLanguageMapping();
      this.pagination.searchKey = ((this.$route.query.search || '') + '').trim();
      this.getArticles();
      this.getSortAndLabel();
    },

    mounted() {
    },

    beforeDestroy() {
      this.setContentLoading(false);
      this.stopImportBatchStream();
    },

    methods: {
      setContentLoading(loading) {
        if (this.loading === loading) {
          return;
        }
        this.loading = loading;
        setAdminContentLoading(loading);
      },
      getSortAndLabel() {
        this.$http.get(this.$constant.baseURL + "/webInfo/listSortAndLabel")
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.sorts = res.data.sorts;
              this.labels = res.data.labels;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      getArticleCoverUrl(article) {
        const cover = article && article.articleCover ? String(article.articleCover).trim() : '';
        return cover || this.getFallbackArticleCoverUrl(article);
      },
      getArticleCoverPreviewList(article) {
        const url = this.getArticleCoverUrl(article);
        return url ? [url] : [];
      },
      getFallbackArticleCoverUrl(article) {
        const covers = Array.isArray(this.mainStore.webInfo?.randomCover)
          ? this.mainStore.webInfo.randomCover.filter(item => item && String(item).trim())
          : [];

        if (covers.length > 0) {
          return covers[this.getStableCoverIndex(article, covers.length)];
        }

        return '/assets/backgroundPicture.jpg';
      },
      getStableCoverIndex(article, length) {
        if (!length) return 0;
        const key = String(article?.id || article?.articleTitle || '');
        let hash = 0;
        for (let i = 0; i < key.length; i++) {
          hash = ((hash << 5) - hash) + key.charCodeAt(i);
          hash |= 0;
        }
        return Math.abs(hash) % length;
      },
      getArticleCoverFallbackText(article) {
        return article?.articleTitle ? `「${article.articleTitle}」` : '暂无封面';
      },
      clearSearch() {
        this.pagination = {
          current: 1,
          size: 10,
          total: 0,
          searchKey: '',
          recommendStatus: null,
          sortId: null,
          labelId: null
        }
        if (this.globalSearchKeyword) {
          this.clearGlobalSearchFilter();
          return;
        }
        this.getArticles();
      },
      clearGlobalSearchFilter() {
        const nextQuery = { ...this.$route.query };
        delete nextQuery.search;
        this.$router.replace({ path: this.$route.path, query: nextQuery });
        this.pagination.current = 1;
        this.pagination.searchKey = '';
        this.getArticles();
      },
      getArticles() {
        let url = "";
        if (this.isBoss) {
          url = "/admin/article/boss/list";
        } else {
          url = "/admin/article/user/list";
        }
        this.$http.post(this.$constant.baseURL + url, this.pagination, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.articles = res.data.records;
              this.pagination.total = res.data.total;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          })
          .finally(() => {
            this.setContentLoading(false);
          });
      },
      handlePageChange(val) {
        this.pagination.current = val;
        this.getArticles();
      },
      searchArticles() {
        this.pagination.total = 0;
        this.pagination.current = 1;
        this.getArticles();
      },
      changeStatus(article, flag) {
        let param;
        if (flag === 1) {
          param = {
            articleId: article.id,
            viewStatus: article.viewStatus
          }
        } else if (flag === 2) {
          param = {
            articleId: article.id,
            commentStatus: article.commentStatus
          }
        } else if (flag === 3) {
          param = {
            articleId: article.id,
            recommendStatus: article.recommendStatus
          }
        }
        this.$http.get(this.$constant.baseURL + "/admin/article/changeArticleStatus", param, true)
          .then((res) => {
            if (flag === 1) {
              this.$message({
                duration: 0,
                showClose: true,
                message: "修改成功！注意，文章不可见时必须设置密码才能访问！",
                type: "warning"
              });
            } else {
              this.$message({
                message: "修改成功！",
                type: "success"
              });
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      handleDelete(item) {
        this.$confirm('确认删除？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true,
          customClass: 'mobile-responsive-confirm'
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/article/deleteArticle", {id: item.id}, true)
            .then((res) => {
              // 刷新文章列表
              this.pagination.current = 1;
              this.getArticles();
              
              this.$message({ message: "删除成功！", type: "success" });
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消删除!'
          });
        });
      },
      // 删除翻译相关方法
      async handleDeleteTranslation(item) {
        this.currentArticle = item;
        this.selectedLanguages = [];
        this.deleteTranslationDialog = true;
        
        // 获取可用翻译语言
        try {
          const response = await this.$http.get(this.$constant.baseURL + "/admin/article/getAvailableLanguages", {
            articleId: item.id
          }, true);
          
          if (response && response.data) {
            this.availableLanguages = response.data;
          } else {
            this.availableLanguages = [];
          }
        } catch (error) {
          this.$message({
            message: "获取翻译语言失败: " + error.message,
            type: "error"
          });
          this.availableLanguages = [];
        }
      },
      
      // 使用统一的后台管理语言映射工具（中文）
      getLanguageName: getAdminLanguageName,
      
      selectAllLanguages() {
        this.selectedLanguages = [...this.availableLanguages];
      },
      
      clearSelectedLanguages() {
        this.selectedLanguages = [];
      },
      
      async confirmDeleteSingleTranslation() {
        if (this.availableLanguages.length !== 1) return;
        
        this.deleteLoading = true;
        const deletedLanguage = this.availableLanguages[0];
        
        try {
          await this.$http.post(this.$constant.baseURL + "/admin/article/deleteTranslation", {
            articleId: this.currentArticle.id,
            language: deletedLanguage
          }, true);
          
          // 更新sitemap - 移除已删除翻译的URL
          await this.updateSitemapAfterTranslationDelete(this.currentArticle.id, [deletedLanguage]);
          
          this.$message({
            message: `成功删除 ${this.getLanguageName(deletedLanguage)} 翻译！`,
            type: "success"
          });
          
          this.deleteTranslationDialog = false;
        } catch (error) {
          this.$message({
            message: "删除翻译失败: " + error.message,
            type: "error"
          });
        } finally {
          this.deleteLoading = false;
        }
      },
      
      async confirmDeleteMultipleTranslations() {
        if (this.selectedLanguages.length === 0) return;
        
        this.deleteLoading = true;
        let successCount = 0;
        let failCount = 0;
        const deletedLanguages = [];
        
        // 如果选择了所有语言，使用删除所有翻译的接口
        if (this.selectedLanguages.length === this.availableLanguages.length) {
          try {
            await this.$http.post(this.$constant.baseURL + "/admin/article/deleteAllTranslations", {
              articleId: this.currentArticle.id
            }, true);
            
            // 更新sitemap - 移除所有翻译的URL
            await this.updateSitemapAfterTranslationDelete(this.currentArticle.id, this.availableLanguages);
            
            this.$message({
              message: "成功删除所有翻译！",
              type: "success"
            });
            
            this.deleteTranslationDialog = false;
            this.deleteLoading = false;
            return;
          } catch (error) {
            this.$message({
              message: "删除所有翻译失败: " + error.message,
              type: "error"
            });
            this.deleteLoading = false;
            return;
          }
        }
        
        // 逐个删除选中的翻译
        for (const language of this.selectedLanguages) {
          try {
            await this.$http.post(this.$constant.baseURL + "/admin/article/deleteTranslation", {
              articleId: this.currentArticle.id,
              language: language
            }, true);
            successCount++;
            deletedLanguages.push(language);
          } catch (error) {
            console.error(`删除 ${language} 翻译失败:`, error);
            failCount++;
          }
        }
        
        // 更新sitemap - 移除成功删除的翻译URL
        if (deletedLanguages.length > 0) {
          await this.updateSitemapAfterTranslationDelete(this.currentArticle.id, deletedLanguages);
        }
        
        // 显示结果消息
        if (failCount === 0) {
          this.$message({
            message: `成功删除 ${successCount} 个翻译！`,
            type: "success"
          });
        } else if (successCount === 0) {
          this.$message({
            message: `删除失败，共 ${failCount} 个翻译删除失败`,
            type: "error"
          });
        } else {
          this.$message({
            message: `部分成功：${successCount} 个删除成功，${failCount} 个删除失败`,
            type: "warning"
          });
        }
        
        this.deleteTranslationDialog = false;
        this.deleteLoading = false;
      },
      

      // 删除翻译后更新sitemap
      async updateSitemapAfterTranslationDelete(articleId, deletedLanguages) {
        try {
          // 调用Java后端的sitemap接口，sitemap功能已迁移到Java端
          const sitemapUrl = this.$constant.baseURL + '/admin/article/updateSitemap';
          
          // 为每个删除的语言发送移除请求
          for (const language of deletedLanguages) {
            try {
              await this.$http.post(sitemapUrl, {
                articleId: articleId,
                action: 'remove',
                language: language  // 传递语言参数，让Java端知道要移除哪个语言版本
              }, true);
              
            } catch (error) {
              console.error(`移除语言 ${language} 的sitemap条目失败:`, error);
            }
          }
          
        } catch (error) {
          console.error('更新sitemap失败:', error);
          // 不阻塞主流程，只记录错误
        }
      },
      
      handleEdit(item) {
        this.$router.push({path: '/postEdit', query: {id: item.id}});
      },
      handleView(item) {
        // 在新标签页中打开前台文章详情页
        // 使用 constant.frontendURL（已根据环境自动配置）
        const articleToken = item.articleSlug || item.id;
        const articleUrl = `${this.$constant.frontendURL}/article/${articleToken}`;
        window.open(articleUrl, '_blank');
      },

      // ============ 导入文章相关方法 ============

      /** 打开导入对话框 */
      openImportDialog() {
        this.resetImportDialog();
        this.importDialogVisible = true;
      },

      /** 重置导入对话框状态 */
      resetImportDialog() {
        this.importStep = 1;
        this.importFileList = [];
        this.importArticles = [];
        this.importParsing = false;
        this.importFinished = false;
        this.importConfig = {
          sortId: null,
          labelId: null,
          viewStatus: true,
          commentStatus: true,
          recommendStatus: false,
          password: '',
          tips: ''
        };
        this.importLabelsTemp = [];
        this.stopImportBatchStream();
        this.importStreamConnecting = false;
        this.importStreamError = '';
        this.importBatchRetryCount = 0;
        this.importTaskMap = {};
        this.importTaskOrder = [];
        this.importProgress = {
          current: 0,
          total: 0,
          success: 0,
          fail: 0,
          partial: 0,
          partialDetails: [],
          failDetails: []
        };
      },

      /** 文件选择变更 */
      handleImportFileChange(file, fileList) {
        // 限制文件数量
        if (fileList.length > 50) {
          this.$message({ message: '最多选择 50 个文件', type: 'warning' });
          fileList.splice(50);
        }
        this.importFileList = fileList;
      },

      /** 文件移除 */
      handleImportFileRemove(file, fileList) {
        this.importFileList = fileList;
      },

      /** 默认分类变更时联动标签 */
      handleImportSortChange(val) {
        if (val === 'new-sort') {
          this.importConfig.sortId = null;
          this.importNewSortSource = 'config';
          this.openImportNewSortDialog();
          return;
        }
        this.importConfig.labelId = null;
        if (!this.$common.isEmpty(val) && !this.$common.isEmpty(this.labels)) {
          this.importLabelsTemp = this.labels.filter(function(l) { return l.sortId === val; });
        } else {
          this.importLabelsTemp = [];
        }
      },

      /** 默认标签变更 */
      handleImportLabelChange(val) {
        if (val === 'new-label') {
          this.importConfig.labelId = null;
          this.importNewLabelSource = 'config';
          this.importNewLabelForm.sortId = this.importConfig.sortId;
          this.openImportNewLabelDialog();
        }
      },

      /** 单篇文章分类变更 */
      handleArticleSortChange(index, val) {
        var art = this.importArticles[index];
        if (val === 'new-sort') {
          this.$set(art, 'sortId', null);
          this.importNewSortSource = index;
          this.openImportNewSortDialog();
          return;
        }
        // 清空标签
        this.$set(art, 'labelId', null);
        // 如果选择了分类，清除文件中带来的 sortName
        if (val) {
          this.$set(art, 'sortName', '');
          this.$set(art, 'labelName', '');
        }
      },

      /** 单篇文章标签变更 */
      handleArticleLabelChange(index, val) {
        var art = this.importArticles[index];
        if (val === 'new-label') {
          this.$set(art, 'labelId', null);
          this.importNewLabelSource = index;
          this.importNewLabelForm.sortId = art.sortId;
          this.openImportNewLabelDialog();
          return;
        }
        // 如果选择了标签，清除文件中带来的 labelName
        if (val) {
          this.$set(art, 'labelName', '');
        }
      },

      /** 获取某篇文章可选的标签列表 */
      getLabelsForArticle(article) {
        var sortId = article.sortId;
        if (!sortId || !this.labels) return [];
        return this.labels.filter(function(l) { return l.sortId === sortId; });
      },

      /** 打开新建分类对话框 */
      openImportNewSortDialog() {
        this.importNewSortForm = { sortName: '', sortDescription: '', priority: 1, sortType: 0 };
        this.importNewSortDialog = true;
        this.$nextTick(function() {
          if (this.$refs.importNewSortForm) {
            this.$refs.importNewSortForm.clearValidate();
          }
        }.bind(this));
      },

      /** 打开新建标签对话框 */
      openImportNewLabelDialog() {
        this.importNewLabelForm = { labelName: '', labelDescription: '', sortId: this.importNewLabelForm.sortId };
        this.importNewLabelDialog = true;
        this.$nextTick(function() {
          if (this.$refs.importNewLabelForm) {
            this.$refs.importNewLabelForm.clearValidate();
          }
        }.bind(this));
      },

      /** 创建新分类 */
      importCreateNewSort() {
        this.$refs.importNewSortForm.validate(function(valid) {
          if (!valid) return;
          this.importNewSortLoading = true;
          var newSortName = this.importNewSortForm.sortName;
          var source = this.importNewSortSource;

          this.$http.post(this.$constant.baseURL + "/webInfo/saveSort", this.importNewSortForm)
            .then(function(res) {
              this.importNewSortLoading = false;
              if (res.code === 200) {
                this.$message.success('分类创建成功');
                this.importNewSortDialog = false;
                // 刷新分类标签列表
                this.getSortAndLabel();
                // 等刷新完后自动选中
                this.$nextTick(function() {
                  setTimeout(function() {
                    var newSort = this.sorts.find(function(s) { return s.sortName === newSortName; });
                    if (newSort) {
                      if (source === 'config') {
                        this.importConfig.sortId = newSort.id;
                        this.importConfig.labelId = null;
                        this.importLabelsTemp = this.labels.filter(function(l) { return l.sortId === newSort.id; });
                      } else if (typeof source === 'number' && this.importArticles[source]) {
                        this.$set(this.importArticles[source], 'sortId', newSort.id);
                        this.$set(this.importArticles[source], 'labelId', null);
                        this.$set(this.importArticles[source], 'sortName', '');
                        this.$set(this.importArticles[source], 'labelName', '');
                      }
                    }
                  }.bind(this), 200);
                }.bind(this));
              }
            }.bind(this))
            .catch(function(error) {
              this.importNewSortLoading = false;
              this.$message({ message: '创建分类失败: ' + error.message, type: 'error' });
            }.bind(this));
        }.bind(this));
      },

      /** 创建新标签 */
      importCreateNewLabel() {
        this.$refs.importNewLabelForm.validate(function(valid) {
          if (!valid) return;
          this.importNewLabelLoading = true;
          var createdLabelName = this.importNewLabelForm.labelName;
          var createdSortId = this.importNewLabelForm.sortId;
          var source = this.importNewLabelSource;

          this.$http.post(this.$constant.baseURL + "/webInfo/saveLabel", this.importNewLabelForm)
            .then(function(res) {
              this.importNewLabelLoading = false;
              if (res.code === 200) {
                this.$message.success('标签创建成功');
                this.importNewLabelDialog = false;
                // 刷新分类标签列表
                this.getSortAndLabel();
                this.$nextTick(function() {
                  setTimeout(function() {
                    var newLabel = this.labels.find(function(l) {
                      return l.labelName === createdLabelName && l.sortId === createdSortId;
                    });
                    if (newLabel) {
                      if (source === 'config') {
                        this.importLabelsTemp = this.labels.filter(function(l) { return l.sortId === createdSortId; });
                        this.importConfig.labelId = newLabel.id;
                      } else if (typeof source === 'number' && this.importArticles[source]) {
                        this.$set(this.importArticles[source], 'labelId', newLabel.id);
                        this.$set(this.importArticles[source], 'labelName', '');
                      }
                    }
                  }.bind(this), 200);
                }.bind(this));
              }
            }.bind(this))
            .catch(function(error) {
              this.importNewLabelLoading = false;
              this.$message({ message: '创建标签失败: ' + error.message, type: 'error' });
            }.bind(this));
        }.bind(this));
      },

      /** 解析所有选中的文件 */
      async parseImportFiles() {
        if (this.importFileList.length === 0) return;

        this.importParsing = true;
        this.importArticles = [];
        var errors = [];

        for (var i = 0; i < this.importFileList.length; i++) {
          var fileItem = this.importFileList[i];
          var rawFile = fileItem.raw;

          // 验证文件
          var validation = validateImportFile(rawFile);
          if (!validation.valid) {
            errors.push(validation.message);
            continue;
          }

          try {
            var content = await readFileAsText(rawFile);
            var ext = getFileExtension(rawFile.name);

            if (ext === 'json') {
              // JSON 文件可能包含多篇文章
              var jsonArticles = parseJsonFile(content);
              for (var j = 0; j < jsonArticles.length; j++) {
                this.importArticles.push(jsonArticles[j]);
              }
            } else {
              // Markdown / txt 文件
              var article = parseMarkdownFile(content, rawFile.name);
              this.importArticles.push(article);
            }
          } catch (e) {
            errors.push(rawFile.name + ': ' + e.message);
          }
        }

        this.importParsing = false;

        if (errors.length > 0) {
          this.$message({
            message: '部分文件解析失败：' + errors.join('；'),
            type: 'warning',
            duration: 5000
          });
        }

        if (this.importArticles.length > 0) {
          this.importStep = 2;
        } else {
          this.$message({ message: '没有解析到任何文章内容', type: 'error' });
        }
      },

      /** 从待导入列表中移除某篇 */
      removeImportArticle(index) {
        this.importArticles.splice(index, 1);
      },

      /** 撤销一级标题提取：将标题还原为文件名，正文恢复为包含 # 的原始内容 */
      undoH1Extract(index) {
        var art = this.importArticles[index];
        if (!art || !art.h1ExtractedAsTitle) return;

        // 恢复原始正文（包含 # 一级标题）
        art.articleContent = art.originalContent;
        // 标题清空，让用户手动输入
        art.articleTitle = '';
        // 更新标记
        art.h1ExtractedAsTitle = false;
        art.needManualTitle = true;

        // 触发 Vue 响应式更新
        this.$set(this.importArticles, index, Object.assign({}, art));

        this.$message({
          message: '已撤销标题提取，一级标题已恢复到正文中，请手动输入文章标题',
          type: 'info',
          duration: 3000
        });
      },

      /** 确认并开始导入 */
      async confirmImport() {
        if (this.importArticles.length === 0) return;

        // 校验：需要手动输入标题的文章不能为空
        var needTitleArticles = [];
        for (var t = 0; t < this.importArticles.length; t++) {
          if (this.importArticles[t].needManualTitle && !this.importArticles[t].articleTitle) {
            needTitleArticles.push(t + 1);
          }
        }
        if (needTitleArticles.length > 0) {
          this.$message({
            message: '第 ' + needTitleArticles.join('、') + ' 篇文章检测到多个一级标题，请手动输入文章标题',
            type: 'warning',
            duration: 5000
          });
          return;
        }

        // 校验：必须有分类和标签（文章自带的 sortId/sortName 或默认配置的 sortId）
        var needSortArticles = [];
        for (var i = 0; i < this.importArticles.length; i++) {
          if (!this.importArticles[i].sortId && !this.importArticles[i].sortName && !this.importConfig.sortId) {
            needSortArticles.push(i + 1);
          }
        }
        if (needSortArticles.length > 0) {
          this.$message({
            message: '第 ' + needSortArticles.join('、') + ' 篇文章未指定分类，请在下拉列表中选择分类、在默认配置中设置，或确保文件的 front-matter 包含 sort/category 字段',
            type: 'warning',
            duration: 5000
          });
          return;
        }

        var needLabelArticles = [];
        for (var k = 0; k < this.importArticles.length; k++) {
          if (!this.importArticles[k].labelId && !this.importArticles[k].labelName && !this.importConfig.labelId) {
            needLabelArticles.push(k + 1);
          }
        }
        if (needLabelArticles.length > 0) {
          this.$message({
            message: '第 ' + needLabelArticles.join('、') + ' 篇文章未指定标签，请在下拉列表中选择标签或在默认配置中设置',
            type: 'warning',
            duration: 5000
          });
          return;
        }

        // 校验：不可见的文章必须同时有密码和密码提示
        var needPasswordArticles = [];
        for (var p = 0; p < this.importArticles.length; p++) {
          var artItem = this.importArticles[p];
          // 确定该文章最终的可见状态：文章自身设置 > 默认配置
          var finalViewStatus = (artItem.viewStatus !== null && artItem.viewStatus !== undefined) ? artItem.viewStatus : this.importConfig.viewStatus;
          if (!finalViewStatus) {
            // 不可见，需要密码和密码提示
            var finalPassword = artItem.password || this.importConfig.password;
            var finalTips = artItem.tips || this.importConfig.tips;
            if (!finalPassword || !finalTips) {
              needPasswordArticles.push(p + 1);
            }
          }
        }
        if (needPasswordArticles.length > 0) {
          this.$message({
            message: '第 ' + needPasswordArticles.join('、') + ' 篇文章设为不可见，但缺少密码或密码提示。请在默认配置或文章列表中同时填写密码和密码提示',
            type: 'warning',
            duration: 5000
          });
          return;
        }

        // 重复标题检测
        var duplicates = this.checkDuplicateTitles();
        if (duplicates.length > 0) {
          try {
            await this.$confirm(
              '以下文章标题与已有文章重复，继续导入将创建同名文章：\n\n' +
              duplicates.map(function(d) { return '• ' + d; }).join('\n') +
              '\n\n是否继续？',
              '检测到重复标题',
              {
                confirmButtonText: '继续导入',
                cancelButtonText: '取消',
                type: 'warning',
                customClass: 'mobile-responsive-confirm'
              }
            );
          } catch (e) {
            return; // 用户取消
          }
        }

        // 进入导入步骤
        this.importStep = 3;
        this.importFinished = false;
        this.importStreamConnecting = false;
        this.importStreamError = '';
        this.importBatchRetryCount = 0;
        this.importTaskMap = {};
        this.importTaskOrder = [];
        this.importProgress = {
          current: 0,
          total: this.importArticles.length,
          success: 0,
          fail: 0,
          partial: 0,
          partialDetails: [],
          failDetails: []
        };

        // 查找默认分类名称和标签名称
        var defaultSortName = '';
        var defaultLabelName = '';
        if (this.importConfig.sortId) {
          var sortItem = this.sorts.find(function(s) { return s.id === this.importConfig.sortId; }.bind(this));
          if (sortItem) defaultSortName = sortItem.sortName;
        }
        if (this.importConfig.labelId) {
          var labelItem = this.importLabelsTemp.find(function(l) { return l.id === this.importConfig.labelId; }.bind(this));
          if (labelItem) defaultLabelName = labelItem.labelName;
        }

        // 构建所有文章的请求体
        var articleVOs = [];
        for (var m = 0; m < this.importArticles.length; m++) {
          var art = this.importArticles[m];

          var articleVO = {
            articleTitle: art.articleTitle || '未命名文章',
            articleContent: smartDowngradeHeadings(art.articleContent || '', downgradeMarkdownHeadings),
            articleCover: art.articleCover || '',
            videoUrl: art.videoUrl || '',
            password: art.password || this.importConfig.password || '',
            tips: art.tips || this.importConfig.tips || '',
            viewStatus: (art.viewStatus !== null && art.viewStatus !== undefined) ? art.viewStatus : this.importConfig.viewStatus,
            commentStatus: (art.commentStatus !== null && art.commentStatus !== undefined) ? art.commentStatus : this.importConfig.commentStatus,
            recommendStatus: (art.recommendStatus !== null && art.recommendStatus !== undefined) ? art.recommendStatus : this.importConfig.recommendStatus,
            submitToSearchEngine: false
          };

          // 分类和标签：优先使用文章自身的 sortId（下拉选择），其次 sortName（文件指定），最后默认配置
          if (art.sortId) {
            articleVO.sortId = art.sortId;
            if (art.labelId) {
              articleVO.labelId = art.labelId;
            } else if (art.labelName) {
              articleVO.labelName = art.labelName;
            } else {
              articleVO.labelId = this.importConfig.labelId;
            }
          } else if (art.sortName) {
            articleVO.sortName = art.sortName;
            if (art.labelName) {
              articleVO.labelName = art.labelName;
            } else {
              articleVO.labelName = defaultLabelName;
            }
          } else {
            articleVO.sortId = this.importConfig.sortId;
            articleVO.labelId = this.importConfig.labelId;
          }

          articleVOs.push(articleVO);
        }

        // 并发提交所有文章到异步保存接口（后端虚拟线程并发处理）
        var tasks = []; // { taskId, title, status }
        var concurrencyLimit = 10; // 并发提交限制，避免瞬间打爆后端

        for (var b = 0; b < articleVOs.length; b += concurrencyLimit) {
          var batch = articleVOs.slice(b, b + concurrencyLimit);
          var batchPromises = batch.map(function(vo) {
            return this.$http.post(
              this.$constant.baseURL + '/article/saveArticleAsync?skipAiTranslation=true',
              vo,
              true
            ).then(function(res) {
              return { taskId: res.data, title: vo.articleTitle, status: 'submitted' };
            }.bind(this)).catch(function(error) {
              // 提交就失败了（参数校验等），直接记为失败
              this.importProgress.fail++;
              this.importProgress.current++;
              this.importProgress.failDetails.push({
                title: vo.articleTitle,
                error: error.message || '提交失败'
              });
              return null;
            }.bind(this));
          }.bind(this));

          var batchResults = await Promise.all(batchPromises);
          for (var r = 0; r < batchResults.length; r++) {
            if (batchResults[r]) {
              tasks.push(batchResults[r]);
            }
          }
        }

        // 通过批量 SSE 监听所有任务状态，必要时回退到轮询
        if (tasks.length > 0) {
          await this.monitorImportTasks(tasks);
        }

        this.importFinished = true;

        // 导入完成汇总通知
        if (this.importProgress.fail === 0 && this.importProgress.partial === 0) {
          this.$message({
            message: '全部导入成功！共 ' + this.importProgress.success + ' 篇文章',
            type: 'success',
            duration: 4000
          });
        } else if (this.importProgress.fail === 0) {
          var successMsg = '全部导入成功！共 ' + this.importProgress.success + ' 篇文章';
          if (this.importProgress.partial > 0) {
            successMsg += '，' + this.importProgress.partial + ' 篇部分完成';
          }
          this.$message({
            message: successMsg,
            type: 'warning',
            duration: 4000
          });
        } else if (this.importProgress.success === 0 && this.importProgress.partial === 0) {
          this.$message({
            message: '导入失败！共 ' + this.importProgress.fail + ' 篇文章失败',
            type: 'error',
            duration: 5000
          });
        } else {
          var partialMsg = '导入完成：' + this.importProgress.success + ' 篇成功，' + this.importProgress.fail + ' 篇失败';
          if (this.importProgress.partial > 0) {
            partialMsg += '，' + this.importProgress.partial + ' 篇部分完成';
          }
          this.$message({
            message: partialMsg,
            type: 'warning',
            duration: 5000
          });
        }
      },

      /** 检测导入文章是否与已有文章标题重复 */
      checkDuplicateTitles() {
        var existingTitles = this.articles.map(function(a) { return a.articleTitle; });
        var duplicates = [];
        for (var i = 0; i < this.importArticles.length; i++) {
          var title = this.importArticles[i].articleTitle;
          if (title && existingTitles.indexOf(title) !== -1) {
            duplicates.push(title);
          }
        }
        return duplicates;
      },

      async monitorImportTasks(tasks) {
        this.initializeImportTaskMap(tasks);
        this.importStreamError = '';
        this.importBatchRetryCount = 0;

        while (!this.areAllImportTasksSettled()) {
          try {
            await this.consumeImportBatchStream(tasks);
          } catch (error) {
            if (this.areAllImportTasksSettled()) {
              break;
            }

            this.importBatchRetryCount++;
            if (this.importBatchRetryCount <= 2) {
              this.importStreamError = '任务流连接中断，正在恢复状态...';
              await this.syncImportTaskStatus(this.getUnsettledImportTasks());
              continue;
            }

            this.importStreamError = '任务流多次中断，正在回退到状态查询...';
            await this.pollImportTasks(this.getUnsettledImportTasks());
            break;
          }
        }

        this.stopImportBatchStream();
        this.importStreamConnecting = false;
        if (!this.importStreamError || this.areAllImportTasksSettled()) {
          this.importStreamError = '';
        }
      },

      initializeImportTaskMap(tasks) {
        this.importTaskOrder = tasks.map(function(task) { return task.taskId; });
        var taskMap = {};
        for (var i = 0; i < tasks.length; i++) {
          var task = tasks[i];
          taskMap[task.taskId] = {
            taskId: task.taskId,
            title: task.title,
            status: 'processing',
            stage: 'queued',
            message: '任务已提交，等待后端接管...',
            statusLabel: '处理中',
            statusColor: '#409EFF',
            stageLabel: '等待排队',
            progress: 5,
            progressStatus: '',
            translationText: '',
            summaryText: '',
            seoPushRequired: false,
            seoPushStatus: 'pending',
            seoPushMessage: '',
            finalized: false
          };
        }
        this.importTaskMap = taskMap;
        for (var j = 0; j < this.importTaskOrder.length; j++) {
          this.applyImportTaskVisualState(this.importTaskMap[this.importTaskOrder[j]]);
        }
      },

      getImportBaseURL() {
        return this.$constant && this.$constant.baseURL
          ? this.$constant.baseURL
          : (window.VueAppConfig && window.VueAppConfig.baseURL
            ? window.VueAppConfig.baseURL
            : (window.location.protocol + '//' + window.location.host));
      },

      getImportAuthHeaders() {
        return {
          'Content-Type': 'application/json'
        };
      },

      stopImportBatchStream() {
        if (this.importBatchHeartbeatTimer) {
          clearTimeout(this.importBatchHeartbeatTimer);
          this.importBatchHeartbeatTimer = null;
        }
        if (this.importBatchController) {
          this.importBatchController.abort();
          this.importBatchController = null;
        }
      },

      armImportBatchHeartbeat() {
        if (this.importBatchHeartbeatTimer) {
          clearTimeout(this.importBatchHeartbeatTimer);
        }
        this.importBatchHeartbeatTimer = setTimeout(function() {
          if (this.areAllImportTasksSettled()) {
            this.stopImportBatchStream();
            return;
          }
          this.importStreamError = '任务流连接中断，正在恢复状态...';
          this.stopImportBatchStream();
        }.bind(this), this.importBatchHeartbeatTimeoutMs);
      },

      async consumeImportBatchStream(tasks) {
        this.stopImportBatchStream();
        this.importStreamConnecting = true;
        var taskIds = tasks.map(function(task) { return task.taskId; }).join(',');
        var baseURL = this.getImportBaseURL();
        var headers = this.getImportAuthHeaders();
        var controller = new AbortController();
        this.importBatchController = controller;

        var response = await fetch(
          baseURL + '/article/streamArticleSaveStatusBatch?taskIds=' + encodeURIComponent(taskIds),
          {
            method: 'GET',
            headers: headers,
            credentials: 'include',
            signal: controller.signal
          }
        );
        if (!response.ok || !response.body) {
          throw new Error('导入任务流订阅失败');
        }

        this.importStreamConnecting = false;
        this.armImportBatchHeartbeat();
        var reader = response.body.getReader();
        var decoder = new TextDecoder();
        var buffer = '';
        var currentEventName = null;

        while (true) {
          var result = await reader.read();
          if (result.done) {
            this.stopImportBatchStream();
            if (this.areAllImportTasksSettled()) {
              return;
            }
            throw new Error('导入任务流已结束');
          }

          this.armImportBatchHeartbeat();
          buffer += decoder.decode(result.value, { stream: true });
          var lines = buffer.split('\n');
          if (lines[lines.length - 1] !== '') {
            buffer = lines.pop();
          } else {
            buffer = '';
          }

          for (var i = 0; i < lines.length; i++) {
            var trimmedLine = lines[i].trim();
            if (!trimmedLine) {
              currentEventName = null;
              continue;
            }
            if (trimmedLine.indexOf('event:') === 0) {
              currentEventName = trimmedLine.substring(6).trim();
              continue;
            }
            if (trimmedLine.indexOf('data:') !== 0) {
              continue;
            }

            var dataStr = trimmedLine.substring(5).trim();
            if (!dataStr) {
              continue;
            }

            try {
              var payload = JSON.parse(dataStr);
              this.handleImportTaskStreamEvent(currentEventName, payload || {});
            } catch (error) {
              console.error('解析导入任务流事件失败:', error);
            }
          }

          if (this.areAllImportTasksSettled()) {
            this.stopImportBatchStream();
            return;
          }
        }
      },

      handleImportTaskStreamEvent(eventName, payload) {
        this.armImportBatchHeartbeat();
        if (eventName === 'batch_heartbeat') {
          return;
        }

        if (!payload || !payload.taskId) {
          return;
        }

        var task = this.importTaskMap[payload.taskId];
        if (!task) {
          return;
        }

        if (eventName === 'title_delta' || eventName === 'content_delta') {
          var currentLength = payload.currentLength || 0;
          var fieldLabel = eventName === 'title_delta' ? '标题' : '正文';
          task.stage = 'translating';
          task.translationText = 'AI翻译' + fieldLabel + '已接收 ' + currentLength + ' 字';
          task.message = '正在流式翻译' + fieldLabel + '...';
          task.statusLabel = '翻译中';
          task.statusColor = '#409EFF';
          this.applyImportTaskVisualState(task);
          return;
        }

        if (eventName === 'translation_delta') {
          var translationLength = payload.currentLength || 0;
          task.stage = 'translating';
          task.translationText = 'AI翻译响应已接收 ' + translationLength + ' 字';
          task.message = payload.message || ('正在接收AI翻译响应... 已接收 ' + translationLength + ' 字');
          task.statusLabel = '翻译中';
          task.statusColor = '#409EFF';
          this.applyImportTaskVisualState(task);
          return;
        }

        if (eventName === 'summary_delta') {
          var summaryLength = payload.currentLength || 0;
          task.stage = 'generating_summary';
          task.summaryText = '摘要已接收 ' + summaryLength + ' 字';
          task.message = payload.message || ('正在流式生成摘要... 已接收 ' + summaryLength + ' 字');
          task.statusLabel = '摘要中';
          task.statusColor = '#409EFF';
          this.applyImportTaskVisualState(task);
          return;
        }

        if (eventName === 'summary_start') {
          task.stage = 'generating_summary';
          task.message = payload.message || '开始流式生成摘要...';
          task.statusLabel = '摘要中';
          task.statusColor = '#409EFF';
          this.applyImportTaskVisualState(task);
          return;
        }

        if (eventName === 'task_error' && payload.status === 'failed') {
          this.finalizeImportTask(task, payload);
          return;
        }

        this.updateImportTaskByStatus(task, payload);
      },

      updateImportTaskByStatus(task, taskStatus) {
        if (!taskStatus) {
          return;
        }

        task.status = taskStatus.status || task.status;
        task.stage = taskStatus.stage || task.stage;
        task.message = taskStatus.seoPushMessage || taskStatus.message || task.message;
        task.seoPushRequired = !!taskStatus.seoPushRequired;
        task.seoPushStatus = taskStatus.seoPushStatus || task.seoPushStatus;
        task.seoPushMessage = taskStatus.seoPushMessage || task.seoPushMessage;
        if (taskStatus.translationStatus === 'streaming' || taskStatus.translationStatus === 'retrying') {
          task.statusLabel = '翻译中';
          task.statusColor = '#409EFF';
        } else if (taskStatus.summaryStatus === 'streaming') {
          task.statusLabel = '摘要中';
          task.statusColor = '#409EFF';
        } else if (this.isImportTaskWaitingForSeoPush(taskStatus)) {
          task.statusLabel = '推送中';
          task.statusColor = '#409EFF';
        }

        if (taskStatus.summaryReceivedChars) {
          task.summaryText = '摘要已接收 ' + taskStatus.summaryReceivedChars + ' 字';
        }

        this.applyImportTaskVisualState(task, taskStatus);

        if (this.isImportTaskTerminal(taskStatus)) {
          this.finalizeImportTask(task, taskStatus);
        }
      },

      isImportTaskWaitingForSeoPush(taskStatus) {
        if (!taskStatus || !taskStatus.seoPushRequired) {
          return false;
        }
        return !taskStatus.seoPushStatus || taskStatus.seoPushStatus === 'pending' || taskStatus.seoPushStatus === 'pushing';
      },

      isImportTaskTerminal(taskStatus) {
        if (!taskStatus) {
          return false;
        }
        if (taskStatus.status !== 'success' && taskStatus.status !== 'partial_success' && taskStatus.status !== 'failed') {
          return false;
        }
        return !this.isImportTaskWaitingForSeoPush(taskStatus);
      },

      finalizeImportTask(task, taskStatus) {
        if (task.finalized) {
          task.status = taskStatus.status || task.status;
          task.message = taskStatus.seoPushMessage || taskStatus.message || task.message;
          return;
        }

        task.finalized = true;
        task.status = taskStatus.status || task.status;
        task.message = taskStatus.seoPushMessage || taskStatus.message || task.message;

        if (task.status === 'success') {
          task.statusLabel = '成功';
          task.statusColor = '#67C23A';
          this.importProgress.success++;
        } else if (task.status === 'partial_success') {
          task.statusLabel = '部分完成';
          task.statusColor = '#E6A23C';
          this.importProgress.partial++;
          this.importProgress.partialDetails.push({
            title: task.title,
            message: taskStatus.summaryMessage || taskStatus.message || '文章已发布，但摘要未完成'
          });
        } else {
          task.statusLabel = '失败';
          task.statusColor = '#F56C6C';
          this.importProgress.fail++;
          this.importProgress.failDetails.push({
            title: task.title,
            error: taskStatus.message || '保存失败'
          });
        }

        this.applyImportTaskVisualState(task, taskStatus);
        this.importProgress.current++;
      },

      applyImportTaskVisualState(task, taskStatus) {
        task.progress = this.inferImportTaskProgress(task, taskStatus);
        task.stageLabel = this.inferImportTaskStageLabel(task, taskStatus);
        task.progressStatus = this.inferImportTaskProgressStatus(task, taskStatus);
      },

      inferImportTaskProgress(task, taskStatus) {
        var status = (taskStatus && taskStatus.status) || task.status || 'processing';
        var stage = (taskStatus && taskStatus.stage) || task.stage || 'queued';
        var translationStatus = (taskStatus && taskStatus.translationStatus) || '';
        var summaryStatus = (taskStatus && taskStatus.summaryStatus) || '';
        var seoPushRequired = taskStatus && taskStatus.seoPushRequired;
        var seoPushStatus = (taskStatus && taskStatus.seoPushStatus) || '';

        if (status === 'success' || status === 'partial_success' || status === 'failed') {
          if (seoPushRequired && (!seoPushStatus || seoPushStatus === 'pending' || seoPushStatus === 'pushing')) {
            return seoPushStatus === 'pushing' ? 96 : 92;
          }
          return 100;
        }
        if (summaryStatus === 'streaming' || stage === 'generating_summary') {
          return 82;
        }
        if (translationStatus === 'streaming' || translationStatus === 'retrying' || stage === 'translating' || stage === 'translation_retry') {
          return 56;
        }
        if (stage === 'saving_translation') {
          return 68;
        }
        if (stage === 'translation_skipped') {
          return 72;
        }
        if (stage === 'db_saved') {
          return 28;
        }
        if (stage === 'queued') {
          return 5;
        }
        return 16;
      },

      inferImportTaskStageLabel(task, taskStatus) {
        var status = (taskStatus && taskStatus.status) || task.status || 'processing';
        var stage = (taskStatus && taskStatus.stage) || task.stage || 'queued';
        var translationStatus = (taskStatus && taskStatus.translationStatus) || '';
        var summaryStatus = (taskStatus && taskStatus.summaryStatus) || '';
        var seoPushRequired = taskStatus && taskStatus.seoPushRequired;
        var seoPushStatus = (taskStatus && taskStatus.seoPushStatus) || '';

        if (seoPushRequired && (!seoPushStatus || seoPushStatus === 'pending' || seoPushStatus === 'pushing')) return '搜索推送';
        if (status === 'success') return '已完成';
        if (status === 'partial_success') return '部分完成';
        if (status === 'failed') return '执行失败';
        if (summaryStatus === 'streaming' || stage === 'generating_summary') return '生成摘要';
        if (translationStatus === 'streaming' || stage === 'translating') return 'AI翻译';
        if (translationStatus === 'retrying' || stage === 'translation_retry') return '翻译重试';
        if (stage === 'saving_translation') return '保存翻译';
        if (stage === 'translation_skipped') return '跳过翻译';
        if (stage === 'db_saved') return '保存文章';
        if (stage === 'queued') return '等待排队';
        return '处理中';
      },

      inferImportTaskProgressStatus(task, taskStatus) {
        var status = (taskStatus && taskStatus.status) || task.status || 'processing';
        if (this.isImportTaskWaitingForSeoPush(taskStatus || task)) return '';
        if (status === 'success') return 'success';
        if (status === 'failed') return 'exception';
        if (status === 'partial_success') return 'warning';
        return '';
      },

      areAllImportTasksSettled() {
        if (!this.importTaskOrder.length) {
          return true;
        }
        for (var i = 0; i < this.importTaskOrder.length; i++) {
          var task = this.importTaskMap[this.importTaskOrder[i]];
          if (task && !task.finalized) {
            return false;
          }
        }
        return true;
      },

      getUnsettledImportTasks() {
        var tasks = [];
        for (var i = 0; i < this.importTaskOrder.length; i++) {
          var taskId = this.importTaskOrder[i];
          var task = this.importTaskMap[taskId];
          if (task && !task.finalized) {
            tasks.push({ taskId: task.taskId, title: task.title });
          }
        }
        return tasks;
      },

      async syncImportTaskStatus(tasks) {
        if (!tasks || tasks.length === 0) {
          return;
        }
        var statusPromises = tasks.map(function(task) {
          return this.$http.get(
            this.$constant.baseURL + '/article/getArticleSaveStatus',
            { taskId: task.taskId },
            true
          ).then(function(res) {
            return { task: task, status: res.data };
          }).catch(function(error) {
            return { task: task, status: { status: 'processing', message: error.message || '任务流恢复中' } };
          });
        }.bind(this));

        var statusResults = await Promise.all(statusPromises);
        for (var i = 0; i < statusResults.length; i++) {
          this.handleImportTaskStreamEvent('task_stage', statusResults[i].status);
        }
      },

      /**
       * 轮询所有异步导入任务的状态
       * @param {Array} tasks - [{ taskId, title, status }]
       */
      async pollImportTasks(tasks) {
        var pendingTasks = tasks.filter(function(task) {
          var localTask = this.importTaskMap[task.taskId];
          return !localTask || !localTask.finalized;
        }.bind(this)); // 浅拷贝，还未完成的任务列表
        var pollInterval = 2000; // 轮询间隔 2 秒
        // 动态超时：基础10分钟 + 每篇文章额外30秒（摘要生成可能受限流影响）
        var baseTimeout = 10 * 60 * 1000;
        var perArticleTimeout = 30 * 1000;
        var maxPollTime = Math.max(baseTimeout, tasks.length * perArticleTimeout);
        var maxPollMinutes = Math.round(maxPollTime / 60000);
        var startTime = Date.now();

        while (pendingTasks.length > 0) {
          // 超时保护
          if (Date.now() - startTime > maxPollTime) {
            // 剩余未完成的任务全部标记为超时失败
            for (var t = 0; t < pendingTasks.length; t++) {
              this.importProgress.fail++;
              this.importProgress.current++;
              this.importProgress.failDetails.push({
                title: pendingTasks[t].title,
                error: '任务超时（超过' + maxPollMinutes + '分钟未完成，可能是摘要生成限流）'
              });
            }
            break;
          }

          // 等待一个轮询间隔
          await new Promise(function(resolve) { setTimeout(resolve, pollInterval); });

          // 并发查询所有待处理任务的状态
          var statusPromises = pendingTasks.map(function(task) {
            return this.$http.get(
              this.$constant.baseURL + '/article/getArticleSaveStatus',
              { taskId: task.taskId },
              true
            ).then(function(res) {
              return { task: task, status: res.data };
            }).catch(function(error) {
              return { task: task, status: { status: 'failed', message: error.message || '查询状态失败' } };
            });
          }.bind(this));

          var statusResults = await Promise.all(statusPromises);

          // 处理已完成的任务
          var stillPending = [];
          for (var s = 0; s < statusResults.length; s++) {
            var result = statusResults[s];
            this.handleImportTaskStreamEvent('task_stage', result.status);
            if (!(result.status.status === 'success' || result.status.status === 'partial_success' || result.status.status === 'failed')) {
              // 仍在处理中
              stillPending.push(result.task);
            }
          }

          pendingTasks = stillPending;
        }
      },

      /** 导入完成后关闭对话框并刷新列表 */
      finishImport() {
        this.stopImportBatchStream();
        this.importDialogVisible = false;
        // 刷新文章列表
        this.pagination.current = 1;
        this.getArticles();
        // 刷新分类和标签（导入可能自动创建了新分类/标签）
        this.getSortAndLabel();

        // 显示最终汇总
        if (this.importProgress.success > 0 || this.importProgress.partial > 0) {
          var msg = '成功导入 ' + this.importProgress.success + ' 篇文章';
          if (this.importProgress.partial > 0) {
            msg += '，' + this.importProgress.partial + ' 篇部分完成';
          }
          if (this.importProgress.fail > 0) {
            msg += '，' + this.importProgress.fail + ' 篇失败';
          }
          this.$notify({
            title: '导入完成',
            message: msg,
            type: (this.importProgress.fail > 0 || this.importProgress.partial > 0) ? 'warning' : 'success',
            duration: 5000
          });
        }
      },

      // ============ 导出文章相关方法 ============

      /**
       * 将文章对象转换为带 front-matter 的 Markdown 字符串
       * @param {Object} article - 文章详情对象（ArticleVO）
       * @returns {string} Markdown 内容
       */
      articleToMarkdown(article) {
        var lines = ['---'];

        lines.push('title: "' + (article.articleTitle || '').replace(/"/g, '\\"') + '"');

        if (article.sort && article.sort.sortName) {
          lines.push('sort: "' + article.sort.sortName.replace(/"/g, '\\"') + '"');
        } else if (article.sortName) {
          lines.push('sort: "' + article.sortName.replace(/"/g, '\\"') + '"');
        }

        if (article.label && article.label.labelName) {
          lines.push('label: "' + article.label.labelName.replace(/"/g, '\\"') + '"');
        } else if (article.labelName) {
          lines.push('label: "' + article.labelName.replace(/"/g, '\\"') + '"');
        }

        if (article.articleCover) {
          lines.push('cover: "' + article.articleCover + '"');
        }

        if (article.videoUrl) {
          lines.push('video: "' + article.videoUrl + '"');
        }

        if (article.viewStatus === false) {
          lines.push('viewStatus: false');
          if (article.password) {
            lines.push('password: "' + article.password.replace(/"/g, '\\"') + '"');
          }
          if (article.tips) {
            lines.push('tips: "' + article.tips.replace(/"/g, '\\"') + '"');
          }
        }

        if (article.commentStatus === false) {
          lines.push('commentStatus: false');
        }

        if (article.recommendStatus === true) {
          lines.push('recommendStatus: true');
        }

        if (article.createTime) {
          lines.push('date: "' + article.createTime + '"');
        }

        lines.push('---');
        lines.push('');
        lines.push(article.articleContent || '');

        return lines.join('\n');
      },

      /**
       * 清理文件名中的非法字符
       * @param {string} name - 原始文件名
       * @returns {string} 安全的文件名
       */
      sanitizeFileName(name) {
        return (name || '未命名文章')
          .replace(/[\\/:*?"<>|]/g, '_')
          .replace(/\s+/g, '_')
          .substring(0, 100);
      },

      /**
       * 触发浏览器下载文件
       * @param {string} content - 文件内容
       * @param {string} fileName - 文件名
       * @param {string} mimeType - MIME 类型
       */
      downloadFile(content, fileName, mimeType) {
        var blob = new Blob([content], { type: mimeType });
        var url = URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
      },

      /**
       * 导出单篇文章
       * 从后端获取文章详情（包含完整 articleContent），然后导出为 Markdown 文件
       */
      async exportSingleArticle(article) {
        try {
          var res = await this.$http.get(
            this.$constant.baseURL + '/admin/article/getArticleById',
            { id: article.id },
            true
          );

          if (!res || !res.data) {
            this.$message({ message: '获取文章详情失败', type: 'error' });
            return;
          }

          var fullArticle = res.data;
          var markdown = this.articleToMarkdown(fullArticle);
          var fileName = this.sanitizeFileName(fullArticle.articleTitle) + '.md';

          this.downloadFile(markdown, fileName, 'text/markdown;charset=utf-8');

          this.$message({ message: '导出成功', type: 'success' });
        } catch (error) {
          this.$message({ message: '导出失败: ' + error.message, type: 'error' });
        }
      },

      /**
       * 导出所有文章
       * 分页获取所有文章详情，打包为 JSON 文件下载
       */
      async exportAllArticles() {
        this.exportAllLoading = true;

        try {
          // 1. 先获取所有文章ID列表（使用大 pageSize 分页查询）
          var allArticles = [];
          var pageSize = 50;
          var current = 1;
          var total = 0;

          // 第一次查询，获取 total
          var url = this.isBoss ? '/admin/article/boss/list' : '/admin/article/user/list';
          var firstRes = await this.$http.post(this.$constant.baseURL + url, {
            current: current,
            size: pageSize
          }, true);

          if (!firstRes || !firstRes.data) {
            this.$message({ message: '获取文章列表失败', type: 'error' });
            this.exportAllLoading = false;
            return;
          }

          total = firstRes.data.total;
          var records = firstRes.data.records || [];
          for (var r = 0; r < records.length; r++) {
            allArticles.push(records[r]);
          }

          // 获取剩余页
          var totalPages = Math.ceil(total / pageSize);
          for (var page = 2; page <= totalPages; page++) {
            var pageRes = await this.$http.post(this.$constant.baseURL + url, {
              current: page,
              size: pageSize
            }, true);
            if (pageRes && pageRes.data && pageRes.data.records) {
              var pageRecords = pageRes.data.records;
              for (var pr = 0; pr < pageRecords.length; pr++) {
                allArticles.push(pageRecords[pr]);
              }
            }
          }

          if (allArticles.length === 0) {
            this.$message({ message: '没有可导出的文章', type: 'warning' });
            this.exportAllLoading = false;
            return;
          }

          // 2. 逐篇获取文章详情（列表接口可能不含 articleContent）
          var exportData = [];
          var failCount = 0;

          this.$message({ message: '正在获取 ' + allArticles.length + ' 篇文章详情，请稍候...', type: 'info', duration: 3000 });

          for (var i = 0; i < allArticles.length; i++) {
            try {
              var detailRes = await this.$http.get(
                this.$constant.baseURL + '/admin/article/getArticleById',
                { id: allArticles[i].id },
                true
              );

              if (detailRes && detailRes.data) {
                var a = detailRes.data;
                exportData.push({
                  articleTitle: a.articleTitle || '',
                  articleContent: a.articleContent || '',
                  articleCover: a.articleCover || '',
                  videoUrl: a.videoUrl || '',
                  viewStatus: a.viewStatus,
                  commentStatus: a.commentStatus,
                  recommendStatus: a.recommendStatus,
                  password: a.password || '',
                  tips: a.tips || '',
                  sortName: (a.sort && a.sort.sortName) ? a.sort.sortName : '',
                  labelName: (a.label && a.label.labelName) ? a.label.labelName : '',
                  createTime: a.createTime || '',
                  updateTime: a.updateTime || ''
                });
              } else {
                failCount++;
              }
            } catch (e) {
              failCount++;
              console.error('获取文章详情失败, ID:', allArticles[i].id, e);
            }
          }

          if (exportData.length === 0) {
            this.$message({ message: '导出失败，未能获取任何文章详情', type: 'error' });
            this.exportAllLoading = false;
            return;
          }

          // 3. 生成 JSON 文件下载
          var jsonContent = JSON.stringify(exportData, null, 2);
          var now = new Date();
          var dateStr = now.getFullYear() + '' +
            String(now.getMonth() + 1).padStart(2, '0') +
            String(now.getDate()).padStart(2, '0') + '_' +
            String(now.getHours()).padStart(2, '0') +
            String(now.getMinutes()).padStart(2, '0') +
            String(now.getSeconds()).padStart(2, '0');
          var fileName = 'articles_export_' + dateStr + '.json';

          this.downloadFile(jsonContent, fileName, 'application/json;charset=utf-8');

          var successMsg = '成功导出 ' + exportData.length + ' 篇文章';
          if (failCount > 0) {
            successMsg += '，' + failCount + ' 篇获取失败';
            this.$message({ message: successMsg, type: 'warning', duration: 5000 });
          } else {
            this.$message({ message: successMsg, type: 'success' });
          }
        } catch (error) {
          this.$message({ message: '导出失败: ' + error.message, type: 'error' });
        } finally {
          this.exportAllLoading = false;
        }
      }
    }
  }
</script>

<style scoped>

  .handle-box {
    margin-bottom: 20px;
  }

  .handle-input {
    width: 150px;
    display: inline-block;
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
  }

  .article-cover-fallback {
    width: 100%;
    height: 100%;
    padding: 4px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--lightGreen);
    color: #fff;
    font-size: 10px;
    line-height: 1.2;
    text-align: center;
    word-break: break-word;
  }

  .pagination {
    margin: 20px 0;
    text-align: right;
  }

  .el-switch {
    margin: 5px;
  }

  /* 导入对话框样式 */
  ::v-deep .el-upload-dragger {
    width: 100%;
  }

  ::v-deep .el-upload {
    width: 100%;
  }

  /* 需要手动输入标题的输入框高亮 */
  .need-title-input ::v-deep .el-input__inner {
    border-color: #E6A23C;
    background-color: #FDF6EC;
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
    .handle-input {
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










