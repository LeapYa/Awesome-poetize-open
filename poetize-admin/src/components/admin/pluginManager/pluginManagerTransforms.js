const deepClone = (value) => JSON.parse(JSON.stringify(value));

export const PLUGIN_TYPES = [
  'mouse_click_effect',
  'particle_effect',
  'waifu_model',
  'editor',
  'article_theme',
  'payment',
  'ai_tool'
];

export const PLUGIN_TYPE_LABELS = {
  mouse_click_effect: '鼠标效果管理',
  particle_effect: '全屏飘落特效管理',
  waifu_model: '看板娘模型管理',
  editor: '文章编辑器管理',
  article_theme: '文章主题管理',
  payment: '文章付费管理',
  ai_tool: 'AI工具管理'
};

export const createDefaultForm = (pluginType = 'mouse_click_effect') => ({
  id: null,
  pluginType,
  pluginKey: '',
  pluginName: '',
  pluginDescription: '',
  pluginConfig: '{}',
  // manifest 必须为 null，空字符串会被 MariaDB JSON 列的 JSON_VALID 约束拒绝
  manifest: null,
  pluginCode: '',
  enabled: true,
  isSystem: false,
  sortOrder: 0
});

export const createDefaultWaifuConfig = () => ({
  modelPath: '',
  texturesStr: '',
  scale: 1.0,
  greetingStr: '',
  idleStr: '',
  thumbnailUrl: ''
});

export const THEME_PRESETS = {
  default: {
    headings: {
      h1: { emoji: '📑', color: null, show: true, paddingLeft: '40px' },
      h2: { emoji: '#', color: '#ff6d6d', show: true, paddingLeft: '25px' },
      h3: { emoji: '▌', color: '#ff6d6d', show: true, paddingLeft: '20px' },
      h4: { emoji: '🌷', color: null, show: true, paddingLeft: '28px' },
      h5: { emoji: '', color: null, show: false, paddingLeft: '' }
    },
    toc: { emoji: '🏖️', show: true }
  },
  minimal: {
    headings: {
      h1: { emoji: '', color: null, show: false, paddingLeft: '' },
      h2: { emoji: '#', color: '#909399', show: true, paddingLeft: '25px' },
      h3: { emoji: '▌', color: '#909399', show: true, paddingLeft: '20px' },
      h4: { emoji: '', color: null, show: false, paddingLeft: '' },
      h5: { emoji: '', color: null, show: false, paddingLeft: '' }
    },
    toc: { emoji: '📖', show: true }
  },
  plain: {
    headings: {
      h1: { emoji: '', color: null, show: false, paddingLeft: '' },
      h2: { emoji: '', color: null, show: false, paddingLeft: '' },
      h3: { emoji: '', color: null, show: false, paddingLeft: '' },
      h4: { emoji: '', color: null, show: false, paddingLeft: '' },
      h5: { emoji: '', color: null, show: false, paddingLeft: '' }
    },
    toc: { emoji: '', show: false }
  },
  garden: {
    headings: {
      h1: { emoji: '🌿', color: null, show: true, paddingLeft: '40px' },
      h2: { emoji: '🌱', color: null, show: true, paddingLeft: '36px' },
      h3: { emoji: '🍃', color: null, show: true, paddingLeft: '32px' },
      h4: { emoji: '🌷', color: null, show: true, paddingLeft: '32px' },
      h5: { emoji: '🌼', color: null, show: true, paddingLeft: '28px' }
    },
    toc: { emoji: '🌺', show: true }
  },
  academic: {
    headings: {
      h1: { emoji: '§', color: null, show: true, paddingLeft: '24px' },
      h2: { emoji: '¶', color: null, show: true, paddingLeft: '20px' },
      h3: { emoji: '▸', color: null, show: true, paddingLeft: '18px' },
      h4: { emoji: '•', color: null, show: true, paddingLeft: '16px' },
      h5: { emoji: '◦', color: null, show: true, paddingLeft: '16px' }
    },
    toc: { emoji: '📚', show: true }
  },
  space: {
    headings: {
      h1: { emoji: '🌟', color: null, show: true, paddingLeft: '40px' },
      h2: { emoji: '⭐', color: null, show: true, paddingLeft: '33px' },
      h3: { emoji: '💫', color: null, show: true, paddingLeft: '31px' },
      h4: { emoji: '✨', color: null, show: true, paddingLeft: '31px' },
      h5: { emoji: '·', color: null, show: true, paddingLeft: '16px' }
    },
    toc: { emoji: '🛸', show: true }
  }
};

