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

            summaries.add(summary);
        }

        return summaries;
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
     * NULL 转 ZERO
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
