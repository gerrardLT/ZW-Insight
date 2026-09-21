package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizBalanceReconciliation;
import com.zwinsight.finance.mapper.BizBalanceReconciliationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 银行存款余额调节表服务
 * <p>调节公式（会计学标准）：
 * 调节后银行余额 = 银行对账单余额 + 银行收企业未收 - 银行付企业未付；
 * 调节后账面余额 = 企业账面余额 + 企业已收银行未收 - 企业已付银行未付；
 * 两调节后余额相等即调平（balanced=1）。
 * 四组「未达账项」由用户从流水中判定后填入（系统不猜测未达账项，实事求是）。</p>
 */
@Service
@RequiredArgsConstructor
public class BalanceReconciliationService {

    private final BizBalanceReconciliationMapper reconciliationMapper;

    /**
     * 分页查询调节表
     */
    public PageResult<BizBalanceReconciliation> page(int page, int size, Long accountId, LocalDate start, LocalDate end) {
        Page<BizBalanceReconciliation> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBalanceReconciliation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(accountId != null, BizBalanceReconciliation::getAccountId, accountId)
                .ge(start != null, BizBalanceReconciliation::getReconciliationDate, start)
                .le(end != null, BizBalanceReconciliation::getReconciliationDate, end)
                .orderByDesc(BizBalanceReconciliation::getReconciliationDate);
        return PageResult.of(reconciliationMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 生成/更新调节表（account+date 唯一）：自动计算调节后余额与是否调平
     */
    @Transactional(rollbackFor = Exception.class)
    public BizBalanceReconciliation save(BizBalanceReconciliation record) {
        if (record.getAccountId() == null) {
            throw new BusinessException(400, "银行账户ID不能为空");
        }
        if (record.getReconciliationDate() == null) {
            throw new BusinessException(400, "调节基准日不能为空");
        }
        if (record.getBankStatementBalance() == null || record.getBookBalance() == null) {
            throw new BusinessException(400, "银行对账单余额与企业账面余额不能为空");
        }
        // 未达账项四组，空值按 0
        BigDecimal entDepBankNot = nvl(record.getEnterpriseDepositBankNot());
        BigDecimal entPayBankNot = nvl(record.getEnterprisePaymentBankNot());
        BigDecimal bankDepEntNot = nvl(record.getBankDepositEnterpriseNot());
        BigDecimal bankPayEntNot = nvl(record.getBankPaymentEnterpriseNot());

        // 调节后余额
        BigDecimal adjustedBank = record.getBankStatementBalance().add(bankDepEntNot).subtract(bankPayEntNot);
        BigDecimal adjustedBook = record.getBookBalance().add(entDepBankNot).subtract(entPayBankNot);
        record.setAdjustedBankBalance(adjustedBank);
        record.setAdjustedBookBalance(adjustedBook);
        record.setBalanced(adjustedBank.compareTo(adjustedBook) == 0 ? 1 : 0);

        // 唯一性 upsert
        BizBalanceReconciliation existing = reconciliationMapper.selectOne(
                new LambdaQueryWrapper<BizBalanceReconciliation>()
                        .eq(BizBalanceReconciliation::getAccountId, record.getAccountId())
                        .eq(BizBalanceReconciliation::getReconciliationDate, record.getReconciliationDate()));
        if (existing != null) {
            record.setId(existing.getId());
            reconciliationMapper.updateById(record);
        } else {
            reconciliationMapper.insert(record);
        }
        return record;
    }

    /**
     * 查询调节表明细（含计算字段，供前端展示）
     */
    public BizBalanceReconciliation detail(Long id) {
        BizBalanceReconciliation record = reconciliationMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "调节表不存在");
        }
        // 重算展示字段（DB 存原始未达账项，调节后余额不入库，实时算）
        BigDecimal adjustedBank = record.getBankStatementBalance()
                .add(nvl(record.getBankDepositEnterpriseNot()))
                .subtract(nvl(record.getBankPaymentEnterpriseNot()));
        BigDecimal adjustedBook = record.getBookBalance()
                .add(nvl(record.getEnterpriseDepositBankNot()))
                .subtract(nvl(record.getEnterprisePaymentBankNot()));
        record.setAdjustedBankBalance(adjustedBank);
        record.setAdjustedBookBalance(adjustedBook);
        record.setBalanced(adjustedBank.compareTo(adjustedBook) == 0 ? 1 : 0);
        return record;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
