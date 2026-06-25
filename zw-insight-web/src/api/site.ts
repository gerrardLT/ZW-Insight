import request from '@/utils/request'

// ======================== 进度计划 ========================
export function getSchedulePage(params: any) {
  return request.get('/v1/site/schedule/page', { params })
}

/** 获取项目进度计划树形数据（甘特图用） */
export function getSchedulePlanTree(projectId: number) {
  return request.get('/v1/site/schedule/plan', { params: { projectId } })
}

/** 更新进度计划日期（甘特图拖拽后保存） */
export function updateSchedulePlanDates(id: number, data: { planStartDate: string; planEndDate: string }) {
  return request.put(`/v1/site/schedule/plan/${id}`, data)
}

export function getScheduleDetail(id: number) {
  return request.get(`/v1/site/schedule/plan/${id}`)
}

export function createSchedule(data: any) {
  return request.post('/v1/site/schedule/plan', data)
}

export function updateSchedule(data: any) {
  return request.put(`/v1/site/schedule/plan/${data.id}`, data)
}

export function deleteSchedule(id: number) {
  return request.delete(`/v1/site/schedule/${id}`)
}

// ======================== 进度反馈 ========================
export function getScheduleFeedbackPage(params: any) {
  return request.get('/v1/site/schedule/feedback/page', { params })
}

export function createScheduleFeedback(data: any) {
  return request.post('/v1/site/schedule/feedback', data)
}

export function submitScheduleFeedback(id: number) {
  return request.post(`/v1/site/schedule/feedback/${id}/submit`)
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
  return request.put(`/v1/site/construction-log/${data.id}`, data)
}

export function deleteConstructionLog(id: number) {
  return request.delete(`/v1/site/construction-log/${id}`)
}

// ======================== 质量安全检查 ========================
export function getInspectionPage(params: any) {
  return request.get('/v1/site/inspection/page', { params })
}

export function createInspection(data: any) {
  return request.post('/v1/site/inspection', data)
}

// 兼容别名：页面引用质量检查和安全检查分别的函数
export function getQualityInspectionPage(params: any) {
  return request.get('/v1/site/inspection/page', { params: { ...params, inspectionType: 'quality' } })
}

export function createQualityInspection(data: any) {
  return request.post('/v1/site/inspection', { ...data, inspectionType: 'quality' })
}

export function updateQualityInspection(data: any) {
  return request.put(`/v1/site/inspection/${data.id}`, data)
}

export function deleteQualityInspection(id: number) {
  return request.delete(`/v1/site/inspection/${id}`)
}

export function getSafetyInspectionPage(params: any) {
  return request.get('/v1/site/inspection/page', { params: { ...params, inspectionType: 'safety' } })
}

export function createSafetyInspection(data: any) {
  return request.post('/v1/site/inspection', { ...data, inspectionType: 'safety' })
}

export function updateSafetyInspection(data: any) {
  return request.put(`/v1/site/inspection/${data.id}`, data)
}

export function deleteSafetyInspection(id: number) {
  return request.delete(`/v1/site/inspection/${id}`)
}

export function assignRectification(id: number, data: any) {
  return request.post(`/v1/site/inspection/${id}/assign`, data)
}

// ======================== 整改管理 ========================
export function submitRectification(inspectionId: number, data: any) {
  return request.post(`/v1/site/rectification/${inspectionId}/submit`, data)
}

export function approveRectification(id: number) {
  return request.post(`/v1/site/rectification/${id}/approve`)
}

// ======================== 竣工验收 ========================
export function getAcceptancePage(params: any) {
  return request.get('/v1/site/completion/page', { params })
}

export function createAcceptance(data: any) {
  return request.post('/v1/site/completion', data)
}

export function submitAcceptance(id: number) {
  return request.post(`/v1/site/completion/${id}/submit`)
}
