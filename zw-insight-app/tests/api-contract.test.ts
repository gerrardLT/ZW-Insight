/**
 * api 层契约钉住测试（2026-08-15 P3 方向3 补测）
 *
 * 后端 Controller 为 Source of Truth：本测试钉住 app 端 api/*.ts 的
 * 路径/方法/payload 契约，防回归（历史实证：项目列表 /v1/project 裸路径仅
 * POST、材料字典走 /v1/basedata/material 等易错点已在 common.ts 注释固化）。
 * request 层经 vi.mock 隔离，仅断言调用参数。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/utils/request', () => ({ default: mockRequest }))

import * as authApi from '@/api/auth'
import * as commonApi from '@/api/common'
import * as shortcutApi from '@/api/shortcut'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('auth.ts 契约', () => {
  it('login POST /v1/auth/login 透传 payload', async () => {
    await authApi.login({ username: 'u', password: 'p', loginType: 'PASSWORD' } as any)
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/v1/auth/login', method: 'POST',
      data: { username: 'u', password: 'p', loginType: 'PASSWORD' },
    })
  })

  it('忘记密码三步：send-code/verify-code/reset 均 POST', async () => {
    await authApi.sendResetCode('13800138000')
    expect(mockRequest).toHaveBeenLastCalledWith({
      url: '/v1/auth/password-reset/send-code', method: 'POST', data: { phone: '13800138000' },
    })
    await authApi.verifyResetCode('13800138000', '123456')
    expect(mockRequest).toHaveBeenLastCalledWith({
      url: '/v1/auth/password-reset/verify-code', method: 'POST', data: { phone: '13800138000', code: '123456' },
    })
    await authApi.resetPassword('13800138000', '123456', 'newPass')
    expect(mockRequest).toHaveBeenLastCalledWith({
      url: '/v1/auth/password-reset/reset', method: 'POST',
      data: { phone: '13800138000', code: '123456', newPassword: 'newPass' },
    })
  })

  it('changePassword PUT /v1/auth/password；logout POST', async () => {
    await authApi.changePassword({ oldPassword: 'a', newPassword: 'b' })
    expect(mockRequest).toHaveBeenLastCalledWith({
      url: '/v1/auth/password', method: 'PUT', data: { oldPassword: 'a', newPassword: 'b' },
    })
    await authApi.logout()
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/auth/logout', method: 'POST' })
  })
})

describe('common.ts 契约（易错路径钉住）', () => {
  it('项目列表走 /v1/project/list（裸路径 /v1/project 仅 POST 创建，历史实证）', async () => {
    await commonApi.getProjectList({ page: 1, size: 500 })
    expect(mockRequest).toHaveBeenCalledWith({ url: '/v1/project/list', data: { page: 1, size: 500 } })
  })

  it('材料字典走 /v1/basedata/material', async () => {
    await commonApi.getMaterialDict({ materialName: '钢' })
    expect(mockRequest).toHaveBeenCalledWith({ url: '/v1/basedata/material', data: { materialName: '钢' } })
  })

  it('审批办理/退回 POST 正确端点', async () => {
    await commonApi.completeTask({ taskId: 't1' })
    expect(mockRequest).toHaveBeenLastCalledWith({
      url: '/v1/workflow/approval/complete', method: 'POST', data: { taskId: 't1' },
    })
    await commonApi.rejectTask({ taskId: 't1', comment: '退回' })
    expect(mockRequest).toHaveBeenLastCalledWith({
      url: '/v1/workflow/approval/reject-previous', method: 'POST', data: { taskId: 't1', comment: '退回' },
    })
  })

  it('消息已读 PUT 路径拼 id；全部已读 PUT read-all', async () => {
    await commonApi.markMessageRead(77)
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/message/msg/77/read', method: 'PUT' })
    await commonApi.markAllMessagesRead()
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/message/msg/read-all', method: 'PUT' })
  })

  it('材料入/出库与现场三单均 POST 各自端点', async () => {
    const cases: Array<[() => Promise<any>, string]> = [
      [() => commonApi.saveMaterialInbound({ id: 1 }), '/v1/material/inbound'],
      [() => commonApi.saveMaterialOutbound({ id: 1 }), '/v1/material/outbound'],
      [() => commonApi.saveConstructionLog({ id: 1 }), '/v1/site/construction-log'],
      [() => commonApi.saveProgressFeedback({ id: 1 }), '/v1/site/schedule/feedback'],
      [() => commonApi.saveInspection({ id: 1 }), '/v1/site/inspection'],
      [() => commonApi.submitInspectionResults(9, { hasProblem: 0 }), '/v1/site/inspection/9/results'],
    ]
    for (const [fn, url] of cases) {
      await fn()
      expect(mockRequest).toHaveBeenLastCalledWith(expect.objectContaining({ url, method: 'POST' }))
    }
  })

  it('财务八类单据保存 POST 各自端点（含其他付款/备用金/个人报销/收票）', async () => {
    const cases: Array<[() => Promise<any>, string]> = [
      [() => commonApi.saveInvoiceApply({ id: 1 }), '/v1/finance/invoice-apply'],
      [() => commonApi.savePaymentReceived({ id: 1 }), '/v1/finance/payment-received'],
      [() => commonApi.savePaymentApply({ id: 1 }), '/v1/finance/payment-apply'],
      [() => commonApi.saveReimbursement({ id: 1 }), '/v1/finance/project-reimbursement'],
      [() => commonApi.saveOtherPayment({ id: 1 }), '/v1/finance/other-payment'],
      [() => commonApi.saveReserveFundApply({ id: 1 }), '/v1/finance/reserve-fund/apply'],
      [() => commonApi.saveReserveFundReturn({ id: 1 }), '/v1/finance/reserve-fund/return'],
      [() => commonApi.savePersonalReimbursement({ id: 1 }), '/v1/finance/personal-reimbursement'],
      [() => commonApi.saveInvoiceReceived({ id: 1 }), '/v1/finance/invoice-received'],
    ]
    for (const [fn, url] of cases) {
      await fn()
      expect(mockRequest).toHaveBeenLastCalledWith(expect.objectContaining({ url, method: 'POST' }))
    }
  })

  it('看板端点：company-overview/budget-execution/project/{id} 等', async () => {
    await commonApi.getCompanyOverview()
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/dashboard/company-overview' })
    await commonApi.getBudgetExecution({ projectId: 3 })
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/dashboard/budget-execution', data: { projectId: 3 } })
    await commonApi.getProjectDashboard(8)
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/dashboard/project/8' })
    await commonApi.getProjectArchive(5)
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/archive/project/5' })
  })
})

describe('shortcut.ts 契约', () => {
  it('可选列表/用户配置 GET；批量保存 POST 包装 shortcutIds', async () => {
    await shortcutApi.getAvailableShortcuts()
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/message/shortcut/available' })
    await shortcutApi.getUserShortcuts()
    expect(mockRequest).toHaveBeenLastCalledWith({ url: '/v1/message/shortcut' })
    await shortcutApi.batchSaveShortcuts([1, 2, 3])
    expect(mockRequest).toHaveBeenLastCalledWith({
      url: '/v1/message/shortcut/batch', method: 'POST', data: { shortcutIds: [1, 2, 3] },
    })
  })
})
