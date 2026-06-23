package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineRepair;
import com.zwinsight.machine.service.MachineRepairService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机械维修接口
 */
@RestController
@RequestMapping("/api/v1/machine/repair")
@RequiredArgsConstructor
public class MachineRepairController {

    private final MachineRepairService repairService;

    @GetMapping
    public R<PageResult<BizMachineRepair>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) Long projectId) {
        return R.ok(repairService.page(page, size, machineId, projectId));
    }

    @PostMapping("/report")
    public R<Void> report(@RequestBody BizMachineRepair repair) {
        repairService.report(repair);
        return R.ok();
    }

    @PostMapping("/{id}/dispatch")
    public R<Void> dispatch(@PathVariable Long id, @RequestParam String repairPerson) {
        repairService.dispatch(id, repairPerson);
        return R.ok();
    }

    @PostMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id, @RequestBody BizMachineRepair update) {
        repairService.complete(id, update);
        return R.ok();
    }

    @GetMapping("/history/{machineId}")
    public R<List<BizMachineRepair>> getHistory(@PathVariable Long machineId) {
        return R.ok(repairService.getHistory(machineId));
    }
}
