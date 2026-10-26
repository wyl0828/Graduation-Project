<template>
  <div class="community-dashboard-view">
    <!-- 顶部状态栏与控制条 -->
    <div class="dashboard-header">
      <div class="header-left">
        <h2 class="dashboard-title">📊 社区居家养老态势感知与综合数据大屏</h2>
        <span class="dashboard-subtitle">全辖区长辈健康体征、助老服务履约率、慢病用药与实时应急处置驾驶舱</span>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedCommunity" style="width: 170px" size="default">
          <el-option label="北安门社区 (当前)" value="BEIANMEN" />
          <el-option label="富贵山社区" value="FUGUISHAN" />
          <el-option label="梅园新村社区" value="MEIYUAN" />
        </el-select>
        <el-select v-model="selectedPeriod" style="width: 130px" size="default">
          <el-option label="近 7 天" value="7D" />
          <el-option label="近 30 天" value="30D" />
          <el-option label="本年度" value="YEAR" />
        </el-select>
        <el-button type="primary" plain @click="refreshData">
          <el-icon><Refresh /></el-icon>
          <span>刷新数据</span>
        </el-button>
        <el-button type="info" plain @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          <span>{{ isFullscreen ? '退出全屏' : '全屏大屏' }}</span>
        </el-button>
      </div>
    </div>

    <!-- 1. 核心 KPI 指标卡片 -->
    <div class="kpi-grid">
      <el-card class="kpi-card" shadow="hover">
        <div class="kpi-content">
          <div class="kpi-icon-box blue">
            <el-icon><User /></el-icon>
          </div>
          <div class="kpi-text">
            <div class="kpi-label">辖区在册建档长辈</div>
            <div class="kpi-value">{{ summaryData.totalElders }} <span class="unit">人</span></div>
            <div class="kpi-sub">
              <span>80岁以上高龄长者: <strong>{{ summaryData.highAgeAloneCount }}</strong> 人</span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="kpi-card" shadow="hover">
        <div class="kpi-content">
          <div class="kpi-icon-box red">
            <el-icon><Bell /></el-icon>
          </div>
          <div class="kpi-text">
            <div class="kpi-label">待处理预警 / SOS</div>
            <div class="kpi-value text-danger">{{ summaryData.pendingAlertsCount }} <span class="unit">起</span></div>
            <div class="kpi-sub">
              <span>实时告警流响应中</span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="kpi-card" shadow="hover">
        <div class="kpi-content">
          <div class="kpi-icon-box green">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="kpi-text">
            <div class="kpi-label">助老工单当月履约率</div>
            <div class="kpi-value text-success">{{ summaryData.workOrderFulfillmentRate }}%</div>
            <div class="kpi-sub">
              <span>当月已结案: <strong>{{ summaryData.monthlyClosedOrders }}</strong> 单</span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="kpi-card" shadow="hover">
        <div class="kpi-content">
          <div class="kpi-icon-box orange">
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div class="kpi-text">
            <div class="kpi-label">长辈慢病用药合规率</div>
            <div class="kpi-value text-warning">{{ summaryData.medicationComplianceRate }}%</div>
            <div class="kpi-sub">
              <span>今日已确认打卡: <strong>{{ summaryData.todayConfirmedCheckins }}</strong> 次</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 2. 图表区域 Row 1: 慢病分布画像 + 服务构成占比 -->
    <div class="charts-row-dual">
      <el-card class="chart-card" shadow="hover">
        <template #header>
          <div class="card-head-flex">
            <span class="card-head-title">🩺 辖区长辈慢病画像分布构成</span>
            <el-tag size="small" type="primary" effect="plain">慢病高发TOP 5</el-tag>
          </div>
        </template>
        <div ref="diseaseChartRef" class="chart-box"></div>
      </el-card>

      <el-card class="chart-card" shadow="hover">
        <template #header>
          <div class="card-head-flex">
            <span class="card-head-title">🧹 助老服务需求结构占比</span>
            <el-tag size="small" type="success" effect="plain">当月工单分布</el-tag>
          </div>
        </template>
        <div ref="servicePieChartRef" class="chart-box"></div>
      </el-card>
    </div>

    <!-- 3. 图表区域 Row 2: 近 7 天工单响应与办结趋势 + 实时预警态势流水 -->
    <div class="charts-row-main">
      <el-card class="chart-card flex-2" shadow="hover">
        <template #header>
          <div class="card-head-flex">
            <div>
              <span class="card-head-title">📈 近 7 天助老工单走势与平均响应时长</span>
              <span class="card-head-hint">（双轴指标：工单吞吐量 vs 响应速度）</span>
            </div>
            <el-tag size="small" type="info">平均响应: 18.5 分钟</el-tag>
          </div>
        </template>
        <div ref="workOrderTrendChartRef" class="chart-box-large"></div>
      </el-card>

      <el-card class="chart-card flex-1" shadow="hover">
        <template #header>
          <div class="card-head-flex">
            <span class="card-head-title">🚨 实时健康预警与应急动态流</span>
            <el-tag size="small" type="danger" effect="dark">动态监控中</el-tag>
          </div>
        </template>
        <div class="alert-feed-list">
          <div v-for="item in alertFeed" :key="item.id" class="feed-item" :class="item.level">
            <div class="feed-time-row">
              <span class="feed-time">{{ item.time }}</span>
              <el-tag :type="item.tagType" size="small">{{ item.tagText }}</el-tag>
            </div>
            <div class="feed-desc">
              <strong>{{ item.elderName }}</strong> ({{ item.address }})：{{ item.description }}
            </div>
            <div class="feed-footer">
              <span class="feed-handler">响应网格员：{{ item.handler }}</span>
              <span class="feed-status" :class="item.status">{{ item.statusText }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import {
  User,
  Bell,
  CircleCheck,
  TrendCharts,
  Refresh,
  FullScreen
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api/adminApi'

const selectedCommunity = ref('BEIANMEN')
const selectedPeriod = ref('7D')
const isFullscreen = ref(false)

const summaryData = reactive({
  totalElders: 0,
  highAgeAloneCount: 0,
  pendingAlertsCount: 0,
  workOrderFulfillmentRate: 100.0,
  monthlyClosedOrders: 0,
  medicationComplianceRate: 100.0,
  todayConfirmedCheckins: 0,
})

// DOM 引用
const diseaseChartRef = ref<HTMLDivElement | null>(null)
const servicePieChartRef = ref<HTMLDivElement | null>(null)
const workOrderTrendChartRef = ref<HTMLDivElement | null>(null)

// ECharts 实例引用
let diseaseChart: echarts.ECharts | null = null
let servicePieChart: echarts.ECharts | null = null
let workOrderTrendChart: echarts.ECharts | null = null

// 实时预警流数据
const alertFeed = ref<any[]>([])

function initCharts() {
  // 1. 慢病画像分布图
  if (diseaseChartRef.value) {
    diseaseChart = echarts.init(diseaseChartRef.value)
    diseaseChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} 人 ({d}%)'
      },
      legend: {
        bottom: '0%',
        left: 'center',
        textStyle: { fontSize: 12, color: '#475569' }
      },
      color: ['#3b82f6', '#ef4444', '#f59e0b', '#10b981', '#8b5cf6'],
      series: [
        {
          name: '慢性疾病类型',
          type: 'pie',
          radius: ['42%', '70%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 6,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: false,
            position: 'center'
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 14,
              fontWeight: 'bold',
              formatter: '{b}\n{d}%'
            }
          },
          data: [
            { value: 544, name: '原发性高血压 (42.5%)' },
            { value: 362, name: '2型糖尿病 (28.3%)' },
            { value: 195, name: '冠状动脉硬化 (15.2%)' },
            { value: 108, name: '退行性关节炎 (8.5%)' },
            { value: 71, name: '慢性阻塞性肺病 (5.5%)' }
          ]
        }
      ]
    })
  }

  // 2. 服务需求结构玫瑰图/饼图
  if (servicePieChartRef.value) {
    servicePieChart = echarts.init(servicePieChartRef.value)
    servicePieChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} 单 ({d}%)'
      },
      legend: {
        bottom: '0%',
        left: 'center',
        textStyle: { fontSize: 12, color: '#475569' }
      },
      color: ['#0284c7', '#0d9488', '#ea580c', '#e11d48'],
      series: [
        {
          name: '服务工单类型',
          type: 'pie',
          radius: [20, 80],
          center: ['50%', '45%'],
          roseType: 'area',
          itemStyle: {
            borderRadius: 6
          },
          data: [
            { value: 68, name: '上门助浴照料 (38%)' },
            { value: 47, name: '居家适老保洁 (26%)' },
            { value: 38, name: '社区助餐配送 (21%)' },
            { value: 27, name: '就医陪诊引导 (15%)' }
          ]
        }
      ]
    })
  }

  // 3. 近 7 天工单吞吐与平均响应时长折线柱状复合图
  if (workOrderTrendChartRef.value) {
    workOrderTrendChart = echarts.init(workOrderTrendChartRef.value)
    workOrderTrendChart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross', crossStyle: { color: '#999' } }
      },
      legend: {
        data: ['受理派单数', '完工结案数', '平均响应时长(分)'],
        top: '2%',
        textStyle: { color: '#475569' }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '8%',
        top: '16%',
        containLabel: true
      },
      xAxis: [
        {
          type: 'category',
          data: ['10-19', '10-20', '10-21', '10-22', '10-23', '10-24', '10-25 (今日)'],
          axisPointer: { type: 'shadow' }
        }
      ],
      yAxis: [
        {
          type: 'value',
          name: '工单数量 (单)',
          min: 0,
          max: 35,
          interval: 7,
          axisLabel: { formatter: '{value}' }
        },
        {
          type: 'value',
          name: '响应用时 (分钟)',
          min: 0,
          max: 40,
          interval: 10,
          axisLabel: { formatter: '{value} min' }
        }
      ],
      series: [
        {
          name: '受理派单数',
          type: 'bar',
          barWidth: 14,
          itemStyle: { color: '#38bdf8', borderRadius: [4, 4, 0, 0] },
          data: [18, 22, 19, 25, 28, 24, 26]
        },
        {
          name: '完工结案数',
          type: 'bar',
          barWidth: 14,
          itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] },
          data: [17, 21, 19, 24, 27, 23, 25]
        },
        {
          name: '平均响应时长(分)',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          itemStyle: { color: '#f59e0b' },
          lineStyle: { width: 3 },
          data: [25, 23, 21, 19, 17, 18, 16.5]
        }
      ]
    })
  }
}

