package com.zwinsight.finance.controller;

import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizRetentionReturn;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.RetentionMoneyService;
import com.zwinsight.common.security.RequiresPermission;
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
@RequiresPermission("finance:view")
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
     * 分页查询质保金返还单
     * <p>2026-10-12 新增：补齐「提交返还审批」入口所需的数据源——原先只有登记(POST /return)
     * 与提交(POST /return/{id}/submit)两个写端点，且 saveReturn 不回传 id，前端无从列出/提交已登记的返还单。</p>
     */
    @GetMapping("/return/page")
    public R<PageResult<BizRetentionReturn>> pageReturn(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long retentionId) {
        return R.ok(retentionReturnService.pageReturn(page, size, retentionId));
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
     * 查询已逾期质保金（到期日早于今天且未返还，工作台逾期统计卡数据源）
     */
    @GetMapping("/overdue")
    public R<List<BizRetentionMoney>> getOverdue() {
        return R.ok(retentionMoneyService.getOverdue());
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
