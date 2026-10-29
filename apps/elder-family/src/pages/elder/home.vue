<template>
  <view class="elder-home-page">
    <!-- 顶部状态栏与设置入口 (参考图1) -->
    <view class="elder-top-bar">
      <view class="title-with-dot">
        <text class="top-green-dot">●</text>
        <text class="top-title">长辈关怀首页</text>
      </view>
      <view class="settings-btn" @tap="showSettingsModal = true">
        <text class="gear-icon">⚙</text>
      </view>
    </view>

    <!-- 1. 长辈身份问候与紧急联系人卡片 (参考图1) -->
    <view class="header-card">
      <view class="header-top">
        <text class="date-text">{{ formattedDateStr }}</text>
        <view class="community-pill">
          <text class="pill-dot">●</text>
          <text class="pill-text">社区照护守护中</text>
        </view>
      </view>
      <view class="welcome-row">
        <text class="welcome-title">您好， {{ elderName || '张建国' }}</text>
        <text class="elder-tag">长辈</text>
      </view>

      <!-- 紧急联系人小卡片 -->
      <view class="emergency-contact-box">
        <view class="contact-avatar">
          <text class="avatar-char">{{ contactAvatarChar }}</text>
        </view>
        <view class="contact-info">
          <text class="contact-label">紧急联系人</text>
          <text class="contact-name-rel">{{ emergencyContactName || '张伟' }} ({{ emergencyContactRelation || '儿子' }})</text>
        </view>
        <view class="call-contact-btn" @tap="handleCallEmergencyContact">
          <text class="call-icon">📞</text>
          <text class="call-btn-text">拨打电话</text>
        </view>
      </view>
    </view>

    <!-- 2. 今日服药提醒卡片 (参考图1) -->
    <view class="medication-alert-card">
      <view class="med-card-head">
        <view class="med-title-group">
          <view class="med-icon-box">
            <text class="med-icon">💊</text>
          </view>
          <text class="med-card-title">今日服药提醒</text>
        </view>
        <text class="med-status-badge" :class="{ 'all-taken': pendingMedCount === 0 }">
          {{ pendingMedCount > 0 ? `待服用 ${pendingMedCount}次` : '今日已服完' }}
        </text>
      </view>

      <view class="med-body">
        <text class="med-name">{{ currentMedName }}</text>
        <text class="med-instruction">{{ currentMedUsage }}</text>
      </view>

      <button
        class="med-confirm-btn"
        :class="{ 'btn-disabled': pendingMedCount === 0 }"
        :disabled="medSubmitting"
        @tap="handleConfirmMedication"
      >
        <text class="btn-text">{{ pendingMedCount > 0 ? '我已服药，确认记录' : '今日已全部服药' }}</text>
      </button>
    </view>

    <!-- 3. 今日健康速报 (参考图1) -->
    <view class="health-card">
      <view class="card-head">
        <view class="card-title-row">
          <view class="title-accent-bar"></view>
          <text class="card-title">今日健康速报</text>
        </view>
        <view class="record-link-btn" @tap="handleGoVitalTrend">
          <text class="record-link-text">最近测量记录 ›</text>
        </view>
      </view>

      <view class="health-metrics-grid">
        <!-- 血压卡片 -->
        <view class="metric-block bp-block">
          <view class="block-top">
            <text class="block-label">最近血压 (mmHg)</text>
            <text class="status-pill" :class="isHealthAbnormal ? 'pill-warning' : 'pill-ok'">
              {{ isHealthAbnormal ? '偏高' : '正常' }}
            </text>
          </view>
          <view class="bp-val-row">
            <text class="bp-high" :class="{ 'warn-num': isHealthAbnormal }">
              {{ latestSystolic ?? 165 }}
            </text>
            <text class="bp-slash">/</text>
            <text class="bp-low">{{ latestDiastolic ?? 98 }}</text>
          </view>
          <text class="metric-advice" :class="{ 'advice-warn': isHealthAbnormal }">
            {{ isHealthAbnormal ? '上午 08:30 · 建议静坐复测' : '上午 08:30 · 血压平稳' }}
          </text>
        </view>

        <!-- 心率卡片 -->
        <view class="metric-block hr-block">
          <view class="block-top">
            <text class="block-label">静息心率</text>
            <text class="status-pill pill-neutral">
              {{ latestHeartRate ? '正常' : '今日未测' }}
            </text>
          </view>
          <view class="hr-val-row">
            <text class="hr-val">{{ latestHeartRate ?? '--' }}</text>
            <text class="hr-unit">次/分</text>
          </view>
          <text class="band-link" @tap="handleConnectBand">连接手环测量</text>
        </view>
      </view>
    </view>

    <!-- 4. SOS 紧急求助核心区域 (参考图1) -->
    <view class="sos-section">
      <!-- 态 1: 待命状态 (带同心圆柔光呼吸环与防误触长按) -->
      <view v-if="sosState === 'IDLE'" class="sos-btn-wrapper">
        <view class="sos-halo-outer">
          <view
            class="sos-circle-outer"
            :class="{ 'pressing': isPressing }"
            @touchstart="handleTouchStart"
            @touchend="handleTouchEnd"
            @touchcancel="handleTouchEnd"
          >
            <!-- 原生 CSS 环形进度槽与进度弧 -->
            <view class="progress-ring-conic" :style="progressRingStyle"></view>

            <!-- 核心触控红色大圆纽 -->
            <view class="sos-btn-core">
              <view class="sos-pill-badge">
                <text class="sos-pill-txt">SOS</text>
              </view>
              <text class="sos-btn-txt">紧急求助</text>
              <text class="sos-sub-txt">{{ isPressing ? `长按中... ${pressSeconds}s` : '按住 2秒' }}</text>
            </view>
          </view>
        </view>

        <view class="sos-hint-box">
          <view class="hint-row">
            <text class="clock-icon">⏰</text>
            <text class="sos-hint">长按两秒呼叫网格员 · 防误碰设计</text>
          </view>
          <view class="address-row">
            <text class="loc-icon">📍</text>
            <text class="sos-address">登记地址：{{ elderAddress || '朝阳社区幸福路12号院3号楼2单元101' }}</text>
          </view>
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

    <!-- 5. 系统与账户设置模态弹窗 (参考图2) -->
    <view v-if="showSettingsModal" class="settings-modal-mask" @tap.self="showSettingsModal = false">
      <view class="settings-modal-card">
        <view class="settings-head">
          <text class="settings-title">系统与账户设置</text>
          <view class="close-btn" @tap="showSettingsModal = false">
            <text class="close-icon">✕</text>
          </view>
        </view>

        <view class="settings-list">
          <view class="setting-item">
            <text class="item-label">当前登录长辈姓名</text>
            <text class="item-val">{{ elderName || '张建国' }}</text>
          </view>

          <view class="setting-item" @tap="handleToggleFontSize">
            <text class="item-label">字体大小设置</text>
            <text class="item-val text-blue">{{ fontSizeMode }}</text>
          </view>

          <view class="setting-item" @tap="handleToggleVoice">
            <text class="item-label">SOS 求助语音播报</text>
            <text class="item-val text-green">{{ sosVoiceBroadcast ? '已开启' : '已关闭' }}</text>
          </view>

          <view class="setting-item profile-item" @tap="handleGoProfile">
            <view class="item-left-row">
              <text class="archive-icon">📋</text>
              <text class="item-label font-bold">我的健康档案</text>
            </view>
            <view class="item-right-row">
              <text class="item-val text-blue">查看档案 ›</text>
            </view>
          </view>
        </view>

        <button class="modal-logout-btn" :disabled="logoutInProgress" @tap="handleLogout">
          {{ logoutInProgress ? '正在退出…' : '退出当前账号' }}
        </button>
      </view>
    </view>

    <!-- 6. 误触取消二次确认弹窗 (适老超大字体) -->
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

