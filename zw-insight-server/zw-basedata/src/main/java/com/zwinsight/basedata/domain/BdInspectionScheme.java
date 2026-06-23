package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检查方案实体（质量/安全通用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bd_inspection_scheme")
public class BdInspectionScheme extends BaseEntity {

    /**
     * 方案名称
     */
    private String schemeName;

    /**
     * 方案类型（QUALITY-质量 SAFETY-安全）
     */
    private String schemeType;

    /**
     * 检查项内容（JSON格式）
     */
    private String content;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;
}
