<template>
  <div class="dispatch-view">
    <div class="view-header">
      <div>
        <h2 class="view-title">工单调度与结案审核</h2>
        <p class="view-subtitle">列表、详情、派单及结案状态均取自工单服务端 API。</p>
      </div>
      <el-button :loading="loading" @click="loadOrders">刷新</el-button>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
    <el-alert v-if="operationError" :title="operationError" type="warning" show-icon :closable="false" />

    <div class="header-stats">
      <el-tag type="danger" size="large" effect="dark">待派工单：{{ pendingOrderList.length }}</el-tag>
      <el-tag type="info" size="large">履约 / 待审核：{{ executingOrderList.length }}</el-tag>
    </div>

    <el-card shadow="never" class="box-card">
      <template #header>
        <div class="card-header">
          <span class="card-title-text">待派工单池</span>
          <span>状态来自 API 的 PENDING_DISPATCH 记录</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="pendingOrderList" border stripe empty-text="没有待派工单">
        <el-table-column prop="orderNo" label="工单编号" min-width="150" />
        <el-table-column prop="sourceType" label="来源" width="120" />
        <el-table-column prop="elderName" label="服务长辈" width="110" />
        <el-table-column prop="serviceTypeName" label="服务项目" min-width="150">
          <template #default="scope">{{ scope.row.serviceTypeName || scope.row.serviceType || '—' }}</template>
        </el-table-column>
        <el-table-column prop="orderTitle" label="工单事项" min-width="180" />
        <el-table-column prop="elderAddress" label="服务地址" min-width="210" />
        <el-table-column prop="statusName" label="状态" width="120" />
        <el-table-column label="创建时间" min-width="165">
          <template #default="scope">{{ formatDate(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="175" fixed="right">
          <template #default="scope">
            <el-button type="primary" size="small" @click="openDispatch(scope.row, false)">派单</el-button>
            <el-button link size="small" @click="openDetail(scope.row.workOrderId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="box-card" style="margin-top: 24px;">
      <template #header>
        <div class="card-header">
          <span class="card-title-text">已派与履约工单</span>
          <span>关闭工单不在活动池中；待结案审核记录可在此审批</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="executingOrderList" border stripe empty-text="没有已派或待审核工单">
        <el-table-column prop="orderNo" label="工单编号" min-width="150" />
        <el-table-column prop="elderName" label="服务长辈" width="110" />
        <el-table-column prop="serviceTypeName" label="服务项目" min-width="140">
          <template #default="scope">{{ scope.row.serviceTypeName || scope.row.serviceType || '—' }}</template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="责任网格员" min-width="140">
          <template #default="scope">
            {{ scope.row.assigneeName || '—' }}<span v-if="scope.row.assigneePhone">（{{ scope.row.assigneePhone }}）</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="流转状态" min-width="145">
          <template #default="scope">{{ scope.row.statusName || scope.row.status }}</template>
        </el-table-column>
        <el-table-column label="派单时间" min-width="165">
          <template #default="scope">{{ formatDate(scope.row.dispatchTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="205" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status === 'DISPATCHED'" type="warning" size="small" @click="openDispatch(scope.row, true)">改派</el-button>
            <el-button v-if="scope.row.status === 'PENDING_RESULT_REVIEW'" type="success" size="small" @click="openReview(scope.row)">审核结案</el-button>
            <el-button link size="small" @click="openDetail(scope.row.workOrderId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dispatchModalVisible" :title="isReassign ? '工单改派' : '派发工单'" width="540px">
      <el-alert type="info" :closable="false" show-icon title="服务端没有提供网格员候选列表。请输入网格员账号 ID，提交后由服务端校验当前社区和有效角色。" />
      <el-form label-position="top" style="margin-top: 16px;">
        <el-form-item label="工单">
          <el-input :model-value="currentDispatchOrder?.orderNo || ''" disabled />
        </el-form-item>
        <el-form-item label="网格员账号 ID" required>
          <el-input-number v-model="assigneeUserId" :min="1" :precision="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="isReassign ? '改派原因' : '派单说明（选填）'">
          <el-input v-model="dispatchReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchModalVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitDispatch">提交派单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewModalVisible" title="工单结案审核" width="560px">
      <el-form label-position="top">
        <el-form-item label="待审核工单">
          <el-input :model-value="currentReviewOrder?.orderNo || ''" disabled />
        </el-form-item>
        <el-form-item label="服务人员提交的完成说明">
          <el-input :model-value="currentReviewOrder?.serviceResultSummary || '服务端尚无完成说明'" type="textarea" :rows="3" disabled />
        </el-form-item>
        <el-form-item label="审核意见（必填）" required>
          <el-input v-model="reviewComment" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewModalVisible = false">取消</el-button>
        <el-button type="success" :loading="actionLoading" @click="submitReview">审核通过并结案</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="工单详情（服务端回读）" width="720px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-alert v-else-if="detailError" :title="detailError" type="error" show-icon :closable="false" />
      <el-descriptions v-else-if="detailOrder" :column="2" border>
        <el-descriptions-item label="工单编号">{{ detailOrder.orderNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工单 ID">{{ detailOrder.workOrderId }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detailOrder.sourceType || '—' }} / {{ detailOrder.sourceReferenceId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailOrder.statusName || detailOrder.status || '—' }}</el-descriptions-item>
        <el-descriptions-item label="服务长辈">{{ detailOrder.elderName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailOrder.elderPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="服务项目">{{ detailOrder.serviceTypeName || detailOrder.serviceType || '—' }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">{{ detailOrder.urgencyLevel || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工单事项" :span="2">{{ detailOrder.orderTitle || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工单说明" :span="2">{{ detailOrder.orderDescription || '—' }}</el-descriptions-item>
        <el-descriptions-item label="服务地址" :span="2">{{ detailOrder.elderAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item label="责任网格员">{{ detailOrder.assigneeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="派单时间">{{ formatDate(detailOrder.dispatchTime) }}</el-descriptions-item>
        <el-descriptions-item label="完成说明" :span="2">{{ detailOrder.serviceResultSummary || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2">{{ detailOrder.reviewComment || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiError } from '../../api/http'
import { adminApi, clearPendingIdempotencyKey } from '../../api/adminApi'

type WorkOrder = {
  workOrderId: number
  orderNo?: string | null
  sourceType?: string | null
  sourceReferenceId?: string | null
  elderId?: number | null
  elderName?: string | null
  elderPhone?: string | null
  elderAddress?: string | null
  serviceType?: string | null
  serviceTypeName?: string | null
  orderTitle?: string | null
  orderDescription?: string | null
  urgencyLevel?: string | null
  status: string
  statusName?: string | null
  currentAssigneeId?: number | null
  assigneeName?: string | null
  assigneePhone?: string | null
  dispatchTime?: string | null
  acceptTime?: string | null
  serviceStartTime?: string | null
  serviceFinishTime?: string | null
  serviceResultSummary?: string | null
  resultPhotoRequired?: boolean | null
  photoExemptionReason?: string | null
  photoUrls?: string[] | null
  reviewerName?: string | null
  reviewedAt?: string | null
  reviewComment?: string | null
  createdAt?: string | null
}

const orders = ref<WorkOrder[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const loadError = ref('')
const operationError = ref('')
const dispatchModalVisible = ref(false)
const reviewModalVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const isReassign = ref(false)
const currentDispatchOrder = ref<WorkOrder | null>(null)
const currentReviewOrder = ref<WorkOrder | null>(null)
const detailOrder = ref<WorkOrder | null>(null)
const assigneeUserId = ref<number | undefined>()
const dispatchReason = ref('')
const reviewComment = ref('')

const pendingOrderList = computed(() => orders.value.filter(order => order.status === 'PENDING_DISPATCH'))
const executingOrderList = computed(() => orders.value.filter(order => order.status !== 'PENDING_DISPATCH'))

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ') : '—'
}

function apiErrorMessage(error: unknown) {
  const apiError = error as ApiError
  if (apiError?.status === 401) return '登录状态已失效，请重新登录后再操作。'
  if (apiError?.status === 409) return apiError.message
    ? `服务端暂未确认操作（HTTP 409）：${apiError.message}`
    : '服务端暂未确认操作（HTTP 409），请稍后重试并核对工单状态。'
  if (apiError?.status) return apiError.message || `服务端请求失败（HTTP ${apiError.status}）`
  if (error instanceof Error) return error.message
  return '服务暂不可用，请检查网络后重试。'
}

function requireOrderList(data: unknown): WorkOrder[] {
  if (!Array.isArray(data)) throw new Error('工单派单池响应不是列表，无法展示真实工单。')
  return data as WorkOrder[]
}

function requireOrderDetail(data: unknown): WorkOrder {
  if (!data || typeof data !== 'object' || !('workOrderId' in data) || !('status' in data)) {
    throw new Error('工单详情缺少 workOrderId/status 字段，无法确认服务端状态。')
  }
  return data as WorkOrder
}

async function loadOrders() {
  loading.value = true
  loadError.value = ''
  operationError.value = ''
  try {
    const response = await adminApi.getWorkOrderDispatchPool()
    orders.value = requireOrderList(response.data)
  } catch (error) {
    loadError.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(() => { void loadOrders() })

function openDispatch(order: WorkOrder, reassign: boolean) {
  currentDispatchOrder.value = order
  isReassign.value = reassign
  assigneeUserId.value = undefined
  dispatchReason.value = ''
  dispatchModalVisible.value = true
}

async function openDetail(workOrderId: number) {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detailOrder.value = null
  try {
    const response = await adminApi.getWorkOrderDetail(workOrderId)
    detailOrder.value = requireOrderDetail(response.data)
  } catch (error) {
    detailError.value = apiErrorMessage(error)
  } finally {
    detailLoading.value = false
  }
}

async function submitDispatch() {
  const order = currentDispatchOrder.value
  const assigneeId = assigneeUserId.value
  if (!order || !Number.isInteger(assigneeId) || !assigneeId || assigneeId <= 0) {
    ElMessage.warning('请输入有效的网格员账号 ID。')
    return
  }
  actionLoading.value = true
  operationError.value = ''
  try {
    const requestBody = {
      assigneeUserId: assigneeId,
      reason: dispatchReason.value.trim() || undefined,
    }
    await adminApi.dispatchWorkOrderToWorker(order.workOrderId, requestBody)
    const detailResponse = await adminApi.getWorkOrderDetail(order.workOrderId)
    const updated = requireOrderDetail(detailResponse.data)
    if (updated.status !== 'DISPATCHED' || updated.currentAssigneeId !== assigneeId) {
      throw new Error('派单请求已返回，但服务端详情未确认目标责任人和 DISPATCHED 状态。')
    }
    clearPendingIdempotencyKey('POST', `/work-orders/${order.workOrderId}/dispatch`, requestBody)
    orders.value = orders.value.map(item => item.workOrderId === updated.workOrderId ? updated : item)
    dispatchModalVisible.value = false
    ElMessage.success('工单派单状态已由服务端详情回读确认。')
    await loadOrders()
  } catch (error) {
    const message = apiErrorMessage(error)
    operationError.value = message
    ElMessage.error(message)
    if ((error as ApiError)?.status === 409) await loadOrders()
  } finally {
    actionLoading.value = false
  }
}

function openReview(order: WorkOrder) {
  currentReviewOrder.value = order
  reviewComment.value = ''
  reviewModalVisible.value = true
}

async function submitReview() {
  const order = currentReviewOrder.value
  const comment = reviewComment.value.trim()
  if (!order || !comment) {
    ElMessage.warning('请填写审核意见。')
    return
  }
  actionLoading.value = true
  operationError.value = ''
  try {
    const requestBody = { action: 'APPROVE', reviewComment: comment }
    await adminApi.reviewWorkOrder(order.workOrderId, requestBody)
    const detailResponse = await adminApi.getWorkOrderDetail(order.workOrderId)
    const closedOrder = requireOrderDetail(detailResponse.data)
    if (closedOrder.status !== 'CLOSED') {
      throw new Error('结案审核请求已返回，但服务端详情未确认 CLOSED 状态。')
    }
    clearPendingIdempotencyKey('POST', `/work-orders/${order.workOrderId}/review`, requestBody)
    orders.value = orders.value.filter(item => item.workOrderId !== closedOrder.workOrderId)
    reviewModalVisible.value = false
    ElMessage.success('工单 CLOSED 状态已由服务端详情回读确认。')
    await loadOrders()
  } catch (error) {
    const message = apiErrorMessage(error)
    operationError.value = message
    ElMessage.error(message)
    if ((error as ApiError)?.status === 409) await loadOrders()
  } finally {
    actionLoading.value = false
  }
}
</script>
<style scoped>
.dispatch-view {
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
</style>
