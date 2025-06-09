<template>
  <div>
    <el-tag effect="dark" class="my-tag">
      <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -3px;">
        <path d="M0 0h1024v1024H0V0z" fill="#202425" opacity=".01"></path>
        <path
          d="M682.666667 204.8h238.933333a34.133333 34.133333 0 0 1 34.133333 34.133333v648.533334a68.266667 68.266667 0 0 1-68.266666 68.266666h-204.8V204.8z"
          fill="#FFAA44"></path>
        <path
          d="M68.266667 921.6a34.133333 34.133333 0 0 0 34.133333 34.133333h785.066667a68.266667 68.266667 0 0 1-68.266667-68.266666V102.4a34.133333 34.133333 0 0 0-34.133333-34.133333H102.4a34.133333 34.133333 0 0 0-34.133333 34.133333v819.2z"
          fill="#11AA66"></path>
        <path
          d="M238.933333 307.2a34.133333 34.133333 0 0 0 0 68.266667h136.533334a34.133333 34.133333 0 1 0 0-68.266667H238.933333z m0 204.8a34.133333 34.133333 0 1 0 0 68.266667h409.6a34.133333 34.133333 0 1 0 0-68.266667H238.933333z m0 204.8a34.133333 34.133333 0 1 0 0 68.266667h204.8a34.133333 34.133333 0 1 0 0-68.266667H238.933333z"
          fill="#FFFFFF"></path>
      </svg>
      文章信息
    </el-tag>
    <el-form :model="article" :rules="rules" ref="ruleForm" label-width="150px"
             class="demo-ruleForm">
      <el-form-item label="标题" prop="articleTitle">
        <el-input v-model="article.articleTitle" maxlength="500" show-word-limit></el-input>
      </el-form-item>

      <el-form-item label="视频链接" prop="videoUrl">
        <el-input maxlength="1000" v-model="article.videoUrl"></el-input>
      </el-form-item>

      <el-form-item label="内容" prop="articleContent">
        <mavon-editor ref="md" @imgAdd="imgAdd" v-model="article.articleContent"/>
      </el-form-item>

      <el-form-item label="是否启用评论" prop="commentStatus">
        <el-tag :type="article.commentStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.commentStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.commentStatus"></el-switch>
      </el-form-item>

      <el-form-item label="是否推荐" prop="recommendStatus">
        <el-tag :type="article.recommendStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.recommendStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.recommendStatus"></el-switch>
      </el-form-item>

      <el-form-item label="是否可见" prop="viewStatus">
        <el-tag :type="article.viewStatus === false ? 'danger' : 'success'"
                disable-transitions>
          {{article.viewStatus === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.viewStatus"></el-switch>
      </el-form-item>

      <el-form-item label="推送至搜索引擎" prop="submitToSeo">
        <el-tag :type="article.submitToSeo === false ? 'info' : 'success'"
                disable-transitions>
          {{article.submitToSeo === false ? '否' : '是'}}
        </el-tag>
        <el-switch v-model="article.submitToSeo"></el-switch>
        <div class="tip-text">
          <i class="el-icon-info"></i> 
          是否在保存后自动推送文章到搜索引擎（百度、谷歌等）以便提高收录速度
        </div>
      </el-form-item>

      <el-form-item v-if="article.viewStatus === false" label="不可见时的访问密码" prop="password">
        <el-input maxlength="30" v-model="article.password"></el-input>
      </el-form-item>

      <el-form-item v-if="article.viewStatus === false" label="密码提示" prop="tips">
        <el-input maxlength="60" v-model="article.tips"></el-input>
      </el-form-item>

      <el-form-item label="封面" prop="articleCover">
        <div style="display: flex">
          <el-input v-model="article.articleCover"></el-input>
          <el-image class="table-td-thumb"
                    lazy
                    style="margin-left: 10px"
                    :preview-src-list="[article.articleCover]"
                    :src="article.articleCover"
                    fit="cover"></el-image>
        </div>
        <uploadPicture :isAdmin="true" :prefix="'articleCover'" style="margin-top: 10px" @addPicture="addArticleCover"
                       :maxSize="2"
                       :maxNumber="1"></uploadPicture>
      </el-form-item>
      <el-form-item label="分类" prop="sortId">
        <el-select v-model="article.sortId" placeholder="请选择分类">
          <el-option
            v-for="item in sorts"
            :key="item.id"
            :label="item.sortName"
            :value="item.id">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="标签" prop="labelId">
        <el-select v-model="article.labelId" placeholder="请选择标签">
          <el-option
            v-for="item in labelsTemp"
            :key="item.id"
            :label="item.labelName"
            :value="item.id">
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <div class="myCenter" style="margin-bottom: 22px">
      <el-button type="primary" @click="submitForm('ruleForm')">保存</el-button>
      <el-button type="danger" @click="resetForm('ruleForm')">重置所有修改</el-button>
    </div>
  </div>
</template>

<script>
  const uploadPicture = () => import("../common/uploadPicture");
  import axios from 'axios';

  export default {
    components: {
      uploadPicture
    },
    data() {
      return {
        id: this.$route.query.id,
        loading: null,
        article: {
          articleTitle: "",
          articleContent: "",
          commentStatus: true,
          recommendStatus: false,
          viewStatus: true,
          submitToSeo: false,
          password: "",
          tips: "",
          articleCover: "",
          videoUrl: "",
          sortId: null,
          labelId: null
        },
        sorts: [],
        labels: [],
        labelsTemp: [],
        rules: {
          articleTitle: [
            {required: true, message: '请输入标题', trigger: 'change'},
            {max: 500, message: '标题长度不能超过500个字符', trigger: 'change'}
          ],
          articleContent: [
            {required: true, message: '请输入内容', trigger: 'change'}
          ],
          commentStatus: [
            {required: true, message: '是否启用评论', trigger: 'change'}
          ],
          recommendStatus: [
            {required: true, message: '是否推荐', trigger: 'change'}
          ],
          viewStatus: [
            {required: true, message: '是否可见', trigger: 'change'}
          ],
          articleCover: [
            {required: true, message: '封面', trigger: 'change'}
          ],
          sortId: [
            {required: true, message: '分类', trigger: 'change'}
          ],
          labelId: [
            {required: true, message: '标签', trigger: 'blur'}
          ]
        }
      };
    },

    watch: {
      'article.sortId'(newVal, oldVal) {
        if (oldVal !== null) {
          this.article.labelId = null;
        }
        if (!this.$common.isEmpty(newVal) && !this.$common.isEmpty(this.labels)) {
          this.labelsTemp = this.labels.filter(l => l.sortId === newVal);
        }
      }
    },

    created() {
      this.getSortAndLabel();
    },

    methods: {
      // 图片上传处理
      imgAdd(pos, file) {
        try {
          let suffix = file.name.lastIndexOf('.') !== -1 ? file.name.substring(file.name.lastIndexOf('.')) : "";
          let key = "articlePicture" + "/" + this.$store.state.currentAdmin.username.replace(/[^a-zA-Z]/g, '') 
                    + this.$store.state.currentAdmin.id + new Date().getTime() 
                    + Math.floor(Math.random() * 1000) + suffix;

          let storeType = localStorage.getItem("defaultStoreType") || "local";

          let fd = new FormData();
          fd.append("file", file);
          fd.append("originalName", file.name);
          fd.append("key", key);
          fd.append("relativePath", key);
          fd.append("type", "articlePicture");
          fd.append("storeType", storeType);

          if (storeType === "local") {
            this.saveLocal(pos, fd);
          } else if (storeType === "qiniu") {
            this.saveQiniu(pos, fd);
          }
        } catch (error) {
          this.showError("图片上传准备失败", error);
        }
      },
      
      // 本地保存图片
      saveLocal(pos, fd) {
        this.$http.upload(this.$constant.baseURL + "/resource/upload", fd, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.$refs.md.$img2Url(pos, res.data);
            } else {
              this.showError("图片上传失败", "服务器未返回有效的图片URL");
            }
          })
          .catch((error) => {
            this.showError("图片本地上传失败", error);
          });
      },
      
      // 七牛云保存图片
      saveQiniu(pos, fd) {
        this.$http.get(this.$constant.baseURL + "/qiniu/getUpToken", {key: fd.get("key")}, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              fd.append("token", res.data);

              this.$http.uploadQiniu(this.$store.state.sysConfig.qiniuUrl, fd)
                .then((res) => {
                  if (!this.$common.isEmpty(res.key)) {
                    let url = this.$store.state.sysConfig['qiniu.downloadUrl'] + res.key;
                    let file = fd.get("file");
                    this.$common.saveResource(this, "articlePicture", url, file.size, file.type, file.name, "qiniu", true);
                    this.$refs.md.$img2Url(pos, url);
                  } else {
                    this.showError("七牛云上传失败", "未返回有效的图片密钥");
                  }
                })
                .catch((error) => {
                  this.showError("七牛云上传请求失败", error);
                });
            } else {
              this.showError("获取七牛云上传Token失败", "服务器未返回有效的Token");
            }
          })
          .catch((error) => {
            this.showError("获取七牛云上传Token失败", error);
          });
      },
      
      // 添加文章封面
      addArticleCover(res) {
        this.article.articleCover = res;
      },
      
      // 获取分类和标签
      getSortAndLabel() {
        this.startLoading("加载分类与标签中...");
        this.$http.get(this.$constant.baseURL + "/webInfo/listSortAndLabel")
          .then((res) => {
            this.stopLoading();
            if (!this.$common.isEmpty(res.data)) {
              this.sorts = res.data.sorts;
              this.labels = res.data.labels;
              if (!this.$common.isEmpty(this.id)) {
                this.getArticle();
              }
            } else {
              this.showError("获取分类与标签失败", "服务器返回数据为空");
            }
          })
          .catch((error) => {
            this.stopLoading();
            this.showError("获取分类与标签失败", error);
          });
      },
      
      // 获取文章信息
      getArticle() {
        this.startLoading("加载文章信息中...");
        this.$http.get(this.$constant.baseURL + "/admin/article/getArticleById", {id: this.id}, true)
          .then((res) => {
            this.stopLoading();
            if (!this.$common.isEmpty(res.data)) {
              this.article = res.data;
            } else {
              this.showError("获取文章信息失败", "服务器返回数据为空");
            }
          })
          .catch((error) => {
            this.stopLoading();
            this.showError("获取文章信息失败", error);
          });
      },
      
      // 提交表单
      submitForm(formName) {
        // 表单验证前预检查
        if (this.article.viewStatus === false && this.$common.isEmpty(this.article.password)) {
          this.showError("验证失败", "文章不可见时必须输入密码");
          return;
        }
        
        if (this.article.articleTitle && this.article.articleTitle.length > 500) {
          this.showError("验证失败", "文章标题长度不能超过500个字符");
          return;
        }

        // 正式表单验证
        this.$refs[formName].validate((valid) => {
          if (valid) {
            let url = this.$common.isEmpty(this.id) 
              ? "/article/saveArticle"
              : "/article/updateArticle";
              
            if (!this.$common.isEmpty(this.id)) {
              this.article.id = this.id;
            }
            
            this.saveArticle(this.article, url);
          } else {
            this.showError("验证失败", "请完善必填项");
          }
        });
      },
      
      // 重置表单
      resetForm(formName) {
        this.$refs[formName].resetFields();
        if (!this.$common.isEmpty(this.id)) {
          this.getArticle();
        }
      },
      
      // 保存文章
      saveArticle(article, url) {
        this.$confirm('确认保存文章？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          // 显示加载中
          this.startLoading("保存文章中...");
          
          // 记录保存请求数据，便于调试
          console.log('准备保存文章:', JSON.stringify(article, null, 2));
          
          // 发送保存请求
          this.$http.post(this.$constant.baseURL + url, article, true)
            .then(res => {
              this.stopLoading();
              
              // 记录完整响应用于调试
              console.log('文章保存响应:', JSON.stringify(res, null, 2));
              
              // 检查保存是否成功
              if (res.code === 200 || res.data === true) {
                // 显示成功通知
                this.$message({
                  message: '文章保存成功，翻译将在后台自动完成',
                  type: 'success',
                  duration: 3000,
                  offset: 20
                });
                
                // 处理SEO提交（如果需要）
                if (article.viewStatus && article.submitToSeo) {
                  // 先检查是否有现成的文章ID
                  let articleId = article.id;
                  
                  // 如果没有ID但是保存成功，尝试从响应中获取
                  if (!articleId && res.data && res.data.id) {
                    articleId = res.data.id;
                  }
                  
                  // 如果是新文章且找不到ID，可以尝试通过其他方式查询
                  if (!articleId && this.$common.isEmpty(this.id)) {
                    console.log('新建文章SEO提交: 尝试查询最新文章ID');
                    this.findAndSubmitLatestArticle(article.articleTitle);
                  } else if (articleId) {
                    console.log('准备SEO提交文章ID:', articleId);
                    this.safeSubmitSeo(articleId);
                  } else {
                    console.warn('无法确定文章ID，SEO提交未执行');
                    this.$message({
                      message: 'SEO提交未完成：无法确定文章ID',
                      type: 'warning',
                      offset: 80
                    });
                  }
                }
                
                // 快速跳转，无需等待
                setTimeout(() => {
                  this.$router.push({path: "/postList"});
                }, 800);
              } else {
                // 处理保存失败的情况
                this.handleSaveError(res);
              }
            })
            .catch(error => {
              this.stopLoading();
              console.error('保存文章网络请求失败:', error);
              
              this.showError("保存失败", error.message || "网络请求错误");
            });
        }).catch(() => {
          // 用户取消保存，无需任何操作
        });
      },
      
      // 查找并提交最新文章
      findAndSubmitLatestArticle(title) {
        console.log('尝试查找最新文章:', title);
        this.$message({
          message: '正在查找新保存的文章以提交SEO...',
          type: 'info',
          offset: 80 // 显示在保存成功通知下方
        });
        
        // 延迟一秒，等待后端处理完成
        setTimeout(() => {
          this.$http.post(this.$constant.baseURL + "/article/listArticle", {
            current: 1,
            size: 10,
            queryString: title
          }, true).then(res => {
            if (res.data && res.data.records && res.data.records.length > 0) {
              // 查找标题匹配且最新的文章
              const articles = res.data.records;
              const targetArticle = articles.find(a => a.articleTitle === title);
              
              if (targetArticle && targetArticle.id) {
                console.log('找到匹配文章:', targetArticle.id);
                this.safeSubmitSeo(targetArticle.id);
              } else {
                console.warn('未找到精确匹配的文章');
                // 如果没有精确匹配，使用第一篇文章
                if (articles[0] && articles[0].id) {
                  console.log('使用最新文章:', articles[0].id);
                  this.safeSubmitSeo(articles[0].id);
                } else {
                  this.showSeoError();
                }
              }
            } else {
              console.warn('查询文章列表失败或结果为空');
              this.showSeoError();
            }
          }).catch(err => {
            console.error('查询文章失败:', err);
            this.showSeoError();
          });
        }, 1000);
      },
      
      // 显示SEO错误
      showSeoError() {
        this.$message({
          message: 'SEO提交未完成：无法找到新保存的文章',
          type: 'warning',
          offset: 160 // 显示在其他通知下方
        });
      },
      
      // 安全地提交SEO（不阻塞主流程）
      safeSubmitSeo(articleId) {
        // 显示开始提交的通知
        this.$message({
          message: '正在提交至搜索引擎...',
          type: 'info',
          offset: 80 // 显示在保存成功通知下方
        });

        // 使用延时确保不影响主流程
        setTimeout(() => {
          try {
            // 使用单独HTTP请求避免依赖问题
            const xhr = new XMLHttpRequest();
            xhr.open('POST', this.$constant.pythonBaseURL + "/python/seo/submitArticle", true);
            xhr.setRequestHeader('Content-Type', 'application/json');
            xhr.timeout = 15000; // 15秒超时
            
            xhr.onload = () => {
              if (xhr.status >= 200 && xhr.status < 300) {
                console.log('SEO提交成功:', xhr.responseText);
                
                // 显示成功通知
                this.$message({
                  message: '文章已成功提交至搜索引擎索引',
                  type: 'success',
                  offset: 160 // 显示在开始提交通知下方
                });
                
                // 尝试解析和显示更详细的结果
                try {
                  const response = JSON.parse(xhr.responseText);
                  if (response && response.data) {
                    console.log('搜索引擎提交详情:', response.data);
                  }
                } catch (e) {
                  console.log('无法解析SEO提交响应详情:', e);
                }
              } else {
                console.error('SEO提交HTTP错误:', xhr.status, xhr.statusText, xhr.responseText);
                
                // 显示错误通知
                this.$message({
                  message: `SEO提交失败(${xhr.status}): ${xhr.statusText}`,
                  type: 'error',
                  offset: 160 // 显示在开始提交通知下方
                });
              }
            };
            
            xhr.onerror = () => {
              console.error('SEO提交网络错误');
              this.$message({
                message: 'SEO提交失败：网络连接错误',
                type: 'error',
                offset: 160 // 显示在开始提交通知下方
              });
            };
            
            xhr.ontimeout = () => {
              console.error('SEO提交请求超时');
              this.$message({
                message: 'SEO提交超时，搜索引擎可能暂时无法访问',
                type: 'warning',
                offset: 160 // 显示在开始提交通知下方
              });
            };
            
            // 发送SEO请求
            xhr.send(JSON.stringify({ articleId: articleId }));
          } catch (e) {
            console.error('SEO提交过程中发生异常:', e);
            this.$message({
              message: `SEO提交异常：${e.message || '未知错误'}`,
              type: 'error',
              offset: 160 // 显示在开始提交通知下方
            });
          }
        }, 100);
      },
      
      // 处理保存失败的情况
      handleSaveError(res) {
        // 详细记录错误
        console.error('保存文章失败，响应:', JSON.stringify(res, null, 2));
        
        // 提取错误消息
        let errorMsg = '服务器返回未知错误';
        if (res.message) {
          errorMsg = res.message;
        } else if (typeof res.data === 'string') {
          errorMsg = res.data;
        } else if (res.data && res.data.message) {
          errorMsg = res.data.message;
        }
        
        this.showError("保存失败", errorMsg);
      },
      
      // 辅助方法：显示加载中
      startLoading(text = "加载中...") {
        if (this.loading) {
          this.loading.close();
        }
        this.loading = this.$loading({
          lock: true,
          text: text,
          spinner: 'el-icon-loading',
          background: 'rgba(0, 0, 0, 0.7)'
        });
      },
      
      // 辅助方法：停止加载
      stopLoading() {
        if (this.loading) {
          this.loading.close();
          this.loading = null;
        }
      },
      
      // 辅助方法：显示成功通知
      showSuccess(title, message) {
        this.$message({
          message: message,
          type: 'success',
          offset: 50 // 显示在顶部，首个通知
        });
      },
      
      // 辅助方法：显示错误通知
      showError(title, error) {
        let errorMessage = typeof error === 'string' 
          ? error 
          : (error && error.message ? error.message : '未知错误');
        
        console.error(title + ':', errorMessage);
        
        this.$message({
          message: errorMessage,
          type: 'error',
          offset: 50 // 显示在顶部，首个通知
        });
      }
    }
  }
</script>

<style scoped>
  .my-tag {
    margin-bottom: 20px;
    width: 100%;
    text-align: left;
    background: var(--lightYellow);
    border: none;
    height: 40px;
    line-height: 40px;
    font-size: 16px;
    color: var(--black);
  }

  .table-td-thumb {
    border-radius: 2px;
    width: 40px;
    height: 40px;
  }

  .el-switch {
    margin-left: 10px;
  }

  .tip-text {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    display: inline-block;
  }

  .el-form-item {
    margin-bottom: 40px;
  }
</style>
