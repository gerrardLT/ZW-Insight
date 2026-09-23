package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 招待费专项明细实体（V2026_60）
 * <p>
 * 对标 docs/资金流转流程.md §6.3「招待费最少字段」：项目/日期/招待对象/事项/人数/地点/
 * 金额/经办人/审批人/发票/支付方式/备注。金额与审批人由报销明细行与审批流承载，
 * 本表补齐结构化业务字段，使 §6.3 的分析维度（人均金额、单次最高、责任人累计、
 * 无事由/无对象/无审批笔数）可由 SQL 直接聚合。
 * </p>
 * <p>约束：与报销明细一对一（uk_reimbursement_detail）；报销明细科目为
 * {@link BizReimbursementDetail#CATEGORY_ENTERTAINMENT} 时本记录必填（Service 强校验）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_entertainment_detail")
public class BizEntertainmentDetail extends BaseEntity {

    /** 支付方式：公账 */
    public static final String PAY_METHOD_PUBLIC = "PUBLIC";
    /** 支付方式：个人报销 */
    public static final String PAY_METHOD_REIMBURSE = "REIMBURSE";

    /** 报销明细ID（biz_reimbursement_detail.id，一对一） */
    private Long reimbursementDetailId;

    /** 项目ID（冗余，便于按项目分析） */
    private Long projectId;

    /** 招待日期 */
    private LocalDate entertainDate;

    /** 招待对象单位（甲方/监理/供应商等） */
    private String hostCompany;

    /** 招待对象人员 */
    private String hostPersons;

    /** 招待事由 */
    private String entertainReason;

    /** 我方人数 */
    private Integer hostCount;

    /** 对方人数 */
    private Integer guestCount;

    /** 消费地点 */
    private String location;

    /** 经办人ID */
    private Long handlerId;

    /** 经办人姓名 */
    private String handlerName;

    /** 发票是否完整（0-否 1-是） */
    private Integer hasInvoice;

    /** 支付方式（PUBLIC-公账/REIMBURSE-个人报销） */
    private String payMethod;

    /** 是否有事前审批（0-无 1-有；文档 §11 招待费预警项） */
    private Integer preApproved;

    /** 备注 */
    private String remark;
}
