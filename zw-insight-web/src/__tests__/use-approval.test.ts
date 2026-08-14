/**
 * useApproval 组合式函数单元测试（2026-08-14 前端深度补测）
 *
 * 覆盖：approve/reject/rejectToInitiator/transfer/terminate 五类审批操作
 * 的请求载荷、默认备注、成功/失败返回值、loading 复位、
 * transfer 缺转办人前置校验、terminate 确认取消短路。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { post: vi.fn() }
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useApproval } from '@/composables/useApproval'

const mockPost = vi.mocked(request.post)
const mockSuccess = vi.mocked(ElMessage.success)
const mockWarning = vi.mocked(ElMessage.warning)
const mockConfirm = vi.mocked(ElMessageBox.confirm)

describe('composables/useApproval', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('approve：载荷含默认备注「同意」，成功返回 true', async () => {
    mockPost.mockResolvedValue({})
    const { approve, loading } = useApproval()

    const result = await approve({ taskId: 't-1' })

    expect(result).toBe(true)
    expect(mockPost).toHaveBeenCalledWith('/v1/workflow/approval/complete', {
      taskId: 't-1',
      comment: '同意',
      variables: undefined
    })
    expect(mockSuccess).toHaveBeenCalledWith('审批通过')
    expect(loading.value).toBe(false)
  })

  it('approve：自定义备注与 variables 透传', async () => {
    mockPost.mockResolvedValue({})
    const { approve } = useApproval()
    await approve({ taskId: 't-1', comment: '同意，注意安全', variables: { level: 1 } })
    expect(mockPost).toHaveBeenCalledWith('/v1/workflow/approval/complete', {
      taskId: 't-1',
      comment: '同意，注意安全',
      variables: { level: 1 }
    })
  })

  it('approve：请求失败返回 false 且 loading 复位', async () => {
    mockPost.mockRejectedValue(new Error('500'))
    const { approve, loading } = useApproval()
    await expect(approve({ taskId: 't-1' })).resolves.toBe(false)
    expect(loading.value).toBe(false)
  })

  it('reject：默认备注「退回」', async () => {
    mockPost.mockResolvedValue({})
    const { reject } = useApproval()
    await reject({ taskId: 't-2' })
    expect(mockPost).toHaveBeenCalledWith('/v1/workflow/approval/reject', {
      taskId: 't-2',
      comment: '退回'
    })
  })

  it('rejectToInitiator：命中独立端点', async () => {
    mockPost.mockResolvedValue({})
    const { rejectToInitiator } = useApproval()
    await rejectToInitiator({ taskId: 't-3' })
    expect(mockPost).toHaveBeenCalledWith('/v1/workflow/approval/reject-to-initiator', {
      taskId: 't-3',
      comment: '退回至发起人'
    })
  })

  it('transfer：缺 targetUserId 时前置拒绝且不发请求', async () => {
    const { transfer } = useApproval()
    const result = await transfer({ taskId: 't-4' })
    expect(result).toBe(false)
    expect(mockWarning).toHaveBeenCalledWith('请选择转办人')
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('transfer：载荷含转办人', async () => {
    mockPost.mockResolvedValue({})
    const { transfer } = useApproval()
    const result = await transfer({ taskId: 't-4', targetUserId: 'u-9' })
    expect(result).toBe(true)
    expect(mockPost).toHaveBeenCalledWith('/v1/workflow/approval/transfer', {
      taskId: 't-4',
      targetUserId: 'u-9',
      comment: '转办'
    })
  })

  it('terminate：用户取消确认 → 返回 false 且不发请求', async () => {
    mockConfirm.mockRejectedValue(new Error('cancel'))
    const { terminate } = useApproval()
    const result = await terminate({ taskId: 't-5' })
    expect(result).toBe(false)
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('terminate：确认后发请求并返回 true', async () => {
    mockConfirm.mockResolvedValue('confirm' as never)
    mockPost.mockResolvedValue({})
    const { terminate } = useApproval()
    const result = await terminate({ taskId: 't-5', comment: '终止该流程' })
    expect(result).toBe(true)
    expect(mockPost).toHaveBeenCalledWith('/v1/workflow/approval/terminate', {
      taskId: 't-5',
      comment: '终止该流程'
    })
  })
})
