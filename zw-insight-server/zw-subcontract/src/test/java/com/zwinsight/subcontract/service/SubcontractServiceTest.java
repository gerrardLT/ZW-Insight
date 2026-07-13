package com.zwinsight.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractOutputReport;
import com.zwinsight.subcontract.domain.BizSubcontractRewardPunish;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.domain.BizSubcontractSettlementDetail;
import com.zwinsight.subcontract.dto.SubcontractSettlementCreateRequest;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailDTO;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailVO;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractOutputReportMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractRewardPunishMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
import com.zwinsight.subcontract.mapper.SubcontractSettlementDetailMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 分包模块 Service 单元测试
 * 覆盖：分包合同 CRUD + DRAFT 状态约束 + 提交审批 + 产值/结算计算
 */
@ExtendWith(MockitoExtension.class)
class SubcontractServiceTest {

    // =====================================================================
    // SubcontractService 测试
    // =====================================================================

    @Nested
    @DisplayName("分包合同服务 - SubcontractService")
    class SubcontractServiceTests {

        @Mock
        private BizSubcontractMapper subcontractMapper;

        @Mock
        private BizBudgetDetailMapper budgetDetailMapper;

        @InjectMocks
        private SubcontractService subcontractService;

        private BizSubcontract sampleContract;

        @BeforeEach
        void setUp() {
            sampleContract = new BizSubcontract();
            sampleContract.setId(1L);
            sampleContract.setProjectId(100L);
            sampleContract.setContractCode("SC-2026-001");
            sampleContract.setContractName("土方工程分包");
            sampleContract.setSubcontractor("测试分包商");
            sampleContract.setContractAmount(new BigDecimal("500000.00"));
            sampleContract.setCumulativeSettlement(BigDecimal.ZERO);
            sampleContract.setCumulativePaid(BigDecimal.ZERO);
            sampleContract.setStatus("DRAFT");
        }

        @Nested
        @DisplayName("分页查询")
        class PageQueryTests {

            @Test
            @DisplayName("按项目ID分页查询 - 返回正确分页结果")
            void page_withProjectId_returnsPageResult() {
                // given
                Page<BizSubcontract> mockPage = new Page<>(1, 10);
                mockPage.setRecords(Collections.singletonList(sampleContract));
                mockPage.setTotal(1L);
                when(subcontractMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                        .thenReturn(mockPage);

                // when
                PageResult<BizSubcontract> result = subcontractService.page(1, 10, 100L);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getRecords()).hasSize(1);
                assertThat(result.getTotal()).isEqualTo(1L);
                verify(subcontractMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
            }

            @Test
            @DisplayName("不传项目ID分页查询 - 返回全部结果")
            void page_withoutProjectId_returnsAll() {
                // given
                Page<BizSubcontract> mockPage = new Page<>(1, 10);
                mockPage.setRecords(Collections.emptyList());
                mockPage.setTotal(0L);
                when(subcontractMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                        .thenReturn(mockPage);

                // when
                PageResult<BizSubcontract> result = subcontractService.page(1, 10, null);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getRecords()).isEmpty();
            }
        }

        @Nested
        @DisplayName("根据ID查询")
        class GetByIdTests {

            @Test
            @DisplayName("查询存在的分包合同 - 返回实体")
            void getById_exists_returnsContract() {
                // given
                when(subcontractMapper.selectById(1L)).thenReturn(sampleContract);

                // when
                BizSubcontract result = subcontractService.getById(1L);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getContractName()).isEqualTo("土方工程分包");
                assertThat(result.getContractAmount()).isEqualByComparingTo("500000.00");
            }

