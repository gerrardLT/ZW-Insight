/**
 * 合同管理相关类型定义
 */
import type { PageQuery, ID } from './api'

/** 施工合同实体 */
export interface ConstructionContract {
  id: ID
  projectId: ID
  projectName?: string
  contractCode: string
  contractType: string
  parentContractId?: ID
  partyAName?: string
  partyAId?: ID
  signingDate?: string
  startDate?: string
  endDate?: string
  contractAmount: number
  taxRate?: number
  amountWithoutTax?: number
  taxAmount?: number
  cumulativeChangeAmount: number
  cumulativeOutput: number
  cumulativeInvoiceAmount: number
  cumulativeReceivedAmount: number
  status: string
  workflowInstanceId?: string
  createdAt?: string
  updatedAt?: string
}

/** 施工合同创建/编辑请求 */
export interface ContractCreateRequest {
  projectId: ID
  contractType: string
  parentContractId?: ID
  partyAName?: string
  partyAId?: ID
  signingDate?: string
  startDate?: string
  endDate?: string
  contractAmount: number
  taxRate?: number
  amountWithoutTax?: number
  taxAmount?: number
}

/** 合同分页查询参数 */
export interface ContractPageQuery extends PageQuery {
  projectId?: ID
  status?: string
}

/** 合同明细 */
export interface ContractDetail {
  id?: ID
  contractId: ID
  contractTable?: string
  itemName: string
  specification?: string
  unit?: string
  quantity?: number
  unitPrice?: number
  totalPrice?: number
  taxRate?: number
  remark?: string
  sortOrder?: number
}

/** 变更签证 */
export interface ChangeVisa {
  id: ID
  contractId: ID
  changeType: string
  changeContent?: string
  changeAmount?: number
  status: string
  createdAt?: string
}

/** 其他合同（采购/劳务/机械/分包） */
export interface OtherContract {
  id: ID
  projectId: ID
  contractCode: string
  contractCategory: string
  partyAId?: ID
  partyAName?: string
  partyBId?: ID
  partyBName?: string
  signingDate?: string
  contractAmount: number
  taxRate?: number
  amountWithoutTax?: number
  taxAmount?: number
  cumulativeSettlement: number
  cumulativePaid: number
  cumulativeInvoiceReceived: number
  status: string
  workflowInstanceId?: string
  createdAt?: string
}

/** 产值报告 */
export interface OutputReport {
  id: ID
  projectId: ID
  contractId: ID
  reportPeriod?: string
  currentOutput: number
  cumulativeOutput?: number
  confirmDate?: string
  status: string
}
