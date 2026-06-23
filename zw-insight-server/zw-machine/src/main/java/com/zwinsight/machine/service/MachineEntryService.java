package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.machine.domain.BizMachineEntry;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.mapper.BizMachineEntryMapper;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 机械进退场服务
 */
@Service
@RequiredArgsConstructor
public class MachineEntryService {

    private final BizMachineEntryMapper entryMapper;
    private final BizMachineLedgerMapper ledgerMapper;

    /**
     * 进场（仅REGISTERED/OUT_FIELD可进场）
     */
    @Transactional(rollbackFor = Exception.class)
    public void entryIn(BizMachineEntry entry) {
        BizMachineLedger ledger = ledgerMapper.selectById(entry.getMachineId());
        if (ledger == null) throw new BusinessException("机械不存在");
        if (!"REGISTERED".equals(ledger.getStatus()) && !"OUT_FIELD".equals(ledger.getStatus())) {
            throw new BusinessException("仅已登记或已退场的机械可进场");
        }
        entry.setEntryType("IN");
        entryMapper.insert(entry);

        ledger.setStatus("IN_FIELD");
        ledgerMapper.updateById(ledger);
    }

    /**
     * 退场（仅IN_FIELD可出场）
     */
    @Transactional(rollbackFor = Exception.class)
    public void entryOut(BizMachineEntry entry) {
        BizMachineLedger ledger = ledgerMapper.selectById(entry.getMachineId());
        if (ledger == null) throw new BusinessException("机械不存在");
        if (!"IN_FIELD".equals(ledger.getStatus())) {
            throw new BusinessException("仅在场的机械可退场");
        }
        entry.setEntryType("OUT");
        entryMapper.insert(entry);

        ledger.setStatus("OUT_FIELD");
        ledgerMapper.updateById(ledger);
    }

    /**
     * 按机械查询进退场记录
     */
    public List<BizMachineEntry> getByMachine(Long machineId) {
        LambdaQueryWrapper<BizMachineEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizMachineEntry::getMachineId, machineId)
                .orderByDesc(BizMachineEntry::getEntryDate);
        return entryMapper.selectList(wrapper);
    }
}
