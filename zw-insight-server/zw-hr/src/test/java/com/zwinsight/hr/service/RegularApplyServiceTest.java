package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizRegularApply;
import com.zwinsight.hr.mapper.BizRegularApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RegularApplyService（转正申请）单元测试
 *
 * 覆盖场景:
 * - 新增保存（DRAFT 状态）/ 更新
 * - 删除与提交的状态校验
 * - 提交发起审批流程并回写 APPROVED
 */
@ExtendWith(MockitoExtension.class)
class RegularApplyServiceTest {

    @Mock
    private BizRegularApplyMapper regularApplyMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private RegularApplyService regularApplyService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizRegularApply.class);
    }

    @Test
    @DisplayName("新增转正申请：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizRegularApply apply = new BizRegularApply();
        apply.setUserName("张三");

        regularApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(regularApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("更新转正申请：仅 DRAFT 可编辑 + status 剥离防篡改（P1 修复）")
    void update_draftGuardAndStatusStripped() {
        BizRegularApply existing = new BizRegularApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(regularApplyMapper.selectById(1L)).thenReturn(existing);

        BizRegularApply apply = new BizRegularApply();
        apply.setId(1L);
        apply.setStatus("APPROVED"); // 恶意携带 status

        regularApplyService.update(apply);

        verify(regularApplyMapper).updateById(argThat(a -> a.getStatus() == null));

        BizRegularApply submitted = new BizRegularApply();
        submitted.setId(2L);
        submitted.setStatus("SUBMITTED");
        when(regularApplyMapper.selectById(2L)).thenReturn(submitted);
        BizRegularApply upd2 = new BizRegularApply();
        upd2.setId(2L);
        assertThatThrownBy(() -> regularApplyService.update(upd2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除转正申请：非草稿状态拒绝删除")
    void delete_nonDraft_rejected() {
        BizRegularApply existing = new BizRegularApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(regularApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> regularApplyService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除转正申请：草稿状态正常删除")
    void delete_draft_success() {
        BizRegularApply existing = new BizRegularApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(regularApplyMapper.selectById(1L)).thenReturn(existing);

        regularApplyService.delete(1L);

        verify(regularApplyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("提交转正申请：不存在抛异常")
    void submit_notFound_throwsException() {
        when(regularApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> regularApplyService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("转正申请不存在");
    }

    @Test
    @DisplayName("提交转正申请：非草稿状态拒绝提交")
    void submit_nonDraft_rejected() {
        BizRegularApply existing = new BizRegularApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(regularApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> regularApplyService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交转正申请：置 SUBMITTED 中间态（P1 审批后生效修复，提交不得直接 APPROVED）")
    void submit_success_setsSubmittedOnly() {
        BizRegularApply apply = new BizRegularApply();
        apply.setId(1L);
        apply.setUserName("张三");
        apply.setUserId(100L);
        apply.setStatus("DRAFT");
        when(regularApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");

        regularApplyService.submit(1L);

        assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        verify(regularApplyMapper).updateById(apply);
        verify(approvalService).startProcess(eq("REGULAR_APPLY"), eq(1L),
                eq("regular_apply_approval"), anyMap());
    }

    @Test
    @DisplayName("审批回调：通过 SUBMITTED→APPROVED；驳回 SUBMITTED→DRAFT；均幂等")
    void approvalCallbacks_transitionAndIdempotent() {
        BizRegularApply apply = new BizRegularApply();
        apply.setId(1L);
        apply.setStatus("SUBMITTED");
        when(regularApplyMapper.selectById(1L)).thenReturn(apply);

        regularApplyService.onApproved(1L);
        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        // 幂等：重复事件不重复处理
        regularApplyService.onApproved(1L);
        verify(regularApplyMapper, org.mockito.Mockito.times(1)).updateById(any());

        BizRegularApply submitted2 = new BizRegularApply();
        submitted2.setId(2L);
        submitted2.setStatus("SUBMITTED");
        when(regularApplyMapper.selectById(2L)).thenReturn(submitted2);
        regularApplyService.onRejected(2L);
        assertThat(submitted2.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizRegularApply> stubPage = new Page<>(1, 10);
        stubPage.setTotal(2);
        when(regularApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizRegularApply> result = regularApplyService.page(1, 10);

        assertThat(result.getTotal()).isEqualTo(2);
    }
}
