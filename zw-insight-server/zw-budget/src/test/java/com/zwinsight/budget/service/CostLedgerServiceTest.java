package com.zwinsight.budget.service;

import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountTxn;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.budget.mapper.BizCostAccountTxnMapper;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CBS 成本流水服务单元测试。
 * <p>
 * 重点覆盖三类高风险行为：
 * </p>
 * <ol>
 *   <li><b>幂等</b>：同一业务事实重复记账必须只生效一次（Outbox 至少一次投递的直接后果）</li>
 *   <li><b>非负约束</b>：调整后余额为负必须拒绝，否则账上会出现负成本</li>
 *   <li><b>可写性门禁</b>：锁定/关闭账户拒绝预算口径变动，但实际成本仍可回写</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CostLedgerService 成本记账")
class CostLedgerServiceTest {

    @Mock
    private BizCostAccountMapper costAccountMapper;

    @Mock
    private BizCostAccountTxnMapper txnMapper;

    @InjectMocks
    private CostLedgerService ledgerService;

    private BizCostAccount account;

    @BeforeEach
    void setUp() {
        account = new BizCostAccount();
        account.setId(1001L);
        account.setProjectId(100L);
        account.setAccountCode("01.02");
        account.setAccountName("主体结构-混凝土");
        account.setCostCategory("MATERIAL");
        account.setStatus("ACTIVE");
        account.setBaselineAmount(new BigDecimal("1000000.00"));
        account.setCurrentAmount(new BigDecimal("1000000.00"));
        account.setCommitmentAmount(new BigDecimal("600000.00"));
        account.setActualAmount(new BigDecimal("400000.00"));
        account.setForecastAmount(new BigDecimal("1050000.00"));
    }

    private CostLedgerService.PostCommand cmd(BigDecimal delta) {
        return new CostLedgerService.PostCommand(
                1001L,
                CostLedgerService.AMT_CURRENT,
                delta,
                CostLedgerService.SRC_CHANGE_EVENT,
                "555:1001",
                "CHG20260001",
                LocalDateTime.now(),
                "变更事件批准传导");
    }

    @Nested
    @DisplayName("正常记账")
    class Posting {

        @Test
        @DisplayName("增加当前预算：调整余额并落流水")
        void postsIncrease() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);
            when(costAccountMapper.adjustCurrentAmount(eq(1001L), any(BigDecimal.class))).thenReturn(1);

            BizCostAccount after = new BizCostAccount();
            after.setCurrentAmount(new BigDecimal("1050000.00"));
            // 第二次 selectById 返回调整后的快照，用于写 balance_after
            when(costAccountMapper.selectById(1001L)).thenReturn(account, after);

            CostLedgerService.PostResult result = ledgerService.post(cmd(new BigDecimal("50000.00")));

            assertThat(result).isEqualTo(CostLedgerService.PostResult.POSTED);
            verify(costAccountMapper).adjustCurrentAmount(1001L, new BigDecimal("50000.00"));

