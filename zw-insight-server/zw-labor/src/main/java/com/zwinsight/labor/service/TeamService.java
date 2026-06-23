package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
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
     * 删除（被引用不可删）
     */
    public void delete(Long id) {
        Long count = rosterMapper.selectCount(
                new LambdaQueryWrapper<BizLaborRoster>().eq(BizLaborRoster::getTeamId, id));
        if (count > 0) {
            throw new BusinessException("该班组下有工人记录，不可删除");
        }
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
