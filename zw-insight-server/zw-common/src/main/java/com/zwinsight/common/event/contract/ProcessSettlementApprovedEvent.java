package com.zwinsight.common.event.contract;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 过程结算「已生效」领域事件契约（Published Language，V2026_108）。
 * <p>
 * <b>为什么放在 zw-common：</b>生产方是 zw-contract 的 {@code FinalSettlementService}，
 * 消费方是 zw-finance 的应收台账。zw-finance 已依赖 zw-contract，反向注入会形成
 * Maven 循环依赖，故走 Spring 事件解耦（与 {@code ChangeEventApprovedEvent} 同一模式）。
 * </p>
 * <h3>消费约束</h3>
 * <ul>
 *   <li>同步 {@code @EventListener}：在发布方事务内执行，消费失败整体回滚，
 *       不允许「结算已批但应收未生成」的半成品</li>
 *   <li>投递语义为<b>至少一次</b>（发布方事务重试可重放），消费方必须按
 *       {@code settlementId} 幂等处理</li>
 *   <li>字段只增不删不改语义；新增字段必须可为 null</li>
 * </ul>
 */
@Data
public class ProcessSettlementApprovedEvent {

    /** 过程结算单ID（幂等键） */
    private Long settlementId;

    /** 项目ID */
    private Long projectId;

    /** 施工合同ID */
    private Long contractId;

    /** 本期审定净额（Σ来源审定 − Σ扣款；应收金额基准） */
    private BigDecimal netAmount;

    /** 本期质保金扣留额（>0 时质保金监听器按单据事实计提） */
    private BigDecimal retentionDeduct;

    /** 结算期间止（应收到期日基准：期间末 + 信用天数） */
    private LocalDate periodEnd;

    /** 结算日期（展示追溯用，可空） */
    private LocalDate settlementDate;
}
