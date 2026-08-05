package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RetentionMoneyService 单元测试
 * <p>质保金：到期日期自动计算（startDate + retentionPeriod 月）、缺省初始化、到期查询。</p>
 */
@ExtendWith(MockitoExtension.class)
class RetentionMoneyServiceTest {

    @Mock
    private BizRetentionMoneyMapper retentionMoneyMapper;

    @InjectMocks
    private RetentionMoneyService service;

    private BizRetentionMoney money(Long id) {
        BizRetentionMoney m = new BizRetentionMoney();
        m.setId(id);
        m.setProjectId(1L);
        m.setRetentionAmount(new BigDecimal("50000"));
        return m;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizRetentionMoney> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(money(1L)));
        page.setTotal(1L);
        when(retentionMoneyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizRetentionMoney> result = service.page(1, 10, 1L, 2L);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 自动计算到期日期并初始化缺省字段")
    void save_computesExpireDateAndDefaults() {
        BizRetentionMoney m = money(null);
        m.setStartDate(LocalDate.of(2026, 1, 15));
        m.setRetentionPeriod(24);

        service.save(m);

        assertThat(m.getExpireDate()).isEqualTo(LocalDate.of(2028, 1, 15));
        assertThat(m.getReturnedAmount()).isEqualByComparingTo("0");
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
        verify(retentionMoneyMapper).insert(m);
    }

    @Test
    @DisplayName("save - 无开始日期不算到期日，已有状态不覆盖")
    void save_noStartDate_keepsExistingStatus() {
        BizRetentionMoney m = money(null);
        m.setStartDate(null);
        m.setRetentionPeriod(12);
        m.setStatus("RETURNED");
        m.setReturnedAmount(new BigDecimal("5"));

        service.save(m);

        assertThat(m.getExpireDate()).isNull();
        assertThat(m.getStatus()).isEqualTo("RETURNED");
        assertThat(m.getReturnedAmount()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("getExpiring - 查询窗口内 ACTIVE 质保金")
    void getExpiring_delegates() {
        BizRetentionMoney m = money(1L);
        when(retentionMoneyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(m));

        assertThat(service.getExpiring(30)).hasSize(1);
    }
}
