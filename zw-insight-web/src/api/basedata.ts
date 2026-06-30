import request from '@/utils/request'

// ======================== 材料字典 ========================
export function getMaterialDictPage(params: any) {
  return request.get('/v1/basedata/material/page', { params })
}

export function getMaterialDictDetail(id: number) {
  return request.get(`/v1/basedata/material/${id}`)
}

export function createMaterialDict(data: any) {
  return request.post('/v1/basedata/material', data)
}

export function updateMaterialDict(data: any) {
  return request.put(`/v1/basedata/material/${data.id}`, data)
}

export function deleteMaterialDict(id: number) {
  return request.delete(`/v1/basedata/material/${id}`)
}

// ======================== 材料分类 ========================
export function getMaterialCategoryTree() {
  return request.get('/v1/basedata/material-category/tree')
}

export function createMaterialCategory(data: any) {
  return request.post('/v1/basedata/material-category', data)
}

export function updateMaterialCategory(data: any) {
  return request.put(`/v1/basedata/material-category/${data.id}`, data)
}

export function deleteMaterialCategory(id: number) {
  return request.delete(`/v1/basedata/material-category/${id}`)
}

// ======================== 供应商 ========================
export function getSupplierPage(params: any) {
  return request.get('/v1/basedata/supplier/page', { params })
}

export function getSupplierDetail(id: number) {
  return request.get(`/v1/basedata/supplier/${id}`)
}

export function createSupplier(data: any) {
  return request.post('/v1/basedata/supplier', data)
}

export function updateSupplier(data: any) {
  return request.put(`/v1/basedata/supplier/${data.id}`, data)
}

export function deleteSupplier(id: number) {
  return request.delete(`/v1/basedata/supplier/${id}`)
}

// ======================== 甲方单位 ========================
export function getOwnerPage(params: any) {
  return request.get('/v1/basedata/owner/page', { params })
}

export function getOwnerDetail(id: number) {
  return request.get(`/v1/basedata/owner/${id}`)
}

export function createOwner(data: any) {
  return request.post('/v1/basedata/owner', data)
}

export function updateOwner(data: any) {
  return request.put(`/v1/basedata/owner/${data.id}`, data)
}

export function deleteOwner(id: number) {
  return request.delete(`/v1/basedata/owner/${id}`)
}

// ======================== 自持公司 ========================
export function getCompanyPage(params: any) {
  return request.get('/v1/basedata/company/page', { params })
}

export function getCompanyDetail(id: number) {
  return request.get(`/v1/basedata/company/${id}`)
}

export function createCompany(data: any) {
  return request.post('/v1/basedata/company', data)
}

export function updateCompany(data: any) {
  return request.put(`/v1/basedata/company/${data.id}`, data)
}

export function deleteCompany(id: number) {
  return request.delete(`/v1/basedata/company/${id}`)
}

// ======================== 检查方案 ========================
export function getInspectionSchemePage(params: any) {
  return request.get('/v1/basedata/inspection-scheme/page', { params })
}

export function createInspectionScheme(data: any) {
  return request.post('/v1/basedata/inspection-scheme', data)
}

export function updateInspectionScheme(data: any) {
  return request.put(`/v1/basedata/inspection-scheme/${data.id}`, data)
}

export function deleteInspectionScheme(id: number) {
  return request.delete(`/v1/basedata/inspection-scheme/${id}`)
}

// ======================== 供应商评价 ========================
export function getSupplierEvaluationPage(params: any) {
  return request.get('/v1/basedata/supplier-evaluation', { params })
}

export function createSupplierEvaluation(data: any) {
  return request.post('/v1/basedata/supplier-evaluation', data)
}

export function updateSupplierEvaluation(data: any) {
  return request.put(`/v1/basedata/supplier-evaluation/${data.id}`, data)
}

export function deleteSupplierEvaluation(id: number) {
  return request.delete(`/v1/basedata/supplier-evaluation/${id}`)
}

// ======================== 供应商黑名单 ========================
export function getSupplierBlacklistPage(params: any) {
  return request.get('/v1/basedata/supplier-blacklist', { params })
}

export function createSupplierBlacklist(data: any) {
  return request.post('/v1/basedata/supplier-blacklist', data)
}

export function deleteSupplierBlacklist(id: number) {
  return request.delete(`/v1/basedata/supplier-blacklist/${id}`)
}
