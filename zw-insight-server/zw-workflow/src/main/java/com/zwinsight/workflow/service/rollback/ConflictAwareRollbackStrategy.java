package com.zwinsight.workflow.service.rollback;

import java.util.List;
import java.util.Map;

/**
 * 支持冲突检测的回滚策略接口
 * <p>
 * 实现此接口的策略在回滚前会先进行冲突检测：
 * 比较快照原始值与当前数据库值，如果不一致则标记为冲突。
 * </p>
 */
public interface ConflictAwareRollbackStrategy extends RollbackStrategy {

    /**
     * 检测数据冲突
     * <p>
     * 比较 snapshotData 中记录的原始值与当前DB中的实际值，
     * 返回不一致的字段列表。
     * </p>
     *
     * @param bizId        业务记录ID
     * @param snapshotData 快照数据（字段名 → 快照时的原始值）
     * @return 冲突字段列表（空列表表示无冲突）
     */
    List<String> detectConflicts(Long bizId, Map<String, Object> snapshotData);
}
