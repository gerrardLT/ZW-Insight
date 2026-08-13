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

        // P0 修复（FIN-RFR-06，2026-08-12）：归还金额必须>0，原实现 null 时 compareTo NPE，
        // 且负/零可落库错误冲减待归还余额
        if (fundReturn.getReturnAmount() == null || fundReturn.getReturnAmount().signum() <= 0) {
            throw new BusinessException("归还金额必须大于0");
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

    /**
     * 删除归还记录（回冲申请 returnedAmount，与 save 回写对称）
     * <p>
     * FIN-RFR-08 收尾（2026-08-13，待决策#2 选 A）：原无删除入口，错录归还
     * 使未还余额错误减少无法回冲；对齐 PaymentReceivedService.delete 模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizReserveFundReturn existing = returnMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("归还记录不存在");
        }
        BigDecimal returnAmount = existing.getReturnAmount() == null
                ? BigDecimal.ZERO : existing.getReturnAmount();
        returnMapper.deleteById(id);

        // 回冲申请已归还金额（恢复待归还余额）
        if (existing.getReserveApplyId() != null) {
            BizReserveFundApply apply = applyMapper.selectById(existing.getReserveApplyId());
            if (apply != null && apply.getReturnedAmount() != null) {
                apply.setReturnedAmount(apply.getReturnedAmount().subtract(returnAmount));
                applyMapper.updateById(apply);
            }
        }
    }
}
