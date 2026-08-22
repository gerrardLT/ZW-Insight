package com.zwinsight.labor.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资发放趋势条目 VO
 */
@Data
public class PayrollTrendItemVO {

    /** 统计月份（格式：YYYY-MM） */
    private String month;

    /** 结算总额 */
    private BigDecimal totalSettlement;

    /** 已付总额 */
    private BigDecimal totalPaid;

    /** 未付总额 */
    private BigDecimal totalUnpaid;
}
