<template>
  <view class="elder-home-page">
    <!-- 顶部状态栏与问候 -->
    <view class="header-card">
      <view class="header-top">
        <text class="date-text">{{ currentDateStr }}</text>
        <view class="community-pill">
          <text class="pill-dot">●</text>
          <text class="pill-text">社区照护服务</text>
        </view>
      </view>
      <text class="welcome-title">您好，{{ elderName }} 长辈</text>
      <text class="guardian-tip">紧急联系人：{{ emergencyContactName || '未填写' }} {{ emergencyContactRelation }} {{ emergencyContactPhone }}</text>
    </view>

    <button class="logout-button" :disabled="logoutInProgress" @tap="handleLogout">
      {{ logoutInProgress ? '正在退出…' : '退出登录' }}
    </button>

    <!-- 健康状态速报卡片 -->
    <view class="health-card" :class="{ 'warning-border': isHealthAbnormal }">
      <view class="card-head">
        <text class="card-title">今日健康速报</text>
        <text class="health-tag" :class="isHealthAbnormal ? 'tag-warn' : 'tag-normal'">{{ latestSystolic === null ? '暂无体征数据' : isHealthAbnormal ? '血压偏高' : '最近血压记录' }}</text>
      </view>

      <view class="health-metrics">
        <view class="metric-item">
          <text class="metric-label">最近血压 (mmHg)</text>
          <view class="metric-val-row">
            <text class="metric-val" :class="{ 'val-warn': isHealthAbnormal }">{{ latestSystolic === null ? '--' : `${latestSystolic}/${latestDiastolic ?? '--'}` }}</text>
          </view>
        </view>
        <view class="metric-item">
          <text class="metric-label">静息心率</text>
          <view class="metric-val-row">
            <text class="metric-val">{{ latestHeartRate ?? '--' }}</text>
            <text class="metric-sub">次/分</text>
          </view>
        </view>
      </view>

      <view v-if="isHealthAbnormal" class="health-warn-box">
        <text class="warn-icon">⚠️</text>
        <text class="warn-desc">最近血压记录偏高，请联系社区医护人员确认。</text>
      </view>
    </view>

    <!-- SOS 紧急求助核心区域 -->
    <view class="sos-section">
      <!-- 态 1: 待命状态 (长按 2 秒防误触大圆纽) -->
      <view v-if="sosState === 'IDLE'" class="sos-btn-wrapper">
        <view
          class="sos-circle-outer"
          :class="{ 'pressing': isPressing }"
          @touchstart="handleTouchStart"
          @touchend="handleTouchEnd"
          @touchcancel="handleTouchEnd"
        >
          <!-- 原生 CSS 环形进度槽与进度弧 (兼容小程序各端，无SVG告警) -->
          <view class="progress-ring-conic" :style="progressRingStyle"></view>

          <!-- 核心触控红色大圆纽 -->
          <view class="sos-btn-core">
            <text class="sos-icon">🆘</text>
            <text class="sos-btn-txt">紧急求助</text>
            <text class="sos-sub-txt">{{ isPressing ? `长按中... ${pressSeconds}s` : '按住 2 秒' }}</text>
          </view>
        </view>

        <view class="sos-hint-box">
          <text class="sos-hint">长按两秒呼叫网格员 · 防误碰设计</text>
          <text class="sos-address">登记地址：{{ elderAddress || '未填写' }}</text>
        </view>
      </view>

      <!-- 态 2: 求助中 (无虚假承诺透明卡片) -->
      <view v-else-if="sosState === 'TRIGGERED'" class="sos-active-card">
        <view class="active-badge">
          <text class="pulsing-dot">●</text>
          <text class="badge-txt">紧急求助响应中</text>
        </view>
        <text class="active-time">求助触发时间：{{ triggeredTime }}</text>
        
        <view class="active-info-box">
          <text class="info-line">当前状态：{{ activeSosStatus || '已受理' }}</text>
          <text v-if="assigneeName" class="info-line">处理人员：{{ assigneeName }}</text>
          <text class="info-line">服务人员会根据求助信息联系您。</text>
        </view>

        <view class="active-actions">
          <button v-if="assigneePhone" class="call-btn" @click="handleCallWorker">
            <text class="btn-icon">📞</text>
            <text>直呼网格员电话</text>
          </button>
          <button class="cancel-btn" @click="openCancelConfirmModal">
            误触取消求助
          </button>
        </view>
      </view>

      <!-- 态 3: 老人主动取消 (带痕留档与电话回访待办) -->
      <view v-else-if="sosState === 'CANCELLED'" class="sos-cancelled-card">
        <view class="cancel-badge">
          <text class="check-icon">✓</text>
          <text class="cancel-badge-txt">您已取消本次紧急求助</text>
        </view>
        <text class="cancel-desc">本次取消已由服务端记录。</text>
        <button class="reset-sos-btn" @click="resetToIdle">返回待命状态</button>
      </view>
    </view>

    <!-- 底部快捷服务导航 (适老两格大卡片) -->
    <view class="quick-nav-grid">
      <view class="nav-card" @click="handleGoProfile">
        <text class="nav-icon">👤</text>
        <view class="nav-text-col">
          <text class="nav-title">我的健康档案</text>
          <text class="nav-desc">查看就医与联系资料</text>
        </view>
      </view>
      <view class="nav-card" @click="handleGoMedicationBox">
        <text class="nav-icon">💊</text>
        <view class="nav-text-col">
          <text class="nav-title">今日服药盒</text>
          <text class="nav-desc">降糖药待打卡</text>
        </view>
      </view>
    </view>

    <!-- 误触取消二次确认弹窗 (适老超大字体) -->
    <view v-if="showCancelConfirmModal" class="confirm-modal-mask">
      <view class="confirm-modal-box">
        <text class="modal-title">取消求助确认</text>
        <text class="modal-content">
          请问您是由于误触碰按下了求助按钮吗？
          点击“确认取消”后会向社区提交取消请求。
        </text>
        <view class="modal-buttons">
          <button class="btn-confirm-cancel" @click="handleConfirmCancel">
            是的，我刚刚误触了
          </button>
          <button class="btn-keep-sos" @click="showCancelConfirmModal = false">
            我确实需要帮助，继续呼叫
          </button>
        </view>
      </view>
    </view>

  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'
