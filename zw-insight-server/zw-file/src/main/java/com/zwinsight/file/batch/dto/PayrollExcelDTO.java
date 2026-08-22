package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 工资单导入 Excel DTO
 * <p>
 * 工资单金额由周期内已审批用工单自动汇总，导入仅创建单据头，
 * teamId 由监听器按班组名称解析后回填（非 Excel 列）。
 * </p>
 */
@Data
public class PayrollExcelDTO {

    @ExcelProperty("班组名称")
    private String teamName;

    @ExcelProperty("用工类型")
    private String orderType;

    @ExcelProperty("周期开始日期")
    private String periodStart;

    @ExcelProperty("周期结束日期")
    private String periodEnd;

    /** 监听器解析回填，非 Excel 列 */
    private Long teamId;
}
