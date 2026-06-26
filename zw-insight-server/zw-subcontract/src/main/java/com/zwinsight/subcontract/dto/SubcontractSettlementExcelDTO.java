package com.zwinsight.subcontract.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 分包结算单汇总导出 Excel DTO
 */
@Data
public class SubcontractSettlementExcelDTO {

    @ExcelProperty("合同编号")
    private String contractCode;

    @ExcelProperty("合同名称")
    private String contractName;

    @ExcelProperty("分包方")
    private String subcontractor;

    @ExcelProperty("本次结算金额")
    private BigDecimal settlementAmount;

    @ExcelProperty("状态")
    private String statusText;

    @ExcelProperty("创建时间")
    private String createdAt;
}
