<template>
  <view class="dashboard-page">
    <!-- 顶部状态栏与操作 -->
    <view class="dashboard-header">
      <text class="header-title">子女看护首页</text>
      <button class="logout-pill" :disabled="logoutInProgress" @tap="handleLogout">
        {{ logoutInProgress ? '退出…' : '退出' }}
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
          <!-- 长辈信息主卡片 -->
          <view class="elder-summary-card">
            <view class="avatar-row">
              <view class="avatar-square">
                <text class="avatar-icon">👴</text>
              </view>
              <view class="elder-meta">
                <view class="name-line">
                  <text class="elder-name">{{ currentElder.name }}</text>
                  <text class="elder-age">{{ currentElder.age ?? 74 }} 岁</text>
                  <text class="authorized-badge">✓ 已授权</text>
                </view>
                <text class="elder-id">档案编号：{{ currentElder.archiveNo || 'ARC202610250001' }}</text>
              </view>
            </view>
            <view class="info-divider"></view>
            <view class="info-list">
              <text class="info-item">亲属关系： {{ currentElder.relationship || '儿子' }}</text>
              <text class="info-item">居住地址： {{ currentElder.address || '朝阳社区幸福路12号院3号楼2单元101' }}</text>
            </view>
          </view>

          <!-- 2x3 功能模块网格卡片 -->
          <view class="dashboard-grid">
            <view class="feature-card" @tap="goToHealthTrend">
              <view class="card-icon-box bg-rose">
                <text class="icon-emoji">❤️</text>
              </view>
              <text class="card-name">体征走势看板</text>
              <text class="card-val">查看长辈健康体征</text>
            </view>

            <view class="feature-card" @tap="goToMedicationPlan">
              <view class="card-icon-box bg-amber">
                <text class="icon-emoji">💊</text>
              </view>
              <text class="card-name">服药计划管理</text>
              <text class="card-val">查看用药安排</text>
            </view>

            <view class="feature-card" @tap="goToServiceBooking">
              <view class="card-icon-box bg-purple">
                <text class="icon-emoji">🍱</text>
              </view>
              <text class="card-name">助老照护预约</text>
              <text class="card-val">查看可申请服务</text>
            </view>

            <view class="feature-card" @tap="goToOrderTimeline">
              <view class="card-icon-box bg-indigo">
                <text class="icon-emoji">⏱️</text>
              </view>
              <text class="card-name">履约工单进度</text>
              <text class="card-val">查看服务办理进度</text>
            </view>

            <view class="feature-card" @tap="goToNotifications">
              <view class="card-icon-box bg-yellow">
                <text class="icon-emoji">🔔</text>
              </view>
              <text class="card-name">关怀通知消息</text>
              <text class="card-val">查看通知</text>
            </view>

            <view class="feature-card" @tap="goToCorrection">
              <view class="card-icon-box bg-sky">
                <text class="icon-emoji">📝</text>
              </view>
              <text class="card-name">资料更正申请</text>
              <text class="card-val">提交档案更正申请</text>
            </view>
          </view>

          <!-- 底部解除亲情关注卡片 -->
          <view class="unlink-card" :class="{ disabled: unlinking }" @tap="handleUnbind">
            <view class="unlink-head">
              <text class="unlink-title">{{ unlinking ? '正在解除…' : '解除亲情关注' }}</text>
              <text class="unlink-arrow">›</text>
            </view>
            <text class="unlink-desc">服务端确认解绑后，页面才会移除该长辈关系</text>
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
.dashboard-page {
  min-height: 100vh;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

/* 顶部状态与操作栏 */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px 8px;
  background: #ffffff;
}

.header-title {
  font-size: 19px;
  font-weight: 800;
  color: #1e293b;
  letter-spacing: 0.5px;
}

.logout-pill {
  margin: 0;
  padding: 4px 14px;
  border-radius: 999px;
  background: #fef2f2;
  color: #ef4444;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.6;
  border: 1px solid #fee2e2;

  &::after {
    border: none;
  }
}

.logout-pill[disabled] {
  opacity: 0.6;
}

.main-scroll {
  flex: 1;
  padding: 12px 16px 24px;
  box-sizing: border-box;
}

.active-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 长辈主卡片 */
.elder-summary-card {
  background: #ffffff;
  border-radius: 18px;
  padding: 18px;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.04);
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-square {
  width: 58px;
  height: 58px;
  border-radius: 16px;
  background: #fef9c3;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .avatar-icon {
    font-size: 32px;
  }
}

.elder-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.elder-name {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.elder-age {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

.authorized-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  background: #ecfdf5;
  color: #059669;
}

.elder-id {
  font-size: 12px;
  color: #94a3b8;
}

.info-divider {
  width: 100%;
  height: 1px;
  margin: 14px 0 12px;
  border-top: 1px dashed #e2e8f0;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-item {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}

/* 2x3 网格功能入口 */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.feature-card {
  background: #ffffff;
  border-radius: 18px;
  padding: 16px 14px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.98);
  }
}

.card-icon-box {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;

  .icon-emoji {
    font-size: 22px;
  }

  &.bg-rose { background: #ffe4e6; }
  &.bg-amber { background: #fef3c7; }
  &.bg-purple { background: #ede9fe; }
  &.bg-indigo { background: #e0e7ff; }
  &.bg-yellow { background: #fef9c3; }
  &.bg-sky { background: #e0f2fe; }
}

.card-name {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}

.card-val {
  font-size: 12px;
  color: #94a3b8;
}

/* 底部解除关注卡片 */
.unlink-card {
  background: #fff5f5;
  border: 1px solid #fee2e2;
  border-radius: 16px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 2px;

  &.disabled {
    opacity: 0.6;
  }

  &:active {
    background: #fee2e2;
  }
}

.unlink-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.unlink-title {
  font-size: 14px;
  font-weight: 700;
  color: #ef4444;
}

.unlink-arrow {
  font-size: 18px;
  color: #f87171;
  font-weight: 700;
}

.unlink-desc {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
}

/* 状态与空数据卡片 */
.state-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px 20px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  margin: 16px;
}

.centered {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.state-title {
  font-size: 18px;
  font-weight: 800;
  color: #1e293b;
}

.state-copy {
  margin-top: 8px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.empty-illu {
  font-size: 48px;
  margin-bottom: 12px;
}

.action-button {
  min-height: 44px;
  border-radius: 12px;
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  font-size: 14px;
  font-weight: 700;
}

.primary-button {
  background: #0284c7;
  color: #ffffff;
}

.secondary-button {
  background: #f1f5f9;
  color: #475569;
}

.error-card {
  border: 1px solid #fee2e2;
}

.relation-list {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.relation-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.relation-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;

  &.status-active { background: #ecfdf5; color: #059669; }
  &.status-pending { background: #fef3c7; color: #92400e; }
  &.status-frozen, &.status-rejected { background: #fee2e2; color: #991b1b; }
  &.status-unknown { background: #f1f5f9; color: #64748b; }
}

.relation-meta, .relation-note {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
</style>
