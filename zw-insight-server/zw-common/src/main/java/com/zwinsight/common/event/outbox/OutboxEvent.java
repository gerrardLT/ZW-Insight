package com.zwinsight.common.event.outbox;

import com.baomidou.mybatisplus.annotation.TableName;
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
 *   PENDING ──claim（乐观占用，attempts+1）──▶ DELIVERING
 *                                                │
 *                              ┌─────────────────┴─────────────────┐
 *                            成功                                 失败
 *                              │                                    │
 *                              ▼                       attempts+1 &lt; maxAttempts ?
 *                          DELIVERED                        │是                │否
 *                       （保留 30 天后 purge）               ▼                  ▼
 *                                                     PENDING               DEAD
 *                                              （next_retry_at 指数退避    （死信，人工介入，
 *                                                60s × 2^(n-1)，等下轮）     永不 purge）
 * </pre>
 *
 * <p>关键点：<b>不存在 FAILED 状态</b>——投递失败要么回落 PENDING 等重试，
 * 要么直接转 DEAD，二者由 {@code OutboxDispatcher.handleFailure} 依 maxAttempts 判定。</p>
 *
 * <h3>幂等键</h3>
 * <p>
 * {@code idempotency_key = aggregateType:aggregateId:eventType:version}，确保同一聚合根的
 * 多次更新只生成唯一事件，避免重复投递。
 * </p>
 *
 * @implNote sys_outbox_event 不继承 BaseEntity（无 deleted/version），由应用层控制
 *           生命周期；{@code tenant_id nullable} 因为跨租户任务需全局扫描。
 *           <b>必须显式 {@code @TableName("sys_outbox_event")}</b>：类名 OutboxEvent 的默认
 *           下划线映射是 {@code outbox_event}（不存在），且不带 sys_ 前缀会落入租户拦截器
 *           拦截范围，与 Mapper 手写 SQL 及 DDL 的 sys_outbox_event 不一致（2026-09-17 实证：
 *           OutboxDispatcher.poll 每 30s 报 Table 'outbox_event' doesn't exist）。
 */
@Data
@TableName("sys_outbox_event")
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

    /**
     * 投递状态（PENDING/DELIVERING/DELIVERED/DEAD）
     * <p>取值以 {@link OutboxStatus} 枚举为唯一权威；<b>无 FAILED 态</b>，
     * 投递失败按 maxAttempts 回落 PENDING 重试或转 DEAD。</p>
     */
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
