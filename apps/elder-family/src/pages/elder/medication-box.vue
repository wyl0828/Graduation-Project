<template>
  <view class="elder-med-page">
    <!-- 顶部状态卡片 -->
    <view class="med-header-card">
      <view class="header-row">
        <text class="page-title">💊 今日长辈药盒</text>
        <text class="date-txt">{{ todayLabel }}</text>
      </view>
      <text class="progress-txt">今日服药进度：已服 {{ takenCount }} / 共 {{ taskList.length }} 次</text>
      <view class="progress-bar-bg">
        <view class="progress-bar-fill" :style="{ width: progressPercent + '%' }"></view>
      </view>
    </view>

    <!-- 今日药盒任务卡片列表 -->
    <view class="task-list-section">
      <text v-if="loadError" class="care-desc">{{ loadError }}</text>
      <text v-else-if="taskList.length === 0" class="care-desc">今日暂无服药任务</text>
      <view
        v-for="task in taskList"
        :key="task.id"
        class="task-card"
        :class="{ 'card-taken': task.status === 'CONFIRMED', 'card-over': task.status === 'OVER_WINDOW' }"
      >
        <!-- 时段与状态标识 -->
        <view class="card-top-row">
          <view class="time-slot-badge">
            <text class="time-icon">⏰</text>
            <text class="time-txt">{{ task.timeLabel }} ({{ task.scheduledTime }})</text>
          </view>
          <text class="status-badge" :class="task.status === 'CONFIRMED' ? 'badge-ok' : (task.status === 'OVER_WINDOW' ? 'badge-over' : 'badge-wait')">
            {{ task.status === 'CONFIRMED' ? '✓ 已按时服用' : (task.status === 'OVER_WINDOW' ? '⚠️ 超时未服' : '待服用') }}
          </text>
        </view>

        <!-- 药品通俗名称与剂量 (适老超大字体) -->
        <view class="med-info-box">
          <view class="name-row">
            <!-- 用户特别要求：通俗功效标签 -->
            <text class="category-tag">{{ task.categoryTag }}</text>
            <text class="medicine-name">{{ task.medicineName }}</text>
          </view>
          <text class="dosage-txt">服用剂量：{{ task.dosage }} · {{ task.usageMethod }}</text>
          <text class="remark-txt">请按医嘱及药品说明服用</text>
        </view>

        <!-- 一键打卡大按钮 (适老超大触控区) -->
        <view class="btn-box">
          <button
            v-if="task.status !== 'CONFIRMED'"
            class="checkin-btn"
            :class="{ 'btn-over-warning': task.status === 'OVER_WINDOW' }"
            @click="handleCheckin(task)"
          >
            <text class="btn-icon">💊</text>
            <text class="btn-text">{{ task.status === 'OVER_WINDOW' ? '补打卡 · 我已服用' : '我已服药 · 点击打卡' }}</text>
          </button>
          <view v-else class="already-taken-box">
            <text class="taken-icon">✓</text>
            <text class="taken-text">已记录服药时间：{{ task.confirmedTime }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 漏服关怀提示框 (若有超时未服) -->
    <view v-if="hasOverWindowTask" class="timeout-care-banner">
      <text class="care-icon">🔔</text>
      <view class="care-text-col">
        <text class="care-title">温馨关怀提醒</text>
        <text class="care-desc">有服药任务尚未打卡。请先核实是否已按医嘱服用，再记录实际情况。</text>
      </view>
    </view>

  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'

interface MedicationTaskItem {
  id: number
  categoryTag: string // 通俗功效标签，如 【降压药】
  medicineName: string
  dosage: string
  usageMethod: string
  scheduledTime: string
  timeLabel: string
  status: 'PENDING' | 'CONFIRMED' | 'OVER_WINDOW'
  confirmedTime?: string
}

const todayLabel = new Date().toLocaleDateString('zh-CN')
const taskList = ref<MedicationTaskItem[]>([])
const loadError = ref('')
const pendingKeys = new Map<number, string>()

const takenCount = computed(() => taskList.value.filter(t => t.status === 'CONFIRMED').length)
const progressPercent = computed(() => {
  if (taskList.value.length === 0) return 0
  return Math.round((takenCount.value / taskList.value.length) * 100)
})

const hasOverWindowTask = computed(() => taskList.value.some(t => t.status === 'OVER_WINDOW'))

const loadTodayTasks = async () => {
  try {
    const profile: any = (await elderApi.getMyProfile()).data
    const res: any = await elderApi.getTodayMedicationTasks(profile.elderId)
    if (!Array.isArray(res?.data)) throw new Error('服药任务响应无效')
    taskList.value = res.data.map((t: any) => ({
        id: t.taskId,
        categoryTag: t.categoryTag || '常规药',
        medicineName: t.medicineName,
        dosage: t.dosage,
        usageMethod: t.usageMethod || '口服',
        scheduledTime: t.scheduledTime ? t.scheduledTime.substring(0, 5) : '08:00',
        timeLabel: t.timeLabel || '常规服药',
        status: t.status === 'OVER_WINDOW_UNCONFIRMED' ? 'OVER_WINDOW' : t.status,
        confirmedTime: t.confirmedAt ? t.confirmedAt.substring(11, 16) : undefined
    }))
    loadError.value = ''
  } catch (e: any) {
    taskList.value = []
    loadError.value = e.message || '无法加载服药任务，请检查网络后重试'
  }
}

onShow(() => { void loadTodayTasks() })

const handleCheckin = async (task: MedicationTaskItem) => {
  if (typeof uni !== 'undefined' && uni.showLoading) {
    uni.showLoading({ title: '正在提交打卡...', mask: true })
  }

  try {
    if (!pendingKeys.has(task.id)) pendingKeys.set(task.id, `med-${task.id}-${Date.now()}`)
    const response: any = await elderApi.checkinMedication({ taskId: task.id, idempotencyKey: pendingKeys.get(task.id) })
    if (response?.data?.taskId !== task.id || response.data.status !== 'CONFIRMED') {
      throw new Error('服药打卡状态未确认')
    }
    pendingKeys.delete(task.id)
    if (typeof uni !== 'undefined' && uni.hideLoading) {
      uni.hideLoading()
    }
    task.status = 'CONFIRMED'
    task.confirmedTime = response.data.confirmedAt?.substring(11, 16) || ''

    // 震动与反馈提示
    if (typeof uni !== 'undefined') {
      if (uni.vibrateShort) uni.vibrateShort({ type: 'medium' })
      if (uni.showToast) {
        uni.showToast({
          title: `已帮您记录 [${task.categoryTag}] 打卡！祝您身体安康`,
          icon: 'success',
          duration: 3000
        })
      }
    }
  } catch (e: any) {
    if (typeof uni !== 'undefined' && uni.hideLoading) {
      uni.hideLoading()
    }
    if (typeof uni !== 'undefined' && uni.showToast) {
      uni.showToast({
        title: e?.message || '网络异常，打卡记录未同步',
        icon: 'none',
        duration: 3000
      })
    }
  }
}

</script>

<style scoped lang="scss">
.elder-med-page {
  min-height: 100vh;
  background-color: #FAF8F5;
  padding: 32rpx;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

/* 顶部进度卡片 */
.med-header-card {
  background-color: #FFFFFF;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  margin-bottom: 28rpx;

  .header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12rpx;

    .page-title {
      font-size: 38rpx;
      font-weight: 800;
      color: #0F172A;
    }

    .date-txt {
      font-size: 26rpx;
      color: #64748B;
    }
  }

  .progress-txt {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
    color: #334155;
    margin-bottom: 16rpx;
  }

  .progress-bar-bg {
    height: 16rpx;
    background-color: #E2E8F0;
    border-radius: 10rpx;
    overflow: hidden;

    .progress-bar-fill {
      height: 100%;
      background: linear-gradient(90deg, #10B981, #059669);
      border-radius: 10rpx;
      transition: width 0.3s ease;
    }
  }
}

/* 药盒任务列表 */
.task-list-section {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.task-card {
  background-color: #FFFFFF;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  border: 4rpx solid #E2E8F0;

  &.card-taken {
    background-color: #F8FAFC;
    border-color: #CBD5E1;
  }

  &.card-over {
    background-color: #FFFBEB;
    border-color: #F59E0B;
  }

  .card-top-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;

    .time-slot-badge {
      display: flex;
      align-items: center;
      gap: 8rpx;

      .time-icon {
        font-size: 32rpx;
      }

      .time-txt {
        font-size: 30rpx;
        font-weight: bold;
        color: #1E293B;
      }
    }

    .status-badge {
      font-size: 24rpx;
      font-weight: bold;
      padding: 6rpx 16rpx;
      border-radius: 12rpx;

      &.badge-ok {
        background-color: #ECFDF5;
        color: #059669;
      }

      &.badge-wait {
        background-color: #EFF6FF;
        color: #2563EB;
      }

      &.badge-over {
        background-color: #FEF3C7;
        color: #D97706;
      }
    }
  }

  .med-info-box {
    margin-bottom: 28rpx;

    .name-row {
      display: flex;
      align-items: center;
      gap: 12rpx;
      margin-bottom: 12rpx;

      .category-tag {
        background-color: #EA580C;
        color: #FFFFFF;
        font-size: 24rpx;
        font-weight: bold;
        padding: 4rpx 12rpx;
        border-radius: 8rpx;
      }

      .medicine-name {
        font-size: 36rpx;
        font-weight: 800;
        color: #0F172A;
      }
    }

    .dosage-txt {
      display: block;
      font-size: 28rpx;
      font-weight: 600;
      color: #334155;
      margin-bottom: 6rpx;
    }

    .remark-txt {
      font-size: 24rpx;
      color: #64748B;
    }
  }

  .btn-box {
    .checkin-btn {
      width: 100%;
      height: 100rpx;
      background: linear-gradient(135deg, #10B981, #059669);
      color: #FFFFFF;
      border-radius: 20rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12rpx;
      box-shadow: 0 8rpx 20rpx rgba(16, 185, 129, 0.3);

      &.btn-over-warning {
        background: linear-gradient(135deg, #F59E0B, #D97706);
        box-shadow: 0 8rpx 20rpx rgba(245, 158, 11, 0.3);
      }

      .btn-icon {
        font-size: 40rpx;
      }

      .btn-text {
        font-size: 34rpx;
        font-weight: 800;
        letter-spacing: 2rpx;
      }
    }

    .already-taken-box {
      background-color: #F1F5F9;
      border-radius: 16rpx;
      padding: 20rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12rpx;

      .taken-icon {
        color: #059669;
        font-size: 32rpx;
        font-weight: bold;
      }

      .taken-text {
        font-size: 26rpx;
        color: #64748B;
        font-weight: 500;
      }
    }
  }
}

/* 漏服关怀提示条 */
.timeout-care-banner {
  margin-top: 28rpx;
  background-color: #FEF3C7;
  border: 2rpx solid #FDE68A;
  border-radius: 20rpx;
  padding: 24rpx;
  display: flex;
  align-items: flex-start;
  gap: 16rpx;

  .care-icon {
    font-size: 40rpx;
  }

  .care-text-col {
    flex: 1;

    .care-title {
      display: block;
      font-size: 28rpx;
      font-weight: bold;
      color: #92400E;
      margin-bottom: 6rpx;
    }

    .care-desc {
      font-size: 24rpx;
      color: #B45309;
      line-height: 1.5;
    }
  }
}

/* 底部 Mock 切换条 */
.mock-switch-bar {
  margin-top: 40rpx;
  padding: 20rpx;
  background-color: #E2E8F0;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;

  .mock-lbl {
    font-size: 24rpx;
    color: #475569;
    font-weight: bold;
  }
}
</style>
