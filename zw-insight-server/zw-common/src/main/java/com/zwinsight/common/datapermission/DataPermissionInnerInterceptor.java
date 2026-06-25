package com.zwinsight.common.datapermission;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * ZW-Insight 数据权限内部拦截器
 * <p>
 * 继承 MyBatis-Plus 的 DataPermissionInterceptor，
 * 使用 {@link ZwDataPermissionHandler} 作为数据权限处理器。
 * </p>
 * <p>
 * 拦截器执行顺序：TenantLine → DataPermission → Pagination → OptimisticLocker
 * </p>
 */
@Slf4j
public class DataPermissionInnerInterceptor extends DataPermissionInterceptor {

    public DataPermissionInnerInterceptor(ZwDataPermissionHandler handler) {
        super(handler);
        log.info("数据权限拦截器初始化完成");
    }
}
