package com.zwinsight.contract.service;

/**
 * 变更事件领域事件类型常量。
 * <p>
 * 集中定义避免各处硬编码字符串导致事件名拼写漂移（拼错的事件名不会编译报错，
 * 只会在运行时静默无人消费，是最难排查的一类缺陷）。
 * </p>
 * <p>下游 Handler 通过实现 {@code OutboxEventHandler} 并以本常量注册 Bean 名来消费。</p>
 */
public final class ChangeEventEvents {

    private ChangeEventEvents() {
    }

    /** 聚合根类型标识 */
    public static final String AGGREGATE_TYPE = "ChangeEvent";

    /** 事件登记（草稿创建） */
    public static final String TYPE_CREATED = "CHANGE_EVENT_CREATED";

    /** 事件内容更新 */
    public static final String TYPE_UPDATED = "CHANGE_EVENT_UPDATED";

    /** 转入评估中 */
    public static final String TYPE_ASSESSING = "CHANGE_EVENT_ASSESSING";

    /** 影响评估已提交（进入审批） */
    public static final String TYPE_ASSESSED = "CHANGE_EVENT_ASSESSED";

    /**
     * 已批准 —— 变更主链的关键跃迁。
     * <p>下游必须消费此事件完成传导：CBS 当前预算调整、合同累计变更金额回写、
     * 必要时生成预算变更记录。</p>
     */
    public static final String TYPE_APPROVED = "CHANGE_EVENT_APPROVED";

    /** 已驳回 */
    public static final String TYPE_REJECTED = "CHANGE_EVENT_REJECTED";

    /** 退回重新评估 */
    public static final String TYPE_REASSESS = "CHANGE_EVENT_REASSESS";

    /** 已作废 */
    public static final String TYPE_CANCELLED = "CHANGE_EVENT_CANCELLED";
}
