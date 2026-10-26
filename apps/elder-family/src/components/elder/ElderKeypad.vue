<template>
  <view class="elder-keypad-wrapper">
    <view class="keypad-grid">
      <!-- 1 ~ 9 -->
      <view
        v-for="num in ['1', '2', '3', '4', '5', '6', '7', '8', '9']"
        :key="num"
        class="key-btn digit-btn"
        hover-class="key-btn-active"
        :hover-stay-time="100"
        @tap="handleInput(num)"
      >
        <text class="btn-text">{{ num }}</text>
      </view>

      <!-- 重填 -->
      <view
        class="key-btn action-btn reset-btn"
        hover-class="key-btn-active"
        :hover-stay-time="100"
        @tap="handleClear"
      >
        <text class="action-text">重填</text>
      </view>

      <!-- 0 -->
      <view
        class="key-btn digit-btn"
        hover-class="key-btn-active"
        :hover-stay-time="100"
        @tap="handleInput('0')"
      >
        <text class="btn-text">0</text>
      </view>

      <!-- 退格 -->
      <view
        class="key-btn action-btn delete-btn"
        hover-class="key-btn-active"
        :hover-stay-time="100"
        @tap="handleBackspace"
      >
        <text class="action-icon">⌫</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (e: 'input', char: string): void
  (e: 'clear'): void
  (e: 'backspace'): void
}>()

function triggerFeedback() {
  // 适老化震动触感反馈
  // #ifdef MP-WEIXIN || APP-PLUS
  uni.vibrateShort({ type: 'light' })
  // #endif
}

function handleInput(char: string) {
  triggerFeedback()
  emit('input', char)
}

function handleClear() {
  triggerFeedback()
  emit('clear')
}

function handleBackspace() {
  triggerFeedback()
  emit('backspace')
}
</script>

<style lang="scss" scoped>
.elder-keypad-wrapper {
  width: 100%;
  background: #f5f4f0;
  border-top: 1px solid #e7e5e4;
  padding: 12px 14px 24px;
  box-sizing: border-box;
}

.keypad-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.key-btn {
  height: 62px; // 严格满足 >=60px 单键高度
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
  transition: all 0.08s ease;

  &.digit-btn {
    background: #ffffff;
    box-shadow: 0 4px 0 #d6d3d1, 0 4px 10px rgba(0, 0, 0, 0.04);

    .btn-text {
      font-size: 28px;
      font-weight: 700;
      color: #1c1917;
    }
  }

  &.action-btn {
    background: #e7e5e4;
    box-shadow: 0 4px 0 #cbd5e1;

    .action-text {
      font-size: 20px;
      font-weight: 700;
      color: #57534e;
    }

    .action-icon {
      font-size: 28px;
      font-weight: 700;
      color: #57534e;
    }
  }

  &-active {
    transform: translateY(3px);
    box-shadow: 0 1px 0 #cbd5e1 !important;
    opacity: 0.9;
  }
}
</style>
