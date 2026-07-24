package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.reference.ReferenceCheck;
import com.zwinsight.common.reference.ReferenceRelation;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 班组服务
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final BizTeamMapper teamMapper;
    private final BizLaborRosterMapper rosterMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询（支持按班组名称/工种筛选，回填项目名与成员数）
     */
    public PageResult<BizTeam> page(int page, int size, Long projectId, String teamName, String workType) {
        Page<BizTeam> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizTeam::getProjectId, projectId)
                .like(StrUtil.isNotBlank(teamName), BizTeam::getTeamName, teamName)
                .like(StrUtil.isNotBlank(workType), BizTeam::getWorkType, workType)
                .orderByDesc(BizTeam::getCreatedAt);
        Page<BizTeam> result = teamMapper.selectPage(pageParam, wrapper);
        List<BizTeam> records = result.getRecords();
        ProjectNameFiller.fill(records, projectMapper,
                BizTeam::getProjectId, BizTeam::setProjectName);
        fillMemberCount(records);
        return PageResult.of(result);
    }

    /**
     * 批量回填班组成员数（从花名册按 team_id 聚合）
     */
    private void fillMemberCount(List<BizTeam> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> teamIds = records.stream().map(BizTeam::getId).collect(Collectors.toList());
        LambdaQueryWrapper<BizLaborRoster> rosterWrapper = new LambdaQueryWrapper<>();
        rosterWrapper.in(BizLaborRoster::getTeamId, teamIds);
        Map<Long, Long> countMap = rosterMapper.selectList(rosterWrapper).stream()
                .filter(r -> r.getTeamId() != null)
                .collect(Collectors.groupingBy(BizLaborRoster::getTeamId, Collectors.counting()));
        records.forEach(t -> t.setMemberCount(countMap.getOrDefault(t.getId(), 0L).intValue()));
    }

    /**
     * 保存
     */
    public void save(BizTeam team) {
        team.setStatus(1);
        teamMapper.insert(team);
    }

    /**
     * 更新
     */
    public void update(BizTeam team) {
        BizTeam existing = teamMapper.selectById(team.getId());
        if (existing == null) {
            throw new BusinessException("班组不存在");
        }
        teamMapper.updateById(team);
    }

    /**
     * 删除（引用校验：花名册、用工单、工资单）
     */
    @ReferenceCheck({
            @ReferenceRelation(tableName = "biz_labor_roster", column = "team_id",
                    displayName = "花名册", codeColumn = ""),
            @ReferenceRelation(tableName = "biz_work_order", column = "team_id",
                    displayName = "用工单", codeColumn = ""),
            @ReferenceRelation(tableName = "biz_labor_payroll", column = "team_id",
                    displayName = "工资单", codeColumn = "")
    })
    public void delete(Long id) {
        teamMapper.deleteById(id);
    }

    /**
     * 更新状态
     */
    public void updateStatus(Long id, Integer status) {
        BizTeam team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException("班组不存在");
        }
        team.setStatus(status);
        teamMapper.updateById(team);
    }
}
