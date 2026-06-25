package com.zwinsight.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.dto.ReminderStatsVO;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizReminderLogMapper;
import com.zwinsight.site.service.ReminderLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 整改催办日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderLogServiceImpl implements ReminderLogService {

    private final BizReminderLogMapper reminderLogMapper;
    private final BizInspectionMapper inspectionMapper;

    @Override
    public void saveLog(BizReminderLog reminderLog) {
        reminderLogMapper.insert(reminderLog);
        log.debug("催办日志保存成功, inspectionId={}, receiverId={}, level={}",
                reminderLog.getInspectionId(), reminderLog.getReceiverId(), reminderLog.getReminderLevel());
    }

    @Override
    public List<BizReminderLog> getLogsByInspectionId(Long inspectionId) {
        return reminderLogMapper.selectList(
                new LambdaQueryWrapper<BizReminderLog>()
                        .eq(BizReminderLog::getInspectionId, inspectionId)
                        .orderByDesc(BizReminderLog::getSentAt));
    }

    @Override
    public ReminderStatsVO getStatsByProjectId(Long projectId) {
        // 1. 查询项目下超期整改记录（status=PENDING, deadline<today）
        LocalDate today = LocalDate.now();
        Long totalOverdueCount = inspectionMapper.selectCount(
                new LambdaQueryWrapper<BizInspection>()
                        .eq(BizInspection::getProjectId, projectId)
                        .eq(BizInspection::getRectificationStatus, "PENDING")
                        .lt(BizInspection::getRectificationDeadline, today));

        // 2. 获取项目下所有检查记录ID
        List<Long> inspectionIds = inspectionMapper.selectList(
                new LambdaQueryWrapper<BizInspection>()
                        .select(BizInspection::getId)
                        .eq(BizInspection::getProjectId, projectId))
                .stream()
                .map(BizInspection::getId)
                .collect(Collectors.toList());

        if (inspectionIds.isEmpty()) {
            return ReminderStatsVO.builder()
                    .totalOverdueCount(totalOverdueCount)
                    .totalReminderCount(0L)
                    .escalatedCount(0L)
                    .build();
        }

        // 3. 统计已催办次数（所有日志条数）
        Long totalReminderCount = reminderLogMapper.selectCount(
                new LambdaQueryWrapper<BizReminderLog>()
                        .in(BizReminderLog::getInspectionId, inspectionIds));

        // 4. 统计已升级通知次数（ESCALATED 级别的日志条数）
        Long escalatedCount = reminderLogMapper.selectCount(
                new LambdaQueryWrapper<BizReminderLog>()
                        .in(BizReminderLog::getInspectionId, inspectionIds)
                        .eq(BizReminderLog::getReminderLevel, "ESCALATED"));

        return ReminderStatsVO.builder()
                .totalOverdueCount(totalOverdueCount)
                .totalReminderCount(totalReminderCount)
                .escalatedCount(escalatedCount)
                .build();
    }
}
