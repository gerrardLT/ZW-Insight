package com.zwinsight.tender.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.tender.domain.BizTenderTask;
import com.zwinsight.tender.mapper.BizTenderTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TenderTaskService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TenderTaskServiceTest {

    @Mock private BizTenderTaskMapper taskMapper;

    @InjectMocks
    private TenderTaskService tenderTaskService;

    @Test
    @DisplayName("列表：按 registerId 查询委托 mapper")
    void testList_delegatesSelect() {
        when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<BizTenderTask> result = tenderTaskService.list(100L);

        assertThat(result).isEmpty();
        verify(taskMapper).selectList(any());
    }

    @Test
    @DisplayName("新增：状态为空时默认 PENDING")
    void testSave_defaultPending() {
        BizTenderTask task = new BizTenderTask();
        when(taskMapper.insert(task)).thenReturn(1);

        tenderTaskService.save(task);

        assertThat(task.getStatus()).isEqualTo("PENDING");
        verify(taskMapper).insert(task);
    }

    @Test
    @DisplayName("新增：已有状态时保留不覆盖")
    void testSave_keepsExistingStatus() {
        BizTenderTask task = new BizTenderTask();
        task.setStatus("COMPLETED");
        when(taskMapper.insert(task)).thenReturn(1);

        tenderTaskService.save(task);

        assertThat(task.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("完成任务：置 COMPLETED 并更新")
    void testComplete_setsCompleted() {
        BizTenderTask task = new BizTenderTask();
        task.setId(1L);
        task.setStatus("PENDING");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(task)).thenReturn(1);

        tenderTaskService.complete(1L);

        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        verify(taskMapper).updateById(task);
    }

    @Test
    @DisplayName("完成任务：不存在抛异常")
    void testComplete_notFound() {
        when(taskMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenderTaskService.complete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标任务不存在");
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新/删除：委托 mapper")
    void testUpdateAndDelete_delegate() {
        BizTenderTask task = new BizTenderTask();
        task.setId(1L);

        tenderTaskService.update(task);
        tenderTaskService.delete(1L);

        verify(taskMapper).updateById(task);
        verify(taskMapper).deleteById(1L);
    }
}
