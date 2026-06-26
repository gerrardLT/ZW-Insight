package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.dto.*;
import com.zwinsight.machine.service.MachineWorkSettlementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机械工作量结算接口
 */
@RestController
@RequestMapping("/api/v1/machine/settlement")
@RequiredArgsConstructor
public class MachineSettlementController {

    private final MachineWorkSettlementService machineWorkSettlementService;

    /**
     * 创建结算单
     */
    @PostMapping
    public R<MachineSettlementCreateResult> createSettlement(@Valid @RequestBody MachineSettlementCreateRequest request) {
        MachineSettlementCreateResult result = machineWorkSettlementService.createSettlement(request);
        return R.ok(result);
    }

    /**
     * 提交审批
     */
    @PostMapping("/{id}/submit")
    public R<Void> submitForApproval(@PathVariable Long id) {
        machineWorkSettlementService.submitForApproval(id);
        return R.ok();
    }

    /**
     * 结算单分页列表
     */
    @GetMapping
    public R<PageResult<MachineSettlementVO>> page(MachineSettlementQuery query) {
        return R.ok(machineWorkSettlementService.page(query));
    }

    /**
     * 结算单详情
     */
    @GetMapping("/{id}")
    public R<MachineSettlementVO> getDetail(@PathVariable Long id) {
        return R.ok(machineWorkSettlementService.getDetail(id));
    }

    /**
     * 项目费用总览
     */
    @GetMapping("/summary")
    public R<MachineSettlementSummaryVO> getProjectSummary(@RequestParam Long projectId) {
        return R.ok(machineWorkSettlementService.getProjectSummary(projectId));
    }

    /**
     * 导出结算单 Excel
     */
    @GetMapping("/{id}/export")
    public void exportSettlement(@PathVariable Long id, HttpServletResponse response) {
        machineWorkSettlementService.exportSettlement(id, response);
    }
}
