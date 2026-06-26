package com.zwinsight.subcontract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.dto.SubcontractSettlementCreateRequest;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailVO;
import com.zwinsight.subcontract.service.SubcontractSettlementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分包结算接口
 * <p>
 * 提供分包结算单的 CRUD、提交审批及 Excel 导出功能。
 */
@RestController
@RequestMapping("/api/v1/subcontract/settlement")
@RequiredArgsConstructor
public class SubcontractSettlementController {

    private final SubcontractSettlementService settlementService;

    /**
     * 创建分包结算单（含明细行）
     */
    @PostMapping
    public R<Long> createSettlement(@Valid @RequestBody SubcontractSettlementCreateRequest request) {
        Long id = settlementService.createSettlement(request);
        return R.ok(id);
    }

    /**
     * 更新分包结算单（含明细行重新计算）
     */
    @PutMapping("/{id}")
    public R<Void> updateSettlement(@PathVariable Long id,
                                    @Valid @RequestBody SubcontractSettlementCreateRequest request) {
        settlementService.updateSettlement(id, request);
        return R.ok();
    }

    /**
     * 分页查询结算单列表
     */
    @GetMapping
    public R<PageResult<BizSubcontractSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(settlementService.page(page, size, projectId, contractId));
    }

    /**
     * 查询结算单详情（含明细行列表和合同信息）
     */
    @GetMapping("/{id}")
    public R<SubcontractSettlementDetailVO> getDetail(@PathVariable Long id) {
        return R.ok(settlementService.getDetailVO(id));
    }

    /**
     * 提交审批
     */
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        settlementService.submit(id);
        return R.ok();
    }

    /**
     * 删除结算单
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        settlementService.delete(id);
        return R.ok();
    }

    /**
     * 导出结算单 Excel（含汇总和明细两个 Sheet）
     */
    @GetMapping("/{id}/export")
    public void exportSettlement(@PathVariable Long id, HttpServletResponse response) {
        settlementService.exportSettlement(id, response);
    }
}
