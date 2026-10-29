import { uniHttp, type ApiResponse } from './http'

type PendingIdempotencyKey = { fingerprint: string; key: string; suppliedKey?: string }

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
  try {
    const user = uni.getStorageSync('user') as { userId?: unknown } | null
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
  const apiError = error as { status?: unknown; statusCode?: unknown; code?: unknown }
  if (apiError?.code === 'API_BASE_URL_MISSING') return false

  const status = typeof apiError?.status === 'number'
    ? apiError.status
    : typeof apiError?.statusCode === 'number'
      ? apiError.statusCode
      : undefined
  if (status === undefined || status === 0) return true
  // Server-side idempotency releases owned 4xx reservations; same-key retry is safe, while changed bodies rotate the key.
  return status >= 200
}

function withPendingIdempotencyKey<T>(
  method: 'POST' | 'PUT',
  endpoint: string,
  body: unknown,
  send: (key: string) => Promise<T>,
  suppliedKey?: string,
): Promise<T> {
  const operation = `${method} ${endpoint}`
  const fingerprint = fingerprintRequest(method, endpoint, body)
  const previous = pendingIdempotencyKeys.get(operation)
  const normalizedSuppliedKey = suppliedKey?.trim()
  const samePendingBody = previous?.fingerprint === fingerprint
  const key = samePendingBody
    ? normalizedSuppliedKey && normalizedSuppliedKey !== previous.key && normalizedSuppliedKey !== previous.suppliedKey
      ? normalizedSuppliedKey
      : previous.key
    : normalizedSuppliedKey && previous?.key === normalizedSuppliedKey
      ? createIdempotencyKey()
      : normalizedSuppliedKey || createIdempotencyKey()
  const pending = { fingerprint, key, suppliedKey: normalizedSuppliedKey }
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
      clearPending()
      return response
    },
    error => {
      if (!shouldRetainPendingKey(error)) clearPending()
      throw error
    },
  )
}

function postWithIdempotencyKey<T = any>(endpoint: string, body?: unknown, suppliedKey?: string): Promise<ApiResponse<T>> {
  return withPendingIdempotencyKey('POST', endpoint, body, key =>
    uniHttp.post<T>(endpoint, body, { 'Idempotency-Key': key }),
    suppliedKey,
  )
}

