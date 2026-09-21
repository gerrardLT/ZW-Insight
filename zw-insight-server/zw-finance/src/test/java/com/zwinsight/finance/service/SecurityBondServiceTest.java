package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizSecurityBond;
import com.zwinsight.finance.mapper.BizSecurityBondMapper;
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
 * SecurityBondService 单元测试（53_V2026_51 四类保证金台账）
 * <p>核心断言：四类比例上限（投标2%/履约10%/质量3%/工资3%）为政策硬约束。</p>
 */
@ExtendWith(MockitoExtension.class)
class SecurityBondServiceTest {

    @Mock private BizSecurityBondMapper bondMapper;

    @InjectMocks
    private SecurityBondService securityBondService;

    private BizSecurityBond sampleBond(String type, String amount, String contractAmount) {
        BizSecurityBond bond = new BizSecurityBond();
        bond.setProjectId(1L);
        bond.setBondType(type);
        bond.setAmount(new BigDecimal(amount));
        bond.setContractAmount(new BigDecimal(contractAmount));
        bond.setDepositDate(LocalDate.of(2026, 9, 1));
        bond.setBondForm("CASH");
        return bond;
    }

    @Nested
    @DisplayName("save() 缴存登记（比例上限校验）")
    class SaveTests {

        @Test
        @DisplayName("正常路径 — 履约保证金 合同额100万 缴9万（9%≤10%）通过")
        void save_performanceWithinLimit_pass() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_PERFORMANCE, "90000", "1000000");

            securityBondService.save(bond);

            assertThat(bond.getRefundStatus()).isEqualTo(BizSecurityBond.STATUS_DEPOSITED);
            verify(bondMapper).insert(bond);
        }

        @Test
        @DisplayName("异常路径 — 履约保证金 合同额100万 缴11万（11%>10%）被政策上限拦截")
        void save_performanceOverLimit_rejected() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_PERFORMANCE, "110000", "1000000");

            assertThatThrownBy(() -> securityBondService.save(bond))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超出比例上限");
        }

        @Test
        @DisplayName("异常路径 — 投标保证金 估算价100万 缴3万（3%>2%）被拦截")
        void save_tenderOverLimit_rejected() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_TENDER, "30000", "1000000");

            assertThatThrownBy(() -> securityBondService.save(bond))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超出比例上限");
        }

        @Test
        @DisplayName("异常路径 — 质量保证金 结算100万 缴4万（4%>3%）被拦截")
        void save_qualityOverLimit_rejected() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_QUALITY, "40000", "1000000");

            assertThatThrownBy(() -> securityBondService.save(bond))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超出比例上限");
        }

        @Test
        @DisplayName("异常路径 — 保函形式未上传担保文件被拦截（防静默）")
        void save_guaranteeWithoutFile_rejected() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_PERFORMANCE, "90000", "1000000");
            bond.setBondForm("BANK_GUARANTEE");
            bond.setGuaranteeFile(null);

            assertThatThrownBy(() -> securityBondService.save(bond))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须上传担保文件");
        }
    }

    @Nested
    @DisplayName("refundConfirm() 退还确认（状态机）")
    class RefundTests {

        @Test
        @DisplayName("正常路径 — REFUND_APPLY 状态确认退还后置 REFUNDED 并记录金额日期")
        void refundConfirm_normalPath() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_TENDER, "20000", "1000000");
            bond.setId(1L);
            bond.setRefundStatus(BizSecurityBond.STATUS_REFUND_APPLY);
            when(bondMapper.selectById(1L)).thenReturn(bond);

            securityBondService.refundConfirm(1L, new BigDecimal("20000"), LocalDate.of(2026, 10, 1));

            assertThat(bond.getRefundStatus()).isEqualTo(BizSecurityBond.STATUS_REFUNDED);
            assertThat(bond.getRefundAmount()).isEqualByComparingTo("20000");
            assertThat(bond.getRefundActualDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            verify(bondMapper).updateById(bond);
        }

        @Test
        @DisplayName("异常路径 — DEPOSITED 状态（未走申请）直接确认被拦截")
        void refundConfirm_wrongStatus_rejected() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_TENDER, "20000", "1000000");
            bond.setId(1L);
            bond.setRefundStatus(BizSecurityBond.STATUS_DEPOSITED);
            when(bondMapper.selectById(1L)).thenReturn(bond);

            assertThatThrownBy(() -> securityBondService.refundConfirm(1L, new BigDecimal("20000"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅[退还申请中]状态可确认退还");
        }

        @Test
        @DisplayName("异常路径 — 退还金额超过缴存金额被拦截")
        void refundConfirm_overAmount_rejected() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_TENDER, "20000", "1000000");
            bond.setId(1L);
            bond.setRefundStatus(BizSecurityBond.STATUS_REFUND_APPLY);
            when(bondMapper.selectById(1L)).thenReturn(bond);

            assertThatThrownBy(() -> securityBondService.refundConfirm(1L, new BigDecimal("30000"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不得超过缴存金额");
        }
    }

    @Nested
    @DisplayName("markUsed() 动用标记（工资类专用）")
    class MarkUsedTests {

        @Test
        @DisplayName("异常路径 — 非工资类型保证金不支持动用标记")
        void markUsed_nonWage_rejected() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_TENDER, "20000", "1000000");
            bond.setId(1L);
            when(bondMapper.selectById(1L)).thenReturn(bond);

            assertThatThrownBy(() -> securityBondService.markUsed(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅农民工工资保证金支持动用标记");
        }

        @Test
        @DisplayName("正常路径 — 工资类 DEPOSITED 状态标记动用后置 USED 并提示10个工作日补足")
        void markUsed_wageNormalPath() {
            BizSecurityBond bond = sampleBond(BizSecurityBond.TYPE_WAGE, "20000", "1000000");
            bond.setId(1L);
            bond.setRefundStatus(BizSecurityBond.STATUS_DEPOSITED);
            when(bondMapper.selectById(1L)).thenReturn(bond);

            securityBondService.markUsed(1L);

            assertThat(bond.getRefundStatus()).isEqualTo(BizSecurityBond.STATUS_USED);
            assertThat(bond.getRemark()).contains("10个工作日内补足");
            verify(bondMapper).updateById(bond);
        }
    }

    @Nested
    @DisplayName("statistics() 占用统计")
    class StatisticsTests {

        @Test
        @DisplayName("正常路径 — 现金与保函分别汇总并计算替代率")
        void statistics_normalPath() {
            BizSecurityBond cash = sampleBond(BizSecurityBond.TYPE_TENDER, "10000", "1000000");
            cash.setBondForm("CASH");
            BizSecurityBond guarantee = sampleBond(BizSecurityBond.TYPE_PERFORMANCE, "30000", "1000000");
            guarantee.setBondForm("BANK_GUARANTEE");
            when(bondMapper.selectList(any())).thenReturn(java.util.List.of(cash, guarantee));

            java.util.Map<String, Object> stats = securityBondService.statistics(null);

            assertThat((BigDecimal) stats.get("total")).isEqualByComparingTo("40000");
            assertThat((BigDecimal) stats.get("guaranteeRatio")).isEqualByComparingTo(new BigDecimal("0.7500"));
        }
    }
}
