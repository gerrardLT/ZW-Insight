package com.zwinsight.budget.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetChange;
import com.zwinsight.budget.domain.BizBudgetConfig;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountLink;
import com.zwinsight.budget.domain.BizCostAccountTxn;
import com.zwinsight.budget.domain.SysBudgetControlConfig;
import com.zwinsight.budget.mapper.BizBudgetChangeMapper;
import com.zwinsight.budget.mapper.BizBudgetConfigMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.budget.mapper.BizCostAccountLinkMapper;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.budget.mapper.BizCostAccountTxnMapper;
import com.zwinsight.budget.mapper.SysBudgetControlConfigMapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-budget 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 7 张表：
 * 预算主表/变更/配置、成本账户三件套（cost-control-backbone）、预算控制配置。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p><b>不纳入级联</b>：biz_budget_detail / biz_budget_change_detail 以
 * {@code budget_id}/{@code change_id} 关联，biz_cost_subcategory 为全局字典，
 * 三者均无 {@code project_id} 列；主单逻辑删除后明细随之失效。</p>
 *
 * <p><b>sys_budget_control_config 特别说明</b>：该表 {@code project_id} 为 NULL 时
 * 表示「系统默认规则」（见 01_p0_core_features.sql 表注释），且有
 * {@code uk_tenant_project} 唯一键。本监听器用 {@code eq("project_id", id)} 匹配，
 * SQL 三值逻辑下 NULL 行永不相等，故系统默认规则不会被误删。</p>
 *
 * <p><b>biz_cost_account_txn 特别说明</b>：该表是成本账户流水账，逻辑删除后
 * 账户余额的历史可追溯性随之关闭——这是项目整体作废的预期结果，与主表同一语义。
 * 正常业务中的单据撤回冲销由各单据自己的回滚路径负责，职责不重叠。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——预算 26+1 条、
 * sys_budget_control_config 234 条（全库最高，因每个项目都会落一行控制配置）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetProjectCascadeCleanupListener {

    private final BizBudgetMapper budgetMapper;
    private final BizBudgetChangeMapper budgetChangeMapper;
    private final BizBudgetConfigMapper budgetConfigMapper;
    private final BizCostAccountMapper costAccountMapper;
    private final BizCostAccountLinkMapper costAccountLinkMapper;
    private final BizCostAccountTxnMapper costAccountTxnMapper;
    private final SysBudgetControlConfigMapper budgetControlConfigMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int budgets = budgetMapper.delete(
                new QueryWrapper<BizBudget>().eq("project_id", projectId));
        int budgetChanges = budgetChangeMapper.delete(
                new QueryWrapper<BizBudgetChange>().eq("project_id", projectId));
        int budgetConfigs = budgetConfigMapper.delete(
                new QueryWrapper<BizBudgetConfig>().eq("project_id", projectId));
        int costAccounts = costAccountMapper.delete(
                new QueryWrapper<BizCostAccount>().eq("project_id", projectId));
        int costAccountLinks = costAccountLinkMapper.delete(
                new QueryWrapper<BizCostAccountLink>().eq("project_id", projectId));
        int costAccountTxns = costAccountTxnMapper.delete(
                new QueryWrapper<BizCostAccountTxn>().eq("project_id", projectId));
        int controlConfigs = budgetControlConfigMapper.delete(
                new QueryWrapper<SysBudgetControlConfig>().eq("project_id", projectId));

        log.info("项目删除级联清理[budget]完成, projectId={}, 预算={} 预算变更={} 预算配置={} "
                        + "成本账户={} 账户关联={} 账户流水={} 控制配置={}",
                projectId, budgets, budgetChanges, budgetConfigs, costAccounts,
                costAccountLinks, costAccountTxns, controlConfigs);
    }
}
