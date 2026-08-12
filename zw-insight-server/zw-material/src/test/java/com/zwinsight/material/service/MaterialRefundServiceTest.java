package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizExpenseContract;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.domain.BizMaterialRefundDetail;
import com.zwinsight.material.dto.MaterialRefundDetailVO;
import com.zwinsight.material.event.MaterialReturnCreatedEvent;
import com.zwinsight.material.mapper.BizMaterialRefundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MaterialRefundService 单元测试
 * <p>材料退款：按入库单价计算退款金额、自动提交审批、审批通过扣减合同已付款、详情组装。</p>
 */
@ExtendWith(MockitoExtension.class)
class MaterialRefundServiceTest {

    @Mock
    private BizMaterialRefundMapper refundMapper;

    @Mock
    private BizMaterialRefundDetailMapper refundDetailMapper;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private BizExpenseContractMapper expenseContractMapper;

    @InjectMocks
    private MaterialRefundService service;

    private MaterialReturnCreatedEvent event() {
        MaterialReturnCreatedEvent.OutboundDetailItem d1 =
                new MaterialReturnCreatedEvent.OutboundDetailItem("螺纹钢", "HRB400", "吨",
                        new BigDecimal("2"), new BigDecimal("3000"));
        MaterialReturnCreatedEvent.OutboundDetailItem d2 =
                new MaterialReturnCreatedEvent.OutboundDetailItem("水泥", "P.O42.5", "袋",
                        new BigDecimal("3"), new BigDecimal("25.5"));
        return new MaterialReturnCreatedEvent(this, 100L, 200L, 300L, Arrays.asList(d1, d2));
    }

    @Test
    @DisplayName("createRefundFromReturn - 金额=数量×入库单价（HALF_UP），明细入库，自动提交审批置 PENDING")
    void createRefundFromReturn_fullFlow() {
        doAnswer(inv -> {
            BizMaterialRefund r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        }).when(refundMapper).insert(any(BizMaterialRefund.class));
        when(approvalService.startProcess(eq("MATERIAL_REFUND"), eq(1L),
                eq("material_refund_approval"), anyMap())).thenReturn("proc-1");

        Long refundId = service.createRefundFromReturn(event());

        assertThat(refundId).isEqualTo(1L);
        // 总额 = 2×3000 + 3×25.5 = 6076.50
        ArgumentCaptor<BizMaterialRefund> refundCaptor = ArgumentCaptor.forClass(BizMaterialRefund.class);
        verify(refundMapper).insert(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getRefundAmount()).isEqualByComparingTo("6076.50");

        ArgumentCaptor<BizMaterialRefundDetail> detailCaptor = ArgumentCaptor.forClass(BizMaterialRefundDetail.class);
        verify(refundDetailMapper, times(2)).insert(detailCaptor.capture());
        assertThat(detailCaptor.getAllValues())
                .extracting(BizMaterialRefundDetail::getAmount)
                .satisfies(amounts -> {
                    assertThat(amounts.get(0)).isEqualByComparingTo("6000.00");
                    assertThat(amounts.get(1)).isEqualByComparingTo("76.50");
                });

        verify(refundMapper).updateById(argThat(r ->
                "PENDING".equals(r.getStatus()) && "proc-1".equals(r.getWorkflowInstanceId())));
    }

    @Test
    @DisplayName("onRefundApproved - APPROVED 时置已审批并扣减合同已付款")
    void onRefundApproved_success() {
        BizMaterialRefund refund = new BizMaterialRefund();
        refund.setId(1L);
        refund.setContractId(200L);
        refund.setRefundAmount(new BigDecimal("6076.50"));
        refund.setStatus("PENDING");
        when(refundMapper.selectById(1L)).thenReturn(refund);
        BizExpenseContract contract = new BizExpenseContract();
        contract.setId(200L);
        contract.setCumulativePaid(new BigDecimal("80000"));
        when(expenseContractMapper.selectById(200L)).thenReturn(contract);

        service.onRefundApproved(new ApprovalCompleteEvent(this, "MATERIAL_REFUND", 1L, "APPROVED"));

        assertThat(refund.getStatus()).isEqualTo("APPROVED");
        verify(expenseContractMapper).deductPaidAmount(200L, new BigDecimal("6076.50"));
    }

