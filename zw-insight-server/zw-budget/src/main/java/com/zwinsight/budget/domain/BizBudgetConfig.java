package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算管控配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_budget_config")
public class BizBudgetConfig extends BaseEntity {

    /**
     * 项目ID（null表示全局配置）
     */
    private Long projectId;

    /**
     * 管控模式（FORBID-禁止超支/WARN-警告超支）
     */
    private String controlMode;

    /**
     * 描述
     */
    private String description;
}
