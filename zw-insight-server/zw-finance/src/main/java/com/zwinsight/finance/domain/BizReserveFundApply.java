package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 备用金申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_reserve_fund_apply")
public class BizReserveFundApply extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 申请人 */
    private String applicant;

    /** 申请日期 */
    private LocalDate applyDate;

    /** 申请金额 */
    private BigDecimal applyAmount;

    /** 已归还金额 */
    private BigDecimal returnedAmount;

    /** 已冲抵金额 */
    private BigDecimal offsetAmount;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
