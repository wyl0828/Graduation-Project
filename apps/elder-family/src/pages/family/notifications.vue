<template>
  <view class="notif-page">
    <view v-if="familyState.loading" class="state-card centered">
      <text class="state-title">正在读取亲情关系…</text>
    </view>
    <view v-else-if="familyState.errorMessage" class="state-card error-card">
      <text class="state-title">无法读取亲情关系</text>
      <text class="state-copy">{{ familyState.errorMessage }}</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <view v-else-if="!currentElder" class="state-card centered">
      <text class="state-title">当前没有可用的长辈上下文</text>
      <text class="state-copy">需要已生效（ACTIVE）的亲情关系，才能进入家庭消息页面。</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <template v-else>
      <view class="notif-header">
        <view>
          <text class="page-title">消息与关怀提醒</text>
          <text class="context-subtitle">当前授权长辈：{{ currentElder.name }}</text>
        </view>
        <button size="mini" class="read-all-btn" :disabled="markingAll || unreadCount === 0" @click="handleMarkAllRead">
          {{ markingAll ? '正在处理…' : '全部标为已读' }}
        </button>
      </view>
      <text class="info-note">消息接口按当前子女账号返回，目前不支持按老人筛选。</text>
      <text v-if="unreadCountError" class="info-note">{{ unreadCountError }}</text>

      <view class="tab-row">
        <view v-for="tab in tabs" :key="tab.key" class="tab-item" :class="{ active: currentTab === tab.key }" @click="currentTab = tab.key">
          <text class="tab-label">{{ tab.label }}</text>
          <text v-if="tab.unreadCount > 0" class="tab-badge">{{ tab.unreadCount }}</text>
        </view>
      </view>

      <view v-if="notificationsLoading" class="state-card centered">
        <text class="state-title">正在读取服务端消息…</text>
      </view>
      <view v-else-if="notificationError" class="state-card error-card">
        <text class="state-title">消息暂不可用</text>
        <text class="state-copy">{{ notificationError }}</text>
        <button class="state-action" @tap="loadCurrentNotifications">重新加载</button>
      </view>
      <view v-else-if="filteredList.length === 0" class="state-card centered">
        <text class="state-title">暂无消息</text>
        <text class="state-copy">当前分类没有服务端返回的消息。</text>
      </view>
      <view v-else class="notif-list">
        <view v-for="item in filteredList" :key="item.id" class="notif-card" :class="{ 'unread-card': !item.readStatus }">
          <view class="card-head">
            <view class="title-with-dot">
              <text v-if="!item.readStatus" class="unread-dot">●</text>
              <text class="item-title">{{ item.title || eventTypeLabel(item.eventType) }}</text>
            </view>
            <text class="item-time">{{ formatDateTime(item.createdAt) }}</text>
          </view>
          <text class="event-type">{{ eventTypeLabel(item.eventType) }}</text>
          <text class="item-content">{{ item.content || '服务端未提供消息内容' }}</text>
          <view v-if="!item.readStatus" class="action-footer">
            <button class="mark-read-btn" :disabled="markingId === item.id" @click.stop="handleMarkRead(item)">
              {{ markingId === item.id ? '正在处理…' : '标为已读' }}
            </button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'
import { elderContext } from '../../state/elderContext'

interface NotificationItem {
  id: number
  eventType: string
  title?: string
  content?: string
  createdAt?: string
  readStatus: boolean
}

type TabKey = 'ALL' | 'MEDICATION_REMINDER' | 'HEALTH_ALERT' | 'SOS_ALERT'

const familyState = elderContext.state
const currentElder = elderContext.selectedElder
const currentTab = ref<TabKey>('ALL')
const notifList = ref<NotificationItem[]>([])
const notificationsLoading = ref(false)
const notificationError = ref('')
const unreadCount = ref<number | null>(null)
const unreadCountError = ref('')
const markingId = ref<number | null>(null)
const markingAll = ref(false)
let loadSequence = 0

