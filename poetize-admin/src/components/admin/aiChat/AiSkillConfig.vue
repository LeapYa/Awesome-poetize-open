<template>
  <div class="ai-skill-config">
    <!-- 顶部操作栏 -->
    <div class="pro-max-section">
      <div class="section-header" style="display: flex; justify-content: space-between; align-items: flex-start;">
        <div>
          <div class="section-title">
            <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" class="title-icon">
              <path d="M12 2L2 7l10 5 10-5-10-5z"></path>
              <path d="M2 17l10 5 10-5"></path>
              <path d="M2 12l10 5 10-5"></path>
            </svg>
            AI 技能配置
          </div>
          <div class="section-subtitle">管理 AI 在各场景下的行为规范。（提示：你也可以直接与主页看板娘对话，让 AI 自动为你创建技能）</div>
        </div>
        <div style="display: flex; gap: 8px; flex-wrap: wrap;">
          <el-select v-model="sceneFilter" size="small" placeholder="场景筛选" clearable style="width: 130px;" @change="fetchSkills">
            <el-option label="全部场景" value=""></el-option>
            <el-option label="评论" value="comment"></el-option>
            <el-option label="聊天" value="chat"></el-option>
            <el-option label="文章" value="article"></el-option>
            <el-option label="通用" value="universal"></el-option>
          </el-select>
          <el-button size="small" type="primary" plain @click="openInstallTextDialog">
            <i class="el-icon-edit-outline"></i> 粘贴安装
          </el-button>
          <el-upload
            :show-file-list="false"
            :before-upload="handleUploadBefore"
            :http-request="handleUploadRequest"
            accept=".md,.markdown,.txt">
            <el-button size="small" type="primary">
              <i class="el-icon-upload2"></i> 上传 .md 文件
            </el-button>
          </el-upload>
        </div>
      </div>


      <!-- Skill 列表 -->
      <div v-if="loading" class="loading-state">
        <i class="el-icon-loading"></i>
        <span>正在加载 Skill 列表...</span>
      </div>

      <div v-else-if="skills.length === 0" class="empty-state glass-card">
        <svg viewBox="0 0 24 24" width="48" height="48" stroke="currentColor" stroke-width="1" fill="none" class="empty-icon text-slate-300">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
          <polyline points="14 2 14 8 20 8"></polyline>
        </svg>
        <div class="empty-text">暂未安装任何 Skill</div>
        <el-button size="small" type="primary" plain class="mt-4" @click="openInstallTextDialog">粘贴安装第一个 Skill</el-button>
      </div>

      <div v-else class="skills-grid">
        <div v-for="skill in skills" :key="skill.id" class="glass-card skill-card">
          <div class="skill-card-header">
            <div class="skill-icon-wrapper">
              <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none">
                <path d="M12 2L2 7l10 5 10-5-10-5z"></path>
                <path d="M2 17l10 5 10-5"></path>
                <path d="M2 12l10 5 10-5"></path>
              </svg>
            </div>
            <div class="skill-info">
              <div class="skill-name-row">
                <span class="skill-name">{{ skill.skillName }}</span>
                <span v-if="skill.isBuiltin" class="tool-badge builtin-badge">内置</span>
              </div>
              <div class="skill-meta">
                <span class="scene-badge">{{ sceneLabel(skill.scene) }}</span>
                <span class="meta-item">· {{ skill.skillKey }}</span>
                <span v-if="skill.version" class="meta-item">· v{{ skill.version }}</span>
              </div>
            </div>
            <div class="skill-switch-container">
              <el-switch :value="skill.enabled" @change="toggleEnabled(skill)"></el-switch>
            </div>
          </div>
          <div class="skill-desc" :class="{ 'text-disabled': !skill.enabled }">{{ skill.description }}</div>
          <div class="skill-card-actions">
            <el-button size="mini" type="text" icon="el-icon-edit" @click="openEditDialog(skill)">编辑</el-button>
            <el-tooltip v-if="skill.isBuiltin" content="内置 Skill 不可删除" placement="top">
              <el-button size="mini" type="text" icon="el-icon-delete" disabled>删除</el-button>
            </el-tooltip>
            <el-button v-else size="mini" type="text" icon="el-icon-delete" class="danger-btn" @click="deleteSkill(skill)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog
      title="编辑 Skill"
      :visible.sync="editDialogVisible"
      width="720px"
      custom-class="centered-dialog"
      :close-on-click-modal="false"
      append-to-body>
      <el-form v-if="editDialogVisible" :model="editForm" label-width="100px" class="compact-form">
        <el-form-item label="显示名称">
          <el-input v-model="editForm.skillName" size="small" placeholder="Skill 显示名称"></el-input>
        </el-form-item>
        <el-form-item label="适用场景">
          <el-select v-model="editForm.scene" size="small" style="width: 100%;">
            <el-option label="评论" value="comment"></el-option>
            <el-option label="聊天" value="chat"></el-option>
            <el-option label="文章" value="article"></el-option>
            <el-option label="通用" value="universal"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="editForm.version" size="small" placeholder="1.0.0"></el-input>
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="editForm.author" size="small" placeholder="作者"></el-input>
        </el-form-item>
        <el-form-item label="SKILL.md">
          <el-input
            v-model="editForm.skillContent"
            type="textarea"
            :rows="16"
            placeholder="完整 SKILL.md 内容（含 YAML frontmatter）"></el-input>
            <div class="form-tip">支持配置 name, description, version, author, scene 等 frontmatter 字段。</div>
          </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="editDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </div>
    </el-dialog>

    <!-- 粘贴安装对话框 -->
    <el-dialog
      title="粘贴安装 Skill"
      :visible.sync="installDialogVisible"
      width="720px"
      custom-class="centered-dialog"
      :close-on-click-modal="false"
      append-to-body>
      <el-form v-if="installDialogVisible" label-width="100px" class="compact-form">
        <el-form-item label="选择场景">
          <el-select v-model="installScene" size="small" style="width: 160px;" @change="onInstallSceneChange">
            <el-option label="评论（comment）" value="comment"></el-option>
            <el-option label="聊天（chat）" value="chat"></el-option>
            <el-option label="文章（article）" value="article"></el-option>
            <el-option label="通用（universal）" value="universal"></el-option>
          </el-select>
          <el-button size="small" type="primary" plain style="margin-left: 8px;" @click="applyInstallTemplate">
            <i class="el-icon-document-copy"></i> 加载示例模板
          </el-button>
          <div class="form-tip scene-desc">{{ sceneGuidance[installScene] }}</div>
        </el-form-item>
        <el-form-item label="SKILL.md">
          <el-input
            v-model="installContent"
            type="textarea"
            :rows="16"
            placeholder="粘贴完整 SKILL.md 内容，或点击上方按钮加载示例模板"></el-input>
        </el-form-item>
        <el-form-item v-if="previewResult" label="解析预览">
          <div class="preview-box">
            <div><strong>name:</strong> {{ previewResult.name }}</div>
            <div><strong>description:</strong> {{ previewResult.description }}</div>
            <div><strong>version:</strong> {{ previewResult.version }}</div>
            <div><strong>scene:</strong> {{ previewResult.scene }}</div>
          </div>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="previewInstall">预览解析</el-button>
        <el-button size="small" @click="installDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="confirmInstallText">安装</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'AiSkillConfig',
  data() {
    return {
      skills: [],
      loading: true,
      saving: false,
      sceneFilter: '',
      editDialogVisible: false,
      installDialogVisible: false,
      editForm: {
        id: null,
        skillName: '',
        scene: 'comment',
        version: '1.0.0',
        author: '',
        skillContent: ''
      },
      installContent: '',
      previewResult: null,
      installScene: 'comment',
      sceneGuidance: {
        comment: '适用于访客对文章留言时的自动回复。',
        chat: '适用于用户与看板娘的对话交互。',
        article: '适用于文章的摘要生成或深度解读。',
        universal: '适用于所有场景的全局规则。'
      }
    };
  },
  mounted() {
    this.fetchSkills();
  },
  methods: {
    fetchSkills() {
      this.loading = true;
      const params = {};
      if (this.sceneFilter) {
        params.scene = this.sceneFilter;
      }
      this.$http.get(this.$constant.baseURL + '/webInfo/ai/skill/list', params, true)
        .then(res => {
          if (res.code === 200 && Array.isArray(res.data)) {
            this.skills = res.data;
          }
        })
        .catch(err => {
          console.error('加载 Skill 列表失败:', err);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    sceneLabel(scene) {
      const map = { comment: '评论', chat: '聊天', article: '文章', universal: '通用' };
      return map[scene] || scene;
    },
    openEditDialog(skill) {
      this.editForm = {
        id: skill.id,
        skillName: skill.skillName,
        scene: skill.scene,
        version: skill.version,
        author: skill.author,
        skillContent: skill.skillContent
      };
      this.editDialogVisible = true;
    },
    saveEdit() {
      if (!this.editForm.skillContent || !this.editForm.skillContent.trim()) {
        this.$message.warning('SKILL.md 内容不能为空');
        return;
      }
      this.saving = true;
      this.$http.put(this.$constant.baseURL + '/webInfo/ai/skill/update/' + this.editForm.id, this.editForm, true)
        .then(res => {
          if (res.code === 200) {
            this.$message.success('Skill 更新成功');
            this.editDialogVisible = false;
            this.fetchSkills();
          } else {
            this.$message.error(res.message || '更新失败');
          }
        })
        .catch(err => {
          this.$message.error('更新失败: ' + (err.message || err));
        })
        .finally(() => {
          this.saving = false;
        });
    },
    openInstallTextDialog() {
      this.installContent = '';
      this.previewResult = null;
      // 若当前已有场景筛选，默认带入该场景，降低选择成本
      this.installScene = this.sceneFilter || 'comment';
      this.installDialogVisible = true;
    },
    onInstallSceneChange() {
      // 仅更新场景，不自动覆盖用户已输入的内容
    },
    getSceneTemplate(scene) {
      const templates = {
        comment: '---\nname: friendly-reply\ndescription: 评论友好互动回复风格\nversion: 1.0.0\nauthor: \nscene: comment\n---\n\n# 评论友好互动回复\n\n## 触发条件\n当访客在文章下留言时，AI 自动生成回复。\n\n## 执行步骤\n1. 识别评论的核心意图（提问 / 感慨 / 互动）\n2. 以友善、平等的语气回应，避免说教\n3. 若是提问且能从文章内容回答，简明引用文章要点\n4. 适度使用一两个 emoji 或轻松表达，但不滥用\n\n## 输出要求\n- 控制在 2-4 句\n- 不重复评论原文\n- 不空泛敷衍（如"说得好""很有道理"）\n\n## 注意事项\n- 不涉及敏感、政治、广告内容\n- 不主动引导加群/关注',
        chat: '---\nname: code-review\ndescription: 聊天场景的代码审查助手\nversion: 1.0.0\nauthor: \nscene: chat\n---\n\n# 代码审查助手\n\n## 触发条件\n用户在聊天中粘贴代码并希望审查、找 bug、优化建议时。\n\n## 执行步骤\n1. 先通读代码，复述其意图（一句话）\n2. 按优先级指出问题：正确性 bug > 安全 > 性能 > 可读性\n3. 每个问题给出：位置、原因、修复示例\n4. 若整体良好，肯定优点后再给改进建议\n\n## 输出要求\n- 用 Markdown 代码块给出修复示例\n- 问题分点列出，避免大段文字\n- 不臆测未给出的上下文，必要时先提问\n\n## 注意事项\n- 不直接给"完美重构"，尊重用户现有架构\n- 涉及危险操作（如 SQL 拼接、反序列化）必须明确警告',
        article: '---\nname: article-summary\ndescription: 文章摘要与结构提取\nversion: 1.0.0\nauthor: \nscene: article\n---\n\n# 文章摘要与结构提取\n\n## 触发条件\n用户请求对文章做摘要、提炼要点、梳理结构时。\n\n## 执行步骤\n1. 通读文章，识别核心论点与支撑段落\n2. 输出一句话主旨\n3. 列出 3-5 个要点（每个不超过两句）\n4. 可选：标注文章结构（开头 / 论证 / 结尾）\n\n## 输出要求\n- 摘要忠于原文，不加入外部观点\n- 要点用列表呈现\n- 控制总长度在 200 字以内\n\n## 注意事项\n- 若文章信息量不足，如实说明而非编造\n- 不评价文章好坏，只做客观提取',
        universal: '---\nname: concise-output\ndescription: 全场景简洁输出风格\nversion: 1.0.0\nauthor: \nscene: universal\n---\n\n# 简洁输出风格\n\n## 触发条件\n所有场景均适用，作为通用的输出风格约束。\n\n## 执行步骤\n1. 优先用最少的字数传达完整信息\n2. 能用列表不用段落，能用短句不用长句\n3. 代码、命令、配置直接给可执行版本，不铺垫\n\n## 输出要求\n- 默认中文回答，技术术语可保留英文\n- 不使用"首先""其次""总而言之"等套话\n- 不重复用户的问题\n\n## 注意事项\n- 简洁不等于省略关键信息，安全性/正确性提示必须保留'
      };
      return templates[scene] || templates.comment;
    },
    applyInstallTemplate() {
      const tpl = this.getSceneTemplate(this.installScene);
      if (this.installContent && this.installContent.trim()) {
        this.$confirm('当前已输入内容，套用模板将覆盖，是否继续？', '提示', {
          confirmButtonText: '覆盖',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.installContent = tpl;
          this.previewResult = null;
        }).catch(() => {});
      } else {
        this.installContent = tpl;
        this.previewResult = null;
      }
    },
    previewInstall() {
      if (!this.installContent || !this.installContent.trim()) {
        this.$message.warning('请先粘贴 SKILL.md 内容');
        return;
      }
      this.$http.post(this.$constant.baseURL + '/webInfo/ai/skill/preview', { content: this.installContent }, true)
        .then(res => {
          if (res.code === 200 && res.data) {
            this.previewResult = res.data;
          } else {
            this.$message.error(res.message || '解析失败');
            this.previewResult = null;
          }
        })
        .catch(err => {
          this.$message.error('解析失败: ' + (err.message || err));
          this.previewResult = null;
        });
    },
    confirmInstallText() {
      if (!this.installContent || !this.installContent.trim()) {
        this.$message.warning('请先粘贴 SKILL.md 内容');
        return;
      }
      this.saving = true;
      this.$http.post(this.$constant.baseURL + '/webInfo/ai/skill/install/text', { content: this.installContent }, true)
        .then(res => {
          if (res.code === 200) {
            this.$message.success('Skill 安装成功');
            this.installDialogVisible = false;
            this.fetchSkills();
          } else {
            this.$message.error(res.message || '安装失败');
          }
        })
        .catch(err => {
          this.$message.error('安装失败: ' + (err.message || err));
        })
        .finally(() => {
          this.saving = false;
        });
    },
    handleUploadBefore(file) {
      const isMd = /\.(md|markdown|txt)$/i.test(file.name);
      if (!isMd) {
        this.$message.error('仅支持 .md / .markdown / .txt 文件');
        return false;
      }
      if (file.size > 64 * 1024) {
        this.$message.error('文件超过 64KB 上限');
        return false;
      }
      return true;
    },
    handleUploadRequest(options) {
      const formData = new FormData();
      formData.append('file', options.file);
      this.saving = true;
      this.$http.post(this.$constant.baseURL + '/webInfo/ai/skill/install', formData, true)
        .then(res => {
          if (res.code === 200) {
            this.$message.success('Skill 安装成功');
            this.fetchSkills();
          } else {
            this.$message.error(res.message || '安装失败');
          }
        })
        .catch(err => {
          this.$message.error('安装失败: ' + (err.message || err));
        })
        .finally(() => {
          this.saving = false;
        });
    },
    toggleEnabled(skill) {
      this.$http.post(this.$constant.baseURL + '/webInfo/ai/skill/toggle/' + skill.id, {}, true)
        .then(res => {
          if (res.code === 200) {
            this.$message.success(skill.enabled ? '已禁用' : '已启用');
            this.fetchSkills();
          } else {
            this.$message.error(res.message || '操作失败');
          }
        })
        .catch(err => {
          this.$message.error('操作失败: ' + (err.message || err));
        });
    },
    deleteSkill(skill) {
      this.$confirm(`确定删除 Skill「${skill.skillName}」吗？`, '删除确认', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$http.delete(this.$constant.baseURL + '/webInfo/ai/skill/delete/' + skill.id, {}, true)
          .then(res => {
            if (res.code === 200) {
              this.$message.success('删除成功');
              this.fetchSkills();
            } else {
              this.$message.error(res.message || '删除失败');
            }
          })
          .catch(err => {
            this.$message.error('删除失败: ' + (err.message || err));
          });
      }).catch(() => {});
    }
  }
};
</script>

<style scoped>
.ai-skill-config {
  padding: 4px 0;
}

.pro-max-section {
  margin-top: 0;
}

.section-header {
  margin-bottom: 16px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-icon {
  color: #409EFF;
}

.section-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #909399;
  gap: 12px;
}

.empty-icon {
  color: #c0c4cc;
}

.empty-text {
  font-size: 14px;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.glass-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.glass-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border-color: #c6e2ff;
}

.skill-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skill-card-header {
  display: flex;
  gap: 12px;
  align-items: center;
}

.skill-switch-container {
  margin-left: auto;
}

.skill-icon-wrapper {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #ecf5ff, #f0f9ff);
  color: #409EFF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.skill-info {
  flex: 1;
  min-width: 0;
}

.skill-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.skill-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color, #303133);
}

