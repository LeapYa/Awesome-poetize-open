<template>
  <div ref="searchRoot" class="admin-config-search" :class="{ 'is-open': panelVisible, 'is-compact': isCompact }">
    <div class="search-trigger" :class="{ 'search-trigger-compact': isCompact }">
      <el-button
        v-if="isCompact"
        class="search-trigger-button"
        size="small"
        icon="el-icon-search"
        circle
        @click="togglePanel">
      </el-button>
      <el-input
        ref="searchInput"
        v-else
        v-model.trim="keyword"
        size="small"
        clearable
        placeholder="搜索配置、页面或功能"
        prefix-icon="el-icon-search"
        @focus="handleFocus"
        @input="handleInput"
        @keydown.native.esc="closePanel"
        @keydown.native.enter.prevent="handleEnter">
      </el-input>
    </div>

    <transition name="el-zoom-in-top">
      <div v-if="panelVisible" class="search-panel" @click.stop>
        <div class="panel-header">
          <span class="panel-title">后台搜索</span>
          <span v-if="hasKeyword" class="panel-meta">共 {{ totalResults }} 条结果</span>
          <span v-else class="panel-meta">输入关键词开始搜索</span>
        </div>

        <div v-if="isCompact" class="compact-search-box">
          <el-input
            ref="panelInput"
            v-model.trim="keyword"
            size="small"
            clearable
            placeholder="搜索配置、页面或功能"
            prefix-icon="el-icon-search"
            @input="handleInput"
            @keydown.native.esc="closePanel"
            @keydown.native.enter.prevent="handleEnter">
          </el-input>
        </div>

        <div v-if="hasKeyword" class="panel-body">
          <div v-if="fieldResults.length" class="result-group">
            <div class="group-title">功能入口</div>
            <button
              v-for="item in fieldResults"
              :key="item.id"
              type="button"
              class="result-item"
              @click="navigateTo(item)">
              <div class="result-main">
                <span class="result-label">{{ item.fieldLabel }}</span>
                <span class="result-page">{{ item.pageTitle }}</span>
              </div>
              <div class="result-sub">{{ item.sectionTitle }}</div>
            </button>
          </div>

          <div v-if="pageResults.length" class="result-group">
            <div class="group-title">相关页面</div>
            <button
              v-for="item in pageResults"
              :key="item.id"
              type="button"
              class="result-item"
              @click="navigateTo(item)">
              <div class="result-main">
                <span class="result-label">{{ item.pageTitle }}</span>
              </div>
              <div class="result-sub">{{ item.sectionTitle }}</div>
            </button>
          </div>

          <div v-if="!totalResults" class="empty-state">
            <i class="el-icon-search"></i>
            <span>没有找到匹配的功能或配置</span>
          </div>
        </div>

        <div v-else class="panel-suggestions">
          <div class="suggestion-title">试试这些关键词</div>
          <div class="suggestion-tags">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              type="button"
              class="suggestion-tag"
              @mousedown.prevent.stop="applySuggestion(suggestion)">
              {{ suggestion }}
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { adminConfigSearchIndex } from '@/utils/adminConfigSearchIndex';
import { useMainStore } from '@/stores/main';

const DEFAULT_SUGGESTIONS = [
  '网站标题',
  'SMTP',
  '导航栏',
  '看板娘',
  'robots',
  '七牛云',
  '新建文章'
];

const CONFIG_TYPE_LABELS = {
  '1': '私有配置',
  '2': '公开配置'
};

const PLUGIN_TYPE_LABELS = {
  mouse_click_effect: '鼠标效果管理',
  particle_effect: '全屏飘落特效管理',
  waifu_model: '看板娘模型管理',
  editor: '文章编辑器管理',
  article_theme: '文章主题管理',
  payment: '文章付费管理',
  ai_tool: 'AI工具管理'
};

const PLUGIN_TYPE_TERMS = {
  mouse_click_effect: ['鼠标点击效果', '点击特效', '鼠标特效'],
  particle_effect: ['全屏飘落特效', '全局飘落特效', '全局飘落', '全屏特效', '全屏粒子特效', '动态粒子特效', '樱花飘落', '背景动画'],
  waifu_model: ['看板娘', 'Live2D', '模型插件'],
  editor: ['文章编辑器', 'Markdown编辑器', '编辑器插件'],
  article_theme: ['文章主题', '阅读主题', '主题插件'],
  payment: ['文章付费', '付费阅读', '支付插件'],
  ai_tool: ['AI工具', '联网搜索', '工具插件', '外部工具']
};

