package com.zwinsight.site.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizRectification;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizRectificationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 整改服务
 * <p>
 * 整改审批为检查人现场确认模式：提交整改（SUBMITTED）→ 检查人经
 * /{id}/approve 端点验收（APPROVED），前端 approveRectification 即走此端点。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RectificationService {

    private final BizRectificationMapper rectificationMapper;
    private final BizInspectionMapper inspectionMapper;
    private final ReminderDeduplicationService reminderDeduplicationService;

    /**
     * 查询某条检查记录下的整改记录（按提交时间倒序，无记录返回空列表）
     */
    public List<BizRectification> listByInspection(Long inspectionId) {
        return rectificationMapper.selectList(
                new LambdaQueryWrapper<BizRectification>()
                        .eq(BizRectification::getInspectionId, inspectionId)
                        .orderByDesc(BizRectification::getCreatedAt)
        );
    }

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
        // P1 修复（2026-08-13，批次三取证枚举）：移除对 rectification_approval 的 startProcess
        // 调用——该流程定义从未存在（无 BPMN、不在 deploy-bpmn.sh 清单、spec 无定义），
        // 导致提交整改必然 500。整改验收走 /{id}/approve 端点（前端一致）。
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
