<template>
  <div id="field-api">
    <SectionTag>API 配置</SectionTag>

    <el-card class="box-card" shadow="never" style="margin-top: 5px; border: none;">
      <!-- API开关 -->
      <div style="margin-bottom: 20px;">
        <el-form :model="apiConfig" label-width="120px">
          <el-form-item label="启用API">
            <el-switch
              v-model="apiConfig.enabled"
              @change="handleApiToggle"
              :disabled="apiLoading"
              active-color="#13ce66"
              inactive-color="#ff4949">
            </el-switch>
          </el-form-item>

          <div v-if="apiConfig.enabled" class="api-enabled-panel">
          <el-form-item label="API密钥">
            <div style="display: flex; align-items: center;">
              <el-input
                v-model="apiConfig.apiKey"
                placeholder="API密钥"
                :disabled="true"
                style="width: 350px;">
              </el-input>
              <el-button
                type="primary"
                size="small"
                style="margin-left: 10px;"
                :loading="apiLoading"
                :disabled="apiLoading"
                @click="regenerateApiKey">
                重新生成
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="IP白名单">
            <div style="width: 100%; max-width: 560px;">
              <el-input
                v-model="apiConfig.ipWhitelist"
                type="textarea"
                :rows="5"
                resize="vertical"
                :placeholder="'留空表示不限制。支持单个IP、CIDR网段，逗号或换行分隔\n203.0.113.10\n198.51.100.0/24'">
              </el-input>
              <p style="margin: 8px 0 0; color: #909399; line-height: 1.6;">
                留空表示不限制。支持单个 IP、CIDR 网段、逗号或换行分隔。当前访问 IP:
                <code>{{ apiConfig.currentIp || 'unknown' }}</code>
              </p>
              <div style="margin-top: 10px;">
                <el-button
                  type="primary"
                  size="small"
                  :loading="apiLoading"
                  :disabled="apiLoading"
                  @click="saveIpWhitelist">
                  保存IP白名单
                </el-button>
                <el-button
                  size="small"
                  :disabled="apiLoading"
                  @click="resetApiConfig">
                  重置
                </el-button>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="API端点">
            <div>
              <p style="margin: 5px 0; color: #606266;">文章创建API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article/create'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">文章更新API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article/update'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">文章异步创建API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article/createAsync'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">文章异步更新API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article/updateAsync'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">任务状态查询API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article/task/{taskId}'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">文章详情按路径API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article/path/{idOrSlug}'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">支付插件状态API:</p>
              <el-input
                :value="$constant.baseURL + '/api/payment/plugin/status'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">支付插件配置API:</p>
              <el-input
                :value="$constant.baseURL + '/api/payment/plugin/configure'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">支付插件连接测试API:</p>
              <el-input
                :value="$constant.baseURL + '/api/payment/plugin/testConnection'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">文章主题状态API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article-theme/status'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">文章主题激活API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article-theme/activate'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">文章复盘数据API:</p>
              <el-input
                :value="$constant.baseURL + '/api/article/analytics/{id}'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">站点访问趋势API:</p>
              <el-input
                :value="$constant.baseURL + '/api/analytics/site/visits?days=7'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">SEO状态API:</p>
              <el-input
                :value="$constant.baseURL + '/api/seo/status'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">受控SEO配置API:</p>
              <el-input
                :value="$constant.baseURL + '/api/seo/config'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
              <p style="margin: 5px 0; color: #606266;">Sitemap更新API:</p>
              <el-input
                :value="$constant.baseURL + '/api/seo/sitemap/update'"
                :disabled="true"
                style="width: 450px; margin-bottom: 10px;">
              </el-input>
            </div>
          </el-form-item>

          <el-form-item label="API文档">
            <div ref="apiDocsContent">
            <div class="api-doc-actions">
              <el-button
                type="primary"
                plain
                size="mini"
                :disabled="apiLoading"
                @click="copyAllApiDocs">
                复制全部文档
              </el-button>
            </div>
            <el-collapse>
              <el-collapse-item title="API调用概述" name="0">
                <div style="padding: 10px;">
                  <p><strong>API认证:</strong></p>
                  <p>所有API请求都需要在请求头中添加<code>X-API-KEY</code>字段，值为API密钥。</p>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,     // 200表示成功，500表示错误
  "message": null, // 错误信息，成功时通常为null
  "data": { ... }  // 响应数据
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="创建文章 API" name="1">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/article/create
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "title": "文章标题",
  "articleSlug": "seo-friendly-url",
  "content": "文章内容，支持Markdown格式",
  "cover": "封面图片URL(可选)",
  "sortName": "分类名称(将自动创建不存在的分类)",
  "sortDescription": "分类描述(可选，自动创建分类时使用)",
  "labelName": "标签名称(将自动创建不存在的标签)",
  "labelDescription": "标签描述(可选，自动创建标签时使用)",
  "summary": "文章摘要(可选)",
  "password": "文章密码(可选)",
  "createTime": "2024-01-01 12:00:00",
  "viewStatus": true,
  "commentStatus": true,
  "submitToSearchEngine": true
}
                  </pre>
                  <p><strong>说明:</strong> <code>createTime</code> 为可选字段，用于从其他系统迁移文章时回填原发布时间，不传则使用当前时间。</p>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "id": 123,
    "articleId": 123,
    "articleSlug": "seo-friendly-url",
    "articleUrl": "https://your-site.example.com/article/seo-friendly-url",
    "viewStatus": true,
    "sortId": 1,
    "labelId": 2
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="异步创建文章 API" name="2">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>适用于 OpenClaw 等需要轮询状态的自动化场景。请求体与同步创建接口一致。</p>
                  <p>不想上传封面时，可传 <code>cover: " "</code>；付费文章可额外传 <code>payType</code>、<code>payAmount</code>、<code>freePercent</code>。</p>
                  <p>当 <code>payType &gt; 0</code> 时，必须先在插件管理中启用并配置文章付费插件，否则接口会拒绝该请求。</p>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/article/createAsync
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "title": "文章标题",
  "articleSlug": "seo-friendly-url",
  "content": "文章内容，支持Markdown格式",
  "sortName": "分类名称",
  "labelName": "标签名称",
  "cover": " ",
  "viewStatus": true,
  "commentStatus": true,
  "submitToSearchEngine": false,
  "payType": 4,
  "payAmount": 19.9,
  "freePercent": 20
}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "taskId": "article_save_1741770000000_123",
    "status": "processing",
    "completed": false,
    "taskStatusUrl": "{{$constant.baseURL}}/api/article/task/article_save_1741770000000_123"
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="异步更新文章 API" name="3">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>需要传入文章 <code>id</code>。未传的分类、标签、状态字段会沿用原文章值。</p>
                  <p><code>articleSlug</code> 未传时沿用原文章 URL 别名；传空字符串会清空别名并回退到数字 ID。</p>
                  <p>如果只是想保留“无自定义封面”的状态，也可以传 <code>cover: " "</code>。</p>
                  <p>如果要改成付费文章，同样要求插件管理里的文章付费插件已经启用且配置完成。</p>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/article/updateAsync
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "id": 123,
  "title": "更新后的标题",
  "articleSlug": "updated-seo-friendly-url",
  "content": "更新后的Markdown内容",
  "cover": " ",
  "submitToSearchEngine": false,
  "payType": 2,
  "freePercent": 30
}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "taskId": "article_update_1741770000000_456",
    "status": "processing",
    "completed": false,
    "taskStatusUrl": "{{$constant.baseURL}}/api/article/task/article_update_1741770000000_456"
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="查询异步任务状态 API" name="4">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/article/task/article_save_1741770000000_123
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "taskId": "article_save_1741770000000_123",
    "status": "success",
    "stage": "complete",
    "message": "文章保存成功！AI摘要已生成",
    "articleId": 123,
    "articleSlug": "seo-friendly-url",
    "articleUrl": "https://your-site.example.com/article/seo-friendly-url",
    "translationStatus": "saved",
    "completed": true,
    "success": true,
    "failed": false
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="支付插件状态 API" name="5">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>用于 OpenClaw 或其他自动化工具检查 payment 插件是否已安装、已激活、已配置，并读取 <code>configSchema</code> 与缺失字段。</p>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/payment/plugin/status?pluginKey=afdian
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "activePluginKey": "afdian",
    "plugins": [
      {
        "pluginKey": "afdian",
        "pluginName": "爱发电",
        "enabled": true,
        "active": true
      }
    ],
    "targetPlugin": {
      "pluginKey": "afdian",
      "pluginName": "爱发电",
      "enabled": true,
      "active": true,
      "configSchema": {
        "userId": { "type": "string", "label": "用户ID" },
        "apiToken": { "type": "string", "label": "Token" }
      },
      "configured": false,
      "missingFields": ["userId", "apiToken"],
      "secretFieldStatus": {
        "apiToken": false
      },
      "nonSecretConfigPreview": {},
      "supportsConnectionTest": true
    }
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="支付插件配置 API" name="6">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>仅允许配置 <code>payment</code> 插件。接口会先做字段校验和连接测试，测试通过后才落库；可选自动激活该插件。</p>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/payment/plugin/configure
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "pluginKey": "afdian",
  "pluginConfig": {
    "userId": "your-user-id",
    "apiToken": "your-api-token"
  },
  "activate": true
}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "pluginKey": "afdian",
    "pluginName": "爱发电",
    "active": true,
    "configured": true,
    "connectionOk": true,
    "missingFields": [],
    "secretFieldStatus": {
      "apiToken": true
    },
    "nonSecretConfigPreview": {
      "userId": "your-user-id"
    },
    "message": "配置已保存并通过连接测试"
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="支付插件连接测试 API" name="7">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/payment/plugin/testConnection
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "pluginKey": "afdian",
  "pluginConfig": {
    "userId": "your-user-id",
    "apiToken": "your-api-token"
  }
}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "pluginKey": "afdian",
    "connectionOk": true,
    "message": "连接测试成功",
    "missingFields": [],
    "secretFieldStatus": {
      "apiToken": true
    },
    "nonSecretConfigPreview": {
      "userId": "your-user-id"
    }
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="获取文章列表 API" name="8">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/article/list?current=1&amp;size=10&amp;sortId=1&amp;labelId=1&amp;searchKey=关键词
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>参数说明:</strong></p>
                  <ul>
                    <li><code>current</code>: 当前页码，从1开始，默认为1</li>
                    <li><code>size</code>: 每页大小，默认为10</li>
                    <li><code>sortId</code>: 分类ID，可选</li>
                    <li><code>labelId</code>: 标签ID，可选</li>
                    <li><code>searchKey</code>: 搜索关键词，可选</li>
                  </ul>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "records": [
      {
        "id": 123,
        "articleTitle": "文章标题",
        "articleSlug": "seo-friendly-url",
        "articleContent": "文章内容摘要...",
        "articleCover": "图片URL",
        "viewCount": 100,
        "commentCount": 5,
        "createTime": "2023-04-01 12:00:00",
        "sort": { "id": 1, "sortName": "分类名称" },
        "label": { "id": 1, "labelName": "标签名称" }
      }
    ],
    "current": 1,
    "size": 10,
    "total": 42,
    "pages": 5
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="获取文章详情 API" name="9">
                <div style="padding: 10px;">
                  <p><strong>请求格式（两种方式任选）:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
