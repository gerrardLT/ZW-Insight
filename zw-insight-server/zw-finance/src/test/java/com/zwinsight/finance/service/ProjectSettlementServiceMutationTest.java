package com.zwinsight.finance.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.BizSettlementContractDetail;
import com.zwinsight.finance.domain.dto.ExpenseContractInfo;
import com.zwinsight.finance.domain.dto.ProjectSettlementUpdateDTO;
import com.zwinsight.finance.domain.dto.SettlementDetailExcelDTO;
import com.zwinsight.finance.domain.dto.SettlementSummaryExcelDTO;
import com.zwinsight.finance.mapper.BizProjectSettlementMapper;
import com.zwinsight.finance.mapper.BizSettlementContractDetailMapper;
import com.zwinsight.finance.mapper.SettlementDataMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectSettlementService 变异补强测试（测试成熟度 2.1.3）
 * <p>
 * 针对 PIT 存活变异补断言：
 * <ul>
 *   <li>createSettlement / updateSettlement 全字段写入（ArgumentCaptor 逐字段断言）</li>
 *   <li>零收入利润率边界、合同金额/产值 null 跳过</li>
 *   <li>四类支出合同明细（含 MATERIAL/MACHINE）字段与结清状态</li>
 *   <li>exportExcel 内容回读验证（DTO 字段真实写入 Sheet，翻译值断言）</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("项目结算服务变异补强测试")
class ProjectSettlementServiceMutationTest {

    @Mock
    private BizProjectSettlementMapper settlementMapper;
    @Mock
    private BizSettlementContractDetailMapper detailMapper;
    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private BizConstructionContractMapper constructionContractMapper;
    @Mock
    private SettlementDataMapper settlementDataMapper;
    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ProjectSettlementService service;

    private static final Long PROJECT_ID = 1L;

    private BizProject project(String status) {
        BizProject p = new BizProject();
        p.setId(PROJECT_ID);
        p.setProjectName("变异补强项目");
        p.setStatus(status);
        return p;
    }

    private ExpenseContractInfo expenseInfo(Long id, String amount, String settled, String paid) {
        ExpenseContractInfo info = new ExpenseContractInfo();
        info.setId(id);
        info.setContractCode("HT-" + id);
        info.setContractName("合同" + id);
        info.setContractAmount(amount == null ? null : new BigDecimal(amount));
        info.setCumulativeSettlement(settled == null ? null : new BigDecimal(settled));
        info.setCumulativePaid(paid == null ? null : new BigDecimal(paid));
        return info;
    }

