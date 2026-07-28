package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 产值报告明细（按工程量清单条目填报，可选）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_output_report_detail")
public class BizOutputReportDetail extends BaseEntity {

    /** 产值报告ID */
    private Long reportId;

    /** 工程量清单条目ID */
    private Long boqItemId;

    /** 本期完成工程量 */
    private BigDecimal quantity;

    /** 本期金额（本期完成工程量 × 清单综合单价） */
    private BigDecimal amount;
}
