<template>
  <view class="booking-page">
    <!-- 顶部长辈信息卡片 -->
    <view class="elder-summary-card">
      <view class="elder-avatar">👴</view>
      <view class="elder-info-col">
        <view class="name-line">
          <text class="elder-name">{{ selectedElder?.name || '请选择已绑定长辈' }}</text>
          <text class="rel-tag">{{ selectedElder?.relationship || '' }} · {{ selectedElder?.age || '--' }}岁</text>
        </view>
        <picker v-if="activeElders.length > 1" :range="activeElders" range-key="name" @change="handleElderChange">
          <text class="address-txt">切换长辈 ›</text>
        </picker>
        <text class="address-txt">登记地址：{{ selectedElder?.address || '未填写' }}</text>
      </view>
    </view>

    <!-- 4 大标准助老服务目录卡片 (单选大卡片) -->
    <view class="service-catalog-section">
      <text class="section-title">选择助老照护项目</text>
      <view class="catalog-grid">
        <view
          v-for="item in catalogList"
          :key="item.id"
          class="catalog-card"
          :class="{ 'card-selected': selectedCatalogId === item.id }"
          @click="selectedCatalogId = item.id"
        >
          <view class="card-head-row">
          <text class="service-icon">🏠</text>
            <view class="radio-circle" :class="{ 'circle-active': selectedCatalogId === item.id }">
              <text v-if="selectedCatalogId === item.id" class="check-mark">✓</text>
            </view>
          </view>
          <text class="service-name">{{ item.name }}</text>
          <text class="service-duration">预计时长：{{ item.durationMin }} 分钟</text>
          <text class="service-desc">{{ item.desc }}</text>
        </view>
      </view>
    </view>

    <!-- 预约上门时间与表单项 -->
    <view class="booking-form-card">
      <text class="section-title">预约上门时间与叮嘱</text>

      <view class="form-row">
        <text class="form-label">上门服务日期:</text>
        <picker mode="date" :start="startDate" :end="endDate" :value="selectedDate" @change="handleDateChange">
          <view class="picker-display">{{ selectedDate || '点击选择服务日期' }} 📅</view>
        </picker>
      </view>

      <view class="form-row">
        <text class="form-label">服务时间段:</text>
        <view class="slot-select-row">
          <view
            class="slot-btn"
            :class="{ 'slot-active': selectedSlot === 'MORNING' }"
            @click="selectedSlot = 'MORNING'"
          >
            <text class="slot-title">上午时段</text>
            <text class="slot-sub">09:00 - 11:30</text>
          </view>
          <view
            class="slot-btn"
            :class="{ 'slot-active': selectedSlot === 'AFTERNOON' }"
            @click="selectedSlot = 'AFTERNOON'"
          >
            <text class="slot-title">下午时段</text>
            <text class="slot-sub">14:00 - 17:00</text>
          </view>
        </view>
      </view>

      <view class="form-row">
        <text class="form-label">上门服务地址:</text>
        <input v-model="serviceAddress" class="picker-display" placeholder="请输入长辈上门服务地址" />
      </view>

      <view class="form-row">
        <text class="form-label">特殊健康情况与叮嘱 (选填):</text>
        <textarea
          v-model="specialNotes"
          class="notes-textarea"
          placeholder="例如：老人腰腿活动不便，厨房地面湿滑，请服务人员携带防滑鞋套重点清洁地面..."
          maxlength="200"
        />
      </view>
    </view>

    <!-- 底部提交按钮 -->
    <view class="submit-footer">
      <button class="submit-booking-btn" @click="handleSubmitBooking">
        确认提交服务预约
      </button>
      <text class="footer-tip">提交成功后可在预约进度中查看社区处理状态</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'
import { elderContext } from '../../state/elderContext'

interface Catalog { id: number; serviceType: string; name: string; durationMin: number; desc: string }
const activeElders = elderContext.activeElders
const selectedElder = elderContext.selectedElder
const selectedCatalogId = ref<number | null>(null)
const catalogList = ref<Catalog[]>([])
const serviceAddress = ref('')
const submitting = ref(false)

