<template>
  <view class="family-med-page">
    <view v-if="familyState.loading" class="state-card centered">
      <text class="state-title">正在读取亲情关系…</text>
    </view>
    <view v-else-if="familyState.errorMessage" class="state-card error-card">
      <text class="state-title">无法读取亲情关系</text>
      <text class="state-copy">{{ familyState.errorMessage }}</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <view v-else-if="!currentElder" class="state-card centered">
      <text class="state-title">没有可查看的长辈</text>
      <text class="state-copy">需要存在已生效（ACTIVE）的亲情关系，才能查看和维护用药计划。</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <template v-else>
      <view class="elder-header-card">
        <view class="elder-info-row">
          <text class="rel-tag">{{ currentElder.relationship || '亲属' }}</text>
          <text class="elder-name">{{ currentElder.name }}</text>
          <text v-if="currentElder.age != null" class="elder-age">{{ currentElder.age }}岁</text>
          <picker v-if="activeElders.length > 1" :range="activeElders" range-key="name" :value="currentElderIndex" @change="handleElderChange">
            <text class="switch-link">切换长辈</text>
          </picker>
        </view>
        <button size="mini" class="add-plan-btn" @click="openAddModal">+ 新建用药计划</button>
      </view>

      <view v-if="pageLoading" class="state-card centered">
        <text class="state-title">正在读取用药计划与依从性…</text>
      </view>
      <view v-else class="content-body">
        <view class="compliance-card">
          <view class="card-head">
            <text class="head-title">近30天服药遵从率</text>
            <text class="head-sub">按时打卡率评价</text>
          </view>
          <view v-if="complianceError" class="state-copy">{{ complianceError }}</view>
          <view v-else-if="compliance" class="compliance-body">
            <view class="rate-circle-box">
              <text class="rate-val">{{ compliance.complianceRate ?? '—' }}<text class="pct">%</text></text>
              <text class="rate-lbl">{{ compliance.ratingText || '服务端未提供评价' }}</text>
            </view>
            <view class="stats-col">
              <view class="stat-line"><text class="stat-lbl">近30天应服次数：</text><text class="stat-num">{{ formatCount(compliance.expectedDosesCount) }} 次</text></view>
              <view class="stat-line"><text class="stat-lbl">按时打卡次数：</text><text class="stat-num green-txt">{{ formatCount(compliance.confirmedDosesCount) }} 次</text></view>
              <view class="stat-line"><text class="stat-lbl">超时/漏服次数：</text><text class="stat-num orange-txt">{{ formatCount(compliance.missedDosesCount) }} 次</text></view>
            </view>
          </view>
          <view v-else class="state-copy">暂无服务端依从性数据</view>
        </view>

        <view class="plan-list-section">
          <text class="section-title">当前用药计划</text>
          <view v-if="planError" class="state-card error-card">
            <text class="state-title">用药计划暂不可用</text>
            <text class="state-copy">{{ planError }}</text>
            <button class="state-action" @click="reloadCurrentElder">重新加载</button>
          </view>
          <view v-else-if="planList.length === 0" class="state-card centered">
            <text class="state-title">暂无用药计划</text>
            <text class="state-copy">当前长辈的计划列表为空。</text>
          </view>
          <view v-for="plan in planList" :key="plan.id" class="plan-item-card">
            <view class="plan-top-row">
              <view class="name-badge-row">
                <text class="alias-pill">{{ plan.categoryTag || '未分类' }}</text>
                <text class="plan-med-name">{{ plan.medicineName }}</text>
              </view>
              <text class="status-tag success">{{ statusLabel(plan.status) }}<text v-if="plan.versionNo != null">（v{{ plan.versionNo }}）</text></text>
            </view>
            <view class="plan-detail-grid">
              <view class="detail-row"><text class="lbl">单次剂量：</text><text class="val">{{ plan.dosage || '未提供' }}</text></view>
              <view class="detail-row"><text class="lbl">服用方式：</text><text class="val">{{ plan.usageMethod || '未提供' }}</text></view>
              <view class="detail-row"><text class="lbl">每日提醒：</text><text class="val bold-val">{{ reminderSummary(plan.reminderTimes) }}</text></view>
            </view>
            <view class="plan-bottom-actions">
              <text class="version-tip">计划编号：{{ plan.id }}</text>
              <button size="mini" class="edit-plan-btn" :disabled="saving" @click="openEditModal(plan)">调整计划</button>
            </view>
          </view>
        </view>
      </view>
    </template>

    <view v-if="showModal" class="modal-mask">
      <view class="modal-card">
        <text class="modal-title">{{ editingPlanId ? '调整用药计划' : '新增用药计划' }}</text>
        <view v-if="editingPlanId" class="form-item">
          <text class="form-label">当前药品:</text>
          <text class="readonly-value">{{ editingPlanName }} · {{ editingCategoryTag }}</text>
        </view>
        <template v-else>
          <view class="form-item">
            <text class="form-label">药品分类:</text>
            <picker :range="categoryTagOptions" @change="handleCategoryTagChange">
              <view class="picker-input">{{ formCategoryTag || '请选择药品分类' }} ▼</view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">药品名称:</text>
            <input v-model="formMedicineName" class="input-text" placeholder="请输入药品通用名称" />
          </view>
        </template>
        <view class="form-item">
          <text class="form-label">单次服用剂量:</text>
          <input v-model="formDosage" class="input-text" placeholder="例如：5mg（1片）" />
        </view>
        <view class="form-item">
          <text class="form-label">服用方式:</text>
          <input v-model="formUsageMethod" class="input-text" placeholder="例如：口服" />
        </view>
        <view class="form-item">
          <text class="form-label">每日提醒时段 (HH:mm):</text>
          <input v-model="formReminderTime" class="input-text" placeholder="例如：08:00" />
        </view>
        <view class="form-item">
          <text class="form-label">调整原因 (必填):</text>
          <input v-model="formChangeReason" class="input-text" placeholder="请填写实际医嘱或调整原因" />
        </view>
        <view class="refresh-today-checkbox-box">
          <checkbox :checked="isRefreshTodayTasks" @tap="isRefreshTodayTasks = !isRefreshTodayTasks" color="#0284C7" />
          <view class="checkbox-text-col">
            <text class="checkbox-main-title">立即刷新今日未开始时段任务</text>
            <text class="checkbox-sub-tip">服务端仅替换今日尚未开始的任务。</text>
          </view>
        </view>
        <view class="modal-btns">
          <button class="btn-cancel" :disabled="saving" @click="showModal = false">取消</button>
          <button class="btn-confirm" :disabled="saving" @click="handleSavePlan">{{ saving ? '正在保存…' : '确认保存' }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'
import { elderContext } from '../../state/elderContext'

interface ReminderTime {
  reminderTime?: string
  timeLabel?: string
}

interface PlanItem {
  id: number
  categoryTag: string
  medicineName: string
  dosage: string
  usageMethod: string
  reminderTimes: ReminderTime[]
  versionNo?: number
  status: string
}

interface Compliance {
  complianceRate?: number | string | null
  expectedDosesCount?: number | null
  confirmedDosesCount?: number | null
  missedDosesCount?: number | null
  ratingText?: string
}

const familyState = elderContext.state
const activeElders = elderContext.activeElders
const currentElder = elderContext.selectedElder
const currentElderIndex = computed(() => activeElders.value.findIndex(item => item.elderId === currentElder.value?.elderId))
const categoryTagOptions = ['降压药', '降糖药', '心脏药', '常规药']
const planList = ref<PlanItem[]>([])
const compliance = ref<Compliance | null>(null)
const pageLoading = ref(false)
const planError = ref('')
const complianceError = ref('')
const showModal = ref(false)
const editingPlanId = ref<number | null>(null)
const editingPlanName = ref('')
const editingCategoryTag = ref('')
const formCategoryTag = ref('')
const formMedicineName = ref('')
const formDosage = ref('')
const formUsageMethod = ref('口服')
const formReminderTime = ref('08:00')
const formChangeReason = ref('')
const isRefreshTodayTasks = ref(false)
const saving = ref(false)
let loadSequence = 0

function formatCount(value?: number | null) {
  return value == null ? '—' : String(value)
}

function reminderSummary(items: ReminderTime[]) {
  if (!items.length) return '服务端未提供提醒时间'
  return items.map(item => [item.reminderTime, item.timeLabel].filter(Boolean).join(' ')).join(' / ')
}

function statusLabel(status: string) {
  if (status === 'ACTIVE') return '执行中'
  return status || '状态未提供'
}

function clearData() {
  planList.value = []
  compliance.value = null
  planError.value = ''
  complianceError.value = ''
}

async function loadData(elderId: number) {
  const sequence = ++loadSequence
  clearData()
  pageLoading.value = true
  const [plansResult, complianceResult] = await Promise.allSettled([
    elderApi.listMedicationPlans(elderId),
    elderApi.getMedicationCompliance(elderId, 30),
  ])
  if (sequence !== loadSequence || currentElder.value?.elderId !== elderId) return false

  let plansLoaded = false
  if (plansResult.status === 'fulfilled') {
    if (Array.isArray(plansResult.value.data)) {
      planList.value = plansResult.value.data.map((plan: any) => ({
        id: Number(plan.planId),
        categoryTag: plan.categoryTag || '',
        medicineName: plan.medicineName || '',
        dosage: plan.dosage || '',
        usageMethod: plan.usageMethod || '',
        reminderTimes: Array.isArray(plan.reminderTimes) ? plan.reminderTimes : [],
        versionNo: plan.currentVersionNo ?? undefined,
        status: plan.status || '',
      })).filter((plan: PlanItem) => Number.isFinite(plan.id) && plan.id > 0)
      plansLoaded = true
    } else {
      planError.value = '用药计划接口返回格式异常。'
    }
  } else {
    planError.value = (plansResult.reason as Error)?.message || '用药计划加载失败，请重试。'
  }

  if (complianceResult.status === 'fulfilled') {
    if (complianceResult.value.data && typeof complianceResult.value.data === 'object') {
      compliance.value = complianceResult.value.data as Compliance
    } else {
      complianceError.value = '服药依从性接口返回格式异常。'
    }
  } else {
    complianceError.value = (complianceResult.reason as Error)?.message || '服药依从性加载失败。'
  }
  pageLoading.value = false
  return plansLoaded
}

async function loadPage() {
  const loaded = await elderContext.refresh()
  clearData()
  if (!loaded || !currentElder.value) {
    pageLoading.value = false
    return
  }
  await loadData(currentElder.value.elderId)
}

async function reloadCurrentElder() {
  if (currentElder.value) await loadData(currentElder.value.elderId)
}

function handleElderChange(event: any) {
  const elder = activeElders.value[Number(event.detail.value)]
  if (!elder || !elderContext.select(elder.elderId)) return
  void loadData(elder.elderId)
}

function openAddModal() {
  if (!currentElder.value || saving.value) return
  editingPlanId.value = null
  editingPlanName.value = ''
  editingCategoryTag.value = ''
  formCategoryTag.value = ''
  formMedicineName.value = ''
  formDosage.value = ''
  formUsageMethod.value = '口服'
  formReminderTime.value = '08:00'
  formChangeReason.value = ''
  isRefreshTodayTasks.value = false
  showModal.value = true
}

function openEditModal(plan: PlanItem) {
  if (saving.value) return
  editingPlanId.value = plan.id
  editingPlanName.value = plan.medicineName
  editingCategoryTag.value = plan.categoryTag
  formCategoryTag.value = plan.categoryTag
  formMedicineName.value = plan.medicineName
  formDosage.value = plan.dosage
  formUsageMethod.value = plan.usageMethod || '口服'
  formReminderTime.value = plan.reminderTimes[0]?.reminderTime || ''
  formChangeReason.value = ''
  isRefreshTodayTasks.value = false
  showModal.value = true
}

function handleCategoryTagChange(event: any) {
  formCategoryTag.value = categoryTagOptions[Number(event.detail.value)] || ''
}

function showToast(title: string, icon: 'success' | 'none' = 'none') {
  if (typeof uni !== 'undefined' && uni.showToast) uni.showToast({ title, icon, duration: 3000 })
}

async function handleSavePlan() {
  const elder = currentElder.value
  if (saving.value) return
  if (!elder) {
    showToast('没有可用的 ACTIVE 长辈上下文')
    return
  }
  if (!formDosage.value.trim() || !formUsageMethod.value.trim() || !formReminderTime.value.trim() || !formChangeReason.value.trim()) {
    showToast('请填写剂量、服用方式、提醒时段和调整原因')
    return
  }
  if (!editingPlanId.value && (!formMedicineName.value.trim() || !formCategoryTag.value)) {
    showToast('请填写药品名称并选择分类')
    return
  }

  saving.value = true
  let acceptedPlanId = 0
  try {
    const reminderTimes = [{ reminderTime: formReminderTime.value.trim(), timeLabel: '' }]
    const response: any = editingPlanId.value
      ? await elderApi.updateMedicationPlan(editingPlanId.value, {
        dosage: formDosage.value.trim(),
        usageMethod: formUsageMethod.value.trim(),
        changeReason: formChangeReason.value.trim(),
        reminderTimes,
        refreshTodayPending: isRefreshTodayTasks.value,
      })
      : await elderApi.createMedicationPlan({
        elderId: elder.elderId,
        medicineName: formMedicineName.value.trim(),
        dosage: formDosage.value.trim(),
        usageMethod: formUsageMethod.value.trim(),
        categoryTag: formCategoryTag.value,
        reminderTimes,
        changeReason: formChangeReason.value.trim(),
        refreshTodayPending: isRefreshTodayTasks.value,
      })
    acceptedPlanId = Number(response?.data?.planId)
    if (!Number.isInteger(acceptedPlanId) || acceptedPlanId <= 0) throw new Error('服务端未返回用药计划编号')
    showModal.value = false

    const plansLoaded = await loadData(elder.elderId)
    if (!plansLoaded) {
      showToast(`服务端已受理计划 ${acceptedPlanId}，但列表刷新失败，请稍后重试`)
      return
    }

    if (isRefreshTodayTasks.value) {
      try {
        const tasksResponse = await elderApi.getTodayMedicationTasks(elder.elderId)
        if (!Array.isArray(tasksResponse.data)) throw new Error('今日服药任务接口返回格式异常。')
        showToast(`计划 ${acceptedPlanId} 已保存，今日任务列表已重新读取`, 'success')
      } catch (error: any) {
        showToast(`计划 ${acceptedPlanId} 已保存，今日任务刷新状态暂不可读取：${error?.message || '读取失败'}`)
      }
    } else {
      showToast(`计划 ${acceptedPlanId} 已保存`, 'success')
    }
  } catch (error: any) {
    showToast(error?.message || '用药计划保存失败，请重试')
  } finally {
    saving.value = false
  }
}

onShow(() => { void loadPage() })
</script>
<style scoped lang="scss">
.family-med-page {
  min-height: 100vh;
  background-color: #F8FAFC;
  padding: 24rpx;
  box-sizing: border-box;
}
.state-card { background: #fff; border-radius: 18rpx; padding: 30rpx; margin: 20rpx 0; }
.centered { text-align: center; }
.error-card { border: 1rpx solid #fecaca; }
.state-title { display: block; color: #1c1917; font-size: 30rpx; font-weight: 700; }
.state-copy { display: block; margin-top: 12rpx; color: #64748b; font-size: 25rpx; line-height: 1.5; }
.state-action { margin-top: 16rpx; background: #0284c7; color: #fff; font-size: 25rpx; }
.switch-link { margin-left: 10rpx; color: #0284c7; font-size: 22rpx; }
.readonly-value { display: block; color: #334155; font-size: 26rpx; }

/* 顶部长辈卡片 */
.elder-header-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 24rpx;

  .elder-info-row {
    display: flex;
    align-items: center;
    gap: 12rpx;

    .rel-tag {
      background-color: #EFF6FF;
      color: #2563EB;
      font-size: 22rpx;
      font-weight: bold;
      padding: 4rpx 12rpx;
      border-radius: 8rpx;
    }

    .elder-name {
      font-size: 34rpx;
      font-weight: bold;
      color: #0F172A;
    }

    .elder-age {
      font-size: 26rpx;
      color: #64748B;
    }
  }

  .add-plan-btn {
    background-color: #0284C7;
    color: #FFFFFF;
    font-size: 24rpx;
    font-weight: 600;
    border-radius: 12rpx;
  }
}

/* 争议冻结提示 */
.frozen-alert-card {
  background-color: #FFFBEB;
  border: 2rpx solid #FDE68A;
  border-radius: 20rpx;
  padding: 36rpx;
  text-align: center;
  margin-top: 40rpx;

  .alert-icon {
    font-size: 64rpx;
  }

  .alert-title {
    display: block;
    font-size: 34rpx;
    font-weight: bold;
    color: #92400E;
    margin: 16rpx 0;
  }

  .alert-desc {
    font-size: 28rpx;
    color: #78350F;
    line-height: 1.6;
  }
}

/* 服药遵从率卡片 */
.compliance-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 24rpx;

  .card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;

    .head-title {
      font-size: 30rpx;
      font-weight: bold;
      color: #0F172A;
    }

    .head-sub {
      font-size: 24rpx;
      color: #64748B;
    }
  }

  .compliance-body {
    display: flex;
    align-items: center;
    gap: 32rpx;

    .rate-circle-box {
      width: 170rpx;
      height: 170rpx;
      border-radius: 50%;
      background: radial-gradient(circle, #ECFDF5 60%, #D1FAE5 100%);
      border: 6rpx solid #10B981;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;

      .rate-val {
        font-size: 38rpx;
        font-weight: 800;
        color: #059669;

        .pct {
          font-size: 22rpx;
        }
      }

      .rate-lbl {
        font-size: 20rpx;
        color: #047857;
        font-weight: 600;
      }
    }

    .stats-col {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 10rpx;

      .stat-line {
        display: flex;
        justify-content: space-between;
        font-size: 26rpx;

        .stat-lbl {
          color: #64748B;
        }

        .stat-num {
          font-weight: bold;
          color: #0F172A;

          &.green-txt {
            color: #10B981;
          }

          &.orange-txt {
            color: #F59E0B;
          }
        }
      }
    }
  }
}

/* 计划列表 */
.plan-list-section {
  .section-title {
    display: block;
    font-size: 30rpx;
    font-weight: bold;
    color: #0F172A;
    margin-bottom: 16rpx;
  }
}

.plan-item-card {
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
  margin-bottom: 20rpx;

  .plan-top-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16rpx;

    .name-badge-row {
      display: flex;
      align-items: center;
      gap: 12rpx;

      .alias-pill {
        background-color: #EA580C;
        color: #FFFFFF;
        font-size: 22rpx;
        font-weight: bold;
        padding: 4rpx 12rpx;
        border-radius: 8rpx;
      }

      .plan-med-name {
        font-size: 32rpx;
        font-weight: bold;
        color: #0F172A;
      }
    }

    .status-tag {
      font-size: 22rpx;
      padding: 4rpx 12rpx;
      border-radius: 8rpx;
      &.success {
        color: #16A34A;
        background-color: #DCFCE7;
      }
    }
  }

  .plan-detail-grid {
    background-color: #F8FAFC;
    border-radius: 12rpx;
    padding: 16rpx 20rpx;
    margin-bottom: 16rpx;
    display: flex;
    flex-direction: column;
    gap: 8rpx;

    .detail-row {
      display: flex;
      font-size: 26rpx;

      .lbl {
        color: #64748B;
        width: 150rpx;
      }

      .val {
        color: #1E293B;

        &.bold-val {
          font-weight: bold;
          color: #0284C7;
        }
      }
    }
  }

  .plan-bottom-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .version-tip {
      font-size: 22rpx;
      color: #94A3B8;
      flex: 1;
      margin-right: 12rpx;
    }

    .edit-plan-btn {
      background-color: #F1F5F9;
      color: #334155;
      font-size: 24rpx;
      border-radius: 8rpx;
    }
  }
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
  padding: 32rpx;

  .modal-card {
    background-color: #FFFFFF;
    border-radius: 24rpx;
    padding: 36rpx;
    width: 100%;
    max-width: 640rpx;
    box-sizing: border-box;

    .modal-title {
      display: block;
      font-size: 34rpx;
      font-weight: bold;
      color: #0F172A;
      margin-bottom: 24rpx;
      text-align: center;
    }

    .form-item {
      margin-bottom: 18rpx;

      .form-label {
        display: block;
        font-size: 26rpx;
        color: #475569;
        margin-bottom: 6rpx;
      }

      .picker-input {
        background-color: #F8FAFC;
        border: 2rpx solid #E2E8F0;
        border-radius: 12rpx;
        padding: 16rpx 20rpx;
        font-size: 28rpx;
        color: #0F172A;
      }

      .input-text {
        background-color: #F8FAFC;
        border: 2rpx solid #E2E8F0;
        border-radius: 12rpx;
        padding: 16rpx 20rpx;
        font-size: 28rpx;
        color: #0F172A;
      }
    }

    .refresh-today-checkbox-box {
      margin: 20rpx 0 28rpx;
      padding: 16rpx;
      background-color: #EFF6FF;
      border-radius: 12rpx;
      display: flex;
      gap: 12rpx;
      align-items: flex-start;

      .checkbox-text-col {
        flex: 1;

        .checkbox-main-title {
          display: block;
          font-size: 26rpx;
          font-weight: bold;
          color: #1D4ED8;
          margin-bottom: 4rpx;
        }

        .checkbox-sub-tip {
          font-size: 22rpx;
          color: #3B82F6;
          line-height: 1.4;
        }
      }
    }

    .modal-btns {
      display: flex;
      gap: 16rpx;

      .btn-cancel {
        flex: 1;
        background-color: #F1F5F9;
        color: #64748B;
        font-size: 28rpx;
        border-radius: 12rpx;
      }

      .btn-confirm {
        flex: 1.5;
        background-color: #0284C7;
        color: #FFFFFF;
        font-size: 28rpx;
        border-radius: 12rpx;
        font-weight: 600;
      }
    }
  }
}
</style>
