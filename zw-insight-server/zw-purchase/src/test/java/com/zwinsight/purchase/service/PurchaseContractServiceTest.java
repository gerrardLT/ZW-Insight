package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.service.BudgetControlService;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseContractDetail;
import com.zwinsight.purchase.mapper.BizPurchaseContractDetailMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PurchaseContractService 单元测试
 * 覆盖：采购合同 CRUD + DRAFT 状态约束 + 提交审批 + 明细查询
 */
@ExtendWith(MockitoExtension.class)
class PurchaseContractServiceTest {

    @Mock
    private BizPurchaseContractMapper purchaseContractMapper;

    @Mock
    private BizPurchaseContractDetailMapper detailMapper;

    @Mock
    private SerialNumberService serialNumberService;

    @Mock
    private BudgetControlService budgetControlService;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private PurchaseContractService purchaseContractService;

    private BizPurchaseContract sampleContract;

    @BeforeEach
    void setUp() {
        sampleContract = new BizPurchaseContract();
        sampleContract.setId(1L);
        sampleContract.setProjectId(100L);
        sampleContract.setContractCode("PC-2026-001");
        sampleContract.setContractName("测试采购合同");
        sampleContract.setContractAmount(new BigDecimal("50000.00"));
        sampleContract.setStatus("DRAFT");
        sampleContract.setCumulativeInbound(BigDecimal.ZERO);
        sampleContract.setCumulativeSettlement(BigDecimal.ZERO);
        sampleContract.setCumulativePaid(BigDecimal.ZERO);
        sampleContract.setCumulativeInvoiceReceived(BigDecimal.ZERO);
    }

    // =====================================================================
    // 分页查询测试
    // =====================================================================

    @Nested
    @DisplayName("分页查询")
    class PageQueryTests {