const formatDate = (offsetDays: number) => {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const startDate = ref(formatDate(1))
const endDate = ref(formatDate(7))
const selectedDate = ref(formatDate(1))
const selectedSlot = ref<'MORNING' | 'AFTERNOON'>('MORNING')
const specialNotes = ref('')

onShow(async () => {
  try {
    const [loaded, catalogs] = await Promise.all([elderContext.refresh(), elderApi.listServiceCatalogs()])
    if (!loaded) throw new Error(elderContext.state.errorMessage)
    serviceAddress.value = selectedElder.value?.address || ''
    catalogList.value = ((catalogs.data || []) as any[]).map(item => ({
      id: item.catalogId,
      serviceType: item.serviceType,
      name: item.serviceName,
      durationMin: item.estimatedDurationMin,
      desc: item.description,
    }))
    selectedCatalogId.value = catalogList.value[0]?.id || null
  } catch (error: any) {
    catalogList.value = []
    uni.showToast({ title: error.message || '无法加载预约信息', icon: 'none' })
  }
})

const handleElderChange = (event: any) => {
  const elder = activeElders.value[Number(event.detail.value)]
  if (!elder || !elderContext.select(elder.elderId)) return
  serviceAddress.value = selectedElder.value?.address || ''
}

const handleDateChange = (e: any) => {
  selectedDate.value = e.detail.value
}

const handleSubmitBooking = async () => {
  if (submitting.value) return
  const elder = selectedElder.value
  const catalog = catalogList.value.find(item => item.id === selectedCatalogId.value)
  if (!elder || !catalog || !selectedDate.value || !serviceAddress.value.trim()) {
    uni.showToast({ title: '请选择长辈、服务和上门地址', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const response: any = await elderApi.createReservation({
      elderId: elder.elderId,
      serviceType: catalog.serviceType,
      serviceDate: selectedDate.value,
      slotCode: selectedSlot.value,
      serviceAddress: serviceAddress.value.trim(),
      specialNotes: specialNotes.value.trim() || undefined,
    })
    if (!Number.isInteger(Number(response.data)) || Number(response.data) <= 0) throw new Error('服务端未返回预约编号')
    uni.showToast({ title: '预约已提交', icon: 'success' })
    uni.navigateTo({ url: `/pages/family/order-timeline?reservationId=${response.data}` })
  } catch (error: any) {
    uni.showToast({ title: error.message || '预约提交失败，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.booking-page {
  min-height: 100vh;
  background-color: #F8FAFC;
  padding: 24rpx;
  box-sizing: border-box;
  padding-bottom: 120rpx;
}

/* 顶部长辈摘要 */
.elder-summary-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 24rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 24rpx;

  .elder-avatar {
    width: 80rpx;
    height: 80rpx;
    background-color: #EFF6FF;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
    font-size: 40rpx;
  }

  .elder-info-col {
    flex: 1;

    .name-line {
      display: flex;
      align-items: center;
      gap: 12rpx;
      margin-bottom: 6rpx;

      .elder-name {
        font-size: 32rpx;
        font-weight: bold;
        color: #0F172A;
      }

      .rel-tag {
        font-size: 22rpx;
        color: #2563EB;
        background-color: #EFF6FF;
        padding: 2rpx 10rpx;
        border-radius: 6rpx;
        font-weight: 500;
      }
    }

    .address-txt {
      font-size: 24rpx;
      color: #64748B;
      line-height: 1.4;
    }
  }
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: bold;
  color: #0F172A;
  margin-bottom: 16rpx;
}

/* 目录网格 */
.service-catalog-section {
  margin-bottom: 24rpx;

  .catalog-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16rpx;

    .catalog-card {
      background-color: #FFFFFF;
      border-radius: 16rpx;
      padding: 24rpx 20rpx;
      border: 3rpx solid #E2E8F0;
      box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.02);
      transition: all 0.2s ease;

      &.card-selected {
        border-color: #0284C7;
        background-color: #F0F9FF;
      }

      .card-head-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12rpx;

        .service-icon {
          font-size: 44rpx;
        }

        .radio-circle {
          width: 36rpx;
          height: 36rpx;
          border-radius: 50%;
          border: 2rpx solid #CBD5E1;
          display: flex;
          justify-content: center;
          align-items: center;

          &.circle-active {
            background-color: #0284C7;
            border-color: #0284C7;

            .check-mark {
              color: #FFFFFF;
              font-size: 22rpx;
              font-weight: bold;
            }
          }
        }
      }

      .service-name {
        display: block;
        font-size: 28rpx;
        font-weight: bold;
        color: #0F172A;
        margin-bottom: 6rpx;
      }

      .service-duration {
        display: block;
        font-size: 22rpx;
        color: #0284C7;
        font-weight: 500;
        margin-bottom: 8rpx;
      }

      .service-desc {
        display: block;
        font-size: 20rpx;
        color: #64748B;
        line-height: 1.4;
      }
    }
  }
}

/* 表单卡片 */
.booking-form-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 32rpx;

  .form-row {
    margin-bottom: 24rpx;

    &:last-child {
      margin-bottom: 0;
    }

    .form-label {
      display: block;
      font-size: 26rpx;
      font-weight: 600;
      color: #334155;
      margin-bottom: 10rpx;
    }

    .picker-display {
      background-color: #F8FAFC;
      border: 2rpx solid #E2E8F0;
      border-radius: 12rpx;
      padding: 20rpx;
      font-size: 28rpx;
      color: #0F172A;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .slot-select-row {
      display: flex;
      gap: 16rpx;

      .slot-btn {
        flex: 1;
        background-color: #F8FAFC;
        border: 2rpx solid #E2E8F0;
        border-radius: 12rpx;
        padding: 20rpx 16rpx;
        text-align: center;

        &.slot-active {
          border-color: #0284C7;
          background-color: #F0F9FF;

          .slot-title {
            color: #0284C7;
          }
        }

        .slot-title {
          display: block;
          font-size: 28rpx;
          font-weight: bold;
          color: #1E293B;
          margin-bottom: 4rpx;
        }

        .slot-sub {
          font-size: 22rpx;
          color: #64748B;
        }
      }
    }

    .notes-textarea {
      width: 100%;
      height: 140rpx;
      background-color: #F8FAFC;
      border: 2rpx solid #E2E8F0;
      border-radius: 12rpx;
      padding: 16rpx;
      box-sizing: border-box;
      font-size: 26rpx;
      color: #0F172A;
    }
  }
}

/* 底部提交 */
.submit-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: #FFFFFF;
  padding: 20rpx 32rpx;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 100;

  .submit-booking-btn {
    width: 100%;
    height: 90rpx;
    background: linear-gradient(135deg, #0284C7, #0369A1);
    color: #FFFFFF;
    font-size: 32rpx;
    font-weight: bold;
    border-radius: 16rpx;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .footer-tip {
    font-size: 20rpx;
    color: #94A3B8;
    margin-top: 8rpx;
  }
}
</style>
