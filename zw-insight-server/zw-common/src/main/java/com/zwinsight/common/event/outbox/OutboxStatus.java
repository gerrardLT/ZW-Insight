package com.zwinsight.common.event.outbox;

/**
 * 发件箱事件投递状态。
 *
 * <pre>
 *   PENDING ──claim──▶ DELIVERING ──成功──▶ DELIVERED（保留期后物理清理）
 *                          │
 *                        失败
 *                          │
 *              attempts < max ? ──是──▶ PENDING（next_retry_at 指数退避）
 *                          │
 *                         否
 *                          ▼
 *                        DEAD（死信，需人工介入）
 * </pre>
 */
public enum OutboxStatus {

    /** 待投递（含等待重试） */
    PENDING,

    /** 投递中（已被某个实例乐观占用，防止集群重复投递） */
    DELIVERING,

    /** 已成功投递 */
    DELIVERED,

    /** 超过最大重试次数，转入死信 */
    DEAD;

    /**
     * 安全解析，未知值返回 null（读取侧宽容，写入侧严格）。
     */
    public static OutboxStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (OutboxStatus s : values()) {
            if (s.name().equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        return null;
    }
}
