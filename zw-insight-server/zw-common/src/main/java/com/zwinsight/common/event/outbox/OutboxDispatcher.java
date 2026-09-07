package com.zwinsight.common.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发件箱投递调度器（Outbox Dispatcher）。
 *
 * <h3>为什么需要它</h3>
 * <p>
 * 跨模块一致性若靠「业务事务里直接调用别的模块的 Service 改字段」实现，会产生两个后果：
 * ① 模块间网状耦合，改一处牵动全身；② 本地事务与远端写入无法原子化，
 * 出现「库改了消息没发」或「消息发了库回滚」。Outbox 把「发消息」降级为
 * 「与业务同事务写一行本地记录」，再由本调度器异步投递，从而以最终一致性换取模块解耦。
 * </p>
 *
 * <h3>投递语义</h3>
 * <ul>
 *   <li><b>至少一次</b>：处理器必须幂等（见 {@link OutboxEventHandler}）</li>
 *   <li><b>顺序尽力保证</b>：按 created_at, id 升序投递；但重试会打乱跨事件顺序，
 *       因此处理器不得依赖事件到达顺序，需要顺序的场景应在负载里带业务序号自行判定</li>
 *   <li><b>集群安全</b>：claim 采用 {@code UPDATE ... WHERE status='PENDING'} 乐观占用，
 *       多实例下同一事件只会被一个实例投递</li>
 * </ul>
 *
 * <h3>失败处理</h3>
 * <p>指数退避 60s × 2^(n-1)，超过 maxAttempts 转 DEAD（死信），需人工介入。</p>
 */
@Slf4j
@Component
public class OutboxDispatcher {

    /** 单批最大投递条数（防止一次拉太多占满连接/内存） */
    private static final int BATCH_SIZE = 50;

    /** 默认最大尝试次数 */
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    /** 退避基数（秒）：60, 120, 240, 480, 960 */
    private static final long RETRY_BACKOFF_BASE_SECONDS = 60L;

    /** 死信保留天数（已投递事件的清理阈值） */
    private static final int DELIVERED_RETENTION_DAYS = 30;

    /** 错误信息落库截断长度（列宽 1000） */
    private static final int ERROR_MAX_LEN = 990;

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    /** eventType → 处理器列表（同一事件允许多个处理器扇出） */
    private final Map<String, List<HandlerBinding<?>>> handlerRegistry = new HashMap<>();

