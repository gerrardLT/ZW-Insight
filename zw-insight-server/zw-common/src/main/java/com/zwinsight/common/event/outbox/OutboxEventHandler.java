package com.zwinsight.common.event.outbox;

/**
 * 发件箱事件处理器 SPI。
 * <p>
 * 业务模块实现本接口消费特定类型的事件（如变更事件批准后调整成本账户），
 * {@link OutboxDispatcher} 按 {@link #supports()} 声明的事件类型路由投递。
 * 同一事件类型允许注册多个处理器（扇出），彼此独立、互不感知。
 * </p>
 *
 * <h3>幂等性硬约束</h3>
 * <p>
 * Outbox 投递语义为<b>至少一次</b>：网络超时、处理器抛错、应用重启都可能导致
 * 同一事件被重复投递。因此实现<b>必须幂等</b>——推荐做法：
 * </p>
 * <ol>
 *   <li>以 {@code eventId}（或 aggregateId + 业务阶段）为幂等键落一张消费记录表，
 *       重复投递时命中记录直接返回；</li>
 *   <li>或使用「设置绝对值」而非「累加增量」的写法，使重复执行结果收敛；</li>
 *   <li>金额调整这类天然非幂等的操作，务必走方案 1。</li>
 * </ol>
 *
 * <h3>异常语义</h3>
 * <p>
 * 抛出任何异常都会触发重试（指数退避），超过最大次数转入死信 DEAD 等待人工介入。
 * 因此：可恢复的错误（下游超时、锁冲突）应当抛出以触发重试；
 * 不可恢复的错误（数据非法、契约不兼容）也应抛出并写清消息，便于死信排查。
 * <b>不要吞异常返回成功</b>——那会让事件静默丢失。
 * </p>
 *
 * @param <T> 事件负载类型（由实现方反序列化）
 */
public interface OutboxEventHandler<T> {

    /**
     * 声明本处理器负责的事件类型，须与
     * {@link OutboxEventRecorder#record} 传入的 eventType 完全一致。
     */
    String supports();

    /**
     * 处理事件（必须幂等）。
     *
     * @param message 已解析的事件消息（含元数据与负载）
     * @throws Exception 任意异常都会触发重试；不要吞掉异常
     */
    void handle(OutboxMessage<T> message) throws Exception;
}
