// 报销费用分析 API（V2026_60 后端 + V2026_65 前端页面）
// 后端：ReimbursementAnalysisController @RequestMapping("/api/v1/finance/reimbursement")
// 对标 docs/资金流转流程.md §6.3 招待费分析维度 + §11 招待费预警项（8 类）
// 呈现原则（驾驶舱 V1 §8.1）：老板默认看异常，正常报销不罗列。
import request from '@/utils/request'
import type { R } from '@/types/api'

/** 招待费聚合指标（§6.3 分析维度） */
export interface EntertainmentSummary {
  /** 累计金额 */
  totalAmount: number
  /** 累计笔数 */
  totalCount: number
  /** 单笔最高金额（对照 §8.1「单笔 >3000 元」预警） */
  maxSingleAmount: number
  /** 人均金额 */
  avgPerCapita: number
  /** 无招待事由笔数 */
  noReasonCount: number
  /** 无招待对象笔数 */
  noHostCount: number
  /** 无事前审批笔数 */
  noPreApprovalCount: number
  /** 发票不完整笔数 */
  noInvoiceCount: number
}

/** 责任人（经办人）累计排行 */
export interface EntertainmentByHandler {
  handlerId?: number
  handlerName?: string
  totalAmount: number
  totalCount: number
}

/**
 * 异常项（§11 八类预警中命中的项）。
 * 后端仅在 count > 0 时下发（正常项隐藏），故本列表为空即代表"无异常"。
 */
export interface EntertainmentAnomaly {
  /** 异常名称（无招待事由/无招待对象/无事前审批/发票不完整/同人同日多笔/超月度限额…） */
  name: string
  count: number
  /** 处置建议 */
  suggestion: string
}

/** 月度趋势行（§6.3「月度变化」，近 6 个月） */
export interface EntertainmentMonthlyTrend {
  /** yyyy-MM，取自主表报销日期（缺失时退回明细创建日） */
  month: string
  amount: number
  cnt: number
}

/**
 * 当月预算执行情况（§6.3 预算执行率 + §11 第 8 类「超月度限额」的分母来源）。
 * 计划额取月度资金计划科目明细的招待费计划额；**未编计划时 hasBaseline=false、
 * planned/rate 为 null、overLimit=false**（不把"未编计划"当"限额 0 元"误报超限）。
 */
export interface EntertainmentBudgetExecution {
  month: string
  actual: number
  planned: number | null
  hasBaseline: boolean
  rate: number | null
  overLimit: boolean
}

/** 招待费分析结果 */
export interface EntertainmentAnalysis {
  summary: EntertainmentSummary
  byHandler: EntertainmentByHandler[]
  anomalies: EntertainmentAnomaly[]
  monthlyTrend: EntertainmentMonthlyTrend[]
  budgetExecution: EntertainmentBudgetExecution
}

/**
 * 招待费分析。
 * @param params.projectId  项目ID（空=全部项目）
 * @param params.sourceType 报销来源（PROJECT/PERSONAL，空=合计）
 */
export function getEntertainmentAnalysis(params?: { projectId?: number; sourceType?: string }) {
  return request.get<R<EntertainmentAnalysis>>(
    '/v1/finance/reimbursement/entertainment-analysis', { params })
}
