package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.event.contract.ProcessSettlementApprovedEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.domain.BizSettlementSource;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizFinalSettlementMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.contract.mapper.BizSettlementSourceMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 合同结算服务（2026-10-16 扩展过程结算，财建〔2022〕183 号过程结算规则）
 * <p>
 * PROCESS：按时间/进度节点对周期内已完成且无争议的工程量（含变更、签证）确认价款。
 * 来源（已批产值/已批签证/手工行）三级金额（申报/审定/争议）落 {@code biz_settlement_source}；
 * 审批通过原子回写合同 {@code cumulative_settlement} 并发布
 * {@link ProcessSettlementApprovedEvent} 生成应收；驳回/撤回自动释放来源占用。
 * FINAL：原竣工结算语义不变，另回写合同累计结算。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinalSettlementService {

    private final BizFinalSettlementMapper settlementMapper;
    private final BizSettlementSourceMapper sourceMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizOutputReportMapper outputReportMapper;
    private final BizChangeVisaMapper changeVisaMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询（支持按合同与类型筛选）
     */
    public PageResult<BizFinalSettlement> page(int page, int size, Long projectId, Long contractId, String settlementType) {
        Page<BizFinalSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFinalSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizFinalSettlement::getProjectId, projectId)
                .eq(contractId != null, BizFinalSettlement::getContractId, contractId)
                .eq(settlementType != null && !settlementType.isBlank(), BizFinalSettlement::getSettlementType, settlementType)
                .orderByDesc(BizFinalSettlement::getCreatedAt);
        Page<BizFinalSettlement> result = settlementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存结算（草稿）。PROCESS 须带来源明细并做归属/占用校验；FINAL 兼容旧入口（无来源）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizFinalSettlement settlement) {
        normalizeType(settlement);
        if (BizFinalSettlement.TYPE_PROCESS.equals(settlement.getSettlementType())) {
            validateAndRecompute(settlement, null);
        }
        // FINAL 兼容旧入口：草稿不强制金额（提交时统一校验），不引入来源
        settlement.setStatus("DRAFT");
        // 信任边界：流程实例由系统写入；申报/争议合计由服务端重算，不接受请求体
        settlement.setWorkflowInstanceId(null);
        settlementMapper.insert(settlement);
        saveSources(settlement);
    }

    /**
     * 提交审批：DRAFT→SUBMITTED，仅启动流程；金额回写由审批通过回调生效。
     * PROCESS 提交时重新校验来源归属与占用（草稿期来源可能被其他结算先行占用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizFinalSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        if (!"DRAFT".equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        normalizeType(settlement);
        if (settlement.getSettlementAmount() == null || settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("结算金额必须大于0");
        }
        BizConstructionContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract == null) {
            throw new BusinessException("关联的施工合同不存在");
        }
        if (!Objects.equals(contract.getProjectId(), settlement.getProjectId())) {
            throw new BusinessException("施工合同不属于该项目");
        }
        // D2 守卫：仅生效/已结算合同可结算（PROCESS 允许 SETTLED 合同补结尾期）
        if (!"EFFECTIVE".equals(contract.getStatus()) && !"SETTLED".equals(contract.getStatus())) {
            throw new BusinessException("仅生效状态的合同可进行结算，当前合同状态：" + contract.getStatus());
        }
        if (projectMapper.selectById(settlement.getProjectId()) == null) {
            throw new BusinessException("关联项目不存在");
        }
        if (BizFinalSettlement.TYPE_PROCESS.equals(settlement.getSettlementType())) {
            // 草稿期来源可能被其他结算先行占用：提交前重新校验并重算合计（不重插来源行）
            List<BizSettlementSource> sources = listSources(id);
            settlement.setSources(sources);
            validateAndRecompute(settlement, id);
            settlementMapper.updateById(settlement);
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("settlementAmount", settlement.getSettlementAmount());
        variables.put("projectId", settlement.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "FINAL_SETTLEMENT", id, "final_settlement_approval", variables);

        settlement.setWorkflowInstanceId(processInstanceId);
        settlement.setStatus("SUBMITTED");
        settlementMapper.updateById(settlement);
    }

    /**
     * 审批通过回调：状态 CAS 后按类型生效。任一步失败抛出，由审批引擎事务整体回滚。
     * <ul>
     *   <li>PROCESS：占用终检 → 原子回写合同累计结算（含上限守卫）→ 发布应收事件</li>
     *   <li>FINAL：合同置 SETTLED + 项目结算额累加（原行为）→ 合同累计结算回写</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizFinalSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        if ("APPROVED".equals(settlement.getStatus())) {
            return; // 重复回调幂等
        }
        // 允许 SUBMITTED，以及"被退回但流程仍在走"的 DRAFT：reject-start 不终止流程。
        if (!"SUBMITTED".equals(settlement.getStatus()) && !"DRAFT".equals(settlement.getStatus())) {
            throw new BusinessException("结算单状态异常，无法生效：" + settlement.getStatus());
        }
        normalizeType(settlement);
        boolean process = BizFinalSettlement.TYPE_PROCESS.equals(settlement.getSettlementType());
        if (process) {
            // 生效前占用终检（提交后来源可能被并发结算占用）
            List<BizSettlementSource> sources = listSources(id);
            settlement.setSources(sources);
            validateAndRecompute(settlement, id);
        }

        int moved = settlementMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFinalSettlement>()
                .eq(BizFinalSettlement::getId, id)
                .in(BizFinalSettlement::getStatus, "SUBMITTED", "DRAFT")
                .set(BizFinalSettlement::getStatus, "APPROVED")
                .set(BizFinalSettlement::getSettlementAmount, settlement.getSettlementAmount())
                .set(BizFinalSettlement::getDeclaredAmount, settlement.getDeclaredAmount())
                .set(BizFinalSettlement::getDisputedAmount, settlement.getDisputedAmount()));
        if (moved != 1) {
            return; // 并发回调已处理
        }

        // 合同累计结算原子回写（带上限：≤ 合同额 + 累计变更）
        if (contractMapper.addCumulativeSettlement(settlement.getContractId(), settlement.getSettlementAmount()) != 1) {
            throw new BusinessException("合同累计结算回写失败：超出合同额（含变更）或合同已不存在，结算未生效");
        }

        if (process) {
            ProcessSettlementApprovedEvent event = new ProcessSettlementApprovedEvent();
            event.setSettlementId(id);
            event.setProjectId(settlement.getProjectId());
            event.setContractId(settlement.getContractId());
            event.setNetAmount(settlement.getSettlementAmount());
            event.setPeriodEnd(settlement.getPeriodEnd());
            event.setSettlementDate(settlement.getSettlementDate());
            // 同步事件：在本次事务内生成应收，失败整体回滚
            eventPublisher.publishEvent(event);
            log.info("过程结算审批通过并生效, id={}, contractId={}, netAmount={}",
                    id, settlement.getContractId(), settlement.getSettlementAmount());
            return;
        }

        // FINAL 原行为：合同置 SETTLED + 项目结算额累加
        BizConstructionContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract == null) {
            throw new BusinessException("关联的施工合同不存在，竣工结算无法生效");
        }
        if (!"EFFECTIVE".equals(contract.getStatus()) && !"SETTLED".equals(contract.getStatus())) {
            throw new BusinessException("合同状态已变更为 " + contract.getStatus() + "，竣工结算无法生效");
        }
        contract.setStatus("SETTLED");
        // 乐观锁冲突返回 0：合同未真正置 SETTLED 时不能继续累加项目结算额（避免半成品）
        if (contractMapper.updateById(contract) != 1) {
            throw new BusinessException("施工合同已被他人修改，竣工结算无法生效");
        }
        if (projectMapper.addSettlementAmount(settlement.getProjectId(), settlement.getSettlementAmount()) != 1) {
            throw new BusinessException("关联项目不存在，竣工结算无法生效");
        }
    }

    /** 审批驳回/撤回回调：SUBMITTED→DRAFT（数据未生效，无需回冲；来源占用随状态退出自动释放） */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        settlementMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFinalSettlement>()
                .eq(BizFinalSettlement::getId, id)
                .eq(BizFinalSettlement::getStatus, "SUBMITTED")
                .set(BizFinalSettlement::getStatus, "DRAFT"));
    }

    /**
     * 查询结算来源明细
     */
    public List<BizSettlementSource> listSources(Long settlementId) {
        return sourceMapper.selectList(new LambdaQueryWrapper<BizSettlementSource>()
                .eq(BizSettlementSource::getSettlementId, settlementId)
                .orderByAsc(BizSettlementSource::getId));
    }

    /**
     * 过程结算可选来源：本合同下已审批产值报告与已审批签证，剔除已被有效结算占用的。
     * MANUAL 行由前端手工录入，不在候选清单内。
     */
    public Map<String, List<Map<String, Object>>> listAvailableSources(Long contractId) {
        List<Map<String, Object>> reports = outputReportMapper.selectList(
                        new LambdaQueryWrapper<BizOutputReport>()
                                .eq(BizOutputReport::getContractId, contractId)
                                .eq(BizOutputReport::getStatus, "APPROVED")
                                .orderByDesc(BizOutputReport::getCreatedAt))
                .stream().map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("sourceId", r.getId());
                    m.put("label", "产值报告 " + r.getReportPeriod() + "（本期 " + r.getCurrentOutput() + "）");
                    m.put("suggestedAmount", r.getCurrentOutput());
                    return m;
                }).toList();
        List<Map<String, Object>> visas = changeVisaMapper.selectList(
                        new LambdaQueryWrapper<BizChangeVisa>()
                                .eq(BizChangeVisa::getContractId, contractId)
                                .eq(BizChangeVisa::getStatus, "APPROVED")
                                .orderByDesc(BizChangeVisa::getCreatedAt))
                .stream().filter(v -> sourceMapper.countSourceOccupied(
                        BizSettlementSource.TYPE_VISA, v.getId(), -1L) == 0)
                .map(v -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("sourceId", v.getId());
                    m.put("label", "签证 " + v.getChangeType() + "（" + v.getChangeAmount() + "）");
                    m.put("suggestedAmount", v.getChangeAmount());
                    return m;
                }).toList();
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        result.put("outputReports", reports.stream().filter(r -> sourceMapper.countSourceOccupied(
                BizSettlementSource.TYPE_OUTPUT_REPORT, (Long) r.get("sourceId"), -1L) == 0).toList());
        result.put("visas", visas);
        return result;
    }

    /** 详情（含来源明细） */
    public BizFinalSettlement getDetail(Long id) {
        BizFinalSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        normalizeType(settlement);
        settlement.setSources(listSources(id));
        return settlement;
    }

    // ==================== 私有方法 ====================

    private void normalizeType(BizFinalSettlement settlement) {
        if (settlement.getSettlementType() == null || settlement.getSettlementType().isBlank()) {
            settlement.setSettlementType(BizFinalSettlement.TYPE_FINAL);
        }
    }

    /**
     * PROCESS 校验与重算：来源非空、归属正确（产值/签证须已审批且属于本合同）、
     * 未被其他有效结算占用；申报/争议合计与净额由服务端重算，不信任请求体。
     */
    private void validateAndRecompute(BizFinalSettlement settlement, Long excludeSettlementId) {
        List<BizSettlementSource> sources = settlement.getSources();
        if (sources == null || sources.isEmpty()) {
            throw new BusinessException(400, "过程结算必须包含至少一条来源明细（产值/签证/手工行）");
        }
        if (settlement.getPeriodStart() == null || settlement.getPeriodEnd() == null) {
            throw new BusinessException(400, "过程结算必须填写结算期间（起止日期）");
        }
        if (settlement.getPeriodEnd().isBefore(settlement.getPeriodStart())) {
            throw new BusinessException(400, "结算期间止不能早于起");
        }
        BigDecimal declared = BigDecimal.ZERO;
        BigDecimal disputed = BigDecimal.ZERO;
        BigDecimal confirmed = BigDecimal.ZERO;
        for (BizSettlementSource s : sources) {
            validateSingleSource(s, settlement, excludeSettlementId);
            declared = declared.add(nvl(s.getDeclaredAmount()));
            disputed = disputed.add(nvl(s.getDisputedAmount()));
            confirmed = confirmed.add(nvl(s.getConfirmedAmount()));
        }
        settlement.setDeclaredAmount(declared);
        settlement.setDisputedAmount(disputed);
        BigDecimal deduct = nvl(settlement.getPrepayDeduct()).add(nvl(settlement.getRetentionDeduct()))
                .add(nvl(settlement.getOtherDeduct()));
        if (deduct.signum() < 0) {
            throw new BusinessException(400, "扣款金额不能为负");
        }
        BigDecimal net = confirmed.subtract(deduct);
        if (net.signum() <= 0) {
            throw new BusinessException(400, "审定净额必须大于0（审定合计需大于扣款合计）");
        }
        settlement.setSettlementAmount(net);
    }

    private void validateSingleSource(BizSettlementSource s, BizFinalSettlement settlement, Long excludeId) {
        if (s.getSourceType() == null) {
            throw new BusinessException(400, "来源明细缺少类型");
        }
        BigDecimal declared = nvl(s.getDeclaredAmount());
        BigDecimal confirmed = nvl(s.getConfirmedAmount());
        BigDecimal disputed = nvl(s.getDisputedAmount());
        if (declared.signum() < 0 || confirmed.signum() < 0 || disputed.signum() < 0) {
            throw new BusinessException(400, "来源明细金额不能为负");
        }
        if (confirmed.add(disputed).compareTo(declared) > 0) {
            throw new BusinessException(400, "审定+争议不能超过申报金额");
        }
        if (confirmed.compareTo(declared) < 0 && (s.getReduceReason() == null || s.getReduceReason().isBlank())) {
            throw new BusinessException(400, "审定低于申报须填写核减原因");
        }
        switch (s.getSourceType()) {
            case BizSettlementSource.TYPE_OUTPUT_REPORT -> {
                if (s.getSourceId() == null) {
                    throw new BusinessException(400, "产值来源必须关联产值报告ID");
                }
                BizOutputReport report = outputReportMapper.selectById(s.getSourceId());
                if (report == null || !"APPROVED".equals(report.getStatus())) {
                    throw new BusinessException(400, "来源产值报告不存在或未审批通过");
                }
                if (!Objects.equals(report.getContractId(), settlement.getContractId())) {
                    throw new BusinessException(400, "来源产值报告不属于本合同");
                }
            }
            case BizSettlementSource.TYPE_VISA -> {
                if (s.getSourceId() == null) {
                    throw new BusinessException(400, "签证来源必须关联签证ID");
                }
                BizChangeVisa visa = changeVisaMapper.selectById(s.getSourceId());
                if (visa == null || !"APPROVED".equals(visa.getStatus())) {
                    throw new BusinessException(400, "来源签证不存在或未审批通过");
                }
                if (!Objects.equals(visa.getContractId(), settlement.getContractId())) {
                    throw new BusinessException(400, "来源签证不属于本合同");
                }
            }
            case BizSettlementSource.TYPE_MANUAL -> {
                if (s.getDescription() == null || s.getDescription().isBlank()) {
                    throw new BusinessException(400, "手工来源行必须填写描述（索赔/奖罚等）");
                }
            }
            default -> throw new BusinessException(400, "未知的来源类型：" + s.getSourceType());
        }
        if (s.getSourceId() != null) {
            int occupied = sourceMapper.countSourceOccupied(s.getSourceType(), s.getSourceId(), excludeId == null ? -1L : excludeId);
            if (occupied > 0) {
                throw new BusinessException(409, "来源已被其他审批中/已生效的结算占用：" + s.getSourceType() + "#" + s.getSourceId());
            }
        }
    }

    private void saveSources(BizFinalSettlement settlement) {
        if (settlement.getSources() == null || settlement.getSources().isEmpty()) {
            return;
        }
        for (BizSettlementSource s : settlement.getSources()) {
            s.setId(null);
            s.setTenantId(settlement.getTenantId());
            s.setProjectId(settlement.getProjectId());
            s.setSettlementId(settlement.getId());
            sourceMapper.insert(s);
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
