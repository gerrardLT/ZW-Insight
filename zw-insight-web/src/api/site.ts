import request from '@/utils/request'

// ======================== 进度计划 ========================
export function getSchedulePage(params: any) {
  return request.get('/v1/site/schedule/page', { params })
}

export function getScheduleDetail(id: number) {
  return request.get(`/v1/site/schedule/${id}`)
}

export function createSchedule(data: any) {
  return request.post('/v1/site/schedule', data)
}

export function updateSchedule(data: any) {
  return request.put('/v1/site/schedule', data)
}

export function deleteSchedule(id: number) {
  return request.delete(`/v1/site/schedule/${id}`)
}

// ======================== 进度反馈 ========================
export function getScheduleFeedbackPage(params: any) {
  return request.get('/v1/site/schedule-feedback/page', { params })
}

export function createScheduleFeedback(data: any) {
  return request.post('/v1/site/schedule-feedback', data)
}

export function updateScheduleFeedback(data: any) {
  return request.put('/v1/site/schedule-feedback', data)
}

export function deleteScheduleFeedback(id: number) {
  return request.delete(`/v1/site/schedule-feedback/${id}`)
}

// ======================== 施工日志 ========================
export function getConstructionLogPage(params: any) {
  return request.get('/v1/site/construction-log/page', { params })
}

export function getConstructionLogDetail(id: number) {
  return request.get(`/v1/site/construction-log/${id}`)
}

export function createConstructionLog(data: any) {
  return request.post('/v1/site/construction-log', data)
}

export function updateConstructionLog(data: any) {
  return request.put('/v1/site/construction-log', data)
}

export function deleteConstructionLog(id: number) {
  return request.delete(`/v1/site/construction-log/${id}`)
}

// ======================== 质量检查 ========================
export function getQualityInspectionPage(params: any) {
  return request.get('/v1/site/quality-inspection/page', { params })
}

export function getQualityInspectionDetail(id: number) {
  return request.get(`/v1/site/quality-inspection/${id}`)
}

export function createQualityInspection(data: any) {
  return request.post('/v1/site/quality-inspection', data)
}

export function updateQualityInspection(data: any) {
  return request.put('/v1/site/quality-inspection', data)
}

export function deleteQualityInspection(id: number) {
  return request.delete(`/v1/site/quality-inspection/${id}`)
}

// ======================== 安全检查 ========================
export function getSafetyInspectionPage(params: any) {
  return request.get('/v1/site/safety-inspection/page', { params })
}

export function getSafetyInspectionDetail(id: number) {
  return request.get(`/v1/site/safety-inspection/${id}`)
}

export function createSafetyInspection(data: any) {
  return request.post('/v1/site/safety-inspection', data)
}

export function updateSafetyInspection(data: any) {
  return request.put('/v1/site/safety-inspection', data)
}

export function deleteSafetyInspection(id: number) {
  return request.delete(`/v1/site/safety-inspection/${id}`)
}

// ======================== 竣工验收 ========================
export function getAcceptancePage(params: any) {
  return request.get('/v1/site/acceptance/page', { params })
}

export function createAcceptance(data: any) {
  return request.post('/v1/site/acceptance', data)
}

export function updateAcceptance(data: any) {
  return request.put('/v1/site/acceptance', data)
}

export function deleteAcceptance(id: number) {
  return request.delete(`/v1/site/acceptance/${id}`)
}
