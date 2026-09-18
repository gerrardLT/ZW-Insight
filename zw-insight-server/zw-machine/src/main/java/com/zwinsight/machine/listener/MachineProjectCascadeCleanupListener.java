package com.zwinsight.machine.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.domain.BizMachineEntry;
import com.zwinsight.machine.domain.BizMachineOilRecord;
import com.zwinsight.machine.domain.BizMachineRepair;
import com.zwinsight.machine.domain.BizMachineSettlement;
import com.zwinsight.machine.domain.BizMachineUsageRecord;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import com.zwinsight.machine.domain.BizMachineWorkSettlement;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import com.zwinsight.machine.mapper.BizMachineEntryMapper;
import com.zwinsight.machine.mapper.BizMachineOilRecordMapper;
import com.zwinsight.machine.mapper.BizMachineRepairMapper;
import com.zwinsight.machine.mapper.BizMachineSettlementMapper;
import com.zwinsight.machine.mapper.BizMachineUsageRecordMapper;
import com.zwinsight.machine.mapper.BizMachineWorkLogMapper;
import com.zwinsight.machine.mapper.BizMachineWorkSettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-machine 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 8 张表。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——机械进场 18+3 条、
 * 工作量记录 9+3 条、工作量结算 8 条、机械合同 1+15 条、维修 3 条。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MachineProjectCascadeCleanupListener {

    private final BizMachineContractMapper machineContractMapper;
    private final BizMachineEntryMapper machineEntryMapper;
    private final BizMachineWorkLogMapper machineWorkLogMapper;
    private final BizMachineWorkSettlementMapper machineWorkSettlementMapper;
    private final BizMachineSettlementMapper machineSettlementMapper;
    private final BizMachineUsageRecordMapper machineUsageRecordMapper;
    private final BizMachineOilRecordMapper machineOilRecordMapper;
    private final BizMachineRepairMapper machineRepairMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int contracts = machineContractMapper.delete(
                new QueryWrapper<BizMachineContract>().eq("project_id", projectId));
        int entries = machineEntryMapper.delete(
                new QueryWrapper<BizMachineEntry>().eq("project_id", projectId));
        int workLogs = machineWorkLogMapper.delete(
                new QueryWrapper<BizMachineWorkLog>().eq("project_id", projectId));
        int workSettlements = machineWorkSettlementMapper.delete(
                new QueryWrapper<BizMachineWorkSettlement>().eq("project_id", projectId));
        int settlements = machineSettlementMapper.delete(
                new QueryWrapper<BizMachineSettlement>().eq("project_id", projectId));
        int usageRecords = machineUsageRecordMapper.delete(
                new QueryWrapper<BizMachineUsageRecord>().eq("project_id", projectId));
        int oilRecords = machineOilRecordMapper.delete(
                new QueryWrapper<BizMachineOilRecord>().eq("project_id", projectId));
        int repairs = machineRepairMapper.delete(
                new QueryWrapper<BizMachineRepair>().eq("project_id", projectId));

        log.info("项目删除级联清理[machine]完成, projectId={}, 合同={} 进场={} 工作量={} "
                        + "工作量结算={} 结算={} 使用记录={} 油耗={} 维修={}",
                projectId, contracts, entries, workLogs, workSettlements,
                settlements, usageRecords, oilRecords, repairs);
    }
}
