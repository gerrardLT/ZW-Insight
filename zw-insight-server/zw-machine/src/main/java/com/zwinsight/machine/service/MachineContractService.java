package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.workflow.service.ApprovalService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import org.springframework.context.event.EventListener;

/**
 * 机械合同服务
 */
@Service
@RequiredArgsConstructor
public class MachineContractService {

    private final BizMachineContractMapper machineContractMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final SerialNumberService serialNumberService;
    private final ApprovalService approvalService;

    private void validate(BizMachineContract contract) {
        if (contract.getProjectId() == null || contract.getContractAmount() == null
                || contract.getContractAmount().signum() <= 0 || contract.getUnitPrice() == null
                || contract.getUnitPrice().signum() <= 0) {
            throw new BusinessException("项目、合同总额及正数计价单价必填");
        }
        if (!List.of("SHIFT", "台班", "MONTHLY", "月租", "包月", "VOLUME", "工作量")
                .contains(contract.getRentalType() == null ? "" : contract.getRentalType())) {
            throw new BusinessException("不支持的机械计价类型");
        }
        if (contract.getStartDate() == null || contract.getEndDate() == null
                || contract.getStartDate().isAfter(contract.getEndDate())) {
            throw new BusinessException("合同起止日期无效");
        }
    }

    private BizMachineContract owned(Long id) {
        BizMachineContract contract = machineContractMapper.selectById(id);
        Long tenantId = SecurityContextHolder.getTenantId();
        if (contract == null || tenantId == null || !Objects.equals(tenantId, contract.getTenantId())) {
            throw new BusinessException("机械合同不存在");
        }
        return contract;
    }

    private void transition(Long id, String from, String to, String processId) {
        LambdaUpdateWrapper<BizMachineContract> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BizMachineContract::getId, id)
                .eq(BizMachineContract::getTenantId, SecurityContextHolder.getTenantId())
                .eq(BizMachineContract::getStatus, from).set(BizMachineContract::getStatus, to);
        if (processId != null) wrapper.set(BizMachineContract::getWorkflowInstanceId, processId);
        if (machineContractMapper.update(null, wrapper) != 1) {
            throw new BusinessException("合同状态已变更，请刷新重试");
        }
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(ApprovalCompleteEvent event) {
        if (!"MACHINE_CONTRACT".equals(event.getBusinessType()) || !"APPROVED".equals(event.getResult())) return;
        BizMachineContract contract = owned(event.getBusinessId());
        if ("EFFECTIVE".equals(contract.getStatus())) return;
        validate(contract);
        transition(contract.getId(), "SUBMITTED", "EFFECTIVE", null);
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(ApprovalRejectEvent event) {
        if (!"MACHINE_CONTRACT".equals(event.getBizType())) return;
        BizMachineContract contract = owned(event.getBizId());
        if ("DRAFT".equals(contract.getStatus())) return;
        transition(contract.getId(), "SUBMITTED", "DRAFT", null);
    }

    public PageResult<BizMachineContract> page(int page, int size, Long projectId, String contractName, String supplierName) {
        Page<BizMachineContract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMachineContract::getProjectId, projectId)
                .like(StrUtil.isNotBlank(contractName), BizMachineContract::getContractName, contractName)
                .like(StrUtil.isNotBlank(supplierName), BizMachineContract::getSupplierName, supplierName)
                .orderByDesc(BizMachineContract::getCreatedAt);
        return PageResult.of(machineContractMapper.selectPage(pageParam, wrapper));
    }

    @BlacklistCheck
    @Transactional(rollbackFor = Exception.class)
    public void save(BizMachineContract contract) {
        validate(contract);
        if (SecurityContextHolder.getTenantId() == null) throw new BusinessException("缺少租户上下文");
        contract.setId(null);
        contract.setTenantId(SecurityContextHolder.getTenantId());
        contract.setWorkflowInstanceId(null);
        contract.setContractCode(serialNumberService.generate("MACHINE_CONTRACT"));
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

        contract.setCumulativeSettlement(BigDecimal.ZERO);
        contract.setCumulativePaid(BigDecimal.ZERO);
        contract.setStatus("DRAFT");
        machineContractMapper.insert(contract);
    }

    @BudgetCheck(category = "MACHINE")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizMachineContract contract = owned(id);
        if (!"DRAFT".equals(contract.getStatus())) throw new BusinessException("仅草稿状态可提交");
        validate(contract);
        String processId = approvalService.startProcess("MACHINE_CONTRACT", id,
                "machine_contract_approval", new java.util.HashMap<>(Map.of("projectId", contract.getProjectId(),
                "contractAmount", contract.getContractAmount())));
        transition(id, "DRAFT", "SUBMITTED", processId);
    }

    public BizMachineContract getById(Long id) {
        return owned(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(BizMachineContract contract) {
        BizMachineContract existing = owned(contract.getId());
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        contract.setProjectId(existing.getProjectId());
        validate(contract);
        LambdaUpdateWrapper<BizMachineContract> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BizMachineContract::getId, existing.getId())
                .eq(BizMachineContract::getTenantId, SecurityContextHolder.getTenantId())
                .eq(BizMachineContract::getStatus, "DRAFT")
                .set(BizMachineContract::getContractName, contract.getContractName())
                .set(BizMachineContract::getSupplierId, contract.getSupplierId())
                .set(BizMachineContract::getSupplierName, contract.getSupplierName())
                .set(BizMachineContract::getMachineName, contract.getMachineName())
                .set(BizMachineContract::getRentalType, contract.getRentalType())
                .set(BizMachineContract::getSigningDate, contract.getSigningDate())
                .set(BizMachineContract::getStartDate, contract.getStartDate())
                .set(BizMachineContract::getEndDate, contract.getEndDate())
                .set(BizMachineContract::getContractAmount, contract.getContractAmount())
                .set(BizMachineContract::getUnitPrice, contract.getUnitPrice())
                .set(BizMachineContract::getPaymentTerms, contract.getPaymentTerms());
        if (machineContractMapper.update(null, wrapper) != 1) throw new BusinessException("合同状态已变更，请刷新重试");
    }

    public void delete(Long id) {
        BizMachineContract existing = owned(id);
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        machineContractMapper.deleteById(id);
    }
}
