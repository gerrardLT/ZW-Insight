package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 定标结果实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_award_result")
public class BizAwardResult extends BaseEntity {

    /**
     * 询价单ID
     */
    private Long inquiryId;

    /**
     * 中标供应商ID
     */
    private Long supplierId;

    /**
     * 中标供应商名称
     */
    private String supplierName;

    /**
     * 中标金额
     */
    private BigDecimal totalAmount;

    /**
     * 排名
     */
    private Integer ranking;
}
