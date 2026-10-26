<template>
  <view class="timeline-page">
    <text v-if="loadError" class="section-title">{{ loadError }}</text>
    <text v-else-if="!reservation" class="section-title">暂无预约记录</text>
    <!-- 顶部工单摘要卡片 -->
    <view v-if="reservation" class="order-summary-card">
      <view class="order-head">
        <text class="service-name-txt">{{ reservation.serviceTypeName || reservation.serviceType }}</text>
        <text class="order-status-badge" :class="statusBadgeClass">
          {{ currentStatusText }}
        </text>
      </view>
      <view class="order-meta-grid">
        <text class="meta-line">预约编号：{{ reservation.reservationNo }}</text>
        <text v-if="reservation.associatedWorkOrderNo" class="meta-line">派生工单：{{ reservation.associatedWorkOrderNo }}</text>
        <text class="meta-line">服务长辈：{{ reservation.elderName }} · {{ reservation.serviceAddress }}</text>
        <text class="meta-line">预约上门时间：{{ reservation.serviceDate }} {{ reservation.slotName || reservation.slotCode }}</text>
      </view>
    </view>

    <!-- 责任网格员联系卡片 (派单后展示) -->
    <view v-if="reservation?.assigneeName" class="worker-contact-card">
      <view class="worker-avatar">🧑‍🔧</view>
      <view class="worker-info-col">
        <view class="worker-name-line">
          <text class="worker-name">{{ reservation.assigneeName }}</text>
          <text class="worker-tag">服务执行人员</text>
        </view>
        <text class="worker-desc">工单状态：{{ reservation.associatedWorkOrderStatus || '待派单' }}</text>
      </view>
      <button v-if="reservation.assigneePhone" size="mini" class="call-worker-btn" @click="handleCallWorker">
        <text>📞 电话沟通</text>
      </button>
    </view>

    <!-- 竖向大字时间轴看板 -->
    <view v-if="reservation" class="timeline-section-card">
      <text class="section-title">服务履约全流程大字时间轴</text>

      <view class="timeline-container">
        <view
          v-for="(node, index) in timelineNodes"
          :key="index"
          class="timeline-node"
          :class="{ 'node-active': node.isCurrent, 'node-passed': node.isPassed }"
        >
          <!-- 左侧节点圆点与连线 -->
          <view class="node-left-col">
            <view class="node-dot">
              <text v-if="node.isPassed" class="dot-check">✓</text>
              <text v-else-if="node.isCurrent" class="dot-pulsing">●</text>
            </view>
            <view v-if="index < timelineNodes.length - 1" class="node-line"></view>
          </view>

          <!-- 右侧文字内容 -->
          <view class="node-content-col">
            <view class="node-title-row">
              <text class="node-title">{{ node.title }}</text>
              <text class="node-time">{{ node.time }}</text>
            </view>
            <text class="node-desc">{{ node.desc }}</text>

          </view>
        </view>
      </view>
    </view>

    <!-- 底部操作栏: 未接单前允许申请取消 -->
    <view v-if="reservation && ['PENDING_CONFIRMATION', 'CONFIRMED'].includes(reservation.status)" class="cancel-action-box">
      <button class="cancel-rsv-btn" @click="handleCancelReservation">
        申请取消本单预约
      </button>
      <text class="cancel-tip">已确认的预约可能需要社区审批，提交后请查看最新状态</text>
    </view>

  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'
import { elderContext } from '../../state/elderContext'

interface Reservation {
  reservationId: number
  elderId: number
  reservationNo: string
  elderName: string
  serviceType: string
  serviceTypeName?: string
  serviceDate: string
  slotCode: string
  slotName?: string
  serviceAddress: string
  status: string
  statusName?: string
  associatedWorkOrderNo?: string
  associatedWorkOrderStatus?: string
  assigneeName?: string
  assigneePhone?: string
  createdAt?: string
  rejectReason?: string
}
const reservation = ref<Reservation | null>(null)
const reservationId = ref<number | null>(null)
const routeHasReservationId = ref(false)
const loadError = ref('')

onLoad((options) => {
  const id = Number(options?.reservationId)
  if (Number.isInteger(id) && id > 0) {
    reservationId.value = id
    routeHasReservationId.value = true
  }
})

