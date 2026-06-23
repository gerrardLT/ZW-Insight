package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 材料字典实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bd_material")
public class BdMaterial extends BaseEntity {

    /**
     * 材料名称
     */
    private String materialName;

    /**
     * 规格
     */
    private String specification;

    /**
     * 单位
     */
    private String unit;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;
}
