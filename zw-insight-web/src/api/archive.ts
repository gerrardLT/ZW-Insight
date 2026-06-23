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

// ======================== 档案借阅 ========================
export function getArchiveBorrowPage(params: any) {
  return request.get('/v1/archive/borrow/page', { params })
}

export function createArchiveBorrow(data: any) {
  return request.post('/v1/archive/borrow', data)
}

export function returnArchiveBorrow(id: number) {
  return request.put(`/v1/archive/borrow/${id}/return`)
}
