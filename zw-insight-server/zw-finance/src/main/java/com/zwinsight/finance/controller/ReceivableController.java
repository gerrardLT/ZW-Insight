package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.service.ReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应收台账接口（V2026_57）
 * <p>只读查询：台账分页 + 账龄分析（回款风险下钻数据源，驾驶舱资金中心消费）。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/receivable")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class ReceivableController {

    private final ReceivableService receivableService;

    /**
     * 分页查询应收台账（按到期日升序，最紧急在前）
     */
    @GetMapping("/page")
    public R<PageResult<BizReceivable>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(receivableService.page(page, size, projectId, status));
    }

    /**
     * 应收账龄分析（按项目 × 账龄桶汇总 OPEN 余额，项目按逾期余额降序）
     */
    @GetMapping("/aging")
    public R<Map<String, Object>> aging(@RequestParam(required = false) Long projectId) {
        return R.ok(receivableService.aging(projectId));
    }
}
