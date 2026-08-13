package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizOtherPayment;
import com.zwinsight.finance.mapper.BizOtherPaymentMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 其他支付服务
 */
@Service
@RequiredArgsConstructor
public class OtherPaymentService {

    private final BizOtherPaymentMapper otherPaymentMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询
     */
    public PageResult<BizOtherPayment> page(int page, int size, Long projectId) {
        Page<BizOtherPayment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizOtherPayment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizOtherPayment::getProjectId, projectId)
                .orderByDesc(BizOtherPayment::getCreatedAt);
        Page<BizOtherPayment> result = otherPaymentMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增其他支付（回写项目totalOtherPayment）
     */
    @BudgetCheck(category = "")
    @Transactional(rollbackFor = Exception.class)
    public void save(BizOtherPayment otherPayment) {
        // P0 修复（FIN-OPT-03，2026-08-12）：付款金额必须>0，
        // 原实现负/零/null 可落库并污染项目其他总支付（null 时 add NPE）
        if (otherPayment.getPaymentAmount() == null
                || otherPayment.getPaymentAmount().signum() <= 0) {
            throw new BusinessException("付款金额必须大于0");
        }
        otherPayment.setStatus("APPROVED");
        otherPaymentMapper.insert(otherPayment);

        // 回写项目其他总支付
        BizProject project = projectMapper.selectById(otherPayment.getProjectId());
        if (project != null) {
            BigDecimal totalOther = project.getTotalOtherPayment() == null
                    ? BigDecimal.ZERO : project.getTotalOtherPayment();
            project.setTotalOtherPayment(totalOther.add(otherPayment.getPaymentAmount()));
            projectMapper.updateById(project);
        }
    }

    /**
     * 删除其他支付记录（回冲项目 totalOtherPayment，与 save 回写对称）
     * <p>
     * FIN-OPT-04 收尾（2026-08-13，待决策#2 选 A）：原无删除入口，错录付款
     * 使项目其他总支出虚高无法回冲；对齐 PaymentReceivedService.delete 模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizOtherPayment existing = otherPaymentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("其他支付记录不存在");
        }
        BigDecimal paymentAmount = existing.getPaymentAmount() == null
                ? BigDecimal.ZERO : existing.getPaymentAmount();
        otherPaymentMapper.deleteById(id);

        // 回冲项目其他总支付
        BizProject project = projectMapper.selectById(existing.getProjectId());
        if (project != null && project.getTotalOtherPayment() != null) {
            project.setTotalOtherPayment(project.getTotalOtherPayment().subtract(paymentAmount));
            projectMapper.updateById(project);
        }
    }
}
