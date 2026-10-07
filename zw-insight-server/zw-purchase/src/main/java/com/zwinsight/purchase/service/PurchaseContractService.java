package com.zwinsight.purchase.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseContractDetail;
import com.zwinsight.purchase.mapper.BizPurchaseContractDetailMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购合同服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseContractService {

    private final BizPurchaseContractMapper purchaseContractMapper;
    private final BizPurchaseContractDetailMapper detailMapper;
    private final SerialNumberService serialNumberService;
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
    @BudgetCheck(category = "MATERIAL")
    @Transactional(rollbackFor = Exception.class)
    public void save(BizPurchaseContract contract) {
        // 自动生成合同编号
        String contractCode = serialNumberService.generate("PURCHASE_CONTRACT");
        contract.setContractCode(contractCode);
        contract.setStatus("DRAFT");

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
        // PI-5: 白名单防篡改：只允许更新业务描述字段，状态与累计字段由业务生命周期强管控
        existing.setContractName(contract.getContractName());
        existing.setPartyBId(contract.getPartyBId());
        existing.setPartyBName(contract.getPartyBName());
        existing.setSupplierName(contract.getSupplierName());
        existing.setSigningDate(contract.getSigningDate());
        existing.setBudgetId(contract.getBudgetId());
        existing.setContractAmount(contract.getContractAmount());
        existing.setPaymentTerms(contract.getPaymentTerms());
        purchaseContractMapper.updateById(existing);
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
        // PI-3: 提交仅进入审批中状态（SUBMITTED），审批通过后再由监听器置为 EFFECTIVE
        contract.setStatus("SUBMITTED");
        purchaseContractMapper.updateById(contract);
    }

    /**
     * 审批通过回调（幂等：仅处于 SUBMITTED 态可置为 EFFECTIVE）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizPurchaseContract contract = purchaseContractMapper.selectById(id);
        if (contract == null) {
            log.warn("采购合同审批通过回调：单据不存在, id={}", id);
            return;
        }
        if ("EFFECTIVE".equals(contract.getStatus())) {
            log.info("采购合同已生效，跳过重复回调, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(contract.getStatus())) {
            log.warn("采购合同当前状态非 SUBMITTED，忽略生效回调: id={}, status={}", id, contract.getStatus());
            return;
        }

        contract.setStatus("EFFECTIVE");
        purchaseContractMapper.updateById(contract);
        log.info("采购合同审批通过生效: id={}, contractCode={}", id, contract.getContractCode());
    }

    /**
     * 审批驳回/撤回回调（仅 SUBMITTED 可回退 DRAFT）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizPurchaseContract contract = purchaseContractMapper.selectById(id);
        if (contract == null) {
            log.warn("采购合同审批驳回回调：单据不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(contract.getStatus())) {
            log.warn("采购合同当前状态非 SUBMITTED，忽略驳回回调: id={}, status={}", id, contract.getStatus());
            return;
        }

        contract.setStatus("DRAFT");
        purchaseContractMapper.updateById(contract);
        log.info("采购合同审批驳回回退草稿: id={}", id);
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
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        purchaseContractMapper.deleteById(id);
    }
}
