package com.zwinsight.common.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 发件箱事件录制器（Transactional Outbox 的生产端）。
 *
 * <h3>解决什么问题</h3>
 * <p>
 * 跨模块通知若直接在业务事务里发消息，必然遇到双写不一致：
 * 「库改了、消息没发出去」或「消息发了、库回滚了」。
 * 本类把「发消息」降级为「与业务同事务写一行本地记录」——
 * INSERT 与业务 UPDATE 共用同一连接同一事务，要么一起提交要么一起回滚，
 * 投递交给 {@link OutboxDispatcher} 异步完成，以最终一致性换取模块解耦。
 * </p>
 *
 * <h3>调用约束</h3>
 * <p><b>必须在 {@code @Transactional} 方法内调用</b>，否则退化为普通写入，失去原子性保证。</p>
 *
 * <h3>幂等键</h3>
 * <p>
 * {@code aggregateType:aggregateId:eventType:version}。version 取聚合根乐观锁版本号：
 * </p>
 * <ul>
 *   <li>同一次状态流转 → 同一 key → 重复录制命中唯一键，按幂等语义静默跳过（返回 false）</li>
 *   <li>合法的二次流转（如「驳回后重新评估再批准」）→ version 已递增 → key 不同，正常写入</li>
 * </ul>
 *
 * <h3>失败语义</h3>
 * <p>
 * 除幂等命中外的任何写入失败都会<b>抛异常</b>，让业务事务一并回滚。
 * 宁可业务操作失败，也不能出现「状态改了但事件丢了」——那会让下游成本账户
 * 与变更审批长期不一致，且无人察觉。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    /** 默认最大投递尝试次数（退避 60/120/240/480/960 秒，约半小时后进死信） */
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    /**
     * 录制事件（负载为对象，内部序列化为 JSON）。
     *
     * @param eventType        事件类型，须与处理器 {@code supports()} 一致
     * @param aggregateType    聚合根类型，如 {@code ChangeEvent}
     * @param aggregateId      聚合根 ID
     * @param aggregateVersion 聚合根乐观锁版本号（可为 null，退化为毫秒时间戳做区分）
     * @param payload          事件负载对象
     * @return true=已写入；false=幂等命中（同一流转已录制过）
     */
    public boolean record(String eventType, String aggregateType, Long aggregateId,
                          Integer aggregateVersion, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 序列化失败属编码缺陷，必须暴露（fail-fast），不可静默丢事件
            throw new IllegalStateException(String.format(
                    "发件箱事件负载序列化失败，eventType=%s, aggregateType=%s, aggregateId=%s",
                    eventType, aggregateType, aggregateId), e);
        }
        return recordJson(eventType, aggregateType, aggregateId, aggregateVersion, json);
    }

    /**
     * 录制事件（负载已是 JSON 字符串，省去二次序列化）。
     *
     * @return true=已写入；false=幂等命中
     */
    public boolean recordJson(String eventType, String aggregateType, Long aggregateId,
                              Integer aggregateVersion, String payloadJson) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType 不能为空");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType 不能为空");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId 不能为空（幂等键依赖它定位业务对象）");
        }

        String discriminator = aggregateVersion != null
                ? String.valueOf(aggregateVersion)
                : "t" + System.currentTimeMillis();
        String idempotencyKey = aggregateType + ":" + aggregateId + ":" + eventType + ":" + discriminator;

        OutboxEvent event = new OutboxEvent();
        // sys_outbox_event 为 sys_ 前缀表，被租户拦截器 ignoreTable 排除，
        // 因此 tenant_id 必须在此显式从上下文取值（投递时再回填给处理器）
        event.setTenantId(SecurityContextHolder.getTenantId());
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setIdempotencyKey(idempotencyKey);
        event.setPayload(payloadJson);
        event.setStatus(OutboxStatus.PENDING.name());
        event.setAttempts(0);
        event.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);

        try {
            outboxEventMapper.insert(event);
        } catch (DuplicateKeyException dup) {
            // 幂等命中：同一聚合根同一版本同一事件类型已录制过，属正常重入而非失败
            log.info("发件箱事件幂等命中，跳过重复录制，key={}", idempotencyKey);
            return false;
        }

        log.debug("发件箱事件已录制，eventType={}, aggregateType={}, aggregateId={}, key={}",
                eventType, aggregateType, aggregateId, idempotencyKey);
        return true;
    }
}
