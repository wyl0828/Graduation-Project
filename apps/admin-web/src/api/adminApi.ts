import { http, type ApiResponse } from './http'

type PendingIdempotencyKey = { fingerprint: string; key: string }

const pendingIdempotencyKeys = new Map<string, PendingIdempotencyKey>()

function stableSerialize(value: unknown): string {
  if (value === undefined) return 'undefined'
  if (value === null || typeof value !== 'object') return JSON.stringify(value) ?? String(value)
  if (Array.isArray(value)) return `[${value.map(stableSerialize).join(',')}]`

  const record = value as Record<string, unknown>
  return `{${Object.keys(record)
    .filter(key => record[key] !== undefined && typeof record[key] !== 'function' && typeof record[key] !== 'symbol')
    .sort()
    .map(key => `${JSON.stringify(key)}:${stableSerialize(record[key])}`)
    .join(',')}}`
}

function currentActorScope(): string {
  if (typeof localStorage === 'undefined') return 'anonymous'
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null') as { userId?: unknown } | null
    return user?.userId == null ? 'anonymous' : `user:${String(user.userId)}`
  } catch {
    return 'anonymous'
  }
}

function fingerprintRequest(method: string, endpoint: string, body: unknown): string {
  const serialized = `${currentActorScope()}\n${method}\n${endpoint}\n${stableSerialize(body)}`
  let hash1 = 2166136261
  let hash2 = 2246822519
  let hash3 = 3266489917
  let hash4 = 668265263

  for (let index = 0; index < serialized.length; index += 1) {
    const code = serialized.charCodeAt(index)
    hash1 = Math.imul(hash1 ^ code, 0x01000193)
    hash2 = Math.imul(hash2 ^ code, 0x85ebca6b)
    hash3 = Math.imul(hash3 ^ code, 0xc2b2ae35)
    hash4 = Math.imul(hash4 ^ code, 0x27d4eb2d)
  }

  return [hash1, hash2, hash3, hash4].map(hash => (hash >>> 0).toString(16).padStart(8, '0')).join('')
}

