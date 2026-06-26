package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineEntry;
import com.zwinsight.machine.service.MachineEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机械进退场接口
 */
@RestController
@RequestMapping("/api/v1/machine/entry")
@RequiredArgsConstructor
public class MachineEntryController {

    private final MachineEntryService entryService;

    @GetMapping("/page")
    public R<PageResult<BizMachineEntry>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) Long projectId) {
        return R.ok(entryService.page(page, size, machineId, projectId));
    }

    @PostMapping("/in")
    public R<Void> entryIn(@RequestBody BizMachineEntry entry) {
        entryService.entryIn(entry);
        return R.ok();
    }

    @PostMapping("/out")
    public R<Void> entryOut(@RequestBody BizMachineEntry entry) {
        entryService.entryOut(entry);
        return R.ok();
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMachineEntry entry) {
        entryService.entryIn(entry);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMachineEntry entry) {
        entry.setId(id);
        entryService.update(entry);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        entryService.delete(id);
        return R.ok();
    }

    @GetMapping("/machine/{machineId}")
    public R<List<BizMachineEntry>> getByMachine(@PathVariable Long machineId) {
        return R.ok(entryService.getByMachine(machineId));
    }
}
