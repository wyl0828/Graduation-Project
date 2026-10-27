<template>
  <div class="staff-view">
    <div class="view-header">
      <div>
        <h2 class="view-title">员工账号与角色管理</h2>
        <p class="view-subtitle">创建本社区工作人员账号，并按岗位调整当前有效角色。</p>
      </div>
      <el-button :loading="loading" @click="loadStaff">
        <el-icon><Refresh /></el-icon>
        刷新列表
      </el-button>
    </div>

    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="feedback-alert"
    />

    <el-card class="staff-card" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <h3>工作人员</h3>
            <p>仅展示本社区工作人员；手机号已脱敏。</p>
          </div>
          <el-tag effect="plain" type="info">{{ staffList.length }} 个账号</el-tag>
        </div>
      </template>

      <el-table :data="staffList" v-loading="loading" border stripe empty-text="当前社区还没有工作人员账号">
        <el-table-column prop="maskedPhone" label="手机号" min-width="155" />
        <el-table-column label="当前角色" min-width="250">
          <template #default="scope">
            <div v-if="scope.row.roleCodes.length" class="role-tags">
              <el-tag
                v-for="role in scope.row.roleCodes"
                :key="role"
                size="small"
                effect="plain"
                :type="roleTagType(role)"
              >{{ roleLabel(role) }}</el-tag>
            </div>
            <span v-else class="muted-text">未分配角色</span>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="135">
          <template #default="scope">
            <el-tag :type="accountStatusType(scope.row.accountStatus)" effect="light">
              {{ accountStatusLabel(scope.row.accountStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openRoleEditor(scope.row)">调整角色</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="staff-card create-card" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <h3>创建工作人员</h3>
            <p>初始密码仅用于本次创建请求，请通过受控方式交付给员工。</p>
          </div>
          <el-icon class="heading-icon"><Plus /></el-icon>
        </div>
      </template>

      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
        class="create-form"
        @submit.prevent="createStaff"
      >
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="createForm.phone"
            autocomplete="tel"
            maxlength="11"
            placeholder="请输入工作人员手机号"
          />
        </el-form-item>
        <el-form-item label="初始密码" prop="initialPassword">
          <el-input
            v-model="createForm.initialPassword"
            type="password"
            autocomplete="new-password"
            show-password
            maxlength="128"
            placeholder="至少 8 个字符"
          />
        </el-form-item>
        <el-form-item label="分配角色" prop="roles">
          <el-checkbox-group v-model="createForm.roles" class="role-options">
            <el-checkbox v-for="option in roleOptions" :key="option.code" :value="option.code">
              {{ option.label }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="field-hint">网格员与护工不能同时分配；家庭角色和运维角色不在此处管理。</div>
        </el-form-item>
        <div v-if="createError" class="form-error" role="alert">{{ createError }}</div>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="createForm.roles.length === 0"
          @click="createStaff"
        >创建账号</el-button>
      </el-form>
    </el-card>

    <el-dialog
      v-model="roleDialogVisible"
      title="调整工作人员角色"
      width="560px"
      :close-on-click-modal="false"
      @closed="resetRoleForm"
    >
      <template v-if="editingStaff">
        <div class="target-summary">
          <span class="target-phone">{{ editingStaff.maskedPhone }}</span>
          <el-tag :type="accountStatusType(editingStaff.accountStatus)" effect="light">
            {{ accountStatusLabel(editingStaff.accountStatus) }}
          </el-tag>
        </div>
        <el-alert
          title="保存后该员工的现有会话会立即失效，需重新登录。此社区最后一名有效管理员不能被撤销。"
          type="warning"
          show-icon
          :closable="false"
          class="dialog-hint"
        />
        <el-form label-position="top" class="role-form">
          <el-form-item label="角色集合">
            <el-checkbox-group v-model="roleForm.roles" class="role-options">
              <el-checkbox v-for="option in roleOptions" :key="option.code" :value="option.code">
                {{ option.label }}
              </el-checkbox>
            </el-checkbox-group>
            <div class="field-hint">清空选择将撤销该账号在本社区的全部工作人员角色，账号记录会保留。</div>
          </el-form-item>
          <el-form-item label="调整原因" required>
            <el-input
              v-model="roleForm.reason"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              placeholder="说明此次角色调整或撤销的原因"
            />
          </el-form-item>
          <div v-if="roleError" class="form-error" role="alert">{{ roleError }}</div>
        </el-form>
      </template>

      <template #footer>
        <el-button :disabled="savingRoles" @click="roleDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="savingRoles"
          :disabled="!roleForm.reason.trim()"
          @click="saveRoles"
        >保存角色</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import type { ApiError } from '../../api/http'
import { adminApi, type StaffRoleCode, type StaffSummary } from '../../api/adminApi'

const router = useRouter()
const staffList = ref<StaffSummary[]>([])
const loading = ref(false)
const loadError = ref('')
const creating = ref(false)
const createError = ref('')
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  phone: '',
  initialPassword: '',
  roles: [] as StaffRoleCode[],
})

const roleOptions: Array<{ code: StaffRoleCode; label: string }> = [
  { code: 'COMMUNITY_ADMIN', label: '社区管理员' },
  { code: 'GRID_WORKER', label: '网格员' },
  { code: 'CARE_WORKER', label: '护工' },
]

const createRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的 11 位手机号', trigger: 'blur' },
  ],
  initialPassword: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, max: 128, message: '初始密码长度须为 8 至 128 个字符', trigger: 'blur' },
  ],
  roles: [{ type: 'array', required: true, min: 1, message: '至少选择一个工作人员角色', trigger: 'change' }],
}

