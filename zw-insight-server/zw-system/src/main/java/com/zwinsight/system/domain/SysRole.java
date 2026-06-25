package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 数据范围：ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF
     */
    @NotBlank(message = "数据范围不能为空")
    @Pattern(regexp = "^(ALL|DEPT_AND_CHILDREN|DEPT|PROJECT|SELF)$",
             message = "数据范围必须为 ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF 之一")
    private String dataScope;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;
}
