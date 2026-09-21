package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizSecurityBond;
import com.zwinsight.finance.mapper.BizSecurityBondMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 保证金台账服务（四类全生命周期：缴存→跟踪→退还/动用）
 * <p>比例上限校验依据《招标投标法实施条例》《建设工程质量保证金管理办法》
 * 《工程建设领域农民工工资保证金规定》（docs/资金流转深度调研报告.md 4.2节）。
 * 纪律：仅 DEPOSITED/USED 状态允许发起退还；退款确认对称回写金额与日期。</p>
 */
@Service
@RequiredArgsConstructor
public class SecurityBondService {

    private final BizSecurityBondMapper bondMapper;

    /** 各类型比例上限（政策法规硬约束） */
    private static final Map<String, BigDecimal> RATE_LIMITS = Map.of(
            BizSecurityBond.TYPE_TENDER, new BigDecimal("0.02"),        // 投标 ≤ 2%
            BizSecurityBond.TYPE_PERFORMANCE, new BigDecimal("0.10"),   // 履约 ≤ 10%
            BizSecurityBond.TYPE_QUALITY, new BigDecimal("0.03"),       // 质量 ≤ 3%
            BizSecurityBond.TYPE_WAGE, new BigDecimal("0.03")          // 工资 ≤ 3%（下限1%为建议值，非硬拦截）
    );

    /**
     * 分页查询保证金台账
     */
    public PageResult<BizSecurityBond> page(int page, int size, Long projectId, String bondType, String refundStatus) {
        Page<BizSecurityBond> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSecurityBond> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSecurityBond::getProjectId, projectId)
                .eq(bondType != null && !bondType.isBlank(), BizSecurityBond::getBondType, bondType)
                .eq(refundStatus != null && !refundStatus.isBlank(), BizSecurityBond::getRefundStatus, refundStatus)
                .orderByDesc(BizSecurityBond::getCreatedAt);
        return PageResult.of(bondMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 新增保证金记录（含四类比例上限校验）
     * <p>比例校验规则：提供了合同金额时，金额不得超过 类型上限 × 合同金额；
     * 保函/保险形式同等校验（保函面额也不允许超比例）。</p>
     */
    public void save(BizSecurityBond bond) {
        validateBond(bond);
        validateRateLimit(bond);

        if (bond.getRefundStatus() == null) {
            bond.setRefundStatus(BizSecurityBond.STATUS_DEPOSITED);
        }
        if (bond.getBondForm() == null) {
            bond.setBondForm("CASH");
        }
        // 保函/保险形式必须上传文件
        if (!"CASH".equals(bond.getBondForm())
                && (bond.getGuaranteeFile() == null || bond.getGuaranteeFile().isBlank())) {
            throw new BusinessException(400, "保函/保险形式必须上传担保文件");
        }
        bondMapper.insert(bond);
    }

    /**
     * 发起退还申请（DEPOSITED/USED → REFUND_APPLY）
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundApply(Long id) {
        BizSecurityBond bond = requireBond(id);
        if (!BizSecurityBond.STATUS_DEPOSITED.equals(bond.getRefundStatus())
                && !BizSecurityBond.STATUS_USED.equals(bond.getRefundStatus())) {
            throw new BusinessException(400, "当前状态[" + bond.getRefundStatus() + "]不允许发起退还申请");
        }
        bond.setRefundStatus(BizSecurityBond.STATUS_REFUND_APPLY);
        bond.setRefundApplyDate(LocalDate.now());
        bondMapper.updateById(bond);
    }

    /**
     * 确认退还完成（REFUND_APPLY → REFUNDED，记录实际金额与日期）
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundConfirm(Long id, BigDecimal refundAmount, LocalDate refundDate) {
        BizSecurityBond bond = requireBond(id);
        if (!BizSecurityBond.STATUS_REFUND_APPLY.equals(bond.getRefundStatus())) {
            throw new BusinessException(400, "仅[退还申请中]状态可确认退还");
        }
        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new BusinessException(400, "退还金额必须大于0");
        }
        if (refundAmount.compareTo(bond.getAmount()) > 0) {
            throw new BusinessException(400, "退还金额不得超过缴存金额（" + bond.getAmount() + "）");
        }
        // 质量保证金：发包人收到申请后14天内核实（建质〔2017〕138号），此处只做超期提示口径的日期记录
        bond.setRefundStatus(BizSecurityBond.STATUS_REFUNDED);
        bond.setRefundAmount(refundAmount);
        bond.setRefundActualDate(refundDate != null ? refundDate : LocalDate.now());
        bondMapper.updateById(bond);
    }

    /**
     * 标记动用（仅工资类：动用后10个工作日内须补足，人社部〔2021〕65号）
     */
    @Transactional(rollbackFor = Exception.class)
    public void markUsed(Long id) {
        BizSecurityBond bond = requireBond(id);
        if (!BizSecurityBond.TYPE_WAGE.equals(bond.getBondType())) {
            throw new BusinessException(400, "仅农民工工资保证金支持动用标记");
        }
        if (!BizSecurityBond.STATUS_DEPOSITED.equals(bond.getRefundStatus())) {
            throw new BusinessException(400, "仅[已缴存]状态可标记动用");
        }
        bond.setRefundStatus(BizSecurityBond.STATUS_USED);
        bond.setRemark(concatRemark(bond.getRemark(), "已动用，须自使用之日起10个工作日内补足（人社部〔2021〕65号）"));
        bondMapper.updateById(bond);
    }

    /**
     * 查询即将到期的保证金（DEPOSITED/USED 状态且到期日在 N 天内）
     */
    public List<BizSecurityBond> expiring(int days) {
        LocalDate now = LocalDate.now();
        LambdaQueryWrapper<BizSecurityBond> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizSecurityBond::getRefundStatus, BizSecurityBond.STATUS_DEPOSITED, BizSecurityBond.STATUS_USED)
                .isNotNull(BizSecurityBond::getDueDate)
                .le(BizSecurityBond::getDueDate, now.plusDays(days))
                .ge(BizSecurityBond::getDueDate, now)
                .orderByAsc(BizSecurityBond::getDueDate);
        return bondMapper.selectList(wrapper);
    }

