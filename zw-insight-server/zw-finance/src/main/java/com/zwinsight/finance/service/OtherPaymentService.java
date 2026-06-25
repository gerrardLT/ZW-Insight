package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
}
