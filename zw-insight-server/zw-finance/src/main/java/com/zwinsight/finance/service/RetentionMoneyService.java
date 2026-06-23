package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 质保金服务
 */
@Service
@RequiredArgsConstructor
public class RetentionMoneyService {

    private final BizRetentionMoneyMapper retentionMoneyMapper;

    /**
     * 分页查询
     */
    public PageResult<BizRetentionMoney> page(int page, int size, Long projectId, Long contractId) {
        Page<BizRetentionMoney> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizRetentionMoney> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizRetentionMoney::getProjectId, projectId)
                .eq(contractId != null, BizRetentionMoney::getContractId, contractId)
                .orderByDesc(BizRetentionMoney::getCreatedAt);
        Page<BizRetentionMoney> result = retentionMoneyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增质保金（自动计算到期日期 = startDate + retentionPeriod月）
     */
    public void save(BizRetentionMoney retentionMoney) {
        // 计算到期日期
        if (retentionMoney.getStartDate() != null && retentionMoney.getRetentionPeriod() != null) {
            retentionMoney.setExpireDate(
                    retentionMoney.getStartDate().plusMonths(retentionMoney.getRetentionPeriod()));
        }
        if (retentionMoney.getReturnedAmount() == null) {
            retentionMoney.setReturnedAmount(BigDecimal.ZERO);
        }
        if (retentionMoney.getStatus() == null) {
            retentionMoney.setStatus("ACTIVE");
        }
        retentionMoneyMapper.insert(retentionMoney);
    }

    /**
     * 查询即将到期的质保金列表
     */
    public List<BizRetentionMoney> getExpiring(int days) {
        LocalDate now = LocalDate.now();
        LocalDate deadline = now.plusDays(days);
        LambdaQueryWrapper<BizRetentionMoney> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizRetentionMoney::getStatus, "ACTIVE")
                .le(BizRetentionMoney::getExpireDate, deadline)
                .ge(BizRetentionMoney::getExpireDate, now)
                .orderByAsc(BizRetentionMoney::getExpireDate);
        return retentionMoneyMapper.selectList(wrapper);
    }
}
