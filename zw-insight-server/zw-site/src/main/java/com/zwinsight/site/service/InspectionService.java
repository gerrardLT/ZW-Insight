package com.zwinsight.site.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizInspectionDetail;
import com.zwinsight.site.mapper.BizInspectionDetailMapper;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 质量安全检查服务
 */
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final BizInspectionMapper inspectionMapper;
    private final BizInspectionDetailMapper inspectionDetailMapper;
    private final BizProjectMapper projectMapper;

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
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizInspection::getProjectId, BizInspection::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 新增检查记录（含明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizInspection inspection) {
        if (inspection.getHasProblem() == null) {
            inspection.setHasProblem(0);
        }
        inspectionMapper.insert(inspection);
        saveDetails(inspection.getId(), inspection.getDetails());
    }

    /**
     * 查询检查记录详情（含明细）
     */
    public BizInspection getDetail(Long id) {
        BizInspection inspection = inspectionMapper.selectById(id);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }
        List<BizInspectionDetail> details = inspectionDetailMapper.selectList(
                new LambdaQueryWrapper<BizInspectionDetail>()
                        .eq(BizInspectionDetail::getInspectionId, id)
                        .orderByAsc(BizInspectionDetail::getSortOrder));
        inspection.setDetails(details);
        return inspection;
    }

    /**
     * 更新检查明细（删除后重建）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDetails(Long id, List<BizInspectionDetail> details) {
        BizInspection inspection = inspectionMapper.selectById(id);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }
        inspectionDetailMapper.deleteByInspectionId(id);
        saveDetails(id, details);
    }

    /**
     * 批量插入检查明细（统一设置 inspectionId、默认检查结果与排序）
     */
    private void saveDetails(Long inspectionId, List<BizInspectionDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        int sortOrder = 1;
        for (BizInspectionDetail detail : details) {
            detail.setId(null);
            detail.setInspectionId(inspectionId);
            if (StrUtil.isBlank(detail.getCheckResult())) {
                detail.setCheckResult("NOT_CHECKED");
            }
            detail.setSortOrder(sortOrder++);
            inspectionDetailMapper.insert(detail);
        }
    }

    /**
     * 更新检查记录
     */
    public void update(BizInspection inspection) {
        inspectionMapper.updateById(inspection);
    }

    /**
     * 删除检查记录
     */
    public void delete(Long id) {
        inspectionMapper.deleteById(id);
    }

    /**
     * 提交巡检结果
     */
    public void submitResults(Long id, java.util.Map<String, Object> results) {
        BizInspection inspection = inspectionMapper.selectById(id);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }
        if (results.containsKey("hasProblem")) {
            inspection.setHasProblem(Integer.valueOf(results.get("hasProblem").toString()));
        }
        if (results.containsKey("problemDescription")) {
            inspection.setProblemDescription(results.get("problemDescription").toString());
        }
        if (results.containsKey("inspectionContent")) {
            inspection.setInspectionContent(results.get("inspectionContent").toString());
        }
        inspectionMapper.updateById(inspection);
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
