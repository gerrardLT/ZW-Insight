package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.purchase.mapper.BizPurchaseSettlementMapper;
import com.zwinsight.purchase.readmodel.MaterialInboundReadMapper;
import com.zwinsight.purchase.readmodel.MaterialInboundView;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PurchaseSettlementService 单元测试
 * <p>核心规则：以入库单为结算依据、金额≤入库金额、一张入库单只结算一次、审批发起即回写合同累计结算。</p>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseSettlementServiceTest {

    @Mock
    private BizPurchaseSettlementMapper settlementMapper;

    @Mock
    private BizPurchaseContractMapper contractMapper;

    @Mock
    private MaterialInboundReadMapper materialInboundReadMapper;

    @Mock
    private SerialNumberService serialNumberService;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private PurchaseSettlementService service;

    private MaterialInboundView inbound(Long id, String status, String amount) {
        MaterialInboundView v = new MaterialInboundView();
        v.setId(id);
        v.setProjectId(10L);
        v.setContractId(20L);
        v.setInboundCode("RK-001");
        v.setStatus(status);
        v.setTotalAmount(amount == null ? null : new BigDecimal(amount));
        return v;
    }

    private BizPurchaseSettlement settlement(String status, String amount) {
        BizPurchaseSettlement s = new BizPurchaseSettlement();
        s.setId(1L);
        s.setProjectId(10L);
        s.setContractId(20L);
        s.setInboundId(5L);
        s.setInboundAmount(new BigDecimal("10000"));
        s.setStatus(status);
        s.setSettlementAmount(amount == null ? null : new BigDecimal(amount));
        return s;
    }

    // ── page / getById ──────────────────────────────────

    @Test
    @DisplayName("page - 分页并填充合同名/供应商名")
    void page_fillsDisplayFields() {
        BizPurchaseSettlement s = settlement("DRAFT", "1000");
        s.setInboundCode("RK-001");
        Page<BizPurchaseSettlement> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>(Collections.singletonList(s)));
        page.setTotal(1L);
        when(settlementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setContractName("采购合同A");
        contract.setSupplierName("供应商A");
        when(contractMapper.selectById(20L)).thenReturn(contract);

        PageResult<BizPurchaseSettlement> result = service.page(1, 10, null, null, null);

        assertThat(result.getRecords().get(0).getContractName()).isEqualTo("采购合同A");
        assertThat(result.getRecords().get(0).getSupplierName()).isEqualTo("供应商A");
    }

    @Test
    @DisplayName("getById - 不存在抛异常，正常时补充入库单号")
    void getById_notFoundAndFill() {
        when(settlementMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L)).hasMessageContaining("采购结算不存在");

        BizPurchaseSettlement s = settlement("DRAFT", "1000");
        s.setInboundCode(null); // 触发从入库单补充
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(contractMapper.selectById(20L)).thenReturn(null);
        when(materialInboundReadMapper.selectById(5L)).thenReturn(inbound(5L, "APPROVED", "10000"));

        BizPurchaseSettlement result = service.getById(1L);

        assertThat(result.getInboundCode()).isEqualTo("RK-001");
    }

    // ── availableInbounds ──────────────────────────────────

    @Test
    @DisplayName("availableInbounds - 合同未选抛异常；剔除已结算的入库单")
    void availableInbounds_filtersSettled() {
        assertThatThrownBy(() -> service.availableInbounds(null))
                .hasMessageContaining("请先选择采购合同");

        MaterialInboundView free = inbound(5L, "APPROVED", "1000");
        MaterialInboundView settled = inbound(6L, "APPROVED", "2000");
        when(materialInboundReadMapper.selectApprovedByContract(20L))
                .thenReturn(new ArrayList<>(Arrays.asList(free, settled)));
        // 顺序调用：入库单 5 无结算记录，入库单 6 已有结算记录
        when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 1L);

        List<MaterialInboundView> result = service.availableInbounds(20L);

        assertThat(result).extracting(MaterialInboundView::getId).containsExactly(5L);
    }

    // ── create ──────────────────────────────────

    @Test
    @DisplayName("create - 守卫：未关联入库单/金额非正/入库单不存在/未审批/未关联合同")
    void create_guardCases_throws() {
        BizPurchaseSettlement noInbound = settlement("DRAFT", "1000");
        noInbound.setInboundId(null);
        assertThatThrownBy(() -> service.create(noInbound)).hasMessageContaining("必须关联入库单");

        BizPurchaseSettlement zeroAmount = settlement("DRAFT", "0");
        assertThatThrownBy(() -> service.create(zeroAmount)).hasMessageContaining("结算金额必须大于0");

        BizPurchaseSettlement s = settlement("DRAFT", "1000");
        when(materialInboundReadMapper.selectById(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(s)).hasMessageContaining("入库单不存在");

        when(materialInboundReadMapper.selectById(5L)).thenReturn(inbound(5L, "DRAFT", "10000"));
        assertThatThrownBy(() -> service.create(s)).hasMessageContaining("仅已审批的入库单可用于结算");

        MaterialInboundView noContract = inbound(5L, "APPROVED", "10000");
        noContract.setContractId(null);
        when(materialInboundReadMapper.selectById(5L)).thenReturn(noContract);
        assertThatThrownBy(() -> service.create(s)).hasMessageContaining("未关联采购合同");
    }

    @Test
    @DisplayName("create - 守卫：重复结算/金额超入库金额")
    void create_duplicateAndOverAmount_throws() {
        BizPurchaseSettlement s = settlement("DRAFT", "1000");
        when(materialInboundReadMapper.selectById(5L)).thenReturn(inbound(5L, "APPROVED", "10000"));
        when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.create(s)).hasMessageContaining("不可重复结算");

        when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        s.setSettlementAmount(new BigDecimal("10001"));
        assertThatThrownBy(() -> service.create(s)).hasMessageContaining("不能大于入库金额");
    }

    @Test
    @DisplayName("create - 正常创建：依入库单带入字段、累计=已审批合计+本次、生成编号置 DRAFT")
    void create_success_fullWriteBack() {
        BizPurchaseSettlement s = settlement("DRAFT", "3000");
        when(materialInboundReadMapper.selectById(5L)).thenReturn(inbound(5L, "APPROVED", "10000"));
        when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 已审批结算合计 2000
        BizPurchaseSettlement approved = settlement("APPROVED", "2000");
        when(settlementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(approved));
        when(serialNumberService.generate("PURCHASE_SETTLEMENT")).thenReturn("PS-001");

        service.create(s);

        assertThat(s.getProjectId()).isEqualTo(10L);
        assertThat(s.getContractId()).isEqualTo(20L);
        assertThat(s.getInboundCode()).isEqualTo("RK-001");
        assertThat(s.getInboundAmount()).isEqualByComparingTo("10000");
        assertThat(s.getCumulativeSettlement()).isEqualByComparingTo("5000");
        assertThat(s.getSettlementNo()).isEqualTo("PS-001");
        assertThat(s.getStatus()).isEqualTo("DRAFT");
        verify(settlementMapper).insert(s);
    }

    // ── update ──────────────────────────────────

    @Test
    @DisplayName("update - 守卫：不存在/非草稿/金额非正/超入库金额")
    void update_guardCases_throws() {
        when(settlementMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(settlement("DRAFT", "100"))).hasMessageContaining("采购结算不存在");

        when(settlementMapper.selectById(1L)).thenReturn(settlement("APPROVED", "1000"));
        assertThatThrownBy(() -> service.update(settlement("APPROVED", "100"))).hasMessageContaining("仅草稿状态可编辑");

        when(settlementMapper.selectById(1L)).thenReturn(settlement("DRAFT", "1000"));
        assertThatThrownBy(() -> service.update(settlement("DRAFT", "0"))).hasMessageContaining("结算金额必须大于0");

        assertThatThrownBy(() -> service.update(settlement("DRAFT", "20000")))
                .hasMessageContaining("不能大于入库金额");
    }

    @Test
    @DisplayName("update - 正常更新：仅改金额/日期/备注，重算累计，结算依据不变")
    void update_success_onlyEditableFields() {
        BizPurchaseSettlement existing = settlement("DRAFT", "1000");
        existing.setInboundCode("RK-001");
        when(settlementMapper.selectById(1L)).thenReturn(existing);
        when(settlementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        BizPurchaseSettlement patch = settlement("DRAFT", "2000");
        patch.setRemark("调整");
        service.update(patch);

        verify(settlementMapper).updateById(argThat(s ->
                s.getSettlementAmount().compareTo(new BigDecimal("2000")) == 0
                        && s.getCumulativeSettlement().compareTo(new BigDecimal("2000")) == 0
                        && "RK-001".equals(s.getInboundCode()))); // 结算依据未被篡改
    }

    // ── delete ──────────────────────────────────

    @Test
    @DisplayName("delete - 仅草稿可删除")
    void delete_draftOnly() {
        when(settlementMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining("采购结算不存在");

        when(settlementMapper.selectById(2L)).thenReturn(settlement("APPROVED", "100"));
        assertThatThrownBy(() -> service.delete(2L)).hasMessageContaining("仅草稿状态可删除");

        when(settlementMapper.selectById(3L)).thenReturn(settlement("DRAFT", "100"));
        service.delete(3L);
        verify(settlementMapper).deleteById(3L);
    }

    @Test
    @DisplayName("delete - E2E_TEST_ 标记数据非草稿放行（E2eTestGuard）")
    void delete_e2eMarkerBypass() {
        BizPurchaseSettlement e2e = settlement("APPROVED", "100");
        e2e.setRemark("E2E_TEST_1723900000000");
        when(settlementMapper.selectById(4L)).thenReturn(e2e);

        service.delete(4L);

        verify(settlementMapper).deleteById(4L);
    }

    // ── submit ──────────────────────────────────

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿")
    void submit_guardCases_throws() {
        when(settlementMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("采购结算不存在");

        when(settlementMapper.selectById(2L)).thenReturn(settlement("APPROVED", "100"));
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("submit - 正常提交：发起审批、置 APPROVED、回写合同累计结算")
    void submit_success_writesBackContract() {
        BizPurchaseSettlement s = settlement("DRAFT", "3000");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(eq("PURCHASE_SETTLEMENT"), eq(1L),
                eq("purchase_settlement_approval"), anyMap())).thenReturn("proc-1");
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setCumulativeSettlement(new BigDecimal("7000"));
        when(contractMapper.selectById(20L)).thenReturn(contract);

        service.submit(1L);

        assertThat(s.getStatus()).isEqualTo("APPROVED");
        assertThat(s.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(contractMapper).updateById(argThat(c ->
                c.getCumulativeSettlement().compareTo(new BigDecimal("10000")) == 0));
    }

    @Test
    @DisplayName("submit - 合同不存在时不回写但不报错")
    void submit_contractMissing_skipsWriteBack() {
        BizPurchaseSettlement s = settlement("DRAFT", "3000");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-1");
        when(contractMapper.selectById(20L)).thenReturn(null);

        service.submit(1L);

        assertThat(s.getStatus()).isEqualTo("APPROVED");
        verify(contractMapper, never()).updateById(any());
    }
}
