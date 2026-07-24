package com.zwinsight.machine.util;

import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 机械名称/编号回填工具
 * <p>
 * 机械业务列表实体通常仅持久化 machineId，而前端列表需要展示机械名称（machineName）与编号（machineCode）。
 * 本工具通过一次批量查询 biz_machine_ledger 构建 id→台账 映射后统一回填，避免逐条查询（N+1）。
 * machineName / machineCode 字段在业务实体中以 {@code @TableField(exist = false)} 声明，不参与持久化。
 * </p>
 */
public final class MachineNameFiller {

    private MachineNameFiller() {
    }

    /**
     * 批量回填机械名称与编号。
     *
     * @param records         列表记录
     * @param ledgerMapper    机械台账 Mapper
     * @param getMachineId    记录 → machineId 提取函数
     * @param setMachineName  记录 + machineName 赋值函数
     * @param setMachineCode  记录 + machineCode 赋值函数（可为 null，忽略编号回填）
     * @param <T>             记录类型
     */
    public static <T> void fill(List<T> records,
                                BizMachineLedgerMapper ledgerMapper,
                                Function<T, Long> getMachineId,
                                BiConsumer<T, String> setMachineName,
                                BiConsumer<T, String> setMachineCode) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> machineIds = records.stream()
                .map(getMachineId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (machineIds.isEmpty()) {
            return;
        }
        Map<Long, BizMachineLedger> ledgerMap = ledgerMapper.selectBatchIds(machineIds).stream()
                .collect(Collectors.toMap(BizMachineLedger::getId, l -> l, (a, b) -> a));
        for (T record : records) {
            Long machineId = getMachineId.apply(record);
            if (machineId == null) {
                continue;
            }
            BizMachineLedger ledger = ledgerMap.get(machineId);
            if (ledger == null) {
                continue;
            }
            if (setMachineName != null) {
                setMachineName.accept(record, ledger.getMachineName());
            }
            if (setMachineCode != null) {
                setMachineCode.accept(record, ledger.getMachineCode());
            }
        }
    }
}