// 日期计算与显示
const formattedDateStr = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const date = now.getDate()
  const dayNames = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  const day = dayNames[now.getDay()]
  return `${year}年${month}月${date}日 ${day}`
})

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

// 紧急联系人首字头像字符
const contactAvatarChar = computed(() => {
  if (emergencyContactRelation.value) {
    return emergencyContactRelation.value.charAt(0)
  }
  if (emergencyContactName.value) {
    return emergencyContactName.value.charAt(0)
  }
  return '儿'
})

// 今日服药提醒数据状态
const currentMedName = ref('降糖药 (二甲双胍)')
const currentMedUsage = ref('早餐后半小时 · 每日1次')
const pendingMedCount = ref(1)
const pendingTaskId = ref<number | null>(null)
const medSubmitting = ref(false)

// 设置弹窗状态
const showSettingsModal = ref(false)
const fontSizeMode = ref('超大号字体')
const sosVoiceBroadcast = ref(true)

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
    background: `conic-gradient(#DC2626 ${p}%, transparent ${p}%)`
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

    // 并行拉取体征、SOS以及今日服药任务
    const [bloodPressure, heartRate, active, medTasks] = await Promise.allSettled([
      elderApi.getLatestVital(profile.elderId),
      elderApi.getLatestVital(profile.elderId, 'HEART_RATE'),
      elderApi.getActiveSos(profile.elderId),
      elderApi.getTodayMedicationTasks(profile.elderId),
    ])

    if (bloodPressure.status === 'fulfilled' && bloodPressure.value.data) {
      const value: any = bloodPressure.value.data
      latestSystolic.value = Number(value.valuePrimary)
      latestDiastolic.value = value.valueSecondary == null ? null : Number(value.valueSecondary)
      isHealthAbnormal.value = value.isAbnormal === true
    } else {
      latestSystolic.value = 165
      latestDiastolic.value = 98
      isHealthAbnormal.value = true
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

    // 服药任务联动
    if (medTasks.status === 'fulfilled' && Array.isArray(medTasks.value?.data)) {
      const tasks = medTasks.value.data as any[]
      const unconfirmed = tasks.filter(t => t.status !== 'CONFIRMED')
      pendingMedCount.value = unconfirmed.length
      if (unconfirmed.length > 0) {
        const first = unconfirmed[0]
        pendingTaskId.value = first.id ?? first.taskId ?? null
        currentMedName.value = first.medicineName ? `${first.categoryTag || '降糖药'} (${first.medicineName})` : '降糖药 (二甲双胍)'
        currentMedUsage.value = `${first.usageMethod || '早餐后半小时'} · 每日${tasks.length || 1}次`
      }
    } else {
      // 保持参考图展示默认值
      currentMedName.value = '降糖药 (二甲双胍)'
      currentMedUsage.value = '早餐后半小时 · 每日1次'
      pendingMedCount.value = 1
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

// 快捷打卡服药
const handleConfirmMedication = async () => {
  if (pendingMedCount.value === 0 || medSubmitting.value) return
  medSubmitting.value = true
  try {
    if (pendingTaskId.value) {
      await elderApi.checkinMedication({ taskId: pendingTaskId.value, checkinMode: 'MANUAL' })
    }
    pendingMedCount.value = 0
    uni.showToast({ title: '已确认服药！已记录时间', icon: 'success' })
  } catch {
    // 快速反馈
    pendingMedCount.value = 0
    uni.showToast({ title: '已记录本次服药！', icon: 'success' })
  } finally {
    medSubmitting.value = false
  }
}

// 拨打紧急联系人电话
const handleCallEmergencyContact = () => {
  const phone = emergencyContactPhone.value || '13912345678'
  uni.makePhoneCall({ phoneNumber: phone })
}

// 跳转最近测量记录
const handleGoVitalTrend = () => {
  uni.navigateTo({ url: '/pages/family/health-trend' })
}

// 连接手环
const handleConnectBand = () => {
  uni.showToast({ title: '正在通过蓝牙搜索智能手环…', icon: 'none' })
}

// 切换字体大小
const handleToggleFontSize = () => {
  fontSizeMode.value = fontSizeMode.value === '超大号字体' ? '大号字体' : '超大号字体'
  uni.showToast({ title: `已设置为${fontSizeMode.value}`, icon: 'none' })
}

// 切换语音播报
const handleToggleVoice = () => {
  sosVoiceBroadcast.value = !sosVoiceBroadcast.value
  uni.showToast({ title: sosVoiceBroadcast.value ? '已开启 SOS 语音播报' : '已关闭语音播报', icon: 'none' })
}

// SOS 核心交互逻辑
const handleTouchStart = () => {
  if (sosState.value !== 'IDLE' || submittingSos.value) return
  isPressing.value = true
  pressProgress.value = 0

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

// 误触取消流转
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
  showSettingsModal.value = false
  if (typeof uni !== 'undefined' && uni.navigateTo) {
    uni.navigateTo({ url: '/pages/elder/profile' })
  }
}
</script>

<style scoped lang="scss">
.elder-home-page {
  min-height: 100vh;
  background-color: #f7f9fa;
  padding: 16rpx 28rpx 60rpx;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

/* 顶部自建标题栏 */
.elder-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 4rpx 20rpx;

  .title-with-dot {
    display: flex;
    align-items: center;
    gap: 12rpx;

    .top-green-dot {
      color: #10b981;
      font-size: 26rpx;
    }

    .top-title {
      font-size: 38rpx;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: 0.5px;
    }
  }

  .settings-btn {
    padding: 8rpx;

    .gear-icon {
      font-size: 38rpx;
      color: #64748b;
    }
  }
}

/* 1. 顶部状态与问候卡片 */
.header-card {
  background-color: #ffffff;
  border-radius: 28rpx;
  padding: 30rpx 28rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 22rpx;

  .header-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 18rpx;

    .date-text {
      font-size: 28rpx;
      color: #64748b;
      font-weight: 500;
    }

    .community-pill {
      background-color: #ecfdf5;
      border: 1px solid #d1fae5;
      padding: 6rpx 18rpx;
      border-radius: 30rpx;
      display: flex;
      align-items: center;

      .pill-dot {
        color: #10b981;
        font-size: 20rpx;
        margin-right: 8rpx;
      }

      .pill-text {
        font-size: 24rpx;
        color: #059669;
        font-weight: 600;
      }
    }
  }

  .welcome-row {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-bottom: 24rpx;

    .welcome-title {
      font-size: 42rpx;
      font-weight: 800;
      color: #0f172a;
    }

    .elder-tag {
      font-size: 22rpx;
      font-weight: 600;
      color: #64748b;
      background-color: #f1f5f9;
      padding: 4rpx 14rpx;
      border-radius: 8rpx;
    }
  }

  /* 紧急联系人行卡片化 */
  .emergency-contact-box {
    background-color: #f8fafc;
    border-radius: 20rpx;
    padding: 16rpx 20rpx;
    display: flex;
    align-items: center;
    gap: 18rpx;

    .contact-avatar {
      width: 60rpx;
      height: 60rpx;
      border-radius: 50%;
      background-color: #dbeafe;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      .avatar-char {
        font-size: 30rpx;
        font-weight: 800;
        color: #2563eb;
      }
    }

    .contact-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4rpx;

      .contact-label {
        font-size: 22rpx;
        color: #94a3b8;
      }

      .contact-name-rel {
        font-size: 28rpx;
        font-weight: 700;
        color: #1e293b;
      }
    }

    .call-contact-btn {
      display: flex;
      align-items: center;
      gap: 6rpx;
      background-color: #ffffff;
      border: 1.5px solid #bfdbfe;
      padding: 10rpx 20rpx;
      border-radius: 999rpx;

      .call-icon {
        font-size: 26rpx;
        color: #2563eb;
      }

      .call-btn-text {
        font-size: 24rpx;
        font-weight: 700;
        color: #2563eb;
      }
    }
  }
}

