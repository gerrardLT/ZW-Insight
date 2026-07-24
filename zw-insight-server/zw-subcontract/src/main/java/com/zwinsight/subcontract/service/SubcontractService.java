package com.zwinsight.subcontract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分包合同服务
 */
@Service
@RequiredArgsConstructor
public class SubcontractService {

    private final BizSubcontractMapper subcontractMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BizProjectMapper projectMapper;

    public PageResult<BizSubcontract> page(int page, int size, Long projectId, String contractName, String subcontractor, String status) {
        Page<BizSubcontract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSubcontract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSubcontract::getProjectId, projectId)
                .like(StrUtil.isNotBlank(contractName), BizSubcontract::getContractName, contractName)
                .like(StrUtil.isNotBlank(subcontractor), BizSubcontract::getSubcontractor, subcontractor)
                .eq(StrUtil.isNotBlank(status), BizSubcontract::getStatus, status)
                .orderByDesc(BizSubcontract::getCreatedAt);
        Page<BizSubcontract> result = subcontractMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizSubcontract::getProjectId, BizSubcontract::setProjectName);
        return PageResult.of(result);
    }

    @BlacklistCheck
    @Transactional(rollbackFor = Exception.class)
    public void save(BizSubcontract contract) {
        // 预算控制：SUBCONTRACT
        if (contract.getBudgetId() != null) {
            LambdaQueryWrapper<BizBudgetDetail> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizBudgetDetail::getBudgetId, contract.getBudgetId())
                    .eq(BizBudgetDetail::getCostCategory, "SUBCONTRACT");
            List<BizBudgetDetail> details = budgetDetailMapper.selectList(wrapper);
            BigDecimal budgetTotal = details.stream()
                    .map(d -> d.getBudgetTotalPrice() != null ? d.getBudgetTotalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            LambdaQueryWrapper<BizSubcontract> contractWrapper = new LambdaQueryWrapper<>();
            contractWrapper.eq(BizSubcontract::getProjectId, contract.getProjectId())
                    .ne(contract.getId() != null, BizSubcontract::getId, contract.getId());
            List<BizSubcontract> existingContracts = subcontractMapper.selectList(contractWrapper);
            BigDecimal usedAmount = existingContracts.stream()
                    .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal newTotal = usedAmount.add(contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO);
            if (newTotal.compareTo(budgetTotal) > 0) {
                throw new BusinessException("分包合同金额超出预算，预算余额：" + budgetTotal.subtract(usedAmount));
            }
        }

        if (contract.getCumulativeSettlement() == null) contract.setCumulativeSettlement(BigDecimal.ZERO);
        if (contract.getCumulativePaid() == null) contract.setCumulativePaid(BigDecimal.ZERO);
        contract.setStatus("DRAFT");
        subcontractMapper.insert(contract);
    }

    @BudgetCheck(category = "SUBCONTRACT")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizSubcontract contract = subcontractMapper.selectById(id);
        if (contract == null) throw new BusinessException("分包合同不存在");
        if (!"DRAFT".equals(contract.getStatus())) throw new BusinessException("仅草稿状态可提交");
        contract.setStatus("EFFECTIVE");
        subcontractMapper.updateById(contract);
    }

    public BizSubcontract getById(Long id) {
        BizSubcontract contract = subcontractMapper.selectById(id);
        if (contract == null) throw new BusinessException("分包合同不存在");
        return contract;
    }

    @BlacklistCheck
    public void update(BizSubcontract contract) {
        BizSubcontract existing = subcontractMapper.selectById(contract.getId());
        if (existing == null) throw new BusinessException("分包合同不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        subcontractMapper.updateById(contract);
    }

    public void delete(Long id) {
        BizSubcontract existing = subcontractMapper.selectById(id);
        if (existing == null) throw new BusinessException("分包合同不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        subcontractMapper.deleteById(id);
    }
}
