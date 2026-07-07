package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购结算服务
 */
@Service
@RequiredArgsConstructor
public class PurchaseSettlementService {

    private final BizPurchaseSettlementMapper settlementMapper;
    private final BizPurchaseContractMapper contractMapper;
    private final BizMaterialInboundMapper inboundMapper;
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
     * 根据ID查询
     */
    public BizPurchaseSettlement getById(Long id) {
        BizPurchaseSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("采购结算不存在");
        }
        return settlement;
    }

    /**
     * 更新采购结算
     */
    public void update(BizPurchaseSettlement settlement) {
        BizPurchaseSettlement existing = settlementMapper.selectById(settlement.getId());
        if (existing == null) {
            throw new BusinessException("采购结算不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        settlementMapper.updateById(settlement);
    }

    /**
     * 删除采购结算
     */
    public void delete(Long id) {
        BizPurchaseSettlement existing = settlementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("采购结算不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        settlementMapper.deleteById(id);
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

        // 获取采购合同
        BizPurchaseContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract == null) {
            throw new BusinessException("采购合同不存在");
        }

        // 校验：累计结算金额不能超过合同金额
        BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
        BigDecimal currentCumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
        BigDecimal newCumulativeSettlement = currentCumulative.add(settlement.getSettlementAmount());

        if (newCumulativeSettlement.compareTo(contractAmount) > 0) {
            BigDecimal maxSettlement = contractAmount.subtract(currentCumulative);
            throw new BusinessException("结算金额超出合同金额限制，当前最大可结算金额：" + maxSettlement);
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("settlementAmount", settlement.getSettlementAmount());
        variables.put("contractId", settlement.getContractId());
        String processInstanceId = approvalService.startProcess(
                "PURCHASE_SETTLEMENT", id, "purchase_settlement_approval", variables);

        settlement.setCumulativeSettlement(newCumulativeSettlement);
        settlement.setWorkflowInstanceId(processInstanceId);
        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        contract.setCumulativeSettlement(newCumulativeSettlement);
        contractMapper.updateById(contract);
    }

    /**
     * 获取指定采购合同下已入库但未结算的入库批次列表
     * <p>
     * 业务逻辑：查询该合同关联的所有已审批入库单，排除已被结算单引用过的批次。
     * 前端在创建结算单时调用此接口，自动带出可结算的入库批次，避免重复结算和手动录入误差。
     * </p>
     *
     * @param contractId 采购合同ID
     * @return 未结算入库单列表（含入库日期、金额等信息）
     */
    public List<BizMaterialInbound> getUnsettledInbounds(Long contractId) {
        // 1. 查询该合同所有已审批的入库单
        LambdaQueryWrapper<BizMaterialInbound> inboundWrapper = new LambdaQueryWrapper<>();
        inboundWrapper.eq(BizMaterialInbound::getContractId, contractId)
                .eq(BizMaterialInbound::getStatus, "APPROVED")
                .orderByDesc(BizMaterialInbound::getInboundDate);
        List<BizMaterialInbound> allInbounds = inboundMapper.selectList(inboundWrapper);

        // 2. 查询该合同已审批的结算单，获取已结算的入库单ID列表
        LambdaQueryWrapper<BizPurchaseSettlement> settlementWrapper = new LambdaQueryWrapper<>();
        settlementWrapper.eq(BizPurchaseSettlement::getContractId, contractId)
                .eq(BizPurchaseSettlement::getStatus, "APPROVED");
        List<BizPurchaseSettlement> settledList = settlementMapper.selectList(settlementWrapper);

        // 已结算的入库单ID集合（从结算单的 inboundId 字段提取）
        List<Long> settledInboundIds = settledList.stream()
                .map(BizPurchaseSettlement::getInboundId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        // 3. 过滤出未结算的入库批次
        if (settledInboundIds.isEmpty()) {
            return allInbounds;
        }
        return allInbounds.stream()
                .filter(inbound -> !settledInboundIds.contains(inbound.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 根据入库批次汇总创建结算单
     * <p>
     * 选择多个未结算入库单后，自动汇总入库总金额作为本次结算金额。
     * </p>
     *
     * @param contractId 采购合同ID
     * @param inboundIds 选择的入库单ID列表
     * @return 创建的结算单
     */
    @Transactional(rollbackFor = Exception.class)
    public BizPurchaseSettlement createFromInbounds(Long contractId, List<Long> inboundIds) {
        if (inboundIds == null || inboundIds.isEmpty()) {
            throw new BusinessException("请选择至少一个入库批次");
        }

        // 查询选中的入库单
        List<BizMaterialInbound> inbounds = inboundMapper.selectBatchIds(inboundIds);
        if (inbounds.isEmpty()) {
            throw new BusinessException("入库单不存在");
        }

        // 校验所有入库单属于同一合同
        for (BizMaterialInbound inbound : inbounds) {
            if (!contractId.equals(inbound.getContractId())) {
                throw new BusinessException("入库单不属于指定合同");
            }
            if (!"APPROVED".equals(inbound.getStatus())) {
                throw new BusinessException("入库单 " + inbound.getInboundCode() + " 未审批，不可结算");
            }
        }

        // 汇总入库金额
        BigDecimal settlementAmount = inbounds.stream()
                .map(i -> i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 获取合同的项目ID
        BizPurchaseContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("采购合同不存在");
        }

        // 创建结算单
        BizPurchaseSettlement settlement = new BizPurchaseSettlement();
        settlement.setProjectId(contract.getProjectId());
        settlement.setContractId(contractId);
        settlement.setSettlementAmount(settlementAmount);
        settlement.setStatus("DRAFT");
        // 关联第一个入库单（如果结算单支持多入库关联，后续可扩展为中间表）
        if (!inboundIds.isEmpty()) {
            settlement.setInboundId(inboundIds.get(0));
        }
        settlementMapper.insert(settlement);

        return settlement;
    }
}
