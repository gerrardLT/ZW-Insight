package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.ReimbursementDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报销费用分析接口（V2026_60）
 * <p>对标 docs/资金流转流程.md §6.3 招待费分析维度与 §11 招待费预警项。
 * 呈现原则（驾驶舱 V1 §8.1）：默认只返回异常项，正常报销不罗列。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/reimbursement")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class ReimbursementAnalysisController {

    private final ReimbursementDetailService reimbursementDetailService;

    /**
     * 招待费分析：总额/次数/单次最高/人均金额 + 责任人累计排行 + 异常项清单。
     *
     * @param projectId  项目ID（可选，空=全部项目）
     * @param sourceType 报销来源（可选 PROJECT/PERSONAL，空=合计）
     */
    @GetMapping("/entertainment-analysis")
    public R<Map<String, Object>> entertainmentAnalysis(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String sourceType) {
        return R.ok(reimbursementDetailService.analyzeEntertainment(projectId, sourceType));
    }
}