const RESOURCE_PATH_TYPE_LABELS = {
  friendUrl: '友链',
  lovePhoto: '图片',
  funny: '音乐',
  favorites: '收藏夹',
  siteInfo: '本站信息',
  contact: '联系方式',
  quickEntry: '快捷入口',
  asideBackground: '侧边栏背景'
};

const RESOURCE_PATH_TYPE_TERMS = {
  friendUrl: ['友链', '友情链接', '网站推荐'],
  lovePhoto: ['图片', '恋爱照片', '相册'],
  funny: ['音乐', '歌曲', '音频资源'],
  favorites: ['收藏夹', '网址收藏', '快捷收藏'],
  siteInfo: ['本站信息', 'siteInfo', '网站名片'],
  contact: ['联系方式', '社交媒体', '联系图标'],
  quickEntry: ['快捷入口', '快速入口', '侧边按钮'],
  asideBackground: ['侧边栏背景', '右侧背景', '渐变背景']
};
const RESOURCE_TYPE_LABELS = {
  orphanResource: '孤儿资源',
  invalidResource: '无效资源',
  'video/article': '文章视频',
  assets: '公共资源',
  internetMeme: '表情包',
  userAvatar: '用户头像',
  articleCover: '文章封面',
  articlePicture: '文章图片',
  articleFile: '文章附件',
  webAvatar: '网站头像',
  webBackgroundImage: '背景图片',
  randomAvatar: '随机头像',
  randomCover: '随机封面',
  graffiti: '画笔图片',
  commentPicture: '评论图片',
  'im/groupAvatar': '聊天群头像',
  'im/groupMessage': '群聊天图片',
  'im/friendMessage': '朋友聊天图片',
  funnyUrl: '音乐声音',
  funnyCover: '音乐封面',
  'love/bgCover': '表白墙背景',
  'love/manCover': '表白墙男生封面',
  'love/womanCover': '表白墙女生封面',
  favoritesCover: '收藏夹封面'
};

const RESOURCE_TYPE_TERMS = {
  orphanResource: ['孤儿资源', '未引用资源', '无引用资源'],
  invalidResource: ['无效资源', '失效资源', '无法加载资源', '加载失败资源'],
  'video/article': ['文章视频', '视频文章', '视频资源', 'Video.Article'],
  assets: ['公共资源', '资源管理', '文件资源'],
  internetMeme: ['表情包', '梗图', 'meme'],
  userAvatar: ['用户头像', '头像资源'],
  articleCover: ['文章封面', '封面图片'],
  articlePicture: ['文章图片', '正文图片'],
  articleFile: ['文章附件', '附件资源', '下载附件', '正文文件'],
  webAvatar: ['网站头像', '站点头像'],
  webBackgroundImage: ['背景图片', '站点背景'],
  randomAvatar: ['随机头像', '头像池'],
  randomCover: ['随机封面', '封面池'],
  graffiti: ['画笔图片', '画笔资源'],
  commentPicture: ['评论图片', '评论配图'],
  'im/groupAvatar': ['聊天群头像', '群头像'],
  'im/groupMessage': ['群聊天图片', '群消息图片'],
  'im/friendMessage': ['朋友聊天图片', '私聊图片'],
  funnyUrl: ['音乐声音', '音频资源', '音乐文件'],
  funnyCover: ['音乐封面', '歌曲封面'],
  'love/bgCover': ['表白墙背景', '情侣页背景', 'Love背景'],
  'love/manCover': ['表白墙男生封面', '男生封面', 'Love.Man'],
  'love/womanCover': ['表白墙女生封面', '女生封面', 'Love.Woman'],
  favoritesCover: ['收藏夹封面', '收藏封面']
};

