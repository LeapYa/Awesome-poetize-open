<template>
  <div :class="{ 'main-dark-mode': isDarkMode }">
    <div>
      <el-tag effect="dark" class="my-tag">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        统计信息
      </el-tag>
      <!-- 总览 -->
      <div id="field-main-stats" style="margin-top: 20px; padding: 0 10px;">
        <div class="history-title-bar">
          <div class="history-title">
            总览
          </div>
          <div class="history-actions">
            <el-button type="text"
                       class="history-action-button block-ip-entry"
                       @click="goToBlacklist"
                       title="封禁恶意IP / UA / 省份 / 国家，跳转到系统日志的封禁列表">
              <i class="el-icon-lock"></i>
              封禁IP/UA/省份/国家
            </el-button>
            <el-button type="text"
                       class="history-action-button"
                       @click="openCleanVisitDialog"
                       :loading="cleaningVisitData"
                       title="清理指定IP的访问统计">
              <i class="el-icon-delete"></i>
              清理访问数据
            </el-button>
            <el-button type="text"
                       class="history-action-button"
                       @click="refreshHistoryCache"
                       :loading="refreshing"
                       title="刷新统计数据">
              <i class="el-icon-refresh"></i>
              {{ refreshing ? '刷新中...' : '刷新' }}
            </el-button>
          </div>
        </div>

        <el-row :gutter="20" class="stat-cards">
          <!-- 访问统计 -->
          <el-col :xs="24" :sm="12" :lg="6" style="margin-bottom: 20px;">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-header">
                <span class="stat-value">{{ historyInfo.ip_history_count || 0 }}</span>
                <el-tag size="small" effect="dark" color="#3CAEFE" style="border:none; border-radius:12px">访问统计</el-tag>
              </div>
              <div class="stat-label">总访问量</div>
              <div class="stat-footer">
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.ip_count_today || 0 }}</span>
                  <span class="stat-footer-label">今日访问量</span>
                </div>
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.ip_count_yest || 0 }}</span>
                  <span class="stat-footer-label">昨日访问量</span>
                </div>
              </div>
            </el-card>
          </el-col>

          <!-- 用户统计 -->
          <el-col :xs="24" :sm="12" :lg="6" style="margin-bottom: 20px;">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-header">
                <span class="stat-value">{{ historyInfo.user_count || 0 }}</span>
                <el-tag size="small" effect="dark" color="#9C27B0" style="border:none; border-radius:12px">用户统计</el-tag>
              </div>
              <div class="stat-label">总用户</div>
              <div class="stat-footer">
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.subscribe_user_count || 0 }}</span>
                  <span class="stat-footer-label">订阅用户</span>
                </div>
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.user_today_visit || 0 }}</span>
                  <span class="stat-footer-label">今日浏览用户</span>
                </div>
              </div>
            </el-card>
          </el-col>

          <!-- 内容统计 -->
          <el-col :xs="24" :sm="12" :lg="6" style="margin-bottom: 20px;">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-header">
                <span class="stat-value">{{ historyInfo.article_count || 0 }}</span>
                <el-tag size="small" effect="dark" color="#2EE4A4" style="border:none; border-radius:12px">内容统计</el-tag>
              </div>
              <div class="stat-label">总文章数</div>
              <div class="stat-footer">
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.sort_count || 0 }}</span>
                  <span class="stat-footer-label">总分类数</span>
                </div>
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.resource_count || 0 }}</span>
                  <span class="stat-footer-label">总资源数</span>
                </div>
              </div>
            </el-card>
          </el-col>

          <!-- 互动统计 -->
          <el-col :xs="24" :sm="12" :lg="6" style="margin-bottom: 20px;">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-header">
                <span class="stat-value">{{ historyInfo.comment_count || 0 }}</span>
                <el-tag size="small" effect="dark" color="#FF8A65" style="border:none; border-radius:12px">互动统计</el-tag>
              </div>
              <div class="stat-label">总评论数</div>
              <div class="stat-footer">
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.tree_hole_count || 0 }}</span>
                  <span class="stat-footer-label">总树洞数</span>
                </div>
                <div class="stat-footer-item">
                  <span class="stat-footer-value">{{ historyInfo.love_count || 0 }}</span>
                  <span class="stat-footer-label">总表白数</span>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-tag effect="dark" class="my-tag" style="margin-top: 30px;">
        <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
          <path
            d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
            fill="#8C7BFD"></path>
          <path
            d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
            fill="#FFE37B"></path>
        </svg>
        访问信息
      </el-tag>

      <!-- 访问信息 Tabs -->
      <el-tabs id="field-main-visit" v-model="activeTab" style="margin-top: 15px; padding: 0 10px;">
        <!-- 总访问 -->
        <el-tab-pane label="总访问" name="total">
          <div class="history-info history-info--total">
            <div class="visit-table-panel">
              <div class="history-name">省份/国家访问TOP10</div>
              <div>
                <el-table :data="historyInfo.ip_history_province">
                  <el-table-column type="index" align="center" width="60"></el-table-column>
                  <el-table-column prop="province" align="center" label="省份/国家" min-width="120"></el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="80"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel">
              <div class="history-name">IP访问TOP10</div>
              <div>
                <el-table :data="historyInfo.ip_history_ip">
                  <el-table-column type="index" align="center" width="60"></el-table-column>
                  <el-table-column prop="ip" align="center" label="IP" min-width="120"></el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="80"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel visit-table-panel--ua">
              <div class="history-name">UA访问TOP10</div>
              <div>
                <el-table :data="getUaRows('ua_history_top')">
                  <el-table-column type="index" align="center" width="50"></el-table-column>
                  <el-table-column align="center" label="类型" width="90">
                    <template slot-scope="scope">
                      <el-tag class="ua-type-tag" size="mini" :type="getUaTagType(scope.row.ua_type)">
                        {{ scope.row.ua_type_label || '未知' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column align="center" label="客户端" min-width="130">
                    <template slot-scope="scope">
                      <el-tooltip :disabled="!scope.row.sample_ua" :content="scope.row.sample_ua" placement="top">
                        <span class="ua-name-cell">{{ scope.row.ua_name || '未知客户端' }}</span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="70"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel visit-table-panel--article">
              <div class="history-name">文章页面访问TOP10</div>
              <div>
                <el-table :data="getArticleRows('article_history_top')">
                  <el-table-column type="index" align="center" width="50"></el-table-column>
                  <el-table-column align="center" label="文章" min-width="160">
                    <template slot-scope="scope">
                      <el-tooltip :disabled="!scope.row.article_path" :content="scope.row.article_path" placement="top">
                        <span class="article-title-cell">{{ scope.row.article_title || scope.row.article_token || '未知文章' }}</span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="70"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel">
              <div class="history-name">来源网站TOP10</div>
              <div>
                <el-table :data="getReferrerRows('referrer_history_top')">
                  <el-table-column type="index" align="center" width="60"></el-table-column>
                  <el-table-column prop="referrer_host" align="center" label="来源网站" min-width="120">
                    <template slot-scope="scope">
                      <span :title="scope.row.referrer_host">
                        <i v-if="scope.row.referrer_host === 'Direct'" class="el-icon-link" style="color: #909399; margin-right: 3px;"></i>
                        {{ scope.row.referrer_host }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="80"></el-table-column>
                </el-table>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 今日访问 -->
        <el-tab-pane label="今日访问" name="today">
          <div class="history-info history-info--today">
            <div class="visit-table-panel">
              <div class="history-name">今日访问省份/国家统计</div>
              <div>
                <el-table :data="historyInfo.province_today">
                  <el-table-column type="index" align="center" width="60"></el-table-column>
                  <el-table-column prop="province" align="center" label="省份/国家" min-width="120"></el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="80"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel">
              <div class="history-name">今日访问用户</div>
              <div class="history-avatar">
                <el-table :data="historyInfo.username_today">
                  <el-table-column align="center" label="头像" width="60">
                    <template slot-scope="scope">
                      <el-avatar class="user-avatar" :size="30" :src="scope.row.avatar"></el-avatar>
                    </template>
                  </el-table-column>
                  <el-table-column prop="username" align="center" label="用户" min-width="100"></el-table-column>
                  <el-table-column prop="visitCount" align="center" label="次数" width="60"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel visit-table-panel--ua">
              <div class="history-name">今日UA访问统计</div>
              <div>
                <el-table :data="getUaRows('ua_today')">
                  <el-table-column type="index" align="center" width="50"></el-table-column>
                  <el-table-column align="center" label="类型" width="90">
                    <template slot-scope="scope">
                      <el-tag class="ua-type-tag" size="mini" :type="getUaTagType(scope.row.ua_type)">
                        {{ scope.row.ua_type_label || '未知' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column align="center" label="客户端" min-width="130">
                    <template slot-scope="scope">
                      <el-tooltip :disabled="!scope.row.sample_ua" :content="scope.row.sample_ua" placement="top">
                        <span class="ua-name-cell">{{ scope.row.ua_name || '未知客户端' }}</span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="70"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel visit-table-panel--article">
              <div class="history-name">今日文章页面访问统计</div>
              <div>
                <el-table :data="getArticleRows('article_today')">
                  <el-table-column type="index" align="center" width="50"></el-table-column>
                  <el-table-column align="center" label="文章" min-width="160">
                    <template slot-scope="scope">
                      <el-tooltip :disabled="!scope.row.article_path" :content="scope.row.article_path" placement="top">
                        <span class="article-title-cell">{{ scope.row.article_title || scope.row.article_token || '未知文章' }}</span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="70"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel">
              <div class="history-name">今日来源网站统计</div>
              <div>
                <el-table :data="getReferrerRows('referrer_today')">
                  <el-table-column type="index" align="center" width="60"></el-table-column>
                  <el-table-column prop="referrer_host" align="center" label="来源网站" min-width="120">
                    <template slot-scope="scope">
                      <span :title="scope.row.referrer_host">
                        <i v-if="scope.row.referrer_host === 'Direct'" class="el-icon-link" style="color: #909399; margin-right: 3px;"></i>
                        {{ scope.row.referrer_host }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="80"></el-table-column>
                </el-table>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 昨日访问 -->
        <el-tab-pane label="昨日访问" name="yesterday">
          <div class="history-info history-info--yesterday">
            <div class="visit-table-panel">
              <div class="history-name">昨日访问用户</div>
              <div class="history-avatar">
                <el-table :data="historyInfo.username_yest">
                  <el-table-column align="center" label="头像" width="60">
                    <template slot-scope="scope">
                      <el-avatar class="user-avatar" :size="30" :src="scope.row.avatar"></el-avatar>
                    </template>
                  </el-table-column>
                  <el-table-column prop="username" align="center" label="用户" min-width="100"></el-table-column>
                  <el-table-column prop="visitCount" align="center" label="次数" width="60"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel visit-table-panel--ua">
              <div class="history-name">昨日UA访问统计</div>
              <div>
                <el-table :data="getUaRows('ua_yest')">
                  <el-table-column type="index" align="center" width="50"></el-table-column>
                  <el-table-column align="center" label="类型" width="90">
                    <template slot-scope="scope">
                      <el-tag class="ua-type-tag" size="mini" :type="getUaTagType(scope.row.ua_type)">
                        {{ scope.row.ua_type_label || '未知' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column align="center" label="客户端" min-width="130">
                    <template slot-scope="scope">
                      <el-tooltip :disabled="!scope.row.sample_ua" :content="scope.row.sample_ua" placement="top">
                        <span class="ua-name-cell">{{ scope.row.ua_name || '未知客户端' }}</span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="70"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel visit-table-panel--article">
              <div class="history-name">昨日文章页面访问统计</div>
              <div>
                <el-table :data="getArticleRows('article_yest')">
                  <el-table-column type="index" align="center" width="50"></el-table-column>
                  <el-table-column align="center" label="文章" min-width="160">
                    <template slot-scope="scope">
                      <el-tooltip :disabled="!scope.row.article_path" :content="scope.row.article_path" placement="top">
                        <span class="article-title-cell">{{ scope.row.article_title || scope.row.article_token || '未知文章' }}</span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="70"></el-table-column>
                </el-table>
              </div>
            </div>
            <div class="visit-table-panel">
              <div class="history-name">昨日来源网站统计</div>
              <div>
                <el-table :data="getReferrerRows('referrer_yest')">
                  <el-table-column type="index" align="center" width="60"></el-table-column>
                  <el-table-column prop="referrer_host" align="center" label="来源网站" min-width="120">
                    <template slot-scope="scope">
                      <span :title="scope.row.referrer_host">
                        <i v-if="scope.row.referrer_host === 'Direct'" class="el-icon-link" style="color: #909399; margin-right: 3px;"></i>
                        {{ scope.row.referrer_host }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="num" align="center" label="数量" width="80"></el-table-column>
                </el-table>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
      <!-- 访问量历史趋势 -->
      <el-card shadow="hover" class="dashboard-box-card" style="margin: 30px 10px 0;">
        <div slot="header" class="dashboard-box-header">
          <span><i class="el-icon-s-data"></i> 访问量历史趋势</span>
        </div>
        <visit-stats ref="visitStatsChart"></visit-stats>
      </el-card>

      <el-dialog
        title="清理访问数据"
        :visible.sync="cleanDialogVisible"
        width="520px"
        :close-on-click-modal="!cleaningVisitData"
        :close-on-press-escape="!cleaningVisitData">
        <el-form label-width="95px" class="clean-visit-form">
          <el-form-item label="清理类型">
            <el-radio-group
              v-model="cleanVisitForm.cleanType"
              size="small"
              @change="handleCleanTypeChange">
              <el-radio-button label="ip">IP</el-radio-button>
              <el-radio-button label="ua">UA</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <template v-if="cleanVisitForm.cleanType === 'ip'">
            <el-form-item label="清理对象">
              <el-radio-group v-model="cleanVisitForm.targetType" @change="handleCleanTargetChange">
                <el-radio label="current">当前 IP</el-radio>
                <el-radio label="manual">指定 IP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="cleanVisitForm.targetType === 'current'" label="当前 IP">
              <div class="current-ip-line">
                <el-tag v-if="currentVisitIp" type="info" class="clean-ip-tag">{{ currentVisitIp }}</el-tag>
                <span v-else class="clean-ip-empty">{{ loadingCurrentIp ? '识别中...' : '未识别到有效IP' }}</span>
                <el-button type="text"
                           class="clean-ip-refresh"
                           :loading="loadingCurrentIp"
                           @click="fetchCurrentVisitIp">
                  刷新
                </el-button>
              </div>
            </el-form-item>
            <el-form-item v-else label="指定 IP">
              <el-input
                v-model.trim="cleanVisitForm.ip"
                placeholder="请输入要清理的 IP"
                clearable>
              </el-input>
            </el-form-item>
            <el-form-item label="后续统计">
              <el-checkbox v-model="cleanVisitForm.addToIgnore">
                同时加入忽略名单，以后不再统计这个 IP
              </el-checkbox>
            </el-form-item>
          </template>
          <el-form-item v-else label="UA">
            <el-input
              v-model.trim="cleanVisitForm.userAgent"
              type="textarea"
              :rows="4"
              maxlength="512"
              show-word-limit
              placeholder="粘贴完整 User-Agent">
            </el-input>
          </el-form-item>
          <el-alert
            class="clean-visit-alert"
            type="warning"
            show-icon
            :closable="false"
            :title="cleanVisitForm.cleanType === 'ua'
              ? '该操作会删除匹配 UA 的数据库历史记录，并清理最近 7 天 Redis 访问记录，执行后不可恢复。'
              : '该操作会删除数据库历史记录，并清理最近 7 天 Redis 访问记录，执行后不可恢复。'">
          </el-alert>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="cleanDialogVisible = false" :disabled="cleaningVisitData">取消</el-button>
          <el-button type="danger" @click="confirmCleanVisitData" :loading="cleaningVisitData">确认清理</el-button>
        </span>
      </el-dialog>

    </div>
  </div>
</template>

<script>
import VisitStats from './visitStats.vue';
import { setAdminContentLoading } from '@/utils/sessionValidation';

export default {
  components: {
    VisitStats
  },
  data() {
    return {
      historyInfo: {},
      loading: false,
      refreshing: false,
      isDarkMode: false,
      activeTab: 'total',
      cleanDialogVisible: false,
      currentVisitIp: '',
      loadingCurrentIp: false,
      cleaningVisitData: false,
      cleanVisitForm: {
        cleanType: 'ip',
        targetType: 'current',
        ip: '',
        userAgent: '',
        addToIgnore: false
      }
    }
  },

  computed: {},

  watch: {},

  created() {
    this.getHistoryInfo();
    // 初始化主题
    this.updateTheme();
  },

  mounted() {
    // 监听主题变化
    this.setupThemeListener();
  },

  beforeDestroy() {
    this.setContentLoading(false);

    // 清理全局事件监听
    if (this.themeChangeListener) {
      this.$root.$off('theme-changed', this.themeChangeListener);
    }
    
    // 清理 storage 事件监听
    if (this.themeListener) {
      window.removeEventListener('storage', this.themeListener);
    }
  },

  methods: {
    goToBlacklist() {
      this.$confirm(
        '将跳转到「系统日志」页面的封禁列表（含安全黑名单与验证码自动封禁），支持封禁 IP / UA / IP网段 / 省份 / 国家，是否继续？',
        '封禁IP/UA/省份/国家',
        {
          confirmButtonText: '前往查看',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        this.$router.push({ path: '/systemLog', query: { blacklist: '1' } });
      }).catch(() => {});
    },
    setContentLoading(loading) {
      if (this.loading === loading) {
        return;
      }
      this.loading = loading;
      setAdminContentLoading(loading);
    },

    getHistoryInfo(showLoading = true) {
      if (showLoading) {
        this.setContentLoading(true);
      }
      this.$http.get(this.$constant.baseURL + "/webInfo/getHistoryInfo", {}, true)
        .then((res) => {
          if (!this.$common.isEmpty(res.data)) {
            this.historyInfo = res.data;
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message,
            type: "error"
          });
        })
        .finally(() => {
          if (showLoading) {
            this.setContentLoading(false);
          }
        });
    },
    
    refreshHistoryCache() {
      if (this.refreshing) return;
      
      this.refreshing = true;
      this.$http.post(this.$constant.baseURL + "/webInfo/refreshHistoryCache", {}, true)
        .then((res) => {
          if (res.success || res.code === 200) {
            this.$message({
              message: `缓存刷新成功！总访问量: ${res.data.totalCount}`,
              type: "success"
            });
            // 刷新完成后重新获取数据
            this.getHistoryInfo(false);
          } else {
            this.$message({
              message: res.message || '刷新失败',
              type: "error"
            });
          }
        })
        .catch((error) => {
          this.$message({
            message: error.message || '网络错误，请稍后重试',
            type: "error"
          });
        })
        .finally(() => {
          this.refreshing = false;
        });
    },

    openCleanVisitDialog() {
      this.cleanVisitForm = {
        cleanType: 'ip',
        targetType: 'current',
        ip: '',
        userAgent: '',
        addToIgnore: false
      };
      this.cleanDialogVisible = true;
      this.fetchCurrentVisitIp();
    },

    handleCleanTypeChange(cleanType) {
      if (cleanType === 'ip' && this.cleanVisitForm.targetType === 'current' && !this.currentVisitIp) {
        this.fetchCurrentVisitIp();
      }
    },

    handleCleanTargetChange(targetType) {
      if (targetType === 'current' && !this.currentVisitIp) {
        this.fetchCurrentVisitIp();
      }
    },

    fetchCurrentVisitIp() {
      if (this.loadingCurrentIp) return;

      this.loadingCurrentIp = true;
      this.$http.get(this.$constant.baseURL + "/webInfo/getCurrentVisitIp", {}, true)
        .then((res) => {
          const data = res.data || {};
          this.currentVisitIp = data.valid ? (data.ip || '') : '';
          if (!data.valid) {
            this.$message({
              message: '当前请求未识别到可清理的有效 IP，可以切换为指定 IP',
              type: 'warning'
            });
          }
        })
        .catch((error) => {
          this.currentVisitIp = '';
          this.$message({
            message: error.message || '获取当前 IP 失败',
            type: 'error'
          });
        })
        .finally(() => {
          this.loadingCurrentIp = false;
        });
    },

    getCleanTargetIp() {
      if (this.cleanVisitForm.targetType === 'current') {
        return (this.currentVisitIp || '').trim();
      }
      return (this.cleanVisitForm.ip || '').trim();
    },

    getCleanTargetValue() {
      if (this.cleanVisitForm.cleanType === 'ua') {
        return (this.cleanVisitForm.userAgent || '').trim();
      }
      return this.getCleanTargetIp();
    },

    formatCleanTargetValue(value) {
      if (!value || value.length <= 80) {
        return value;
      }
      return value.slice(0, 80) + '...';
    },

    confirmCleanVisitData() {
      if (this.cleaningVisitData) return;

      const cleanType = this.cleanVisitForm.cleanType || 'ip';
      const targetValue = this.getCleanTargetValue();
      const targetLabel = cleanType === 'ua' ? 'UA' : 'IP';
      const displayTargetValue = this.formatCleanTargetValue(targetValue);
      if (!targetValue) {
        this.$message({
          message: `请先输入要清理的 ${targetLabel}`,
          type: 'warning'
        });
        return;
      }

      const ignoreText = cleanType === 'ip' && this.cleanVisitForm.addToIgnore ? '，并加入忽略名单' : '';
      this.$confirm(`确定要清理 ${targetLabel}「${displayTargetValue}」的访问统计${ignoreText}吗？该操作不可恢复。`, '确认清理', {
        confirmButtonText: '确认清理',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.cleaningVisitData = true;
        const payload = {
          cleanType,
          targetValue
        };
        if (cleanType === 'ua') {
          payload.userAgent = targetValue;
        } else {
          payload.useCurrentIp = this.cleanVisitForm.targetType === 'current';
          payload.ip = this.cleanVisitForm.targetType === 'manual' ? targetValue : '';
          payload.addToIgnore = this.cleanVisitForm.addToIgnore;
        }

        this.$http.post(this.$constant.baseURL + "/webInfo/cleanVisitData", payload, true)
          .then((res) => {
            const data = res.data || {};
            const ignoreResult = data.addedToIgnore
              ? '，已加入忽略名单'
              : (data.ignoreRequested && data.alreadyIgnored ? '，已在忽略名单中' : '');
            this.$message({
              message: `清理完成：数据库 ${data.deletedDbCount || 0} 条，Redis ${data.removedRedisCount || 0} 条${cleanType === 'ip' ? ignoreResult : ''}`,
              type: 'success'
            });
            this.cleanDialogVisible = false;
            this.getHistoryInfo(false);
            if (this.$refs.visitStatsChart && this.$refs.visitStatsChart.fetchVisitStats) {
              this.$refs.visitStatsChart.fetchVisitStats();
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message || '清理访问数据失败',
              type: 'error'
            });
          })
          .finally(() => {
            this.cleaningVisitData = false;
          });
      }).catch(() => {
        // 取消操作
      });
    },
    
    // 更新主题状态
    updateTheme() {
      const theme = localStorage.getItem('theme');
      if (theme) {
        // 用户手动设置了主题
        this.isDarkMode = theme === 'dark';
      } else {
        // 用户未设置，检查 DOM 或系统偏好
        const hasDarkClass = document.body.classList.contains('dark-mode') || 
                            document.documentElement.classList.contains('dark-mode');
        if (hasDarkClass) {
          this.isDarkMode = true;
        } else {
          // 最后检查系统偏好
          const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
          this.isDarkMode = prefersDark;
        }
      }
    },
    
    // 监听主题变化
    setupThemeListener() {
      // 监听全局主题变化事件（由父组件 admin.vue 触发）
      this.themeChangeListener = (isDark) => {
        this.isDarkMode = isDark;
      };
      this.$root.$on('theme-changed', this.themeChangeListener);
      
      // 监听 storage 事件（跨标签页）
      this.themeListener = (e) => {
        if (e.key === 'theme') {
          this.updateTheme();
        }
      };
      window.addEventListener('storage', this.themeListener);
    },

    getUaRows(key) {
      const rows = this.historyInfo && this.historyInfo[key];
      return Array.isArray(rows) ? rows : [];
    },

    getArticleRows(key) {
      const rows = this.historyInfo && this.historyInfo[key];
      return Array.isArray(rows) ? rows : [];
    },

    getReferrerRows(key) {
      const rows = this.historyInfo && this.historyInfo[key];
      return Array.isArray(rows) ? rows : [];
    },

    getUaTagType(type) {
      const tagTypes = {
        search_engine: 'success',
        spoofed_search_engine: 'danger',
        scanner: 'danger',
        crawler: 'warning',
        automation: 'warning',
        mobile: '',
        pc: 'info',
        unknown: 'danger'
      };
      return tagTypes[type] || 'info';
    }
  }
}
</script>

<style scoped>

  .my-tag {
    width: calc(100% - 20px);
    text-align: left;
    background: var(--lightYellow);
    border: none;
    height: 40px;
    line-height: 40px;
    font-size: 16px;
    color: var(--black);
  }

  .el-tag {
    margin: 10px;
  }

  .history-title {
    margin: 15px auto 15px;
    width: 120px;
    text-align: center;
    padding: 10px 20px;
    background: var(--lightGreen);
    color: var(--white);
    font-weight: bold;
    border-radius: 5px;
  }

  .history-title-bar {
    position: relative;
    min-height: 55px;
    margin-bottom: 20px;
  }

  .history-title-bar .history-title {
    margin-bottom: 0;
  }

  .history-actions {
    position: absolute;
    top: 15px;
    right: 0;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .history-action-button {
    color: #409EFF;
    padding: 0;
  }

  .history-action-button.block-ip-entry,
  .history-action-button.block-ip-entry i,
  ::v-deep .history-action-button.block-ip-entry span {
    color: #f56c6c;
  }

  .history-action-button.block-ip-entry:hover,
  .history-action-button.block-ip-entry:focus,
  .history-action-button.block-ip-entry:hover i,
  .history-action-button.block-ip-entry:focus i,
  ::v-deep .history-action-button.block-ip-entry:hover span,
  ::v-deep .history-action-button.block-ip-entry:focus span {
    color: #f78989;
  }

  .clean-visit-form {
    padding-right: 10px;
  }

  .current-ip-line {
    display: flex;
    align-items: center;
    gap: 10px;
    min-height: 32px;
  }

  .clean-ip-tag {
    margin: 0;
  }

  .clean-ip-empty {
    color: #909399;
  }

  .clean-ip-refresh {
    padding: 0;
  }

  .clean-visit-alert {
    margin-top: 6px;
  }

  .history-name {
    font-size: 18px;
    font-weight: bold;
    margin: 0 10px 10px 0;
    text-align: center;
    color: var(--black, #333);
    transition: color 0.3s ease;
  }

  .history-info {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-around;
    gap: 20px;
    width: 100%;
    text-align: center;
    margin: 20px auto 0;
  }

  .history-info--total {
    max-width: 1680px;
  }

  .history-info--today {
    max-width: 1720px;
  }

  .history-info--yesterday {
    max-width: 1400px;
  }

  .visit-table-panel {
    width: 100%;
    max-width: 300px;
    margin-bottom: 20px;
  }

  .visit-table-panel--ua {
    max-width: 360px;
  }

  .visit-table-panel--article {
    max-width: 360px;
  }

  .ua-name-cell {
    display: inline-block;
    max-width: 150px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }

  .article-title-cell {
    display: inline-block;
    max-width: 190px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }

  .ua-type-tag {
    margin: 0;
  }

  /* 移动端表格适配 */
  @media screen and (max-width: 768px) {
    .history-info {
      flex-direction: column;
      align-items: center;
    }
    
    .history-info > div {
      margin-right: 0 !important;
    }

    .history-title-bar {
      padding-bottom: 30px;
    }

    .history-actions {
      top: 58px;
      left: 0;
      right: 0;
      justify-content: center;
    }
  }

  .history-info >>> .el-table .cell {
    line-height: unset;
  }

  .history-avatar >>> .el-table .el-table__row .el-table__cell {
    padding: 3.5px 0;
  }

  .history-info >>> .el-table::before {
    height: unset;
  }

  #field-main-visit >>> #tab-total {
    padding-left: 20px;
  }

  #field-main-visit >>> #tab-yesterday {
    padding-right: 20px;
  }

  .stat-cards {
    margin: 0 auto;
  }

  .stat-card {
    border-radius: 12px;
    background: #fdfdfd;
    min-height: 180px;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }

  .stat-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .stat-value {
    font-size: 28px;
    font-weight: bold;
    color: var(--black);
  }

  .stat-label {
    margin-top: 10px;
    font-size: 14px;
    color: #666;
  }

  .stat-footer {
    margin-top: 25px;
    display: flex;
    justify-content: space-between;
  }

  .stat-footer-item {
    display: flex;
    flex-direction: column;
  }

  .stat-footer-value {
    font-size: 16px;
    font-weight: bold;
    color: var(--black);
  }

  .stat-footer-label {
    margin-top: 5px;
    font-size: 12px;
    color: #999;
  }

  .dashboard-box-card {
    border-radius: 12px;
    border: none;
    box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
  }
  
  .dashboard-box-header {
    font-size: 16px;
    font-weight: bold;
    color: var(--black);
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  /* ========== 深色模式适配 ========== */
  .main-dark-mode .my-tag {
    background: var(--lightYellow);
    color: var(--black);
  }

  .main-dark-mode .stat-card {
    background: #242526;
    border: 1px solid #34383f;
  }

  .main-dark-mode .stat-value {
    color: var(--white);
  }

  .main-dark-mode .stat-label {
    color: #aaa;
  }

  .main-dark-mode .stat-footer-value {
    color: var(--white);
  }

  .main-dark-mode .stat-footer-label {
    color: #888;
  }

  .main-dark-mode .history-title {
    background: var(--lightGreen);
    color: var(--white);
  }

  .main-dark-mode .dashboard-box-card {
    background: #242526;
    border: 1px solid #34383f;
  }

  .main-dark-mode .dashboard-box-header {
    color: var(--white);
  }

  /* 所有标题文字 */
  .main-dark-mode .history-name {
    color: rgba(255, 255, 255, 0.9) !important;
  }

  /* 表格样式 */
  .main-dark-mode >>> .el-table {
    overflow: hidden;
    border-radius: 8px;
    background-color: #202124 !important;
    color: rgba(255, 255, 255, 0.88) !important;
  }

  .main-dark-mode >>> .el-table__header-wrapper,
  .main-dark-mode >>> .el-table__body-wrapper {
    background-color: #202124 !important;
  }

  .main-dark-mode >>> .el-table th,
  .main-dark-mode >>> .el-table th.el-table__cell {
    background-color: #282a2e !important;
    color: rgba(255, 255, 255, 0.92) !important;
    border-color: #3a3d43 !important;
  }

  .main-dark-mode >>> .el-table tr,
  .main-dark-mode >>> .el-table td {
    background-color: #202124 !important;
    color: rgba(255, 255, 255, 0.86) !important;
    border-color: #33363c !important;
  }

  .main-dark-mode >>> .el-table--enable-row-hover .el-table__body tr:hover > td {
    background-color: #2b3038 !important;
  }

  .main-dark-mode >>> .el-table::before {
    background-color: #33363c !important;
  }

  .main-dark-mode >>> .el-table .cell {
    color: rgba(255, 255, 255, 0.86) !important;
  }

  .main-dark-mode >>> .el-table__empty-text {
    color: rgba(255, 255, 255, 0.46) !important;
  }

  /* 加载动画 */
  .main-dark-mode >>> .el-loading-mask {
    background-color: rgba(0, 0, 0, 0.8) !important;
  }

  .main-dark-mode >>> .el-loading-spinner .el-icon-loading {
    color: var(--lightGreen) !important;
  }

  .main-dark-mode >>> .el-loading-spinner .el-loading-text {
    color: rgba(255, 255, 255, 0.9) !important;
  }

  /* 刷新按钮 */
  .main-dark-mode >>> .el-button--text {
    color: #409EFF !important;
  }

  /* Tabs 组件适配 */
  .main-dark-mode >>> .el-tabs__nav.is-top {
    background-color: transparent !important;
  }

  .main-dark-mode >>> #tab-total.is-active {
    background-color: rgba(64, 158, 255, 0.12) !important;
    color: #409EFF !important;
  }
  
  .main-dark-mode >>> .el-tabs__nav {
    border: none !important;
  }
  
  .main-dark-mode >>> .el-tabs__header {
    background: transparent !important;
    border-bottom: 1px solid rgba(255,255,255,0.12) !important;
    margin-bottom: 0;
  }

  .main-dark-mode >>> .el-tabs__content {
    background-color: rgba(255, 255, 255, 0.035) !important;
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-top: none;
    border-radius: 0 0 10px 10px;
    padding: 18px 16px 22px;
  }

  .main-dark-mode >>> .el-tabs__item {
    color: rgba(255, 255, 255, 0.7) !important;
  }

  .main-dark-mode >>> .el-tabs__item.is-active,
  .main-dark-mode >>> .el-tabs__item:hover {
    color: #409EFF !important;
  }

  .main-dark-mode >>> .el-tabs__item.is-active {
    background-color: rgba(64, 158, 255, 0.1) !important;
  }

  .main-dark-mode >>> .el-tabs__nav-wrap::after {
    background-color: rgba(255, 255, 255, 0.1) !important;
  }
  
  .main-dark-mode >>> .el-tabs__active-bar {
    background-color: #409EFF !important;
  }

</style>
