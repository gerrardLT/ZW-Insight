package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InvoiceApplyService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class InvoiceApplyServiceTest {

    @Mock
    private BizInvoiceApplyMapper invoiceApplyMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private InvoiceApplyService invoiceApplyService;

    // ============ save() 测试 ============

    @Nested
    @DisplayName("save() 新增开票申请")
    class SaveTests {

        @Test
        @DisplayName("正常路径 — 状态设为 DRAFT，mapper.insert 被调用")
        void save_normalPath_setsStatusDraftAndCallsInsert() {
            BizInvoiceApply invoiceApply = new BizInvoiceApply();
            invoiceApply.setContractId(1L);
            invoiceApply.setInvoiceAmount(new BigDecimal("50000.00"));

            invoiceApplyService.save(invoiceApply);

            assertThat(invoiceApply.getStatus()).isEqualTo("DRAFT");
            verify(invoiceApplyMapper).insert(invoiceApply);
        }
    }

    // ============ getById() 测试 ============

    @Nested
    @DisplayName("getById() 查询开票申请")
    class GetByIdTests {

        @Test
        @DisplayName("正常路径 — 返回 mapper 查询结果")
        void getById_exists_returnsInvoiceApply() {
            Long id = 1L;
            BizInvoiceApply expected = new BizInvoiceApply();
            expected.setId(id);
            expected.setStatus("DRAFT");
            expected.setInvoiceAmount(new BigDecimal("10000.00"));

            when(invoiceApplyMapper.selectById(id)).thenReturn(expected);

            BizInvoiceApply result = invoiceApplyService.getById(id);

            assertThat(result).isSameAs(expected);
            verify(invoiceApplyMapper).selectById(id);
        }

        @Test
        @DisplayName("记录不存在 — 抛出 BusinessException")
        void getById_notExists_throwsBusinessException() {
            Long id = 999L;
            when(invoiceApplyMapper.selectById(id)).thenReturn(null);

            assertThatThrownBy(() -> invoiceApplyService.getById(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("开票申请不存在");
        }
    }

    // ============ update() 测试 ============

    @Nested
    @DisplayName("update() 更新开票申请")
    class UpdateTests {

        @Test
        @DisplayName("正常路径（DRAFT 状态）— mapper.updateById 被调用")
        void update_draftStatus_callsUpdateById() {
            Long id = 1L;
            BizInvoiceApply existing = new BizInvoiceApply();
            existing.setId(id);
            existing.setStatus("DRAFT");

            BizInvoiceApply updateData = new BizInvoiceApply();
            updateData.setId(id);
            updateData.setInvoiceAmount(new BigDecimal("20000.00"));

            when(invoiceApplyMapper.selectById(id)).thenReturn(existing);

            invoiceApplyService.update(updateData);

            verify(invoiceApplyMapper).updateById(updateData);
        }

        @Test
        @DisplayName("非 DRAFT 状态 — 抛出 BusinessException")
        void update_nonDraftStatus_throwsBusinessException() {
            Long id = 2L;
            BizInvoiceApply existing = new BizInvoiceApply();
            existing.setId(id);
            existing.setStatus("APPROVED");

            BizInvoiceApply updateData = new BizInvoiceApply();
            updateData.setId(id);

            when(invoiceApplyMapper.selectById(id)).thenReturn(existing);

            assertThatThrownBy(() -> invoiceApplyService.update(updateData))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅草稿状态可编辑");

            verify(invoiceApplyMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("记录不存在 — 抛出 BusinessException")
        void update_notExists_throwsBusinessException() {
            Long id = 999L;
            BizInvoiceApply updateData = new BizInvoiceApply();
            updateData.setId(id);

            when(invoiceApplyMapper.selectById(id)).thenReturn(null);

            assertThatThrownBy(() -> invoiceApplyService.update(updateData))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("开票申请不存在");

            verify(invoiceApplyMapper, never()).updateById(any());
        }
    }

    // ============ delete() 测试 ============

    @Nested
    @DisplayName("delete() 删除开票申请")
    class DeleteTests {

        @Test
        @DisplayName("正常路径 — 仅 DRAFT 可删除")
        void delete_draftStatus_callsDeleteById() {
            Long id = 1L;
            BizInvoiceApply existing = new BizInvoiceApply();
            existing.setId(id);
            existing.setStatus("DRAFT");

            when(invoiceApplyMapper.selectById(id)).thenReturn(existing);

            invoiceApplyService.delete(id);

            verify(invoiceApplyMapper).deleteById(id);
        }

        @Test
        @DisplayName("非 DRAFT 状态 — 抛出 BusinessException")
        void delete_nonDraftStatus_throwsBusinessException() {
            Long id = 2L;
            BizInvoiceApply existing = new BizInvoiceApply();
            existing.setId(id);
            existing.setStatus("APPROVED");

            when(invoiceApplyMapper.selectById(id)).thenReturn(existing);

            assertThatThrownBy(() -> invoiceApplyService.delete(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅草稿状态可删除");

            verify(invoiceApplyMapper, never()).deleteById(anyLong());
        }
    }

    // ============ submit() 测试 ============

    @Nested
    @DisplayName("submit() 提交开票申请")
    class SubmitTests {

        @Test
        @DisplayName("正常路径 — 金额校验通过 + 审批流发起 + 状态变更为 APPROVED + 合同累计开票金额回写")
        void submit_normalPath_approvalStartedAndContractUpdated() {
            Long id = 1L;
            Long contractId = 100L;
            BigDecimal invoiceAmount = new BigDecimal("30000.00");

            BizInvoiceApply invoiceApply = new BizInvoiceApply();
            invoiceApply.setId(id);
            invoiceApply.setContractId(contractId);
            invoiceApply.setInvoiceAmount(invoiceAmount);
            invoiceApply.setStatus("DRAFT");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setCumulativeOutput(new BigDecimal("100000.00"));
            contract.setCumulativeInvoiceAmount(new BigDecimal("50000.00"));

            when(invoiceApplyMapper.selectById(id)).thenReturn(invoiceApply);
            when(contractMapper.selectById(contractId)).thenReturn(contract);
            when(approvalService.startProcess(
                    eq("INVOICE_APPLY"), eq(id), eq("invoice_apply_approval"), anyMap()))
                    .thenReturn("process-instance-001");

            invoiceApplyService.submit(id);

            // 验证状态变更为 APPROVED
            assertThat(invoiceApply.getStatus()).isEqualTo("APPROVED");
            assertThat(invoiceApply.getWorkflowInstanceId()).isEqualTo("process-instance-001");

            // 验证 mapper.updateById 被调用保存开票申请
            verify(invoiceApplyMapper).updateById(invoiceApply);

            // 验证审批流程发起
            verify(approvalService).startProcess(
                    eq("INVOICE_APPLY"), eq(id), eq("invoice_apply_approval"), anyMap());

            // 验证合同累计开票金额回写：50000 + 30000 = 80000
            assertThat(contract.getCumulativeInvoiceAmount())
                    .isEqualByComparingTo(new BigDecimal("80000.00"));
            verify(contractMapper).updateById(contract);
        }

        @Test
        @DisplayName("开票金额超限 — invoiceAmount > cumulativeOutput - cumulativeInvoiceAmount 时抛 BusinessException")
        void submit_amountExceedsLimit_throwsBusinessException() {
            Long id = 2L;
            Long contractId = 200L;

            BizInvoiceApply invoiceApply = new BizInvoiceApply();
            invoiceApply.setId(id);
            invoiceApply.setContractId(contractId);
            invoiceApply.setInvoiceAmount(new BigDecimal("60000.00")); // 超出限额
            invoiceApply.setStatus("DRAFT");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setCumulativeOutput(new BigDecimal("100000.00"));
            contract.setCumulativeInvoiceAmount(new BigDecimal("50000.00"));
            // maxInvoiceAmount = 100000 - 50000 = 50000，而申请开票 60000 > 50000

            when(invoiceApplyMapper.selectById(id)).thenReturn(invoiceApply);
            when(contractMapper.selectById(contractId)).thenReturn(contract);

            assertThatThrownBy(() -> invoiceApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("开票金额不能超过累计产值减已开票金额");

            // 验证审批流未发起
            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
            // 验证合同未被更新
            verify(contractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("非 DRAFT 状态 — 抛出 BusinessException")
        void submit_nonDraftStatus_throwsBusinessException() {
            Long id = 3L;

            BizInvoiceApply invoiceApply = new BizInvoiceApply();
            invoiceApply.setId(id);
            invoiceApply.setStatus("APPROVED");

            when(invoiceApplyMapper.selectById(id)).thenReturn(invoiceApply);

            assertThatThrownBy(() -> invoiceApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅草稿状态可提交");

            verify(contractMapper, never()).selectById(anyLong());
            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }

        @Test
        @DisplayName("关联合同不存在 — 抛出 BusinessException")
        void submit_contractNotExists_throwsBusinessException() {
            Long id = 4L;
            Long contractId = 999L;

            BizInvoiceApply invoiceApply = new BizInvoiceApply();
            invoiceApply.setId(id);
            invoiceApply.setContractId(contractId);
            invoiceApply.setInvoiceAmount(new BigDecimal("10000.00"));
            invoiceApply.setStatus("DRAFT");

            when(invoiceApplyMapper.selectById(id)).thenReturn(invoiceApply);
            when(contractMapper.selectById(contractId)).thenReturn(null);

            assertThatThrownBy(() -> invoiceApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("关联合同不存在");

            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }
    }
}
