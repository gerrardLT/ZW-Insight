package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * LaborContractService 单元测试
 * 覆盖：劳务合同 CRUD + DRAFT 状态约束
 * <p>预算校验已统一由 @BudgetCheck 切面（BudgetControlAspect + BudgetControlConfigService）承担，
 * 相关校验逻辑在 zw-budget 模块测试中覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class LaborContractServiceTest {

    @Mock
    private BizLaborContractMapper laborContractMapper;

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
            PageResult<BizLaborContract> result = laborContractService.page(1, 10, 100L, null, null, null);

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
            PageResult<BizLaborContract> result = laborContractService.page(1, 10, null, null, null, null);

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
        @DisplayName("保存合同 - DRAFT 初始化 + 累计字段归零")
        void save_draftWithZeroDefaults() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setContractAmount(new BigDecimal("200000.00"));
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
        @DisplayName("保存合同 - 大额金额精度不丢失")
        void save_largeAmount_precisionPreserved() {
            // given
            BizLaborContract contract = new BizLaborContract();
            contract.setProjectId(100L);
            contract.setContractAmount(new BigDecimal("999999999.99"));
            when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

            // when
            laborContractService.save(contract);

            // then: 大额金额保持精度
            verify(laborContractMapper).insert(argThat(c ->
                    new BigDecimal("999999999.99").compareTo(c.getContractAmount()) == 0
            ));
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
}
