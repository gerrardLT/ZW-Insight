package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 进度计划服务
 */
@Service
@RequiredArgsConstructor
public class SchedulePlanService {

    private final BizSchedulePlanMapper planMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询进度计划（平铺列表，支持项目名称/任务名称筛选，回填项目名）
     */
    public PageResult<BizSchedulePlan> page(int page, int size, Long projectId, String projectName, String taskName) {
        // projectName 不是本表字段，需先经 biz_project 解析为 projectId 集合再过滤
        List<Long> nameMatchedIds = null;
        if (StrUtil.isNotBlank(projectName)) {
            LambdaQueryWrapper<BizProject> projectWrapper = new LambdaQueryWrapper<>();
            projectWrapper.like(BizProject::getProjectName, projectName);
            nameMatchedIds = projectMapper.selectList(projectWrapper).stream()
                    .map(BizProject::getId).collect(Collectors.toList());
            if (nameMatchedIds.isEmpty()) {
                return PageResult.of(new Page<>(page, size));
            }
        }
        Page<BizSchedulePlan> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSchedulePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSchedulePlan::getProjectId, projectId)
                .in(nameMatchedIds != null, BizSchedulePlan::getProjectId, nameMatchedIds)
                .like(StrUtil.isNotBlank(taskName), BizSchedulePlan::getTaskName, taskName)
                .orderByAsc(BizSchedulePlan::getSortOrder);
        Page<BizSchedulePlan> result = planMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizSchedulePlan::getProjectId, BizSchedulePlan::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 获取项目进度计划（树形返回）
     */
    public List<BizSchedulePlan> list(Long projectId) {
        LambdaQueryWrapper<BizSchedulePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSchedulePlan::getProjectId, projectId)
                .orderByAsc(BizSchedulePlan::getSortOrder);
        List<BizSchedulePlan> allPlans = planMapper.selectList(wrapper);
        return buildTree(allPlans);
    }

    /**
     * 新增计划任务
     */
    public void save(BizSchedulePlan plan) {
        if (plan.getProgress() == null) {
            plan.setProgress(BigDecimal.ZERO);
        }
        if (plan.getTaskStatus() == null) {
            plan.setTaskStatus("NOT_STARTED");
        }
        if (plan.getParentId() == null) {
            plan.setParentId(0L);
        }
        planMapper.insert(plan);
    }

    /**
     * 更新计划任务
     */
    public void update(BizSchedulePlan plan) {
        BizSchedulePlan existing = planMapper.selectById(plan.getId());
        if (existing == null) {
            throw new BusinessException("计划任务不存在");
        }
        planMapper.updateById(plan);
    }

    /**
     * 删除计划任务（含子任务检查）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizSchedulePlan existing = planMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("计划任务不存在");
        }
        // 检查是否有子任务
        LambdaQueryWrapper<BizSchedulePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSchedulePlan::getParentId, id);
        Long childCount = planMapper.selectCount(wrapper);
        if (childCount > 0) {
            throw new BusinessException("存在子任务，无法删除");
        }
        planMapper.deleteById(id);
    }

    /**
     * 计算父节点进度（子节点进度平均值）
     */
    public void calculateParentProgress(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        LambdaQueryWrapper<BizSchedulePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSchedulePlan::getParentId, parentId);
        List<BizSchedulePlan> children = planMapper.selectList(wrapper);

        if (children.isEmpty()) {
            return;
        }

        BigDecimal totalProgress = children.stream()
                .map(c -> c.getProgress() == null ? BigDecimal.ZERO : c.getProgress())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgProgress = totalProgress.divide(
                BigDecimal.valueOf(children.size()), 2, RoundingMode.HALF_UP);

        BizSchedulePlan parent = planMapper.selectById(parentId);
        if (parent != null) {
            parent.setProgress(avgProgress);
            // 如果所有子任务完成，父任务也标记为完成
            boolean allCompleted = children.stream()
                    .allMatch(c -> "COMPLETED".equals(c.getTaskStatus()));
            if (allCompleted) {
                parent.setTaskStatus("COMPLETED");
            } else {
                boolean anyInProgress = children.stream()
                        .anyMatch(c -> "IN_PROGRESS".equals(c.getTaskStatus()) || "COMPLETED".equals(c.getTaskStatus()));
                if (anyInProgress) {
                    parent.setTaskStatus("IN_PROGRESS");
                }
            }
            planMapper.updateById(parent);

            // 递归向上计算
            calculateParentProgress(parent.getParentId());
        }
    }

    /**
     * 构建树形结构
     */
    private List<BizSchedulePlan> buildTree(List<BizSchedulePlan> allPlans) {
        Map<Long, List<BizSchedulePlan>> groupByParent = allPlans.stream()
                .collect(Collectors.groupingBy(BizSchedulePlan::getParentId));

        List<BizSchedulePlan> roots = groupByParent.getOrDefault(0L, new ArrayList<>());
        for (BizSchedulePlan root : roots) {
            fillChildren(root, groupByParent);
        }
        return roots;
    }

    private void fillChildren(BizSchedulePlan parent, Map<Long, List<BizSchedulePlan>> groupByParent) {
        List<BizSchedulePlan> children = groupByParent.getOrDefault(parent.getId(), new ArrayList<>());
        parent.setChildren(children.isEmpty() ? null : children);
        for (BizSchedulePlan child : children) {
            fillChildren(child, groupByParent);
        }
    }
}