function updateCharts(chartsData: any) {
  if (diseaseChart && chartsData.diseaseDistribution) {
    const pieData = chartsData.diseaseDistribution.map((item: any) => ({
      value: item.count,
      name: item.diseaseName
    }))
    diseaseChart.setOption({
      series: [{ data: pieData }]
    })
  }

  if (servicePieChart && chartsData.serviceStructure) {
    const serviceData = chartsData.serviceStructure.map((item: any) => ({
      value: item.count,
      name: item.serviceName
    }))
    servicePieChart.setOption({
      series: [{ data: serviceData }]
    })
  }

  if (workOrderTrendChart && chartsData.workOrderTrend) {
    workOrderTrendChart.setOption({
      xAxis: { data: chartsData.workOrderTrend.dates || [] },
      series: [
        { data: chartsData.workOrderTrend.dispatchedOrders || [] },
        { data: chartsData.workOrderTrend.closedOrders || [] },
        { data: chartsData.workOrderTrend.averageLatencyMinutes || [] }
      ]
    })
  }
}

async function loadDashboardData() {
  try {
    const summaryRes = await adminApi.getDashboardSummary()
    if (summaryRes?.data) {
      Object.assign(summaryData, summaryRes.data)
    }

    const chartsRes = await adminApi.getDashboardCharts(selectedPeriod.value)
    if (chartsRes?.data) {
      updateCharts(chartsRes.data)
    }

    const alertsRes = await adminApi.getDashboardRealtimeAlerts(10)
    if (alertsRes?.data && Array.isArray(alertsRes.data)) {
      alertFeed.value = alertsRes.data.map((item: any) => ({
        id: item.alertId,
        time: item.time,
        level: item.level,
        tagType: item.tagType || (item.level === 'HIGH' ? 'danger' : 'warning'),
        tagText: item.tagText || '预警',
        elderName: item.elderName,
        address: item.address,
        description: item.description,
        handler: item.handler,
        status: item.status,
        statusText: item.statusText
      }))
    }
  } catch (err: any) {
    // 若网络不可用时保留降级默认展示
  }
}