const CONFIG_INTENT_RULES = [
  {
    test: key => key === 'store.type',
    terms: ['存储平台', '存储方式', '对象存储', '上传存储']
  },
  {
    test: key => key.startsWith('local.'),
    terms: ['本地存储', '本地上传', '本地文件', '本地资源']
  },
  {
    test: key => key.startsWith('qiniu.') || key === 'qiniuUrl',
    terms: ['七牛', '七牛云', '对象存储', 'CDN', '上传地址']
  },
  {
    test: key => key.startsWith('lsky.'),
    terms: ['兰空图床', '图床', '图片上传']
  },
  {
    test: key => key.startsWith('easyimage.'),
    terms: ['简单图床', '图床', '图片上传']
  },
  {
    test: key => key === 'im.enable',
    terms: ['聊天室', 'IM', '即时聊天']
  },
  {
    test: key => key === 'enableComment',
    terms: ['评论', '评论开关', '关闭评论', '开启评论']
  },
  {
    test: key => key === 'beian' || key === 'policeBeian',
    terms: ['备案', '备案号', '公安备案']
  },
  {
    test: key => key === 'webStaticResourcePrefix',
    terms: ['静态资源', '资源前缀', '静态资源前缀', '静态文件']
  },
  {
    test: key => key.startsWith('image.webp.'),
    terms: ['WebP', '图片转WebP', '图片格式', '图片优化']
  },
  {
    test: key => key.startsWith('image.compress.'),
    terms: ['图片压缩', '压缩模式', '图片优化']
  },
  {
    test: key => key.startsWith('font.'),
    terms: ['字体', '字体文件', '字体CDN', '字体分片']
  },
  {
    test: key => key === 'tencent.lbs.key',
    terms: ['腾讯地图', '腾讯位置服务', '地图Key', 'LBS']
  },
  {
    test: key => key.startsWith('user.code.'),
    terms: ['验证码邮件', '邮箱验证码', '验证码模板']
  },
  {
    test: key => key.startsWith('user.subscribe.'),
    terms: ['订阅邮件', '订阅模板', '文章订阅']
  }
];

function normalizeText(value) {
  return (value || '')
    .toString()
    .toLowerCase()
    .replace(/\s+/g, '')
    .trim();
}

function uniqueTexts(values) {
  return Array.from(new Set((values || []).filter(Boolean)));
}

function getConfigKeyTokens(configKey) {
  if (!configKey) {
    return [];
  }

  const parts = configKey.split(/[._-]/).filter(Boolean);
  const combos = [];

  parts.forEach((part, index) => {
    combos.push(part);
    if (index < parts.length - 1) {
      combos.push(parts[index] + '.' + parts[index + 1]);
    }
  });

  return uniqueTexts([configKey].concat(parts, combos));
}

function getConfigIntentTerms(configKey) {
  return uniqueTexts(
    CONFIG_INTENT_RULES
      .filter(rule => rule.test(configKey))
      .flatMap(rule => rule.terms)
  );
}

function getPluginIntentTerms(pluginType) {
  return uniqueTexts(PLUGIN_TYPE_TERMS[pluginType] || []);
}

function getResourcePathIntentTerms(resourceType) {
  return uniqueTexts(RESOURCE_PATH_TYPE_TERMS[resourceType] || []);
}

function getResourceIntentTerms(resourceType) {
  return uniqueTexts(RESOURCE_TYPE_TERMS[resourceType] || []);
}