# 按URL别名或数字ID（推荐）
GET {{$constant.baseURL}}/api/article/path/seo-friendly-url
X-API-KEY: {{apiConfig.apiKey}}

# 按数字ID
GET {{$constant.baseURL}}/api/article/123
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "id": 123,
    "articleTitle": "文章标题",
    "articleSlug": "seo-friendly-url",
    "articleContent": "完整文章内容，包括Markdown格式",
    "articleCover": "图片URL",
    "viewCount": 100,
    "commentStatus": true,
    "recommendStatus": false,
    "viewStatus": true,
    "createTime": "2023-04-01 12:00:00",
    "updateTime": "2023-04-02 14:30:00",
    "sortId": 1,
    "labelId": 1,
    "sortName": "分类名称",
    "labelName": "标签名称",
    "articleUrl": "https://your-site.example.com/article/seo-friendly-url"
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="获取分类列表 API" name="10">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/categories
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": [
    {
      "id": 1,
      "sortName": "技术文章",
      "sortDescription": "技术类文章",
      "sortType": 0,
      "priority": 1
    }
  ]
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="获取标签列表 API" name="11">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/tags
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": [
    {
      "id": 1,
      "sortId": 1,
      "labelName": "Java",
      "labelDescription": "Java编程语言"
    }
  ]
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="文章主题状态 API" name="12">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/article-theme/status
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "activePluginKey": "academic",
    "plugins": [
      {
        "pluginKey": "academic",
        "pluginName": "学术主题",
        "enabled": true,
        "active": true,
        "pluginConfig": {}
      }
    ]
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="文章主题激活 API" name="13">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/article-theme/activate
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "pluginKey": "academic"
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="文章复盘数据 API" name="14">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/article/analytics/123
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="站点访问趋势 API" name="15">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/analytics/site/visits?days=7
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>参数说明:</strong></p>
                  <ul>
                    <li><code>days</code>: 统计天数，仅支持 7 或 30</li>
                  </ul>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "daily": [
      {
        "visit_date": "2026-07-25",
        "unique_visits": 120,      // 独立IP数
        "total_visits": 340,       // 总访问次数
        "human_visits": 210,       // UA识别为 PC/移动端浏览器的访问（可能含高仿UA爬虫）
        "human_unique_visits": 95, // 浏览器UA独立IP
        "js_verified_visits": 150, // 前端JS上报的访问（真实执行了JS，最硬的真实访客口径）
        "js_verified_unique_visits": 80, // JS已验证独立IP
        "browser_no_js_visits": 55, // 浏览器UA但从未执行JS（大概率是高仿UA爬虫）
        "bot_visits": 100,         // 已识别的爬虫/扫描器/自动化访问
        "unclassified_visits": 30, // UA类型未能识别的访问
        "avg_unique_visits": 98.5,
        "avg_total_visits": 300.2
      }
    ],
    "summary": {                   // 周期汇总：人机分离总量与占比
      "days": 7,
      "total_visits": 2380,
      "human_visits": 1470,
      "js_verified_visits": 1050,
      "browser_no_js_visits": 385,
      "bot_visits": 700,
      "unclassified_visits": 210,
      "human_share_percent": 61.76,
      "js_verified_share_percent": 44.12,
      "bot_share_percent": 29.41,
      "unclassified_share_percent": 8.82
    },
    "ua_type_breakdown": [ ... ],  // 按UA类型（PC/移动/爬虫/扫描器等）的访问构成
    "top_uas": [ ... ],            // Top UA聚合，含 bot_verify_status 与 sample_ua
    "referrer_breakdown": [        // 来源站点 × 人机交叉统计
      {
        "referrer_host": "Direct",
        "visits": 900,
        "unique_visitors": 300,
        "human_visits": 320,
        "js_verified_visits": 180,
        "browser_no_js_visits": 140,
        "bot_visits": 450,
        "unclassified_visits": 130,
        "suspicious_share_percent": 80.0  // 含 browser_no_js；偏高说明 Direct 增长主要是爬虫噪声
      }
    ],
    "region_breakdown": [          // 地区分布（海外按国家、中国按省份，Top 30）
      {
        "region": "广东省",
        "visits": 320,
        "unique_visitors": 120,
        "js_verified_visits": 260   // 接近0说明该地区流量基本是爬虫（常见于数据中心所在地）
      }
    ],
    "agent_guide": { ... }         // 面向自动化工具的流量解读提示
  }
}
                  </pre>
                  <p><strong>解读建议:</strong> 判断真实流量请优先看 <code>js_verified_visits</code>（执行过JS的真实浏览器，高仿UA爬虫无法伪造）；<code>browser_no_js_visits</code> 是UA像浏览器但不执行JS的访问，大概率是伪装爬虫；若 <code>js_verified_visits</code> 平稳而 <code>total_visits</code> 突增，基本可判定为爬虫活动。</p>
                </div>
              </el-collapse-item>
              <el-collapse-item title="SEO状态 API" name="16">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/seo/status
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "enabled": true,
    "searchEnginePushEnabled": {
      "baidu": true,
      "google": true,
      "bing": false
    },
    "siteVerificationConfigured": {
      "baidu": true,
      "google": false
    },
    "sitemapAvailable": true,
    "lastSitemapUpdateTime": "2026-03-13T10:30:45",
    "searchEnginePingEnabled": true,
    "sitemapBaseUrl": "https://your-site.example.com",
    "summary": {
      "healthy": true,
      "warnings": []
    }
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="受控SEO配置 API" name="17">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>仅允许修改受控 SEO 字段，不允许通过 API-key 修改 <code>custom_head_code</code>、<code>robots_txt</code> 等高风险配置。</p>
                  <p><strong>读取当前配置:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/seo/config
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/seo/config
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "enable": true,
  "site_description": "新的站点描述",
  "site_keywords": "博客,自动化,OpenClaw",
  "default_author": "Admin",
  "og_image": "https://example.com/og.png",
  "site_logo": "https://example.com/logo.png",
  "og_type": "article",
  "twitter_card": "summary_large_image",
  "twitter_site": "@poetize",
  "twitter_creator": "@poetize",
  "baidu_push_enabled": true,
  "bing_push_enabled": false,
  "baidu_site_verification": "token-1",
  "google_site_verification": "token-2"
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="Sitemap更新 API" name="18">
                <div style="padding: 10px;">
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/seo/sitemap/update
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "triggered": true,
    "lastSitemapUpdateTime": "2026-03-13T10:31:10",
    "searchEnginePingEnabled": true,
    "siteBaseUrl": "https://your-site.example.com",
    "message": "sitemap 更新已触发"
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="同步更新文章 API" name="19">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>与异步更新接口参数一致，但同步返回结果，适合不想轮询任务状态的简单场景。同样支持可选的 <code>createTime</code> 回填。</p>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/article/update
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "id": 123,
  "title": "更新后的标题",
  "content": "更新后的Markdown内容"
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="局部更新文章章节 API" name="20">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>按章节标题定位并局部修改文章内容，避免整篇重新提交。<code>action</code> 支持：<code>replace</code>（替换章节）、<code>insert_after</code>（章节后插入）、<code>insert_before</code>（标题前插入）、<code>delete</code>（删除章节）、<code>append</code>（文末追加，无需 heading）。</p>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/article/updateSection
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "articleId": 123,
  "heading": "安装步骤",
  "action": "replace",
  "content": "## 安装步骤\n新的章节内容...",
  "skipAiTranslation": false
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="资源上传 API" name="21">
                <div style="padding: 10px;">
                  <p><strong>说明:</strong></p>
                  <p>上传图片等资源文件（multipart/form-data），返回可直接用作文章封面或正文图片的 URL。相同内容的文件会自动复用（<code>reused: true</code>）。</p>
                  <p><strong>请求格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/resource/upload
