package com.zwinsight.site.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 检查方案列表 VO（供前端展示方案选择列表）
 */
@Data
public class InspectionSchemeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 方案ID */
    private Long id;

    /** 方案名称 */
    private String schemeName;

    /** 检查类型（QUALITY/SAFETY），对应 bd_inspection_scheme.scheme_type */
    private String schemeType;
}
