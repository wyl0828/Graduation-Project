<template>
  <view
    class="voice-guide-bar"
    :class="{ playing: isPlaying }"
    hover-class="voice-bar-active"
    @tap="handlePlayVoice"
  >
    <view class="voice-icon-box">
      <text class="voice-emoji">🔊</text>
      <view v-if="isPlaying" class="sound-wave">
        <text class="bar bar-1"></text>
        <text class="bar bar-2"></text>
        <text class="bar bar-3"></text>
      </view>
    </view>
    <text class="voice-text">{{ isPlaying ? '正在语音播报中...' : text }}</text>
    <text class="voice-tip">点击收听</text>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    text?: string
    speechContent?: string
  }>(),
  {
    text: '点击听语音引导操作',
    speechContent: '请您在下方方格中，依次输入社区为您发放的六位数字激活码。'
  }
)

const isPlaying = ref(false)

function handlePlayVoice() {
  if (isPlaying.value) {
    isPlaying.value = false
    return
  }
  isPlaying.value = true
  uni.showToast({
    title: `📢 语音播报: "${props.speechContent}"`,
    icon: 'none',
    duration: 3500
  })
  setTimeout(() => {
    isPlaying.value = false
  }, 3500)
}
</script>

<style lang="scss" scoped>
.voice-guide-bar {
  width: 100%;
  height: 52px; // 严格满足 >=52px
  background: #fff7ed;
  border: 1.5px solid #fdba74;
  border-radius: 14px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  box-sizing: border-box;
  margin-bottom: 16px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(234, 88, 12, 0.08);

  &.playing {
    background: #ffedd5;
    border-color: #ea580c;
  }

  .voice-icon-box {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-right: 10px;

    .voice-emoji {
      font-size: 24px;
    }

    .sound-wave {
      display: flex;
      align-items: flex-end;
      gap: 2px;
      height: 16px;

      .bar {
        width: 3px;
        background: #ea580c;
        border-radius: 2px;
        animation: wave 0.8s infinite ease-in-out;

        &.bar-1 {
          height: 8px;
          animation-delay: 0.1s;
        }
        &.bar-2 {
          height: 14px;
          animation-delay: 0.3s;
        }
        &.bar-3 {
          height: 10px;
          animation-delay: 0.2s;
        }
      }
    }
  }

  .voice-text {
    flex: 1;
    font-size: 18px; // 适老正文 >= 18px
    font-weight: 700;
    color: #9a3412;
  }

  .voice-tip {
    font-size: 14px;
    color: #c2410c;
    background: #ffedd5;
    padding: 3px 8px;
    border-radius: 8px;
    font-weight: 600;
  }

  &-active {
    opacity: 0.85;
    transform: scale(0.99);
  }
}

@keyframes wave {
  0%, 100% {
    transform: scaleY(0.4);
  }
  50% {
    transform: scaleY(1);
  }
}
</style>
