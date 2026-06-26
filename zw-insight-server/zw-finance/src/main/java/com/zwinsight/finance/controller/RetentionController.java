package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.domain.BizRetentionReturn;
import com.zwinsight.finance.service.RetentionMoneyService;
import com.zwinsight.finance.service.RetentionReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 质保金管理接口
 */
@RestController
@RequestMapping("/api/v1/finance/retention")
@RequiredArgsConstructor
public class RetentionController {

    private final RetentionMoneyService retentionMoneyService;
    private final RetentionReturnService retentionReturnService;

    /**
     * 分页查询质保金
     */
    @GetMapping("/page")
    public R<PageResult<BizRetentionMoney>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(retentionMoneyService.page(page, size, projectId, contractId));
    }

    /**
     * 新增质保金
     */
    @PostMapping
    public R<Void> save(@RequestBody BizRetentionMoney retentionMoney) {
        retentionMoneyService.save(retentionMoney);
        return R.ok();
    }

    /**
     * 查询即将到期质保金
     */
    @GetMapping("/expiring")
    public R<List<BizRetentionMoney>> getExpiring(
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(retentionMoneyService.getExpiring(days));
    }

    /**
     * 新增返还申请
     */
    @PostMapping("/return")
    public R<Void> saveReturn(@RequestBody BizRetentionReturn retentionReturn) {
        retentionReturnService.save(retentionReturn);
        return R.ok();
    }

    /**
     * 提交返还申请
     */
    @PostMapping("/return/{id}/submit")
    public R<Void> submitReturn(@PathVariable Long id) {
        retentionReturnService.submit(id);
        return R.ok();
    }
}
