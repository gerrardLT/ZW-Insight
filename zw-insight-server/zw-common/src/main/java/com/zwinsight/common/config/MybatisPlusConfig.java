package com.zwinsight.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zwinsight.common.datapermission.DataPermissionDataProvider;
import com.zwinsight.common.datapermission.DataPermissionInnerInterceptor;
import org.springframework.context.annotation.Lazy;
import com.zwinsight.common.datapermission.ZwDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置
 */
@Configuration
public class MybatisPlusConfig {

    @Autowired(required = false)
    @Lazy
    private DataPermissionDataProvider dataPermissionDataProvider;

    /**
     * MyBatis-Plus 插件配置
     * <p>
     * 拦截器注册顺序（严格遵守）：TenantLine → DataPermission → Pagination → OptimisticLocker
     * </p>
     * <p>
     * 顺序说明：
     * - TenantLine 必须最先执行，确保 tenant_id 条件优先注入到 WHERE 子句（Requirement 5.1）
     * - DataPermission 在租户之后执行，其生成的数据权限条件会通过 AND 追加到已有 WHERE 子句中，
     *   从而与 tenant_id 条件形成 AND 逻辑连接（Requirement 5.2）
     * - 最终 SQL 形如：WHERE tenant_id = ? AND (数据权限条件)
     * - MyBatis-Plus 的 InnerInterceptor 链机制保证了各拦截器追加的条件均通过 AND 组合
     * </p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户插件（必须在最前面，保证 tenant_id 条件优先注入）
        // 租户隔离是最基础的数据边界，所有后续拦截器在此基础上叠加
        interceptor.addInnerInterceptor(tenantLineInnerInterceptor());

        // 2. 数据权限插件（租户之后、分页之前）
        // 在 tenant_id 条件已存在的 WHERE 子句上，通过 AND 追加角色数据范围条件
        // 最终效果：同一租户内按角色 dataScope 进一步限制数据可见性
        if (dataPermissionDataProvider != null) {
            ZwDataPermissionHandler handler = new ZwDataPermissionHandler(dataPermissionDataProvider);
            interceptor.addInnerInterceptor(new DataPermissionInnerInterceptor(handler));
        }

        // 3. 分页插件（在所有条件注入完成后处理分页）
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 4. 乐观锁插件（最后执行，仅处理 UPDATE 语句的版本号）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 多租户插件
     */
    private TenantLineInnerInterceptor tenantLineInnerInterceptor() {
        return new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = SecurityContextHolder.getTenantId();
                return new LongValue(tenantId != null ? tenantId : 0L);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // sys_开头的系统表和act_开头的流程表不做租户隔离
                // msg_available_shortcut 为全局可选快捷功能定义表（实体无 tenantId），不做租户隔离
                // biz_approval_rollback_log 为流程回滚审计日志（实体 BizApprovalRollbackLog 不继承
                // BaseEntity、无 tenantId 列），若参与租户隔离会追加 tenant_id 条件导致
                // "Unknown column 'tenant_id' in 'where clause'" 报 500，故排除
                return tableName.startsWith("sys_")
                        || tableName.startsWith("act_")
                        || "msg_available_shortcut".equals(tableName)
                        || "biz_approval_rollback_log".equals(tableName);
            }
        });
    }

    /**
     * 自动填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "tenantId", Long.class, SecurityContextHolder.getTenantId());
                this.strictInsertFill(metaObject, "createdBy", Long.class, SecurityContextHolder.getUserId());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
