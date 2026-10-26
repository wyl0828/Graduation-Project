import { uniHttp } from './http'

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
    return uniHttp.post('/emergency/sos/trigger', data)
  },
  cancelSosByElder(sosId: number, cancelReason: string) {
    return uniHttp.post(`/emergency/sos/${sosId}/cancel`, { cancelReason })
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
    return uniHttp.post('/medication/tasks/checkin', data)
  },
  getMedicationCompliance(elderId: number, days = 30) {
    return uniHttp.get(`/medication/compliance/${elderId}`, { days })
  },

  // M06 助老服务预约与工单
  listServiceCatalogs() {
    return uniHttp.get('/service-catalogs')
  },
  createReservation(data: { elderId: number; serviceType: string; serviceDate: string; slotCode: string; serviceAddress: string; specialNotes?: string }) {
    return uniHttp.post('/service-reservations', data)
  },
  getMyReservations(elderId?: number) {
    return uniHttp.get('/service-reservations/my', elderId ? { elderId } : undefined)
  },
  getReservationDetail(reservationId: number) {
    return uniHttp.get(`/service-reservations/${reservationId}`)
  },
  cancelReservation(reservationId: number, reason: string) {
    return uniHttp.post(`/service-reservations/${reservationId}/cancel`, { reason })
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
