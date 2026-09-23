package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.domain.BizBankBalance;
import com.zwinsight.finance.domain.BizBankFlow;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import com.zwinsight.finance.mapper.BizBankBalanceMapper;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 银行流水与余额登记服务（资金日报头寸的数据源）
 * <p>纪律：余额登记与流水均为「资金形态/头寸」维度，不回写项目 total_income/total_expense；
 * 流水导入按 transaction_no 去重（已存在则跳过并返回计数，不静默覆盖）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankFlowService {

    private final BizBankFlowMapper flowMapper;
    private final BizBankBalanceMapper balanceMapper;
    private final BizBankAccountMapper accountMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final PaymentApplyService paymentApplyService;

    // ==================== 余额登记 ====================

    /**
     * 登记/更新某账户某日余额（account+date 唯一，存在即更新，upsert 语义）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordBalance(Long accountId, LocalDate snapshotDate, BigDecimal balance, String remark) {
        requireAccount(accountId);
        if (snapshotDate == null) {
            throw new BusinessException(400, "余额日期不能为空");
        }
        if (balance == null) {
            throw new BusinessException(400, "余额不能为空");
        }
        BizBankBalance existing = balanceMapper.selectOne(new LambdaQueryWrapper<BizBankBalance>()
                .eq(BizBankBalance::getAccountId, accountId)
                .eq(BizBankBalance::getSnapshotDate, snapshotDate));
        if (existing != null) {
            existing.setBalance(balance);
            existing.setSource("MANUAL");
            if (remark != null) {
                existing.setRemark(remark);
            }
            balanceMapper.updateById(existing);
        } else {
            BizBankBalance record = new BizBankBalance();
            record.setAccountId(accountId);
            record.setSnapshotDate(snapshotDate);
            record.setBalance(balance);
            record.setSource("MANUAL");
            record.setRemark(remark);
            balanceMapper.insert(record);
        }
    }

    /**
     * 各账户截至指定日期的最新余额（日报头寸数据源）。
     * 对每个启用账户取 <= asOfDate 的最后一条余额登记；无登记的账户余额计 0 并在结果中体现。
     */
    public List<Map<String, Object>> latestBalances(LocalDate asOfDate) {
        List<BizBankAccount> accounts = accountMapper.selectList(
                new LambdaQueryWrapper<BizBankAccount>().eq(BizBankAccount::getStatus, 1));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizBankAccount account : accounts) {
            BizBankBalance latest = balanceMapper.selectOne(new LambdaQueryWrapper<BizBankBalance>()
                    .eq(BizBankBalance::getAccountId, account.getId())
                    .le(BizBankBalance::getSnapshotDate, asOfDate)
                    .orderByDesc(BizBankBalance::getSnapshotDate)
                    .last("LIMIT 1"));
            Map<String, Object> row = new HashMap<>();
            row.put("accountId", account.getId());
            row.put("accountName", account.getAccountName());
            row.put("accountType", account.getAccountType());
            row.put("bankName", account.getBankName());
            row.put("balance", latest != null ? latest.getBalance() : BigDecimal.ZERO);
            row.put("balanceDate", latest != null ? latest.getSnapshotDate() : null);
            result.add(row);
        }
        return result;
    }

    // ==================== 流水管理 ====================

    /**
     * 分页查询流水
     */
    public PageResult<BizBankFlow> pageFlows(int page, int size, Long accountId, LocalDate start,
                                             LocalDate end, Integer reconciled) {
        Page<BizBankFlow> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBankFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(accountId != null, BizBankFlow::getAccountId, accountId)
                .ge(start != null, BizBankFlow::getFlowDate, start)
                .le(end != null, BizBankFlow::getFlowDate, end)
                .eq(reconciled != null, BizBankFlow::getReconciled, reconciled)
                .orderByDesc(BizBankFlow::getFlowDate);
        return PageResult.of(flowMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 手工登记单笔流水
     */
    public void addFlow(BizBankFlow flow) {
        validateFlow(flow);
        requireAccount(flow.getAccountId());
        flow.setReconciled(0);
        if (flow.getSource() == null) {
            flow.setSource("MANUAL");
        }
        // 手工录入也校验流水号重复
        if (flow.getTransactionNo() != null && !flow.getTransactionNo().isBlank()) {
            Long dup = flowMapper.selectCount(new LambdaQueryWrapper<BizBankFlow>()
                    .eq(BizBankFlow::getAccountId, flow.getAccountId())
                    .eq(BizBankFlow::getTransactionNo, flow.getTransactionNo()));
            if (dup > 0) {
                throw new BusinessException(400, "流水号[" + flow.getTransactionNo() + "]在该账户已存在");
            }
        }
        flowMapper.insert(flow);
    }

    /**
     * 批量导入流水（来自网银导出解析后的结构化数据）。
     * <p>按 accountId + transactionNo 去重：已存在的跳过，不覆盖已有勾稽状态。
     * 无流水号的记录视为独立交易直接入库。</p>
     *
     * @return 导入结果：{inserted, skipped}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFlows(List<BizBankFlow> flows) {
        if (flows == null || flows.isEmpty()) {
            throw new BusinessException(400, "导入数据为空");
        }
        int inserted = 0;
        int skipped = 0;
        for (BizBankFlow flow : flows) {
            validateFlow(flow);
            requireAccount(flow.getAccountId());
            boolean isDup = false;
            if (flow.getTransactionNo() != null && !flow.getTransactionNo().isBlank()) {
                Long dup = flowMapper.selectCount(new LambdaQueryWrapper<BizBankFlow>()
                        .eq(BizBankFlow::getAccountId, flow.getAccountId())
                        .eq(BizBankFlow::getTransactionNo, flow.getTransactionNo()));
                isDup = dup > 0;
            }
            if (isDup) {
                skipped++;
                continue;
            }
            flow.setReconciled(0);
            flow.setSource("IMPORT");
            flowMapper.insert(flow);
            inserted++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("inserted", inserted);
        result.put("skipped", skipped);
        log.info("银行流水导入完成: inserted={}, skipped={}", inserted, skipped);
        return result;
    }

    /**
     * 勾稽匹配：将一笔流水关联到内部单据（付款申请/回款登记），支持部分勾稽。
     * <p>仅未勾稽流水可匹配；匹配后置 reconciled=1 并记录 matchedType/matchedId/matchAmount。</p>
     * <p>付款申请联动（V2026_56）：勾稽保存后调用
     * {@link PaymentApplyService#refreshPayStatus(Long)} 重算支付执行态（足额→PAID）。</p>
     *
     * @param matchAmount 本次勾稽金额（可选；缺省按流水整笔金额勾稽），必须 &gt;0 且 ≤ 流水金额
     */
    @Transactional(rollbackFor = Exception.class)
    public void matchFlow(Long flowId, String matchedType, Long matchedId, BigDecimal matchAmount) {
        BizBankFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BusinessException(404, "流水不存在");
        }
        if (flow.getReconciled() != null && flow.getReconciled() == 1) {
            throw new BusinessException(400, "该流水已勾稽，不可重复匹配");
        }
        if (!List.of(BizBankFlow.MATCH_PAYMENT_APPLY, BizBankFlow.MATCH_PAYMENT_RECEIVED).contains(matchedType)) {
            throw new BusinessException(400, "匹配的单据类型不合法");
        }
        if (matchedId == null) {
            throw new BusinessException(400, "匹配单据ID不能为空");
        }
        // 方向一致性：收入流水→回款，支出流水→付款（防错配）
        if (BizBankFlow.DIRECTION_IN.equals(flow.getDirection())
                && !BizBankFlow.MATCH_PAYMENT_RECEIVED.equals(matchedType)) {
            throw new BusinessException(400, "收入流水只能匹配回款登记");
        }
        if (BizBankFlow.DIRECTION_OUT.equals(flow.getDirection())
                && !BizBankFlow.MATCH_PAYMENT_APPLY.equals(matchedType)) {
            throw new BusinessException(400, "支出流水只能匹配付款申请");
        }
        // 勾稽金额校验：缺省整笔；显式传入时须为正且不超流水金额
        BigDecimal effective = matchAmount;
        if (effective == null) {
            effective = flow.getAmount();
        } else if (effective.signum() <= 0) {
            throw new BusinessException(400, "勾稽金额必须大于0");
        } else if (effective.compareTo(flow.getAmount()) > 0) {
            throw new BusinessException(400, "勾稽金额不能超过流水金额 " + flow.getAmount());
        }
        // 付款申请需已审批生效，且累计勾稽（含本次）不得超过付款金额（防超付勾稽）
        if (BizBankFlow.MATCH_PAYMENT_APPLY.equals(matchedType)) {
            BizPaymentApply apply = paymentApplyMapper.selectById(matchedId);
            if (apply == null) {
                throw new BusinessException(404, "付款申请不存在: " + matchedId);
            }
            if (!"APPROVED".equals(apply.getStatus())) {
                throw new BusinessException(400, "仅审批通过的付款申请可勾稽支付，当前状态：" + apply.getStatus());
            }
            BigDecimal already = sumMatchedAmount(matchedId);
            BigDecimal paymentAmount = apply.getPaymentAmount() == null ? BigDecimal.ZERO : apply.getPaymentAmount();
            if (already.add(effective).compareTo(paymentAmount) > 0) {
                throw new BusinessException(400, "累计勾稽金额不能超过付款金额 " + paymentAmount
                        + "（已勾稽 " + already + "，本次 " + effective + "）");
            }
        }
        flow.setReconciled(1);
        flow.setMatchedType(matchedType);
        flow.setMatchedId(matchedId);
        flow.setMatchAmount(matchAmount);
        flowMapper.updateById(flow);
        if (BizBankFlow.MATCH_PAYMENT_APPLY.equals(matchedType)) {
            paymentApplyService.refreshPayStatus(matchedId);
        }
    }

    /**
     * 取消勾稽（恢复为未匹配状态）；付款申请联动重算支付态（可能 PAID→UNPAID）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unmatchFlow(Long flowId) {
        BizBankFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BusinessException(404, "流水不存在");
        }
        String matchedType = flow.getMatchedType();
        Long matchedId = flow.getMatchedId();
        flow.setReconciled(0);
        flow.setMatchedType(null);
        flow.setMatchedId(null);
        flow.setMatchAmount(null);
        flowMapper.updateById(flow);
        if (BizBankFlow.MATCH_PAYMENT_APPLY.equals(matchedType) && matchedId != null) {
            paymentApplyService.refreshPayStatus(matchedId);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 某付款申请已勾稽金额合计（match_amount 空则取流水 amount）。
     */
    private BigDecimal sumMatchedAmount(Long paymentApplyId) {
        List<BizBankFlow> matched = flowMapper.selectList(
                new LambdaQueryWrapper<BizBankFlow>()
                        .eq(BizBankFlow::getReconciled, 1)
                        .eq(BizBankFlow::getMatchedType, BizBankFlow.MATCH_PAYMENT_APPLY)
                        .eq(BizBankFlow::getMatchedId, paymentApplyId));
        BigDecimal total = BigDecimal.ZERO;
        for (BizBankFlow f : matched) {
            total = total.add(f.getMatchAmount() != null ? f.getMatchAmount()
                    : (f.getAmount() != null ? f.getAmount() : BigDecimal.ZERO));
        }
        return total;
    }

    private void requireAccount(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(400, "银行账户ID不能为空");
        }
        if (accountMapper.selectById(accountId) == null) {
            throw new BusinessException(404, "银行账户不存在: " + accountId);
        }
    }

    private void validateFlow(BizBankFlow flow) {
        if (flow.getFlowDate() == null) {
            throw new BusinessException(400, "交易日期不能为空");
        }
        if (!BizBankFlow.DIRECTION_IN.equals(flow.getDirection())
                && !BizBankFlow.DIRECTION_OUT.equals(flow.getDirection())) {
            throw new BusinessException(400, "流水方向不合法，需为 IN 或 OUT");
        }
        if (flow.getAmount() == null || flow.getAmount().signum() <= 0) {
            throw new BusinessException(400, "交易金额必须大于0");
        }
    }
}
