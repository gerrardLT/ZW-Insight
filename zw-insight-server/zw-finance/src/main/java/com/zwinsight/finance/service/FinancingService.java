package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizFinancing;
import com.zwinsight.finance.domain.BizFinancingRepayment;
import com.zwinsight.finance.mapper.BizFinancingMapper;
import com.zwinsight.finance.mapper.BizFinancingRepaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 融资借贷服务（借款台账 + 三种还款方式的还款计划生成 + 按期核销）
 * <p>计划生成公式（r=月利率=年利率/12，n=期数，P=本金）：
 * 1) 等额本息 EQUAL_INSTALLMENT：月供 = P×r×(1+r)^n/((1+r)^n-1)，
 *    每期利息 = 剩余本金×r，每期本金 = 月供-利息；末期做余额校正防舍入漂移；
 * 2) 等额本金 EQUAL_PRINCIPAL：每期本金 = P/n（前 n-1 期四舍五入，末期取余差），
 *    每期利息 = 剩余本金×r；
 * 3) 到期还本付息 BULLET：单期，本金=P，利息 = P×r×n。</p>
 * <p>纪律：融资不回写项目 total_income/total_expense（非经营性收支）；
 * 还款核销与台账 totalRepaid 同事务对称更新；全部期还清自动置 SETTLED。</p>
 */
@Service
@RequiredArgsConstructor
public class FinancingService {

    private final BizFinancingMapper financingMapper;
    private final BizFinancingRepaymentMapper repaymentMapper;

    private static final MathContext MC = new MathContext(20);