function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `idem-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}

function clearPendingIdempotencyKeys(): void {
  pendingIdempotencyKeys.clear()
}

function shouldRetainPendingKey(error: unknown): boolean {
  const apiError = error as { status?: unknown; code?: unknown }
  if (apiError?.code === 'API_BASE_URL_MISSING') return false

  const status = typeof apiError?.status === 'number' ? apiError.status : undefined
  if (status === undefined || status === 0) return true
  // Server-side idempotency releases owned 4xx reservations; same-key retry is safe, while changed bodies rotate the key.
  return status >= 200
}

function withPendingIdempotencyKey<T>(
  method: 'POST' | 'PUT',
  endpoint: string,
  body: unknown,
  send: (key: string) => Promise<T>,
  retainKeyOnSuccess = false,
): Promise<T> {
  const operation = `${method} ${endpoint}`
  const fingerprint = fingerprintRequest(method, endpoint, body)
  const previous = pendingIdempotencyKeys.get(operation)
  const key = previous?.fingerprint === fingerprint ? previous.key : createIdempotencyKey()
  const pending = { fingerprint, key }
  pendingIdempotencyKeys.set(operation, pending)

  const clearPending = () => {
    if (pendingIdempotencyKeys.get(operation) === pending) pendingIdempotencyKeys.delete(operation)
  }

  let request: Promise<T>
  try {
    request = send(key)
  } catch (error) {
    if (!shouldRetainPendingKey(error)) clearPending()
    return Promise.reject(error)
  }

  return request.then(
    response => {
      if (!retainKeyOnSuccess) clearPending()
      return response
    },
    error => {
      if (!shouldRetainPendingKey(error)) clearPending()
      throw error
    },
  )
}

export function clearPendingIdempotencyKey(method: 'POST' | 'PUT', endpoint: string, body?: unknown): void {
  const operation = `${method} ${endpoint}`
  const pending = pendingIdempotencyKeys.get(operation)
  if (pending?.fingerprint === fingerprintRequest(method, endpoint, body)) {
    pendingIdempotencyKeys.delete(operation)
  }
}

function postWithIdempotencyKey<T = unknown>(
  endpoint: string,
  body?: unknown,
  retainKeyOnSuccess = false,
): Promise<ApiResponse<T>> {
  return withPendingIdempotencyKey('POST', endpoint, body, key =>
    http.post<T>(endpoint, body, { 'Idempotency-Key': key }),
    retainKeyOnSuccess,
  )
}

function putWithIdempotencyKey<T = unknown>(
  endpoint: string,
  body?: unknown,
  retainKeyOnSuccess = false,
): Promise<ApiResponse<T>> {
  return withPendingIdempotencyKey('PUT', endpoint, body, key =>
    http.put<T>(endpoint, body, { 'Idempotency-Key': key }),
    retainKeyOnSuccess,
  )
}

export interface AdminLoginResponse {
  userId: number
  phone: string
  csrfToken: string | null
  activeRole?: string | null
}

export interface AdminCurrentUser {
  userId: string
  phone: string
  communityId: string | number | null
  currentRole: string
}

export type StaffRoleCode = 'COMMUNITY_ADMIN' | 'GRID_WORKER' | 'CARE_WORKER'

export interface StaffSummary {
  userId: number
  maskedPhone: string
  accountStatus: string
  roleCodes: StaffRoleCode[]
}

export interface StaffCreateRequest {
  phone: string
  initialPassword: string
  roles: StaffRoleCode[]
}

export interface StaffRolesUpdateRequest {
  roles: StaffRoleCode[]
  reason: string
}

export interface CreateElderRequest {
  name: string
  sex: string
  birthDate: string
  address: string
  contactPhone?: string
  contactPhoneMissingReason?: string
  emergencyContactName?: string
  emergencyContactRelation?: string
  emergencyContactPhone?: string
  emergencyContactPhoneMissingReason?: string
}

export interface DashboardSummaryVO {
  totalElders: number | null
  highAgeAloneCount: number | null
  pendingAlertsCount: number | null
  workOrderFulfillmentRate: number | null
  medicationComplianceRate: number | null
  monthlyClosedOrders: number | null
  todayConfirmedCheckins: number | null
}

export interface DiseaseDistributionVO {
  name: string | null
  value: number | null
  percentage: number | null
}

export interface ServiceStructureVO {
  serviceType: string | null
  serviceName: string | null
  orderCount: number | null
  percentage: number | null
}

export interface WorkOrder7DayTrendVO {
  dateList: string[] | null
  dispatchedCounts: Array<number | null> | null
  closedCounts: Array<number | null> | null
  avgResponseMinutes: Array<number | null> | null
}

export interface DashboardChartsVO {
  diseaseDistribution: DiseaseDistributionVO[] | null
  serviceStructure: ServiceStructureVO[] | null
  workOrderTrend: WorkOrder7DayTrendVO | null
}

export interface RealtimeAlertFeedVO {
  alertId: number
  time: string | null
  level: string | null
  tagType: 'danger' | 'warning' | 'info' | null
  tagText: string | null
  elderName: string | null
  address: string | null
  description: string | null
  handler: string | null
  status: string | null
  statusText: string | null
}

export const adminApi = {
  // M01 认证
  login(phone: string, password: string) {
    return http.post<AdminLoginResponse>('/auth/login', { phone, password, clientType: 'WEB' })
  },
  getCurrentUser() {
    return http.get<AdminCurrentUser>('/auth/current-user')
  },
  logout() {
    return http.post('/auth/logout').finally(clearPendingIdempotencyKeys)
  },

  // M01 社区工作人员账号与角色管理
  getStaffList() {
    return http.get<StaffSummary[]>('/admin/staff')
  },
  createStaff(data: StaffCreateRequest) {
    return postWithIdempotencyKey<StaffSummary>('/admin/staff', data)
  },
  replaceStaffRoles(userId: number, data: StaffRolesUpdateRequest) {
    return putWithIdempotencyKey<StaffSummary>(`/admin/staff/${userId}/roles`, data, true)
  },

  // M02 老人档案 (P-03 / P-04)
  getElderList(params?: { name?: string; archiveNo?: string; status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params?.name) searchParams.set('name', params.name)
    if (params?.archiveNo) searchParams.set('archiveNo', params.archiveNo)
    if (params?.status) searchParams.set('status', params.status)
    if (params?.page) searchParams.set('page', String(params.page))
    if (params?.size) searchParams.set('size', String(params.size))
    const queryStr = searchParams.toString()
    return http.get(`/elder/archives${queryStr ? '?' + queryStr : ''}`)
  },
  getElderDetail(elderId: number) {
    return http.get(`/elder/archives/${elderId}`)
  },
  createElder(data: CreateElderRequest) {
    return http.post('/elder/archives', data)
  },
  issueActivationCode(elderId: number, data?: { issueMode?: string; specialReason?: string; proxyBasisType?: string }) {
    return http.post(`/elder/archives/${elderId}/activation-code`, data || {})
  },
  getActiveActivationCode(elderId: number) {
    return http.get(`/elder/archives/${elderId}/activation-code`)
  },
  updateElderStatus(elderId: number, status: string, reason: string) {
    return http.post(`/elder/archives/${elderId}/status`, { status, reason })
  },

  // M02 亲情核验与风控 (P-10)
  getKinshipApplications(status?: string) {
    return http.get(`/family/links/applications${status ? '?status=' + status : ''}`)
  },
  reviewKinshipApplication(applicationId: number, approved: boolean, decisionReason?: string) {
    return http.post(`/family/links/applications/${applicationId}/review`, { approved, decisionReason })
  },
  freezeKinship(linkId: number, freezeReasonCode: string, freezeReasonText: string) {
    return http.post(`/family/links/${linkId}/freeze`, { freezeReasonCode, freezeReasonText })
  },
  unfreezeKinship(linkId: number, reason: string) {
    return http.post(`/family/links/${linkId}/unfreeze`, { reason })
  },
  unlinkKinship(linkId: number, reason: string) {
    return http.post(`/family/links/${linkId}/unlink`, { reason })
  },

  // M02 档案更正审批工作台 (P-12)
  getCorrectionList(status?: string) {
    return http.get(`/elder/corrections${status ? '?status=' + status : ''}`)
  },
  getCorrectionDetail(requestId: number) {
    return http.get(`/elder/corrections/${requestId}`)
  },
  reviewCorrection(requestId: number, approved: boolean, decisionReason?: string) {
    return http.post(`/elder/corrections/${requestId}/review`, { approved, decisionReason })
  },

  // M03 健康告警处置
  getHealthAlerts(params?: { elderId?: number; status?: string; alertLevel?: string }) {
    const searchParams = new URLSearchParams()
    if (params?.elderId) searchParams.set('elderId', String(params.elderId))
    if (params?.status) searchParams.set('status', params.status)
    if (params?.alertLevel) searchParams.set('alertLevel', params.alertLevel)
    const queryStr = searchParams.toString()
    return http.get(`/health/alerts${queryStr ? '?' + queryStr : ''}`)
  },
  handleHealthAlert(alertId: number, data: { handlingResult: string; dispatchWorkOrder?: boolean }) {
    return http.post(`/health/alerts/${alertId}/handle`, data)
  },

  // M05 SOS 救援调度与处置中心 (P-16)
  getSosEvents(status?: string) {
    return http.get(`/emergency/sos/list${status ? '?status=' + status : ''}`)
  },
  dispatchSos(sosId: number, data: { assigneeId: number; dispatchReason?: string }) {
    return postWithIdempotencyKey(`/emergency/sos/${sosId}/dispatch`, data, true)
  },
  closeSos(sosId: number, data: { resultSummary: string; isFalseAlarm?: boolean }) {
    return postWithIdempotencyKey(`/emergency/sos/${sosId}/close`, data, true)
  },

  // M09 社区态势感知大屏
  getDashboardSummary() {
    return http.get<DashboardSummaryVO>('/dashboard/community/summary')
  },
  getDashboardCharts(period = '7D') {
    return http.get<DashboardChartsVO>(`/dashboard/community/charts?period=${period}`)
  },
  getDashboardRealtimeAlerts(limit = 10) {
    return http.get<RealtimeAlertFeedVO[]>(`/dashboard/community/alerts-feed?limit=${limit}`)
  },

  // M06 / M07 工单调度与履约
  getWorkOrderDispatchPool(params?: { communityId?: number; status?: string }) {
    const searchParams = new URLSearchParams()
    if (params?.communityId) searchParams.set('communityId', String(params.communityId))
    if (params?.status) searchParams.set('status', params.status)
    const query = searchParams.toString()
    return http.get(`/work-orders/dispatch-pool${query ? '?' + query : ''}`)
  },
  getMyTasks(status?: string) {
    return http.get(`/work-orders/my-tasks${status ? '?status=' + status : ''}`)
  },
  acceptWorkOrder(workOrderId: number) {
    return postWithIdempotencyKey(`/work-orders/${workOrderId}/accept`, undefined, true)
  },
  startWorkOrder(workOrderId: number) {
    return postWithIdempotencyKey(`/work-orders/${workOrderId}/start`, undefined, true)
  },
  getWorkOrderDetail(workOrderId: number) {
    return http.get(`/work-orders/${workOrderId}`)
  },
  dispatchWorkOrder(workOrderId: number, data: { assigneeId: number; dispatchNotes?: string }) {
    return postWithIdempotencyKey(`/work-orders/${workOrderId}/dispatch`, data)
  },
  submitWorkOrderResult(workOrderId: number, data: { resultSummary: string; photoUrls?: string[]; isExemptPhoto?: boolean; photoExemptionReason?: string }) {
    return postWithIdempotencyKey(`/work-orders/${workOrderId}/execution/submit`, data, true)
  },
  reviewWorkOrder(workOrderId: number, data: { action: string; reviewComment?: string }) {
    return postWithIdempotencyKey(`/work-orders/${workOrderId}/review`, data, true)
  },

  // 文件上传 (工单履约凭据照片)
  uploadPhotoEvidence(file: File, workOrderId?: number) {
    const formData = new FormData()
    formData.append('file', file)
    if (workOrderId !== undefined) formData.append('workOrderId', String(workOrderId))
    return http.request<{ url: string; fileName: string; size: number }>('/files/upload', {
      method: 'POST',
      body: formData,
    })
  },

  // M12 审计追溯
  getAuditLogs(params?: { targetType?: string; action?: string; actorRole?: string; traceId?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params?.targetType) searchParams.set('targetType', params.targetType)
    if (params?.action) searchParams.set('action', params.action)
    if (params?.actorRole) searchParams.set('actorRole', params.actorRole)
    if (params?.traceId) searchParams.set('traceId', params.traceId)
    if (params?.page) searchParams.set('page', String(params.page))
    if (params?.size) searchParams.set('size', String(params.size))
    const query = searchParams.toString()
    return http.get(`/audit/logs${query ? '?' + query : ''}`)
  },
  getAuditDiff(auditId: number) {
    return http.get(`/audit/logs/${auditId}/diff`)
  },
  async exportAuditLogs(params?: { targetType?: string; action?: string; actorRole?: string; traceId?: string }) {
    const searchParams = new URLSearchParams()
    if (params?.targetType) searchParams.set('targetType', params.targetType)
    if (params?.action) searchParams.set('action', params.action)
    if (params?.actorRole) searchParams.set('actorRole', params.actorRole)
    if (params?.traceId) searchParams.set('traceId', params.traceId)

    const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/+$/, '')
    const query = searchParams.toString()
    const endpoint = `${apiBaseUrl}/audit/logs/export${query ? '?' + query : ''}`
    const response = await fetch(endpoint, { method: 'GET', credentials: 'include' })

    if (response.status === 401) {
      localStorage.removeItem('user')
      localStorage.removeItem('csrfToken')
      localStorage.removeItem('activeRole')
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    const contentType = response.headers.get('content-type')?.toLowerCase() || ''
    if (!response.ok || !contentType.includes('text/csv')) {
      const responseText = await response.text().catch(() => '')
      let message = responseText || `导出接口返回了非 CSV 响应 (${response.status})`
      try {
        const payload = JSON.parse(responseText)
        if (payload?.message) message = payload.message
      } catch {
        // 保留服务端返回的文本错误信息。
      }
      throw new Error(message)
    }

    return response.blob()
  },

  // M10 IoT 体征受控仿真控制台 (P-17)
  getSimulationStatus(elderId: number, vitalType = 'BLOOD_PRESSURE') {
    return http.get(`/simulation/status?elderId=${elderId}&vitalType=${encodeURIComponent(vitalType)}`)
  },
  startSimulation(data: { elderId: number; vitalType?: string; sampleIntervalSec?: number; baseValuePrimary?: number; baseValueSecondary?: number; volatilityRange?: number; abnormalInjectionRate?: number }) {
    return http.post('/simulation/start', data)
  },
  stopSimulation(elderId: number, vitalType = 'BLOOD_PRESSURE') {
    return http.post('/simulation/stop', { elderId, vitalType })
  },
  injectSingleAbnormal(data: { elderId: number; vitalType?: string; valuePrimary: number; valueSecondary?: number }) {
    return http.post('/simulation/inject-abnormal', data)
  },

  // M04 社区用药计划与电话关怀 (P-21)
  getMedicationPlans(elderId: number) {
    return http.get(`/medication/plans/elder/${elderId}`)
  },
  getMedicationPlanVersions(planId: number) {
    return http.get(`/medication/plans/${planId}/versions`)
  },
  getMedicationCompliance(elderId: number, days = 30) {
    return http.get(`/medication/compliance/${elderId}?days=${days}`)
  },
  getCommunityTimeoutMedications() {
    return http.get('/medication/admin/timeout-tasks')
  },
  recordMedicationPhoneCare(taskId: number, careNotes?: string) {
    return http.post(`/medication/admin/tasks/${taskId}/phone-care${careNotes ? '?careNotes=' + encodeURIComponent(careNotes) : ''}`)
  },

  // M03/M06 detail and dispatch contracts aligned to backend DTOs.
  getHealthAlertDetail(alertId: number) {
    return http.get(`/health/alerts/${alertId}`)
  },
  dispatchWorkOrderToWorker(workOrderId: number, data: { assigneeUserId: number; reason?: string }) {
    return postWithIdempotencyKey(`/work-orders/${workOrderId}/dispatch`, data, true)
  },
}
