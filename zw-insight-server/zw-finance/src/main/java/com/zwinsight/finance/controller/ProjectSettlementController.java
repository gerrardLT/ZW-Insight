package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.BizSettlementContractDetail;
import com.zwinsight.finance.domain.dto.ProjectSettlementUpdateDTO;
import com.zwinsight.finance.service.ProjectSettlementService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 项目最终结算接口
 */
@RestController
@RequestMapping("/api/v1/project-settlements")
@RequiredArgsConstructor
public class ProjectSettlementController {

    private final ProjectSettlementService settlementService;

    /**
     * 分页查询结算单列表
     */
    @GetMapping
    public R<PageResult<BizProjectSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(settlementService.page(page, size, projectId, status));
    }

    /**
     * 创建结算单（自动汇总数据）
     */
    @PostMapping
    public R<Long> create(@RequestParam Long projectId) {
        Long id = settlementService.createSettlement(projectId);
        return R.ok(id);
    }

    /**
     * 查询结算单详情
     */
    @GetMapping("/{id}")
    public R<BizProjectSettlement> detail(@PathVariable Long id) {
        return R.ok(settlementService.getById(id));
    }

    /**
     * 编辑结算单（仅 DRAFT/REJECTED 状态可编辑）
     * <p>
     * 驳回后允许财务人员修改后重新提交（R3.6）
     * </p>
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody ProjectSettlementUpdateDTO updateDTO) {
        settlementService.updateSettlement(id, updateDTO);
        return R.ok();
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
     * 导出结算报告 Excel
     */
    @PostMapping("/{id}/export")
    public void export(@PathVariable Long id, HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("settlement_" + id + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        settlementService.exportExcel(id, response);
    }

    /**
     * 查询未结清合同列表
     */
    @GetMapping("/{id}/unsettled-contracts")
    public R<List<BizSettlementContractDetail>> unsettledContracts(@PathVariable Long id) {
        BizProjectSettlement settlement = settlementService.getById(id);
        return R.ok(settlementService.getUnsettledContracts(settlement.getProjectId()));
    }
}
