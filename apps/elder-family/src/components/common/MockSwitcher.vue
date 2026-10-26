<template>
  <view class="mock-switcher-root">
    <!-- 悬浮调试胶囊 -->
    <view class="mock-pill" @tap="togglePanel">
      <text class="pill-dot"></text>
      <text class="pill-text">原型切换器</text>
    </view>

    <!-- 弹窗抽屉 -->
    <view v-if="isOpen" class="mock-modal-overlay" @tap="togglePanel">
      <view class="mock-modal-card" @tap.stop>
        <view class="card-header">
          <view class="header-title">
            <text class="title-bold">⚙️ 原型状态切换器 (Mock Switcher)</text>
            <text class="title-sub">无需连接后端，点击即时切换页面交互状态</text>
          </view>
          <view class="btn-close" @tap="togglePanel">✕</view>
        </view>

        <scroll-view scroll-y class="card-body">
          <!-- 数据源驱动模式 -->
          <view class="section">
            <text class="section-title">🔌 数据源模式</text>
            <view class="btn-group">
              <view
                class="tag-btn"
                :class="{ active: !mockState.useRealApi }"
                @tap="mockState.useRealApi = false"
              >
                离线 Mock
              </view>
              <view
                class="tag-btn success"
                :class="{ active: mockState.useRealApi }"
                @tap="mockState.useRealApi = true"
              >
                真实后端 API (8080)
              </view>
            </view>
          </view>

          <!-- 老人激活码页面状态 -->
          <view class="section">
            <text class="section-title">👴 老人端 · 6位激活码状态</text>
            <view class="btn-group">
              <view
                class="tag-btn"
                :class="{ active: mockState.elderMode === 'NORMAL' }"
                @tap="changeElderMode('NORMAL')"
              >
                待输入 (正常)
              </view>
              <view
                class="tag-btn warning"
                :class="{ active: mockState.elderMode === 'CODE_EXPIRED' }"
                @tap="changeElderMode('CODE_EXPIRED')"
              >
                激活码过期 (24h)
              </view>
              <view
                class="tag-btn danger"
                :class="{ active: mockState.elderMode === 'LOCKED' }"
                @tap="changeElderMode('LOCKED')"
              >
                输错锁定 (15min)
              </view>
              <view
                class="tag-btn success"
                :class="{ active: mockState.elderMode === 'SUCCESS' }"
                @tap="changeElderMode('SUCCESS')"
              >
                激活成功
              </view>
            </view>
          </view>

          <!-- 子女端多长辈/亲情授权状态 -->
          <view class="section">
            <text class="section-title">👨‍👩‍👧 子女端 · 亲情授权核心状态</text>
            <view class="btn-group">
              <view
                class="tag-btn"
                :class="{ active: mockState.familyMode === 'ACTIVE' }"
                @tap="changeFamilyMode('ACTIVE')"
              >
                已绑定生效 (ACTIVE)
              </view>
              <view
                class="tag-btn warning"
                :class="{ active: mockState.familyMode === 'PENDING' }"
                @tap="changeFamilyMode('PENDING')"
              >
                待社区审核 (PENDING)
              </view>
              <view
                class="tag-btn danger"
                :class="{ active: mockState.familyMode === 'FROZEN' }"
                @tap="changeFamilyMode('FROZEN')"
              >
                争议冻结拦截 (FROZEN)
              </view>
              <view
                class="tag-btn"
                :class="{ active: mockState.familyMode === 'EMPTY' }"
                @tap="changeFamilyMode('EMPTY')"
              >
                未绑定空态 (EMPTY)
              </view>
              <view
                class="tag-btn danger"
                :class="{ active: mockState.familyMode === 'REJECTED' }"
                @tap="changeFamilyMode('REJECTED')"
              >
                审核驳回 (REJECTED)
              </view>
            </view>
          </view>

          <!-- 并发冲突与异常 -->
          <view class="section">
            <text class="section-title">⚡ 业务并发冲突 (FZ-01)</text>
            <view class="toggle-row" @tap="toggleConflict">
              <text class="row-label">模拟档案并发修改冲突 (409 Conflict)</text>
              <view class="switch-box" :class="{ on: mockState.has409Conflict }">
                <text class="switch-thumb"></text>
              </view>
            </view>
          </view>

          <!-- 原型全页面快捷导航 -->
          <view class="section">
            <text class="section-title">🧭 全模块原型页面快捷直达</text>
            <view class="nav-grid">
              <view class="nav-item" @tap="navTo('/pages/elder/home')">
                <text class="nav-code">M05</text>
                <text class="nav-name">长辈SOS首页</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/elder/medication-box')">
                <text class="nav-code">M04</text>
                <text class="nav-name">今日长辈药盒</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/health-trend')">
                <text class="nav-code">M03</text>
                <text class="nav-name">长辈健康走势</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/medication-plan')">
                <text class="nav-code">M04</text>
                <text class="nav-name">服药计划管理</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/service-booking')">
                <text class="nav-code">M07</text>
                <text class="nav-name">助老照护预约</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/order-timeline')">
                <text class="nav-code">M06</text>
                <text class="nav-name">履约工单进度</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/notifications')">
                <text class="nav-code">M11</text>
                <text class="nav-name">关怀通知中心</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/dashboard')">
                <text class="nav-code">M02</text>
                <text class="nav-name">子女监护首页</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/elder/activate')">
                <text class="nav-code">M01</text>
                <text class="nav-name">老人6位激活</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/elder/profile')">
                <text class="nav-code">M02</text>
                <text class="nav-name">老人资料维护</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/bind-apply')">
                <text class="nav-code">M02</text>
                <text class="nav-name">亲情绑定申请</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/family/correction-apply')">
                <text class="nav-code">M02</text>
                <text class="nav-name">档案更正申请</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/auth/role-select')">
                <text class="nav-code">M01</text>
                <text class="nav-name">角色分流页</text>
              </view>
              <view class="nav-item" @tap="navTo('/pages/auth/login')">
                <text class="nav-code">M01</text>
                <text class="nav-name">统一登录页</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  mockState,
  setElderMode,
  setFamilyMode,
  toggle409Conflict,
  type ElderStatusMode,
  type FamilyStatusMode
} from '../../mock/mockState'

