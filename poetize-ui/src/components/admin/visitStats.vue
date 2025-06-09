<template>
  <div class="stats-container">
    <!-- 统计卡片区域 -->
    <div class="stat-cards">
      <div class="stat-card">
        <span class="stat-value">{{ totalVisits }}</span>
        <span class="stat-label">总访问量</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ uniqueVisits }}</span>
        <span class="stat-label">独立访客</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ avgDailyVisits }}</span>
        <span class="stat-label">日均访问</span>
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
      
      <!-- 加载遮罩 -->
      <div v-if="loading" class="loading-overlay">
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
      chart: null
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
    avgDailyVisits() {
      if (!this.visitStats.length) return 0;
      const avg = this.visitStats[0]?.avg_unique_visits || 0;
      return avg.toFixed(1);
    }
  },

  mounted() {
    this.initChart();
    this.fetchVisitStats();
    
    // 响应窗口大小变化
    window.addEventListener('resize', this.resizeChart);
  },

  beforeDestroy() {
    if (this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
    window.removeEventListener('resize', this.resizeChart);
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
          avg_unique_visits: 0
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
      
      // 计算平均值
      const avgVisits = this.calculateAverage(uniqueVisits);
      const avgLine = Array(dates.length).fill(avgVisits);

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
          backgroundColor: 'rgba(255, 255, 255, 0.9)',
          borderRadius: 8,
          borderWidth: 0,
          padding: [8, 12],
          textStyle: {
            color: '#1d1d1f',
            fontSize: 12
          },
          axisPointer: {
            type: 'line', // 改为直线指示器
            lineStyle: {
              color: 'rgba(0, 0, 0, 0.1)',
              width: 1
            }
          },
          formatter: function(params) {
            let date = params[0].axisValue;
            let result = `<div style="font-weight: 600; margin-bottom: 6px; font-size: 12px;">${date}</div>`;
            
            params.forEach(item => {
              let color = '';
              if (item.seriesName === '独立访客') color = '#0071e3';
              else if (item.seriesName === '总访问量') color = '#34c759';
              else color = '#ff9500';
              
              result += `<div style="display: flex; align-items: center; justify-content: space-between; margin: 4px 0; font-size: 12px;">
                <span style="color: #86868b;">${item.seriesName}:</span>
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
            color: '#86868b',
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
            color: '#86868b',
            fontSize: 10,
            margin: 12
          },
          splitLine: {
            lineStyle: {
              color: 'rgba(0, 0, 0, 0.05)',
              type: 'dashed'
            }
          }
        },
        series: [
          {
            name: '独立访客',
            type: 'line',
            data: uniqueVisits,
            showSymbol: false,
            symbol: 'circle',
            symbolSize: 6,
            lineStyle: {
              width: 2,
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
            smooth: false, // 修改为直线而非曲线
            z: 3
          },
          {
            name: '总访问量',
            type: 'bar',
            data: totalVisits,
            barWidth: '40%',
            itemStyle: {
              color: '#34c759',
              opacity: 0.3,
              borderRadius: [2, 2, 0, 0]
            },
            z: 2
          },
          {
            name: '平均独立访客',
            type: 'line',
            data: avgLine,
            symbol: 'none',
            lineStyle: {
              width: 1,
              type: 'dashed',
              color: '#ff9500'
            },
            smooth: false, // 确保平均线也是直线
            z: 1
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

    fetchVisitStats() {
      this.loading = true;
      
      this.$http.get(this.$constant.pythonBaseURL + `/webInfo/getDailyVisitStats?days=${this.timeRange}`)
        .then(res => {
          if (res.code === 200 && res.data) {
            this.visitStats = res.data;
            this.updateChart();
          } else {
            this.$message.error(res.message || '获取访问统计数据失败');
          }
        })
        .catch(error => {
          this.$message.error(`获取访问统计数据出错: ${error.message}`);
        })
        .finally(() => {
          this.loading = false;
        });
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
  grid-template-columns: repeat(3, 1fr);
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
  transition: all 0.2s;
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
  transition: all 0.2s;
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
</style> 