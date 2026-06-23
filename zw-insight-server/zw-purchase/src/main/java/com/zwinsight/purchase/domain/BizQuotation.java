package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报价单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_quotation")
public class BizQuotation extends BaseEntity {

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
     * 报价总额
     */
    private BigDecimal totalAmount;

    /**
     * 状态（DRAFT-草稿/SUBMITTED-已提交）
     */
    private String status;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
}
