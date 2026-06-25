package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 材料字典导入 Excel DTO
 */
@Data
public class MaterialExcelDTO {

    @ExcelProperty("材料名称")
    private String materialName;

    @ExcelProperty("材料编码")
    private String materialCode;

    @ExcelProperty("规格型号")
    private String specification;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("材料分类")
    private String category;
}
