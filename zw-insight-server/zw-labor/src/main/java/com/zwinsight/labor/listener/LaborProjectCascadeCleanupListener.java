package com.zwinsight.labor.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborOutputReport;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizLaborRewardPunish;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizLaborSettlement;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborOutputReportMapper;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizLaborRewardPunishMapper;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizLaborSettlementMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-labor 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 8 张表。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——薪资单 10 条、结算 7 条、
 * 产值上报 3 条、劳务合同 5 条、奖罚 3 条、花名册 3 条、班组 7+12 条。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaborProjectCascadeCleanupListener {

    private final BizLaborContractMapper laborContractMapper;
    private final BizLaborSettlementMapper laborSettlementMapper;
    private final BizLaborPayrollMapper laborPayrollMapper;
    private final BizLaborOutputReportMapper laborOutputReportMapper;
    private final BizLaborRosterMapper laborRosterMapper;
    private final BizLaborRewardPunishMapper laborRewardPunishMapper;
    private final BizTeamMapper teamMapper;
    private final BizWorkOrderMapper workOrderMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int contracts = laborContractMapper.delete(
                new QueryWrapper<BizLaborContract>().eq("project_id", projectId));
        int settlements = laborSettlementMapper.delete(
                new QueryWrapper<BizLaborSettlement>().eq("project_id", projectId));
        int payrolls = laborPayrollMapper.delete(
                new QueryWrapper<BizLaborPayroll>().eq("project_id", projectId));
        int outputReports = laborOutputReportMapper.delete(
                new QueryWrapper<BizLaborOutputReport>().eq("project_id", projectId));
        int rosters = laborRosterMapper.delete(
                new QueryWrapper<BizLaborRoster>().eq("project_id", projectId));
        int rewardPunish = laborRewardPunishMapper.delete(
                new QueryWrapper<BizLaborRewardPunish>().eq("project_id", projectId));
        int teams = teamMapper.delete(
                new QueryWrapper<BizTeam>().eq("project_id", projectId));
        int workOrders = workOrderMapper.delete(
                new QueryWrapper<BizWorkOrder>().eq("project_id", projectId));

        log.info("项目删除级联清理[labor]完成, projectId={}, 合同={} 结算={} 薪资={} "
                        + "产值={} 花名册={} 奖罚={} 班组={} 工单={}",
                projectId, contracts, settlements, payrolls, outputReports,
                rosters, rewardPunish, teams, workOrders);
    }
}