    /**
     * 分页查询融资台账
     */
    public PageResult<BizFinancing> page(int page, int size, String status, String financingType) {
        Page<BizFinancing> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFinancing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), BizFinancing::getStatus, status)
                .eq(financingType != null && !financingType.isBlank(), BizFinancing::getFinancingType, financingType)
                .orderByDesc(BizFinancing::getCreatedAt);
        return PageResult.of(financingMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 登记融资并自动生成还款计划（同事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(BizFinancing financing) {
        validateFinancing(financing);

        financing.setStatus(BizFinancing.STATUS_ACTIVE);
        financing.setTotalRepaid(BigDecimal.ZERO);
        financingMapper.insert(financing);

        List<BizFinancingRepayment> plan = generatePlan(financing);
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (BizFinancingRepayment installment : plan) {
            repaymentMapper.insert(installment);
            totalInterest = totalInterest.add(installment.getInterestDue());
        }
        financing.setTotalInterest(totalInterest);
        financingMapper.updateById(financing);
    }

    /**
     * 查询还款计划（按期数升序）
     */
    public List<BizFinancingRepayment> repayments(Long financingId) {
        requireFinancing(financingId);
        return repaymentMapper.selectList(new LambdaQueryWrapper<BizFinancingRepayment>()
                .eq(BizFinancingRepayment::getFinancingId, financingId)
                .orderByAsc(BizFinancingRepayment::getPeriodNo));
    }

    /**
     * 登记还款（按期核销：先息后本，超还部分拒绝；对称更新台账 totalRepaid；
     * 该期还清置 PAID，全部期还清置 SETTLED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordRepayment(Long financingId, Integer periodNo,
                                BigDecimal interestPaid, BigDecimal principalPaid, LocalDate paidDate) {
        BizFinancing financing = requireFinancing(financingId);
        if (BizFinancing.STATUS_SETTLED.equals(financing.getStatus())) {
            throw new BusinessException(400, "该笔融资已结清，无需还款");
        }
        BizFinancingRepayment installment = repaymentMapper.selectOne(
                new LambdaQueryWrapper<BizFinancingRepayment>()
                        .eq(BizFinancingRepayment::getFinancingId, financingId)
                        .eq(BizFinancingRepayment::getPeriodNo, periodNo));
        if (installment == null) {
            throw new BusinessException(404, "第 " + periodNo + " 期还款计划不存在");
        }
        if (BizFinancingRepayment.STATUS_PAID.equals(installment.getStatus())) {
            throw new BusinessException(400, "第 " + periodNo + " 期已还清");
        }
        BigDecimal addInterest = interestPaid == null ? BigDecimal.ZERO : interestPaid;
        BigDecimal addPrincipal = principalPaid == null ? BigDecimal.ZERO : principalPaid;
        if (addInterest.signum() < 0 || addPrincipal.signum() < 0) {
            throw new BusinessException(400, "还款金额不能为负");
        }
        if (addInterest.signum() == 0 && addPrincipal.signum() == 0) {
            throw new BusinessException(400, "还款金额必须大于0");
        }
        // 超还校验（先息后本口径：利息余额 + 本金余额）
        BigDecimal interestBalance = installment.getInterestDue()
                .subtract(nvl(installment.getInterestPaid()));
        BigDecimal principalBalance = installment.getPrincipalDue()
                .subtract(nvl(installment.getPrincipalPaid()));
        if (addInterest.compareTo(interestBalance) > 0) {
            throw new BusinessException(400, "实还利息 " + addInterest + " 超过该期利息余额 " + interestBalance);
        }
        if (addPrincipal.compareTo(principalBalance) > 0) {
            throw new BusinessException(400, "实还本金 " + addPrincipal + " 超过该期本金余额 " + principalBalance);
        }

        installment.setInterestPaid(nvl(installment.getInterestPaid()).add(addInterest));
        installment.setPrincipalPaid(nvl(installment.getPrincipalPaid()).add(addPrincipal));
        installment.setPaidDate(paidDate != null ? paidDate : LocalDate.now());
        boolean cleared = installment.getInterestPaid().compareTo(installment.getInterestDue()) == 0
                && installment.getPrincipalPaid().compareTo(installment.getPrincipalDue()) == 0;
        installment.setStatus(cleared ? BizFinancingRepayment.STATUS_PAID : BizFinancingRepayment.STATUS_PARTIAL);
        repaymentMapper.updateById(installment);

        // 对称更新台账累计已还
        BigDecimal paid = financing.getTotalRepaid() == null ? BigDecimal.ZERO : financing.getTotalRepaid();
        financing.setTotalRepaid(paid.add(addInterest).add(addPrincipal));
        // 全部期还清 → SETTLED
        Long pendingCount = repaymentMapper.selectCount(new LambdaQueryWrapper<BizFinancingRepayment>()
                .eq(BizFinancingRepayment::getFinancingId, financingId)
                .ne(BizFinancingRepayment::getStatus, BizFinancingRepayment.STATUS_PAID));
        if (pendingCount == 0) {
            financing.setStatus(BizFinancing.STATUS_SETTLED);
        }
        financingMapper.updateById(financing);
    }

    /**
     * 融资统计（老板看板融资费用维度）：
     * 在借本金余额 / 在借笔数 / 计划总利息 / 已还总额
     */
    public Map<String, Object> statistics() {
        List<BizFinancing> all = financingMapper.selectList(null);
        BigDecimal outstandingPrincipal = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalRepaid = BigDecimal.ZERO;
        int activeCount = 0;
        for (BizFinancing f : all) {
            totalInterest = totalInterest.add(nvl(f.getTotalInterest()));
            totalRepaid = totalRepaid.add(nvl(f.getTotalRepaid()));
            if (BizFinancing.STATUS_ACTIVE.equals(f.getStatus()) || BizFinancing.STATUS_OVERDUE.equals(f.getStatus())) {
                activeCount++;
                // 本金余额 = 本金 - 已还本金（已还总额中先抵利息，简化口径：已还本金 ≤ 已还总额）
                BigDecimal repaidPrincipal = nvl(f.getTotalRepaid())
                        .subtract(nvl(f.getTotalInterest())).max(BigDecimal.ZERO)
                        .min(nvl(f.getPrincipal()));
                outstandingPrincipal = outstandingPrincipal.add(nvl(f.getPrincipal()).subtract(repaidPrincipal));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("activeCount", activeCount);
        result.put("outstandingPrincipal", outstandingPrincipal);
        result.put("totalInterest", totalInterest);
        result.put("totalRepaid", totalRepaid);
        result.put("count", all.size());
        return result;
    }

    // ==================== 私有方法：计划生成 ====================

    /**
     * 按还款方式生成还款计划（应还日期 = 放款日 + 期数月）
     */
    private List<BizFinancingRepayment> generatePlan(BizFinancing financing) {
        int n = financing.getTermMonths();
        BigDecimal p = financing.getPrincipal();
        BigDecimal monthlyRate = financing.getAnnualRate()
                .divide(new BigDecimal("12"), 12, RoundingMode.HALF_UP);
        return switch (financing.getRepaymentMethod()) {
            case BizFinancing.METHOD_EQUAL_INSTALLMENT -> equalInstallmentPlan(financing, n, p, monthlyRate);
            case BizFinancing.METHOD_EQUAL_PRINCIPAL -> equalPrincipalPlan(financing, n, p, monthlyRate);
            case BizFinancing.METHOD_BULLET -> bulletPlan(financing, n, p, monthlyRate);
            default -> throw new BusinessException(400, "不支持的还款方式：" + financing.getRepaymentMethod());
        };
    }

    /** 等额本息：月供 = P×r×(1+r)^n/((1+r)^n-1)；末期余额校正 */
    private List<BizFinancingRepayment> equalInstallmentPlan(BizFinancing f, int n, BigDecimal p, BigDecimal r) {
        List<BizFinancingRepayment> plan = new ArrayList<>();
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        // r=0 时月供退化为纯本金均摊
        BigDecimal monthlyPayment;
        if (r.signum() == 0) {
            monthlyPayment = p.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal pow = onePlusR.pow(n, MC);
            monthlyPayment = p.multiply(r).multiply(pow)
                    .divide(pow.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        }
        BigDecimal remaining = p;
        BigDecimal principalScheduled = BigDecimal.ZERO;
        for (int i = 1; i <= n; i++) {
            BigDecimal interest = remaining.multiply(r).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal;
            if (i == n) {
                // 末期校正：本金 = 剩余全部（消除逐期舍入漂移）
                principal = remaining;
            } else {
                principal = monthlyPayment.subtract(interest);
                if (principal.compareTo(remaining) > 0) {
                    principal = remaining;
                }
            }
            remaining = remaining.subtract(principal);
            principalScheduled = principalScheduled.add(principal);
            plan.add(buildInstallment(f, i, principal, interest));
        }
        return plan;
    }

    /** 等额本金：前 n-1 期本金 = P/n 四舍五入，末期取余差；利息 = 剩余本金×r */
    private List<BizFinancingRepayment> equalPrincipalPlan(BizFinancing f, int n, BigDecimal p, BigDecimal r) {
        List<BizFinancingRepayment> plan = new ArrayList<>();
        BigDecimal basePrincipal = p.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        BigDecimal remaining = p;
        for (int i = 1; i <= n; i++) {
            BigDecimal principal = (i == n) ? remaining : basePrincipal.min(remaining);
            BigDecimal interest = remaining.multiply(r).setScale(2, RoundingMode.HALF_UP);
            remaining = remaining.subtract(principal);
            plan.add(buildInstallment(f, i, principal, interest));
        }
        return plan;
    }

    /** 到期还本付息：单期，本金=P，利息 = P×r×n */
    private List<BizFinancingRepayment> bulletPlan(BizFinancing f, int n, BigDecimal p, BigDecimal r) {
        List<BizFinancingRepayment> plan = new ArrayList<>();
        BigDecimal interest = p.multiply(r).multiply(BigDecimal.valueOf(n)).setScale(2, RoundingMode.HALF_UP);
        plan.add(buildInstallment(f, 1, p, interest));
        return plan;
    }

    private BizFinancingRepayment buildInstallment(BizFinancing financing, int periodNo,
                                                   BigDecimal principalDue, BigDecimal interestDue) {
        BizFinancingRepayment installment = new BizFinancingRepayment();
        installment.setFinancingId(financing.getId());
        installment.setPeriodNo(periodNo);
        installment.setDueDate(financing.getStartDate().plusMonths(periodNo));
        installment.setPrincipalDue(principalDue);
        installment.setInterestDue(interestDue);
        installment.setPrincipalPaid(BigDecimal.ZERO);
        installment.setInterestPaid(BigDecimal.ZERO);
        installment.setStatus(BizFinancingRepayment.STATUS_PENDING);
        return installment;
    }

    private void validateFinancing(BizFinancing financing) {
        if (!List.of(BizFinancing.TYPE_BANK_LOAN, BizFinancing.TYPE_OTHER).contains(financing.getFinancingType())) {
            throw new BusinessException(400, "融资类型不合法，需为 BANK_LOAN 或 OTHER");
        }
        if (financing.getContractNo() == null || financing.getContractNo().isBlank()) {
            throw new BusinessException(400, "借款合同编号不能为空");
        }
        if (financing.getLenderName() == null || financing.getLenderName().isBlank()) {
            throw new BusinessException(400, "出借方名称不能为空");
        }
        if (financing.getPrincipal() == null || financing.getPrincipal().signum() <= 0) {
            throw new BusinessException(400, "借款本金必须大于0");
        }
        if (financing.getAnnualRate() == null
                || financing.getAnnualRate().signum() < 0
                || financing.getAnnualRate().compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(400, "年利率不合法，需在[0,1)之间（如0.045表示4.5%）");
        }
        if (financing.getTermMonths() == null || financing.getTermMonths() < 1 || financing.getTermMonths() > 360) {
            throw new BusinessException(400, "期限（月）不合法，需在1-360之间");
        }
        if (financing.getStartDate() == null) {
            throw new BusinessException(400, "放款日期不能为空");
        }
        if (!List.of(BizFinancing.METHOD_EQUAL_INSTALLMENT, BizFinancing.METHOD_EQUAL_PRINCIPAL,
                BizFinancing.METHOD_BULLET).contains(financing.getRepaymentMethod())) {
            throw new BusinessException(400, "还款方式不合法，需为 EQUAL_INSTALLMENT/EQUAL_PRINCIPAL/BULLET");
        }
        // 到期日缺省 = 放款日 + n 月（填写时须晚于放款日）
        if (financing.getEndDate() == null) {
            financing.setEndDate(financing.getStartDate().plusMonths(financing.getTermMonths()));
        } else if (!financing.getEndDate().isAfter(financing.getStartDate())) {
            throw new BusinessException(400, "到期日期必须晚于放款日期");
        }
        // 合同编号唯一（租户内）
        Long count = financingMapper.selectCount(new LambdaQueryWrapper<BizFinancing>()
                .eq(BizFinancing::getContractNo, financing.getContractNo()));
        if (count > 0) {
            throw new BusinessException(400, "借款合同编号[" + financing.getContractNo() + "]已存在");
        }
    }

    private BizFinancing requireFinancing(Long id) {
        BizFinancing financing = financingMapper.selectById(id);
        if (financing == null) {
            throw new BusinessException(404, "融资台账不存在");
        }
        return financing;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
