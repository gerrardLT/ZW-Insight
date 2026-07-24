package com.zwinsight.labor.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.dto.LaborRosterExcelDTO;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 劳务花名册服务
 */
@Service
@RequiredArgsConstructor
public class LaborRosterService {

    private final BizLaborRosterMapper rosterMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizTeamMapper teamMapper;

    /**
     * 分页查询（支持按姓名/班组名/工种筛选，回填班组名）
     */
    public PageResult<BizLaborRoster> page(int page, int size, Long projectId, Long teamId,
                                           String workerName, String teamName, String workType) {
        List<Long> teamMatchedIds = null;
        if (StrUtil.isNotBlank(teamName)) {
            LambdaQueryWrapper<BizTeam> teamWrapper = new LambdaQueryWrapper<>();
            teamWrapper.like(BizTeam::getTeamName, teamName);
            teamMatchedIds = teamMapper.selectList(teamWrapper).stream()
                    .map(BizTeam::getId).collect(Collectors.toList());
            if (teamMatchedIds.isEmpty()) {
                return PageResult.of(new Page<>(page, size));
            }
        }
        Page<BizLaborRoster> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborRoster> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborRoster::getProjectId, projectId)
                .eq(teamId != null, BizLaborRoster::getTeamId, teamId)
                .in(teamMatchedIds != null, BizLaborRoster::getTeamId, teamMatchedIds)
                .like(StrUtil.isNotBlank(workerName), BizLaborRoster::getWorkerName, workerName)
                .like(StrUtil.isNotBlank(workType), BizLaborRoster::getWorkType, workType)
                .orderByDesc(BizLaborRoster::getCreatedAt);
        Page<BizLaborRoster> result = rosterMapper.selectPage(pageParam, wrapper);
        fillTeamName(result.getRecords());
        return PageResult.of(result);
    }

    /**
     * 批量回填班组名称（从 biz_team）
     */
    private void fillTeamName(List<BizLaborRoster> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> teamIds = records.stream()
                .map(BizLaborRoster::getTeamId)
                .filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toList());
        if (teamIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));
        records.forEach(r -> r.setTeamName(nameMap.get(r.getTeamId())));
    }

    /**
     * 保存
     */
    public void save(BizLaborRoster roster) {
        roster.setStatus(1);
        rosterMapper.insert(roster);
    }

    /**
     * 更新
     */
    public void update(BizLaborRoster roster) {
        BizLaborRoster existing = rosterMapper.selectById(roster.getId());
        if (existing == null) {
            throw new BusinessException("花名册记录不存在");
        }
        rosterMapper.updateById(roster);
    }

    /**
     * 删除（已有工单不可删）
     */
    public void delete(Long id) {
        Long count = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getWorkerId, id));
        if (count > 0) {
            throw new BusinessException("该工人已有派工记录，不可删除");
        }
        rosterMapper.deleteById(id);
    }

    /**
     * 批量导入
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchImport(MultipartFile file, Long projectId, Long teamId) {
        List<BizLaborRoster> rosters = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), LaborRosterExcelDTO.class,
                    new PageReadListener<LaborRosterExcelDTO>(dataList -> {
                        for (LaborRosterExcelDTO dto : dataList) {
                            BizLaborRoster roster = new BizLaborRoster();
                            roster.setProjectId(projectId);
                            roster.setTeamId(teamId);
                            roster.setWorkerName(dto.getWorkerName());
                            roster.setIdCard(dto.getIdCard());
                            roster.setPhone(dto.getPhone());
                            roster.setWorkerType(dto.getWorkerType());
                            roster.setStatus(1);
                            rosters.add(roster);
                        }
                    })).sheet().doRead();
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        if (rosters.isEmpty()) {
            throw new BusinessException("导入文件无有效数据");
        }

        for (BizLaborRoster roster : rosters) {
            // 跳过身份证号重复的
            if (StrUtil.isNotBlank(roster.getIdCard())) {
                long count = rosterMapper.selectCount(
                        new LambdaQueryWrapper<BizLaborRoster>()
                                .eq(BizLaborRoster::getIdCard, roster.getIdCard())
                                .eq(BizLaborRoster::getProjectId, projectId));
                if (count > 0) {
                    continue;
                }
            }
            rosterMapper.insert(roster);
        }
        return rosters.size();
    }
}