const filteredList = computed(() => {
  if (currentTab.value === 'ALL') return notifList.value
  return notifList.value.filter(item => item.eventType === currentTab.value)
})

const tabs = computed(() => [
  { key: 'ALL' as const, label: '全部', unreadCount: unreadCount.value ?? countUnread('ALL') },
  { key: 'MEDICATION_REMINDER' as const, label: '服药提醒', unreadCount: countUnread('MEDICATION_REMINDER') },
  { key: 'HEALTH_ALERT' as const, label: '健康预警', unreadCount: countUnread('HEALTH_ALERT') },
  { key: 'SOS_ALERT' as const, label: '紧急求助', unreadCount: countUnread('SOS_ALERT') },
])

function countUnread(type: 'ALL' | Exclude<TabKey, 'ALL'>) {
  return notifList.value.filter(item => !item.readStatus && (type === 'ALL' || item.eventType === type)).length
}

function eventTypeLabel(eventType: string) {
  if (eventType === 'MEDICATION_REMINDER') return '服药提醒'
  if (eventType === 'HEALTH_ALERT') return '健康预警'
  if (eventType === 'SOS_ALERT') return '紧急求助'
  return eventType || '通知'
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ') : '时间未提供'
}

function showToast(title: string, icon: 'success' | 'none' = 'none') {
  if (typeof uni !== 'undefined' && uni.showToast) uni.showToast({ title, icon, duration: 3000 })
}

async function loadCurrentNotifications() {
  const sequence = ++loadSequence
  notificationsLoading.value = true
  notificationError.value = ''
  unreadCountError.value = ''
  notifList.value = []
  unreadCount.value = null
  const [listResult, countResult] = await Promise.allSettled([
    elderApi.getMyNotifications(),
    elderApi.getUnreadNotificationCount(),
  ])
  if (sequence !== loadSequence) return false

  let listLoaded = false
  if (listResult.status === 'fulfilled') {
    if (Array.isArray(listResult.value.data)) {
      notifList.value = listResult.value.data.map((item: any) => ({
        id: Number(item.deliveryId),
        eventType: item.eventType || '',
        title: item.title || '',
        content: item.content || '',
        createdAt: item.createdAt || '',
        readStatus: item.readStatus === true,
      })).filter((item: NotificationItem) => Number.isInteger(item.id) && item.id > 0)
      listLoaded = true
    } else {
      notificationError.value = '消息列表接口返回格式异常。'
    }
  } else {
    notificationError.value = (listResult.reason as Error)?.message || '消息加载失败，请重试。'
  }

  if (countResult.status === 'fulfilled') {
    const count = Number(countResult.value.data)
    if (Number.isFinite(count) && count >= 0) unreadCount.value = count
    else unreadCountError.value = '未读消息数量接口返回格式异常。'
  } else {
    unreadCountError.value = (countResult.reason as Error)?.message || '未读消息数量暂不可用。'
  }
  notificationsLoading.value = false
  return listLoaded
}

async function loadPage() {
  const loaded = await elderContext.refresh()
  notifList.value = []
  unreadCount.value = null
  if (!loaded || !currentElder.value) return
  await loadCurrentNotifications()
}

async function handleMarkRead(item: NotificationItem) {
  if (markingId.value != null || item.readStatus) return
  markingId.value = item.id
  try {
    await elderApi.markNotificationAsRead(item.id)
    const loaded = await loadCurrentNotifications()
    if (!loaded) {
      showToast('服务端已处理，但消息刷新失败')
      return
    }
    const refreshed = notifList.value.find(current => current.id === item.id)
    if (refreshed?.readStatus) showToast('已标为已读', 'success')
    else showToast('服务端已处理，刷新后仍未确认已读')
  } catch (error: any) {
    showToast(error?.message || '标记已读失败，请重试')
  } finally {
    markingId.value = null
  }
}