        @Test
        @DisplayName("按项目ID和状态分页查询 - 返回正确分页结果")
        void page_withProjectIdAndStatus_returnsPageResult() {
            // given
            Page<BizPurchaseContract> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.singletonList(sampleContract));
            mockPage.setTotal(1L);
            when(purchaseContractMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizPurchaseContract> result = purchaseContractService.page(1, 10, 100L, null, null, "DRAFT");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1L);
            verify(purchaseContractMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("不传项目ID和状态 - 查询全部采购合同")
        void page_withoutFilters_returnsAll() {
            // given
            Page<BizPurchaseContract> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);
            when(purchaseContractMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizPurchaseContract> result = purchaseContractService.page(1, 10, null, null, null, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
        }
    }

    // =====================================================================
    // 根据ID查询测试
    // =====================================================================

    @Nested
    @DisplayName("根据ID查询")
    class GetByIdTests {

        @Test
        @DisplayName("查询存在的采购合同 - 返回合同实体")
        void getById_exists_returnsContract() {
            // given
            when(purchaseContractMapper.selectById(1L)).thenReturn(sampleContract);

            // when
            BizPurchaseContract result = purchaseContractService.getById(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getContractName()).isEqualTo("测试采购合同");
            assertThat(result.getContractAmount()).isEqualByComparingTo("50000.00");
        }

        @Test
        @DisplayName("查询不存在的采购合同 - 抛出BusinessException")
        void getById_notExists_throwsException() {
            // given
            when(purchaseContractMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> purchaseContractService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采购合同不存在");
        }
    }

    // =====================================================================
    // 新增采购合同测试
    // =====================================================================

    @Nested
    @DisplayName("新增采购合同")
    class SaveTests {

        @Test
        @DisplayName("新增采购合同 - 自动生成编号并设为DRAFT状态")
        void save_generatesCodeAndSetsDraftStatus() {
            // given
            BizPurchaseContract newContract = new BizPurchaseContract();
            newContract.setProjectId(200L);
            newContract.setContractName("新采购合同");
            newContract.setContractAmount(new BigDecimal("100000.00"));

            when(serialNumberService.generate("PURCHASE_CONTRACT")).thenReturn("PC-2026-002");
            when(budgetControlService.checkBudget(eq(200L), eq("MATERIAL"), any(BigDecimal.class)))
                    .thenReturn(true);
            when(purchaseContractMapper.insert(any(BizPurchaseContract.class))).thenReturn(1);

            // when
            purchaseContractService.save(newContract);

            // then
            verify(purchaseContractMapper).insert(argThat(contract ->
                    "PC-2026-002".equals(contract.getContractCode()) &&
                    "DRAFT".equals(contract.getStatus())
            ));
            verify(serialNumberService).generate("PURCHASE_CONTRACT");
            verify(budgetControlService).checkBudget(200L, "MATERIAL", new BigDecimal("100000.00"));
        }

        @Test
        @DisplayName("新增采购合同 - 累计字段为null时初始化为ZERO")
        void save_nullCumulativeFields_initToZero() {
            // given
            BizPurchaseContract newContract = new BizPurchaseContract();
            newContract.setProjectId(200L);
            newContract.setContractName("新采购合同");
            newContract.setContractAmount(new BigDecimal("80000.00"));
            newContract.setCumulativeInbound(null);
            newContract.setCumulativeSettlement(null);
            newContract.setCumulativePaid(null);
            newContract.setCumulativeInvoiceReceived(null);

            when(serialNumberService.generate("PURCHASE_CONTRACT")).thenReturn("PC-2026-003");
            when(budgetControlService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                    .thenReturn(true);
            when(purchaseContractMapper.insert(any(BizPurchaseContract.class))).thenReturn(1);

            // when
            purchaseContractService.save(newContract);

            // then
            verify(purchaseContractMapper).insert(argThat(contract ->
                    BigDecimal.ZERO.compareTo(contract.getCumulativeInbound()) == 0 &&
                    BigDecimal.ZERO.compareTo(contract.getCumulativeSettlement()) == 0 &&
                    BigDecimal.ZERO.compareTo(contract.getCumulativePaid()) == 0 &&
                    BigDecimal.ZERO.compareTo(contract.getCumulativeInvoiceReceived()) == 0
            ));
        }

        @Test
        @DisplayName("新增采购合同 - 累计字段已有值时不覆盖")
        void save_existingCumulativeFields_notOverwritten() {
            // given
            BizPurchaseContract newContract = new BizPurchaseContract();
            newContract.setProjectId(200L);
            newContract.setContractName("新采购合同");
            newContract.setContractAmount(new BigDecimal("80000.00"));
            newContract.setCumulativeInbound(new BigDecimal("10000.00"));
            newContract.setCumulativeSettlement(new BigDecimal("5000.00"));
            newContract.setCumulativePaid(new BigDecimal("3000.00"));
            newContract.setCumulativeInvoiceReceived(new BigDecimal("2000.00"));

            when(serialNumberService.generate("PURCHASE_CONTRACT")).thenReturn("PC-2026-004");
            when(budgetControlService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                    .thenReturn(true);
            when(purchaseContractMapper.insert(any(BizPurchaseContract.class))).thenReturn(1);

            // when
            purchaseContractService.save(newContract);

            // then: 已有值不被覆盖
            verify(purchaseContractMapper).insert(argThat(contract ->
                    new BigDecimal("10000.00").compareTo(contract.getCumulativeInbound()) == 0 &&
                    new BigDecimal("5000.00").compareTo(contract.getCumulativeSettlement()) == 0
            ));
        }

        @Test
        @DisplayName("新增采购合同 - 预算校验FORBID模式超预算时抛异常")
        void save_budgetExceeded_forbidMode_throwsException() {
            // given
            BizPurchaseContract newContract = new BizPurchaseContract();
            newContract.setProjectId(200L);
            newContract.setContractName("超预算合同");
            newContract.setContractAmount(new BigDecimal("9999999.99"));

            when(serialNumberService.generate("PURCHASE_CONTRACT")).thenReturn("PC-2026-005");
            when(budgetControlService.checkBudget(eq(200L), eq("MATERIAL"), any(BigDecimal.class)))
                    .thenThrow(new BusinessException("预算不足"));

            // when & then
            assertThatThrownBy(() -> purchaseContractService.save(newContract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预算不足");

            verify(purchaseContractMapper, never()).insert(any());
        }
    }

    // =====================================================================
    // 更新采购合同测试
    // =====================================================================

    @Nested
    @DisplayName("更新采购合同")
    class UpdateTests {

        @Test
        @DisplayName("更新DRAFT状态合同 - 成功")
        void update_draftStatus_success() {
            // given
            BizPurchaseContract updateContract = new BizPurchaseContract();
            updateContract.setId(1L);
            updateContract.setContractName("更新后的名称");

            when(purchaseContractMapper.selectById(1L)).thenReturn(sampleContract); // status=DRAFT
            when(purchaseContractMapper.updateById(any(BizPurchaseContract.class))).thenReturn(1);

            // when
            purchaseContractService.update(updateContract);

            // then
            verify(purchaseContractMapper).updateById(updateContract);
        }

        @Test
        @DisplayName("更新非DRAFT状态合同 - 抛出异常")
        void update_effectiveStatus_throwsException() {
            // given
            sampleContract.setStatus("EFFECTIVE");
            BizPurchaseContract updateContract = new BizPurchaseContract();
            updateContract.setId(1L);

            when(purchaseContractMapper.selectById(1L)).thenReturn(sampleContract);

            // when & then
            assertThatThrownBy(() -> purchaseContractService.update(updateContract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可编辑");

            verify(purchaseContractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("更新不存在的合同 - 抛出异常")
        void update_notExists_throwsException() {
            // given
            BizPurchaseContract updateContract = new BizPurchaseContract();
            updateContract.setId(999L);

            when(purchaseContractMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> purchaseContractService.update(updateContract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采购合同不存在");
        }
    }

    // =====================================================================
    // 删除采购合同测试
    // =====================================================================

    @Nested
    @DisplayName("删除采购合同")
    class DeleteTests {

        @Test
        @DisplayName("删除DRAFT状态合同 - 成功")
        void delete_draftStatus_success() {
            // given
            when(purchaseContractMapper.selectById(1L)).thenReturn(sampleContract);
            when(purchaseContractMapper.deleteById(1L)).thenReturn(1);

            // when
            purchaseContractService.delete(1L);

            // then
            verify(purchaseContractMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除非DRAFT状态合同 - 抛出异常")
        void delete_effectiveStatus_throwsException() {
            // given
            sampleContract.setStatus("EFFECTIVE");
            when(purchaseContractMapper.selectById(1L)).thenReturn(sampleContract);

            // when & then
            assertThatThrownBy(() -> purchaseContractService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可删除");

            verify(purchaseContractMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除不存在的合同 - 抛出异常")
        void delete_notExists_throwsException() {
            // given
            when(purchaseContractMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> purchaseContractService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采购合同不存在");
        }
    }

    // =====================================================================
    // 提交审批测试
    // =====================================================================

    @Nested
    @DisplayName("提交审批")
    class SubmitTests {

        @Test
        @DisplayName("提交DRAFT合同 - 发起审批流程并更新状态为EFFECTIVE")
        void submit_draftContract_startsWorkflowAndUpdatesStatus() {
            // given
            when(purchaseContractMapper.selectById(1L)).thenReturn(sampleContract);
            when(approvalService.startProcess(eq("PURCHASE_CONTRACT"), eq(1L),
                    eq("purchase_contract_approval"), anyMap()))
                    .thenReturn("process-instance-001");
            when(purchaseContractMapper.updateById(any(BizPurchaseContract.class))).thenReturn(1);

            // when
            purchaseContractService.submit(1L);

            // then
            verify(approvalService).startProcess(eq("PURCHASE_CONTRACT"), eq(1L),
                    eq("purchase_contract_approval"), argThat(vars ->
                            vars.containsKey("contractAmount") &&
                            vars.containsKey("projectId")
                    ));
            verify(purchaseContractMapper).updateById(argThat(contract ->
                    "EFFECTIVE".equals(contract.getStatus()) &&
                    "process-instance-001".equals(contract.getWorkflowInstanceId())
            ));
        }

        @Test
        @DisplayName("提交非DRAFT状态合同 - 抛出异常")
        void submit_effectiveContract_throwsException() {
            // given
            sampleContract.setStatus("EFFECTIVE");
            when(purchaseContractMapper.selectById(1L)).thenReturn(sampleContract);

            // when & then
            assertThatThrownBy(() -> purchaseContractService.submit(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可提交");

            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }

        @Test
        @DisplayName("提交不存在的合同 - 抛出异常")
        void submit_notExists_throwsException() {
            // given
            when(purchaseContractMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> purchaseContractService.submit(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采购合同不存在");
        }
    }

    // =====================================================================
    // 获取合同明细测试
    // =====================================================================

    @Nested
    @DisplayName("获取合同明细")
    class GetDetailsTests {

        @Test
        @DisplayName("获取合同明细 - 返回按排序号排列的明细列表")
        void getDetails_returnsOrderedDetails() {
            // given
            BizPurchaseContractDetail detail1 = new BizPurchaseContractDetail();
            detail1.setContractId(1L);
            detail1.setMaterialName("水泥");
            detail1.setSortOrder(1);

            BizPurchaseContractDetail detail2 = new BizPurchaseContractDetail();
            detail2.setContractId(1L);
            detail2.setMaterialName("钢筋");
            detail2.setSortOrder(2);

            when(detailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(detail1, detail2));

            // when
            List<BizPurchaseContractDetail> result = purchaseContractService.getDetails(1L);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getMaterialName()).isEqualTo("水泥");
            assertThat(result.get(1).getMaterialName()).isEqualTo("钢筋");
        }

        @Test
        @DisplayName("获取合同明细 - 无明细时返回空列表")
        void getDetails_noDetails_returnsEmptyList() {
            // given
            when(detailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            List<BizPurchaseContractDetail> result = purchaseContractService.getDetails(1L);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =====================================================================
    // BigDecimal 金额精度测试
    // =====================================================================

    @Nested
    @DisplayName("BigDecimal 金额精度")
    class BigDecimalPrecisionTests {

        @Test
        @DisplayName("新增合同 - 大额合同金额精度不丢失")
        void save_largeAmount_precisionPreserved() {
            // given
            BizPurchaseContract newContract = new BizPurchaseContract();
            newContract.setProjectId(200L);
            newContract.setContractName("大额采购合同");
            newContract.setContractAmount(new BigDecimal("999999999.99"));

            when(serialNumberService.generate("PURCHASE_CONTRACT")).thenReturn("PC-2026-010");
            when(budgetControlService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                    .thenReturn(true);
            when(purchaseContractMapper.insert(any(BizPurchaseContract.class))).thenReturn(1);

            // when
            purchaseContractService.save(newContract);

            // then
            verify(purchaseContractMapper).insert(argThat(contract ->
                    new BigDecimal("999999999.99").compareTo(contract.getContractAmount()) == 0
            ));
        }

        @Test
        @DisplayName("新增合同 - 小数精度保持2位")
        void save_decimalPrecision_preserved() {
            // given
            BizPurchaseContract newContract = new BizPurchaseContract();
            newContract.setProjectId(200L);
            newContract.setContractName("精度测试合同");
            newContract.setContractAmount(new BigDecimal("12345.67"));

            when(serialNumberService.generate("PURCHASE_CONTRACT")).thenReturn("PC-2026-011");
            when(budgetControlService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                    .thenReturn(true);
            when(purchaseContractMapper.insert(any(BizPurchaseContract.class))).thenReturn(1);

            // when
            purchaseContractService.save(newContract);

            // then
            verify(purchaseContractMapper).insert(argThat(contract ->
                    new BigDecimal("12345.67").compareTo(contract.getContractAmount()) == 0
            ));
        }
    }
}
