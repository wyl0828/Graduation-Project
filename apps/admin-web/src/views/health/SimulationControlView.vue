<template>
  <div class="simulation-control-view">
    <div class="view-header">
      <div>
        <h2 class="view-title">IoT 体征仿真控制台</h2>
        <p class="view-subtitle">配置服务端仿真采样、读取真实运行状态，或提交一条仿真体征记录。</p>
      </div>
      <div class="header-status">
        <el-tag :type="statusTagType" effect="dark" size="large">
          服务端状态：{{ statusLabel }}
        </el-tag>
        <el-button :loading="statusLoading" :disabled="!targetElderId || actionLoading !== null" @click="refreshStatus">
          刷新状态
        </el-button>
      </div>
    </div>

    <el-alert v-if="archiveError" :title="archiveError" type="error" show-icon :closable="false" />
    <el-alert v-if="statusError" :title="statusError" type="error" show-icon :closable="false" class="message-alert" />
    <el-alert v-if="operationError" :title="operationError" type="warning" show-icon :closable="false" class="message-alert" />
    <el-alert
      v-if="vitalType === 'HEART_RATE'"
      title="当前服务端会把所有非血压指标的单位统一保存为 mmol/L；心率单位需后端修正后才能准确记录。"
      type="warning"
      show-icon
      :closable="false"
      class="message-alert"
    />

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="config-card">
          <template #header>
            <div class="card-header">
              <span class="card-title-text">仿真配置</span>
              <span v-if="communityId" class="community-context">当前社区 ID：{{ communityId }}</span>
            </div>
          </template>

          <el-skeleton v-if="elderLoading" :rows="3" animated />
          <el-alert v-else-if="!archiveError && elders.length === 0" title="当前可选档案列表中没有 ACTIVE 老人档案。" type="info" show-icon :closable="false" />

          <el-form v-else label-position="top">
            <el-form-item label="目标老人档案" required>
              <el-select
                v-model="targetElderId"
                filterable
                clearable
                :loading="elderLoading"
                :disabled="elderLoading || actionLoading !== null"
                placeholder="选择真实老人档案"
                style="width: 100%"
                @change="handleSelectionChange"
              >
                <el-option
                  v-for="elder in elders"
                  :key="elder.elderId"
                  :value="elder.elderId"
                  :label="elderOptionLabel(elder)"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="体征指标">
              <el-radio-group v-model="vitalType" :disabled="actionLoading !== null" @change="handleVitalTypeChange">
                <el-radio-button label="BLOOD_PRESSURE">血压</el-radio-button>
                <el-radio-button label="BLOOD_SUGAR">血糖</el-radio-button>
                <el-radio-button label="HEART_RATE">心率</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item :label="`主测量值基线（${unitLabel}）`">
              <el-slider v-model="baseValuePrimary" :min="primaryRange.min" :max="primaryRange.max" :step="primaryRange.step" show-input />
            </el-form-item>

            <el-form-item v-if="vitalType === 'BLOOD_PRESSURE'" label="舒张压基线（mmHg）">
              <el-slider v-model="baseValueSecondary" :min="40" :max="150" :step="1" show-input />
            </el-form-item>

            <el-form-item :label="`随机波动幅度（±${volatility} ${unitLabel}）`">
              <el-slider v-model="volatility" :min="volatilityRange.min" :max="volatilityRange.max" :step="volatilityRange.step" show-input />
            </el-form-item>

            <el-form-item label="自动采样间隔（秒）">
              <el-slider v-model="sampleIntervalSec" :min="5" :max="60" :step="5" show-input />
            </el-form-item>

            <el-form-item v-if="vitalType === 'BLOOD_PRESSURE'" label="随机采样附加异常值概率（%）">
              <el-slider v-model="abnormalInjectionRatePercent" :min="0" :max="50" :step="1" show-input />
            </el-form-item>
            <el-alert
              v-if="vitalType === 'BLOOD_PRESSURE' && abnormalInjectionRatePercent > 0"
              title="服务端可能按该概率对后续随机采样值叠加异常偏移；是否产生告警需以服务端告警记录为准。"
              type="info"
              :closable="false"
              class="config-note"
            />

            <div class="action-buttons-box">
              <el-button
                v-if="confirmedStatus === 'STOPPED'"
                type="success"
                size="large"
                style="width: 100%"
                :loading="actionLoading === 'START'"
                :disabled="!canOperate"
                @click="startSimulation"
              >启动服务端体征发生器</el-button>
              <el-button
                v-else-if="confirmedStatus === 'RUNNING'"
                type="danger"
                size="large"
                style="width: 100%"
                :loading="actionLoading === 'STOP'"
                :disabled="!canOperate"
                @click="stopSimulation"
              >停止服务端体征发生器</el-button>
              <el-button v-else type="primary" plain style="width: 100%" :disabled="!targetElderId || statusLoading || actionLoading !== null" @click="refreshStatus">
                先读取服务端状态
              </el-button>
            </div>
            <div v-if="!statusFresh && simulationStatus" class="stale-hint">上次确认状态已过期，当前操作按钮会在重新读取后启用。</div>
          </el-form>
        </el-card>

        <el-card shadow="never" class="inject-card" style="margin-top: 20px;">
          <template #header>
            <span class="card-title-text">提交单条仿真体征记录</span>
          </template>
          <p class="inject-desc">
            此操作会向服务端提交一条 <code>SIMULATED</code> 测量记录。只有响应返回有效体征记录 ID 后，本页才确认保存；告警结果请以预警页面的服务端记录为准。
          </p>
          <el-form label-position="top">
            <el-form-item :label="`主测量值（${unitLabel}）`" required>
              <el-input-number v-model="injectionPrimary" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item v-if="vitalType === 'BLOOD_PRESSURE'" label="舒张压（mmHg）" required>
              <el-input-number v-model="injectionSecondary" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-button
              type="danger"
              plain
              style="width: 100%"
              :loading="actionLoading === 'INJECT'"
              :disabled="!canOperate || injectionPrimary === undefined || (vitalType === 'BLOOD_PRESSURE' && injectionSecondary === undefined)"
              @click="handleInjectRecord"
            >提交体征记录</el-button>
          </el-form>
          <el-alert v-if="lastInjectedVitalId" class="inject-confirmation" type="success" show-icon :closable="false">
            <template #title>服务端已保存体征记录，ID：{{ lastInjectedVitalId }}</template>
          </el-alert>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="monitor-card">
          <template #header>
            <div class="card-header">
              <span class="card-title-text">服务端仿真状态</span>
              <span class="stream-stats">{{ selectedElderLabel }} · {{ vitalTypeLabel }}</span>
            </div>
          </template>

          <el-skeleton v-if="statusLoading" :rows="5" animated />
          <el-empty v-else-if="!targetElderId" description="请先从当前档案列表选择老人" />
          <el-empty v-else-if="!simulationStatus && !statusError" description="尚未读取服务端状态" />
          <el-descriptions v-else-if="simulationStatus" :column="1" border>
            <el-descriptions-item label="服务端状态">
              <el-tag :type="statusTagType">{{ statusLabel }}</el-tag>
              <span v-if="!statusFresh" class="stale-inline">（需重新核验）</span>
            </el-descriptions-item>
            <el-descriptions-item label="老人档案 ID">{{ simulationStatus.elderId }}</el-descriptions-item>
            <el-descriptions-item label="指标类型">{{ metricName(simulationStatus.vitalType) }}</el-descriptions-item>
            <el-descriptions-item label="采样间隔">{{ simulationStatus.sampleIntervalSec ?? '—' }} 秒</el-descriptions-item>
            <el-descriptions-item label="仿真来源记录累计数">{{ simulationStatus.generatedCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="本次配置启动时间">{{ formatDate(simulationStatus.startedAt) }}</el-descriptions-item>
            <el-descriptions-item label="最近仿真记录时间">{{ formatDate(simulationStatus.lastGeneratedAt) }}</el-descriptions-item>
            <el-descriptions-item label="最近一次成功读取">{{ formatDate(statusReadAt) }}</el-descriptions-item>
          </el-descriptions>

          <el-divider />
          <p class="monitor-note">此页面不生成本地测量流水，也不根据输入值推定告警。上方计数和时间均来自状态接口。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiError } from '../../api/http'
import { adminApi } from '../../api/adminApi'

type ElderArchive = {
  elderId: number
  archiveNo?: string | null
  name?: string | null
  age?: number | null
  status?: string | null
}

type ElderPage = {
  items: ElderArchive[]
  total: number
  pageSize: number
}

type SimulationStatus = {
  status: 'RUNNING' | 'STOPPED'
  elderId: number
  vitalType: string
  sampleIntervalSec?: number | null
  generatedCount?: number | null
  startedAt?: string | null
  lastGeneratedAt?: string | null
}

type VitalType = 'BLOOD_PRESSURE' | 'BLOOD_SUGAR' | 'HEART_RATE'
type SimulationAction = 'START' | 'STOP' | 'INJECT'

const elders = ref<ElderArchive[]>([])
const communityId = ref('')
const currentRole = ref('')
const elderLoading = ref(false)
const archiveError = ref('')

const targetElderId = ref<number | undefined>()
const vitalType = ref<VitalType>('BLOOD_PRESSURE')
const baseValuePrimary = ref(130)
const baseValueSecondary = ref(82)
const volatility = ref(4)
const sampleIntervalSec = ref(10)
const abnormalInjectionRatePercent = ref(0)
const injectionPrimary = ref<number | undefined>()
const injectionSecondary = ref<number | undefined>()

const simulationStatus = ref<SimulationStatus | null>(null)
const statusLoading = ref(false)
const statusFresh = ref(false)
const statusError = ref('')
const statusReadAt = ref('')
const operationError = ref('')
const actionLoading = ref<SimulationAction | null>(null)
const lastInjectedVitalId = ref<string | number>('')
let statusRequestSequence = 0

const unitLabel = computed(() => {
  if (vitalType.value === 'BLOOD_PRESSURE') return 'mmHg'
  if (vitalType.value === 'BLOOD_SUGAR') return 'mmol/L'
  return '服务端记录单位 mmol/L'
})

const primaryRange = computed(() => {
  if (vitalType.value === 'BLOOD_PRESSURE') return { min: 60, max: 250, step: 1 }
  if (vitalType.value === 'BLOOD_SUGAR') return { min: 1, max: 30, step: 0.1 }
  return { min: 30, max: 220, step: 1 }
})

const volatilityRange = computed(() => vitalType.value === 'BLOOD_SUGAR'
  ? { min: 0.1, max: 5, step: 0.1 }
  : { min: 1, max: 20, step: 0.5 })

const vitalTypeLabel = computed(() => metricName(vitalType.value))
const selectedElder = computed(() => elders.value.find(elder => elder.elderId === targetElderId.value) || null)
const selectedElderLabel = computed(() => selectedElder.value?.name || (targetElderId.value ? `老人 ID ${targetElderId.value}` : '未选择老人'))
const confirmedStatus = computed(() => statusFresh.value ? simulationStatus.value?.status || null : null)
const canOperate = computed(() => Boolean(targetElderId.value) && currentRole.value === 'COMMUNITY_ADMIN' && statusFresh.value && !statusLoading.value && actionLoading.value === null)

const statusLabel = computed(() => {
  if (statusLoading.value) return '读取中'
  if (!statusFresh.value) return simulationStatus.value ? '待重新核验' : '未知'
  return simulationStatus.value?.status === 'RUNNING' ? '运行中' : simulationStatus.value?.status === 'STOPPED' ? '已停止' : '未知'
})

const statusTagType = computed(() => {
  if (!statusFresh.value) return 'warning'
  return simulationStatus.value?.status === 'RUNNING' ? 'success' : 'info'
})

function apiErrorMessage(error: unknown) {
  const apiError = error as ApiError
  if (apiError?.status === 401) return '登录状态已失效，请重新登录后再操作。'
  if (apiError?.status === 403) return '当前账号没有仿真控制权限。'
  if (apiError?.status) return apiError.message || `服务端请求失败（HTTP ${apiError.status}）`
  if (error instanceof Error) return error.message
  return '服务暂不可用，请检查网络后重试。'
}

function metricName(value?: string | null) {
  switch (value) {
    case 'BLOOD_PRESSURE': return '血压'
    case 'BLOOD_SUGAR': return '血糖'
    case 'HEART_RATE': return '心率'
    default: return value || '—'
  }
}

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ') : '—'
}

