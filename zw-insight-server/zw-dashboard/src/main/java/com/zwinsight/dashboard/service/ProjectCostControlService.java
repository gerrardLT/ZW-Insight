package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.dashboard.dto.ProjectCostControlDTO;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectWbsNode;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectWbsNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目成本控制看板服务（Project Cost 360）
 * <p>
 * 聚合 CBS 成本账户数据，计算 Baseline / Current / Commitment / Actual / Forecast / Variance
 * 六个维度的成本指标，支持按 WBS 和费用类别下钻分析。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCostControlService {

    private final BizCostAccountMapper costAccountMapper;
    private final BizProjectWbsNodeMapper wbsNodeMapper;
    private final BizProjectMapper projectMapper;

    /** 费用类别中文映射 */
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "MATERIAL", "材料费",
            "LABOR", "人工费",
            "MACHINE", "机械费",
            "SUBCONTRACT", "分包费",
            "INDIRECT", "间接费",
            "OTHER", "其他费用"
    );

    /**
     * UI §7.2「七类成本结构」的文档类别（固定展示顺序，与文档一致）。
     * <p>前四类可由 CBS {@code cost_category} <b>直接映射</b>；后三类（措施/管理/商务）
     * 在 CBS 中同属 INDIRECT/OTHER，需按子类名关键词细分（见 {@link #DOC_SUBCATEGORY_RULES}）。</p>
     */
    private static final List<String[]> DOC_CATEGORIES = List.of(
            new String[]{"MATERIAL", "材料"},
            new String[]{"SUBCONTRACT", "分包"},
            new String[]{"LABOR", "人工"},
            new String[]{"MACHINE", "机械"},
            new String[]{"MEASURE", "措施"},
            new String[]{"ADMIN", "管理"},
            new String[]{"BUSINESS", "商务"});

    /** cost_category → 文档类别（四类直接映射） */
    private static final Map<String, String> DOC_BY_CATEGORY = Map.of(
            "MATERIAL", "MATERIAL",
            "SUBCONTRACT", "SUBCONTRACT",
            "LABOR", "LABOR",
            "MACHINE", "MACHINE");

    /**
     * INDIRECT/OTHER 下按<b>子类名关键词</b>归入措施/管理/商务（有序，命中即停）。
     * <p>关键词取自资金科目体系（{@code biz_fund_category} 的 EXP-DIRECT-MEASURE 措施费、
     * EXP-INDIRECT-ENTERTAIN 招待费 / TRAVEL 差旅费 / VEHICLE 车辆费 / MEETING 会议费 /
     * OFFICE 办公费 / SALARY 管理人员工资 / AUDIT 审计费 / CONSULTING 咨询费 等）
     * 与演示子类字典的真实命名。<b>未命中的不猜类</b>，归入 OTHER 并标
     * {@code basis=UNCLASSIFIED}，由前端如实展示（不静默归入“管理”等任意类）。</p>
     */
    private static final List<Map.Entry<String, List<String>>> DOC_SUBCATEGORY_RULES = List.of(
            Map.entry("MEASURE", List.of("措施", "临设", "临时设施", "安全", "文明", "防护",
                    "脚手架", "检测", "试验", "排水", "围挡")),
            Map.entry("BUSINESS", List.of("招待", "差旅", "车辆", "会议", "办公", "商务",
                    "投标", "经营", "报价")),
            Map.entry("ADMIN", List.of("管理", "工资", "审计", "咨询", "专家", "房租",
                    "水电", "生活", "保险", "税费")));

    /**
     * 获取项目成本控制看板数据
     *
     * @param projectId 项目 ID
     * @return 成本控制 DTO
     */
    public ProjectCostControlDTO getCostControl(Long projectId) {
        ProjectCostControlDTO dto = new ProjectCostControlDTO();
        dto.setProjectId(projectId);

        // 1. 查询项目基本信息
        BizProject project = projectMapper.selectById(projectId);
        if (project != null) {
            dto.setProjectName(project.getProjectName());
        }

        // 2. 查询 WBS 节点数量
        Long wbsCount = wbsNodeMapper.selectCount(
                new LambdaQueryWrapper<BizProjectWbsNode>()
                        .eq(BizProjectWbsNode::getProjectId, projectId));
        dto.setWbsCount(wbsCount.intValue());

        // 3. 查询所有成本账户（排除已删除）
        List<BizCostAccount> accounts = costAccountMapper.selectList(
                new LambdaQueryWrapper<BizCostAccount>()
                        .eq(BizCostAccount::getProjectId, projectId)
                        .orderByAsc(BizCostAccount::getAccountCode));
        dto.setAccountCount(accounts.size());

        // 4. 查询 WBS 节点用于名称填充
        Map<Long, BizProjectWbsNode> wbsMap = new HashMap<>();
        if (!accounts.isEmpty()) {
            Set<Long> wbsIds = accounts.stream()
                    .map(BizCostAccount::getWbsNodeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!wbsIds.isEmpty()) {
                List<BizProjectWbsNode> wbsNodes = wbsNodeMapper.selectBatchIds(wbsIds);
                wbsMap = wbsNodes.stream()
                        .collect(Collectors.toMap(BizProjectWbsNode::getId, n -> n));
            }
        }

        // 5. 构建账户摘要列表
        List<ProjectCostControlDTO.CostAccountSummary> accountSummaries = new ArrayList<>();
        for (BizCostAccount account : accounts) {
            ProjectCostControlDTO.CostAccountSummary summary = buildAccountSummary(account, wbsMap);
            accountSummaries.add(summary);
        }
        dto.setAccounts(accountSummaries);

        // 6. 计算汇总指标
        dto.setTotals(calculateTotals(accounts));

        // 7. 按费用类别分组汇总
        dto.setCategorySummaries(calculateCategorySummaries(accounts));

        // 7.1 按 UI §7.2「七类成本结构」口径分组（与 CBS 六类并存，不互替）
        dto.setDocCategories(calculateDocCategories(accounts));

        // 8. 趋势数据（暂返回空列表，后续可从实际成本流水聚合）
        dto.setTrends(new ArrayList<>());

        log.debug("项目成本控制看板数据构建完成，projectId={}, accounts={}", projectId, accounts.size());
        return dto;
    }

    /**
     * 构建单个账户摘要
     */
    private ProjectCostControlDTO.CostAccountSummary buildAccountSummary(
            BizCostAccount account, Map<Long, BizProjectWbsNode> wbsMap) {
        
        ProjectCostControlDTO.CostAccountSummary summary = new ProjectCostControlDTO.CostAccountSummary();
        summary.setAccountId(account.getId());
        summary.setCode(account.getAccountCode());
        summary.setName(account.getAccountName());
        summary.setCostCategory(account.getCostCategory());
        summary.setCostSubcategory(account.getCostSubcategory());
        summary.setStatus(account.getStatus());

        // WBS 信息填充
        if (account.getWbsNodeId() != null) {
            BizProjectWbsNode wbs = wbsMap.get(account.getWbsNodeId());
            if (wbs != null) {
                summary.setWbsCode(wbs.getNodeCode());
                summary.setWbsName(wbs.getNodeName());
            }
        }

        // 金额字段
        BigDecimal baseline = nullToZero(account.getBaselineAmount());
        BigDecimal current = nullToZero(account.getCurrentAmount());
        BigDecimal commitment = nullToZero(account.getCommitmentAmount());
        BigDecimal actual = nullToZero(account.getActualAmount());
        BigDecimal forecast = nullToZero(account.getForecastAmount());

        summary.setBaseline(baseline);
        summary.setCurrent(current);
        summary.setCommitment(commitment);
        summary.setActual(actual);
        summary.setForecast(forecast);
        summary.setRemaining(current.subtract(actual));
        summary.setVariance(current.subtract(forecast));
        summary.setVarianceRate(calculateRateOrNull(current.subtract(forecast), current));
        summary.setUsageRate(calculateRate(actual, current));

        return summary;
    }

    /**
     * 计算汇总指标
     */
    private ProjectCostControlDTO.CostTotals calculateTotals(List<BizCostAccount> accounts) {
        ProjectCostControlDTO.CostTotals totals = new ProjectCostControlDTO.CostTotals();

        BigDecimal baselineTotal = BigDecimal.ZERO;
        BigDecimal currentTotal = BigDecimal.ZERO;
        BigDecimal commitmentTotal = BigDecimal.ZERO;
        BigDecimal actualTotal = BigDecimal.ZERO;
        BigDecimal forecastTotal = BigDecimal.ZERO;

        for (BizCostAccount account : accounts) {
            baselineTotal = baselineTotal.add(nullToZero(account.getBaselineAmount()));
            currentTotal = currentTotal.add(nullToZero(account.getCurrentAmount()));
            commitmentTotal = commitmentTotal.add(nullToZero(account.getCommitmentAmount()));
            actualTotal = actualTotal.add(nullToZero(account.getActualAmount()));
            forecastTotal = forecastTotal.add(nullToZero(account.getForecastAmount()));
        }

        totals.setBaselineTotal(baselineTotal);
        totals.setCurrentTotal(currentTotal);
        totals.setCommitmentTotal(commitmentTotal);
        totals.setActualTotal(actualTotal);
        totals.setForecastTotal(forecastTotal);
        totals.setRemainingBudget(currentTotal.subtract(actualTotal));
        totals.setVarianceAmount(currentTotal.subtract(forecastTotal));
        totals.setVarianceRate(calculateRate(totals.getVarianceAmount(), currentTotal));
        // UI §7.1「预计超支」：以目标成本（原始批准基准）为参照，正数 = 预计超出目标
        totals.setForecastOverrun(forecastTotal.subtract(baselineTotal));
        totals.setUsageRate(calculateRate(actualTotal, currentTotal));
        totals.setCommitmentRate(calculateRate(commitmentTotal, currentTotal));

        return totals;
    }

    /**
     * 按费用类别分组汇总
     */
    private List<ProjectCostControlDTO.CategorySummary> calculateCategorySummaries(List<BizCostAccount> accounts) {
        Map<String, List<BizCostAccount>> grouped = accounts.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCostCategory() != null ? a.getCostCategory() : "OTHER",
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<ProjectCostControlDTO.CategorySummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<BizCostAccount>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<BizCostAccount> categoryAccounts = entry.getValue();

            ProjectCostControlDTO.CategorySummary summary = new ProjectCostControlDTO.CategorySummary();
            summary.setCostCategory(category);
            summary.setCategoryName(CATEGORY_NAMES.getOrDefault(category, category));
            summary.setAccountCount(categoryAccounts.size());

            BigDecimal baseline = BigDecimal.ZERO;
            BigDecimal current = BigDecimal.ZERO;
            BigDecimal commitment = BigDecimal.ZERO;
            BigDecimal actual = BigDecimal.ZERO;
            BigDecimal forecast = BigDecimal.ZERO;

            for (BizCostAccount account : categoryAccounts) {
                baseline = baseline.add(nullToZero(account.getBaselineAmount()));
                current = current.add(nullToZero(account.getCurrentAmount()));
                commitment = commitment.add(nullToZero(account.getCommitmentAmount()));
                actual = actual.add(nullToZero(account.getActualAmount()));
                forecast = forecast.add(nullToZero(account.getForecastAmount()));
            }

            summary.setBaseline(baseline);
            summary.setCurrent(current);
            summary.setCommitment(commitment);
            summary.setActual(actual);
            summary.setForecast(forecast);
            summary.setVariance(current.subtract(forecast));
            summary.setVarianceRate(calculateRateOrNull(current.subtract(forecast), current));

            summaries.add(summary);
        }

        return summaries;
    }

    /**
     * 按 UI §7.2「七类成本结构」汇总（材料/分包/人工/机械/措施/管理/商务）。
     * <p>归类优先级：① cost_category 直接映射四类 → ② INDIRECT/OTHER 按子类名关键词
     * → ③ 都未命中则归 OTHER（basis=UNCLASSIFIED）。每类附 basis 使口径可追溯。</p>
     * <p><b>无账户的类别仍返回行</b>（金额全 0、riskLevel=INFO），使前端七类结构完整；
     * 这与“伪造数据”不同：金额来自真实账户汇总，零就是零。</p>
     */
    private List<ProjectCostControlDTO.DocCategorySummary> calculateDocCategories(
            List<BizCostAccount> accounts) {
        // 归类：文档类别码 → 账户列表
        Map<String, List<BizCostAccount>> grouped = new LinkedHashMap<>();
        List<BizCostAccount> unclassified = new ArrayList<>();
        for (BizCostAccount account : accounts) {
            String docCode = resolveDocCategory(account);
            if (docCode == null) {
                unclassified.add(account);
            } else {
                grouped.computeIfAbsent(docCode, k -> new ArrayList<>()).add(account);
            }
        }

        List<ProjectCostControlDTO.DocCategorySummary> result = new ArrayList<>();
        for (String[] def : DOC_CATEGORIES) {
            String code = def[0];
            boolean direct = DOC_BY_CATEGORY.containsValue(code);
            List<BizCostAccount> list = grouped.getOrDefault(code, List.of());
            result.add(buildDocSummary(code, def[1],
                    direct ? "CBS_CATEGORY" : "CBS_SUBCATEGORY_KEYWORD", list));
        }
        // 未能归类的账户单列（不静默并入任何一类，否则金额去向不可追溯）
        result.add(buildDocSummary("OTHER", "未归类", "UNCLASSIFIED", unclassified));
        return result;
    }

    private ProjectCostControlDTO.DocCategorySummary buildDocSummary(
            String code, String name, String basis, List<BizCostAccount> list) {
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal forecast = BigDecimal.ZERO;
        for (BizCostAccount a : list) {
            current = current.add(nullToZero(a.getCurrentAmount()));
            actual = actual.add(nullToZero(a.getActualAmount()));
            forecast = forecast.add(nullToZero(a.getForecastAmount()));
        }
        BigDecimal variance = current.subtract(forecast);

        ProjectCostControlDTO.DocCategorySummary s = new ProjectCostControlDTO.DocCategorySummary();
        s.setCode(code);
        s.setName(name);
        s.setBasis(basis);
        s.setCurrent(current);
        s.setActual(actual);
        s.setForecast(forecast);
        s.setVariance(variance);
        s.setVarianceRate(calculateRateOrNull(variance, current));
        s.setRiskLevel(docRiskLevel(current, variance));
        s.setAccountCount(list.size());
        return s;
    }

    /**
     * 文档类别风险等级（UI §7.2 逐类标注 🔴/🟡）。
     * <p>预计超支率 = −variance / current（variance 为负即超支）：
     * &gt;10% → RED；&gt;0 → YELLOW；≤ 0 → GREEN。无预算基准（current=0）→ INFO，
     * <b>不得当作“未超支”给绿灯</b>。</p>
     */
    private String docRiskLevel(BigDecimal current, BigDecimal variance) {
        if (current == null || current.compareTo(BigDecimal.ZERO) == 0) {
            return "INFO";
        }
        if (variance.signum() >= 0) {
            return "GREEN";
        }
        BigDecimal overrunRate = variance.negate()
                .divide(current, 4, RoundingMode.HALF_UP);
        return overrunRate.compareTo(new BigDecimal("0.10")) > 0 ? "RED" : "YELLOW";
    }

    /**
     * 将 CBS 账户归入文档七类；无法归类返回 null。
     */
    private String resolveDocCategory(BizCostAccount account) {
        String category = account.getCostCategory();
        if (category != null && DOC_BY_CATEGORY.containsKey(category)) {
            return DOC_BY_CATEGORY.get(category);
        }
        // INDIRECT / OTHER / 空类别：按子类名关键词细分（兼看账户名，子类未填时降级使用）
        String text = account.getCostSubcategory();
        if (text == null || text.isBlank()) {
            text = account.getAccountName();
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        for (Map.Entry<String, List<String>> rule : DOC_SUBCATEGORY_RULES) {
            for (String keyword : rule.getValue()) {
                if (text.contains(keyword)) {
                    return rule.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 计算比率（百分比，保留 2 位小数）
     */
    private BigDecimal calculateRate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0 || numerator == null) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算比率，<b>无分母基准时返回 null</b>（UI §7.2 偏差率列）。
     * <p>与 {@link #calculateRate} 的区别：后者分母为 0 时返回 0，会把“无预算基准”
     * 展示成“偏差 0%”（把无法计算伪装成无偏差）。本方法用于新增的偏差率字段，
     * 前端据 null 显示“—”；不修改 {@link #calculateRate} 以免改变存量使用方行为。</p>
     */
    private BigDecimal calculateRateOrNull(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0 || numerator == null) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * NULL 转 ZERO
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
