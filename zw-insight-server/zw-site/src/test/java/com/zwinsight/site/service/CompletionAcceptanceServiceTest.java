package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.site.domain.BizCompletionAcceptance;
import com.zwinsight.site.mapper.BizCompletionAcceptanceMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompletionAcceptanceService（竣工验收）单元测试
 *
 * 覆盖场景:
 * - 新增保存（DRAFT 状态）
 * - 提交的状态校验
 * - 提交发起审批流程并将项目状态置为 COMPLETED
 */
@ExtendWith(MockitoExtension.class)
class CompletionAcceptanceServiceTest {

    @Mock
    private BizCompletionAcceptanceMapper acceptanceMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private CompletionAcceptanceService completionAcceptanceService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizCompletionAcceptance.class);
    }

    @Test
    @DisplayName("新增竣工验收：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizCompletionAcceptance acceptance = new BizCompletionAcceptance();
        acceptance.setProjectId(1L);

        completionAcceptanceService.save(acceptance);

        assertThat(acceptance.getStatus()).isEqualTo("DRAFT");
        verify(acceptanceMapper).insert(acceptance);
    }

    @Test
    @DisplayName("提交竣工验收：不存在抛异常")
    void submit_notFound_throwsException() {
        when(acceptanceMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> completionAcceptanceService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("竣工验收记录不存在");
    }

    @Test
    @DisplayName("提交竣工验收：非草稿状态拒绝提交")
    void submit_nonDraft_rejected() {
        BizCompletionAcceptance existing = new BizCompletionAcceptance();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(acceptanceMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> completionAcceptanceService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交竣工验收：发起审批流程并将项目置为 COMPLETED")
    void submit_success_completesProject() {
        BizCompletionAcceptance acceptance = new BizCompletionAcceptance();
        acceptance.setId(1L);
        acceptance.setProjectId(10L);
        acceptance.setStatus("DRAFT");
        when(acceptanceMapper.selectById(1L)).thenReturn(acceptance);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");
        BizProject project = new BizProject();
        project.setId(10L);
        project.setStatus("CONSTRUCTION");
        when(projectMapper.selectById(10L)).thenReturn(project);

        completionAcceptanceService.submit(1L);

        assertThat(acceptance.getStatus()).isEqualTo("APPROVED");
        assertThat(acceptance.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(acceptanceMapper).updateById(acceptance);
        verify(approvalService).startProcess(eq("COMPLETION_ACCEPTANCE"), eq(1L),
                eq("completion_acceptance_approval"), anyMap());

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("提交竣工验收：项目不存在时不更新项目状态")
    void submit_projectNotFound_skipsProjectUpdate() {
        BizCompletionAcceptance acceptance = new BizCompletionAcceptance();
        acceptance.setId(1L);
        acceptance.setProjectId(10L);
        acceptance.setStatus("DRAFT");
        when(acceptanceMapper.selectById(1L)).thenReturn(acceptance);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");
        when(projectMapper.selectById(10L)).thenReturn(null);

        completionAcceptanceService.submit(1L);

        assertThat(acceptance.getStatus()).isEqualTo("APPROVED");
        verify(projectMapper, org.mockito.Mockito.never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizCompletionAcceptance> stubPage = new Page<>(1, 10);
        stubPage.setTotal(2);
        when(acceptanceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizCompletionAcceptance> result = completionAcceptanceService.page(1, 10, 10L);

        assertThat(result.getTotal()).isEqualTo(2);
    }
}
