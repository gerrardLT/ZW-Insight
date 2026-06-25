package com.zwinsight.workflow.service.rollback;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.workflow.dto.RollbackLogQuery;
import com.zwinsight.workflow.dto.RollbackLogVO;

import java.util.Map;

/**
 * 审批数据回滚服务接口
 * <p>
 * 提供审批流程中的数据快照保存和回滚执行功能。
 * 当审批驳回或撤回时，自动将业务数据恢复到审批提交前的状态。
 * </p>
 */
public interface ApprovalRollbackService {

    /**
     * 审批提交时保存数据快照
     *
     * @param workflowInstanceId 流程实例ID
     * @param bizType            业务类型
     * @param bizId              业务记录ID
     * @param snapshotData       快照数据（字段名 → 原始值）
     */
    void saveSnapshot(String workflowInstanceId, String bizType, Long bizId, Map<String, Object> snapshotData);

    /**
     * 执行数据回滚
     *
     * @param workflowInstanceId 流程实例ID
     * @return 回滚执行结果
     */
    RollbackResult executeRollback(String workflowInstanceId);

    /**
     * 查询回滚记录
     *
     * @param query 查询参数
     * @return 分页回滚日志列表
     */
    PageResult<RollbackLogVO> queryRollbackLogs(RollbackLogQuery query);

    /**
     * 确认冲突处理
     *
     * @param rollbackLogId 回滚日志ID
     * @param resolution    处理方式描述
     */
    void confirmConflict(Long rollbackLogId, String resolution);
}
