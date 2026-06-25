package com.zwinsight.workflow.service.rollback.strategy;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.service.rollback.RollbackStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于字段回滚的抽象策略基类
 * <p>
 * 提供通用的列名安全校验和驼峰转下划线逻辑，
 * 子类仅需指定 bizType 和目标表名。
 * </p>
 */
@Slf4j
public abstract class AbstractFieldRollbackStrategy implements RollbackStrategy {

    /**
     * 合法 SQL 标识符校验：仅允许字母、数字和下划线，最长64字符
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    protected final JdbcTemplate jdbcTemplate;

    protected AbstractFieldRollbackStrategy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 子类提供目标表名
     */
    protected abstract String getTableName();

    @Override
    public void rollback(Long bizId, Map<String, Object> snapshotData) {
        String bizType = getBizType();
        String tableName = getTableName();

        log.info("执行{}回滚, bizId={}, fields={}", bizType, bizId, snapshotData.keySet());

        for (Map.Entry<String, Object> entry : snapshotData.entrySet()) {
            String fieldName = entry.getKey();
            Object originalValue = entry.getValue();

            // 将快照字段名转换为数据库字段名（驼峰→下划线）
            String columnName = camelToUnderscore(fieldName);

            // 安全校验：防止动态列名注入
            validateIdentifier(columnName);

            String sql = "UPDATE " + tableName + " SET " + columnName + " = ? WHERE id = ?";
            jdbcTemplate.update(sql, originalValue, bizId);
            log.debug("回滚字段: {}={}, bizId={}", columnName, originalValue, bizId);
        }

        log.info("{}回滚完成, bizId={}", bizType, bizId);
    }

    /**
     * 驼峰转下划线
     */
    protected String camelToUnderscore(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 校验 SQL 标识符合法性（防止注入）
     */
    protected void validateIdentifier(String identifier) {
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException("回滚字段名不合法: " + identifier);
        }
    }
}
