package com.zwinsight.file.template;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统模板实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_template")
public class SysTemplate extends BaseEntity {

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板类型：IMPORT / EXPORT / PRINT
     */
    private String templateType;

    /**
     * 模块编码（如 material_inbound, finance_invoice）
     */
    private String moduleCode;

    /**
     * 关联文件ID（IMPORT/EXPORT 模板对应的 Excel 文件）
     */
    private Long fileId;

    /**
     * 模板内容（PRINT 模板的 HTML 内容）
     */
    private String templateContent;

    /**
     * 是否默认模板（0-否 1-是）
     */
    private Integer isDefault;
}