Content-Type: multipart/form-data
X-API-KEY: {{apiConfig.apiKey}}

file: (二进制文件)
type: articleCover        # 可选，默认 articleCover
                  </pre>
                  <p><strong>响应格式:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
{
  "code": 200,
  "message": null,
  "data": {
    "url": "/upload/articleCover/xxx.webp",
    "type": "articleCover",
    "originalName": "cover.png",
    "size": 102400,
    "mimeType": "image/webp",
    "reused": false
  }
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="评论列表/发表评论 API" name="22">
                <div style="padding: 10px;">
                  <p><strong>查询评论列表:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/comment/list
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "source": 123,            // 文章ID
  "commentType": "article",
  "current": 1,
  "size": 10,
  "floorCommentId": 456     // 可选，分页某楼层的子回复时传入
}
                  </pre>
                  <p><strong>发表/回复评论（免验证码）:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/comment/save
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "source": 123,
  "type": "article",
  "commentContent": "评论内容",
  "commentInfo": "{\"aiReply\":true}",  // 可选，标记为AI回复
  "parentCommentId": 456,               // 可选，回复某条评论
  "parentUserId": 1,                    // 可选
  "floorCommentId": 456                 // 可选，所属楼层
}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="翻译管理 API" name="23">
                <div style="padding: 10px;">
                  <p><strong>获取翻译 / 可用语言列表:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