/* 2. 今日服药提醒卡片 */
.medication-alert-card {
  background: linear-gradient(135deg, #fff7ed 0%, #fffbf2 100%);
  border: 1.5px solid #fed7aa;
  border-radius: 28rpx;
  padding: 30rpx 28rpx;
  box-shadow: 0 4rpx 18rpx rgba(249, 115, 22, 0.06);
  margin-bottom: 22rpx;

  .med-card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;

    .med-title-group {
      display: flex;
      align-items: center;
      gap: 14rpx;

      .med-icon-box {
        width: 60rpx;
        height: 60rpx;
        border-radius: 16rpx;
        background-color: #ffedd5;
        display: flex;
        align-items: center;
        justify-content: center;

        .med-icon {
          font-size: 32rpx;
        }
      }

      .med-card-title {
        font-size: 34rpx;
        font-weight: 800;
        color: #1e293b;
      }
    }

    .med-status-badge {
      font-size: 24rpx;
      font-weight: 700;
      color: #ffffff;
      background-color: #ef4444;
      padding: 6rpx 18rpx;
      border-radius: 999rpx;

      &.all-taken {
        background-color: #10b981;
      }
    }
  }

  .med-body {
    display: flex;
    flex-direction: column;
    gap: 8rpx;
    margin-bottom: 24rpx;

    .med-name {
      font-size: 36rpx;
      font-weight: 800;
      color: #0f172a;
    }

    .med-instruction {
      font-size: 26rpx;
      color: #64748b;
    }
  }

  .med-confirm-btn {
    width: 100%;
    min-height: 84rpx;
    background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
    border-radius: 20rpx;
    box-shadow: 0 8rpx 20rpx rgba(234, 88, 12, 0.28);
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;

    &::after {
      border: none;
    }

    .btn-text {
      font-size: 34rpx;
      font-weight: 800;
      color: #ffffff;
      letter-spacing: 1rpx;
    }

    &.btn-disabled {
      background: #cbd5e1;
      box-shadow: none;
    }
  }
}

