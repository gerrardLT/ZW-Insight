package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizBankFlow;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.SysAmountTierConfig;
import com.zwinsight.finance.dto.BatchOperationRequest;
import com.zwinsight.finance.dto.ContractPayableInfo;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
    private final BizBankFlowMapper bankFlowMapper;
    private final BizOtherContractMapper otherContractMapper;
    private final ContractPayableMapper contractPayableMapper;
    private final BizProjectMapper projectMapper;
    private final SettlementDataMapper settlementDataMapper;
    private final ApprovalService approvalService;
    private final ApplicationEventPublisher eventPublisher;
    private final FundCategoryService fundCategoryService;
    private final FundPlanService fundPlanService;
    private final AmountTierService amountTierService;

    /** 走各模块合同表（biz_purchase_contract 等）的合同类型；其余（OTHER_EXPENSE/OTHER_INCOME/空）走 biz_other_contract */
    private static final java.util.Set<String> MODULE_CATEGORIES =
            java.util.Set.of("PURCHASE", "LABOR", "MACHINE", "SUBCONTRACT");

    /**
     * 分页查询（payStatus 筛选支持「已批未付」清单，V2026_56）
     */
    public PageResult<BizPaymentApply> page(int page, int size, Long projectId, Long contractId,
                                            String status, String payStatus) {
        Page<BizPaymentApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPaymentApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPaymentApply::getProjectId, projectId)
                .eq(contractId != null, BizPaymentApply::getContractId, contractId)
                .eq(StrUtil.isNotBlank(status), BizPaymentApply::getStatus, status)
                .eq(StrUtil.isNotBlank(payStatus), BizPaymentApply::getPayStatus, payStatus)
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
        // 资金分类校验（53_V2026_51）：提供了科目编码时必须有效且为支出向（不静默丢弃）
        if (paymentApply.getPaymentCategory() != null && !paymentApply.getPaymentCategory().isBlank()) {
            fundCategoryService.getByCode(paymentApply.getPaymentCategory(), "EXPENSE");
        }
        // 先计划后支付（53_V2026_51）：付款日期落月存在 APPROVED 月度计划且超计划时拦截
        fundPlanService.validatePaymentAgainstPlan(
                paymentApply.getProjectId(), paymentApply.getPaymentDate(), paymentApply.getPaymentAmount());
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
     * <p>对称回滚（参照 {@code PaymentReceivedService.delete}）：APPROVED 单据曾由
     * {@link #onApproved(Long)} 回写合同累计已付与项目总支出，删除必须同时冲销，
     * 否则单据没了而账还在（线上取证：91801 的 +250 即此类残留）。</p>
     * <p><b>仅 APPROVED 才冲销</b>：DRAFT/SUBMITTED/REJECTED 从未回写过
     * （submit 只置状态，onRejected 不回写），冲销它们会把账做负。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizPaymentApply existing = paymentApplyMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        BigDecimal amount = existing.getPaymentAmount() == null
                ? BigDecimal.ZERO : existing.getPaymentAmount();
        paymentApplyMapper.deleteById(id);

        if ("APPROVED".equals(existing.getStatus())) {
            addCumulativePaid(existing, amount.negate());
            projectMapper.addTotalExpense(existing.getProjectId(), amount.negate());
            log.info("付款申请删除并冲销累计值, id={}, amount={}", id, amount);
        }
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

        // 提交时点回填可付快照（详情抽屉「累计结算快照/未付金额快照」数据源，提交后不再变化）：
        // 累计结算快照取合同原值；未付金额快照 = 可付余额，与 validatePaymentLimit 同口径（含净奖惩）。
        // 驳回重提时刷新为最新时点值。字段在 save/update（草稿态）不回填。
        BigDecimal cumulativeSettlement = payable.getCumulativeSettlement() == null
                ? BigDecimal.ZERO : payable.getCumulativeSettlement();
        BigDecimal cumulativePaid = payable.getCumulativePaid() == null
                ? BigDecimal.ZERO : payable.getCumulativePaid();
        BigDecimal rewardPunishNet = settlementDataMapper.sumRewardPunishNetByContract(paymentApply.getContractId());
        if (rewardPunishNet == null) {
            rewardPunishNet = BigDecimal.ZERO;
        }
        paymentApply.setCumulativeSettlementSnapshot(cumulativeSettlement);
        paymentApply.setUnpaidAmountSnapshot(cumulativeSettlement.add(rewardPunishNet).subtract(cumulativePaid));

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentAmount", paymentApply.getPaymentAmount());
        variables.put("contractId", paymentApply.getContractId());
        // 金额分级审批（54_V2026_52）：匹配档位并将等级传入流程变量，
        // 由 payment_apply_approval 的 BPMN 排他网关按 approvalTier 路由到对应审批节点。
        // approvalTier 恒写入（未命中档位=0，网关走 default 流），避免条件表达式引用未定义变量报错；
        // 注意：这只是流程路由变量，不伪造 sys_amount_tier_config 配置档（tierName 仅在命中时写入）。
        SysAmountTierConfig tier = amountTierService.matchTier(
                SysAmountTierConfig.MODULE_PAYMENT_APPLY, paymentApply.getPaymentAmount());
        variables.put("approvalTier", tier != null ? tier.getTierLevel() : 0);
        if (tier != null) {
            variables.put("tierName", tier.getTierName());
            log.info("付款申请命中审批档位, id={}, amount={}, tier={}, {}",
                    id, paymentApply.getPaymentAmount(), tier.getTierLevel(), tier.getTierName());
        }
        String processInstanceId = approvalService.startProcess(
                "PAYMENT_APPLY", id, "payment_apply_approval", variables);

        paymentApply.setWorkflowInstanceId(processInstanceId);
        paymentApply.setStatus("SUBMITTED");
        paymentApplyMapper.updateById(paymentApply);
    }

    /**
     * 批量操作（统一入口，整体单事务，任一失败回滚全部）。
     * <p>语义与单条同源：逐条走单条校验（仅 DRAFT 可删；仅 DRAFT/REJECTED 可提交），
     * 失败时抛出带单据上下文的 BusinessException，由事务回滚保证不产生半途状态。</p>
     *
     * @param request ids 非空；action：delete / submit
     * @return 成功处理的条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batch(BatchOperationRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new BusinessException("ID 列表不能为空");
        }
        String action = request.getAction() == null ? "" : request.getAction().trim();
        int count = 0;
        for (Long id : request.getIds()) {
            try {
                switch (action) {
                    case "delete" -> delete(id);
                    case "submit" -> submit(id);
                    default -> throw new BusinessException("不支持的批量操作类型：" + action);
                }
            } catch (BusinessException e) {
                throw new BusinessException("批量" + ("delete".equals(action) ? "删除" : "提交")
                        + "中断（单据 ID=" + id + "）：" + e.getMessage());
            }
            count++;
        }
        log.info("付款申请批量{}成功，count={}", "delete".equals(action) ? "删除" : "提交", count);
        return count;
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
     * 支付执行态刷新（V2026_56，由 {@link BankFlowService#matchFlow}/{@link BankFlowService#unmatchFlow} 勾稽变更后调用）。
     * <p>口径不变量：total_expense 仍为<b>审批口径</b>（仅 {@link #onApproved(Long)} 回写）；
     * pay_status 为<b>现金口径</b>的增量语义，本方法不回写项目账/合同累计已付，
     * 不影响 R7 审计基线与 52_V2026_50 勾稽种子。</p>
     * <p>判定规则（以已勾稽银行流水为唯一事实源，重算幂等）：
     * 勾稽金额合计（match_amount 空则取流水 amount）≥ 付款金额 → PAID；
     * 0 &lt; 勾稽合计 &lt; 付款金额 → <b>PARTIAL_PAID</b>（V2026_64，资金流转 §8「07 部分付款」）；
     * 勾稽合计 = 0 → UNPAID。
     * pay_date/pay_account_id 只要存在勾稽即记首笔（现金确已流出，非足额也如实记录）。
     * 非 APPROVED 单据不处理（未生效单据无支付语义）。</p>
     *
     * @param id 付款申请ID（不存在时抛 BusinessException，不静默跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshPayStatus(Long id) {
        BizPaymentApply apply = paymentApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!"APPROVED".equals(apply.getStatus())) {
            return;
        }
        List<BizBankFlow> flows = bankFlowMapper.selectList(
                new LambdaQueryWrapper<BizBankFlow>()
                        .eq(BizBankFlow::getReconciled, 1)
                        .eq(BizBankFlow::getMatchedType, BizBankFlow.MATCH_PAYMENT_APPLY)
                        .eq(BizBankFlow::getMatchedId, id)
                        .orderByAsc(BizBankFlow::getFlowDate));
        BigDecimal matchedTotal = BigDecimal.ZERO;
        LocalDate firstFlowDate = null;
        Long firstAccountId = null;
        for (BizBankFlow flow : flows) {
            BigDecimal amount = flow.getMatchAmount() != null ? flow.getMatchAmount() : flow.getAmount();
            if (amount == null) {
                continue;
            }
            matchedTotal = matchedTotal.add(amount);
            if (firstFlowDate == null) {
                firstFlowDate = flow.getFlowDate();
                firstAccountId = flow.getAccountId();
            }
        }
        BigDecimal paymentAmount = apply.getPaymentAmount() == null ? BigDecimal.ZERO : apply.getPaymentAmount();
        // 三档判定（V2026_64）：足额→PAID；部分→PARTIAL_PAID；无勾稽→UNPAID
        // paymentAmount.signum() > 0 保护：金额为 0 的异常单据不得因“0 ≥ 0”被误判为已付
        boolean paid = paymentAmount.signum() > 0 && matchedTotal.compareTo(paymentAmount) >= 0;
        boolean partial = !paid && matchedTotal.signum() > 0;
        String newStatus = paid ? BizPaymentApply.PAY_STATUS_PAID
                : (partial ? BizPaymentApply.PAY_STATUS_PARTIAL : BizPaymentApply.PAY_STATUS_UNPAID);
        boolean anyMatched = matchedTotal.signum() > 0;
        apply.setPayStatus(newStatus);
        // 只要有勾稽就记首笔支付日/账户（部分支付也确实现金流出）；全部取消勾稽时置空
        apply.setPayDate(anyMatched ? firstFlowDate : null);
        apply.setPayAccountId(anyMatched ? firstAccountId : null);
        paymentApplyMapper.updateById(apply);
        log.info("付款申请支付态刷新, id={}, payStatus={}, 勾稽合计={}, 付款金额={}, 剩余未付={}",
                id, newStatus, matchedTotal, paymentAmount, paymentAmount.subtract(matchedTotal).max(BigDecimal.ZERO));
    }

    /**
     * 手工标记已支付（无网银流水导入场景的备选路径，与流水勾稽同一状态字段）。
     * <p>仅 APPROVED 且 UNPAID 可标记；若已存在勾稽流水则拒绝手工标记
     * （流水为唯一事实源，防双轨冲突）。口径不变量同 {@link #refreshPayStatus(Long)}：
     * 不回写项目账/合同累计已付。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Long id, LocalDate payDate, Long payAccountId) {
        BizPaymentApply apply = paymentApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!"APPROVED".equals(apply.getStatus())) {
            throw new BusinessException("仅审批通过的付款申请可标记支付，当前状态：" + apply.getStatus());
        }
        if (BizPaymentApply.PAY_STATUS_PAID.equals(apply.getPayStatus())) {
            throw new BusinessException("该付款申请已标记支付，不可重复标记");
        }
        if (BizPaymentApply.PAY_STATUS_PARTIAL.equals(apply.getPayStatus())) {
            // 部分支付必然源自银行勾稽：手工整笔标记会掩盖“还差多少未付”的事实
            throw new BusinessException("该付款申请已部分支付（银行勾稽产生），请继续勾稽剩余金额或取消勾稽，不可手工整笔标记");
        }
        Long matchedFlows = bankFlowMapper.selectCount(new LambdaQueryWrapper<BizBankFlow>()
                .eq(BizBankFlow::getReconciled, 1)
                .eq(BizBankFlow::getMatchedType, BizBankFlow.MATCH_PAYMENT_APPLY)
                .eq(BizBankFlow::getMatchedId, id));
        if (matchedFlows != null && matchedFlows > 0) {
            throw new BusinessException("该付款申请已有银行流水勾稽，支付态以流水为准，不可手工标记");
        }
        if (payDate == null) {
            throw new BusinessException("支付日期不能为空");
        }
        apply.setPayStatus(BizPaymentApply.PAY_STATUS_PAID);
        apply.setPayDate(payDate);
        apply.setPayAccountId(payAccountId);
        paymentApplyMapper.updateById(apply);
        log.info("付款申请手工标记已支付, id={}, payDate={}, accountId={}", id, payDate, payAccountId);
    }

    /**
     * 撤销手工支付标记（仅无勾稽流水的手工标记可撤；有流水时须走取消勾稽联动）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokePaid(Long id) {
        BizPaymentApply apply = paymentApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!BizPaymentApply.PAY_STATUS_PAID.equals(apply.getPayStatus())) {
            if (BizPaymentApply.PAY_STATUS_PARTIAL.equals(apply.getPayStatus())) {
                throw new BusinessException("部分支付状态由银行流水勾稽产生，请通过取消勾稽调整，不可手工撤销");
            }
            throw new BusinessException("该付款申请未标记支付，无需撤销");
        }
        Long matchedFlows = bankFlowMapper.selectCount(new LambdaQueryWrapper<BizBankFlow>()
                .eq(BizBankFlow::getReconciled, 1)
                .eq(BizBankFlow::getMatchedType, BizBankFlow.MATCH_PAYMENT_APPLY)
                .eq(BizBankFlow::getMatchedId, id));
        if (matchedFlows != null && matchedFlows > 0) {
            throw new BusinessException("支付态由银行流水勾稽产生，请通过取消勾稽撤销");
        }
        apply.setPayStatus(BizPaymentApply.PAY_STATUS_UNPAID);
        apply.setPayDate(null);
        apply.setPayAccountId(null);
        paymentApplyMapper.updateById(apply);
        log.info("付款申请撤销手工支付标记, id={}", id);
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
            // 事件携带当前租户：催办监听器为 @Async，不传则站内消息被写防护拒绝
            eventPublisher.publishEvent(new UrgeNotifyEvent(this, userId, title, content,
                    workflowInstanceId, null, SecurityContextHolder.getTenantId()));
        } catch (Exception e) {
            log.error("发送付款申请通知失败, workflowInstanceId={}", workflowInstanceId, e);
        }
    }
}
