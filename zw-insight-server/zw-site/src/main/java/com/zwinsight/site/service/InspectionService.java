package com.zwinsight.site.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.mapper.BizInspectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 质量安全检查服务
 */
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final BizInspectionMapper inspectionMapper;

    /**
     * 分页查询
     */
    public PageResult<BizInspection> page(int page, int size, Long projectId, String inspectionType) {
        Page<BizInspection> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizInspection::getProjectId, projectId)
                .eq(StrUtil.isNotBlank(inspectionType), BizInspection::getInspectionType, inspectionType)
                .orderByDesc(BizInspection::getCreatedAt);
        Page<BizInspection> result = inspectionMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增检查记录
     */
    public void save(BizInspection inspection) {
        if (inspection.getHasProblem() == null) {
            inspection.setHasProblem(0);
        }
        inspectionMapper.insert(inspection);
    }

    /**
     * 指派整改（设置责任人和整改期限）
     */
    public void assignRectification(Long id, Long responsiblePersonId, LocalDate deadline) {
        BizInspection inspection = inspectionMapper.selectById(id);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }
        if (inspection.getHasProblem() == null || inspection.getHasProblem() != 1) {
            throw new BusinessException("该检查记录无问题，无需整改");
        }
        inspection.setResponsiblePersonId(responsiblePersonId);
        inspection.setRectificationDeadline(deadline);
        inspection.setRectificationStatus("PENDING");
        inspectionMapper.updateById(inspection);
    }
}