    @Test
    @DisplayName("onRefundApproved - 退款额超过合同累计已付款时拒绝（MAT-37/D3 扣减下限守卫）")
    void onRefundApproved_exceedsCumulativePaid_rejected() {
        BizMaterialRefund refund = new BizMaterialRefund();
        refund.setId(1L);
        refund.setContractId(200L);
        refund.setRefundAmount(new BigDecimal("90000"));
        refund.setStatus("PENDING");
        when(refundMapper.selectById(1L)).thenReturn(refund);
        BizExpenseContract contract = new BizExpenseContract();
        contract.setId(200L);
        contract.setCumulativePaid(new BigDecimal("80000"));
        when(expenseContractMapper.selectById(200L)).thenReturn(contract);

        assertThatThrownBy(() -> service.onRefundApproved(
                new ApprovalCompleteEvent(this, "MATERIAL_REFUND", 1L, "APPROVED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退款金额超过合同累计已付款");

        verify(expenseContractMapper, never()).deductPaidAmount(any(), any());
    }

    @Test
    @DisplayName("onRefundApproved - 业务类型不符/结果非 APPROVED/退款不存在均忽略")
    void onRefundApproved_ignoreCases() {
        service.onRefundApproved(new ApprovalCompleteEvent(this, "OTHER", 1L, "APPROVED"));
        service.onRefundApproved(new ApprovalCompleteEvent(this, "MATERIAL_REFUND", 1L, "REJECTED"));
        when(refundMapper.selectById(2L)).thenReturn(null);
        service.onRefundApproved(new ApprovalCompleteEvent(this, "MATERIAL_REFUND", 2L, "APPROVED"));

        verify(refundMapper, never()).updateById(any());
        verify(expenseContractMapper, never()).deductPaidAmount(any(), any());
    }

    @Test
    @DisplayName("onRefundApproved - 幂等（C3）：已 APPROVED 重复事件不重复扣减合同累计已付款")
    void onRefundApproved_alreadyApproved_skipsDeduction() {
        BizMaterialRefund refund = new BizMaterialRefund();
        refund.setId(1L);
        refund.setContractId(200L);
        refund.setRefundAmount(new BigDecimal("6076.50"));
        refund.setStatus("APPROVED");
        when(refundMapper.selectById(1L)).thenReturn(refund);

        service.onRefundApproved(new ApprovalCompleteEvent(this, "MATERIAL_REFUND", 1L, "APPROVED"));

        verify(refundMapper, never()).updateById(any());
        verify(expenseContractMapper, never()).deductPaidAmount(any(), any());
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizMaterialRefund> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(new BizMaterialRefund()));
        page.setTotal(1L);
        when(refundMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizMaterialRefund> result = service.page(1, 10, 200L,
                LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("getDetail - 不存在返回 null；正常组装 VO 与明细项")
    void getDetail_variants() {
        when(refundMapper.selectById(99L)).thenReturn(null);
        assertThat(service.getDetail(99L)).isNull();

        BizMaterialRefund refund = new BizMaterialRefund();
        refund.setId(1L);
        refund.setProjectId(300L);
        refund.setOutboundId(100L);
        refund.setContractId(200L);
        refund.setRefundCode("RF-001");
        refund.setRefundAmount(new BigDecimal("6076.50"));
        refund.setStatus("APPROVED");
        when(refundMapper.selectById(1L)).thenReturn(refund);
        BizMaterialRefundDetail detail = new BizMaterialRefundDetail();
        detail.setId(10L);
        detail.setMaterialName("螺纹钢");
        detail.setQuantity(new BigDecimal("2"));
        detail.setUnitPrice(new BigDecimal("3000"));
        detail.setAmount(new BigDecimal("6000"));
        when(refundDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(detail));

        MaterialRefundDetailVO vo = service.getDetail(1L);

        assertThat(vo.getRefundCode()).isEqualTo("RF-001");
        assertThat(vo.getStatus()).isEqualTo("APPROVED");
        assertThat(vo.getDetails()).hasSize(1);
        assertThat(vo.getDetails().get(0).getMaterialName()).isEqualTo("螺纹钢");
        assertThat(vo.getDetails().get(0).getAmount()).isEqualByComparingTo("6000");
    }
}
