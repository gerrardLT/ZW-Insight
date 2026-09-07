package com.zwinsight.common.event.outbox;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事务性发件箱实体（Transactional Outbox，sys_outbox_event 表）
 * <p>
 * 实现最终一致性：业务事务内将域事件写入本表 → 后台调度轮询投递 → 幂等消费。
 * </p>
 *
 * <h3>状态机</h3>
 * <pre>
 *   PENDING ──(attempt ≤ max)──▶ DELIVERED / FAILED ──retry──▶ PENDING ──...
 *                                                      │
 *                                          attempt > max ▼
 *                                                   DEAD
 * </pre>
 *
 * <h3>幂等键</h3>
 * <p>
 * {@code idempotency_key = aggregateType:aggregateId:eventType:version}，确保同一聚合根的
 * 多次更新只生成唯一事件，避免重复投递。
 * </p>
 *
 * @implNote sys_outbox_event 不继承 BaseEntity（无 deleted/version），由应用层控制
 *           生命周期；{@code tenant_id nullable} 因为跨租户任务需全局扫描。
 */
@Data
public class OutboxEvent {

    /** ID（雪花算法） */
    private Long id;

    /** 租户 ID（可空，跨租户任务无需指定上下文即可读） */
    private Long tenantId;

    /** 事件类型（如 CHANGE_EVENT_APPROVED） */
    private String eventType;

    /** 聚合根类型（如 ChangeEvent） */
    private String aggregateType;

    /** 聚合根 ID */
    private Long aggregateId;

    /** 幂等键（防止重复投递：aggregateType:aggregateId:eventType:version） */
    private String idempotencyKey;

    /** JSON 负载（event payload） */
    private String payload;

    /** 投递状态（PENDING/DELIVERED/FAILED/DEAD） */
    private String status;

    /** 已尝试次数 */
    private Integer attempts;

    /** 最大允许尝试次数（超出转 DEAD 进入死信队列） */
    private Integer maxAttempts;

    /** 下次重试时间（NULL 表示可立即投递，指数退避后写入） */
    private LocalDateTime nextRetryAt;

    /** 最后一次错误信息 */
    private String lastError;

    /** 投递成功时间 */
    private LocalDateTime publishedAt;

    /** 创建时间（写入了 DB 之后即存在） */
    private LocalDateTime createdAt;

    /** 更新时间（每次重试/失败都会刷新） */
    private LocalDateTime updatedAt;
}
