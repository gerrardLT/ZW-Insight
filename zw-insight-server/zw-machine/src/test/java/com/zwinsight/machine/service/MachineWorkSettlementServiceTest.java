package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.machine.domain.*;
import com.zwinsight.machine.dto.*;
import com.zwinsight.machine.mapper.*;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 机械工作量结算服务 单元测试
 * <p>
 * 覆盖：结算单 CRUD + DRAFT 状态约束 + 结算计算（BigDecimal 精度）+ 审批流状态流转
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MachineWorkSettlementService 单元测试")
class MachineWorkSettlementServiceTest {

    @Mock private BizMachineWorkSettlementMapper settlementMapper;
    @Mock private BizMachineWorkSettlementDetailMapper detailMapper;
    @Mock private BizMachineWorkLogMapper workLogMapper;
    @Mock private BizMachineLedgerMapper ledgerMapper;
    @Mock private BizMachineContractMapper contractMapper;
    @Mock private ApprovalService approvalService;

    @InjectMocks
    private MachineWorkSettlementService settlementService;

    private MockedStatic<SecurityContextHolder> securityContextMock;

    @BeforeEach
    void setUp() {
        securityContextMock = mockStatic(SecurityContextHolder.class);
        securityContextMock.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
    }

    @AfterEach
    void tearDown() {
        securityContextMock.close();
    }

    // ==================== 创建结算单测试 ====================

    @Nested
    @DisplayName("创建结算单")
    class CreateSettlementTests {

        @Test
        @DisplayName("正常创建：有工作日志且无重叠周期，结算总金额 BigDecimal 精度正确")
        void createSettlement_happyPath_bigDecimalPrecision() {
            // Given
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            // 无周期重叠
            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(0);

            // 模拟两条工作日志（同一台机械），台班数分别为 1.5 和 2.7
            BizMachineWorkLog log1 = buildWorkLog(100L, 1L, LocalDate.of(2024, 7, 5),
                    new BigDecimal("1.50"), new BigDecimal("100.00"), "UNSETTLED");
            BizMachineWorkLog log2 = buildWorkLog(101L, 1L, LocalDate.of(2024, 7, 10),
                    new BigDecimal("2.70"), new BigDecimal("200.00"), "UNSETTLED");
            when(workLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log1, log2));

            // 模拟有生效合同，台班单价 = 850.33
            BizMachineContract contract = buildContract(1L, "挖掘机-A01", "台班",
                    new BigDecimal("850.33"), "EFFECTIVE");
            when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(contract));

