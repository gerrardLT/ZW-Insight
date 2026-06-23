package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报价明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_quotation_detail")
public class BizQuotationDetail extends BaseEntity {

    /**
     * 报价单ID
     */
    private Long quotationId;

    /**
     * 询价物料ID
     */
    private Long inquiryItemId;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 合计金额
     */
    private BigDecimal totalPrice;
}