function elderOptionLabel(elder: ElderArchive) {
  const parts = [elder.name || `老人 ${elder.elderId}`]
  if (elder.age !== null && elder.age !== undefined) parts.push(`${elder.age} 岁`)
  if (elder.archiveNo) parts.push(elder.archiveNo)
  return `${parts.join(' · ')}（ID: ${elder.elderId}）`
}

function requireAdminSession(data: unknown) {
  if (!data || typeof data !== 'object' || !('currentRole' in data) || !('communityId' in data)) {
    throw new Error('当前用户响应缺少角色或社区信息，无法读取社区档案。')
  }
  const user = data as { currentRole?: unknown; communityId?: unknown }
  if (user.currentRole !== 'COMMUNITY_ADMIN') {
    throw new Error('仿真启动和停止仅支持社区管理员账号。')
  }
  if (user.communityId === null || user.communityId === undefined || String(user.communityId).trim() === '') {
    throw new Error('当前管理员账号未分配社区 ID。')
  }
  currentRole.value = String(user.currentRole)
  communityId.value = String(user.communityId)
}

function requireElderPage(data: unknown): ElderPage {
  if (!data || typeof data !== 'object' || !('items' in data) || !('total' in data) || !('pageSize' in data)) {
    throw new Error('老人档案响应不符合 PageResult 格式，无法选择目标老人。')
  }
  const page = data as { items?: unknown; total?: unknown; pageSize?: unknown }
  if (!Array.isArray(page.items) || typeof page.total !== 'number' || typeof page.pageSize !== 'number' || page.pageSize <= 0) {
    throw new Error('老人档案分页缺少有效 items、total 或 pageSize。')
  }
  const items = page.items.filter((item): item is ElderArchive =>
    Boolean(item && typeof item === 'object' && 'elderId' in item && Number.isFinite(Number((item as ElderArchive).elderId))),
  )
  return { items, total: page.total, pageSize: page.pageSize }
}

