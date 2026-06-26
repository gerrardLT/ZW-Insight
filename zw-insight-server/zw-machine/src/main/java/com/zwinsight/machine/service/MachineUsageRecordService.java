package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineUsageRecord;
import com.zwinsight.machine.mapper.BizMachineUsageRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 机械使用记录服务
 */
@Service
@RequiredArgsConstructor
public class MachineUsageRecordService {

    private final BizMachineUsageRecordMapper usageRecordMapper;

    public PageResult<BizMachineUsageRecord> page(int page, int size, Long projectId, Long contractId) {
        Page<BizMachineUsageRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineUsageRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMachineUsageRecord::getProjectId, projectId)
                .eq(contractId != null, BizMachineUsageRecord::getContractId, contractId)
                .orderByDesc(BizMachineUsageRecord::getCreatedAt);
        return PageResult.of(usageRecordMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizMachineUsageRecord record) {
        usageRecordMapper.insert(record);
    }

    public void update(BizMachineUsageRecord record) {
        BizMachineUsageRecord existing = usageRecordMapper.selectById(record.getId());
        if (existing == null) throw new BusinessException("机械使用记录不存在");
        usageRecordMapper.updateById(record);
    }

    public void delete(Long id) {
        usageRecordMapper.deleteById(id);
    }
}