const roleDialogVisible = ref(false)
const editingStaff = ref<StaffSummary | null>(null)
const savingRoles = ref(false)
const roleError = ref('')
const roleForm = reactive({
  roles: [] as StaffRoleCode[],
  reason: '',
})

function apiErrorMessage(error: unknown) {
  const apiError = error as ApiError
  if (apiError?.status === 401) return '登录状态已失效，请重新登录。'
  if (apiError?.status === 403) return apiError.message || '当前账号无权管理工作人员。'
  if (apiError?.status === 409) return apiError.message || '角色调整与当前账号状态冲突，请刷新后重试。'
  return error instanceof Error ? error.message : '服务暂不可用，请检查网络后重试。'
}

async function loadStaff() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await adminApi.getStaffList()
    staffList.value = Array.isArray(response.data) ? response.data : []
  } catch (error) {
    loadError.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function createStaff() {
  createError.value = ''
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().then(() => true).catch(() => false)
  if (!valid || creating.value) return

  creating.value = true
  try {
    const response = await adminApi.createStaff({
      phone: createForm.phone.trim(),
      initialPassword: createForm.initialPassword,
      roles: [...createForm.roles],
    })
    staffList.value = [response.data, ...staffList.value.filter(item => item.userId !== response.data.userId)]
    ElMessage.success('工作人员账号已创建。请通过受控方式交付初始密码。')
    createForm.phone = ''
    createForm.initialPassword = ''
    createForm.roles = []
    createFormRef.value.clearValidate()
    await loadStaff()
  } catch (error) {
    createError.value = apiErrorMessage(error)
  } finally {
    creating.value = false
  }
}

function openRoleEditor(staff: StaffSummary) {
  editingStaff.value = staff
  roleForm.roles = [...staff.roleCodes]
  roleForm.reason = ''
  roleError.value = ''
  roleDialogVisible.value = true
}

function clearLocalAuthState() {
  localStorage.removeItem('user')
  localStorage.removeItem('token')
  localStorage.removeItem('activeRole')
  localStorage.removeItem('csrfToken')
}

function readCurrentUserId() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null') as { userId?: unknown } | null
    const userId = user?.userId
    if (typeof userId === 'number' && Number.isSafeInteger(userId) && userId > 0) return userId
    if (typeof userId === 'string' && /^\d+$/.test(userId)) {
      const parsed = Number(userId)
      return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
    }
    return null
  } catch {
    return null
  }
}

