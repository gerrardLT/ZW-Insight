package com.zwinsight.contract.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizContractDetailMapper;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConstructionContractService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("施工合同服务 - 单元测试")
class ConstructionContractServiceTest {

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private BizContractDetailMapper detailMapper;

    @Mock
    private SerialNumberService serialNumberService;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ConstructionContractService contractService;

    // ============ save() 测试 ============

    @Nested
    @DisplayName("save() - 新增合同")
    class SaveTests {

        @Test
        @DisplayName("正常路径：自动编号+DRAFT状态+税金计算+初始化累计字段")
        void save_happyPath_shouldSetCodeStatusTaxAndCumulativeFields() {
            // Given
            BizConstructionContract contract = new BizConstructionContract();
            contract.setContractAmount(new BigDecimal("1000000"));
            contract.setTaxRate(new BigDecimal("13"));

            when(serialNumberService.generate("CONTRACT")).thenReturn("CONTRACT202507010001");
            when(contractMapper.insert(any(BizConstructionContract.class))).thenReturn(1);

            // When
            contractService.save(contract);

            // Then
            ArgumentCaptor<BizConstructionContract> captor = ArgumentCaptor.forClass(BizConstructionContract.class);
            verify(contractMapper).insert(captor.capture());
            BizConstructionContract saved = captor.getValue();

            // 验证自动编号
            assertThat(saved.getContractCode()).isEqualTo("CONTRACT202507010001");
            // 验证状态设为 DRAFT
            assertThat(saved.getStatus()).isEqualTo("DRAFT");
            // 验证税金已计算
            assertThat(saved.getAmountWithoutTax()).isNotNull();
            assertThat(saved.getTaxAmount()).isNotNull();
            // 验证累计字段初始化为 0
            assertThat(saved.getCumulativeChangeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getCumulativeOutput()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getCumulativeInvoiceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getCumulativeReceivedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("税金计算验证：contractAmount=1130000, taxRate=13 → amountWithoutTax=1000000.00, taxAmount=130000.00")
        void save_taxCalculation_shouldComputeCorrectly() {
            // Given
            BizConstructionContract contract = new BizConstructionContract();
            contract.setContractAmount(new BigDecimal("1130000"));
            contract.setTaxRate(new BigDecimal("13"));

            when(serialNumberService.generate("CONTRACT")).thenReturn("CONTRACT202507010001");
            when(contractMapper.insert(any(BizConstructionContract.class))).thenReturn(1);

            // When
            contractService.save(contract);

            // Then
            ArgumentCaptor<BizConstructionContract> captor = ArgumentCaptor.forClass(BizConstructionContract.class);
            verify(contractMapper).insert(captor.capture());
            BizConstructionContract saved = captor.getValue();

            // amountWithoutTax = 1130000 / (1 + 13/100) = 1130000 / 1.13 = 1000000.00
            assertThat(saved.getAmountWithoutTax()).isEqualByComparingTo(new BigDecimal("1000000.00"));
            // taxAmount = 1130000 - 1000000.00 = 130000.00
            assertThat(saved.getTaxAmount()).isEqualByComparingTo(new BigDecimal("130000.00"));
        }

        @Test
        @DisplayName("累计字段已有值时不覆盖")
        void save_cumulativeFieldsAlreadySet_shouldNotOverride() {
            // Given
            BizConstructionContract contract = new BizConstructionContract();
            contract.setContractAmount(new BigDecimal("500000"));
            contract.setTaxRate(new BigDecimal("9"));
            contract.setCumulativeChangeAmount(new BigDecimal("10000"));
            contract.setCumulativeOutput(new BigDecimal("20000"));
            contract.setCumulativeInvoiceAmount(new BigDecimal("30000"));
            contract.setCumulativeReceivedAmount(new BigDecimal("40000"));

            when(serialNumberService.generate("CONTRACT")).thenReturn("CONTRACT202507010002");
            when(contractMapper.insert(any(BizConstructionContract.class))).thenReturn(1);

            // When
            contractService.save(contract);

            // Then
            ArgumentCaptor<BizConstructionContract> captor = ArgumentCaptor.forClass(BizConstructionContract.class);
            verify(contractMapper).insert(captor.capture());
            BizConstructionContract saved = captor.getValue();

            // 已有值的字段不应被覆盖为 0
            assertThat(saved.getCumulativeChangeAmount()).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(saved.getCumulativeOutput()).isEqualByComparingTo(new BigDecimal("20000"));
            assertThat(saved.getCumulativeInvoiceAmount()).isEqualByComparingTo(new BigDecimal("30000"));
            assertThat(saved.getCumulativeReceivedAmount()).isEqualByComparingTo(new BigDecimal("40000"));
        }
    }

    // ============ update() 测试 ============

    @Nested
    @DisplayName("update() - 更新合同")
    class UpdateTests {

        @Test
        @DisplayName("DRAFT状态可编辑并重新计税")
        void update_draftStatus_shouldRecalculateTax() {
            // Given
            Long contractId = 100L;
            BizConstructionContract existing = new BizConstructionContract();
            existing.setId(contractId);
            existing.setStatus("DRAFT");

            BizConstructionContract updateContract = new BizConstructionContract();
            updateContract.setId(contractId);
            updateContract.setContractAmount(new BigDecimal("2260000"));
            updateContract.setTaxRate(new BigDecimal("13"));

            when(contractMapper.selectById(contractId)).thenReturn(existing);
            when(contractMapper.updateById(any(BizConstructionContract.class))).thenReturn(1);

            // When
            contractService.update(updateContract);

            // Then
            ArgumentCaptor<BizConstructionContract> captor = ArgumentCaptor.forClass(BizConstructionContract.class);
            verify(contractMapper).updateById(captor.capture());
            BizConstructionContract updated = captor.getValue();

            // 验证重新计算税金: 2260000 / 1.13 = 2000000.00
            assertThat(updated.getAmountWithoutTax()).isEqualByComparingTo(new BigDecimal("2000000.00"));
            assertThat(updated.getTaxAmount()).isEqualByComparingTo(new BigDecimal("260000.00"));
        }

        @Test
        @DisplayName("非DRAFT状态编辑抛出异常")
        void update_nonDraftStatus_shouldThrowException() {
            // Given
            Long contractId = 100L;
            BizConstructionContract existing = new BizConstructionContract();
            existing.setId(contractId);
            existing.setStatus("EFFECTIVE");

            BizConstructionContract updateContract = new BizConstructionContract();
            updateContract.setId(contractId);

            when(contractMapper.selectById(contractId)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> contractService.update(updateContract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可编辑");

            verify(contractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("合同不存在时抛出异常")
        void update_contractNotExists_shouldThrowException() {
            // Given
            Long contractId = 999L;
            BizConstructionContract updateContract = new BizConstructionContract();
            updateContract.setId(contractId);

            when(contractMapper.selectById(contractId)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.update(updateContract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("合同不存在");

            verify(contractMapper, never()).updateById(any());
        }
    }

    // ============ delete() 测试 ============

    @Nested
    @DisplayName("delete() - 删除合同")
    class DeleteTests {

        @Test
        @DisplayName("DRAFT状态可删除")
        void delete_draftStatus_shouldDelete() {
            // Given
            Long contractId = 100L;
            BizConstructionContract existing = new BizConstructionContract();
            existing.setId(contractId);
            existing.setStatus("DRAFT");

            when(contractMapper.selectById(contractId)).thenReturn(existing);
            when(contractMapper.deleteById(contractId)).thenReturn(1);

            // When
            contractService.delete(contractId);

            // Then
            verify(contractMapper).deleteById(contractId);
        }

        @Test
        @DisplayName("非DRAFT状态删除抛出异常")
        void delete_nonDraftStatus_shouldThrowException() {
            // Given
            Long contractId = 100L;
            BizConstructionContract existing = new BizConstructionContract();
            existing.setId(contractId);
            existing.setStatus("EFFECTIVE");

            when(contractMapper.selectById(contractId)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> contractService.delete(contractId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可删除");

            verify(contractMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("合同不存在时抛出异常")
        void delete_contractNotExists_shouldThrowException() {
            // Given
            Long contractId = 999L;
            when(contractMapper.selectById(contractId)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.delete(contractId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("合同不存在");

            verify(contractMapper, never()).deleteById(anyLong());
        }
    }

    // ============ submit() 测试 ============

    @Nested
    @DisplayName("submit() - 提交审批")
    class SubmitTests {

        @Test
        @DisplayName("正常路径：审批流发起+状态变更为EFFECTIVE")
        void submit_happyPath_shouldStartProcessAndChangeStatus() {
            // Given
            Long contractId = 100L;
            BizConstructionContract existing = new BizConstructionContract();
            existing.setId(contractId);
            existing.setStatus("DRAFT");
            existing.setContractAmount(new BigDecimal("1130000"));
            existing.setProjectId(1L);

            when(contractMapper.selectById(contractId)).thenReturn(existing);
            when(approvalService.startProcess(
                    eq("CONSTRUCTION_CONTRACT"),
                    eq(contractId),
                    eq("construction_contract_approval"),
                    any(Map.class)
            )).thenReturn("PROCESS-001");
            when(contractMapper.updateById(any(BizConstructionContract.class))).thenReturn(1);

            // When
            contractService.submit(contractId);

            // Then
            ArgumentCaptor<BizConstructionContract> captor = ArgumentCaptor.forClass(BizConstructionContract.class);
            verify(contractMapper).updateById(captor.capture());
            BizConstructionContract submitted = captor.getValue();

            assertThat(submitted.getStatus()).isEqualTo("EFFECTIVE");
            assertThat(submitted.getWorkflowInstanceId()).isEqualTo("PROCESS-001");
        }

        @Test
        @DisplayName("非DRAFT状态提交抛出异常")
        void submit_nonDraftStatus_shouldThrowException() {
            // Given
            Long contractId = 100L;
            BizConstructionContract existing = new BizConstructionContract();
            existing.setId(contractId);
            existing.setStatus("SETTLED");

            when(contractMapper.selectById(contractId)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> contractService.submit(contractId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可提交");

            verify(approvalService, never()).startProcess(any(), any(), any(), any());
            verify(contractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("合同不存在时抛出异常")
        void submit_contractNotExists_shouldThrowException() {
            // Given
            Long contractId = 999L;
            when(contractMapper.selectById(contractId)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.submit(contractId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("合同不存在");

            verify(approvalService, never()).startProcess(any(), any(), any(), any());
        }
    }

    // ============ getById() 测试 ============

    @Nested
    @DisplayName("getById() - 根据ID查询合同")
    class GetByIdTests {

        @Test
        @DisplayName("合同存在时正常返回")
        void getById_exists_shouldReturnContract() {
            // Given
            Long contractId = 100L;
            BizConstructionContract expected = new BizConstructionContract();
            expected.setId(contractId);
            expected.setContractCode("CONTRACT202507010001");
            expected.setContractAmount(new BigDecimal("1130000"));

            when(contractMapper.selectById(contractId)).thenReturn(expected);

            // When
            BizConstructionContract result = contractService.getById(contractId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(contractId);
            assertThat(result.getContractCode()).isEqualTo("CONTRACT202507010001");
            assertThat(result.getContractAmount()).isEqualByComparingTo(new BigDecimal("1130000"));
        }

        @Test
        @DisplayName("合同不存在时抛出异常")
        void getById_notExists_shouldThrowException() {
            // Given
            Long contractId = 999L;
            when(contractMapper.selectById(contractId)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.getById(contractId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("合同不存在");
        }
    }
}
