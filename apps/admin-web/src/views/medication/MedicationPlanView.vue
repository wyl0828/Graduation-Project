<template>
  <div class="medication-plan-view">
    <div class="view-header">
      <div>
        <h2 class="view-title">💊 社区长辈用药监视与电话关怀</h2>
        <p class="view-subtitle">超时任务来自当前账号授权清单；计划和遵从率按当前社区档案逐人读取。</p>
      </div>
      <div class="header-actions">
        <el-tag type="success" size="large" effect="dark">
          当前社区有计划长辈近 30 天任务加权打卡率：{{ communityComplianceText }}
        </el-tag>
        <el-tag type="warning" size="large" effect="dark">
          待电话关怀：{{ timeoutLoading ? '读取中…' : timeoutLoadError ? '读取失败' : `${pendingTimeoutList.length} 项` }}
        </el-tag>
        <el-button :loading="refreshing" @click="refreshPageData">刷新服务端数据</el-button>
      </div>
    </div>

    <el-alert
      v-if="timeoutLoadError"
      class="load-alert"
      type="error"
      :title="`超时任务读取失败：${timeoutLoadError}`"
      :closable="false"
      show-icon
    />

    <el-card class="timeout-care-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title-text">🔔 当前授权范围内的超时未打卡任务</span>
          <el-tag type="warning" size="small">以服务端任务状态为准</el-tag>
        </div>
      </template>

      <el-table
        v-loading="timeoutLoading"
        :data="pendingTimeoutList"
        border
        stripe
        style="width: 100%"
        empty-text="当前没有可关怀的超时任务"
      >
        <el-table-column prop="elderName" label="长辈姓名" width="130">
          <template #default="scope">{{ scope.row.elderName || `老人 ${scope.row.elderId}` }}</template>
        </el-table-column>
        <el-table-column label="未打卡药品" min-width="220">
          <template #default="scope">
            <el-tag type="danger" size="small">{{ scope.row.categoryTag || '用药任务' }}</el-tag>
            <span class="medicine-name">{{ scope.row.medicineName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dosage" label="计划剂量" width="120">
          <template #default="scope">{{ scope.row.dosage || '—' }}</template>
        </el-table-column>
        <el-table-column label="计划时段" min-width="170">
          <template #default="scope">
            {{ formatTime(scope.row.scheduledTime) }}{{ scope.row.timeLabel ? ` · ${scope.row.timeLabel}` : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="taskDate" label="任务日期" width="120">
          <template #default="scope">{{ scope.row.taskDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="服务端状态" width="160">
          <template #default="scope">
            <el-tag type="warning">{{ taskStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="scope">
            <el-button
              type="primary"
              size="small"
              :disabled="careActionLoading"
              @click="handleOpenCareModal(scope.row)"
            >
              登记电话关怀
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-alert
      v-if="planLoadError"
      class="load-alert"
      type="error"
      :title="`用药计划读取失败：${planLoadError}`"
      :closable="false"
      show-icon
    />

    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title-text">📋 当前授权范围内的用药计划</span>
          <div class="filter-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索长辈姓名 / 药品名称"
              prefix-icon="Search"
              style="width: 260px"
              clearable
            />
            <el-tag type="info">{{ plansLoading ? '读取中…' : `${planList.length} 条计划` }}</el-tag>
          </div>
        </div>
      </template>

      <el-table
        v-loading="plansLoading"
        :data="filteredPlanList"
        border
        stripe
        style="width: 100%"
        empty-text="当前授权范围内没有用药计划"
      >
        <el-table-column prop="elderName" label="长辈姓名" width="130">
          <template #default="scope">{{ scope.row.elderName || `老人 ${scope.row.elderId}` }}</template>
        </el-table-column>
        <el-table-column label="药品" min-width="220">
          <template #default="scope">
            <el-tag v-if="scope.row.categoryTag" type="warning" size="small">{{ scope.row.categoryTag }}</el-tag>
            <span class="medicine-name">{{ scope.row.medicineName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dosage" label="单次剂量" width="130">
          <template #default="scope">{{ scope.row.dosage || '—' }}</template>
        </el-table-column>
        <el-table-column label="每日提醒时段" min-width="190">
          <template #default="scope">{{ reminderTimeText(scope.row.reminderTimes) }}</template>
        </el-table-column>
        <el-table-column prop="currentVersionNo" label="当前版本" width="110">
          <template #default="scope">
            <el-tag size="small">{{ scope.row.currentVersionNo ? `v${scope.row.currentVersionNo}` : '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="complianceRate" label="长辈近 30 天打卡率" width="155">
          <template #default="scope">
            <span class="compliance-rate">{{ rateText(scope.row.complianceRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划状态" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ planStatusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本历史" width="130" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="handleOpenHistory(scope.row)">
              查看版本履历
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="careModalVisible" title="登记电话关怀" width="520px">
      <div v-if="currentCareItem" class="care-summary">
        <p><strong>长辈：</strong>{{ currentCareItem.elderName || `老人 ${currentCareItem.elderId}` }}</p>
        <p><strong>未打卡药品：</strong>{{ currentCareItem.medicineName }}（{{ currentCareItem.dosage || '剂量未提供' }}）</p>
        <p><strong>任务时段：</strong>{{ currentCareItem.taskDate || '—' }} {{ formatTime(currentCareItem.scheduledTime) }}</p>
      </div>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="提交会将服务端任务状态改为 CONFIRMED。确认已完成电话联系后再操作。"
        description="服务端目前没有关怀备注的持久化与回读字段；本操作仅更新任务状态。页面会重新读取超时清单，只有确认该任务已消失后才移除待办。"
      />
      <template #footer>
        <el-button :disabled="careActionLoading" @click="careModalVisible = false">取消</el-button>
        <el-button type="primary" :loading="careActionLoading" @click="handleSubmitCare">
          确认已完成电话关怀
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyDrawerVisible" title="用药计划版本历史" size="520px">
      <div v-if="currentHistoryPlan" class="history-drawer-content">
        <div class="drawer-header-box">
          <span class="med-name">{{ currentHistoryPlan.medicineName }}</span>
          <span class="med-sub">长辈：{{ currentHistoryPlan.elderName || `老人 ${currentHistoryPlan.elderId}` }}</span>
        </div>

        <el-alert
          v-if="historyLoadError"
          class="history-alert"
          type="error"
          :title="historyLoadError"
          :closable="false"
          show-icon
        />

        <el-skeleton v-if="historyLoading" :rows="5" animated />
        <el-empty v-else-if="!historyLoadError && historyVersions.length === 0" description="服务端没有返回版本记录" />
        <el-timeline v-else-if="!historyLoadError" class="history-timeline">
          <el-timeline-item
            v-for="version in historyVersions"
            :key="version.versionId"
            :timestamp="formatDateTime(version.createdAt)"
            placement="top"
            :type="version.versionNo === currentHistoryPlan.currentVersionNo ? 'primary' : 'info'"
          >
            <el-card shadow="never" class="version-card">
              <div class="ver-head">
                <span class="ver-no">版本号：v{{ version.versionNo }}</span>
                <el-tag
                  size="small"
                  :type="version.versionNo === currentHistoryPlan.currentVersionNo ? 'success' : 'info'"
                >
                  {{ version.versionNo === currentHistoryPlan.currentVersionNo ? '当前版本' : '历史版本' }}
                </el-tag>
              </div>
              <div class="ver-detail">
                <p><strong>药品与剂量：</strong>{{ version.medicineName }}，{{ version.dosage || '—' }}</p>
                <p><strong>提醒时段：</strong>{{ reminderTimeText(version.reminderTimes) }}</p>
                <p><strong>频次：</strong>{{ version.frequencyType || '—' }}</p>
                <p><strong>生效日期：</strong>{{ version.startDate || '—' }}{{ version.endDate ? ` 至 ${version.endDate}` : ' 起' }}</p>
                <p><strong>调整原因：</strong>{{ version.changeReason || '—' }}</p>
                <p class="version-creator">操作人：{{ version.creatorName || '—' }}</p>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiError } from '../../api/http'
import { adminApi } from '../../api/adminApi'

interface ReminderTime {
  reminderTime?: string | null
  timeLabel?: string | null
}

interface MedicationPlan {
  planId: number
  elderId: number
  elderName?: string | null
  medicineName: string
  dosage?: string | null
  usageMethod?: string | null
  categoryTag?: string | null
  status: string
  currentVersionNo?: number | null
  reminderTimes?: ReminderTime[] | null
  complianceRate?: number | null
}

interface MedicationCompliance {
  elderId: number
  expectedDosesCount: number
  confirmedDosesCount: number
  complianceRate: number
}

interface ElderPage {
  items: Array<{ elderId: number; name: string | null; status: string | null }>
  pageSize: number
  total: number
}

interface MedicationTask {
  taskId: number
  elderId: number
  elderName?: string | null
  planId?: number | null
  taskDate?: string | null
  scheduledTime?: string | null
  timeLabel?: string | null
  medicineName: string
  dosage?: string | null
  categoryTag?: string | null
  status: string
}

interface MedicationPlanVersion {
  versionId: number
  planId: number
  versionNo: number
  medicineName: string
  dosage?: string | null
  frequencyType?: string | null
  startDate?: string | null
  endDate?: string | null
  changeReason?: string | null
  creatorName?: string | null
  createdAt?: string | null
  reminderTimes?: ReminderTime[] | null
}

const searchKeyword = ref('')
const planList = ref<MedicationPlan[]>([])
const pendingTimeoutList = ref<MedicationTask[]>([])
const complianceList = ref<MedicationCompliance[]>([])
const plansLoading = ref(false)
const timeoutLoading = ref(false)
const planLoadError = ref('')
const timeoutLoadError = ref('')
const refreshing = computed(() => plansLoading.value || timeoutLoading.value)

const filteredPlanList = computed(() => {
  const keyword = searchKeyword.value.trim().toLocaleLowerCase()
  if (!keyword) return planList.value
  return planList.value.filter(plan =>
    (plan.elderName || '').toLocaleLowerCase().includes(keyword)
    || plan.medicineName.toLocaleLowerCase().includes(keyword)
    || (plan.categoryTag || '').toLocaleLowerCase().includes(keyword),
  )
})

const communityComplianceText = computed(() => {
  const expected = complianceList.value.reduce((sum, item) => sum + item.expectedDosesCount, 0)
  const confirmed = complianceList.value.reduce((sum, item) => sum + item.confirmedDosesCount, 0)
  if (expected <= 0) return '暂无可统计任务'
  return `${((confirmed / expected) * 100).toFixed(1)}%`
})

const careModalVisible = ref(false)
const careActionLoading = ref(false)
const currentCareItem = ref<MedicationTask | null>(null)

const historyDrawerVisible = ref(false)
const historyLoading = ref(false)
const historyLoadError = ref('')
const historyVersions = ref<MedicationPlanVersion[]>([])
const currentHistoryPlan = ref<MedicationPlan | null>(null)

function apiErrorMessage(error: unknown) {
  const apiError = error as ApiError
  if (apiError?.status === 401) return '登录状态已失效，请重新登录后再操作。'
  if (apiError?.status === 409) return '服务端记录状态已变化，请刷新后重试。'
  if (apiError?.status) return apiError.message || `服务端请求失败（HTTP ${apiError.status}）`
  if (error instanceof Error) return error.message
  return '服务暂不可用，请检查网络后重试。'
}

function requirePositiveId(value: unknown, name: string) {
  const id = Number(value)
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error(`${name}缺少有效 ID，无法展示服务端数据。`)
  return id
}

function requireElderPage(data: unknown): ElderPage {
  if (!data || typeof data !== 'object') throw new Error('老人档案接口没有返回分页数据。')
  const page = data as { items?: unknown; pageSize?: unknown; total?: unknown }
  if (!Array.isArray(page.items) || typeof page.pageSize !== 'number' || page.pageSize <= 0 || typeof page.total !== 'number') {
    throw new Error('老人档案分页响应缺少有效 items、pageSize 或 total。')
  }
  const items = page.items.map((item, index) => {
    if (!item || typeof item !== 'object') throw new Error(`老人档案第 ${index + 1} 条记录格式无效。`)
    const elder = item as Record<string, unknown>
    return {
      elderId: requirePositiveId(elder.elderId, '老人档案'),
      name: typeof elder.name === 'string' ? elder.name : null,
      status: typeof elder.status === 'string' ? elder.status : null,
    }
  })
  return { items, pageSize: page.pageSize, total: page.total }
}

function requireMedicationPlans(data: unknown, elderId: number): MedicationPlan[] {
  if (!Array.isArray(data)) throw new Error(`老人 ${elderId} 的用药计划响应不是列表。`)
  return data.map((item, index) => {
    if (!item || typeof item !== 'object') throw new Error(`老人 ${elderId} 的第 ${index + 1} 条计划格式无效。`)
    const raw = item as Record<string, unknown>
    const planId = requirePositiveId(raw.planId, '用药计划')
    const rowElderId = requirePositiveId(raw.elderId, '用药计划')
    if (rowElderId !== elderId || typeof raw.medicineName !== 'string' || typeof raw.status !== 'string') {
      throw new Error(`用药计划 ${planId} 缺少药品、状态或老人 ID 与请求不一致。`)
    }
    return {
      ...(raw as unknown as MedicationPlan),
      planId,
      elderId: rowElderId,
      elderName: typeof raw.elderName === 'string' ? raw.elderName : null,
      reminderTimes: Array.isArray(raw.reminderTimes) ? raw.reminderTimes as ReminderTime[] : [],
    }
  })
}

function requireCompliance(data: unknown, expectedElderId: number): MedicationCompliance {
  if (!data || typeof data !== 'object') throw new Error(`老人 ${expectedElderId} 的遵从率响应格式无效。`)
  const raw = data as Record<string, unknown>
  const elderId = requirePositiveId(raw.elderId, '遵从率')
  const expected = Number(raw.expectedDosesCount)
  const confirmed = Number(raw.confirmedDosesCount)
  const rate = Number(raw.complianceRate)
  if (
    elderId !== expectedElderId
    || !Number.isFinite(expected) || expected < 0
    || !Number.isFinite(confirmed) || confirmed < 0
    || !Number.isFinite(rate) || rate < 0 || rate > 100
  ) {
    throw new Error(`老人 ${expectedElderId} 的遵从率响应字段无效或对象不匹配。`)
  }
  return { elderId, expectedDosesCount: expected, confirmedDosesCount: confirmed, complianceRate: rate }
}

function requireTimeoutTasks(data: unknown): MedicationTask[] {
  if (!Array.isArray(data)) throw new Error('超时任务接口响应不是列表。')
  return data.map((item, index) => {
    if (!item || typeof item !== 'object') throw new Error(`超时任务第 ${index + 1} 条记录格式无效。`)
    const raw = item as Record<string, unknown>
    const taskId = requirePositiveId(raw.taskId, '超时任务')
    const elderId = requirePositiveId(raw.elderId, '超时任务')
    if (typeof raw.medicineName !== 'string' || typeof raw.status !== 'string') {
      throw new Error(`超时任务 ${taskId} 缺少药品名称或任务状态。`)
    }
    return {
      ...(raw as unknown as MedicationTask),
      taskId,
      elderId,
      elderName: typeof raw.elderName === 'string' ? raw.elderName : null,
    }
  })
}

function requireMedicationVersions(data: unknown, expectedPlanId: number): MedicationPlanVersion[] {
  if (!Array.isArray(data)) throw new Error('用药计划版本接口响应不是列表。')
  return data.map((item, index) => {
    if (!item || typeof item !== 'object') throw new Error(`版本历史第 ${index + 1} 条记录格式无效。`)
    const raw = item as Record<string, unknown>
    const versionId = requirePositiveId(raw.versionId, '用药计划版本')
    const planId = requirePositiveId(raw.planId, '用药计划版本')
    const versionNo = Number(raw.versionNo)
    if (planId !== expectedPlanId || !Number.isSafeInteger(versionNo) || versionNo <= 0 || typeof raw.medicineName !== 'string') {
      throw new Error(`用药计划版本 ${versionId} 与当前计划不匹配或字段无效。`)
    }
    return {
      ...(raw as unknown as MedicationPlanVersion),
      versionId,
      planId,
      versionNo,
      reminderTimes: Array.isArray(raw.reminderTimes) ? raw.reminderTimes as ReminderTime[] : [],
    }
  })
}

async function fetchPlansForElder(elder: Pick<MedicationTask, 'elderId' | 'elderName'>) {
  const response = await adminApi.getMedicationPlans(elder.elderId)
  const plans = requireMedicationPlans(response.data, elder.elderId)
  if (plans.length === 0) return { plans, compliance: null }

  const complianceResponse = await adminApi.getMedicationCompliance(elder.elderId, 30)
  const compliance = requireCompliance(complianceResponse.data, elder.elderId)
  return {
    plans: plans.map(plan => ({
      ...plan,
      elderName: plan.elderName || elder.elderName || null,
      complianceRate: compliance.complianceRate,
    })),
    compliance,
  }
}

async function loadPlans() {
  plansLoading.value = true
  planLoadError.value = ''
  try {
    const eldersById = new Map<number, Pick<MedicationTask, 'elderId' | 'elderName'>>()
    const pageSize = 100
    for (let pageNo = 1; ; pageNo++) {
      const response = await adminApi.getElderList({ page: pageNo, size: pageSize })
      const page = requireElderPage(response.data)
      for (const elder of page.items) {
        eldersById.set(elder.elderId, { elderId: elder.elderId, elderName: elder.name })
      }
      if (pageNo * page.pageSize >= page.total) break
      if (page.items.length === 0 || pageNo >= 100) throw new Error('社区档案分页未完整返回，停止计算计划统计。')
    }
    const elders = [...eldersById.values()]
    const plans: MedicationPlan[] = []
    const complianceByElder = new Map<number, MedicationCompliance>()

    for (let index = 0; index < elders.length; index += 6) {
      const group = elders.slice(index, index + 6)
      const results = await Promise.all(group.map(fetchPlansForElder))
      for (const result of results) {
        plans.push(...result.plans)
        if (result.compliance) complianceByElder.set(result.compliance.elderId, result.compliance)
      }
    }

    planList.value = plans
    complianceList.value = [...complianceByElder.values()]
    return true
  } catch (error) {
    planList.value = []
    complianceList.value = []
    planLoadError.value = apiErrorMessage(error)
    return false
  } finally {
    plansLoading.value = false
  }
}

async function loadTimeoutTasks() {
  timeoutLoading.value = true
  timeoutLoadError.value = ''
  try {
    const response = await adminApi.getCommunityTimeoutMedications()
    pendingTimeoutList.value = requireTimeoutTasks(response.data)
    return true
  } catch (error) {
    timeoutLoadError.value = apiErrorMessage(error)
    return false
  } finally {
    timeoutLoading.value = false
  }
}

async function refreshPageData() {
  await Promise.all([loadTimeoutTasks(), loadPlans()])
}

function handleOpenCareModal(task: MedicationTask) {
  currentCareItem.value = task
  careModalVisible.value = true
}

async function handleSubmitCare() {
  const task = currentCareItem.value
  if (!task || careActionLoading.value) return

  careActionLoading.value = true
  let writeReturned = false
  try {
    await adminApi.recordMedicationPhoneCare(task.taskId)
    writeReturned = true

    const response = await adminApi.getCommunityTimeoutMedications()
    const refreshedTasks = requireTimeoutTasks(response.data)
    pendingTimeoutList.value = refreshedTasks
    timeoutLoadError.value = ''

    if (refreshedTasks.some(item => item.taskId === task.taskId)) {
      const message = '服务端写入请求已返回，但回读清单仍包含该任务；待办保留，请刷新后核验。'
      timeoutLoadError.value = message
      ElMessage.error(message)
      return
    }

    careModalVisible.value = false
    currentCareItem.value = null
    ElMessage.success('服务端已将任务更新为 CONFIRMED，且回读清单确认该待办已移出。')
  } catch (error) {
    const message = apiErrorMessage(error)
    if (writeReturned) {
      timeoutLoadError.value = `写入请求已返回，但无法回读确认；待办保留。${message}`
      ElMessage.error(timeoutLoadError.value)
    } else {
      ElMessage.error(`电话关怀写入失败，待办保留。${message}`)
    }
  } finally {
    careActionLoading.value = false
  }
}

async function handleOpenHistory(plan: MedicationPlan) {
  currentHistoryPlan.value = plan
  historyVersions.value = []
  historyLoadError.value = ''
  historyDrawerVisible.value = true
  historyLoading.value = true
  try {
    const response = await adminApi.getMedicationPlanVersions(plan.planId)
    historyVersions.value = requireMedicationVersions(response.data, plan.planId)
  } catch (error) {
    historyLoadError.value = apiErrorMessage(error)
  } finally {
    historyLoading.value = false
  }
}

function formatTime(value?: string | null) {
  return value ? value.slice(0, 5) : '—'
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function reminderTimeText(times?: ReminderTime[] | null) {
  if (!times?.length) return '—'
  return times.map(item => {
    const time = item.reminderTime ? formatTime(item.reminderTime) : ''
    return [time, item.timeLabel].filter(Boolean).join(' ')
  }).filter(Boolean).join('、') || '—'
}

function rateText(rate?: number | null) {
  return typeof rate === 'number' && Number.isFinite(rate) ? `${rate.toFixed(1)}%` : '—'
}

function taskStatusLabel(status?: string) {
  if (status === 'OVER_WINDOW_UNCONFIRMED') return '超时未确认'
  if (status === 'PENDING') return '待打卡'
  return status || '未知'
}

function planStatusLabel(status?: string) {
  if (status === 'ACTIVE') return '在服'
  if (status === 'INACTIVE') return '已停用'
  return status || '未知'
}

onMounted(() => { void refreshPageData() })
</script>

<style scoped>
.medication-plan-view {
  padding: 8px 4px;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.view-title {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 22px;
  font-weight: 700;
}

.view-subtitle {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.header-actions,
.filter-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.timeout-care-card {
  margin-bottom: 20px;
  border: 1px solid #fde68a;
  border-radius: 12px;
  background-color: #fffdf9;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.card-title-text {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.medicine-name {
  margin-left: 8px;
  color: #0f172a;
  font-weight: 600;
}

.compliance-rate {
  color: #059669;
  font-weight: 700;
}

.load-alert {
  margin-bottom: 16px;
}

.care-summary {
  margin-bottom: 18px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
}

.care-summary p {
  margin: 4px 0;
}

.history-drawer-content .drawer-header-box {
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.drawer-header-box .med-name {
  display: block;
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

.drawer-header-box .med-sub {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 14px;
}

.history-alert {
  margin-top: 16px;
}

.history-timeline {
  margin-top: 24px;
}

.version-card {
  border-radius: 8px;
  background-color: #f8fafc;
}

.ver-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.ver-no {
  color: #0f172a;
  font-weight: 700;
}

.ver-detail p {
  margin: 4px 0;
  color: #334155;
  font-size: 13px;
}

.ver-detail .version-creator {
  margin-top: 10px;
  color: #64748b;
}

@media (max-width: 1000px) {
  .view-header,
  .header-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .card-header,
  .filter-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
