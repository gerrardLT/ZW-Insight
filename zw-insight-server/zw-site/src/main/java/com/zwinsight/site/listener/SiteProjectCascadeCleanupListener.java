package com.zwinsight.site.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.site.domain.BizCompletionAcceptance;
import com.zwinsight.site.domain.BizConstructionLog;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizRectification;
import com.zwinsight.site.domain.BizScheduleFeedback;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizCompletionAcceptanceMapper;
import com.zwinsight.site.mapper.BizConstructionLogMapper;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizRectificationMapper;
import com.zwinsight.site.mapper.BizScheduleFeedbackMapper;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import com.zwinsight.site.sign.BizSignRecord;
import com.zwinsight.site.sign.BizSignRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-site 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 7 张表：
 * 竣工验收、施工日志、检查/整改、进度计划/反馈、定位签到。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p><b>包结构注意</b>：{@code BizSignRecord} 与其 Mapper 位于
 * {@code com.zwinsight.site.sign} 子包（定位签到是独立功能域，未按
 * domain/mapper 分层），import 路径与本模块其余 6 个实体不同。</p>
 *
 * <p><b>不纳入级联</b>：biz_inspection_detail 以 {@code inspection_id} 关联；
 * biz_reminder_config / biz_reminder_log 为提醒配置与投递日志，按业务键
 * （而非 project_id）组织；三者均无 {@code project_id} 列。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——竣工验收 4 条、
 * 检查 3+3 条、施工日志 3 条、进度计划 3 条、进度反馈 3 条。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteProjectCascadeCleanupListener {

    private final BizCompletionAcceptanceMapper completionAcceptanceMapper;
    private final BizConstructionLogMapper constructionLogMapper;
    private final BizInspectionMapper inspectionMapper;
    private final BizRectificationMapper rectificationMapper;
    private final BizSchedulePlanMapper schedulePlanMapper;
    private final BizScheduleFeedbackMapper scheduleFeedbackMapper;
    private final BizSignRecordMapper signRecordMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int completions = completionAcceptanceMapper.delete(
                new QueryWrapper<BizCompletionAcceptance>().eq("project_id", projectId));
        int constructionLogs = constructionLogMapper.delete(
                new QueryWrapper<BizConstructionLog>().eq("project_id", projectId));
        int inspections = inspectionMapper.delete(
                new QueryWrapper<BizInspection>().eq("project_id", projectId));
        int rectifications = rectificationMapper.delete(
                new QueryWrapper<BizRectification>().eq("project_id", projectId));
        int schedulePlans = schedulePlanMapper.delete(
                new QueryWrapper<BizSchedulePlan>().eq("project_id", projectId));
        int scheduleFeedbacks = scheduleFeedbackMapper.delete(
                new QueryWrapper<BizScheduleFeedback>().eq("project_id", projectId));
        int signRecords = signRecordMapper.delete(
                new QueryWrapper<BizSignRecord>().eq("project_id", projectId));

        log.info("项目删除级联清理[site]完成, projectId={}, 竣工验收={} 施工日志={} 检查={} "
                        + "整改={} 进度计划={} 进度反馈={} 签到={}",
                projectId, completions, constructionLogs, inspections, rectifications,
                schedulePlans, scheduleFeedbacks, signRecords);
    }
}
