package com.zwinsight.app.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.basedata.domain.BizSupplierEvaluation;
import com.zwinsight.basedata.mapper.BizSupplierEvaluationMapper;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.purchase.mapper.BizPurchaseSettlementMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商自动评分服务单元测试（评分算法 + 定时任务数据流）
 */
@ExtendWith(MockitoExtension.class)
class SupplierAutoScoreServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizPurchaseContract.class);
        TableInfoHelper.initTableInfo(assistant, BizMaterialInbound.class);
        TableInfoHelper.initTableInfo(assistant, BizMaterialRefund.class);
        TableInfoHelper.initTableInfo(assistant, BizPurchaseSettlement.class);
        TableInfoHelper.initTableInfo(assistant, BizSupplierEvaluation.class);
    }

    @Mock private BizPurchaseContractMapper purchaseContractMapper;
    @Mock private BizPurchaseSettlementMapper settlementMapper;
    @Mock private BizMaterialInboundMapper inboundMapper;
    @Mock private BizMaterialRefundMapper refundMapper;
    @Mock private BizSupplierEvaluationMapper evaluationMapper;
    @Mock private com.zwinsight.security.service.TenantTaskRunner tenantTaskRunner;

    @InjectMocks
    private SupplierAutoScoreService autoScoreService;

    @org.junit.jupiter.api.BeforeEach
    void stubTenantRunner() {
        // 逐租户执行器透传：直接执行单租户逻辑
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            ((java.util.function.LongConsumer) inv.getArgument(1)).accept(9999L);
            return null;
        }).when(tenantTaskRunner).runForActiveTenants(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private BizPurchaseContract contract(Long id, BigDecimal amount) {
        BizPurchaseContract c = new BizPurchaseContract();
        c.setId(id);
        c.setPartyBId(100L);
        c.setPartyBName("测试供应商");
        c.setContractAmount(amount);
        c.setStatus("EFFECTIVE");
        return c;
    }

    @Test
    @DisplayName("评分：无退货+足额履约场景各维度得分正确")
    void testScoreSupplier_goodPerformance() {
        BizPurchaseContract c = contract(1L, new BigDecimal("100000"));
        // 无入库金额（quality 满分）；3 次入库批次/1 份合同（timeliness 满分）
        when(inboundMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(inboundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        // 无退货（service 满分）
        when(refundMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 结算=合同金额（price 满分）
        BizPurchaseSettlement settlement = new BizPurchaseSettlement();
        settlement.setSettlementAmount(new BigDecimal("100000"));
        when(settlementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(settlement));

        autoScoreService.scoreSupplier(100L, List.of(c),
                LocalDate.now().minusMonths(1), LocalDate.now());

        ArgumentCaptor<BizSupplierEvaluation> captor = ArgumentCaptor.forClass(BizSupplierEvaluation.class);
        verify(evaluationMapper).insert(captor.capture());
        BizSupplierEvaluation eval = captor.getValue();
        assertThat(eval.getQualityScore()).isEqualTo(20);
        assertThat(eval.getTimelinessScore()).isEqualTo(20);
        assertThat(eval.getPriceScore()).isEqualTo(20);
        assertThat(eval.getServiceScore()).isEqualTo(20);
        assertThat(eval.getCooperationScore()).isEqualTo(4); // 1 份合同 = 20*1/5
        assertThat(eval.getTotalScore()).isEqualByComparingTo("16.8");
        assertThat(eval.getEvaluationType()).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("评分：高退货率质量 0 分、退货 1 次服务 15 分")
    void testScoreSupplier_highRefundRate() {
        BizPurchaseContract c = contract(1L, new BigDecimal("100000"));
        BizMaterialInbound inbound = new BizMaterialInbound();
        inbound.setTotalAmount(new BigDecimal("10000"));
        when(inboundMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(inbound));
        when(inboundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        BizMaterialRefund refund = new BizMaterialRefund();
        refund.setRefundAmount(new BigDecimal("5000")); // 退货率 50% ≥ 20%
        when(refundMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(refund));
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(settlementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        autoScoreService.scoreSupplier(100L, List.of(c),
                LocalDate.now().minusMonths(1), LocalDate.now());

        ArgumentCaptor<BizSupplierEvaluation> captor = ArgumentCaptor.forClass(BizSupplierEvaluation.class);
        verify(evaluationMapper).insert(captor.capture());
        BizSupplierEvaluation eval = captor.getValue();
        assertThat(eval.getQualityScore()).isZero();
        assertThat(eval.getServiceScore()).isEqualTo(15);
    }

    @Test
    @DisplayName("定时评分：无生效合同时不产生评价记录")
    void testMonthlyAutoScore_noContracts() {
        when(purchaseContractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        autoScoreService.executeMonthlyAutoScore();

        verify(evaluationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("定时评分：按供应商分组逐个评分并落库")
    void testMonthlyAutoScore_scoresBySupplier() {
        when(purchaseContractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(contract(1L, new BigDecimal("100000"))));
        when(inboundMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(inboundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(settlementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        autoScoreService.executeMonthlyAutoScore();

        verify(evaluationMapper).insert(any(BizSupplierEvaluation.class));
    }
}
