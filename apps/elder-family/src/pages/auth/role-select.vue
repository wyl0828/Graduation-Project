<template>
  <view class="page-container">

    <!-- 顶部品牌与温馨标语 -->
    <view class="brand-header">
      <view class="badge-tag">智慧社区健康照护服务平台</view>
      <text class="brand-title">社区居家养老</text>
      <text class="brand-slogan">温暖守护 · 智慧邻里 · 贴心到家</text>
    </view>

    <!-- 两个超大适老高对比度入口卡片 -->
    <view class="cards-wrapper">
      <!-- 卡片 A：长辈端入口 -->
      <view
        class="role-card elder-card"
        hover-class="card-hover-elder"
        @tap="goToElder"
      >
        <view class="card-badge elder-badge">推荐长者专用</view>
        <view class="card-main-row">
          <view class="role-icon-box elder-icon-box">
            <text class="icon-emoji">👴</text>
          </view>
          <view class="card-text-col">
            <text class="role-title elder-title">我是长辈</text>
            <text class="role-edition elder-edition">（适老极简版）</text>
          </view>
        </view>
        <view class="role-desc elder-desc">
          字大清晰 · 语音播报 · 一键呼救
        </view>
        <view class="card-footer-tags">
          <text class="pill-feat">🔊 语音播报</text>
          <text class="pill-feat">🚨 紧急直通</text>
          <text class="pill-feat">💊 用药提醒</text>
        </view>
        <view class="card-action-bar elder-action-bar">
          <text class="action-btn-text">点击进入适老版</text>
          <text class="action-arrow">→</text>
        </view>
      </view>

      <!-- 卡片 B：子女端入口 -->
      <view
        class="role-card family-card"
        hover-class="card-hover-family"
        @tap="goToFamily"
      >
        <view class="card-badge family-badge">家庭看护代办</view>
        <view class="card-main-row">
          <view class="role-icon-box family-icon-box">
            <text class="icon-emoji">👨‍👩‍👧</text>
          </view>
          <view class="card-text-col">
            <text class="role-title family-title">我是子女</text>
            <text class="role-edition family-edition">（监护代办版）</text>
          </view>
        </view>
        <view class="role-desc family-desc">
          远程看护 · 体征监控 · 服务代办
        </view>
        <view class="card-footer-tags">
          <text class="pill-feat blue">📊 体征联动</text>
          <text class="pill-feat blue">🍱 助餐申请</text>
          <text class="pill-feat blue">🛡️ 亲情管理</text>
        </view>
        <view class="card-action-bar family-action-bar">
          <text class="action-btn-text">点击进入监护版</text>
          <text class="action-arrow">→</text>
        </view>
      </view>
    </view>

    <!-- 底部无障碍兜底服务 -->
    <view class="footer-section">
      <view class="login-link-row">
        <text class="link-label">已有正式账号密码？</text>
        <text class="link-btn" @tap="goToLogin">去统一登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'

const roles = ref<string[]>([])
const switching = ref(false)

onShow(async () => {
  try {
    const response = await elderApi.getCurrentUser()
    roles.value = response.data?.roles || []
  } catch {
    uni.reLaunch({ url: '/pages/auth/login' })
  }
})

async function switchTo(role: 'ELDER' | 'CHILD', destination: string) {
  if (switching.value) return
  if (!roles.value.includes(role)) {
    uni.showToast({ title: '当前账号没有该角色权限', icon: 'none' })
    return
  }
  switching.value = true
  try {
    const response = await elderApi.switchRole(role)
    if (response.data?.token) uni.setStorageSync('token', response.data.token)
    uni.setStorageSync('activeRole', role)
    uni.reLaunch({ url: destination })
  } catch (error: any) {
    uni.showToast({ title: error.message || '角色切换失败', icon: 'none' })
  } finally {
    switching.value = false
  }
}

function goToElder() {
  void switchTo('ELDER', '/pages/elder/home')
}

function goToFamily() {
  void switchTo('CHILD', '/pages/family/dashboard')
}

function goToLogin() {
  uni.navigateTo({
    url: '/pages/auth/login'
  })
}

</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100vh;
  background-color: $elder-bg;
  padding: 30px 18px 40px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.brand-header {
  margin-top: 10px;
  margin-bottom: 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;

  .badge-tag {
    font-size: 13px;
    font-weight: 700;
    color: #ea580c;
    background: #fff7ed;
    border: 1px solid #ffedd5;
    padding: 4px 12px;
    border-radius: 12px;
    margin-bottom: 8px;
  }

  .brand-title {
    font-size: 28px;
    font-weight: 800;
    color: #1c1917;
    letter-spacing: 1px;
  }

  .brand-slogan {
    font-size: 15px;
    color: #78716c;
    margin-top: 6px;
  }
}

