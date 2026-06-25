package com.zwinsight.machine.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 机械结算单导出 - Sheet2: 机械明细
 */
@Data
public class MachineSettlementDetailExcelDTO {

    @ExcelProperty("机械名称")
    private String machineName;

    @ExcelProperty("机械编号")
    private String machineCode;

    @ExcelProperty("计价方式")
    private String pricingType;

    @ExcelProperty("台班数")
    private BigDecimal shiftCount;

    @ExcelProperty("工作量")
    private BigDecimal workVolume;

    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    @ExcelProperty("小计金额")
    private BigDecimal subtotal;
}
