<template>
  <div class="kinship-view">
    <el-card shadow="never" class="header-card">
      <div class="card-intro">
        <h2 class="title">亲情绑定核验与争议风控大厅 (P-10)</h2>
        <p class="desc">
          按服务端申请流水核验亲情绑定。关系冻结、解冻与解绑依赖服务端提供关系编号和关系状态。
        </p>
      </div>

      <el-alert
        v-if="!relationshipOperationsAvailable && !listLoading && !listError"
        title="当前接口未提供关系管理所需信息"
        type="warning"
        :closable="false"
        show-icon
        description="申请列表只返回申请编号和审核状态，不包含亲情关系编号及关系状态。冻结、解冻和解绑操作已禁用；请待服务端提供可验证的关系信息后使用。"
        class="contract-alert"
      />

      <el-tabs v-model="activeTab" class="status-tabs">
        <el-tab-pane name="PENDING">
          <template #label>
            <span>待审核 (PENDING)</span>
            <el-badge :value="pendingCount" class="tab-badge" type="warning" />
          </template>
        </el-tab-pane>
        <el-tab-pane name="APPROVED">
          <template #label>
            <span>已通过申请 (APPROVED)</span>
            <el-badge :value="approvedCount" class="tab-badge" type="success" />
          </template>
        </el-tab-pane>
        <el-tab-pane name="REJECTED">
          <template #label>
            <span>已驳回申请 (REJECTED)</span>
            <el-badge :value="rejectedCount" class="tab-badge" type="info" />
          </template>
        </el-tab-pane>
        <el-tab-pane label="全部申请" name="ALL" />
      </el-tabs>
    </el-card>

    <el-card shadow="never" class="table-card" style="margin-top: 16px" v-loading="listLoading">
      <el-alert
        v-if="listError"
        :title="listError"
        type="error"
        show-icon
        :closable="false"
      >
        <template #default>
          <el-button size="small" @click="loadApplications">重新加载</el-button>
        </template>
      </el-alert>
      <el-table
        v-else
        :data="filteredList"
        border
        stripe
        style="width: 100%"
        empty-text="暂无符合当前状态的亲情申请"
      >
        <el-table-column prop="applicationId" label="申请编号" width="110" />
        <el-table-column label="申请子女账号" min-width="150">
          <template #default="{ row }">
            <div>{{ row.applicantName || `账号 #${row.childUserId}` }}</div>
            <div v-if="row.applicantPhone" class="secondary-text">{{ row.applicantPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="长辈档案" min-width="160">
          <template #default="{ row }">
            <div>{{ row.elderName || `档案 #${row.elderId}` }}</div>
            <div v-if="row.archiveNo" class="secondary-text">{{ row.archiveNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="relationship" label="申请关系" width="120" />
        <el-table-column prop="applyReason" label="申请事由" min-width="220" show-overflow-tooltip />
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="申请状态" width="130">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">{{ getStatusText(row.status) }}</el-tag>
            <div v-if="row.reviewedAt" class="secondary-text">审核于 {{ formatDateTime(row.reviewedAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="审核与关系管理" width="290" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button type="success" size="small" :loading="actionLoadingId === row.applicationId" @click="approveApplication(row)">
                核准通过
              </el-button>
              <el-button type="danger" size="small" plain :disabled="actionLoadingId === row.applicationId" @click="openRejectDialog(row)">
                驳回申请
              </el-button>
            </template>
            <template v-else-if="row.linkId && row.linkStatus === 'ACTIVE'">
              <el-button type="warning" size="small" :disabled="actionLoadingId === row.applicationId" @click="openFreezeDialog(row)">
                争议冻结
              </el-button>
              <el-button type="danger" size="small" link :disabled="actionLoadingId === row.applicationId" @click="openUnlinkDialog(row)">
                解绑
              </el-button>
            </template>
            <template v-else-if="row.linkId && row.linkStatus === 'FROZEN'">
              <el-button type="success" size="small" :disabled="actionLoadingId === row.applicationId" @click="openUnfreezeDialog(row)">
                解冻恢复
              </el-button>
            </template>
            <span v-else class="unavailable-text">
              {{ row.linkId ? '关系状态不可用' : '关系管理接口未提供编号' }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="rejectDialogVisible" title="驳回亲情绑定申请" width="460px">
      <el-form label-width="90px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="actionReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="actionLoadingId !== null" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="freezeDialogVisible" title="设置亲情争议冻结" width="480px">
      <el-alert
        title="争议冻结风控提醒"
        type="warning"
        description="冻结操作会暂停亲情授权，服务端将记录复核期限。"
        show-icon
        :closable="false"
        class="dialog-alert"
      />
      <el-form label-width="90px">
        <el-form-item label="冻结事由" required>
          <el-input v-model="actionReason" type="textarea" :rows="3" placeholder="请填写冻结事由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="actionLoadingId !== null" @click="confirmFreeze">确认冻结</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="unfreezeDialogVisible" title="解除争议冻结并恢复权限" width="480px">
      <el-form label-width="90px">
        <el-form-item label="恢复依据" required>
          <el-input v-model="actionReason" type="textarea" :rows="3" placeholder="请填写恢复依据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unfreezeDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="actionLoadingId !== null" @click="confirmUnfreeze">确认解冻</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="unlinkDialogVisible" title="解绑亲情关系" width="480px">
      <el-form label-width="90px">
        <el-form-item label="解绑原因" required>
          <el-input v-model="actionReason" type="textarea" :rows="3" placeholder="请填写解绑原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unlinkDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="actionLoadingId !== null" @click="confirmUnlink">确认解绑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api/adminApi'

interface KinshipApplication {
  applicationId: number
  elderId: number
  childUserId: number
  applicantName?: string
  applicantPhone?: string
  elderName?: string
  archiveNo?: string
  relationship?: string
  applyReason?: string
  status: string
  createdAt?: string
  reviewedAt?: string
  decisionReason?: string
  linkId?: number
  linkStatus?: string
}

type ActionKind = 'reject' | 'freeze' | 'unfreeze' | 'unlink'

const activeTab = ref('PENDING')
const applications = ref<KinshipApplication[]>([])
const listLoading = ref(false)
const listError = ref('')
const selectedRow = ref<KinshipApplication | null>(null)
const actionReason = ref('')
const actionLoadingId = ref<number | null>(null)
const rejectDialogVisible = ref(false)
const freezeDialogVisible = ref(false)
const unfreezeDialogVisible = ref(false)
const unlinkDialogVisible = ref(false)

const filteredList = computed(() => activeTab.value === 'ALL'
  ? applications.value
  : applications.value.filter(item => item.status === activeTab.value))
const pendingCount = computed(() => applications.value.filter(item => item.status === 'PENDING').length)
const approvedCount = computed(() => applications.value.filter(item => item.status === 'APPROVED').length)
const rejectedCount = computed(() => applications.value.filter(item => item.status === 'REJECTED').length)
const relationshipOperationsAvailable = computed(() => applications.value.some(item => Number.isSafeInteger(item.linkId) && Boolean(item.linkStatus)))

function getStatusTag(status: string) {
  if (status === 'PENDING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'info'
  return 'info'
}

function getStatusText(status: string) {
  if (status === 'PENDING') return '待审核'
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已驳回'
  return status || '未知状态'
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ')
}

function errorMessage(error: unknown) {
  return error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
}

async function loadApplications() {
  listLoading.value = true
  listError.value = ''
  try {
    const response = await adminApi.getKinshipApplications()
    applications.value = Array.isArray(response.data) ? response.data as KinshipApplication[] : []
  } catch (error) {
    listError.value = `亲情申请列表加载失败：${errorMessage(error)}`
    throw error
  } finally {
    listLoading.value = false
  }
}

async function runAction(row: KinshipApplication, kind: ActionKind, reason: string) {
  if (actionLoadingId.value !== null) return
  actionLoadingId.value = row.applicationId
  let writeConfirmed = false
  try {
    if (kind === 'reject') {
      await adminApi.reviewKinshipApplication(row.applicationId, false, reason)
    } else if (kind === 'freeze' && row.linkId) {
      await adminApi.freezeKinship(row.linkId, 'KINSHIP_DISPUTE', reason)
    } else if (kind === 'unfreeze' && row.linkId) {
      await adminApi.unfreezeKinship(row.linkId, reason)
    } else if (kind === 'unlink' && row.linkId) {
      await adminApi.unlinkKinship(row.linkId, reason)
    } else {
      throw new Error('服务端未提供可验证的亲情关系编号，无法执行关系管理操作')
    }
    writeConfirmed = true
    await loadApplications()
    closeDialogs()
    ElMessage.success(actionSuccessText(kind))
  } catch (error) {
    ElMessage.error(writeConfirmed
      ? `服务端已确认操作，但回读申请状态失败：${errorMessage(error)}。请重新加载确认最新状态。`
      : errorMessage(error))
  } finally {
    actionLoadingId.value = null
  }
}

function actionSuccessText(kind: ActionKind) {
  switch (kind) {
    case 'reject': return '服务端已确认驳回申请，列表状态已更新'
    case 'freeze': return '服务端已确认冻结，列表状态已更新'
    case 'unfreeze': return '服务端已确认解冻，列表状态已更新'
    case 'unlink': return '服务端已确认解绑，列表状态已更新'
  }
}

async function approveApplication(row: KinshipApplication) {
  try {
    await ElMessageBox.confirm(
      `确认通过申请 #${row.applicationId}（老人档案 #${row.elderId}，子女账号 #${row.childUserId}）？`,
      '亲情绑定核准确认',
      { confirmButtonText: '核准通过', cancelButtonText: '取消', type: 'success' },
    )
  } catch {
    return
  }
  if (actionLoadingId.value !== null) return
  actionLoadingId.value = row.applicationId
  let writeConfirmed = false
  try {
    await adminApi.reviewKinshipApplication(row.applicationId, true)
    writeConfirmed = true
    await loadApplications()
    ElMessage.success('服务端已确认核准，申请列表状态已更新')
  } catch (error) {
    ElMessage.error(writeConfirmed
      ? `服务端已确认核准，但回读申请状态失败：${errorMessage(error)}。请重新加载确认最新状态。`
      : errorMessage(error))
  } finally {
    actionLoadingId.value = null
  }
}

function openDialog(row: KinshipApplication, visible: typeof rejectDialogVisible) {
  selectedRow.value = row
  actionReason.value = ''
  visible.value = true
}

function openRejectDialog(row: KinshipApplication) { openDialog(row, rejectDialogVisible) }
function openFreezeDialog(row: KinshipApplication) { openDialog(row, freezeDialogVisible) }
function openUnfreezeDialog(row: KinshipApplication) { openDialog(row, unfreezeDialogVisible) }
function openUnlinkDialog(row: KinshipApplication) { openDialog(row, unlinkDialogVisible) }

function closeDialogs() {
  rejectDialogVisible.value = false
  freezeDialogVisible.value = false
  unfreezeDialogVisible.value = false
  unlinkDialogVisible.value = false
  selectedRow.value = null
  actionReason.value = ''
}

async function confirmReasonAction(kind: ActionKind, requiredLabel: string) {
  if (!actionReason.value.trim()) {
    ElMessage.error(`${requiredLabel}必须填写`)
    return
  }
  const row = selectedRow.value
  if (!row) return
  await runAction(row, kind, actionReason.value.trim())
}

function confirmReject() { return confirmReasonAction('reject', '驳回原因') }
function confirmFreeze() { return confirmReasonAction('freeze', '冻结事由') }
function confirmUnfreeze() { return confirmReasonAction('unfreeze', '恢复依据') }
function confirmUnlink() { return confirmReasonAction('unlink', '解绑原因') }

onMounted(() => {
  void loadApplications().catch(() => undefined)
})
</script>

<style scoped>
.header-card,
.table-card {
  border-radius: 12px;
}

.card-intro .title {
  margin-bottom: 6px;
  color: #1e293b;
  font-size: 18px;
  font-weight: 800;
}

.card-intro .desc {
  margin-bottom: 16px;
  color: #64748b;
  font-size: 13px;
}

.contract-alert {
  margin-bottom: 14px;
}

.dialog-alert {
  margin-bottom: 14px;
}

.tab-badge {
  margin-left: 6px;
}

.secondary-text {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.unavailable-text {
  color: #9a6700;
  font-size: 12px;
}
</style>
