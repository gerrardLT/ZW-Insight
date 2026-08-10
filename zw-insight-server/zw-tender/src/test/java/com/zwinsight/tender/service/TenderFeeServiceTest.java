package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizTenderFee;
import com.zwinsight.tender.mapper.BizTenderFeeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TenderFeeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TenderFeeServiceTest {

    @Mock private BizTenderFeeMapper feeMapper;

    @InjectMocks
    private TenderFeeService tenderFeeService;

    @Test
    @DisplayName("分页查询：按 registerId 过滤返回 PageResult")
    void testPage_returnsResult() {
        when(feeMapper.selectPage(any(Page.class), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        PageResult<BizTenderFee> result = tenderFeeService.page(1, 10, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("新增：状态初始化为 DRAFT")
    void testSave_initializesDraft() {
        BizTenderFee fee = new BizTenderFee();
        when(feeMapper.insert(fee)).thenReturn(1);

        tenderFeeService.save(fee);

        assertThat(fee.getStatus()).isEqualTo("DRAFT");
        verify(feeMapper).insert(fee);
    }

    @Test
    @DisplayName("更新：DRAFT 可编辑")
    void testUpdate_draftAllowed() {
        BizTenderFee fee = new BizTenderFee();
        fee.setId(1L);
        BizTenderFee existing = new BizTenderFee();
        existing.setStatus("DRAFT");
        when(feeMapper.selectById(1L)).thenReturn(existing);
        when(feeMapper.updateById(fee)).thenReturn(1);

        tenderFeeService.update(fee);

        verify(feeMapper).updateById(fee);
    }

    @Test
    @DisplayName("更新：非 DRAFT 拒绝")
    void testUpdate_nonDraftRejected() {
        BizTenderFee fee = new BizTenderFee();
        fee.setId(1L);
        BizTenderFee existing = new BizTenderFee();
        existing.setStatus("PAID");
        when(feeMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> tenderFeeService.update(fee))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
        verify(feeMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新：不存在抛异常")
    void testUpdate_notFound() {
        BizTenderFee fee = new BizTenderFee();
        fee.setId(999L);
        when(feeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenderFeeService.update(fee))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标费用不存在");
    }

    @Test
    @DisplayName("删除：DRAFT 可删")
    void testDelete_draftAllowed() {
        BizTenderFee existing = new BizTenderFee();
        existing.setStatus("DRAFT");
        when(feeMapper.selectById(1L)).thenReturn(existing);

        tenderFeeService.delete(1L);

        verify(feeMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非 DRAFT 拒绝")
    void testDelete_nonDraftRejected() {
        BizTenderFee existing = new BizTenderFee();
        existing.setStatus("PAID");
        when(feeMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> tenderFeeService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
        verify(feeMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("确认付款：置 PAID 并保存回单")
    void testConfirmPayment_setsPaidAndReceipt() {
        BizTenderFee fee = new BizTenderFee();
        fee.setId(1L);
        fee.setStatus("DRAFT");
        when(feeMapper.selectById(1L)).thenReturn(fee);
        when(feeMapper.updateById(fee)).thenReturn(1);

        tenderFeeService.confirmPayment(1L, "receipt.pdf");

        assertThat(fee.getStatus()).isEqualTo("PAID");
        assertThat(fee.getReceiptFile()).isEqualTo("receipt.pdf");
        verify(feeMapper).updateById(fee);
    }

    @Test
    @DisplayName("确认付款：不存在抛异常")
    void testConfirmPayment_notFound() {
        when(feeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenderFeeService.confirmPayment(999L, "r.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标费用不存在");
    }
}
