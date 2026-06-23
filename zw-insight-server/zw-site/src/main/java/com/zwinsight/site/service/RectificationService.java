package com.zwinsight.site.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizRectification;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizRectificationMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 整改服务
 */
@Service
@RequiredArgsConstructor
public class RectificationService {

    private final BizRectificationMapper rectificationMapper;
    private final BizInspectionMapper inspectionMapper;
    private final ApprovalService approvalService;

    /**
     * 提交整改结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long inspectionId, BizRectification rectification) {
        BizInspection inspection = inspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }
        if (!"PENDING".equals(inspection.getRectificationStatus())) {
            throw new BusinessException("当前状态不允许提交整改");
        }

        rectification.setInspectionId(inspectionId);
        rectification.setProjectId(inspection.getProjectId());
        rectification.setStatus("SUBMITTED");
        rectificationMapper.insert(rectification);

        // 更新检查记录的整改状态
        inspection.setRectificationStatus("SUBMITTED");
        inspection.setRectificationDate(LocalDate.now());
        inspectionMapper.updateById(inspection);

        // 发起整改审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("inspectionId", inspectionId);
        variables.put("projectId", inspection.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "RECTIFICATION", rectification.getId(), "rectification_approval", variables);
        rectification.setWorkflowInstanceId(processInstanceId);
        rectificationMapper.updateById(rectification);
    }

    /**
     * 审批整改（通过→更新inspection的rectificationStatus为APPROVED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        BizRectification rectification = rectificationMapper.selectById(id);
        if (rectification == null) {
            throw new BusinessException("整改记录不存在");
        }
        if (!"SUBMITTED".equals(rectification.getStatus())) {
            throw new BusinessException("仅已提交状态可审批");
        }

        rectification.setStatus("APPROVED");
        rectificationMapper.updateById(rectification);

        // 更新检查记录的整改状态为已通过
        BizInspection inspection = inspectionMapper.selectById(rectification.getInspectionId());
        if (inspection != null) {
            inspection.setRectificationStatus("APPROVED");
            inspectionMapper.updateById(inspection);
        }
    }
}
