<template>
  <view class="unavailable-page">
    <text class="title">密码找回暂未开放</text>
    <text class="description">当前尚无短信核验服务，系统无法验证手机号控制权。请联系社区工作人员处理账号问题。</text>
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
