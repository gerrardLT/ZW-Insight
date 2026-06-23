package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 产值报告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_output_report")
public class BizOutputReport extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 报告期间（如 2024-01）
     */
    private String reportPeriod;

    /**
     * 本期产值
     */
    private BigDecimal currentOutput;

    /**
     * 累计产值
     */
    private BigDecimal cumulativeOutput;

    /**
     * 确认日期
     */
    private LocalDate confirmDate;

    /**
     * 状态（DRAFT-草稿/APPROVED-已审批）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
