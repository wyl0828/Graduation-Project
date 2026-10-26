<template>
  <view class="profile-page">

    <view class="content-scroll">
      <!-- 语音引导条 -->
      <VoiceGuideBar
        text="点击听个人资料朗读"
        speechContent="老人家，这里是您的联系资料。如果您更换了手机号码或住址，请直接修改并点击保存。"
      />

      <!-- 只读基础资料卡片 -->
      <view class="info-card">
        <view class="card-top-bar">
          <text class="card-headline">👤 长者身份基本资料</text>
          <text class="read-only-tag">社区实名认证</text>
        </view>
        <view class="item-row">
          <text class="label">姓名</text>
          <text class="value name-val">{{ elderData.name }}</text>
        </view>
        <view class="item-row">
          <text class="label">档案编号</text>
          <text class="value">{{ elderData.archiveNo }}</text>
        </view>
        <view class="notice-box">
          <text class="notice-icon">🔒</text>
          <text class="notice-text">
            姓名等身份字段由社区维护；如需更正，请联系社区工作人员。
          </text>
        </view>
      </view>

      <!-- 本人可修改的生活联系信息 -->
      <view class="info-card edit-card">
        <text class="card-headline">✏️ 本人常住与紧急联系方式</text>

        <!-- 联系电话 -->
        <view class="form-group">
          <text class="form-label">我的联系电话</text>
          <input
            v-model="elderData.phone"
            class="elder-input"
            type="number"
            maxlength="11"
            placeholder="请输入老人家本人手机号"
          />
        </view>

        <!-- 现居地址 -->
        <view class="form-group">
          <text class="form-label">现居住地址</text>
          <textarea
            v-model="elderData.address"
            class="elder-textarea"
            placeholder="请输入详细居住门牌号（便于助餐配送与上门护理）"
          />
        </view>

        <!-- 紧急联系人 -->
        <view class="form-group">
          <text class="form-label">紧急联系人（优先呼叫）</text>
          <view class="contact-inputs">
            <input
              v-model="elderData.emergencyContact.name"
              class="elder-input half-input"
              placeholder="亲属姓名"
            />
            <input
              v-model="elderData.emergencyContact.relation"
              class="elder-input half-input"
              placeholder="亲属称谓(如长子)"
            />
          </view>
          <input
            v-model="elderData.emergencyContact.phone"
            class="elder-input mt-8"
            type="number"
            placeholder="亲属紧急联系手机号"
          />
        </view>

        <!-- 保存按钮 -->
        <view class="save-btn" @tap="handleSave">
          <text class="save-btn-text">保 存 修 改</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import VoiceGuideBar from '../../components/elder/VoiceGuideBar.vue'
import { elderApi } from '../../api/elderApi'

const elderData = reactive({
  name: '',
  archiveNo: '',
  phone: '',
  address: '',
  emergencyContact: {
    name: '',
    relation: '',
    phone: ''
  }
})
const profileId = ref<number | null>(null)
const saving = ref(false)

function applyProfile(profile: any) {
  profileId.value = profile.elderId
  elderData.name = profile.name || ''
  elderData.archiveNo = profile.archiveNo || ''
  elderData.phone = profile.contactPhone || ''
  elderData.address = profile.address || ''
  elderData.emergencyContact.name = profile.emergencyContactName || ''
  elderData.emergencyContact.relation = profile.emergencyContactRelation || ''
  elderData.emergencyContact.phone = profile.emergencyContactPhone || ''
}

onShow(async () => {
  try {
    const response: any = await elderApi.getMyProfile()
    if (!response.data?.elderId) throw new Error('无法确认本人档案')
    applyProfile(response.data)
  } catch (error: any) {
    profileId.value = null
    uni.showToast({ title: error.message || '档案加载失败', icon: 'none' })
  }
})

async function handleSave() {
  if (!profileId.value || saving.value) return
  saving.value = true
  uni.showLoading({ title: '正在保存资料...' })
  try {
    const response: any = await elderApi.updateMyProfile({
      contactPhone: elderData.phone,
      address: elderData.address,
      emergencyContactName: elderData.emergencyContact.name,
      emergencyContactRelation: elderData.emergencyContact.relation,
      emergencyContactPhone: elderData.emergencyContact.phone,
    })
    if (response.data?.elderId !== profileId.value) throw new Error('服务端未确认档案更新')
    applyProfile(response.data)
    uni.showToast({ title: '档案已保存', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '保存失败，请重试', icon: 'none' })
  } finally {
    uni.hideLoading()
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  background-color: $elder-bg;
  padding: 16px;
  box-sizing: border-box;
}

.content-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  background: #ffffff;
  border-radius: $card-radius;
  padding: 18px 16px;
  box-shadow: $card-shadow;

  .card-top-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 14px;

    .read-only-tag {
      font-size: 12px;
      font-weight: 700;
      color: #64748b;
      background: #f1f5f9;
      padding: 3px 8px;
      border-radius: 6px;
    }
  }

  .card-headline {
    font-size: 20px; // 标题 >=20px
    font-weight: 800;
    color: #1c1917;
    display: block;
    margin-bottom: 14px;
  }

  .item-row {
    display: flex;
    justify-content: space-between;
    padding: 10px 0;
    border-bottom: 1px solid #f5f4f0;
    font-size: 18px; // 适老正文 >=18px

    .label {
      color: #78716c;
    }

    .value {
      color: #1c1917;
      font-weight: 600;

      &.name-val {
        font-size: 22px;
        font-weight: 800;
        color: #ea580c;
      }
    }
  }

  .notice-box {
    margin-top: 14px;
    background: #fafaf9;
    border-radius: 10px;
    padding: 10px 12px;
    display: flex;
    align-items: flex-start;
    gap: 8px;

    .notice-icon {
      font-size: 16px;
    }

    .notice-text {
      font-size: 13px;
      color: #78716c;
      line-height: 1.4;
    }
  }
}

.edit-card {
  border: 1.5px solid #fed7aa;
}

.form-group {
  margin-bottom: 16px;

  .form-label {
    font-size: 18px; // 适老 >=18px
    font-weight: 700;
    color: #1c1917;
    margin-bottom: 8px;
    display: block;
  }

  .elder-input {
    width: 100%;
    height: 52px; // 适老高度 >=52px
    background: #faf8f5;
    border: 2px solid #d6d3d1;
    border-radius: 12px;
    padding: 0 14px;
    font-size: 18px; // 适老大字
    font-weight: 600;
    box-sizing: border-box;
    color: #1c1917;

    &:focus {
      border-color: #ea580c;
    }
  }

  .elder-textarea {
    width: 100%;
    height: 80px;
    background: #faf8f5;
    border: 2px solid #d6d3d1;
    border-radius: 12px;
    padding: 10px 14px;
    font-size: 18px;
    box-sizing: border-box;
    color: #1c1917;
    line-height: 1.4;
  }

  .contact-inputs {
    display: flex;
    gap: 10px;

    .half-input {
      flex: 1;
    }
  }

  .mt-8 {
    margin-top: 8px;
  }
}

.save-btn {
  height: 54px; // 适老触控区 >=52px
  border-radius: 14px;
  background: linear-gradient(135deg, $elder-gradient-start, $elder-gradient-end);
  box-shadow: 0 4px 14px rgba(234, 88, 12, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 20px;
  cursor: pointer;

  .save-btn-text {
    font-size: 20px;
    font-weight: 800;
    color: #ffffff;
    letter-spacing: 2px;
  }
}
</style>
