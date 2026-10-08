package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import com.zwinsight.machine.mapper.BizMachineWorkLogMapper;
import com.zwinsight.machine.util.MachineNameFiller;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 机械工作日志服务
 */
@Service
@RequiredArgsConstructor
public class MachineWorkLogService {

    private final BizMachineWorkLogMapper workLogMapper;
    private final BizMachineLedgerMapper ledgerMapper;
    private final BizProjectMapper projectMapper;
    private final com.zwinsight.machine.mapper.BizMachineContractMapper contractMapper;

    private void validateBinding(BizMachineWorkLog workLog) {
        Long tenantId = com.zwinsight.common.config.SecurityContextHolder.getTenantId();
        if (tenantId == null || workLog.getContractId() == null || workLog.getWorkDate() == null) {
            throw new BusinessException("租户、合同及工作日期必填");
        }
        var contract = contractMapper.selectById(workLog.getContractId());
        if (contract == null || !java.util.Objects.equals(tenantId, contract.getTenantId())
                || !java.util.Objects.equals(workLog.getProjectId(), contract.getProjectId())
                || !"EFFECTIVE".equals(contract.getStatus())) {
            throw new BusinessException("合同不存在、未生效或不属于本项目");
        }
        if (contract.getStartDate() == null || contract.getEndDate() == null
                || workLog.getWorkDate().isBefore(contract.getStartDate())
                || workLog.getWorkDate().isAfter(contract.getEndDate())) {
            throw new BusinessException("工作日期不在合同有效期内");
        }
        BizMachineLedger ledger = ledgerMapper.lockById(workLog.getMachineId(), tenantId);
        if (ledger == null || !java.util.Objects.equals(tenantId, ledger.getTenantId())
                || !"IN_FIELD".equals(ledger.getStatus())
                || !String.valueOf(workLog.getProjectId()).equals(ledger.getCurrentProject())) throw new BusinessException("仅本租户在场机械可记录工作日志");
        validateQuantities(workLog.getShiftCount(), workLog.getWorkQuantity());
    }

    private BizMachineWorkLog editable(Long id) {
        Long tenantId = com.zwinsight.common.config.SecurityContextHolder.getTenantId();
        if (tenantId == null) throw new BusinessException("缺少租户上下文");
        BizMachineWorkLog existing = workLogMapper.lockById(id, tenantId);
        if (existing == null) throw new BusinessException("工作日志不存在");
        if (!"DRAFT".equals(existing.getStatus()) || "SETTLED".equals(existing.getSettlementStatus())
                || workLogMapper.countOccupied(id, tenantId) != 0) throw new BusinessException("已确认、已结算或被结算占用的日志不可修改");
        return existing;
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        BizMachineWorkLog existing = editable(id);
        validateBinding(existing);
        existing.setStatus("CONFIRMED");
        if (workLogMapper.updateById(existing) != 1) throw new BusinessException("日志状态已变更，请重试");
    }

    public PageResult<BizMachineWorkLog> page(int page, int size, Long machineId, Long projectId, String machineName, String workDate) {
        // machineName 属台账展示字段，需先经 biz_machine_ledger 解析为 machineId 集合再过滤
        List<Long> nameMatchedIds = null;
        if (StrUtil.isNotBlank(machineName)) {
            LambdaQueryWrapper<BizMachineLedger> ledgerWrapper = new LambdaQueryWrapper<>();
            ledgerWrapper.like(BizMachineLedger::getMachineName, machineName);
            nameMatchedIds = ledgerMapper.selectList(ledgerWrapper).stream()
                    .map(BizMachineLedger::getId).collect(Collectors.toList());
            if (nameMatchedIds.isEmpty()) {
                return PageResult.of(new Page<>(page, size));
            }
        }
        LocalDate workDateValue = StrUtil.isNotBlank(workDate) ? LocalDate.parse(workDate) : null;
        Page<BizMachineWorkLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineWorkLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(machineId != null, BizMachineWorkLog::getMachineId, machineId)
                .eq(projectId != null, BizMachineWorkLog::getProjectId, projectId)
                .eq(workDateValue != null, BizMachineWorkLog::getWorkDate, workDateValue)
                .in(nameMatchedIds != null, BizMachineWorkLog::getMachineId, nameMatchedIds)
                .orderByDesc(BizMachineWorkLog::getWorkDate);
        Page<BizMachineWorkLog> result = workLogMapper.selectPage(pageParam, wrapper);
        MachineNameFiller.fill(result.getRecords(), ledgerMapper,
                BizMachineWorkLog::getMachineId, BizMachineWorkLog::setMachineName, null);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizMachineWorkLog::getProjectId, BizMachineWorkLog::setProjectName);
        return PageResult.of(result);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void save(BizMachineWorkLog workLog) {
        validateBinding(workLog);
        workLog.setId(null);
        workLog.setTenantId(com.zwinsight.common.config.SecurityContextHolder.getTenantId());
        workLog.setSettlementStatus("UNSETTLED");
        workLog.setStatus("DRAFT");
        workLogMapper.insert(workLog);
    }

    /** 台班数/工作量非负校验（null 视同 0 放行，兼容工作量计价模式） */
    private void validateQuantities(java.math.BigDecimal shiftCount, java.math.BigDecimal workQuantity) {
        if ((shiftCount != null && shiftCount.signum() < 0)
                || (workQuantity != null && workQuantity.signum() < 0)) {
            throw new BusinessException("台班数/工作量不可为负数");
        }
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void update(BizMachineWorkLog workLog) {
        BizMachineWorkLog existing = editable(workLog.getId());
        workLog.setProjectId(existing.getProjectId());
        workLog.setMachineId(existing.getMachineId());
        workLog.setTenantId(existing.getTenantId());
        workLog.setStatus("DRAFT");
        validateBinding(workLog);
        if (existing == null) throw new BusinessException("工作日志不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        // B4 修复（2026-08-11）：结算审批后 status 仍为 DRAFT 但 settlementStatus=SETTLED，
        // 已结算日志的台班数/工作量是结算金额依据，禁止篡改
        if ("SETTLED".equals(existing.getSettlementStatus())) throw new BusinessException("已结算的工作日志不可编辑");
        // P2 修复（2026-08-12，MAC-22/23）：结算状态置 null 防伪造；台班/工作量非负校验
        validateQuantities(workLog.getShiftCount(), workLog.getWorkQuantity());
        workLog.setSettlementStatus("UNSETTLED");
        workLogMapper.updateById(workLog);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizMachineWorkLog existing = editable(id);
        if (existing == null) throw new BusinessException("工作日志不存在");
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) throw new BusinessException("仅草稿状态可删除");
        // B4 修复：同上，已结算日志不可删除
        if ("SETTLED".equals(existing.getSettlementStatus())) throw new BusinessException("已结算的工作日志不可删除");
        workLogMapper.deleteById(id);
    }
}
