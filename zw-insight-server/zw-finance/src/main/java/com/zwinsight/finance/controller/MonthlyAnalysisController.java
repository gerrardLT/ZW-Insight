package com.zwinsight.finance.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.MonthlyAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 月度经营分析表接口（V2026_67，资金流转流程 §9 / 驾驶舱 §12「项目管理层最终只看一张表」）
 * <p>10 类费用 × 6 列矩阵：预算 / 本月发生 / 累计发生 / 累计支付 / 应付未付 / 预计最终。
 * 数据由 {@code MonthlyAnalysisTask}（每月 1 日 04:00 生成上月）落库，本接口只读查询与手工补生成。</p>
 * <p><b>口径提示（响应体的 notes 会随行下发，前端必须展示）</b>：
 * 「本月发生」为两个时点快照之差，首次生成无上月行时为 null；
 * 「累计支付/应付未付」仅直接费四类有合同权威来源，间接费为 null 而非 0。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/monthly-analysis")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class MonthlyAnalysisController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyAnalysisService monthlyAnalysisService;

    /**
     * 查询某项目某月的经营分析表。
     *
     * @param projectId 项目ID（必填，本表按项目粒度）
     * @param month     月份 yyyy-MM；不传则取该项目已生成的最新月份，无数据时回退当月
     */
    @GetMapping
    public R<Map<String, Object>> query(@RequestParam Long projectId,
                                        @RequestParam(required = false) String month) {
        String targetMonth = month;
        if (targetMonth == null || targetMonth.isBlank()) {
            String latest = monthlyAnalysisService.latestMonth(projectId);
            targetMonth = latest != null ? latest : YearMonth.now().format(MONTH_FMT);
        }
        return R.ok(monthlyAnalysisService.query(projectId, targetMonth));
    }

    /**
     * §9 十类的中文名与固定行序（前端表头与 CSV 导出共用，避免两处硬编码漂移）。
     */
    @GetMapping("/categories")
    public R<Map<String, String>> categories() {
        return R.ok(monthlyAnalysisService.categoryNames());
    }

    /**
     * 手工生成（或重跑覆盖）某项目某月的分析表。
     * <p>用途：定时任务未跑、新项目补建 CBS 后回填、或口径调整后重算。
     * 同月重跑为覆盖更新（唯一键 upsert），不会重复插行。</p>
     */
    @PostMapping("/generate")
    @OperLog(module = "月度经营分析", operType = "INSERT", description = "生成月度经营分析表")
    public R<Map<String, Object>> generate(@RequestParam Long projectId,
                                           @RequestParam String month) {
        return R.ok(monthlyAnalysisService.generate(projectId, month));
    }

    /**
     * 手工生成全部已建 CBS 账户项目的某月分析表（运维批量补跑用）。
     * <p>单项目失败不中断其余项目，失败明细随响应返回（不静默）。</p>
     */
    @PostMapping("/generate-all")
    @OperLog(module = "月度经营分析", operType = "INSERT", description = "批量生成月度经营分析表")
    public R<Map<String, Object>> generateAll(@RequestParam String month) {
        return R.ok(monthlyAnalysisService.generateAll(month));
    }
}
