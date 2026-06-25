package com.zwinsight.workflow.service.rollback.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 分包结算回滚策略
 */
@Slf4j
@Component
public class SubcontractSettlementRollbackStrategy extends AbstractFieldRollbackStrategy {

    public SubcontractSettlementRollbackStrategy(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public String getBizType() {
        return "SUBCONTRACT_SETTLEMENT";
    }

    @Override
    protected String getTableName() {
        return "biz_subcontract_settlement";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long bizId, Map<String, Object> snapshotData) {
        super.rollback(bizId, snapshotData);
    }
}
