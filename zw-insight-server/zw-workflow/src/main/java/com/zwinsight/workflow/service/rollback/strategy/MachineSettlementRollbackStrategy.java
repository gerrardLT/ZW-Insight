package com.zwinsight.workflow.service.rollback.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 机械结算回滚策略
 */
@Slf4j
@Component
public class MachineSettlementRollbackStrategy extends AbstractFieldRollbackStrategy {

    public MachineSettlementRollbackStrategy(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public String getBizType() {
        return "MACHINE_SETTLEMENT";
    }

    @Override
    protected String getTableName() {
        return "biz_machine_settlement";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long bizId, Map<String, Object> snapshotData) {
        super.rollback(bizId, snapshotData);
    }
}
