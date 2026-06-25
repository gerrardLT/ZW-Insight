package com.zwinsight.site.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizRectification;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizRectificationMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 整改服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RectificationService {

    private final BizRectificationMapper rectificationMapper;
    private final BizInspectionMapper inspectionMapper;
    private final ApprovalService approvalService;
    private final ReminderDeduplicationService reminderDeduplicationService;

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

        // 清除催办标记：SUBMITTED 状态下暂停催办
        // 设计说明：RectificationReminderTask 仅查询 PENDING 状态的记录(Requirement 6.2)，
        // 因此 SUBMITTED 状态的记录不会被扫描到。此处额外清除 Redis 催办标记，
        // 确保即使存在时间窗口竞争也不会误发催办。
        try {
            reminderDeduplicationService.clearMarks(inspectionId);
        } catch (Exception e) {
            log.warn("清除催办标记失败(SUBMITTED), inspectionId={}: {}", inspectionId, e.getMessage());
        }

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

            // 整改通过，清除所有催办标记(Requirement 9.3)
            // 整改已完成，后续不再需要催办
            try {
                reminderDeduplicationService.clearMarks(rectification.getInspectionId());
            } catch (Exception e) {
                log.warn("清除催办标记失败(APPROVED), inspectionId={}: {}", rectification.getInspectionId(), e.getMessage());
            }
        }
    }
}
