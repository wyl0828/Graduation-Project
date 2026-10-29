<template>
  <view class="context-switcher-root">
    <scroll-view scroll-x class="pill-scroll" :show-scrollbar="false">
      <view class="pill-track">
        <view
          v-for="elder in elderContext.state.elders"
          :key="elder.linkId"
          class="elder-pill"
          :class="{
            active: elder.status === 'ACTIVE' && elderContext.state.selectedElderId === elder.elderId,
            disabled: elder.status !== 'ACTIVE',
            pending: elder.status === 'PENDING',
            frozen: elder.status === 'FROZEN',
            rejected: elder.status === 'REJECTED'
          }"
          @tap="onSelect(elder.elderId, elder.status)"
        >
          <text class="pill-name">{{ elder.name }}</text>
          <text class="pill-badge">{{ getBadgeText(elder.status) }}</text>
        </view>

        <view class="elder-pill add-pill" @tap="onAddElder">
          <text class="add-icon">+</text>
          <text class="add-text">申请绑定长辈</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { elderContext, type KinshipStatus } from '../../state/elderContext'

function getBadgeText(status: KinshipStatus) {
  switch (status) {
    case 'ACTIVE': return '已绑定'
    case 'PENDING': return '审核中'
    case 'FROZEN': return '争议冻结'
    case 'REJECTED': return '已驳回'
    default: return '状态未知'
  }
}

function onSelect(elderId: number, status: KinshipStatus) {
  if (status !== 'ACTIVE') {
    uni.showToast({ title: '该关系尚不可查看长辈业务信息', icon: 'none' })
    return
  }
  if (!elderContext.select(elderId)) return
  uni.showToast({ title: '已切换长辈上下文', icon: 'none' })
}

function onAddElder() {
  uni.navigateTo({ url: '/pages/family/bind-apply' })
}
</script>

<style lang="scss" scoped>
.context-switcher-root {
  width: 100%;
  background: transparent;
  padding: 12px 0 6px;
  position: sticky;
  top: 0;
  z-index: 99;
}

.pill-scroll {
  width: 100%;
  white-space: nowrap;
}

.pill-track {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
}

.elder-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 16px;
  border-radius: 999px;
  background: #ffffff;
  border: 1.5px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
  transition: all 0.2s ease;

  .pill-name {
    font-size: 15px;
    font-weight: 700;
    color: #334155;
  }

  .pill-badge {
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: #e2e8f0;
    color: #475569;
  }

  &.active {
    background: #ffffff;
    border: 2px solid #0284c7;
    box-shadow: 0 2px 8px rgba(2, 132, 199, 0.12);

    .pill-name {
      color: #0284c7;
      font-weight: 800;
    }

    .pill-badge {
      background: #0284c7;
      color: #ffffff;
    }
  }

  &.disabled {
    opacity: 0.75;
  }

  &.pending .pill-badge {
    background: #fef3c7;
    color: #92400e;
  }

  &.frozen .pill-badge,
  &.rejected .pill-badge {
    background: #fee2e2;
    color: #991b1b;
  }
}

.add-pill {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  padding: 7px 16px;
  border-radius: 999px;

  .add-icon {
    font-size: 15px;
    font-weight: 700;
    color: #475569;
    margin-right: 4px;
  }

  .add-text {
    font-size: 13px;
    font-weight: 600;
    color: #475569;
  }
}
</style>
