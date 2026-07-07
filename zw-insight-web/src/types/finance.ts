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
  contractId: ID
  receiveDate: string
  receiveAmount: number
  receiver?: string
  receiveType?: string
  bankAccount?: string
  status: string
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
