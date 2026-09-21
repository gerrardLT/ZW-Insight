package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizBalanceReconciliation;
import com.zwinsight.finance.mapper.BizBalanceReconciliationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BalanceReconciliationService 单元测试（55_V2026_53 余额调节表）
 * <p>核心断言：调节后双向余额公式正确、是否调平判定、account+date 唯一 upsert。</p>
 */
@ExtendWith(MockitoExtension.class)
class BalanceReconciliationServiceTest {

    @Mock private BizBalanceReconciliationMapper reconciliationMapper;

    @InjectMocks
    private BalanceReconciliationService reconciliationService;

    /** 构造基础调节记录：银行对账单 100 万、企业账面 98 万 */
    private BizBalanceReconciliation baseRecord() {
        BizBalanceReconciliation record = new BizBalanceReconciliation();
        record.setAccountId(1L);
        record.setReconciliationDate(LocalDate.of(2026, 9, 20));
        record.setBankStatementBalance(new BigDecimal("1000000"));
        record.setBookBalance(new BigDecimal("980000"));
        return record;
    }

    @Nested
    @DisplayName("save() 调节后余额计算")
    class SaveTests {

        @Test
        @DisplayName("正常路径 — 调节后双向余额相等则调平（balanced=1）")
        void save_balanced() {
            BizBalanceReconciliation record = baseRecord();
            // 企业已收银行未收 3 万；银行付企业未付 1 万 → 账面调节后 = 98+3 = 101
            record.setEnterpriseDepositBankNot(new BigDecimal("30000"));
            // 银行收企业未收 1 万 → 银行调节后 = 100+1 = 101
            record.setBankDepositEnterpriseNot(new BigDecimal("10000"));
            when(reconciliationMapper.selectOne(any())).thenReturn(null);

            BizBalanceReconciliation saved = reconciliationService.save(record);

            assertThat(saved.getAdjustedBookBalance()).isEqualByComparingTo("1010000");
            assertThat(saved.getAdjustedBankBalance()).isEqualByComparingTo("1010000");
            assertThat(saved.getBalanced()).isEqualTo(1);
            verify(reconciliationMapper).insert(record);
        }

        @Test
        @DisplayName("正常路径 — 调节后余额不等则未调平（balanced=0）")
        void save_notBalanced() {
            BizBalanceReconciliation record = baseRecord();
            record.setEnterpriseDepositBankNot(new BigDecimal("30000")); // 账面调节后 101 万
            // 银行侧无未达账项 → 银行调节后 100 万，不等
            when(reconciliationMapper.selectOne(any())).thenReturn(null);

            BizBalanceReconciliation saved = reconciliationService.save(record);

            assertThat(saved.getAdjustedBookBalance()).isEqualByComparingTo("1010000");
            assertThat(saved.getAdjustedBankBalance()).isEqualByComparingTo("1000000");
            assertThat(saved.getBalanced()).isZero();
        }

        @Test
        @DisplayName("正常路径 — 空未达账项按 0 处理")
        void save_nullOutstandingItems_treatedAsZero() {
            BizBalanceReconciliation record = baseRecord();
            record.setBankStatementBalance(new BigDecimal("500000"));
            record.setBookBalance(new BigDecimal("500000"));
            // 四组未达账项全为 null
            when(reconciliationMapper.selectOne(any())).thenReturn(null);

            BizBalanceReconciliation saved = reconciliationService.save(record);

            assertThat(saved.getAdjustedBankBalance()).isEqualByComparingTo("500000");
            assertThat(saved.getAdjustedBookBalance()).isEqualByComparingTo("500000");
            assertThat(saved.getBalanced()).isEqualTo(1);
        }

        @Test
        @DisplayName("异常路径 — 缺银行对账单余额被拒绝")
        void save_missingBankBalance_rejected() {
            BizBalanceReconciliation record = baseRecord();
            record.setBankStatementBalance(null);

            assertThatThrownBy(() -> reconciliationService.save(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能为空");
            verify(reconciliationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("正常路径 — 同账户同日存在时执行更新而非插入（upsert）")
        void save_existing_updates() {
            BizBalanceReconciliation record = baseRecord();
            BizBalanceReconciliation existing = baseRecord();
            existing.setId(999L);
            when(reconciliationMapper.selectOne(any())).thenReturn(existing);

            BizBalanceReconciliation saved = reconciliationService.save(record);

            assertThat(saved.getId()).isEqualTo(999L);
            verify(reconciliationMapper).updateById(record);
            verify(reconciliationMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("detail() 实时重算调节后余额")
    class DetailTests {

        @Test
        @DisplayName("正常路径 — 读取原始未达账项后实时计算调节余额")
        void detail_recomputes() {
            BizBalanceReconciliation stored = baseRecord();
            stored.setId(1L);
            stored.setEnterprisePaymentBankNot(new BigDecimal("20000")); // 企业已付银行未付 2 万
            stored.setBankPaymentEnterpriseNot(new BigDecimal("40000")); // 银行付企业未付 4 万
            when(reconciliationMapper.selectById(1L)).thenReturn(stored);

            BizBalanceReconciliation detail = reconciliationService.detail(1L);

            // 账面调节后 = 98 - 2 = 96；银行调节后 = 100 - 4 = 96 → 调平
            assertThat(detail.getAdjustedBookBalance()).isEqualByComparingTo("960000");
            assertThat(detail.getAdjustedBankBalance()).isEqualByComparingTo("960000");
            assertThat(detail.getBalanced()).isEqualTo(1);
        }

        @Test
        @DisplayName("异常路径 — 调节表不存在返回 404")
        void detail_notFound_throws() {
            when(reconciliationMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> reconciliationService.detail(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
        }
    }
}
