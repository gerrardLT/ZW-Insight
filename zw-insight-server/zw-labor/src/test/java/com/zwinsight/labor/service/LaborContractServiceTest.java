package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * LaborContractService 单元测试
 * 覆盖：劳务合同 CRUD + DRAFT 状态约束 + 结算/工资计算（BigDecimal）
 */
@ExtendWith(MockitoExtension.class)
class LaborContractServiceTest {

    @Mock
    private BizLaborContractMapper laborContractMapper;

    @Mock
    private BizBudgetDetailMapper budgetDetailMapper;

    @InjectMocks
    private LaborContractService laborContractService;

    private BizLaborContract sampleContract;

    @BeforeEach
    void setUp() {
        sampleContract = new BizLaborContract();
        sampleContract.setId(1L);
        sampleContract.setProjectId(100L);
        sampleContract.setContractName("测试劳务合同");
        sampleContract.setContractCode("LC-2026-001");
        sampleContract.setContractAmount(new BigDecimal("200000.00"));
        sampleContract.setCumulativeSettlement(BigDecimal.ZERO);
        sampleContract.setCumulativePaid(BigDecimal.ZERO);
        sampleContract.setStatus("DRAFT");
    }

    // =====================================================================
    // 分页查询测试
    // =====================================================================

    @Nested
    @DisplayName("分页查询")
    class PageQueryTests {

