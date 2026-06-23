package com.zwinsight.system.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 用户导入导出DTO
 */
@Data
public class SysUserExcelDTO {

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;
}
