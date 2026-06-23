package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 银行账户服务
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BizBankAccountMapper bankAccountMapper;

    /**
     * 分页查询
     */
    public PageResult<BizBankAccount> page(int page, int size, String accountType, Long projectId) {
        Page<BizBankAccount> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBankAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(accountType != null, BizBankAccount::getAccountType, accountType)
                .eq(projectId != null, BizBankAccount::getProjectId, projectId)
                .orderByDesc(BizBankAccount::getCreatedAt);
        Page<BizBankAccount> result = bankAccountMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增
     */
    public void save(BizBankAccount bankAccount) {
        if (bankAccount.getStatus() == null) {
            bankAccount.setStatus(1);
        }
        bankAccountMapper.insert(bankAccount);
    }

    /**
     * 更新
     */
    public void update(BizBankAccount bankAccount) {
        BizBankAccount existing = bankAccountMapper.selectById(bankAccount.getId());
        if (existing == null) {
            throw new BusinessException("银行账户不存在");
        }
        bankAccountMapper.updateById(bankAccount);
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        bankAccountMapper.deleteById(id);
    }
}
