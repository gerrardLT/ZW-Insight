package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算控制配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_budget_control_config")
public class SysBudgetControlConfig extends BaseEntity {

    /** 项目ID（NULL表示系统默认规则） */
    private Long projectId;

    /** 控制模式(WARN_ONLY/BLOCK/EXEMPT) */
    private String controlMode;

    /** 预警阈值(50-99，百分比整数) */
    private Integer warningThreshold;

    /** 是否系统默认(1是/0否) */
    private Integer isDefault;

    /** 项目名称（非表字段，分页时按 projectId 批量回填） */
    @TableField(exist = false)
    private String projectName;
}
