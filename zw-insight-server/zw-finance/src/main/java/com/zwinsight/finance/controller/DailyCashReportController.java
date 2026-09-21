package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizBankFlow;
import com.zwinsight.finance.domain.BizDailyCashReport;
import com.zwinsight.finance.service.DailyCashReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 资金日报接口（每日头寸，老板视角）
 */
@RestController
@RequestMapping("/api/v1/finance/daily-cash-report")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class DailyCashReportController {

    private final DailyCashReportService dailyCashReportService;

    /**
     * 生成/刷新某日日报（幂等覆盖）
     */
    @PostMapping("/generate")
    public R<BizDailyCashReport> generate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) BigDecimal largeThreshold) {
        return R.ok(dailyCashReportService.generate(reportDate, largeThreshold));
    }

    /**
     * 查询某日日报（不传日期返回最近一天）
     */
    @GetMapping
    public R<BizDailyCashReport> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return R.ok(dailyCashReportService.getReport(reportDate));
    }

    /**
     * 近 N 日日报趋势
     */
    @GetMapping("/trend")
    public R<List<BizDailyCashReport>> trend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dailyCashReportService.trend(days));
    }

    /**
     * 大额支出明细（当日超门槛的支出流水，供日报下钻）
     */
    @GetMapping("/large-outflows")
    public R<List<BizBankFlow>> largeOutflows(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) BigDecimal threshold) {
        return R.ok(dailyCashReportService.largeOutflows(reportDate, threshold));
    }
}
