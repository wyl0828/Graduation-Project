<template>
  <div class="execution-view">
    <div class="view-header">
      <div>
        <h2 class="view-title">工单执行与成果审核</h2>
        <p class="view-subtitle">网格员与护工仅查看本人当前承接的工单；社区管理员在此审核服务成果。</p>
      </div>
      <el-button :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
    <el-alert v-if="operationError" :title="operationError" type="warning" show-icon :closable="false" class="message-alert" />

    <el-tabs v-if="isWorker || isAdmin" v-model="activeTab" class="custom-tabs" type="border-card">
      <el-tab-pane v-if="isWorker" label="我的执行任务" name="WORKER_DESK">
        <el-table :data="workerOrders" border stripe style="width: 100%" v-loading="loading" :empty-text="isCareWorker ? '当前没有分配给你的护工工单' : '当前没有待处理的网格员工单'">
          <el-table-column prop="orderNo" label="工单编号" min-width="150" />
          <el-table-column prop="elderName" label="服务长辈" width="110" />
          <el-table-column label="服务项目" min-width="140">
            <template #default="scope">{{ scope.row.serviceTypeName || scope.row.serviceType || '—' }}</template>
          </el-table-column>
          <el-table-column prop="elderAddress" label="服务地址" min-width="220" />
          <el-table-column label="当前状态" min-width="145">
            <template #default="scope">
              <el-tag :type="statusTagType(scope.row.status)">{{ scope.row.statusName || scope.row.status || '—' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="执行时间" min-width="165">
            <template #default="scope">{{ formatDate(scope.row.serviceStartTime || scope.row.acceptTime || scope.row.dispatchTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="190" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.status === 'DISPATCHED'"
                type="primary"
                size="small"
                :loading="isOrderActionLoading(scope.row.workOrderId)"
                :disabled="actionLoadingId !== null"
                @click="transitionWorkerOrder(scope.row, 'accept')"
              >确认接单</el-button>
              <el-button
                v-else-if="scope.row.status === 'ACCEPTED'"
                type="primary"
                size="small"
                :loading="isOrderActionLoading(scope.row.workOrderId)"
                :disabled="actionLoadingId !== null"
                @click="transitionWorkerOrder(scope.row, 'start')"
              >开始服务</el-button>
              <el-button
                v-else-if="scope.row.status === 'PROCESSING'"
                type="primary"
                size="small"
                :disabled="actionLoadingId !== null"
                @click="handleOpenReportModal(scope.row)"
              >登记服务成果</el-button>
              <span v-else-if="scope.row.status === 'PENDING_RESULT_REVIEW'" class="muted-text">等待管理员审核</span>
              <span v-else class="muted-text">当前状态无需操作</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="isAdmin" label="服务成果审核" name="REVIEW_DESK">
        <el-table :data="pendingReviewOrders" border stripe style="width: 100%" v-loading="loading" empty-text="当前没有待审核的服务成果">
          <el-table-column prop="orderNo" label="工单编号" min-width="150" />
          <el-table-column prop="elderName" label="服务长辈" width="110" />
          <el-table-column label="服务项目" min-width="140">
            <template #default="scope">{{ scope.row.serviceTypeName || scope.row.serviceType || '—' }}</template>
          </el-table-column>
          <el-table-column label="执行网格员" min-width="150">
            <template #default="scope">
              {{ scope.row.assigneeName || (scope.row.currentAssigneeId ? `ID: ${scope.row.currentAssigneeId}` : '—') }}
            </template>
          </el-table-column>
          <el-table-column prop="serviceResultSummary" label="服务人员完成说明" min-width="240" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.serviceResultSummary || '服务人员尚未提交完成说明' }}</template>
          </el-table-column>
          <el-table-column label="现场凭据" min-width="180">
            <template #default="scope">
              <div v-if="scope.row.resultPhotoRequired === false">
                <el-tag type="warning" size="small">已申请免照</el-tag>
                <div class="exemption-reason">{{ scope.row.photoExemptionReason || '未提供免照理由' }}</div>
              </div>
              <div v-else-if="scope.row.photoUrls?.length">
                <el-tag size="small" type="success">凭据图 {{ scope.row.photoUrls.length }} 张</el-tag>
              </div>
              <el-tag v-else size="small" type="danger">缺少照片凭据</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                :disabled="actionLoadingId !== null"
                @click="handleOpenReviewModal(scope.row)"
              >审核成果</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-empty v-else-if="!loading && !loadError" description="当前登录角色无权访问工单执行与审核页面" />

    <el-dialog
      v-model="reportModalVisible"
      title="登记服务成果与现场凭据"
      width="620px"
      :close-on-click-modal="false"
      :before-close="handleReportDialogClose"
      @closed="resetReportForm"
    >
      <el-form label-position="top">
        <el-form-item label="服务工单">
          <el-input :model-value="reportOrderLabel" disabled />
        </el-form-item>

        <el-form-item label="服务执行总结（必填）" required>
          <el-input
            v-model="reportSummary"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="请据实记录完成的服务内容和现场情况，不要填写未核实的信息。"
          />
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="isExemptPhoto" label="涉及隐私的服务，申请免上传现场照片" />
          <div v-if="isExemptPhoto" class="exemption-input">
            <el-input
              v-model="photoExemptionReason"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="请说明免照理由，至少填写 5 个字。"
            />
          </div>
        </el-form-item>

        <el-form-item v-if="!isExemptPhoto" label="现场服务凭据照片（至少上传 1 张）" required>
          <el-upload
            v-model:file-list="uploadedFileList"
            class="drag-uploader"
            drag
            action="#"
            :http-request="handleCustomUpload"
            :on-remove="handleRemoveUploadedFile"
            :disabled="submittingReport"
            multiple
            accept="image/png,image/jpeg,image/jpg,image/webp"
          >
            <div class="el-upload__text">
              将现场服务照片拖到此处，或<em>点击选择图片</em>
              <div class="upload-tip">支持 JPG、PNG、WEBP，单张不超过 10MB</div>
            </div>
          </el-upload>
          <div class="upload-count">已由服务端确认 {{ uploadedPhotoUrls.length }} 张照片地址<span v-if="uploadingCount">，正在上传 {{ uploadingCount }} 张</span></div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submittingReport || uploadingCount > 0" @click="reportModalVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submittingReport"
          :disabled="uploadingCount > 0"
          @click="handleSubmitReport"
        >提交服务成果</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="reviewModalVisible"
      title="服务成果审核"
      width="680px"
      :close-on-click-modal="false"
      :before-close="handleReviewDialogClose"
      @closed="resetReviewForm"
    >
      <div v-if="currentReviewingOrder" class="review-dialog-body">
        <el-descriptions border :column="2">
          <el-descriptions-item label="工单编号">{{ currentReviewingOrder.orderNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="服务长辈">{{ currentReviewingOrder.elderName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="服务项目">{{ currentReviewingOrder.serviceTypeName || currentReviewingOrder.serviceType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="执行人员">{{ currentReviewingOrder.assigneeName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="服务总结" :span="2">{{ currentReviewingOrder.serviceResultSummary || '服务人员尚未提交完成说明' }}</el-descriptions-item>
          <el-descriptions-item label="现场凭据" :span="2">
            <div v-if="currentReviewingOrder.resultPhotoRequired === false" class="review-exemption">
              免照理由：{{ currentReviewingOrder.photoExemptionReason || '未提供免照理由' }}
            </div>
            <div v-else-if="currentReviewingOrder.photoUrls?.length" class="proof-photo-gallery">
              <el-image
                v-for="(url, index) in currentReviewingOrder.photoUrls"
                :key="`${url}-${index}`"
                :src="url"
                :preview-src-list="currentReviewingOrder.photoUrls"
                fit="cover"
                class="photo-thumb"
              />
            </div>
            <div v-else class="missing-proof">没有现场照片凭据</div>
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-position="top" class="review-form">
          <el-form-item label="审核意见（必填）" required>
            <el-input
              v-model="reviewComment"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请填写实际核验结果或需要整改的内容。"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button :disabled="submittingReview" @click="reviewModalVisible = false">取消</el-button>
        <el-button type="danger" plain :loading="submittingReview && reviewAction === 'REJECT'" :disabled="submittingReview" @click="handleRejectReview">驳回重办</el-button>
        <el-button type="success" :loading="submittingReview && reviewAction === 'APPROVE'" :disabled="submittingReview" @click="handlePassReview">通过并结案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import type { ApiError } from '../../api/http'
import { adminApi, clearPendingIdempotencyKey } from '../../api/adminApi'

type WorkOrder = {
  workOrderId: number
  orderNo?: string | null
  elderName?: string | null
  elderPhone?: string | null
  elderAddress?: string | null
  serviceType?: string | null
  serviceTypeName?: string | null
  status: string
  statusName?: string | null
  currentAssigneeId?: number | null
  assigneeName?: string | null
  dispatchTime?: string | null
  acceptTime?: string | null
  serviceStartTime?: string | null
  serviceResultSummary?: string | null
  resultPhotoRequired?: boolean | null
  photoExemptionReason?: string | null
  photoUrls?: string[] | null
  reviewComment?: string | null
}

type UploadFile = {
  uid: number | string
  name: string
  url?: string
  status?: string
}

const activeTab = ref<'WORKER_DESK' | 'REVIEW_DESK'>('WORKER_DESK')
const currentRole = ref('')
const loading = ref(false)
const loadError = ref('')
const operationError = ref('')
const actionLoadingId = ref<number | null>(null)

const workerOrders = ref<WorkOrder[]>([])
const pendingReviewOrders = ref<WorkOrder[]>([])

const reportModalVisible = ref(false)
const submittingReport = ref(false)
const currentReportingOrder = ref<WorkOrder | null>(null)
const reportSummary = ref('')
const isExemptPhoto = ref(false)
const photoExemptionReason = ref('')
const uploadedFileList = ref<UploadFile[]>([])
const uploadedPhotoUrls = ref<string[]>([])
const uploadingCount = ref(0)

const reviewModalVisible = ref(false)
const submittingReview = ref(false)
const reviewAction = ref<'APPROVE' | 'REJECT' | null>(null)
const currentReviewingOrder = ref<WorkOrder | null>(null)
const reviewComment = ref('')

const isWorker = computed(() => currentRole.value === 'GRID_WORKER' || currentRole.value === 'CARE_WORKER')
const isCareWorker = computed(() => currentRole.value === 'CARE_WORKER')
const isAdmin = computed(() => currentRole.value === 'COMMUNITY_ADMIN')
const reportOrderLabel = computed(() => {
  const order = currentReportingOrder.value
  return order ? `${order.orderNo || `工单 ${order.workOrderId}`} - ${order.serviceTypeName || order.serviceType || '服务项目'}（${order.elderName || '服务长辈'}）` : ''
})

function apiErrorMessage(error: unknown) {
  const apiError = error as ApiError
  if (apiError?.status === 401) return '登录状态已失效，请重新登录后再操作。'
  if (apiError?.status === 409) return apiError.message
    ? `服务端暂未确认操作（HTTP 409）：${apiError.message}`
    : '服务端暂未确认操作（HTTP 409），请稍后核对服务端状态再重试。'
  if (apiError?.status) return apiError.message || `服务端请求失败（HTTP ${apiError.status}）`
  if (error instanceof Error) return error.message
  return '服务暂不可用，请检查网络后重试。'
}

function requireCurrentRole(data: unknown) {
  if (!data || typeof data !== 'object' || !('currentRole' in data)) {
    throw new Error('当前用户响应缺少角色信息，无法加载对应工单工作台。')
  }
  const role = (data as { currentRole?: unknown }).currentRole
  if (role !== 'COMMUNITY_ADMIN' && role !== 'GRID_WORKER' && role !== 'CARE_WORKER') {
    throw new Error('当前登录角色无权访问工单执行与审核页面。')
  }
  return role
}

function requireOrderList(data: unknown): WorkOrder[] {
  if (!Array.isArray(data)) throw new Error('工单列表响应不是列表，无法展示服务端工单。')
  return data as WorkOrder[]
}

function requireOrderDetail(data: unknown): WorkOrder {
  if (!data || typeof data !== 'object' || !('workOrderId' in data) || !('status' in data)) {
    throw new Error('工单详情缺少工单 ID 或状态，无法确认服务端变更。')
  }
  return data as WorkOrder
}

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ') : '—'
}

function statusTagType(status: string) {
  switch (status) {
    case 'DISPATCHED': return 'warning'
    case 'ACCEPTED': return 'primary'
    case 'PROCESSING': return 'success'
    case 'PENDING_RESULT_REVIEW': return 'info'
    default: return 'info'
  }
}

function isOrderActionLoading(workOrderId: number) {
  return actionLoadingId.value === workOrderId
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const userResponse = await adminApi.getCurrentUser()
    const role = requireCurrentRole(userResponse.data)
    const roleChanged = currentRole.value !== role
    currentRole.value = role
    activeTab.value = role === 'COMMUNITY_ADMIN' ? 'REVIEW_DESK' : 'WORKER_DESK'

    if (roleChanged) {
      workerOrders.value = []
      pendingReviewOrders.value = []
    }

    if (role === 'GRID_WORKER' || role === 'CARE_WORKER') {
      const response = await adminApi.getMyTasks()
      workerOrders.value = requireOrderList(response.data).filter(order =>
        ['DISPATCHED', 'ACCEPTED', 'PROCESSING', 'PENDING_RESULT_REVIEW'].includes(order.status),
      )
      pendingReviewOrders.value = []
    } else {
      const response = await adminApi.getWorkOrderDispatchPool({ status: 'PENDING_RESULT_REVIEW' })
      pendingReviewOrders.value = requireOrderList(response.data).filter(order => order.status === 'PENDING_RESULT_REVIEW')
      workerOrders.value = []
    }
  } catch (error) {
    loadError.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(() => { void loadData() })

function replaceOrder(order: WorkOrder) {
  if (isWorker.value) {
    workerOrders.value = workerOrders.value.map(item => item.workOrderId === order.workOrderId ? order : item)
  } else if (isAdmin.value) {
    pendingReviewOrders.value = pendingReviewOrders.value.map(item => item.workOrderId === order.workOrderId ? order : item)
  }
}

async function readConfirmedOrder(workOrderId: number, expectedStatus: string) {
  const response = await adminApi.getWorkOrderDetail(workOrderId)
  const order = requireOrderDetail(response.data)
  if (order.workOrderId !== workOrderId || order.status !== expectedStatus) {
    throw new Error(`操作请求已返回，但详情未确认工单进入 ${expectedStatus} 状态；当前页面保留原状态，可刷新后重试。`)
  }
  return order
}

async function transitionWorkerOrder(order: WorkOrder, action: 'accept' | 'start') {
  if (actionLoadingId.value !== null) return
  actionLoadingId.value = order.workOrderId
  operationError.value = ''
  try {
    const nextStatus = action === 'accept' ? 'ACCEPTED' : 'PROCESSING'
    if (action === 'accept') await adminApi.acceptWorkOrder(order.workOrderId)
    else await adminApi.startWorkOrder(order.workOrderId)

    const confirmedOrder = await readConfirmedOrder(order.workOrderId, nextStatus)
    const endpoint = `/work-orders/${order.workOrderId}/${action}`
    clearPendingIdempotencyKey('POST', endpoint)
    replaceOrder(confirmedOrder)
    ElMessage.success(action === 'accept' ? '接单状态已由服务端详情确认。' : '服务开始状态已由服务端详情确认。')
    await loadData()
  } catch (error) {
    operationError.value = apiErrorMessage(error)
    ElMessage.error(operationError.value)
  } finally {
    actionLoadingId.value = null
  }
}

function handleOpenReportModal(order: WorkOrder) {
  currentReportingOrder.value = order
  reportSummary.value = ''
  isExemptPhoto.value = false
  photoExemptionReason.value = ''
  uploadedFileList.value = []
  uploadedPhotoUrls.value = []
  uploadingCount.value = 0
  operationError.value = ''
  reportModalVisible.value = true
}

function handleReportDialogClose(done: () => void) {
  if (submittingReport.value || uploadingCount.value > 0) {
    ElMessage.warning('提交或上传完成后再关闭窗口。')
    return
  }
  done()
}

function resetReportForm() {
  if (submittingReport.value || uploadingCount.value > 0) return
  currentReportingOrder.value = null
  reportSummary.value = ''
  isExemptPhoto.value = false
  photoExemptionReason.value = ''
  uploadedFileList.value = []
  uploadedPhotoUrls.value = []
}

function normalizeUploadError(error: unknown) {
  return error instanceof Error ? error : new Error('照片上传失败。')
}

function toUploadAjaxError(error: Error) {
  const status = (error as ApiError).status ?? 0
  return Object.assign(error, {
    name: 'UploadAjaxError',
    status,
    method: 'POST',
    url: `${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/files/upload`,
  })
}

async function handleCustomUpload(options: UploadRequestOptions) {
  const file = options?.file as File & { uid?: number | string }
  const uid = file?.uid
  uploadingCount.value += 1
  try {
    const workOrderId = currentReportingOrder.value?.workOrderId
    if (isCareWorker.value && workOrderId === undefined) {
      throw new Error('无法确认当前工单，护工照片凭据未上传。请关闭窗口并重新打开工单。')
    }
    const response = await adminApi.uploadPhotoEvidence(file, workOrderId)
    const url = typeof response.data?.url === 'string' ? response.data.url.trim() : ''
    if (!url || url.startsWith('blob:') || url.startsWith('data:')) {
      throw new Error('上传响应未返回有效的服务端照片地址。')
    }

    if (!uploadedPhotoUrls.value.includes(url)) uploadedPhotoUrls.value = [...uploadedPhotoUrls.value, url]
    const fileEntry = uploadedFileList.value.find(item => item.uid === uid)
    if (fileEntry) {
      fileEntry.url = url
      fileEntry.status = 'success'
    } else {
      uploadedFileList.value = [...uploadedFileList.value, { uid: uid ?? url, name: file.name, url, status: 'success' }]
    }
    options.onSuccess(response.data)
    ElMessage.success('照片已上传，服务端已返回凭据地址。')
  } catch (error) {
    const uploadError = normalizeUploadError(error)
    uploadedPhotoUrls.value = uploadedPhotoUrls.value.filter(url => url !== uploadedFileList.value.find(item => item.uid === uid)?.url)
    uploadedFileList.value = uploadedFileList.value.filter(item => item.uid !== uid)
    options.onError(toUploadAjaxError(uploadError))
    ElMessage.error(apiErrorMessage(uploadError))
  } finally {
    uploadingCount.value = Math.max(0, uploadingCount.value - 1)
  }
}

function handleRemoveUploadedFile(file: UploadFile) {
  if (file.url) uploadedPhotoUrls.value = uploadedPhotoUrls.value.filter(url => url !== file.url)
  uploadedFileList.value = uploadedFileList.value.filter(item => item.uid !== file.uid)
}

function assertExecutionResultConfirmed(order: WorkOrder, summary: string, photoUrls: string[], exempt: boolean, exemptionReason: string) {
  if (order.status !== 'PENDING_RESULT_REVIEW' || order.serviceResultSummary !== summary) {
    throw new Error('提交请求已返回，但详情未确认服务总结和待审核状态；本地填报内容已保留，请刷新后重试。')
  }
  if (exempt) {
    if (order.resultPhotoRequired !== false || order.photoExemptionReason !== exemptionReason) {
      throw new Error('提交请求已返回，但详情未确认免照标记和理由；本地填报内容已保留，请刷新后重试。')
    }
    return
  }
  const savedUrls = Array.isArray(order.photoUrls) ? order.photoUrls : []
  if (order.resultPhotoRequired === false || photoUrls.some(url => !savedUrls.includes(url))) {
    throw new Error('提交请求已返回，但详情未确认全部服务端照片地址；本地填报内容已保留，请刷新后重试。')
  }
}

async function handleSubmitReport() {
  const order = currentReportingOrder.value
  const summary = reportSummary.value.trim()
  const exemptionReason = photoExemptionReason.value.trim()
  const photoUrls = [...uploadedPhotoUrls.value]
  const exempt = isExemptPhoto.value

  if (!order) {
    ElMessage.warning('请重新打开工单后再登记服务成果。')
    return
  }
  if (summary.length < 5) {
    ElMessage.warning('请据实填写至少 5 个字的服务执行总结。')
    return
  }
  if (exempt && exemptionReason.length < 5) {
    ElMessage.warning('申请免照时必须填写至少 5 个字的理由。')
    return
  }
  if (!exempt && photoUrls.length === 0) {
    ElMessage.warning('非免照服务请至少上传一张已确认服务端地址的现场照片。')
    return
  }
  if (uploadingCount.value > 0) {
    ElMessage.warning('请等待所有照片上传完成后再提交。')
    return
  }

  submittingReport.value = true
  operationError.value = ''
  try {
    const requestBody = {
      resultSummary: summary,
      photoUrls: exempt ? [] : photoUrls,
      isExemptPhoto: exempt,
      photoExemptionReason: exempt ? exemptionReason : undefined,
    }
    await adminApi.submitWorkOrderResult(order.workOrderId, requestBody)
    const confirmedOrder = await readConfirmedOrder(order.workOrderId, 'PENDING_RESULT_REVIEW')
    assertExecutionResultConfirmed(confirmedOrder, summary, photoUrls, exempt, exemptionReason)
    clearPendingIdempotencyKey('POST', `/work-orders/${order.workOrderId}/execution/submit`, requestBody)
    replaceOrder(confirmedOrder)
    reportModalVisible.value = false
    ElMessage.success('服务成果及凭据已由服务端详情回读确认，已进入管理员审核。')
    await loadData()
  } catch (error) {
    operationError.value = apiErrorMessage(error)
    ElMessage.error(operationError.value)
  } finally {
    submittingReport.value = false
  }
}

function handleOpenReviewModal(order: WorkOrder) {
  currentReviewingOrder.value = order
  reviewComment.value = ''
  reviewAction.value = null
  operationError.value = ''
  reviewModalVisible.value = true
}

function handleReviewDialogClose(done: () => void) {
  if (submittingReview.value) {
    ElMessage.warning('审核提交完成后再关闭窗口。')
    return
  }
  done()
}

function resetReviewForm() {
  if (submittingReview.value) return
  currentReviewingOrder.value = null
  reviewComment.value = ''
  reviewAction.value = null
}

async function submitReview(action: 'APPROVE' | 'REJECT') {
  const order = currentReviewingOrder.value
  const comment = reviewComment.value.trim()
  if (!order || !comment) {
    ElMessage.warning('请填写审核意见。')
    return
  }
  if (submittingReview.value) return

  const expectedStatus = action === 'APPROVE' ? 'CLOSED' : 'PROCESSING'
  submittingReview.value = true
  reviewAction.value = action
  operationError.value = ''
  try {
    const requestBody = { action, reviewComment: comment }
    await adminApi.reviewWorkOrder(order.workOrderId, requestBody)
    const confirmedOrder = await readConfirmedOrder(order.workOrderId, expectedStatus)
    if (action === 'APPROVE' && confirmedOrder.reviewComment !== comment) {
      throw new Error('审核请求已返回，但详情未确认审核意见；页面保留本次输入，请刷新后重试。')
    }
    if (action === 'REJECT' && !confirmedOrder.reviewComment?.includes(comment)) {
      throw new Error('驳回请求已返回，但详情未确认驳回意见；页面保留本次输入，请刷新后重试。')
    }
    clearPendingIdempotencyKey('POST', `/work-orders/${order.workOrderId}/review`, requestBody)
    pendingReviewOrders.value = pendingReviewOrders.value.filter(item => item.workOrderId !== confirmedOrder.workOrderId)
    reviewModalVisible.value = false
    ElMessage.success(action === 'APPROVE' ? '结案状态已由服务端详情确认。' : '退回重办状态已由服务端详情确认。')
    await loadData()
  } catch (error) {
    operationError.value = apiErrorMessage(error)
    ElMessage.error(operationError.value)
  } finally {
    submittingReview.value = false
    reviewAction.value = null
  }
}

function handlePassReview() {
  return submitReview('APPROVE')
}

function handleRejectReview() {
  return submitReview('REJECT')
}
</script>

<style scoped>
.execution-view {
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
  color: #0f172a;
  margin: 0 0 6px;
}

.view-subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.custom-tabs {
  background-color: #fff;
}

.message-alert {
  margin-top: 12px;
}

.muted-text {
  color: #64748b;
  font-size: 13px;
}

.exemption-reason {
  max-width: 220px;
  margin-top: 4px;
  color: #92400e;
  font-size: 12px;
  white-space: normal;
}

.drag-uploader {
  width: 100%;
}

.upload-tip,
.upload-count {
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
}

.exemption-input {
  width: 100%;
  margin-top: 8px;
}

.proof-photo-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.photo-thumb {
  width: 90px;
  height: 90px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
}

.review-form {
  margin-top: 16px;
}

.review-exemption {
  color: #b45309;
}

.missing-proof {
  color: #dc2626;
}
</style>