import { logoutCurrentSession } from '../../state/authSession'

const currentDateStr = ref(new Date().toLocaleDateString('zh-CN'))
const elderId = ref<number | null>(null)
const elderName = ref('')
const elderAddress = ref('')
const emergencyContactName = ref('')
const emergencyContactRelation = ref('')
const emergencyContactPhone = ref('')
const latestSystolic = ref<number | null>(null)
const latestDiastolic = ref<number | null>(null)
const latestHeartRate = ref<number | null>(null)
const isHealthAbnormal = ref(false)
const activeSosStatus = ref('')
const assigneeName = ref('')
const assigneePhone = ref('')
const pendingSosKey = ref<string | null>(null)

// SOS 核心交互逻辑 (长按 2 秒防误触)
const sosState = ref<'IDLE' | 'TRIGGERED' | 'CANCELLED'>('IDLE')
const isPressing = ref(false)
const pressProgress = ref(0) // 0 ~ 100
const triggeredTime = ref('')
const showCancelConfirmModal = ref(false)
const logoutInProgress = ref(false)

function handleLogout(): void {
  if (logoutInProgress.value) return
  logoutInProgress.value = true
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号并切换账号吗？',
    confirmText: '退出登录',
    cancelText: '继续使用',
    success: async (result) => {
      if (!result.confirm) {
        logoutInProgress.value = false
        return
      }

      try {
        await logoutCurrentSession()
      } catch (error) {
        const message = error instanceof Error ? error.message : '退出登录失败'
        uni.showToast({ title: `${message}，请重试`, icon: 'none', duration: 3000 })
        logoutInProgress.value = false
      }
    },
    fail: () => {
      logoutInProgress.value = false
      uni.showToast({ title: '无法打开退出确认框，请重试', icon: 'none', duration: 3000 })
    },
  })
}

// 环形进度条样式 (采用 CSS conic-gradient 兼容小程序全环境)
const progressRingStyle = computed(() => {
  const p = Math.min(100, Math.max(0, pressProgress.value))
  return {
    background: `conic-gradient(#DC2626 ${p}%, #E2E8F0 ${p}%)`
  }
})

