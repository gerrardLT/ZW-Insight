package com.zwinsight.common.event.contract;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 变更事件「已批准」领域事件契约（Published Language）。
 * <p>
 * <b>为什么放在 zw-common 而不是 zw-contract？</b>
 * 变更事件的消费方横跨多个模块（成本账户在 zw-budget、合同回写在 zw-contract、
 * 预算变更记录在 zw-budget、看板读模型在 zw-dashboard）。若契约定义在生产方模块，
 * 消费方就必须依赖生产方，模块间会退化成网状耦合。把事件契约放进共享内核，
 * 生产方与消费方都只依赖 zw-common，模块边界才能保持单向。
 * </p>
 *
 * <h3>消费约束</h3>
 * <ul>
 *   <li>投递语义为<b>至少一次</b>，消费方必须按 {@code eventId + eventType} 幂等处理</li>
 *   <li>字段只增不删不改语义；新增字段必须可为 null，保证旧消费者不被破坏</li>
 *   <li>不携带整个聚合快照：消息体越小越稳定，聚合演进不会击穿既有消费者</li>
 * </ul>
 */
@Data
public class ChangeEventApprovedEvent {

    /** 事件契约版本（消费方据此做兼容分支，缺省视为 1） */
    private Integer schemaVersion = 1;

    /** 变更事件ID（幂等键组成部分） */
    private Long eventId;

    /** 变更事件编号（日志与业务追溯用） */
    private String eventNumber;

    /** 项目ID */
    private Long projectId;

    /** 租户ID（消费方回填上下文用） */
    private Long tenantId;

    /** 成本影响总额（正=增加成本，负=节约） */
    private BigDecimal costDelta;

    /** 工期影响天数（正=延误，负=提前） */
    private Integer scheduleDelayDays;

    /** 受影响的成本账户明细（CBS current_amount 调整依据） */
    private List<AccountDelta> affectedAccounts = new ArrayList<>();

    /** 受影响的 WBS 节点 */
    private List<Long> affectedWbsIds = new ArrayList<>();

    /** 批准时间 */
    private LocalDateTime approvedAt;

    /** 批准人 */
    private Long approvedBy;

    /**
     * 单个成本账户的调整指令。
     */
    @Data
    public static class AccountDelta {
        /** 成本账户ID */
        private Long accountId;
        /** 调整方向（INCREASE-增加/DECREASE-减少） */
        private String deltaType;
        /** 调整金额（绝对值，方向由 deltaType 决定） */
        private BigDecimal deltaAmount;

        /**
         * 解析为带符号金额（INCREASE 为正，DECREASE 为负）。
         * <p>方向与数值分离存储是为了业务可读性，计算时必须收敛为单一符号量，
         * 否则各处正负号约定不一致会算出相反的账。</p>
         */
        public BigDecimal toSignedAmount() {
            BigDecimal amount = deltaAmount != null ? deltaAmount : BigDecimal.ZERO;
            if ("DECREASE".equalsIgnoreCase(deltaType)) {
                return amount.negate();
            }
            return amount;
        }
    }

    /** 是否存在需要传导的成本影响（纯范围/工期变更可短路，不必触碰 CBS） */
    public boolean hasCostImpact() {
        return costDelta != null && costDelta.signum() != 0;
    }

    /** 是否存在明细级账户调整指令 */
    public boolean hasAccountDetail() {
        return affectedAccounts != null && !affectedAccounts.isEmpty();
    }

    /**
     * 明细合计（用于与 costDelta 交叉校验，发现评估数据自相矛盾）。
     */
    public BigDecimal sumAccountDeltas() {
        if (affectedAccounts == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (AccountDelta d : affectedAccounts) {
            if (d != null) {
                sum = sum.add(d.toSignedAmount());
            }
        }
        return sum;
    }
}
