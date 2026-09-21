package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.domain.BizBankBalance;
import com.zwinsight.finance.domain.BizBankFlow;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import com.zwinsight.finance.mapper.BizBankBalanceMapper;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BankFlowService 单元测试（55_V2026_53 银行流水与余额登记）
 * <p>核心断言：余额 upsert、流水导入按流水号去重不静默覆盖、勾稽方向一致性。</p>
 */
@ExtendWith(MockitoExtension.class)
class BankFlowServiceTest {

    @Mock private BizBankFlowMapper flowMapper;
    @Mock private BizBankBalanceMapper balanceMapper;
    @Mock private BizBankAccountMapper accountMapper;

    @InjectMocks
    private BankFlowService bankFlowService;

    private BizBankAccount sampleAccount() {
        BizBankAccount account = new BizBankAccount();
        account.setId(1L);
        account.setAccountName("基本户");
        account.setAccountType("BASIC");
        account.setStatus(1);
        return account;
    }

    @Nested
    @DisplayName("recordBalance() 余额登记 upsert")
    class RecordBalanceTests {

        @Test
        @DisplayName("正常路径 — 首次登记执行 insert")
        void recordBalance_newInsert() {
            when(accountMapper.selectById(1L)).thenReturn(sampleAccount());
            when(balanceMapper.selectOne(any())).thenReturn(null);

            bankFlowService.recordBalance(1L, LocalDate.of(2026, 9, 20), new BigDecimal("500000"), "月末");

            ArgumentCaptor<BizBankBalance> captor = ArgumentCaptor.forClass(BizBankBalance.class);
            verify(balanceMapper).insert(captor.capture());
            assertThat(captor.getValue().getBalance()).isEqualByComparingTo("500000");
            assertThat(captor.getValue().getSource()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("正常路径 — 同日重复登记执行 update 覆盖余额")
        void recordBalance_existingUpdates() {
            BizBankAccount account = sampleAccount();
            BizBankBalance existing = new BizBankBalance();
            existing.setId(10L);
            existing.setBalance(new BigDecimal("100"));
            when(accountMapper.selectById(1L)).thenReturn(account);
            when(balanceMapper.selectOne(any())).thenReturn(existing);

            bankFlowService.recordBalance(1L, LocalDate.of(2026, 9, 20), new BigDecimal("500000"), null);

            assertThat(existing.getBalance()).isEqualByComparingTo("500000");
            verify(balanceMapper).updateById(existing);
            verify(balanceMapper, never()).insert(any());
        }

        @Test
        @DisplayName("异常路径 — 账户不存在拒绝登记")
        void recordBalance_accountNotFound_rejected() {
            when(accountMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> bankFlowService.recordBalance(
                    1L, LocalDate.now(), BigDecimal.TEN, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("银行账户不存在");
        }
    }

    @Nested
    @DisplayName("importFlows() 流水导入去重")
    class ImportTests {

        private BizBankFlow inFlow(String txnNo) {
            BizBankFlow flow = new BizBankFlow();
            flow.setAccountId(1L);
            flow.setFlowDate(LocalDate.of(2026, 9, 20));
            flow.setDirection(BizBankFlow.DIRECTION_IN);
            flow.setAmount(new BigDecimal("10000"));
            flow.setTransactionNo(txnNo);
            return flow;
        }

        @Test
        @DisplayName("正常路径 — 全新流水全部入库（imported=2, skipped=0）")
        void importFlows_allNew() {
            when(accountMapper.selectById(1L)).thenReturn(sampleAccount());
            when(flowMapper.selectCount(any())).thenReturn(0L);

            Map<String, Object> result = bankFlowService.importFlows(
                    List.of(inFlow("TXN-A"), inFlow("TXN-B")));

            assertThat(result.get("inserted")).isEqualTo(2);
            assertThat(result.get("skipped")).isEqualTo(0);
            verify(flowMapper, times(2)).insert(any());
        }

        @Test
        @DisplayName("正常路径 — 已存在流水号被跳过（不静默覆盖已有勾稽）")
        void importFlows_skipDuplicate() {
            when(accountMapper.selectById(1L)).thenReturn(sampleAccount());
            // 第一条重复，第二条新
            when(flowMapper.selectCount(any())).thenReturn(1L, 0L);

            Map<String, Object> result = bankFlowService.importFlows(
                    List.of(inFlow("TXN-DUP"), inFlow("TXN-NEW")));

            assertThat(result.get("inserted")).isEqualTo(1);
            assertThat(result.get("skipped")).isEqualTo(1);
            verify(flowMapper, times(1)).insert(any());
        }

        @Test
        @DisplayName("异常路径 — 空导入列表被拒绝")
        void importFlows_empty_rejected() {
            assertThatThrownBy(() -> bankFlowService.importFlows(List.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("导入数据为空");
        }
    }

    @Nested
    @DisplayName("matchFlow() 勾稽方向一致性")
    class MatchTests {

        private BizBankFlow flow(String direction) {
            BizBankFlow flow = new BizBankFlow();
            flow.setId(1L);
            flow.setDirection(direction);
            flow.setReconciled(0);
            return flow;
        }

        @Test
        @DisplayName("正常路径 — 支出流水匹配付款申请成功")
        void matchFlow_outToPaymentApply_ok() {
            BizBankFlow f = flow(BizBankFlow.DIRECTION_OUT);
            when(flowMapper.selectById(1L)).thenReturn(f);

            bankFlowService.matchFlow(1L, BizBankFlow.MATCH_PAYMENT_APPLY, 100L);

            assertThat(f.getReconciled()).isEqualTo(1);
            assertThat(f.getMatchedType()).isEqualTo(BizBankFlow.MATCH_PAYMENT_APPLY);
        }

        @Test
        @DisplayName("异常路径 — 收入流水错配付款申请被拦截")
        void matchFlow_inToPaymentApply_rejected() {
            when(flowMapper.selectById(1L)).thenReturn(flow(BizBankFlow.DIRECTION_IN));

            assertThatThrownBy(() -> bankFlowService.matchFlow(
                    1L, BizBankFlow.MATCH_PAYMENT_APPLY, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("收入流水只能匹配回款登记");
        }

        @Test
        @DisplayName("异常路径 — 已勾稽流水不可重复匹配")
        void matchFlow_alreadyReconciled_rejected() {
            BizBankFlow f = flow(BizBankFlow.DIRECTION_OUT);
            f.setReconciled(1);
            when(flowMapper.selectById(1L)).thenReturn(f);

            assertThatThrownBy(() -> bankFlowService.matchFlow(
                    1L, BizBankFlow.MATCH_PAYMENT_APPLY, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已勾稽");
        }
    }
}
