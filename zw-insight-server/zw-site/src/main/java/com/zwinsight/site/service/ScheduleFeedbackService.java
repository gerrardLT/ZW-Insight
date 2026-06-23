package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.site.domain.BizScheduleFeedback;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizScheduleFeedbackMapper;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 进度反馈服务
 */
@Service
@RequiredArgsConstructor
public class ScheduleFeedbackService {

    private final BizScheduleFeedbackMapper feedbackMapper;
    private final BizSchedulePlanMapper planMapper;
    private final SchedulePlanService schedulePlanService;

    /**
     * 分页查询
     */
    public PageResult<BizScheduleFeedback> page(int page, int size, Long projectId, Long planId) {
        Page<BizScheduleFeedback> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizScheduleFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizScheduleFeedback::getProjectId, projectId)
                .eq(planId != null, BizScheduleFeedback::getPlanId, planId)
                .orderByDesc(BizScheduleFeedback::getCreatedAt);
        Page<BizScheduleFeedback> result = feedbackMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增反馈（同步更新计划的实际日期/状态/进度）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizScheduleFeedback feedback) {
        feedback.setStatus("DRAFT");
        feedbackMapper.insert(feedback);

        // 同步更新计划任务
        syncPlan(feedback);
    }

    /**
     * 提交反馈
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizScheduleFeedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException("反馈记录不存在");
        }
        if (!"DRAFT".equals(feedback.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        feedback.setStatus("APPROVED");
        feedbackMapper.updateById(feedback);

        // 同步更新计划任务
        syncPlan(feedback);
    }

    /**
     * 同步更新计划任务的实际日期、状态和进度
     */
    private void syncPlan(BizScheduleFeedback feedback) {
        BizSchedulePlan plan = planMapper.selectById(feedback.getPlanId());
        if (plan == null) {
            throw new BusinessException("关联计划任务不存在");
        }

        if (feedback.getActualStartDate() != null) {
            plan.setActualStartDate(feedback.getActualStartDate());
        }
        if (feedback.getActualEndDate() != null) {
            plan.setActualEndDate(feedback.getActualEndDate());
        }
        if (feedback.getTaskStatus() != null) {
            plan.setTaskStatus(feedback.getTaskStatus());
        }
        if (feedback.getProgress() != null) {
            plan.setProgress(feedback.getProgress());
        }
        planMapper.updateById(plan);

        // 重新计算父节点进度
        schedulePlanService.calculateParentProgress(plan.getParentId());
    }
}
