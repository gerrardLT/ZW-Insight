package com.zwinsight.machine.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 机械结算单导出 - Sheet1: 结算汇总
 */
@Data
public class MachineSettlementExcelDTO {

    @ExcelProperty("结算单编号")
    private String settlementCode;

    @ExcelProperty("结算周期")
    private String period;

    @ExcelProperty("结算总金额")
    private BigDecimal totalAmount;

    @ExcelProperty("状态")
    private String statusText;

    @ExcelProperty("创建时间")
    private String createdAt;
}
