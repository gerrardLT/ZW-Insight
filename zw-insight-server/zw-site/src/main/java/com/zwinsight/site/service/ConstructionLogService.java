package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.site.domain.BizConstructionLog;
import com.zwinsight.site.mapper.BizConstructionLogMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 施工日志服务
 */
@Service
@RequiredArgsConstructor
public class ConstructionLogService {

    private final BizConstructionLogMapper logMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询（支持日期范围）
     */
    public PageResult<BizConstructionLog> page(int page, int size, Long projectId,
                                                LocalDate startDate, LocalDate endDate) {
        Page<BizConstructionLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizConstructionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizConstructionLog::getProjectId, projectId)
                .ge(startDate != null, BizConstructionLog::getLogDate, startDate)
                .le(endDate != null, BizConstructionLog::getLogDate, endDate)
                .orderByDesc(BizConstructionLog::getLogDate);
        Page<BizConstructionLog> result = logMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizConstructionLog::getProjectId, BizConstructionLog::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 新增施工日志
     */
    public void save(BizConstructionLog log) {
        logMapper.insert(log);
    }

    /**
     * 更新施工日志
     */
    public void update(BizConstructionLog log) {
        BizConstructionLog existing = logMapper.selectById(log.getId());
        if (existing == null) {
            throw new BusinessException("施工日志不存在");
        }
        logMapper.updateById(log);
    }

    /**
     * 删除施工日志
     */
    public void delete(Long id) {
        BizConstructionLog existing = logMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("施工日志不存在");
        }
        logMapper.deleteById(id);
    }
}