let timer: any = null
const currentSosId = ref<number | null>(null)
const submittingSos = ref(false)
const PRESS_DURATION_MS = 2000
const pressSeconds = computed(() => ((pressProgress.value / 100) * 2).toFixed(1))

onShow(async () => {
  try {
    const profile: any = (await elderApi.getMyProfile()).data
    elderId.value = profile.elderId
    elderName.value = profile.name || ''
    elderAddress.value = profile.address || ''
    emergencyContactName.value = profile.emergencyContactName || ''
    emergencyContactRelation.value = profile.emergencyContactRelation || ''
    emergencyContactPhone.value = profile.emergencyContactPhone || ''
    const [bloodPressure, heartRate, active] = await Promise.allSettled([
      elderApi.getLatestVital(profile.elderId),
      elderApi.getLatestVital(profile.elderId, 'HEART_RATE'),
      elderApi.getActiveSos(profile.elderId),
    ])
    if (bloodPressure.status === 'fulfilled' && bloodPressure.value.data) {
      const value: any = bloodPressure.value.data
      latestSystolic.value = Number(value.valuePrimary)
      latestDiastolic.value = value.valueSecondary == null ? null : Number(value.valueSecondary)
      isHealthAbnormal.value = value.isAbnormal === true
    } else {
      latestSystolic.value = null
      latestDiastolic.value = null
      isHealthAbnormal.value = false
    }
    latestHeartRate.value = heartRate.status === 'fulfilled' && heartRate.value.data
      ? Number((heartRate.value.data as any).valuePrimary) : null
    if (active.status === 'fulfilled' && active.value.data?.sosId) {
      const sos: any = active.value.data
      currentSosId.value = sos.sosId
      sosState.value = sos.status === 'CANCELLED' ? 'CANCELLED' : 'TRIGGERED'
      triggeredTime.value = sos.occurredAt || ''
      activeSosStatus.value = sos.statusDesc || sos.status || ''
      assigneeName.value = sos.assigneeName || ''
      assigneePhone.value = sos.assigneePhone || ''
    } else if (active.status === 'fulfilled') {
      currentSosId.value = null
      sosState.value = 'IDLE'
    }
  } catch {
    elderId.value = null
    uni.showToast({ title: '无法加载本人档案，请检查登录状态', icon: 'none' })
  }
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

const handleTouchStart = () => {
  if (sosState.value !== 'IDLE' || submittingSos.value) return
  isPressing.value = true
  pressProgress.value = 0

  // 震动反馈 (Uni-app 原生震动)
  if (typeof uni !== 'undefined' && uni.vibrateShort) {
    uni.vibrateShort({ type: 'light' })
  }

  const startTime = Date.now()
  timer = setInterval(() => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(100, (elapsed / PRESS_DURATION_MS) * 100)
    pressProgress.value = progress

    if (progress >= 100) {
      clearInterval(timer)
      triggerSosSuccess()
    }
  }, 50)
}

const handleTouchEnd = () => {
  if (!isPressing.value) return
  isPressing.value = false
  if (timer) {
    clearInterval(timer)
  }
  // 未按满 2 秒，瞬间回弹清零
  if (pressProgress.value < 100) {
    pressProgress.value = 0
  }
}

const triggerSosSuccess = async () => {
  isPressing.value = false
  pressProgress.value = 0

  if (!elderId.value) {
    uni.showToast({ title: '无法确认本人档案，请重新登录', icon: 'none' })
    return
  }
  if (submittingSos.value) return
  submittingSos.value = true
  if (!pendingSosKey.value) pendingSosKey.value = `sos-${Date.now()}-${Math.random().toString(36).slice(2)}`

  if (typeof uni !== 'undefined' && uni.showLoading) {
    uni.showLoading({ title: '正在向居委会求助...', mask: true })
  }

  try {
    const res: any = await elderApi.triggerSos({
      elderId: elderId.value,
      pressDurationMs: 2200,
      locationAddress: elderAddress.value || undefined,
      idempotencyKey: pendingSosKey.value,
    })

    if (typeof uni !== 'undefined' && uni.hideLoading) {
      uni.hideLoading()
    }

    if (!res?.data?.sosId) throw new Error('服务端未返回求助编号')
    currentSosId.value = res.data.sosId
    pendingSosKey.value = null

    // 只有后端接口真正成功受理，才更新求助态
    sosState.value = 'TRIGGERED'
    triggeredTime.value = res.data.occurredAt || ''
    activeSosStatus.value = res.data.statusDesc || res.data.status || '已受理'
    assigneeName.value = res.data.assigneeName || ''
    assigneePhone.value = res.data.assigneePhone || ''

    if (typeof uni !== 'undefined' && uni.vibrateLong) {
      uni.vibrateLong({})
    }
    if (typeof uni !== 'undefined' && uni.showToast) {
      uni.showToast({
        title: '已触发紧急求助！',
        icon: 'success',
        duration: 2500
      })
    }
  } catch (e: any) {
    if (typeof uni !== 'undefined' && uni.hideLoading) {
      uni.hideLoading()
    }
    sosState.value = 'IDLE'
    if (typeof uni !== 'undefined' && uni.showModal) {
      uni.showModal({
        title: '求助状态未确认',
        content: '无法确认社区是否收到求助。请立即通过电话联系身边人员或当地急救服务，并可重试本次提交。',
        confirmText: '知道了',
        confirmColor: '#DC2626',
        showCancel: false,
      })
    }
  } finally {
    submittingSos.value = false
  }
}

// 误触取消流转 (带痕取消)
const openCancelConfirmModal = () => {
  showCancelConfirmModal.value = true
}

const handleConfirmCancel = async () => {
  showCancelConfirmModal.value = false
  if (!currentSosId.value) {
    uni.showToast({ title: '没有可取消的求助记录', icon: 'none' })
    return
  }
  let submitted = false
  try {
    await elderApi.cancelSosByElder(currentSosId.value, '老人确认误触取消')
    submitted = true
    const confirmed: any = await elderApi.getActiveSos(elderId.value || undefined)
    if (confirmed?.data?.sosId !== currentSosId.value || confirmed.data.status !== 'CANCELLED') {
      throw new Error('取消状态未确认')
    }
    sosState.value = 'CANCELLED'
    uni.showToast({ title: '求助已取消', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: submitted ? '状态未确认，请刷新' : error.message || '取消失败，请重试', icon: 'none' })
  }
}

const resetToIdle = () => {
  sosState.value = 'IDLE'
  pressProgress.value = 0
}

const handleCallWorker = () => {
  if (assigneePhone.value && typeof uni !== 'undefined' && uni.makePhoneCall) {
    uni.makePhoneCall({ phoneNumber: assigneePhone.value })
  }
}

const handleGoProfile = () => {
  if (typeof uni !== 'undefined' && uni.navigateTo) {
    uni.navigateTo({ url: '/pages/elder/profile' })
  }
}

const handleGoMedicationBox = () => {
  if (typeof uni !== 'undefined' && uni.navigateTo) {
    uni.navigateTo({ url: '/pages/elder/medication-box' })
  }
}

</script>

<style scoped lang="scss">
.elder-home-page {
  min-height: 100vh;
  background-color: #FAF8F5;
  padding: 32rpx;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.logout-button {
  width: 100%;
  max-width: 640rpx;
  min-height: 72rpx;
  margin: 0 auto 24rpx;
  border: 2rpx solid #B91C1C;
  border-radius: 16rpx;
  background: #FFFFFF;
  color: #991B1B;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.4;
}

.logout-button[disabled] {
  opacity: 0.65;
}

/* 顶部状态卡片 */
.header-card {
  background-color: #FFFFFF;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  margin-bottom: 24rpx;

  .header-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16rpx;

    .date-text {
      font-size: 28rpx;
      color: #64748B;
    }

    .community-pill {
      background-color: #F1F5F9;
      padding: 6rpx 18rpx;
      border-radius: 30rpx;
      display: flex;
      align-items: center;

      .pill-dot {
        color: #10B981;
        font-size: 20rpx;
        margin-right: 8rpx;
      }

      .pill-text {
        font-size: 24rpx;
        color: #334155;
        font-weight: 500;
      }
    }
  }

  .welcome-title {
    display: block;
    font-size: 40rpx;
    font-weight: bold;
    color: #1E293B;
    margin-bottom: 12rpx;
  }

  .guardian-tip {
    font-size: 26rpx;
    color: #64748B;
  }
}

/* 健康速报 */
.health-card {
  background-color: #FFFFFF;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  margin-bottom: 28rpx;
  border: 4rpx solid transparent;

  &.warning-border {
    border-color: #F97316;
    background-color: #FFFDF9;
  }

  .card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24rpx;

    .card-title {
      font-size: 34rpx;
      font-weight: bold;
      color: #1E293B;
    }

    .health-tag {
      font-size: 24rpx;
      padding: 6rpx 16rpx;
      border-radius: 12rpx;
      font-weight: 600;

      &.tag-normal {
        background-color: #ECFDF5;
        color: #059669;
      }

      &.tag-warn {
        background-color: #FFEDD5;
        color: #C2410C;
      }
    }
  }

  .health-metrics {
    display: flex;
    gap: 20rpx;

    .metric-item {
      flex: 1;
      background-color: #F8FAFC;
      border-radius: 16rpx;
      padding: 20rpx;

      .metric-label {
        font-size: 26rpx;
        color: #64748B;
        display: block;
        margin-bottom: 8rpx;
      }

      .metric-val-row {
        display: flex;
        align-items: baseline;
        gap: 8rpx;

        .metric-val {
          font-size: 40rpx;
          font-weight: bold;
          color: #0F172A;

          &.val-warn {
            color: #DC2626;
          }
        }

        .metric-sub {
          font-size: 22rpx;
          color: #64748B;
        }
      }
    }
  }

  .health-warn-box {
    margin-top: 20rpx;
    padding: 16rpx 20rpx;
    background-color: #FEF3C7;
    border-radius: 12rpx;
    display: flex;
    align-items: flex-start;
    gap: 12rpx;

    .warn-icon {
      font-size: 32rpx;
    }

    .warn-desc {
      font-size: 26rpx;
      color: #92400E;
      line-height: 1.4;
      flex: 1;
    }
  }
}

