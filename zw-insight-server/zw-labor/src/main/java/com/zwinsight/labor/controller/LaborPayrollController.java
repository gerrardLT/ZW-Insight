package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.service.LaborPayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 劳务工资单接口
 */
@RestController
@RequestMapping("/api/v1/labor/payroll")
@RequiredArgsConstructor
public class LaborPayrollController {

    private final LaborPayrollService payrollService;

    @GetMapping
    public R<PageResult<BizLaborPayroll>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long teamId) {
        return R.ok(payrollService.page(page, size, projectId, teamId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizLaborPayroll payroll) {
        payrollService.save(payroll);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        payrollService.submit(id);
        return R.ok();
    }
}
