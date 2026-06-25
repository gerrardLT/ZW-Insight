package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
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
     * 分页查询进退场记录
     */
    public PageResult<BizMachineEntry> page(int page, int size, Long machineId, Long projectId) {
        Page<BizMachineEntry> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(machineId != null, BizMachineEntry::getMachineId, machineId)
                .eq(projectId != null, BizMachineEntry::getProjectId, projectId)
                .orderByDesc(BizMachineEntry::getEntryDate);
        return PageResult.of(entryMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 更新
     */
    public void update(BizMachineEntry entry) {
        entryMapper.updateById(entry);
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        entryMapper.deleteById(id);
    }

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