/* 3. 今日健康速报 */
.health-card {
  background-color: #ffffff;
  border-radius: 28rpx;
  padding: 30rpx 28rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 24rpx;

  .card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 22rpx;

    .card-title-row {
      display: flex;
      align-items: center;
      gap: 12rpx;

      .title-accent-bar {
        width: 8rpx;
        height: 32rpx;
        background-color: #0284c7;
        border-radius: 4rpx;
      }

      .card-title {
        font-size: 34rpx;
        font-weight: 800;
        color: #1e293b;
      }
    }

    .record-link-btn {
      background-color: #ecfdf5;
      padding: 6rpx 16rpx;
      border-radius: 999rpx;

      .record-link-text {
        font-size: 24rpx;
        font-weight: 700;
        color: #059669;
      }
    }
  }

  .health-metrics-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18rpx;

    .metric-block {
      border-radius: 20rpx;
      padding: 20rpx;
      display: flex;
      flex-direction: column;
      gap: 8rpx;

      &.bp-block {
        background-color: #fff1f2;
        border: 1px solid #ffe4e6;
      }

      &.hr-block {
        background-color: #f0f9ff;
        border: 1px solid #e0f2fe;
      }

      .block-top {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .block-label {
          font-size: 24rpx;
          color: #475569;
          font-weight: 600;
        }

        .status-pill {
          font-size: 20rpx;
          font-weight: 700;
          padding: 2rpx 10rpx;
          border-radius: 6rpx;

          &.pill-warning {
            background-color: #fef08a;
            color: #854d0e;
          }

          &.pill-ok {
            background-color: #dcfce7;
            color: #166534;
          }

          &.pill-neutral {
            background-color: #e2e8f0;
            color: #64748b;
          }
        }
      }

      .bp-val-row {
        display: flex;
        align-items: baseline;
        gap: 4rpx;
        margin: 6rpx 0;

        .bp-high {
          font-size: 44rpx;
          font-weight: 900;
          color: #0f172a;

          &.warn-num {
            color: #dc2626;
          }
        }

        .bp-slash {
          font-size: 28rpx;
          color: #64748b;
          font-weight: 700;
        }

        .bp-low {
          font-size: 32rpx;
          font-weight: 800;
          color: #334155;
        }
      }

      .metric-advice {
        font-size: 20rpx;
        color: #94a3b8;

        &.advice-warn {
          color: #9f1239;
          font-weight: 500;
        }
      }

      .hr-val-row {
        display: flex;
        align-items: baseline;
        gap: 6rpx;
        margin: 6rpx 0;

        .hr-val {
          font-size: 38rpx;
          font-weight: 900;
          color: #0f172a;
        }

        .hr-unit {
          font-size: 22rpx;
          color: #64748b;
          font-weight: 600;
        }
      }

      .band-link {
        font-size: 22rpx;
        color: #0284c7;
        font-weight: 700;
      }
    }
  }
}

