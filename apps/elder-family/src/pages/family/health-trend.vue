<template>
  <view class="family-health-page">
    <view v-if="familyState.loading" class="state-card centered">
      <text class="state-title">正在读取亲情关系…</text>
    </view>
    <view v-else-if="familyState.errorMessage" class="state-card error-card">
      <text class="state-title">无法读取亲情关系</text>
      <text class="state-copy">{{ familyState.errorMessage }}</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <view v-else-if="!currentElder" class="state-card centered">
      <text class="state-title">没有可查看的长辈</text>
      <text class="state-copy">需要存在已生效（ACTIVE）的亲情关系，才能查看体征数据。</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <template v-else>
      <view class="elder-switch-header">
        <view class="elder-info-box">
          <text class="elder-tag">{{ currentElder.relationship || '亲属' }}</text>
          <text class="elder-name">{{ currentElder.name }}</text>
          <text v-if="currentElder.age != null" class="elder-age">{{ currentElder.age }}岁</text>
        </view>
        <picker v-if="activeElders.length > 1" :range="activeElders" range-key="name" :value="currentElderIndex" @change="handleElderChange">
          <button size="mini" class="switch-btn">切换长辈 ▼</button>
        </picker>
      </view>

      <view v-if="pageLoading" class="state-card centered">
        <text class="state-title">正在读取体征与预警…</text>
      </view>
      <view v-else class="health-content-body">
        <view v-if="activeAlert" class="alert-banner-card">
          <view class="alert-header">
            <text class="alert-badge">{{ activeAlert.alertLevel || '健康预警' }} · {{ activeAlert.metricName || '体征异常' }}</text>
            <text class="alert-time">{{ formatDateTime(activeAlert.createdAt) }}</text>
          </view>
          <view class="alert-detail">
            <text class="alert-msg">
              当前值：<text class="highlight-val">{{ activeAlert.actualValue ?? '未提供' }}</text>
              <text v-if="activeAlert.thresholdValue != null">，阈值：{{ activeAlert.thresholdValue }}</text>
            </text>
            <text class="alert-status">处理状态：{{ activeAlert.status || '未提供' }}<text v-if="activeAlert.handlerName">，处理人：{{ activeAlert.handlerName }}</text></text>
          </view>
        </view>
        <view v-else-if="alertError" class="state-card error-card">
          <text class="state-title">预警信息暂不可用</text>
          <text class="state-copy">{{ alertError }}</text>
        </view>
        <view v-else class="state-card">
          <text class="state-title">当前没有待处理预警</text>
        </view>

        <view v-if="trendError" class="state-card error-card">
          <text class="state-title">体征走势暂不可用</text>
          <text class="state-copy">{{ trendError }}</text>
          <button class="state-action" @tap="loadPage">重新加载</button>
        </view>
        <template v-else>
          <view class="summary-card">
            <view class="summary-item">
              <text class="lbl">最近一次血压</text>
              <text v-if="latestReadingText" class="val">
                {{ latestReadingText }}<text v-if="latestPoint" class="unit">mmHg</text>
              </text>
              <text v-else class="val">暂无数据</text>
              <text v-if="latestPoint || latestValueText" class="status-pill" :class="latestStatusClass">{{ latestStatusLabel }}</text>
              <text v-if="latestTimestamp" class="summary-time">{{ formatDateTime(latestTimestamp) }}</text>
            </view>
          </view>

          <view class="chart-card">
            <view class="chart-header">
              <view class="chart-title-col">
                <text class="chart-title">血压走势（近7次测量）</text>
                <text class="chart-sub">数据来自服务端体征记录</text>
              </view>
            </view>
            <view v-if="displayTrendPoints.length === 0" class="empty-copy">暂无血压测量记录</view>
            <view v-else class="trend-bars-wrapper">
              <view class="chart-guide-lines">
                <view class="guide-item red-guide"><text class="guide-text">收缩压</text></view>
                <view class="guide-item blue-guide"><text class="guide-text">舒张压</text></view>
              </view>
              <view class="bars-container">
                <view v-for="(item, idx) in displayTrendPoints" :key="item.id || idx" class="bar-col">
                  <view class="bar-pair">
                    <view class="bar-pill systolic" :class="{ 'alert-bar': item.isAbnormal }" :style="{ height: Math.min(100, Math.max(30, (item.valuePrimary - 70) * 1.1)) + 'px' }">
                      <text class="val-txt">{{ item.valuePrimary }}</text>
                    </view>
                    <view class="bar-pill diastolic" :style="{ height: Math.min(75, Math.max(20, item.valueSecondary - 40)) + 'px' }">
                      <text class="val-txt">{{ item.valueSecondary }}</text>
                    </view>
                  </view>
                  <text class="date-txt" :class="{ 'alert-txt': item.isAbnormal }">{{ formatChartDate(item.recordedAt) }}</text>
                </view>
              </view>
            </view>
          </view>

          <view class="history-card">
            <text class="history-title">体征采集记录</text>
            <view v-if="recordList.length === 0" class="empty-copy">暂无服务端体征记录</view>
            <view v-else class="history-list">
              <view v-for="item in recordList" :key="item.id" class="history-item">
                <view class="item-left">
                  <text class="item-time">{{ formatDateTime(item.recordedAt) }}</text>
                  <view class="item-source-row">
                    <text class="source-tag" :class="item.sourceType === 'SIMULATED' ? 'src-iot' : item.sourceType === 'MANUAL' ? 'src-manual' : 'src-unknown'">{{ sourceLabel(item.sourceType) }}</text>
                  </view>
                </view>
                <view class="item-right">
                  <text class="val-txt" :class="{ 'val-highlight': item.isAbnormal }">{{ item.valuePrimary }} / {{ item.valueSecondary }} <text class="unit-txt">mmHg</text></text>
                  <text class="status-txt" :class="item.isAbnormal ? 'st-warn' : 'st-ok'">{{ item.isAbnormal ? '异常' : '正常' }}</text>
                </view>
              </view>
            </view>
          </view>
        </template>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'
