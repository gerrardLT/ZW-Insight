package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.site.domain.BizScheduleFeedback;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizScheduleFeedbackMapper;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScheduleFeedbackService（进度反馈）单元测试
 *
 * 覆盖场景:
 * - 新增反馈同步更新计划任务的实际日期/状态/进度并触发父节点重算
 * - 提交反馈的状态校验
 * - 关联计划不存在抛异常
 */
@ExtendWith(MockitoExtension.class)
class ScheduleFeedbackServiceTest {

    @Mock
    private BizScheduleFeedbackMapper feedbackMapper;

    @Mock
    private BizSchedulePlanMapper planMapper;

    @Mock
    private SchedulePlanService schedulePlanService;

    @InjectMocks
    private ScheduleFeedbackService scheduleFeedbackService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizScheduleFeedback.class);
    }

    private BizScheduleFeedback feedback(Long planId) {
        BizScheduleFeedback feedback = new BizScheduleFeedback();
        feedback.setPlanId(planId);
        feedback.setProjectId(10L);
        feedback.setActualStartDate(LocalDate.of(2026, 7, 1));
        feedback.setActualEndDate(LocalDate.of(2026, 7, 10));
        feedback.setTaskStatus("IN_PROGRESS");
        feedback.setProgress(new BigDecimal("60"));
        return feedback;
    }

    @Test
    @DisplayName("新增反馈：DRAFT 状态并同步更新计划任务")
    void save_syncsPlanFields() {
        BizScheduleFeedback feedback = feedback(5L);
        BizSchedulePlan plan = new BizSchedulePlan();
        plan.setId(5L);
        plan.setParentId(1L);
        when(planMapper.selectById(5L)).thenReturn(plan);

        scheduleFeedbackService.save(feedback);

        assertThat(feedback.getStatus()).isEqualTo("DRAFT");
        verify(feedbackMapper).insert(feedback);

        ArgumentCaptor<BizSchedulePlan> captor = ArgumentCaptor.forClass(BizSchedulePlan.class);
        verify(planMapper).updateById(captor.capture());
        BizSchedulePlan updated = captor.getValue();
        assertThat(updated.getActualStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(updated.getActualEndDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(updated.getTaskStatus()).isEqualTo("IN_PROGRESS");
        assertThat(updated.getProgress()).isEqualByComparingTo(new BigDecimal("60"));
        verify(schedulePlanService).calculateParentProgress(1L);
    }

    @Test
    @DisplayName("新增反馈：关联计划不存在抛异常")
    void save_planNotFound_throwsException() {
        BizScheduleFeedback feedback = feedback(5L);
        when(planMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> scheduleFeedbackService.save(feedback))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联计划任务不存在");
    }

    @Test
    @DisplayName("提交反馈：不存在抛异常")
    void submit_notFound_throwsException() {
        when(feedbackMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> scheduleFeedbackService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("反馈记录不存在");
    }

    @Test
    @DisplayName("提交反馈：非草稿状态拒绝提交")
    void submit_nonDraft_rejected() {
        BizScheduleFeedback existing = feedback(5L);
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(feedbackMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> scheduleFeedbackService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交反馈：回写 APPROVED 并再次同步计划")
    void submit_success_approvesAndSyncs() {
        BizScheduleFeedback existing = feedback(5L);
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(feedbackMapper.selectById(1L)).thenReturn(existing);
        BizSchedulePlan plan = new BizSchedulePlan();
        plan.setId(5L);
        plan.setParentId(0L);
        when(planMapper.selectById(5L)).thenReturn(plan);

        scheduleFeedbackService.submit(1L);

        assertThat(existing.getStatus()).isEqualTo("APPROVED");
        verify(feedbackMapper).updateById(existing);
        verify(planMapper).updateById(plan);
        verify(schedulePlanService).calculateParentProgress(0L);
    }

    @Test
    @DisplayName("提交反馈：反馈未带进度/状态时不覆盖计划原值")
    void submit_partialFeedback_keepsPlanValues() {
        BizScheduleFeedback existing = new BizScheduleFeedback();
        existing.setId(1L);
        existing.setPlanId(5L);
        existing.setStatus("DRAFT");
        when(feedbackMapper.selectById(1L)).thenReturn(existing);
        BizSchedulePlan plan = new BizSchedulePlan();
        plan.setId(5L);
        plan.setParentId(0L);
        plan.setTaskStatus("IN_PROGRESS");
        plan.setProgress(new BigDecimal("30"));
        when(planMapper.selectById(5L)).thenReturn(plan);

        scheduleFeedbackService.submit(1L);

        assertThat(plan.getTaskStatus()).isEqualTo("IN_PROGRESS");
        assertThat(plan.getProgress()).isEqualByComparingTo(new BigDecimal("30"));
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizScheduleFeedback> stubPage = new Page<>(1, 10);
        stubPage.setTotal(2);
        when(feedbackMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizScheduleFeedback> result = scheduleFeedbackService.page(1, 10, 10L, 5L);

        assertThat(result.getTotal()).isEqualTo(2);
    }
}