    /** 标准创建前置 mock：项目已竣工、无进行中结算单、全部汇总数据 */
    private void mockCreatePreconditions(String contractAmount, String output) {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project("COMPLETED"));
        when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BizConstructionContract c1 = new BizConstructionContract();
        c1.setContractAmount(contractAmount == null ? null : new BigDecimal(contractAmount));
        c1.setCumulativeOutput(output == null ? null : new BigDecimal(output));
        when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(c1));

        when(settlementDataMapper.sumReceivedByProject(PROJECT_ID)).thenReturn(new BigDecimal("800000"));
        when(settlementDataMapper.sumInvoicedByProject(PROJECT_ID)).thenReturn(new BigDecimal("950000"));
        when(settlementDataMapper.sumSubcontractSettlement(PROJECT_ID)).thenReturn(new BigDecimal("200000"));
        when(settlementDataMapper.sumLaborSettlement(PROJECT_ID)).thenReturn(new BigDecimal("100000"));
        when(settlementDataMapper.sumMaterialSettlement(PROJECT_ID)).thenReturn(new BigDecimal("150000"));
        when(settlementDataMapper.sumMachineSettlement(PROJECT_ID)).thenReturn(new BigDecimal("50000"));
        when(settlementDataMapper.sumPaymentByProject(PROJECT_ID)).thenReturn(new BigDecimal("165000"));
        when(settlementDataMapper.sumRewardPunishNetByProject(PROJECT_ID)).thenReturn(new BigDecimal("-5000"));
        doAnswer(inv -> {
            BizProjectSettlement s = inv.getArgument(0);
            s.setId(100L);
            return 1;
        }).when(settlementMapper).insert(any(BizProjectSettlement.class));
    }

    private void mockEmptyExpenseContracts() {
        for (String table : Arrays.asList("biz_subcontract", "biz_labor_contract",
                "biz_purchase_contract", "biz_machine_contract")) {
            when(settlementMapper.selectExpenseContracts(eq(PROJECT_ID), eq(table)))
                    .thenReturn(Collections.emptyList());
        }
    }

    // ==================== createSettlement 全字段写入 ====================

    @Test
    @DisplayName("创建结算单：全部汇总字段逐一写入（杀 setter 移除变异）")
    void createSettlement_allAggregateFieldsWritten() {
        mockCreatePreconditions("1000000", "900000");
        mockEmptyExpenseContracts();

        service.createSettlement(PROJECT_ID);

        ArgumentCaptor<BizProjectSettlement> captor = ArgumentCaptor.forClass(BizProjectSettlement.class);
        verify(settlementMapper).insert(captor.capture());
        BizProjectSettlement s = captor.getValue();

        assertThat(s.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(s.getSettlementCode()).startsWith("JS-" + PROJECT_ID + "-");
        assertThat(s.getConstructionContractAmount()).isEqualByComparingTo("1000000.00");
        assertThat(s.getCumulativeOutput()).isEqualByComparingTo("900000.00");
        assertThat(s.getCumulativeReceived()).isEqualByComparingTo("800000.00");
        assertThat(s.getCumulativeInvoiced()).isEqualByComparingTo("950000.00");
        assertThat(s.getTotalIncome()).isEqualByComparingTo("900000.00");
        assertThat(s.getSubcontractSettled()).isEqualByComparingTo("200000.00");
        assertThat(s.getLaborSettled()).isEqualByComparingTo("100000.00");
        assertThat(s.getMaterialSettled()).isEqualByComparingTo("150000.00");
        assertThat(s.getMachineSettled()).isEqualByComparingTo("50000.00");
        assertThat(s.getOtherExpense()).isEqualByComparingTo("0.00");
        assertThat(s.getRewardPunishNet()).isEqualByComparingTo("-5000.00");
        assertThat(s.getCumulativePaid()).isEqualByComparingTo("165000.00");
        assertThat(s.getTotalExpenditure()).isEqualByComparingTo("660000.00");
        assertThat(s.getProfit()).isEqualByComparingTo("240000.00");
        assertThat(s.getProfitRate()).isEqualByComparingTo("26.67");
        assertThat(s.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("创建结算单：合同金额/产值为 null 安全跳过且零收入利润率=0（边界）")
    void createSettlement_nullAmountsSkipped_zeroIncomeProfitRateZero() {
        mockCreatePreconditions(null, null);
        mockEmptyExpenseContracts();

        service.createSettlement(PROJECT_ID);

        ArgumentCaptor<BizProjectSettlement> captor = ArgumentCaptor.forClass(BizProjectSettlement.class);
        verify(settlementMapper).insert(captor.capture());
        BizProjectSettlement s = captor.getValue();

        assertThat(s.getConstructionContractAmount()).isEqualByComparingTo("0.00");
        assertThat(s.getCumulativeOutput()).isEqualByComparingTo("0.00");
        assertThat(s.getTotalIncome()).isEqualByComparingTo("0.00");
        // totalIncome = 0 时利润率直接置 0.00，不做除法（>0 边界变异时此处将除零异常）
        assertThat(s.getProfitRate()).isEqualByComparingTo("0.00");
    }

    // ==================== 四类支出合同明细 ====================

    @Test
    @DisplayName("创建结算单：材料/机械合同明细字段完整写入且结清状态正确")
    void createSettlement_materialAndMachineDetailsWritten() {
        mockCreatePreconditions("1000000", "900000");
        when(settlementMapper.selectExpenseContracts(eq(PROJECT_ID), eq("biz_subcontract")))
                .thenReturn(Collections.emptyList());
        when(settlementMapper.selectExpenseContracts(eq(PROJECT_ID), eq("biz_labor_contract")))
                .thenReturn(Collections.emptyList());
        // 材料：已结算 < 合同额 → UNSETTLED；机械：已结算 >= 合同额 → SETTLED
        when(settlementMapper.selectExpenseContracts(eq(PROJECT_ID), eq("biz_purchase_contract")))
                .thenReturn(Collections.singletonList(expenseInfo(21L, "300000", "100000", "90000")));
        when(settlementMapper.selectExpenseContracts(eq(PROJECT_ID), eq("biz_machine_contract")))
                .thenReturn(Collections.singletonList(expenseInfo(22L, "80000", "80000", null)));

        service.createSettlement(PROJECT_ID);

        ArgumentCaptor<BizSettlementContractDetail> captor =
                ArgumentCaptor.forClass(BizSettlementContractDetail.class);
        verify(detailMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<BizSettlementContractDetail> details = captor.getAllValues();

        BizSettlementContractDetail material = details.stream()
                .filter(d -> "MATERIAL".equals(d.getContractType())).findFirst().orElseThrow();
        assertThat(material.getSettlementId()).isEqualTo(100L);
        assertThat(material.getContractId()).isEqualTo(21L);
        assertThat(material.getContractCode()).isEqualTo("HT-21");
        assertThat(material.getContractName()).isEqualTo("合同21");
        assertThat(material.getContractAmount()).isEqualByComparingTo("300000");
        assertThat(material.getSettledAmount()).isEqualByComparingTo("100000");
        assertThat(material.getPaidAmount()).isEqualByComparingTo("90000");
        assertThat(material.getUnsettledAmount()).isEqualByComparingTo("200000.00");
        assertThat(material.getSettlementStatus()).isEqualTo("UNSETTLED");

        BizSettlementContractDetail machine = details.stream()
                .filter(d -> "MACHINE".equals(d.getContractType())).findFirst().orElseThrow();
        assertThat(machine.getSettlementId()).isEqualTo(100L);
        assertThat(machine.getContractId()).isEqualTo(22L);
        assertThat(machine.getPaidAmount()).isEqualByComparingTo("0"); // null → 0
        assertThat(machine.getUnsettledAmount()).isEqualByComparingTo("0.00");
        assertThat(machine.getSettlementStatus()).isEqualTo("SETTLED");
    }

    // ==================== updateSettlement 重新汇总全字段 ====================

    @Test
    @DisplayName("编辑结算单（重新汇总）：全部字段重算写入，最终结算金额优先")
    void updateSettlement_resummarize_allFieldsWritten() {
        BizProjectSettlement existing = new BizProjectSettlement();
        existing.setId(1L);
        existing.setProjectId(PROJECT_ID);
        existing.setStatus("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(existing);

        BizConstructionContract c1 = new BizConstructionContract();
        c1.setContractAmount(new BigDecimal("1000000"));
        c1.setCumulativeOutput(new BigDecimal("900000"));
        when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(c1));
        when(settlementDataMapper.sumReceivedByProject(PROJECT_ID)).thenReturn(new BigDecimal("800000"));
        when(settlementDataMapper.sumInvoicedByProject(PROJECT_ID)).thenReturn(new BigDecimal("950000"));
        when(settlementDataMapper.sumSubcontractSettlement(PROJECT_ID)).thenReturn(new BigDecimal("200000"));
        when(settlementDataMapper.sumLaborSettlement(PROJECT_ID)).thenReturn(new BigDecimal("100000"));
        when(settlementDataMapper.sumMaterialSettlement(PROJECT_ID)).thenReturn(new BigDecimal("150000"));
        when(settlementDataMapper.sumMachineSettlement(PROJECT_ID)).thenReturn(new BigDecimal("50000"));
        when(settlementDataMapper.sumPaymentByProject(PROJECT_ID)).thenReturn(new BigDecimal("165000"));
        when(settlementDataMapper.sumRewardPunishNetByProject(PROJECT_ID)).thenReturn(new BigDecimal("-5000"));
        mockEmptyExpenseContracts();

        ProjectSettlementUpdateDTO dto = new ProjectSettlementUpdateDTO();
        dto.setResummarize(Boolean.TRUE);
        dto.setFinalSettlementAmount(new BigDecimal("1200000")); // 收入以最终结算金额为准
        dto.setOtherExpense(new BigDecimal("30000"));

        service.updateSettlement(1L, dto);

        ArgumentCaptor<BizProjectSettlement> captor = ArgumentCaptor.forClass(BizProjectSettlement.class);
        verify(settlementMapper).updateById(captor.capture());
        BizProjectSettlement s = captor.getValue();

        assertThat(s.getConstructionContractAmount()).isEqualByComparingTo("1000000.00");
        assertThat(s.getCumulativeOutput()).isEqualByComparingTo("900000.00");
        assertThat(s.getCumulativeReceived()).isEqualByComparingTo("800000.00");
        assertThat(s.getCumulativeInvoiced()).isEqualByComparingTo("950000.00");
        assertThat(s.getTotalIncome()).isEqualByComparingTo("1200000.00");
        assertThat(s.getFinalSettlementAmount()).isEqualByComparingTo("1200000.00");
        assertThat(s.getSubcontractSettled()).isEqualByComparingTo("200000.00");
        assertThat(s.getLaborSettled()).isEqualByComparingTo("100000.00");
        assertThat(s.getMaterialSettled()).isEqualByComparingTo("150000.00");
        assertThat(s.getMachineSettled()).isEqualByComparingTo("50000.00");
        assertThat(s.getOtherExpense()).isEqualByComparingTo("30000.00");
        assertThat(s.getRewardPunishNet()).isEqualByComparingTo("-5000.00");
        assertThat(s.getCumulativePaid()).isEqualByComparingTo("165000.00");
        // 总支出 = 200000+100000+150000+50000+165000+(-5000)+30000 = 690000
        assertThat(s.getTotalExpenditure()).isEqualByComparingTo("690000.00");
        assertThat(s.getProfit()).isEqualByComparingTo("510000.00");
        assertThat(s.getProfitRate()).isEqualByComparingTo("42.50");
        // 旧明细被删除并重新生成
        verify(detailMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("编辑结算单（重新汇总）：零总收入时利润率=0（边界）")
    void updateSettlement_resummarize_zeroIncomeProfitRateZero() {
        BizProjectSettlement existing = new BizProjectSettlement();
        existing.setId(1L);
        existing.setProjectId(PROJECT_ID);
        existing.setStatus("DRAFT");
        // otherExpense 故意保持 null：验证生产代码 null 兜底（历史数据致重新汇总 NPE，
        // 2026-08-07 变异测试暴露后已在 ProjectSettlementService 加固）
        when(settlementMapper.selectById(1L)).thenReturn(existing);

        when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(settlementDataMapper.sumReceivedByProject(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        when(settlementDataMapper.sumInvoicedByProject(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        when(settlementDataMapper.sumSubcontractSettlement(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        when(settlementDataMapper.sumLaborSettlement(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        when(settlementDataMapper.sumMaterialSettlement(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        when(settlementDataMapper.sumMachineSettlement(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        when(settlementDataMapper.sumPaymentByProject(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        when(settlementDataMapper.sumRewardPunishNetByProject(PROJECT_ID)).thenReturn(BigDecimal.ZERO);
        mockEmptyExpenseContracts();

        ProjectSettlementUpdateDTO dto = new ProjectSettlementUpdateDTO();
        dto.setResummarize(Boolean.TRUE);
        // 无最终结算金额且累计产值为 0 → totalIncome = 0

        service.updateSettlement(1L, dto);

        ArgumentCaptor<BizProjectSettlement> captor = ArgumentCaptor.forClass(BizProjectSettlement.class);
        verify(settlementMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalIncome()).isEqualByComparingTo("0.00");
        assertThat(captor.getValue().getProfitRate()).isEqualByComparingTo("0.00");
    }

    // ==================== exportExcel 内容回读验证 ====================

    @Test
    @DisplayName("导出Excel：回读双 Sheet 内容验证字段与中文翻译（杀 DTO setter 与翻译变异）")
    void exportExcel_contentVerifiedByReadBack() throws Exception {
        BizProjectSettlement s = new BizProjectSettlement();
        s.setId(1L);
        s.setProjectId(PROJECT_ID);
        s.setStatus("APPROVED");
        s.setSettlementCode("JS-1-20260807");
        s.setConstructionContractAmount(new BigDecimal("1000000.00"));
        s.setCumulativeOutput(new BigDecimal("900000.00"));
        s.setCumulativeReceived(new BigDecimal("800000.00"));
        s.setCumulativeInvoiced(new BigDecimal("950000.00"));
        s.setTotalIncome(new BigDecimal("900000.00"));
        s.setSubcontractSettled(new BigDecimal("200000.00"));
        s.setLaborSettled(new BigDecimal("100000.00"));
        s.setMaterialSettled(new BigDecimal("150000.00"));
        s.setMachineSettled(new BigDecimal("50000.00"));
        s.setCumulativePaid(new BigDecimal("165000.00"));
        s.setTotalExpenditure(new BigDecimal("660000.00"));
        s.setProfit(new BigDecimal("240000.00"));
        s.setProfitRate(new BigDecimal("26.67"));
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project("COMPLETED"));

        BizSettlementContractDetail detail = new BizSettlementContractDetail();
        detail.setContractType("SUBCONTRACT");
        detail.setContractCode("FB-001");
        detail.setContractName("分包合同一");
        detail.setContractAmount(new BigDecimal("200000"));
        detail.setSettledAmount(new BigDecimal("100000"));
        detail.setPaidAmount(new BigDecimal("90000"));
        detail.setUnsettledAmount(new BigDecimal("100000.00"));
        detail.setSettlementStatus("UNSETTLED");
        when(detailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(detail));

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = mock(ServletOutputStream.class);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doAnswer(inv -> {
            bos.write(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(sos).write(any(byte[].class), anyInt(), anyInt());
        when(response.getOutputStream()).thenReturn(sos);

        service.exportExcel(1L, response);

        // Sheet1 回读：汇总字段全部真实写入
        List<SettlementSummaryExcelDTO> summaryRows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(bos.toByteArray()), SettlementSummaryExcelDTO.class,
                        new AnalysisEventListener<SettlementSummaryExcelDTO>() {
                            @Override
                            public void invoke(SettlementSummaryExcelDTO data, AnalysisContext context) {
                                summaryRows.add(data);
                            }

                            @Override
                            public void doAfterAllAnalysed(AnalysisContext context) {
                            }
                        })
                .sheet(0).doRead();
        assertThat(summaryRows).hasSize(1);
        SettlementSummaryExcelDTO sum = summaryRows.get(0);
        assertThat(sum.getSettlementCode()).isEqualTo("JS-1-20260807");
        assertThat(sum.getProjectName()).isEqualTo("变异补强项目");
        assertThat(sum.getConstructionContractAmount()).isEqualByComparingTo("1000000.00");
        assertThat(sum.getCumulativeOutput()).isEqualByComparingTo("900000.00");
        assertThat(sum.getCumulativeReceived()).isEqualByComparingTo("800000.00");
        assertThat(sum.getCumulativeInvoiced()).isEqualByComparingTo("950000.00");
        assertThat(sum.getTotalIncome()).isEqualByComparingTo("900000.00");
        assertThat(sum.getSubcontractSettled()).isEqualByComparingTo("200000.00");
        assertThat(sum.getLaborSettled()).isEqualByComparingTo("100000.00");
        assertThat(sum.getMaterialSettled()).isEqualByComparingTo("150000.00");
        assertThat(sum.getMachineSettled()).isEqualByComparingTo("50000.00");
        assertThat(sum.getCumulativePaid()).isEqualByComparingTo("165000.00");
        assertThat(sum.getTotalExpenditure()).isEqualByComparingTo("660000.00");
        assertThat(sum.getProfit()).isEqualByComparingTo("240000.00");
        assertThat(sum.getProfitRate()).isEqualByComparingTo("26.67");

        // Sheet2 回读：明细字段与中文翻译
        List<SettlementDetailExcelDTO> detailRows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(bos.toByteArray()), SettlementDetailExcelDTO.class,
                        new AnalysisEventListener<SettlementDetailExcelDTO>() {
                            @Override
                            public void invoke(SettlementDetailExcelDTO data, AnalysisContext context) {
                                detailRows.add(data);
                            }

                            @Override
                            public void doAfterAllAnalysed(AnalysisContext context) {
                            }
                        })
                .sheet(1).doRead();
        assertThat(detailRows).hasSize(1);
        SettlementDetailExcelDTO d = detailRows.get(0);
        assertThat(d.getContractType()).isEqualTo("分包合同");
        assertThat(d.getContractCode()).isEqualTo("FB-001");
        assertThat(d.getContractName()).isEqualTo("分包合同一");
        assertThat(d.getContractAmount()).isEqualByComparingTo("200000");
        assertThat(d.getSettledAmount()).isEqualByComparingTo("100000");
        assertThat(d.getPaidAmount()).isEqualByComparingTo("90000");
        assertThat(d.getUnsettledAmount()).isEqualByComparingTo("100000.00");
        assertThat(d.getSettlementStatus()).isEqualTo("未结清");
    }
}
