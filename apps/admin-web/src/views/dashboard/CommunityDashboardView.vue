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
        <el-tag type="info" effect="plain">趋势范围：近 7 天</el-tag>
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

    <div v-if="summaryStatus === 'loading'" class="request-status" role="status">正在加载汇总数据…</div>
    <div v-else-if="summaryStatus === 'empty'" class="request-status">暂无汇总数据</div>
    <div v-else-if="summaryStatus === 'error'" class="request-status request-error" role="alert">
      汇总数据加载失败：{{ summaryError }}
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
            <div class="kpi-value">{{ formatCount(summaryData?.totalElders) }} <span class="unit">人</span></div>
            <div class="kpi-sub">
              <span>80岁以上高龄长者: <strong>{{ formatCount(summaryData?.highAgeAloneCount) }}</strong> 人</span>
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
            <div class="kpi-value text-danger">{{ formatCount(summaryData?.pendingAlertsCount) }} <span class="unit">起</span></div>
            <div class="kpi-sub">
              <span>服务端待处理预警统计</span>
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
            <div class="kpi-value text-success">{{ formatPercent(summaryData?.workOrderFulfillmentRate) }}</div>
            <div class="kpi-sub">
              <span>当月已结案: <strong>{{ formatCount(summaryData?.monthlyClosedOrders) }}</strong> 单</span>
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
            <div class="kpi-value text-warning">{{ formatPercent(summaryData?.medicationComplianceRate) }}</div>
            <div class="kpi-sub">
              <span>今日已确认打卡: <strong>{{ formatCount(summaryData?.todayConfirmedCheckins) }}</strong> 次</span>
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
        <div class="chart-visual">
          <div ref="diseaseChartRef" class="chart-box"></div>
          <div v-if="chartStateMessage(hasDiseaseData, '慢病画像')" class="chart-state" role="status">
            {{ chartStateMessage(hasDiseaseData, '慢病画像') }}
          </div>
        </div>
      </el-card>

      <el-card class="chart-card" shadow="hover">
        <template #header>
          <div class="card-head-flex">
            <span class="card-head-title">🧹 助老服务需求结构占比</span>
            <el-tag size="small" type="success" effect="plain">当月工单分布</el-tag>
          </div>
        </template>
        <div class="chart-visual">
          <div ref="servicePieChartRef" class="chart-box"></div>
          <div v-if="chartStateMessage(hasServiceData, '服务需求')" class="chart-state" role="status">
            {{ chartStateMessage(hasServiceData, '服务需求') }}
          </div>
        </div>
      </el-card>
    </div>

    <!-- 3. 图表区域 Row 2: 近 7 天工单响应与办结趋势 + 实时预警态势流水 -->
    <div class="charts-row-main">
      <el-card class="chart-card flex-2" shadow="hover">
        <template #header>
          <div class="card-head-flex">
            <div>
              <span class="card-head-title">📈 近 7 天工单与首次接单响应</span>
            </div>
            <el-select v-model="responseCategory" aria-label="响应统计工单类别" style="width: 130px">
              <el-option label="全部工单" value="ALL" />
              <el-option label="普通工单" value="ORDINARY" />
              <el-option label="SOS 工单" value="SOS" />
            </el-select>
          </div>
        </template>
        <div class="chart-visual">
          <div ref="workOrderTrendChartRef" class="chart-box-large"></div>
          <div v-if="chartStateMessage(hasTrendData, '工单走势')" class="chart-state" role="status">
            {{ chartStateMessage(hasTrendData, '工单走势') }}
          </div>
        </div>
        <p class="response-description">按上海时间建单日统计首次有效接单响应；仍未接单数为截至当前的有效待接单工单。该指标代表社区接单响应。</p>
        <table v-if="chartsStatus === 'success' || chartsStatus === 'empty'" class="response-table">
          <caption>首次接单响应明细（无有效样本显示“无数据”）</caption>
          <thead><tr><th>建单日期</th><th>已接单</th><th>仍未接单</th><th>平均首次接单响应</th></tr></thead>
          <tbody>
            <tr v-for="(date, index) in selectedResponseTrend?.dateList ?? []" :key="date">
              <th scope="row">{{ date }}</th>
              <td>{{ formatCount(selectedResponseTrend?.acceptedCounts?.[index]) }}</td>
              <td>{{ formatCount(selectedResponseTrend?.unacceptedCounts?.[index]) }}</td>
              <td>{{ formatResponseMinutes(selectedResponseTrend?.avgResponseMinutes?.[index]) }}</td>
            </tr>
          </tbody>
        </table>
      </el-card>

      <el-card class="chart-card flex-1" shadow="hover">
        <template #header>
          <div class="card-head-flex">
            <span class="card-head-title">🚨 实时健康预警与应急动态流</span>
            <el-tag size="small" :type="alertFeedTagType" effect="dark">{{ alertFeedStatusLabel }}</el-tag>
          </div>
        </template>
        <div v-if="alertFeedStatus === 'loading'" class="feed-state" role="status">正在加载告警流…</div>
        <div v-else-if="alertFeedStatus === 'empty'" class="feed-state">暂无预警数据</div>
        <div v-else-if="alertFeedStatus === 'error'" class="feed-state request-error" role="alert">
          告警流加载失败：{{ alertFeedError }}
        </div>
        <div v-else class="alert-feed-list">
          <div v-for="item in alertFeed" :key="item.alertId" class="feed-item" :class="item.level || ''">
            <div class="feed-time-row">
              <span class="feed-time">{{ item.time || '时间未提供' }}</span>
              <el-tag :type="getAlertTagType(item.tagType)" size="small">{{ item.tagText || '预警' }}</el-tag>
            </div>
            <div class="feed-desc">
              <strong>{{ item.elderName || '未提供姓名' }}</strong> ({{ item.address || '地址未提供' }})：{{ item.description || '暂无说明' }}
            </div>
            <div class="feed-footer">
              <span class="feed-handler">响应网格员：{{ item.handler || '暂无处理人' }}</span>
              <span class="feed-status" :class="item.status || ''">{{ item.statusText || item.status || '状态未知' }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
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
import {
  adminApi,
  type DashboardChartsVO,
  type DashboardSummaryVO,
  type RealtimeAlertFeedVO,
} from '../../api/adminApi'

type DashboardRequestState = 'loading' | 'success' | 'empty' | 'error'
type DashboardTagType = 'success' | 'info' | 'warning' | 'danger'

const selectedCommunity = ref('BEIANMEN')
const isFullscreen = ref(false)

const summaryData = ref<DashboardSummaryVO | null>(null)
const chartsData = ref<DashboardChartsVO | null>(null)
const responseCategory = ref<'ALL' | 'ORDINARY' | 'SOS'>('ALL')
const selectedResponseTrend = computed(() => responseCategory.value === 'ALL'
  ? chartsData.value?.workOrderTrend
  : chartsData.value?.workOrderTrendByCategory?.[responseCategory.value])
const alertFeed = ref<RealtimeAlertFeedVO[]>([])
const summaryStatus = ref<DashboardRequestState>('loading')
const chartsStatus = ref<DashboardRequestState>('loading')
const alertFeedStatus = ref<DashboardRequestState>('loading')
const summaryError = ref('')
const chartsError = ref('')
const alertFeedError = ref('')

// DOM 引用
const diseaseChartRef = ref<HTMLDivElement | null>(null)
const servicePieChartRef = ref<HTMLDivElement | null>(null)
const workOrderTrendChartRef = ref<HTMLDivElement | null>(null)

// ECharts 实例引用
let diseaseChart: echarts.ECharts | null = null
let servicePieChart: echarts.ECharts | null = null
let workOrderTrendChart: echarts.ECharts | null = null
let dashboardRequestSequence = 0

function hasPositiveCount(values: Array<number | null> | null | undefined): boolean {
  return values?.some(value => value != null && value > 0) ?? false
}

const hasDiseaseData = computed(() => hasPositiveCount(chartsData.value?.diseaseDistribution?.map(item => item.value)))
const hasServiceData = computed(() => hasPositiveCount(chartsData.value?.serviceStructure?.map(item => item.orderCount)))
const hasTrendData = computed(() => {
  const trend = selectedResponseTrend.value
  return Boolean(
    trend?.dateList?.length &&
    (hasPositiveCount(trend.dispatchedCounts) || hasPositiveCount(trend.closedCounts)),
  )
})
const alertFeedStatusLabel = computed(() => {
  if (alertFeedStatus.value === 'loading') return '加载中'
  if (alertFeedStatus.value === 'empty') return '暂无预警'
  if (alertFeedStatus.value === 'error') return '加载失败'
  return '已更新'
})
const alertFeedTagType = computed<DashboardTagType>(() => {
  if (alertFeedStatus.value === 'error') return 'danger'
  if (alertFeedStatus.value === 'loading') return 'info'
  if (alertFeedStatus.value === 'empty') return 'warning'
  return 'success'
})

function formatCount(value: number | null | undefined): string | number {
  return value ?? '无数据'
}

function formatPercent(value: number | null | undefined): string {
  return value == null ? '无数据' : `${value}%`
}

function formatResponseMinutes(value: number | null | undefined): string {
  return value == null ? '无数据' : `${value} 分钟`
}

watch(responseCategory, () => updateCharts(chartsData.value))

function getAlertTagType(value: RealtimeAlertFeedVO['tagType']): DashboardTagType {
  if (value === 'danger' || value === 'warning' || value === 'info') return value
  return 'info'
}

function chartStateMessage(hasData: boolean, label: string): string {
  if (chartsStatus.value === 'loading') return '正在加载图表数据…'
  if (chartsStatus.value === 'error') return `图表数据加载失败：${chartsError.value}`
  return hasData ? '' : `暂无${label}数据`
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

function hasSummaryData(data: DashboardSummaryVO): boolean {
  return Object.values(data).some(value => value != null)
}

function hasChartsData(data: DashboardChartsVO | null): boolean {
  const trend = data?.workOrderTrend
  return Boolean(
    hasPositiveCount(data?.diseaseDistribution?.map(item => item.value)) ||
    hasPositiveCount(data?.serviceStructure?.map(item => item.orderCount)) ||
    (trend?.dateList?.length && (
      hasPositiveCount(trend.dispatchedCounts) ||
      hasPositiveCount(trend.closedCounts)
    )),
  )
}

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
          data: []
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
          data: []
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
        data: ['建单数', '完工结案数', '平均首次接单响应(分)'],
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
          data: [],
          axisPointer: { type: 'shadow' }
        }
      ],
      yAxis: [
        {
          type: 'value',
          name: '工单数量 (单)',
          min: 0,
          axisLabel: { formatter: '{value}' }
        },
        {
          type: 'value',
          name: '响应用时 (分钟)',
          min: 0,
          axisLabel: { formatter: '{value} min' }
        }
      ],
      series: [
        {
          name: '建单数',
          type: 'bar',
          barWidth: 14,
          itemStyle: { color: '#38bdf8', borderRadius: [4, 4, 0, 0] },
          data: []
        },
        {
          name: '完工结案数',
          type: 'bar',
          barWidth: 14,
          itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] },
          data: []
        },
        {
          name: '平均首次接单响应(分)',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          itemStyle: { color: '#f59e0b' },
          lineStyle: { width: 3 },
          data: []
        }
      ]
    })
  }
}

