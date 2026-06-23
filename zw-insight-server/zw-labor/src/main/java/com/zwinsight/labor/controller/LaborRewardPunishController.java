package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizLaborRewardPunish;
import com.zwinsight.labor.service.LaborRewardPunishService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 劳务奖罚接口
 */
@RestController
@RequestMapping("/api/v1/labor/reward-punish")
@RequiredArgsConstructor
public class LaborRewardPunishController {

    private final LaborRewardPunishService rewardPunishService;

    @GetMapping
    public R<PageResult<BizLaborRewardPunish>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(rewardPunishService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizLaborRewardPunish rewardPunish) {
        rewardPunishService.save(rewardPunish);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        rewardPunishService.delete(id);
        return R.ok();
    }
}
