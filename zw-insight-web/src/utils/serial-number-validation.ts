/**
 * 编号规则表单校验 — 纯函数逻辑
 * 供前端表单校验和属性测试使用
 */

/**
 * 校验 businessType 字段
 * 规则：仅允许字母、数字和下划线，长度 1-50
 * @param value 待校验值
 * @returns true 表示校验通过，false 表示校验失败
 */
export function validateBusinessType(value: string): boolean {
  return /^[a-zA-Z0-9_]{1,50}$/.test(value)
}

/**
 * 校验 seqLength 字段
 * 规则：必须为 1-10 范围内的整数
 * @param value 待校验值
 * @returns true 表示校验通过，false 表示校验失败
 */
export function validateSeqLength(value: number): boolean {
  return Number.isInteger(value) && value >= 1 && value <= 10
}
