import request from '@/utils/request'

// ======================== 档案管理 ========================
export function getArchivePage(params: any) {
  return request.get('/v1/archive/page', { params })
}

export function getArchiveDetail(id: number) {
  return request.get(`/v1/archive/${id}`)
}

export function createArchive(data: any) {
  return request.post('/v1/archive', data)
}

export function updateArchive(data: any) {
  return request.put('/v1/archive', data)
}

export function deleteArchive(id: number) {
  return request.delete(`/v1/archive/${id}`)
}

// ======================== 档案分类 ========================
export function getArchiveCategoryList() {
  return request.get('/v1/archive/category/list')
}

export function createArchiveCategory(data: any) {
  return request.post('/v1/archive/category', data)
}

export function updateArchiveCategory(data: any) {
  return request.put('/v1/archive/category', data)
}

export function deleteArchiveCategory(id: number) {
  return request.delete(`/v1/archive/category/${id}`)
}

// ======================== 项目档案 ========================
export function getProjectArchive(projectId: number) {
  return request.get(`/v1/archive/project/${projectId}`)
}

export function getTenderArchive(registerId: number) {
  return request.get(`/v1/archive/tender/${registerId}`)
}

export function getBudgetArchive(projectId: number) {
  return request.get(`/v1/archive/budget/${projectId}`)
}

export function getContractArchive(contractId: number) {
  return request.get(`/v1/archive/contract/${contractId}`)
}

export function getSupplierArchive(supplierId: number) {
  return request.get(`/v1/archive/supplier/${supplierId}`)
}
