package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizBill;
import com.zwinsight.finance.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 票据台账接口（应收/应付承兑汇票：背书/贴现/兑现/兑付）
 */
@RestController
@RequestMapping("/api/v1/finance/bill")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class BillController {

    private final BillService billService;

    /**
     * 分页查询票据台账
     */
    @GetMapping("/page")
    public R<PageResult<BizBill>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId) {
        return R.ok(billService.page(page, size, direction, status, projectId));
    }

    /**
     * 登记票据
     */
    @PostMapping
    public R<Void> register(@RequestBody BizBill bill) {
        billService.register(bill);
        return R.ok();
    }

    /**
     * 背书转让（仅应收票据）
     */
    @PostMapping("/{id}/endorse")
    public R<Void> endorse(
            @PathVariable Long id,
            @RequestParam String endorseeName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endorseDate) {
        billService.endorse(id, endorseeName, endorseDate);
        return R.ok();
    }

    /**
     * 贴现变现（仅应收票据，按剩余天数/360 单利计息）
     */
    @PostMapping("/{id}/discount")
    public R<Void> discount(
            @PathVariable Long id,
            @RequestParam BigDecimal annualRate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate discountDate) {
        billService.discount(id, discountDate, annualRate);
        return R.ok();
    }

    /**
     * 到期兑现（仅应收票据）
     */
    @PostMapping("/{id}/redeem")
    public R<Void> redeem(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate redeemDate) {
        billService.redeem(id, redeemDate);
        return R.ok();
    }

    /**
     * 到期兑付（仅应付票据）
     */
    @PostMapping("/{id}/pay-out")
    public R<Void> payOut(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDate) {
        billService.payOut(id, payDate);
        return R.ok();
    }

    /**
     * 查询即将到期票据（N 天内，含已逾期）
     */
    @GetMapping("/expiring")
    public R<List<BizBill>> expiring(@RequestParam(defaultValue = "30") int days) {
        return R.ok(billService.expiring(days));
    }

    /**
     * 票据统计（应收/应付持有、贴现净额、背书转出）
     */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(required = false) Long projectId) {
        return R.ok(billService.statistics(projectId));
    }
}
