package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.SysAmountTierConfig;
import com.zwinsight.finance.mapper.SysAmountTierConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AmountTierService 单元测试（54_V2026_52 金额分级审批）
 * <p>核心断言：区间匹配 [min, max) 左闭右开、50 万红线命中财务负责人档、无匹配返回 null。</p>
 */
@ExtendWith(MockitoExtension.class)
class AmountTierServiceTest {

    @Mock private SysAmountTierConfigMapper tierConfigMapper;

    @InjectMocks
    private AmountTierService amountTierService;

    /** 种子档位（与 54_V2026_52 一致）：0-10万/10-50万/50-500万/500万+ */
    private List<SysAmountTierConfig> seedTiers() {
        return List.of(
                tier(1, "普通审批", "0", "100000"),
                tier(2, "部门负责人审批", "100000", "500000"),
                tier(3, "财务负责人审批", "500000", "5000000"),
                tier(4, "老板审批", "5000000", null));
    }

    private SysAmountTierConfig tier(int level, String name, String min, String max) {
        SysAmountTierConfig config = new SysAmountTierConfig();
        config.setModule(SysAmountTierConfig.MODULE_PAYMENT_APPLY);
        config.setTierLevel(level);
        config.setTierName(name);
        config.setMinAmount(new BigDecimal(min));
        config.setMaxAmount(max == null ? null : new BigDecimal(max));
        config.setEnabled(1);
        return config;
    }

    @Nested
    @DisplayName("matchTier() 档位匹配")
    class MatchTierTests {

        @Test
        @DisplayName("正常路径 — 8万命中普通审批（tier 1）")
        void matchTier_normalAmount() {
            when(tierConfigMapper.selectList(any())).thenReturn(seedTiers());

            SysAmountTierConfig matched = amountTierService.matchTier(
                    SysAmountTierConfig.MODULE_PAYMENT_APPLY, new BigDecimal("80000"));

            assertThat(matched.getTierLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常路径 — 50万整命中财务负责人（左闭右开：50万∉[10万,50万)）")
        void matchTier_boundary500k() {
            when(tierConfigMapper.selectList(any())).thenReturn(seedTiers());

            SysAmountTierConfig matched = amountTierService.matchTier(
                    SysAmountTierConfig.MODULE_PAYMENT_APPLY, new BigDecimal("500000"));

            assertThat(matched.getTierLevel()).isEqualTo(3);
            assertThat(matched.getTierName()).isEqualTo("财务负责人审批");
        }

        @Test
        @DisplayName("正常路径 — 600万命中老板审批（无上限档）")
        void matchTier_aboveAllTiers() {
            when(tierConfigMapper.selectList(any())).thenReturn(seedTiers());

            SysAmountTierConfig matched = amountTierService.matchTier(
                    SysAmountTierConfig.MODULE_PAYMENT_APPLY, new BigDecimal("6000000"));

            assertThat(matched.getTierLevel()).isEqualTo(4);
        }

        @Test
        @DisplayName("正常路径 — 模块无启用档位时返回 null（走默认审批链，不静默造默认档）")
        void matchTier_noTiers_returnsNull() {
            when(tierConfigMapper.selectList(any())).thenReturn(List.of());

            SysAmountTierConfig matched = amountTierService.matchTier(
                    SysAmountTierConfig.MODULE_PAYMENT_APPLY, new BigDecimal("500000"));

            assertThat(matched).isNull();
        }

        @Test
        @DisplayName("正常路径 — 停用档位不参与匹配（全部停用→null）")
        void matchTier_disabledTiers_skipped() {
            SysAmountTierConfig disabled = tier(3, "财务负责人审批", "500000", "5000000");
            disabled.setEnabled(0);
            when(tierConfigMapper.selectList(any())).thenReturn(List.of(disabled));

            SysAmountTierConfig matched = amountTierService.matchTier(
                    SysAmountTierConfig.MODULE_PAYMENT_APPLY, new BigDecimal("600000"));

            assertThat(matched).isNull();
        }
    }

    @Nested
    @DisplayName("save() 新增档位")
    class SaveTests {

        @Test
        @DisplayName("异常路径 — 模块内等级重复被拦截")
        void save_duplicateLevel_rejected() {
            when(tierConfigMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> amountTierService.save(tier(1, "普通审批", "0", "100000")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在等级 1 的档位");
        }

        @Test
        @DisplayName("异常路径 — 上限不大于下限被拦截")
        void save_invalidRange_rejected() {
            assertThatThrownBy(() -> amountTierService.save(tier(5, "新档", "100000", "100000")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("上限必须大于下限");
        }
    }
}