const isOpen = ref(false)

function togglePanel() {
  isOpen.value = !isOpen.value
}

function changeElderMode(mode: ElderStatusMode) {
  setElderMode(mode)
  uni.showToast({
    title: `已切换老人状态: ${mode}`,
    icon: 'none'
  })
}

function changeFamilyMode(mode: FamilyStatusMode) {
  setFamilyMode(mode)
  uni.showToast({
    title: `已切换子女状态: ${mode}`,
    icon: 'none'
  })
}

function toggleConflict() {
  toggle409Conflict()
  uni.showToast({
    title: mockState.has409Conflict ? '已开启 409 冲突模拟' : '已关闭冲突模拟',
    icon: 'none'
  })
}

function navTo(url: string) {
  isOpen.value = false
  uni.navigateTo({
    url,
    fail: () => {
      uni.reLaunch({ url })
    }
  })
}
</script>

<style lang="scss" scoped>
.mock-switcher-root {
  position: relative;
  z-index: 9999;
}

// 悬浮在右上角
.mock-pill {
  position: fixed;
  right: 12px;
  top: 50px;
  background: rgba(28, 25, 23, 0.85);
  backdrop-filter: blur(8px);
  padding: 6px 14px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.2);
  z-index: 9998;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.15);

  .pill-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #22c55e;
    box-shadow: 0 0 8px #22c55e;
  }

  .pill-text {
    font-size: 13px;
    font-weight: 600;
    color: #ffffff;
    letter-spacing: 0.5px;
  }
}

// 遮罩
.mock-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  z-index: 9999;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

// 底部卡片
.mock-modal-card {
  width: 100%;
  max-height: 80vh;
  background: #ffffff;
  border-radius: 24px 24px 0 0;
  padding: 20px 16px 28px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0eee9;

  .header-title {
    display: flex;
    flex-direction: column;

    .title-bold {
      font-size: 17px;
      font-weight: 700;
      color: #1c1917;
    }

    .title-sub {
      font-size: 12px;
      color: #78716c;
      margin-top: 2px;
    }
  }

  .btn-close {
    width: 32px;
    height: 32px;
    border-radius: 16px;
    background: #f5f4f0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    color: #78716c;
  }
}

.card-body {
  flex: 1;
  padding-top: 14px;
}

.section {
  margin-bottom: 20px;

  .section-title {
    font-size: 14px;
    font-weight: 700;
    color: #44403c;
    margin-bottom: 10px;
    display: block;
  }
}

.btn-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  .tag-btn {
    padding: 8px 12px;
    font-size: 13px;
    border-radius: 10px;
    background: #f5f4f0;
    color: #44403c;
    font-weight: 500;
    border: 1px solid transparent;

    &.active {
      background: #0284c7;
      color: #ffffff;
      font-weight: 700;
    }

    &.warning.active {
      background: #f59e0b;
      color: #ffffff;
    }

    &.danger.active {
      background: #ef4444;
      color: #ffffff;
    }

    &.success.active {
      background: #10b981;
      color: #ffffff;
    }
  }
}

.toggle-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #faf8f5;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e7e5e4;

  .row-label {
    font-size: 13px;
    color: #1c1917;
    font-weight: 600;
  }

  .switch-box {
    width: 44px;
    height: 24px;
    border-radius: 12px;
    background: #d6d3d1;
    position: relative;
    transition: background 0.2s;

    .switch-thumb {
      position: absolute;
      left: 2px;
      top: 2px;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #ffffff;
      transition: transform 0.2s;
    }

    &.on {
      background: #ea580c;

      .switch-thumb {
        transform: translateX(20px);
      }
    }
  }
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;

  .nav-item {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 8px 4px;
    display: flex;
    flex-direction: column;
    align-items: center;

    .nav-code {
      font-size: 11px;
      font-weight: 800;
      color: #0284c7;
    }

    .nav-name {
      font-size: 11px;
      color: #334155;
      margin-top: 2px;
      text-align: center;
    }
  }
}
</style>
