package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizSealApply;
import com.zwinsight.hr.mapper.BizSealApplyMapper;
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
 * SealApplyService（用印申请）单元测试
 *
 * 覆盖场景:
 * - 新增保存（DRAFT 状态）/ 更新
 * - 删除与提交的状态校验
 * - 提交发起审批流程并回写 APPROVED
 */
@ExtendWith(MockitoExtension.class)
class SealApplyServiceTest {

    @Mock
    private BizSealApplyMapper sealApplyMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private SealApplyService sealApplyService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSealApply.class);
    }

    @Test
    @DisplayName("新增用印申请：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizSealApply apply = new BizSealApply();
        apply.setApplicant("张三");

        sealApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(sealApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("更新用印申请：仅 DRAFT 可编辑 + status 剥离防篡改（P1 修复）")
    void update_draftGuardAndStatusStripped() {
        BizSealApply existing = new BizSealApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(sealApplyMapper.selectById(1L)).thenReturn(existing);

        BizSealApply apply = new BizSealApply();
        apply.setId(1L);
        apply.setStatus("APPROVED"); // 恶意携带 status

        sealApplyService.update(apply);

        verify(sealApplyMapper).updateById(argThat(a -> a.getStatus() == null));

        BizSealApply submitted = new BizSealApply();
        submitted.setId(2L);
        submitted.setStatus("SUBMITTED");
        when(sealApplyMapper.selectById(2L)).thenReturn(submitted);
        BizSealApply upd2 = new BizSealApply();
        upd2.setId(2L);
        assertThatThrownBy(() -> sealApplyService.update(upd2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除用印申请：非草稿状态拒绝删除")
    void delete_nonDraft_rejected() {
        BizSealApply existing = new BizSealApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(sealApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> sealApplyService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除用印申请：草稿状态正常删除")
    void delete_draft_success() {
        BizSealApply existing = new BizSealApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(sealApplyMapper.selectById(1L)).thenReturn(existing);

        sealApplyService.delete(1L);

        verify(sealApplyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("提交用印申请：不存在抛异常")
    void submit_notFound_throwsException() {
        when(sealApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> sealApplyService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用印申请不存在");
    }

    @Test
    @DisplayName("提交用印申请：非草稿状态拒绝提交")
    void submit_nonDraft_rejected() {
        BizSealApply existing = new BizSealApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(sealApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> sealApplyService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交用印申请：置 SUBMITTED 中间态（P1 审批后生效修复，提交不得直接 APPROVED）")
    void submit_success_setsSubmittedOnly() {
        BizSealApply apply = new BizSealApply();
        apply.setId(1L);
        apply.setSealType("公章");
        apply.setApplicant("张三");
        apply.setStatus("DRAFT");
        when(sealApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");

        sealApplyService.submit(1L);

        assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        verify(sealApplyMapper).updateById(apply);
        verify(approvalService).startProcess(eq("SEAL_APPLY"), eq(1L),
                eq("seal_apply_approval"), anyMap());
    }

    @Test
    @DisplayName("审批回调：通过 SUBMITTED→APPROVED；驳回 SUBMITTED→DRAFT；均幂等")
    void approvalCallbacks_transitionAndIdempotent() {
        BizSealApply apply = new BizSealApply();
        apply.setId(1L);
        apply.setStatus("SUBMITTED");
        when(sealApplyMapper.selectById(1L)).thenReturn(apply);

        sealApplyService.onApproved(1L);
        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        sealApplyService.onApproved(1L);
        verify(sealApplyMapper, org.mockito.Mockito.times(1)).updateById(any());

        BizSealApply submitted2 = new BizSealApply();
        submitted2.setId(2L);
        submitted2.setStatus("SUBMITTED");
        when(sealApplyMapper.selectById(2L)).thenReturn(submitted2);
        sealApplyService.onRejected(2L);
        assertThat(submitted2.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizSealApply> stubPage = new Page<>(1, 10);
        stubPage.setTotal(1);
        when(sealApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizSealApply> result = sealApplyService.page(1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
    }
}