import { elderContext } from '../../state/elderContext'

interface VitalPoint {
  id: number | string
  recordedAt: string
  valuePrimary: number
  valueSecondary: number
  sourceType: string
  isAbnormal: boolean
}

interface HealthAlert {
  alertId: number
  elderId: number
  alertLevel?: string
  metricName?: string
  status?: string
  actualValue?: number | string
  thresholdValue?: number | string
  handlerName?: string
  createdAt?: string
}

const familyState = elderContext.state
const activeElders = elderContext.activeElders
const currentElder = elderContext.selectedElder
const currentElderIndex = computed(() => activeElders.value.findIndex(item => item.elderId === currentElder.value?.elderId))
const pageLoading = ref(false)
const trendError = ref('')
const alertError = ref('')
const recordList = ref<VitalPoint[]>([])
const trendStatus = ref('')
const latestValueText = ref('')
const latestRecordedAt = ref('')
const activeAlert = ref<HealthAlert | null>(null)
let loadSequence = 0

const latestPoint = computed(() => recordList.value[0] ?? null)
const latestReadingText = computed(() => latestPoint.value
  ? `${latestPoint.value.valuePrimary} / ${latestPoint.value.valueSecondary}`
  : latestValueText.value)
const latestTimestamp = computed(() => latestRecordedAt.value || latestPoint.value?.recordedAt || '')
const latestStatusLabel = computed(() => ({ NORMAL: '正常', HIGH: '偏高', LOW: '偏低' }[trendStatus.value] || trendStatus.value || '状态未知'))
const latestStatusClass = computed(() => trendStatus.value === 'HIGH' ? 'status-warn' : trendStatus.value === 'LOW' ? 'status-warn' : trendStatus.value === 'NORMAL' ? 'status-ok' : '')
const displayTrendPoints = computed(() => [...recordList.value].slice(0, 7).reverse())

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ') : '时间未提供'
}

function formatChartDate(value?: string) {
  if (!value) return '—'
  const normalized = value.replace('T', ' ')
  return normalized.length >= 16 ? normalized.slice(5, 16) : normalized
}

function sourceLabel(sourceType: string) {
  if (sourceType === 'SIMULATED') return '设备上报'
  if (sourceType === 'MANUAL') return '社区管理员录入'
  return sourceType || '来源未知'
}

function clearHealthData() {
  recordList.value = []
  trendStatus.value = ''
  latestValueText.value = ''
  latestRecordedAt.value = ''
  activeAlert.value = null
  trendError.value = ''
  alertError.value = ''
}

