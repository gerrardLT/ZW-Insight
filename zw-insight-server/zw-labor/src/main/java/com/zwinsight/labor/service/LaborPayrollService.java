package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 劳务工资单服务
 */
@Service
@RequiredArgsConstructor
public class LaborPayrollService {

    private final BizLaborPayrollMapper payrollMapper;
    private final BizWorkOrderMapper workOrderMapper;

    /**
     * 分页查询
     */
    public PageResult<BizLaborPayroll> page(int page, int size, Long projectId, Long teamId) {
        Page<BizLaborPayroll> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborPayroll> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborPayroll::getProjectId, projectId)
                .eq(teamId != null, BizLaborPayroll::getTeamId, teamId)
                .orderByDesc(BizLaborPayroll::getCreatedAt);
        Page<BizLaborPayroll> result = payrollMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存工资单（按周期/班组汇总工单）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizLaborPayroll payroll) {
        // 汇总该周期内该班组的已审批工单
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizWorkOrder::getProjectId, payroll.getProjectId())
                .eq(BizWorkOrder::getTeamId, payroll.getTeamId())
                .eq(BizWorkOrder::getStatus, "APPROVED")
                .ge(BizWorkOrder::getWorkDate, payroll.getPeriodStart())
                .le(BizWorkOrder::getWorkDate, payroll.getPeriodEnd());

        if (payroll.getOrderType() != null) {
            wrapper.eq(BizWorkOrder::getOrderType, payroll.getOrderType());
        }

        List<BizWorkOrder> workOrders = workOrderMapper.selectList(wrapper);
        BigDecimal totalSettlement = workOrders.stream()
                .map(wo -> wo.getTotalAmount() != null ? wo.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        payroll.setTotalSettlement(totalSettlement);
        payroll.setTotalPaid(BigDecimal.ZERO);
        payroll.setUnpaid(totalSettlement);
        payroll.setStatus("DRAFT");
        payrollMapper.insert(payroll);
    }

    /**
     * 提交
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizLaborPayroll payroll = payrollMapper.selectById(id);
        if (payroll == null) {
            throw new BusinessException("工资单不存在");
        }
        if (!"DRAFT".equals(payroll.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        payroll.setStatus("APPROVED");
        payrollMapper.updateById(payroll);
    }
}
