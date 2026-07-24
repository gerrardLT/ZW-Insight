package com.zwinsight.labor.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 劳务工资单服务
 */
@Service
@RequiredArgsConstructor
public class LaborPayrollService {

    private final BizLaborPayrollMapper payrollMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizTeamMapper teamMapper;

    /**
     * 分页查询
     */
    public PageResult<BizLaborPayroll> page(int page, int size, Long projectId, Long teamId,
                                            String teamName, String status) {
        List<Long> teamMatchedIds = null;
        if (StrUtil.isNotBlank(teamName)) {
            LambdaQueryWrapper<BizTeam> teamWrapper = new LambdaQueryWrapper<>();
            teamWrapper.like(BizTeam::getTeamName, teamName);
            teamMatchedIds = teamMapper.selectList(teamWrapper).stream()
                    .map(BizTeam::getId).collect(Collectors.toList());
            if (teamMatchedIds.isEmpty()) {
                return PageResult.of(new Page<>(page, size));
            }
        }
        Page<BizLaborPayroll> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborPayroll> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborPayroll::getProjectId, projectId)
                .eq(teamId != null, BizLaborPayroll::getTeamId, teamId)
                .in(teamMatchedIds != null, BizLaborPayroll::getTeamId, teamMatchedIds)
                .eq(StrUtil.isNotBlank(status), BizLaborPayroll::getStatus, status)
                .orderByDesc(BizLaborPayroll::getCreatedAt);
        Page<BizLaborPayroll> result = payrollMapper.selectPage(pageParam, wrapper);
        fillTeamName(result.getRecords());
        return PageResult.of(result);
    }

    /**
     * 回填班组名称
     */
    private void fillTeamName(List<BizLaborPayroll> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> teamIds = records.stream()
                .map(BizLaborPayroll::getTeamId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (teamIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));
        records.forEach(r -> r.setTeamName(nameMap.get(r.getTeamId())));
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

    /**
     * 根据ID查询
     */
    public BizLaborPayroll getById(Long id) {
        BizLaborPayroll payroll = payrollMapper.selectById(id);
        if (payroll == null) {
            throw new BusinessException("工资单不存在");
        }
        return payroll;
    }

    /**
     * 更新工资单
     */
    public void update(BizLaborPayroll payroll) {
        BizLaborPayroll existing = payrollMapper.selectById(payroll.getId());
        if (existing == null) {
            throw new BusinessException("工资单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        payrollMapper.updateById(payroll);
    }

    /**
     * 删除工资单
     */
    public void delete(Long id) {
        BizLaborPayroll existing = payrollMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("工资单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        payrollMapper.deleteById(id);
    }
}
