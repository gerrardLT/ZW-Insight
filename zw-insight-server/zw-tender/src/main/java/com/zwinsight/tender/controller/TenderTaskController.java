package com.zwinsight.tender.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.tender.domain.BizTenderTask;
import com.zwinsight.tender.service.TenderTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投标任务接口
 */
@RestController
@RequestMapping("/api/v1/tender/task")
@RequiredArgsConstructor
public class TenderTaskController {

    private final TenderTaskService taskService;

    @GetMapping("/{registerId}")
    public R<List<BizTenderTask>> list(@PathVariable Long registerId) {
        return R.ok(taskService.list(registerId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizTenderTask task) {
        taskService.save(task);
        return R.ok();
    }

    @PostMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        taskService.complete(id);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizTenderTask task) {
        task.setId(id);
        taskService.update(task);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return R.ok();
    }
}
