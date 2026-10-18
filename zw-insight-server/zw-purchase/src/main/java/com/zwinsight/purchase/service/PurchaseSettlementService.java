package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseSettlementService {

    private final BizPurchaseSettlementMapper settlementMapper;
    private final com.zwinsight.purchase.mapper.PurchaseSourceMapper sourceMapper;
    private final com.zwinsight.purchase.mapper.BizSettlementToleranceMapper toleranceMapper;
    private final BizPurchaseContractMapper contractMapper;
    private final MaterialInboundReadMapper materialInboundReadMapper;
    private final SerialNumberService serialNumberService;
    private final ApprovalService approvalService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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
        if ("LINES".equals(settlement.getSettlementMode())) settlement.setDetails(sourceMapper.settlementLines(id));
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
        inbounds.removeIf(inbound -> sourceMapper.incompatible(inbound.getId(), -1L, 0) > 0);
        return inbounds;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Map<String,Object>> inboundLines(Long inboundId) {
        sourceMapper.lockInbound(inboundId);
        var inbound = materialInboundReadMapper.selectById(inboundId);
        if (inbound == null || !"APPROVED".equals(inbound.getStatus())) throw new BusinessException("来源入库单不存在或未审批");
        var lines = sourceMapper.lines(inboundId);
        for (var line : lines) {
            Long id = Long.valueOf(line.get("inboundDetailId").toString());
            line.put("availableQuantity", new BigDecimal(line.get("quantity").toString())
                    .subtract(sourceMapper.settled(id, -1L)).subtract(sourceMapper.returned(id)));
        }
        return lines;
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
        if (settlement.getDetails() == null && (settlement.getSettlementAmount() == null || settlement.getSettlementAmount().signum() <= 0)) {
            throw new BusinessException("结算金额必须大于0");
        }

        settlement.setId(null);
        settlement.setTenantId(null);
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
        sourceMapper.lockInbound(inbound.getId());
        settlement.setSettlementMode(settlement.getDetails() == null ? "LEGACY" : "LINES");
        checkMode(settlement, -1L);
        if ("LINES".equals(settlement.getSettlementMode())) calculateLines(settlement, -1L);
        // 一张入库单只能结算一次
        if ("LEGACY".equals(settlement.getSettlementMode()) && settlementExistsForInbound(inbound.getId(), null)) {
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
        saveLines(settlement);
    }

    /**
     * 更新采购结算（仅草稿可编辑；仅允许修改结算金额/日期/备注，结算依据不可改）
     */
    private void checkMode(BizPurchaseSettlement s, Long excludeId) {
        if (!"LINES".equals(s.getSettlementMode())) {
            var inbound = materialInboundReadMapper.selectById(s.getInboundId());
            if (inbound == null) throw new BusinessException("来源入库单不存在");
            // 整单模式不因「无来源的历史退货」拦截：沿用既有行为；只拦已明确指向本入库单的来源退货。
            // 无来源退货只影响分批模式（见 calculateLines），因其无法推断扣减哪一行。
            for (var line : sourceMapper.lines(s.getInboundId())) {
                Long lineId = Long.valueOf(line.get("inboundDetailId").toString());
                if (sourceMapper.returned(lineId).signum() > 0)
                    throw new BusinessException(409, "来源入库单已有明确来源退货，请改用分批结算");
            }
        }
        if (sourceMapper.incompatible(s.getInboundId(), excludeId, "LINES".equals(s.getSettlementMode()) ? 0 : 1) > 0)
            throw new BusinessException(409, "同一入库单整单与分批结算不得混用");
    }

    private void calculateLines(BizPurchaseSettlement s, Long excludeId) {
        var inbound = materialInboundReadMapper.selectById(s.getInboundId());
        if (inbound == null || !"APPROVED".equals(inbound.getStatus())) throw new BusinessException("来源入库单未审批");
        if (sourceMapper.unlinkedReturns(inbound.getContractId()) > 0)
            throw new BusinessException(409, "合同存在未关联来源入库行的历史退货，须人工核实，不可自动推断");
        if (s.getDetails() == null || s.getDetails().isEmpty()) throw new BusinessException("请选择结算明细");
        s.setDetails(s.getDetails().stream().map(x -> (Map<String,Object>) new HashMap<String,Object>(x)).collect(java.util.stream.Collectors.toList()));
        var sources = sourceMapper.lines(s.getInboundId());
        java.util.Set<Long> seen = new java.util.HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var line : s.getDetails()) {
            Long id;
            BigDecimal quantity;
            try {
                id = new BigDecimal(String.valueOf(line.get("inboundDetailId"))).longValueExact();
                quantity = new BigDecimal(String.valueOf(line.get("quantity")));
            } catch (RuntimeException e) { throw new BusinessException("来源行或数量格式错误"); }
            if (!seen.add(id) || quantity.signum() <= 0 || quantity.scale() > 4) throw new BusinessException("来源行重复或数量无效");
            final Long sourceId = id;
            var source = sources.stream().filter(x -> String.valueOf(x.get("inboundDetailId")).equals(sourceId.toString())).findFirst()
                    .orElseThrow(() -> new BusinessException("明细不属于所选入库单"));
            BigDecimal available = new BigDecimal(source.get("quantity").toString())
                    .subtract(sourceMapper.settled(id, excludeId)).subtract(sourceMapper.returned(id));
            if (quantity.compareTo(available) > 0) throw new BusinessException(409, "数量超过可结算余量（含审批中占用及退货）");
            BigDecimal sourcePrice = new BigDecimal(source.get("unitPrice").toString());
            // C4（V2026_111）：结算单价可偏离入库单价，超容差必须填修订原因（调价/折扣/扣款/运费等）
            BigDecimal settlementPrice = sourcePrice;
            Object inputPrice = line.get("settlementUnitPrice");
            if (inputPrice != null && !String.valueOf(inputPrice).isBlank()) {
                try {
                    settlementPrice = new BigDecimal(String.valueOf(inputPrice));
                } catch (NumberFormatException e) {
                    throw new BusinessException("结算单价格式错误");
                }
                if (settlementPrice.signum() < 0 || settlementPrice.scale() > 4) throw new BusinessException("结算单价无效");
            }
            BigDecimal sourceAmount = quantity.multiply(sourcePrice);
            BigDecimal difference = quantity.multiply(settlementPrice.subtract(sourcePrice)).setScale(2, java.math.RoundingMode.HALF_UP);
            String reason = line.get("differenceReason") == null ? "" : String.valueOf(line.get("differenceReason")).trim();
            // create() 会在校验前清空客户端 tenantId（由 MyBatis-Plus 插入时自动填充），
            // 容差读取回退登录上下文租户
            Long tenantId = s.getTenantId() != null ? s.getTenantId()
                    : com.zwinsight.common.config.SecurityContextHolder.getTenantId();
            if (exceedsTolerance(tenantId, sourceAmount, difference)) {
                if (reason.isEmpty()) {
                    throw new BusinessException(409, "结算单价偏离入库单价超过容差，必须填写修订原因（明细 #" + id + "）");
                }
            } else if (difference.signum() == 0) {
                reason = "";
            }
            BigDecimal amount = quantity.multiply(settlementPrice).setScale(2, java.math.RoundingMode.HALF_UP);
            line.put("unitPrice", sourcePrice);
            line.put("amount", amount);
            line.put("inboundDetailId", id);
            line.put("sourceUnitPrice", sourcePrice);
            line.put("settlementUnitPrice", settlementPrice);
            line.put("priceDifference", difference);
            line.put("differenceReason", reason);
            total = total.add(amount);
        }
        if (total.signum() <= 0) throw new BusinessException("结算金额必须大于0");
        s.setSettlementAmount(total);
    }

    /** 容差上限 = max(来源金额 × pct%, abs)；无配置行默认 0（任何价差都须填原因） */
    private boolean exceedsTolerance(Long tenantId, BigDecimal sourceAmount, BigDecimal difference) {
        if (difference == null || difference.signum() == 0) {
            return false;
        }
        BigDecimal pct = BigDecimal.ZERO;
        BigDecimal abs = BigDecimal.ZERO;
        if (tenantId != null) {
            var config = toleranceMapper.tolerance(tenantId);
            if (config != null) {
                if (config.get("pctTolerance") != null) pct = new BigDecimal(config.get("pctTolerance").toString());
                if (config.get("absTolerance") != null) abs = new BigDecimal(config.get("absTolerance").toString());
            }
        }
        BigDecimal ceiling = sourceAmount.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                .max(abs);
        return difference.abs().compareTo(ceiling) > 0;
    }

    private void saveLines(BizPurchaseSettlement s) {
        if (!"LINES".equals(s.getSettlementMode())) return;
        sourceMapper.deleteLines(s.getId());
        for (var line : s.getDetails()) {
            line.put("id", com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            line.put("tenantId", s.getTenantId()); line.put("projectId", s.getProjectId()); line.put("settlementId", s.getId());
            sourceMapper.insertLine(line);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(BizPurchaseSettlement settlement) {

        BizPurchaseSettlement existing = settlementMapper.selectById(settlement.getId());
        if (existing == null) {
            throw new BusinessException("采购结算不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        sourceMapper.lockInbound(existing.getInboundId());
        existing = settlementMapper.lockById(existing.getId());
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException(409, "单据状态已变更");
        if ("LINES".equals(existing.getSettlementMode())) {
            existing.setDetails(settlement.getDetails()); calculateLines(existing, existing.getId());
            settlement.setSettlementAmount(existing.getSettlementAmount());
        } else if (settlement.getDetails() != null) throw new BusinessException(409, "旧整单不可改为分批");
        if (settlement.getSettlementAmount() == null || settlement.getSettlementAmount().signum() <= 0) throw new BusinessException("结算金额必须大于0");
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
        saveLines(existing);
    }

    /**
     * 删除采购结算
     * <p>对称回滚：APPROVED 单据曾由 {@link #submit(Long)} 回写合同累计结算，
     * 删除必须同时冲销；DRAFT 从未回写，不得冲销（否则把账做负）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizPurchaseSettlement existing = settlementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("采购结算不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        BigDecimal amount = existing.getSettlementAmount() == null
                ? BigDecimal.ZERO : existing.getSettlementAmount();
        sourceMapper.lockInbound(existing.getInboundId());
        existing = settlementMapper.lockById(id);
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) throw new BusinessException(409, "单据状态已变更");
        sourceMapper.deleteLines(id);
        settlementMapper.deleteById(id);

        if ("APPROVED".equals(existing.getStatus())) {
            contractMapper.addSettlement(existing.getContractId(), amount.negate());
            // 应付台账对称冲减（V2026_115）
            com.zwinsight.common.event.finance.ExpenseSettlementPayableEvent reversal =
                    new com.zwinsight.common.event.finance.ExpenseSettlementPayableEvent();
            reversal.setSourceType("PURCHASE_SETTLEMENT");
            reversal.setSourceId(existing.getId());
            reversal.setProjectId(existing.getProjectId());
            reversal.setContractId(existing.getContractId());
            reversal.setContractCategory("PURCHASE");
            reversal.setAmount(amount);
            reversal.setReversed(true);
            eventPublisher.publishEvent(reversal);
            log.info("采购结算删除并冲销累计值, id={}, amount={}", id, amount);
        }
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

        sourceMapper.lockInbound(settlement.getInboundId());
        settlement = settlementMapper.lockById(id);
        if (!"DRAFT".equals(settlement.getStatus())) throw new BusinessException(409, "单据已提交");
        checkMode(settlement, id);
        if ("LINES".equals(settlement.getSettlementMode())) {
            settlement.setDetails(sourceMapper.settlementLines(id)); calculateLines(settlement, id);
        }
        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("settlementAmount", settlement.getSettlementAmount());
        variables.put("projectId", settlement.getProjectId());
        variables.put("contractId", settlement.getContractId());
        String processInstanceId = approvalService.startProcess(
                "PURCHASE_SETTLEMENT", id, "purchase_settlement_approval", variables);

        settlement.setWorkflowInstanceId(processInstanceId);
        // PI-3: 提交仅进入审批中状态（SUBMITTED），审批通过后再置 APPROVED 并原子累加合同累计结算
        settlement.setStatus("SUBMITTED");
        settlementMapper.updateById(settlement);
    }

    /**
     * 审批通过回调（幂等：仅 SUBMITTED 态可生效，原子累加合同累计结算，PI-2/PI-3）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizPurchaseSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            log.warn("采购结算审批通过回调：单据不存在, id={}", id);
            return;
        }
        sourceMapper.lockInbound(settlement.getInboundId());
        settlement = settlementMapper.lockById(id);
        if ("APPROVED".equals(settlement.getStatus())) {
            log.info("采购结算已生效，跳过重复回调, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(settlement.getStatus())) {
            log.warn("采购结算当前状态非 SUBMITTED，忽略生效回调: id={}, status={}", id, settlement.getStatus());
            return;
        }

        // C4（V2026_111）：生效前第三处重算——存储明细按最新来源单价与容差复核，
        // 超容差而无原因的存量/篡改数据在此拦截，不生效。
        if ("LINES".equals(settlement.getSettlementMode())) {
            settlement.setDetails(sourceMapper.settlementLines(id));
            calculateLines(settlement, id);
        }

        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // PI-2: 改用原子 addSettlement SQL 累加，杜绝读改写并发丢失
        if (settlement.getContractId() != null && settlement.getSettlementAmount() != null) {
            contractMapper.addSettlement(settlement.getContractId(), settlement.getSettlementAmount());
            // 应付台账（V2026_115）：结算生效生成应付行，事件消费方 zw-finance 同事务内执行
            com.zwinsight.common.event.finance.ExpenseSettlementPayableEvent payableEvent =
                    new com.zwinsight.common.event.finance.ExpenseSettlementPayableEvent();
            payableEvent.setSourceType("PURCHASE_SETTLEMENT");
            payableEvent.setSourceId(settlement.getId());
            payableEvent.setProjectId(settlement.getProjectId());
            payableEvent.setContractId(settlement.getContractId());
            payableEvent.setContractCategory("PURCHASE");
            payableEvent.setAmount(settlement.getSettlementAmount());
            payableEvent.setRemark("采购结算单 " + settlement.getSettlementNo() + " 审批通过生成");
            eventPublisher.publishEvent(payableEvent);
            log.info("采购结算审批通过并原子回写合同结算额: settlementId={}, contractId={}, amount={}",
                    id, settlement.getContractId(), settlement.getSettlementAmount());
        }
    }

    /**
     * 审批退回保留在途占用；撤回/终止才由 SUBMITTED 回退 DRAFT
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        // REJECT means a live process moved to an earlier node; keep SUBMITTED and its reservation.
        onRejected(id, "REJECT");
    }

    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id, String rejectType) {
        if (!java.util.Set.of("WITHDRAW", "TERMINATE").contains(rejectType == null ? "" : rejectType)) return;
        BizPurchaseSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) return;
        sourceMapper.lockInbound(settlement.getInboundId());
        settlement = settlementMapper.lockById(id);
        if (settlement == null || !"SUBMITTED".equals(settlement.getStatus())) return;
        settlement.setStatus("DRAFT");
        settlementMapper.updateById(settlement);
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
