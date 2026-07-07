/**
 * 项目管理相关类型定义
 */
import type { PageQuery, ID } from './api'

/** 项目实体 */
export interface Project {
  id: ID
  projectCode: string
  projectName: string
  projectNature?: string
  projectType?: string
  ownerCompanyId?: ID
  ownerCompanyName?: string
  signingCompanyId?: ID
  signingCompanyName?: string
  projectOverview?: string
  projectAddress?: string
  contactName?: string
  contactPhone?: string
  needTender?: number
  status: string
  budgetAmount: number
  contractAmount: number
  cumulativeOutput: number
  settlementAmount: number
  totalIncome: number
  totalExpense: number
  totalOtherPayment: number
  tenantId?: ID
  createdBy?: ID
  createdAt?: string
  updatedAt?: string
}

/** 项目创建/编辑请求 */
export interface ProjectCreateRequest {
  projectName: string
  projectNature?: string
  projectType?: string
  ownerCompanyId?: ID
  ownerCompanyName?: string
  signingCompanyId?: ID
  signingCompanyName?: string
  projectOverview?: string
  projectAddress?: string
  contactName?: string
  contactPhone?: string
  needTender?: number
  budgetAmount?: number
}

/** 项目分页查询参数 */
export interface ProjectPageQuery extends PageQuery {
  projectName?: string
  status?: string
  projectType?: string
}

/** 项目成员 */
export interface ProjectMember {
  id: ID
  projectId: ID
  userId: ID
  userName?: string
  roleType: string
  joinDate?: string
  leaveDate?: string
  status?: number
}

/** 添加项目成员请求 */
export interface ProjectMemberAddRequest {
  userId: ID
  userName?: string
  projectRoles: string[]
}

/** 更新成员角色请求 */
export interface UpdateMemberRolesRequest {
  projectRoles: string[]
}
