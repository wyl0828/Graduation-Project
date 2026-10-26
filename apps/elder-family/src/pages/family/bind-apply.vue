<template>
  <view class="bind-apply-page">

    <view class="form-scroll">
      <!-- 引导提示 -->
      <view class="guide-banner">
        <text class="banner-icon">🛡️</text>
        <view class="banner-texts">
          <text class="banner-title">申请长辈监护授权</text>
          <text class="banner-desc">为保护老人隐私，提交后需经所属社区居委会审核核验老人意愿。</text>
        </view>
      </view>

      <!-- 表单卡片 -->
      <view class="form-card">
        <text class="card-title">长辈基本信息</text>

        <!-- 社区提供的档案 ID，服务端仅创建待审核申请 -->
        <view class="form-item">
          <text class="item-label">长辈档案 ID <text class="required">*</text></text>
          <input
            v-model="formData.elderId"
            class="input-control"
            type="number"
            placeholder="请填写社区提供的数字档案 ID"
          />
        </view>
        <text class="char-count">提交后仍需社区核验关系与老人意愿，审核通过前无法查看长辈信息。</text>
      </view>

      <view class="form-card">
        <text class="card-title">亲属关系声明</text>

        <!-- 亲属关系单选 Pills -->
        <view class="form-item">
          <text class="item-label">您与老人的关系 <text class="required">*</text></text>
          <view class="pills-grid">
            <view
              v-for="rel in relationships"
              :key="rel"
              class="relation-pill"
              :class="{ selected: formData.relation === rel }"
              @tap="formData.relation = rel"
            >
              <text class="pill-text">{{ rel }}</text>
            </view>
          </view>
        </view>

        <!-- 证明事由与附言 -->
        <view class="form-item">
          <text class="item-label">监护说明与附言 <text class="required">*</text></text>
          <textarea
            v-model="formData.applyReason"
            class="textarea-control"
            maxlength="200"
            placeholder="请简述您常态看护情况（如：独居老人照料、代办就医订餐等），限200字"
          />
          <text class="char-count">{{ formData.applyReason.length }}/200</text>
        </view>
      </view>

      <!-- 承诺勾选 -->
      <view class="agreement-row" @tap="formData.isAgreed = !formData.isAgreed">
        <view class="checkbox" :class="{ checked: formData.isAgreed }">
          <text v-if="formData.isAgreed" class="check-mark">✓</text>
        </view>
        <text class="agreement-text">
          本人承诺所填亲属信息真实合法，若存在冒领监护权愿承担法律后果。
        </text>
      </view>

      <!-- 提交按钮 -->
      <view class="submit-btn" :class="{ disabled: !canSubmit }" @tap="handleSubmit">
        <text class="btn-text">提交社区审核</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, computed, ref } from 'vue'
import { elderApi } from '../../api/elderApi'

const relationships = ['父亲', '母亲', '祖父母', '岳父/岳母', '法定指定监护人']

const formData = reactive({
  elderId: '',
  relation: '',
  applyReason: '',
  isAgreed: false
})
const submitting = ref(false)

const canSubmit = computed(() => {
  return (
    Number.isInteger(Number(formData.elderId)) && Number(formData.elderId) > 0 &&
    formData.relation !== '' &&
    formData.applyReason.trim() !== '' &&
    formData.isAgreed
  )
})

