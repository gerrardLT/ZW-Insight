package com.zwinsight.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectStatusLog;
import com.zwinsight.project.domain.enums.ProjectEvent;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectStatusLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 项目状态机（P1-M1 深度优化，蓝图 §5.1/§5.2 不变量 I1-I3 的执行者）。
 * <p>
 * <b>转移边表</b>是状态变更的唯一合法通道：任何模块（投标/合同/现场/本模块）
 * 不得再散落 setStatus 直写，统一经 {@link #fire}。非法边直接拒绝（I1）；
 * START_CONSTRUCTION 带开工准入守卫（I2，需存在 EFFECTIVE 施工合同）；
 * 周期字段随事件回写（I3：开工记实际开工日、竣工记实际竣工日，均只首次落值）。
 * 每次流转写一条 biz_project_status_log（审计 + 大事记时间线）。
 * </p>
 * <p>
 * 终态：CLOSED / LOST / TERMINATED（不可出边）。审批中间态：CLOSING（结项）、
 * TERMINATING（终止）——只允许对应审批回调事件出边；TERMINATE_REJECTED 的
 * 恢复目标从最近一条 TERMINATE_APPLY 日志的 from_status 回溯（不留额外列）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ProjectStateMachine {

    private final BizProjectMapper projectMapper;
    private final BizProjectStatusLogMapper statusLogMapper;

    /** 终态集合（I1：不可出边） */
    public static final Set<String> TERMINAL_STATUSES = Set.of("CLOSED", "LOST", "TERMINATED");

    /** 全部合法状态（含 LOST/PAUSED/TERMINATING/TERMINATED 新态） */
    public static final Set<String> ALL_STATUSES = Set.of(
            "DRAFT", "FILED", "TENDERING", "WON", "LOST", "CONSTRUCTION", "PAUSED",
            "COMPLETED", "CLOSING", "CLOSED", "TERMINATING", "TERMINATED");

    /** 转移边表：事件 → 允许的源状态集合（I1 的静态定义） */
    private static final Map<ProjectEvent, Set<String>> EDGES = new EnumMap<>(ProjectEvent.class);

    static {
        EDGES.put(ProjectEvent.SUBMIT, Set.of("DRAFT"));
        EDGES.put(ProjectEvent.WITHDRAW, Set.of("FILED"));
        EDGES.put(ProjectEvent.GO_TENDER, Set.of("FILED"));
        EDGES.put(ProjectEvent.WIN_BID, Set.of("TENDERING"));
        EDGES.put(ProjectEvent.LOSE_BID, Set.of("TENDERING"));
        EDGES.put(ProjectEvent.START_CONSTRUCTION, Set.of("WON", "FILED"));
        EDGES.put(ProjectEvent.PAUSE, Set.of("CONSTRUCTION"));
        EDGES.put(ProjectEvent.RESUME, Set.of("PAUSED"));
        EDGES.put(ProjectEvent.COMPLETE, Set.of("CONSTRUCTION"));
        EDGES.put(ProjectEvent.APPLY_CLOSE, Set.of("COMPLETED"));
        EDGES.put(ProjectEvent.APPROVE_CLOSE, Set.of("CLOSING"));
        EDGES.put(ProjectEvent.REJECT_CLOSE, Set.of("CLOSING"));
        // TERMINATE_APPLY：任意非终态可发起终止（动态校验，见 fire）
        EDGES.put(ProjectEvent.TERMINATE_APPLY, Set.of());
        EDGES.put(ProjectEvent.TERMINATE_APPROVED, Set.of("TERMINATING"));
        EDGES.put(ProjectEvent.TERMINATE_REJECTED, Set.of("TERMINATING"));
    }

    /** 事件的固定目标状态（TERMINATE_REJECTED 例外：从日志回溯恢复） */
    private static final Map<ProjectEvent, String> TARGET = new EnumMap<>(ProjectEvent.class);

    static {
        TARGET.put(ProjectEvent.SUBMIT, "FILED");
        TARGET.put(ProjectEvent.WITHDRAW, "DRAFT");
        TARGET.put(ProjectEvent.GO_TENDER, "TENDERING");
        TARGET.put(ProjectEvent.WIN_BID, "WON");
        TARGET.put(ProjectEvent.LOSE_BID, "LOST");
        TARGET.put(ProjectEvent.START_CONSTRUCTION, "CONSTRUCTION");
        TARGET.put(ProjectEvent.PAUSE, "PAUSED");
        TARGET.put(ProjectEvent.RESUME, "CONSTRUCTION");
        TARGET.put(ProjectEvent.COMPLETE, "COMPLETED");
        TARGET.put(ProjectEvent.APPLY_CLOSE, "CLOSING");
        TARGET.put(ProjectEvent.APPROVE_CLOSE, "CLOSED");
        TARGET.put(ProjectEvent.REJECT_CLOSE, "COMPLETED");
        TARGET.put(ProjectEvent.TERMINATE_APPLY, "TERMINATING");
        TARGET.put(ProjectEvent.TERMINATE_APPROVED, "TERMINATED");
    }

    /** 需要开工准入守卫的事件（I2） */
    private static final Set<ProjectEvent> NEEDS_CONSTRUCTION_GUARD = EnumSet.of(ProjectEvent.START_CONSTRUCTION);

    /**
     * 触发状态事件（唯一合法流转通道）。
     *
     * @param project    目标项目（调用方已加载）
     * @param event      业务事件
     * @param remark     备注（落标/暂停/终止原因等）
     * @param operatorId 操作人（null=系统回调）
     * @return 落库后的项目
     */
    public BizProject fire(BizProject project, ProjectEvent event, String remark, Long operatorId) {
        String from = project.getStatus();

        if (event == ProjectEvent.TERMINATE_APPLY) {
            // 动态边：任意非终态、非审批中间态可发起终止
            if (TERMINAL_STATUSES.contains(from) || "CLOSING".equals(from) || "TERMINATING".equals(from)) {
                throw new BusinessException("项目状态[" + from + "]不允许发起终止");
            }
        } else {
            Set<String> allowedFrom = EDGES.get(event);
            if (allowedFrom == null) {
                throw new BusinessException("未知的项目状态事件：" + event);
            }
            boolean ok = event == ProjectEvent.TERMINATE_REJECTED
                    ? "TERMINATING".equals(from)
                    : allowedFrom.contains(from);
            if (!ok) {
                throw new BusinessException(String.format("项目状态[%s]不允许由事件[%s]流转（合法源：%s）",
                        from, event, allowedFrom));
            }
        }

        // I2 开工准入：必须存在 EFFECTIVE 施工合同
        if (NEEDS_CONSTRUCTION_GUARD.contains(event)) {
            long effective = projectMapper.countEffectiveConstructionContracts(project.getId());
            if (effective == 0) {
                throw new BusinessException("开工失败：项目尚无生效（EFFECTIVE）的施工合同，无法进入施工中");
            }
        }

        String to = TARGET.get(event);
        if (event == ProjectEvent.TERMINATE_REJECTED) {
            to = restoreStatusBeforeTerminate(project.getId());
        }
        return apply(project, event, to, remark, operatorId);
    }

    /** 终止驳回恢复目标：回溯最近一条 TERMINATE_APPLY 日志的 from_status */
    private String restoreStatusBeforeTerminate(Long projectId) {
        BizProjectStatusLog last = statusLogMapper.selectOne(new LambdaQueryWrapper<BizProjectStatusLog>()
                .eq(BizProjectStatusLog::getProjectId, projectId)
                .eq(BizProjectStatusLog::getEvent, ProjectEvent.TERMINATE_APPLY.name())
                .orderByDesc(BizProjectStatusLog::getId)
                .last("LIMIT 1"));
        if (last == null || last.getFromStatus() == null) {
            // 日志缺失兜底：回到 CONSTRUCTION（宁可保守可继续操作，不留死锁态）
            return "CONSTRUCTION";
        }
        return last.getFromStatus();
    }

    /** 应用流转：目标态 + 周期回写（I3）+ 落库 + 写日志 */
    private BizProject apply(BizProject project, ProjectEvent event, String to, String remark, Long operatorId) {
        String from = project.getStatus();
        LocalDate today = LocalDate.now();
        // I3 周期回写：首次开工/竣工落实际日期
        if (event == ProjectEvent.START_CONSTRUCTION && project.getActualStartDate() == null) {
            project.setActualStartDate(today);
        }
        if (event == ProjectEvent.COMPLETE) {
            if (project.getActualStartDate() != null && today.isBefore(project.getActualStartDate())) {
                throw new BusinessException("实际竣工日期早于实际开工日期，拒绝流转");
            }
            if (project.getActualEndDate() == null) {
                project.setActualEndDate(today);
            }
        }
        if (project.getPlannedStartDate() != null && project.getPlannedEndDate() != null
                && project.getPlannedEndDate().isBefore(project.getPlannedStartDate())) {
            throw new BusinessException("计划竣工日期早于计划开工日期，请先修正周期");
        }
        project.setStatus(to);
        // 乐观锁（BaseEntity @Version）：并发修改 fail-fast，不静默丢失更新（沿用旧 updateStatus 语义）
        int affected = projectMapper.updateById(project);
        if (affected == 0) {
            throw new BusinessException("项目已被并发修改，请刷新后重试");
        }

        BizProjectStatusLog logRow = new BizProjectStatusLog();
        logRow.setProjectId(project.getId());
        logRow.setFromStatus(from);
        logRow.setToStatus(to);
        logRow.setEvent(event.name());
        logRow.setRemark(remark);
        logRow.setOperatorId(operatorId);
        statusLogMapper.insert(logRow);
        return project;
    }

    /** 边表只读视图（测试/文档用） */
    public static Map<ProjectEvent, Set<String>> edges() {
        return Map.copyOf(EDGES);
    }
}
