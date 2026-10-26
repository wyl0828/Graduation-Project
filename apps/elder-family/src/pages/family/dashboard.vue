<template>
  <view class="dashboard-page">
    <view class="session-actions">
      <button class="logout-button" :disabled="logoutInProgress" @tap="handleLogout">
        {{ logoutInProgress ? '正在退出…' : '退出登录' }}
      </button>
    </view>

    <view v-if="familyState.loading" class="state-card centered">
      <text class="state-title">正在读取亲情关系…</text>
    </view>

    <view v-else-if="familyState.errorMessage" class="state-card error-card">
      <text class="state-title">无法读取亲情关系</text>
      <text class="state-copy">{{ familyState.errorMessage }}</text>
      <view class="action-button primary-button" @tap="refreshRelationships">
        <text>重新加载</text>
      </view>
      <view v-if="isLoginExpired" class="action-button secondary-button" @tap="goToLogin">
        <text>返回登录</text>
      </view>
    </view>

    <template v-else>
      <ElderContextSwitcher v-if="familyState.elders.length > 0" />

      <scroll-view scroll-y class="main-scroll">
        <view v-if="familyState.elders.length === 0" class="state-card centered empty-card">
          <view class="empty-illu">👵👴</view>
          <text class="state-title">暂无亲情关系</text>
          <text class="state-copy">
            当前账号尚无待审核或已生效的亲情关系。申请绑定长辈并经社区审核后，可在此查看授权范围内的信息。
          </text>
          <view class="action-button primary-button" @tap="goToBind">
            <text>申请绑定长辈</text>
          </view>
        </view>

        <view v-else-if="activeElders.length === 0" class="relation-list">
          <view class="state-card">
            <text class="state-title">当前没有可查看的长辈</text>
            <text class="state-copy">以下关系状态来自服务端；待审核、冻结或已驳回的关系不能作为业务查看上下文。</text>
          </view>

          <view v-for="elder in familyState.elders" :key="elder.linkId" class="relation-card">
            <view class="relation-heading">
              <text class="elder-name">{{ elder.name || '长辈' }}</text>
              <text class="status-badge" :class="statusClass(elder.status)">
                {{ statusTitle(elder.status) }}
              </text>
            </view>
            <text class="relation-meta">关系：{{ elder.relationship || '未提供' }}</text>
            <text v-if="elder.archiveNo" class="relation-meta">档案编号：{{ elder.archiveNo }}</text>
            <text class="relation-note">{{ statusDescription(elder) }}</text>
            <text v-if="elder.status === 'FROZEN' && elder.freezeReason" class="relation-note">
              冻结说明：{{ elder.freezeReason }}
            </text>
            <text v-if="elder.status === 'PENDING' && elder.reviewDeadline" class="relation-meta">
              审核期限：{{ elder.reviewDeadline }}
            </text>
          </view>

          <view class="action-button primary-button" @tap="goToBind">
            <text>申请绑定其他长辈</text>
          </view>
        </view>

        <view v-else-if="currentElder" class="active-block">
          <view class="elder-summary-card">
            <view class="avatar-row">
              <view class="avatar-circle">{{ currentElder.sex === '女' ? '👵' : '👴' }}</view>
              <view class="elder-meta">
                <view class="name-line">
                  <text class="elder-name">{{ currentElder.name }}</text>
                  <text v-if="currentElder.age != null" class="elder-age">{{ currentElder.age }} 岁</text>
                  <text class="active-badge">✓ 已授权</text>
                </view>
                <text v-if="currentElder.archiveNo" class="elder-id">档案编号：{{ currentElder.archiveNo }}</text>
              </view>
            </view>
            <view class="info-tags">
              <text class="tag-item">亲属关系：{{ currentElder.relationship || '未提供' }}</text>
              <text v-if="currentElder.address" class="tag-item">居住地址：{{ currentElder.address }}</text>
            </view>
          </view>

          <view class="dashboard-grid">
            <view class="feature-card" @tap="goToHealthTrend">
              <text class="card-icon red-icon">❤️</text>
              <text class="card-name">体征走势看板</text>
              <text class="card-val">查看长辈健康体征</text>
            </view>
            <view class="feature-card" @tap="goToMedicationPlan">
              <text class="card-icon blue-icon">💊</text>
              <text class="card-name">服药计划管理</text>
              <text class="card-val">查看用药安排</text>
            </view>
            <view class="feature-card" @tap="goToServiceBooking">
              <text class="card-icon amber-icon">🍱</text>
              <text class="card-name">助老照护预约</text>
              <text class="card-val">查看可申请服务</text>
            </view>
            <view class="feature-card" @tap="goToOrderTimeline">
              <text class="card-icon teal-icon">⏱️</text>
              <text class="card-name">履约工单进度</text>
              <text class="card-val">查看服务办理进度</text>
            </view>
            <view class="feature-card" @tap="goToNotifications">
              <text class="card-icon purple-icon">🔔</text>
              <text class="card-name">关怀通知消息</text>
              <text class="card-val">查看通知</text>
            </view>
            <view class="feature-card" @tap="goToCorrection">
              <text class="card-icon green-icon">📝</text>
              <text class="card-name">资料更正申请</text>
              <text class="card-val">提交档案更正申请</text>
            </view>
          </view>

          <view class="manage-box">
            <view class="action-item" :class="{ disabled: unlinking }" @tap="handleUnbind">
              <text class="action-title">{{ unlinking ? '正在解除…' : '解除亲情关注' }}</text>
              <text class="action-desc">服务端确认解绑后，页面才会移除该长辈关系</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ElderContextSwitcher from '../../components/family/ElderContextSwitcher.vue'
