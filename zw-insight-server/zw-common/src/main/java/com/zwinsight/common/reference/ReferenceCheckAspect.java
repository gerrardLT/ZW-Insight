package com.zwinsight.common.reference;

import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 引用校验 AOP 切面
 * <p>
 * 拦截标注了 {@link ReferenceCheck} 注解的删除方法，在删除执行前自动校验引用关系。
 * 如果存在引用则抛出 {@link ReferenceExistsException}；如果数据库查询异常则阻止删除并记录日志。
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ReferenceCheckAspect {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 表名/列名合法性校验：仅允许字母、数字和下划线
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    /**
     * 引用详情最大返回条数
     */
    private static final int MAX_DETAIL_COUNT = 10;

    @Before("@annotation(check)")
    public void doCheck(JoinPoint point, ReferenceCheck check) {
        Long entityId = extractEntityId(point);
        if (entityId == null) {
            log.warn("引用校验：无法从方法参数中提取实体ID，跳过校验。方法: {}",
                    point.getSignature().toShortString());
            return;
        }

        List<ReferenceInfoVO> allReferences = new ArrayList<>();
        long totalCount = 0;

        for (ReferenceRelation relation : check.value()) {
            // 校验表名和列名合法性（防止 SQL 注入）
            validateIdentifier(relation.tableName(), "tableName");
            validateIdentifier(relation.column(), "column");
            if (!relation.codeColumn().isEmpty()) {
                validateIdentifier(relation.codeColumn(), "codeColumn");
            }

            try {
                // 引用计数查询（参数化 SQL 防注入）
                long count = queryReferenceCount(relation, entityId);
                if (count > 0) {
                    totalCount += count;
                    // 查询引用详情（最多前 10 条）
                    List<ReferenceInfoVO> details = queryReferenceDetails(relation, entityId);
                    allReferences.addAll(details);
                }
            } catch (DataAccessException e) {
                // DB 查询异常时阻止删除并记录 ERROR 日志
                log.error("引用校验数据库查询异常，阻止删除操作。表: {}, 列: {}, 实体ID: {}",
                        relation.tableName(), relation.column(), entityId, e);
                throw new BusinessException("引用校验异常，请稍后重试");
            }
        }

        if (totalCount > 0) {
            // 限制返回的引用详情总数不超过 10 条
            List<ReferenceInfoVO> limitedReferences = allReferences.size() > MAX_DETAIL_COUNT
                    ? allReferences.subList(0, MAX_DETAIL_COUNT)
                    : allReferences;
            throw new ReferenceExistsException("实体", limitedReferences, totalCount);
        }
    }

    /**
     * 从方法参数中提取实体 ID
     * <p>
     * 支持以下参数模式：
     * <ul>
     *   <li>第一个参数为 Long 类型：直接作为实体 ID</li>
     *   <li>第一个参数为对象且含 getId() 方法：通过反射获取 ID</li>
     * </ul>
     * </p>
     */
    Long extractEntityId(JoinPoint point) {
        Object[] args = point.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        Object firstArg = args[0];
        if (firstArg == null) {
            return null;
        }

        // 情况1：第一个参数就是 Long 类型
        if (firstArg instanceof Long longId) {
            return longId;
        }

        // 情况2：尝试通过反射调用 getId() 方法
        try {
            Method getIdMethod = firstArg.getClass().getMethod("getId");
            Object idValue = getIdMethod.invoke(firstArg);
            if (idValue instanceof Long longId) {
                return longId;
            }
            if (idValue != null) {
                return Long.parseLong(idValue.toString());
            }
        } catch (NoSuchMethodException e) {
            // 对象没有 getId() 方法
            log.debug("引用校验：参数对象无 getId() 方法，类型: {}", firstArg.getClass().getName());
        } catch (Exception e) {
            log.warn("引用校验：通过反射获取 ID 失败", e);
        }

        return null;
    }

    /**
     * 查询引用计数
     */
    private long queryReferenceCount(ReferenceRelation relation, Long entityId) {
        String sql = String.format("SELECT COUNT(1) FROM %s WHERE %s = ? AND deleted = 0",
                relation.tableName(), relation.column());
        Long count = jdbcTemplate.queryForObject(sql, Long.class, entityId);
        return count != null ? count : 0;
    }

    /**
     * 查询引用详情（最多前 10 条）
     */
    private List<ReferenceInfoVO> queryReferenceDetails(ReferenceRelation relation, Long entityId) {
        List<ReferenceInfoVO> details = new ArrayList<>();

        String codeSelectClause = relation.codeColumn().isEmpty()
                ? "NULL AS document_code"
                : relation.codeColumn() + " AS document_code";

        String sql = String.format(
                "SELECT %s, created_at FROM %s WHERE %s = ? AND deleted = 0 ORDER BY created_at DESC LIMIT %d",
                codeSelectClause, relation.tableName(), relation.column(), MAX_DETAIL_COUNT);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, entityId);
        for (Map<String, Object> row : rows) {
            ReferenceInfoVO info = ReferenceInfoVO.builder()
                    .referenceType(relation.displayName())
                    .documentCode(row.get("document_code") != null ? row.get("document_code").toString() : null)
                    .referenceTime(convertToLocalDateTime(row.get("created_at")))
                    .build();
            details.add(info);
        }

        return details;
    }

    /**
     * 校验 SQL 标识符合法性（防止注解值 SQL 注入）
     */
    private void validateIdentifier(String identifier, String fieldName) {
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException(
                    String.format("引用校验配置错误：%s '%s' 包含非法字符", fieldName, identifier));
        }
    }

    /**
     * 将数据库查询结果转换为 LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
