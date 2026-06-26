package com.zwinsight.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 税率新增/修改请求 DTO
 */
@Data
public class TaxRateRequest {

    /** 税率名称（1-30字符） */
    private String name;

    /** 税率数值（0.01-99.99，不超过2位小数） */
    private BigDecimal rateValue;
}
