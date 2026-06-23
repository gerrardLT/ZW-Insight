package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 询价物料明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inquiry_item")
public class BizInquiryItem extends BaseEntity {

    /**
     * 询价单ID
     */
    private Long inquiryId;

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
     * 数量
     */
    private BigDecimal quantity;
}
