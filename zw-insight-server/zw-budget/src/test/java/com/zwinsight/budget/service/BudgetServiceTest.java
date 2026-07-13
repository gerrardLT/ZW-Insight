package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.dto.BudgetCreateRequest;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * BudgetService 单元测试
 * 覆盖：预算 CRUD + DRAFT 状态约束 + 提交审批 + BigDecimal 金额计算
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BizBudgetMapper budgetMapper;

    @Mock
    private BizBudgetDetailMapper budgetDetailMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private BudgetService budgetService;

    private BizBudget sampleBudget;

    @BeforeEach
    void setUp() {
        sampleBudget = new BizBudget();
        sampleBudget.setId(1L);
        sampleBudget.setProjectId(100L);
        sampleBudget.setBudgetType("ORIGINAL");
        sampleBudget.setChangeSeq(0);
        sampleBudget.setTotalAmount(new BigDecimal("500000.00"));
        sampleBudget.setStatus("DRAFT");
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
            Page<BizBudget> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.singletonList(sampleBudget));
            mockPage.setTotal(1L);
            when(budgetMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizBudget> result = budgetService.page(1, 10, 100L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1L);
            verify(budgetMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("不传项目ID分页查询 - 查询全部ORIGINAL预算")
        void page_withoutProjectId_returnsAllOriginalBudgets() {
            // given
            Page<BizBudget> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);
            when(budgetMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizBudget> result = budgetService.page(1, 10, null);

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
        @DisplayName("查询存在的预算 - 返回预算实体")
        void getById_exists_returnsBudget() {
            // given
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);

            // when
            BizBudget result = budgetService.getById(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getProjectId()).isEqualTo(100L);
            assertThat(result.getTotalAmount()).isEqualByComparingTo("500000.00");
        }

        @Test
        @DisplayName("查询不存在的预算 - 抛出BusinessException")
        void getById_notExists_throwsException() {
            // given
            when(budgetMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> budgetService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预算不存在");
        }
    }

    // =====================================================================
    // 保存预算测试
    // =====================================================================

    @Nested
    @DisplayName("保存预算")
    class SaveTests {

        @Test
        @DisplayName("保存ORIGINAL预算 - 项目无已有预算时成功")
        void save_originalBudget_noExisting_success() {
            // given
            BizBudget newBudget = new BizBudget();
            newBudget.setProjectId(200L);
            newBudget.setBudgetType("ORIGINAL");
            newBudget.setTotalAmount(new BigDecimal("1000000.00"));

            when(budgetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(budgetMapper.insert(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.save(newBudget);

            // then
            verify(budgetMapper).insert(argThat(budget ->
                    "DRAFT".equals(budget.getStatus()) &&
                    budget.getChangeSeq() == 0 &&
                    budget.getTotalAmount().compareTo(new BigDecimal("1000000.00")) == 0
            ));
        }

        @Test
        @DisplayName("保存ORIGINAL预算 - 项目已有预算时抛异常")
        void save_originalBudget_alreadyExists_throwsException() {
            // given
            BizBudget newBudget = new BizBudget();
            newBudget.setProjectId(100L);
            newBudget.setBudgetType("ORIGINAL");

            when(budgetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            // when & then
            assertThatThrownBy(() -> budgetService.save(newBudget))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("该项目已存在原始预算，不可重复创建");

            verify(budgetMapper, never()).insert(any());
        }

        @Test
        @DisplayName("保存预算 - totalAmount为null时默认设为ZERO")
        void save_nullTotalAmount_defaultsToZero() {
            // given
            BizBudget newBudget = new BizBudget();
            newBudget.setProjectId(300L);
            newBudget.setBudgetType("ORIGINAL");
            newBudget.setTotalAmount(null);

            when(budgetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(budgetMapper.insert(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.save(newBudget);

            // then
            verify(budgetMapper).insert(argThat(budget ->
                    BigDecimal.ZERO.compareTo(budget.getTotalAmount()) == 0
            ));
        }

        @Test
        @DisplayName("保存预算 - 状态强制设为DRAFT")
        void save_forcesStatusToDraft() {
            // given
            BizBudget newBudget = new BizBudget();
            newBudget.setProjectId(400L);
            newBudget.setBudgetType("ORIGINAL");
            newBudget.setStatus("APPROVED"); // 尝试绕过状态

            when(budgetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(budgetMapper.insert(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.save(newBudget);

            // then: 状态被强制覆写为 DRAFT
            verify(budgetMapper).insert(argThat(budget ->
                    "DRAFT".equals(budget.getStatus())
            ));
        }
    }

    // =====================================================================
    // 从请求DTO创建预算测试
    // =====================================================================

    @Nested
    @DisplayName("从请求DTO创建预算")
    class SaveFromRequestTests {

        @Test
        @DisplayName("通过DTO创建预算 - 属性正确复制")
        void saveFromRequest_copiesPropertiesAndSaves() {
            // given
            BudgetCreateRequest request = new BudgetCreateRequest();
            request.setProjectId(500L);
            request.setBudgetType("ORIGINAL");
            request.setTotalAmount(new BigDecimal("800000.50"));

            when(budgetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(budgetMapper.insert(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.saveFromRequest(request);

            // then
            verify(budgetMapper).insert(argThat(budget ->
                    budget.getProjectId().equals(500L) &&
                    "ORIGINAL".equals(budget.getBudgetType()) &&
                    "DRAFT".equals(budget.getStatus())
            ));
        }
    }

    // =====================================================================
    // 更新预算测试
    // =====================================================================

    @Nested
    @DisplayName("更新预算")
    class UpdateTests {

        @Test
        @DisplayName("更新DRAFT状态预算 - 成功")
        void update_draftStatus_success() {
            // given
            BizBudget updateBudget = new BizBudget();
            updateBudget.setId(1L);
            updateBudget.setTotalAmount(new BigDecimal("600000.00"));

            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget); // status=DRAFT
            when(budgetMapper.updateById(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.update(updateBudget);

            // then
            verify(budgetMapper).updateById(updateBudget);
        }

        @Test
        @DisplayName("更新非DRAFT状态预算 - 抛出异常")
        void update_approvedStatus_throwsException() {
            // given
            sampleBudget.setStatus("APPROVED");
            BizBudget updateBudget = new BizBudget();
            updateBudget.setId(1L);

            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);

            // when & then
            assertThatThrownBy(() -> budgetService.update(updateBudget))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可编辑");

            verify(budgetMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("更新不存在的预算 - 抛出异常")
        void update_notExists_throwsException() {
            // given
            BizBudget updateBudget = new BizBudget();
            updateBudget.setId(999L);

            when(budgetMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> budgetService.update(updateBudget))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预算不存在");
        }
    }

    // =====================================================================
    // 从请求DTO更新预算测试
    // =====================================================================

    @Nested
    @DisplayName("从请求DTO更新预算")
    class UpdateFromRequestTests {

        @Test
        @DisplayName("通过DTO更新预算 - 正确设置ID并调用update")
        void updateFromRequest_setsIdAndDelegates() {
            // given
            BudgetCreateRequest request = new BudgetCreateRequest();
            request.setProjectId(100L);
            request.setBudgetType("ORIGINAL");
            request.setTotalAmount(new BigDecimal("750000.00"));

            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);
            when(budgetMapper.updateById(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.updateFromRequest(1L, request);

            // then
            verify(budgetMapper).updateById(argThat(budget ->
                    budget.getId().equals(1L)
            ));
        }
    }

    // =====================================================================
    // 删除预算测试
    // =====================================================================

    @Nested
    @DisplayName("删除预算")
    class DeleteTests {

        @Test
        @DisplayName("删除DRAFT状态预算 - 成功")
        void delete_draftStatus_success() {
            // given
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);
            when(budgetMapper.deleteById(1L)).thenReturn(1);

            // when
            budgetService.delete(1L);

            // then
            verify(budgetMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除非DRAFT状态预算 - 抛出异常")
        void delete_approvedStatus_throwsException() {
            // given
            sampleBudget.setStatus("APPROVED");
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);

            // when & then
            assertThatThrownBy(() -> budgetService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可删除");

            verify(budgetMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除不存在的预算 - 抛出异常")
        void delete_notExists_throwsException() {
            // given
            when(budgetMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> budgetService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预算不存在");
        }
    }

    // =====================================================================
    // 提交审批测试
    // =====================================================================

    @Nested
    @DisplayName("提交审批")
    class SubmitTests {

        @Test
        @DisplayName("提交DRAFT预算 - 汇总明细金额并更新状态为APPROVED")
        void submit_draftBudget_calculatesAmountAndApproves() {
            // given
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);

            // 两条明细：100000 + 250000.50 = 350000.50
            BizBudgetDetail detail1 = new BizBudgetDetail();
            detail1.setBudgetId(1L);
            detail1.setBudgetTotalPrice(new BigDecimal("100000.00"));

            BizBudgetDetail detail2 = new BizBudgetDetail();
            detail2.setBudgetId(1L);
            detail2.setBudgetTotalPrice(new BigDecimal("250000.50"));

            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(detail1, detail2));

            BizProject project = new BizProject();
            project.setId(100L);
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(budgetMapper.updateById(any(BizBudget.class))).thenReturn(1);
            when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);

            // when
            budgetService.submit(1L);

            // then: 预算状态更新为APPROVED，金额汇总正确
            verify(budgetMapper).updateById(argThat(budget ->
                    "APPROVED".equals(budget.getStatus()) &&
                    new BigDecimal("350000.50").compareTo(budget.getTotalAmount()) == 0
            ));

            // then: 项目预算金额回写
            verify(projectMapper).updateById(argThat(proj ->
                    new BigDecimal("350000.50").compareTo(proj.getBudgetAmount()) == 0
            ));
        }

        @Test
        @DisplayName("提交非DRAFT预算 - 抛出异常")
        void submit_nonDraftBudget_throwsException() {
            // given
            sampleBudget.setStatus("APPROVED");
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);

            // when & then
            assertThatThrownBy(() -> budgetService.submit(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可提交");

            verify(budgetMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("提交不存在的预算 - 抛出异常")
        void submit_notExists_throwsException() {
            // given
            when(budgetMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> budgetService.submit(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预算不存在");
        }

        @Test
        @DisplayName("提交预算无明细 - 总金额为ZERO")
        void submit_noDetails_totalAmountIsZero() {
            // given
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            BizProject project = new BizProject();
            project.setId(100L);
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(budgetMapper.updateById(any(BizBudget.class))).thenReturn(1);
            when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);

            // when
            budgetService.submit(1L);

            // then: 无明细时总额为 0
            verify(budgetMapper).updateById(argThat(budget ->
                    BigDecimal.ZERO.compareTo(budget.getTotalAmount()) == 0 &&
                    "APPROVED".equals(budget.getStatus())
            ));
        }

        @Test
        @DisplayName("提交预算时部分明细金额为null - 视为ZERO参与汇总")
        void submit_detailsWithNullAmount_treatsAsZero() {
            // given
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);

            BizBudgetDetail detail1 = new BizBudgetDetail();
            detail1.setBudgetTotalPrice(new BigDecimal("200000.00"));

            BizBudgetDetail detail2 = new BizBudgetDetail();
            detail2.setBudgetTotalPrice(null); // null 视为 0

            BizBudgetDetail detail3 = new BizBudgetDetail();
            detail3.setBudgetTotalPrice(new BigDecimal("80000.00"));

            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(detail1, detail2, detail3));

            BizProject project = new BizProject();
            project.setId(100L);
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(budgetMapper.updateById(any(BizBudget.class))).thenReturn(1);
            when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);

            // when
            budgetService.submit(1L);

            // then: 200000 + 0 + 80000 = 280000
            verify(budgetMapper).updateById(argThat(budget ->
                    new BigDecimal("280000.00").compareTo(budget.getTotalAmount()) == 0
            ));
        }

        @Test
        @DisplayName("提交预算时项目不存在 - 不回写项目金额但不报错")
        void submit_projectNotFound_skipProjectUpdate() {
            // given
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);
            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(projectMapper.selectById(100L)).thenReturn(null);
            when(budgetMapper.updateById(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.submit(1L);

            // then: 预算状态依然更新
            verify(budgetMapper).updateById(argThat(budget ->
                    "APPROVED".equals(budget.getStatus())
            ));
            // 项目不存在时不调用 projectMapper.updateById
            verify(projectMapper, never()).updateById(any());
        }
    }

    // =====================================================================
    // 按项目ID获取预算测试
    // =====================================================================

    @Nested
    @DisplayName("按项目ID获取预算")
    class GetByProjectTests {

        @Test
        @DisplayName("项目存在预算 - 返回ORIGINAL预算")
        void getByProject_exists_returnsBudget() {
            // given
            when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sampleBudget);

            // when
            BizBudget result = budgetService.getByProject(100L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getProjectId()).isEqualTo(100L);
            assertThat(result.getBudgetType()).isEqualTo("ORIGINAL");
        }

        @Test
        @DisplayName("项目无预算 - 返回null")
        void getByProject_notExists_returnsNull() {
            // given
            when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when
            BizBudget result = budgetService.getByProject(999L);

            // then
            assertThat(result).isNull();
        }
    }

    // =====================================================================
    // BigDecimal 金额精度测试
    // =====================================================================

    @Nested
    @DisplayName("BigDecimal 金额精度")
    class BigDecimalPrecisionTests {

        @Test
        @DisplayName("提交预算 - 明细金额含小数时精度不丢失")
        void submit_decimalPrecision_preserved() {
            // given
            when(budgetMapper.selectById(1L)).thenReturn(sampleBudget);

            BizBudgetDetail detail1 = new BizBudgetDetail();
            detail1.setBudgetTotalPrice(new BigDecimal("123456.78"));

            BizBudgetDetail detail2 = new BizBudgetDetail();
            detail2.setBudgetTotalPrice(new BigDecimal("0.01"));

            BizBudgetDetail detail3 = new BizBudgetDetail();
            detail3.setBudgetTotalPrice(new BigDecimal("999999.99"));

            when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(detail1, detail2, detail3));

            BizProject project = new BizProject();
            project.setId(100L);
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(budgetMapper.updateById(any(BizBudget.class))).thenReturn(1);
            when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);

            // when
            budgetService.submit(1L);

            // then: 123456.78 + 0.01 + 999999.99 = 1123456.78
            verify(budgetMapper).updateById(argThat(budget ->
                    new BigDecimal("1123456.78").compareTo(budget.getTotalAmount()) == 0
            ));
        }

        @Test
        @DisplayName("保存预算 - 大额预算金额正确存储")
        void save_largeAmount_preserved() {
            // given
            BizBudget newBudget = new BizBudget();
            newBudget.setProjectId(600L);
            newBudget.setBudgetType("ORIGINAL");
            newBudget.setTotalAmount(new BigDecimal("999999999.99"));

            when(budgetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(budgetMapper.insert(any(BizBudget.class))).thenReturn(1);

            // when
            budgetService.save(newBudget);

            // then: 大额金额精度保持
            verify(budgetMapper).insert(argThat(budget ->
                    new BigDecimal("999999999.99").compareTo(budget.getTotalAmount()) == 0
            ));
        }
    }
}
