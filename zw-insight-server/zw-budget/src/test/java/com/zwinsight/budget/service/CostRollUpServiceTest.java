package com.zwinsight.budget.service;

import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountLink;
import com.zwinsight.budget.mapper.BizCostAccountLinkMapper;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.budget.mapper.CostRollUpMapper;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成本归集服务单元测试。
 * <p>
 * 归集是成本主线的自动喂养机制：合同→承诺、结算/出库→实际。
 * 测错的后果是「账自动记错」，比手工记错更危险（没人会去核对自动结果），
 * 因此这里重点钉住三类行为：
 * </p>
 * <ol>
 *   <li><b>口径正确</b>：承诺来自合同，实际来自结算/出库，绝不混用付款</li>
 *   <li><b>歧义不猜</b>：一科目多账户且无绑定时，报告 unmapped 而非按比例分摊</li>
 *   <li><b>幂等收敛</b>：重跑不双记；源单据变化产生新跃迁正常记账</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CostRollUpService 成本归集")
class CostRollUpServiceTest {

    @Mock private CostRollUpMapper rollUpMapper;
    @Mock private BizCostAccountMapper costAccountMapper;
    @Mock private BizCostAccountLinkMapper linkMapper;
    @Mock private CostLedgerService costLedgerService;

    @InjectMocks private CostRollUpService rollUpService;

    private static final Long PROJECT_ID = 100L;

    // ==================== 构造工具 ====================

