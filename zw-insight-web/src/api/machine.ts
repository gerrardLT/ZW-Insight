import request from '@/utils/request'

// ======================== 机械合同 ========================
export function getMachineContractPage(params: any) {
  return request.get('/v1/machine/contract/page', { params })
}

export function getMachineContractDetail(id: number) {
  return request.get(`/v1/machine/contract/${id}`)
}

export function createMachineContract(data: any) {
  return request.post('/v1/machine/contract', data)
}

export function updateMachineContract(data: any) {
  return request.put('/v1/machine/contract', data)
}

export function deleteMachineContract(id: number) {
  return request.delete(`/v1/machine/contract/${id}`)
}

export function submitMachineContract(id: number) {
  return request.put(`/v1/machine/contract/${id}/submit`)
}

// ======================== 使用记录 ========================
export function getMachineUsagePage(params: any) {
  return request.get('/v1/machine/usage/page', { params })
}

export function createMachineUsage(data: any) {
  return request.post('/v1/machine/usage', data)
}

export function updateMachineUsage(data: any) {
  return request.put('/v1/machine/usage', data)
}

export function deleteMachineUsage(id: number) {
  return request.delete(`/v1/machine/usage/${id}`)
}

// ======================== 机械结算 ========================
export function getMachineSettlementPage(params: any) {
  return request.get('/v1/machine/settlement/page', { params })
}

export function createMachineSettlement(data: any) {
  return request.post('/v1/machine/settlement', data)
}

export function updateMachineSettlement(data: any) {
  return request.put('/v1/machine/settlement', data)
}

export function deleteMachineSettlement(id: number) {
  return request.delete(`/v1/machine/settlement/${id}`)
}

export function submitMachineSettlement(id: number) {
  return request.put(`/v1/machine/settlement/${id}/submit`)
}

// ======================== 机械台账 ========================
export function getMachineLedgerPage(params: any) {
  return request.get('/v1/machine/ledger/page', { params })
}

export function getMachineLedgerDetail(id: number) {
  return request.get(`/v1/machine/ledger/${id}`)
}

export function createMachineLedger(data: any) {
  return request.post('/v1/machine/ledger', data)
}

export function updateMachineLedger(data: any) {
  return request.put('/v1/machine/ledger', data)
}

export function deleteMachineLedger(id: number) {
  return request.delete(`/v1/machine/ledger/${id}`)
}

// ======================== 进出场登记 ========================
export function getMachineEntryPage(params: any) {
  return request.get('/v1/machine/entry/page', { params })
}

export function createMachineEntry(data: any) {
  return request.post('/v1/machine/entry', data)
}

export function updateMachineEntry(data: any) {
  return request.put('/v1/machine/entry', data)
}

export function deleteMachineEntry(id: number) {
  return request.delete(`/v1/machine/entry/${id}`)
}

// ======================== 工作日志/台班 ========================
export function getMachineWorkLogPage(params: any) {
  return request.get('/v1/machine/work-log/page', { params })
}

export function createMachineWorkLog(data: any) {
  return request.post('/v1/machine/work-log', data)
}

export function updateMachineWorkLog(data: any) {
  return request.put('/v1/machine/work-log', data)
}

export function deleteMachineWorkLog(id: number) {
  return request.delete(`/v1/machine/work-log/${id}`)
}

// ======================== 加油记录 ========================
export function getMachineRefuelPage(params: any) {
  return request.get('/v1/machine/refuel/page', { params })
}

export function createMachineRefuel(data: any) {
  return request.post('/v1/machine/refuel', data)
}

export function deleteMachineRefuel(id: number) {
  return request.delete(`/v1/machine/refuel/${id}`)
}

// ======================== 维修记录 ========================
export function getMachineRepairPage(params: any) {
  return request.get('/v1/machine/repair/page', { params })
}

export function createMachineRepair(data: any) {
  return request.post('/v1/machine/repair', data)
}

export function updateMachineRepair(data: any) {
  return request.put('/v1/machine/repair', data)
}

export function deleteMachineRepair(id: number) {
  return request.delete(`/v1/machine/repair/${id}`)
}