async function handleSubmit() {
  if (submitting.value) return
  if (!canSubmit.value) {
    uni.showToast({ title: '请完整填写表单并勾选承诺', icon: 'none' })
    return
  }

  submitting.value = true
  uni.showLoading({ title: '正在提交社区核验...' })
  try {
    const response: any = await elderApi.applyKinship(Number(formData.elderId), formData.relation, formData.applyReason.trim())
    if (!Number.isInteger(Number(response.data)) || Number(response.data) <= 0) throw new Error('服务端未返回申请编号')
    uni.showModal({
      title: '申请已提交',
      content: `申请编号 ${response.data}。社区审核通过前无法查看长辈业务信息。`,
      confirmText: '查看进度',
      showCancel: false,
      success: () => {
        uni.redirectTo({ url: '/pages/family/dashboard' })
      }
    })
  } catch (error: any) {
    uni.showToast({ title: error.message || '申请提交失败，请重试', icon: 'none' })
  } finally {
    uni.hideLoading()
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.bind-apply-page {
  min-height: 100vh;
  background-color: $elder-bg;
  padding: 16px;
  box-sizing: border-box;
}

.guide-banner {
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 14px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;

  .banner-icon {
    font-size: 24px;
  }

  .banner-texts {
    display: flex;
    flex-direction: column;

    .banner-title {
      font-size: 15px;
      font-weight: 700;
      color: #0369a1;
    }

    .banner-desc {
      font-size: 12px;
      color: #0284c7;
      margin-top: 2px;
    }
  }
}

.form-card {
  background: #ffffff;
  border-radius: 18px;
  padding: 18px 16px;
  box-shadow: $card-shadow;
  margin-bottom: 16px;

  .card-title {
    font-size: 17px;
    font-weight: 800;
    color: #1c1917;
    margin-bottom: 14px;
    display: block;
  }
}

.form-item {
  margin-bottom: 16px;

  .item-label {
    font-size: 14px;
    font-weight: 700;
    color: #44403c;
    margin-bottom: 6px;
    display: block;

    .required {
      color: #ef4444;
    }
  }

  .input-control {
    width: 100%;
    height: 48px;
    background: #faf8f5;
    border: 1.5px solid #e7e5e4;
    border-radius: 12px;
    padding: 0 14px;
    font-size: 15px;
    box-sizing: border-box;
    color: #1c1917;
  }

  .community-picker {
    height: 48px;
    background: #faf8f5;
    border: 1.5px solid #e7e5e4;
    border-radius: 12px;
    padding: 0 14px;
    display: flex;
    align-items: center;
    justify-content: space-between;

    .picker-value {
      font-size: 15px;
      color: #1c1917;
    }

    .picker-arrow {
      font-size: 12px;
      color: #78716c;
    }
  }

  .textarea-control {
    width: 100%;
    height: 90px;
    background: #faf8f5;
    border: 1.5px solid #e7e5e4;
    border-radius: 12px;
    padding: 10px 14px;
    font-size: 14px;
    box-sizing: border-box;
    color: #1c1917;
    line-height: 1.4;
  }

  .char-count {
    font-size: 12px;
    color: #a8a29e;
    text-align: right;
    display: block;
    margin-top: 4px;
  }
}

.pills-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  .relation-pill {
    padding: 8px 14px;
    border-radius: 10px;
    background: #f5f4f0;
    border: 1.5px solid transparent;
    cursor: pointer;

    .pill-text {
      font-size: 14px;
      font-weight: 600;
      color: #44403c;
    }

    &.selected {
      background: #f0f9ff;
      border-color: #0284c7;

      .pill-text {
        color: #0284c7;
        font-weight: 700;
      }
    }
  }
}

.agreement-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 10px 4px 20px;
  cursor: pointer;

  .checkbox {
    width: 20px;
    height: 20px;
    border-radius: 6px;
    border: 2px solid #a8a29e;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 2px;

    &.checked {
      background: #0284c7;
      border-color: #0284c7;

      .check-mark {
        color: #ffffff;
        font-size: 14px;
        font-weight: 800;
      }
    }
  }

  .agreement-text {
    flex: 1;
    font-size: 13px;
    color: #57534e;
    line-height: 1.4;
  }
}

.submit-btn {
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, #38bdf8, #0284c7);
  box-shadow: 0 4px 14px rgba(2, 132, 199, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  .btn-text {
    font-size: 17px;
    font-weight: 700;
    color: #ffffff;
  }

  &.disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}
</style>
