package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetConfig;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetConfigMapper;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算管控服务
 */
@Service
@RequiredArgsConstructor
public class BudgetControlService {

    private final BizBudgetMapper budgetMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BizBudgetConfigMapper budgetConfigMapper;

    /**
     * 获取剩余预算
     *
     * @param projectId    项目ID
     * @param costCategory 费用类别
     * @return 剩余预算金额
     */
    public BigDecimal getRemainingBudget(Long projectId, String costCategory) {
        // 获取该项目所有已审批预算中该类别的预算总额
        LambdaQueryWrapper<BizBudget> budgetWrapper = new LambdaQueryWrapper<>();
        budgetWrapper.eq(BizBudget::getProjectId, projectId)
                .eq(BizBudget::getStatus, "APPROVED");
        List<BizBudget> approvedBudgets = budgetMapper.selectList(budgetWrapper);

        BigDecimal totalBudget = BigDecimal.ZERO;
        for (BizBudget budget : approvedBudgets) {
            LambdaQueryWrapper<BizBudgetDetail> detailWrapper = new LambdaQueryWrapper<>();
            detailWrapper.eq(BizBudgetDetail::getBudgetId, budget.getId())
                    .eq(BizBudgetDetail::getCostCategory, costCategory);
            List<BizBudgetDetail> details = budgetDetailMapper.selectList(detailWrapper);
            for (BizBudgetDetail detail : details) {
                totalBudget = totalBudget.add(
                        detail.getBudgetTotalPrice() != null ? detail.getBudgetTotalPrice() : BigDecimal.ZERO);
            }
        }

        // TODO: 扣除已发生的费用（可从采购合同等模块获取）
        // 当前简化处理：剩余预算 = 预算总额
        return totalBudget;
    }

    /**
     * 预算校验
     *
     * @param projectId    项目ID
     * @param costCategory 费用类别
     * @param amount       本次金额
     * @return 是否允许（true-允许, false-不允许）
     */
    public boolean checkBudget(Long projectId, String costCategory, BigDecimal amount) {
        BigDecimal remaining = getRemainingBudget(projectId, costCategory);

        // 如果剩余预算足够，直接允许
        if (remaining.compareTo(amount) >= 0) {
            return true;
        }

        // 超预算，查看管控模式
        BizBudgetConfig config = getConfig(projectId);
        String controlMode = (config != null) ? config.getControlMode() : "WARN";

        if ("FORBID".equals(controlMode)) {
            throw new BusinessException("预算不足，当前剩余预算：" + remaining + "，本次申请：" + amount);
        }

        // WARN模式：允许但返回false表示超预算
        return false;
    }

    /**
     * 获取管控配置（优先项目级，其次全局）
     */
    private BizBudgetConfig getConfig(Long projectId) {
        // 先查项目级配置
        LambdaQueryWrapper<BizBudgetConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudgetConfig::getProjectId, projectId);
        BizBudgetConfig config = budgetConfigMapper.selectOne(wrapper);
        if (config != null) {
            return config;
        }

        // 再查全局配置（projectId为null）
        LambdaQueryWrapper<BizBudgetConfig> globalWrapper = new LambdaQueryWrapper<>();
        globalWrapper.isNull(BizBudgetConfig::getProjectId);
        return budgetConfigMapper.selectOne(globalWrapper);
    }
}
