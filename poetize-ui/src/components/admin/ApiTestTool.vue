<template>
  <div class="api-test-tool">
    <el-tabs v-model="activeTab" type="card">
      <el-tab-pane label="创建文章" name="create">
        <div style="margin-bottom: 15px;">
          <p>使用下面的表单测试文章创建API</p>
          <el-form :model="createForm" label-width="120px" size="small">
            <el-form-item label="文章标题">
              <el-input v-model="createForm.title" placeholder="请输入文章标题"></el-input>
            </el-form-item>
            <el-form-item label="文章内容">
              <el-input type="textarea" v-model="createForm.content" placeholder="支持Markdown格式" :rows="5"></el-input>
            </el-form-item>
            <el-form-item label="分类名称">
              <el-input v-model="createForm.sortName" placeholder="输入分类名称，不存在将自动创建"></el-input>
            </el-form-item>
            <el-form-item label="标签名称">
              <el-input v-model="createForm.labelName" placeholder="输入标签名称，不存在将自动创建"></el-input>
            </el-form-item>
            <el-form-item label="封面图片URL">
              <el-input v-model="createForm.cover" placeholder="可选"></el-input>
            </el-form-item>
            <el-form-item label="文章摘要">
              <el-input v-model="createForm.summary" placeholder="可选"></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="testCreateArticle" :loading="loading">测试创建</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
      
      <el-tab-pane label="查询文章" name="query">
        <div style="margin-bottom: 15px;">
          <p>测试文章查询API</p>
          <el-form :inline="true" :model="queryForm" size="small">
            <el-form-item label="分页">
              <el-input-number v-model="queryForm.current" :min="1" :max="100" placeholder="页码" style="width: 80px;"></el-input-number>
              <span style="margin: 0 5px;">/</span>
              <el-input-number v-model="queryForm.size" :min="1" :max="50" placeholder="每页数量" style="width: 80px;"></el-input-number>
            </el-form-item>
            <el-form-item label="搜索词">
              <el-input v-model="queryForm.searchKey" placeholder="可选" style="width: 150px;"></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="testQueryArticles" :loading="loading">查询列表</el-button>
            </el-form-item>
          </el-form>
          
          <el-form :inline="true" :model="detailForm" size="small" style="margin-top: 15px;">
            <el-form-item label="文章ID">
              <el-input-number v-model="detailForm.id" :min="1" placeholder="要查询的文章ID" style="width: 150px;"></el-input-number>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="testGetArticleDetail" :loading="loading">查询详情</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
      
      <el-tab-pane label="分类和标签" name="taxonomy">
        <div style="margin-bottom: 15px;">
          <p>测试分类和标签API</p>
          <el-button type="primary" @click="testGetCategories" :loading="loading" style="margin-right: 10px;">获取所有分类</el-button>
          <el-button type="primary" @click="testGetTags" :loading="loading">获取所有标签</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <div v-if="result">
      <el-divider>响应结果</el-divider>
      <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; max-height: 300px; overflow: auto;">{{result}}</pre>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ApiTestTool',
  props: {
    apiKey: {
      type: String,
      required: true
    },
    baseURL: {
      type: String,
      required: true
    }
  },
  data() {
    return {
      activeTab: 'create',
      loading: false,
      result: null,
      createForm: {
        title: '测试文章标题',
        content: '# 这是一个测试文章\n\n这是通过API测试工具创建的文章，支持**Markdown**格式。',
        sortName: '测试分类',
        labelName: '测试标签',
        cover: '',
        summary: '测试文章摘要'
      },
      queryForm: {
        current: 1,
        size: 10,
        searchKey: ''
      },
      detailForm: {
        id: null
      }
    }
  },
  methods: {
    testCreateArticle() {
      if (!this.createForm.title || !this.createForm.content) {
        this.$message.warning('文章标题和内容不能为空');
        return;
      }
      
      this.loading = true;
      
      const data = {
        title: this.createForm.title,
        content: this.createForm.content,
        sortName: this.createForm.sortName,
        labelName: this.createForm.labelName,
        cover: this.createForm.cover || undefined,
        summary: this.createForm.summary || undefined,
        viewStatus: true,
        commentStatus: true,
        submitToSearchEngine: false
      };
      
      this.$http.post(this.baseURL + "/api/article/create", data, {
        headers: {
          'X-API-KEY': this.apiKey
        }
      })
        .then(res => {
          this.result = JSON.stringify(res, null, 2);
          this.loading = false;
        })
        .catch(error => {
          this.result = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
          this.loading = false;
        });
    },
    
    testQueryArticles() {
      this.loading = true;
      
      const params = {
        current: this.queryForm.current,
        size: this.queryForm.size
      };
      
      if (this.queryForm.searchKey) {
        params.searchKey = this.queryForm.searchKey;
      }
      
      this.$http.get(this.baseURL + "/api/article/list", {
        params: params,
        headers: {
          'X-API-KEY': this.apiKey
        }
      })
        .then(res => {
          this.result = JSON.stringify(res, null, 2);
          this.loading = false;
        })
        .catch(error => {
          this.result = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
          this.loading = false;
        });
    },
    
    testGetArticleDetail() {
      if (!this.detailForm.id) {
        this.$message.warning('文章ID不能为空');
        return;
      }
      
      this.loading = true;
      
      this.$http.get(this.baseURL + "/api/article/" + this.detailForm.id, {
        headers: {
          'X-API-KEY': this.apiKey
        }
      })
        .then(res => {
          this.result = JSON.stringify(res, null, 2);
          this.loading = false;
        })
        .catch(error => {
          this.result = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
          this.loading = false;
        });
    },
    
    testGetCategories() {
      this.loading = true;
      
      this.$http.get(this.baseURL + "/api/categories", {
        headers: {
          'X-API-KEY': this.apiKey
        }
      })
        .then(res => {
          this.result = JSON.stringify(res, null, 2);
          this.loading = false;
        })
        .catch(error => {
          this.result = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
          this.loading = false;
        });
    },
    
    testGetTags() {
      this.loading = true;
      
      this.$http.get(this.baseURL + "/api/tags", {
        headers: {
          'X-API-KEY': this.apiKey
        }
      })
        .then(res => {
          this.result = JSON.stringify(res, null, 2);
          this.loading = false;
        })
        .catch(error => {
          this.result = JSON.stringify(error.response ? error.response.data : error.message, null, 2);
          this.loading = false;
        });
    }
  }
}
</script>

<style scoped>
.api-test-tool {
  margin-top: 10px;
}
</style> 