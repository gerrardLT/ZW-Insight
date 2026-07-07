package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 机械合同服务
 */
@Service
@RequiredArgsConstructor
public class MachineContractService {

    private final BizMachineContractMapper machineContractMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;

    public PageResult<BizMachineContract> page(int page, int size, Long projectId) {
        Page<BizMachineContract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMachineContract::getProjectId, projectId)
                .orderByDesc(BizMachineContract::getCreatedAt);
        return PageResult.of(machineContractMapper.selectPage(pageParam, wrapper));
    }

    @BlacklistCheck
    @Transactional(rollbackFor = Exception.class)
    public void save(BizMachineContract contract) {
        // 预算控制
        if (contract.getBudgetId() != null) {
            LambdaQueryWrapper<BizBudgetDetail> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizBudgetDetail::getBudgetId, contract.getBudgetId())
                    .eq(BizBudgetDetail::getCostCategory, "MACHINE");
            List<BizBudgetDetail> details = budgetDetailMapper.selectList(wrapper);
            BigDecimal budgetTotal = details.stream()
                    .map(d -> d.getBudgetTotalPrice() != null ? d.getBudgetTotalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            LambdaQueryWrapper<BizMachineContract> contractWrapper = new LambdaQueryWrapper<>();
            contractWrapper.eq(BizMachineContract::getProjectId, contract.getProjectId())
                    .ne(contract.getId() != null, BizMachineContract::getId, contract.getId());
            List<BizMachineContract> existingContracts = machineContractMapper.selectList(contractWrapper);
            BigDecimal usedAmount = existingContracts.stream()
                    .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal newTotal = usedAmount.add(contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO);
            if (newTotal.compareTo(budgetTotal) > 0) {
                throw new BusinessException("机械合同金额超出预算，预算余额：" + budgetTotal.subtract(usedAmount));
            }
        }

        if (contract.getCumulativeSettlement() == null) contract.setCumulativeSettlement(BigDecimal.ZERO);
        if (contract.getCumulativePaid() == null) contract.setCumulativePaid(BigDecimal.ZERO);
        contract.setStatus("DRAFT");
        machineContractMapper.insert(contract);
    }

    @BudgetCheck(category = "MACHINE")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizMachineContract contract = machineContractMapper.selectById(id);
        if (contract == null) throw new BusinessException("机械合同不存在");
        if (!"DRAFT".equals(contract.getStatus())) throw new BusinessException("仅草稿状态可提交");
        contract.setStatus("EFFECTIVE");
        machineContractMapper.updateById(contract);
    }

    public BizMachineContract getById(Long id) {
        BizMachineContract contract = machineContractMapper.selectById(id);
        if (contract == null) throw new BusinessException("机械合同不存在");
        return contract;
    }

    public void update(BizMachineContract contract) {
        BizMachineContract existing = machineContractMapper.selectById(contract.getId());
        if (existing == null) throw new BusinessException("机械合同不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        machineContractMapper.updateById(contract);
    }

    public void delete(Long id) {
        BizMachineContract existing = machineContractMapper.selectById(id);
        if (existing == null) throw new BusinessException("机械合同不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        machineContractMapper.deleteById(id);
    }
}
