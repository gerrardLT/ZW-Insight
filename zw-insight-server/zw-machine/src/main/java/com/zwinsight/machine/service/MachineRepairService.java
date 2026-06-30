package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineRepair;
import com.zwinsight.machine.mapper.BizMachineRepairMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 机械维修服务
 */
@Service
@RequiredArgsConstructor
public class MachineRepairService {

    private final BizMachineRepairMapper repairMapper;

    public PageResult<BizMachineRepair> page(int page, int size, Long machineId, Long projectId) {
        Page<BizMachineRepair> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineRepair> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(machineId != null, BizMachineRepair::getMachineId, machineId)
                .eq(projectId != null, BizMachineRepair::getProjectId, projectId)
                .orderByDesc(BizMachineRepair::getReportDate);
        return PageResult.of(repairMapper.selectPage(pageParam, wrapper));
    }

    public void report(BizMachineRepair repair) {
        repair.setReportDate(LocalDate.now());
        repair.setRepairStatus("REPORTED");
        repairMapper.insert(repair);
    }

    public void dispatch(Long id, String repairPerson) {
        BizMachineRepair repair = repairMapper.selectById(id);
        if (repair == null) throw new BusinessException("维修记录不存在");
        if (!"REPORTED".equals(repair.getRepairStatus())) throw new BusinessException("仅已报修状态可派工");
        repair.setRepairPerson(repairPerson);
        repair.setRepairStatus("DISPATCHED");
        repairMapper.updateById(repair);
    }

    public void complete(Long id, BizMachineRepair update) {
        BizMachineRepair repair = repairMapper.selectById(id);
        if (repair == null) throw new BusinessException("维修记录不存在");
        if (!"DISPATCHED".equals(repair.getRepairStatus()) && !"REPAIRING".equals(repair.getRepairStatus())) {
            throw new BusinessException("仅已派工或维修中状态可完成");
        }
        repair.setRepairDate(update.getRepairDate() != null ? update.getRepairDate() : LocalDate.now());
        repair.setRepairCost(update.getRepairCost());
        repair.setRepairStatus("COMPLETED");
        repairMapper.updateById(repair);
    }

    public List<BizMachineRepair> getHistory(Long machineId) {
        LambdaQueryWrapper<BizMachineRepair> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizMachineRepair::getMachineId, machineId)
                .orderByDesc(BizMachineRepair::getReportDate);
        return repairMapper.selectList(wrapper);
    }

    /**
     * 更新维修记录
     */
    public void update(BizMachineRepair repair) {
        repairMapper.updateById(repair);
    }

    /**
     * 删除维修记录
     */
    public void delete(Long id) {
        repairMapper.deleteById(id);
    }
}
