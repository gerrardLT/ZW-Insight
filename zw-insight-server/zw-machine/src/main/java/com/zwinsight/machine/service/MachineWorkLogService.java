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

    public void save(BizMachineWorkLog workLog) {
        // 仅IN_FIELD的机械可记录
        BizMachineLedger ledger = ledgerMapper.selectById(workLog.getMachineId());
        if (ledger == null) throw new BusinessException("机械不存在");
        if (!"IN_FIELD".equals(ledger.getStatus())) throw new BusinessException("仅在场机械可记录工作日志");
        // P2 修复（2026-08-12，批次二 MAC-23）：台班/工作量非负校验，
        // 负值会经结算汇总扣减合同累计结算
        validateQuantities(workLog.getShiftCount(), workLog.getWorkQuantity());
        // P2 修复（MAC-22）：结算状态由结算链路维护，防创建时伪造 SETTLED 绕过退场守卫
        workLog.setSettlementStatus(null);
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

    public void update(BizMachineWorkLog workLog) {
        BizMachineWorkLog existing = workLogMapper.selectById(workLog.getId());
        if (existing == null) throw new BusinessException("工作日志不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        // B4 修复（2026-08-11）：结算审批后 status 仍为 DRAFT 但 settlementStatus=SETTLED，
        // 已结算日志的台班数/工作量是结算金额依据，禁止篡改
        if ("SETTLED".equals(existing.getSettlementStatus())) throw new BusinessException("已结算的工作日志不可编辑");
        // P2 修复（2026-08-12，MAC-22/23）：结算状态置 null 防伪造；台班/工作量非负校验
        validateQuantities(workLog.getShiftCount(), workLog.getWorkQuantity());
        workLog.setSettlementStatus(null);
        workLogMapper.updateById(workLog);
    }

    public void delete(Long id) {
        BizMachineWorkLog existing = workLogMapper.selectById(id);
        if (existing == null) throw new BusinessException("工作日志不存在");
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) throw new BusinessException("仅草稿状态可删除");
        // B4 修复：同上，已结算日志不可删除
        if ("SETTLED".equals(existing.getSettlementStatus())) throw new BusinessException("已结算的工作日志不可删除");
        workLogMapper.deleteById(id);
    }
}
