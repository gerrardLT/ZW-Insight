package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 过程结算来源明细（V2026_108）。
 * <p>
 * 表达「本期结算确认了什么」：已审批产值报告 / 已审批变更签证 / 手工行（索赔、奖罚等）。
 * 申报/审定/争议三级金额体现双方确认过程；同一来源（source_type+source_id）
 * 至多被一张 SUBMITTED/APPROVED 结算占用（提交与审批生效前双重校验）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_settlement_source")
public class BizSettlementSource extends BaseEntity {

    /** 来源类型：已审批产值报告 */
    public static final String TYPE_OUTPUT_REPORT = "OUTPUT_REPORT";
    /** 来源类型：已审批变更签证 */
    public static final String TYPE_VISA = "VISA";
    /** 来源类型：手工行（索赔/奖罚/其他） */
    public static final String TYPE_MANUAL = "MANUAL";

    /** 项目ID（级联清理登记用） */
    private Long projectId;

    /** 所属结算单ID */
    private Long settlementId;

    /** 来源类型（OUTPUT_REPORT/VISA/MANUAL） */
    private String sourceType;

    /** 来源单据ID（MANUAL 行为空） */
    private Long sourceId;

    /** 描述（MANUAL 行必填；引用行展示来源单号） */
    private String description;

    /** 申报金额（乙方申报） */
    private BigDecimal declaredAmount;

    /** 审定金额（甲方/监理确认，参与结算净额计算） */
    private BigDecimal confirmedAmount;

    /** 争议金额（未确认，留待下期或竣工结算） */
    private BigDecimal disputedAmount;

    /** 核减原因（审定 < 申报时必填） */
    private String reduceReason;
}
