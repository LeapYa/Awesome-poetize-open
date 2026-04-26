<template>
  <div>
    <div class="handle-box">
      <el-input v-model="pagination.searchKey" placeholder="草稿标题" class="handle-input mrb10"></el-input>
      <el-button type="primary" icon="el-icon-search" @click="searchDrafts">搜索</el-button>
      <el-button type="danger" @click="clearSearch">清除参数</el-button>
      <el-button type="primary" @click="$router.push({ path: '/postEdit' })">新建草稿</el-button>
    </div>

    <el-table :data="drafts" border class="table" header-cell-class-name="table-header">
      <el-table-column prop="id" label="草稿ID" min-width="210" show-overflow-tooltip></el-table-column>
      <el-table-column prop="titleCache" label="标题" min-width="220" show-overflow-tooltip>
        <template slot-scope="scope">
          <span>{{ scope.row.titleCache || '未命名草稿' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ownerUsername" label="创建者" width="120" align="center"></el-table-column>
      <el-table-column prop="lastEditorUsername" label="最后编辑人" width="120" align="center"></el-table-column>
      <el-table-column label="类型" width="180" align="center">
        <template slot-scope="scope">
          <span>{{ formatDraftType(scope.row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="协作者" min-width="180">
        <template slot-scope="scope">
          <div v-if="scope.row.collaborators && scope.row.collaborators.length">
            <el-tag
              v-for="item in scope.row.collaborators"
              :key="item.userId"
              size="mini"
              style="margin-right: 6px; margin-bottom: 4px;"
            >
              {{ item.username }}
            </el-tag>
          </div>
          <span v-else style="color: #909399;">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === 'PUBLISHING' ? 'warning' : 'success'" size="mini">
            {{ scope.row.status === 'PUBLISHING' ? '发布中' : '编辑中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" align="center"></el-table-column>
      <el-table-column label="操作" width="180" align="center">
        <template slot-scope="scope">
          <el-button type="text" icon="el-icon-edit" @click="openDraft(scope.row)">打开</el-button>
          <el-button type="text" icon="el-icon-delete" style="color: #F56C6C" @click="deleteDraft(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :current-page="pagination.current"
        :page-size="pagination.size"
        :total="pagination.total"
        @current-change="handlePageChange"
      >
      </el-pagination>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      pagination: {
        current: 1,
        size: 10,
        total: 0,
        searchKey: ''
      },
      drafts: []
    }
  },
  created() {
    this.loadDrafts()
  },
  methods: {
    loadDrafts() {
      this.$http.post(this.$constant.baseURL + '/admin/articleDraft/list', this.pagination, true)
        .then((res) => {
          if (res.code !== 200 || !res.data) {
            this.$message.error(res.message || '获取草稿列表失败')
            return
          }
          this.drafts = res.data.records || []
          this.pagination.total = res.data.total || 0
        })
        .catch((error) => {
          this.$message.error(error.message || '获取草稿列表失败')
        })
    },
    handlePageChange(page) {
      this.pagination.current = page
      this.loadDrafts()
    },
    searchDrafts() {
      this.pagination.current = 1
      this.loadDrafts()
    },
    clearSearch() {
      this.pagination.searchKey = ''
      this.pagination.current = 1
      this.loadDrafts()
    },
    openDraft(draft) {
      this.$router.push({ path: '/postEdit', query: { draftId: draft.id } })
    },
    formatDraftType(draft) {
      if (draft && draft.draftType === 'REVISION') {
        return `修订草稿 #${draft.articleId}`
      }
      return '新建草稿'
    },
    deleteDraft(draft) {
      const isRevisionDraft = draft && draft.draftType === 'REVISION'
      const confirmMessage = isRevisionDraft
        ? '删除后修订草稿内容无法恢复，原文章不会受影响，确认继续？'
        : '删除后草稿内容无法恢复，确认继续？'
      this.$confirm(confirmMessage, isRevisionDraft ? '删除修订草稿' : '删除草稿', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }).then(() => {
        this.$http.delete(this.$constant.baseURL + `/admin/articleDraft/${draft.id}`, {}, true)
          .then((res) => {
            if (res.code === 200) {
              this.$message.success(isRevisionDraft ? '修订草稿已删除' : '草稿已删除')
              this.loadDrafts()
              return
            }
            this.$message.error(res.message || '删除草稿失败')
          })
          .catch((error) => {
            this.$message.error(error.message || '删除草稿失败')
          })
      }).catch(() => {})
    }
  }
}
</script>