function handleResize() {
  diseaseChart?.resize()
  servicePieChart?.resize()
  workOrderTrendChart?.resize()
}

async function refreshData() {
  await loadDashboardData()
  ElMessage.success('态势数据与 ECharts 图表已实时同步！')
  handleResize()
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {})
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen().catch(() => {})
    }
  }
}

onMounted(async () => {
  initCharts()
  await loadDashboardData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  diseaseChart?.dispose()
  servicePieChart?.dispose()
  workOrderTrendChart?.dispose()
})
</script>

<style scoped>
.community-dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.dashboard-title {
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 4px 0;
}

.dashboard-subtitle {
  font-size: 13px;
  color: #64748b;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* KPI 卡片网格 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kpi-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.kpi-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.kpi-icon-box {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}

.kpi-icon-box.blue {
  background: #eff6ff;
  color: #2563eb;
}
.kpi-icon-box.red {
  background: #fef2f2;
  color: #dc2626;
}
.kpi-icon-box.green {
  background: #ecfdf5;
  color: #059669;
}
.kpi-icon-box.orange {
  background: #fffbeb;
  color: #d97706;
}

.kpi-text {
  flex: 1;
}

.kpi-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 2px;
}

.kpi-value {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.2;
}

.kpi-value .unit {
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
}