async function fetchActiveElders() {
  const firstResponse = await adminApi.getElderList({ status: 'ACTIVE', page: 1, size: 100 })
  const firstPage = requireElderPage(firstResponse.data)
  const pageCount = Math.ceil(firstPage.total / firstPage.pageSize)
  if (pageCount > 200) throw new Error('ACTIVE 老人档案超过 20,000 条，无法在当前页面一次完整读取。')

  const items = [...firstPage.items]
  for (let page = 2; page <= pageCount; page += 1) {
    const response = await adminApi.getElderList({ status: 'ACTIVE', page, size: firstPage.pageSize })
    const result = requireElderPage(response.data)
    items.push(...result.items)
  }

  const uniqueById = new Map<number, ElderArchive>()
  for (const elder of items) {
    const elderId = Number(elder.elderId)
    if (elder.status && elder.status !== 'ACTIVE') continue
    uniqueById.set(elderId, { ...elder, elderId })
  }
  return [...uniqueById.values()]
}

async function loadElders() {
  elderLoading.value = true
  archiveError.value = ''
  try {
    const userResponse = await adminApi.getCurrentUser()
    requireAdminSession(userResponse.data)
    const activeElders = await fetchActiveElders()
    elders.value = activeElders
    if (!activeElders.some(elder => elder.elderId === targetElderId.value)) {
      targetElderId.value = activeElders[0]?.elderId
    }
  } catch (error) {
    archiveError.value = apiErrorMessage(error)
    elders.value = []
    targetElderId.value = undefined
    simulationStatus.value = null
    statusFresh.value = false
  } finally {
    elderLoading.value = false
  }
}