export const createDefaultThemeConfig = () => deepClone(THEME_PRESETS.default);

export const createDefaultAiToolEditorMode = () => ({
  headers: 'kv',
  query: 'kv',
  body: 'kv'
});

export const createDefaultAiToolForm = () => ({
  method: 'GET',
  url: '',
  headersText: '{}',
  queryText: '{}',
  bodyText: '{}',
  headersList: [],
  queryList: [{ key: 'q', value: '{{args.query}}' }],
  bodyList: [],
  responsePath: '',
  timeoutMs: 10000,
  inputSchemaText: `{
  "type": "object",
  "properties": {
    "query": { "type": "string", "description": "搜索关键词" },
    "limit": { "type": "integer", "description": "返回条数", "default": 5 }
  },
  "required": ["query"]
}`,
  configSchemaText: `{
  "apiKey": { "type": "string", "label": "API Key", "description": "第三方服务 API Key", "defaultValue": "" }
}`,
  pluginConfigText: `{
  "apiKey": ""
}`
});

export const createEditorRules = () => ({
  pluginName: [
    { required: true, message: '请输入插件名称', trigger: 'blur' }
  ],
  pluginKey: [
    { required: true, message: '请输入插件标识符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  pluginConfig: [
    {
      validator: (rule, value, callback) => {
        if (!value) return callback();
        try {
          JSON.parse(value);
          callback();
        } catch (e) {
          callback(new Error('请输入合法的JSON格式'));
        }
      },
      trigger: 'blur'
    }
  ]
});

export const AI_TOOL_ASSISTANT_PROMPT = [
  '# Role',
  'You are an expert HTTP API integration assistant and backend tool configuration architect.',
  '',
  '# Objective',
  'Your goal is to help the user fill in an HTTP AI Tool configuration form for this admin panel.',
  '',
  'You must generate configuration that can be directly pasted into the form fields.',
  'Do not explain general concepts unless the user explicitly asks.',
  'Do not invent fields or guess response structures.',
  '',
  '# Workflow',
  'Step 1: First ask for a one-sentence description of the tool purpose, then prefer asking for the official API documentation.',
  'If the official API documentation is enough, use it directly.',
  '',
  'Step 2: Only if the documentation is insufficient, ask for the missing minimum information:',
  '- a request example (curl / Postman / full request example)',
  '- a successful response JSON example',
  '',
  'Step 3: Once the information is sufficient, output the final configuration strictly following the form fields below.',
  '',
  '# Form Field Meanings',
  '- pluginName: display name of the tool',
  '- pluginKey: unique tool key, use lowercase letters and underscores',
  '- pluginDescription: short tool description for the AI model',
  '- inputSchema: standard JSON Schema for model-call arguments',
  '- configSchema: admin form field definition object, NOT standard JSON Schema',
  '- method: HTTP method, only GET / POST / PUT / DELETE',
  '- url: request URL',
  '- headers: JSON object for request headers',
  '- query: JSON object for query parameters, use {} if empty',
  '- body: JSON object for request body, use {} if empty',
  '- responsePath: path used to extract useful data from the response JSON',
  '- timeoutMs: request timeout in milliseconds',
  '- pluginConfig: actual fixed config values filled by admin',
  '',
  '# Critical Rules',
  '1. inputSchema IS standard JSON Schema.',
  '2. configSchema IS NOT standard JSON Schema.',
  '3. configSchema must be a field-map object:',
  '   - key = config field name',
  '   - value = field definition',
  '4. For enum-like config fields, prefer:',
  '   - type = "select"',
  '   - options = [...]',
  '   Do not use enum in configSchema.',
  '5. pluginConfig must contain only actual values.',
  '6. If pluginConfig contains sensitive fields such as apiKey, token, secret, or password, the value may be empty, but you must explicitly tell the user to replace it with their own real credential in the admin panel.',
  '7. headers, query, and body must always be valid JSON objects.',
  '8. If empty, output {} instead of "留空".',
  '9. Template variables must be strings, for example:',
  '   - "{{args.query}}"',
  '   - "{{config.apiKey}}"',
  '',
  '# Correct configSchema Example',
  `{
  "apiKey": {
    "type": "string",
    "label": "API Key",
    "description": "Third-party service API key",
    "defaultValue": ""
  },
  "searchDepth": {
    "type": "select",
    "label": "搜索深度",
    "description": "搜索深度",
    "defaultValue": "basic",
    "options": [
      { "label": "基础", "value": "basic" },
      { "label": "高级", "value": "advanced" }
    ]
  }
}`,
  '',
  '# Wrong configSchema Example',
  `{
  "type": "object",
  "properties": {
    "apiKey": {
      "type": "string"
    }
  },
  "required": ["apiKey"]
}`,
  '',
  '# Output Format',
  'If information is insufficient, output only:',
  '请先提供以下信息：',
  '1. 这个工具的用途（一句话描述）',
  '2. 官方 API 文档',
  '3. 如果文档不够，再补充一个请求示例',
  '4. 如果还不够，再补充一个成功响应 JSON 示例',
  '',
  'If information is sufficient, output strictly in this format:',
  '',
  '- 插件名称 pluginName：填写 xxx',
  '- 插件标识 pluginKey：填写 xxx',
  '- 插件描述 pluginDescription：填写 xxx',
  '- 工具参数Schema inputSchema：填写以下 JSON',
  '- 配置Schema configSchema：填写以下 JSON',
  '- HTTP方法 method：选择 xxx',
  '- 请求地址 url：填写 xxx',
  '- 请求头模板 headers：填写以下 JSON',
  '- Query模板 query：填写以下 JSON',
  '- Body模板 body：填写以下 JSON',
  '- 结果路径 responsePath：填写 xxx',
  '- 超时时间 timeoutMs：填写 xxx',
  '- 工具配置 pluginConfig：填写以下 JSON',
  '',
  '# Constraints',
  '- Prefer official API documentation over asking too many questions.',
  '- Ask only for missing information.',
  '- Do not output unsupported structures.',
  '- Do not output long explanations.',
  '- The final result must be directly usable in the admin form.'
].join('\n');

export function getModelPath(row) {
  try {
    const config = JSON.parse(row.pluginConfig || '{}');
    return config.modelPath || '未配置';
  } catch (e) {
    return '配置解析失败';
  }
}

export function getPreviewImage(row) {
  if (row && row.pluginConfig) {
    try {
      const config = JSON.parse(row.pluginConfig);
      if (config.thumbnailUrl) {
        return config.thumbnailUrl;
      }
    } catch (e) {
      return '';
    }
  }
  return '';
}

export function parseWaifuConfig(pluginConfig) {
  try {
    const config = JSON.parse(pluginConfig || '{}');
    return {
      modelPath: config.modelPath || '',
      texturesStr: (config.textures || []).join('\n'),
      scale: config.scale || 1.0,
      greetingStr: (config.messages?.greeting || []).join('\n'),
      idleStr: (config.messages?.idle || []).join('\n'),
      thumbnailUrl: config.thumbnailUrl || ''
    };
  } catch (e) {
    return createDefaultWaifuConfig();
  }
}

export function serializeWaifuConfig(waifuConfig) {
  const textures = (waifuConfig.texturesStr || '').split('\n').filter((item) => item.trim());
  const greetings = (waifuConfig.greetingStr || '').split('\n').filter((item) => item.trim());
  const idles = (waifuConfig.idleStr || '').split('\n').filter((item) => item.trim());

  return JSON.stringify({
    modelPath: waifuConfig.modelPath,
    textures: textures.length > 0 ? textures : [waifuConfig.modelPath],
    scale: waifuConfig.scale,
    thumbnailUrl: waifuConfig.thumbnailUrl || '',
    messages: {
      greeting: greetings.length > 0 ? greetings : ['你好呀~'],
      idle: idles.length > 0 ? idles : ['点点我嘛~']
    }
  });
}

export function parseThemeConfig(pluginConfig) {
  try {
    const config = JSON.parse(pluginConfig || '{}');
    return {
      headings: {
        h1: { emoji: config.headings?.h1?.emoji || '', color: config.headings?.h1?.color || null, show: config.headings?.h1?.show !== false, paddingLeft: config.headings?.h1?.paddingLeft || '' },
        h2: { emoji: config.headings?.h2?.emoji || '', color: config.headings?.h2?.color || null, show: config.headings?.h2?.show !== false, paddingLeft: config.headings?.h2?.paddingLeft || '' },
        h3: { emoji: config.headings?.h3?.emoji || '', color: config.headings?.h3?.color || null, show: config.headings?.h3?.show !== false, paddingLeft: config.headings?.h3?.paddingLeft || '' },
        h4: { emoji: config.headings?.h4?.emoji || '', color: config.headings?.h4?.color || null, show: config.headings?.h4?.show !== false, paddingLeft: config.headings?.h4?.paddingLeft || '' },
        h5: { emoji: config.headings?.h5?.emoji || '', color: config.headings?.h5?.color || null, show: config.headings?.h5?.show !== false, paddingLeft: config.headings?.h5?.paddingLeft || '' }
      },
      toc: {
        emoji: config.toc?.emoji || '',
        show: config.toc?.show !== false
      }
    };
  } catch (e) {
    return createDefaultThemeConfig();
  }
}

export function serializeThemeConfig(themeConfig) {
  return JSON.stringify({
    headings: {
      h1: { emoji: themeConfig.headings.h1.emoji || '', color: themeConfig.headings.h1.color || null, show: themeConfig.headings.h1.show, paddingLeft: themeConfig.headings.h1.paddingLeft || '' },
      h2: { emoji: themeConfig.headings.h2.emoji || '', color: themeConfig.headings.h2.color || null, show: themeConfig.headings.h2.show, paddingLeft: themeConfig.headings.h2.paddingLeft || '' },
      h3: { emoji: themeConfig.headings.h3.emoji || '', color: themeConfig.headings.h3.color || null, show: themeConfig.headings.h3.show, paddingLeft: themeConfig.headings.h3.paddingLeft || '' },
      h4: { emoji: themeConfig.headings.h4.emoji || '', color: themeConfig.headings.h4.color || null, show: themeConfig.headings.h4.show, paddingLeft: themeConfig.headings.h4.paddingLeft || '' },
      h5: { emoji: themeConfig.headings.h5.emoji || '', color: themeConfig.headings.h5.color || null, show: themeConfig.headings.h5.show, paddingLeft: themeConfig.headings.h5.paddingLeft || '' }
    },
    toc: {
      emoji: themeConfig.toc.emoji || '',
      show: themeConfig.toc.show
    }
  });
}

export function getThemePresetConfig(presetKey) {
  return THEME_PRESETS[presetKey] ? deepClone(THEME_PRESETS[presetKey]) : null;
}

export function isSecretField(fieldKey) {
  const secretKeywords = ['key', 'secret', 'token', 'password', 'private', 'api_key', 'apikey', 'apiToken'];
  const lowerKey = String(fieldKey || '').toLowerCase();
  return secretKeywords.some((keyword) => lowerKey.includes(keyword));
}

export function getSchemaFieldPlaceholder(key, schema = {}) {
  if (schema.placeholder) return schema.placeholder;
  if (schema.type === 'select') return `请选择${schema.label || key}`;
  if (schema.type === 'number') return `请输入${schema.label || key}`;
  if (schema.type === 'boolean') return '';
  return `请输入${schema.label || key}`;
}

export function createSchemaDrivenConfigState(row, pluginConfig) {
  let paymentConfigSchema = {};
  let paymentConfig = {};

  if (row.manifest) {
    try {
      const manifest = JSON.parse(row.manifest);
      if (manifest.configSchema) {
        paymentConfigSchema = manifest.configSchema;
      }
    } catch (e) {
      paymentConfigSchema = {};
    }
  }

  try {
    const existingConfig = JSON.parse(pluginConfig || '{}');
    Object.keys(paymentConfigSchema).forEach((key) => {
      const schema = paymentConfigSchema[key];
      paymentConfig[key] = Object.prototype.hasOwnProperty.call(existingConfig, key)
        ? existingConfig[key]
        : (schema.defaultValue !== undefined ? schema.defaultValue : '');
    });
  } catch (e) {
    Object.keys(paymentConfigSchema).forEach((key) => {
      const schema = paymentConfigSchema[key];
      paymentConfig[key] = schema.defaultValue !== undefined ? schema.defaultValue : '';
    });
  }

  return {
    paymentConfigSchema,
    paymentConfig
  };
}

export function kvListToObject(list) {
  const result = {};
  (list || []).forEach((item) => {
    const key = (item.key || '').trim();
    if (!key) return;
    result[key] = item.value || '';
  });
  return result;
}

export function objectToKvList(obj) {
  return Object.keys(obj || {}).map((key) => ({
    key,
    value: obj[key] == null ? '' : String(obj[key])
  }));
}

export function parseJsonText(text, label, fallback = {}) {
  try {
    return JSON.parse(text || JSON.stringify(fallback));
  } catch (e) {
    throw new Error(`${label} 不是合法的 JSON`);
  }
}

export function normalizeJsonText(text, label) {
  try {
    const parsed = JSON.parse(text || '{}');
    return JSON.stringify(parsed);
  } catch (e) {
    throw new Error(`${label} 不是合法的 JSON`);
  }
}

export function buildDefaultConfigFromSchema(schema) {
  const config = {};
  Object.keys(schema || {}).forEach((key) => {
    const field = schema[key] || {};
    config[key] = field.defaultValue !== undefined ? field.defaultValue : '';
  });
  return config;
}

export function getAiToolSectionMeta(section) {
  const map = {
    headers: { textField: 'headersText', listField: 'headersList', label: '请求头模板' },
    query: { textField: 'queryText', listField: 'queryList', label: 'Query模板' },
    body: { textField: 'bodyText', listField: 'bodyList', label: 'Body模板' }
  };
  return map[section];
}

export function getAiToolJsonPlaceholder(section) {
  const map = {
    headers: '例如：{"Authorization":"Bearer {{config.apiKey}}","Content-Type":"application/json"}',
    query: '例如：{"q":"{{args.query}}"}',
    body: '例如：{"query":"{{args.query}}","api_key":"{{config.apiKey}}"}'
  };
  return map[section] || '{}';
}

export function createAiToolConfigState(row, pluginConfig) {
  let manifest = {};
  try {
    manifest = row.manifest ? JSON.parse(row.manifest) : {};
  } catch (e) {
    manifest = {};
  }

  const tool = manifest.tool || {};
  const runtime = manifest.runtime || {};
  const configSchema = manifest.configSchema || {};
  let parsedPluginConfig = {};
  try {
    parsedPluginConfig = JSON.parse(pluginConfig || '{}');
  } catch (e) {
    parsedPluginConfig = {};
  }

  if (Object.keys(parsedPluginConfig).length === 0 && Object.keys(configSchema).length > 0) {
    parsedPluginConfig = buildDefaultConfigFromSchema(configSchema);
  }

  return {
    aiToolEditorMode: createDefaultAiToolEditorMode(),
    aiToolForm: {
      method: runtime.method || 'GET',
      url: runtime.url || '',
      headersText: JSON.stringify(runtime.headers || {}, null, 2),
      queryText: JSON.stringify(runtime.query || {}, null, 2),
      bodyText: JSON.stringify(runtime.body || {}, null, 2),
      headersList: objectToKvList(runtime.headers || {}),
      queryList: objectToKvList(runtime.query || {}),
      bodyList: objectToKvList(runtime.body || {}),
      responsePath: runtime.responsePath || '',
      timeoutMs: runtime.timeoutMs || 10000,
      inputSchemaText: JSON.stringify(tool.inputSchema || {
        type: 'object',
        properties: {
          query: { type: 'string', description: '搜索关键词' }
        },
        required: ['query']
      }, null, 2),
      configSchemaText: JSON.stringify(configSchema || {}, null, 2),
      pluginConfigText: JSON.stringify(parsedPluginConfig || {}, null, 2)
    }
  };
}

export function buildAiToolManifest({ form, aiToolForm, aiToolEditorMode }) {
  const inputSchema = parseJsonText(aiToolForm.inputSchemaText, '工具参数Schema');
  const configSchema = parseJsonText(aiToolForm.configSchemaText, '配置Schema');
  const headers = aiToolEditorMode.headers === 'json'
    ? parseJsonText(aiToolForm.headersText, '请求头模板')
    : kvListToObject(aiToolForm.headersList);
  const query = aiToolEditorMode.query === 'json'
    ? parseJsonText(aiToolForm.queryText, 'Query模板')
    : kvListToObject(aiToolForm.queryList);
  const body = ['GET', 'DELETE'].includes(aiToolForm.method)
    ? {}
    : (aiToolEditorMode.body === 'json'
      ? parseJsonText(aiToolForm.bodyText, 'Body模板')
      : kvListToObject(aiToolForm.bodyList));

  return {
    name: form.pluginKey,
    displayName: form.pluginName,
    pluginType: 'ai_tool',
    description: form.pluginDescription,
    configSchema,
    tool: {
      name: form.pluginKey,
      description: form.pluginDescription,
      inputSchema
    },
    runtime: {
      type: 'http',
      method: aiToolForm.method,
      url: aiToolForm.url,
      headers,
      query,
      body,
      responsePath: aiToolForm.responsePath,
      timeoutMs: aiToolForm.timeoutMs,
      contentType: 'application/json'
    }
  };
}

export function getAiPromptDialog(currentPluginType) {
  const mouseClickPrompt = `请帮我编写一个网页鼠标点击效果的JavaScript代码。

函数签名：函数体直接执行，可用参数如下：
- x: 点击的页面X坐标（包含滚动偏移）
- y: 点击的页面Y坐标（包含滚动偏移）
- config: JSON配置对象，包含用户自定义的参数
- anime: anime.js动画库实例（可用于创建复杂动画效果）

要求：
1. 代码会被直接执行（无需定义函数名，直接写函数体）
2. 创建的DOM元素必须设置 pointer-events: none 防止干扰用户操作
3. 使用 z-index: 1000 或更高确保效果在最上层
4. 创建的元素必须在动画结束后自行移除，避免内存泄漏
5. 如果使用canvas，可以获取或创建id为"mousedown-effect"的canvas元素复用
6. 如果需要将页面坐标转为视口坐标（用于position:fixed元素），使用：
   const viewportX = x - window.scrollX;
   const viewportY = y - window.scrollY;

请生成一个【在此描述你想要的效果】的鼠标点击效果。`;

  const particlePrompt = `请帮我编写一个网页全屏飘落特效的JavaScript代码。

代码模型：脚本会被直接执行一次，用于初始化一个全屏常驻的背景粒子/飘落效果，而不是点击时触发的函数。

可用参数如下：
- config: JSON配置对象，包含用户自定义的参数
- anime: anime.js动画库实例（如你确实需要，可以用于补充动画）

要求：
1. 代码会被直接执行，请直接写完整初始化逻辑，不要再包一层 function(x, y, ...)。
2. 特效必须是全屏常驻背景效果，例如樱花、雪花、光点、星屑、气泡等持续飘落或漂浮效果。
3. 需要自行创建并挂载全屏层或 canvas，覆盖整个视口。
4. 必须设置 pointer-events: none，避免遮挡页面交互。
5. 需要处理窗口 resize，确保全屏尺寸始终正确。
6. 需要避免重复挂载；如果同一个特效容器已存在，应复用或直接返回，不能越挂越多。
7. 需要支持从 config 读取参数，例如数量、速度、漂移、缩放、图片地址、zIndex、生成区域等。
8. 如果创建了定时器、事件监听或动画循环，请让结构尽量可控，避免明显内存泄漏。
9. 不要使用点击坐标，不要引用 x / y，不要使用 id 为 "mousedown-effect" 的 canvas 约定。
10. 效果层请使用足够高的 z-index，但仍保持为纯展示层。

请生成一个【在此描述你想要的效果】的全屏飘落特效。`;

  const configs = {
    mouse_click_effect: {
      title: '使用AI生成点击效果代码',
      intro: '复制以下提示词发送给AI（如DeepSeek、ChatGPT、Claude等），让AI帮你生成自定义点击效果代码：',
      prompt: mouseClickPrompt,
      examplesTitle: '效果描述示例',
      examplesHint: '可以替换上面模板最后一句的【在此描述你想要的效果】部分：',
      copySuccessMessage: '点击特效提示词已复制到剪贴板',
      examples: [
        { name: '爱心飘落', description: '点击时产生一个红色爱心向上飘起并逐渐消失' },
        { name: '水波纹', description: '点击时产生从中心向外扩散的圆形水波纹效果' },
        { name: '星星闪烁', description: '点击时产生多个五角星向四周散开并闪烁' },
        { name: '雪花飘落', description: '点击时产生几片雪花缓缓下落' },
        { name: '彩色气泡', description: '点击时产生多个彩色气泡向上飘起' },
        { name: '文字弹出', description: '点击时显示自定义文字向上弹出淡出' },
        { name: '雷电效果', description: '点击时产生闪电样的光效扩散' },
        { name: '樱花飘落', description: '点击时产生几片粉色樱花瓣随风飘落' }
      ]
    },
    particle_effect: {
      title: '使用AI生成全屏飘落特效代码',
      intro: '复制以下提示词发送给AI（如DeepSeek、ChatGPT、Claude等），让AI帮你生成全屏常驻的飘落特效代码：',
      prompt: particlePrompt,
      examplesTitle: '特效描述示例',
      examplesHint: '可以替换上面模板最后一句的【在此描述你想要的效果】部分：',
      copySuccessMessage: '全屏飘落特效提示词已复制到剪贴板',
      examples: [
        { name: '樱花飘落', description: '全屏持续飘落粉色樱花瓣，带轻微横向漂移和随机旋转' },
        { name: '雪花飘落', description: '全屏持续飘落大小不同的雪花，速度缓慢，适合冬季氛围' },
        { name: '金色光点', description: '全屏漂浮发光粒子，缓慢上下浮动，营造梦幻背景' },
        { name: '星屑流动', description: '全屏出现细小星屑从上方向下飘动，带少量闪烁效果' },
        { name: '泡泡上浮', description: '全屏半透明气泡从底部缓慢上浮，大小和速度随机' },
        { name: '落叶飘舞', description: '全屏持续飘落树叶，带旋转和横向摆动，形成秋日氛围' }
      ]
    }
  };

  return configs[currentPluginType] || configs.mouse_click_effect;
}

export function getPluginCodeEditorMeta(currentPluginType) {
  const metas = {
    mouse_click_effect: {
      placeholder: '自定义点击效果的JS代码。可用参数：x, y (点击坐标), config (JSON配置对象), anime (anime.js库)',
      description: '编写自定义点击效果。函数签名：<code>function(x, y, config, anime) { ... }</code>',
      showAnimeDoc: true
    },
    particle_effect: {
      placeholder: '自定义全屏飘落特效的JS代码。可用参数：config (JSON配置对象), anime (anime.js库)',
      description: '编写全屏常驻特效初始化脚本。代码会直接执行，请自行创建全屏层、处理 resize，并避免重复挂载。',
      showAnimeDoc: false
    }
  };

  return metas[currentPluginType] || metas.mouse_click_effect;
}
