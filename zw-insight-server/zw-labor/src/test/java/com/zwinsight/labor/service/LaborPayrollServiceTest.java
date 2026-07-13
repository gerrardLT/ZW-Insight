package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LaborPayrollService 单元测试
 * 覆盖：工资单 CRUD + DRAFT 状态约束 + BigDecimal 汇总计算
 */
@ExtendWith(MockitoExtension.class)
class LaborPayrollServiceTest {

    @Mock private BizLaborPayrollMapper payrollMapper;
    @Mock private BizWorkOrderMapper workOrderMapper;

    @InjectMocks
    private LaborPayrollService laborPayrollService;

    private BizLaborPayroll samplePayroll;

    @BeforeEach
    void setUp() {
        samplePayroll = new BizLaborPayroll();
        samplePayroll.setId(1L);
        samplePayroll.setProjectId(100L);
        samplePayroll.setTeamId(10L);
        samplePayroll.setPeriodStart(LocalDate.of(2026, 1, 1));
        samplePayroll.setPeriodEnd(LocalDate.of(2026, 1, 31));
        samplePayroll.setStatus("DRAFT");
    }

    // ==================== 保存（工资汇总计算） ====================

    @Test
    @DisplayName("保存工资单：汇总已审批工单金额")
    void testSave_aggregatesApprovedWorkOrders() {
        BizWorkOrder wo1 = new BizWorkOrder();
        wo1.setTotalAmount(new BigDecimal("5000.50"));
        BizWorkOrder wo2 = new BizWorkOrder();
        wo2.setTotalAmount(new BigDecimal("3200.75"));

        when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(wo1, wo2));
        when(payrollMapper.insert(any(BizLaborPayroll.class))).thenReturn(1);

        BizLaborPayroll payroll = new BizLaborPayroll();
        payroll.setProjectId(100L);
        payroll.setTeamId(10L);
        payroll.setPeriodStart(LocalDate.of(2026, 1, 1));
        payroll.setPeriodEnd(LocalDate.of(2026, 1, 31));

        laborPayrollService.save(payroll);

        // 5000.50 + 3200.75 = 8201.25
        assertThat(payroll.getTotalSettlement()).isEqualByComparingTo(new BigDecimal("8201.25"));
        assertThat(payroll.getTotalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(payroll.getUnpaid()).isEqualByComparingTo(new BigDecimal("8201.25"));
        assertThat(payroll.getStatus()).isEqualTo("DRAFT");
        verify(payrollMapper).insert(payroll);
    }

