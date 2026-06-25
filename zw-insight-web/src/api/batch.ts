import request from '@/utils/request'

// ======================== 批量导入导出 ========================

export function importData(moduleCode: string, file: File, projectId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('moduleCode', moduleCode)
  if (projectId) formData.append('projectId', String(projectId))
  return request.post('/v1/batch/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function startExport(moduleCode: string, params?: any) {
  return request.post('/v1/batch/export', { moduleCode, params })
}

export function getExportStatus(taskId: number) {
  return request.get(`/v1/batch/export/${taskId}/status`)
}

export function downloadExportFile(taskId: number) {
  return request.get(`/v1/batch/export/${taskId}/download`, { responseType: 'blob' })
}

export function downloadTemplate(moduleCode: string) {
  return request.get(`/v1/batch/template/${moduleCode}`, { responseType: 'blob' })
}

// ======================== 文件预览 ========================

export function getFilePreviewUrl(fileId: number) {
  return request.get('/v1/file/preview-url', { params: { fileId } })
}

// ======================== 模板管理 ========================

export function getTemplateList(params?: { moduleCode?: string; templateType?: string }) {
  return request.get('/v1/file/template', { params })
}

export function createTemplate(data: any) {
  return request.post('/v1/file/template', data)
}

export function updateTemplate(id: number, data: any) {
  return request.put(`/v1/file/template/${id}`, data)
}

export function deleteTemplate(id: number) {
  return request.delete(`/v1/file/template/${id}`)
}

export function renderTemplate(id: number, variables: Record<string, any>) {
  return request.post(`/v1/file/template/${id}/render`, variables)
}
