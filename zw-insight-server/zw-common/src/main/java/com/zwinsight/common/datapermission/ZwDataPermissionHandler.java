package com.zwinsight.common.datapermission;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.DataPermissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZW-Insight 数据权限处理器
 * <p>
 * 实现 MyBatis-Plus MultiDataPermissionHandler 接口，
 * 根据当前用户角色的数据范围自动追加 SQL WHERE 条件。
 * </p>
 * <p>
 * 处理逻辑：
 * 1. 通过 mappedStatementId 查找 Mapper 方法上的 @DataPermission 注解
 * 2. 如果存在注解，获取当前用户有效数据范围（多角色取最大优先级）
 * 3. 根据数据范围生成对应的 SQL 条件表达式
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class ZwDataPermissionHandler implements MultiDataPermissionHandler {

    private final DataPermissionDataProvider dataProvider;

    /** 注解缓存：mappedStatementId -> DataPermission（null 表示无注解） */
    private final Map<String, DataPermission> annotationCache = new ConcurrentHashMap<>();

    /** 系统管理模块 Mapper 包前缀，对该模块不做数据权限过滤 */
    private static final String SYSTEM_MAPPER_PREFIX = "com.zwinsight.system.mapper.";

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 0. 系统管理模块的 Mapper 不做数据权限过滤（Requirement 5.3）
        if (mappedStatementId != null && mappedStatementId.startsWith(SYSTEM_MAPPER_PREFIX)) {
            log.debug("数据权限：跳过系统管理模块 Mapper - {}", mappedStatementId);
            return null;
        }

        // 1. 查找 Mapper 方法上的 @DataPermission 注解（优先于用户上下文检查）
        DataPermission annotation = findAnnotation(mappedStatementId);
        if (annotation == null) {
            // 未标注 @DataPermission 注解，不做数据权限过滤
            return null;
        }

        // 2. 获取当前用户ID — 注解存在时必须有有效的安全上下文（Requirement 2.8）
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new DataPermissionException("无法获取用户上下文，拒绝执行需要数据权限过滤的查询: " + mappedStatementId);
        }

        // 3. 获取有效数据范围（多角色取最大优先级），异常时降级为 SELF
        DataScopeEnum effectiveScope = getEffectiveScope(userId);
        if (effectiveScope == DataScopeEnum.ALL) {
            // 全部数据，不追加过滤条件
            return null;
        }

        // 4. 查找匹配当前表的 DataColumn 配置
        DataColumn matchedColumn = findMatchedColumn(annotation, table);
        if (matchedColumn == null) {
            // 未配置当前表的过滤规则，不过滤
            return null;
        }

        // 5. 根据数据范围构建过滤条件
        return buildCondition(effectiveScope, matchedColumn, table, userId);
    }

    /**
     * 获取用户有效数据范围（多角色取最大优先级）
     * <p>
     * 每次调用实时查询数据库，不使用缓存，确保配置变更立即生效。
     * 当 DataProvider 查询异常时，降级为 SELF 范围（最小权限原则）。
     * </p>
     *
     * @param userId 用户ID
     * @return 有效数据范围枚举
     */
    public DataScopeEnum getEffectiveScope(Long userId) {
        List<String> scopes;
        try {
            scopes = dataProvider.getUserDataScopes(userId);
        } catch (Exception e) {
            log.error("数据权限：获取用户数据范围异常，降级为 SELF 范围. userId={}", userId, e);
            return DataScopeEnum.SELF;
        }

        if (scopes == null || scopes.isEmpty()) {
            return DataScopeEnum.SELF;
        }

        DataScopeEnum maxScope = DataScopeEnum.SELF;
        for (String scopeName : scopes) {
            DataScopeEnum scope = DataScopeEnum.fromName(scopeName);
            if (scope.getPriority() > maxScope.getPriority()) {
                maxScope = scope;
            }
        }
        return maxScope;
    }

    /**
     * 根据数据范围构建 SQL 过滤条件
     */
    private Expression buildCondition(DataScopeEnum scope, DataColumn column, Table table, Long userId) {
        return switch (scope) {
            case SELF -> buildSelfCondition(column, table, userId);
            case PROJECT -> buildProjectCondition(column, table, userId);
            case DEPT -> buildDeptCondition(column, table, userId);
            case DEPT_AND_CHILDREN -> buildDeptAndChildrenCondition(column, table, userId);
            default -> null; // ALL 不过滤
        };
    }

    /**
     * 构建 SELF 范围条件：WHERE created_by = #{userId}
     */
    public Expression buildSelfCondition(DataColumn column, Table table, Long userId) {
        Column col = new Column(resolveColumnWithAlias(table, column.userColumn()));
        EqualsTo equalsTo = new EqualsTo(col, new LongValue(userId));
        return equalsTo;
    }

    /**
     * 构建 PROJECT 范围条件：WHERE project_id IN (用户参与的项目ID列表)
     */
    public Expression buildProjectCondition(DataColumn column, Table table, Long userId) {
        List<Long> projectIds;
        try {
            projectIds = dataProvider.getUserProjectIds(userId);
        } catch (Exception e) {
            log.error("数据权限：获取用户项目列表异常，降级为 SELF 范围. userId={}", userId, e);
            return buildSelfCondition(column, table, userId);
        }

        if (projectIds == null || projectIds.isEmpty()) {
            // 用户未参与任何项目，构建一个不可能匹配的条件：1 = 0
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }

        String columnName = resolveColumnWithAlias(table, column.projectColumn());
        return buildInExpression(columnName, projectIds);
    }

    /**
     * 构建 DEPT 范围条件：WHERE dept_id = #{userDeptId}
     */
    public Expression buildDeptCondition(DataColumn column, Table table, Long userId) {
        Long deptId;
        try {
            deptId = dataProvider.getUserDeptId(userId);
        } catch (Exception e) {
            log.error("数据权限：获取用户部门ID异常，降级为 SELF 范围. userId={}", userId, e);
            return buildSelfCondition(column, table, userId);
        }

        if (deptId == null) {
            // 用户未分配部门，退回到 SELF 条件
            return buildSelfCondition(column, table, userId);
        }

        Column col = new Column(resolveColumnWithAlias(table, column.deptColumn()));
        return new EqualsTo(col, new LongValue(deptId));
    }

    /**
     * 构建 DEPT_AND_CHILDREN 范围条件：WHERE dept_id IN (部门及所有子部门ID)
     */
    public Expression buildDeptAndChildrenCondition(DataColumn column, Table table, Long userId) {
        Long deptId;
        try {
            deptId = dataProvider.getUserDeptId(userId);
        } catch (Exception e) {
            log.error("数据权限：获取用户部门ID异常，降级为 SELF 范围. userId={}", userId, e);
            return buildSelfCondition(column, table, userId);
        }

        if (deptId == null) {
            // 用户未分配部门，退回到 SELF 条件
            return buildSelfCondition(column, table, userId);
        }

        List<Long> deptIds;
        try {
            deptIds = dataProvider.getDeptAndChildIds(deptId);
        } catch (Exception e) {
            log.error("数据权限：获取部门及子部门ID异常，降级为仅本部门. deptId={}", deptId, e);
            Column col = new Column(resolveColumnWithAlias(table, column.deptColumn()));
            return new EqualsTo(col, new LongValue(deptId));
        }

        if (deptIds == null || deptIds.isEmpty()) {
            // 无法查询部门树，仅用本部门
            Column col = new Column(resolveColumnWithAlias(table, column.deptColumn()));
            return new EqualsTo(col, new LongValue(deptId));
        }

        String columnName = resolveColumnWithAlias(table, column.deptColumn());
        return buildInExpression(columnName, deptIds);
    }

    /**
     * 构建 IN 表达式：column IN (id1, id2, ...)
     */
    private Expression buildInExpression(String columnName, List<Long> ids) {
        InExpression inExpression = new InExpression();
        inExpression.setLeftExpression(new Column(columnName));

        ExpressionList expressionList = new ExpressionList();
        for (Long id : ids) {
            expressionList.addExpressions(new LongValue(id));
        }
        inExpression.setRightItemsList(expressionList);

        return inExpression;
    }

    /**
     * 拼接表别名和列名
     */
    private String resolveColumnWithAlias(Table table, String columnName) {
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        if (alias != null && !alias.isBlank()) {
            return alias + "." + columnName;
        }
        return columnName;
    }

    /**
     * 查找匹配当前表的 DataColumn 配置
     */
    private DataColumn findMatchedColumn(DataPermission annotation, Table table) {
        DataColumn[] columns = annotation.value();
        if (columns.length == 0) {
            return null;
        }

        String tableAlias = table.getAlias() != null ? table.getAlias().getName() : "";
        String tableName = table.getName();

        for (DataColumn column : columns) {
            String configAlias = column.alias();
            if (configAlias.isEmpty()) {
                // 未指定别名的列配置匹配主表（第一个匹配即返回）
                return column;
            }
            if (configAlias.equalsIgnoreCase(tableAlias) || configAlias.equalsIgnoreCase(tableName)) {
                return column;
            }
        }

        return null;
    }

    /**
     * 通过 mappedStatementId 查找 Mapper 方法上的 @DataPermission 注解
     * <p>
     * mappedStatementId 格式：com.xxx.mapper.XxxMapper.methodName
     * </p>
     */
    private DataPermission findAnnotation(String mappedStatementId) {
        if (mappedStatementId == null) {
            return null;
        }

        // 先检查缓存（使用 containsKey 判断是否已缓存，值可能为 null 不适合 ConcurrentHashMap）
        if (annotationCache.containsKey(mappedStatementId)) {
            return annotationCache.get(mappedStatementId);
        }

        DataPermission annotation = resolveAnnotation(mappedStatementId);
        if (annotation != null) {
            annotationCache.put(mappedStatementId, annotation);
        }
        return annotation;
    }

    /**
     * 解析 mappedStatementId 对应的 @DataPermission 注解
     */
    private DataPermission resolveAnnotation(String mappedStatementId) {
        try {
            int lastDot = mappedStatementId.lastIndexOf('.');
            if (lastDot < 0) {
                return null;
            }

            String className = mappedStatementId.substring(0, lastDot);
            String methodName = mappedStatementId.substring(lastDot + 1);

            Class<?> clazz = Class.forName(className);

            // 先查方法级注解
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    DataPermission methodAnnotation = method.getAnnotation(DataPermission.class);
                    if (methodAnnotation != null) {
                        return methodAnnotation;
                    }
                }
            }

            // 再查类级注解
            return clazz.getAnnotation(DataPermission.class);
        } catch (ClassNotFoundException e) {
            log.warn("数据权限：无法加载 Mapper 类 - {}", mappedStatementId, e);
            return null;
        }
    }
}