import { elderApi } from '../../api/elderApi'
import { elderContext, type BoundElder, type KinshipStatus } from '../../state/elderContext'
import { logoutCurrentSession } from '../../state/authSession'

const familyState = elderContext.state
const activeElders = elderContext.activeElders
const currentElder = elderContext.selectedElder
const unlinking = ref(false)
const logoutInProgress = ref(false)
const isLoginExpired = computed(() => familyState.errorMessage.includes('登录状态已失效'))

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

onShow(() => {
  void refreshRelationships()
})

async function refreshRelationships() {
  await elderContext.refresh()
}

function statusTitle(status: KinshipStatus) {
  switch (status) {
    case 'ACTIVE': return '已生效'
    case 'PENDING': return '待审核'
    case 'FROZEN': return '争议冻结'
    case 'REJECTED': return '已驳回'
    default: return '状态未知'
  }
}

function statusClass(status: KinshipStatus) {
  switch (status) {
    case 'ACTIVE': return 'status-active'
    case 'PENDING': return 'status-pending'
    case 'FROZEN': return 'status-frozen'
    case 'REJECTED': return 'status-rejected'
    default: return 'status-unknown'
  }
}

function statusDescription(elder: BoundElder) {
  switch (elder.status) {
    case 'PENDING': return '亲情关系申请正在等待社区审核，审核通过前不能查看长辈业务信息。'
    case 'FROZEN': return '该亲情关系处于冻结状态，相关访问权限已暂停，请联系所属社区核实处理。'
    case 'REJECTED': return '该亲情关系申请未通过审核，可联系所属社区了解后续办理方式。'
    default: return '该关系当前不可用于查看长辈业务信息。'
  }
}

function goToBind() { uni.navigateTo({ url: '/pages/family/bind-apply' }) }
function goToLogin() { uni.reLaunch({ url: '/pages/auth/login' }) }
function goToCorrection() { uni.navigateTo({ url: '/pages/family/correction-apply' }) }
function goToHealthTrend() { uni.navigateTo({ url: '/pages/family/health-trend' }) }
function goToMedicationPlan() { uni.navigateTo({ url: '/pages/family/medication-plan' }) }
function goToServiceBooking() { uni.navigateTo({ url: '/pages/family/service-booking' }) }
function goToOrderTimeline() { uni.navigateTo({ url: '/pages/family/order-timeline' }) }
function goToNotifications() { uni.navigateTo({ url: '/pages/family/notifications' }) }

