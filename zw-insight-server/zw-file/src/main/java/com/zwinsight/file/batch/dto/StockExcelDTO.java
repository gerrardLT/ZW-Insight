package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 库存查询导出 Excel DTO（仅导出，不支持导入；含安全库存预警标记）
 */
@Data
public class StockExcelDTO {

    @ExcelProperty("材料名称")
    private String materialName;

    @ExcelProperty("规格型号")
    private String specification;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("当前库存")
    private String stockQuantity;

    @ExcelProperty("最低库存")
    private String minStock;

    @ExcelProperty("所属项目")
    private String projectName;

    @ExcelProperty("库存状态")
    private String warningStatus;
}