async function loadHealthData(elderId: number) {
  const sequence = ++loadSequence
  clearHealthData()
  pageLoading.value = true
  const [trendResult, alertResult] = await Promise.allSettled([
    elderApi.queryVitalTrend(elderId, 'BLOOD_PRESSURE', 7),
    elderApi.listHealthAlerts(elderId, 'PENDING'),
  ])
  if (sequence !== loadSequence || currentElder.value?.elderId !== elderId) return

  if (trendResult.status === 'fulfilled') {
    const payload = trendResult.value.data as { dataPoints?: unknown; latestStatus?: string; latestValueText?: string; latestRecordedAt?: string } | null
    if (!payload || !Array.isArray(payload.dataPoints)) {
      trendError.value = '体征走势接口返回格式异常。'
    } else {
      trendStatus.value = payload.latestStatus || ''
      latestValueText.value = payload.latestValueText || ''
      latestRecordedAt.value = payload.latestRecordedAt || ''
      recordList.value = payload.dataPoints.flatMap((point: any) => {
        const valuePrimary = Number(point?.valuePrimary)
        const valueSecondary = Number(point?.valueSecondary)
        if (!Number.isFinite(valuePrimary) || !Number.isFinite(valueSecondary)) return []
        return [{
          id: point.vitalId ?? `${point.recordedAt || 'record'}-${valuePrimary}-${valueSecondary}`,
          recordedAt: point.recordedAt || '',
          valuePrimary,
          valueSecondary,
          sourceType: point.sourceType || '',
          isAbnormal: point.isAbnormal === true,
        }]
      }).sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))
    }
  } else {
    trendError.value = (trendResult.reason as Error)?.message || '體征走势加载失败，请重试。'
  }

  if (alertResult.status === 'fulfilled') {
    const payload = alertResult.value.data as { items?: unknown } | null
    if (!payload || !Array.isArray(payload.items)) {
      alertError.value = '健康预警接口返回格式异常。'
    } else {
      activeAlert.value = (payload.items as HealthAlert[]).find(item => item.elderId === elderId && item.status === 'PENDING') || null
    }
  } else {
    alertError.value = (alertResult.reason as Error)?.message || '健康预警加载失败，请重试。'
  }
  pageLoading.value = false
}

async function loadPage() {
  const loaded = await elderContext.refresh()
  clearHealthData()
  if (!loaded || !currentElder.value) {
    pageLoading.value = false
    return
  }
  await loadHealthData(currentElder.value.elderId)
}

function handleElderChange(event: any) {
  const elder = activeElders.value[Number(event.detail.value)]
  if (!elder || !elderContext.select(elder.elderId)) return
  void loadHealthData(elder.elderId)
}

onShow(() => { void loadPage() })
</script>
<style scoped lang="scss">
.family-health-page {
  min-height: 100vh;
  background-color: #F8FAFC;
  padding: 24rpx;
  box-sizing: border-box;
}

/* 顶部选择横幅 */
.elder-switch-header {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 20rpx;

  .elder-info-box {
    display: flex;
    align-items: center;
    gap: 12rpx;

    .elder-tag {
      background-color: #EFF6FF;
      color: #2563EB;
      font-size: 22rpx;
      font-weight: bold;
      padding: 4rpx 12rpx;
      border-radius: 8rpx;
    }

    .elder-name {
      font-size: 34rpx;
      font-weight: bold;
      color: #0F172A;
    }

    .elder-age {
      font-size: 26rpx;
      color: #64748B;
    }
  }

  .switch-btn {
    background-color: #F1F5F9;
    color: #334155;
    font-size: 24rpx;
    font-weight: 500;
  }
}

/* 争议冻结拦截 */
.frozen-intercept-card {
  background-color: #FFFBEB;
  border: 2rpx solid #FDE68A;
  border-radius: 20rpx;
  padding: 40rpx 32rpx;
  text-align: center;
  margin-top: 40rpx;

  .frozen-icon {
    font-size: 64rpx;
  }

  .frozen-title {
    display: block;
    font-size: 34rpx;
    font-weight: bold;
    color: #92400E;
    margin: 16rpx 0;
  }

  .frozen-desc {
    display: block;
    font-size: 28rpx;
    color: #78350F;
    line-height: 1.6;
    margin-bottom: 28rpx;
    text-align: left;
  }

  .frozen-meta {
    font-size: 22rpx;
    color: #B45309;
    display: flex;
    flex-direction: column;
    gap: 8rpx;
  }
}

