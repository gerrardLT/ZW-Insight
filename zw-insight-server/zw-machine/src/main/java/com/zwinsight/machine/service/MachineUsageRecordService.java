package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineUsageRecord;
import com.zwinsight.machine.mapper.BizMachineUsageRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 机械使用记录服务
 */
@Service
@RequiredArgsConstructor
public class MachineUsageRecordService {

    private final BizMachineUsageRecordMapper usageRecordMapper;

    /**
     * 分页查询
     * <p>2026-10-10：补 startDate/endDate 条件——机械结算单新建页按「结算周期」拉取使用记录时
     * 传了这两个参数，但后端此前无入参，周期筛选被忽略导致预览合计不按周期口径。</p>
     */
    public PageResult<BizMachineUsageRecord> page(int page, int size, Long projectId, Long contractId,
                                                 LocalDate startDate, LocalDate endDate) {
        Page<BizMachineUsageRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineUsageRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMachineUsageRecord::getProjectId, projectId)
                .eq(contractId != null, BizMachineUsageRecord::getContractId, contractId)
                .ge(startDate != null, BizMachineUsageRecord::getRecordDate, startDate)
                .le(endDate != null, BizMachineUsageRecord::getRecordDate, endDate)
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
