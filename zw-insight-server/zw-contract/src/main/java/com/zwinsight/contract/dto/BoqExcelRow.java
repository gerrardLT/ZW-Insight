package com.zwinsight.contract.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工程量清单 Excel 行 DTO
 * <p>
 * 对应 BOQ 模板 Excel 列结构：
 * 列0: 项目编码 | 列1: 项目名称 | 列2: 单位 | 列3: 工程数量 | 列4: 综合单价 | 列5: 合价
 */
@Data
public class BoqExcelRow {

    /** 项目编码（如 1、1.1、1.1.1） */
    @ExcelProperty(index = 0)
    private String itemCode;

    /** 项目名称 */
    @ExcelProperty(index = 1)
    private String itemName;

    /** 计量单位 */
    @ExcelProperty(index = 2)
    private String unit;

    /** 工程数量 */
    @ExcelProperty(index = 3)
    private BigDecimal quantity;

    /** 综合单价 */
    @ExcelProperty(index = 4)
    private BigDecimal unitPrice;

    /** 合价 */
    @ExcelProperty(index = 5)
    private BigDecimal totalPrice;
}
