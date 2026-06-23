package com.zwinsight.purchase.dto;

import com.zwinsight.purchase.domain.BizQuotationDetail;
import lombok.Data;

import java.util.List;

/**
 * 供应商报价提交 DTO
 */
@Data
public class QuotationSubmitDTO {

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
     * 报价明细列表
     */
    private List<BizQuotationDetail> details;
}