onShow(() => {
  if (!routeHasReservationId.value) reservationId.value = null
  void loadReservation()
})

async function loadReservation() {
  try {
    if (!(await elderContext.refresh())) throw new Error(elderContext.state.errorMessage)
    const selected = elderContext.selectedElder.value
    if (!selected) throw new Error('当前没有可查看的长辈')
    if (!reservationId.value) {
      const list: any = await elderApi.getMyReservations()
      const items = Array.isArray(list.data)
        ? (list.data as Reservation[]).filter(item => item.elderId === selected.elderId) : []
      const newest = [...items].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))[0]
      if (!newest) {
        reservation.value = null
        loadError.value = ''
        return
      }
      reservationId.value = newest.reservationId
    }
    const response: any = await elderApi.getReservationDetail(reservationId.value)
    if (!response.data?.reservationId) throw new Error('预约详情响应无效')
    if (response.data.elderId !== selected.elderId) throw new Error('该预约不属于当前选择的长辈')
    reservation.value = response.data
    loadError.value = ''
  } catch (error: any) {
    reservation.value = null
    loadError.value = error.message || '无法加载预约进度'
  }
}

const currentStatusText = computed(() => {
  return reservation.value?.statusName || reservation.value?.status || ''
})

const statusBadgeClass = computed(() => {
  if (reservation.value?.status === 'PENDING_CONFIRMATION') return 'badge-pending'
  if (reservation.value?.status === 'CONFIRMED') return 'badge-processing'
  return 'badge-closed'
})

const timelineNodes = computed(() => {
  const item = reservation.value
  if (!item) return []
  return [
    {
      title: '子女在线提交预约申请',
      time: item.createdAt || '',
      desc: `为 ${item.elderName} 预约 ${item.serviceTypeName || item.serviceType}。`,
      isPassed: true,
      isCurrent: false
    },
    {
      title: '社区确认预约',
      time: '',
      desc: item.status === 'REJECTED' ? (item.rejectReason || '预约未通过') : item.status === 'PENDING_CONFIRMATION' ? '等待社区处理' : `当前预约状态：${item.statusName || item.status}`,
      isPassed: item.status === 'CONFIRMED',
      isCurrent: item.status === 'PENDING_CONFIRMATION'
    },
    {
      title: '关联工单进度',
      time: '',
      desc: item.associatedWorkOrderNo
        ? `${item.associatedWorkOrderNo}：${item.associatedWorkOrderStatus || '待派单'}`
        : '社区确认后将生成工单',
      isPassed: item.associatedWorkOrderStatus === 'CLOSED',
      isCurrent: Boolean(item.associatedWorkOrderNo) && item.associatedWorkOrderStatus !== 'CLOSED'
    }
  ]
})

const handleCallWorker = () => {
  if (reservation.value?.assigneePhone) {
    uni.makePhoneCall({ phoneNumber: reservation.value.assigneePhone })
  }
}

const handleCancelReservation = () => {
  if (!reservation.value) return
  const id = reservation.value.reservationId
  uni.showModal({
    title: '确认提交取消申请？',
    content: '已确认的预约可能需要社区审核。',
    confirmColor: '#DC2626',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await elderApi.cancelReservation(id, '子女申请取消预约')
        await loadReservation()
        uni.showToast({ title: '取消请求已提交', icon: 'success' })
      } catch (error: any) {
        uni.showToast({ title: error.message || '取消请求失败', icon: 'none' })
      }
    },
  })
}
</script>

<style scoped lang="scss">
.timeline-page {
  min-height: 100vh;
  background-color: #F8FAFC;
  padding: 24rpx;
  box-sizing: border-box;
}

/* 顶部卡片 */
.order-summary-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 20rpx;

  .order-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16rpx;

    .service-name-txt {
      font-size: 34rpx;
      font-weight: bold;
      color: #0F172A;
    }

    .order-status-badge {
      font-size: 24rpx;
      font-weight: bold;
      padding: 6rpx 16rpx;
      border-radius: 12rpx;

      &.badge-pending {
        background-color: #EFF6FF;
        color: #2563EB;
      }

      &.badge-processing {
        background-color: #FEF3C7;
        color: #D97706;
      }

      &.badge-closed {
        background-color: #ECFDF5;
        color: #059669;
      }
    }
  }

  .order-meta-grid {
    display: flex;
    flex-direction: column;
    gap: 8rpx;

    .meta-line {
      font-size: 24rpx;
      color: #64748B;
    }
  }
}

