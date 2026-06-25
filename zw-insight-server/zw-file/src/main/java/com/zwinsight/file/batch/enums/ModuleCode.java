package com.zwinsight.file.batch.enums;

import lombok.Getter;

/**
 * 批量导入导出模块编码
 */
@Getter
public enum ModuleCode {

    MACHINE_LEDGER("MACHINE_LEDGER", "机械台账", "机械台账导入模板.xlsx"),
    LABOR_ROSTER("LABOR_ROSTER", "劳务花名册", "劳务花名册导入模板.xlsx"),
    SYS_USER("SYS_USER", "系统用户", "系统用户导入模板.xlsx"),
    SUPPLIER("SUPPLIER", "供应商", "供应商导入模板.xlsx"),
    MATERIAL("MATERIAL", "材料字典", "材料字典导入模板.xlsx");

    /**
     * 模块编码
     */
    private final String code;

    /**
     * 模块名称
     */
    private final String name;

    /**
     * 导入模板文件名
     */
    private final String templateFileName;

    ModuleCode(String code, String name, String templateFileName) {
        this.code = code;
        this.name = name;
        this.templateFileName = templateFileName;
    }

    /**
     * 根据编码获取枚举
     */
    public static ModuleCode fromCode(String code) {
        for (ModuleCode mc : values()) {
            if (mc.getCode().equals(code)) {
                return mc;
            }
        }
        throw new IllegalArgumentException("不支持的模块编码: " + code);
    }
}
