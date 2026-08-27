package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RetentionMoneyService 单元测试
 * <p>质保金：到期日期自动计算（startDate + retentionPeriod 月）、缺省初始化、到期查询。</p>
 */
@ExtendWith(MockitoExtension.class)
class RetentionMoneyServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizRetentionMoney.class);
    }

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

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("getExpiring - 窗口边界：今天≤到期日≤今天+N 双闭区间 + ACTIVE 过滤（P1 FIN-RTN-05）")
    void getExpiring_windowBoundaries() {
        when(retentionMoneyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        service.getExpiring(30);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(retentionMoneyMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // 双闭区间：到期日 >= 今天 且 <= 今天+30，且仅 ACTIVE
        assertThat(sql).contains("expire_date >=").contains("expire_date <=").contains("status =");
        java.util.Collection<Object> params = captor.getValue().getParamNameValuePairs().values();
        LocalDate now = LocalDate.now();
        assertThat(params).contains(now, now.plusDays(30), "ACTIVE");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("getOverdue - 逾期查询：仅 ACTIVE 且到期日 < 今天（与预警任务 OVERDUE 同口径）")
    void getOverdue_windowBoundaries() {
        when(retentionMoneyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        service.getOverdue();

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(retentionMoneyMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("expire_date <").doesNotContain("expire_date >=").contains("status =");
        java.util.Collection<Object> params = captor.getValue().getParamNameValuePairs().values();
        assertThat(params).contains(LocalDate.now(), "ACTIVE");
    }

    @Test
    @DisplayName("getOverdue - 无逾期记录返回空列表，不抛异常")
    void getOverdue_emptyResult() {
        when(retentionMoneyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        assertThat(service.getOverdue()).isEmpty();
    }

    @Test
    @DisplayName("getOverdue - 有逾期记录按到期日升序透传返回")
    void getOverdue_delegates() {
        BizRetentionMoney m = money(1L);
        m.setExpireDate(LocalDate.now().minusDays(5));
        when(retentionMoneyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(m));

        assertThat(service.getOverdue()).hasSize(1);
        assertThat(service.getOverdue().get(0).getExpireDate()).isBefore(LocalDate.now());
    }

    @Test
    @DisplayName("save - 质保金金额负/零/null 拒绝（P0 FIN-RTN-04）")
    void save_invalidAmount_rejected() {
        BizRetentionMoney neg = money(1L);
        neg.setRetentionAmount(new java.math.BigDecimal("-1000"));
        assertThatThrownBy(() -> service.save(neg))
                .isInstanceOf(BusinessException.class).hasMessageContaining("质保金金额必须大于0");

        BizRetentionMoney zero = money(2L);
        zero.setRetentionAmount(java.math.BigDecimal.ZERO);
        assertThatThrownBy(() -> service.save(zero))
                .isInstanceOf(BusinessException.class).hasMessageContaining("质保金金额必须大于0");

        BizRetentionMoney nullAmount = money(3L);
        nullAmount.setRetentionAmount(null);
        assertThatThrownBy(() -> service.save(nullAmount))
                .isInstanceOf(BusinessException.class).hasMessageContaining("质保金金额必须大于0");

        verify(retentionMoneyMapper, never()).insert(any());
    }
}