        @Test
        @DisplayName("按项目ID分页查询 - 返回正确分页结果")
        void page_withProjectId_returnsPageResult() {
            // given
            Page<BizLaborContract> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.singletonList(sampleContract));
            mockPage.setTotal(1L);
            when(laborContractMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizLaborContract> result = laborContractService.page(1, 10, 100L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1L);
            verify(laborContractMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("不传项目ID分页查询 - 返回全部合同")
        void page_withoutProjectId_returnsAll() {
            // given
            Page<BizLaborContract> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);
            when(laborContractMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizLaborContract> result = laborContractService.page(1, 10, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
        }
    }

    // =====================================================================
    // 保存合同测试
    // =====================================================================

    @Nested
    @DisplayName("保存劳务合同")
    class SaveTests {

        @Test
        @DisplayName("保存合同（无预算关联）- DRAFT 初始化 + 累计字段归零")
        void save_noBudget_draftWithZeroDefaults() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setContractAmount(new BigDecimal("200000.00"));
            // budgetId 为 null，不触发预算校验
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when
            laborContractService.save(contract);

            // then
            assertThat(contract.getStatus()).isEqualTo("DRAFT");
            assertThat(contract.getCumulativeSettlement()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(contract.getCumulativePaid()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(laborContractMapper).insert(contract);
        }

        @Test
        @DisplayName("保存合同（有预算关联）- 预算充足时成功")
        void save_withBudget_withinLimit_success() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("150000.00"));

            // 预算 LABOR 类别总额 = 300000
            BizBudgetDetail detail = new BizBudgetDetail();
            detail.setBudgetTotalPrice(new BigDecimal("300000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail));

            // 已有合同金额 = 100000
            BizLaborContract existingContract = new BizLaborContract();
            existingContract.setContractAmount(new BigDecimal("100000.00"));
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(existingContract));

            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when (100000 + 150000 = 250000 <= 300000 预算)
            laborContractService.save(contract);

            // then
            assertThat(contract.getStatus()).isEqualTo("DRAFT");
            verify(laborContractMapper).insert(contract);
        }

        @Test
        @DisplayName("保存合同 - 金额超出预算抛异常")
        void save_exceedsBudget_throwsException() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("500000.00"));

            // 预算 LABOR 类别总额 = 300000
            BizBudgetDetail detail = new BizBudgetDetail();
            detail.setBudgetTotalPrice(new BigDecimal("300000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail));

            // 已有合同 = 0
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when & then (500000 > 300000)
            assertThatThrownBy(() -> laborContractService.save(contract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("劳务合同金额超出预算");

            verify(laborContractMapper, never()).insert(any());
        }

        @Test
        @DisplayName("保存合同 - 状态强制设为 DRAFT（即使传入其他状态）")
        void save_forcesStatusToDraft() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setStatus("EFFECTIVE"); // 尝试绕过状态

            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when
            laborContractService.save(contract);

            // then: 状态被强制覆写为 DRAFT
            assertThat(contract.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("保存合同 - cumulativeSettlement 为 null 时默认设为 ZERO")
        void save_nullCumulativeSettlement_defaultsToZero() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeSettlement(null);
            contract.setCumulativePaid(null);

            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when
            laborContractService.save(contract);

            // then
            assertThat(contract.getCumulativeSettlement()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(contract.getCumulativePaid()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("保存合同 - contractAmount 为 null 时预算校验不报错")
        void save_nullContractAmount_budgetCheckSafe() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(null); // null 金额

            BizBudgetDetail detail = new BizBudgetDetail();
            detail.setBudgetTotalPrice(new BigDecimal("300000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail));
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when (null 视为 0，0 <= 300000)
            laborContractService.save(contract);

            // then
            verify(laborContractMapper).insert(contract);
        }
    }

    // =====================================================================
    // 根据ID查询测试
    // =====================================================================

    @Nested
    @DisplayName("根据ID查询")
    class GetByIdTests {

        @Test
        @DisplayName("查询存在的合同 - 返回合同实体")
        void getById_exists_returnsContract() {
            // given
            when(laborContractMapper.selectById(1L)).thenReturn(sampleContract);

            // when
            BizLaborContract result = laborContractService.getById(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getContractName()).isEqualTo("测试劳务合同");
            assertThat(result.getContractAmount()).isEqualByComparingTo("200000.00");
        }

        @Test
        @DisplayName("查询不存在的合同 - 抛出 BusinessException")
        void getById_notExists_throwsException() {
            // given
            when(laborContractMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> laborContractService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("劳务合同不存在");
        }
    }

    // =====================================================================
    // 更新合同测试
    // =====================================================================

    @Nested
    @DisplayName("更新劳务合同")
    class UpdateTests {

        @Test
        @DisplayName("更新 DRAFT 状态合同 - 成功")
        void update_draftStatus_success() {
            // given
            when(laborContractMapper.selectById(1L)).thenReturn(sampleContract); // status=DRAFT
            when(laborContractMapper.updateById(any(BizLaborContract.class))).thenReturn(1);

            BizLaborContract updateContract = new BizLaborContract();
            updateContract.setId(1L);
            updateContract.setContractName("更新后的合同名称");

            // when
            laborContractService.update(updateContract);

            // then
            verify(laborContractMapper).updateById(updateContract);
        }

        @Test
        @DisplayName("更新非 DRAFT 状态合同 - 抛出异常")
        void update_effectiveStatus_throwsException() {
            // given
            sampleContract.setStatus("EFFECTIVE");
            when(laborContractMapper.selectById(1L)).thenReturn(sampleContract);

            BizLaborContract updateContract = new BizLaborContract();
            updateContract.setId(1L);

            // when & then
            assertThatThrownBy(() -> laborContractService.update(updateContract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可编辑");

            verify(laborContractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("更新不存在的合同 - 抛出异常")
        void update_notExists_throwsException() {
            // given
            when(laborContractMapper.selectById(999L)).thenReturn(null);

            BizLaborContract updateContract = new BizLaborContract();
            updateContract.setId(999L);

            // when & then
            assertThatThrownBy(() -> laborContractService.update(updateContract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("劳务合同不存在");
        }
    }

    // =====================================================================
    // 删除合同测试
    // =====================================================================

    @Nested
    @DisplayName("删除劳务合同")
    class DeleteTests {

        @Test
        @DisplayName("删除 DRAFT 状态合同 - 成功")
        void delete_draftStatus_success() {
            // given
            when(laborContractMapper.selectById(1L)).thenReturn(sampleContract);
            when(laborContractMapper.deleteById(1L)).thenReturn(1);

            // when
            laborContractService.delete(1L);

            // then
            verify(laborContractMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除非 DRAFT 状态合同 - 抛出异常")
        void delete_effectiveStatus_throwsException() {
            // given
            sampleContract.setStatus("EFFECTIVE");
            when(laborContractMapper.selectById(1L)).thenReturn(sampleContract);

            // when & then
            assertThatThrownBy(() -> laborContractService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可删除");

            verify(laborContractMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除不存在的合同 - 抛出异常")
        void delete_notExists_throwsException() {
            // given
            when(laborContractMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> laborContractService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("劳务合同不存在");
        }
    }

    // =====================================================================
    // 提交审批测试
    // =====================================================================

    @Nested
    @DisplayName("提交审批")
    class SubmitTests {

        @Test
        @DisplayName("提交 DRAFT 合同 - 状态变更为 EFFECTIVE")
        void submit_draftContract_becomesEffective() {
            // given
            when(laborContractMapper.selectById(1L)).thenReturn(sampleContract);
            when(laborContractMapper.updateById(any(BizLaborContract.class))).thenReturn(1);

            // when
            laborContractService.submit(1L);

            // then
            verify(laborContractMapper).updateById(argThat(c ->
                    "EFFECTIVE".equals(c.getStatus())
            ));
        }

        @Test
        @DisplayName("提交非 DRAFT 合同 - 抛出异常")
        void submit_effectiveContract_throwsException() {
            // given
            sampleContract.setStatus("EFFECTIVE");
            when(laborContractMapper.selectById(1L)).thenReturn(sampleContract);

            // when & then
            assertThatThrownBy(() -> laborContractService.submit(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可提交");

            verify(laborContractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("提交不存在的合同 - 抛出异常")
        void submit_notExists_throwsException() {
            // given
            when(laborContractMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> laborContractService.submit(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("劳务合同不存在");
        }
    }

    // =====================================================================
    // BigDecimal 精度与预算计算测试
    // =====================================================================

    @Nested
    @DisplayName("BigDecimal 精度与预算计算")
    class BigDecimalPrecisionTests {

        @Test
        @DisplayName("预算校验 - 多条预算明细 BigDecimal 加总精度正确")
        void save_multipleBudgetDetails_precisionCorrect() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("249999.99"));

            // 多条预算明细: 100000.01 + 150000.00 = 250000.01
            BizBudgetDetail detail1 = new BizBudgetDetail();
            detail1.setBudgetTotalPrice(new BigDecimal("100000.01"));
            BizBudgetDetail detail2 = new BizBudgetDetail();
            detail2.setBudgetTotalPrice(new BigDecimal("150000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail1, detail2));

            // 无已有合同
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when (249999.99 <= 250000.01 预算)
            laborContractService.save(contract);

            // then: 没有超限，成功插入
            verify(laborContractMapper).insert(contract);
        }

        @Test
        @DisplayName("预算校验 - 大额金额精度不丢失")
        void save_largeAmount_precisionPreserved() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("999999999.99"));

            // 预算足够大
            BizBudgetDetail detail = new BizBudgetDetail();
            detail.setBudgetTotalPrice(new BigDecimal("1000000000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail));
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when
            laborContractService.save(contract);

            // then: 大额金额保持精度
            verify(laborContractMapper).insert(argThat(c ->
                    new BigDecimal("999999999.99").compareTo(c.getContractAmount()) == 0
            ));
        }

        @Test
        @DisplayName("预算校验 - 已有合同金额含 null 时视为零")
        void save_existingContractNullAmount_treatsAsZero() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("280000.00"));

            BizBudgetDetail detail = new BizBudgetDetail();
            detail.setBudgetTotalPrice(new BigDecimal("300000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail));

            // 已有合同金额为 null
            BizLaborContract existingContract = new BizLaborContract();
            existingContract.setContractAmount(null);
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(existingContract));
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when (0 + 280000 = 280000 <= 300000)
            laborContractService.save(contract);

            // then
            verify(laborContractMapper).insert(contract);
        }

        @Test
        @DisplayName("预算校验 - budgetTotalPrice 为 null 的明细视为零")
        void save_budgetDetailNullPrice_treatsAsZero() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("100.00"));

            // 预算明细: 200000 + null = 200000
            BizBudgetDetail detail1 = new BizBudgetDetail();
            detail1.setBudgetTotalPrice(new BigDecimal("200000.00"));
            BizBudgetDetail detail2 = new BizBudgetDetail();
            detail2.setBudgetTotalPrice(null); // null 视为 0
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail1, detail2));

            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when (100 <= 200000)
            laborContractService.save(contract);

            // then
            verify(laborContractMapper).insert(contract);
        }

        @Test
        @DisplayName("预算校验 - 边界情况：新合同金额恰好等于预算余额")
        void save_exactlyAtBudgetLimit_success() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("100000.00"));

            BizBudgetDetail detail = new BizBudgetDetail();
            detail.setBudgetTotalPrice(new BigDecimal("300000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail));

            // 已有合同 = 200000
            BizLaborContract existing = new BizLaborContract();
            existing.setContractAmount(new BigDecimal("200000.00"));
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(existing));
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when (200000 + 100000 = 300000 = 预算 300000，不超出)
            laborContractService.save(contract);

            // then: 恰好等于预算，不抛异常
            verify(laborContractMapper).insert(contract);
        }

        @Test
        @DisplayName("预算校验 - 超出 0.01 元即拒绝")
        void save_exceedsByOneCent_throwsException() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setBudgetId(1L);
            contract.setContractAmount(new BigDecimal("100000.01"));

            BizBudgetDetail detail = new BizBudgetDetail();
            detail.setBudgetTotalPrice(new BigDecimal("300000.00"));
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(detail));

            // 已有合同 = 200000
            BizLaborContract existing = new BizLaborContract();
            existing.setContractAmount(new BigDecimal("200000.00"));
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(existing));

            // when (200000 + 100000.01 = 300000.01 > 300000 预算)
            assertThatThrownBy(() -> laborContractService.save(contract))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("劳务合同金额超出预算");

            verify(laborContractMapper, never()).insert(any());
        }
    }
}
