package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 系统用户导入 Excel DTO
 */
@Data
public class SysUserExcelDTO {

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("部门")
    private String deptName;

    @ExcelProperty("岗位")
    private String postName;

    @ExcelProperty("邮箱")
    private String email;
}