export default {
  name: 'AdminConfigSearch',

  data() {
    return {
      keyword: '',
      panelVisible: false,
      isCompact: false,
      suggestions: DEFAULT_SUGGESTIONS,
      dynamicConfigEntries: [],
      dynamicConfigLoaded: false,
      dynamicConfigLoading: false,
      dynamicPluginEntries: [],
      dynamicPluginLoaded: false,
      dynamicPluginLoading: false,
      dynamicArticleEntries: [],
      dynamicArticleLoaded: false,
      dynamicArticleLoading: false,
      dynamicResourceEntries: [],
      dynamicResourceLoaded: false,
      dynamicResourceLoading: false,
      dynamicResourcePathEntries: [],
      dynamicResourcePathLoaded: false,
      dynamicResourcePathLoading: false,
      dynamicLoveEntries: [],
      dynamicLoveLoaded: false,
      dynamicLoveLoading: false,
      suppressOutsideClose: false
    };
  },

  computed: {
    mainStore() {
      return useMainStore();
    },
    hasKeyword() {
      return this.keyword.length > 0;
    },
    normalizedKeyword() {
      return normalizeText(this.keyword);
    },
    searchEntries() {
      return adminConfigSearchIndex.concat(
        this.dynamicConfigEntries,
        this.dynamicPluginEntries,
        this.dynamicArticleEntries,
        this.dynamicResourceEntries,
        this.dynamicResourcePathEntries,
        this.dynamicLoveEntries
      );
    },
    matchedResults() {
      if (!this.hasKeyword || this.normalizedKeyword.length < 1) {
        return [];
      }

      return this.searchEntries
        .map((item) => {
          const match = this.getMatchResult(item, this.normalizedKeyword);
          return match
            ? {
                ...item,
                score: match.score,
                matchRank: match.rank
              }
            : null;
        })
        .filter(Boolean)
        .sort((a, b) => {
          if (b.score !== a.score) {
            return b.score - a.score;
          }
          if (b.matchRank !== a.matchRank) {
            return b.matchRank - a.matchRank;
          }
          return a.fieldLabel.length - b.fieldLabel.length;
        })
        .slice(0, 12);
    },
    fieldResults() {
      return this.matchedResults.filter(item => item.type === 'field').slice(0, 8);
    },
    pageResults() {
      const hasStrongFieldMatch = this.fieldResults.some(item => item.score >= 160);
      return this.matchedResults
        .filter((item) => {
          if (item.type !== 'page') {
            return false;
          }
          if (hasStrongFieldMatch) {
            return item.score >= 170;
          }
          return item.score >= 140;
        })
        .slice(0, 3);
    },
    totalResults() {
      return this.fieldResults.length + this.pageResults.length;
    }
  },

  mounted() {
    this.updateViewport();
    this.fetchDynamicConfigs();
    this.fetchDynamicPlugins();
    this.fetchDynamicArticles();
    this.fetchDynamicResources();
    this.fetchDynamicResourcePaths();
    this.fetchDynamicLoves();
    document.addEventListener('click', this.handleOutsideClick);
    window.addEventListener('resize', this.updateViewport);
    if (this.$bus && this.$bus.$on) {
      this.$bus.$on('sysConfigUpdated', this.handleSysConfigUpdated);
    }
  },

  beforeDestroy() {
    document.removeEventListener('click', this.handleOutsideClick);
    window.removeEventListener('resize', this.updateViewport);
    if (this.$bus && this.$bus.$off) {
      this.$bus.$off('sysConfigUpdated', this.handleSysConfigUpdated);
    }
  },

  methods: {
    updateViewport() {
      this.isCompact = window.innerWidth <= 640;
    },
    getTextMatchScore(target, query, weights = {}) {
      if (!target) {
        return null;
      }

      if (target === query) {
        return {
          score: weights.exact || 0,
          rank: 3
        };
      }

      if (query.length >= 1 && target.startsWith(query)) {
        return {
          score: weights.prefix || 0,
          rank: 2
        };
      }

      if (query.length >= 2 && target.includes(query)) {
        return {
          score: weights.contains || 0,
          rank: 1
        };
      }

      return null;
    },
    pickBestMatch(values, query, weights) {
      return values.reduce((best, value) => {
        const current = this.getTextMatchScore(normalizeText(value), query, weights);
        if (!current) {
          return best;
        }
        if (!best || current.score > best.score || (current.score === best.score && current.rank > best.rank)) {
          return current;
        }
        return best;
      }, null);
    },
    getMatchResult(item, query) {
      if (query.length >= 2) {
        const searchableTexts = [item.fieldLabel, item.pageTitle, item.sectionTitle]
          .concat(item.keywords || [], item.aliases || [])
          .map(value => normalizeText(value))
          .filter(Boolean);
        const hasWholeQueryMatch = searchableTexts.some(value => value.includes(query));
        if (!hasWholeQueryMatch) {
          return null;
        }
      }

      const fieldMatch = this.getTextMatchScore(normalizeText(item.fieldLabel), query, {
        exact: item.type === 'field' ? 240 : 200,
        prefix: item.type === 'field' ? 190 : 150,
        contains: item.type === 'field' ? 160 : 120
      });
      const keywordMatch = this.pickBestMatch(item.keywords || [], query, {
        exact: item.type === 'field' ? 210 : 180,
        prefix: item.type === 'field' ? 170 : 140,
        contains: item.type === 'field' ? 135 : 105
      });
      const aliasMatch = this.pickBestMatch(item.aliases || [], query, {
        exact: item.type === 'field' ? 185 : 150,
        prefix: item.type === 'field' ? 145 : 115,
        contains: item.type === 'field' ? 115 : 0
      });
      const pageMatch = this.getTextMatchScore(normalizeText(item.pageTitle), query, {
        exact: 150,
        prefix: 110,
        contains: 80
      });
      const sectionMatch = this.getTextMatchScore(normalizeText(item.sectionTitle), query, {
        exact: 120,
        prefix: 90,
        contains: 65
      });

      const matches = [fieldMatch, keywordMatch, aliasMatch, pageMatch, sectionMatch].filter(Boolean);

      if (!matches.length) {
        return null;
      }

      const bestMatch = matches.reduce((best, current) => {
        if (!best || current.score > best.score || (current.score === best.score && current.rank > best.rank)) {
          return current;
        }
        return best;
      }, null);

      const minScore = item.type === 'field'
        ? (query.length <= 2 ? 145 : 110)
        : (query.length <= 2 ? 160 : 130);

      if (!bestMatch || bestMatch.score < minScore) {
        return null;
      }

      return bestMatch;
    },
    buildDynamicPluginEntries(pluginGroups) {
      return Object.keys(pluginGroups || {}).flatMap((pluginType) => {
        const pluginList = pluginGroups[pluginType] || [];
        const typeLabel = PLUGIN_TYPE_LABELS[pluginType] || '插件管理';
        const intentTerms = getPluginIntentTerms(pluginType);

        return pluginList.map((plugin) => {
          const pluginKey = plugin.pluginKey || '';
          const pluginName = plugin.pluginName || pluginKey || '未命名插件';
          const pluginDescription = plugin.pluginDescription || '';

          return {
            id: 'sys-plugin-' + pluginType + '-' + plugin.id,
            type: 'field',
            route: {
              path: '/pluginManager',
              query: {
                type: pluginType,
                search: pluginKey || pluginName
              }
            },
            pageTitle: '插件管理',
            sectionTitle: typeLabel + (pluginKey ? ' · ' + pluginKey : ''),
            fieldLabel: pluginName,
            keywords: uniqueTexts([pluginName, pluginKey, pluginDescription, typeLabel].concat(intentTerms)),
            aliases: uniqueTexts([pluginKey, pluginType].concat(intentTerms))
          };
        });
      });
    },
    buildDynamicArticleEntries(articles) {
      return (articles || []).map((article) => {
        const articleTitle = article.articleTitle || '未命名文章';
        const authorName = article.username || '';
        const sortName = article.sort && article.sort.sortName ? article.sort.sortName : '';
        const labelName = article.label && article.label.labelName ? article.label.labelName : '';

        return {
          id: 'article-' + article.id,
          type: 'field',
          route: {
            path: '/postList',
            query: {
              search: articleTitle
            }
          },
          pageTitle: '文章管理',
          sectionTitle: [authorName, sortName, labelName].filter(Boolean).join(' · ') || '文章列表',
          fieldLabel: articleTitle,
          keywords: uniqueTexts([articleTitle, authorName, sortName, labelName, String(article.id || '')]),
          aliases: ['文章', '博客', '文章列表', 'postList']
        };
      });
    },
    buildDynamicResourceEntries(resources) {
      return (resources || []).map((resource) => {
        const resourceType = resource.type || '';
        const typeLabel = RESOURCE_TYPE_LABELS[resourceType] || '资源管理';
        const originalName = resource.originalName || typeLabel || '未命名资源';
        const path = resource.path || '';
        const mimeType = resource.mimeType || '';
        const storeType = resource.storeType || '';
        const intentTerms = getResourceIntentTerms(resourceType);

        return {
          id: 'resource-' + resource.id,
          type: 'field',
          route: {
            path: '/resourceList',
            query: {
              search: originalName || path || resourceType,
              resourceType
            }
          },
          pageTitle: '资源管理',
          sectionTitle: typeLabel + (storeType ? ' · ' + storeType : ''),
          fieldLabel: originalName,
          keywords: uniqueTexts([originalName, path, mimeType, storeType, typeLabel, String(resource.id || '')].concat(intentTerms)),
          aliases: uniqueTexts([resourceType, mimeType, storeType].concat(intentTerms))
        };
      });
    },
    buildDynamicResourcePathEntries(resourcePaths) {
      return (resourcePaths || []).map((resourcePath) => {
        const resourceType = resourcePath.type || '';
        const typeLabel = RESOURCE_PATH_TYPE_LABELS[resourceType] || '资源聚合';
        const title = resourcePath.title || typeLabel || '未命名资源';
        const classify = resourcePath.classify || '';
        const introduction = resourcePath.introduction || '';
        const url = resourcePath.url || '';
        const intentTerms = getResourcePathIntentTerms(resourceType);

        return {
          id: 'resource-path-' + resourcePath.id,
          type: 'field',
          route: {
            path: '/resourcePathList',
            query: {
              search: title || classify || resourceType,
              resourceType
            }
          },
          pageTitle: '资源聚合',
          sectionTitle: typeLabel + (classify ? ' · ' + classify : ''),
          fieldLabel: title,
          keywords: uniqueTexts([title, classify, introduction, url, typeLabel].concat(intentTerms)),
          aliases: uniqueTexts([resourceType].concat(intentTerms))
        };
      });
    },
    buildDynamicLoveEntries(loves) {
      return (loves || []).map((love) => {
        const manName = love.manName || '';
        const womanName = love.womanName || '';
        const countdownTitle = love.countdownTitle || '';
        const familyInfo = love.familyInfo || '';
        const primaryLabel = [manName, womanName].filter(Boolean).join(' / ') || countdownTitle || '表白墙记录';

        return {
          id: 'love-record-' + love.id,
          type: 'field',
          route: {
            path: '/loveList',
            query: {
              search: manName || womanName || countdownTitle || String(love.id)
            }
          },
          pageTitle: '表白墙',
          sectionTitle: countdownTitle || '表白墙记录',
          fieldLabel: primaryLabel,
          keywords: uniqueTexts([manName, womanName, countdownTitle, familyInfo, String(love.id), String(love.userId || '')]),
          aliases: uniqueTexts(['表白墙', '家庭祝福', '情侣页'])
        };
      });
    },
    buildDynamicConfigEntries(configList) {
      return (configList || []).map((config) => {
        const configKey = config.configKey || '';
        const configName = config.configName || configKey || '未命名配置';
        const configTypeLabel = CONFIG_TYPE_LABELS[config.configType] || '系统配置';
        const configKeyTokens = getConfigKeyTokens(configKey);
        const intentTerms = getConfigIntentTerms(configKey);
        const previewValue = (config.configValue || '').toString().trim();
        const shortValue = previewValue && previewValue.length <= 24 ? previewValue : '';

        return {
          id: 'sys-config-' + config.id,
          type: 'field',
          route: {
            path: '/configList',
            query: {
              search: configKey || configName
            }
          },
          pageTitle: '配置管理',
          sectionTitle: configTypeLabel + (configKey ? ' · ' + configKey : ''),
          fieldLabel: configName,
          keywords: uniqueTexts([configName, configKey].concat(intentTerms, shortValue ? [shortValue] : [])),
          aliases: uniqueTexts(configKeyTokens.concat(intentTerms))
        };
      });
    },
    fetchDynamicPlugins() {
      if (this.dynamicPluginLoading) {
        return;
      }

      this.dynamicPluginLoading = true;
      const pluginTypes = Object.keys(PLUGIN_TYPE_LABELS);
      const requests = pluginTypes.map((pluginType) => {
        return this.$http.get(this.$constant.baseURL + '/sysPlugin/listPlugins', { pluginType }, true)
          .then((res) => ({ pluginType, data: res.data || [] }))
          .catch(() => ({ pluginType, data: [] }));
      });

      Promise.all(requests)
        .then((results) => {
          const pluginGroups = results.reduce((groups, result) => {
            groups[result.pluginType] = result.data;
            return groups;
          }, {});
          this.dynamicPluginEntries = this.buildDynamicPluginEntries(pluginGroups);
          this.dynamicPluginLoaded = true;
        })
        .catch(() => {
          this.dynamicPluginEntries = [];
        })
        .finally(() => {
          this.dynamicPluginLoading = false;
        });
    },
    fetchDynamicArticles() {
      if (this.dynamicArticleLoading) {
        return;
      }

      this.dynamicArticleLoading = true;
      const payload = {
        current: 1,
        size: 200,
        searchKey: '',
        recommendStatus: null,
        sortId: null,
        labelId: null
      };
      const primaryUrl = this.mainStore.currentAdmin && this.mainStore.currentAdmin.isBoss
        ? '/admin/article/boss/list'
        : '/admin/article/user/list';
      const fallbackUrl = primaryUrl === '/admin/article/boss/list'
        ? '/admin/article/user/list'
        : '/admin/article/boss/list';

      const request = (url) => this.$http.post(this.$constant.baseURL + url, payload, true);

      request(primaryUrl)
        .catch(() => request(fallbackUrl))
        .then((res) => {
          const records = res && res.data ? (res.data.records || []) : [];
          this.dynamicArticleEntries = this.buildDynamicArticleEntries(records);
          this.dynamicArticleLoaded = true;
        })
        .catch(() => {
          this.dynamicArticleEntries = [];
        })
        .finally(() => {
          this.dynamicArticleLoading = false;
        });
    },
    fetchDynamicResources() {
      if (this.dynamicResourceLoading) {
        return;
      }

      this.dynamicResourceLoading = true;
      this.$http.post(this.$constant.baseURL + '/resource/listResource', {
        current: 1,
        size: 500,
        resourceType: ''
      }, true)
        .then((res) => {
          const records = res && res.data ? (res.data.records || []) : [];
          this.dynamicResourceEntries = this.buildDynamicResourceEntries(records);
          this.dynamicResourceLoaded = true;
        })
        .catch(() => {
          this.dynamicResourceEntries = [];
        })
        .finally(() => {
          this.dynamicResourceLoading = false;
        });
    },    fetchDynamicResourcePaths() {
      if (this.dynamicResourcePathLoading) {
        return;
      }

      this.dynamicResourcePathLoading = true;
      this.$http.post(this.$constant.baseURL + '/webInfo/listResourcePath', {
        current: 1,
        size: 500,
        resourceType: '',
        status: null,
        searchKey: ''
      }, true)
        .then((res) => {
          const records = res && res.data ? (res.data.records || []) : [];
          this.dynamicResourcePathEntries = this.buildDynamicResourcePathEntries(records);
          this.dynamicResourcePathLoaded = true;
        })
        .catch(() => {
          this.dynamicResourcePathEntries = [];
        })
        .finally(() => {
          this.dynamicResourcePathLoading = false;
        });
    },
    fetchDynamicLoves() {
      if (this.dynamicLoveLoading) {
        return;
      }

      this.dynamicLoveLoading = true;
      this.$http.post(this.$constant.baseURL + '/family/listFamily', {
        current: 1,
        size: 500,
        status: null
      }, true)
        .then((res) => {
          const records = res && res.data ? (res.data.records || []) : [];
          this.dynamicLoveEntries = this.buildDynamicLoveEntries(records);
          this.dynamicLoveLoaded = true;
        })
        .catch(() => {
          this.dynamicLoveEntries = [];
        })
        .finally(() => {
          this.dynamicLoveLoading = false;
        });
    },
    fetchDynamicConfigs() {
      if (this.dynamicConfigLoading) {
        return;
      }

      this.dynamicConfigLoading = true;
      this.$http.get(this.$constant.baseURL + '/sysConfig/listConfig', {}, true)
        .then((res) => {
          this.dynamicConfigEntries = this.buildDynamicConfigEntries(res.data || []);
          this.dynamicConfigLoaded = true;
        })
        .catch(() => {
          this.dynamicConfigEntries = [];
        })
        .finally(() => {
          this.dynamicConfigLoading = false;
        });
    },
    handleSysConfigUpdated() {
      this.fetchDynamicConfigs();
    },
    handleFocus() {
      this.openPanel();
    },
    handleInput() {
      this.openPanel();
    },
    handleEnter() {
      const firstResult = this.fieldResults[0] || this.pageResults[0];
      if (firstResult) {
        this.navigateTo(firstResult);
      }
    },
    togglePanel() {
      if (this.panelVisible) {
        this.closePanel();
        return;
      }
      this.openPanel();
    },
    openPanel() {
      this.panelVisible = true;
      if (!this.dynamicConfigLoaded) {
        this.fetchDynamicConfigs();
      }
      if (!this.dynamicPluginLoaded) {
        this.fetchDynamicPlugins();
      }
      if (!this.dynamicArticleLoaded) {
        this.fetchDynamicArticles();
      }
      if (!this.dynamicResourceLoaded) {
        this.fetchDynamicResources();
      }
      if (!this.dynamicResourcePathLoaded) {
        this.fetchDynamicResourcePaths();
      }
      if (!this.dynamicLoveLoaded) {
        this.fetchDynamicLoves();
      }
      if (this.isCompact) {
        this.$nextTick(() => {
          if (this.$refs.panelInput && this.$refs.panelInput.focus) {
            this.$refs.panelInput.focus();
          }
        });
      }
    },
    navigateTo(item) {
      let routeConfig = typeof item.route === 'object' ? { ...item.route } : { path: item.route };
      if (item.type === 'field' && item.id && item.id.startsWith('field-')) {
        routeConfig.query = { ...routeConfig.query, focus: item.id };
      }
      this.$router.push(routeConfig).catch(() => {});
      this.closePanel();
    },
    applySuggestion(value) {
      this.suppressOutsideClose = true;
      this.keyword = value;
      this.openPanel();
      this.$nextTick(() => {
        const targetInput = this.isCompact ? this.$refs.panelInput : this.$refs.searchInput;
        if (targetInput && targetInput.focus) {
          targetInput.focus();
        }
        window.setTimeout(() => {
          this.suppressOutsideClose = false;
        }, 0);
      });
    },
    handleOutsideClick(event) {
      if (this.suppressOutsideClose) {
        return;
      }
      if (!this.$refs.searchRoot) return;
      if (!this.$refs.searchRoot.contains(event.target)) {
        this.closePanel();
      }
    },
    closePanel() {
      this.panelVisible = false;
    }
  }
};
</script>

