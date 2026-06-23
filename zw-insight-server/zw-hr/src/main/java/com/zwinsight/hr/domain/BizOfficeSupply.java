package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 办公用品实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_office_supply")
public class BizOfficeSupply extends BaseEntity {

    /** 分类名称 */
    private String categoryName;

    /** 用品名称 */
    private String supplyName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 图片URL */
    private String imageUrl;

    /** 库存数量 */
    private Integer stockQuantity;

    /** 状态（1-启用 0-停用） */
    private Integer status;
}
