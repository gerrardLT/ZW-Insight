package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务类型实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_business_type")
public class WfBusinessType extends BaseEntity {

    /**
     * 业务名称
     */
    private String typeName;

    /**
     * 业务标识
     */
    private String typeCode;

    /**
     * 父ID（0表示顶级）
     */
    private Long parentId;

    /**
     * 排序
     */
    private Integer sortOrder;
}
