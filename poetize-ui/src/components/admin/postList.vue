<template>
  <div>
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
    </div>
    <el-table :data="articles" border class="table" header-cell-class-name="table-header">
      <el-table-column prop="id" label="ID" width="55" align="center"></el-table-column>
      <el-table-column prop="username" label="作者" align="center"></el-table-column>
      <el-table-column prop="articleTitle" label="文章标题" align="center"></el-table-column>
      <el-table-column prop="sort.sortName" label="分类" align="center"></el-table-column>
      <el-table-column prop="label.labelName" label="标签" align="center"></el-table-column>
      <el-table-column prop="viewCount" label="浏览量" align="center"></el-table-column>
      <el-table-column prop="likeCount" label="点赞数" align="center"></el-table-column>
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
          <el-image lazy class="table-td-thumb" :src="scope.row.articleCover" fit="cover"></el-image>
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
      <el-table-column label="操作" width="180" align="center">
        <template slot-scope="scope">
          <el-button type="text" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button type="text" icon="el-icon-delete" style="color: var(--orangeRed)" @click="handleDelete(scope.row)">
            删除
          </el-button>
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
  </div>
</template>

<script>

  export default {
    data() {
      return {
        isBoss: this.$store.state.currentAdmin.isBoss,
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
        sorts: [],
        labels: [],
        labelsTemp: []
      }
    },

    computed: {},

    watch: {
      'pagination.sortId'(newVal) {
        this.pagination.labelId = null;
        if (!this.$common.isEmpty(newVal) && !this.$common.isEmpty(this.labels)) {
          this.labelsTemp = this.labels.filter(l => l.sortId === newVal);
        }
      }
    },

    created() {
      this.getArticles();
      this.getSortAndLabel();
    },

    mounted() {
    },

    methods: {
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
      clearSearch() {
        this.pagination = {
          current: 1,
          size: 10,
          total: 0,
          searchKey: "",
          recommendStatus: null,
          sortId: null,
          labelId: null
        }
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
          center: true
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/article/deleteArticle", {id: item.id}, true)
            .then((res) => {
              // 刷新文章列表
              this.pagination.current = 1;
              this.getArticles();

              // 调用 Python SEO API 增量移除 sitemap URL
              const seoUrl = this.$constant.pythonBaseURL + '/seo/removeArticleFromSitemap';
              this.$http.post(seoUrl, { id: item.id }, true)
                .then(() => {
                  this.$message({ message: "删除成功，sitemap 已更新！", type: "success" });
                })
                .catch(() => {
                  this.$message({ message: "文章已删，但 sitemap 更新失败，请稍后重试", type: "warning" });
                });
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
      handleEdit(item) {
        this.$router.push({path: '/postEdit', query: {id: item.id}});
      }
    }
  }
</script>

<style scoped>

  .handle-box {
    margin-bottom: 20px;
  }

  .handle-input {
    width: 160px;
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

  .pagination {
    margin: 20px 0;
    text-align: right;
  }

  .el-switch {
    margin: 5px;
  }
</style>
