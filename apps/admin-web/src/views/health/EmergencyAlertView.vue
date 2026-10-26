<template>
  <div class="emergency-alert-view">
    <div class="view-header">
      <div>
        <h2 class="view-title">SOS 与健康告警处置</h2>
        <p class="view-subtitle">数据与处置结果均来自当前登录管理员可访问的服务端记录。</p>
      </div>
      <el-button :loading="sosLoading || alertLoading" @click="refreshAll">刷新</el-button>
    </div>

    <el-alert v-if="sosLoadError" :title="sosLoadError" type="error" show-icon :closable="false" />
    <el-card shadow="never" class="box-card">
      <template #header>
        <div class="card-header">
          <span class="card-title-text">活动 SOS（{{ activeSosList.length }}）</span>
          <el-tag type="danger" effect="dark">仅展示服务端状态为 ACCEPTED 的记录</el-tag>
        </div>
      </template>
      <el-table v-loading="sosLoading" :data="activeSosList" border stripe empty-text="没有可访问的活动 SOS 记录">
        <el-table-column label="SOS 编号" min-width="110">
          <template #default="scope">SOS-ID {{ scope.row.sosId }}</template>
        </el-table-column>
        <el-table-column prop="elderName" label="长辈" width="120" />
        <el-table-column label="年龄" width="80">
          <template #default="scope">{{ scope.row.elderAge ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="elderAddress" label="服务地址" min-width="200" />
        <el-table-column prop="statusDesc" label="状态" min-width="110" />
        <el-table-column label="责任网格员" min-width="150">
          <template #default="scope">
            {{ scope.row.assigneeName || '尚未派单' }}<span v-if="scope.row.assigneePhone">（{{ scope.row.assigneePhone }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="紧急联系人" min-width="150">
          <template #default="scope">
            {{ scope.row.contactName || '—' }}<span v-if="scope.row.contactPhone">（{{ scope.row.contactPhone }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" min-width="170">
          <template #default="scope">{{ formatDate(scope.row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openSosDispatch(scope.row)">派单 / 改派</el-button>
            <el-button size="small" type="success" @click="openSosClose(scope.row)">结案</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-alert v-if="cancelledLoadError" :title="cancelledLoadError" type="error" show-icon :closable="false" />
    <el-card shadow="never" class="box-card" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span class="card-title-text">已取消 SOS 记录（{{ cancelledSosList.length }}）</span>
          <el-tag type="warning">服务端暂无单独的回访登记接口</el-tag>
        </div>
      </template>
      <el-table v-loading="sosLoading" :data="cancelledSosList" border stripe empty-text="没有可访问的已取消 SOS 记录">
        <el-table-column label="SOS 编号" min-width="110">
          <template #default="scope">SOS-ID {{ scope.row.sosId }}</template>
        </el-table-column>
        <el-table-column prop="elderName" label="长辈" width="120" />
        <el-table-column prop="cancelReason" label="取消原因" min-width="240" />
        <el-table-column label="触发时间" min-width="170">
          <template #default="scope">{{ formatDate(scope.row.occurredAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-alert v-if="alertLoadError" :title="alertLoadError" type="error" show-icon :closable="false" />
    <el-card shadow="never" class="box-card" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span class="card-title-text">健康告警（{{ filteredAlertList.length }}）</span>
          <el-radio-group v-model="alertFilter" size="small" @change="loadHealthAlerts">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="PENDING">待处理</el-radio-button>
            <el-radio-button label="CLOSED">已结案</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table v-loading="alertLoading" :data="filteredAlertList" border stripe empty-text="没有符合条件的健康告警">
        <el-table-column label="告警编号" min-width="120">
          <template #default="scope">ALT-ID {{ scope.row.alertId }}</template>
        </el-table-column>
        <el-table-column prop="elderName" label="长辈" width="110" />
        <el-table-column prop="metricName" label="指标" min-width="140" />
        <el-table-column label="实测值 / 阈值" min-width="160">
          <template #default="scope">{{ scope.row.actualValue ?? '—' }} / {{ scope.row.thresholdValue ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="alertLevel" label="级别" width="100" />
        <el-table-column prop="alertCategory" label="告警类别" min-width="150" />
        <el-table-column label="创建时间" min-width="170">
          <template #default="scope">{{ formatDate(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="处置结果" min-width="190">
          <template #default="scope">{{ scope.row.handlingResult || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status === 'PENDING'" type="primary" size="small" @click="openAlertResolve(scope.row)">登记处置并结案</el-button>
            <span v-else>{{ scope.row.handlerName || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="sosDispatchVisible" title="SOS 派单 / 改派" width="520px">
      <el-form label-position="top">
        <el-form-item label="SOS 记录">
          <el-input :model-value="selectedSos ? `SOS-ID ${selectedSos.sosId} · ${selectedSos.elderName || '未知长辈'}` : ''" disabled />
        </el-form-item>
        <el-form-item label="网格员账号 ID（服务端将校验社区与有效角色）" required>
          <el-input-number v-model="sosAssigneeId" :min="1" :precision="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="派单原因">
          <el-input v-model="sosDispatchReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sosDispatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="sosActionLoading" @click="submitSosDispatch">提交派单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sosCloseVisible" title="SOS 结案" width="520px">
      <el-form label-position="top">
        <el-form-item label="SOS 记录">
          <el-input :model-value="selectedSos ? `SOS-ID ${selectedSos.sosId} · ${selectedSos.elderName || '未知长辈'}` : ''" disabled />
        </el-form-item>
        <el-form-item label="现场处置 / 回访结论（至少 5 个字符）" required>
          <el-input v-model="sosResultSummary" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
        <el-checkbox v-model="sosFalseAlarm">按误报 / 误触结案</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="sosCloseVisible = false">取消</el-button>
        <el-button type="success" :loading="sosActionLoading" @click="submitSosClose">提交结案</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="alertResolveVisible" title="健康告警处置" width="520px">
      <el-form label-position="top">
        <el-form-item label="告警对象与指标">
          <el-input :model-value="selectedAlert ? `${selectedAlert.elderName || '—'} · ${selectedAlert.metricName || selectedAlert.alertCategory || '—'}` : ''" disabled />
        </el-form-item>
        <el-form-item label="处置说明" required>
          <el-input v-model="alertResolution" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="alertResolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="alertActionLoading" @click="submitAlertResolve">提交处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiError } from '../../api/http'
import { adminApi } from '../../api/adminApi'

type SosEvent = {
  sosId: number
  elderName?: string | null
  elderAge?: number | null
  elderAddress?: string | null
  status?: string
  statusDesc?: string | null
  assigneeId?: number | null
  assigneeName?: string | null
  assigneePhone?: string | null
  contactName?: string | null
  contactPhone?: string | null
  occurredAt?: string | null
  cancelReason?: string | null
}
type HealthAlert = {
  alertId: number
  elderName?: string | null
  alertCategory?: string | null
  alertLevel?: string | null
  status: string
  actualValue?: number | string | null
  thresholdValue?: number | string | null
  metricName?: string | null
  createdAt?: string | null
  handlerName?: string | null
  handlingResult?: string | null
}

const activeSosList = ref<SosEvent[]>([])
const cancelledSosList = ref<SosEvent[]>([])
const alertList = ref<HealthAlert[]>([])
const alertFilter = ref<'ALL' | 'PENDING' | 'CLOSED'>('ALL')
const sosLoading = ref(false)
const alertLoading = ref(false)
const sosActionLoading = ref(false)
const alertActionLoading = ref(false)
const sosLoadError = ref('')
const cancelledLoadError = ref('')
const alertLoadError = ref('')
const sosDispatchVisible = ref(false)
const sosCloseVisible = ref(false)
const alertResolveVisible = ref(false)
const selectedSos = ref<SosEvent | null>(null)
const selectedAlert = ref<HealthAlert | null>(null)
const sosAssigneeId = ref<number | undefined>()
const sosDispatchReason = ref('')
const sosResultSummary = ref('')
const sosFalseAlarm = ref(false)
const alertResolution = ref('')

const filteredAlertList = computed(() => alertFilter.value === 'ALL'
  ? alertList.value
  : alertList.value.filter(alert => alert.status === alertFilter.value))

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ') : '—'
}

function apiErrorMessage(error: unknown) {
  const apiError = error as ApiError
  if (apiError?.status === 401) return '登录状态已失效，请重新登录后再操作。'
  if (apiError?.status === 409) return '记录状态已变化，页面没有提交本地成功状态，请刷新后重试。'
  if (apiError?.status) return apiError.message || `服务端请求失败（HTTP ${apiError.status}）`
  if (error instanceof Error) return error.message
  return '服务暂不可用，请检查网络后重试。'
}

function requireList<T>(data: unknown, name: string): T[] {
  if (!Array.isArray(data)) throw new Error(`${name}接口响应字段缺失，无法展示真实记录。`)
  return data as T[]
}

function requirePageItems<T>(data: unknown, name: string): T[] {
  if (!data || typeof data !== 'object' || !Array.isArray((data as { items?: unknown }).items)) {
    throw new Error(`${name}接口缺少 items 列表字段，无法展示真实记录。`)
  }
  return (data as { items: T[] }).items
}

async function loadSosData() {
  sosLoading.value = true
  sosLoadError.value = ''
  cancelledLoadError.value = ''
  try {
    const [activeResponse, cancelledResponse] = await Promise.all([
      adminApi.getSosEvents('ACCEPTED'),
      adminApi.getSosEvents('CANCELLED'),
    ])
    activeSosList.value = requireList<SosEvent>(activeResponse.data, '活动 SOS')
    cancelledSosList.value = requireList<SosEvent>(cancelledResponse.data, '已取消 SOS')
    return true
  } catch (error) {
    const message = apiErrorMessage(error)
    sosLoadError.value = message
    cancelledLoadError.value = message
    return false
  } finally {
    sosLoading.value = false
  }
}

async function loadHealthAlerts() {
  alertLoading.value = true
  alertLoadError.value = ''
  try {
    const status = alertFilter.value === 'ALL' ? undefined : alertFilter.value
    const response = await adminApi.getHealthAlerts({ status })
    alertList.value = requirePageItems<HealthAlert>(response.data, '健康告警')
    return true
  } catch (error) {
    alertLoadError.value = apiErrorMessage(error)
    return false
  } finally {
    alertLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadSosData(), loadHealthAlerts()])
}

onMounted(() => { void refreshAll() })

function openSosDispatch(sos: SosEvent) {
  selectedSos.value = sos
  sosAssigneeId.value = undefined
  sosDispatchReason.value = ''
  sosDispatchVisible.value = true
}

function openSosClose(sos: SosEvent) {
  selectedSos.value = sos
  sosResultSummary.value = ''
  sosFalseAlarm.value = false
  sosCloseVisible.value = true
}

async function submitSosDispatch() {
  const sos = selectedSos.value
  const assigneeId = sosAssigneeId.value
  if (!sos || !Number.isInteger(assigneeId) || !assigneeId || assigneeId <= 0) {
    ElMessage.warning('请输入有效的网格员账号 ID。')
    return
  }
  sosActionLoading.value = true
  try {
    await adminApi.dispatchSos(sos.sosId, { assigneeId, dispatchReason: sosDispatchReason.value.trim() || undefined })
    const response = await adminApi.getSosEvents('ACCEPTED')
    const rows = requireList<SosEvent>(response.data, '活动 SOS')
    activeSosList.value = rows
    const updated = rows.find(item => item.sosId === sos.sosId)
    if (!updated || updated.assigneeId !== assigneeId) {
      throw new Error('派单请求已返回，但服务端回读未确认目标责任人；列表已按服务端数据刷新。')
    }
    sosDispatchVisible.value = false
    ElMessage.success('SOS 派单已由服务端确认。')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
    if ((error as ApiError)?.status === 409) await loadSosData()
  } finally {
    sosActionLoading.value = false
  }
}

async function submitSosClose() {
  const sos = selectedSos.value
  const resultSummary = sosResultSummary.value.trim()
  if (!sos || resultSummary.length < 5) {
    ElMessage.warning('请填写至少 5 个字符的处置结论。')
    return
  }
  sosActionLoading.value = true
  try {
    await adminApi.closeSos(sos.sosId, { resultSummary, isFalseAlarm: sosFalseAlarm.value })
    const response = await adminApi.getSosEvents('ACCEPTED')
    const rows = requireList<SosEvent>(response.data, '活动 SOS')
    activeSosList.value = rows
    if (rows.some(item => item.sosId === sos.sosId)) {
      throw new Error('结案请求已返回，但服务端仍将此记录列为活动 SOS；页面保留服务端当前状态。')
    }
    sosCloseVisible.value = false
    ElMessage.success('SOS 结案状态已由服务端列表回读确认。')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
    if ((error as ApiError)?.status === 409) await loadSosData()
  } finally {
    sosActionLoading.value = false
  }
}

function openAlertResolve(alert: HealthAlert) {
  selectedAlert.value = alert
  alertResolution.value = ''
  alertResolveVisible.value = true
}

async function submitAlertResolve() {
  const alert = selectedAlert.value
  const handlingResult = alertResolution.value.trim()
  if (!alert || handlingResult.length < 5) {
    ElMessage.warning('请填写至少 5 个字符的处置说明。')
    return
  }
  alertActionLoading.value = true
  try {
    await adminApi.handleHealthAlert(alert.alertId, { handlingResult })
    const detailResponse = await adminApi.getHealthAlertDetail(alert.alertId)
    const detail = detailResponse.data as HealthAlert
    if (!detail || detail.status !== 'CLOSED') {
      throw new Error('处置请求已返回，但服务端详情未确认告警已结案。')
    }
    alertList.value = alertList.value.map(item => item.alertId === detail.alertId ? detail : item)
    alertResolveVisible.value = false
    ElMessage.success('健康告警结案状态已由服务端详情回读确认。')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
    if ((error as ApiError)?.status === 409) await loadHealthAlerts()
  } finally {
    alertActionLoading.value = false
  }
}
</script>
<style scoped>
.emergency-alert-view {
  padding: 8px 4px;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.view-title {
  font-size: 22px;
  font-weight: bold;
  color: #0F172A;
  margin: 0 0 6px 0;
}

.view-subtitle {
  font-size: 14px;
  color: #64748B;
  margin: 0;
}

.header-stats {
  display: flex;
  gap: 12px;
}

/* SOS 活跃强提示横幅 */
.sos-active-banner {
  background: linear-gradient(135deg, #FEF2F2, #FFF5F5);
  border: 2px solid #EF4444;
  border-radius: 12px;
}

.sos-banner-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pulse-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pulsing-badge {
  color: #DC2626;
  font-weight: 800;
  font-size: 16px;
  animation: pulse-red 1.2s infinite;
}

.sos-no {
  font-size: 14px;
  color: #64748B;
}

.sos-banner-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.elder-vital-col .name {
  font-size: 20px;
  font-weight: bold;
  color: #0F172A;
  margin-right: 12px;
}

.elder-vital-col .meta {
  font-size: 14px;
  color: #475569;
}

.elder-vital-col .address-line {
  margin-top: 8px;
  font-size: 14px;
  color: #1E293B;
}

.elder-vital-col .time-line {
  margin-top: 4px;
  font-size: 13px;
  color: #991B1B;
}

.dispatch-state-col {
  padding: 0 24px;
  border-left: 1px dashed #FCA5A5;
  border-right: 1px dashed #FCA5A5;
}

.dispatch-state-col .lbl {
  color: #64748B;
  font-size: 13px;
}

.dispatch-state-col .worker-name {
  font-size: 15px;
  font-weight: bold;
  color: #0F172A;
  margin-right: 8px;
}

.dispatch-state-col .contact-box {
  margin-top: 8px;
  font-size: 13px;
  color: #334155;
}

.action-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title-text {
  font-size: 16px;
  font-weight: bold;
  color: #0F172A;
}

@keyframes pulse-red {
  0% { opacity: 1; }
  50% { opacity: 0.4; }
  100% { opacity: 1; }
}
</style>
