package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import com.zwinsight.machine.mapper.BizMachineWorkLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 机械工作日志服务
 */
@Service
@RequiredArgsConstructor
public class MachineWorkLogService {

    private final BizMachineWorkLogMapper workLogMapper;
    private final BizMachineLedgerMapper ledgerMapper;

    public PageResult<BizMachineWorkLog> page(int page, int size, Long machineId, Long projectId) {
        Page<BizMachineWorkLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineWorkLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(machineId != null, BizMachineWorkLog::getMachineId, machineId)
                .eq(projectId != null, BizMachineWorkLog::getProjectId, projectId)
                .orderByDesc(BizMachineWorkLog::getWorkDate);
        return PageResult.of(workLogMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizMachineWorkLog workLog) {
        // 仅IN_FIELD的机械可记录
        BizMachineLedger ledger = ledgerMapper.selectById(workLog.getMachineId());
        if (ledger == null) throw new BusinessException("机械不存在");
        if (!"IN_FIELD".equals(ledger.getStatus())) throw new BusinessException("仅在场机械可记录工作日志");
        workLog.setStatus("DRAFT");
        workLogMapper.insert(workLog);
    }

    public void update(BizMachineWorkLog workLog) {
        BizMachineWorkLog existing = workLogMapper.selectById(workLog.getId());
        if (existing == null) throw new BusinessException("工作日志不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        workLogMapper.updateById(workLog);
    }

    public void delete(Long id) {
        BizMachineWorkLog existing = workLogMapper.selectById(id);
        if (existing == null) throw new BusinessException("工作日志不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        workLogMapper.deleteById(id);
    }
}
