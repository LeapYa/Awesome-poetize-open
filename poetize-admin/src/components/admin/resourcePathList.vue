<template>
  <div>
    <div>
      <div v-if="routeSearchKeyword" style="margin-bottom: 12px; padding: 10px 14px; border-radius: 8px; background: #f4f8ff; color: #606266; display: flex; align-items: center; justify-content: space-between; gap: 12px;">
        <span>当前显示的是全局搜索结果：{{ routeSearchKeyword }}</span>
        <el-button type="text" @click="clearGlobalSearchFilter">清除全局筛选</el-button>
      </div>
      <div class="handle-box">
        <el-select clearable v-model="pagination.resourceType" placeholder="资源聚合类型" class="handle-select mrb10">
          <el-option
            v-for="(item, i) in resourceTypes"
            :key="i"
            :label="item.label"
            :value="item.value">
          </el-option>
        </el-select>
        <el-select clearable v-model="pagination.status" placeholder="状态" class="handle-select mrb10">
          <el-option key="1" label="启用" :value="true"></el-option>
          <el-option key="2" label="禁用" :value="false"></el-option>
        </el-select>
        <el-button type="primary" icon="el-icon-search" @click="search()">搜索</el-button>
        <el-button type="primary" @click="addResourcePathDialog = true">新增资源聚合</el-button>
      </div>
      <el-table :data="filteredResourcePaths" border class="table" header-cell-class-name="table-header">
        <el-table-column prop="id" label="ID" width="55" align="center"></el-table-column>
        <el-table-column prop="title" label="标题" align="center"></el-table-column>
        <el-table-column prop="classify" label="分类" align="center"></el-table-column>
        <el-table-column prop="introduction" label="简介" align="center"></el-table-column>
        <el-table-column label="封面" align="center">
          <template slot-scope="scope">
            <el-image lazy :preview-src-list="[scope.row.cover]" class="table-td-thumb" :src="scope.row.cover"
                      fit="cover"></el-image>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="链接" align="center"></el-table-column>

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

        <el-table-column prop="remark" label="备注" align="center"></el-table-column>
        <el-table-column prop="createTime" label="创建时间" align="center"></el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template slot-scope="scope">
            <el-button type="text" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="text" icon="el-icon-delete" style="color: var(--orangeRed)"
                       @click="handleDelete(scope.row)">
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

    <el-dialog title="图片"
               :visible.sync="coverDialog"
               width="25%"
               custom-class="centered-dialog"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <uploadPicture :isAdmin="true" :prefix="resourcePath.type + 'Cover'" @addPicture="addPicture"
                       :maxNumber="1"></uploadPicture>
      </div>
    </el-dialog>

    <el-dialog title="文件"
               :visible.sync="uploadDialog"
               width="25%"
               custom-class="centered-dialog"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <uploadPicture :isAdmin="true" :prefix="resourcePath.type + 'Url'" @addPicture="addFile" :maxSize="10"
                       :maxNumber="1" :listType="'text'" :accept="'image/*, video/*, audio/*'"></uploadPicture>
      </div>
    </el-dialog>

    <el-dialog title="额外背景图片"
               :visible.sync="extraBackgroundDialog"
               width="25%"
               custom-class="centered-dialog"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <uploadPicture :isAdmin="true" :prefix="'asideBackgroundExtra'" @addPicture="addExtraBackground" :maxSize="5"
                       :maxNumber="1"></uploadPicture>
      </div>
    </el-dialog>

    <el-dialog title="资源聚合"
               :visible.sync="addResourcePathDialog"
               width="50%"
               custom-class="centered-dialog"
               :before-close="clearDialog"
               :append-to-body="true"
               :close-on-click-modal="false"
               center>
      <div style="position: relative;">
        <div>
          <div class="myCenter">
            <el-radio-group v-model="resourcePath.type">
              <el-radio-button 
                v-for="item in resourceTypes" 
                :key="item.value"
                :label="item.value">
                {{ item.label }}
                <!-- 带提示的资源类型显示问号图标 - 使用 el-popover -->
                <el-popover
                  v-if="['contact', 'quickEntry', 'asideBackground'].includes(item.value)"
                  placement="right"
                  trigger="hover"
                  :append-to-body="true"
                  :open-delay="200"
                  popper-class="resource-type-help-popover">
                  <img 
                    :src="getHelpImage(item.value)" 
                    :alt="item.label + '使用说明'" 
                    class="help-image"
                    style="display: block; max-width: 300px; border-radius: 4px;" />
                  <i 
                    slot="reference"
                    class="el-icon-question type-tip-icon">
                  </i>
                </el-popover>
              </el-radio-button>
            </el-radio-group>
          </div>
          
          <div style="margin-bottom: 5px">
            标题：
            <span v-if="resourcePath.type === 'quickEntry'" style="color: #909399; font-size: 12px;">
              （支持图标占位符，如：[star]朋友圈、前往朋友圈[xiaoche]、联系我[heart]、快速访问[rocket]）
            </span>
            <span v-if="resourcePath.type === 'asideBackground'" style="color: #909399; font-size: 12px;">
              （自动填写，无需手动设置）
            </span>
          </div>
          <el-input maxlength="60" v-model="resourcePath.title" :disabled="resourcePath.type === 'asideBackground'" :placeholder="resourcePath.type === 'asideBackground' ? '自动填写：侧边栏背景' : ''"></el-input>
          <div style="margin-top: 10px;margin-bottom: 5px">分类：</div>
          <el-select v-if="resourcePath.type === 'friendUrl'" v-model="resourcePath.classify" placeholder="请选择分类" style="width: 100%">
            <el-option label="🌟青出于蓝" value="🌟青出于蓝"></el-option>
            <el-option label="🥇友情链接" value="🥇友情链接"></el-option>
          </el-select>
          <el-input v-else-if="['lovePhoto', 'funny', 'favorites', 'contact'].includes(resourcePath.type)"
                    maxlength="30" v-model="resourcePath.classify" placeholder="联系方式类型（如：社交媒体、邮箱等）"></el-input>
          <el-input v-else disabled maxlength="30" v-model="resourcePath.classify" placeholder=""></el-input>
          <div style="margin-top: 10px;margin-bottom: 5px">简介：</div>
          <el-input :disabled="!['friendUrl', 'favorites', 'siteInfo', 'contact'].includes(resourcePath.type)"
                    maxlength="1000" v-model="resourcePath.introduction" :placeholder="resourcePath.type === 'siteInfo' ? '网站描述' : (resourcePath.type === 'contact' ? '联系方式描述（可选）' : '')"></el-input>
          <div style="margin-top: 10px;margin-bottom: 5px">
            封面：
            <span v-if="resourcePath.type === 'contact'" style="color: #909399; font-size: 12px;">
              （有封面则只显示图标，无封面则显示标题文字（支持图标占位符）；支持URL或直接粘贴SVG代码自动上传）
            </span>
            <span v-if="resourcePath.type === 'asideBackground'" style="color: #909399; font-size: 12px;">
              （支持图片URL或CSS渐变代码，如：linear-gradient(-45deg, #e8d8b9, #eccec5)，必填）
            </span>
          </div>
          <div v-if="resourcePath.type === 'asideBackground'">
            <div style="margin-bottom: 5px">主背景层（最底层）：</div>
            <el-input 
              type="textarea"
              :rows="2"
              v-model="resourcePath.cover" 
              placeholder="图片URL或CSS代码，如：linear-gradient(-45deg, #e8d8b9, #eccec5, #a3e9eb, #bdbdf0, #eec1ea)">
            </el-input>
            <div style="width: 100%;margin-top: 5px; margin-bottom: 10px">
              <proButton :info="'上传主背景图片'"
                         @click.native="addResourcePathCover()"
                         :before="$constant.before_color_1"
                         :after="$constant.after_color_1">
              </proButton>
            </div>
            
            <div style="margin-bottom: 5px; margin-top: 15px">
              额外背景层（可选，用于堆叠效果）：
              <span style="color: #909399; font-size: 12px;">
                （支持图片URL或CSS代码，将堆叠在主背景之上）
              </span>
            </div>
            <el-input 
              type="textarea"
              :rows="2"
              v-model="resourcePath.extraBackground" 
              placeholder="可选，如：linear-gradient(#fff0, #ebfcfd 40%, #caeafa)">
            </el-input>
            <div style="width: 100%;margin-top: 5px">
              <proButton :info="'上传额外背景图片'"
                         @click.native="uploadExtraBackground()"
                         :before="$constant.before_color_1"
                         :after="$constant.after_color_1">
              </proButton>
            </div>
          </div>
          <div v-else style="display: flex">
            <el-input 
              v-model="resourcePath.cover" 
              :disabled="resourcePath.type === 'quickEntry'"
              @paste.native="handleCoverPaste"
              :placeholder="resourcePath.type === 'contact' ? '支持图片URL或直接粘贴SVG代码自动上传' : ''">
            </el-input>
            <div style="width: 66px;margin: 3.5px 0 0 10px">
              <proButton :info="'上传封面'"
                         @click.native="addResourcePathCover()"
                         :before="$constant.before_color_1"
                         :after="$constant.after_color_1">
              </proButton>
            </div>
          </div>
          <div style="margin-top: 10px;margin-bottom: 5px">链接：</div>
          <div style="display: flex">
            <el-input :disabled="!['friendUrl', 'funny', 'favorites', 'contact', 'quickEntry'].includes(resourcePath.type)"
                      v-model="resourcePath.url"
                      :placeholder="resourcePath.type === 'siteInfo' ? '自动获取（来自网站设置->网站地址）' : (resourcePath.type === 'contact' ? '联系方式链接（可选）' : (resourcePath.type === 'quickEntry' ? '跳转链接（必填）' : (resourcePath.type === 'asideBackground' ? '无需填写' : '')))"
                      :class="{'readonly-input': resourcePath.type === 'siteInfo'}"></el-input>
            <div style="width: 66px;margin: 3.5px 0 0 10px">
              <proButton :info="'上传文件'"
                         @click.native="addResourcePathUrl()"
                         :before="$constant.before_color_1"
                         :after="$constant.after_color_1">
              </proButton>
            </div>
          </div>
          
          <!-- 快捷入口按钮样式自定义 -->
          <template v-if="resourcePath.type === 'quickEntry'">
            <div style="margin-top: 10px;margin-bottom: 5px">
              按钮样式：
              <span style="color: #909399; font-size: 12px;">
                （可选，留空则使用默认样式）
              </span>
            </div>
            <div style="display: flex; gap: 10px;">
              <el-input v-model="resourcePath.btnWidth" placeholder="宽度（默认65%）" style="flex: 1;">
                <template slot="prepend">宽度</template>
              </el-input>
              <el-input v-model="resourcePath.btnHeight" placeholder="高度（默认35px）" style="flex: 1;">
                <template slot="prepend">高度</template>
              </el-input>
              <el-input v-model="resourcePath.btnRadius" placeholder="圆角（默认1rem）" style="flex: 1;">
                <template slot="prepend">圆角</template>
              </el-input>
            </div>
          </template>
          
          <!-- 联系方式图标样式自定义 -->
          <template v-if="resourcePath.type === 'contact'">
            <div style="margin-top: 10px;margin-bottom: 5px">
              图标样式：
              <span style="color: #909399; font-size: 12px;">
                （可选，留空则使用默认样式）
              </span>
            </div>
            <div style="display: flex; gap: 10px;">
              <el-input v-model="resourcePath.btnWidth" placeholder="宽度（默认25px）" style="flex: 1;">
                <template slot="prepend">宽度</template>
              </el-input>
              <el-input v-model="resourcePath.btnHeight" placeholder="高度（默认25px）" style="flex: 1;">
                <template slot="prepend">高度</template>
              </el-input>
              <el-input v-model="resourcePath.btnRadius" placeholder="圆角（默认0）" style="flex: 1;">
                <template slot="prepend">圆角</template>
              </el-input>
            </div>
          </template>
          
          <div style="margin-top: 10px;margin-bottom: 5px">资源类型：</div>
          <div style="margin-top: 10px;margin-bottom: 5px">
            备注：
            <span v-if="resourcePath.type === 'quickEntry' || resourcePath.type === 'contact' || resourcePath.type === 'asideBackground'" style="color: #909399; font-size: 12px;">
              （备注字段用于存储样式信息，请勿手动修改）
            </span>
          </div>
          <el-input :disabled="resourcePath.type === 'quickEntry' || resourcePath.type === 'contact' || resourcePath.type === 'asideBackground'"
                    maxlength="1000" v-model="resourcePath.remark" type="textarea" 
                    :placeholder="(resourcePath.type === 'quickEntry' || resourcePath.type === 'contact' || resourcePath.type === 'asideBackground') ? '自动生成，请勿手动填写' : ''"></el-input>
        </div>
        <div style="display: flex;margin-top: 30px" class="myCenter">
          <proButton :info="'提交'"
                     @click.native="addResourcePath()"
                     :before="$constant.before_color_2"
                     :after="$constant.after_color_2">
          </proButton>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>

  const uploadPicture = () => import( "../common/uploadPicture");
  const proButton = () => import( "../common/proButton");

  export default {
    components: {
      uploadPicture,
      proButton
    },
    data() {
      return {
        resourceTypes: [
          {label: "友链", value: "friendUrl"},
          {label: "图片", value: "lovePhoto"},
          {label: "音乐", value: "funny"},
          {label: "收藏夹", value: "favorites"},
          {label: "本站信息", value: "siteInfo"},
          {label: "联系方式", value: "contact"},
          {label: "快捷入口", value: "quickEntry"},
          {label: "侧边栏背景", value: "asideBackground"}
        ],
        pagination: {
          current: 1,
          size: 10,
          total: 0,
          resourceType: "",
          status: null
        },
        resourcePaths: [],
        coverDialog: false,
        uploadDialog: false,
        extraBackgroundDialog: false,
        addResourcePathDialog: false,
        isUpdate: false,
        resourcePath: {
          title: "",
          classify: "",
          introduction: "",
          cover: "",
          url: "",
          type: "",
          remark: "",
          btnWidth: "",
          btnHeight: "",
          btnRadius: "",
          extraBackground: ""
        }
      }
    },

    computed: {
      routeSearchKeyword() {
        return ((this.$route.query.search || '') + '').toLowerCase().replace(/\s+/g, '').trim();
      },
      filteredResourcePaths() {
        if (!this.routeSearchKeyword) {
          return this.resourcePaths;
        }

        return this.resourcePaths.filter((item) => {
          return [item.title, item.classify, item.introduction, item.type, item.url, item.remark].some((value) => {
            return ((value || '') + '').toLowerCase().replace(/\s+/g, '').includes(this.routeSearchKeyword);
          });
        });
      }
    },

    watch: {
      '$route.query': {
        handler() {
          this.applyRouteQuery();
          this.getResourcePaths();
        },
        deep: true
      }
    },

    created() {
      this.applyRouteQuery();
      this.getResourcePaths();
    },

    mounted() {
    },

    methods: {
      getHelpImage(type) {
        // 根据类型返回对应的帮助图片路径
        const tipImages = {
          'contact': '/assets/contactHelp.png',
          'quickEntry': '/assets/quickEntryHelp.png',
          'asideBackground': '/assets/asideBackgroundHelp.png'
        };
        return tipImages[type] || '';
      },
      async handleCoverPaste(event) {
        // 获取粘贴的文本
        const pastedText = (event.clipboardData || window.clipboardData).getData('text');
        
        // 检测是否是SVG代码
        if (pastedText.trim().startsWith('<svg') && pastedText.includes('</svg>')) {
          // 阻止默认粘贴行为
          event.preventDefault();
          
          // 显示上传中提示
          const loading = this.$loading({
            lock: true,
            text: '正在上传SVG文件...',
            spinner: 'el-icon-loading',
            background: 'rgba(0, 0, 0, 0.7)'
          });
          
          try {
            // 将SVG代码上传为文件
            const svgUrl = await this.uploadSvgAsFile(pastedText.trim());
            
            // 设置封面字段
            this.$nextTick(() => {
              this.resourcePath.cover = svgUrl;
            });
            
            loading.close();
            this.$message({
              message: "SVG已自动上传成功",
              type: "success"
            });
          } catch (error) {
            loading.close();
            this.$message({
              message: "SVG上传失败：" + error.message,
              type: "error"
            });
          }
        }
        // 如果不是SVG代码，使用默认粘贴行为
      },
      
      async uploadSvgAsFile(svgCode) {
        // 创建SVG Blob
        const blob = new Blob([svgCode], { type: 'image/svg+xml' });
        
        // 生成文件名（使用时间戳）
        const timestamp = Date.now();
        const filename = `icon_${timestamp}.svg`;
        
        // 创建File对象
        const file = new File([blob], filename, { type: 'image/svg+xml' });
        
        // 创建FormData
        const formData = new FormData();
        formData.append('file', file);
        formData.append('type', this.resourcePath.type || 'contact');
        // 使用专门的contactCover目录，后端会自动创建
        formData.append('relativePath', `contactCover/${filename}`);
        formData.append('storeType', 'local'); // 默认使用本地存储
        formData.append('originalName', filename);
        
        // 上传文件（cookie自动携带认证信息）
        const response = await fetch(this.$constant.baseURL + "/resource/upload", {
          method: 'POST',
          credentials: 'include',
          body: formData
        });
        
        const result = await response.json();
        
        if (result.code === 200 && result.data) {
          return result.data;
        } else {
          throw new Error(result.message || '上传失败');
        }
      },
      
      addPicture(res) {
        this.resourcePath.cover = res;
        this.coverDialog = false;
      },
      addFile(res) {
        this.resourcePath.url = res;
        this.uploadDialog = false;
      },
      addExtraBackground(res) {
        this.resourcePath.extraBackground = res;
        this.extraBackgroundDialog = false;
      },
      uploadExtraBackground() {
        if (this.addResourcePathDialog === false) {
          return;
        }
        if (this.resourcePath.type !== 'asideBackground') {
          this.$message({
            message: "仅侧边栏背景类型支持上传额外背景！",
            type: "error"
          });
          return;
        }
        this.extraBackgroundDialog = true;
      },
      addResourcePathUrl() {
        if (this.addResourcePathDialog === false) {
          return;
        }
        if (!['funny'].includes(this.resourcePath.type)) {
          this.$message({
            message: "请选择有效资源类型！",
            type: "error"
          });
          return;
        }
        this.uploadDialog = true;
      },
      addResourcePathCover() {
        if (this.addResourcePathDialog === false) {
          return;
        }
        if (this.$common.isEmpty(this.resourcePath.type)) {
          this.$message({
            message: "请选择资源类型！",
            type: "error"
          });
          return;
        }
        this.coverDialog = true;
      },
      addResourcePath() {
        // 侧边栏背景类型的特殊验证
        if (this.resourcePath.type === 'asideBackground') {
          if (this.$common.isEmpty(this.resourcePath.cover)) {
            this.$message({
              message: "侧边栏背景图片/CSS代码不能为空！",
              type: "error"
            });
            return;
          }
        } else {
          // 其他类型的常规验证
          if (this.$common.isEmpty(this.resourcePath.title) || this.$common.isEmpty(this.resourcePath.type)) {
            this.$message({
              message: "标题和资源类型不能为空！",
              type: "error"
            });
            return;
          }
        }
        
        const payload = JSON.parse(JSON.stringify(this.resourcePath));
        if (payload.type === 'siteInfo') {
          payload.url = '';
        }
        // 侧边栏背景自动设置标题
        if (payload.type === 'asideBackground') {
          payload.title = '侧边栏背景';
        }
        this.$http.post(this.$constant.baseURL + "/webInfo/" + (this.isUpdate ? "updateResourcePath" : "saveResourcePath"), payload, true)
          .then((res) => {
            this.$message({
              message: "保存成功！",
              type: "success"
            });
            this.addResourcePathDialog = false;
            this.clearDialog();
            this.search();
          })
      .catch((error) => {
        this.$message({
          message: error.message,
          type: "error"
        });
      });
      },
      applyRouteQuery() {
        const query = this.$route.query || {};
        this.pagination.current = 1;
        this.pagination.resourceType = query.resourceType || '';
        this.pagination.searchKey = query.search || '';
        this.pagination.size = query.search ? 500 : 10;
      },
      clearGlobalSearchFilter() {
        const nextQuery = { ...this.$route.query };
        delete nextQuery.search;
        delete nextQuery.resourceType;
        this.$router.replace({ path: this.$route.path, query: nextQuery });
      },
      search() {
        this.pagination.total = 0;
        this.pagination.current = 1;
        this.getResourcePaths();
      },
      getResourcePaths() {
        this.$http.post(this.$constant.baseURL + "/webInfo/listResourcePath", this.pagination, true)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.resourcePaths = res.data.records;
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
      changeStatus(item) {
        const payload = JSON.parse(JSON.stringify(item));
        if (payload.type === 'siteInfo') {
          payload.url = '';
        }
        this.$http.post(this.$constant.baseURL + "/webInfo/updateResourcePath", payload, true)
          .then((res) => {
            this.$message({
              message: "修改成功！",
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
      handlePageChange(val) {
        this.pagination.current = val;
        this.getResourcePaths();
      },
      handleDelete(item) {
        this.$confirm('确认删除？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true,
          customClass: 'mobile-responsive-confirm'
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/webInfo/deleteResourcePath", {id: item.id}, true)
            .then((res) => {
              this.search();
              this.$message({
                message: "删除成功！",
                type: "success"
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
        this.resourcePath = JSON.parse(JSON.stringify(item));
        if (this.resourcePath.type === 'siteInfo') {
          this.resourcePath.url = '';
        }
        this.addResourcePathDialog = true;
        this.isUpdate = true;
      },
      clearDialog() {
        this.isUpdate = false;
        this.addResourcePathDialog = false;
        this.resourcePath = {
          title: "",
          classify: "",
          introduction: "",
          cover: "",
          url: "",
          type: "",
          remark: "",
          btnWidth: "",
          btnHeight: "",
          btnRadius: "",
          extraBackground: ""
        }
      }
    }
  }
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
  }

  .pagination {
    margin: 20px 0;
    text-align: right;
  }

  .el-switch {
    margin: 5px;
  }

  /* 资源类型提示图标样式 */
  .type-tip-icon {
    display: inline-block;
    margin-left: 4px;
    font-size: 14px;
    cursor: help;
    opacity: 0.7;
    transition: opacity 0.3s ease;
    vertical-align: middle;
  }

  .type-tip-icon:hover {
    opacity: 1;
  }
</style>

<style>
/* 资源类型帮助提示 Popover 全局样式 - 暗色模式适配 */
.dark-mode .resource-type-help-popover {
  background-color: #2c2c2c !important;
  border-color: #404040 !important;
}

.dark-mode .resource-type-help-popover .popper__arrow::after {
  border-right-color: #2c2c2c !important;
  border-bottom-color: #2c2c2c !important;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .resource-type-help-popover {
    max-width: 90vw !important;
  }
  
  .resource-type-help-popover .help-image {
    max-width: 100% !important;
  }
}
</style>