.skill-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}

.scene-badge {
  background: #f0f9ff;
  color: #409EFF;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.meta-item {
  white-space: nowrap;
}

.skill-desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-top: 4px;
}

.text-disabled {
  color: #a8abb2;
}

.skill-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px dashed #ebeef5;
  padding-top: 8px;
  margin-top: 4px;
}

.tool-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.5;
}

.builtin-badge {
  background: #f4f4f5;
  color: #909399;
  border: 1px solid #e9e9eb;
}

.active-badge {
  background: #f0f9ff;
  color: #409EFF;
  border: 1px solid #d9ecff;
}

.inactive-badge {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fde2e2;
}

.danger-btn {
  color: #f56c6c !important;
}

.danger-btn:hover {
  color: #f78989 !important;
}

.compact-form .el-form-item {
  margin-bottom: 14px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}

.scene-desc {
  margin-top: 6px;
  padding: 6px 10px;
  background: #f5f7fa;
  border-left: 3px solid #409eff;
  color: #606266;
  border-radius: 2px;
}



.preview-box {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}

.dialog-footer {
  text-align: right;
}

.mt-4 {
  margin-top: 16px;
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .skills-grid {
    grid-template-columns: 1fr;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start !important;
    gap: 12px;
  }

  .section-header > div:last-child {
    width: 100%;
  }
}
</style>
