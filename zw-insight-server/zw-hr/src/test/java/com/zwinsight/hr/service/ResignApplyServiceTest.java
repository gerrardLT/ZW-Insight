package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizResignApply;
import com.zwinsight.hr.mapper.BizResignApplyMapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResignApplyService（离职申请）单元测试
 *
 * 覆盖场景:
 * - 新增保存（DRAFT 状态）
 * - 提交校验（不存在/非草稿状态）
 * - 提交成功（发起审批 + 停用账号）
 * - 分页查询
 */
@ExtendWith(MockitoExtension.class)
class ResignApplyServiceTest {

    @Mock
    private BizResignApplyMapper resignApplyMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ResignApplyService resignApplyService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizResignApply.class);
    }

    @Test
    @DisplayName("保存离职申请：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizResignApply apply = new BizResignApply();
        apply.setUserName("张三");

        resignApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(resignApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("提交离职申请：不存在抛异常")
    void submit_notFound_throwsException() {
        when(resignApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> resignApplyService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("离职申请不存在");
    }

    @Test
    @DisplayName("提交离职申请：非草稿状态拒绝")
    void submit_nonDraft_rejected() {
        BizResignApply apply = new BizResignApply();
        apply.setId(1L);
        apply.setStatus("APPROVED");
        when(resignApplyMapper.selectById(1L)).thenReturn(apply);

        assertThatThrownBy(() -> resignApplyService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交离职申请：置 SUBMITTED 中间态，不提前停用账号（P1 审批后生效修复）")
    void submit_success_setsSubmittedOnly() {
        BizResignApply apply = new BizResignApply();
        apply.setId(1L);
        apply.setUserId(10L);
        apply.setUserName("张三");
        apply.setStatus("DRAFT");
        when(resignApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(eq("RESIGN_APPLY"), eq(1L), eq("resign_apply_approval"), anyMap()))
                .thenReturn("proc-1");

        resignApplyService.submit(1L);

        assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        verify(resignApplyMapper).updateById(apply);
        // 未审批不得停用账号
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("审批通过回调：SUBMITTED→APPROVED 并停用账号")
    void onApproved_success_disablesUser() {
        BizResignApply apply = new BizResignApply();
        apply.setId(1L);
        apply.setUserId(10L);
        apply.setStatus("SUBMITTED");
        when(resignApplyMapper.selectById(1L)).thenReturn(apply);
        SysUser user = new SysUser();
        user.setId(10L);
        user.setStatus(1);
        when(userMapper.selectById(10L)).thenReturn(user);
        when(resignApplyMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);

        resignApplyService.onApproved(1L);

        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        assertThat(user.getStatus()).isEqualTo(0);
        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("审批通过回调：账号不存在抛异常（回滚，不静默置 APPROVED）；非 SUBMITTED 幂等跳过")
    void onApproved_userNotFoundThrows_andIdempotent() {
        BizResignApply apply = new BizResignApply();
        apply.setId(1L);
        apply.setUserId(10L);
        apply.setStatus("SUBMITTED");
        when(resignApplyMapper.selectById(1L)).thenReturn(apply);
        when(userMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> resignApplyService.onApproved(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号不存在");
        assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(resignApplyMapper, never()).update(any(), any());

        // 幂等：非 SUBMITTED 直接跳过
        apply.setStatus("APPROVED");
        resignApplyService.onApproved(1L);
        verify(userMapper, org.mockito.Mockito.times(1)).selectById(10L);
    }

    @Test
    @DisplayName("审批通过回调：账号属于其他租户拒绝；并发回调 CAS 落空不重复停用")
    void onApproved_crossTenantAndCasLost() {
        BizResignApply apply = new BizResignApply();
        apply.setId(1L);
        apply.setUserId(10L);
        apply.setTenantId(1L);
        apply.setStatus("SUBMITTED");
        when(resignApplyMapper.selectById(1L)).thenReturn(apply);
        SysUser foreign = new SysUser();
        foreign.setId(10L);
        foreign.setTenantId(2L);
        foreign.setStatus(1);
        when(userMapper.selectById(10L)).thenReturn(foreign);

        assertThatThrownBy(() -> resignApplyService.onApproved(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不属于申请所在租户");
        assertThat(foreign.getStatus()).isEqualTo(1);

        SysUser mine = new SysUser();
        mine.setId(10L);
        mine.setTenantId(1L);
        mine.setStatus(1);
        when(userMapper.selectById(10L)).thenReturn(mine);
        when(resignApplyMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(0);
        resignApplyService.onApproved(1L);
        assertThat(mine.getStatus()).isEqualTo(1);
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizResignApply> stubPage = new Page<>(1, 10);
        stubPage.setTotal(5);
        when(resignApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizResignApply> result = resignApplyService.page(1, 10);

        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.getRecords()).isEmpty();
    }
}
