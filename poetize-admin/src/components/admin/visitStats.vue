<template>
    <div class="stats-container" :class="{ 'stats-dark-mode': isDarkMode }">
      <!-- 统计卡片区域 -->
      <div class="stat-cards">
        <div class="stat-card">
          <span class="stat-value">{{ jsVerifiedVisits }}</span>
          <span class="stat-label">真实访客（JS已验证）</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ totalVisits }}</span>
          <span class="stat-label">总访问量（含爬虫）</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ uniqueVisits }}</span>
          <span class="stat-label">独立访客IP</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ botVisits }}</span>
          <span class="stat-label">爬虫/扫描访问</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ browserNoJsVisits }}</span>
          <span class="stat-label">伪装浏览器（无JS）</span>
        </div>
      </div>
      
      <!-- 图表区域 -->
      <div class="chart-wrapper">
        <div class="chart-header">
          <h3 class="chart-title">网站访问统计</h3>
          <div class="chart-controls">
            <div class="time-selector">
              <button 
                v-for="period in ['7', '30', '90']" 
                :key="period"
                @click="timeRange = period; fetchVisitStats()"
                :class="['time-btn', timeRange === period ? 'active' : '']">
                {{ period === '7' ? '最近7天' : period === '30' ? '最近30天' : '最近90天' }}
              </button>
            </div>
            <button class="refresh-btn" @click="fetchVisitStats()">
              <i class="el-icon-refresh"></i>
            </button>
          </div>
        </div>
        <div id="visitChart" class="chart-area"></div>
        <p class="chart-hint">真实访客（JS已验证）= 前端JS成功执行并上报的访客；伪装浏览器（无JS）= UA像浏览器但从未执行JS的访问，大概率为爬虫。</p>

        <!-- 加载遮罩 -->
        <div v-if="loading" class="loading-overlay">
          <div class="loading-spinner"></div>
        </div>
      </div>

      <!-- 访客地区分布地图 -->
      <div class="chart-wrapper">
        <div class="chart-header">
          <h3 class="chart-title">访客地区分布</h3>
          <div class="chart-controls">
            <div class="time-selector">
              <button
                @click="mapMode = 'real'; updateMap()"
                :class="['time-btn', mapMode === 'real' ? 'active' : '']">
                真实访客
              </button>
              <button
                @click="mapMode = 'all'; updateMap()"
                :class="['time-btn', mapMode === 'all' ? 'active' : '']">
                全部访问
              </button>
            </div>
          </div>
        </div>
        <div id="provinceMap" class="map-area"></div>
        <div v-if="mapLoadFailed" class="map-fallback">地图加载失败，请检查 map/world-cn.json 是否存在后刷新重试</div>
        <p class="chart-hint">世界底图 + 中国省级细分：默认按“真实访客（JS已验证）”着色，可切换为全部访问；支持拖拽与滚轮缩放，悬停查看具体数据。</p>

        <!-- 海外地区条形列表 -->
        <div v-if="overseasStats.length" class="overseas-section">
          <h4 class="overseas-title">海外地区 Top</h4>
          <div v-for="item in overseasStats" :key="item.province" class="overseas-row">
            <span class="overseas-name">{{ item.province }}</span>
            <div class="overseas-bar-wrap">
              <div class="overseas-bar" :style="{ width: item.percent + '%' }"></div>
            </div>
            <span class="overseas-value">{{ mapMode === 'real' ? item.js_verified_visits : item.num }}</span>
          </div>
        </div>

        <!-- 加载遮罩 -->
        <div v-if="mapLoading" class="loading-overlay">
          <div class="loading-spinner"></div>
        </div>
      </div>
    </div>
  </template>
  
  <script>
  // 导入ECharts
  import * as echarts from 'echarts'
  
  export default {
    data() {
      return {
        timeRange: '30',
        loading: false,
        visitStats: [],
        chart: null,
        isDarkMode: false,
        // 访客地区分布地图
        provinceStats: [],
        mapChart: null,
        mapMode: 'real',
        mapLoading: false,
        mapLoadFailed: false,
        chinaMapRegistered: false,
        chinaProvinceNames: null,
        // 当前 visualMap 是否为小屏紧凑态，跨断点时重绘地图
        mapCompact: null
      }
    },
  
    computed: {
      totalVisits() {
        if (!this.visitStats.length) return 0;
        return this.visitStats.reduce((sum, item) => sum + item.total_visits, 0);
      },
      uniqueVisits() {
        if (!this.visitStats.length) return 0;
        return this.visitStats.reduce((sum, item) => sum + item.unique_visits, 0);
      },
      jsVerifiedVisits() {
        if (!this.visitStats.length) return 0;
        return this.visitStats.reduce((sum, item) => sum + (item.js_verified_visits || 0), 0);
      },
      botVisits() {
        if (!this.visitStats.length) return 0;
        return this.visitStats.reduce((sum, item) => sum + (item.bot_visits || 0), 0);
      },
      browserNoJsVisits() {
        if (!this.visitStats.length) return 0;
        return this.visitStats.reduce((sum, item) => sum + (item.browser_no_js_visits || 0), 0);
      },
      // 海外地区（不属于中国省份的地区）Top 8，按当前地图口径取值
      overseasStats() {
        if (!this.provinceStats.length || !this.chinaProvinceNames) return [];
        const valueOf = (item) => this.mapMode === 'real'
          ? (item.js_verified_visits || 0)
          : (item.num || 0);
        const overseas = this.provinceStats
          .filter(item => !this.chinaProvinceNames.has(this.normalizeProvinceName(item.province)))
          .sort((a, b) => valueOf(b) - valueOf(a))
          .slice(0, 8);
        const max = overseas.length ? Math.max(valueOf(overseas[0]), 1) : 1;
        return overseas.map(item => ({
          ...item,
          percent: Math.max(Math.round(valueOf(item) * 100 / max), 2)
        }));
      }
    },
  
    mounted() {
      this.updateTheme();
      this.setupThemeListener();
      this.initChart();
      this.initMap();
      this.fetchVisitStats();
      
      // 响应窗口大小变化
      window.addEventListener('resize', this.resizeChart);
      // 侧边栏折叠/移动端布局变化不会触发 window resize，用 ResizeObserver 跟随容器实际宽度
      this.setupResizeObserver();
    },
  
    beforeDestroy() {
      if (this.chart) {
        this.chart.dispose();
        this.chart = null;
      }
      if (this.mapChart) {
        this.mapChart.dispose();
        this.mapChart = null;
      }
      window.removeEventListener('resize', this.resizeChart);
      if (this.resizeObserver) {
        this.resizeObserver.disconnect();
        this.resizeObserver = null;
      }
      if (this.resizeRaf) {
        cancelAnimationFrame(this.resizeRaf);
        this.resizeRaf = null;
      }

      // 清理全局事件监听
      if (this.themeChangeListener) {
        this.$root.$off('theme-changed', this.themeChangeListener);
      }
    },

    methods: {
      initChart() {
        const chartDom = document.getElementById('visitChart');
        this.chart = echarts.init(chartDom);
      },
  
      resizeChart() {
        if (this.chart) {
          this.chart.resize();
        }
        if (this.mapChart) {
          this.mapChart.resize();
          // 跨越移动端断点时重设 visualMap 尺寸
          const isCompact = window.innerWidth <= 768;
          if (this.mapCompact !== null && isCompact !== this.mapCompact) {
            this.updateMap();
          }
        }
      },

      // 监听图表容器自身尺寸：侧边栏 left 有 0.3s 过渡，初始化时量到的是过渡中的窄宽度，
      // 容器宽度稳定后由 ResizeObserver 触发 resize 校正（rAF 合帧，避免过渡期间高频重绘）
      setupResizeObserver() {
        if (typeof ResizeObserver === 'undefined') return;
        this.resizeObserver = new ResizeObserver(() => {
          if (this.resizeRaf) cancelAnimationFrame(this.resizeRaf);
          this.resizeRaf = requestAnimationFrame(() => this.resizeChart());
        });
        const chartDom = document.getElementById('visitChart');
        const mapDom = document.getElementById('provinceMap');
        if (chartDom) this.resizeObserver.observe(chartDom);
        if (mapDom) this.resizeObserver.observe(mapDom);
      },
  
      updateChart() {
        if (!this.chart) return;

        // 生成完整的日期范围
        const now = new Date();
        const days = parseInt(this.timeRange);
        const dateRange = [];
        const fullData = {};
        
        // 创建完整的日期范围数组和数据映射对象
        for (let i = days - 1; i >= 0; i--) {
          const date = new Date(now);
          date.setDate(date.getDate() - i);
          const dateStr = this.formatDate(date);
          dateRange.push(dateStr);
          fullData[dateStr] = { 
            visit_date: dateStr,
            unique_visits: 0,
            total_visits: 0,
            avg_unique_visits: 0,
            js_verified_visits: 0,
            browser_no_js_visits: 0,
            bot_visits: 0
          };
        }
        
        // 用实际数据填充映射对象
        if (this.visitStats && this.visitStats.length) {
          this.visitStats.forEach(item => {
            if (fullData[item.visit_date]) {
              fullData[item.visit_date] = item;
            }
          });
        }
        
        // 从映射对象生成完整的数据数组
        const completeDataset = dateRange.map(date => fullData[date]);
        
        // 提取图表所需的数据点
        const dates = completeDataset.map(item => item.visit_date);
        const uniqueVisits = completeDataset.map(item => item.unique_visits);
        const totalVisits = completeDataset.map(item => item.total_visits);
        const jsVerifiedVisits = completeDataset.map(item => item.js_verified_visits || 0);
        const browserNoJsVisits = completeDataset.map(item => item.browser_no_js_visits || 0);
        const botVisits = completeDataset.map(item => item.bot_visits || 0);
        
        // 计算平均值
        const avgVisits = this.calculateAverage(uniqueVisits);
        const avgLine = Array(dates.length).fill(avgVisits);
        
        // 深色模式颜色适配
        const isDark = this.isDarkMode;
        const textColor = isDark ? 'rgba(255, 255, 255, 0.7)' : '#86868b';
        const splitLineColor = isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.05)';
        const tooltipBgColor = isDark ? 'rgba(30, 30, 30, 0.95)' : 'rgba(255, 255, 255, 0.9)';
        const tooltipTextColor = isDark ? '#f5f5f7' : '#1d1d1f';
        const tooltipLabelColor = isDark ? 'rgba(255, 255, 255, 0.6)' : '#86868b';
  
        const option = {
          backgroundColor: 'transparent',
          grid: {
            left: '3%',
            right: '4%',
            bottom: '40px',
            top: '10px',
            containLabel: true
          },
          tooltip: {
            trigger: 'axis',
            backgroundColor: tooltipBgColor,
            borderRadius: 8,
            borderWidth: 0,
            padding: [8, 12],
            textStyle: {
              color: tooltipTextColor,
              fontSize: 12
            },
            axisPointer: {
              type: 'line',
              lineStyle: {
                color: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
                width: 1
              }
            },
            formatter: function(params) {
              let date = params[0].axisValue;
              let result = `<div style="font-weight: 600; margin-bottom: 6px; font-size: 12px;">${date}</div>`;
              
              params.forEach(item => {
                let color = '';
                if (item.seriesName === '真实访客(JS已验证)') color = '#0071e3';
                else if (item.seriesName === '独立访客IP') color = '#5856d6';
                else if (item.seriesName === '总访问量') color = '#34c759';
                else if (item.seriesName === '伪装浏览器(无JS)') color = '#ff9500';
                else if (item.seriesName === '爬虫/扫描') color = '#ff3b30';
                else color = '#8e8e93';
                
                result += `<div style="display: flex; align-items: center; justify-content: space-between; margin: 4px 0; font-size: 12px;">
                  <span style="color: ${tooltipLabelColor};">${item.seriesName}:</span>
                  <span style="color: ${color}; font-weight: 600; margin-left: 12px;">${item.value}</span>
                </div>`;
              });
              
              return result;
            }
          },
          legend: {
            show: false
          },
          xAxis: {
            type: 'category',
            data: dates,
            boundaryGap: false,
            axisLine: {
              show: false
            },
            axisTick: {
              show: false
            },
            axisLabel: {
              color: textColor,
              fontSize: 10,
              margin: 12,
              formatter: function (value) {
                return value.substring(5); // 只显示月-日
              }
            },
            splitLine: {
              show: false
            }
          },
          yAxis: {
            type: 'value',
            minInterval: 1,
            axisLine: {
              show: false
            },
            axisTick: {
              show: false
            },
            axisLabel: {
              color: textColor,
              fontSize: 10,
              margin: 12
            },
            splitLine: {
              lineStyle: {
                color: splitLineColor,
                type: 'dashed'
              }
            }
          },
          series: [
            {
              name: '真实访客(JS已验证)',
              type: 'line',
              data: jsVerifiedVisits,
              showSymbol: false,
              symbol: 'circle',
              symbolSize: 6,
              lineStyle: {
                width: 2.5,
                color: '#0071e3'
              },
              itemStyle: {
                color: '#0071e3'
              },
              areaStyle: {
                color: {
                  type: 'linear',
                  x: 0,
                  y: 0,
                  x2: 0,
                  y2: 1,
                  colorStops: [
                    {offset: 0, color: 'rgba(0, 113, 227, 0.2)'},
                    {offset: 1, color: 'rgba(0, 113, 227, 0)'}
                  ]
                }
              },
              smooth: false,
              z: 5
            },
            {
              name: '独立访客IP',
              type: 'line',
              data: uniqueVisits,
              showSymbol: false,
              symbol: 'circle',
              symbolSize: 6,
              lineStyle: {
                width: 1.5,
                type: 'dotted',
                color: '#5856d6'
              },
              itemStyle: {
                color: '#5856d6'
              },
              smooth: false,
              z: 4
            },
            {
              name: '伪装浏览器(无JS)',
              type: 'line',
              data: browserNoJsVisits,
              showSymbol: false,
              symbol: 'circle',
              symbolSize: 6,
              lineStyle: {
                width: 1.5,
                type: 'dashed',
                color: '#ff9500'
              },
              itemStyle: {
                color: '#ff9500'
              },
              smooth: false,
              z: 3
            },
            {
              name: '爬虫/扫描',
              type: 'line',
              data: botVisits,
              showSymbol: false,
              symbol: 'circle',
              symbolSize: 6,
              lineStyle: {
                width: 1.5,
                type: 'dashed',
                color: '#ff3b30'
              },
              itemStyle: {
                color: '#ff3b30'
              },
              smooth: false,
              z: 3
            },
            {
              name: '总访问量',
              type: 'bar',
              data: totalVisits,
              barWidth: '40%',
              itemStyle: {
                color: '#34c759',
                opacity: 0.25,
                borderRadius: [2, 2, 0, 0]
              },
              z: 1
            },
            {
              name: '平均独立访客',
              type: 'line',
              data: avgLine,
              symbol: 'none',
              lineStyle: {
                width: 1,
                type: 'dashed',
                color: '#8e8e93'
              },
              smooth: false,
              z: 2
            }
          ]
        };
  
        this.chart.setOption(option);
      },
  
      // 添加格式化日期的辅助方法
      formatDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
      },
      
      // 计算平均值的辅助方法
      calculateAverage(array) {
        const sum = array.reduce((a, b) => a + b, 0);
        return array.length > 0 ? (sum / array.length).toFixed(1) : 0;
      },
  
      // 初始化地图实例
      initMap() {
        const mapDom = document.getElementById('provinceMap');
        if (mapDom) {
          this.mapChart = echarts.init(mapDom);
        }
      },

      // 加载并注册”世界底图 + 中国省级细分”合并地图（仅加载一次）
      async ensureChinaMapRegistered() {
        if (this.chinaMapRegistered) return true;
        try {
          const response = await fetch(`${import.meta.env.BASE_URL}map/world-cn.json`);
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
          }
          const geoJson = await response.json();
          echarts.registerMap('world-cn', geoJson);
          // 中国省份 feature 带 level==='province'，用于区分国内/海外
          this.chinaProvinceNames = new Set(
            (geoJson.features || [])
              .filter(f => f.properties && f.properties.level === 'province')
              .map(f => f.properties.name)
              .filter(Boolean)
          );
          this.chinaMapRegistered = true;
          return true;
        } catch (error) {
          console.error('世界地图 GeoJSON 加载失败:', error);
          this.mapLoadFailed = true;
          return false;
        }
      },

      // 拉取省份/国家访客统计
      async fetchProvinceStats() {
        this.mapLoading = true;
        this.mapLoadFailed = false;
        try {
          const registered = await this.ensureChinaMapRegistered();
          if (!registered) return;
          const res = await this.$http.get(
            this.$constant.baseURL + `/webInfo/getProvinceVisitStats?days=${this.timeRange}`, {}, true);
          if ((res.code === 200 || res.success) && Array.isArray(res.data)) {
            this.provinceStats = res.data;
            this.updateMap();
          } else {
            this.$message.error(res.message || '获取地区访客统计失败');
          }
        } catch (error) {
          console.error('获取地区访客统计出错:', error);
        } finally {
          this.mapLoading = false;
        }
      },

      // 省份简称/城市名 映射为地图使用的省级全称
      normalizeProvinceName(name) {
        if (!name) return '';
        const n = String(name).trim();
        // 已是全称
        if (/(省|市|自治区|特别行政区)$/.test(n)) return n;
        const special = {
          '北京': '北京市', '天津': '天津市', '上海': '上海市', '重庆': '重庆市',
          '内蒙古': '内蒙古自治区', '广西': '广西壮族自治区', '西藏': '西藏自治区',
          '宁夏': '宁夏回族自治区', '新疆': '新疆维吾尔自治区',
          '香港': '香港特别行政区', '澳门': '澳门特别行政区', '台湾': '台湾省'
        };
        if (special[n]) return special[n];
        // 常见城市 → 所属省份（region 兜底为城市时使用）
        const cityToProvince = {
          '广州': '广东省', '深圳': '广东省', '杭州': '浙江省', '成都': '四川省',
          '武汉': '湖北省', '南京': '江苏省', '西安': '陕西省', '青岛': '山东省'
        };
        if (cityToProvince[n]) return cityToProvince[n];
        // 普通省份简称补“省”
        return n + '省';
      },

      // 中文国家名 → 世界地图 GeoJSON 英文名（常见访客来源国）
      countryNameToMapName(name) {
        const map = {
          '美国': 'United States', '日本': 'Japan', '韩国': 'Korea', '新加坡': 'Singapore',
          '德国': 'Germany', '英国': 'United Kingdom', '法国': 'France', '俄罗斯': 'Russia',
          '加拿大': 'Canada', '澳大利亚': 'Australia', '印度': 'India', '荷兰': 'Netherlands',
          '巴西': 'Brazil', '意大利': 'Italy', '西班牙': 'Spain', '瑞典': 'Sweden',
          '瑞士': 'Switzerland', '芬兰': 'Finland', '挪威': 'Norway', '丹麦': 'Denmark',
          '波兰': 'Poland', '土耳其': 'Turkey', '泰国': 'Thailand', '越南': 'Vietnam',
          '马来西亚': 'Malaysia', '印度尼西亚': 'Indonesia', '菲律宾': 'Philippines',
          '墨西哥': 'Mexico', '阿根廷': 'Argentina', '南非': 'South Africa',
          '埃及': 'Egypt', '沙特阿拉伯': 'Saudi Arabia', '阿联酋': 'United Arab Emirates',
          '以色列': 'Israel', '乌克兰': 'Ukraine', '爱尔兰': 'Ireland',
          '比利时': 'Belgium', '奥地利': 'Austria', '葡萄牙': 'Portugal',
          '希腊': 'Greece', '捷克': 'Czech Rep.', '匈牙利': 'Hungary',
          '罗马尼亚': 'Romania', '新西兰': 'New Zealand', '智利': 'Chile',
          '哥伦比亚': 'Colombia', '秘鲁': 'Peru', '巴基斯坦': 'Pakistan',
          '孟加拉国': 'Bangladesh', '尼日利亚': 'Nigeria', '肯尼亚': 'Kenya',
          '哈萨克斯坦': 'Kazakhstan', '蒙古': 'Mongolia', '朝鲜': 'Dem. Rep. Korea',
          '缅甸': 'Myanmar', '柬埔寨': 'Cambodia', '老挝': 'Lao PDR',
          '尼泊尔': 'Nepal', '斯里兰卡': 'Sri Lanka', '伊朗': 'Iran', '伊拉克': 'Iraq'
        };
        return map[String(name).trim()] || null;
      },

      // 渲染“世界底图 + 中国省级”访客热力地图
      updateMap() {
        if (!this.mapChart || !this.chinaMapRegistered) return;

        const valueOf = (item) => this.mapMode === 'real'
          ? (item.js_verified_visits || 0)
          : (item.num || 0);

        const data = [];
        let maxValue = 1;
        const tooltipMeta = {};
        this.provinceStats.forEach(item => {
          // 先按中国省份匹配，失败再按国家名映射到世界底图
          let mapName = this.normalizeProvinceName(item.province);
          if (!this.chinaProvinceNames || !this.chinaProvinceNames.has(mapName)) {
            mapName = this.countryNameToMapName(item.province);
          }
          if (!mapName) return;
          const value = valueOf(item);
          // 同名叠加（理论上不会发生，防止映射碰撞丢数据）
          const existing = data.find(d => d.name === mapName);
          if (existing) {
            existing.value += value;
            const meta = tooltipMeta[mapName];
            meta.num = (meta.num || 0) + (item.num || 0);
            meta.unique_visitors = (meta.unique_visitors || 0) + (item.unique_visitors || 0);
            meta.js_verified_visits = (meta.js_verified_visits || 0) + (item.js_verified_visits || 0);
          } else {
            data.push({ name: mapName, value });
            tooltipMeta[mapName] = {
              displayName: item.province,
              num: item.num || 0,
              unique_visitors: item.unique_visitors || 0,
              js_verified_visits: item.js_verified_visits || 0
            };
          }
          if (value > maxValue) maxValue = value;
        });
        data.forEach(d => { if (d.value > maxValue) maxValue = d.value; });

        const isDark = this.isDarkMode;
        const textColor = isDark ? 'rgba(255, 255, 255, 0.7)' : '#86868b';
        const tooltipBgColor = isDark ? 'rgba(30, 30, 30, 0.95)' : 'rgba(255, 255, 255, 0.95)';
        const tooltipTextColor = isDark ? '#f5f5f7' : '#1d1d1f';
        const areaColor = isDark ? 'rgba(255, 255, 255, 0.04)' : '#f0f2f5';
        const borderColor = isDark ? 'rgba(255, 255, 255, 0.15)' : '#d8dce1';

        // 小屏下收紧 visualMap 色条：默认尺寸（20×140）在 300px 高的移动端地图上遮挡过多
        const isCompact = window.innerWidth <= 768;
        this.mapCompact = isCompact;

        this.mapChart.setOption({
          backgroundColor: 'transparent',
          tooltip: {
            trigger: 'item',
            backgroundColor: tooltipBgColor,
            borderRadius: 8,
            borderWidth: 0,
            padding: [8, 12],
            textStyle: { color: tooltipTextColor, fontSize: 12 },
            formatter: (params) => {
              const meta = tooltipMeta[params.name];
              if (!meta) {
                return `<div style="font-weight:600;font-size:12px;">${params.name}</div><div style="font-size:12px;color:${textColor};">暂无访客</div>`;
              }
              const title = meta.displayName && meta.displayName !== params.name
                ? `${meta.displayName}（${params.name}）` : params.name;
              return `<div style="font-weight:600;margin-bottom:4px;font-size:12px;">${title}</div>` +
                `<div style="font-size:12px;">真实访客(JS已验证)：<b>${meta.js_verified_visits || 0}</b></div>` +
                `<div style="font-size:12px;">全部访问：<b>${meta.num || 0}</b></div>` +
                `<div style="font-size:12px;">独立访客IP：<b>${meta.unique_visitors || 0}</b></div>`;
            }
          },
          visualMap: {
            min: 0,
            max: maxValue,
            left: isCompact ? 6 : 10,
            bottom: isCompact ? 6 : 10,
            calculable: true,
            itemWidth: isCompact ? 10 : 20,
            itemHeight: isCompact ? 60 : 140,
            text: [this.mapMode === 'real' ? '真实访客' : '全部访问', ''],
            textStyle: { color: textColor, fontSize: isCompact ? 9 : 11 },
            inRange: {
              color: isDark
                ? ['rgba(0, 113, 227, 0.15)', 'rgba(0, 113, 227, 0.45)', '#0a84ff']
                : ['#e3f0ff', '#7db8f7', '#0071e3']
            }
          },
          series: [{
            name: this.mapMode === 'real' ? '真实访客' : '全部访问',
            type: 'map',
            map: 'world-cn',
            roam: true,               // 支持拖拽与滚轮缩放
            scaleLimit: { min: 0.8, max: 12 },
            zoom: 1.8,
            center: [95, 30],         // 初始视角聚焦亚欧（中国居中偏右）
            label: { show: false },
            itemStyle: {
              areaColor: areaColor,
              borderColor: borderColor,
              borderWidth: 0.6
            },
            emphasis: {
              label: { show: true, fontSize: 11, color: tooltipTextColor },
              itemStyle: { areaColor: '#ff9500' }
            },
            select: { disabled: true },
            data: data
          }]
        }, true);
      },

      fetchVisitStats() {
        this.loading = true;
        // 时间范围联动：地区统计与趋势图共用同一口径
        this.fetchProvinceStats();

        // 直接调用Java后端API，使用管理员token进行认证
        this.$http.get(this.$constant.baseURL + `/webInfo/getDailyVisitStats?days=${this.timeRange}`, {}, true)
          .then(res => {
            // Java后端返回的是PoetryResult格式，检查success字段或code字段
            if ((res.code === 200 || res.success) && res.data) {
              this.visitStats = res.data;
              this.updateChart();
            } else {
              this.$message.error(res.message || '获取访问统计数据失败');
            }
          })
          .catch(error => {
            console.error('获取访问统计数据出错:', error);
            // 提供更详细的错误信息
            let errorMessage = '获取访问统计数据出错';
            if (error.response) {
              // 服务器返回了错误响应
              if (error.response.status === 401) {
                errorMessage = '权限不足，请确认您有管理员权限';
              } else if (error.response.status === 403) {
                errorMessage = '访问被拒绝，请重新登录';
              } else if (error.response.data && error.response.data.message) {
                errorMessage = error.response.data.message;
              } else {
                errorMessage = `服务器错误 (${error.response.status})`;
              }
            } else if (error.request) {
              // 网络错误
              errorMessage = '网络连接失败，请检查网络连接';
            } else {
              // 其他错误
              errorMessage = error.message || '未知错误';
            }
            this.$message.error(errorMessage);
          })
          .finally(() => {
            this.loading = false;
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
          // 主题变化时重新渲染图表与地图
          this.updateChart();
          this.updateMap();
        };
        this.$root.$on('theme-changed', this.themeChangeListener);

        // 监听 storage 事件（跨标签页）
        this.themeListener = (e) => {
          if (e.key === 'theme') {
            this.updateTheme();
            this.updateChart();
          }
        };
        window.addEventListener('storage', this.themeListener);
      }
    }
  }
  </script>
  
  <style scoped>
  /* 整体容器 */
  .stats-container {
    padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  }
  
  /* 统计卡片区域 */
  .stat-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 16px;
    margin-bottom: 16px;
  }
  
  .stat-card {
    background: transparent;
    padding: 14px 16px;
    border-radius: 10px;
    display: flex;
    flex-direction: column;
    background-color: #f5f5f7;
    border: none;
    text-align: left;
  }
  
  .stat-value {
    font-size: 28px;
    font-weight: 600;
    color: #1d1d1f;
    margin-bottom: 4px;
  }
  
  .stat-label {
    font-size: 13px;
    color: #86868b;
    font-weight: normal;
  }
  
  /* 图表区域 */
  .chart-wrapper {
    background-color: #f5f5f7;
    border-radius: 10px;
    padding: 16px;
    position: relative;
    margin-bottom: 16px;
  }
  
  .chart-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }
  
  .chart-title {
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
    margin: 0;
  }
  
  .chart-controls {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  .time-selector {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  .time-btn {
    background: transparent;
    border: none;
    font-size: 12px;
    color: #86868b;
    cursor: pointer;
    padding: 6px 10px;
    border-radius: 6px;
    /* 性能优化: 只监听背景色变化 */
    transition: background-color 0.2s ease, transform 0.2s ease;
    transform: translateZ(0);
  }
  
  .time-btn.active {
    background-color: #0071e3;
    color: #fff;
  }
  
  .time-btn:hover:not(.active) {
    background-color: rgba(0, 0, 0, 0.05);
  }
  
  .refresh-btn {
    background: transparent;
    border: none;
    font-size: 14px;
    color: #86868b;
    cursor: pointer;
    padding: 6px;
    border-radius: 50%;
    /* 性能优化: 只监听背景色变化 */
    transition: background-color 0.2s ease, color 0.2s ease;
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .refresh-btn:hover {
    background-color: rgba(0, 0, 0, 0.05);
    color: #0071e3;
  }
  
  .chart-area {
    width: 100%;
    height: 300px;
  }


  
  /* 加载遮罩 */
  .loading-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: rgba(245, 245, 247, 0.7);
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    z-index: 10;
  }
  
  .loading-spinner {
    width: 20px;
    height: 20px;
    border: 2px solid transparent;
    border-top-color: #0071e3;
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
  
  @keyframes spin {
    to { transform: rotate(360deg); }
  }
  
  /* 地图区域 */
  .map-area {
    width: 100%;
    height: 420px;
  }
  
  .map-fallback {
    padding: 40px 0;
    text-align: center;
    font-size: 13px;
    color: #86868b;
  }
  
  /* 海外地区条形列表 */
  .overseas-section {
    margin-top: 12px;
    border-top: 1px dashed rgba(0, 0, 0, 0.08);
    padding-top: 12px;
  }
  
  .overseas-title {
    font-size: 13px;
    font-weight: 600;
    color: #1d1d1f;
    margin: 0 0 8px 0;
  }
  
  .overseas-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 6px;
    font-size: 12px;
  }
  
  .overseas-name {
    width: 90px;
    flex-shrink: 0;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  
  .overseas-bar-wrap {
    flex: 1;
    height: 8px;
    border-radius: 4px;
    background: rgba(0, 0, 0, 0.05);
    overflow: hidden;
  }
  
  .overseas-bar {
    height: 100%;
    border-radius: 4px;
    background: linear-gradient(90deg, #7db8f7, #0071e3);
    transition: width 0.4s ease;
  }
  
  .overseas-value {
    width: 48px;
    flex-shrink: 0;
    text-align: right;
    font-weight: 600;
    color: #0071e3;
  }
  
  .stats-dark-mode .overseas-section {
    border-top-color: rgba(255, 255, 255, 0.12);
  }
  
  .stats-dark-mode .overseas-title,
  .stats-dark-mode .overseas-name {
    color: rgba(255, 255, 255, 0.9) !important;
  }
  
  .stats-dark-mode .overseas-bar-wrap {
    background: rgba(255, 255, 255, 0.1);
  }
  
  .stats-dark-mode .map-fallback {
    color: rgba(255, 255, 255, 0.6) !important;
  }
  
  @media (max-width: 768px) {
    .map-area {
      height: 300px;
    }
  }
  
  /* 响应式调整 */
  @media (max-width: 768px) {
    .stat-cards {
      grid-template-columns: 1fr;
      gap: 12px;
    }
    
    .chart-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 12px;
    }
    
    .chart-area {
      height: 250px;
    }
  }
  
  @media (min-width: 769px) and (max-width: 1024px) {
    .stat-cards {
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
    }
  }
  
  /* 趋势图口径说明 */
  .chart-hint {
    font-size: 12px;
    color: #86868b;
    margin-top: 8px;
    line-height: 1.5;
  }
  
  .stats-dark-mode .chart-hint {
    color: rgba(255, 255, 255, 0.5) !important;
  }
  
  /* ========== 深色模式适配 ========== */
  .stats-dark-mode .stat-card {
    background-color: rgba(255, 255, 255, 0.05) !important;
  }
  
  .stats-dark-mode .stat-value {
    color: rgba(255, 255, 255, 0.9) !important;
  }
  
  .stats-dark-mode .stat-label {
    color: rgba(255, 255, 255, 0.6) !important;
  }
  
  .stats-dark-mode .chart-wrapper {
    background-color: rgba(255, 255, 255, 0.05) !important;
  }
  
  .stats-dark-mode .chart-title {
    color: rgba(255, 255, 255, 0.9) !important;
  }
  
  .stats-dark-mode .time-btn {
    color: rgba(255, 255, 255, 0.6) !important;
  }
  
  .stats-dark-mode .time-btn.active {
    background-color: #0071e3 !important;
    color: #fff !important;
  }
  
  .stats-dark-mode .time-btn:hover:not(.active) {
    background-color: rgba(255, 255, 255, 0.1) !important;
  }
  
  .stats-dark-mode .refresh-btn {
    color: rgba(255, 255, 255, 0.6) !important;
  }
  
  .stats-dark-mode .refresh-btn:hover {
    background-color: rgba(255, 255, 255, 0.1) !important;
    color: #0071e3 !important;
  }
  
  .stats-dark-mode .loading-overlay {
    background-color: rgba(30, 30, 30, 0.8) !important;
  }

  .stats-dark-mode .loading-spinner {
    border-top-color: #0071e3 !important;
  }


  </style>