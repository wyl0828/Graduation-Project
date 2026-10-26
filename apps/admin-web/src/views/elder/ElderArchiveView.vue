<template>
  <div class="archive-view">
    <div class="page-heading">
      <div>
        <h2>老人档案</h2>
        <p>档案数据由服务端分页接口返回，可见范围以服务端当前社区隔离策略为准。</p>
      </div>
      <el-button type="primary" @click="openCreateDrawer">登记新建档案</el-button>
    </div>

    <el-alert
      v-if="operationError"
      class="operation-alert"
      type="error"
      :closable="false"
      :title="operationError"
      show-icon
    />

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters" class="filter-form" @submit.prevent="searchArchives">
        <el-form-item label="老人姓名">
          <el-input v-model="filters.name" placeholder="请输入姓名" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="档案编号">
          <el-input v-model="filters.archiveNo" placeholder="请输入档案编号" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item label="档案状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="在档正常" value="ACTIVE" />
            <el-option label="已停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="listLoading" @click="searchArchives">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-alert
        v-if="listError"
        class="list-error"
        type="error"
        :closable="false"
        :title="listError"
        show-icon
      />
      <el-table
        :data="archives"
        v-loading="listLoading"
        border
        stripe
        row-key="elderId"
        style="width: 100%"
      >
        <el-table-column prop="archiveNo" label="档案编号" min-width="165" />
        <el-table-column prop="name" label="老人姓名" width="115">
          <template #default="{ row }">
            <span class="elder-name">{{ row.name || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="90">
          <template #default="{ row }">{{ formatSex(row.sex) }}</template>
        </el-table-column>
        <el-table-column prop="birthDate" label="出生日期" width="125">
          <template #default="{ row }">{{ row.birthDate || '—' }}</template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" min-width="145">
          <template #default="{ row }">{{ row.contactPhone || '未登记' }}</template>
        </el-table-column>
        <el-table-column prop="address" label="现居地址" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.address || '—' }}</template>
        </el-table-column>
        <el-table-column label="档案状态" width="105">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '在档正常' : row.status === 'INACTIVE' ? '已停用' : row.status || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="激活码" width="125">
          <template #default="{ row }">
            <el-tag :type="row.hasActiveActivationCode ? 'warning' : 'info'" size="small">
              {{ row.hasActiveActivationCode ? '存在有效码' : '无有效码' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              :disabled="actionLoading"
              @click="openActivationDialog(row)"
            >
              签发/重签激活码
            </el-button>
            <el-button
              :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
              link
              size="small"
              :disabled="actionLoading || (row.status !== 'ACTIVE' && row.status !== 'INACTIVE')"
              @click="toggleArchiveStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '停用档案' : row.status === 'INACTIVE' ? '重新启用' : '状态未知' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="listError ? '档案列表暂不可用' : '暂无符合条件的老人档案'" />
        </template>
      </el-table>

      <div class="pagination-row">
        <span>共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="sizes, prev, pager, next"
          :disabled="listLoading"
          @current-change="loadArchives"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="createDrawerVisible" title="新建社区老人档案" size="560px">
      <el-alert
        class="drawer-note"
        type="info"
        :closable="false"
        title="当前建档接口对空出生日期会保存 1950-01-01 默认值，因此需先核实并填写真实日期。接口未提供身份证号回显，本页不收集该字段。"
        show-icon
      />
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="132px"
        class="create-form"
      >
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="老人姓名" prop="name">
          <el-input v-model="createForm.name" maxlength="80" placeholder="请输入老人姓名" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="createForm.sex" style="width: 100%">
            <el-option label="未说明" value="UNKNOWN" />
            <el-option label="男" value="MALE" />
            <el-option label="女" value="FEMALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker
            v-model="createForm.birthDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择核实后的出生日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="现居地址" prop="address">
          <el-input v-model="createForm.address" maxlength="240" placeholder="请输入现居地址" />
        </el-form-item>

        <el-divider content-position="left">联系方式</el-divider>
        <el-form-item label="联系电话">
          <el-input v-model="createForm.contactPhone" maxlength="32" placeholder="没有可留空并说明原因" />
        </el-form-item>
        <el-form-item v-if="!createForm.contactPhone.trim()" label="电话缺失原因">
          <el-input
            v-model="createForm.contactPhoneMissingReason"
            type="textarea"
            :rows="2"
            maxlength="240"
            placeholder="未填写联系电话时必填"
          />
        </el-form-item>

        <el-divider content-position="left">紧急联系人</el-divider>
        <el-form-item label="联系人姓名">
          <el-input v-model="createForm.emergencyContactName" maxlength="80" placeholder="可选" />
        </el-form-item>
        <el-form-item label="亲属关系">
          <el-input v-model="createForm.emergencyContactRelation" maxlength="40" placeholder="可选" />
        </el-form-item>
        <el-form-item label="联系人电话">
          <el-input v-model="createForm.emergencyContactPhone" maxlength="32" placeholder="没有可留空并说明原因" />
        </el-form-item>
        <el-form-item v-if="!createForm.emergencyContactPhone.trim()" label="电话缺失原因">
          <el-input
            v-model="createForm.emergencyContactPhoneMissingReason"
            type="textarea"
            :rows="2"
            maxlength="240"
            placeholder="未填写紧急联系人电话时必填"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button :disabled="createLoading" @click="createDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="createLoading" @click="submitCreateArchive">提交建档</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="activationDialogVisible"
      title="签发老人激活码"
      width="580px"
      :close-on-click-modal="false"
    >
      <div v-if="currentSelectedElder" class="activation-dialog-body">
        <el-descriptions border :column="2" size="small">
          <el-descriptions-item label="老人姓名">{{ currentSelectedElder.name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="档案编号">{{ currentSelectedElder.archiveNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="档案状态">
            {{ currentSelectedElder.status === 'ACTIVE' ? '在档正常' : '已停用' }}
          </el-descriptions-item>
          <el-descriptions-item label="有效激活码">
            {{ activationLoading ? '正在读取' : activationLoadError ? '未确认' : activeTicket ? '有（服务端回读）' : '无（服务端回读）' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          class="drawer-note"
          type="warning"
          :closable="false"
          title="当前未接入短信发送渠道。签发只生成一次性激活码，不代表验证码已送达老人，也不代表老人已完成激活；请按社区线下流程核验并交接。"
          show-icon
        />

        <el-alert
          v-if="activationLoading"
          class="drawer-note"
          type="info"
          :closable="false"
          title="正在读取服务端激活码状态…"
          show-icon
        />
        <el-alert
          v-else-if="activationLoadError"
          class="drawer-note"
          type="error"
          :closable="false"
          :title="'激活码状态读取失败：' + activationLoadError"
          show-icon
        />
        <div v-if="activeTicket" class="ticket-state">
          <p>当前服务端状态：<el-tag type="warning" size="small">{{ activeTicket.status }}</el-tag></p>
          <p>签发时间：{{ formatDateTime(activeTicket.issuedAt) }}</p>
          <p>过期时间：{{ formatDateTime(activeTicket.expiresAt) }}</p>
          <p v-if="activeTicket.remainingTries != null">剩余尝试次数：{{ activeTicket.remainingTries }}</p>
        </div>

        <el-form label-width="120px" size="small" class="issue-form">
          <el-form-item label="签发方式">
            <el-radio-group v-model="activationForm.issueMode">
              <el-radio label="IN_PERSON">当面核验</el-radio>
              <el-radio label="SPECIAL_PROXY">特殊代理</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="activationForm.issueMode === 'SPECIAL_PROXY'">
            <el-form-item label="代理依据类型" required>
              <el-input v-model="activationForm.proxyBasisType" maxlength="80" placeholder="填写已核验的代理依据类型" />
            </el-form-item>
            <el-form-item label="核验说明" required>
              <el-input
                v-model="activationForm.specialReason"
                type="textarea"
                :rows="3"
                maxlength="500"
                placeholder="记录代理人和社区核验依据"
              />
            </el-form-item>
          </template>
        </el-form>

        <el-alert
          v-if="activationError"
          class="drawer-note"
          type="error"
          :closable="false"
          :title="activationError"
          show-icon
        />

        <div v-if="newGeneratedCode" class="generated-code-box">
          <div class="code-alert-title">服务端本次返回的一次性激活码</div>
          <div class="huge-code-text">{{ newGeneratedCode }}</div>
          <p class="code-caption">请先确认页面上的服务端回读状态，再按线下流程交接；系统没有短信投递或交付回执记录。</p>
          <el-button type="primary" size="small" @click="copyCode(newGeneratedCode)">复制激活码</el-button>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button :disabled="activationActionLoading" @click="activationDialogVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="activationActionLoading"
            :disabled="activationLoading || Boolean(activationLoadError) || !currentSelectedElder || currentSelectedElder.status !== 'ACTIVE'"
            @click="issueActivationCode"
          >
            {{ activeTicket ? '作废旧码并重签' : '签发新激活码' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ApiError } from '../../api/http'
import { adminApi } from '../../api/adminApi'

interface ElderArchive {
  elderId: number
  archiveNo: string
  name: string
  sex?: string | null
  age?: number | null
  birthDate?: string | null
  address?: string | null
  contactPhone?: string | null
  emergencyContactName?: string | null
  emergencyContactPhone?: string | null
  emergencyContactRelation?: string | null
  status: string
  hasActiveActivationCode?: boolean | null
  latestActivationTicketId?: string | null
}

interface ElderPage {
  items: ElderArchive[]
  page: number
  pageSize: number
  total: number
}

interface ActivationTicket {
  ticketId: string
  elderId: number
  elderName?: string | null
  activationCodePlain?: string | null
  status: string
  issuedAt?: string | null
  expiresAt?: string | null
  remainingHours?: number | null
  remainingTries?: number | null
}

const filters = reactive({ name: '', archiveNo: '', status: '' })
const archives = ref<ElderArchive[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const listLoading = ref(false)
const listError = ref('')
const operationError = ref('')
const actionLoading = ref(false)
let listRequestVersion = 0

const createDrawerVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  name: '',
  sex: 'UNKNOWN',
  birthDate: '',
  address: '',
  contactPhone: '',
  contactPhoneMissingReason: '',
  emergencyContactName: '',
  emergencyContactRelation: '',
  emergencyContactPhone: '',
  emergencyContactPhoneMissingReason: '',
})
const createRules: FormRules = {
  name: [{ required: true, message: '请填写老人姓名', trigger: 'blur' }],
  birthDate: [{ required: true, message: '请填写核实后的出生日期', trigger: 'change' }],
  address: [{ required: true, message: '请填写现居地址', trigger: 'blur' }],
}

const activationDialogVisible = ref(false)
const activationLoading = ref(false)
const activationActionLoading = ref(false)
const activationLoadError = ref('')
const activationError = ref('')
const currentSelectedElder = ref<ElderArchive | null>(null)
const activeTicket = ref<ActivationTicket | null>(null)
const newGeneratedCode = ref('')
const activationForm = reactive({
  issueMode: 'IN_PERSON',
  proxyBasisType: '',
  specialReason: '',
})

function apiErrorMessage(error: unknown) {
  const apiError = error as ApiError
  if (apiError?.status === 401) return '登录状态已失效，请重新登录后再操作。'
  if (apiError?.status) return apiError.message || '服务端请求失败（HTTP ' + apiError.status + '）'
  if (error instanceof Error) return error.message
  return '服务暂不可用，请检查网络后重试。'
}

function requireElderPage(data: unknown): ElderPage {
  if (!data || typeof data !== 'object' || !('items' in data) || !('page' in data) || !('pageSize' in data) || !('total' in data)) {
    throw new Error('老人档案分页响应字段不完整，无法展示服务端列表。')
  }
  const result = data as { items?: unknown; page?: unknown; pageSize?: unknown; total?: unknown }
  if (
    !Array.isArray(result.items)
    || typeof result.page !== 'number'
    || typeof result.pageSize !== 'number'
    || typeof result.total !== 'number'
  ) {
    throw new Error('老人档案分页响应格式无效，无法展示服务端列表。')
  }
  const items = result.items.filter((item): item is ElderArchive => {
    return Boolean(
      item
      && typeof item === 'object'
      && 'elderId' in item
      && Number.isFinite(Number((item as ElderArchive).elderId))
      && 'archiveNo' in item
      && 'status' in item,
    )
  }).map(item => ({ ...item, elderId: Number(item.elderId) }))
  if (items.length !== result.items.length) {
    throw new Error('老人档案分页包含无法识别的记录，不能确认服务端数据。')
  }
  return { items, page: result.page, pageSize: result.pageSize, total: result.total }
}

function requireElderDetail(data: unknown, expectedElderId?: number): ElderArchive {
  if (
    !data
    || typeof data !== 'object'
    || !('elderId' in data)
    || !('archiveNo' in data)
    || !('status' in data)
  ) {
    throw new Error('老人档案详情缺少 ID、档案编号或状态，无法确认写入结果。')
  }
  const elder = data as ElderArchive
  const elderId = Number(elder.elderId)
  if (!Number.isFinite(elderId) || (expectedElderId != null && elderId !== expectedElderId)) {
    throw new Error('老人档案详情与本次操作对象不一致，无法确认写入结果。')
  }
  return { ...elder, elderId }
}

function requireActivationTicket(data: unknown, expectedElderId: number): ActivationTicket | null {
  if (data == null) return null
  if (
    typeof data !== 'object'
    || !('ticketId' in data)
    || !('elderId' in data)
    || !('status' in data)
  ) {
    throw new Error('激活码详情缺少票据 ID、老人 ID 或状态，无法确认签发结果。')
  }
  const ticket = data as ActivationTicket
  if (Number(ticket.elderId) !== expectedElderId) {
    throw new Error('激活码详情与当前老人档案不一致，无法确认签发结果。')
  }
  return { ...ticket, elderId: Number(ticket.elderId) }
}

async function fetchArchiveByNo(archiveNo: string) {
  const response = await adminApi.getElderList({ archiveNo, page: 1, size: 10 })
  const result = requireElderPage(response.data)
  const elder = result.items.find(item => item.archiveNo === archiveNo)
  if (!elder) throw new Error('服务端档案列表未找到档案编号 ' + archiveNo + '，无法确认写入结果。')
  return elder
}

async function loadArchives(): Promise<boolean> {
  const requestVersion = ++listRequestVersion
  listLoading.value = true
  listError.value = ''
  try {
    const response = await adminApi.getElderList({
      ...filters,
      page: page.value,
      size: pageSize.value,
    })
    const result = requireElderPage(response.data)
    if (requestVersion !== listRequestVersion) return false
    archives.value = result.items
    page.value = result.page
    pageSize.value = result.pageSize
    total.value = result.total
    return true
  } catch (error) {
    if (requestVersion !== listRequestVersion) return false
    archives.value = []
    total.value = 0
    listError.value = apiErrorMessage(error)
    return false
  } finally {
    if (requestVersion === listRequestVersion) listLoading.value = false
  }
}

async function searchArchives() {
  page.value = 1
  await loadArchives()
}

async function resetFilters() {
  filters.name = ''
  filters.archiveNo = ''
  filters.status = ''
  page.value = 1
  await loadArchives()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  await loadArchives()
}

function resetCreateForm() {
  Object.assign(createForm, {
    name: '',
    sex: 'UNKNOWN',
    birthDate: '',
    address: '',
    contactPhone: '',
    contactPhoneMissingReason: '',
    emergencyContactName: '',
    emergencyContactRelation: '',
    emergencyContactPhone: '',
    emergencyContactPhoneMissingReason: '',
  })
  operationError.value = ''
  void createFormRef.value?.clearValidate()
}

function openCreateDrawer() {
  resetCreateForm()
  createDrawerVisible.value = true
}

async function submitCreateArchive() {
  operationError.value = ''
  if (!createForm.contactPhone.trim() && !createForm.contactPhoneMissingReason.trim()) {
    ElMessage.warning('未填写联系电话时，请说明电话缺失原因。')
    return
  }
  if (!createForm.emergencyContactPhone.trim() && !createForm.emergencyContactPhoneMissingReason.trim()) {
    ElMessage.warning('未填写紧急联系人电话时，请说明电话缺失原因。')
    return
  }
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  createLoading.value = true
  let writeAccepted = false
  try {
    const createResponse = await adminApi.createElder({
      name: createForm.name.trim(),
      sex: createForm.sex,
      birthDate: createForm.birthDate,
      address: createForm.address.trim(),
      contactPhone: createForm.contactPhone.trim() || undefined,
      contactPhoneMissingReason: createForm.contactPhone.trim() ? undefined : createForm.contactPhoneMissingReason.trim(),
      emergencyContactName: createForm.emergencyContactName.trim() || undefined,
      emergencyContactRelation: createForm.emergencyContactRelation.trim() || undefined,
      emergencyContactPhone: createForm.emergencyContactPhone.trim() || undefined,
      emergencyContactPhoneMissingReason: createForm.emergencyContactPhone.trim()
        ? undefined
        : createForm.emergencyContactPhoneMissingReason.trim(),
    })
    writeAccepted = true

    const created = requireElderDetail(createResponse.data)
    const detailResponse = await adminApi.getElderDetail(created.elderId)
    const detail = requireElderDetail(detailResponse.data, created.elderId)
    if (detail.archiveNo !== created.archiveNo || detail.name !== createForm.name.trim() || detail.status !== 'ACTIVE') {
      throw new Error('新档案详情与建档响应不一致。')
    }
    const listRecord = await fetchArchiveByNo(created.archiveNo)
    if (listRecord.elderId !== created.elderId || listRecord.name !== createForm.name.trim()) {
      throw new Error('档案列表回读记录与本次建档响应不一致。')
    }

    filters.name = ''
    filters.archiveNo = ''
    filters.status = ''
    page.value = 1
    const refreshed = await loadArchives()
    if (!refreshed) throw new Error('详情和精确列表回读已完成，但当前档案列表刷新失败：' + listError.value)
    createDrawerVisible.value = false
    ElMessage.success('老人档案已创建，并通过服务端详情和列表回读确认。')
  } catch (error) {
    const reason = apiErrorMessage(error)
    const message = writeAccepted
      ? '建档请求已被服务端接受，但回读确认未完成；请重新查询核实。' + reason
      : reason
    operationError.value = message
    ElMessage.error(message)
  } finally {
    createLoading.value = false
  }
}

async function toggleArchiveStatus(row: ElderArchive) {
  if (row.status !== 'ACTIVE' && row.status !== 'INACTIVE') {
    ElMessage.warning('服务端档案状态未知，不能安全变更。')
    return
  }
  const targetStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const actionText = targetStatus === 'INACTIVE' ? '停用' : '重新启用'
  try {
    await ElMessageBox.confirm(
      '确认' + actionText + '老人【' + row.name + '】的档案？状态以服务端回读结果为准。',
      '档案状态变更确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  actionLoading.value = true
  operationError.value = ''
  let writeAccepted = false
  try {
    await adminApi.updateElderStatus(
      row.elderId,
      targetStatus,
      targetStatus === 'INACTIVE' ? '管理员停用档案' : '管理员重新启用档案',
    )
    writeAccepted = true

    const detailResponse = await adminApi.getElderDetail(row.elderId)
    const detail = requireElderDetail(detailResponse.data, row.elderId)
    const listRecord = await fetchArchiveByNo(row.archiveNo)
    if (detail.status !== targetStatus || listRecord.status !== targetStatus) {
      throw new Error('服务端档案详情或列表尚未确认目标状态。')
    }
    const refreshed = await loadArchives()
    if (!refreshed) throw new Error('详情和精确列表回读已确认目标状态，但当前档案列表刷新失败：' + listError.value)
    ElMessage.success('档案已' + actionText + '，并通过服务端详情和列表回读确认。')
  } catch (error) {
    const reason = apiErrorMessage(error)
    const message = writeAccepted
      ? '状态请求已被服务端接受，但回读确认未完成；请重新查询核实。' + reason
      : reason
    operationError.value = message
    ElMessage.error(message)
  } finally {
    actionLoading.value = false
  }
}

async function openActivationDialog(elder: ElderArchive) {
  currentSelectedElder.value = elder
  activeTicket.value = null
  newGeneratedCode.value = ''
  activationError.value = ''
  activationLoadError.value = ''
  activationForm.issueMode = 'IN_PERSON'
  activationForm.proxyBasisType = ''
  activationForm.specialReason = ''
  activationDialogVisible.value = true
  activationLoading.value = true
  try {
    const response = await adminApi.getActiveActivationCode(elder.elderId)
    activeTicket.value = requireActivationTicket(response.data, elder.elderId)
    if (elder.hasActiveActivationCode === true && !activeTicket.value) {
      activationLoadError.value = '列表显示存在有效激活码，但激活码详情未返回有效票据。'
    } else if (elder.hasActiveActivationCode === false && activeTicket.value) {
      activationLoadError.value = '列表与激活码详情状态不一致，请刷新列表后再操作。'
    }
  } catch (error) {
    activationLoadError.value = apiErrorMessage(error)
  } finally {
    activationLoading.value = false
  }
}

async function issueActivationCode() {
  const elder = currentSelectedElder.value
  if (!elder) return
  if (activationForm.issueMode === 'SPECIAL_PROXY') {
    if (!activationForm.proxyBasisType.trim() || !activationForm.specialReason.trim()) {
      ElMessage.warning('特殊代理签发需要填写代理依据类型和核验说明。')
      return
    }
  }

  if (activeTicket.value) {
    try {
      await ElMessageBox.confirm(
        '重签会立即使当前有效激活码失效。继续后请以服务端回读结果为准。',
        '确认重签激活码',
        { confirmButtonText: '继续重签', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }

  activationActionLoading.value = true
  activationError.value = ''
  let writeAccepted = false
  try {
    const issueResponse = await adminApi.issueActivationCode(elder.elderId, {
      issueMode: activationForm.issueMode,
      proxyBasisType: activationForm.issueMode === 'SPECIAL_PROXY' ? activationForm.proxyBasisType.trim() : undefined,
      specialReason: activationForm.issueMode === 'SPECIAL_PROXY' ? activationForm.specialReason.trim() : undefined,
    })
    writeAccepted = true
    const issued = requireActivationTicket(issueResponse.data, elder.elderId)
    if (!issued?.ticketId || issued.status !== 'ISSUED' || !/^\d{6}$/.test(issued.activationCodePlain || '')) {
      throw new Error('签发响应缺少有效票据状态或一次性明文激活码。')
    }
    newGeneratedCode.value = issued.activationCodePlain || ''

    const [elderDetailResponse, activeCodeResponse] = await Promise.all([
      adminApi.getElderDetail(elder.elderId),
      adminApi.getActiveActivationCode(elder.elderId),
    ])
    const detail = requireElderDetail(elderDetailResponse.data, elder.elderId)
    const ticket = requireActivationTicket(activeCodeResponse.data, elder.elderId)
    const listRecord = await fetchArchiveByNo(elder.archiveNo)
    if (
      !ticket
      || ticket.ticketId !== issued.ticketId
      || ticket.status !== 'ISSUED'
      || detail.hasActiveActivationCode !== true
      || listRecord.hasActiveActivationCode !== true
    ) {
      throw new Error('服务端档案或激活码列表/详情未确认本次新票据。')
    }
    activeTicket.value = ticket
    currentSelectedElder.value = detail
    const refreshed = await loadArchives()
    if (!refreshed) throw new Error('档案详情和激活码详情已回读，但当前档案列表刷新失败：' + listError.value)
    ElMessage.success('新激活码已签发，并通过服务端详情和列表回读确认；尚未通过短信发送或记录交付。')
  } catch (error) {
    const reason = apiErrorMessage(error)
    const message = writeAccepted
      ? '签发请求已被服务端接受，但回读确认未完成；页面保留接口返回的一次性码，请勿据此认定已送达。' + reason
      : reason
    activationError.value = message
    operationError.value = message
    ElMessage.error(message)
  } finally {
    activationActionLoading.value = false
  }
}

async function copyCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('激活码已复制到剪贴板。')
  } catch {
    ElMessage.error('复制失败，请手动选中并复制激活码。')
  }
}

function formatSex(sex?: string | null) {
  if (sex === 'MALE') return '男'
  if (sex === 'FEMALE') return '女'
  if (sex === 'UNKNOWN') return '未说明'
  return sex || '—'
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ') : '—'
}

onMounted(() => {
  void loadArchives()
})
</script>

<style scoped>
.archive-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h2 {
  margin: 0;
  color: #1e293b;
  font-size: 20px;
}

.page-heading p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.filter-card,
.table-card {
  border-radius: 12px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.elder-name {
  color: #1e293b;
  font-weight: 700;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 18px;
  color: #64748b;
  font-size: 13px;
}

.drawer-note,
.list-error,
.operation-alert {
  margin-bottom: 14px;
}

.create-form {
  padding-top: 4px;
}

.drawer-footer,
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.activation-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.issue-form {
  padding-top: 4px;
}

.ticket-state {
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}

.ticket-state p {
  margin: 4px 0;
}

.generated-code-box {
  padding: 18px;
  border: 2px dashed #ea580c;
  border-radius: 14px;
  background: #fff7ed;
  text-align: center;
}

.code-alert-title {
  color: #9a3412;
  font-size: 14px;
  font-weight: 700;
}

.huge-code-text {
  margin: 12px 0;
  color: #ea580c;
  font-family: monospace;
  font-size: 40px;
  font-weight: 900;
  letter-spacing: 12px;
  user-select: all;
}

.code-caption {
  margin: 0 0 14px;
  color: #78716c;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .page-heading,
  .pagination-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .pagination-row {
    overflow-x: auto;
  }
}
</style>
