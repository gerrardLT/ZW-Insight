package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 合同结算实体（2026-10-16 扩展：PROCESS 过程结算 / FINAL 竣工结算）
 * <p>
 * PROCESS：按月/节点对已完工程量（含变更签证）确认无争议价款，{@code settlementAmount}
 * = Σ来源审定额 − Σ扣款（预付款扣回/质保金扣留/其他），审批通过原子回写合同
 * {@code cumulative_settlement} 并经事件生成应收；不置合同 SETTLED。
 * FINAL：原竣工结算语义不变，另回写合同累计结算。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_final_settlement")
public class BizFinalSettlement extends BaseEntity {

    /** 过程结算类型 */
    public static final String TYPE_PROCESS = "PROCESS";
    /** 竣工结算类型（存量默认） */
    public static final String TYPE_FINAL = "FINAL";

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 结算类型（PROCESS/FINAL，V2026_108 起；存量行默认 FINAL）
     */
    private String settlementType;

    /**
     * 结算期间起（PROCESS 用）
     */
    private LocalDate periodStart;

    /**
     * 结算期间止（PROCESS 用）
     */
    private LocalDate periodEnd;

    /**
     * 结算金额（PROCESS=审定净额；FINAL=竣工结算总额）
     */
    private BigDecimal settlementAmount;

    /**
     * 申报金额合计（Σ来源申报，展示用，服务端重算）
     */
    private BigDecimal declaredAmount;

    /**
     * 争议金额合计（Σ来源争议，未确认部分）
     */
    private BigDecimal disputedAmount;

    /** 预付款扣回 */
    private BigDecimal prepayDeduct;

    /** 质保金扣留 */
    private BigDecimal retentionDeduct;

    /** 其他扣款 */
    private BigDecimal otherDeduct;

    /**
     * 结算日期
     */
    private LocalDate settlementDate;

    /**
     * 状态（DRAFT-草稿/APPROVED-已审批）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;

    /**
     * 来源明细（瞬态：产值/签证/手工行，保存时落 biz_settlement_source）
     */
    @TableField(exist = false)
    private List<BizSettlementSource> sources;
}
