package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.purchase.mapper.BizPurchaseSettlementMapper;
import com.zwinsight.purchase.readmodel.MaterialInboundReadMapper;
import com.zwinsight.purchase.readmodel.MaterialInboundView;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购结算服务
 * <p>
 * 采购结算以「材料入库单」为结算依据：创建结算单时必须关联一张已审批的入库单，
 * 系统据此自动带入入库金额(inboundAmount)，并校验本次结算金额不超过入库金额，
 * 同一入库单只能结算一次（防止重复结算）。提交后发起 Flowable 审批，审批发起成功
 * 即回写合同累计结算金额。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PurchaseSettlementService {

    private final BizPurchaseSettlementMapper settlementMapper;
    private final BizPurchaseContractMapper contractMapper;
    private final MaterialInboundReadMapper materialInboundReadMapper;
    private final SerialNumberService serialNumberService;
    private final ApprovalService approvalService;

    /**
     * 分页查询（附合同名称/供应商名称/入库单号展示字段）
     */
    public PageResult<BizPurchaseSettlement> page(int page, int size, Long projectId, Long contractId, String status) {
        Page<BizPurchaseSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPurchaseSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPurchaseSettlement::getProjectId, projectId)
                .eq(contractId != null, BizPurchaseSettlement::getContractId, contractId)
                .eq(status != null && !status.isEmpty(), BizPurchaseSettlement::getStatus, status)
                .orderByDesc(BizPurchaseSettlement::getCreatedAt);
        Page<BizPurchaseSettlement> result = settlementMapper.selectPage(pageParam, wrapper);
        result.getRecords().forEach(this::fillDisplayFields);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BizPurchaseSettlement getById(Long id) {
        BizPurchaseSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("采购结算不存在");
        }
        fillDisplayFields(settlement);
        return settlement;
    }

    /**
     * 查询指定合同下可结算的入库单（已审批且尚未被结算的批次）
     */
    public List<MaterialInboundView> availableInbounds(Long contractId) {
        if (contractId == null) {
            throw new BusinessException("请先选择采购合同");
        }
        List<MaterialInboundView> inbounds = materialInboundReadMapper.selectApprovedByContract(contractId);
        // 剔除已存在结算记录的入库单（一张入库单只结算一次）
        inbounds.removeIf(inbound -> settlementExistsForInbound(inbound.getId(), null));
        return inbounds;
    }

    /**
     * 新增采购结算（草稿）
     * <p>
     * 必须关联入库单：据入库单带入 projectId/contractId/inboundAmount，
     * 校验结算金额 ≤ 入库金额，并阻止对同一入库单重复结算。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(BizPurchaseSettlement settlement) {
        if (settlement.getInboundId() == null) {
            throw new BusinessException("采购结算必须关联入库单");
        }
        if (settlement.getSettlementAmount() == null || settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("结算金额必须大于0");
        }

        // 加载入库单作为结算依据
        MaterialInboundView inbound = materialInboundReadMapper.selectById(settlement.getInboundId());
        if (inbound == null) {
            throw new BusinessException("关联的入库单不存在");
        }
        if (!"APPROVED".equals(inbound.getStatus())) {
            throw new BusinessException("仅已审批的入库单可用于结算");
        }
        if (inbound.getContractId() == null) {
            throw new BusinessException("该入库单未关联采购合同，无法结算");
        }
        // 一张入库单只能结算一次
        if (settlementExistsForInbound(inbound.getId(), null)) {
            throw new BusinessException("该入库单已存在结算记录，不可重复结算");
        }

        BigDecimal inboundAmount = inbound.getTotalAmount() != null ? inbound.getTotalAmount() : BigDecimal.ZERO;
        if (settlement.getSettlementAmount().compareTo(inboundAmount) > 0) {
            throw new BusinessException("结算金额不能大于入库金额");
        }

        // 依入库单带入项目/合同/入库金额（后端为准，不信任前端传入）
        settlement.setProjectId(inbound.getProjectId());
        settlement.setContractId(inbound.getContractId());
        settlement.setInboundCode(inbound.getInboundCode());
        settlement.setInboundAmount(inboundAmount);

        // 计算累计结算金额 = 合同已审批结算合计 + 本次
        BigDecimal cumulative = sumApprovedSettlement(inbound.getContractId()).add(settlement.getSettlementAmount());
        settlement.setCumulativeSettlement(cumulative);

        settlement.setSettlementNo(serialNumberService.generate("PURCHASE_SETTLEMENT"));
        settlement.setStatus("DRAFT");
        settlement.setWorkflowInstanceId(null);
        settlementMapper.insert(settlement);
    }

    /**
     * 更新采购结算（仅草稿可编辑；仅允许修改结算金额/日期/备注，结算依据不可改）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(BizPurchaseSettlement settlement) {
        BizPurchaseSettlement existing = settlementMapper.selectById(settlement.getId());
        if (existing == null) {
            throw new BusinessException("采购结算不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        if (settlement.getSettlementAmount() == null || settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("结算金额必须大于0");
        }
        BigDecimal inboundAmount = existing.getInboundAmount() != null ? existing.getInboundAmount() : BigDecimal.ZERO;
        if (settlement.getSettlementAmount().compareTo(inboundAmount) > 0) {
            throw new BusinessException("结算金额不能大于入库金额");
        }

        // 仅更新可编辑字段，其余保持原值（结算依据不可篡改）
        existing.setSettlementAmount(settlement.getSettlementAmount());
        existing.setSettlementDate(settlement.getSettlementDate());
        existing.setRemark(settlement.getRemark());
        existing.setCumulativeSettlement(
                sumApprovedSettlement(existing.getContractId()).add(settlement.getSettlementAmount()));
        settlementMapper.updateById(existing);
    }

    /**
     * 删除采购结算（仅草稿可删除）
     */
    @Transactional(rollbackFor = Exception.class)
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
     * 提交审批（审批发起成功 → status=APPROVED，回写合同累计结算金额）
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
        variables.put("projectId", settlement.getProjectId());
        variables.put("contractId", settlement.getContractId());
        String processInstanceId = approvalService.startProcess(
                "PURCHASE_SETTLEMENT", id, "purchase_settlement_approval", variables);

        settlement.setWorkflowInstanceId(processInstanceId);
        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 回写合同累计结算金额
        BizPurchaseContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract != null) {
            BigDecimal current = contract.getCumulativeSettlement() != null
                    ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            contract.setCumulativeSettlement(current.add(settlement.getSettlementAmount()));
            contractMapper.updateById(contract);
        }
    }

    // ===== 私有方法 =====

    /**
     * 是否已存在关联指定入库单的结算记录（排除指定ID自身）
     */
    private boolean settlementExistsForInbound(Long inboundId, Long excludeId) {
        LambdaQueryWrapper<BizPurchaseSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizPurchaseSettlement::getInboundId, inboundId)
                .ne(excludeId != null, BizPurchaseSettlement::getId, excludeId);
        return settlementMapper.selectCount(wrapper) > 0;
    }

    /**
     * 合同已审批结算金额合计
     */
    private BigDecimal sumApprovedSettlement(Long contractId) {
        LambdaQueryWrapper<BizPurchaseSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizPurchaseSettlement::getContractId, contractId)
                .eq(BizPurchaseSettlement::getStatus, "APPROVED");
        return settlementMapper.selectList(wrapper).stream()
                .map(s -> s.getSettlementAmount() != null ? s.getSettlementAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 填充展示字段：合同名称、供应商名称、入库单号
     */
    private void fillDisplayFields(BizPurchaseSettlement settlement) {
        if (settlement.getContractId() != null) {
            BizPurchaseContract contract = contractMapper.selectById(settlement.getContractId());
            if (contract != null) {
                settlement.setContractName(contract.getContractName());
                settlement.setSupplierName(contract.getSupplierName());
            }
        }
        if (settlement.getInboundCode() == null && settlement.getInboundId() != null) {
            MaterialInboundView inbound = materialInboundReadMapper.selectById(settlement.getInboundId());
            if (inbound != null) {
                settlement.setInboundCode(inbound.getInboundCode());
            }
        }
    }
}
