import request from '@/utils/request'

// ======================== 审批流程 ========================
// ApprovalController: /api/v1/workflow/approval

export function startProcess(data: any) {
  return request.post('/v1/workflow/approval/start', data)
}

export function completeTask(data: any) {
  return request.post('/v1/workflow/approval/complete', data)
}

export function rejectToPrevious(data: any) {
  return request.post('/v1/workflow/approval/reject-previous', data)
}

export function rejectToStart(data: any) {
  return request.post('/v1/workflow/approval/reject-start', data)
}

export function terminateProcess(data: any) {
  return request.post('/v1/workflow/approval/terminate', data)
}

export function transferTask(data: any) {
  return request.post('/v1/workflow/approval/transfer', data)
}

export function delegateTask(data: any) {
  return request.post('/v1/workflow/approval/delegate', data)
}

export function getTodoTasks(params: any) {
  return request.get('/v1/workflow/approval/todo', { params })
}

export function getDoneTasks(params: any) {
  return request.get('/v1/workflow/approval/done', { params })
}

export function batchApprove(data: any) {
  return request.post('/v1/workflow/approval/batch-approve', data)
}

// ======================== 流程定义 ========================
// ProcessDefinitionController: /api/v1/workflow/process

export function deployProcess(data: FormData) {
  return request.post('/v1/workflow/process/deploy', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getProcessList() {
  return request.get('/v1/workflow/process')
}

export function getProcessImage(id: string) {
  return `/api/v1/workflow/process/${id}/image`
}

export function getProcessVersions(processKey: string) {
  return request.get(`/v1/workflow/process/${processKey}/versions`)
}

// ======================== 业务类型 ========================
// BusinessTypeController: /api/v1/workflow/business-type

export function getBusinessTypeTree() {
  return request.get('/v1/workflow/business-type/tree')
}

export function getBusinessTypeDetail(id: number) {
  return request.get(`/v1/workflow/business-type/${id}`)
}

export function createBusinessType(data: any) {
  return request.post('/v1/workflow/business-type', data)
}

export function updateBusinessType(data: any) {
  return request.put('/v1/workflow/business-type', data)
}

export function deleteBusinessType(id: number) {
  return request.delete(`/v1/workflow/business-type/${id}`)
}

// ======================== 审批委托/代理 ========================
// DelegateController: /api/v1/workflow/delegate

/** 创建委托配置 */
export function createDelegation(data: {
  delegateId: number
  startTime: string
  endTime: string
  reason?: string
}) {
  return request.post('/v1/workflow/delegate', data)
}

/** 取消委托 */
export function cancelDelegation(id: number) {
  return request.delete(`/v1/workflow/delegate/${id}`)
}

/** 查询我的委托配置列表（我委托给别人的） */
export function getMyDelegations() {
  return request.get('/v1/workflow/delegate/my')
}

/** 查询当前生效的委托（我的） */
export function getActiveDelegation() {
  return request.get('/v1/workflow/delegate/active')
}

/** 查询委托给我的（我作为代理人的生效委托列表） */
export function getDelegationsToMe() {
  return request.get('/v1/workflow/delegate/to-me')
}

// ======================== 审批回滚 ========================
// ApprovalRollbackController: /api/v1/workflow/rollback

/** 查询回滚记录列表 */
export function getRollbackLogs(params: {
  workflowInstanceId?: string
  bizType?: string
  rollbackStatus?: number
  page?: number
  size?: number
}) {
  return request.get('/v1/workflow/rollback/logs', { params })
}

/** 确认冲突处理 */
export function confirmRollbackConflict(id: number, data: { resolution: string }) {
  return request.post(`/v1/workflow/rollback/${id}/confirm`, data)
}
