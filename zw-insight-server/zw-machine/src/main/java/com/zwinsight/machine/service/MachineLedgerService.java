package com.zwinsight.machine.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.reference.ReferenceCheck;
import com.zwinsight.common.reference.ReferenceRelation;
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

    /**
     * 删除（引用校验：进出场、工作量）
     * <p>注：机械合同表（biz_machine_contract）以 project_id/supplier_id 关联，不含 machine_id，
     * 故不做机械合同引用校验；进出场/工作量表无单据编号列，引用详情仅展示类型。</p>
     */
    @ReferenceCheck({
            @ReferenceRelation(tableName = "biz_machine_entry", column = "machine_id",
                    displayName = "进出场记录", codeColumn = ""),
            @ReferenceRelation(tableName = "biz_machine_work_log", column = "machine_id",
                    displayName = "工作量记录", codeColumn = "")
    })
    public void delete(Long id) {
        ledgerMapper.deleteById(id);
    }
}
