package com.zwinsight.hr.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.hr.domain.BizOfficeSupply;
import com.zwinsight.hr.domain.BizOfficeSupplyInOut;
import com.zwinsight.hr.mapper.BizOfficeSupplyInOutMapper;
import com.zwinsight.hr.mapper.BizOfficeSupplyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OfficeSupplyInOutService（办公用品出入库）单元测试
 *
 * 覆盖场景:
 * - 新增记录（DRAFT 状态）
 * - 提交校验（不存在/非草稿/关联用品不存在）
 * - 入库库存增加、出库库存减少、库存不足拦截
 */
@ExtendWith(MockitoExtension.class)
class OfficeSupplyInOutServiceTest {

    @Mock
    private BizOfficeSupplyInOutMapper inOutMapper;

    @Mock
    private BizOfficeSupplyMapper supplyMapper;

    @InjectMocks
    private OfficeSupplyInOutService inOutService;

    @Test
    @DisplayName("新增出入库记录：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizOfficeSupplyInOut inOut = new BizOfficeSupplyInOut();
        inOut.setSupplyId(5L);
        inOut.setIoType("IN");

        inOutService.save(inOut);

        assertThat(inOut.getStatus()).isEqualTo("DRAFT");
        verify(inOutMapper).insert(inOut);
    }

    @Test
    @DisplayName("提交出入库：记录不存在抛异常")
    void submit_notFound_throwsException() {
        when(inOutMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inOutService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出入库记录不存在");
    }

    @Test
    @DisplayName("提交出入库：非草稿状态拒绝")
    void submit_nonDraft_rejected() {
        BizOfficeSupplyInOut inOut = new BizOfficeSupplyInOut();
        inOut.setId(1L);
        inOut.setStatus("APPROVED");
        when(inOutMapper.selectById(1L)).thenReturn(inOut);

        assertThatThrownBy(() -> inOutService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交出入库：关联办公用品不存在抛异常")
    void submit_supplyNotFound_throwsException() {
        BizOfficeSupplyInOut inOut = new BizOfficeSupplyInOut();
        inOut.setId(1L);
        inOut.setSupplyId(5L);
        inOut.setStatus("DRAFT");
        when(inOutMapper.selectById(1L)).thenReturn(inOut);
        when(supplyMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> inOutService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联办公用品不存在");
    }

    @Test
    @DisplayName("提交入库：库存增加并置为 APPROVED")
    void submit_in_increasesStock() {
        BizOfficeSupplyInOut inOut = createInOut("IN", 10);
        when(inOutMapper.selectById(1L)).thenReturn(inOut);
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setId(5L);
        supply.setStockQuantity(3);
        when(supplyMapper.selectById(5L)).thenReturn(supply);

        inOutService.submit(1L);

        assertThat(supply.getStockQuantity()).isEqualTo(13);
        assertThat(inOut.getStatus()).isEqualTo("APPROVED");
        verify(supplyMapper).updateById(supply);
        verify(inOutMapper).updateById(inOut);
    }

    @Test
    @DisplayName("提交出库：库存减少")
    void submit_out_decreasesStock() {
        BizOfficeSupplyInOut inOut = createInOut("OUT", 4);
        when(inOutMapper.selectById(1L)).thenReturn(inOut);
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setId(5L);
        supply.setStockQuantity(10);
        when(supplyMapper.selectById(5L)).thenReturn(supply);

        inOutService.submit(1L);

        assertThat(supply.getStockQuantity()).isEqualTo(6);
        assertThat(inOut.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("提交出库：库存不足拦截")
    void submit_out_insufficientStock_rejected() {
        BizOfficeSupplyInOut inOut = createInOut("OUT", 20);
        when(inOutMapper.selectById(1L)).thenReturn(inOut);
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setId(5L);
        supply.setStockQuantity(10);
        when(supplyMapper.selectById(5L)).thenReturn(supply);

        assertThatThrownBy(() -> inOutService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");
    }

    private BizOfficeSupplyInOut createInOut(String ioType, int quantity) {
        BizOfficeSupplyInOut inOut = new BizOfficeSupplyInOut();
        inOut.setId(1L);
        inOut.setSupplyId(5L);
        inOut.setIoType(ioType);
        inOut.setQuantity(quantity);
        inOut.setStatus("DRAFT");
        return inOut;
    }
}
