package com.zwinsight.common.event.finance;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支出结算「已生效 / 已冲销」领域事件契约（Published Language，V2026_115 应付台账）。
 * <p>
 * <b>为什么放在 zw-common：</b>生产方是 zw-subcontract / zw-purchase / zw-labor / zw-machine
 * 的各结算服务，消费方是 zw-finance 的应付台账。四个模块与 zw-finance 之间任一直接依赖
 * 都可能成环（zw-finance 付款侧经 ContractPayableMapper 跨表读五类合同），故走
 * Spring 事件解耦（与 {@code ProcessSettlementApprovedEvent} 同一模式）。
 * </p>
 * <h3>消费约束</h3>
 * <ul>
 *   <li>同步 {@code @EventListener}：在发布方事务内执行，消费失败整体回滚，
 *       不允许「结算已批但应付未生成」的半成品</li>
 *   <li>投递语义为<b>至少一次</b>，消费方必须按
 *       {@code sourceType + sourceId + contractId} 幂等处理</li>
 *   <li>生成口径：应付金额 = 本次审批通过的结算金额（单据事实）；
 *       合同历史余额（结算−已付）由迁移 BACKFILL 行覆盖，两者不重复计量</li>
 *   <li>冲销事件（reversed=true）：对应结算单删除/作废时按原额冲减应付行，
 *       消费方按 GREATEST(0) 截断并同步项目应付单值</li>
 *   <li>字段只增不删不改语义；新增字段必须可为 null</li>
 * </ul>
 */
@Data
public class ExpenseSettlementPayableEvent {

    /** 来源类型（SUBCONTRACT_SETTLEMENT / PURCHASE_SETTLEMENT / LABOR_SETTLEMENT / MACHINE_SETTLEMENT） */
    private String sourceType;

    /** 来源结算单ID（幂等键之一） */
    private Long sourceId;

    /** 项目ID */
    private Long projectId;

    /** 支出合同ID（幂等键之一；机械结算按明细合同逐条发布） */
    private Long contractId;

    /** 合同类别（SUBCONTRACT / PURCHASE / LABOR / MACHINE，与付款申请 contractCategory 同码表） */
    private String contractCategory;

    /** 本事件对应的结算金额（正数；冲销事件传原额，消费方截断至余额） */
    private BigDecimal amount;

    /** 是否冲销（false=生效生成应付；true=结算单删除/作废冲减应付） */
    private boolean reversed;

    /** 备注（生成行 remark，可空） */
    private String remark;
}
