package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BizSupplierBlacklist;
import com.zwinsight.basedata.service.SupplierBlacklistService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 供应商黑名单接口
 */
@RestController
@RequestMapping("/api/v1/basedata/supplier-blacklist")
@RequiredArgsConstructor
public class SupplierBlacklistController {

    private final SupplierBlacklistService blacklistService;

    /**
     * 分页查询黑名单
     */
    @GetMapping
    public R<PageResult<BizSupplierBlacklist>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(blacklistService.page(page, size));
    }

    /**
     * 加入黑名单
     */
    @PostMapping
    public R<Void> add(@RequestBody BizSupplierBlacklist blacklist) {
        blacklistService.add(blacklist.getSupplierId(), blacklist.getSupplierName(), blacklist.getReason());
        return R.ok();
    }

    /**
     * 移出黑名单
     */
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        blacklistService.remove(id);
        return R.ok();
    }

    /**
     * 检查是否在黑名单中
     */
    @GetMapping("/check/{supplierId}")
    public R<Boolean> isBlacklisted(@PathVariable Long supplierId) {
        return R.ok(blacklistService.isBlacklisted(supplierId));
    }
}