GET {{$constant.baseURL}}/api/translation/get?articleId=123&amp;language=en
GET {{$constant.baseURL}}/api/translation/languages?articleId=123
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                  <p><strong>手动保存/覆盖翻译:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/translation/save
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "articleId": 123,
  "targetLanguage": "en",
  "translatedTitle": "Translated Title",
  "translatedContent": "Translated Markdown content...",
  "translatedSummary": "可选摘要"
}
                  </pre>
                  <p><strong>删除 / 重新生成翻译:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/translation/delete
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{ "articleId": 123, "language": "en" }

POST {{$constant.baseURL}}/api/translation/regenerate?articleId=123
X-API-KEY: {{apiConfig.apiKey}}
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="分类管理 API" name="24">
                <div style="padding: 10px;">
                  <p><strong>创建 / 更新 / 删除分类:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/sort/create
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "sortName": "技术文章",
  "sortDescription": "技术类文章",   // 必填
  "sortType": 1,                     // 可选，默认1（普通分类）
  "priority": 99                     // 可选，默认99
}

POST {{$constant.baseURL}}/api/sort/update
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{ "id": 1, "sortName": "新名称", "sortDescription": "新描述" }

GET {{$constant.baseURL}}/api/sort/delete?id=1
X-API-KEY: {{apiConfig.apiKey}}

