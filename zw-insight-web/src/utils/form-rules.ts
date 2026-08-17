/**
 * 表单校验规则工厂 — 纯函数逻辑
 * 供各业务页面 formRules 复用，风格对齐 serial-number-validation.ts
 */

/**
 * 金额正值校验规则（必填 + 大于 0）
 * 背景：el-input-number :min="0" 允许 0，required 规则不拦截数字 0，
 * 0 元单据可穿透进审批流（审计缺陷 D3）；后端 service 层有同款守卫兜底
 * （zw-finance 四处先例："XX金额必须大于0"）。
 * @param message 校验失败提示，默认"金额必须大于 0"
 * @returns Element Plus formRules 规则数组
 */
export function positiveAmount(message = '金额必须大于 0') {
  return [
    {
      validator: (_rule: any, value: any, callback: (error?: Error) => void) => {
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error(message))
        } else {
          callback()
        }
      },
      trigger: ['blur', 'change'],
    },
  ]
}
