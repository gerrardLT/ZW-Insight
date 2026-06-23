package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.domain.BizMachineOilRecord;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import com.zwinsight.machine.mapper.BizMachineOilRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 机械加油记录服务
 */
@Service
@RequiredArgsConstructor
public class MachineOilRecordService {

    private final BizMachineOilRecordMapper oilRecordMapper;
    private final BizMachineLedgerMapper ledgerMapper;

    public PageResult<BizMachineOilRecord> page(int page, int size, Long machineId, Long projectId) {
        Page<BizMachineOilRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineOilRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(machineId != null, BizMachineOilRecord::getMachineId, machineId)
                .eq(projectId != null, BizMachineOilRecord::getProjectId, projectId)
                .orderByDesc(BizMachineOilRecord::getOilDate);
        return PageResult.of(oilRecordMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizMachineOilRecord record) {
        // 仅IN_FIELD可记录
        BizMachineLedger ledger = ledgerMapper.selectById(record.getMachineId());
        if (ledger == null) throw new BusinessException("机械不存在");
        if (!"IN_FIELD".equals(ledger.getStatus())) throw new BusinessException("仅在场机械可记录加油");
        oilRecordMapper.insert(record);
    }
}
