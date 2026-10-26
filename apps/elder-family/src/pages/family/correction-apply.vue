<template>
  <view class="correction-page">
    <view v-if="familyState.loading" class="state-card centered">
      <text class="state-title">正在读取亲情关系…</text>
    </view>
    <view v-else-if="familyState.errorMessage" class="state-card error-card">
      <text class="state-title">无法读取亲情关系</text>
      <text class="state-copy">{{ familyState.errorMessage }}</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <view v-else-if="!currentElder" class="state-card centered">
      <text class="state-title">没有可更正的长辈档案</text>
      <text class="state-copy">需要存在已生效（ACTIVE）的亲情关系，才能提交档案更正申请。</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <view v-else-if="pageLoading" class="state-card centered">
      <text class="state-title">正在读取长辈档案基线…</text>
    </view>
    <view v-else-if="baselineError" class="state-card error-card">
      <text class="state-title">档案基线暂不可用</text>
      <text class="state-copy">{{ baselineError }}</text>
      <button class="state-action" @tap="loadPage">重新加载</button>
    </view>
    <view v-else-if="baseline" class="content-scroll">
      <view class="elder-context-card">
        <text class="context-label">当前 ACTIVE 长辈</text>
        <text class="context-name">{{ currentElder.name }} · {{ currentElder.relationship || '亲属' }}</text>
      </view>

      <view class="notice-card">
        <text class="notice-title">档案更正申请须知</text>
        <text class="notice-text">
          提交时服务端会按当前档案值固化快照。请先核对下方基线；审批时如档案已变化，社区会按服务端冲突规则处理。
        </text>
      </view>

      <view class="section-card">
        <view class="card-header">
          <text class="title">当前档案基线</text>
          <text class="tag">服务端读取</text>
        </view>
        <view class="baseline-item"><text class="bl-label">老人姓名：</text><text class="bl-val">{{ baseline.name || currentElder.name }}</text></view>
        <view class="baseline-item"><text class="bl-label">档案编号：</text><text class="bl-val">{{ baseline.archiveNo || currentElder.archiveNo || '未提供' }}</text></view>
        <view class="baseline-item"><text class="bl-label">现居地址：</text><text class="bl-val">{{ baseline.address || '未填写' }}</text></view>
        <view class="baseline-item"><text class="bl-label">联系电话：</text><text class="bl-val">{{ baseline.contactPhone || '未填写' }}</text></view>
        <view class="baseline-item"><text class="bl-label">紧急联系人：</text><text class="bl-val">{{ emergencyContactSummary }}</text></view>
      </view>

      <view class="section-card">
        <text class="title">选择需要更正的项目</text>
        <view class="checkbox-group">
          <view class="correction-row">
            <view class="chk-header" @tap="fields.address.selected = !fields.address.selected">
              <view class="chk-box" :class="{ on: fields.address.selected }"><text v-if="fields.address.selected">✓</text></view>
              <text class="chk-label">更正老人居住地址</text>
            </view>
            <view v-if="fields.address.selected" class="input-sub-box">
              <input v-model="fields.address.newVal" class="field-input" placeholder="请输入最新详细居住地址" />
            </view>
          </view>

          <view class="correction-row">
            <view class="chk-header" @tap="fields.contactPhone.selected = !fields.contactPhone.selected">
              <view class="chk-box" :class="{ on: fields.contactPhone.selected }"><text v-if="fields.contactPhone.selected">✓</text></view>
              <text class="chk-label">更正老人联系电话</text>
            </view>
            <view v-if="fields.contactPhone.selected" class="input-sub-box">
              <input v-model="fields.contactPhone.newVal" class="field-input" type="number" placeholder="请输入最新联系电话" />
            </view>
          </view>

          <view class="correction-row">
            <view class="chk-header" @tap="fields.emergencyContactName.selected = !fields.emergencyContactName.selected">
              <view class="chk-box" :class="{ on: fields.emergencyContactName.selected }"><text v-if="fields.emergencyContactName.selected">✓</text></view>
              <text class="chk-label">更正紧急联系人姓名</text>
            </view>
            <view v-if="fields.emergencyContactName.selected" class="input-sub-box">
              <input v-model="fields.emergencyContactName.newVal" class="field-input" placeholder="请输入紧急联系人姓名" />
            </view>
          </view>

          <view class="correction-row">
            <view class="chk-header" @tap="fields.emergencyContactRelation.selected = !fields.emergencyContactRelation.selected">
              <view class="chk-box" :class="{ on: fields.emergencyContactRelation.selected }"><text v-if="fields.emergencyContactRelation.selected">✓</text></view>
              <text class="chk-label">更正紧急联系人关系</text>
            </view>
            <view v-if="fields.emergencyContactRelation.selected" class="input-sub-box">
              <input v-model="fields.emergencyContactRelation.newVal" class="field-input" placeholder="例如：子女、配偶" />
            </view>
          </view>

          <view class="correction-row">
            <view class="chk-header" @tap="fields.emergencyContactPhone.selected = !fields.emergencyContactPhone.selected">
              <view class="chk-box" :class="{ on: fields.emergencyContactPhone.selected }"><text v-if="fields.emergencyContactPhone.selected">✓</text></view>
              <text class="chk-label">更正紧急联系人电话</text>
            </view>
            <view v-if="fields.emergencyContactPhone.selected" class="input-sub-box">
              <input v-model="fields.emergencyContactPhone.newVal" class="field-input" type="number" placeholder="请输入紧急联系人电话" />
            </view>
          </view>
        </view>

        <view class="reason-block">
          <text class="reason-label">更正理由 <text class="req">*</text></text>
          <textarea v-model="reason" class="reason-textarea" maxlength="200" placeholder="请说明需要更正的原因" />
          <text class="count-tip">{{ reason.length }}/200</text>
        </view>
      </view>

      <view class="submit-btn" :class="{ disabled: submitting }" @tap="handleSubmit">
        <text class="btn-text">{{ submitting ? '正在提交…' : '提交居委会审批更正' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { elderApi } from '../../api/elderApi'
import { elderContext } from '../../state/elderContext'

interface ElderArchiveBaseline {
  elderId: number
  name?: string
  archiveNo?: string
  address?: string
  contactPhone?: string
  emergencyContactName?: string
  emergencyContactRelation?: string
  emergencyContactPhone?: string
  rowVersion?: number
}

const familyState = elderContext.state
const currentElder = elderContext.selectedElder
const baseline = ref<ElderArchiveBaseline | null>(null)
const pageLoading = ref(false)
const baselineError = ref('')
const submitting = ref(false)
const fields = reactive({
  address: { selected: false, newVal: '' },
  contactPhone: { selected: false, newVal: '' },
  emergencyContactName: { selected: false, newVal: '' },
  emergencyContactRelation: { selected: false, newVal: '' },
  emergencyContactPhone: { selected: false, newVal: '' },
})
const reason = ref('')
const emergencyContactSummary = computed(() => {
  const profile = baseline.value
  if (!profile) return '未提供'
  const values = [profile.emergencyContactName, profile.emergencyContactRelation, profile.emergencyContactPhone].filter(Boolean)
  return values.length ? values.join(' · ') : '未填写'
})
let loadSequence = 0

function clearBaseline() {
  baseline.value = null
  baselineError.value = ''
}

async function loadProfileBaseline(elderId: number) {
  const sequence = ++loadSequence
  pageLoading.value = true
  clearBaseline()
  try {
    const response = await elderApi.getElderArchive(elderId)
    if (sequence !== loadSequence || currentElder.value?.elderId !== elderId) return
    const profile = response.data as ElderArchiveBaseline | null
    if (!profile || profile.elderId !== elderId) throw new Error('档案接口未返回当前长辈的资料。')
    baseline.value = profile
  } catch (error: any) {
    if (sequence === loadSequence) baselineError.value = error?.message || '长辈档案基线读取失败，请重试。'
  } finally {
    if (sequence === loadSequence) pageLoading.value = false
  }
}

async function loadPage() {
  clearBaseline()
  const loaded = await elderContext.refresh()
  if (!loaded || !currentElder.value) {
    pageLoading.value = false
    return
  }
  await loadProfileBaseline(currentElder.value.elderId)
}

function selectedItems() {
  const candidates = [
    { fieldCode: 'address', field: fields.address },
    { fieldCode: 'contactPhone', field: fields.contactPhone },
    { fieldCode: 'emergencyContactName', field: fields.emergencyContactName },
    { fieldCode: 'emergencyContactRelation', field: fields.emergencyContactRelation },
    { fieldCode: 'emergencyContactPhone', field: fields.emergencyContactPhone },
  ]
  return candidates.filter(item => item.field.selected).map(item => ({ fieldCode: item.fieldCode, proposedValue: item.field.newVal.trim() }))
}

function toast(title: string, icon: 'success' | 'none' = 'none') {
  if (typeof uni !== 'undefined' && uni.showToast) uni.showToast({ title, icon, duration: 3000 })
}

async function handleSubmit() {
  if (submitting.value) return
  const elder = currentElder.value
  const shownBaseline = baseline.value
  const items = selectedItems()
  if (!elder || !shownBaseline) {
    toast('当前长辈档案基线尚不可用')
    return
  }
  if (!items.length) {
    toast('请至少选择一项更正内容')
    return
  }
  if (items.some(item => !item.proposedValue)) {
    toast('请填写所选项目的更正内容')
    return
  }
  if (!reason.value.trim()) {
    toast('请填写更正理由')
    return
  }

  submitting.value = true
  try {
    const contextLoaded = await elderContext.refresh()
    const activeElder = currentElder.value
    if (!contextLoaded || !activeElder || activeElder.elderId !== elder.elderId) {
      toast(familyState.errorMessage || '当前长辈的 ACTIVE 授权已不可用')
      return
    }

    const freshResponse = await elderApi.getElderArchive(elder.elderId)
    const freshBaseline = freshResponse.data as ElderArchiveBaseline | null
    if (!freshBaseline || freshBaseline.elderId !== elder.elderId) throw new Error('无法核对当前档案基线。')
    if (shownBaseline.rowVersion != null && freshBaseline.rowVersion != null && shownBaseline.rowVersion !== freshBaseline.rowVersion) {
      baseline.value = freshBaseline
      toast('档案已更新，基线已刷新，请核对后重新提交')
      return
    }

    const response: any = await elderApi.submitCorrection(elder.elderId, reason.value.trim(), items)
    const requestId = Number(response?.data)
    if (!Number.isInteger(requestId) || requestId <= 0) throw new Error('服务端未返回档案更正申请编号。')
    uni.showModal({
      title: '更正申请已提交',
      content: `服务端已受理，申请编号：${requestId}`,
      confirmText: '返回',
      showCancel: false,
      success: () => uni.navigateBack(),
    })
  } catch (error: any) {
    if (error?.statusCode === 409) {
      await loadProfileBaseline(elder.elderId)
      toast('档案发生并发变化，基线已刷新，请重新核对')
    } else {
      toast(error?.message || '档案更正申请提交失败，请重试')
    }
  } finally {
    submitting.value = false
  }
}

onShow(() => { void loadPage() })
</script>
<style lang="scss" scoped>
.correction-page {
  min-height: 100vh;
  background-color: $elder-bg;
  padding: 16px;
  box-sizing: border-box;
}
.state-card { background: #fff; border-radius: 18px; padding: 20px; margin: 16px 0; }
.centered { text-align: center; }
.error-card { border: 1px solid #fecaca; }
.state-title { display: block; color: #1c1917; font-size: 17px; font-weight: 700; }
.state-copy { display: block; margin-top: 10px; color: #64748b; font-size: 14px; line-height: 1.5; }
.state-action { margin-top: 14px; background: #0284c7; color: #fff; font-size: 14px; }
.elder-context-card { display: flex; flex-direction: column; gap: 4px; background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 14px; }
.context-label { color: #64748b; font-size: 12px; }
.context-name { color: #1c1917; font-size: 16px; font-weight: 700; }
.disabled { opacity: 0.6; }

.notice-card {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 14px;
  padding: 12px 14px;
  margin-bottom: 16px;

  .notice-title {
    font-size: 14px;
    font-weight: 700;
    color: #92400e;
    display: block;
    margin-bottom: 4px;
  }

  .notice-text {
    font-size: 13px;
    color: #78350f;
    line-height: 1.4;
  }
}

.section-card {
  background: #ffffff;
  border-radius: 18px;
  padding: 18px 16px;
  box-shadow: $card-shadow;
  margin-bottom: 16px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .title {
      font-size: 16px;
      font-weight: 800;
      color: #1c1917;
    }

    .tag {
      font-size: 11px;
      font-weight: 700;
      color: #64748b;
      background: #f1f5f9;
      padding: 2px 8px;
      border-radius: 6px;
    }
  }

  .title {
    font-size: 16px;
    font-weight: 800;
    color: #1c1917;
    margin-bottom: 12px;
    display: block;
  }

  .baseline-item {
    font-size: 14px;
    margin-bottom: 6px;
    line-height: 1.4;

    .bl-label {
      color: #78716c;
    }
    .bl-val {
      color: #1c1917;
      font-weight: 600;
    }
  }
}

.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.correction-row {
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 12px;

  .chk-header {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;

    .chk-box {
      width: 20px;
      height: 20px;
      border-radius: 6px;
      border: 2px solid #a8a29e;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 800;
      color: #ffffff;

      &.on {
        background: #0284c7;
        border-color: #0284c7;
      }
    }

    .chk-label {
      font-size: 15px;
      font-weight: 700;
      color: #1c1917;
    }
  }

  .input-sub-box {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px dashed #f1f5f9;

    .field-input {
      width: 100%;
      height: 44px;
      background: #faf8f5;
      border: 1px solid #cbd5e1;
      border-radius: 10px;
      padding: 0 12px;
      font-size: 14px;
      box-sizing: border-box;
      color: #1c1917;
    }
  }
}

.reason-block {
  margin-top: 16px;

  .reason-label {
    font-size: 14px;
    font-weight: 700;
    color: #44403c;
    margin-bottom: 6px;
    display: block;

    .req {
      color: #ef4444;
    }
  }

  .reason-textarea {
    width: 100%;
    height: 80px;
    background: #faf8f5;
    border: 1.5px solid #e7e5e4;
    border-radius: 12px;
    padding: 10px 12px;
    font-size: 14px;
    box-sizing: border-box;
    color: #1c1917;
  }

  .count-tip {
    font-size: 12px;
    color: #a8a29e;
    text-align: right;
    display: block;
    margin-top: 4px;
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
  margin-top: 20px;
  cursor: pointer;

  .btn-text {
    font-size: 16px;
    font-weight: 700;
    color: #ffffff;
  }
}
</style>
