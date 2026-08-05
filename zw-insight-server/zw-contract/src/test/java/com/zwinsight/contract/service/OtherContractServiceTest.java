package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OtherContractService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class OtherContractServiceTest {

    @Mock
    private BizOtherContractMapper otherContractMapper;

    @InjectMocks
    private OtherContractService service;

    private BizOtherContract contract(Long id, String status) {
        BizOtherContract c = new BizOtherContract();
        c.setId(id);
        c.setStatus(status);
        return c;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizOtherContract> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(contract(1L, "DRAFT")));
        page.setTotal(1L);
        when(otherContractMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizOtherContract> result = service.page(1, 10, 1L, "OTHER_EXPENSE");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("getById - 不存在抛异常")
    void getById_notFound_throws() {
        when(otherContractMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(99L)).hasMessageContaining("合同不存在");
    }

    @Test
    @DisplayName("save - 置 DRAFT 且 null 累计字段初始化为 0，已有值不覆盖")
    void save_initializesCumulativeFields() {
        BizOtherContract c = contract(null, null);
        c.setCumulativeSettlement(new BigDecimal("123")); // 已有值保留

        service.save(c);

        assertThat(c.getStatus()).isEqualTo("DRAFT");
        assertThat(c.getCumulativeSettlement()).isEqualByComparingTo("123");
        assertThat(c.getCumulativeInvoice()).isEqualByComparingTo("0");
        assertThat(c.getCumulativeReceived()).isEqualByComparingTo("0");
        assertThat(c.getCumulativePaid()).isEqualByComparingTo("0");
        verify(otherContractMapper).insert(c);
    }

    @Test
    @DisplayName("update - 不存在/非草稿抛异常；草稿可编辑")
    void update_variants() {
        when(otherContractMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(contract(1L, null)))
                .hasMessageContaining("合同不存在");

        when(otherContractMapper.selectById(2L)).thenReturn(contract(2L, "EFFECTIVE"));
        assertThatThrownBy(() -> service.update(contract(2L, null)))
                .hasMessageContaining("仅草稿状态可编辑");

        when(otherContractMapper.selectById(3L)).thenReturn(contract(3L, "DRAFT"));
        service.update(contract(3L, null));
        verify(otherContractMapper).updateById(any());
    }

    @Test
    @DisplayName("delete - 不存在/非草稿抛异常；草稿可删")
    void delete_variants() {
        when(otherContractMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining("合同不存在");

        when(otherContractMapper.selectById(2L)).thenReturn(contract(2L, "EFFECTIVE"));
        assertThatThrownBy(() -> service.delete(2L)).hasMessageContaining("仅草稿状态可删除");

        when(otherContractMapper.selectById(3L)).thenReturn(contract(3L, "DRAFT"));
        service.delete(3L);
        verify(otherContractMapper).deleteById(3L);
    }
}
