package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质保金实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_retention_money")
public class BizRetentionMoney extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同ID */
    private Long contractId;

    /** 质保金比例 */
    private BigDecimal retentionRate;

    /** 质保金金额 */
    private BigDecimal retentionAmount;

    /** 质保期（月） */
    private Integer retentionPeriod;

    /** 质保开始日期 */
    private LocalDate startDate;

    /** 质保到期日期 */
    private LocalDate expireDate;

    /** 已返还金额 */
    private BigDecimal returnedAmount;

    /** 状态（ACTIVE-有效/EXPIRED-到期/RETURNED-已返还） */
    private String status;
}
