package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算变更服务
 */
@Service
@RequiredArgsConstructor
public class BudgetChangeService {

    private final BizBudgetMapper budgetMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询变更预算
     */
    public PageResult<BizBudget> page(int page, int size, Long projectId) {
        Page<BizBudget> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizBudget::getProjectId, projectId)
                .eq(BizBudget::getBudgetType, "CHANGE")
                .orderByDesc(BizBudget::getChangeSeq);
        Page<BizBudget> result = budgetMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存变更预算（changeSeq自增）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizBudget budget) {
        budget.setBudgetType("CHANGE");
        budget.setStatus("DRAFT");

        // 获取当前最大变更序号
        LambdaQueryWrapper<BizBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudget::getProjectId, budget.getProjectId())
                .eq(BizBudget::getBudgetType, "CHANGE")
                .orderByDesc(BizBudget::getChangeSeq)
                .last("LIMIT 1");
        BizBudget lastChange = budgetMapper.selectOne(wrapper);
        int nextSeq = (lastChange != null ? lastChange.getChangeSeq() : 0) + 1;
        budget.setChangeSeq(nextSeq);

        if (budget.getTotalAmount() == null) {
            budget.setTotalAmount(BigDecimal.ZERO);
        }
        budgetMapper.insert(budget);
    }

    /**
     * 提交审批（审批通过→重新计算并更新项目预算金额）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizBudget budget = budgetMapper.selectById(id);
        if (budget == null) {
            throw new BusinessException("预算变更不存在");
        }
        if (!"DRAFT".equals(budget.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 汇总本次变更明细金额
        BigDecimal changeTotalAmount = calculateTotalAmount(id);
        budget.setTotalAmount(changeTotalAmount);
        budget.setStatus("APPROVED");
        budgetMapper.updateById(budget);

        // 重新计算项目预算金额 = 所有已审批预算的总金额之和
        BigDecimal projectBudgetAmount = recalculateProjectBudget(budget.getProjectId());

        // 更新项目预算金额
        BizProject project = projectMapper.selectById(budget.getProjectId());
        if (project != null) {
            project.setBudgetAmount(projectBudgetAmount);
            projectMapper.updateById(project);
        }
    }

    /**
     * 重新计算项目预算总额（原始 + 所有已审批变更）
     */
    private BigDecimal recalculateProjectBudget(Long projectId) {
        LambdaQueryWrapper<BizBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudget::getProjectId, projectId)
                .eq(BizBudget::getStatus, "APPROVED");
        List<BizBudget> approvedBudgets = budgetMapper.selectList(wrapper);
        return approvedBudgets.stream()
                .map(b -> b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算预算明细合计
     */
    private BigDecimal calculateTotalAmount(Long budgetId) {
        LambdaQueryWrapper<BizBudgetDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudgetDetail::getBudgetId, budgetId);
        List<BizBudgetDetail> details = budgetDetailMapper.selectList(wrapper);
        return details.stream()
                .map(d -> d.getBudgetTotalPrice() != null ? d.getBudgetTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
