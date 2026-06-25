import request from '@/utils/request'

// ======================== 项目档案 ========================
export function getProjectArchive(projectId: number) {
  return request.get(`/v1/archive/project/${projectId}`)
}

// ======================== 投标档案 ========================
export function getTenderArchive(registerId: number) {
  return request.get(`/v1/archive/tender/${registerId}`)
}

// ======================== 预算档案 ========================
export function getBudgetArchive(projectId: number) {
  return request.get(`/v1/archive/budget/${projectId}`)
}

// ======================== 合同档案 ========================
export function getContractArchive(contractId: number) {
  return request.get(`/v1/archive/contract/${contractId}`)
}

// ======================== 供应商档案 ========================
export function getSupplierArchive(supplierId: number) {
  return request.get(`/v1/archive/supplier/${supplierId}`)
}

// ======================== 材料合同档案 ========================
export function getMaterialContractArchive(contractId: number) {
  return request.get(`/v1/archive/material-contract/${contractId}`)
}

// ======================== 分包档案 ========================
export function getSubcontractArchive(contractId: number) {
  return request.get(`/v1/archive/subcontract/${contractId}`)
}

// ======================== 机械合同档案 ========================
export function getMachineContractArchive(contractId: number) {
  return request.get(`/v1/archive/machine-contract/${contractId}`)
}

// ======================== 人事档案 ========================
export function getPersonnelArchive(userId: number) {
  return request.get(`/v1/archive/personnel/${userId}`)
}

// ======================== 车辆档案 ========================
export function getVehicleArchive(vehicleId: number) {
  return request.get(`/v1/archive/vehicle/${vehicleId}`)
}

// ======================== 兼容别名（页面引用传统 CRUD） ========================
// archive/index.vue 使用传统分页列表模式
export function getArchivePage(params: any) {
  return request.get(`/v1/archive/project/${params.projectId || 0}`)
}

export function createArchive(data: any) {
  return Promise.resolve({ code: 200, data: null })
}

export function updateArchive(data: any) {
  return Promise.resolve({ code: 200, data: null })
}

export function deleteArchive(id: number) {
  return Promise.resolve({ code: 200, data: null })
}