function updateCharts(data: DashboardChartsVO | null) {
  const diseaseData = (data?.diseaseDistribution ?? []).map(item => ({ value: item.value, name: item.name || '未分类' }))
  const serviceData = (data?.serviceStructure ?? []).map(item => ({ value: item.orderCount, name: item.serviceName || '服务类型未提供' }))
  const trend = responseCategory.value === 'ALL' ? data?.workOrderTrend : data?.workOrderTrendByCategory?.[responseCategory.value]

  diseaseChart?.setOption({ series: [{ data: diseaseData }] })
  servicePieChart?.setOption({ series: [{ data: serviceData }] })
  workOrderTrendChart?.setOption({
    xAxis: { data: trend?.dateList ?? [] },
    series: [
      { data: trend?.dispatchedCounts ?? [] },
      { data: trend?.closedCounts ?? [] },
      { data: trend?.avgResponseMinutes ?? [] },
    ],
  })
}

async function loadDashboardData(): Promise<boolean | null> {
  const requestSequence = ++dashboardRequestSequence
  summaryStatus.value = 'loading'
  chartsStatus.value = 'loading'
  alertFeedStatus.value = 'loading'
  summaryError.value = ''
  chartsError.value = ''
  alertFeedError.value = ''

  const [summaryResult, chartsResult, alertsResult] = await Promise.allSettled([
    adminApi.getDashboardSummary(),
    adminApi.getDashboardCharts(),
    adminApi.getDashboardRealtimeAlerts(10),
  ] as const)
  if (requestSequence !== dashboardRequestSequence) return null

  let allRequestsSucceeded = true

  if (summaryResult.status === 'fulfilled') {
    const data = summaryResult.value.data
    summaryData.value = data
    summaryStatus.value = data && hasSummaryData(data) ? 'success' : 'empty'
  } else {
    summaryData.value = null
    summaryStatus.value = 'error'
    summaryError.value = errorMessage(summaryResult.reason)
    allRequestsSucceeded = false
  }

  if (chartsResult.status === 'fulfilled') {
    const data = chartsResult.value.data
    chartsData.value = data
    updateCharts(data)
    chartsStatus.value = hasChartsData(data) ? 'success' : 'empty'
  } else {
    chartsData.value = null
    updateCharts(null)
    chartsStatus.value = 'error'
    chartsError.value = errorMessage(chartsResult.reason)
    allRequestsSucceeded = false
  }

  if (alertsResult.status === 'fulfilled' && Array.isArray(alertsResult.value.data)) {
    alertFeed.value = alertsResult.value.data
    alertFeedStatus.value = alertFeed.value.length > 0 ? 'success' : 'empty'
  } else if (alertsResult.status === 'fulfilled') {
    alertFeed.value = []
    alertFeedStatus.value = 'error'
    alertFeedError.value = '服务端响应格式异常'
    allRequestsSucceeded = false
  } else {
    alertFeed.value = []
    alertFeedStatus.value = 'error'
    alertFeedError.value = errorMessage(alertsResult.reason)
    allRequestsSucceeded = false
  }

  return allRequestsSucceeded
}

function handleResize() {
  diseaseChart?.resize()
  servicePieChart?.resize()
  workOrderTrendChart?.resize()
}

async function refreshData() {
  const succeeded = await loadDashboardData()
  if (succeeded === null) return
  handleResize()
  if (succeeded) {
    ElMessage.success('看板数据已更新')
  } else {
    ElMessage.warning('部分看板数据加载失败，请查看对应状态后重试')
  }
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

.request-status,
.feed-state {
  padding: 10px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}

.request-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
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

.response-description {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.response-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  text-align: center;
}

.response-table caption {
  padding: 8px;
  text-align: left;
  color: #475569;
}

.response-table th,
.response-table td {
  padding: 7px 4px;
  border-bottom: 1px solid #e2e8f0;
}

.chart-visual {
  position: relative;
}

.chart-state {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(255, 255, 255, 0.92);
  color: #64748b;
  font-size: 14px;
  text-align: center;
  pointer-events: none;
}

.feed-state {
  display: grid;
  min-height: 120px;
  place-items: center;
  text-align: center;
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