-- 或者使用 POST 请求（符合 RESTful 规范） --
POST {{$constant.baseURL}}/api/sort/delete
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{ "id": 1 }
                  </pre>
                </div>
              </el-collapse-item>
              <el-collapse-item title="标签管理 API" name="25">
                <div style="padding: 10px;">
                  <p><strong>创建 / 更新 / 删除标签:</strong></p>
                  <pre style="background-color: #f5f7fa; padding: 10px; border-radius: 4px; overflow: auto;">
POST {{$constant.baseURL}}/api/label/create
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{
  "labelName": "Java",
  "labelDescription": "Java编程语言",  // 必填
  "sortId": 1                          // 必填，所属分类ID
}

POST {{$constant.baseURL}}/api/label/update
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{ "id": 1, "labelName": "新名称", "labelDescription": "新描述" }

GET {{$constant.baseURL}}/api/label/delete?id=1
X-API-KEY: {{apiConfig.apiKey}}

-- 或者使用 POST 请求（符合 RESTful 规范） --
POST {{$constant.baseURL}}/api/label/delete
Content-Type: application/json
X-API-KEY: {{apiConfig.apiKey}}

{ "id": 1 }
                  </pre>
                </div>
              </el-collapse-item>
            </el-collapse>
            </div>
          </el-form-item>

          <el-form-item>
            <div style="display: flex; justify-content: flex-end; margin-top: 20px;"></div>
          </el-form-item>
          </div>
        </el-form>
      </div>

    </el-card>
  </div>
