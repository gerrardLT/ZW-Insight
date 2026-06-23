package com.zwinsight.hr.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.BizOfficeSupply;
import com.zwinsight.hr.domain.BizOfficeSupplyInOut;
import com.zwinsight.hr.service.OfficeSupplyInOutService;
import com.zwinsight.hr.service.OfficeSupplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 办公用品管理接口
 */
@RestController
@RequestMapping("/api/v1/hr/office-supply")
@RequiredArgsConstructor
public class OfficeSupplyController {

    private final OfficeSupplyService supplyService;
    private final OfficeSupplyInOutService inOutService;

    // ===== 办公用品 =====

    @GetMapping
    public R<PageResult<BizOfficeSupply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String supplyName) {
        return R.ok(supplyService.page(page, size, supplyName));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizOfficeSupply supply) {
        supplyService.save(supply);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizOfficeSupply supply) {
        supply.setId(id);
        supplyService.update(supply);
        return R.ok();
    }

    // ===== 出入库 =====

    @GetMapping("/in-out")
    public R<PageResult<BizOfficeSupplyInOut>> inOutPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long supplyId,
            @RequestParam(required = false) String ioType) {
        return R.ok(inOutService.page(page, size, supplyId, ioType));
    }

    @PostMapping("/in-out")
    public R<Void> saveInOut(@RequestBody BizOfficeSupplyInOut inOut) {
        inOutService.save(inOut);
        return R.ok();
    }

    @PostMapping("/in-out/{id}/submit")
    public R<Void> submitInOut(@PathVariable Long id) {
        inOutService.submit(id);
        return R.ok();
    }
}
