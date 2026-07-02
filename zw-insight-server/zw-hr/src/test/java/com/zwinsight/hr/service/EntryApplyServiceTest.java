package com.zwinsight.hr.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.hr.domain.BizEntryApply;
import com.zwinsight.hr.mapper.BizEntryApplyMapper;
import com.zwinsight.system.service.SysUserService;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntryApplyServiceTest {

    @Mock private BizEntryApplyMapper entryApplyMapper;
    @Mock private SysUserService sysUserService;
    @Mock private ApprovalService approvalService;

    @InjectMocks
    private EntryApplyService entryApplyService;

    @Test
    @DisplayName("新增入职申请：默认DRAFT状态")
    void testSave() {
        BizEntryApply apply = new BizEntryApply();
        apply.setRealName("张三");
        when(entryApplyMapper.insert(any())).thenReturn(1);

        entryApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(entryApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(entryApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> entryApplyService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入职申请不存在");
    }

    @Test
    @DisplayName("更新：非DRAFT拒绝")
    void testUpdate_nonDraftRejected() {
        BizEntryApply existing = new BizEntryApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(entryApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> entryApplyService.update(new BizEntryApply() {{ setId(1L); }}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除：非DRAFT拒绝")
    void testDelete_nonDraftRejected() {
        BizEntryApply existing = new BizEntryApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(entryApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> entryApplyService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除：DRAFT可删")
    void testDelete_draftAllowed() {
        BizEntryApply existing = new BizEntryApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(entryApplyMapper.selectById(1L)).thenReturn(existing);

        entryApplyService.delete(1L);

        verify(entryApplyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("提交：DRAFT→APPROVED并创建账号")
    void testSubmit() {
        BizEntryApply apply = new BizEntryApply();
        apply.setId(1L);
        apply.setStatus("DRAFT");
        apply.setRealName("张三");
        apply.setUsername("zhangsan");
        when(entryApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-123");

        entryApplyService.submit(1L);

        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        verify(sysUserService).save(any());
    }
}