function requireSimulationStatus(data: unknown, expectedElderId: number, expectedVitalType: VitalType): SimulationStatus {
  if (!data || typeof data !== 'object' || !('status' in data) || !('elderId' in data) || !('vitalType' in data)) {
    throw new Error('仿真状态响应缺少老人 ID、指标或状态字段。')
  }
  const value = data as SimulationStatus
  if (Number(value.elderId) !== expectedElderId || value.vitalType !== expectedVitalType) {
    throw new Error('仿真状态响应与当前选择的老人或指标不一致。')
  }
  if (value.status !== 'RUNNING' && value.status !== 'STOPPED') {
    throw new Error(`服务端返回未知的仿真状态：${String(value.status)}`)
  }
  return { ...value, elderId: Number(value.elderId) }
}

async function requestStatus(elderId: number, type: VitalType) {
  const response = await adminApi.getSimulationStatus(elderId, type)
  return requireSimulationStatus(response.data, elderId, type)
}

async function refreshStatus() {
  const elderId = targetElderId.value
  const type = vitalType.value
  if (!elderId) {
    simulationStatus.value = null
    statusFresh.value = false
    statusError.value = ''
    return
  }

  const sequence = ++statusRequestSequence
  statusLoading.value = true
  statusError.value = ''
  try {
    const status = await requestStatus(elderId, type)
    if (sequence !== statusRequestSequence || elderId !== targetElderId.value || type !== vitalType.value) return
    simulationStatus.value = status
    statusFresh.value = true
    statusReadAt.value = new Date().toISOString().slice(0, 19)
  } catch (error) {
    if (sequence !== statusRequestSequence || elderId !== targetElderId.value || type !== vitalType.value) return
    statusFresh.value = false
    statusError.value = apiErrorMessage(error)
  } finally {
    if (sequence === statusRequestSequence) statusLoading.value = false
  }
}

