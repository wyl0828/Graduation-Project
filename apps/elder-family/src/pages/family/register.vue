<template>
  <view class="unavailable-page">
    <text class="title">子女注册暂未开放</text>
    <text class="description">注册需要核验手机号控制权。当前尚无短信服务，无法安全创建新账号。已有账号可返回登录。</text>
    <button class="back-button" @click="backToLogin">返回登录</button>
  </view>
</template>

<script setup lang="ts">
function backToLogin() {
  uni.reLaunch({ url: '/pages/auth/login' })
}
</script>

<style scoped>
.unavailable-page { min-height: 100vh; padding: 80rpx 40rpx; background: #faf8f5; display: flex; flex-direction: column; gap: 32rpx; }
.title { font-size: 42rpx; font-weight: 700; color: #1c1917; }
.description { font-size: 30rpx; line-height: 1.7; color: #57534e; }
.back-button { margin: 24rpx 0 0; background: #ea580c; color: #fff; border-radius: 16rpx; }
</style>
