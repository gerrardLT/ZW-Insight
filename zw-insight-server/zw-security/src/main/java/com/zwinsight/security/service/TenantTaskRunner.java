package com.zwinsight.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * 跨租户定时任务执行器。
 * <p>
 * 背景（2026-08-11 修复 A3）：租户拦截器增强后，无租户上下文的写 SQL 直接拒绝、
 * 读 SQL 注入 tenant_id=0 查空。跨租户定时任务（到期提醒/库存预警/催办等）
 * 原先在无上下文线程中执行，长期静默失效（查空即返回）。本执行器逐个正常租户
 * 设置上下文后执行业务逻辑，单租户失败不影响其他租户。
 * </p>
 * <p>状态码 1 = 正常（与 zw-system 的 TenantStatusEnum.NORMAL 一致，
 * 避免 zw-security 反向依赖 zw-system）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantTaskRunner {

    /** 租户状态：正常（TenantStatusEnum.NORMAL） */
    private static final int TENANT_STATUS_NORMAL = 1;

    private final SysTenantMapper tenantMapper;

    /**
     * 遍历所有正常租户，逐个设置租户上下文后执行 action。
     *
     * @param taskName 任务名（日志标识）
     * @param action   单租户业务逻辑，入参为 tenantId
     */
    public void runForActiveTenants(String taskName, LongConsumer action) {
        List<SysTenant> tenants = tenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getStatus, TENANT_STATUS_NORMAL));
        log.info("[{}] 开始逐租户执行，正常租户数={}", taskName, tenants.size());
        int successCount = 0;
        for (SysTenant tenant : tenants) {
            SecurityContextHolder.setTenantId(tenant.getId());
            try {
                action.accept(tenant.getId());
                successCount++;
            } catch (Exception e) {
                log.error("[{}] 租户 {} 执行失败，继续下一租户", taskName, tenant.getId(), e);
            } finally {
                SecurityContextHolder.clear();
            }
        }
        log.info("[{}] 逐租户执行完成，成功 {}/{}", taskName, successCount, tenants.size());
    }
}
