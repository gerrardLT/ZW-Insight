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

// ======================== 其它收入合同档案（只读聚合列表） ========================
export function getOtherIncomeContractArchive(params: { page?: number; size?: number; keyword?: string }) {
  return request.get('/v1/archive/other-income-contract', { params })
}

// ======================== 其它支出合同档案（只读聚合列表） ========================
export function getOtherExpenseContractArchive(params: { page?: number; size?: number; keyword?: string }) {
  return request.get('/v1/archive/other-expense-contract', { params })
}

// ======================== 办公用品档案（只读聚合列表） ========================
export function getOfficeSupplyArchive(params: { page?: number; size?: number; keyword?: string }) {
  return request.get('/v1/archive/office-supply', { params })
}
