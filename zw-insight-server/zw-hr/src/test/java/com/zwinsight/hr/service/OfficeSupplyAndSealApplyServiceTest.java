package com.zwinsight.hr.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.hr.domain.BizOfficeSupply;
import com.zwinsight.hr.domain.BizSealApply;
import com.zwinsight.hr.mapper.BizOfficeSupplyMapper;
import com.zwinsight.hr.mapper.BizSealApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfficeSupplyAndSealApplyServiceTest {

    @ExtendWith(MockitoExtension.class)
    static class OfficeSupplyServiceTest {

        @Mock private BizOfficeSupplyMapper supplyMapper;
        @InjectMocks private OfficeSupplyService officeSupplyService;

        @Test
        @DisplayName("新增办公用品：库存默认0")
        void testSave_defaultStock() {
            BizOfficeSupply supply = new BizOfficeSupply();
            supply.setSupplyName("打印纸");
            supply.setStockQuantity(null);
            when(supplyMapper.insert(any())).thenReturn(1);

            officeSupplyService.save(supply);

            assertThat(supply.getStockQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("更新办公用品：不存在抛异常")
        void testUpdate_notFound() {
            when(supplyMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> officeSupplyService.update(new BizOfficeSupply() {{ setId(999L); }}))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("办公用品不存在");
        }

        @Test
        @DisplayName("删除办公用品：正常删除")
        void testDelete() {
            officeSupplyService.delete(1L);
            verify(supplyMapper).deleteById(1L);
        }
    }

    @ExtendWith(MockitoExtension.class)
    static class SealApplyServiceTest {

        @Mock private BizSealApplyMapper sealApplyMapper;
        @Mock private ApprovalService approvalService;
        @InjectMocks private SealApplyService sealApplyService;

        @Test
        @DisplayName("新增用印申请：DRAFT状态")
        void testSave() {
            BizSealApply apply = new BizSealApply();
            apply.setSealType("公章");
            when(sealApplyMapper.insert(any())).thenReturn(1);

            sealApplyService.save(apply);

            assertThat(apply.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("删除：非DRAFT拒绝")
        void testDelete_nonDraft() {
            BizSealApply apply = new BizSealApply();
            apply.setId(1L);
            apply.setStatus("APPROVED");
            when(sealApplyMapper.selectById(1L)).thenReturn(apply);

            assertThatThrownBy(() -> sealApplyService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可删除");
        }

        @Test
        @DisplayName("提交：DRAFT→APPROVED")
        void testSubmit() {
            BizSealApply apply = new BizSealApply();
            apply.setId(1L);
            apply.setStatus("DRAFT");
            apply.setSealType("公章");
            apply.setApplicant("张三");
            when(sealApplyMapper.selectById(1L)).thenReturn(apply);
            when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-1");

            sealApplyService.submit(1L);

            assertThat(apply.getStatus()).isEqualTo("APPROVED");
        }
    }
}
