import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

/** 审批操作类型 */
export type ApprovalAction = 'approve' | 'reject' | 'rejectToInitiator' | 'transfer' | 'terminate'

/** 审批操作参数 */
export interface ApprovalParams {
  taskId: string
  comment?: string
  targetUserId?: string
  variables?: Record<string, unknown>
}

/**
 * 审批通用逻辑组合式函数
 * 封装审批通过、退回、转办、终止等操作
 *
 * @example
 * const { approve, reject, rejectToInitiator, transfer, terminate, loading } = useApproval()
 * await approve({ taskId: 'xxx', comment: '同意' })
 */
export function useApproval() {
  const loading = ref(false)

  /** 审批通过 */
  async function approve(params: ApprovalParams) {
    loading.value = true
    try {
      await request.post('/v1/workflow/approval/complete', {
        taskId: params.taskId,
        comment: params.comment || '同意',
        variables: params.variables
      })
      ElMessage.success('审批通过')
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 退回至上一节点 */
  async function reject(params: ApprovalParams) {
    loading.value = true
    try {
      await request.post('/v1/workflow/approval/reject', {
        taskId: params.taskId,
        comment: params.comment || '退回'
      })
      ElMessage.success('已退回')
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 退回至发起人 */
  async function rejectToInitiator(params: ApprovalParams) {
    loading.value = true
    try {
      await request.post('/v1/workflow/approval/reject-to-initiator', {
        taskId: params.taskId,
        comment: params.comment || '退回至发起人'
      })
      ElMessage.success('已退回至发起人')
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 转办 */
  async function transfer(params: ApprovalParams) {
    if (!params.targetUserId) {
      ElMessage.warning('请选择转办人')
      return false
    }
    loading.value = true
    try {
      await request.post('/v1/workflow/approval/transfer', {
        taskId: params.taskId,
        targetUserId: params.targetUserId,
        comment: params.comment || '转办'
      })
      ElMessage.success('转办成功')
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /** 终止流程 */
  async function terminate(params: ApprovalParams) {
    try {
      await ElMessageBox.confirm('确定终止该审批流程吗？终止后不可恢复。', '终止确认', {
        type: 'warning'
      })
    } catch {
      return false
    }

    loading.value = true
    try {
      await request.post('/v1/workflow/approval/terminate', {
        taskId: params.taskId,
        comment: params.comment || '终止'
      })
      ElMessage.success('已终止')
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  return { approve, reject, rejectToInitiator, transfer, terminate, loading }
}
