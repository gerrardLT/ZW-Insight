package com.zwinsight.contract.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工程量清单 Excel 导入 DTO
 */
@Data
public class QuantityListExcelDTO {

    @ExcelProperty("项目名称")
    private String itemName;

    @ExcelProperty("规格")
    private String specification;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("数量")
    private BigDecimal quantity;

    @ExcelProperty("单价")
    private BigDecimal unitPrice;
}
