package com.zwinsight.common.event.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事务性发件箱 Mapper（sys_outbox_event）
 * <p>
 * 表名以 {@code sys_} 开头，被 TenantLineInnerInterceptor 的 ignoreTable 规则排除，
 * 因此投递器可在无租户上下文下跨租户扫描待投递事件（与 act_* 流程表同理）。
 * </p>
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /**
     * 查询到期可投递的事件（PENDING 且 next_retry_at 为空或已到期），按写入顺序投递。
     * <p>
     * 使用 {@code last("LIMIT n")} 而非分页插件：投递器只关心「最早的一批」，
     * 分页语义反而会带来 offset 漂移。
     * </p>
     *
     * @param now   当前时间（用于判断重试是否到期）
     * @param limit 单批最大条数
     * @return 待投递事件列表
     */
    default List<OutboxEvent> selectDueEvents(LocalDateTime now, int limit) {
        LambdaQueryWrapper<OutboxEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutboxEvent::getStatus, OutboxStatus.PENDING.name())
                .and(w -> w.isNull(OutboxEvent::getNextRetryAt)
                        .or().le(OutboxEvent::getNextRetryAt, now))
                .orderByAsc(OutboxEvent::getCreatedAt)
                .orderByAsc(OutboxEvent::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return selectList(wrapper);
    }

    /**
     * 乐观占用一条事件：仅当状态仍为 PENDING 时才置为 DELIVERING，
     * 返回 1 表示本实例抢到投递权（集群多实例下防止重复投递）。
     *
     * @param id 事件 ID
     * @return 影响行数（1=占用成功，0=已被其他实例占用或状态已变）
     */
    @Update("UPDATE sys_outbox_event SET status = 'DELIVERING', attempts = attempts + 1, "
            + "updated_at = NOW() WHERE id = #{id} AND status = 'PENDING'")
    int claimForDelivery(@Param("id") Long id);

    /**
     * 标记投递成功。
     *
     * @param id          事件 ID
     * @param publishedAt 投递成功时间
     * @return 影响行数
     */
    @Update("UPDATE sys_outbox_event SET status = 'DELIVERED', published_at = #{publishedAt}, "
            + "last_error = NULL, next_retry_at = NULL, updated_at = NOW() WHERE id = #{id}")
    int markDelivered(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);

    /**
     * 标记投递失败并安排下一次重试（指数退避由调用方计算 nextRetryAt）。
     * <p>
     * 状态回落 PENDING 以便下轮重新抢占；attempts 已在 claim 时自增，此处不再累加。
     * </p>
     *
     * @param id          事件 ID
     * @param error       错误摘要（截断至 1000 字符以内由调用方保证）
     * @param nextRetryAt 下次重试时间
     * @return 影响行数
     */
    @Update("UPDATE sys_outbox_event SET status = 'PENDING', last_error = #{error}, "
            + "next_retry_at = #{nextRetryAt}, updated_at = NOW() WHERE id = #{id}")
    int markFailedWithRetry(@Param("id") Long id,
                            @Param("error") String error,
                            @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /**
     * 转入死信（超过最大重试次数），需人工介入排查。
     *
     * @param id    事件 ID
     * @param error 错误摘要
     * @return 影响行数
     */
    @Update("UPDATE sys_outbox_event SET status = 'DEAD', last_error = #{error}, "
            + "next_retry_at = NULL, updated_at = NOW() WHERE id = #{id}")
    int markDead(@Param("id") Long id, @Param("error") String error);

    /**
     * 清理已投递且超过保留期的事件（避免表无限膨胀）。
     *
     * @param before 保留期边界时间（早于此时间的已投递事件将被物理删除）
     * @return 删除行数
     */
    @Update("DELETE FROM sys_outbox_event WHERE status = 'DELIVERED' AND published_at < #{before}")
    int purgeDeliveredBefore(@Param("before") LocalDateTime before);
}
