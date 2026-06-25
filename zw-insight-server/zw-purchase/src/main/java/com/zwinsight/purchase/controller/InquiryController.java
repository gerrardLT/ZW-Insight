package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.service.BidRankingService;
import com.zwinsight.purchase.service.InquiryService;
import com.zwinsight.purchase.service.QuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 询价单接口
 */
@RestController
@RequestMapping("/api/v1/purchase/inquiry")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;
    private final QuotationService quotationService;
    private final BidRankingService bidRankingService;

    @GetMapping
    @GetMapping("/page")
    public R<PageResult<BizInquiry>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(inquiryService.page(page, size));
    }

    @GetMapping("/{id}")
    public R<BizInquiry> getById(@PathVariable Long id) {
        return R.ok(inquiryService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizInquiry inquiry) {
        inquiryService.save(inquiry);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizInquiry inquiry) {
        inquiry.setId(id);
        inquiryService.update(inquiry);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        inquiryService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    @PutMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        inquiryService.publish(id);
        return R.ok();
    }

    /** 获取询价单的报价列表（前端路径别名） */
    @GetMapping("/{id}/quotations")
    public R<java.util.List<com.zwinsight.purchase.domain.BizQuotation>> getQuotations(@PathVariable Long id) {
        return R.ok(quotationService.getByInquiry(id));
    }

    /** 定标确认（前端路径别名） */
    @PostMapping("/confirm-bid")
    public R<Void> confirmBid(@RequestBody java.util.Map<String, Long> body) {
        Long inquiryId = body.get("inquiryId");
        Long supplierId = body.get("supplierId");
        bidRankingService.confirmWinner(inquiryId, supplierId);
        return R.ok();
    }
}
