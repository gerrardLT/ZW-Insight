package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.BizSettlementContractDetail;
import com.zwinsight.finance.domain.dto.ExpenseContractInfo;
import com.zwinsight.finance.domain.dto.ProjectSettlementUpdateDTO;
import com.zwinsight.finance.mapper.BizProjectSettlementMapper;
import com.zwinsight.finance.mapper.BizSettlementContractDetailMapper;
import com.zwinsight.finance.mapper.SettlementDataMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProjectSettlementService 单元测试（全量补充）
 * <p>项目最终结算：创建汇总、编辑（重新汇总/手动调整）、提交审批、审批回调、未结清查询、Excel 导出。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectSettlementServiceFullTest {

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

    private BizProject project(String status) {
        BizProject p = new BizProject();
        p.setId(1L);
        p.setProjectName("测试项目");
        p.setStatus(status);
        return p;
    }

    private BizProjectSettlement settlement(Long id, String status) {
        BizProjectSettlement s = new BizProjectSettlement();
        s.setId(id);
        s.setProjectId(1L);
        s.setStatus(status);
        // 非重新汇总编辑分支直接读取这些字段，需全部初始化
        s.setSubcontractSettled(new BigDecimal("100"));
        s.setLaborSettled(new BigDecimal("200"));
        s.setMaterialSettled(new BigDecimal("300"));
        s.setMachineSettled(new BigDecimal("400"));
        s.setCumulativePaid(new BigDecimal("500"));
        s.setRewardPunishNet(new BigDecimal("10"));
        s.setOtherExpense(new BigDecimal("20"));
        s.setTotalIncome(new BigDecimal("2000"));
        return s;
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

    // ── createSettlement ──────────────────────────────────

    @Nested
    @DisplayName("createSettlement 创建结算单")
    class CreateTests {

        @Test
        @DisplayName("守卫：项目不存在/未竣工/已有进行中结算单")
        void guardCases_throws() {
            when(projectMapper.selectById(1L)).thenReturn(null);
            assertThatThrownBy(() -> service.createSettlement(1L)).hasMessageContaining("项目不存在");

            when(projectMapper.selectById(2L)).thenReturn(project("CONSTRUCTION"));
            assertThatThrownBy(() -> service.createSettlement(2L)).hasMessageContaining("项目未竣工");

            when(projectMapper.selectById(3L)).thenReturn(project("COMPLETED"));
            when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
            assertThatThrownBy(() -> service.createSettlement(3L)).hasMessageContaining("已存在进行中的结算单");
        }

        @Test
        @DisplayName("正常创建：收入取累计产值、支出六项汇总、利润与利润率、生成合同明细")
        void success_fullAggregation() {
            when(projectMapper.selectById(1L)).thenReturn(project("COMPLETED"));
            when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            // 两份施工合同（其中一份金额为 null 验证跳过）
            BizConstructionContract c1 = new BizConstructionContract();
            c1.setContractAmount(new BigDecimal("1000000"));
            c1.setCumulativeOutput(new BigDecimal("900000"));
            BizConstructionContract c2 = new BizConstructionContract();
            c2.setContractAmount(null);
            c2.setCumulativeOutput(new BigDecimal("100000"));
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(c1, c2));

            when(settlementDataMapper.sumReceivedByProject(1L)).thenReturn(new BigDecimal("800000"));
            when(settlementDataMapper.sumInvoicedByProject(1L)).thenReturn(new BigDecimal("950000"));
            when(settlementDataMapper.sumSubcontractSettlement(1L)).thenReturn(new BigDecimal("200000"));
            when(settlementDataMapper.sumLaborSettlement(1L)).thenReturn(new BigDecimal("100000"));
            when(settlementDataMapper.sumMaterialSettlement(1L)).thenReturn(new BigDecimal("150000"));
            when(settlementDataMapper.sumMachineSettlement(1L)).thenReturn(new BigDecimal("50000"));
            when(settlementDataMapper.sumPaymentByProject(1L)).thenReturn(new BigDecimal("165000"));
            when(settlementDataMapper.sumRewardPunishNetByProject(1L)).thenReturn(new BigDecimal("-5000"));
            doAnswer(inv -> {
                BizProjectSettlement s = inv.getArgument(0);
                s.setId(100L);
                return 1;
            }).when(settlementMapper).insert(any(BizProjectSettlement.class));
            // 合同明细：分包已结清 + 劳务未结清
            when(settlementMapper.selectExpenseContracts(eq(1L), eq("biz_subcontract")))
                    .thenReturn(Collections.singletonList(expenseInfo(11L, "200000", "200000", "180000")));
            when(settlementMapper.selectExpenseContracts(eq(1L), eq("biz_labor_contract")))
                    .thenReturn(Collections.singletonList(expenseInfo(12L, "100000", "60000", null)));
            when(settlementMapper.selectExpenseContracts(eq(1L), eq("biz_purchase_contract")))
                    .thenReturn(Collections.emptyList());
            when(settlementMapper.selectExpenseContracts(eq(1L), eq("biz_machine_contract")))
                    .thenReturn(Collections.emptyList());

            Long settlementId = service.createSettlement(1L);

            assertThat(settlementId).isEqualTo(100L);
            ArgumentCaptor<BizProjectSettlement> captor = ArgumentCaptor.forClass(BizProjectSettlement.class);
            verify(settlementMapper).insert(captor.capture());
            BizProjectSettlement saved = captor.getValue();
            // 总收入 = 累计产值 900000+100000 = 1000000
            assertThat(saved.getTotalIncome()).isEqualByComparingTo("1000000.00");
            // 总支出 = 200000+100000+150000+50000+165000+(-5000) = 660000
            assertThat(saved.getTotalExpenditure()).isEqualByComparingTo("660000.00");
            assertThat(saved.getProfit()).isEqualByComparingTo("340000.00");
            // 利润率 = 340000/1000000 = 34.00%
            assertThat(saved.getProfitRate()).isEqualByComparingTo("34.00");
            assertThat(saved.getStatus()).isEqualTo("DRAFT");
            assertThat(saved.getSettlementCode()).startsWith("JS-1-");

            // 两条合同明细，状态分别为已结清/未结清
            ArgumentCaptor<BizSettlementContractDetail> detailCaptor =
                    ArgumentCaptor.forClass(BizSettlementContractDetail.class);
            verify(detailMapper, times(2)).insert(detailCaptor.capture());
            List<BizSettlementContractDetail> details = detailCaptor.getAllValues();
            assertThat(details).extracting(BizSettlementContractDetail::getSettlementStatus)
                    .containsExactly("SETTLED", "UNSETTLED");
            assertThat(details.get(1).getUnsettledAmount()).isEqualByComparingTo("40000.00");
            assertThat(details.get(1).getPaidAmount()).isEqualByComparingTo("0"); // null → 0
        }
    }

    // ── page / getById ──────────────────────────────────

    @Test
    @DisplayName("page - 分页透传；getById - 不存在抛异常")
    void pageAndGetById() {
        Page<BizProjectSettlement> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);
        when(settlementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        PageResult<BizProjectSettlement> result = service.page(1, 10, 1L, "DRAFT");
        assertThat(result.getRecords()).isEmpty();

        when(settlementMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L)).hasMessageContaining("结算单不存在");
    }

    // ── updateSettlement ──────────────────────────────────

    @Nested
    @DisplayName("updateSettlement 编辑结算单")
    class UpdateTests {

        @Test
        @DisplayName("守卫：不存在/状态不可编辑")
        void guardCases_throws() {
            when(settlementMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> service.updateSettlement(99L, new ProjectSettlementUpdateDTO()))
                    .hasMessageContaining("结算单不存在");

            when(settlementMapper.selectById(1L)).thenReturn(settlement(1L, "APPROVED"));
            assertThatThrownBy(() -> service.updateSettlement(1L, new ProjectSettlementUpdateDTO()))
                    .hasMessageContaining("仅草稿或已驳回状态可编辑");
        }

        @Test
        @DisplayName("驳回后编辑：状态回 DRAFT（P1 FIN-SET-09）")
        void rejectedEdit_backToDraft() {
            BizProjectSettlement s = settlement(1L, "REJECTED");
            when(settlementMapper.selectById(1L)).thenReturn(s);

            service.updateSettlement(1L, new ProjectSettlementUpdateDTO());

            assertThat(s.getStatus()).isEqualTo("DRAFT");
            verify(settlementMapper).updateById(s);
        }

        @Test
        @DisplayName("手动调整（不重新汇总）：改最终结算金额与其他支出后重算支出/利润/利润率")
        void manualAdjust_recalculates() {
            BizProjectSettlement s = settlement(1L, "REJECTED");
            when(settlementMapper.selectById(1L)).thenReturn(s);
            ProjectSettlementUpdateDTO dto = new ProjectSettlementUpdateDTO();
            dto.setFinalSettlementAmount(new BigDecimal("3000"));
            dto.setOtherExpense(new BigDecimal("50"));

            service.updateSettlement(1L, dto);

            // totalIncome 更新为最终结算金额
            assertThat(s.getTotalIncome()).isEqualByComparingTo("3000.00");
            assertThat(s.getOtherExpense()).isEqualByComparingTo("50.00");
            // 总支出 = 100+200+300+400+500+10+50 = 1560
            assertThat(s.getTotalExpenditure()).isEqualByComparingTo("1560.00");
            // 利润 = 3000-1560 = 1440；利润率 = 48.00
            assertThat(s.getProfit()).isEqualByComparingTo("1440.00");
            assertThat(s.getProfitRate()).isEqualByComparingTo("48.00");
            // REJECTED 编辑后回到 DRAFT
            assertThat(s.getStatus()).isEqualTo("DRAFT");
            verify(settlementMapper).updateById(s);
            verify(constructionContractMapper, never()).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("手动调整 - 无字段变更时不重算")
        void manualAdjust_noChange_skipsRecalc() {
            BizProjectSettlement s = settlement(1L, "DRAFT");
            BigDecimal profitBefore = new BigDecimal("999");
            s.setProfit(profitBefore);
            when(settlementMapper.selectById(1L)).thenReturn(s);

            service.updateSettlement(1L, new ProjectSettlementUpdateDTO());

            assertThat(s.getProfit()).isEqualByComparingTo("999"); // 未重算
            verify(settlementMapper).updateById(s);
        }

        @Test
        @DisplayName("重新汇总：拉取最新收支并重算，删除旧明细重新生成")
        void resummarize_fullRecalc() {
            BizProjectSettlement s = settlement(1L, "DRAFT");
            when(settlementMapper.selectById(1L)).thenReturn(s);
            BizConstructionContract c = new BizConstructionContract();
            c.setContractAmount(new BigDecimal("5000"));
            c.setCumulativeOutput(new BigDecimal("4800"));
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(c));
            when(settlementDataMapper.sumReceivedByProject(1L)).thenReturn(new BigDecimal("4000"));
            when(settlementDataMapper.sumInvoicedByProject(1L)).thenReturn(new BigDecimal("4500"));
            when(settlementDataMapper.sumSubcontractSettlement(1L)).thenReturn(new BigDecimal("1000"));
            when(settlementDataMapper.sumLaborSettlement(1L)).thenReturn(new BigDecimal("800"));
            when(settlementDataMapper.sumMaterialSettlement(1L)).thenReturn(new BigDecimal("600"));
            when(settlementDataMapper.sumMachineSettlement(1L)).thenReturn(new BigDecimal("400"));
            when(settlementDataMapper.sumPaymentByProject(1L)).thenReturn(new BigDecimal("500"));
            when(settlementDataMapper.sumRewardPunishNetByProject(1L)).thenReturn(BigDecimal.ZERO);
            when(settlementMapper.selectExpenseContracts(anyLong(), anyString()))
                    .thenReturn(Collections.emptyList());

            ProjectSettlementUpdateDTO dto = new ProjectSettlementUpdateDTO();
            dto.setResummarize(true);
            dto.setFinalSettlementAmount(new BigDecimal("5200")); // 最终结算金额优先
            dto.setOtherExpense(new BigDecimal("30"));

            service.updateSettlement(1L, dto);

            // 总收入 = 最终结算金额 5200（优先于累计产值）
            assertThat(s.getTotalIncome()).isEqualByComparingTo("5200.00");
            assertThat(s.getFinalSettlementAmount()).isEqualByComparingTo("5200.00");
            // 总支出 = 1000+800+600+400+500+0+30 = 3330
            assertThat(s.getTotalExpenditure()).isEqualByComparingTo("3330.00");
            assertThat(s.getProfit()).isEqualByComparingTo("1870.00");
            verify(detailMapper).delete(any(LambdaQueryWrapper.class)); // 删旧明细
        }
    }

    // ── getUnsettledContracts ──────────────────────────────────

    @Test
    @DisplayName("getUnsettledContracts - 无结算单返回空；有则查未结清明细")
    void getUnsettledContracts_variants() {
        when(settlementMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThat(service.getUnsettledContracts(1L)).isEmpty();

        when(settlementMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(settlement(100L, "APPROVED"));
        BizSettlementContractDetail detail = new BizSettlementContractDetail();
        when(detailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(detail));
        assertThat(service.getUnsettledContracts(1L)).hasSize(1);
    }

    // ── submit / onApproved / onRejected ──────────────────────────────────

    @Nested
    @DisplayName("审批流转")
    class ApprovalTests {

        @Test
        @DisplayName("submit - 守卫：不存在/状态不允许")
        void submit_guardCases_throws() {
            when(settlementMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("结算单不存在");

            when(settlementMapper.selectById(1L)).thenReturn(settlement(1L, "SUBMITTED"));
            assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿或已驳回状态可提交审批");
        }

        @Test
        @DisplayName("submit - 正常：启动流程置 SUBMITTED 并记录流程实例")
        void submit_success() {
            BizProjectSettlement s = settlement(1L, "DRAFT");
            s.setProfit(new BigDecimal("123"));
            when(settlementMapper.selectById(1L)).thenReturn(s);
            when(approvalService.startProcess(eq("PROJECT_SETTLEMENT"), eq(1L),
                    eq("project_settlement_approval"), anyMap())).thenReturn("proc-9");

            service.submit(1L);

            assertThat(s.getStatus()).isEqualTo("SUBMITTED");
            assertThat(s.getWorkflowInstanceId()).isEqualTo("proc-9");
        }

        @Test
        @DisplayName("onApproved - 置 APPROVED 并将施工合同批量 SETTLED")
        void onApproved_success() {
            BizProjectSettlement s = settlement(1L, "SUBMITTED");
            when(settlementMapper.selectById(1L)).thenReturn(s);
            when(constructionContractMapper.settleByProjectId(1L)).thenReturn(2);

            service.onApproved(1L);

            assertThat(s.getStatus()).isEqualTo("APPROVED");
            verify(constructionContractMapper).settleByProjectId(1L);
        }

        @Test
        @DisplayName("onApproved - 幂等：已 APPROVED 重复回调短路不重复 SETTLED（P0 FIN-SET-14）")
        void onApproved_idempotent() {
            BizProjectSettlement s = settlement(1L, "APPROVED");
            when(settlementMapper.selectById(1L)).thenReturn(s);

            service.onApproved(1L);

            verify(settlementMapper, never()).updateById(any());
            verify(constructionContractMapper, never()).settleByProjectId(any());
        }

        @Test
        @DisplayName("onApproved/onRejected - 结算单不存在抛异常")
        void callbacks_notFound_throws() {
            when(settlementMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> service.onApproved(99L)).hasMessageContaining("结算单不存在");
            assertThatThrownBy(() -> service.onRejected(99L)).hasMessageContaining("结算单不存在");
        }

        @Test
        @DisplayName("onRejected - 置 REJECTED")
        void onRejected_success() {
            BizProjectSettlement s = settlement(1L, "SUBMITTED");
            when(settlementMapper.selectById(1L)).thenReturn(s);

            service.onRejected(1L);

            assertThat(s.getStatus()).isEqualTo("REJECTED");
        }
    }

    // ── exportExcel ──────────────────────────────────

    @Nested
    @DisplayName("exportExcel 导出")
    class ExportTests {

        @Test
        @DisplayName("结算单不存在抛异常")
        void export_notFound_throws() {
            when(settlementMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.exportExcel(99L, mock(HttpServletResponse.class)))
                    .hasMessageContaining("结算单不存在");
        }

        @Test
        @DisplayName("正常导出：双 Sheet 写入、合同类型与状态翻译")
        void export_success_writesExcel() throws Exception {
            BizProjectSettlement s = settlement(1L, "APPROVED");
            s.setSettlementCode("JS-1-20260805");
            s.setProfit(new BigDecimal("100"));
            s.setProfitRate(new BigDecimal("10"));
            when(settlementMapper.selectById(1L)).thenReturn(s);
            when(projectMapper.selectById(1L)).thenReturn(project("COMPLETED"));
            BizSettlementContractDetail detail = new BizSettlementContractDetail();
            detail.setContractType("SUBCONTRACT");
            detail.setSettlementStatus("UNSETTLED");
            detail.setContractCode("FB-001");
            when(detailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(detail));

            HttpServletResponse response = mock(HttpServletResponse.class);
            ServletOutputStream sos = mock(ServletOutputStream.class);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doAnswer(inv -> {
                byte[] bytes = inv.getArgument(0);
                int off = inv.getArgument(1);
                int len = inv.getArgument(2);
                bos.write(bytes, off, len);
                return null;
            }).when(sos).write(any(byte[].class), anyInt(), anyInt());
            when(response.getOutputStream()).thenReturn(sos);

            service.exportExcel(1L, response);

            // xlsx 为 ZIP 格式，魔数 PK
            assertThat(bos.size()).isGreaterThan(100);
            assertThat(bos.toByteArray()[0]).isEqualTo((byte) 'P');
            assertThat(bos.toByteArray()[1]).isEqualTo((byte) 'K');
        }

        @Test
        @DisplayName("IO 异常包装为业务异常")
        void export_ioException_wrapped() throws Exception {
            BizProjectSettlement s = settlement(1L, "APPROVED");
            when(settlementMapper.selectById(1L)).thenReturn(s);
            when(projectMapper.selectById(1L)).thenReturn(null);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(response.getOutputStream()).thenThrow(new java.io.IOException("流损坏"));

            assertThatThrownBy(() -> service.exportExcel(1L, response))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("导出Excel失败");
        }
    }
}
