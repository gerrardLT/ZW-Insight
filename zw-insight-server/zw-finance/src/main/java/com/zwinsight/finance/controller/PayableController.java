package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizPayable;
import com.zwinsight.finance.service.PayableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应付账款台账接口（V2026_115 只读查询）
 * <p>生成/核销/冲减均由业务事件与付款审批链在事务内驱动，本控制器不做写入口——
 * 与应收台账的「无手工补录」同纪律：台账行必须有单据事实支撑（R7 审计约束）。</p>
 * <p>权限：沿用类级 {@code finance:view}（对称 ReceivableController 的权限目录约束说明）。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/payable")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class PayableController {

    private final PayableService payableService;

    /**
     * 分页查询应付台账（按到期日升序，最紧急在前）
     */
    @GetMapping("/page")
    public R<PageResult<BizPayable>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractCategory,
            @RequestParam(required = false) Long contractId) {
        return R.ok(payableService.page(page, size, projectId, status, contractCategory, contractId));
    }

    /**
     * 应付账龄分析（按项目 × 账龄桶汇总 OPEN 余额，项目按逾期余额降序）
     */
    @GetMapping("/aging")
    public R<Map<String, Object>> aging(@RequestParam(required = false) Long projectId) {
        return R.ok(payableService.aging(projectId));
    }
}