    @Test
    @DisplayName("保存工资单：无已审批工单时金额为零")
    void testSave_noWorkOrders_zeroTotal() {
        when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(payrollMapper.insert(any(BizLaborPayroll.class))).thenReturn(1);

        BizLaborPayroll payroll = new BizLaborPayroll();
        payroll.setProjectId(100L);
        payroll.setTeamId(10L);
        payroll.setPeriodStart(LocalDate.of(2026, 1, 1));
        payroll.setPeriodEnd(LocalDate.of(2026, 1, 31));

        laborPayrollService.save(payroll);

        assertThat(payroll.getTotalSettlement()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(payroll.getTotalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(payroll.getUnpaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("保存工资单：工单金额为 null 时当作零处理")
    void testSave_nullTotalAmountTreatedAsZero() {
        BizWorkOrder wo1 = new BizWorkOrder();
        wo1.setTotalAmount(null);  // null
        BizWorkOrder wo2 = new BizWorkOrder();
        wo2.setTotalAmount(new BigDecimal("4500"));

        when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(wo1, wo2));
        when(payrollMapper.insert(any(BizLaborPayroll.class))).thenReturn(1);

        BizLaborPayroll payroll = new BizLaborPayroll();
        payroll.setProjectId(100L);
        payroll.setTeamId(10L);
        payroll.setPeriodStart(LocalDate.of(2026, 1, 1));
        payroll.setPeriodEnd(LocalDate.of(2026, 1, 31));

        laborPayrollService.save(payroll);

        // 0 + 4500 = 4500
        assertThat(payroll.getTotalSettlement()).isEqualByComparingTo(new BigDecimal("4500"));
    }

    @Test
    @DisplayName("保存工资单：指定 orderType 过滤工单")
    void testSave_withOrderTypeFilter() {
        BizWorkOrder wo = new BizWorkOrder();
        wo.setTotalAmount(new BigDecimal("6000"));

        when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(wo));
        when(payrollMapper.insert(any(BizLaborPayroll.class))).thenReturn(1);

        BizLaborPayroll payroll = new BizLaborPayroll();
        payroll.setProjectId(100L);
        payroll.setTeamId(10L);
        payroll.setPeriodStart(LocalDate.of(2026, 1, 1));
        payroll.setPeriodEnd(LocalDate.of(2026, 1, 31));
        payroll.setOrderType("FIXED");

        laborPayrollService.save(payroll);

        assertThat(payroll.getTotalSettlement()).isEqualByComparingTo(new BigDecimal("6000"));
        verify(workOrderMapper).selectList(any(LambdaQueryWrapper.class));
    }

    // ==================== BigDecimal 精度验证 ====================

    @Test
    @DisplayName("保存工资单：大额 BigDecimal 精度不丢失")
    void testSave_bigDecimalPrecision() {
        BizWorkOrder wo1 = new BizWorkOrder();
        wo1.setTotalAmount(new BigDecimal("999999.99"));
        BizWorkOrder wo2 = new BizWorkOrder();
        wo2.setTotalAmount(new BigDecimal("888888.88"));
        BizWorkOrder wo3 = new BizWorkOrder();
        wo3.setTotalAmount(new BigDecimal("0.01"));

        when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(wo1, wo2, wo3));
        when(payrollMapper.insert(any(BizLaborPayroll.class))).thenReturn(1);

        BizLaborPayroll payroll = new BizLaborPayroll();
        payroll.setProjectId(100L);
        payroll.setTeamId(10L);
        payroll.setPeriodStart(LocalDate.of(2026, 1, 1));
        payroll.setPeriodEnd(LocalDate.of(2026, 1, 31));

        laborPayrollService.save(payroll);

        // 999999.99 + 888888.88 + 0.01 = 1888888.88
        assertThat(payroll.getTotalSettlement()).isEqualByComparingTo(new BigDecimal("1888888.88"));
        assertThat(payroll.getUnpaid()).isEqualByComparingTo(new BigDecimal("1888888.88"));
    }

    // ==================== 提交 ====================

    @Test
    @DisplayName("提交工资单：DRAFT → APPROVED")
    void testSubmit_draftToApproved() {
        when(payrollMapper.selectById(1L)).thenReturn(samplePayroll);

        laborPayrollService.submit(1L);

        verify(payrollMapper).updateById(argThat(p -> "APPROVED".equals(p.getStatus())));
    }

    @Test
    @DisplayName("提交工资单：非 DRAFT 拒绝")
    void testSubmit_nonDraftRejected() {
        samplePayroll.setStatus("APPROVED");
        when(payrollMapper.selectById(1L)).thenReturn(samplePayroll);

        assertThatThrownBy(() -> laborPayrollService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交工资单：不存在抛异常")
    void testSubmit_notFound() {
        when(payrollMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> laborPayrollService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工资单不存在");
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("查询详情：正常返回")
    void testGetById_found() {
        when(payrollMapper.selectById(1L)).thenReturn(samplePayroll);

        BizLaborPayroll result = laborPayrollService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("查询详情：不存在抛异常")
    void testGetById_notFound() {
        when(payrollMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> laborPayrollService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工资单不存在");
    }

    // ==================== 更新 ====================

    @Test
    @DisplayName("更新工资单：DRAFT 可编辑")
    void testUpdate_draftAllowed() {
        when(payrollMapper.selectById(1L)).thenReturn(samplePayroll);

        BizLaborPayroll update = new BizLaborPayroll();
        update.setId(1L);
        laborPayrollService.update(update);

        verify(payrollMapper).updateById(update);
    }

    @Test
    @DisplayName("更新工资单：非 DRAFT 拒绝")
    void testUpdate_nonDraftRejected() {
        samplePayroll.setStatus("APPROVED");
        when(payrollMapper.selectById(1L)).thenReturn(samplePayroll);

        BizLaborPayroll update = new BizLaborPayroll();
        update.setId(1L);

        assertThatThrownBy(() -> laborPayrollService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("更新工资单：不存在抛异常")
    void testUpdate_notFound() {
        when(payrollMapper.selectById(999L)).thenReturn(null);

        BizLaborPayroll update = new BizLaborPayroll();
        update.setId(999L);

        assertThatThrownBy(() -> laborPayrollService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工资单不存在");
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除工资单：DRAFT 可删")
    void testDelete_draftAllowed() {
        when(payrollMapper.selectById(1L)).thenReturn(samplePayroll);

        laborPayrollService.delete(1L);

        verify(payrollMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除工资单：非 DRAFT 拒绝")
    void testDelete_nonDraftRejected() {
        samplePayroll.setStatus("APPROVED");
        when(payrollMapper.selectById(1L)).thenReturn(samplePayroll);

        assertThatThrownBy(() -> laborPayrollService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除工资单：不存在抛异常")
    void testDelete_notFound() {
        when(payrollMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> laborPayrollService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工资单不存在");
    }
}
