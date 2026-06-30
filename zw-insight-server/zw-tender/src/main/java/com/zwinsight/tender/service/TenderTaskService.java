package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.tender.domain.BizTenderTask;
import com.zwinsight.tender.mapper.BizTenderTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 投标任务服务
 */
@Service
@RequiredArgsConstructor
public class TenderTaskService {

    private final BizTenderTaskMapper taskMapper;

    /**
     * 查询指定登记的任务列表
     */
    public List<BizTenderTask> list(Long registerId) {
        LambdaQueryWrapper<BizTenderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTenderTask::getRegisterId, registerId)
                .orderByAsc(BizTenderTask::getDeadline);
        return taskMapper.selectList(wrapper);
    }

    /**
     * 新增任务
     */
    public void save(BizTenderTask task) {
        if (task.getStatus() == null) {
            task.setStatus("PENDING");
        }
        taskMapper.insert(task);
    }

    /**
     * 完成任务
     */
    public void complete(Long id) {
        BizTenderTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("投标任务不存在");
        }
        task.setStatus("COMPLETED");
        taskMapper.updateById(task);
    }

    /**
     * 更新任务
     */
    public void update(BizTenderTask task) {
        taskMapper.updateById(task);
    }

    /**
     * 删除任务
     */
    public void delete(Long id) {
        taskMapper.deleteById(id);
    }
}
