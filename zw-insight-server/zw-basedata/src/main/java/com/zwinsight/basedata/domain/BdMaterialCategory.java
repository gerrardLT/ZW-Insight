package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 材料分类实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bd_material_category")
public class BdMaterialCategory extends BaseEntity {

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
