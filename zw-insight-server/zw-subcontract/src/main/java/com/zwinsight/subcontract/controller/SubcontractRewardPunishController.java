package com.zwinsight.subcontract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.subcontract.domain.BizSubcontractRewardPunish;
import com.zwinsight.subcontract.service.SubcontractRewardPunishService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分包奖罚接口
 */
@RestController
@RequestMapping("/api/v1/subcontract/reward-punish")
@RequiredArgsConstructor
public class SubcontractRewardPunishController {

    private final SubcontractRewardPunishService rewardPunishService;

    @GetMapping
    @GetMapping("/page")
    public R<PageResult<BizSubcontractRewardPunish>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(rewardPunishService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizSubcontractRewardPunish rewardPunish) {
        rewardPunishService.save(rewardPunish);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        rewardPunishService.delete(id);
        return R.ok();
    }
}
