package com.zwinsight.machine.controller;

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

    @GetMapping("/machine/{machineId}")
    public R<List<BizMachineEntry>> getByMachine(@PathVariable Long machineId) {
        return R.ok(entryService.getByMachine(machineId));
    }
}