    public OutboxDispatcher(OutboxEventMapper outboxEventMapper,
                            ObjectMapper objectMapper,
                            ObjectProvider<OutboxEventHandler<?>> handlers) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
        // 启动时收集全部处理器并按 supports() 建索引。
        // 用 ObjectProvider 而非直接注入 List：无处理器时不应导致容器启动失败。
        handlers.forEach(this::register);
        log.info("发件箱调度器初始化完成，已注册事件类型 {} 个：{}",
                handlerRegistry.size(), handlerRegistry.keySet());
    }

    /**
     * 注册处理器，并通过反射解析其泛型负载类型（供反序列化用）。
     */
    private void register(OutboxEventHandler<?> handler) {
        String eventType = handler.supports();
        if (eventType == null || eventType.isBlank()) {
            log.error("处理器 {} 的 supports() 返回空，已跳过注册", handler.getClass().getName());
            return;
        }
        Class<?> payloadType = resolvePayloadType(handler);
        handlerRegistry.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(new HandlerBinding<>(handler, payloadType));
        log.info("注册发件箱处理器：eventType={}, handler={}, payloadType={}",
                eventType, handler.getClass().getSimpleName(),
                payloadType != null ? payloadType.getSimpleName() : "String");
    }

    /**
     * 解析 {@code OutboxEventHandler<T>} 中 T 的实际类型。
     * <p>解析失败时返回 null，投递时退化为把原始 JSON 字符串交给处理器自行解析。</p>
     */
    private Class<?> resolvePayloadType(OutboxEventHandler<?> handler) {
        try {
            ResolvableType type = ResolvableType
                    .forClass(OutboxEventHandler.class, handler.getClass());
            Class<?> resolved = type.getGeneric(0).resolve();
            // 泛型被擦除为 Object 时视为未声明，走原始 JSON 分支
            return (resolved == null || resolved == Object.class) ? null : resolved;
        } catch (Exception e) {
            log.warn("解析处理器 {} 的负载泛型失败，将传原始 JSON", handler.getClass().getName(), e);
            return null;
        }
    }

    /**
     * 定时投递到期事件。
     * <p>间隔可配：{@code outbox.poll-delay}（毫秒），默认 30 秒。</p>
     */
    @Scheduled(fixedDelayString = "${outbox.poll-delay:30000}")
    public void poll() {
        List<OutboxEvent> due;
        try {
            due = outboxEventMapper.selectDueEvents(LocalDateTime.now(), BATCH_SIZE);
        } catch (Exception e) {
            // 查询失败（如表未建/DB 抖动）不应让调度线程死掉，记录后等下一轮
            log.error("查询待投递发件箱事件失败，本轮跳过", e);
            return;
        }
        if (due.isEmpty()) {
            return;
        }

        log.info("发件箱本轮待投递 {} 条", due.size());
        int delivered = 0;
        int failed = 0;
        int skipped = 0;

        for (OutboxEvent event : due) {
            DeliveryResult result = deliver(event);
            switch (result) {
                case DELIVERED -> delivered++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }
        log.info("发件箱本轮完成：成功 {}，失败 {}，跳过 {}", delivered, failed, skipped);
    }

    /**
     * 投递单个事件。
     */
    private DeliveryResult deliver(OutboxEvent event) {
        // 1. 乐观占用：抢不到说明其他实例正在处理
        int claimed = outboxEventMapper.claimForDelivery(event.getId());
        if (claimed != 1) {
            return DeliveryResult.SKIPPED;
        }

        List<HandlerBinding<?>> bindings = handlerRegistry.get(event.getEventType());
        if (bindings == null || bindings.isEmpty()) {
            // 无消费者不是「成功」：静默丢弃会让业务断链且无从察觉。
            // 记为失败并重试，给「消费者尚未部署」的窗口期留缓冲；最终进死信由人工确认。
            String msg = "未注册事件类型 [" + event.getEventType() + "] 的处理器";
            log.warn("{}, outboxId={}", msg, event.getId());
            handleFailure(event, msg);
            return DeliveryResult.FAILED;
        }

        Long previousTenant = SecurityContextHolder.getTenantId();
        try {
            // 2. 回填租户上下文：处理器写业务表时要过租户拦截器，
            //    调度线程无上下文会被注入 tenant_id=0 而写错/查空。
            if (event.getTenantId() != null) {
                SecurityContextHolder.setTenantId(event.getTenantId());
            }

            for (HandlerBinding<?> binding : bindings) {
                invokeHandler(binding, event);
            }

            outboxEventMapper.markDelivered(event.getId(), LocalDateTime.now());
            log.debug("发件箱事件投递成功，id={}, eventType={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId());
            return DeliveryResult.DELIVERED;

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("发件箱事件投递失败，id={}, eventType={}, error={}",
                    event.getId(), event.getEventType(), msg);
            handleFailure(event, msg);
            return DeliveryResult.FAILED;
        } finally {
            // 恢复上下文，避免污染线程池中的后续任务
            if (previousTenant != null) {
                SecurityContextHolder.setTenantId(previousTenant);
            } else {
                SecurityContextHolder.clear();
            }
        }
    }

    /**
     * 反序列化负载并调用处理器。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invokeHandler(HandlerBinding binding, OutboxEvent event) throws Exception {
        Object payload;
        if (binding.payloadType() == null) {
            payload = event.getPayload();
        } else {
            try {
                payload = objectMapper.readValue(event.getPayload(), binding.payloadType());
            } catch (Exception e) {
                // 负载反序列化失败属契约不兼容，重试也不会好转，但仍按失败处理进死信留证
                throw new IllegalStateException("事件负载反序列化失败，eventType=" + event.getEventType()
                        + ", 期望类型=" + binding.payloadType().getSimpleName(), e);
            }
        }

        OutboxMessage<Object> message = new OutboxMessage<>();
        message.setId(event.getId());
        message.setTenantId(event.getTenantId());
        message.setEventType(event.getEventType());
        message.setAggregateType(event.getAggregateType());
        message.setAggregateId(event.getAggregateId());
        message.setIdempotencyKey(event.getIdempotencyKey());
        message.setPayload(payload);
        message.setRawPayload(event.getPayload());
        message.setAttempts(event.getAttempts());
        message.setCreatedAt(event.getCreatedAt());

        binding.handler().handle(message);
    }

    /**
     * 失败处理：未超上限则指数退避重回 PENDING，否则转死信。
     */
    private void handleFailure(OutboxEvent event, String errorMessage) {
        // claim 时已把 attempts 自增，这里读到的 event.attempts 是旧值，需 +1 得到真实次数
        int attempts = (event.getAttempts() != null ? event.getAttempts() : 0) + 1;
        int maxAttempts = event.getMaxAttempts() != null ? event.getMaxAttempts() : DEFAULT_MAX_ATTEMPTS;
        String error = truncate(errorMessage);

        if (attempts >= maxAttempts) {
            outboxEventMapper.markDead(event.getId(), error);
            log.error("发件箱事件转入死信，需人工介入：id={}, eventType={}, aggregateId={}, attempts={}, error={}",
                    event.getId(), event.getEventType(), event.getAggregateId(), attempts, error);
        } else {
            long backoffSeconds = RETRY_BACKOFF_BASE_SECONDS * (1L << (attempts - 1));
            LocalDateTime nextRetryAt = LocalDateTime.now().plus(backoffSeconds, ChronoUnit.SECONDS);
            outboxEventMapper.markFailedWithRetry(event.getId(), error, nextRetryAt);
            log.info("发件箱事件将在 {} 秒后重试：id={}, attempts={}/{}",
                    backoffSeconds, event.getId(), attempts, maxAttempts);
        }
    }

    /**
     * 清理超过保留期的已投递事件，防止表无限膨胀。
     * <p>死信（DEAD）永不清理——那是待人工处理的证据。</p>
     */
    @Scheduled(cron = "${outbox.purge-cron:0 0 3 * * ?}")
    public void purge() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(DELIVERED_RETENTION_DAYS);
            int purged = outboxEventMapper.purgeDeliveredBefore(threshold);
            if (purged > 0) {
                log.info("清理已投递发件箱事件 {} 条（保留 {} 天）", purged, DELIVERED_RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.error("清理发件箱历史事件失败", e);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= ERROR_MAX_LEN ? s : s.substring(0, ERROR_MAX_LEN);
    }

    /** 单条投递结果 */
    private enum DeliveryResult {
        DELIVERED, FAILED, SKIPPED
    }

    /** 处理器与其负载类型的绑定 */
    private record HandlerBinding<T>(OutboxEventHandler<T> handler, Class<?> payloadType) {
    }
}