function handleUnbind() {
  if (unlinking.value || !currentElder.value) return
  uni.showModal({
    title: '确认解除亲情关系？',
    content: '解除后将停止通过该关系查看长辈健康与服务信息。',
    confirmColor: '#EF4444',
    success: async (result) => {
      if (!result.confirm || !currentElder.value || unlinking.value) return
      unlinking.value = true
      try {
        await elderApi.unlinkKinship(currentElder.value.linkId, '家属主动解绑')
        uni.showToast({ title: '服务端已确认解绑', icon: 'success' })
        await elderContext.refresh()
      } catch (error) {
        uni.showToast({ title: elderContext.getErrorMessage(error), icon: 'none', duration: 3000 })
      } finally {
        unlinking.value = false
      }
    },
  })
}
</script>

<style lang="scss" scoped>
.dashboard-page { min-height: 100vh; background: #f8fafc; display: flex; flex-direction: column; }
.session-actions { display: flex; justify-content: flex-end; padding: 8px 16px 0; }
.logout-button { min-width: 120px; min-height: 44px; border: 1px solid #b91c1c; border-radius: 10px; background: #fff; color: #991b1b; font-size: 15px; font-weight: 700; }
.logout-button[disabled] { opacity: 0.65; }
.main-scroll { flex: 1; padding: 16px; box-sizing: border-box; }
.state-card, .relation-card, .elder-summary-card, .feature-card, .manage-box { background: #fff; border-radius: 16px; padding: 16px; box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06); }
.state-card { margin: 16px; }
.centered { display: flex; align-items: center; flex-direction: column; text-align: center; padding: 32px 20px; }
.state-title { display: block; font-size: 19px; font-weight: 800; color: #1c1917; }
.state-copy { display: block; margin-top: 10px; color: #64748b; line-height: 1.55; font-size: 14px; }
.empty-illu { font-size: 52px; margin-bottom: 10px; }
.action-button { min-height: 46px; border-radius: 12px; margin-top: 14px; display: flex; justify-content: center; align-items: center; padding: 0 20px; font-size: 15px; font-weight: 700; }
.primary-button { color: #fff; background: linear-gradient(135deg, #38bdf8, #0284c7); }
.secondary-button { color: #0369a1; background: #e0f2fe; }
.error-card { border: 1px solid #fecaca; }
.relation-list { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.relation-list > .state-card { margin: 0; }
.relation-card { display: flex; flex-direction: column; gap: 8px; }
.relation-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.elder-name { font-size: 19px; font-weight: 800; color: #1c1917; }
.relation-meta, .relation-note { color: #64748b; font-size: 13px; line-height: 1.5; }
.relation-note { color: #475569; }
.status-badge, .active-badge { border-radius: 8px; padding: 4px 8px; font-size: 11px; font-weight: 700; white-space: nowrap; }
.status-pending { color: #92400e; background: #fef3c7; }
.status-frozen, .status-rejected { color: #991b1b; background: #fee2e2; }
.status-active { color: #166534; background: #dcfce7; }
.status-unknown { color: #475569; background: #e2e8f0; }
.active-block { padding: 16px; }
.elder-summary-card { margin-bottom: 16px; }
.avatar-row { display: flex; align-items: center; gap: 14px; }
.avatar-circle { width: 52px; height: 52px; border-radius: 16px; background: #f0f9ff; display: flex; align-items: center; justify-content: center; font-size: 28px; }
.elder-meta { display: flex; flex-direction: column; }
.name-line { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.elder-age { font-size: 14px; color: #78716c; }
.active-badge { color: #166534; background: #dcfce7; }
.elder-id { font-size: 12px; color: #a8a29e; margin-top: 4px; }
.info-tags { margin-top: 12px; padding-top: 10px; border-top: 1px dashed #e2e8f0; display: flex; flex-direction: column; gap: 4px; }
.tag-item { font-size: 13px; color: #64748b; }
.dashboard-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.feature-card { display: flex; flex-direction: column; gap: 6px; min-height: 110px; }
.card-icon { font-size: 24px; }
.card-name { font-size: 15px; font-weight: 700; color: #1c1917; }
.card-val { font-size: 12px; color: #64748b; }
.manage-box { padding: 14px 16px; }
.action-item { display: flex; flex-direction: column; }
.action-item.disabled { opacity: 0.55; }
.action-title { font-size: 15px; font-weight: 700; color: #ef4444; }
.action-desc { font-size: 12px; color: #a8a29e; margin-top: 3px; }
</style>