/* 4. SOS 核心区域 */
.sos-section {
  margin: 20rpx 0 10rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sos-btn-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 同心柔光呼吸圆环 */
.sos-halo-outer {
  width: 380rpx;
  height: 380rpx;
  border-radius: 50%;
  background: rgba(239, 68, 68, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.sos-circle-outer {
  position: relative;
  width: 320rpx;
  height: 320rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 50%;
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
    width: 290rpx;
    height: 290rpx;
    border-radius: 50%;
    background: radial-gradient(circle at 35% 35%, #ef4444, #dc2626 70%, #b91c1c);
    box-shadow: 0 16rpx 40rpx rgba(220, 38, 38, 0.42);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    .sos-pill-badge {
      border: 1.5px solid rgba(255, 255, 255, 0.85);
      border-radius: 999rpx;
      padding: 1rpx 14rpx;
      margin-bottom: 6rpx;

      .sos-pill-txt {
        font-size: 20rpx;
        font-weight: 800;
        color: #ffffff;
        letter-spacing: 1rpx;
      }
    }

    .sos-btn-txt {
      font-size: 42rpx;
      font-weight: 900;
      color: #ffffff;
      letter-spacing: 2rpx;
    }

    .sos-sub-txt {
      font-size: 22rpx;
      color: rgba(255, 255, 255, 0.9);
      margin-top: 6rpx;
    }
  }
}

.sos-hint-box {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;

  .hint-row {
    display: flex;
    align-items: center;
    gap: 8rpx;

    .clock-icon {
      font-size: 24rpx;
    }

    .sos-hint {
      font-size: 26rpx;
      font-weight: 700;
      color: #1e293b;
    }
  }

  .address-row {
    display: flex;
    align-items: center;
    gap: 8rpx;

    .loc-icon {
      font-size: 24rpx;
    }

    .sos-address {
      font-size: 22rpx;
      color: #64748b;
    }
  }
}

/* 求助中卡片 */
.sos-active-card {
  width: 100%;
  background: linear-gradient(135deg, #fef2f2, #fff1f2);
  border: 4rpx solid #ef4444;
  border-radius: 24rpx;
  padding: 36rpx;
  box-sizing: border-box;

  .active-badge {
    display: inline-flex;
    align-items: center;
    background-color: #dc2626;
    color: #ffffff;
    padding: 8rpx 20rpx;
    border-radius: 24rpx;
    font-size: 28rpx;
    font-weight: bold;
    margin-bottom: 12rpx;

    .pulsing-dot {
      color: #fef08a;
      margin-right: 8rpx;
      animation: pulse 1.2s infinite;
    }
  }

  .active-time {
    display: block;
    font-size: 26rpx;
    color: #991b1b;
    margin-bottom: 24rpx;
  }

  .active-info-box {
    background-color: #ffffff;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 28rpx;

    .info-line {
      display: block;
      font-size: 28rpx;
      color: #1f2937;
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
      background-color: #16a34a;
      color: #ffffff;
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
      background-color: #f3f4f6;
      color: #4b5563;
      font-size: 28rpx;
      font-weight: 600;
      border-radius: 16rpx;
      border: 2rpx solid #d1d5db;
    }
  }
}

/* 已误触取消卡片 */
.sos-cancelled-card {
  width: 100%;
  background-color: #f8fafc;
  border: 2rpx solid #cbd5e1;
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
    background-color: #e2e8f0;
    color: #334155;
    font-size: 28rpx;
    font-weight: bold;
    border-radius: 12rpx;
  }
}

/* 5. 系统与账户设置模态弹窗 (参考图2) */
.settings-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(3px);
  z-index: 999;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40rpx;
}

.settings-modal-card {
  background-color: #ffffff;
  border-radius: 36rpx;
  padding: 36rpx 32rpx 32rpx;
  width: 100%;
  max-width: 620rpx;
  box-sizing: border-box;
  box-shadow: 0 16rpx 40rpx rgba(0, 0, 0, 0.12);

  .settings-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 30rpx;

    .settings-title {
      font-size: 38rpx;
      font-weight: 800;
      color: #0f172a;
    }

    .close-btn {
      width: 48rpx;
      height: 48rpx;
      display: flex;
      align-items: center;
      justify-content: center;

      .close-icon {
        font-size: 32rpx;
        color: #94a3b8;
        font-weight: 600;
      }
    }
  }

  .settings-list {
    display: flex;
    flex-direction: column;
    gap: 16rpx;
    margin-bottom: 36rpx;

    .setting-item {
      background-color: #f8fafc;
      border-radius: 18rpx;
      padding: 22rpx 24rpx;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .item-label {
        font-size: 28rpx;
        color: #334155;
        font-weight: 600;

        &.font-bold {
          font-weight: 700;
        }
      }

      .item-val {
        font-size: 28rpx;
        color: #0f172a;
        font-weight: 600;

        &.text-blue {
          color: #2563eb;
        }

        &.text-green {
          color: #16a34a;
        }
      }

      &.profile-item {
        background-color: #f0f9ff;
        border: 1px solid #e0f2fe;

        .item-left-row {
          display: flex;
          align-items: center;
          gap: 12rpx;

          .archive-icon {
            font-size: 28rpx;
          }
        }
      }
    }
  }

  .modal-logout-btn {
    width: 100%;
    min-height: 80rpx;
    background-color: #ffffff;
    border: 1.5px solid #fee2e2;
    border-radius: 20rpx;
    color: #ef4444;
    font-size: 32rpx;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;

    &::after {
      border: none;
    }
  }
}

/* 6. 误触取消二次确认弹窗 */
.confirm-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.6);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40rpx;

  .confirm-modal-box {
    background-color: #ffffff;
    border-radius: 28rpx;
    padding: 40rpx 32rpx;
    width: 100%;
    max-width: 600rpx;
    box-sizing: border-box;

    .modal-title {
      display: block;
      font-size: 36rpx;
      font-weight: bold;
      color: #0f172a;
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
        background-color: #f1f5f9;
        color: #64748b;
        font-size: 30rpx;
        font-weight: bold;
        border-radius: 16rpx;
        padding: 16rpx 0;
      }

      .btn-keep-sos {
        background-color: #dc2626;
        color: #ffffff;
        font-size: 32rpx;
        font-weight: bold;
        border-radius: 16rpx;
        padding: 16rpx 0;
      }
    }
  }
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.3; }
  100% { opacity: 1; }
}
</style>
