<template>
  <div class="audit-log-view">
    <div class="view-header">
      <div>
        <h2 class="view-title">审计日志</h2>
        <p class="view-subtitle">查询后端已记录的操作日志、筛选记录并查看服务端返回的变更明细。</p>
      </div>
      <div class="header-tools">
        <el-button type="success" plain :loading="exporting" @click="handleExport">
          <el-icon><Download /></el-icon>
          <span>导出 CSV</span>
        </el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon class="compliance-alert">
      此页面展示审计接口已记录的数据。记录覆盖范围取决于后端业务写入情况；详情接口返回 HMAC-SHA256 指纹，但不提供验签结果或完整性保证。
    </el-alert>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="对象类型">
          <el-input v-model="filters.targetType" placeholder="精确匹配类型代码" clearable style="width: 190px" />
        </el-form-item>

        <el-form-item label="动作代码">
          <el-input v-model="filters.action" placeholder="按动作代码筛选" clearable style="width: 190px" />
        </el-form-item>

        <el-form-item label="操作人角色">
          <el-input v-model="filters.actorRole" placeholder="精确匹配角色代码" clearable style="width: 180px" />
        </el-form-item>

        <el-form-item label="Trace-Id">
          <el-input v-model="filters.traceId" placeholder="精确匹配追踪 ID" clearable style="width: 220px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="applyFilters">
            <el-icon><Search /></el-icon>
            <span>查询</span>
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-alert v-if="listError" type="error" :closable="false" :title="listError" class="list-error" />
      <el-table v-loading="loading" :data="logList" style="width: 100%" stripe>
        <el-table-column prop="auditId" label="流水号" width="100">
          <template #default="{ row }">
            <span class="mono-text">#{{ row.auditId }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="occurredAt" label="发生时间" width="200">
          <template #default="{ row }">
            <span class="mono-text">{{ row.occurredAt || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作人 / 角色" width="200">
          <template #default="{ row }">
            <div class="actor-cell">
              <strong>{{ row.actorName || '—' }}</strong>
              <el-tag v-if="row.actorRole" size="small" :type="getRoleTagType(row.actorRole)" effect="plain">
                {{ row.actorRole }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作目标" width="180">
          <template #default="{ row }">
            <div>
              <span class="target-type">{{ row.targetType || '—' }}</span>
              <div class="target-id mono-text">{{ row.targetId || '—' }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="业务动作" width="220">
          <template #default="{ row }">
            <div>{{ row.actionName || row.action || '—' }}</div>
            <div v-if="row.actionName" class="target-type">{{ row.action }}</div>
          </template>
        </el-table-column>

        <el-table-column label="执行结果" width="110">
          <template #default="{ row }">
            <el-tag :type="getResultTagType(row.result)" effect="plain" size="small">
              {{ row.result || '—' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="clientIp" label="客户端 IP" width="140">
          <template #default="{ row }">
            <span class="mono-text">{{ row.clientIp || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="traceId" label="Trace-Id" min-width="180">
          <template #default="{ row }">
            <el-tooltip v-if="row.traceId" content="点击复制追踪 ID" placement="top">
              <span class="trace-link" @click="copyTraceId(row.traceId)">
                {{ row.traceId }}
              </span>
            </el-tooltip>
            <span v-else>—</span>
          </template>
        </el-table-column>

        <el-table-column label="详情" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">
              <el-icon><Document /></el-icon>
              <span>查看详情</span>
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="totalCount"
          @current-change="loadLogs"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" title="审计详情" size="60%" direction="rtl">
      <div v-if="detailLoading" class="detail-placeholder">正在读取审计详情…</div>
      <el-alert v-else-if="detailError" type="error" :closable="false" :title="detailError" />
      <div v-else-if="selectedRow && selectedDetail" class="drawer-diff-content">
        <el-descriptions title="元数据" :column="2" border size="small">
          <el-descriptions-item label="审计流水号">#{{ selectedRow.auditId }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ selectedDetail.occurredAt || selectedRow.occurredAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ selectedDetail.actorName || selectedRow.actorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ selectedDetail.actorRole || selectedRow.actorRole || '—' }}</el-descriptions-item>
          <el-descriptions-item label="来源 IP">{{ selectedDetail.clientIp || selectedRow.clientIp || '—' }}</el-descriptions-item>
          <el-descriptions-item label="对象类型">{{ selectedRow.targetType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="对象 ID">{{ selectedRow.targetId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ selectedRow.actionName || selectedRow.action || '—' }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ selectedRow.result || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Trace-Id" :span="2">
            <span class="mono-text">{{ selectedDetail.traceId || selectedRow.traceId || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="服务端 HMAC-SHA256 指纹" :span="2">
            <span class="mono-text signature-text">{{ selectedDetail.shaSignature || '—' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert type="warning" :closable="false">
          接口返回指纹值，但不提供独立验签结果；此指纹不代表记录已通过完整性校验。
        </el-alert>

        <div class="diff-section-title">
          <span>变更字段</span>
        </div>

        <el-table :data="selectedDetail.diffItems || []" border size="small" class="diff-table">
          <el-table-column prop="fieldName" label="字段名称" width="140" />
          <el-table-column label="变更前">
            <template #default="{ row }">
              <span class="diff-before">{{ formatDiffValue(row.baselineValue) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变更后">
            <template #default="{ row }">
              <span class="diff-after">{{ formatDiffValue(row.proposedValue) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="diff-section-title">
          <span>JSON 明细</span>
        </div>

        <div class="json-compare-grid">
          <div class="json-panel">
            <div class="json-panel-head">变更前</div>
            <pre class="json-code">{{ formatJson(selectedDetail.beforeJson) }}</pre>
          </div>
          <div class="json-panel">
            <div class="json-panel-head">变更后</div>
            <pre class="json-code">{{ formatJson(selectedDetail.afterJson) }}</pre>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Download, Search, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/adminApi'

interface AuditLogItem {
  auditId: number
  occurredAt: string
  actorName?: string
  actorRole?: string
  targetType?: string
  targetId?: string
  action?: string
  actionName?: string
  result?: string
  clientIp?: string
  traceId?: string
}

interface AuditDiffItem {
  fieldName: string
  baselineValue: string | null
  proposedValue: string | null
}

interface AuditLogDetail {
  auditId: number
  occurredAt: string
  actorName: string
  actorRole: string
  clientIp: string
  traceId: string
  shaSignature: string
  diffItems: AuditDiffItem[]
  beforeJson: unknown
  afterJson: unknown
}

interface AuditLogPage {
  items: AuditLogItem[]
  page: number
  pageSize: number
  total: number
}

const filters = ref({
  targetType: '',
  action: '',
  actorRole: '',
  traceId: '',
})

const logList = ref<AuditLogItem[]>([])
const totalCount = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const exporting = ref(false)
const listError = ref('')

const drawerVisible = ref(false)
const selectedRow = ref<AuditLogItem | null>(null)
const selectedDetail = ref<AuditLogDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

let listRequestId = 0
let detailRequestId = 0

async function loadLogs() {
  const requestId = ++listRequestId
  loading.value = true
  listError.value = ''

  try {
    const response = await adminApi.getAuditLogs({
      ...filters.value,
      page: currentPage.value,
      size: pageSize.value,
    })
    const page = response.data as AuditLogPage | null
    if (!page || !Array.isArray(page.items)) {
      throw new Error('服务端返回的审计列表格式无效')
    }
    if (requestId !== listRequestId) return
    logList.value = page.items
    totalCount.value = Number(page.total) || 0
  } catch (error) {
    if (requestId !== listRequestId) return
    logList.value = []
    totalCount.value = 0
    listError.value = '审计日志加载失败：' + getErrorMessage(error)
  } finally {
    if (requestId === listRequestId) loading.value = false
  }
}

function applyFilters() {
  currentPage.value = 1
  void loadLogs()
}

function resetFilters() {
  filters.value = {
    targetType: '',
    action: '',
    actorRole: '',
    traceId: '',
  }
  currentPage.value = 1
  void loadLogs()
}

function handlePageSizeChange() {
  currentPage.value = 1
  void loadLogs()
}

async function openDetail(row: AuditLogItem) {
  const requestId = ++detailRequestId
  selectedRow.value = row
  selectedDetail.value = null
  detailError.value = ''
  detailLoading.value = true
  drawerVisible.value = true

  try {
    const response = await adminApi.getAuditDiff(row.auditId)
    if (requestId !== detailRequestId) return
    selectedDetail.value = response.data as AuditLogDetail
  } catch (error) {
    if (requestId !== detailRequestId) return
    detailError.value = '审计详情读取失败：' + getErrorMessage(error)
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await adminApi.exportAuditLogs(filters.value)
    if (blob.size === 0) {
      throw new Error('服务端返回了空文件')
    }

    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = 'audit_compliance_report.csv'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
    ElMessage.success('CSV 下载已开始')
  } catch (error) {
    ElMessage.error('审计日志导出失败：' + getErrorMessage(error))
  } finally {
    exporting.value = false
  }
}

async function copyTraceId(traceId: string) {
  try {
    if (!navigator.clipboard?.writeText) {
      throw new Error('当前浏览器不支持复制')
    }
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('Trace-Id 已复制')
  } catch (error) {
    ElMessage.info(getErrorMessage(error) + '：' + traceId)
  }
}

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '未知错误'
}

function getRoleTagType(role: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (role) {
    case 'COMMUNITY_ADMIN': return 'danger'
    case 'GRID_WORKER': return 'warning'
    case 'CHILD': return 'success'
    default: return 'info'
  }
}

function getResultTagType(result?: string): 'success' | 'danger' | 'info' {
  if (result === 'SUCCESS') return 'success'
  if (result === 'FAILURE') return 'danger'
  return 'info'
}

function formatDiffValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '（空）'
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

function formatJson(value: unknown): string {
  if (value === null || value === undefined) return '暂无明细'
  return JSON.stringify(value, null, 2) ?? String(value)
}

onMounted(() => {
  void loadLogs()
})
</script>

<style scoped>
.audit-log-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.view-title {
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 4px 0;
}

.view-subtitle {
  font-size: 13px;
  color: #64748b;
}

.compliance-alert,
.filter-card,
.table-card {
  border-radius: 8px;
}

.filter-card,
.table-card {
  border: 1px solid #e2e8f0;
}

.filter-form {
  margin-bottom: -18px;
}

.list-error {
  margin-bottom: 12px;
}

.mono-text {
  font-family: monospace;
  font-size: 12px;
  color: #334155;
}

.actor-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.target-type {
  font-size: 12px;
  color: #64748b;
}

.target-id {
  font-weight: 600;
  color: #0f172a;
}

.trace-link {
  font-family: monospace;
  font-size: 12px;
  color: #0284c7;
  cursor: pointer;
  text-decoration: underline;
}

.trace-link:hover {
  color: #0369a1;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.drawer-diff-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-placeholder {
  color: #64748b;
  padding: 24px 0;
}

.signature-text {
  word-break: break-all;
}

.diff-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  border-left: 4px solid #0284c7;
  padding-left: 8px;
  margin-top: 8px;
}

.diff-table {
  border-radius: 6px;
}

.diff-before {
  color: #b91c1c;
  background: #fef2f2;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  word-break: break-word;
}

.diff-after {
  color: #15803d;
  background: #f0fdf4;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-weight: 600;
  word-break: break-word;
}

.json-compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.json-panel {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
}

.json-panel-head {
  background: #f8fafc;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  border-bottom: 1px solid #e2e8f0;
}

.json-code {
  background: #0f172a;
  color: #38bdf8;
  padding: 12px;
  font-size: 12px;
  line-height: 1.4;
  height: 220px;
  overflow: auto;
  margin: 0;
  font-family: Consolas, Monaco, monospace;
}
</style>