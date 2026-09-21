package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizWageDeposit;
import com.zwinsight.finance.domain.BizWageSpecialAccount;
import com.zwinsight.finance.mapper.BizWageDepositMapper;
import com.zwinsight.finance.mapper.BizWageSpecialAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 农民工工资专户服务（条例合规：拨付记录 → 余额与到位率监控）
 * <p>纪律：
 * 1) 拨付记录与专户余额同事务更新（对称写，不允许出现流水与余额脱钩）；
 * 2) 合规判定口径——到位率 = 累计到账 / 人工费预算；拨付周期以最近到账日期计算（≤1个月）；
 * 3) 专户不可静默删除，只能销户（CANCELLED）留痕。</p>
 */
@Service
@RequiredArgsConstructor
public class WageSpecialAccountService {

    private final BizWageSpecialAccountMapper accountMapper;
    private final BizWageDepositMapper depositMapper;

    /**
     * 分页查询专户
     */
    public PageResult<BizWageSpecialAccount> page(int page, int size, Long projectId, String status) {
        Page<BizWageSpecialAccount> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizWageSpecialAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizWageSpecialAccount::getProjectId, projectId)
                .eq(status != null && !status.isBlank(), BizWageSpecialAccount::getStatus, status)
                .orderByDesc(BizWageSpecialAccount::getCreatedAt);
        return PageResult.of(accountMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 新增专户（项目维度唯一：一个项目一个在用专户）
     */
    public void save(BizWageSpecialAccount account) {
        if (account.getProjectId() == null) {
            throw new BusinessException(400, "项目ID不能为空");
        }
        if (account.getAccountNo() == null || account.getAccountNo().isBlank()) {
            throw new BusinessException(400, "专户账号不能为空");
        }
        if (account.getBankName() == null || account.getBankName().isBlank()) {
            throw new BusinessException(400, "开户银行不能为空");
        }
        // 项目唯一性：同项目不允许存在第二个在用专户
        Long count = accountMapper.selectCount(new LambdaQueryWrapper<BizWageSpecialAccount>()
                .eq(BizWageSpecialAccount::getProjectId, account.getProjectId())
                .eq(BizWageSpecialAccount::getStatus, BizWageSpecialAccount.STATUS_ACTIVE));
        if (count > 0) {
            throw new BusinessException(400, "该项目已存在在用工资专户，不可重复开设");
        }
        if (account.getWageBudget() == null || account.getWageBudget().signum() < 0) {
            account.setWageBudget(BigDecimal.ZERO);
        }
        account.setStatus(BizWageSpecialAccount.STATUS_ACTIVE);
        account.setTotalReceived(BigDecimal.ZERO);
        account.setTotalPaid(BigDecimal.ZERO);
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setComplianceFlag(BizWageSpecialAccount.COMPLIANCE_COMPLIANT);
        accountMapper.insert(account);
    }

    /**
     * 登记人工费拨付到账（对称更新专户累计到账与余额，重算合规状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordDeposit(BizWageDeposit deposit) {
        BizWageSpecialAccount account = requireAccount(deposit.getAccountId());
        if (!BizWageSpecialAccount.STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BusinessException(400, "专户状态[" + account.getStatus() + "]不允许登记拨付");
        }
        if (deposit.getAmount() == null || deposit.getAmount().signum() <= 0) {
            throw new BusinessException(400, "拨付金额必须大于0");
        }
        if (deposit.getDepositDate() == null) {
            throw new BusinessException(400, "到账日期不能为空");
        }

        // 先写流水
        deposit.setProjectId(account.getProjectId());
        depositMapper.insert(deposit);

        // 对称更新专户汇总
        account.setTotalReceived(account.getTotalReceived().add(deposit.getAmount()));
        account.setCurrentBalance(account.getCurrentBalance().add(deposit.getAmount()));
        account.setLastDepositDate(deposit.getDepositDate());
        accountMapper.updateById(recomputeCompliance(account));
    }

    /**
     * 登记代发工资（专户余额扣减，总包代发台账入口，条例第31条）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordWagePayment(Long accountId, BigDecimal amount, LocalDate payDate) {
        BizWageSpecialAccount account = requireAccount(accountId);
        if (!BizWageSpecialAccount.STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BusinessException(400, "专户状态[" + account.getStatus() + "]不允许代发");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(400, "代发金额必须大于0");
        }
        if (amount.compareTo(account.getCurrentBalance()) > 0) {
            throw new BusinessException(400, "代发金额超过专户当前余额（" + account.getCurrentBalance() + "），专户资金仅可用于农民工工资");
        }
        account.setTotalPaid(account.getTotalPaid().add(amount));
        account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
        accountMapper.updateById(recomputeCompliance(account));
    }

    /**
     * 合规巡检（每日定时任务入口）：
     * 拨付周期 > 1个月 → OVERDUE；到位率 < 80% → INSUFFICIENT（可配置阈值的前置版本）
     */
    public List<BizWageSpecialAccount> complianceScan() {
        List<BizWageSpecialAccount> active = accountMapper.selectList(
                new LambdaQueryWrapper<BizWageSpecialAccount>()
                        .eq(BizWageSpecialAccount::getStatus, BizWageSpecialAccount.STATUS_ACTIVE));
        for (BizWageSpecialAccount account : active) {
            BizWageSpecialAccount recomputed = recomputeCompliance(account);
            if (!recomputed.getComplianceFlag().equals(account.getComplianceFlag())) {
                accountMapper.updateById(recomputed);
            }
        }
        return active;
    }

    /**
     * 拨付记录查询（专户维度）
     */
    public List<BizWageDeposit> deposits(Long accountId) {
        return depositMapper.selectList(new LambdaQueryWrapper<BizWageDeposit>()
                .eq(BizWageDeposit::getAccountId, accountId)
                .orderByDesc(BizWageDeposit::getDepositDate));
    }

    /**
     * 人工费到位率（老板看板合规指标）：
     * 到位率 = 累计到账 / 人工费预算（预算为0时返回0）
     */
    public BigDecimal arrivalRate(Long accountId) {
        BizWageSpecialAccount account = requireAccount(accountId);
        if (account.getWageBudget() == null || account.getWageBudget().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return account.getTotalReceived()
                .divide(account.getWageBudget(), 4, RoundingMode.HALF_UP);
    }

    /**
     * 销户（留痕不可物理删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        BizWageSpecialAccount account = requireAccount(id);
        if (account.getCurrentBalance() != null && account.getCurrentBalance().signum() > 0) {
            throw new BusinessException(400, "专户余额非零（" + account.getCurrentBalance() + "），须清零后方可销户");
        }
        account.setStatus(BizWageSpecialAccount.STATUS_CANCELLED);
        accountMapper.updateById(account);
    }

    // ==================== 私有方法 ====================

    private BizWageSpecialAccount requireAccount(Long id) {
        BizWageSpecialAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new BusinessException(404, "工资专户不存在");
        }
        return account;
    }

    /**
     * 重算合规状态：
     * 1) 最近到账距今天 > 31 天 → OVERDUE（条例第24条：拨付周期不得超过1个月）
     * 2) 到位率 < 80% → INSUFFICIENT
     * 3) 否则 COMPLIANT
     */
    private BizWageSpecialAccount recomputeCompliance(BizWageSpecialAccount account) {
        String flag = BizWageSpecialAccount.COMPLIANCE_COMPLIANT;
        if (account.getLastDepositDate() != null) {
            long daysSince = ChronoUnit.DAYS.between(account.getLastDepositDate(), LocalDate.now());
            if (daysSince > 31) {
                flag = BizWageSpecialAccount.COMPLIANCE_OVERDUE;
            }
        }
        if (BizWageSpecialAccount.COMPLIANCE_COMPLIANT.equals(flag)
                && account.getWageBudget() != null && account.getWageBudget().signum() > 0) {
            BigDecimal rate = account.getTotalReceived().divide(account.getWageBudget(), 4, RoundingMode.HALF_UP);
            if (rate.compareTo(new BigDecimal("0.80")) < 0) {
                flag = BizWageSpecialAccount.COMPLIANCE_INSUFFICIENT;
            }
        }
        account.setComplianceFlag(flag);
        return account;
    }
}
