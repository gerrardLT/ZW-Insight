package com.zwinsight.workflow.service.rollback;

import java.util.Map;

/**
 * 回滚策略接口
 * <p>
 * 各业务模块实现此接口，注册到 RollbackStrategyRegistry 中。
 * 当审批驳回/撤回时，系统根据 bizType 查找对应策略执行回滚。
 * </p>
 */
public interface RollbackStrategy {

    /**
     * 获取业务类型标识
     *
     * @return 业务类型（如 BUDGET_CHANGE、CONTRACT_CHANGE 等）
     */
    String getBizType();

    /**
     * 执行回滚
     *
     * @param bizId        业务记录ID
     * @param snapshotData 快照数据（字段名 → 原始值）
     */
    void rollback(Long bizId, Map<String, Object> snapshotData);
}
