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
        validate(feedback);
        feedback.setStatus("DRAFT");
        // 草稿只落反馈记录，不改正式计划：草稿阶段可被丢弃或修改，提交后才影响计划进度
        feedbackMapper.insert(feedback);
    }

    /** 反馈入参校验：计划存在且属于所填项目；进度 0–100；实际开始不晚于实际结束 */
    private void validate(BizScheduleFeedback feedback) {
        BizSchedulePlan plan = planMapper.selectById(feedback.getPlanId());
        if (plan == null) {
            throw new BusinessException("关联计划任务不存在");
        }
        if (feedback.getProjectId() != null && plan.getProjectId() != null
                && !feedback.getProjectId().equals(plan.getProjectId())) {
            throw new BusinessException("计划任务不属于该项目");
        }
        if (feedback.getProgress() != null
                && (feedback.getProgress().signum() < 0 || feedback.getProgress().compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new BusinessException("进度必须在 0 到 100 之间");
        }
        if (feedback.getActualStartDate() != null && feedback.getActualEndDate() != null
                && feedback.getActualStartDate().isAfter(feedback.getActualEndDate())) {
            throw new BusinessException("实际开始日期不能晚于实际结束日期");
        }
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
        validate(feedback);
        feedback.setStatus("APPROVED");
        feedbackMapper.updateById(feedback);

        // 提交（确认）后才同步到正式计划任务
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