async function loadPage() {
  await loadElders()
  if (targetElderId.value) await refreshStatus()
}

onMounted(() => { void loadPage() })

function invalidateStatus(message = '') {
  statusRequestSequence += 1
  statusLoading.value = false
  simulationStatus.value = null
  statusFresh.value = false
  statusReadAt.value = ''
  statusError.value = message
  operationError.value = ''
  lastInjectedVitalId.value = ''
}

function handleSelectionChange() {
  invalidateStatus()
  if (targetElderId.value) void refreshStatus()
}

function handleVitalTypeChange() {
  if (vitalType.value === 'BLOOD_PRESSURE') {
    baseValuePrimary.value = 130
    baseValueSecondary.value = 82
    volatility.value = 4
  } else if (vitalType.value === 'BLOOD_SUGAR') {
    baseValuePrimary.value = 6
    volatility.value = 0.5
  } else {
    baseValuePrimary.value = 75
    volatility.value = 3
  }
  injectionPrimary.value = undefined
  injectionSecondary.value = undefined
  invalidateStatus()
  if (targetElderId.value) void refreshStatus()
}

async function reconcileStatusAfterFailedAction(elderId: number, type: VitalType) {
  try {
    const status = await requestStatus(elderId, type)
    if (elderId === targetElderId.value && type === vitalType.value) {
      simulationStatus.value = status
      statusFresh.value = true
      statusReadAt.value = new Date().toISOString().slice(0, 19)
    }
  } catch {
    statusFresh.value = false
  }
}

