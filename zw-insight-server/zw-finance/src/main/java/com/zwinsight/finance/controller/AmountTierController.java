package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.SysAmountTierConfig;
import com.zwinsight.finance.service.AmountTierService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 金额分级审批配置接口
 * <p>档位匹配结果作为 Flowable 流程变量 approvalTier 驱动审批链路由。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/amount-tier")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class AmountTierController {

    private final AmountTierService amountTierService;

    /**
     * 查询模块档位配置（按等级升序）
     */
    @GetMapping("/list")
    public R<List<SysAmountTierConfig>> listByModule(@RequestParam String module) {
        return R.ok(amountTierService.listByModule(module));
    }

    /**
     * 匹配档位（前端提交前预览：该金额会走哪个审批档）
     */
    @GetMapping("/match")
    public R<SysAmountTierConfig> matchTier(@RequestParam String module, @RequestParam BigDecimal amount) {
        return R.ok(amountTierService.matchTier(module, amount));
    }

    /**
     * 新增档位（模块内等级唯一）
     */
    @PostMapping
    public R<Void> save(@RequestBody SysAmountTierConfig config) {
        amountTierService.save(config);
        return R.ok();
    }

    /**
     * 修改档位（等级与模块不可改；maxAmount 为 null 表示无上限）
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysAmountTierConfig config) {
        config.setId(id);
        amountTierService.update(config);
        return R.ok();
    }
}
