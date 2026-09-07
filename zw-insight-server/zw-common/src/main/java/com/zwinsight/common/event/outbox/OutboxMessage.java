package com.zwinsight.common.event.outbox;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发件箱事件消息（投递给 {@link OutboxEventHandler} 的入参）。
 * <p>
 * 除业务负载外还携带元数据，消费方据此实现幂等、审计与租户上下文回填：
 * </p>
 * <ul>
 *   <li>{@code idempotencyKey}：幂等键，消费方应以此去重（至少一次投递语义下必需）</li>
 *   <li>{@code attempts}：当前第几次尝试，可用于「多次重试后走降级分支」的判断</li>
 *   <li>{@code tenantId}：生产方租户，消费方需回填 SecurityContext 才能通过租户拦截器</li>
 * </ul>
 *
 * @param <T> 负载类型
 */
@Data
public class OutboxMessage<T> {

    /** 发件箱记录ID */
    private Long id;

    /** 租户ID（可能为 null，表示生产时无上下文） */
    private Long tenantId;

    /** 事件类型 */
    private String eventType;

    /** 聚合根类型 */
    private String aggregateType;

    /** 聚合根ID */
    private Long aggregateId;

    /** 幂等键（消费方去重依据） */
    private String idempotencyKey;

    /** 已反序列化的业务负载 */
    private T payload;

    /** 原始 JSON 负载（负载反序列化失败时仍可读取排查） */
    private String rawPayload;

    /** 当前尝试次数（1 表示首次投递） */
    private Integer attempts;

    /** 事件写入时间 */
    private LocalDateTime createdAt;

    /** 是否最后一次尝试（attempts 已达 maxAttempts，失败即转死信） */
    public boolean isLastAttempt() {
        return attempts != null && attempts >= 1;
    }
}
