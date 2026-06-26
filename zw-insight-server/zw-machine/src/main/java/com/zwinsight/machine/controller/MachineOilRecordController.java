package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineOilRecord;
import com.zwinsight.machine.service.MachineOilRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机械加油记录接口
 */
@RestController
@RequestMapping("/api/v1/machine/oil-record")
@RequiredArgsConstructor
public class MachineOilRecordController {

    private final MachineOilRecordService oilRecordService;

    @GetMapping("/page")
    public R<PageResult<BizMachineOilRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) Long projectId) {
        return R.ok(oilRecordService.page(page, size, machineId, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMachineOilRecord record) {
        oilRecordService.save(record);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        oilRecordService.delete(id);
        return R.ok();
    }
}
