package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.domain.BizReserveFundReturn;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
import com.zwinsight.finance.mapper.BizReserveFundReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 备用金归还服务
 */
@Service
@RequiredArgsConstructor
public class ReserveFundReturnService {

    private final BizReserveFundReturnMapper returnMapper;
    private final BizReserveFundApplyMapper applyMapper;

    /**
     * 新增归还（校验≤待归还金额，更新申请returnedAmount）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizReserveFundReturn fundReturn) {
        BizReserveFundApply apply = applyMapper.selectById(fundReturn.getReserveApplyId());
        if (apply == null) {
            throw new BusinessException("备用金申请不存在");
        }

        // 计算待归还金额 = 申请金额 - 已归还 - 已冲抵
        BigDecimal applyAmount = apply.getApplyAmount() == null ? BigDecimal.ZERO : apply.getApplyAmount();
        BigDecimal returned = apply.getReturnedAmount() == null ? BigDecimal.ZERO : apply.getReturnedAmount();
        BigDecimal offset = apply.getOffsetAmount() == null ? BigDecimal.ZERO : apply.getOffsetAmount();
        BigDecimal pendingReturn = applyAmount.subtract(returned).subtract(offset);

        if (fundReturn.getReturnAmount().compareTo(pendingReturn) > 0) {
            throw new BusinessException("归还金额不能超过待归还金额：" + pendingReturn);
        }

        returnMapper.insert(fundReturn);

        // 更新申请已归还金额
        apply.setReturnedAmount(returned.add(fundReturn.getReturnAmount()));
        applyMapper.updateById(apply);
    }
}
