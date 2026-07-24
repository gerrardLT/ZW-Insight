package com.zwinsight.purchase.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.budget.service.BudgetControlService;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseContractDetail;
import com.zwinsight.purchase.mapper.BizPurchaseContractDetailMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购合同服务
 */
@Service
@RequiredArgsConstructor
public class PurchaseContractService {

    private final BizPurchaseContractMapper purchaseContractMapper;
    private final BizPurchaseContractDetailMapper detailMapper;
    private final SerialNumberService serialNumberService;
    private final BudgetControlService budgetControlService;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizPurchaseContract> page(int page, int size, Long projectId, String contractName, String supplierName, String status) {
        Page<BizPurchaseContract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPurchaseContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPurchaseContract::getProjectId, projectId)
                .like(StrUtil.isNotBlank(contractName), BizPurchaseContract::getContractName, contractName)
                .like(StrUtil.isNotBlank(supplierName), BizPurchaseContract::getSupplierName, supplierName)
                .eq(StrUtil.isNotBlank(status), BizPurchaseContract::getStatus, status)
                .orderByDesc(BizPurchaseContract::getCreatedAt);
        Page<BizPurchaseContract> result = purchaseContractMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BizPurchaseContract getById(Long id) {
        BizPurchaseContract contract = purchaseContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("采购合同不存在");
        }
        return contract;
    }

    /**
     * 新增采购合同（自动编号 + 预算校验 + 黑名单拦截）
     */
    @BlacklistCheck
    @Transactional(rollbackFor = Exception.class)
    public void save(BizPurchaseContract contract) {
        // 自动生成合同编号
        String contractCode = serialNumberService.generate("PURCHASE_CONTRACT");
        contract.setContractCode(contractCode);
        contract.setStatus("DRAFT");

        // 预算校验（材料采购归类为 MATERIAL）
        budgetControlService.checkBudget(contract.getProjectId(), "MATERIAL", contract.getContractAmount());

        // 初始化累计字段
        if (contract.getCumulativeInbound() == null) {
            contract.setCumulativeInbound(BigDecimal.ZERO);
        }
        if (contract.getCumulativeSettlement() == null) {
            contract.setCumulativeSettlement(BigDecimal.ZERO);
        }
        if (contract.getCumulativePaid() == null) {
            contract.setCumulativePaid(BigDecimal.ZERO);
        }
        if (contract.getCumulativeInvoiceReceived() == null) {
            contract.setCumulativeInvoiceReceived(BigDecimal.ZERO);
        }

        purchaseContractMapper.insert(contract);
    }

    /**
     * 更新采购合同
     */
    @BlacklistCheck
    public void update(BizPurchaseContract contract) {
        BizPurchaseContract existing = purchaseContractMapper.selectById(contract.getId());
        if (existing == null) {
            throw new BusinessException("采购合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        purchaseContractMapper.updateById(contract);
    }

    /**
     * 提交审批
     */
    @BudgetCheck(category = "MATERIAL")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizPurchaseContract contract = purchaseContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("采购合同不存在");
        }
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("contractAmount", contract.getContractAmount());
        variables.put("projectId", contract.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "PURCHASE_CONTRACT", id, "purchase_contract_approval", variables);

        contract.setWorkflowInstanceId(processInstanceId);
        contract.setStatus("EFFECTIVE");
        purchaseContractMapper.updateById(contract);
    }

    /**
     * 获取合同明细
     */
    public List<BizPurchaseContractDetail> getDetails(Long contractId) {
        LambdaQueryWrapper<BizPurchaseContractDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizPurchaseContractDetail::getContractId, contractId)
                .orderByAsc(BizPurchaseContractDetail::getSortOrder);
        return detailMapper.selectList(wrapper);
    }

    /**
     * 删除采购合同
     */
    public void delete(Long id) {
        BizPurchaseContract existing = purchaseContractMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("采购合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        purchaseContractMapper.deleteById(id);
    }
}