/* 预警横幅 */
.alert-banner-card {
  background: linear-gradient(135deg, #FEF2F2, #FFF5F5);
  border-left: 8rpx solid #DC2626;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;

  .alert-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12rpx;

    .alert-badge {
      background-color: #DC2626;
      color: #FFFFFF;
      font-size: 24rpx;
      font-weight: bold;
      padding: 4rpx 14rpx;
      border-radius: 8rpx;
    }

    .alert-time {
      font-size: 22rpx;
      color: #991B1B;
    }
  }

  .alert-detail {
    .alert-msg {
      display: block;
      font-size: 28rpx;
      color: #1F2937;
      margin-bottom: 8rpx;

      .highlight-val {
        color: #DC2626;
        font-weight: bold;
      }
    }

    .alert-status {
      display: block;
      font-size: 24rpx;
      color: #4B5563;
    }
  }
}

/* 指标卡片 */
.summary-card {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;

  .summary-item {
    flex: 1;
    background-color: #FFFFFF;
    border-radius: 16rpx;
    padding: 20rpx 16rpx;
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.02);

    .lbl {
      display: block;
      font-size: 22rpx;
      color: #64748B;
      margin-bottom: 8rpx;
    }

    .val {
      display: block;
      font-size: 32rpx;
      font-weight: bold;
      color: #0F172A;
      margin-bottom: 8rpx;

      .unit {
        font-size: 18rpx;
        color: #64748B;
        font-weight: normal;
      }

      &.val-red {
        color: #DC2626;
      }
    }

    .status-pill {
      font-size: 20rpx;
      padding: 2rpx 10rpx;
      border-radius: 6rpx;
      font-weight: 500;

      &.status-warn {
        background-color: #FFEDD5;
        color: #C2410C;
      }

      &.status-ok {
        background-color: #DCFCE7;
        color: #15803D;
      }
    }
  }
}

/* 图表容器 */
.chart-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;

  .chart-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16rpx;

    .chart-title {
      display: block;
      font-size: 30rpx;
      font-weight: bold;
      color: #0F172A;
    }

    .chart-sub {
      display: block;
      font-size: 22rpx;
      color: #64748B;
      margin-top: 4rpx;
    }

    .add-metric-btn {
      background-color: #0284C7;
      color: #FFFFFF;
      font-size: 24rpx;
      border-radius: 12rpx;
    }
  }

  .trend-bars-wrapper {
    width: 100%;
    padding: 16rpx 0;

    .chart-guide-lines {
      display: flex;
      justify-content: flex-end;
      gap: 20rpx;
      margin-bottom: 16rpx;

      .guide-item {
        display: flex;
        align-items: center;
        gap: 8rpx;

        .guide-dash {
          width: 24rpx;
          height: 4rpx;
          border-radius: 2rpx;
        }

        .guide-text {
          font-size: 20rpx;
          color: #64748B;
        }

        &.red-guide .guide-dash {
          background-color: #EF4444;
        }

        &.blue-guide .guide-dash {
          background-color: #3B82F6;
        }
      }
    }

    .bars-container {
      display: flex;
      justify-content: space-around;
      align-items: flex-end;
      height: 240rpx;
      border-bottom: 2rpx solid #E2E8F0;
      padding-bottom: 8rpx;

      .bar-col {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8rpx;

        .bar-pair {
          display: flex;
          align-items: flex-end;
          gap: 6rpx;

          .bar-pill {
            width: 24rpx;
            border-radius: 6rpx 6rpx 0 0;
            display: flex;
            justify-content: center;
            align-items: flex-start;
            padding-top: 4rpx;

            .val-txt {
              font-size: 16rpx;
              color: #FFFFFF;
              font-weight: bold;
              transform: scale(0.85);
            }

            &.systolic {
              background: linear-gradient(180deg, #F87171, #EF4444);
            }

            &.diastolic {
              background: linear-gradient(180deg, #60A5FA, #3B82F6);
            }

            &.alert-bar {
              background: linear-gradient(180deg, #DC2626, #991B1B);
              box-shadow: 0 0 8rpx rgba(239, 68, 68, 0.5);
            }
          }
        }

        .date-txt {
          font-size: 20rpx;
          color: #94A3B8;

          &.alert-txt {
            color: #DC2626;
            font-weight: bold;
          }
        }
      }
    }
  }
}

/* 历史流水列表 */
.history-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;

  .history-title {
    display: block;
    font-size: 30rpx;
    font-weight: bold;
    color: #0F172A;
    margin-bottom: 16rpx;
  }

  .history-list {
    display: flex;
    flex-direction: column;
    gap: 16rpx;

    .history-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16rpx 0;
      border-bottom: 1rpx solid #F1F5F9;

      &:last-child {
        border-bottom: none;
      }

      .item-left {
        .item-time {
          display: block;
          font-size: 26rpx;
          font-weight: 500;
          color: #1E293B;
          margin-bottom: 6rpx;
        }

        .item-source-row {
          display: flex;
          align-items: center;
          gap: 10rpx;

          .source-tag {
            font-size: 20rpx;
            padding: 2rpx 8rpx;
            border-radius: 4rpx;

            &.src-iot {
              background-color: #EFF6FF;
              color: #1D4ED8;
            }

            &.src-manual {
              background-color: #F3F4F6;
              color: #4B5563;
            }
          }

          .record-remark {
            font-size: 22rpx;
            color: #64748B;
          }
        }
      }

      .item-right {
        text-align: right;

        .val-txt {
          display: block;
          font-size: 30rpx;
          font-weight: bold;
          color: #0F172A;

          .unit-txt {
            font-size: 20rpx;
            font-weight: normal;
            color: #64748B;
          }

          &.val-highlight {
            color: #DC2626;
          }
        }

        .status-txt {
          font-size: 22rpx;
          font-weight: 500;

          &.st-warn {
            color: #DC2626;
          }

          &.st-ok {
            color: #16A34A;
          }
        }
      }
    }
  }
}

