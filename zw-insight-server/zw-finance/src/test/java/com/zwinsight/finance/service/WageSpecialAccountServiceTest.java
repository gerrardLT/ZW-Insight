package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizWageDeposit;
import com.zwinsight.finance.domain.BizWageSpecialAccount;
import com.zwinsight.finance.mapper.BizWageDepositMapper;
import com.zwinsight.finance.mapper.BizWageSpecialAccountMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WageSpecialAccountService 单元测试（53_V2026_51 农民工工资专户）
 * <p>核心断言：拨付与余额对称更新、代发不超专户余额、项目唯一在用专户。</p>
 */
@ExtendWith(MockitoExtension.class)
class WageSpecialAccountServiceTest {

    @Mock private BizWageSpecialAccountMapper accountMapper;
    @Mock private BizWageDepositMapper depositMapper;

    @InjectMocks
    private WageSpecialAccountService wageSpecialAccountService;

    private BizWageSpecialAccount activeAccount() {
        BizWageSpecialAccount account = new BizWageSpecialAccount();
        account.setId(1L);
        account.setProjectId(10L);
        account.setAccountNo("6222-0000");
        account.setBankName("建设银行");
        account.setStatus(BizWageSpecialAccount.STATUS_ACTIVE);
        account.setWageBudget(new BigDecimal("1000000"));
        account.setTotalReceived(new BigDecimal("500000"));
        account.setTotalPaid(BigDecimal.ZERO);
        account.setCurrentBalance(new BigDecimal("500000"));
        account.setLastDepositDate(LocalDate.now());
        return account;
    }

    @Nested
    @DisplayName("save() 开户")
    class SaveTests {

        @Test
        @DisplayName("正常路径 — 新开专户初始化为 ACTIVE 且余额为零")
        void save_normalPath() {
            BizWageSpecialAccount account = new BizWageSpecialAccount();
            account.setProjectId(10L);
            account.setAccountNo("6222-0001");
            account.setBankName("工商银行");
            when(accountMapper.selectCount(any())).thenReturn(0L);

            wageSpecialAccountService.save(account);

            assertThat(account.getStatus()).isEqualTo(BizWageSpecialAccount.STATUS_ACTIVE);
            assertThat(account.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(account.getComplianceFlag()).isEqualTo(BizWageSpecialAccount.COMPLIANCE_COMPLIANT);
            verify(accountMapper).insert(account);
        }

        @Test
        @DisplayName("异常路径 — 同项目已存在在用专户时拒绝重复开设")
        void save_duplicateRejected() {
            BizWageSpecialAccount account = new BizWageSpecialAccount();
            account.setProjectId(10L);
            account.setAccountNo("6222-0002");
            account.setBankName("工商银行");
            when(accountMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> wageSpecialAccountService.save(account))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可重复开设");
        }
    }

    @Nested
    @DisplayName("recordDeposit() 拨付到账（对称更新）")
    class DepositTests {

        @Test
        @DisplayName("正常路径 — 拨付10万后累计到账与余额同步增加并记录到账日期")
        void recordDeposit_normalPath() {
            BizWageSpecialAccount account = activeAccount();
            when(accountMapper.selectById(1L)).thenReturn(account);
            BizWageDeposit deposit = new BizWageDeposit();
            deposit.setAccountId(1L);
            deposit.setAmount(new BigDecimal("100000"));
            deposit.setDepositDate(LocalDate.of(2026, 9, 18));

            wageSpecialAccountService.recordDeposit(deposit);

            assertThat(account.getTotalReceived()).isEqualByComparingTo("600000");
            assertThat(account.getCurrentBalance()).isEqualByComparingTo("600000");
            assertThat(account.getLastDepositDate()).isEqualTo(LocalDate.of(2026, 9, 18));
            assertThat(deposit.getProjectId()).isEqualTo(10L);
            verify(depositMapper).insert(deposit);
            verify(accountMapper).updateById(account);
        }

        @Test
        @DisplayName("异常路径 — 非正数拨付金额被拦截")
        void recordDeposit_negativeAmount_rejected() {
            when(accountMapper.selectById(1L)).thenReturn(activeAccount());
            BizWageDeposit deposit = new BizWageDeposit();
            deposit.setAccountId(1L);
            deposit.setAmount(BigDecimal.ZERO);
            deposit.setDepositDate(LocalDate.now());

            assertThatThrownBy(() -> wageSpecialAccountService.recordDeposit(deposit))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须大于0");
        }
    }

    @Nested
    @DisplayName("recordWagePayment() 总包代发")
    class WagePaymentTests {

        @Test
        @DisplayName("正常路径 — 代发20万后余额扣减、累计代发增加")
        void recordWagePayment_normalPath() {
            BizWageSpecialAccount account = activeAccount();
            when(accountMapper.selectById(1L)).thenReturn(account);

            wageSpecialAccountService.recordWagePayment(1L, new BigDecimal("200000"), LocalDate.now());

            assertThat(account.getTotalPaid()).isEqualByComparingTo("200000");
            assertThat(account.getCurrentBalance()).isEqualByComparingTo("300000");
            verify(accountMapper).updateById(account);
        }

        @Test
        @DisplayName("异常路径 — 代发金额超过专户余额被拦截（专款专用）")
        void recordWagePayment_overBalance_rejected() {
            BizWageSpecialAccount account = activeAccount();
            when(accountMapper.selectById(1L)).thenReturn(account);

            assertThatThrownBy(() -> wageSpecialAccountService
                    .recordWagePayment(1L, new BigDecimal("600000"), LocalDate.now()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超过专户当前余额");
        }
    }

    @Nested
    @DisplayName("arrivalRate() 到位率")
    class ArrivalRateTests {

        @Test
        @DisplayName("正常路径 — 预算100万到账50万到位率0.5")
        void arrivalRate_normalPath() {
            when(accountMapper.selectById(1L)).thenReturn(activeAccount());

            BigDecimal rate = wageSpecialAccountService.arrivalRate(1L);

            assertThat(rate).isEqualByComparingTo(new BigDecimal("0.5000"));
        }

        @Test
        @DisplayName("异常路径 — 专户不存在时抛业务异常")
        void arrivalRate_notFound_throws() {
            when(accountMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> wageSpecialAccountService.arrivalRate(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
        }
    }
}
