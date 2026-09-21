package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizBill;
import com.zwinsight.finance.mapper.BizBillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 票据台账服务（应收/应付承兑汇票全生命周期）
 * <p>生命周期：
 * 应收票据：登记(HELD) → 到期兑现(REDEEMED) / 背书转让(ENDORSED) / 贴现变现(DISCOUNTED)
 * 应付票据：登记(HELD) → 到期兑付(PAID_OUT)
 * 贴现公式（银行惯例单利）：贴现利息 = 面值 × 年贴现率 × 剩余天数 / 360。</p>
 * <p>纪律：票据属资金形态转换（现金↔票据），不回写项目收支累计；
 * 金额纪律延续——total_income/total_expense 仅由经营性单据回写。</p>
 */
@Service
@RequiredArgsConstructor
public class BillService {

    private final BizBillMapper billMapper;

    /** 贴现天数基准（银行惯例 360 天） */
    private static final BigDecimal DAYS_BASE = new BigDecimal("360");

    /**
     * 分页查询票据台账
     */
    public PageResult<BizBill> page(int page, int size, String direction, String status, Long projectId) {
        Page<BizBill> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(direction != null && !direction.isBlank(), BizBill::getDirection, direction)
                .eq(status != null && !status.isBlank(), BizBill::getStatus, status)
                .eq(projectId != null, BizBill::getProjectId, projectId)
                .orderByDesc(BizBill::getCreatedAt);
        return PageResult.of(billMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 登记票据（方向决定后续可用操作）
     */
    public void register(BizBill bill) {
        validateBill(bill);
        bill.setStatus(BizBill.STATUS_HELD);
        billMapper.insert(bill);
    }

    /**
     * 背书转让（仅应收票据 HELD → ENDORSED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void endorse(Long id, String endorseeName, LocalDate endorseDate) {
        BizBill bill = requireBill(id);
        if (!BizBill.DIRECTION_RECEIVABLE.equals(bill.getDirection())) {
            throw new BusinessException(400, "仅应收票据支持背书转让");
        }
        if (!BizBill.STATUS_HELD.equals(bill.getStatus())) {
            throw new BusinessException(400, "仅[持有]状态可背书，当前状态：" + bill.getStatus());
        }
        if (endorseeName == null || endorseeName.isBlank()) {
            throw new BusinessException(400, "被背书人不能为空");
        }
        bill.setStatus(BizBill.STATUS_ENDORSED);
        bill.setEndorseeName(endorseeName);
        bill.setEndorseDate(endorseDate != null ? endorseDate : LocalDate.now());
        billMapper.updateById(bill);
    }

    /**
     * 贴现变现（仅应收票据 HELD → DISCOUNTED）
     * <p>贴现利息 = 面值 × 年贴现率 × 剩余天数 / 360（保留2位）；
     * 贴现净额 = 面值 - 贴现利息。贴现日必须在出票日与到期日之间。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void discount(Long id, LocalDate discountDate, BigDecimal annualRate) {
        BizBill bill = requireBill(id);
        if (!BizBill.DIRECTION_RECEIVABLE.equals(bill.getDirection())) {
            throw new BusinessException(400, "仅应收票据支持贴现");
        }
        if (!BizBill.STATUS_HELD.equals(bill.getStatus())) {
            throw new BusinessException(400, "仅[持有]状态可贴现，当前状态：" + bill.getStatus());
        }
        if (annualRate == null || annualRate.signum() <= 0 || annualRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(400, "贴现年利率不合法，需在(0,1)之间（如0.048表示4.8%）");
        }
        LocalDate date = discountDate != null ? discountDate : LocalDate.now();
        if (!date.isAfter(bill.getIssueDate()) || !date.isBefore(bill.getDueDate())) {
            throw new BusinessException(400, "贴现日期须在出票日（" + bill.getIssueDate() + "）与到期日（" + bill.getDueDate() + "）之间");
        }
        long remainingDays = ChronoUnit.DAYS.between(date, bill.getDueDate());
        if (remainingDays <= 0) {
            throw new BusinessException(400, "票据已到期，无需贴现");
        }
        // 贴现利息 = 面值 × 年贴现率 × 剩余天数 / 360
        BigDecimal interest = bill.getFaceAmount()
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(remainingDays))
                .divide(DAYS_BASE, 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = bill.getFaceAmount().subtract(interest);

        bill.setStatus(BizBill.STATUS_DISCOUNTED);
        bill.setDiscountDate(date);
        bill.setDiscountRate(annualRate);
        bill.setDiscountInterest(interest);
        bill.setDiscountNetAmount(netAmount);
        billMapper.updateById(bill);
    }

    /**
     * 到期兑现（应收票据 HELD → REDEEMED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void redeem(Long id, LocalDate redeemDate) {
        BizBill bill = requireBill(id);
        if (!BizBill.DIRECTION_RECEIVABLE.equals(bill.getDirection())) {
            throw new BusinessException(400, "仅应收票据支持兑现操作");
        }
        if (!BizBill.STATUS_HELD.equals(bill.getStatus())) {
            throw new BusinessException(400, "仅[持有]状态可兑现，当前状态：" + bill.getStatus());
        }
        bill.setStatus(BizBill.STATUS_REDEEMED);
        bill.setRemark(concatRemark(bill.getRemark(), "到期兑现于 " + (redeemDate != null ? redeemDate : LocalDate.now())));
        billMapper.updateById(bill);
    }

    /**
     * 到期兑付（应付票据 HELD → PAID_OUT）
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOut(Long id, LocalDate payDate) {
        BizBill bill = requireBill(id);
        if (!BizBill.DIRECTION_PAYABLE.equals(bill.getDirection())) {
            throw new BusinessException(400, "仅应付票据支持兑付操作");
        }
        if (!BizBill.STATUS_HELD.equals(bill.getStatus())) {
            throw new BusinessException(400, "仅[持有]状态可兑付，当前状态：" + bill.getStatus());
        }
        bill.setStatus(BizBill.STATUS_PAID_OUT);
        bill.setRemark(concatRemark(bill.getRemark(), "到期兑付于 " + (payDate != null ? payDate : LocalDate.now())));
        billMapper.updateById(bill);
    }

    /**
     * 查询即将到期票据（HELD 状态且 N 天内到期，含已逾期）
     */
    public List<BizBill> expiring(int days) {
        LocalDate now = LocalDate.now();
        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBill::getStatus, BizBill.STATUS_HELD)
                .le(BizBill::getDueDate, now.plusDays(days))
                .orderByAsc(BizBill::getDueDate);
        return billMapper.selectList(wrapper);
    }

    /**
     * 票据统计（老板看板资金形态维度）：
     * 应收持有面值 / 应付持有面值 / 已贴现未到期净额 / 背书转出面值
     */
    public Map<String, Object> statistics(Long projectId) {
        LambdaQueryWrapper<BizBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizBill::getProjectId, projectId);
        List<BizBill> all = billMapper.selectList(wrapper);

        BigDecimal receivableHeld = BigDecimal.ZERO;
        BigDecimal payableHeld = BigDecimal.ZERO;
        BigDecimal discountedNet = BigDecimal.ZERO;
        BigDecimal endorsed = BigDecimal.ZERO;
        for (BizBill bill : all) {
            switch (bill.getStatus()) {
                case BizBill.STATUS_HELD -> {
                    if (BizBill.DIRECTION_RECEIVABLE.equals(bill.getDirection())) {
                        receivableHeld = receivableHeld.add(bill.getFaceAmount());
                    } else {
                        payableHeld = payableHeld.add(bill.getFaceAmount());
                    }
                }
                case BizBill.STATUS_DISCOUNTED -> discountedNet = discountedNet
                        .add(bill.getDiscountNetAmount() != null ? bill.getDiscountNetAmount() : BigDecimal.ZERO);
                case BizBill.STATUS_ENDORSED -> endorsed = endorsed.add(bill.getFaceAmount());
                default -> { /* 兑现/兑付不计入存量 */ }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("receivableHeld", receivableHeld);
        result.put("payableHeld", payableHeld);
        result.put("discountedNet", discountedNet);
        result.put("endorsed", endorsed);
        result.put("count", all.size());
        return result;
    }

    // ==================== 私有方法 ====================

    private BizBill requireBill(Long id) {
        BizBill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new BusinessException(404, "票据不存在");
        }
        return bill;
    }

    private void validateBill(BizBill bill) {
        if (bill.getBillNo() == null || bill.getBillNo().isBlank()) {
            throw new BusinessException(400, "票据号码不能为空");
        }
        if (!BizBill.DIRECTION_RECEIVABLE.equals(bill.getDirection())
                && !BizBill.DIRECTION_PAYABLE.equals(bill.getDirection())) {
            throw new BusinessException(400, "票据方向不合法，需为 RECEIVABLE 或 PAYABLE");
        }
        if (!List.of("BANK_ACCEPTANCE", "COMMERCIAL_ACCEPTANCE").contains(bill.getBillType())) {
            throw new BusinessException(400, "票据类型不合法，需为 BANK_ACCEPTANCE 或 COMMERCIAL_ACCEPTANCE");
        }
        if (bill.getFaceAmount() == null || bill.getFaceAmount().signum() <= 0) {
            throw new BusinessException(400, "票面金额必须大于0");
        }
        if (bill.getIssueDate() == null || bill.getDueDate() == null) {
            throw new BusinessException(400, "出票日期与到期日期不能为空");
        }
        if (!bill.getDueDate().isAfter(bill.getIssueDate())) {
            throw new BusinessException(400, "到期日期必须晚于出票日期");
        }
    }

    private String concatRemark(String old, String addition) {
        return old == null || old.isBlank() ? addition : old + "；" + addition;
    }
}
