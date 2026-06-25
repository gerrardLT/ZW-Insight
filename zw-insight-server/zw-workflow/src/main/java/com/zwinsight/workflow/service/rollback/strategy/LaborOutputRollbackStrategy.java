package com.zwinsight.workflow.service.rollback.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 劳务产值回滚策略
 */
@Slf4j
@Component
public class LaborOutputRollbackStrategy extends AbstractFieldRollbackStrategy {

    public LaborOutputRollbackStrategy(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public String getBizType() {
        return "LABOR_OUTPUT";
    }

    @Override
    protected String getTableName() {
        return "biz_labor_output";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long bizId, Map<String, Object> snapshotData) {
        super.rollback(bizId, snapshotData);
    }
}