    /**
     * 保证金占用统计（老板看板数据源）：
     * 各类型占用总额、现金/保函占比、保函替代率
     */
    public Map<String, Object> statistics(Long projectId) {
        LambdaQueryWrapper<BizSecurityBond> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSecurityBond::getProjectId, projectId)
                .in(BizSecurityBond::getRefundStatus,
                        BizSecurityBond.STATUS_DEPOSITED, BizSecurityBond.STATUS_USED);
        List<BizSecurityBond> occupying = bondMapper.selectList(wrapper);

        Map<String, BigDecimal> byType = new HashMap<>();
        BigDecimal cashTotal = BigDecimal.ZERO;
        BigDecimal guaranteeTotal = BigDecimal.ZERO;
        for (BizSecurityBond bond : occupying) {
            byType.merge(bond.getBondType(), bond.getAmount(), BigDecimal::add);
            if ("CASH".equals(bond.getBondForm())) {
                cashTotal = cashTotal.add(bond.getAmount());
            } else {
                guaranteeTotal = guaranteeTotal.add(bond.getAmount());
            }
        }
        BigDecimal total = cashTotal.add(guaranteeTotal);

        Map<String, Object> result = new HashMap<>();
        result.put("byType", byType);
        result.put("cashTotal", cashTotal);
        result.put("guaranteeTotal", guaranteeTotal);
        result.put("total", total);
        // 保函替代率：非现金形式占用的占比（减少现金占压，国办发〔2016〕49号导向）
        result.put("guaranteeRatio", total.signum() > 0
                ? guaranteeTotal.divide(total, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("count", occupying.size());
        return result;
    }

    // ==================== 私有方法 ====================

    private BizSecurityBond requireBond(Long id) {
        BizSecurityBond bond = bondMapper.selectById(id);
        if (bond == null) {
            throw new BusinessException(404, "保证金记录不存在");
        }
        return bond;
    }

    private void validateBond(BizSecurityBond bond) {
        if (bond.getProjectId() == null) {
            throw new BusinessException(400, "项目ID不能为空");
        }
        if (bond.getBondType() == null || !RATE_LIMITS.containsKey(bond.getBondType())) {
            throw new BusinessException(400, "保证金类型不合法，需为 TENDER/PERFORMANCE/QUALITY/WAGE");
        }
        if (bond.getAmount() == null || bond.getAmount().signum() <= 0) {
            throw new BusinessException(400, "保证金金额必须大于0");
        }
        if (bond.getDepositDate() == null) {
            throw new BusinessException(400, "缴存日期不能为空");
        }
        if (bond.getBondForm() != null && !List.of("CASH", "BANK_GUARANTEE", "INSURANCE").contains(bond.getBondForm())) {
            throw new BusinessException(400, "保证金形式不合法，需为 CASH/BANK_GUARANTEE/INSURANCE");
        }
        // 免存必须有理由（防止静默免存，真实数据原则）
        if (bond.getAmount().signum() == 0 && (bond.getExemptReason() == null || bond.getExemptReason().isBlank())) {
            throw new BusinessException(400, "免存保证金必须填写免存理由");
        }
    }

    /**
     * 四类比例上限校验（政策硬约束）：
     * 提供了合同金额时，金额不得超过 类型上限 × 合同金额
     */
    private void validateRateLimit(BizSecurityBond bond) {
        if (bond.getContractAmount() == null || bond.getContractAmount().signum() <= 0) {
            return; // 未提供基数时跳过（历史数据兼容）
        }
        BigDecimal limit = RATE_LIMITS.get(bond.getBondType())
                .multiply(bond.getContractAmount())
                .setScale(2, RoundingMode.HALF_UP);
        if (bond.getAmount().compareTo(limit) > 0) {
            String typeName = switch (bond.getBondType()) {
                case BizSecurityBond.TYPE_TENDER -> "投标保证金（≤估算价2%）";
                case BizSecurityBond.TYPE_PERFORMANCE -> "履约保证金（≤合同额10%）";
                case BizSecurityBond.TYPE_QUALITY -> "质量保证金（≤结算总额3%）";
                default -> "农民工工资保证金（≤合同额3%）";
            };
            throw new BusinessException(400, typeName + "超出比例上限，最大允许金额：" + limit);
        }
    }

    private String concatRemark(String old, String addition) {
        return old == null || old.isBlank() ? addition : old + "；" + addition;
    }
}
