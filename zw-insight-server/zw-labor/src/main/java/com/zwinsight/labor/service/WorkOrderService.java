package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 派工单服务
 */
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final BizWorkOrderMapper workOrderMapper;

    /**
     * 分页查询
     */
    public PageResult<BizWorkOrder> page(int page, int size, Long projectId, Long teamId, String status) {
        Page<BizWorkOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizWorkOrder::getProjectId, projectId)
                .eq(teamId != null, BizWorkOrder::getTeamId, teamId)
                .eq(status != null, BizWorkOrder::getStatus, status)
                .orderByDesc(BizWorkOrder::getCreatedAt);
        Page<BizWorkOrder> result = workOrderMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存工单
     */
    public void save(BizWorkOrder workOrder) {
        calculateTotalAmount(workOrder);
        workOrder.setStatus("DRAFT");
        workOrderMapper.insert(workOrder);
    }

    /**
     * 批量保存
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSave(List<BizWorkOrder> workOrders) {
        for (BizWorkOrder workOrder : workOrders) {
            calculateTotalAmount(workOrder);
            workOrder.setStatus("DRAFT");
            workOrderMapper.insert(workOrder);
        }
    }

    /**
     * 更新（仅DRAFT）
     */
    public void update(BizWorkOrder workOrder) {
        BizWorkOrder existing = workOrderMapper.selectById(workOrder.getId());
        if (existing == null) {
            throw new BusinessException("工单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        calculateTotalAmount(workOrder);
        workOrderMapper.updateById(workOrder);
    }

    /**
     * 删除（仅DRAFT）
     */
    public void delete(Long id) {
        BizWorkOrder existing = workOrderMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("工单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        workOrderMapper.deleteById(id);
    }

    /**
     * 提交
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizWorkOrder workOrder = workOrderMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException("工单不存在");
        }
        if (!"DRAFT".equals(workOrder.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        workOrder.setStatus("APPROVED");
        workOrderMapper.updateById(workOrder);
    }

    /**
     * 计算合计金额
     */
    private void calculateTotalAmount(BizWorkOrder workOrder) {
        BigDecimal hours = workOrder.getHours() != null ? workOrder.getHours() : BigDecimal.ZERO;
        BigDecimal hourlyRate = workOrder.getHourlyRate() != null ? workOrder.getHourlyRate() : BigDecimal.ZERO;
        BigDecimal overtime = workOrder.getOvertime() != null ? workOrder.getOvertime() : BigDecimal.ZERO;
        BigDecimal overtimeRate = workOrder.getOvertimeRate() != null ? workOrder.getOvertimeRate() : BigDecimal.ZERO;
        BigDecimal total = hours.multiply(hourlyRate).add(overtime.multiply(overtimeRate));
        workOrder.setTotalAmount(total);
    }
}
