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
@TableName("biz_bid_result")
public class BizBidResult extends BaseEntity {

    /**
     * 询价单ID
     */
    private Long inquiryId;

    /**
     * 供应商ID
     */
    private Long supplierId;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 排名
     */
    private Integer ranking;

    /**
     * 报价总额
     */
    private BigDecimal totalAmount;

    /**
     * 是否中标（0-否 1-是）
     */
    private Integer isWinner;
}
