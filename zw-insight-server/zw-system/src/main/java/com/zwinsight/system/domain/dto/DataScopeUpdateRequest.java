package com.zwinsight.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 数据权限范围更新请求
 */
@Data
public class DataScopeUpdateRequest {

    /**
     * 数据范围：ALL / DEPT_AND_CHILDREN / DEPT / PROJECT / SELF
     */
    @NotBlank(message = "数据范围不能为空")
    @Pattern(regexp = "^(ALL|DEPT_AND_CHILDREN|DEPT|PROJECT|SELF)$",
             message = "数据范围必须为 ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF 之一")
    private String dataScope;
}
