import request from '@/utils/request'

// ======================== 项目管理 ========================
export function getProjectPage(params: any) {
  return request.get('/v1/project/page', { params })
}

export function getProjectList(params?: any) {
  return request.get('/v1/project/list', { params })
}

export function getProjectDetail(id: number) {
  return request.get(`/v1/project/${id}`)
}

export function createProject(data: any) {
  return request.post('/v1/project', data)
}

export function updateProject(data: any) {
  return request.put('/v1/project', data)
}

export function deleteProject(id: number) {
  return request.delete(`/v1/project/${id}`)
}

export function submitProject(id: number) {
  return request.put(`/v1/project/${id}/submit`)
}

// ======================== 建设单位（甲方） ========================
export function getOwnerList(params?: any) {
  return request.get('/v1/basedata/owner/list', { params })
}

export function getOwnerPage(params?: any) {
  return request.get('/v1/basedata/owner/page', { params })
}

// ======================== 签约公司 ========================
export function getCompanyList(params?: any) {
  return request.get('/v1/basedata/company/list', { params })
}