.cards-wrapper {
  display: flex;
  flex-direction: column;
  gap: 18px;
  flex: 1;
}

.role-card {
  position: relative;
  background: #ffffff;
  border-radius: $card-radius;
  box-shadow: $card-shadow;
  padding: 22px 18px;
  display: flex;
  flex-direction: column;
  min-height: 160px; // 满足 >=140px
  border: 2px solid transparent;
  transition: all 0.2s ease;
  cursor: pointer;

  .card-badge {
    position: absolute;
    top: 16px;
    right: 16px;
    font-size: 12px;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 12px;
  }

  .card-main-row {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 8px;

    .role-icon-box {
      width: 58px;
      height: 58px;
      border-radius: 16px;
      display: flex;
      align-items: center;
      justify-content: center;

      .icon-emoji {
        font-size: 32px;
      }
    }

    .card-text-col {
      display: flex;
      flex-direction: column;

      .role-title {
        font-size: 26px; // 标题 >=24px
        font-weight: 800;
        line-height: 1.2;
      }

      .role-edition {
        font-size: 16px;
        font-weight: 600;
        margin-top: 2px;
      }
    }
  }

  .role-desc {
    font-size: 18px; // 正文 >=18px
    font-weight: 700;
    margin-bottom: 12px;
  }

  .card-footer-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 14px;

    .pill-feat {
      font-size: 13px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 8px;
      background: #fff7ed;
      color: #c2410c;

      &.blue {
        background: #f0f9ff;
        color: #0369a1;
      }
    }
  }

  .card-action-bar {
    margin-top: auto;
    height: 48px; // 触控区 >=48px
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;

    .action-btn-text {
      font-size: 17px;
      font-weight: 700;
    }

    .action-arrow {
      font-size: 20px;
      font-weight: 800;
    }
  }
}

// 长辈端特色样式
.elder-card {
  border-color: #fed7aa;

  .elder-badge {
    background: #ffedd5;
    color: #c2410c;
  }

  .elder-icon-box {
    background: #ffedd5;
  }

  .elder-title {
    color: #ea580c;
  }

  .elder-edition {
    color: #9a3412;
  }

  .elder-desc {
    color: #1c1917;
  }

  .elder-action-bar {
    background: linear-gradient(135deg, $elder-gradient-start, $elder-gradient-end);
    color: #ffffff;
    box-shadow: 0 4px 14px rgba(234, 88, 12, 0.25);
  }
}

.card-hover-elder {
  transform: translateY(-2px);
  border-color: #ea580c;
  box-shadow: $card-shadow-active;
}

// 子女端特色样式
.family-card {
  border-color: #bae6fd;

  .family-badge {
    background: #e0f2fe;
    color: #0369a1;
  }

  .family-icon-box {
    background: #e0f2fe;
  }

  .family-title {
    color: #0284c7;
  }

  .family-edition {
    color: #0369a1;
  }

  .family-desc {
    color: #1c1917;
  }

  .family-action-bar {
    background: linear-gradient(135deg, #38bdf8, #0284c7);
    color: #ffffff;
    box-shadow: 0 4px 14px rgba(2, 132, 199, 0.25);
  }
}

.card-hover-family {
  transform: translateY(-2px);
  border-color: #0284c7;
  box-shadow: 0 8px 24px rgba(2, 132, 199, 0.15);
}

.footer-section {
  margin-top: 26px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;

  .help-box {
    width: 100%;
    background: #ffffff;
    border-radius: 14px;
    padding: 12px 14px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    gap: 12px;
    border: 1px dashed #d6d3d1;
    cursor: pointer;

    .help-icon {
      font-size: 24px;
    }

    .help-content {
      display: flex;
      flex-direction: column;

      .help-title {
        font-size: 14px;
        font-weight: 700;
        color: #44403c;
      }

      .help-phone {
        font-size: 13px;
        color: #ea580c;
        font-weight: 600;
      }
    }
  }

  .login-link-row {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;

    .link-label {
      color: #78716c;
    }

    .link-btn {
      color: #0284c7;
      font-weight: 700;
      text-decoration: underline;
      cursor: pointer;
    }
  }

  .copyright-note {
    font-size: 11px;
    color: #a8a29e;
    text-align: center;
  }
}
</style>
