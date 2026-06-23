package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.service.InquiryService;
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

    @GetMapping
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

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        inquiryService.publish(id);
        return R.ok();
    }
}
