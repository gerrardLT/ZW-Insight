package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.dto.ContractPayableInfo;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.ContractPayableMapper;
import com.zwinsight.finance.mapper.SettlementDataMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 付款申请服务
 * <p>
 * 审批后生效模式：submit 仅校验并启动流程（状态 SUBMITTED），
 * 审批通过后由 PaymentApplyApprovalListener 回调 {@link #onApproved(Long)} 回写合同已付与项目支出。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplyService {

    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizOtherContractMapper otherContractMapper;
    private final ContractPayableMapper contractPayableMapper;
    private final BizProjectMapper projectMapper;
    private final SettlementDataMapper settlementDataMapper;
    private final ApprovalService approvalService;
    private final ApplicationEventPublisher eventPublisher;

    /** 走各模块合同表（biz_purchase_contract 等）的合同类型；其余（OTHER_EXPENSE/OTHER_INCOME/空）走 biz_other_contract */
    private static final java.util.Set<String> MODULE_CATEGORIES =
            java.util.Set.of("PURCHASE", "LABOR", "MACHINE", "SUBCONTRACT");

    /**
     * 分页查询
     */
    public PageResult<BizPaymentApply> page(int page, int size, Long projectId, Long contractId, String status) {
        Page<BizPaymentApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPaymentApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPaymentApply::getProjectId, projectId)
                .eq(contractId != null, BizPaymentApply::getContractId, contractId)
                .eq(StrUtil.isNotBlank(status), BizPaymentApply::getStatus, status)
                .orderByDesc(BizPaymentApply::getCreatedAt);
        Page<BizPaymentApply> result = paymentApplyMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizPaymentApply::getProjectId, BizPaymentApply::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 新增付款申请
     */
    public void save(BizPaymentApply paymentApply) {
        // 审计缺陷 D3 修复（2026-08-17）：付款金额必须>0，原实现负/零无校验可进审批流
        if (paymentApply.getPaymentAmount() == null || paymentApply.getPaymentAmount().signum() <= 0) {
            throw new BusinessException("付款金额必须大于0");
        }
        paymentApply.setStatus("DRAFT");
        paymentApplyMapper.insert(paymentApply);
    }

    /**
     * 根据ID查询
     */
    public BizPaymentApply getById(Long id) {
        BizPaymentApply paymentApply = paymentApplyMapper.selectById(id);
        if (paymentApply == null) {
            throw new BusinessException("付款申请不存在");
        }
        return paymentApply;
    }

    /**
     * 更新付款申请
     */
    public void update(BizPaymentApply paymentApply) {
        BizPaymentApply existing = paymentApplyMapper.selectById(paymentApply.getId());
        if (existing == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        paymentApplyMapper.updateById(paymentApply);
    }

    /**
     * 删除付款申请
     */
    public void delete(Long id) {
        BizPaymentApply existing = paymentApplyMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        paymentApplyMapper.deleteById(id);
    }

    /**
     * 提交付款申请（校验paymentAmount≤累计结算-已付后启动流程，状态置 SUBMITTED，不回写累计数据）
     */
    @BudgetCheck(category = "")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizPaymentApply paymentApply = paymentApplyMapper.selectById(id);
        if (paymentApply == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!"DRAFT".equals(paymentApply.getStatus()) && !"REJECTED".equals(paymentApply.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
        }

        // 校验付款金额（按合同类型路由到对应合同表）
        ContractPayableInfo payable = resolvePayable(paymentApply);
        if (payable == null) {
            throw new BusinessException("关联合同不存在");
        }
        validatePaymentLimit(payable, paymentApply.getContractId(), paymentApply.getPaymentAmount());

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentAmount", paymentApply.getPaymentAmount());
        variables.put("contractId", paymentApply.getContractId());
        String processInstanceId = approvalService.startProcess(
                "PAYMENT_APPLY", id, "payment_apply_approval", variables);

        paymentApply.setWorkflowInstanceId(processInstanceId);
        paymentApply.setStatus("SUBMITTED");
        paymentApplyMapper.updateById(paymentApply);
    }

    /**
     * 审批通过回调：回写合同累计已付与项目总支出（SQL 原子累加）
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容存量在途单据与重复事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizPaymentApply paymentApply = paymentApplyMapper.selectById(id);
        if (paymentApply == null) {
            log.warn("付款申请审批通过回调：申请不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(paymentApply.getStatus())) {
            log.info("付款申请已生效，跳过重复回调, id={}", id);
            return;
        }

        ContractPayableInfo payable = resolvePayable(paymentApply);
        if (payable == null) {
            log.error("付款申请审批通过回调：关联合同不存在, id={}, contractId={}, category={}",
                    id, paymentApply.getContractId(), paymentApply.getContractCategory());
            return;
        }

        // 审批期间上限可能变化，生效前重新校验；不通过则置 REJECTED 并通知发起人
        try {
            validatePaymentLimit(payable, paymentApply.getContractId(), paymentApply.getPaymentAmount());
        } catch (BusinessException e) {
            paymentApply.setStatus("REJECTED");
            paymentApplyMapper.updateById(paymentApply);
            notifyInitiator(paymentApply.getCreatedBy(), "付款申请生效失败", e.getMessage(), paymentApply.getWorkflowInstanceId());
            log.warn("付款申请生效校验失败已驳回, id={}, reason={}", id, e.getMessage());
            return;
        }

        paymentApply.setStatus("APPROVED");
        paymentApplyMapper.updateById(paymentApply);

        // 回写合同累计已付（按合同类型路由）与项目总支出（原子累加）
        addCumulativePaid(paymentApply, paymentApply.getPaymentAmount());
        projectMapper.addTotalExpense(paymentApply.getProjectId(), paymentApply.getPaymentAmount());

        log.info("付款申请审批通过并生效, id={}, paymentAmount={}", id, paymentApply.getPaymentAmount());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（数据未生效，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizPaymentApply paymentApply = paymentApplyMapper.selectById(id);
        if (paymentApply == null) {
            log.warn("付款申请驳回回调：申请不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(paymentApply.getStatus())) {
            return;
        }
        paymentApply.setStatus("REJECTED");
        paymentApplyMapper.updateById(paymentApply);
        log.info("付款申请审批驳回, id={}", id);
    }

    /**
     * 校验付款金额不超过（累计结算 + 净奖惩）减已付金额
     * <p>奖励增加可付、处罚减少可付（净奖惩：奖励为正、处罚为负）。
     * 净奖惩仅劳务/分包合同适用（sumRewardPunishNetByContract 仅覆盖这两张奖惩表），
     * 其余类型返回 0。</p>
     */
    private void validatePaymentLimit(ContractPayableInfo payable, Long contractId, BigDecimal paymentAmount) {
        BigDecimal cumulativeSettlement = payable.getCumulativeSettlement() == null
                ? BigDecimal.ZERO : payable.getCumulativeSettlement();
        BigDecimal cumulativePaid = payable.getCumulativePaid() == null
                ? BigDecimal.ZERO : payable.getCumulativePaid();
        BigDecimal rewardPunishNet = settlementDataMapper.sumRewardPunishNetByContract(contractId);
        if (rewardPunishNet == null) {
            rewardPunishNet = BigDecimal.ZERO;
        }
        BigDecimal maxPayment = cumulativeSettlement.add(rewardPunishNet).subtract(cumulativePaid);

        if (paymentAmount.compareTo(maxPayment) > 0) {
            throw new BusinessException("付款金额不能超过（累计结算含奖惩）减已付金额，最大可付金额：" + maxPayment);
        }
    }

    /**
     * 按合同类型读取可付信息（累计结算/累计已付）。
     * PURCHASE/LABOR/MACHINE/SUBCONTRACT 路由到各模块合同表；
     * 其余（OTHER_EXPENSE/OTHER_INCOME/空，向后兼容）走 biz_other_contract。
     */
    private ContractPayableInfo resolvePayable(BizPaymentApply paymentApply) {
        String category = paymentApply.getContractCategory();
        Long contractId = paymentApply.getContractId();
        if (category != null && MODULE_CATEGORIES.contains(category)) {
            return switch (category) {
                case "PURCHASE" -> contractPayableMapper.purchasePayable(contractId);
                case "LABOR" -> contractPayableMapper.laborPayable(contractId);
                case "MACHINE" -> contractPayableMapper.machinePayable(contractId);
                case "SUBCONTRACT" -> contractPayableMapper.subcontractPayable(contractId);
                default -> null;
            };
        }
        BizOtherContract other = otherContractMapper.selectById(contractId);
        return other == null ? null
                : new ContractPayableInfo(other.getCumulativeSettlement(), other.getCumulativePaid());
    }

    /**
     * 按合同类型原子累加合同累计已付金额。
     */
    private void addCumulativePaid(BizPaymentApply paymentApply, BigDecimal amount) {
        String category = paymentApply.getContractCategory();
        Long contractId = paymentApply.getContractId();
        if (category != null && MODULE_CATEGORIES.contains(category)) {
            switch (category) {
                case "PURCHASE" -> contractPayableMapper.addPurchasePaid(contractId, amount);
                case "LABOR" -> contractPayableMapper.addLaborPaid(contractId, amount);
                case "MACHINE" -> contractPayableMapper.addMachinePaid(contractId, amount);
                case "SUBCONTRACT" -> contractPayableMapper.addSubcontractPaid(contractId, amount);
                default -> { /* 不可达 */ }
            }
            return;
        }
        otherContractMapper.addCumulativePaid(contractId, amount);
    }

    /**
     * 站内信通知发起人（生效失败等异常场景，不静默）
     */
    private void notifyInitiator(Long userId, String title, String content, String workflowInstanceId) {
        if (userId == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(new UrgeNotifyEvent(this, userId, title, content, workflowInstanceId, null));
        } catch (Exception e) {
            log.error("发送付款申请通知失败, workflowInstanceId={}", workflowInstanceId, e);
        }
    }
}