/* 手工录入弹窗 */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
  padding: 32rpx;

  .modal-box {
    background-color: #FFFFFF;
    border-radius: 24rpx;
    padding: 36rpx;
    width: 100%;
    max-width: 600rpx;
    box-sizing: border-box;

    .modal-box-title {
      display: block;
      font-size: 34rpx;
      font-weight: bold;
      color: #0F172A;
      margin-bottom: 28rpx;
      text-align: center;
    }

    .form-row {
      margin-bottom: 20rpx;

      .form-lbl {
        display: block;
        font-size: 26rpx;
        color: #475569;
        margin-bottom: 8rpx;
      }

      .form-input {
        background-color: #F8FAFC;
        border: 2rpx solid #E2E8F0;
        border-radius: 12rpx;
        padding: 16rpx 20rpx;
        font-size: 28rpx;
        color: #0F172A;
      }
    }

    .modal-actions {
      display: flex;
      gap: 16rpx;
      margin-top: 32rpx;

      .btn-cancel {
        flex: 1;
        background-color: #F1F5F9;
        color: #475569;
        font-size: 28rpx;
        border-radius: 12rpx;
      }

      .btn-submit {
        flex: 1;
        background-color: #0284C7;
        color: #FFFFFF;
        font-size: 28rpx;
        border-radius: 12rpx;
      }
    }
  }
}

/* 底部 Mock 切换条 */
.mock-footer-bar {
  margin-top: 30rpx;
  padding: 16rpx 20rpx;
  background-color: #E2E8F0;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;

  .mock-tip {
    font-size: 22rpx;
    color: #475569;
    font-weight: bold;
  }
}
.state-card { background: #fff; border-radius: 18rpx; padding: 30rpx; margin: 20rpx 0; }
.centered { text-align: center; }
.error-card { border: 1rpx solid #fecaca; }
.state-title { display: block; color: #1c1917; font-size: 30rpx; font-weight: 700; }
.state-copy, .empty-copy { display: block; margin-top: 12rpx; color: #64748b; font-size: 25rpx; line-height: 1.5; }
.state-action { margin-top: 16rpx; background: #0284c7; color: #fff; font-size: 25rpx; }
.summary-time { display: block; margin-top: 8rpx; color: #64748b; font-size: 22rpx; }
.src-unknown { color: #475569; background: #e2e8f0; }
</style>
