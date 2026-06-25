package com.zwinsight.purchase.portal.dto;

import com.zwinsight.purchase.domain.BizQuotationDetail;
import lombok.Data;

import java.util.List;

/**
 * 供应商报价提交 DTO
 */
@Data
public class SupplierQuotationSubmitDTO {

    /**
     * 询价单ID
     */
    private Long inquiryId;

    /**
     * 报价明细列表
     */
    private List<BizQuotationDetail> details;
}
