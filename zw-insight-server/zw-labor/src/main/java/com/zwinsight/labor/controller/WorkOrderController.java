package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 派工单接口
 */
@RestController
@RequestMapping("/api/v1/labor/work-order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @GetMapping
    @GetMapping("/page")
    public R<PageResult<BizWorkOrder>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String status) {
        return R.ok(workOrderService.page(page, size, projectId, teamId, status));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizWorkOrder workOrder) {
        workOrderService.save(workOrder);
        return R.ok();
    }

    @PostMapping("/batch")
    public R<Void> batchSave(@RequestBody List<BizWorkOrder> workOrders) {
        workOrderService.batchSave(workOrders);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizWorkOrder workOrder) {
        workOrder.setId(id);
        workOrderService.update(workOrder);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        workOrderService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        workOrderService.submit(id);
        return R.ok();
    }
}
