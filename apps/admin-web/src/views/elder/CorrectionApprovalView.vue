<template>
  <div class="correction-approval-view">
    <el-card shadow="never" class="header-card">
      <div class="card-intro">
        <h2 class="title">档案资料更正审批工作台 (P-12 / FZ-01)</h2>
        <p class="desc">
          展示服务端提供的申请基线、档案当前值和建议值。发生并发冲突或申请人授权失效时，以服务端审核结果为准。
        </p>
      </div>

      <el-tabs v-model="activeTab" class="status-tabs" @tab-change="handleTabChange">
        <el-tab-pane name="PENDING_REVIEW">
          <template #label>
            <span>待审核</span>
            <el-badge :value="pendingCount" class="tab-badge" type="warning" />
          </template>
        </el-tab-pane>
        <el-tab-pane name="APPROVED_APPLIED">
          <template #label>
            <span>已批准</span>
            <el-badge :value="approvedCount" class="tab-badge" type="success" />
          </template>
        </el-tab-pane>
        <el-tab-pane name="REJECTED">
          <template #label>
            <span>已驳回</span>
            <el-badge :value="rejectedCount" class="tab-badge" type="info" />
          </template>
        </el-tab-pane>
        <el-tab-pane label="全部申请" name="ALL" />
      </el-tabs>

      <div class="selector-row">
        <span class="label">申请单：</span>
        <el-select
          v-model="selectedCorrectionId"
          placeholder="当前状态下没有申请单"
          clearable
          :disabled="visibleCorrections.length === 0 || listLoading"
          style="width: min(620px, 100%)"
          @change="loadSelectedCorrection"
        >
          <el-option
            v-for="item in visibleCorrections"
            :key="item.requestId"
            :label="formatSelectorLabel(item)"
            :value="item.requestId"
          />
        </el-select>
        <el-button :loading="listLoading" @click="loadCorrections">刷新列表</el-button>
      </div>
    </el-card>

    <el-card v-if="listError" shadow="never" class="state-card error-card" style="margin-top: 16px">
      <el-alert :title="listError" type="error" show-icon :closable="false" />
      <el-button class="retry-button" @click="loadCorrections">重新加载</el-button>
    </el-card>
    <el-card v-else-if="detailError" shadow="never" class="state-card error-card" style="margin-top: 16px">
      <el-alert :title="detailError" type="error" show-icon :closable="false" />
      <el-button class="retry-button" @click="loadSelectedCorrection">重新读取详情</el-button>
    </el-card>
    <el-card v-else-if="listLoading || detailLoading" shadow="never" class="state-card" style="margin-top: 16px" v-loading="true">
      <div class="loading-placeholder" />
    </el-card>
    <el-card v-else-if="!visibleCorrections.length" shadow="never" class="state-card" style="margin-top: 16px">
      <el-empty :description="allCorrections.length ? '当前状态下没有档案更正申请' : '服务端暂无档案更正申请'" />
    </el-card>
    <el-card v-else-if="!currentCorrection" shadow="never" class="state-card" style="margin-top: 16px">
      <el-empty description="请选择一条申请查看服务端详情" />
    </el-card>

    <div v-else class="main-body" style="margin-top: 16px">
      <el-card shadow="never" class="info-card">
        <el-descriptions title="申请单基本详情" border :column="3">
          <el-descriptions-item label="更正单号">
            <el-tag type="info">{{ currentCorrection.requestId }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="长辈姓名">
            <strong>{{ currentCorrection.elderName || '未提供' }}</strong>
            <span v-if="currentCorrection.archiveNo">（{{ currentCorrection.archiveNo }}）</span>
            <span v-else>（档案 #{{ currentCorrection.elderId }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="申请账号">
            #{{ currentCorrection.applicantUserId }}
            <span v-if="currentCorrection.applicantPhone">（{{ currentCorrection.applicantPhone }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(currentCorrection.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatDateTime(currentCorrection.reviewedAt) }}</el-descriptions-item>
          <el-descriptions-item label="申请状态">
            <el-tag :type="getStatusTag(currentCorrection.status)">{{ getStatusText(currentCorrection.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请事由说明" :span="3">
            <span>{{ currentCorrection.reason || '未提供' }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentCorrection.decisionReason" label="服务端审核意见" :span="3">
            {{ currentCorrection.decisionReason }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="diff-card" style="margin-top: 16px">
        <template #header>
          <div class="diff-header">
            <span class="header-title">档案字段基线与更正建议比对</span>
            <el-tag v-if="hasConflict" type="danger" effect="dark">检测到并发修改冲突</el-tag>
            <el-tag v-else type="success" effect="plain">快照基线一致</el-tag>
          </div>
        </template>

        <el-alert
          v-if="currentCorrection.blockCode"
          :title="`服务端阻断标记：${currentCorrection.blockCode}`"
          type="error"
          description="该申请存在服务端阻断状态。审核操作仍由服务端执行并校验；请先核对申请人授权及档案最新状态。"
          show-icon
          :closable="false"
          class="block-alert"
        />
        <el-alert
          v-else-if="hasConflict"
          title="并发一致性冲突"
          type="error"
          description="至少一个档案字段当前值与申请时的基线值不一致。批准操作已禁用，请核对冲突字段并填写驳回原因。"
          show-icon
          :closable="false"
          class="block-alert"
        />

        <el-table :data="currentCorrection.items" border stripe style="width: 100%" empty-text="服务端未返回更正字段">
          <el-table-column prop="fieldName" label="更正字段名称" width="180">
            <template #default="{ row }"><strong>{{ row.fieldName || row.fieldCode }}</strong></template>
          </el-table-column>
          <el-table-column label="申请时原值快照" min-width="220">
            <template #default="{ row }"><span class="baseline-text">{{ displayValue(row.baselineValue) }}</span></template>
          </el-table-column>
          <el-table-column label="档案当前值" min-width="220">
            <template #default="{ row }">
              <span :class="isItemConflict(row) ? 'conflict-text' : 'normal-db-text'">{{ displayValue(row.currentDbValue) }}</span>
              <el-tag v-if="isItemConflict(row)" type="danger" size="small" class="conflict-tag">已变更冲突</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="建议更正值" min-width="220">
            <template #default="{ row }"><span class="proposed-text">{{ displayValue(row.proposedValue) }}</span></template>
          </el-table-column>
          <el-table-column label="核验结果" width="130" align="center">
            <template #default="{ row }">
              <el-tag :type="isItemConflict(row) ? 'danger' : 'success'">
                {{ isItemConflict(row) ? '冲突阻断' : '基线一致' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="decision-footer">
          <el-button
            type="danger"
            plain
            :disabled="currentCorrection.status !== 'PENDING_REVIEW' || actionLoading"
            @click="openRejectModal"
          >
            驳回更正申请
          </el-button>
          <el-button
            type="success"
            :disabled="currentCorrection.status !== 'PENDING_REVIEW' || hasConflict || actionLoading"
            :loading="actionLoading && activeAction === 'approve'"
            @click="handleApprove"
          >
            {{ hasConflict ? '存在冲突，暂不能批准' : '核准并通过更正入档' }}
          </el-button>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="rejectModalVisible" title="驳回档案更正申请" width="460px">
      <el-form label-width="90px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actionLoading" @click="rejectModalVisible = false">取消</el-button>
        <el-button type="danger" :loading="actionLoading && activeAction === 'reject'" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api/adminApi'

interface CorrectionItem {
  fieldCode: string
  fieldName?: string
  baselineValue?: string | null
  currentDbValue?: string | null
  proposedValue?: string | null
  hasConflict?: boolean
}

interface CorrectionDetail {
  requestId: number
  elderId: number
  elderName?: string
  archiveNo?: string
  applicantUserId: number
  applicantPhone?: string
  status: string
  reason?: string
  blockCode?: string | null
  submittedAt?: string
  reviewedAt?: string | null
  decisionReason?: string | null
  items: CorrectionItem[]
}

type CorrectionTab = 'PENDING_REVIEW' | 'APPROVED_APPLIED' | 'REJECTED' | 'ALL'
type ReviewAction = 'approve' | 'reject'

const activeTab = ref<CorrectionTab>('PENDING_REVIEW')
const allCorrections = ref<CorrectionDetail[]>([])
const selectedCorrectionId = ref<number | undefined>()
const currentCorrection = ref<CorrectionDetail | null>(null)
const listLoading = ref(false)
const detailLoading = ref(false)
const listError = ref('')
const detailError = ref('')
const rejectModalVisible = ref(false)
const rejectReason = ref('')
const actionLoading = ref(false)
const activeAction = ref<ReviewAction | null>(null)

const visibleCorrections = computed(() => activeTab.value === 'ALL'
  ? allCorrections.value
  : allCorrections.value.filter(item => item.status === activeTab.value))
const pendingCount = computed(() => allCorrections.value.filter(item => item.status === 'PENDING_REVIEW').length)
const approvedCount = computed(() => allCorrections.value.filter(item => item.status === 'APPROVED_APPLIED').length)
const rejectedCount = computed(() => allCorrections.value.filter(item => item.status === 'REJECTED').length)
const hasConflict = computed(() => currentCorrection.value?.items.some(isItemConflict) ?? false)

function errorMessage(error: unknown) {
  return error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ') : '—'
}

function formatSelectorLabel(item: CorrectionDetail) {
  const elderLabel = item.elderName || `档案 #${item.elderId}`
  return `申请 #${item.requestId} - ${elderLabel}（账号 #${item.applicantUserId}）`
}

function displayValue(value?: string | null) {
  return value === null || value === undefined || value === '' ? '（空）' : value
}

function isItemConflict(item: CorrectionItem) {
  if (typeof item.hasConflict === 'boolean') return item.hasConflict
  return (item.baselineValue ?? '').trim() !== (item.currentDbValue ?? '').trim()
}

function getStatusTag(status: string) {
  if (status === 'APPROVED_APPLIED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING_REVIEW') return 'warning'
  return 'info'
}

function getStatusText(status: string) {
  if (status === 'APPROVED_APPLIED') return '已核准入档'
  if (status === 'REJECTED') return '已驳回'
  if (status === 'PENDING_REVIEW') return '待社区审核'
  return status || '未知状态'
}

async function loadCorrections(preferredId?: number) {
  listLoading.value = true
  listError.value = ''
  detailError.value = ''
  try {
    const response = await adminApi.getCorrectionList()
    allCorrections.value = Array.isArray(response.data) ? response.data as CorrectionDetail[] : []
    const selected = preferredId ?? selectedCorrectionId.value
    const preferred = visibleCorrections.value.find(item => item.requestId === selected)
      ?? visibleCorrections.value[0]
      ?? allCorrections.value[0]
    selectedCorrectionId.value = preferred?.requestId
    if (preferred) {
      await loadCorrectionDetail(preferred.requestId)
    } else {
      currentCorrection.value = null
    }
  } catch (error) {
    listError.value = `档案更正申请列表加载失败：${errorMessage(error)}`
    throw error
  } finally {
    listLoading.value = false
  }
}

async function loadCorrectionDetail(value = selectedCorrectionId.value) {
  detailError.value = ''
  if (value === undefined || value === null) {
    currentCorrection.value = null
    selectedCorrectionId.value = undefined
    return
  }
  selectedCorrectionId.value = value
  detailLoading.value = true
  currentCorrection.value = null
  try {
    const response = await adminApi.getCorrectionDetail(value)
    currentCorrection.value = response.data as CorrectionDetail
  } catch (error) {
    detailError.value = `更正申请 #${value} 详情加载失败：${errorMessage(error)}`
  } finally {
    detailLoading.value = false
  }
}

function loadSelectedCorrection(value?: number | string) {
  void loadCorrectionDetail(value === undefined || value === '' ? undefined : Number(value))
}

function handleTabChange() {
  if (!visibleCorrections.value.some(item => item.requestId === selectedCorrectionId.value)) {
    selectedCorrectionId.value = visibleCorrections.value[0]?.requestId
  }
  void loadCorrectionDetail(selectedCorrectionId.value)
}

async function submitReview(approved: boolean, reason?: string) {
  const requestId = currentCorrection.value?.requestId
  if (!requestId || actionLoading.value) return
  actionLoading.value = true
  activeAction.value = approved ? 'approve' : 'reject'
  let writeConfirmed = false
  try {
    await adminApi.reviewCorrection(requestId, approved, reason)
    writeConfirmed = true
    // 重新请求列表和详情，以服务端回读状态更新页面。
    activeTab.value = 'ALL'
    await loadCorrections(requestId)
    rejectModalVisible.value = false
    rejectReason.value = ''
    ElMessage.success(approved ? '服务端已确认批准，申请详情已回读更新' : '服务端已确认驳回，申请详情已回读更新')
  } catch (error) {
    ElMessage.error(writeConfirmed
      ? `服务端已确认审核操作，但回读状态失败：${errorMessage(error)}。请点击“刷新列表”确认最新状态。`
      : errorMessage(error))
  } finally {
    actionLoading.value = false
    activeAction.value = null
  }
}

async function handleApprove() {
  if (!currentCorrection.value) return
  if (hasConflict.value) {
    ElMessage.error('服务端详情显示基线冲突，暂不能批准')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批准更正申请 #${currentCorrection.value.requestId}？服务端将再次核验申请人授权和档案基线。`,
      '核准确认',
      { confirmButtonText: '批准生效', cancelButtonText: '取消', type: 'success' },
    )
  } catch {
    return
  }
  await submitReview(true)
}

function openRejectModal() {
  rejectReason.value = hasConflict.value
    ? '系统详情显示档案字段已偏离申请时基线，请核对后重新提交申请。'
    : ''
  rejectModalVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.error('请填写驳回原因')
    return
  }
  await submitReview(false, rejectReason.value.trim())
}

onMounted(() => {
  void loadCorrections().catch(() => undefined)
})
</script>

<style scoped>
.header-card,
.info-card,
.diff-card,
.state-card {
  border-radius: 12px;
}

.card-intro .title {
  margin-bottom: 6px;
  color: #1e293b;
  font-size: 18px;
  font-weight: 800;
}

.card-intro .desc {
  margin-bottom: 14px;
  color: #64748b;
  font-size: 13px;
}

.selector-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.selector-row .label {
  flex: 0 0 auto;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.tab-badge {
  margin-left: 6px;
}

.retry-button {
  margin-top: 14px;
}

.loading-placeholder {
  min-height: 220px;
}

.diff-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.diff-header .header-title {
  color: #1e293b;
  font-size: 15px;
  font-weight: 700;
}

.block-alert {
  margin-bottom: 16px;
}

.baseline-text {
  color: #64748b;
  font-size: 13px;
}

.normal-db-text {
  color: #1e293b;
  font-size: 13px;
  font-weight: 600;
}

.conflict-text {
  padding: 2px 6px;
  border-radius: 4px;
  background: #fee2e2;
  color: #dc2626;
  font-size: 13px;
  font-weight: 800;
}

.conflict-tag {
  margin-left: 8px;
}

.proposed-text {
  color: #0284c7;
  font-size: 13px;
  font-weight: 700;
}

.decision-footer {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 24px;
}
</style>
