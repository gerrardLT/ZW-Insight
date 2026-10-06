package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目关键字段变更台账（V2026_81，不变量 I4）。
 * <p>
 * 立项（FILED 及之后）状态下修改关键字段（业主/签约方/金额/计划周期/项目名称）
 * 必须落此台账：who/when/old/new 全留痕。字段名用驼峰属性名。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_project_change_log", autoResultMap = true)
public class BizProjectChangeLog extends BaseEntity {

    @TableField("project_id")
    private Long projectId;

    @TableField("field_name")
    private String fieldName;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    @TableField("operator_id")
    private Long operatorId;
}
