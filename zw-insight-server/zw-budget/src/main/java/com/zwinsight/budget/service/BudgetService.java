package com.zwinsight.budget.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.dto.BudgetCreateRequest;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
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
    public PageResult<BizBudget> page(int page, int size, Long projectId, String status) {
        Page<BizBudget> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizBudget::getProjectId, projectId)
                .eq(cn.hutool.core.util.StrUtil.isNotBlank(status), BizBudget::getStatus, status)
                .eq(BizBudget::getBudgetType, "ORIGINAL")
                .orderByDesc(BizBudget::getCreatedAt);
        Page<BizBudget> result = budgetMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizBudget::getProjectId, BizBudget::setProjectName);
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
     * 查询指定预算下的全部明细（供预算变更表单选择原预算明细）
     */
    public List<BizBudgetDetail> getDetailsByBudgetId(Long budgetId) {
        LambdaQueryWrapper<BizBudgetDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudgetDetail::getBudgetId, budgetId)
                .orderByAsc(BizBudgetDetail::getId);
        return budgetDetailMapper.selectList(wrapper);
    }

    /**
     * 从请求 DTO 创建预算（含明细行；缺陷#8：补齐预算明细录入能力）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFromRequest(BudgetCreateRequest request) {
        BizBudget budget = new BizBudget();
        BeanUtil.copyProperties(request, budget);
        save(budget);
        saveDetails(budget.getId(), request.getDetails());
        // 存在明细时以明细合计为准，与 submit() 汇总口径保持一致
        if (request.getDetails() != null && !request.getDetails().isEmpty()) {
            budget.setTotalAmount(calculateTotalAmount(budget.getId()));
            budgetMapper.updateById(budget);
        }
    }

    /**
     * 从请求 DTO 更新预算（同步重建明细行）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFromRequest(Long id, BudgetCreateRequest request) {
        BizBudget budget = new BizBudget();
        BeanUtil.copyProperties(request, budget);
        budget.setId(id);
        update(budget);
        if (request.getDetails() != null) {
            budgetDetailMapper.delete(new LambdaQueryWrapper<BizBudgetDetail>()
                    .eq(BizBudgetDetail::getBudgetId, id));
            saveDetails(id, request.getDetails());
            budget.setTotalAmount(calculateTotalAmount(id));
            budgetMapper.updateById(budget);
        }
    }

    /**
     * 保存预算明细行（budgetTotalPrice 缺省时按 数量×单价 计算）
     */
    private void saveDetails(Long budgetId, List<BudgetCreateRequest.DetailItem> items) {
        if (items == null) {
            return;
        }
        for (BudgetCreateRequest.DetailItem item : items) {
            BizBudgetDetail detail = new BizBudgetDetail();
            BeanUtil.copyProperties(item, detail);
            detail.setBudgetId(budgetId);
            if (detail.getBudgetTotalPrice() == null) {
                BigDecimal qty = detail.getBudgetQuantity() != null ? detail.getBudgetQuantity() : BigDecimal.ZERO;
                BigDecimal price = detail.getBudgetUnitPrice() != null ? detail.getBudgetUnitPrice() : BigDecimal.ZERO;
                detail.setBudgetTotalPrice(qty.multiply(price));
            }
            budgetDetailMapper.insert(detail);
        }
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

        // 汇总明细金额：仅当存在明细行时以明细合计覆盖总额；
        // 无明细时保留用户录入的 totalAmount（前端预算编制弹窗仅录总额，
        // 若用空明细汇总 0 覆盖会导致预算总额清零并连带回写破坏项目预算，
        // 2026-08-17 归零重建后真实浏览器全链路实测发现）。
        List<BizBudgetDetail> details = listDetails(id);
        if (!details.isEmpty()) {
            BigDecimal totalAmount = details.stream()
                    .map(d -> d.getBudgetTotalPrice() != null ? d.getBudgetTotalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            budget.setTotalAmount(totalAmount);
        }
        budget.setStatus("APPROVED");
        budgetMapper.updateById(budget);

        // 回写项目预算金额
        BizProject project = projectMapper.selectById(budget.getProjectId());
        if (project != null) {
            project.setBudgetAmount(budget.getTotalAmount());
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
     * 更新预算
     */
    public void update(BizBudget budget) {
        BizBudget existing = budgetMapper.selectById(budget.getId());
        if (existing == null) {
            throw new BusinessException("预算不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        budgetMapper.updateById(budget);
    }

    /**
     * 删除预算
     */
    public void delete(Long id) {
        BizBudget existing = budgetMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("预算不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        budgetMapper.deleteById(id);
    }

    /**
     * 查询预算明细行
     */
    private List<BizBudgetDetail> listDetails(Long budgetId) {
        LambdaQueryWrapper<BizBudgetDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudgetDetail::getBudgetId, budgetId);
        return budgetDetailMapper.selectList(wrapper);
    }

    /**
     * 计算预算明细合计
     */
    private BigDecimal calculateTotalAmount(Long budgetId) {
        return listDetails(budgetId).stream()
                .map(d -> d.getBudgetTotalPrice() != null ? d.getBudgetTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
