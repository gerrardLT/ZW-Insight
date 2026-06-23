package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 询价供应商关联实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inquiry_supplier")
public class BizInquirySupplier extends BaseEntity {

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
}
