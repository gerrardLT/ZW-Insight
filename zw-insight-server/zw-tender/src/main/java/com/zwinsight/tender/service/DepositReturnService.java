package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizDepositReturn;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.tender.mapper.BizDepositReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保证金退还服务
 */
@Service
@RequiredArgsConstructor
public class DepositReturnService {

    private final BizDepositReturnMapper returnMapper;
    private final BizDepositApplyMapper depositApplyMapper;

    /**
     * 分页查询
     */
    public PageResult<BizDepositReturn> page(int page, int size, Long depositApplyId) {
        Page<BizDepositReturn> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizDepositReturn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(depositApplyId != null, BizDepositReturn::getDepositApplyId, depositApplyId)
                .orderByDesc(BizDepositReturn::getReturnDate);
        Page<BizDepositReturn> result = returnMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增退还
     * <p>P2 修复（2026-08-12，批次二 TND-36/37）：原裸 insert 无金额上限/存在性校验，
     * 可超额退款、重复全额退；对齐质保金/备用金归还口径（金额>0 且累计≤缴纳额）。</p>
     */
    public void save(BizDepositReturn depositReturn) {
        if (depositReturn.getReturnAmount() == null || depositReturn.getReturnAmount().signum() <= 0) {
            throw new BusinessException("退还金额必须大于0");
        }
        BizDepositApply apply = depositApplyMapper.selectById(depositReturn.getDepositApplyId());
        if (apply == null) {
            throw new BusinessException("保证金申请不存在");
        }
        BigDecimal deposited = apply.getDepositAmount() != null ? apply.getDepositAmount() : BigDecimal.ZERO;
        LambdaQueryWrapper<BizDepositReturn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizDepositReturn::getDepositApplyId, depositReturn.getDepositApplyId());
        List<BizDepositReturn> existing = returnMapper.selectList(wrapper);
        BigDecimal returned = existing.stream()
                .map(r -> r.getReturnAmount() != null ? r.getReturnAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (returned.add(depositReturn.getReturnAmount()).compareTo(deposited) > 0) {
            BigDecimal maxReturn = deposited.subtract(returned);
            throw new BusinessException("退还金额超过可退余额，当前最大可退金额：" + maxReturn);
        }
        returnMapper.insert(depositReturn);
    }

    /**
     * 更新退还记录
     */
    public void update(BizDepositReturn depositReturn) {
        returnMapper.updateById(depositReturn);
    }

    /**
     * 删除退还记录
     */
    public void delete(Long id) {
        returnMapper.deleteById(id);
    }
}