    private BizCostAccount account(long id, String code, String name, String category,
                                   String commitment, String actual) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setProjectId(PROJECT_ID);
        a.setAccountCode(code);
        a.setAccountName(name);
        a.setCostCategory(category);
        a.setStatus("ACTIVE");
        a.setCommitmentAmount(new BigDecimal(commitment));
        a.setActualAmount(new BigDecimal(actual));
        return a;
    }

    private CostRollUpMapper.SourceDoc doc(long id, String number, String amount) {
        CostRollUpMapper.SourceDoc d = new CostRollUpMapper.SourceDoc();
        d.setSourceId(id);
        d.setSourceNumber(number);
        d.setAmount(new BigDecimal(amount));
        d.setOccurredAt(LocalDateTime.now());
        return d;
    }

    private BizCostAccountLink link(long accountId, String sourceType, String sourceId) {
        BizCostAccountLink l = new BizCostAccountLink();
        l.setProjectId(PROJECT_ID);
        l.setAccountId(accountId);
        l.setSourceType(sourceType);
        l.setSourceId(sourceId);
        return l;
    }

    /** 默认：所有源单据查询返回空，测试按需覆盖 */
    private void stubEmptySources() {
        when(rollUpMapper.listPurchaseContracts(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listLaborContracts(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listMachineContracts(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listSubcontracts(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listPurchaseSettlements(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listLaborSettlements(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listMachineSettlements(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listSubcontractSettlements(anyLong())).thenReturn(List.of());
        when(rollUpMapper.listMaterialOutbounds(anyLong())).thenReturn(List.of());
        when(linkMapper.selectByProject(anyLong())).thenReturn(List.of());
    }

    // ==================== 承诺额归集 ====================

    @Nested
    @DisplayName("承诺额归集（有效合同 → COMMITMENT）")
    class Commitment {

        @Test
        @DisplayName("科目下唯一账户：采购合同自动归集到该账户")
        void singleAccountAutoAllocates() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService).post(captor.capture());

            CostLedgerService.PostCommand cmd = captor.getValue();
            assertThat(cmd.accountId()).isEqualTo(1001L);
            assertThat(cmd.amountType()).isEqualTo(CostLedgerService.AMT_COMMITMENT);
            assertThat(cmd.deltaAmount()).isEqualByComparingTo("100000");
            // 来源类型必须是 ROLLUP 而非 MANUAL：审计要能分辨「系统算的」与「人改的」
            assertThat(cmd.sourceType()).isEqualTo(CostLedgerService.SRC_ROLLUP);
            assertThat(report.getPostedCount()).isEqualTo(1);
            assertThat(report.getUnmapped()).isEmpty();
        }

        @Test
        @DisplayName("多合同累加：同科目两张合同汇总为一个目标值，只记一笔差额")
        void multipleContractsAccumulate() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID)).thenReturn(List.of(
                    doc(555L, "PO-001", "100000"),
                    doc(556L, "PO-002", "50000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService, times(1)).post(captor.capture());
            // 150000 一次性记入，而不是两张单据各记一笔——归集算的是累计目标值
            assertThat(captor.getValue().deltaAmount()).isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("幂等键编码状态跃迁 from→to，重跑可辨识")
        void idempotencyKeyEncodesTransition() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "30000", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService).post(captor.capture());
            CostLedgerService.PostCommand cmd = captor.getValue();
            // current=30000 → target=100000，delta=70000，键含 30000->100000
            assertThat(cmd.deltaAmount()).isEqualByComparingTo("70000");
            assertThat(cmd.sourceId()).contains("ROLLUP").contains("1001").contains("30000->100000");
        }

        @Test
        @DisplayName("目标值与当前值相等：delta=0，完全不写流水")
        void noChangeNoPosting() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "100000", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            verify(costLedgerService, never()).post(any());
            assertThat(report.getPostedCount()).isZero();
            assertThat(report.getUnmapped()).isEmpty();
        }

        @Test
        @DisplayName("源单据减少（合同作废）：产生负 delta，把多记的承诺冲回")
        void sourceDecreaseProducesNegativeDelta() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "100000", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            // 原先 100000 的合同已不在有效集合中，只剩 40000
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(556L, "PO-002", "40000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService).post(captor.capture());
            assertThat(captor.getValue().deltaAmount()).isEqualByComparingTo("-60000");
        }
    }

    // ==================== 实际成本归集 ====================

    @Nested
    @DisplayName("实际成本归集（结算/出库 → ACTUAL）")
    class Actual {

        @Test
        @DisplayName("劳务结算归集到劳务账户的 ACTUAL")
        void laborSettlementToActual() {
            BizCostAccount labor = account(1002L, "02", "劳务", "LABOR", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(labor));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listLaborSettlements(PROJECT_ID))
                    .thenReturn(List.of(doc(777L, "LS-001", "50000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService).post(captor.capture());
            assertThat(captor.getValue().amountType()).isEqualTo(CostLedgerService.AMT_ACTUAL);
            assertThat(captor.getValue().deltaAmount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("材料实际 = 采购结算 + 出库消耗，两者累加到同一账户")
        void materialActualCombinesSettlementAndOutbound() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseSettlements(PROJECT_ID))
                    .thenReturn(List.of(doc(700L, "PS-001", "30000")));
            when(rollUpMapper.listMaterialOutbounds(PROJECT_ID))
                    .thenReturn(List.of(doc(888L, "OUT-001", "20000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService, times(1)).post(captor.capture());
            assertThat(captor.getValue().amountType()).isEqualTo(CostLedgerService.AMT_ACTUAL);
            assertThat(captor.getValue().deltaAmount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("承诺与实际互不干扰：合同不进 ACTUAL，结算不进 COMMITMENT")
        void commitmentAndActualStaySeparate() {
            BizCostAccount sub = account(1003L, "03", "分包", "SUBCONTRACT", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(sub));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listSubcontracts(PROJECT_ID))
                    .thenReturn(List.of(doc(600L, "SC-001", "200000")));
            when(rollUpMapper.listSubcontractSettlements(PROJECT_ID))
                    .thenReturn(List.of(doc(601L, "SS-001", "80000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService, times(2)).post(captor.capture());

            List<CostLedgerService.PostCommand> cmds = captor.getAllValues();
            CostLedgerService.PostCommand commitmentCmd = cmds.stream()
                    .filter(c -> CostLedgerService.AMT_COMMITMENT.equals(c.amountType())).findFirst().orElseThrow();
            CostLedgerService.PostCommand actualCmd = cmds.stream()
                    .filter(c -> CostLedgerService.AMT_ACTUAL.equals(c.amountType())).findFirst().orElseThrow();

            assertThat(commitmentCmd.deltaAmount()).isEqualByComparingTo("200000");
            assertThat(actualCmd.deltaAmount()).isEqualByComparingTo("80000");
        }

        @Test
        @DisplayName("材料退货（负金额出库）自然冲减实际成本，无需特判")
        void negativeOutboundOffsetsActual() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "50000");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listMaterialOutbounds(PROJECT_ID)).thenReturn(List.of(
                    doc(888L, "OUT-001", "50000"),
                    doc(889L, "RET-001", "-8000")));   // 退货单
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService).post(captor.capture());
            // target = 50000 - 8000 = 42000，current = 50000 → delta = -8000
            assertThat(captor.getValue().deltaAmount()).isEqualByComparingTo("-8000");
        }
    }

    // ==================== 歧义处理：不猜 ====================

    @Nested
    @DisplayName("歧义不猜：多账户需显式绑定")
    class Ambiguity {

        @Test
        @DisplayName("同科目两账户且无绑定：单据进 unmapped 报告，不做任何分摊")
        void ambiguousWithoutBindingReportsUnmapped() {
            BizCostAccount concrete = account(1001L, "01.02", "混凝土", "MATERIAL", "0", "0");
            BizCostAccount steel = account(1002L, "01.03", "钢筋", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(concrete, steel));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            // 关键断言：绝不自动分摊
            verify(costLedgerService, never()).post(any());
            assertThat(report.getPostedCount()).isZero();
            assertThat(report.getUnmapped()).hasSize(1);

            CostRollUpService.UnmappedDoc unmapped = report.getUnmapped().get(0);
            assertThat(unmapped.getSourceNumber()).isEqualTo("PO-001");
            assertThat(unmapped.getAmount()).isEqualByComparingTo("100000");
            assertThat(unmapped.getReason()).contains("2 个成本账户");
            assertThat(report.needsAttention()).isTrue();
        }

        @Test
        @DisplayName("显式绑定优先：绑到钢筋账户就记钢筋，不受账户顺序影响")
        void explicitBindingWins() {
            BizCostAccount concrete = account(1001L, "01.02", "混凝土", "MATERIAL", "0", "0");
            BizCostAccount steel = account(1002L, "01.03", "钢筋", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(concrete, steel));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(
                    link(1002L, "PURCHASE_CONTRACT", "555")));
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService).post(captor.capture());
            assertThat(captor.getValue().accountId()).isEqualTo(1002L);
            assertThat(report.getUnmapped()).isEmpty();
        }

        @Test
        @DisplayName("部分绑定：已绑定的正常归集，未绑定的进 unmapped")
        void partialBindingSplitsCorrectly() {
            BizCostAccount concrete = account(1001L, "01.02", "混凝土", "MATERIAL", "0", "0");
            BizCostAccount steel = account(1002L, "01.03", "钢筋", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(concrete, steel));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(
                    link(1002L, "PURCHASE_CONTRACT", "555")));
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID)).thenReturn(List.of(
                    doc(555L, "PO-001", "100000"),   // 已绑定 → 钢筋
                    doc(556L, "PO-002", "30000")));   // 未绑定 → unmapped
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            ArgumentCaptor<CostLedgerService.PostCommand> captor =
                    ArgumentCaptor.forClass(CostLedgerService.PostCommand.class);
            verify(costLedgerService).post(captor.capture());
            assertThat(captor.getValue().accountId()).isEqualTo(1002L);
            assertThat(captor.getValue().deltaAmount()).isEqualByComparingTo("100000");

            assertThat(report.getUnmapped()).hasSize(1);
            assertThat(report.getUnmapped().get(0).getSourceNumber()).isEqualTo("PO-002");
        }

        @Test
        @DisplayName("科目下无账户：单据进 unmapped 并说明原因（不静默丢弃）")
        void noAccountForCategoryReportsUnmapped() {
            BizCostAccount labor = account(1002L, "02", "劳务", "LABOR", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(labor));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            verify(costLedgerService, never()).post(any());
            assertThat(report.getUnmapped()).hasSize(1);
            assertThat(report.getUnmapped().get(0).getReason()).contains("无「MATERIAL」科目的成本账户");
        }

        @Test
        @DisplayName("绑定指向已删除/跨项目账户：显式暴露脏数据，不静默丢账")
        void bindingToUnknownAccountSurfaces() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            // 绑定指向 9999，但 9999 不在本项目账户集合中
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(
                    link(9999L, "PURCHASE_CONTRACT", "555")));
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            verify(costLedgerService, never()).post(any());
            assertThat(report.getUnmapped()).hasSize(1);
            assertThat(report.getUnmapped().get(0).getReason()).contains("9999");
        }
    }

    // ==================== 容错与报告 ====================

    @Nested
    @DisplayName("容错与报告完整性")
    class Resilience {

        @Test
        @DisplayName("项目无 CBS 账户：返回说明性报告而非抛错（新项目常态）")
        void noAccountsReturnsInformativeReport() {
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(new ArrayList<>());

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            assertThat(report.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(report.getMessage()).contains("尚未建立 CBS");
            assertThat(report.getPostedCount()).isZero();
            verify(costLedgerService, never()).post(any());
            // 无账户时不必再查源单据，避免无谓开销
            verify(rollUpMapper, never()).listPurchaseContracts(anyLong());
        }

        @Test
        @DisplayName("projectId 为空：拒绝执行")
        void rejectsNullProjectId() {
            assertThatThrownBy(() -> rollUpService.rollup(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID");
        }

        @Test
        @DisplayName("单账户记账失败（如已锁定）不阻断其余账户，但必须记入 failed 报告")
        void singleFailureDoesNotAbortOthers() {
            BizCostAccount locked = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            BizCostAccount active = account(1002L, "02", "劳务", "LABOR", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(locked, active));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));
            when(rollUpMapper.listLaborContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(666L, "LC-001", "70000")));
            // 材料账户已关闭 → 记账抛错；劳务账户正常
            when(costLedgerService.post(any()))
                    .thenThrow(new BusinessException("成本账户[01]已关闭（项目结案），禁止已承诺变动"))
                    .thenReturn(CostLedgerService.PostResult.POSTED);

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            assertThat(report.getFailed()).hasSize(1);
            assertThat(report.getFailed().get(0).getAccountCode()).isEqualTo("01");
            assertThat(report.getFailed().get(0).getReason()).contains("已关闭");
            assertThat(report.getPostedCount()).isEqualTo(1);
            assertThat(report.needsAttention()).isTrue();
        }

        @Test
        @DisplayName("幂等命中计入 duplicateCount，不算失败也不算成功记账")
        void duplicateCountedSeparately() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.DUPLICATE);

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            assertThat(report.getDuplicateCount()).isEqualTo(1);
            assertThat(report.getPostedCount()).isZero();
            assertThat(report.getFailed()).isEmpty();
        }

        @Test
        @DisplayName("金额字段为 null 的源单据按 0 处理，不抛 NPE")
        void nullAmountTolerated() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            CostRollUpMapper.SourceDoc nullAmount = new CostRollUpMapper.SourceDoc();
            nullAmount.setSourceId(555L);
            nullAmount.setSourceNumber("PO-001");
            nullAmount.setAmount(null);
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID)).thenReturn(List.of(nullAmount));

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            // 目标值 0 == 当前值 0 → 不记账，也不报错
            verify(costLedgerService, never()).post(any());
            assertThat(report.getUnmapped()).isEmpty();
        }

        @Test
        @DisplayName("报告消息汇总三类计数，便于前端直接展示")
        void reportMessageSummarizes() {
            BizCostAccount mat = account(1001L, "01", "材料", "MATERIAL", "0", "0");
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(mat));
            when(linkMapper.selectByProject(PROJECT_ID)).thenReturn(List.of());
            when(rollUpMapper.listPurchaseContracts(PROJECT_ID))
                    .thenReturn(List.of(doc(555L, "PO-001", "100000")));
            when(costLedgerService.post(any())).thenReturn(CostLedgerService.PostResult.POSTED);

            CostRollUpService.RollupReport report = rollUpService.rollup(PROJECT_ID);

            assertThat(report.getMessage())
                    .contains("记账 1 笔")
                    .contains("幂等跳过 0 笔")
                    .contains("待绑定单据 0 张");
            assertThat(report.getSourceDocCount()).isEqualTo(1);
        }
    }
}