/* SOS 核心区域 */
.sos-section {
  margin: 36rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sos-btn-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sos-circle-outer {
  position: relative;
  width: 320rpx;
  height: 320rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  transition: transform 0.15s ease;

  &.pressing {
    transform: scale(0.96);
  }

  .progress-ring-conic {
    position: absolute;
    top: 0;
    left: 0;
    width: 320rpx;
    height: 320rpx;
    border-radius: 50%;
    z-index: 1;
    transition: background 0.05s linear;
  }

  .sos-btn-core {
    position: relative;
    z-index: 2;
    width: 270rpx;
    height: 270rpx;
    border-radius: 50%;
    background: radial-gradient(circle at 35% 35%, #EF4444, #B91C1C);
    box-shadow: 0 16rpx 36rpx rgba(220, 38, 38, 0.4);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    .sos-icon {
      font-size: 52rpx;
      margin-bottom: 4rpx;
    }

    .sos-btn-txt {
      font-size: 38rpx;
      font-weight: 800;
      color: #FFFFFF;
      letter-spacing: 2rpx;
    }

    .sos-sub-txt {
      font-size: 22rpx;
      color: rgba(255, 255, 255, 0.85);
      margin-top: 4rpx;
    }
  }
}

.sos-hint-box {
  margin-top: 24rpx;
  text-align: center;

  .sos-hint {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
    color: #475569;
    margin-bottom: 6rpx;
  }

  .sos-address {
    font-size: 24rpx;
    color: #94A3B8;
  }
}

/* 求助中卡片 */
.sos-active-card {
  width: 100%;
  background: linear-gradient(135deg, #FEF2F2, #FFF1F2);
  border: 4rpx solid #EF4444;
  border-radius: 24rpx;
  padding: 36rpx;
  box-sizing: border-box;

  .active-badge {
    display: inline-flex;
    align-items: center;
    background-color: #DC2626;
    color: #FFFFFF;
    padding: 8rpx 20rpx;
    border-radius: 24rpx;
    font-size: 28rpx;
    font-weight: bold;
    margin-bottom: 12rpx;

    .pulsing-dot {
      color: #FEF08A;
      margin-right: 8rpx;
      animation: pulse 1.2s infinite;
    }
  }

  .active-time {
    display: block;
    font-size: 26rpx;
    color: #991B1B;
    margin-bottom: 24rpx;
  }

  .active-info-box {
    background-color: #FFFFFF;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 28rpx;

    .info-line {
      display: block;
      font-size: 28rpx;
      color: #1F2937;
      line-height: 1.6;
      margin-bottom: 8rpx;

      &:last-child {
        margin-bottom: 0;
      }
    }
  }

  .active-actions {
    display: flex;
    gap: 16rpx;

    .call-btn {
      flex: 1.4;
      background-color: #16A34A;
      color: #FFFFFF;
      font-size: 30rpx;
      font-weight: bold;
      border-radius: 16rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16rpx 0;

      .btn-icon {
        margin-right: 8rpx;
      }
    }

    .cancel-btn {
      flex: 1;
      background-color: #F3F4F6;
      color: #4B5563;
      font-size: 28rpx;
      font-weight: 600;
      border-radius: 16rpx;
      border: 2rpx solid #D1D5DB;
    }
  }
}

/* 已误触取消卡片 */
.sos-cancelled-card {
  width: 100%;
  background-color: #F8FAFC;
  border: 2rpx solid #CBD5E1;
  border-radius: 24rpx;
  padding: 36rpx;
  box-sizing: border-box;

  .cancel-badge {
    display: flex;
    align-items: center;
    gap: 10rpx;
    color: #059669;
    font-size: 32rpx;
    font-weight: bold;
    margin-bottom: 16rpx;
  }

  .cancel-desc {
    display: block;
    font-size: 28rpx;
    color: #475569;
    line-height: 1.5;
    margin-bottom: 24rpx;
  }

  .reset-sos-btn {
    background-color: #E2E8F0;
    color: #334155;
    font-size: 28rpx;
    font-weight: bold;
    border-radius: 12rpx;
  }
}

/* 快捷两格入口 */
.quick-nav-grid {
  display: flex;
  gap: 20rpx;
  margin-top: 10rpx;

  .nav-card {
    flex: 1;
    background-color: #FFFFFF;
    border-radius: 20rpx;
    padding: 24rpx 20rpx;
    display: flex;
    align-items: center;
    box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);

    .nav-icon {
      font-size: 48rpx;
      margin-right: 16rpx;
    }

    .nav-text-col {
      display: flex;
      flex-direction: column;

      .nav-title {
        font-size: 30rpx;
        font-weight: bold;
        color: #0F172A;
      }

      .nav-desc {
        font-size: 22rpx;
        color: #94A3B8;
        margin-top: 4rpx;
      }
    }
  }
}

/* 误触取消二次确认弹窗 */
.confirm-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.6);
  z-index: 999;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40rpx;

  .confirm-modal-box {
    background-color: #FFFFFF;
    border-radius: 28rpx;
    padding: 40rpx 32rpx;
    width: 100%;
    max-width: 600rpx;
    box-sizing: border-box;

    .modal-title {
      display: block;
      font-size: 36rpx;
      font-weight: bold;
      color: #0F172A;
      text-align: center;
      margin-bottom: 20rpx;
    }

    .modal-content {
      display: block;
      font-size: 30rpx;
      color: #334155;
      line-height: 1.6;
      margin-bottom: 36rpx;
      text-align: center;
    }

    .modal-buttons {
      display: flex;
      flex-direction: column;
      gap: 16rpx;

      .btn-confirm-cancel {
        background-color: #F1F5F9;
        color: #64748B;
        font-size: 30rpx;
        font-weight: bold;
        border-radius: 16rpx;
        padding: 16rpx 0;
      }

      .btn-keep-sos {
        background-color: #DC2626;
        color: #FFFFFF;
        font-size: 32rpx;
        font-weight: bold;
        border-radius: 16rpx;
        padding: 16rpx 0;
      }
    }
  }
}

/* 底部 Mock 切换条 */
.mock-switch-bar {
  margin-top: 50rpx;
  padding: 20rpx;
  background-color: #E2E8F0;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;

  .mock-label {
    font-size: 24rpx;
    color: #475569;
    font-weight: bold;
  }
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.3; }
  100% { opacity: 1; }
}
</style>
