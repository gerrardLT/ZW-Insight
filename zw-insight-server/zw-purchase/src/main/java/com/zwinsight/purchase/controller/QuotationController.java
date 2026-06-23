package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.dto.QuotationSubmitDTO;
import com.zwinsight.purchase.service.QuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报价接口
 */
@RestController
@RequestMapping("/api/v1/purchase/quotation")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;

    @PostMapping("/submit")
    public R<Void> submit(@RequestBody QuotationSubmitDTO dto) {
        BizQuotation quotation = new BizQuotation();
        quotation.setInquiryId(dto.getInquiryId());
        quotation.setSupplierId(dto.getSupplierId());
        quotation.setSupplierName(dto.getSupplierName());
        quotationService.submitQuote(quotation, dto.getDetails() != null ? dto.getDetails() : List.of());
        return R.ok();
    }

    @GetMapping("/inquiry/{inquiryId}")
    public R<List<BizQuotation>> getByInquiry(@PathVariable Long inquiryId) {
        return R.ok(quotationService.getByInquiry(inquiryId));
    }
}