async function saveRoles() {
  const staff = editingStaff.value
  const reason = roleForm.reason.trim()
  if (!staff || savingRoles.value) return
  if (!reason) {
    roleError.value = '请填写角色调整原因。'
    return
  }

  savingRoles.value = true
  roleError.value = ''
  const roles = [...roleForm.roles]
  const isCurrentUser = readCurrentUserId() === staff.userId
  try {
    const response = await adminApi.replaceStaffRoles(staff.userId, { roles, reason })
    if (isCurrentUser && !roles.includes('COMMUNITY_ADMIN')) {
      ElMessage.success('角色已更新，当前管理员权限已撤销，正在退出工作台。')
      clearLocalAuthState()
      roleDialogVisible.value = false
      await router.replace('/login')
      return
    }

    if (isCurrentUser) {
      const currentUser = await adminApi.getCurrentUser()
      if (currentUser.data?.currentRole !== 'COMMUNITY_ADMIN') {
        clearLocalAuthState()
        roleDialogVisible.value = false
        await router.replace('/login')
        return
      }
    }

    staffList.value = staffList.value.map(item => item.userId === response.data.userId ? response.data : item)
    roleDialogVisible.value = false
    ElMessage.success(isCurrentUser ? '角色请求已处理，当前会话仍有效。' : '工作人员角色请求已处理。')
    await loadStaff()
  } catch (error) {
    if (isCurrentUser && [401, 403].includes((error as ApiError)?.status || 0)) {
      clearLocalAuthState()
      roleDialogVisible.value = false
      await router.replace('/login')
      return
    }
    roleError.value = apiErrorMessage(error)
  } finally {
    savingRoles.value = false
  }
}

function resetRoleForm() {
  if (savingRoles.value) return
  editingStaff.value = null
  roleForm.roles = []
  roleForm.reason = ''
  roleError.value = ''
}

function roleLabel(role: StaffRoleCode) {
  return roleOptions.find(option => option.code === role)?.label || role
}

function roleTagType(role: StaffRoleCode) {
  if (role === 'COMMUNITY_ADMIN') return 'danger'
  return role === 'CARE_WORKER' ? 'success' : 'warning'
}

function accountStatusLabel(status: string) {
  if (status === 'ACTIVE') return '正常'
  if (status === 'DISABLED') return '已停用'
  return status || '未知'
}

function accountStatusType(status: string) {
  return status === 'ACTIVE' ? 'success' : status === 'DISABLED' ? 'danger' : 'info'
}

onMounted(() => { void loadStaff() })
</script>

<style scoped>
.staff-view {
  padding: 8px 4px 24px;
}

.view-header,
.card-heading,
.target-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.view-header {
  margin-bottom: 20px;
}

.view-title {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 22px;
  font-weight: 700;
}

.view-subtitle,
.card-heading p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.staff-card {
  margin-bottom: 18px;
  border-color: #e2e8f0;
  border-radius: 10px;
}

.card-heading h3 {
  margin: 0 0 4px;
  color: #0f172a;
  font-size: 16px;
}

.card-heading p {
  font-size: 13px;
}

.feedback-alert,
.dialog-hint {
  margin-bottom: 16px;
}

.role-tags,
.role-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.create-form {
  max-width: 760px;
}

.field-hint {
  width: 100%;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.form-error {
  margin: -2px 0 14px;
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.5;
}

.heading-icon {
  color: #0284c7;
  font-size: 20px;
}

.target-summary {
  justify-content: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.target-phone {
  color: #0f172a;
  font-weight: 600;
}

.role-form {
  margin-top: 16px;
}

.muted-text {
  color: #94a3b8;
}
</style>
