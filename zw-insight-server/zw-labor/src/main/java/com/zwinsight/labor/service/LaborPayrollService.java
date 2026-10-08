package com.zwinsight.labor.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizLaborPayrollDetail;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizLaborPayrollDetailMapper;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 劳务工资单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaborPayrollService {

    private final BizLaborPayrollMapper payrollMapper;
    private final BizLaborPayrollDetailMapper payrollDetailMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizTeamMapper teamMapper;
    private final ApprovalService approvalService;

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
     * 保存工资单（按周期/班组汇总工单，并落地工单明细快照 LI-3）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizLaborPayroll payroll) {
        // 同班组同用工类型周期重叠检查
        LambdaQueryWrapper<BizLaborPayroll> overlapWrapper = new LambdaQueryWrapper<>();
        overlapWrapper.eq(BizLaborPayroll::getTeamId, payroll.getTeamId())
                .eq(payroll.getOrderType() != null, BizLaborPayroll::getOrderType, payroll.getOrderType())
                .le(BizLaborPayroll::getPeriodStart, payroll.getPeriodEnd())
                .ge(BizLaborPayroll::getPeriodEnd, payroll.getPeriodStart());
        Long overlapCount = payrollMapper.selectCount(overlapWrapper);
        if (overlapCount != null && overlapCount > 0) {
            throw new BusinessException("该班组在此周期内已存在工资单，不可重复创建（周期重叠）");
        }

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
        payroll.setWorkflowInstanceId(null);
        payrollMapper.insert(payroll);

        // 落地工单快照明细（LI-3）
        for (BizWorkOrder wo : workOrders) {
            BizLaborPayrollDetail detail = new BizLaborPayrollDetail();
            detail.setPayrollId(payroll.getId());
            detail.setWorkOrderId(wo.getId());
            detail.setWorkerId(wo.getWorkerId());
            detail.setWorkerName(wo.getWorkerName());
            detail.setOrderType(wo.getOrderType());
            detail.setWorkDate(wo.getWorkDate());
            detail.setAmount(wo.getTotalAmount() != null ? wo.getTotalAmount() : BigDecimal.ZERO);
            payrollDetailMapper.insert(detail);
        }
    }

    /**
     * 提交审批
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

        Map<String, Object> variables = new HashMap<>();
        variables.put("totalSettlement", payroll.getTotalSettlement());
        variables.put("projectId", payroll.getProjectId());
        variables.put("teamId", payroll.getTeamId());
        String processInstanceId = approvalService.startProcess(
                "LABOR_PAYROLL", id, "labor_payroll_approval", variables);

        payroll.setWorkflowInstanceId(processInstanceId);
        payroll.setStatus("SUBMITTED");
        payrollMapper.updateById(payroll);
    }

    /**
     * 审批通过回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizLaborPayroll payroll = payrollMapper.selectById(id);
        if (payroll == null) {
            log.warn("劳务工资单审批通过回调：单据不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(payroll.getStatus())) {
            log.info("劳务工资单已生效，跳过重复回调, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(payroll.getStatus())) {
            log.warn("劳务工资单当前状态非 SUBMITTED，忽略生效回调: id={}, status={}", id, payroll.getStatus());
            return;
        }

        payroll.setStatus("APPROVED");
        payrollMapper.updateById(payroll);
        log.info("劳务工资单审批通过: id={}", id);
    }

    /**
     * 审批驳回回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizLaborPayroll payroll = payrollMapper.selectById(id);
        if (payroll == null) {
            log.warn("劳务工资单审批驳回回调：单据不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(payroll.getStatus())) {
            log.warn("劳务工资单当前状态非 SUBMITTED，忽略驳回回调: id={}, status={}", id, payroll.getStatus());
            return;
        }

        payroll.setStatus("DRAFT");
        payrollMapper.updateById(payroll);
        log.info("劳务工资单审批驳回回退草稿: id={}", id);
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
     * 查询工资单明细快照
     */
    public List<BizLaborPayrollDetail> listDetails(Long payrollId) {
        LambdaQueryWrapper<BizLaborPayrollDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizLaborPayrollDetail::getPayrollId, payrollId)
                .orderByAsc(BizLaborPayrollDetail::getWorkDate);
        return payrollDetailMapper.selectList(wrapper);
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

        Long teamId = payroll.getTeamId() != null ? payroll.getTeamId() : existing.getTeamId();
        String orderType = payroll.getOrderType() != null ? payroll.getOrderType() : existing.getOrderType();
        LocalDate periodStart = payroll.getPeriodStart() != null ? payroll.getPeriodStart() : existing.getPeriodStart();
        LocalDate periodEnd = payroll.getPeriodEnd() != null ? payroll.getPeriodEnd() : existing.getPeriodEnd();
        if (teamId != null && periodStart != null && periodEnd != null) {
            LambdaQueryWrapper<BizLaborPayroll> overlapWrapper = new LambdaQueryWrapper<>();
            overlapWrapper.eq(BizLaborPayroll::getTeamId, teamId)
                    .eq(orderType != null, BizLaborPayroll::getOrderType, orderType)
                    .le(BizLaborPayroll::getPeriodStart, periodEnd)
                    .ge(BizLaborPayroll::getPeriodEnd, periodStart)
                    .ne(BizLaborPayroll::getId, payroll.getId());
            Long overlapCount = payrollMapper.selectCount(overlapWrapper);
            if (overlapCount != null && overlapCount > 0) {
                throw new BusinessException("该班组在此周期内已存在工资单，不可重复创建（周期重叠）");
            }
        }

        payroll.setStatus(null);
        payroll.setTotalSettlement(null);
        payroll.setTotalPaid(null);
        payroll.setUnpaid(null);
        payroll.setWorkflowInstanceId(null);

        payrollMapper.updateById(payroll);
    }

    /**
     * 删除工资单（级联删除快照）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizLaborPayroll existing = payrollMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("工资单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        payrollDetailMapper.delete(new LambdaQueryWrapper<BizLaborPayrollDetail>()
                .eq(BizLaborPayrollDetail::getPayrollId, id));
        payrollMapper.deleteById(id);
    }
}
