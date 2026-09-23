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
 * <p><b>第二次同类缺陷修正（2026-09-24）</b>：2026-08-11 只补了租户上下文，
 * 未补用户上下文——而数据权限处理器在 userId==null 时直接抛异常，导致
 * 定时任务再次静默失效（线上实证：FundForecastTask「成功 0/2」、
 * RiskScanTask 3/7 规则失败）。现统一标记为系统任务（{@code markSystemTask}），
 * 数据权限按租户全量放行；同时对「全部租户失败」升级为 ERROR 日志，
 * 避免任务实质空转却只留一条 INFO。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantTaskRunner {

    /** 租户状态：正常（TenantStatusEnum.NORMAL） */
    private static final int TENANT_STATUS_NORMAL = 1;

    private final SysTenantMapper tenantMapper;

    /**
     * 遍历所有正常租户，逐个设置租户上下文 + 系统任务标记后执行 action。
     * <p>系统任务标记使数据权限按租户全量放行（定时任务无登录用户）；
     * 租户隔离仍由租户拦截器保证。上下文在 finally 中清理，防止线程池复用污染。</p>
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
            // 定时任务无登录用户：标记为系统任务，否则数据权限处理器会因 userId==null 抛异常
            SecurityContextHolder.markSystemTask();
            try {
                action.accept(tenant.getId());
                successCount++;
            } catch (Exception e) {
                log.error("[{}] 租户 {} 执行失败，继续下一租户", taskName, tenant.getId(), e);
            } finally {
                SecurityContextHolder.clear();
            }
        }
        // 全部失败时必须醒目告警：否则任务实质空转却只留一条 INFO（历史踩坑）
        if (!tenants.isEmpty() && successCount == 0) {
            log.error("[{}] 逐租户执行全部失败（成功 0/{}）——定时任务实质未生效，请根据上方异常堆栈排查",
                    taskName, tenants.size());
        } else if (successCount < tenants.size()) {
            log.warn("[{}] 逐租户执行部分失败，成功 {}/{}", taskName, successCount, tenants.size());
        } else {
            log.info("[{}] 逐租户执行完成，成功 {}/{}", taskName, successCount, tenants.size());
        }
    }
}
