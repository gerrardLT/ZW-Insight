package com.zwinsight.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度产值数据
 */
@Data
public class MonthlyOutputDTO {

    /** 月份，格式 YYYY-MM */
    private String month;

    /** 产值金额 */
    private BigDecimal amount;
}