<style scoped>
.admin-config-search {
  position: relative;
  display: flex;
  align-items: center;
  margin-right: 16px;
  flex-shrink: 0;
}

.search-trigger {
  width: 280px;
}

.search-trigger-compact {
  width: auto;
}

.search-trigger-button {
  padding: 8px;
  border-radius: 10px;
}

.search-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 420px;
  max-height: 70vh;
  overflow: hidden;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.14);
  z-index: 2000;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.panel-meta {
  font-size: 12px;
  color: #909399;
}

.panel-body,
.panel-suggestions {
  max-height: calc(70vh - 54px);
  overflow-y: auto;
  padding: 12px 0;
}

.compact-search-box {
  padding: 12px 16px 0;
}

.result-group + .result-group {
  margin-top: 6px;
}

.group-title,
.suggestion-title {
  padding: 0 16px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
}

.result-item {
  width: 100%;
  padding: 10px 16px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.result-item:hover {
  background: #f5f7fa;
}

.result-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result-label {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.result-page {
  font-size: 12px;
  color: #409EFF;
  white-space: nowrap;
}

.result-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 16px;
  color: #909399;
  font-size: 13px;
}

.suggestion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 16px 12px;
}

.suggestion-tag {
  border: 1px solid #dcdfe6;
  background: #ffffff;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggestion-tag:hover {
  border-color: #409EFF;
  color: #409EFF;
}

@media screen and (max-width: 768px) {
  .search-trigger {
    width: 160px;
  }

  .search-panel {
    width: min(360px, calc(100vw - 24px));
    right: 0;
  }
}

@media screen and (max-width: 640px) {
  .admin-config-search.is-compact {
    position: absolute;
    top: 50%;
    right: 120px;
    margin-right: 0;
    transform: translateY(-50%);
    z-index: 5;
  }

  .admin-config-search.is-compact .search-panel {
    right: -84px;
  }
}

@media screen and (max-width: 480px) {
  .admin-config-search {
    margin-right: 6px;
  }

  .admin-config-search.is-compact {
    right: 96px;
  }

  .search-trigger {
    width: auto;
  }

  .search-panel {
    width: min(360px, calc(100vw - 16px));
  }
}
</style>
