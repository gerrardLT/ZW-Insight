package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.dto.InvoiceSummaryDTO;
import com.zwinsight.finance.service.InvoiceSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 发票汇总接口
 * <p>
 * 统一汇总已开票与已收票数据，支持按项目、日期范围筛选。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/finance/invoice-summary")
@RequiredArgsConstructor
public class InvoiceSummaryController {

    private final InvoiceSummaryService invoiceSummaryService;

    @GetMapping
    public R<List<InvoiceSummaryDTO>> summary(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(invoiceSummaryService.summary(projectId, startDate, endDate));
    }
}
