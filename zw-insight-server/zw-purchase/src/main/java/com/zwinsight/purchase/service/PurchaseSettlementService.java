package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.purchase.mapper.BizPurchaseSettlementMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 采购结算服务
 */
@Service
@RequiredArgsConstructor
public class PurchaseSettlementService {

    private final BizPurchaseSettlementMapper settlementMapper;
    private final BizPurchaseContractMapper contractMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizPurchaseSettlement> page(int page, int size, Long projectId, Long contractId) {
        Page<BizPurchaseSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPurchaseSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPurchaseSettlement::getProjectId, projectId)
                .eq(contractId != null, BizPurchaseSettlement::getContractId, contractId)
                .orderByDesc(BizPurchaseSettlement::getCreatedAt);
        Page<BizPurchaseSettlement> result = settlementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存采购结算（草稿）
     */
    public void save(BizPurchaseSettlement settlement) {
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);
    }

    /**
     * 提交审批（审批通过→回写采购合同累计结算）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizPurchaseSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("采购结算不存在");
        }
        if (!"DRAFT".equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("settlementAmount", settlement.getSettlementAmount());
        variables.put("contractId", settlement.getContractId());
        String processInstanceId = approvalService.startProcess(
                "PURCHASE_SETTLEMENT", id, "purchase_settlement_approval", variables);

        // 回写采购合同累计结算
        BizPurchaseContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract == null) {
            throw new BusinessException("采购合同不存在");
        }

        BigDecimal newCumulativeSettlement = (contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO)
                .add(settlement.getSettlementAmount());

        settlement.setCumulativeSettlement(newCumulativeSettlement);
        settlement.setWorkflowInstanceId(processInstanceId);
        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        contract.setCumulativeSettlement(newCumulativeSettlement);
        contractMapper.updateById(contract);
    }
}
