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
 * 预算服务
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BizBudgetMapper budgetMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询
     */
    public PageResult<BizBudget> page(int page, int size, Long projectId) {
        Page<BizBudget> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizBudget::getProjectId, projectId)
                .eq(BizBudget::getBudgetType, "ORIGINAL")
                .orderByDesc(BizBudget::getCreatedAt);
        Page<BizBudget> result = budgetMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BizBudget getById(Long id) {
        BizBudget budget = budgetMapper.selectById(id);
        if (budget == null) {
            throw new BusinessException("预算不存在");
        }
        return budget;
    }

    /**
     * 保存预算（校验每项目仅1条ORIGINAL）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizBudget budget) {
        // 校验：每项目仅允许1条ORIGINAL预算
        if ("ORIGINAL".equals(budget.getBudgetType())) {
            LambdaQueryWrapper<BizBudget> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizBudget::getProjectId, budget.getProjectId())
                    .eq(BizBudget::getBudgetType, "ORIGINAL");
            Long count = budgetMapper.selectCount(wrapper);
            if (count > 0) {
                throw new BusinessException("该项目已存在原始预算，不可重复创建");
            }
        }

        budget.setChangeSeq(0);
        budget.setStatus("DRAFT");
        if (budget.getTotalAmount() == null) {
            budget.setTotalAmount(BigDecimal.ZERO);
        }
        budgetMapper.insert(budget);
    }

    /**
     * 提交审批（审批通过→回写项目预算金额）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizBudget budget = budgetMapper.selectById(id);
        if (budget == null) {
            throw new BusinessException("预算不存在");
        }
        if (!"DRAFT".equals(budget.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 汇总明细金额
        BigDecimal totalAmount = calculateTotalAmount(id);
        budget.setTotalAmount(totalAmount);
        budget.setStatus("APPROVED");
        budgetMapper.updateById(budget);

        // 回写项目预算金额
        BizProject project = projectMapper.selectById(budget.getProjectId());
        if (project != null) {
            project.setBudgetAmount(totalAmount);
            projectMapper.updateById(project);
        }
    }

    /**
     * 按项目ID获取预算（含原始+变更）
     */
    public BizBudget getByProject(Long projectId) {
        LambdaQueryWrapper<BizBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudget::getProjectId, projectId)
                .eq(BizBudget::getBudgetType, "ORIGINAL")
                .last("LIMIT 1");
        return budgetMapper.selectOne(wrapper);
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
