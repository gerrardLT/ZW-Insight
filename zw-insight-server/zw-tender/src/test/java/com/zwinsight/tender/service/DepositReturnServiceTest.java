package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizDepositReturn;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.tender.mapper.BizDepositReturnMapper;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.*;

/**
 * DepositReturnService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DepositReturnServiceTest {

    @Mock private BizDepositReturnMapper returnMapper;
    @Mock private BizDepositApplyMapper depositApplyMapper;

    @InjectMocks
    private DepositReturnService depositReturnService;

    @Test
    @DisplayName("分页查询：带 depositApplyId 过滤返回 PageResult")
    void testPage_withFilter_returnsResult() {
        when(returnMapper.selectPage(any(Page.class), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        PageResult<BizDepositReturn> result = depositReturnService.page(1, 10, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).isEmpty();
        verify(returnMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("分页查询：depositApplyId 为空时不报错")
    void testPage_nullFilter_returnsResult() {
        when(returnMapper.selectPage(any(Page.class), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        PageResult<BizDepositReturn> result = depositReturnService.page(1, 10, null);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("新增退还：金额≤可退余额正常落库")
    void testSave_withinBalance_inserts() {
        BizDepositApply apply = new BizDepositApply();
        apply.setId(10L);
        apply.setDepositAmount(new BigDecimal("5000"));
        when(depositApplyMapper.selectById(10L)).thenReturn(apply);
        when(returnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BizDepositReturn ret = new BizDepositReturn();
        ret.setDepositApplyId(10L);
        ret.setReturnAmount(new BigDecimal("5000"));

        depositReturnService.save(ret);

        verify(returnMapper).insert(ret);
    }

    @Test
    @DisplayName("新增退还：超额/负零/申请不存在拒绝（P2 TND-36/37）")
    void testSave_guardCases_rejected() {
        BizDepositApply apply = new BizDepositApply();
        apply.setId(10L);
        apply.setDepositAmount(new BigDecimal("5000"));
        when(depositApplyMapper.selectById(10L)).thenReturn(apply);
        when(returnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BizDepositReturn over = new BizDepositReturn();
        over.setDepositApplyId(10L);
        over.setReturnAmount(new BigDecimal("5001"));
        assertThatThrownBy(() -> depositReturnService.save(over))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退还金额超过可退余额");

        BizDepositReturn neg = new BizDepositReturn();
        neg.setDepositApplyId(10L);
        neg.setReturnAmount(new BigDecimal("-1"));
        assertThatThrownBy(() -> depositReturnService.save(neg))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退还金额必须大于0");

        BizDepositReturn missing = new BizDepositReturn();
        missing.setDepositApplyId(999L);
        missing.setReturnAmount(new BigDecimal("100"));
        when(depositApplyMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> depositReturnService.save(missing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保证金申请不存在");

        verify(returnMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增退还：已退部分后余额不足拒绝（防重复全额退）")
    void testSave_alreadyPartiallyReturned_rejected() {
        BizDepositApply apply = new BizDepositApply();
        apply.setId(10L);
        apply.setDepositAmount(new BigDecimal("5000"));
        when(depositApplyMapper.selectById(10L)).thenReturn(apply);

        BizDepositReturn returned = new BizDepositReturn();
        returned.setReturnAmount(new BigDecimal("3000"));
        when(returnMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(returned));

        BizDepositReturn again = new BizDepositReturn();
        again.setDepositApplyId(10L);
        again.setReturnAmount(new BigDecimal("3000"));
        assertThatThrownBy(() -> depositReturnService.save(again))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退还金额超过可退余额");
        verify(returnMapper, never()).insert(any());
    }

    @Test
    @DisplayName("更新退还：委托 mapper.updateById")
    void testUpdate_delegatesUpdate() {
        BizDepositReturn ret = new BizDepositReturn();
        ret.setId(1L);
        when(returnMapper.updateById(ret)).thenReturn(1);

        depositReturnService.update(ret);

        verify(returnMapper).updateById(ret);
    }

    @Test
    @DisplayName("删除退还：委托 mapper.deleteById")
    void testDelete_delegatesDelete() {
        depositReturnService.delete(1L);

        verify(returnMapper).deleteById(1L);
    }
}