export const elderApi = {
  // M01 认证
  login(phone: string, password: string, clientType = 'MINIAPP') {
    return uniHttp.post('/auth/login', { phone, password, clientType })
  },
  registerChild(phone: string, password: string, name: string) {
    return uniHttp.post('/auth/register-child', { phone, password, name })
  },
  getCurrentUser() {
    return uniHttp.get('/auth/current-user')
  },
  logout(): Promise<ApiResponse<void>> {
    return uniHttp.post<void>('/auth/logout').finally(clearPendingIdempotencyKeys)
  },
  switchRole(targetRoleCode: string) {
    return uniHttp.post('/auth/switch-role', { targetRoleCode })
  },

  // 手机号挑战验证码 (激活/重置密码)
  requestPhoneChallenge(phone: string, purpose: string = 'ACTIVATION', relatedRequestId?: string) {
    return uniHttp.post('/auth/phone-challenges', { phone, purpose, relatedRequestId })
  },

  // M01 / M02 老人 6 位激活码消费免密/短信挑战激活 (P-05)
  consumeActivationCode(data: {
    ticketId: string
    challengeId: string
    verificationCode: string
    activationCode: string
    initialPassword?: string
    emergencyContactName?: string
    emergencyContactPhone?: string
    emergencyContactRelation?: string
  }) {
    return uniHttp.post('/elder/activation/consume', data)
  },

  // M02 老人资料本人自维护 (P-06)
  getMyProfile() {
    return uniHttp.get('/elder/profile/me')
  },
  getElderArchive(elderId: number) {
    return uniHttp.get(`/elder/archives/${elderId}`)
  },
  updateMyProfile(data: { contactPhone?: string; emergencyContactName?: string; emergencyContactPhone?: string; emergencyContactRelation?: string; address?: string }) {
    return uniHttp.put('/elder/profile/me', data)
  },

  // M02 子女亲情绑定与多老人上下文 (P-08 / P-09)
  getMyBoundElders() {
    return uniHttp.get('/family/links/my-elders')
  },
  applyKinship(elderId: number, relationship: string, applyReason: string, evidenceMetadata?: string) {
    return uniHttp.post('/family/links/apply', { elderId, relationship, applyReason, evidenceMetadata })
  },
  unlinkKinship(linkId: number, reason: string) {
    return uniHttp.post(`/family/links/${linkId}/unlink`, { reason })
  },

  // M02 档案更正申请 (P-11)
  submitCorrection(elderId: number, reason: string, items: Array<{ fieldCode: string; proposedValue: string }>) {
    return uniHttp.post('/elder/corrections', { elderId, reason, items })
  },

  // M03 健康体征监测与预警 (P-15)
  recordVital(data: { elderId: number; vitalType: string; valuePrimary: number; valueSecondary?: number; unit: string; sourceType?: string; remark?: string; idempotencyKey?: string }) {
    return uniHttp.post('/health/vitals', data)
  },
  queryVitalTrend(elderId: number, vitalType = 'BLOOD_PRESSURE', days = 7) {
    return uniHttp.get('/health/vitals/trend', { elderId, vitalType, days })
  },
  getLatestVital(elderId: number, vitalType = 'BLOOD_PRESSURE') {
    return uniHttp.get('/health/vitals/latest', { elderId, vitalType })
  },
  listHealthAlerts(elderId?: number, status?: string) {
    return uniHttp.get('/health/alerts', { elderId, status })
  },

  // M05 SOS 应急呼叫与求助 (P-14)
  triggerSos(data: { elderId?: number; pressDurationMs: number; locationAddress?: string; longitude?: number; latitude?: number; idempotencyKey?: string }) {
    const { idempotencyKey, ...requestBody } = data
    return postWithIdempotencyKey('/emergency/sos/trigger', requestBody, idempotencyKey)
  },
  cancelSosByElder(sosId: number, cancelReason: string, idempotencyKey?: string) {
    return postWithIdempotencyKey(`/emergency/sos/${sosId}/cancel`, { cancelReason }, idempotencyKey)
  },
  getActiveSos(elderId?: number) {
    return uniHttp.get('/emergency/sos/active', elderId ? { elderId } : undefined)
  },

  // M04 用药计划与今日药盒 (P-18 / P-19)
  createMedicationPlan(data: any) {
    return uniHttp.post('/medication/plans', data)
  },
  updateMedicationPlan(planId: number, data: any) {
    return uniHttp.put(`/medication/plans/${planId}`, data)
  },
  listMedicationPlans(elderId: number) {
    return uniHttp.get(`/medication/plans/elder/${elderId}`)
  },
  listMedicationPlanVersions(planId: number) {
    return uniHttp.get(`/medication/plans/${planId}/versions`)
  },
  getTodayMedicationTasks(elderId: number) {
    return uniHttp.get(`/medication/tasks/today/${elderId}`)
  },
  checkinMedication(data: { taskId: number; checkinMode?: string; idempotencyKey?: string }) {
    const { idempotencyKey, ...requestBody } = data
    return postWithIdempotencyKey('/medication/tasks/checkin', requestBody, idempotencyKey)
  },
  getMedicationCompliance(elderId: number, days = 30) {
    return uniHttp.get(`/medication/compliance/${elderId}`, { days })
  },

  // M06 助老服务预约与工单
  listServiceCatalogs() {
    return uniHttp.get('/service-catalogs')
  },
  createReservation(data: { elderId: number; serviceType: string; serviceDate: string; slotCode: string; serviceAddress: string; specialNotes?: string }) {
    return postWithIdempotencyKey('/service-reservations', data)
  },
  getMyReservations(elderId?: number) {
    return uniHttp.get('/service-reservations/my', elderId ? { elderId } : undefined)
  },
  getReservationDetail(reservationId: number) {
    return uniHttp.get(`/service-reservations/${reservationId}`)
  },
  cancelReservation(reservationId: number, reason: string) {
    return postWithIdempotencyKey(`/service-reservations/${reservationId}/cancel`, { reason })
  },
  getWorkOrderTimeline(orderId: number) {
    return uniHttp.get(`/work-orders/${orderId}`)
  },

  // M11 亲情互动与关怀通知 (P-20)
  getMyNotifications(query?: { type?: string; readStatus?: boolean }) {
    return uniHttp.get('/notifications/my', query)
  },
  getUnreadNotificationCount() {
    return uniHttp.get('/notifications/unread-count')
  },
  markNotificationAsRead(deliveryId: number) {
    return uniHttp.post(`/notifications/${deliveryId}/read`)
  },
  markAllNotificationsAsRead() {
    return uniHttp.post('/notifications/read-all')
  },
}
