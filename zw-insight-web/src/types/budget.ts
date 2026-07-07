/**
 * 预算管理相关类型定义
 */
import type { PageQuery, ID } from './api'

/** 预算实体 */
export interface Budget {
  id: ID
  projectId: ID
  budgetType: string
  changeSeq: number
  totalAmount: number
  status: string
  workflowInstanceId?: string
  createdAt?: string
  updatedAt?: string
}

/** 预算创建/编辑请求 */
export interface BudgetCreateRequest {
  projectId: ID
  budgetType: string
  totalAmount?: number
}

/** 预算分页查询参数 */
export interface BudgetPageQuery extends PageQuery {
  projectId?: ID
}

/** 预算明细 */
export interface BudgetDetail {
  id?: ID
  budgetId: ID
  costCategory: string
  costSubcategory?: string
  itemName: string
  specification?: string
  unit?: string
  budgetQuantity?: number
  budgetUnitPrice?: number
  budgetTotalPrice?: number
  adjustedQuantity?: number
  adjustedUnitPrice?: number
  adjustedTotalPrice?: number
  remark?: string
}

/** 预算变更请求 */
export interface BudgetChangeRequest {
  projectId: ID
  budgetId: ID
  changeReason: string
  details: BudgetChangeDetailRequest[]
}

/** 预算变更明细 */
export interface BudgetChangeDetailRequest {
  budgetDetailId?: ID
  costCategory: string
  costSubcategory?: string
  itemName: string
  originalQuantity?: number
  originalUnitPrice?: number
  originalTotalPrice?: number
  adjustedQuantity?: number
  adjustedUnitPrice?: number
  adjustedTotalPrice?: number
  adjustAmount?: number
  changeReason?: string
}

/** 预算控制配置 */
export interface BudgetControlConfig {
  id: ID
  projectId?: ID
  controlMode: string
  warningThreshold: number
  enabled: boolean
}

/** 预算二级科目 */
export interface BudgetSubcategory {
  id: ID
  costCategory: string
  subcategoryName: string
  sortOrder?: number
}
