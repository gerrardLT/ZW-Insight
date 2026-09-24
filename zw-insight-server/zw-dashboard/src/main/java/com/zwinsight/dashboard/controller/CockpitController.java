package com.zwinsight.dashboard.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.dashboard.domain.BizProfitSnapshot;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.dto.RiskHandleRequest;
import com.zwinsight.dashboard.service.CockpitService;
import com.zwinsight.dashboard.service.ProfitSnapshotService;
import com.zwinsight.dashboard.service.RiskScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工程老板经营驾驶舱接口（V2026_59）
 * <p>
 * 对标 docs/工程老板经营驾驶舱_UI原型结构_V1.md：经营总览 8 卡、利润趋势与归因、
 * 项目健康度、风险中心（老板待处理事项）。所有指标来自真实单据/台账/快照聚合。
 * </p>
 * <p>口径提示：forecastProfit（预计利润 = 合同收入 − CBS 完工预测总成本）与
 * realizedProfit（已实现收支差）为两套口径，字段名区分，禁止混用。</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard/cockpit")
@RequiredArgsConstructor
@RequiresPermission("dashboard:view")
public class CockpitController {

    private final CockpitService cockpitService;
    private final ProfitSnapshotService profitSnapshotService;
    private final RiskScanService riskScanService;

    /**
     * 经营总览 8 卡（§4）：合同收入/预计总成本/预计利润/预计利润率
     * + 累计回款/累计支付/应收未收/90天资金缺口，另附账户资金与已实现利润。
     * <p>UI §14 全局筛选：{@code ownerCompanyId}（所属公司）/ {@code projectId}（项目）。
     * 有筛选时资金缺口读项目级快照且<b>不含账户余额</b>（无法拆分），
     * 响应体 {@code gapBasis} 标明口径，前端必须展示该提示。</p>
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> getOverview(
            @RequestParam(required = false) Long ownerCompanyId,
            @RequestParam(required = false) Long projectId) {
        return R.ok(cockpitService.getOverview(ownerCompanyId, projectId));
    }

    /**
     * 全局筛选器可选项（UI §14）：所属公司 / 项目 / 6 个快捷筛选。
     * <p>同时返回 {@code unsupportedDimensions}：区域与项目经理<b>无数据源</b>，
     * 前端据此置灰并说明原因，不得做成选了不生效的假下拉。</p>
     */
    @GetMapping("/filter-options")
    public R<Map<String, Object>> getFilterOptions() {
        return R.ok(cockpitService.getFilterOptions());
    }

