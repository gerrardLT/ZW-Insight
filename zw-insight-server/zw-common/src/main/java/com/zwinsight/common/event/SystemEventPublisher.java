package com.zwinsight.common.event;

import com.zwinsight.common.config.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 以「系统任务」身份发布应用事件。
 * <p>
 * <b>背景（2026-10-12 修复）</b>：审批通过/驳回/撤回事件由流程引擎触发，各业务模块在监听器里按
 * {@code businessId}（来自流程变量，受信）读单并回写。这是**系统操作**，不应受「发起人/审批人的
 * 行级数据范围」约束——否则当审批人无该项目数据范围时，回调的 {@code selectById} 会被
 * {@code @DataPermission} 谓词过滤为 null，业务侧多只 {@code log.warn} 后 return，单据永久停留
 * 在原状态且无法重提（实证：劳务产值单级 PM 审批，见批次 3 报告）。
 * </p>
 * <p>
 * 此处临时标记系统任务，使 {@code ZwDataPermissionHandler} 按「租户内全量」放行（租户隔离仍由
 * {@code TenantLineInnerInterceptor} 依据 tenantId 保证，不会跨租户泄露）；发布结束立即还原，
 * 避免污染同一请求后续查询的行级过滤。
 * </p>
 */
public final class SystemEventPublisher {

    private SystemEventPublisher() {
        // 工具类禁止实例化
    }

    /**
     * 以系统任务身份同步发布事件；发布后恢复原系统任务标记（若原本已是系统任务则不还原）。
     *
     * @param publisher Spring 事件发布器
     * @param event     待发布的事件
     */
    public static void publishSystemEvent(ApplicationEventPublisher publisher, Object event) {
        boolean previousSystemTask = SecurityContextHolder.isSystemTask();
        try {
            SecurityContextHolder.markSystemTask();
            publisher.publishEvent(event);
        } finally {
            if (!previousSystemTask) {
                SecurityContextHolder.unmarkSystemTask();
            }
        }
    }
}
