package com.zwinsight.subcontract.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 分包结算明细导出 Excel DTO
 */
@Data
public class SubcontractSettlementDetailExcelDTO {

    @ExcelProperty("序号")
    private Integer sortOrder;

    @ExcelProperty("工程项名称")
    private String itemName;

    @ExcelProperty("计量单位")
    private String unit;

    @ExcelProperty("本次结算数量")
    private BigDecimal quantity;

    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    @ExcelProperty("本次结算金额")
    private BigDecimal amount;

    @ExcelProperty("备注")
    private String remark;
}
