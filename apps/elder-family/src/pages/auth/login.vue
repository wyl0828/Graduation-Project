<template>
  <view class="login-page">

    <view class="login-header">
      <view class="logo-box">🏛️</view>
      <text class="app-title">社区居家养老服务系统</text>
      <text class="app-sub">统一身份验证与授权访问</text>
    </view>

    <view class="form-card">
      <text class="form-heading">账号密码登录</text>

      <!-- 手机号 -->
      <view class="input-group">
        <text class="field-label">手机号码</text>
        <view class="field-box">
          <text class="field-prefix">+86</text>
          <input
            v-model="phone"
            class="input-native"
            type="number"
            maxlength="11"
            placeholder="请输入11位注册手机号"
          />
        </view>
      </view>

      <!-- 登录密码 -->
      <view class="input-group">
        <view class="label-row">
          <text class="field-label">登录密码</text>
          <text class="forgot-link" @tap="goToForgotPassword">忘记密码？</text>
        </view>
        <view class="field-box">
          <input
            v-model="password"
            class="input-native"
            :password="!showPassword"
            placeholder="请输入至少8位密码"
          />
          <text class="eye-btn" @tap="showPassword = !showPassword">
            {{ showPassword ? '👁️' : '🙈' }}
          </text>
        </view>
      </view>

      <!-- 登录按钮 -->
      <view class="login-btn" :class="{ loading: isLoading }" @tap="handleLogin">
        <text class="btn-text">{{ isLoading ? '正在登录中...' : '登 录' }}</text>
      </view>
    </view>

    <!-- 底部角色分流与激活引导 -->
    <view class="assist-links-box">
      <view class="assist-card elder-guide-card" @tap="goToElderActivate">
        <text class="guide-icon">👵</text>
        <view class="guide-texts">
          <text class="guide-main">长辈首次使用系统？</text>
        <text class="guide-sub">暂无短信核验服务，激活暂未开放</text>
        </view>
      </view>

      <view class="assist-card family-guide-card" @tap="goToFamilyRegister">
        <text class="guide-icon">👨‍👩‍👧</text>
        <view class="guide-texts">
          <text class="guide-main">新家庭子女注册</text>
        <text class="guide-sub">暂无短信核验服务，注册暂未开放</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'

const phone = ref('')
const password = ref('')
const showPassword = ref(false)
const isLoading = ref(false)

function enterRole(role?: string) {
  if (role === 'ELDER') {
    uni.reLaunch({ url: '/pages/elder/home' })
  } else if (role === 'CHILD') {
    uni.reLaunch({ url: '/pages/family/dashboard' })
  } else {
    uni.navigateTo({ url: '/pages/auth/role-select' })
  }
}

onShow(async () => {
  if (!uni.getStorageSync('token')) return
  try {
    const current = await elderApi.getCurrentUser()
    if (current.data?.currentRole) enterRole(current.data.currentRole)
  } catch {
    uni.removeStorageSync('token')
    uni.removeStorageSync('user')
    uni.removeStorageSync('activeRole')
  }
})

async function handleLogin() {
  if (isLoading.value) return
  if (phone.value.length !== 11) {
    uni.showToast({ title: '请输入有效的11位手机号', icon: 'none' })
    return
  }
  if (password.value.length < 6) {
    uni.showToast({ title: '密码长度至少6位', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    const res = await elderApi.login(phone.value.trim(), password.value, 'MINIAPP')
    const data = res.data
    if (data && data.token) {
      uni.setStorageSync('token', data.token)
      uni.setStorageSync('user', data.userAccount || { userId: data.userId, phone: phone.value })
      if (data.activeRole) {
        uni.setStorageSync('activeRole', data.activeRole)
      }
      uni.showToast({ title: '登录成功', icon: 'success' })
      enterRole(data.activeRole)
    } else {
      uni.showToast({ title: res.message || '未获取到有效登录信息', icon: 'none' })
    }
  } catch (err: any) {
    uni.showToast({ title: err.message || '登录失败，请检查账号密码', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

function goToForgotPassword() {
  uni.showToast({ title: '暂无短信核验服务，请联系社区工作人员', icon: 'none' })
}

function goToElderActivate() {
  uni.showToast({ title: '激活暂未开放', icon: 'none' })
}

function goToFamilyRegister() {
  uni.showToast({ title: '注册暂未开放', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  background-color: $elder-bg;
  padding: 40px 18px 20px;
  box-sizing: border-box;
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;

  .logo-box {
    width: 60px;
    height: 60px;
    border-radius: 18px;
    background: #ffffff;
    box-shadow: $card-shadow;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 32px;
    margin-bottom: 12px;
  }

  .app-title {
    font-size: 22px;
    font-weight: 800;
    color: #1c1917;
  }

  .app-sub {
    font-size: 13px;
    color: #78716c;
    margin-top: 4px;
  }
}

.form-card {
  background: #ffffff;
  border-radius: $card-radius;
  padding: 22px 18px;
  box-shadow: $card-shadow;
  margin-bottom: 20px;

  .form-heading {
    font-size: 18px;
    font-weight: 800;
    color: #1c1917;
    margin-bottom: 18px;
    display: block;
  }
}

.input-group {
  margin-bottom: 16px;

  .label-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
  }

  .field-label {
    font-size: 14px;
    font-weight: 700;
    color: #44403c;
  }

  .forgot-link {
    font-size: 12px;
    color: #0284c7;
    cursor: pointer;
  }

  .field-box {
    height: 50px;
    background: #faf8f5;
    border: 1.5px solid #e7e5e4;
    border-radius: 12px;
    display: flex;
    align-items: center;
    padding: 0 12px;

    .field-prefix {
      font-size: 14px;
      font-weight: 700;
      color: #78716c;
      margin-right: 8px;
      border-right: 1px solid #d6d3d1;
      padding-right: 8px;
    }

    .input-native {
      flex: 1;
      font-size: 15px;
      color: #1c1917;
    }

    .eye-btn {
      font-size: 18px;
      cursor: pointer;
      padding: 4px;
    }
  }
}

.login-btn {
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, $elder-gradient-start, $elder-gradient-end);
  box-shadow: 0 4px 14px rgba(234, 88, 12, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 22px;
  cursor: pointer;

  .btn-text {
    font-size: 18px;
    font-weight: 800;
    color: #ffffff;
    letter-spacing: 2px;
  }

  &.loading {
    opacity: 0.7;
  }
}

.assist-links-box {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .assist-card {
    background: #ffffff;
    border-radius: 14px;
    padding: 12px 14px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;

    .guide-icon {
      font-size: 26px;
    }

    .guide-texts {
      display: flex;
      flex-direction: column;

      .guide-main {
        font-size: 14px;
        font-weight: 700;
        color: #1c1917;
      }

      .guide-sub {
        font-size: 12px;
        color: #78716c;
        margin-top: 2px;
      }
    }

    &.elder-guide-card {
      border: 1px solid #fed7aa;
      background: #fff7ed;
      .guide-main {
        color: #9a3412;
      }
      .guide-sub {
        color: #c2410c;
      }
    }

    &.family-guide-card {
      border: 1px solid #bae6fd;
      background: #f0f9ff;
      .guide-main {
        color: #0369a1;
      }
      .guide-sub {
        color: #0284c7;
      }
    }
  }
}
</style>
