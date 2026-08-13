package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizTransferApply;
import com.zwinsight.hr.mapper.BizTransferApplyMapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
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
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TransferApplyService（调动申请）单元测试
 *
 * 覆盖场景:
 * - 新增保存（DRAFT 状态）/ 更新
 * - 删除与提交的状态校验
 * - 提交发起审批流程并同步更新员工部门/岗位
 */
@ExtendWith(MockitoExtension.class)
class TransferApplyServiceTest {

    @Mock
    private BizTransferApplyMapper transferApplyMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private TransferApplyService transferApplyService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizTransferApply.class);
    }

    @Test
    @DisplayName("新增调动申请：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizTransferApply apply = new BizTransferApply();
        apply.setUserName("张三");

        transferApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(transferApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("更新调动申请：仅 DRAFT 可编辑 + status 剥离防篡改（P1 修复）")
    void update_draftGuardAndStatusStripped() {
        BizTransferApply existing = new BizTransferApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(transferApplyMapper.selectById(1L)).thenReturn(existing);

        BizTransferApply apply = new BizTransferApply();
        apply.setId(1L);
        apply.setStatus("APPROVED"); // 恶意携带 status

        transferApplyService.update(apply);

        verify(transferApplyMapper).updateById(argThat(a -> a.getStatus() == null));

        // 非草稿拒绝
        BizTransferApply submitted = new BizTransferApply();
        submitted.setId(2L);
        submitted.setStatus("SUBMITTED");
        when(transferApplyMapper.selectById(2L)).thenReturn(submitted);
        BizTransferApply upd2 = new BizTransferApply();
        upd2.setId(2L);
        assertThatThrownBy(() -> transferApplyService.update(upd2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除调动申请：非草稿状态拒绝删除")
    void delete_nonDraft_rejected() {
        BizTransferApply existing = new BizTransferApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(transferApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> transferApplyService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除调动申请：草稿状态正常删除")
    void delete_draft_success() {
        BizTransferApply existing = new BizTransferApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(transferApplyMapper.selectById(1L)).thenReturn(existing);

        transferApplyService.delete(1L);

        verify(transferApplyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("提交调动申请：不存在抛异常")
    void submit_notFound_throwsException() {
        when(transferApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> transferApplyService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调动申请不存在");
    }

    @Test
    @DisplayName("提交调动申请：非草稿状态拒绝提交")
    void submit_nonDraft_rejected() {
        BizTransferApply existing = new BizTransferApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(transferApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> transferApplyService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交调动申请：置 SUBMITTED 中间态，不提前变更员工部门/岗位（P1 审批后生效修复）")
    void submit_success_setsSubmittedOnly() {
        BizTransferApply apply = new BizTransferApply();
        apply.setId(1L);
        apply.setUserName("张三");
        apply.setUserId(100L);
        apply.setToOrgId(200L);
        apply.setToPostId(300L);
        apply.setStatus("DRAFT");
        when(transferApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");

        transferApplyService.submit(1L);

        assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        verify(transferApplyMapper).updateById(apply);
        verify(approvalService).startProcess(eq("TRANSFER_APPLY"), eq(1L),
                eq("transfer_apply_approval"), anyMap());
        // 未审批不得变更员工部门/岗位
        verify(userMapper, org.mockito.Mockito.never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("审批通过回调：SUBMITTED→APPROVED 并同步更新员工部门/岗位")
    void onApproved_success_updatesUserOrgAndPost() {
        BizTransferApply apply = new BizTransferApply();
        apply.setId(1L);
        apply.setUserId(100L);
        apply.setToOrgId(200L);
        apply.setToPostId(300L);
        apply.setStatus("SUBMITTED");
        when(transferApplyMapper.selectById(1L)).thenReturn(apply);
        SysUser user = new SysUser();
        user.setId(100L);
        user.setOrgId(10L);
        user.setPostId(20L);
        when(userMapper.selectById(100L)).thenReturn(user);

        transferApplyService.onApproved(1L);

        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        // 员工部门/岗位按申请单目标值回写
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(200L);
        assertThat(captor.getValue().getPostId()).isEqualTo(300L);
    }

    @Test
    @DisplayName("审批通过回调：员工不存在时仅告警不回写；幂等（非 SUBMITTED 跳过）")
    void onApproved_userNotFoundAndIdempotent() {
        BizTransferApply apply = new BizTransferApply();
        apply.setId(1L);
        apply.setUserId(100L);
        apply.setStatus("SUBMITTED");
        when(transferApplyMapper.selectById(1L)).thenReturn(apply);
        when(userMapper.selectById(100L)).thenReturn(null);

        transferApplyService.onApproved(1L);

        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        verify(userMapper, org.mockito.Mockito.never()).updateById(any(SysUser.class));

        // 幂等：非 SUBMITTED 跳过
        transferApplyService.onApproved(1L);
        verify(transferApplyMapper, org.mockito.Mockito.times(1)).updateById(any());
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizTransferApply> stubPage = new Page<>(1, 10);
        stubPage.setTotal(4);
        when(transferApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizTransferApply> result = transferApplyService.page(1, 10);

        assertThat(result.getTotal()).isEqualTo(4);
    }
}