async function changeSimulationState(action: 'START' | 'STOP') {
  const elderId = targetElderId.value
  const type = vitalType.value
  if (!elderId || actionLoading.value !== null) return

  actionLoading.value = action
  statusFresh.value = false
  statusError.value = ''
  operationError.value = ''
  try {
    if (action === 'START') {
      await adminApi.startSimulation({
        elderId,
        vitalType: type,
        sampleIntervalSec: sampleIntervalSec.value,
        baseValuePrimary: baseValuePrimary.value,
        baseValueSecondary: type === 'BLOOD_PRESSURE' ? baseValueSecondary.value : undefined,
        volatilityRange: volatility.value,
        abnormalInjectionRate: type === 'BLOOD_PRESSURE' ? abnormalInjectionRatePercent.value / 100 : 0,
      })
    } else {
      // Always include the selected ID; the controller otherwise defaults to elderId=1.
      await adminApi.stopSimulation(elderId, type)
    }

    const observed = await requestStatus(elderId, type)
    if (elderId !== targetElderId.value || type !== vitalType.value) {
      throw new Error('当前老人或指标已变化，本次状态回读未应用到新的选择。')
    }
    simulationStatus.value = observed
    statusFresh.value = true
    statusReadAt.value = new Date().toISOString().slice(0, 19)
    const expectedStatus = action === 'START' ? 'RUNNING' : 'STOPPED'
    if (observed.status !== expectedStatus) {
      throw new Error(`服务端详情回读为 ${observed.status}，未确认${action === 'START' ? '启动' : '停止'}成功。`)
    }
    ElMessage.success(action === 'START' ? '服务端状态已确认运行。' : '服务端状态已确认停止。')
  } catch (error) {
    operationError.value = apiErrorMessage(error)
    ElMessage.error(operationError.value)
    await reconcileStatusAfterFailedAction(elderId, type)
  } finally {
    actionLoading.value = null
  }
}

function startSimulation() {
  return changeSimulationState('START')
}

function stopSimulation() {
  return changeSimulationState('STOP')
}

function parseVitalId(value: unknown) {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) return value
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) return value
  return null
}

async function handleInjectRecord() {
  const elderId = targetElderId.value
  const type = vitalType.value
  const primary = injectionPrimary.value
  const secondary = injectionSecondary.value
  if (!elderId || primary === undefined || !Number.isFinite(primary)) {
    ElMessage.warning('请选择老人并填写有效的主测量值。')
    return
  }
  if (type === 'BLOOD_PRESSURE' && (secondary === undefined || !Number.isFinite(secondary))) {
    ElMessage.warning('血压记录需要填写舒张压。')
    return
  }
  if (!canOperate) {
    ElMessage.warning('请先读取当前老人和指标的服务端状态。')
    return
  }

  actionLoading.value = 'INJECT'
  operationError.value = ''
  lastInjectedVitalId.value = ''
  try {
    const response = await adminApi.injectSingleAbnormal({
      elderId,
      vitalType: type,
      valuePrimary: primary,
      valueSecondary: type === 'BLOOD_PRESSURE' ? secondary : undefined,
    })
    const vitalId = parseVitalId(response.data)
    if (vitalId === null) throw new Error('服务端未返回有效的体征记录 ID，无法确认记录已保存。')
    lastInjectedVitalId.value = vitalId
    injectionPrimary.value = undefined
    injectionSecondary.value = undefined
    ElMessage.success(`服务端已返回体征记录 ID ${vitalId}。告警结果请以预警页面的服务端记录为准。`)
    await refreshStatus()
  } catch (error) {
    operationError.value = apiErrorMessage(error)
    ElMessage.error(operationError.value)
  } finally {
    actionLoading.value = null
  }
}
</script>

<style scoped>
.simulation-control-view {
  padding: 8px 4px;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.view-title {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 22px;
  font-weight: bold;
}

.view-subtitle {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 12px;
}

.content-row {
  margin-top: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title-text {
  color: #0f172a;
  font-size: 16px;
  font-weight: bold;
}

.community-context,
.stream-stats {
  color: #64748b;
  font-size: 12px;
}

.action-buttons-box {
  margin-top: 24px;
}

.inject-card {
  border: 1px solid #fecaca;
  background-color: #fef2f2;
}

.inject-desc {
  margin: 0 0 14px;
  color: #7f1d1d;
  font-size: 13px;
  line-height: 1.6;
}

.inject-confirmation {
  margin-top: 14px;
}

.message-alert,
.config-note {
  margin-top: 12px;
}

.config-note {
  margin-top: 0;
  margin-bottom: 12px;
}

.stale-hint,
.stale-inline {
  color: #b45309;
  font-size: 12px;
}

.stale-hint {
  margin-top: 8px;
}

.stale-inline {
  margin-left: 8px;
}

.monitor-note {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 767px) {
  .view-header,
  .header-status {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
