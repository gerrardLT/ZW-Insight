package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizDepositReturn;
import com.zwinsight.tender.mapper.BizDepositReturnMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DepositReturnService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DepositReturnServiceTest {

    @Mock private BizDepositReturnMapper returnMapper;

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
    @DisplayName("新增退还：委托 mapper.insert")
    void testSave_delegatesInsert() {
        BizDepositReturn ret = new BizDepositReturn();
        when(returnMapper.insert(ret)).thenReturn(1);

        depositReturnService.save(ret);

        verify(returnMapper).insert(ret);
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
