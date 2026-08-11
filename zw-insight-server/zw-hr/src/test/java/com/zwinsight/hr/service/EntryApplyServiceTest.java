package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizEntryApply;
import com.zwinsight.hr.mapper.BizEntryApplyMapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.system.service.SysUserService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * EntryApplyService（入职申请）单元测试
 *
 * 覆盖场景:
 * - 新增保存（DRAFT 状态）
 * - 查询/更新/删除的状态校验
 * - 提交审批通过后自动创建系统账号
 */
@ExtendWith(MockitoExtension.class)
class EntryApplyServiceTest {

    @Mock
    private BizEntryApplyMapper entryApplyMapper;

    @Mock
    private SysUserService sysUserService;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private EntryApplyService entryApplyService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizEntryApply.class);
    }

    @Test
    @DisplayName("新增入职申请：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizEntryApply apply = new BizEntryApply();
        apply.setRealName("张三");

        entryApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(entryApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void getById_notFound_throwsException() {
        when(entryApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> entryApplyService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入职申请不存在");
    }

    @Test
    @DisplayName("根据ID查询：存在返回实体")
    void getById_found_returnsEntity() {
        BizEntryApply apply = new BizEntryApply();
        apply.setId(1L);
        when(entryApplyMapper.selectById(1L)).thenReturn(apply);

        BizEntryApply result = entryApplyService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("更新入职申请：非草稿状态拒绝编辑")
    void update_nonDraft_rejected() {
        BizEntryApply existing = new BizEntryApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(entryApplyMapper.selectById(1L)).thenReturn(existing);

        BizEntryApply update = new BizEntryApply();
        update.setId(1L);

        assertThatThrownBy(() -> entryApplyService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除入职申请：非草稿状态拒绝删除")
    void delete_nonDraft_rejected() {
        BizEntryApply existing = new BizEntryApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(entryApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> entryApplyService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除入职申请：草稿状态正常删除")
    void delete_draft_success() {
        BizEntryApply existing = new BizEntryApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(entryApplyMapper.selectById(1L)).thenReturn(existing);

        entryApplyService.delete(1L);

        verify(entryApplyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("提交入职申请：不存在抛异常")
    void submit_notFound_throwsException() {
        when(entryApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> entryApplyService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入职申请不存在");
    }

    @Test
    @DisplayName("提交入职申请：审批通过后自动创建系统账号")
    void submit_success_createsSystemAccount() {
        BizEntryApply apply = new BizEntryApply();
        apply.setId(1L);
        apply.setUsername("zhangsan");
        apply.setRealName("张三");
        apply.setPhone("13800138000");
        apply.setOrgId(100L);
        apply.setPostId(200L);
        apply.setStatus("DRAFT");
        when(entryApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");

        entryApplyService.submit(1L);

        // 状态与流程实例回写
        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        assertThat(apply.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(entryApplyMapper).updateById(apply);

        // 自动创建系统账号，字段从申请单带入
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserService).save(captor.capture());
        SysUser created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("zhangsan");
        assertThat(created.getRealName()).isEqualTo("张三");
        assertThat(created.getOrgId()).isEqualTo(100L);
        assertThat(created.getPostId()).isEqualTo(200L);
        assertThat(created.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("提交入职申请：建账号失败（用户名已存在）异常上抛，由 @Transactional 回滚申请状态（失败不吞）")
    void submit_accountCreationFailed_exceptionPropagates() {
        BizEntryApply apply = new BizEntryApply();
        apply.setId(1L);
        apply.setUsername("zhangsan");
        apply.setRealName("张三");
        apply.setStatus("DRAFT");
        when(entryApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");
        // 建账号失败（如用户名已存在）
        doThrow(new com.zwinsight.common.exception.BusinessException("用户名已存在"))
                .when(sysUserService).save(any(SysUser.class));

        // 异常必须上抛（@Transactional 保证申请状态回滚，不残留 APPROVED）
        assertThatThrownBy(() -> entryApplyService.submit(1L))
                .isInstanceOf(com.zwinsight.common.exception.BusinessException.class)
                .hasMessageContaining("用户名已存在");

        verify(sysUserService).save(any(SysUser.class));
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizEntryApply> stubPage = new Page<>(1, 10);
        stubPage.setTotal(3);
        when(entryApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizEntryApply> result = entryApplyService.page(1, 10, "张");

        assertThat(result.getTotal()).isEqualTo(3);
    }
}