            ArgumentCaptor<BizCostAccountTxn> captor = ArgumentCaptor.forClass(BizCostAccountTxn.class);
            verify(txnMapper).insert(captor.capture());
            BizCostAccountTxn txn = captor.getValue();
            assertThat(txn.getAccountId()).isEqualTo(1001L);
            assertThat(txn.getProjectId()).isEqualTo(100L);
            assertThat(txn.getAmountType()).isEqualTo(CostLedgerService.AMT_CURRENT);
            assertThat(txn.getDeltaAmount()).isEqualByComparingTo("50000.00");
            assertThat(txn.getBalanceAfter()).isEqualByComparingTo("1050000.00");
            assertThat(txn.getSourceType()).isEqualTo(CostLedgerService.SRC_CHANGE_EVENT);
            assertThat(txn.getSourceId()).isEqualTo("555:1001");
            assertThat(txn.getSourceNumber()).isEqualTo("CHG20260001");
        }

        @Test
        @DisplayName("减少实际成本（冲销）：负增量同样记账")
        void postsNegativeDelta() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);
            when(costAccountMapper.adjustActualAmount(eq(1001L), any(BigDecimal.class))).thenReturn(1);

            CostLedgerService.PostCommand actualCmd = new CostLedgerService.PostCommand(
                    1001L, CostLedgerService.AMT_ACTUAL, new BigDecimal("-20000.00"),
                    CostLedgerService.SRC_SETTLEMENT, "ST-9001", "ST-9001",
                    LocalDateTime.now(), "结算冲销");

            assertThat(ledgerService.post(actualCmd)).isEqualTo(CostLedgerService.PostResult.POSTED);
            verify(costAccountMapper).adjustActualAmount(1001L, new BigDecimal("-20000.00"));
        }

        @Test
        @DisplayName("零变动不落流水：避免台账被噪声淹没")
        void zeroDeltaSkipsLedger() {
            CostLedgerService.PostResult result = ledgerService.post(cmd(BigDecimal.ZERO));

            assertThat(result).isEqualTo(CostLedgerService.PostResult.POSTED);
            verify(txnMapper, never()).insert(any());
            verify(costAccountMapper, never()).adjustCurrentAmount(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("幂等保证")
    class Idempotency {

        @Test
        @DisplayName("同一业务事实重复记账：第二次返回 DUPLICATE 且不动余额")
        void duplicateIsNoOp() {
            when(txnMapper.countBySource(
                    CostLedgerService.SRC_CHANGE_EVENT, "555:1001", 1001L, CostLedgerService.AMT_CURRENT))
                    .thenReturn(1L);

            CostLedgerService.PostResult result = ledgerService.post(cmd(new BigDecimal("50000.00")));

            assertThat(result).isEqualTo(CostLedgerService.PostResult.DUPLICATE);
            verify(costAccountMapper, never()).adjustCurrentAmount(anyLong(), any());
            verify(txnMapper, never()).insert(any());
        }

        @Test
        @DisplayName("并发撞唯一键：转 DUPLICATE 而非把异常抛给调用方")
        void concurrentDuplicateBecomesNoOp() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);
            when(costAccountMapper.adjustCurrentAmount(anyLong(), any(BigDecimal.class))).thenReturn(1);
            when(txnMapper.insert(any(BizCostAccountTxn.class)))
                    .thenThrow(new DuplicateKeyException("uk_txn_source"));

            CostLedgerService.PostResult result = ledgerService.postPostCommandSafely(cmd(new BigDecimal("50000.00")));

            assertThat(result).isEqualTo(CostLedgerService.PostResult.DUPLICATE);
        }

        @Test
        @DisplayName("批量记账逐条独立幂等：部分命中不影响其余记账")
        void batchPostingIsPerCommandIdempotent() {
            BizCostAccount second = new BizCostAccount();
            second.setId(1002L);
            second.setProjectId(100L);
            second.setAccountCode("01.03");
            second.setStatus("ACTIVE");
            second.setCurrentAmount(new BigDecimal("200000.00"));

            when(costAccountMapper.selectById(1002L)).thenReturn(second);
            // 1001 已记过账（幂等探针先于账户查询命中，因此不会读 1001 账户），1002 未记
            when(txnMapper.countBySource(anyString(), anyString(), eq(1001L), anyString())).thenReturn(1L);
            when(txnMapper.countBySource(anyString(), anyString(), eq(1002L), anyString())).thenReturn(0L);
            when(costAccountMapper.adjustCurrentAmount(eq(1002L), any(BigDecimal.class))).thenReturn(1);

            List<CostLedgerService.PostCommand> commands = List.of(
                    cmd(new BigDecimal("50000.00")),
                    new CostLedgerService.PostCommand(1002L, CostLedgerService.AMT_CURRENT,
                            new BigDecimal("30000.00"), CostLedgerService.SRC_CHANGE_EVENT,
                            "555:1002", "CHG20260001", LocalDateTime.now(), "变更传导"));

            int posted = ledgerService.postBatch(commands);

            assertThat(posted).isEqualTo(1);
            verify(costAccountMapper, never()).adjustCurrentAmount(eq(1001L), any());
            verify(costAccountMapper).adjustCurrentAmount(eq(1002L), any(BigDecimal.class));
        }
    }

    @Nested
    @DisplayName("约束与门禁")
    class Guards {

        @Test
        @DisplayName("调整后余额为负：拒绝并给出可读原因")
        void rejectsNegativeBalance() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);
            // SQL 的 WHERE current_amount + delta >= 0 不命中 → 影响行数 0
            when(costAccountMapper.adjustCurrentAmount(anyLong(), any(BigDecimal.class))).thenReturn(0);

            assertThatThrownBy(() -> ledgerService.post(cmd(new BigDecimal("-99999999"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("负数")
                    .hasMessageContaining("01.02");

            verify(txnMapper, never()).insert(any());
        }

        @Test
        @DisplayName("已关闭账户：任何维度都拒绝变动")
        void closedAccountRejectsAll() {
            account.setStatus("CLOSED");
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);

            assertThatThrownBy(() -> ledgerService.post(cmd(new BigDecimal("1000"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已关闭");
        }

        @Test
        @DisplayName("锁定账户：拒绝预算口径变动")
        void lockedAccountRejectsBudgetChange() {
            account.setStatus("LOCKED");
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);

            assertThatThrownBy(() -> ledgerService.post(cmd(new BigDecimal("1000"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已锁定");
        }

        @Test
        @DisplayName("锁定账户：实际成本仍可回写（已发生支出必须入账，否则账实不符）")
        void lockedAccountStillAcceptsActualCost() {
            account.setStatus("LOCKED");
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);
            when(costAccountMapper.adjustActualAmount(anyLong(), any(BigDecimal.class))).thenReturn(1);

            CostLedgerService.PostCommand actualCmd = new CostLedgerService.PostCommand(
                    1001L, CostLedgerService.AMT_ACTUAL, new BigDecimal("8000.00"),
                    CostLedgerService.SRC_PAYMENT, "PAY-7001", "PAY-7001",
                    LocalDateTime.now(), "付款入账");

            assertThat(ledgerService.post(actualCmd)).isEqualTo(CostLedgerService.PostResult.POSTED);
            verify(costAccountMapper).adjustActualAmount(1001L, new BigDecimal("8000.00"));
        }

        @Test
        @DisplayName("BASELINE/FORECAST 不支持增量记账，必须走覆盖式设置")
        void baselineRejectsIncrementalPosting() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);

            CostLedgerService.PostCommand baselineCmd = new CostLedgerService.PostCommand(
                    1001L, CostLedgerService.AMT_BASELINE, new BigDecimal("1000"),
                    CostLedgerService.SRC_MANUAL, "M-1", null, LocalDateTime.now(), null);

            assertThatThrownBy(() -> ledgerService.post(baselineCmd))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不支持增量记账");
        }

        @Test
        @DisplayName("账户不存在：拒绝记账")
        void rejectsMissingAccount() {
            when(costAccountMapper.selectById(9999L)).thenReturn(null);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);

            CostLedgerService.PostCommand missing = new CostLedgerService.PostCommand(
                    9999L, CostLedgerService.AMT_CURRENT, BigDecimal.TEN,
                    CostLedgerService.SRC_CHANGE_EVENT, "1:9999", null, LocalDateTime.now(), null);

            assertThatThrownBy(() -> ledgerService.post(missing))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("缺 accountId / amountType / sourceType / sourceId 一律拒绝")
        void rejectsMissingMandatoryFields() {
            assertThatThrownBy(() -> ledgerService.post(new CostLedgerService.PostCommand(
                    null, CostLedgerService.AMT_CURRENT, BigDecimal.TEN,
                    CostLedgerService.SRC_MANUAL, "1", null, null, null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("账户ID");

            assertThatThrownBy(() -> ledgerService.post(new CostLedgerService.PostCommand(
                    1001L, "", BigDecimal.TEN,
                    CostLedgerService.SRC_MANUAL, "1", null, null, null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("金额维度");

            assertThatThrownBy(() -> ledgerService.post(new CostLedgerService.PostCommand(
                    1001L, CostLedgerService.AMT_CURRENT, BigDecimal.TEN,
                    "", "1", null, null, null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("来源类型");

            assertThatThrownBy(() -> ledgerService.post(new CostLedgerService.PostCommand(
                    1001L, CostLedgerService.AMT_CURRENT, BigDecimal.TEN,
                    CostLedgerService.SRC_MANUAL, " ", null, null, null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("来源业务ID");
        }

        @Test
        @DisplayName("非法金额维度拒绝")
        void rejectsUnknownAmountType() {
            assertThatThrownBy(() -> ledgerService.post(new CostLedgerService.PostCommand(
                    1001L, "PROFIT", BigDecimal.TEN,
                    CostLedgerService.SRC_MANUAL, "1", null, null, null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("非法金额维度");
        }

        @Test
        @DisplayName("deltaAmount 为 null 拒绝（无变动应跳过而非传 null）")
        void rejectsNullDelta() {
            assertThatThrownBy(() -> ledgerService.post(new CostLedgerService.PostCommand(
                    1001L, CostLedgerService.AMT_CURRENT, null,
                    CostLedgerService.SRC_MANUAL, "1", null, null, null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("变动金额");
        }
    }

    @Nested
    @DisplayName("覆盖式设置（BASELINE / FORECAST）")
    class SetAmount {

        @Test
        @DisplayName("设置完工预测：记录 delta 与新余额")
        void setsForecast() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account);
            when(txnMapper.countBySource(anyString(), anyString(), anyLong(), anyString())).thenReturn(0L);

            CostLedgerService.PostResult result = ledgerService.setAmount(
                    1001L, CostLedgerService.AMT_FORECAST, new BigDecimal("1200000.00"),
                    CostLedgerService.SRC_MANUAL, "FC-2026-09", "9月预测重算");

            assertThat(result).isEqualTo(CostLedgerService.PostResult.POSTED);

            ArgumentCaptor<BizCostAccount> patchCaptor = ArgumentCaptor.forClass(BizCostAccount.class);
            verify(costAccountMapper).updateById(patchCaptor.capture());
            assertThat(patchCaptor.getValue().getForecastAmount()).isEqualByComparingTo("1200000.00");

            ArgumentCaptor<BizCostAccountTxn> txnCaptor = ArgumentCaptor.forClass(BizCostAccountTxn.class);
            verify(txnMapper).insert(txnCaptor.capture());
            // 原预测 1050000 → 新预测 1200000，delta = +150000
            assertThat(txnCaptor.getValue().getDeltaAmount()).isEqualByComparingTo("150000.00");
            assertThat(txnCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("1200000.00");
        }

        @Test
        @DisplayName("CURRENT 不允许覆盖式设置（必须走变更事件传导，保证有审批依据）")
        void rejectsOverwriteOnCurrent() {
            assertThatThrownBy(() -> ledgerService.setAmount(
                    1001L, CostLedgerService.AMT_CURRENT, new BigDecimal("999"),
                    CostLedgerService.SRC_MANUAL, "X", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅支持 BASELINE / FORECAST");
        }

        @Test
        @DisplayName("负值拒绝")
        void rejectsNegativeValue() {
            assertThatThrownBy(() -> ledgerService.setAmount(
                    1001L, CostLedgerService.AMT_FORECAST, new BigDecimal("-1"),
                    CostLedgerService.SRC_MANUAL, "X", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不得为负");
        }

        @Test
        @DisplayName("幂等：同一来源重复设置只生效一次")
        void setAmountIsIdempotent() {
            when(txnMapper.countBySource(CostLedgerService.SRC_MANUAL, "FC-1", 1001L,
                    CostLedgerService.AMT_FORECAST)).thenReturn(1L);

            assertThat(ledgerService.setAmount(1001L, CostLedgerService.AMT_FORECAST,
                    new BigDecimal("1200000"), CostLedgerService.SRC_MANUAL, "FC-1", null))
                    .isEqualTo(CostLedgerService.PostResult.DUPLICATE);
            verify(costAccountMapper, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("台账查询")
    class Queries {

        @Test
        @DisplayName("按账户查流水透传过滤条件")
        void listByAccountPassesFilters() {
            LocalDateTime from = LocalDateTime.now().minusDays(30);
            LocalDateTime to = LocalDateTime.now();
            when(txnMapper.listByAccount(1001L, CostLedgerService.AMT_CURRENT, from, to))
                    .thenReturn(List.of(new BizCostAccountTxn()));

            List<BizCostAccountTxn> result =
                    ledgerService.listByAccount(1001L, CostLedgerService.AMT_CURRENT, from, to);

            assertThat(result).hasSize(1);
            verify(txnMapper, times(1)).listByAccount(1001L, CostLedgerService.AMT_CURRENT, from, to);
        }

        @Test
        @DisplayName("缺 accountId 拒绝查询（避免全表扫描）")
        void listByAccountRequiresId() {
            assertThatThrownBy(() -> ledgerService.listByAccount(null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账户ID");
        }

        @Test
        @DisplayName("按来源汇总：null 归零")
        void sumBySourceNullSafe() {
            when(txnMapper.sumDeltaBySource(100L, CostLedgerService.SRC_CHANGE_EVENT,
                    CostLedgerService.AMT_CURRENT)).thenReturn(null);

            assertThat(ledgerService.sumBySource(100L,
                    CostLedgerService.SRC_CHANGE_EVENT, CostLedgerService.AMT_CURRENT))
                    .isEqualByComparingTo("0");
        }
    }
}