            @Test
            @DisplayName("查询不存在的分包合同 - 抛出BusinessException")
            void getById_notExists_throwsException() {
                // given
                when(subcontractMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> subcontractService.getById(999L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("分包合同不存在");
            }
        }

        @Nested
        @DisplayName("保存分包合同")
        class SaveTests {

            @Test
            @DisplayName("正常保存分包合同 - 状态设为DRAFT、累计金额初始化为ZERO")
            void save_normalContract_setsDefaultValues() {
                // given
                BizSubcontract newContract = new BizSubcontract();
                newContract.setProjectId(200L);
                newContract.setContractName("混凝土浇筑分包");
                newContract.setContractAmount(new BigDecimal("300000.00"));

                when(subcontractMapper.insert(any(BizSubcontract.class))).thenReturn(1);

                // when
                subcontractService.save(newContract);

                // then
                verify(subcontractMapper).insert(argThat(contract ->
                        "DRAFT".equals(contract.getStatus()) &&
                        BigDecimal.ZERO.compareTo(contract.getCumulativeSettlement()) == 0 &&
                        BigDecimal.ZERO.compareTo(contract.getCumulativePaid()) == 0
                ));
            }

            @Test
            @DisplayName("保存合同带预算控制 - 金额未超预算时成功")
            void save_withBudgetCheck_withinBudget_success() {
                // given
                BizSubcontract newContract = new BizSubcontract();
                newContract.setProjectId(100L);
                newContract.setBudgetId(10L);
                newContract.setContractAmount(new BigDecimal("200000.00"));

                // 预算总额 500000
                BizBudgetDetail budgetDetail = new BizBudgetDetail();
                budgetDetail.setBudgetTotalPrice(new BigDecimal("500000.00"));
                when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                        .thenReturn(Collections.singletonList(budgetDetail));

                // 已有合同使用 100000
                BizSubcontract existingContract = new BizSubcontract();
                existingContract.setContractAmount(new BigDecimal("100000.00"));
                when(subcontractMapper.selectList(any(LambdaQueryWrapper.class)))
                        .thenReturn(Collections.singletonList(existingContract));

                when(subcontractMapper.insert(any(BizSubcontract.class))).thenReturn(1);

                // when
                subcontractService.save(newContract);

                // then: 100000 + 200000 = 300000 < 500000，允许保存
                verify(subcontractMapper).insert(any(BizSubcontract.class));
            }

            @Test
            @DisplayName("保存合同带预算控制 - 金额超出预算时抛异常")
            void save_withBudgetCheck_exceedsBudget_throwsException() {
                // given
                BizSubcontract newContract = new BizSubcontract();
                newContract.setProjectId(100L);
                newContract.setBudgetId(10L);
                newContract.setContractAmount(new BigDecimal("450000.00"));

                // 预算总额 500000
                BizBudgetDetail budgetDetail = new BizBudgetDetail();
                budgetDetail.setBudgetTotalPrice(new BigDecimal("500000.00"));
                when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                        .thenReturn(Collections.singletonList(budgetDetail));

                // 已有合同使用 200000
                BizSubcontract existingContract = new BizSubcontract();
                existingContract.setContractAmount(new BigDecimal("200000.00"));
                when(subcontractMapper.selectList(any(LambdaQueryWrapper.class)))
                        .thenReturn(Collections.singletonList(existingContract));

                // when & then: 200000 + 450000 = 650000 > 500000
                assertThatThrownBy(() -> subcontractService.save(newContract))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("分包合同金额超出预算");

                verify(subcontractMapper, never()).insert(any());
            }

            @Test
            @DisplayName("保存合同 - 累计结算/付款为null时初始化为ZERO")
            void save_nullCumulativeFields_initializedToZero() {
                // given
                BizSubcontract newContract = new BizSubcontract();
                newContract.setProjectId(300L);
                newContract.setContractAmount(new BigDecimal("100000.00"));
                newContract.setCumulativeSettlement(null);
                newContract.setCumulativePaid(null);

                when(subcontractMapper.insert(any(BizSubcontract.class))).thenReturn(1);

                // when
                subcontractService.save(newContract);

                // then
                verify(subcontractMapper).insert(argThat(contract ->
                        BigDecimal.ZERO.compareTo(contract.getCumulativeSettlement()) == 0 &&
                        BigDecimal.ZERO.compareTo(contract.getCumulativePaid()) == 0
                ));
            }
        }

        @Nested
        @DisplayName("更新分包合同")
        class UpdateTests {

            @Test
            @DisplayName("更新DRAFT状态合同 - 成功")
            void update_draftStatus_success() {
                // given
                BizSubcontract updateContract = new BizSubcontract();
                updateContract.setId(1L);
                updateContract.setContractName("更新后的合同名");

                when(subcontractMapper.selectById(1L)).thenReturn(sampleContract);
                when(subcontractMapper.updateById(any(BizSubcontract.class))).thenReturn(1);

                // when
                subcontractService.update(updateContract);

                // then
                verify(subcontractMapper).updateById(updateContract);
            }

            @Test
            @DisplayName("更新非DRAFT状态合同 - 抛出异常")
            void update_effectiveStatus_throwsException() {
                // given
                sampleContract.setStatus("EFFECTIVE");
                BizSubcontract updateContract = new BizSubcontract();
                updateContract.setId(1L);

                when(subcontractMapper.selectById(1L)).thenReturn(sampleContract);

                // when & then
                assertThatThrownBy(() -> subcontractService.update(updateContract))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可编辑");

                verify(subcontractMapper, never()).updateById(any());
            }

            @Test
            @DisplayName("更新不存在的合同 - 抛出异常")
            void update_notExists_throwsException() {
                // given
                BizSubcontract updateContract = new BizSubcontract();
                updateContract.setId(999L);

                when(subcontractMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> subcontractService.update(updateContract))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("分包合同不存在");
            }
        }

        @Nested
        @DisplayName("删除分包合同")
        class DeleteTests {

            @Test
            @DisplayName("删除DRAFT状态合同 - 成功")
            void delete_draftStatus_success() {
                // given
                when(subcontractMapper.selectById(1L)).thenReturn(sampleContract);
                when(subcontractMapper.deleteById(1L)).thenReturn(1);

                // when
                subcontractService.delete(1L);

                // then
                verify(subcontractMapper).deleteById(1L);
            }

            @Test
            @DisplayName("删除非DRAFT状态合同 - 抛出异常")
            void delete_effectiveStatus_throwsException() {
                // given
                sampleContract.setStatus("EFFECTIVE");
                when(subcontractMapper.selectById(1L)).thenReturn(sampleContract);

                // when & then
                assertThatThrownBy(() -> subcontractService.delete(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可删除");

                verify(subcontractMapper, never()).deleteById(anyLong());
            }

            @Test
            @DisplayName("删除不存在的合同 - 抛出异常")
            void delete_notExists_throwsException() {
                // given
                when(subcontractMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> subcontractService.delete(999L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("分包合同不存在");
            }
        }

        @Nested
        @DisplayName("提交审批")
        class SubmitTests {

            @Test
            @DisplayName("提交DRAFT合同 - 状态变为EFFECTIVE")
            void submit_draftContract_becomesEffective() {
                // given
                when(subcontractMapper.selectById(1L)).thenReturn(sampleContract);
                when(subcontractMapper.updateById(any(BizSubcontract.class))).thenReturn(1);

                // when
                subcontractService.submit(1L);

                // then
                verify(subcontractMapper).updateById(argThat(contract ->
                        "EFFECTIVE".equals(contract.getStatus())
                ));
            }

            @Test
            @DisplayName("提交非DRAFT合同 - 抛出异常")
            void submit_nonDraftContract_throwsException() {
                // given
                sampleContract.setStatus("EFFECTIVE");
                when(subcontractMapper.selectById(1L)).thenReturn(sampleContract);

                // when & then
                assertThatThrownBy(() -> subcontractService.submit(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可提交");

                verify(subcontractMapper, never()).updateById(any());
            }

            @Test
            @DisplayName("提交不存在的合同 - 抛出异常")
            void submit_notExists_throwsException() {
                // given
                when(subcontractMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> subcontractService.submit(999L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("分包合同不存在");
            }
        }
    }

    // =====================================================================
    // SubcontractOutputService 测试（产值报告）
    // =====================================================================

    @Nested
    @DisplayName("分包产值服务 - SubcontractOutputService")
    class SubcontractOutputServiceTests {

        @Mock
        private BizSubcontractOutputReportMapper outputReportMapper;

        @Mock
        private BizSubcontractMapper subcontractMapper;

        @InjectMocks
        private SubcontractOutputService outputService;

        private BizSubcontractOutputReport sampleReport;

        @BeforeEach
        void setUp() {
            sampleReport = new BizSubcontractOutputReport();
            sampleReport.setId(1L);
            sampleReport.setProjectId(100L);
            sampleReport.setContractId(10L);
            sampleReport.setCurrentOutput(new BigDecimal("80000.00"));
            sampleReport.setCumulativeOutput(new BigDecimal("200000.00"));
            sampleReport.setStatus("DRAFT");
        }

        @Nested
        @DisplayName("分页查询")
        class PageQueryTests {

            @Test
            @DisplayName("按项目和合同ID查询 - 返回分页结果")
            void page_withFilters_returnsPageResult() {
                // given
                Page<BizSubcontractOutputReport> mockPage = new Page<>(1, 10);
                mockPage.setRecords(Collections.singletonList(sampleReport));
                mockPage.setTotal(1L);
                when(outputReportMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                        .thenReturn(mockPage);

                // when
                PageResult<BizSubcontractOutputReport> result = outputService.page(1, 10, 100L, 10L);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getRecords()).hasSize(1);
            }
        }

        @Nested
        @DisplayName("保存产值报告")
        class SaveTests {

            @Test
            @DisplayName("保存产值报告 - 状态强制设为DRAFT")
            void save_setsStatusToDraft() {
                // given
                BizSubcontractOutputReport report = new BizSubcontractOutputReport();
                report.setProjectId(100L);
                report.setContractId(10L);
                report.setCurrentOutput(new BigDecimal("50000.00"));
                report.setStatus("APPROVED"); // 尝试绕过

                when(outputReportMapper.insert(any(BizSubcontractOutputReport.class))).thenReturn(1);

                // when
                outputService.save(report);

                // then
                verify(outputReportMapper).insert(argThat(r ->
                        "DRAFT".equals(r.getStatus())
                ));
            }
        }

        @Nested
        @DisplayName("更新产值报告")
        class UpdateTests {

            @Test
            @DisplayName("更新DRAFT状态报告 - 成功")
            void update_draftStatus_success() {
                // given
                BizSubcontractOutputReport update = new BizSubcontractOutputReport();
                update.setId(1L);
                update.setCurrentOutput(new BigDecimal("90000.00"));

                when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
                when(outputReportMapper.updateById(any(BizSubcontractOutputReport.class))).thenReturn(1);

                // when
                outputService.update(update);

                // then
                verify(outputReportMapper).updateById(update);
            }

            @Test
            @DisplayName("更新非DRAFT状态报告 - 抛出异常")
            void update_approvedStatus_throwsException() {
                // given
                sampleReport.setStatus("APPROVED");
                BizSubcontractOutputReport update = new BizSubcontractOutputReport();
                update.setId(1L);

                when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);

                // when & then
                assertThatThrownBy(() -> outputService.update(update))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可编辑");
            }

            @Test
            @DisplayName("更新不存在的报告 - 抛出异常")
            void update_notExists_throwsException() {
                // given
                BizSubcontractOutputReport update = new BizSubcontractOutputReport();
                update.setId(999L);

                when(outputReportMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> outputService.update(update))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("产值报告不存在");
            }
        }

        @Nested
        @DisplayName("删除产值报告")
        class DeleteTests {

            @Test
            @DisplayName("删除DRAFT状态报告 - 成功")
            void delete_draftStatus_success() {
                // given
                when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
                when(outputReportMapper.deleteById(1L)).thenReturn(1);

                // when
                outputService.delete(1L);

                // then
                verify(outputReportMapper).deleteById(1L);
            }

            @Test
            @DisplayName("删除非DRAFT状态报告 - 抛出异常")
            void delete_approvedStatus_throwsException() {
                // given
                sampleReport.setStatus("APPROVED");
                when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);

                // when & then
                assertThatThrownBy(() -> outputService.delete(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可删除");
            }
        }

        @Nested
        @DisplayName("提交产值报告")
        class SubmitTests {

            @Test
            @DisplayName("提交DRAFT报告 - 状态变为APPROVED")
            void submit_draftReport_becomesApproved() {
                // given
                when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
                when(outputReportMapper.updateById(any(BizSubcontractOutputReport.class))).thenReturn(1);

                // when
                outputService.submit(1L);

                // then
                verify(outputReportMapper).updateById(argThat(r ->
                        "APPROVED".equals(r.getStatus())
                ));
            }

            @Test
            @DisplayName("提交非DRAFT报告 - 抛出异常")
            void submit_nonDraftReport_throwsException() {
                // given
                sampleReport.setStatus("APPROVED");
                when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);

                // when & then
                assertThatThrownBy(() -> outputService.submit(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可提交");
            }

            @Test
            @DisplayName("提交不存在的报告 - 抛出异常")
            void submit_notExists_throwsException() {
                // given
                when(outputReportMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> outputService.submit(999L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("产值报告不存在");
            }
        }
    }

    // =====================================================================
    // SubcontractSettlementService 测试（结算服务）
    // =====================================================================

    @Nested
    @DisplayName("分包结算服务 - SubcontractSettlementService")
    class SubcontractSettlementServiceTests {

        @Mock
        private BizSubcontractSettlementMapper settlementMapper;

        @Mock
        private SubcontractSettlementDetailMapper detailMapper;

        @Mock
        private BizSubcontractMapper subcontractMapper;

        @Mock
        private BizProjectMapper projectMapper;

        @InjectMocks
        private SubcontractSettlementService settlementService;

        private BizSubcontractSettlement sampleSettlement;
        private BizSubcontract sampleContract;

        @BeforeEach
        void setUp() {
            sampleSettlement = new BizSubcontractSettlement();
            sampleSettlement.setId(1L);
            sampleSettlement.setProjectId(100L);
            sampleSettlement.setContractId(10L);
            sampleSettlement.setSettlementAmount(new BigDecimal("150000.00"));
            sampleSettlement.setStatus("DRAFT");

            sampleContract = new BizSubcontract();
            sampleContract.setId(10L);
            sampleContract.setProjectId(100L);
            sampleContract.setContractAmount(new BigDecimal("500000.00"));
            sampleContract.setCumulativeSettlement(new BigDecimal("100000.00"));
        }

        @Nested
        @DisplayName("分页查询")
        class PageQueryTests {

            @Test
            @DisplayName("按项目和合同ID查询 - 返回分页结果")
            void page_withFilters_returnsPageResult() {
                // given
                Page<BizSubcontractSettlement> mockPage = new Page<>(1, 10);
                mockPage.setRecords(Collections.singletonList(sampleSettlement));
                mockPage.setTotal(1L);
                when(settlementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                        .thenReturn(mockPage);

                // when
                PageResult<BizSubcontractSettlement> result = settlementService.page(1, 10, 100L, 10L);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getRecords()).hasSize(1);
                assertThat(result.getTotal()).isEqualTo(1L);
            }
        }

        @Nested
        @DisplayName("简单保存结算记录")
        class SimpleSaveTests {

            @Test
            @DisplayName("保存结算记录 - 状态设为DRAFT")
            void save_setsStatusToDraft() {
                // given
                BizSubcontractSettlement settlement = new BizSubcontractSettlement();
                settlement.setContractId(10L);
                settlement.setSettlementAmount(new BigDecimal("80000.00"));

                when(settlementMapper.insert(any(BizSubcontractSettlement.class))).thenReturn(1);

                // when
                settlementService.save(settlement);

                // then
                verify(settlementMapper).insert(argThat(s ->
                        "DRAFT".equals(s.getStatus())
                ));
            }
        }

        @Nested
        @DisplayName("创建结算单（含明细行）")
        class CreateSettlementTests {

            @Test
            @DisplayName("创建结算单 - 明细行金额正确计算 quantity × unitPrice")
            void createSettlement_calculatesDetailAmountsCorrectly() {
                // given
                SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
                request.setContractId(10L);
                request.setProjectId(100L);

                SubcontractSettlementDetailDTO detail1 = new SubcontractSettlementDetailDTO();
                detail1.setItemName("土方开挖");
                detail1.setQuantity(new BigDecimal("100.00"));
                detail1.setUnitPrice(new BigDecimal("50.00"));

                SubcontractSettlementDetailDTO detail2 = new SubcontractSettlementDetailDTO();
                detail2.setItemName("砌墙");
                detail2.setQuantity(new BigDecimal("200.50"));
                detail2.setUnitPrice(new BigDecimal("80.00"));

                request.setDetails(Arrays.asList(detail1, detail2));

                when(settlementMapper.insert(any(BizSubcontractSettlement.class))).thenReturn(1);
                when(detailMapper.insert(any(BizSubcontractSettlementDetail.class))).thenReturn(1);
                when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);

                // when
                Long id = settlementService.createSettlement(request);

                // then: 行金额: 100×50=5000, 200.50×80=16040 => 总金额 = 21040
                verify(settlementMapper).updateById(argThat(s ->
                        new BigDecimal("21040.00").compareTo(s.getSettlementAmount()) == 0
                ));
                verify(detailMapper, times(2)).insert(any(BizSubcontractSettlementDetail.class));
            }

            @Test
            @DisplayName("创建结算单 - 明细行金额HALF_UP舍入精度正确")
            void createSettlement_halfUpRounding_correct() {
                // given
                SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
                request.setContractId(10L);
                request.setProjectId(100L);

                // 100.333 × 30.567 = 3067.525111 → HALF_UP → 3067.53
                SubcontractSettlementDetailDTO detail = new SubcontractSettlementDetailDTO();
                detail.setItemName("精密计量项");
                detail.setQuantity(new BigDecimal("100.333"));
                detail.setUnitPrice(new BigDecimal("30.567"));

                request.setDetails(Collections.singletonList(detail));

                when(settlementMapper.insert(any(BizSubcontractSettlement.class))).thenReturn(1);
                when(detailMapper.insert(any(BizSubcontractSettlementDetail.class))).thenReturn(1);
                when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);

                // when
                settlementService.createSettlement(request);

                // then: 100.333 × 30.567 = 3067.525511 → 3067.53 (HALF_UP)
                verify(detailMapper).insert(argThat(d ->
                        new BigDecimal("3067.53").compareTo(d.getAmount()) == 0
                ));
                verify(settlementMapper).updateById(argThat(s ->
                        new BigDecimal("3067.53").compareTo(s.getSettlementAmount()) == 0
                ));
            }
        }

        @Nested
        @DisplayName("更新结算单")
        class UpdateSettlementTests {

            @Test
            @DisplayName("更新DRAFT状态结算 - 成功")
            void update_draftStatus_success() {
                // given
                BizSubcontractSettlement update = new BizSubcontractSettlement();
                update.setId(1L);
                update.setSettlementAmount(new BigDecimal("160000.00"));

                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);

                // when
                settlementService.update(update);

                // then
                verify(settlementMapper).updateById(update);
            }

            @Test
            @DisplayName("更新非DRAFT状态结算 - 抛出异常")
            void update_approvedStatus_throwsException() {
                // given
                sampleSettlement.setStatus("APPROVED");
                BizSubcontractSettlement update = new BizSubcontractSettlement();
                update.setId(1L);

                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

                // when & then
                assertThatThrownBy(() -> settlementService.update(update))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可编辑");
            }

            @Test
            @DisplayName("更新不存在的结算 - 抛出异常")
            void update_notExists_throwsException() {
                // given
                BizSubcontractSettlement update = new BizSubcontractSettlement();
                update.setId(999L);

                when(settlementMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> settlementService.update(update))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("结算记录不存在");
            }
        }

        @Nested
        @DisplayName("更新结算单（含明细行重算）")
        class UpdateSettlementWithDetailsTests {

            @Test
            @DisplayName("更新结算单明细 - 删除旧明细、插入新明细、重算总金额")
            void updateSettlement_replacesDetailsAndRecalculates() {
                // given
                SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
                request.setContractId(10L);
                request.setProjectId(100L);

                SubcontractSettlementDetailDTO newDetail = new SubcontractSettlementDetailDTO();
                newDetail.setItemName("新项目");
                newDetail.setQuantity(new BigDecimal("50.00"));
                newDetail.setUnitPrice(new BigDecimal("120.00"));
                request.setDetails(Collections.singletonList(newDetail));

                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(detailMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);
                when(detailMapper.insert(any(BizSubcontractSettlementDetail.class))).thenReturn(1);
                when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);

                // when
                settlementService.updateSettlement(1L, request);

                // then: 50 × 120 = 6000
                verify(detailMapper).delete(any(LambdaQueryWrapper.class));
                verify(detailMapper).insert(any(BizSubcontractSettlementDetail.class));
                verify(settlementMapper).updateById(argThat(s ->
                        new BigDecimal("6000.00").compareTo(s.getSettlementAmount()) == 0
                ));
            }

            @Test
            @DisplayName("更新非DRAFT结算单 - 抛出异常")
            void updateSettlement_nonDraft_throwsException() {
                // given
                sampleSettlement.setStatus("APPROVED");
                SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
                request.setDetails(Collections.emptyList());

                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

                // when & then
                assertThatThrownBy(() -> settlementService.updateSettlement(1L, request))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可编辑");
            }
        }

        @Nested
        @DisplayName("删除结算单")
        class DeleteTests {

            @Test
            @DisplayName("删除DRAFT结算 - 同时删除明细行")
            void delete_draftStatus_deletesWithDetails() {
                // given
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(detailMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);
                when(settlementMapper.deleteById(1L)).thenReturn(1);

                // when
                settlementService.delete(1L);

                // then
                verify(detailMapper).delete(any(LambdaQueryWrapper.class));
                verify(settlementMapper).deleteById(1L);
            }

            @Test
            @DisplayName("删除非DRAFT结算 - 抛出异常")
            void delete_approvedStatus_throwsException() {
                // given
                sampleSettlement.setStatus("APPROVED");
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

                // when & then
                assertThatThrownBy(() -> settlementService.delete(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可删除");

                verify(settlementMapper, never()).deleteById(anyLong());
            }

            @Test
            @DisplayName("删除不存在的结算 - 抛出异常")
            void delete_notExists_throwsException() {
                // given
                when(settlementMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> settlementService.delete(999L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("结算记录不存在");
            }
        }

        @Nested
        @DisplayName("提交结算审批")
        class SubmitTests {

            @Test
            @DisplayName("提交结算 - 累计金额未超合同时成功并回写累计结算和项目支出")
            void submit_withinContractLimit_success() {
                // given: 合同500000, 累计已结算100000, 本次150000 => 250000 < 500000
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(subcontractMapper.selectById(10L)).thenReturn(sampleContract);

                BizProject project = new BizProject();
                project.setId(100L);
                project.setTotalExpense(new BigDecimal("200000.00"));
                when(projectMapper.selectById(100L)).thenReturn(project);
                when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);
                when(subcontractMapper.updateById(any(BizSubcontract.class))).thenReturn(1);
                when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);

                // when
                settlementService.submit(1L);

                // then: 状态变 APPROVED
                verify(settlementMapper).updateById(argThat(s ->
                        "APPROVED".equals(s.getStatus())
                ));
                // 合同累计结算 = 100000 + 150000 = 250000
                verify(subcontractMapper).updateById(argThat(c ->
                        new BigDecimal("250000.00").compareTo(c.getCumulativeSettlement()) == 0
                ));
                // 项目总支出 = 200000 + 150000 = 350000
                verify(projectMapper).updateById(argThat(p ->
                        new BigDecimal("350000.00").compareTo(p.getTotalExpense()) == 0
                ));
            }

            @Test
            @DisplayName("提交结算 - 累计金额超合同时抛异常")
            void submit_exceedsContractLimit_throwsException() {
                // given: 合同500000, 累计已结算100000, 本次450000 => 550000 > 500000
                sampleSettlement.setSettlementAmount(new BigDecimal("450000.00"));
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(subcontractMapper.selectById(10L)).thenReturn(sampleContract);

                // when & then
                assertThatThrownBy(() -> settlementService.submit(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("结算金额超出合同金额限制");

                verify(settlementMapper, never()).updateById(any());
            }

            @Test
            @DisplayName("提交结算 - 合同不存在时抛异常")
            void submit_contractNotFound_throwsException() {
                // given
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(subcontractMapper.selectById(10L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> settlementService.submit(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("分包合同不存在");
            }

            @Test
            @DisplayName("提交非DRAFT结算 - 抛出异常")
            void submit_nonDraftSettlement_throwsException() {
                // given
                sampleSettlement.setStatus("APPROVED");
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

                // when & then
                assertThatThrownBy(() -> settlementService.submit(1L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("仅草稿状态可提交");
            }

            @Test
            @DisplayName("提交结算 - 项目不存在时不回写项目支出但不报错")
            void submit_projectNotFound_skipsProjectUpdate() {
                // given: 合同限额内
                sampleSettlement.setSettlementAmount(new BigDecimal("50000.00"));
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(subcontractMapper.selectById(10L)).thenReturn(sampleContract);
                when(projectMapper.selectById(100L)).thenReturn(null);
                when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);
                when(subcontractMapper.updateById(any(BizSubcontract.class))).thenReturn(1);

                // when
                settlementService.submit(1L);

                // then: 结算状态更新
                verify(settlementMapper).updateById(argThat(s ->
                        "APPROVED".equals(s.getStatus())
                ));
                // 项目不存在时不回写
                verify(projectMapper, never()).updateById(any());
            }

            @Test
            @DisplayName("提交结算 - 合同累计结算为null时视为ZERO")
            void submit_nullCumulativeSettlement_treatedAsZero() {
                // given
                sampleContract.setCumulativeSettlement(null);
                sampleSettlement.setSettlementAmount(new BigDecimal("100000.00"));

                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
                when(subcontractMapper.selectById(10L)).thenReturn(sampleContract);

                BizProject project = new BizProject();
                project.setId(100L);
                project.setTotalExpense(BigDecimal.ZERO);
                when(projectMapper.selectById(100L)).thenReturn(project);
                when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);
                when(subcontractMapper.updateById(any(BizSubcontract.class))).thenReturn(1);
                when(projectMapper.updateById(any(BizProject.class))).thenReturn(1);

                // when
                settlementService.submit(1L);

                // then: 0 + 100000 = 100000 <= 500000
                verify(subcontractMapper).updateById(argThat(c ->
                        new BigDecimal("100000.00").compareTo(c.getCumulativeSettlement()) == 0
                ));
            }
        }

        @Nested
        @DisplayName("获取结算详情VO")
        class GetDetailVOTests {

            @Test
            @DisplayName("获取详情 - 含合同信息和明细行")
            void getDetailVO_returnsCompleteVO() {
                // given
                when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

                BizSubcontractSettlementDetail detail = new BizSubcontractSettlementDetail();
                detail.setItemName("项目A");
                detail.setAmount(new BigDecimal("5000.00"));
                when(detailMapper.selectList(any(LambdaQueryWrapper.class)))
                        .thenReturn(Collections.singletonList(detail));

                BizSubcontract contract = new BizSubcontract();
                contract.setId(10L);
                contract.setContractCode("SC-001");
                contract.setContractName("测试合同");
                contract.setSubcontractor("测试分包商");
                when(subcontractMapper.selectById(10L)).thenReturn(contract);

                // when
                SubcontractSettlementDetailVO vo = settlementService.getDetailVO(1L);

                // then
                assertThat(vo).isNotNull();
                assertThat(vo.getSettlement()).isEqualTo(sampleSettlement);
                assertThat(vo.getDetails()).hasSize(1);
                assertThat(vo.getContractCode()).isEqualTo("SC-001");
                assertThat(vo.getContractName()).isEqualTo("测试合同");
                assertThat(vo.getSubcontractor()).isEqualTo("测试分包商");
            }

            @Test
            @DisplayName("获取不存在的结算详情 - 抛出异常")
            void getDetailVO_notExists_throwsException() {
                // given
                when(settlementMapper.selectById(999L)).thenReturn(null);

                // when & then
                assertThatThrownBy(() -> settlementService.getDetailVO(999L))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("结算记录不存在");
            }
        }
    }

    // =====================================================================
    // SubcontractRewardPunishService 测试（奖罚服务）
    // =====================================================================

    @Nested
    @DisplayName("分包奖罚服务 - SubcontractRewardPunishService")
    class SubcontractRewardPunishServiceTests {

        @Mock
        private BizSubcontractRewardPunishMapper rewardPunishMapper;

        @InjectMocks
        private SubcontractRewardPunishService rewardPunishService;

        @Nested
        @DisplayName("分页查询")
        class PageQueryTests {

            @Test
            @DisplayName("按项目和合同ID查询 - 返回分页结果")
            void page_withFilters_returnsPageResult() {
                // given
                Page<BizSubcontractRewardPunish> mockPage = new Page<>(1, 10);
                BizSubcontractRewardPunish record = new BizSubcontractRewardPunish();
                record.setId(1L);
                mockPage.setRecords(Collections.singletonList(record));
                mockPage.setTotal(1L);
                when(rewardPunishMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                        .thenReturn(mockPage);

                // when
                PageResult<BizSubcontractRewardPunish> result = rewardPunishService.page(1, 10, 100L, 10L);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getRecords()).hasSize(1);
            }
        }

        @Nested
        @DisplayName("保存奖罚记录")
        class SaveTests {

            @Test
            @DisplayName("保存奖罚记录 - 正常路径")
            void save_normalRecord_success() {
                // given
                BizSubcontractRewardPunish record = new BizSubcontractRewardPunish();
                record.setProjectId(100L);
                record.setContractId(10L);

                when(rewardPunishMapper.insert(any(BizSubcontractRewardPunish.class))).thenReturn(1);

                // when
                rewardPunishService.save(record);

                // then
                verify(rewardPunishMapper).insert(record);
            }
        }

        @Nested
        @DisplayName("删除奖罚记录")
        class DeleteTests {

            @Test
            @DisplayName("删除奖罚记录 - 正常路径")
            void delete_normalRecord_success() {
                // given
                when(rewardPunishMapper.deleteById(1L)).thenReturn(1);

                // when
                rewardPunishService.delete(1L);

                // then
                verify(rewardPunishMapper).deleteById(1L);
            }
        }
    }
}
