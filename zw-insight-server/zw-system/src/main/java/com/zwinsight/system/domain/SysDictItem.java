package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典值实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {

    /**
     * 字典ID
     */
    private Long dictId;

    /**
     * 父ID（支持树形字典值）
     */
    private Long parentId;

    /**
     * 标签
     */
    private String label;

    /**
     * 值
     */
    private String value;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