            // 模拟台账
            BizMachineLedger ledger = buildLedger(1L, "挖掘机-A01", "EXC-001");
            when(ledgerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ledger));

            // 模拟生成编号
            when(settlementMapper.getMaxCodeByPrefix(anyString())).thenReturn(null);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);
            when(settlementMapper.updateById(any())).thenReturn(1);

            // When
            MachineSettlementCreateResult result = settlementService.createSettlement(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getExcludedWorkLogCount()).isEqualTo(0);

            // 验证结算单 totalAmount 精度：(1.50 + 2.70) × 850.33 = 4.20 × 850.33 = 3571.39 (HALF_UP)
            BigDecimal expectedTotal = new BigDecimal("1.50").add(new BigDecimal("2.70"))
                    .multiply(new BigDecimal("850.33"))
                    .setScale(2, RoundingMode.HALF_UP);
            assertThat(expectedTotal).isEqualTo(new BigDecimal("3571.39"));

            // 验证 updateById 调用时 totalAmount 正确
            ArgumentCaptor<BizMachineWorkSettlement> updateCaptor = ArgumentCaptor.forClass(BizMachineWorkSettlement.class);
            verify(settlementMapper).updateById(updateCaptor.capture());
            assertThat(updateCaptor.getValue().getTotalAmount()).isEqualByComparingTo(expectedTotal);
        }

        @Test
        @DisplayName("排除已结算工作日志：仅结算未结算的")
        void createSettlement_excludeSettledLogs() {
            // Given
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(0);

            BizMachineWorkLog settled = buildWorkLog(100L, 1L, LocalDate.of(2024, 7, 3),
                    new BigDecimal("2.00"), new BigDecimal("50.00"), "SETTLED");
            BizMachineWorkLog unsettled = buildWorkLog(101L, 1L, LocalDate.of(2024, 7, 5),
                    new BigDecimal("3.00"), new BigDecimal("80.00"), "UNSETTLED");
            when(workLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(settled, unsettled));

            // 无合同关联
            when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(settlementMapper.getMaxCodeByPrefix(anyString())).thenReturn(null);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);
            when(settlementMapper.updateById(any())).thenReturn(1);

            // When
            MachineSettlementCreateResult result = settlementService.createSettlement(request);

            // Then
            assertThat(result.getExcludedWorkLogCount()).isEqualTo(1);
            assertThat(result.getExcludedWorkLogIds()).containsExactly(100L);
        }

        @Test
        @DisplayName("周期开始日期晚于结束日期 - 抛业务异常")
        void createSettlement_invalidPeriod() {
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 31));
            request.setPeriodEnd(LocalDate.of(2024, 7, 1));

            assertThatThrownBy(() -> settlementService.createSettlement(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("周期开始日期不能晚于结束日期");
        }

        @Test
        @DisplayName("周期重叠 - 抛业务异常")
        void createSettlement_overlappingPeriod() {
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(1);

            assertThatThrownBy(() -> settlementService.createSettlement(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结算周期不能重叠");
        }

        @Test
        @DisplayName("无可结算工作日志 - 抛业务异常")
        void createSettlement_noWorkLogs() {
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(0);
            when(workLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> settlementService.createSettlement(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无可结算的工作量记录");
        }

        @Test
        @DisplayName("全部工作日志已结算 - 抛业务异常")
        void createSettlement_allLogsSettled() {
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(0);

            BizMachineWorkLog settled1 = buildWorkLog(100L, 1L, LocalDate.of(2024, 7, 3),
                    new BigDecimal("2.00"), new BigDecimal("50.00"), "SETTLED");
            BizMachineWorkLog settled2 = buildWorkLog(101L, 1L, LocalDate.of(2024, 7, 5),
                    new BigDecimal("3.00"), new BigDecimal("80.00"), "SETTLED");
            when(workLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(settled1, settled2));

            assertThatThrownBy(() -> settlementService.createSettlement(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无可结算的工作量记录");
        }

        @Test
        @DisplayName("多台机械：BigDecimal 精度验证 - 各机械独立计价汇总")
        void createSettlement_multipleMachines_precisionCheck() {
            // Given
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(0);

            // 机械1：台班 3.33，机械2：台班 5.67
            BizMachineWorkLog log1 = buildWorkLog(100L, 1L, LocalDate.of(2024, 7, 5),
                    new BigDecimal("3.33"), null, "UNSETTLED");
            BizMachineWorkLog log2 = buildWorkLog(101L, 2L, LocalDate.of(2024, 7, 6),
                    new BigDecimal("5.67"), null, "UNSETTLED");
            when(workLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log1, log2));

            // 两台机械的合同：单价分别为 1200.55 和 780.99
            BizMachineContract contract1 = buildContract(1L, "挖掘机-A01", "台班",
                    new BigDecimal("1200.55"), "EFFECTIVE");
            BizMachineContract contract2 = buildContract(2L, "压路机-B01", "台班",
                    new BigDecimal("780.99"), "EFFECTIVE");
            when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(contract1, contract2));

            BizMachineLedger ledger1 = buildLedger(1L, "挖掘机-A01", "EXC-001");
            BizMachineLedger ledger2 = buildLedger(2L, "压路机-B01", "ROL-001");
            when(ledgerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ledger1, ledger2));

            when(settlementMapper.getMaxCodeByPrefix(anyString())).thenReturn(null);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);
            when(settlementMapper.updateById(any())).thenReturn(1);

            // When
            MachineSettlementCreateResult result = settlementService.createSettlement(request);

            // Then
            assertThat(result).isNotNull();

            // 机械1：3.33 × 1200.55 = 3997.83 (HALF_UP)
            // 机械2：5.67 × 780.99 = 4428.21 (HALF_UP)
            // 总计：3997.83 + 4428.21 = 8426.04
            BigDecimal sub1 = new BigDecimal("3.33").multiply(new BigDecimal("1200.55"))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal sub2 = new BigDecimal("5.67").multiply(new BigDecimal("780.99"))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal expectedTotal = sub1.add(sub2).setScale(2, RoundingMode.HALF_UP);

            ArgumentCaptor<BizMachineWorkSettlement> captor = ArgumentCaptor.forClass(BizMachineWorkSettlement.class);
            verify(settlementMapper).updateById(captor.capture());
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(expectedTotal);
        }

        @Test
        @DisplayName("无合同关联时：默认台班计价，单价为0，总金额为0")
        void createSettlement_noContract_zeroAmount() {
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(0);

            BizMachineWorkLog log1 = buildWorkLog(100L, 1L, LocalDate.of(2024, 7, 5),
                    new BigDecimal("5.00"), new BigDecimal("200.00"), "UNSETTLED");
            when(workLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log1));
            when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(settlementMapper.getMaxCodeByPrefix(anyString())).thenReturn(null);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);
            when(settlementMapper.updateById(any())).thenReturn(1);

            MachineSettlementCreateResult result = settlementService.createSettlement(request);

            assertThat(result).isNotNull();

            // 无合同时 subtotal = 0
            ArgumentCaptor<BizMachineWorkSettlement> captor = ArgumentCaptor.forClass(BizMachineWorkSettlement.class);
            verify(settlementMapper).updateById(captor.capture());
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("工作量计价模式：subtotal = workVolume × unitPrice")
        void createSettlement_volumePricing() {
            MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
            request.setProjectId(1L);
            request.setPeriodStart(LocalDate.of(2024, 7, 1));
            request.setPeriodEnd(LocalDate.of(2024, 7, 31));

            when(settlementMapper.countOverlapping(eq(1L), any(), any(), isNull())).thenReturn(0);

            // 工作量 = 350.55
            BizMachineWorkLog log1 = buildWorkLog(100L, 1L, LocalDate.of(2024, 7, 5),
                    new BigDecimal("2.00"), new BigDecimal("350.55"), "UNSETTLED");
            when(workLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log1));

            // 合同为工作量计价, 单价 = 12.88
            BizMachineContract contract = buildContract(1L, "泵车-C01", "工作量",
                    new BigDecimal("12.88"), "EFFECTIVE");
            when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(contract));

            BizMachineLedger ledger = buildLedger(1L, "泵车-C01", "PMP-001");
            when(ledgerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ledger));

            when(settlementMapper.getMaxCodeByPrefix(anyString())).thenReturn(null);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);
            when(settlementMapper.updateById(any())).thenReturn(1);

            settlementService.createSettlement(request);

            // 验证：350.55 × 12.88 = 4515.08 (HALF_UP)
            BigDecimal expectedTotal = new BigDecimal("350.55").multiply(new BigDecimal("12.88"))
                    .setScale(2, RoundingMode.HALF_UP);

            ArgumentCaptor<BizMachineWorkSettlement> captor = ArgumentCaptor.forClass(BizMachineWorkSettlement.class);
            verify(settlementMapper).updateById(captor.capture());
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(expectedTotal);
        }
    }

    // ==================== 提交审批测试 ====================

    @Nested
    @DisplayName("提交审批")
    class SubmitForApprovalTests {

        @Test
        @DisplayName("草稿状态提交审批 - 正常")
        void submitForApproval_draftStatus() {
            BizMachineWorkSettlement settlement = buildSettlement(1L, 0, new BigDecimal("5000.00"));
            when(settlementMapper.selectById(1L)).thenReturn(settlement);
            when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                    .thenReturn("proc-instance-001");
            when(settlementMapper.updateById(any())).thenReturn(1);

            settlementService.submitForApproval(1L);

            assertThat(settlement.getStatus()).isEqualTo(1);
            assertThat(settlement.getWorkflowInstanceId()).isEqualTo("proc-instance-001");
            verify(approvalService).startProcess(eq("machine_settlement"), eq(1L), eq("machine_settlement"), anyMap());
        }

        @Test
        @DisplayName("已驳回状态提交审批 - 正常（重新提交）")
        void submitForApproval_rejectedStatus() {
            BizMachineWorkSettlement settlement = buildSettlement(1L, 3, new BigDecimal("3000.00"));
            when(settlementMapper.selectById(1L)).thenReturn(settlement);
            when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                    .thenReturn("proc-instance-002");
            when(settlementMapper.updateById(any())).thenReturn(1);

            settlementService.submitForApproval(1L);

            assertThat(settlement.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("审批中状态提交 - 拒绝")
        void submitForApproval_pendingStatus_rejected() {
            BizMachineWorkSettlement settlement = buildSettlement(1L, 1, new BigDecimal("5000.00"));
            when(settlementMapper.selectById(1L)).thenReturn(settlement);

            assertThatThrownBy(() -> settlementService.submitForApproval(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿或已驳回状态的结算单可提交审批");
        }

        @Test
        @DisplayName("已审批状态提交 - 拒绝")
        void submitForApproval_approvedStatus_rejected() {
            BizMachineWorkSettlement settlement = buildSettlement(1L, 2, new BigDecimal("5000.00"));
            when(settlementMapper.selectById(1L)).thenReturn(settlement);

            assertThatThrownBy(() -> settlementService.submitForApproval(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿或已驳回状态的结算单可提交审批");
        }

        @Test
        @DisplayName("结算单不存在 - 抛异常")
        void submitForApproval_notFound() {
            when(settlementMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> settlementService.submitForApproval(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结算单不存在");
        }
    }

    // ==================== 费用总览测试 ====================

    @Nested
    @DisplayName("项目费用总览")
    class ProjectSummaryTests {

        @Test
        @DisplayName("正常查询费用总览：未付款 = 已结算 - 已付款")
        void getProjectSummary_happyPath() {
            when(detailMapper.sumApprovedAmountByProject(1L)).thenReturn(new BigDecimal("100000.00"));
            BizMachineContract contract = new BizMachineContract();
            contract.setCumulativePaid(new BigDecimal("60000.00"));
            when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(contract));

            LambdaQueryWrapper<BizMachineWorkSettlement> wrapper = new LambdaQueryWrapper<>();
            when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            MachineSettlementSummaryVO summary = settlementService.getProjectSummary(1L);

            assertThat(summary.getProjectId()).isEqualTo(1L);
            assertThat(summary.getTotalSettledAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
            assertThat(summary.getTotalPaidAmount()).isEqualByComparingTo(new BigDecimal("60000.00"));
            // 未付款 = 100000 - 60000 = 40000
            assertThat(summary.getUnpaidAmount()).isEqualByComparingTo(new BigDecimal("40000.00"));
            assertThat(summary.getSettlementCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("项目ID为空 - 抛异常")
        void getProjectSummary_nullProjectId() {
            assertThatThrownBy(() -> settlementService.getProjectSummary(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");
        }

        @Test
        @DisplayName("无合同时已付款为0")
        void getProjectSummary_noContracts() {
            when(detailMapper.sumApprovedAmountByProject(1L)).thenReturn(new BigDecimal("50000.00"));
            when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            MachineSettlementSummaryVO summary = settlementService.getProjectSummary(1L);

            assertThat(summary.getTotalPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getUnpaidAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        }
    }

    // ==================== 分页查询测试 ====================

    @Nested
    @DisplayName("分页查询")
    class PageQueryTests {

        @Test
        @DisplayName("正常分页查询")
        void page_happyPath() {
            MachineSettlementQuery query = new MachineSettlementQuery();
            query.setProjectId(1L);
            query.setPage(1);
            query.setSize(10);

            BizMachineWorkSettlement settlement = buildSettlement(1L, 0, new BigDecimal("5000.00"));
            settlement.setProjectId(1L);
            settlement.setSettlementCode("JXJS-202407-0001");
            settlement.setPeriodStart(LocalDate.of(2024, 7, 1));
            settlement.setPeriodEnd(LocalDate.of(2024, 7, 31));

            Page<BizMachineWorkSettlement> page = new Page<>(1, 10);
            page.setRecords(List.of(settlement));
            page.setTotal(1);

            when(settlementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            PageResult<MachineSettlementVO> result = settlementService.page(query);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getSettlementCode()).isEqualTo("JXJS-202407-0001");
        }
    }

    // ==================== 详情查询测试 ====================

    @Nested
    @DisplayName("结算单详情")
    class DetailQueryTests {

        @Test
        @DisplayName("查询详情 - 含明细和台账信息")
        void getDetail_withDetails() {
            BizMachineWorkSettlement settlement = buildSettlement(1L, 2, new BigDecimal("8000.00"));
            settlement.setProjectId(1L);
            settlement.setSettlementCode("JXJS-202407-0001");
            settlement.setPeriodStart(LocalDate.of(2024, 7, 1));
            settlement.setPeriodEnd(LocalDate.of(2024, 7, 31));
            when(settlementMapper.selectById(1L)).thenReturn(settlement);

            BizMachineWorkSettlementDetail detail = new BizMachineWorkSettlementDetail();
            detail.setId(10L);
            detail.setSettlementId(1L);
            detail.setLedgerId(5L);
            detail.setShiftCount(new BigDecimal("4.00"));
            detail.setWorkVolume(new BigDecimal("200.00"));
            detail.setUnitPrice(new BigDecimal("2000.00"));
            detail.setSubtotal(new BigDecimal("8000.00"));
            detail.setPricingType("SHIFT");
            detail.setWorkLogIds(List.of(100L, 101L, 102L));
            when(detailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(detail));

            BizMachineLedger ledger = buildLedger(5L, "挖掘机-A01", "EXC-001");
            when(ledgerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ledger));

            MachineSettlementVO vo = settlementService.getDetail(1L);

            assertThat(vo.getId()).isEqualTo(1L);
            assertThat(vo.getDetails()).hasSize(1);
            assertThat(vo.getDetails().get(0).getMachineName()).isEqualTo("挖掘机-A01");
            assertThat(vo.getDetails().get(0).getMachineCode()).isEqualTo("EXC-001");
            assertThat(vo.getDetails().get(0).getSubtotal()).isEqualByComparingTo(new BigDecimal("8000.00"));
        }

        @Test
        @DisplayName("查询不存在的结算单 - 抛异常")
        void getDetail_notFound() {
            when(settlementMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> settlementService.getDetail(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结算单不存在");
        }
    }

    // ==================== 辅助方法 ====================

    private BizMachineWorkLog buildWorkLog(Long id, Long machineId, LocalDate workDate,
                                           BigDecimal shiftCount, BigDecimal workQuantity,
                                           String settlementStatus) {
        BizMachineWorkLog log = new BizMachineWorkLog();
        log.setId(id);
        log.setMachineId(machineId);
        log.setProjectId(1L);
        log.setWorkDate(workDate);
        log.setShiftCount(shiftCount);
        log.setWorkQuantity(workQuantity);
        log.setSettlementStatus(settlementStatus);
        return log;
    }

    private BizMachineContract buildContract(Long projectId, String machineName, String rentalType,
                                             BigDecimal contractAmount, String status) {
        BizMachineContract contract = new BizMachineContract();
        contract.setProjectId(projectId);
        contract.setMachineName(machineName);
        contract.setRentalType(rentalType);
        contract.setContractAmount(contractAmount);
        contract.setStatus(status);
        contract.setCumulativeSettlement(BigDecimal.ZERO);
        contract.setCumulativePaid(BigDecimal.ZERO);
        return contract;
    }

    private BizMachineLedger buildLedger(Long id, String machineName, String machineCode) {
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setId(id);
        ledger.setMachineName(machineName);
        ledger.setMachineCode(machineCode);
        return ledger;
    }

    private BizMachineWorkSettlement buildSettlement(Long id, Integer status, BigDecimal totalAmount) {
        BizMachineWorkSettlement settlement = new BizMachineWorkSettlement();
        settlement.setId(id);
        settlement.setStatus(status);
        settlement.setTotalAmount(totalAmount);
        settlement.setCreatedAt(LocalDateTime.now());
        return settlement;
    }
}
