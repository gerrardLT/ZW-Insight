package com.zwinsight.machine.service;

import cn.hutool.core.util.StrUtil;
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

/**
 * 机械台账服务
 */
@Service
@RequiredArgsConstructor
public class MachineLedgerService {

    private final BizMachineLedgerMapper ledgerMapper;
    private final BizMachineEntryMapper entryMapper;

    public PageResult<BizMachineLedger> page(int page, int size, String machineName, String machineType) {
        Page<BizMachineLedger> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineLedger> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(machineName), BizMachineLedger::getMachineName, machineName)
                .eq(StrUtil.isNotBlank(machineType), BizMachineLedger::getMachineType, machineType)
                .orderByDesc(BizMachineLedger::getCreatedAt);
        return PageResult.of(ledgerMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizMachineLedger ledger) {
        ledger.setStatus("REGISTERED");
        ledgerMapper.insert(ledger);
    }

    public void update(BizMachineLedger ledger) {
        BizMachineLedger existing = ledgerMapper.selectById(ledger.getId());
        if (existing == null) throw new BusinessException("机械台账不存在");
        ledgerMapper.updateById(ledger);
    }

    public void delete(Long id) {
        // 检查是否有进退场记录
        Long count = entryMapper.selectCount(
                new LambdaQueryWrapper<BizMachineEntry>().eq(BizMachineEntry::getMachineId, id));
        if (count > 0) throw new BusinessException("该机械有进退场记录，不可删除");
        ledgerMapper.deleteById(id);
    }
}