/* 网格员联系卡片 */
.worker-contact-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  display: flex;
  align-items: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 20rpx;

  .worker-avatar {
    font-size: 48rpx;
    margin-right: 16rpx;
  }

  .worker-info-col {
    flex: 1;

    .worker-name-line {
      display: flex;
      align-items: center;
      gap: 12rpx;
      margin-bottom: 4rpx;

      .worker-name {
        font-size: 30rpx;
        font-weight: bold;
        color: #0F172A;
      }

      .worker-tag {
        font-size: 20rpx;
        color: #0284C7;
        background-color: #F0F9FF;
        padding: 2rpx 8rpx;
        border-radius: 6rpx;
      }
    }

    .worker-desc {
      font-size: 22rpx;
      color: #64748B;
    }
  }

  .call-worker-btn {
    background-color: #ECFDF5;
    color: #059669;
    font-size: 24rpx;
    font-weight: bold;
    border-radius: 12rpx;
    border: 1rpx solid #A7F3D0;
  }
}

/* 竖向时间轴 */
.timeline-section-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 32rpx 28rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 30rpx;

  .section-title {
    display: block;
    font-size: 30rpx;
    font-weight: bold;
    color: #0F172A;
    margin-bottom: 28rpx;
  }
}

.timeline-container {
  display: flex;
  flex-direction: column;
}

.timeline-node {
  display: flex;
  min-height: 120rpx;

  .node-left-col {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 60rpx;
    margin-right: 16rpx;

    .node-dot {
      width: 40rpx;
      height: 40rpx;
      border-radius: 50%;
      background-color: #E2E8F0;
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 2;

      .dot-check {
        color: #FFFFFF;
        font-size: 24rpx;
        font-weight: bold;
      }

      .dot-pulsing {
        color: #FFFFFF;
        font-size: 20rpx;
      }
    }

    .node-line {
      width: 4rpx;
      flex: 1;
      background-color: #E2E8F0;
      margin: 4rpx 0;
    }
  }

  &.node-passed {
    .node-dot {
      background-color: #10B981;
    }
    .node-line {
      background-color: #10B981;
    }
  }

  &.node-active {
    .node-dot {
      background-color: #0284C7;
      box-shadow: 0 0 0 8rpx rgba(2, 132, 199, 0.2);
    }
    .node-title {
      color: #0284C7;
      font-weight: bold;
    }
  }

  .node-content-col {
    flex: 1;
    padding-bottom: 32rpx;

    .node-title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8rpx;

      .node-title {
        font-size: 28rpx;
        font-weight: 600;
        color: #1E293B;
      }

      .node-time {
        font-size: 22rpx;
        color: #94A3B8;
      }
    }

    .node-desc {
      display: block;
      font-size: 24rpx;
      color: #64748B;
      line-height: 1.5;
    }

    .node-photos-row {
      margin-top: 12rpx;

      .photo-lbl {
        display: block;
        font-size: 22rpx;
        color: #475569;
        margin-bottom: 8rpx;
      }

      .photo-thumbnails {
        display: flex;
        gap: 12rpx;

        .photo-box {
          background-color: #F1F5F9;
          border: 1rpx dashed #CBD5E1;
          border-radius: 8rpx;
          padding: 8rpx 14rpx;
          font-size: 20rpx;
          color: #475569;
        }
      }
    }
  }
}

/* 取消按钮 */
.cancel-action-box {
  margin-top: 10rpx;
  text-align: center;

  .cancel-rsv-btn {
    background-color: #FEE2E2;
    color: #DC2626;
    font-size: 28rpx;
    font-weight: bold;
    border-radius: 12rpx;
  }

  .cancel-tip {
    font-size: 20rpx;
    color: #94A3B8;
    margin-top: 8rpx;
    display: block;
  }
}

/* 底部 Mock 切换条 */
.mock-switch-bar {
  margin-top: 30rpx;
  padding: 16rpx 20rpx;
  background-color: #E2E8F0;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;

  .mock-lbl {
    font-size: 22rpx;
    color: #475569;
    font-weight: bold;
  }
}
</style>
