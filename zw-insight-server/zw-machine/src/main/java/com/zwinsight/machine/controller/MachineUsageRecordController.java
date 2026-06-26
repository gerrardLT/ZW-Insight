package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineUsageRecord;
import com.zwinsight.machine.service.MachineUsageRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机械使用记录接口
 */
@RestController
@RequestMapping("/api/v1/machine/usage-record")
@RequiredArgsConstructor
public class MachineUsageRecordController {

    private final MachineUsageRecordService usageRecordService;

    @GetMapping("/page")
    public R<PageResult<BizMachineUsageRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(usageRecordService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMachineUsageRecord record) {
        usageRecordService.save(record);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMachineUsageRecord record) {
        record.setId(id);
        usageRecordService.update(record);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        usageRecordService.delete(id);
        return R.ok();
    }
}
