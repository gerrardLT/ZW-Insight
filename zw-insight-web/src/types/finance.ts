/**
 * 财务管理相关类型定义
 */
import type { PageQuery, ID } from './api'

/** 开票申请 */
export interface InvoiceApply {
  id: ID
  projectId: ID
  contractId: ID
  invoiceType?: string
  invoiceAmount: number
  invoiceTitle?: string
  taxpayerId?: string
  bankAccount?: string
  bankName?: string
  contractAmount?: number
  settlementAmount?: number
  historicalInvoiced?: number
  status: string
  workflowInstanceId?: string
  createdAt?: string
}

/** 开票申请创建请求 */
export interface InvoiceApplyCreateRequest {
  projectId: ID
  contractId: ID
  invoiceType?: string
  invoiceAmount: number
  taxRate?: number
  applyDate?: string
  invoiceTitle?: string
  taxpayerId?: string
  bankAccount?: string
  bankName?: string
}

/** 开票分页查询参数 */
export interface InvoiceApplyPageQuery extends PageQuery {
  projectId?: ID
  contractId?: ID
}

/** 回款登记 */
export interface PaymentReceived {
  id: ID
  projectId: ID
  projectName?: string
  contractId?: ID
  receiveDate: string
  receiveAmount: number
  receiver?: string
  receiveType?: string
  receiveBankAccount?: string
  status?: string
  createdAt?: string
}

/** 付款申请 */
export interface PaymentApply {
  id: ID
  projectId: ID
  contractId?: ID
  contractCategory?: string
  supplierId?: ID
  supplierName?: string
  paymentAmount: number
  paymentDate?: string
  cumulativeSettlement?: number
  unpaidAmount?: number
  status: string
  workflowInstanceId?: string
  createdAt?: string
}

/** 付款申请创建请求 */
export interface PaymentApplyCreateRequest {
  projectId: ID
  contractId?: ID
  contractCategory?: string
  supplierId?: ID
  supplierName?: string
  paymentAmount: number
  paymentDate?: string
}

/** 收票登记 */
export interface InvoiceReceived {
  id: ID
  projectId: ID
  contractId?: ID
  invoiceCode?: string
  invoiceAmount: number
  invoiceDate?: string
  supplierName?: string
  status: string
  createdAt?: string
}

/** 发票明细汇总（按项目维度） */
export interface InvoiceSummary {
  projectId: ID
  projectName?: string
  invoicedCount: number
  invoicedAmount: number
  invoicedTaxAmount: number
  receivedCount: number
  receivedAmount: number
  receivedTaxAmount: number
}

/** 发票汇总查询参数 */
export interface InvoiceSummaryQuery {
  projectId?: ID
  startDate?: string
  endDate?: string
}

/** 其他费用付款 */
export interface OtherPayment {
  id: ID
  projectId: ID
  payerName?: string
  paymentDate?: string
  paymentAmount: number
  remark?: string
  status: string
  createdAt?: string
}

/** 项目报销 */
export interface ProjectReimbursement {
  id: ID
  projectId: ID
  totalAmount: number
  reimbursementDate?: string
  offsetReserve?: number
  reserveApplyId?: ID
  offsetAmount?: number
  status: string
  workflowInstanceId?: string
  createdAt?: string
}

/** 备用金申请 */
export interface ReserveFundApply {
  id: ID
  projectId: ID
  applicant?: string
  applyDate?: string
  applyAmount: number
  returnedAmount?: number
  offsetAmount?: number
  status: string
  workflowInstanceId?: string
  createdAt?: string
}

/** 备用金归还 */
export interface ReserveFundReturn {
  id?: ID
  reserveApplyId: ID
  returnAmount: number
  returnDate?: string
}

/** 个人报销 */
export interface PersonalReimbursement {
  id: ID
  totalAmount: number
  reimbursementDate?: string
  remark?: string
  status: string
  workflowInstanceId?: string
  createdAt?: string
}

/** 质保金 */
export interface RetentionMoney {
  id: ID
  projectId: ID
  contractId?: ID
  retentionRate?: number
  retentionAmount: number
  retentionPeriod?: number
  startDate?: string
  expireDate?: string
  returnedAmount?: number
  status: string
  createdAt?: string
}

/** 质保金返还 */
export interface RetentionReturn {
  id?: ID
  retentionId: ID
  returnAmount: number
  returnDate?: string
  status?: string
}
