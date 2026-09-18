package com.zwinsight.subcontract.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractOutputReport;
import com.zwinsight.subcontract.domain.BizSubcontractRewardPunish;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractOutputReportMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractRewardPunishMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-subcontract 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 4 张表。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p>分包结算明细（biz_subcontract_settlement_detail）以 {@code settlement_id} 关联、
 * 无 {@code project_id} 列，不在本监听器职责内；结算主单逻辑删除后明细随之失效，
 * 查询侧一律经主单 JOIN。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——分包结算 7+8 条、
 * 产值上报 3 条、分包合同 15 条（指向物理不存在的项目）、奖罚 3 条。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubcontractProjectCascadeCleanupListener {

    private final BizSubcontractMapper subcontractMapper;
    private final BizSubcontractSettlementMapper subcontractSettlementMapper;
    private final BizSubcontractOutputReportMapper subcontractOutputReportMapper;
    private final BizSubcontractRewardPunishMapper subcontractRewardPunishMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int contracts = subcontractMapper.delete(
                new QueryWrapper<BizSubcontract>().eq("project_id", projectId));
        int settlements = subcontractSettlementMapper.delete(
                new QueryWrapper<BizSubcontractSettlement>().eq("project_id", projectId));
        int outputReports = subcontractOutputReportMapper.delete(
                new QueryWrapper<BizSubcontractOutputReport>().eq("project_id", projectId));
        int rewardPunish = subcontractRewardPunishMapper.delete(
                new QueryWrapper<BizSubcontractRewardPunish>().eq("project_id", projectId));

        log.info("项目删除级联清理[subcontract]完成, projectId={}, 分包合同={} 结算={} 产值={} 奖罚={}",
                projectId, contracts, settlements, outputReports, rewardPunish);
    }
}
