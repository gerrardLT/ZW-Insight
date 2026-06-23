package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 回滚注册表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_rollback_action")
public class WfRollbackAction extends BaseEntity {

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 目标表名
     */
    private String targetTable;

    /**
     * 目标字段
     */
    private String targetField;

    /**
     * 目标记录ID
     */
    private Long targetId;

    /**
     * 操作类型：ADD/SUBTRACT/SET
     */
    private String operationType;

    /**
     * 操作值
     */
    private BigDecimal operValue;

    /**
     * 原始值
     */
    private BigDecimal originalValue;

    /**
     * 是否已回滚（0-未回滚 1-已回滚）
     */
    private Integer executed;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