    /**
     * 利润趋势（§5.1）：预计利润快照曲线 + 已实现收支月度曲线。
     *
     * @param projectId 项目ID（不传为公司级）
     * @param months    快照月数（默认 6，范围 1-36）
     * @param year      已实现收支年份（默认当年）
     */
    @GetMapping("/profit-trend")
    public R<Map<String, Object>> getProfitTrend(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) Integer year) {
        return R.ok(cockpitService.getProfitTrend(projectId, months, year));
    }

    /**
     * 利润变化归因（§5.1 点击月份展开）：本期 vs 上期快照按成本类别分解差额。
     *
     * @param month 目标月份 yyyy-MM
     */
    @GetMapping("/profit-attribution")
    public R<Map<String, Object>> getProfitAttribution(
            @RequestParam String month,
            @RequestParam(required = false) Long projectId) {
        return R.ok(profitSnapshotService.attributeProfitChange(projectId, month));
    }

    /**
     * 项目经营健康度（§5.2）：规则自动定级 🟢🟡🔴 + 四级排序，异常项目排最前。
     *
     * @param quickFilter    快捷筛选（ALL/HIGH_RISK/LOSS/FUND_TIGHT/PROFIT_DOWN/MONTH_ABNORMAL）；
     *                       非法值报 400，不静默当作“全部”
     * @param ownerCompanyId 所属公司筛选（UI §14）
     * @param projectId      项目筛选（UI §14）
     */
    @GetMapping("/project-health")
    public R<List<Map<String, Object>>> getProjectHealth(
            @RequestParam(required = false) String quickFilter,
            @RequestParam(required = false) Long ownerCompanyId,
            @RequestParam(required = false) Long projectId) {
        return R.ok(cockpitService.getProjectHealth(quickFilter, ownerCompanyId, projectId));
    }

    /**
     * 资金比率（资金流转 §10.2 支付率、§10.3 合同执行率）
     * <p>支付率同时返回审批口径（paymentRate，paidBasis=APPROVAL_WRITEBACK）
     * 与现金口径（cashPaymentRate，银行勾稽合计），前端必须标明用的哪个口径。</p>
     */
    @GetMapping("/fund-ratios")
    public R<Map<String, Object>> getFundRatios() {
        return R.ok(cockpitService.getFundRatios());
    }

    /**
     * 项目预计利润明细（单项目详情/成本中心数据源，实时计算不依赖快照）
     */
    @GetMapping("/project-forecasts")
    public R<List<Map<String, Object>>> listProjectForecasts() {
        return R.ok(profitSnapshotService.listProjectForecasts());
    }

    /**
     * 预计利润快照列表（按月升序，供趋势表与审计追溯）
     */
    @GetMapping("/profit-snapshots")
    public R<List<BizProfitSnapshot>> listSnapshots(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "12") int months) {
        return R.ok(profitSnapshotService.getTrend(projectId, months));
    }

    // ==================== 风险中心（§11-12） ====================

    /**
     * 风险分级汇总（数量 + 影响金额，仅统计未关闭风险）
     */
    @GetMapping("/risk/summary")
    public R<Map<String, Object>> getRiskSummary(@RequestParam(required = false) Long projectId) {
        return R.ok(riskScanService.summary(projectId));
    }

    /**
     * 风险台账分页（严重级别降序 → 影响金额降序，对齐"TOP风险"呈现）
     */
    @GetMapping("/risk/page")
    public R<PageResult<BizRiskRegister>> pageRisks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) Long projectId) {
        return R.ok(riskScanService.page(page, size, severity, handleStatus, riskType, projectId));
    }

    /**
     * 风险详情（六要素完整体；移动端详情页与穿透入口使用）
     * <p>路由说明：本方法为 `/risk/{id}`，与 `/risk/summary`、`/risk/page` 共存时
     * Spring 优先匹配字面量路径，不会被误当成 id="summary"。</p>
     */
    @GetMapping("/risk/{id}")
    public R<BizRiskRegister> getRisk(@PathVariable Long id) {
        return R.ok(riskScanService.getById(id));
    }

    /**
     * 风险处理流转（认领 PROCESSING / 解决 RESOLVED / 忽略 IGNORED / 重开 OPEN）
     * <p>处理人取当前登录用户并强制留痕（六要素之"当前处理状态"可审计）。</p>
     * <p>参数走请求体而非 query：移动端 uni.request 对 PUT 会把 data 放 body，
     * 用 {@code @RequestParam} 会取不到；亦避免一致性审计将 query 误判为路径。</p>
     */
    @PutMapping("/risk/{id}/handle")
    @OperLog(module = "风险中心", operType = "UPDATE", description = "风险处理流转")
    public R<Void> handleRisk(@PathVariable Long id, @RequestBody RiskHandleRequest request) {
        if (request == null || request.getAction() == null || request.getAction().isBlank()) {
            return R.fail(400, "处理动作 action 不能为空");
        }
        riskScanService.handle(id, request.getAction(), SecurityContextHolder.getUserId(),
                request.getHandleNote());
        return R.ok();
    }

    /**
     * 手动触发风险扫描（定时任务 RiskScanTask 每日 02:00 自动执行；
     * 本端点供首屏初始化与运维复核，返回扫描统计不静默）。
     * <p>权限：沿用类级 dashboard:view（已登记于 47_V2026_45_2__permission_guard_catalog.sql）。
     * 不另标 dashboard:manage —— 该标识未在权限目录登记，标了会造成“无法通过角色配置授予”
     * 的伪权限（仅 SUPER_ADMIN 靠豁免能调）。如需收紧，先在该权限目录脚本登记后再启用。</p>
     */
    @PutMapping("/risk/scan")
    @OperLog(module = "风险中心", operType = "UPDATE", description = "手动触发风险扫描")
    public R<Map<String, Object>> scanRisks() {
        return R.ok(riskScanService.scan());
    }
}
