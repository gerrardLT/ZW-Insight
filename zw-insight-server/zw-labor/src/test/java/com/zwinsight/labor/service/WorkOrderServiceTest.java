package com.zwinsight.labor.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorkOrderService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private BizWorkOrderMapper workOrderMapper;

    @InjectMocks
    private WorkOrderService workOrderService;

    @Test
    @DisplayName("保存工单：计算合计金额 + DRAFT 初始化")
    void testSave_calculatesTotalAndDraftInitialized() {
        BizWorkOrder wo = new BizWorkOrder();
        wo.setHours(new BigDecimal("8"));
        wo.setHourlyRate(new BigDecimal("100"));
        wo.setOvertime(new BigDecimal("2"));
        wo.setOvertimeRate(new BigDecimal("150"));
        when(workOrderMapper.insert(any(BizWorkOrder.class))).thenReturn(1);

        workOrderService.save(wo);

        // 8*100 + 2*150 = 800 + 300 = 1100
        assertThat(wo.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1100"));
        assertThat(wo.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("保存工单：空字段默认为零")
    void testSave_nullFieldsDefaultToZero() {
        BizWorkOrder wo = new BizWorkOrder();
        // hours, hourlyRate, overtime, overtimeRate 全部为 null
        when(workOrderMapper.insert(any(BizWorkOrder.class))).thenReturn(1);

        workOrderService.save(wo);

        assertThat(wo.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("批量保存：逐条计算并插入")
    void testBatchSave_insertsAll() {
        BizWorkOrder wo1 = new BizWorkOrder();
        wo1.setHours(new BigDecimal("4"));
        wo1.setHourlyRate(new BigDecimal("50"));

        BizWorkOrder wo2 = new BizWorkOrder();
        wo2.setHours(new BigDecimal("6"));
        wo2.setHourlyRate(new BigDecimal("80"));

        when(workOrderMapper.insert(any(BizWorkOrder.class))).thenReturn(1);

        workOrderService.batchSave(List.of(wo1, wo2));

        verify(workOrderMapper, times(2)).insert(any(BizWorkOrder.class));
        // 4*50 = 200
        assertThat(wo1.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200"));
        // 6*80 = 480
        assertThat(wo2.getTotalAmount()).isEqualByComparingTo(new BigDecimal("480"));
    }

    @Test
    @DisplayName("更新：DRAFT 可编辑并重新计算金额")
    void testUpdate_draftAllowed_recalculates() {
        BizWorkOrder existing = new BizWorkOrder();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(workOrderMapper.selectById(1L)).thenReturn(existing);

        BizWorkOrder update = new BizWorkOrder();
        update.setId(1L);
        update.setHours(new BigDecimal("10"));
        update.setHourlyRate(new BigDecimal("200"));

        workOrderService.update(update);

        // 10*200 = 2000
        assertThat(update.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2000"));
        verify(workOrderMapper).updateById(update);
    }

    @Test
    @DisplayName("更新：非 DRAFT 拒绝")
    void testUpdate_nonDraftRejected() {
        BizWorkOrder existing = new BizWorkOrder();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(workOrderMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> workOrderService.update(new BizWorkOrder() {{ setId(1L); }}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除：DRAFT 可删")
    void testDelete_draftAllowed() {
        BizWorkOrder existing = new BizWorkOrder();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(workOrderMapper.selectById(1L)).thenReturn(existing);

        workOrderService.delete(1L);

        verify(workOrderMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非 DRAFT 拒绝")
    void testDelete_nonDraftRejected() {
        BizWorkOrder existing = new BizWorkOrder();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(workOrderMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> workOrderService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("提交：DRAFT→APPROVED")
    void testSubmit_draftToApproved() {
        BizWorkOrder wo = new BizWorkOrder();
        wo.setId(1L);
        wo.setStatus("DRAFT");
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        workOrderService.submit(1L);

        verify(workOrderMapper).updateById(argThat(w -> "APPROVED".equals(w.getStatus())));
    }

    @Test
    @DisplayName("提交：非 DRAFT 拒绝")
    void testSubmit_nonDraftRejected() {
        BizWorkOrder wo = new BizWorkOrder();
        wo.setId(1L);
        wo.setStatus("APPROVED");
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        assertThatThrownBy(() -> workOrderService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }
}