async function handleMarkAllRead() {
  if (markingAll.value || unreadCount.value === 0 || countUnread('ALL') === 0) return
  markingAll.value = true
  try {
    await elderApi.markAllNotificationsAsRead()
    const loaded = await loadCurrentNotifications()
    if (!loaded) {
      showToast('服务端已处理，但消息刷新失败')
      return
    }
    if (countUnread('ALL') > 0 || (unreadCount.value != null && unreadCount.value > 0)) {
      showToast('服务端已处理，刷新后仍有未读消息')
    } else if (unreadCount.value == null) {
      showToast('消息已刷新为已读，但未读总数暂不可核对')
    } else {
      showToast('已全部标为已读', 'success')
    }
  } catch (error: any) {
    showToast(error?.message || '全部标记已读失败，请重试')
  } finally {
    markingAll.value = false
  }
}

onShow(() => { void loadPage() })
</script>
<style scoped lang="scss">
.notif-page {
  min-height: 100vh;
  background-color: #F8FAFC;
  padding: 24rpx;
  box-sizing: border-box;
}
.state-card { background: #fff; border-radius: 18rpx; padding: 30rpx; margin: 20rpx 0; }
.centered { text-align: center; }
.error-card { border: 1rpx solid #fecaca; }
.state-title { display: block; color: #1c1917; font-size: 30rpx; font-weight: 700; }
.state-copy { display: block; margin-top: 12rpx; color: #64748b; font-size: 25rpx; line-height: 1.5; }
.state-action { margin-top: 16rpx; background: #0284c7; color: #fff; font-size: 25rpx; }
.context-subtitle { display: block; margin-top: 6rpx; color: #64748b; font-size: 22rpx; }
.info-note { display: block; margin: 0 0 16rpx; color: #64748b; font-size: 22rpx; line-height: 1.45; }
.event-type { display: inline-block; margin-bottom: 10rpx; padding: 4rpx 10rpx; border-radius: 8rpx; color: #0369a1; background: #e0f2fe; font-size: 21rpx; }
.mark-read-btn { color: #0369a1; background: #e0f2fe; font-size: 23rpx; }
.mark-read-btn[disabled] { opacity: 0.6; }

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;

  .page-title {
    font-size: 34rpx;
    font-weight: bold;
    color: #0F172A;
  }

  .read-all-btn {
    background-color: #F1F5F9;
    color: #64748B;
    font-size: 22rpx;
    border-radius: 8rpx;
  }
}

.tab-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;

  .tab-item {
    padding: 12rpx 24rpx;
    background-color: #FFFFFF;
    border-radius: 30rpx;
    display: flex;
    align-items: center;
    gap: 8rpx;
    box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.02);

    &.active {
      background-color: #0284C7;

      .tab-label {
        color: #FFFFFF;
        font-weight: bold;
      }
    }

    .tab-label {
      font-size: 26rpx;
      color: #475569;
    }

    .tab-badge {
      background-color: #EF4444;
      color: #FFFFFF;
      font-size: 18rpx;
      padding: 2rpx 8rpx;
      border-radius: 12rpx;
      font-weight: bold;
    }
  }
}

.notif-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.notif-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);

  &.unread-card {
    border-left: 8rpx solid #0284C7;
  }

  .card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12rpx;

    .title-with-dot {
      display: flex;
      align-items: center;
      gap: 8rpx;

      .unread-dot {
        color: #0284C7;
        font-size: 18rpx;
      }

      .item-title {
        font-size: 28rpx;
        font-weight: bold;
        color: #0F172A;
      }
    }

    .item-time {
      font-size: 22rpx;
      color: #94A3B8;
    }
  }

  .item-content {
    display: block;
    font-size: 26rpx;
    color: #475569;
    line-height: 1.5;
  }

  .action-footer {
    margin-top: 20rpx;
    display: flex;
    justify-content: flex-end;

    .call-elder-btn {
      background-color: #ECFDF5;
      color: #059669;
      font-size: 24rpx;
      font-weight: 600;
      border-radius: 12rpx;
      border: 1rpx solid #A7F3D0;
    }
  }
}
</style>
