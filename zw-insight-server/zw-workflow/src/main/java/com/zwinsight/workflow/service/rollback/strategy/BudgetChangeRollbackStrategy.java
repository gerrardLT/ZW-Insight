package com.zwinsight.workflow.service.rollback.strategy;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.service.rollback.RollbackStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 预算变更回滚策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetChangeRollbackStrategy implements RollbackStrategy {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 合法 SQL 标识符校验：仅允许字母、数字和下划线，最长64字符
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    @Override
    public String getBizType() {
        return "BUDGET_CHANGE";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long bizId, Map<String, Object> snapshotData) {
        log.info("执行预算变更回滚, bizId={}, fields={}", bizId, snapshotData.keySet());

        for (Map.Entry<String, Object> entry : snapshotData.entrySet()) {
            String fieldName = entry.getKey();
            Object originalValue = entry.getValue();

            // 将快照字段名转换为数据库字段名（驼峰→下划线）
            String columnName = camelToUnderscore(fieldName);

            // 安全校验：防止动态列名注入
            validateIdentifier(columnName);

            String sql = "UPDATE biz_budget SET " + columnName + " = ? WHERE id = ?";
            jdbcTemplate.update(sql, originalValue, bizId);
            log.debug("回滚字段: {}={}, bizId={}", columnName, originalValue, bizId);
        }

        log.info("预算变更回滚完成, bizId={}", bizId);
    }

    private String camelToUnderscore(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 校验 SQL 标识符合法性（防止注入）
     */
    private void validateIdentifier(String identifier) {
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException("回滚字段名不合法: " + identifier);
        }
    }
}
