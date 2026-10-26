import { http } from './http'

export const adminApi = {
  // M01 认证
  login(phone: string, password: string) {
    return http.post('/auth/login', { phone, password, clientType: 'WEB' })
  },
  getCurrentUser() {
    return http.get('/auth/current-user')
  },
  logout() {
    return http.post('/auth/logout')
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
  createElder(data: any) {
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
    return http.post(`/emergency/sos/${sosId}/dispatch`, data)
  },
  closeSos(sosId: number, data: { resultSummary: string; isFalseAlarm?: boolean }) {
    return http.post(`/emergency/sos/${sosId}/close`, data)
  },

  // M09 社区态势感知大屏
  getDashboardSummary() {
    return http.get('/dashboard/community/summary')
  },
  getDashboardCharts(period = '7d') {
    return http.get(`/dashboard/community/charts?period=${period}`)
  },
  getDashboardRealtimeAlerts(limit = 10) {
    return http.get(`/dashboard/community/realtime-alerts?limit=${limit}`)
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
    return http.post(`/work-orders/${workOrderId}/accept`)
  },
  startWorkOrder(workOrderId: number) {
    return http.post(`/work-orders/${workOrderId}/start`)
  },
  getWorkOrderDetail(workOrderId: number) {
    return http.get(`/work-orders/${workOrderId}`)
  },
  dispatchWorkOrder(workOrderId: number, data: { assigneeId: number; dispatchNotes?: string }) {
    return http.post(`/work-orders/${workOrderId}/dispatch`, data)
  },
  submitWorkOrderResult(workOrderId: number, data: { resultSummary: string; photoUrls?: string[]; isExemptPhoto?: boolean; photoExemptionReason?: string }) {
    return http.post(`/work-orders/${workOrderId}/execution/submit`, data)
  },
  reviewWorkOrder(workOrderId: number, data: { action: string; reviewComment?: string }) {
    return http.post(`/work-orders/${workOrderId}/review`, data)
  },

  // 文件上传 (工单履约凭据照片)
  uploadPhotoEvidence(file: File) {
    const formData = new FormData()
    formData.append('file', file)
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
    return http.post(`/work-orders/${workOrderId}/dispatch`, data)
  },
}
