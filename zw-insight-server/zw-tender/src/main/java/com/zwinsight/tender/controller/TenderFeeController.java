package com.zwinsight.tender.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.tender.domain.BizTenderFee;
import com.zwinsight.tender.service.TenderFeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 投标费用接口
 */
@RestController
@RequestMapping("/api/v1/tender/fee")
@RequiredArgsConstructor
public class TenderFeeController {

    private final TenderFeeService feeService;

    @GetMapping
    @GetMapping("/page")
    public R<PageResult<BizTenderFee>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long registerId) {
        return R.ok(feeService.page(page, size, registerId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizTenderFee fee) {
        feeService.save(fee);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizTenderFee fee) {
        fee.setId(id);
        feeService.update(fee);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        feeService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/confirm-payment")
    public R<Void> confirmPayment(@PathVariable Long id, @RequestBody Map<String, String> params) {
        feeService.confirmPayment(id, params.get("receiptFile"));
        return R.ok();
    }
}
