package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfRollbackAction;
import com.zwinsight.workflow.mapper.WfRollbackActionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回滚服务 - 管理审批流程中的数据回写操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RollbackService {

    private final WfRollbackActionMapper rollbackActionMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 注册回写操作
     *
     * @param processInstanceId 流程实例ID
     * @param table             目标表名
     * @param field             目标字段
     * @param targetId          目标记录ID
     * @param opType            操作类型（ADD/SUBTRACT/SET）
     * @param value             操作值
     * @param originalValue     原始值
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerAction(String processInstanceId, String table, String field,
                               Long targetId, String opType, BigDecimal value, BigDecimal originalValue) {
        // 获取当前最大排序号
        LambdaQueryWrapper<WfRollbackAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfRollbackAction::getProcessInstanceId, processInstanceId)
                .orderByDesc(WfRollbackAction::getSortOrder)
                .last("LIMIT 1");
        WfRollbackAction lastAction = rollbackActionMapper.selectOne(wrapper);
        int nextSort = (lastAction != null) ? lastAction.getSortOrder() + 1 : 1;

        WfRollbackAction action = new WfRollbackAction();
        action.setProcessInstanceId(processInstanceId);
        action.setTargetTable(table);
        action.setTargetField(field);
        action.setTargetId(targetId);
        action.setOperationType(opType);
        action.setOperValue(value);
        action.setOriginalValue(originalValue);
        action.setExecuted(0);
        action.setSortOrder(nextSort);
        rollbackActionMapper.insert(action);

        log.info("注册回滚操作, processInstanceId={}, table={}, field={}, targetId={}, opType={}",
                processInstanceId, table, field, targetId, opType);
    }

    /**
     * 逆序执行回滚
     *
     * @param processInstanceId 流程实例ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(String processInstanceId) {
        LambdaQueryWrapper<WfRollbackAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfRollbackAction::getProcessInstanceId, processInstanceId)
                .eq(WfRollbackAction::getExecuted, 0)
                .orderByDesc(WfRollbackAction::getSortOrder);
        List<WfRollbackAction> actions = rollbackActionMapper.selectList(wrapper);

        if (actions.isEmpty()) {
            log.info("没有需要回滚的操作, processInstanceId={}", processInstanceId);
            return;
        }

        for (WfRollbackAction action : actions) {
            executeRollback(action);
            action.setExecuted(1);
            rollbackActionMapper.updateById(action);
        }

        log.info("回滚执行完成, processInstanceId={}, 操作数={}", processInstanceId, actions.size());
    }

    /**
     * 查询已注册操作
     *
     * @param processInstanceId 流程实例ID
     * @return 回滚操作列表
     */
    public List<WfRollbackAction> getRollbackActions(String processInstanceId) {
        LambdaQueryWrapper<WfRollbackAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfRollbackAction::getProcessInstanceId, processInstanceId)
                .orderByAsc(WfRollbackAction::getSortOrder);
        return rollbackActionMapper.selectList(wrapper);
    }

    // ===== 私有方法 =====

    private void executeRollback(WfRollbackAction action) {
        String sql;
        switch (action.getOperationType()) {
            case "ADD":
                // 原操作是加，回滚则减
                sql = String.format("UPDATE %s SET %s = %s - ? WHERE id = ?",
                        action.getTargetTable(), action.getTargetField(), action.getTargetField());
                jdbcTemplate.update(sql, action.getOperValue(), action.getTargetId());
                break;
            case "SUBTRACT":
                // 原操作是减，回滚则加
                sql = String.format("UPDATE %s SET %s = %s + ? WHERE id = ?",
                        action.getTargetTable(), action.getTargetField(), action.getTargetField());
                jdbcTemplate.update(sql, action.getOperValue(), action.getTargetId());
                break;
            case "SET":
                // 原操作是设置，回滚则恢复原始值
                sql = String.format("UPDATE %s SET %s = ? WHERE id = ?",
                        action.getTargetTable(), action.getTargetField());
                jdbcTemplate.update(sql, action.getOriginalValue(), action.getTargetId());
                break;
            default:
                throw new BusinessException("未知的回滚操作类型: " + action.getOperationType());
        }

        log.debug("执行回滚: table={}, field={}, id={}, opType={}",
                action.getTargetTable(), action.getTargetField(), action.getTargetId(), action.getOperationType());
    }
}
