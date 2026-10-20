package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.FinanceWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 财务待办首屏聚合（S3.1）：待审批、已批未付、逾期未付、应收逾期与 TOP5。
 */
@RestController
@RequestMapping("/api/v1/finance/workbench")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class FinanceWorkbenchController {

    private final FinanceWorkbenchService financeWorkbenchService;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(financeWorkbenchService.summary());
    }
}
