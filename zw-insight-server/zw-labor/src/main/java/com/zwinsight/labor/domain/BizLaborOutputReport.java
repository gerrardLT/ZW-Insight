package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 劳务产值报告
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_labor_output_report")
public class BizLaborOutputReport extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同ID */
    private Long contractId;

    /** 本期产值 */
    private BigDecimal currentOutput;

    /** 累计产值 */
    private BigDecimal cumulativeOutput;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;
}
