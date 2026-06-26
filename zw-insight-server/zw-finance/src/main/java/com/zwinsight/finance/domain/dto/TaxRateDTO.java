package com.zwinsight.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 税率响应 DTO
 */
@Data
public class TaxRateDTO {

    /** 主键ID */
    private Long id;

    /** 税率名称 */
    private String name;

    /** 税率数值（如 13.00 表示 13%） */
    private BigDecimal rateValue;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
