/**
 * 枚举基线：业务状态 code -> 中文标签 的独立映射。
 *
 * 用途：列表/详情页把后端返回的状态 code 翻译成中文标签展示，本文件作为
 * 「独立事实基线」用于校验前端翻译是否正确。基线来源于各页面筛选区
 * <el-option> 的产品声明（与表格内 statusMap 是独立声明），并对照种子数据核对。
 *
 * 断言逻辑：后端返回 record.status = 'CONSTRUCTION' → 表格单元格应显示 '施工中'。
 * 若前端漏翻译（显示原始 code）或翻译错误，断言即失败并暴露不一致。
 */

/** 项目状态 */
export const PROJECT_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  FILED: '已报备',
  TENDERING: '招标中',
  WON: '已中标',
  CONSTRUCTION: '施工中',
  COMPLETED: '已竣工',
  CLOSING: '结项审批中',
  CLOSED: '已关闭',
}

/** 施工合同状态（contract/index.vue 筛选区 el-option） */
export const CONTRACT_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  EFFECTIVE: '已生效',
  SETTLED: '已结算',
  CLOSED: '已关闭',
}

/** 预算状态（budget/index.vue 筛选区 el-option） */
export const BUDGET_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已批准',
}

/** 简单合同生效状态（purchase/subcontract/labor 合同列表：EFFECTIVE→生效，其余→草稿） */
export const SIMPLE_CONTRACT_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  EFFECTIVE: '生效',
}

/** 单据审批状态（material 入/出库等：APPROVED→已审批，其余→草稿） */
export const DOC_APPROVE_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVED: '已审批',
}

/** 通用审批状态（付款申请等） */
export const PAYMENT_APPLY_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  PAID: '已付款',
}

/** 开票申请状态（finance/invoice-apply.vue 筛选区 el-option，无 PAID） */
export const INVOICE_APPLY_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
}

/** 结算单状态（采购/分包结算） */
export const SETTLEMENT_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVED: '已审批',
}

/** 机械进出场类型 */
export const MACHINE_ENTRY_TYPE: Record<string, string> = {
  IN: '进场',
  OUT: '出场',
}

/** 存储类型 */
export const STORAGE_TYPE: Record<string, string> = {
  LOCAL: '本地存储',
  MINIO: 'MinIO',
  ALIYUN: '阿里云 OSS',
  TENCENT: '腾讯云 COS',
  QINIU: '七牛云',
}

/** 通用审批流程状态（工作流/单据通用，按需在各 spec 使用/扩展） */
export const APPROVAL_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  PAID: '已付款',
  CLOSED: '已关闭',
}

/** 投标报名状态（tender/register.vue statusLabelMap，缺失 code 回退原值） */
export const TENDER_REGISTER_STATUS: Record<string, string> = {
  REGISTERED: '报名中',
  SUBMITTED: '已投标',
  WON: '中标',
  LOST: '未中标',
}

/** 离职申请状态（hr/resign-apply.vue statusMap，缺失 code 回退原值） */
export const RESIGN_STATUS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
}

/**
 * 各模块枚举待写 spec 时按实际页面 <el-option> 声明补充：
 * - contract / budget / labor / material / subcontract / site / tender / hr 等
 * 补充时保持「来源于页面产品声明 + 种子数据核对」的独立性原则。
 */