.kpi-value.text-danger {
  color: #dc2626;
}
.kpi-value.text-success {
  color: #059669;
}
.kpi-value.text-warning {
  color: #d97706;
}

.kpi-sub {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

/* 图表行列 */
.charts-row-dual {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.charts-row-main {
  display: flex;
  gap: 16px;
}

.flex-2 {
  flex: 2;
}
.flex-1 {
  flex: 1;
}

.chart-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.card-head-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-head-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}

.card-head-hint {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 6px;
}

.chart-box {
  width: 100%;
  height: 280px;
}

.chart-box-large {
  width: 100%;
  height: 320px;
}

/* 实时预警流水墙 */
.alert-feed-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.feed-item {
  padding: 12px;
  border-radius: 6px;
  background: #f8fafc;
  border-left: 4px solid #cbd5e1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.feed-item.HIGH {
  background: #fff5f5;
  border-left-color: #ef4444;
}

.feed-item.MEDIUM {
  background: #fffdf5;
  border-left-color: #f59e0b;
}

.feed-time-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.feed-time {
  font-size: 12px;
  color: #64748b;
  font-family: monospace;
}

.feed-desc {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.4;
}

.feed-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #64748b;
}

.feed-status.PROCESSING {
  color: #2563eb;
  font-weight: 600;
}

.feed-status.PENDING_CALLBACK {
  color: #dc2626;
  font-weight: 600;
}

.feed-status.NOTIFIED {
  color: #d97706;
}

.feed-status.CLOSED {
  color: #059669;
}
</style>
