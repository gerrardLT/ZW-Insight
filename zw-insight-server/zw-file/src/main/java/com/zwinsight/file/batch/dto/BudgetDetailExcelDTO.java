package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 预算明细导入 Excel DTO
 */
@Data
public class BudgetDetailExcelDTO {

    @ExcelProperty("费用类别")
    private String costCategory;

    @ExcelProperty("费用子类")
    private String costSubcategory;

    @ExcelProperty("项目名称")
    private String itemName;

    @ExcelProperty("规格")
    private String specification;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("预算数量")
    private String budgetQuantity;

    @ExcelProperty("预算单价")
    private String budgetUnitPrice;

    @ExcelProperty("预算合计")
    private String budgetTotalPrice;

    @ExcelProperty("备注")
    private String remark;
}