</template>

<script>
import SectionTag from './SectionTag.vue';

export default {
  name: 'ApiSettings',
  components: { SectionTag },
  data() {
    return {
      apiConfig: {
        enabled: false,
        apiKey: '',
        ipWhitelist: '',
        currentIp: ''
      },
      apiLoading: false
    }
  },
  created() {
    this.getApiConfig();
  },
  methods: {
    async fetchApiConfig() {
      const res = await this.$http.get(this.$constant.baseURL + "/webInfo/getApiConfig", true);
      return Object.assign(
        { enabled: false, apiKey: '', ipWhitelist: '', currentIp: '' },
        res.data || {}
      );
    },
    async getApiConfig() {
      try {
        this.apiConfig = await this.fetchApiConfig();
      } catch (error) {
        this.$message.error("获取API配置失败: " + error.message);
        throw error;
      }
    },
    async handleApiToggle(value) {
      const previousConfig = { ...this.apiConfig, enabled: !value };
      this.apiConfig.enabled = value;

      try {
        await this.saveApiConfig({
          successMessage: value ? "API已启用" : "API已停用",
          errorPrefix: value ? "启用API失败" : "停用API失败"
        });
      } catch (error) {
        this.apiConfig = previousConfig;
      }
    },
    async regenerateApiKey() {
      this.apiLoading = true;
      try {
        const res = await this.$http.post(this.$constant.baseURL + "/webInfo/regenerateApiKey", {}, true);
        this.apiConfig.apiKey = res.data;
        this.$message({ message: "API密钥已重新生成", type: "success" });
      } catch (error) {
        this.$message({ message: "重新生成API密钥失败: " + error.message, type: "error" });
      } finally {
        this.apiLoading = false;
      }
    },
    buildApiConfigPayload() {
      return {
        enabled: this.apiConfig.enabled,
        apiKey: this.apiConfig.apiKey,
        ipWhitelist: this.apiConfig.ipWhitelist || ''
      };
    },
    async saveIpWhitelist() {
      try {
        await this.saveApiConfig({
          successMessage: "API IP白名单保存成功",
          errorPrefix: "保存API IP白名单失败"
        });
      } catch (error) {
        return;
      }
    },
    getApiDocsText() {
      const docsElement = this.$refs.apiDocsContent;
      if (!docsElement) {
        return "";
      }

      const sections = Array.from(docsElement.querySelectorAll('.el-collapse-item'))
        .map(section => this.formatApiDocSection(section))
        .filter(Boolean);

      if (!sections.length) {
        return "";
      }

      return this.sanitizeApiDocText([
        "# API文档",
        ...sections
      ].join("\n\n").trim());
    },
    formatApiDocSection(section) {
      const titleElement = section.querySelector('.el-collapse-item__header');
      const title = this.normalizeDocLine(titleElement ? titleElement.innerText : '');
      const content = section.querySelector('.el-collapse-item__content');
      if (!title || !content) {
        return "";
      }

      const body = this.formatDocChildren(content);
      if (!body) {
        return `## ${title}`;
      }

      return `## ${title}\n\n${body}`;
    },
    formatDocChildren(node) {
      return Array.from(node.childNodes)
        .map(child => this.formatDocNode(child))
        .filter(Boolean)
        .join("\n\n")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
    },
    formatDocNode(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        return this.normalizeDocLine(node.textContent || '');
      }

      if (node.nodeType !== Node.ELEMENT_NODE) {
        return "";
      }

      const tagName = node.tagName.toLowerCase();

      if (tagName === 'pre') {
        return this.formatCodeBlock(node.textContent || '');
      }

      if (tagName === 'ul') {
        return Array.from(node.children)
          .map(item => `- ${this.formatInlineContent(item)}`)
          .join("\n");
      }

      if (tagName === 'ol') {
        return Array.from(node.children)
          .map((item, index) => `${index + 1}. ${this.formatInlineContent(item)}`)
          .join("\n");
      }

      if (tagName === 'p') {
        return this.formatInlineContent(node);
      }

      if (tagName === 'div') {
        return this.formatDocChildren(node);
      }

      return this.formatInlineContent(node);
    },
    formatCodeBlock(text) {
      const normalized = text
        .replace(/\r\n/g, '\n')
        .replace(/\s+$/g, '')
        .trim();

      if (!normalized) {
        return "";
      }

      const language = this.detectCodeBlockLanguage(normalized);
      const fence = language ? `\`\`\`${language}` : "```";

      return [
        fence,
        normalized,
        "```"
      ].join("\n");
    },
    detectCodeBlockLanguage(text) {
      const lines = text.split('\n');
      const firstLine = lines.length ? lines[0].trim() : '';
      if (!firstLine) {
        return '';
      }

      if (/^(GET|POST|PUT|PATCH|DELETE)\s+/i.test(firstLine)) {
        return 'http';
      }

      if (/^[\[{]/.test(firstLine)) {
        return 'json';
      }

      return '';
    },
    formatInlineContent(node) {
      return Array.from(node.childNodes)
        .map(child => {
          if (child.nodeType === Node.TEXT_NODE) {
            return child.textContent || '';
          }

          if (child.nodeType !== Node.ELEMENT_NODE) {
            return '';
          }

          const tagName = child.tagName.toLowerCase();
          if (tagName === 'code') {
            return `\`${this.normalizeDocLine(child.textContent || '')}\``;
          }

          if (tagName === 'br') {
            return '\n';
          }

          return this.formatInlineContent(child);
        })
        .join('')
        .replace(/[ \t]+\n/g, '\n')
        .replace(/\n[ \t]+/g, '\n')
        .replace(/[ \t]{2,}/g, ' ')
        .replace(/\s+([,:;])/g, '$1')
        .replace(/\n{3,}/g, '\n\n')
        .trim();
    },
    normalizeDocLine(text) {
      return text
        .replace(/\s+/g, ' ')
        .trim();
    },
    sanitizeApiDocText(text) {
      let sanitized = text;
      const baseUrl = this.$constant && this.$constant.baseURL ? this.$constant.baseURL : '';
      const apiKey = this.apiConfig && this.apiConfig.apiKey ? this.apiConfig.apiKey : '';

      if (baseUrl) {
        sanitized = sanitized.replace(new RegExp(this.escapeRegExp(baseUrl), 'g'), 'https://your-site.example.com');
      }

      if (apiKey) {
        sanitized = sanitized.replace(new RegExp(this.escapeRegExp(apiKey), 'g'), 'YOUR_API_KEY');
      }

      return sanitized
        .replace(/\n{3,}/g, '\n\n')
        .trim();
    },
    escapeRegExp(text) {
      return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    },
    fallbackCopyText(text) {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.setAttribute("readonly", "readonly");
      textarea.style.position = "fixed";
      textarea.style.top = "-9999px";
      document.body.appendChild(textarea);
      textarea.select();

      try {
        document.execCommand("copy");
      } finally {
        document.body.removeChild(textarea);
      }
    },
    async copyAllApiDocs() {
      const docsText = this.getApiDocsText();
      if (!docsText) {
        this.$message.warning("暂无可复制的API文档");
        return;
      }

      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(docsText);
        } else {
          this.fallbackCopyText(docsText);
        }
        this.$message.success("API文档已复制");
      } catch (error) {
        this.$message.error("复制API文档失败: " + error.message);
      }
    },
    async saveApiConfig(options = {}) {
      const {
        successMessage = "API配置保存成功",
        errorPrefix = "保存API配置失败"
      } = options;

      this.apiLoading = true;
      try {
        await this.$http.post(this.$constant.baseURL + "/webInfo/saveApiConfig", this.buildApiConfigPayload(), true);
        this.apiConfig = await this.fetchApiConfig();
        this.$message({ message: successMessage, type: "success" });
      } catch (error) {
        this.$message({ message: errorPrefix + ": " + error.message, type: "error" });
        throw error;
      } finally {
        this.apiLoading = false;
      }
    },
    async resetApiConfig() {
      try {
        await this.getApiConfig();
      } catch (error) {
        return;
      }
    }
  }
}
</script>

<style scoped>
.api-doc-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.api-enabled-panel {
  max-height: 50vh;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 12px;
}

.api-enabled-panel::-webkit-scrollbar {
  width: 6px;
}

.api-enabled-panel::-webkit-scrollbar-thumb {
  background: #d7dee8;
  border-radius: 999px;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
}

@media screen and (max-width: 500px) {
  .el-input {
    width: 100% !important;
  }
}
</style>
