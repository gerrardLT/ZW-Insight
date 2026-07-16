package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.reference.ReferenceCheck;
import com.zwinsight.common.reference.ReferenceRelation;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 班组服务
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final BizTeamMapper teamMapper;
    private final BizLaborRosterMapper rosterMapper;

    /**
     * 分页查询
     */
    public PageResult<BizTeam> page(int page, int size, Long projectId) {
        Page<BizTeam> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizTeam::getProjectId, projectId)
                .orderByDesc(BizTeam::getCreatedAt);
        Page<BizTeam> result = teamMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
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
