package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.dashboard.domain.BizProfitSnapshot;
import com.zwinsight.dashboard.mapper.BizProfitSnapshotMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProfitSnapshotService 单元测试（V2026_59 预计利润口径）
 * <p>核心断言：预计利润 = 合同收入 − CBS 完工预测总成本；口径标记如实（不静默混用）；
 * 根账户去重（防父子重复计数）；无数据回退路径可追溯。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProfitSnapshotServiceTest {

    @Mock private BizProfitSnapshotMapper snapshotMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private BizConstructionContractMapper constructionContractMapper;
    @Mock private BizCostAccountMapper costAccountMapper;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProfitSnapshotService profitSnapshotService;

    private BizProject project(Long id, String status, String contractAmount) {
        BizProject p = new BizProject();
        p.setId(id);
        p.setProjectName("测试项目" + id);
        p.setStatus(status);
        p.setContractAmount(new BigDecimal(contractAmount));
        p.setCumulativeOutput(new BigDecimal("5000000"));
        p.setTotalExpense(new BigDecimal("3000000"));
        return p;
    }

    private BizConstructionContract constructionContract(String amount, String status) {
        BizConstructionContract c = new BizConstructionContract();
        c.setContractAmount(new BigDecimal(amount));
        c.setStatus(status);
        return c;
    }

    private BizCostAccount account(Long id, Long parentId, String category,
                                   String actual, String forecast) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setParentId(parentId);
        a.setCostCategory(category);
        a.setActualAmount(new BigDecimal(actual));
        a.setForecastAmount(new BigDecimal(forecast));
        a.setCurrentAmount(new BigDecimal(forecast));
        return a;
    }

    @Nested
    @DisplayName("computeProjectForecast() 预计利润口径")
    class ForecastTests {

        @Test
        @DisplayName("正常路径 — 施工合同收入 − CBS 根账户 EAC 合计，口径标记为施工合同/CBS")
        void forecast_constructionContractAndCbs() {
            BizProject p = project(1L, "CONSTRUCTION", "9000000");
            // 施工合同 800万（生效）——优先于项目 contractAmount 900万
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(constructionContract("8000000", "EFFECTIVE")));
            // CBS：根账户 MATERIAL 实际400万/预测500万 + 子账户（不应重复计入）
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    account(10L, null, "MATERIAL", "4000000", "5000000"),
                    account(11L, 10L, "MATERIAL", "2000000", "2500000")));

            ProfitSnapshotService.ProjectForecast f = profitSnapshotService.computeProjectForecast(p);

            assertThat(f.contractIncome()).isEqualByComparingTo("8000000");
            assertThat(f.incomeBasis()).isEqualTo(BizProfitSnapshot.BASIS_CONSTRUCTION_CONTRACT);
            // 仅根账户：预测 500万（子账户 250万不重复计入）
            assertThat(f.forecastTotalCost()).isEqualByComparingTo("5000000");
            assertThat(f.actualCost()).isEqualByComparingTo("4000000");
            assertThat(f.costBasis()).isEqualTo(BizProfitSnapshot.BASIS_CBS_FORECAST);
            assertThat(f.forecastProfit()).isEqualByComparingTo("3000000");
            assertThat(f.categoryForecast()).containsEntry("MATERIAL", new BigDecimal("5000000"));
        }

        @Test
        @DisplayName("回退路径 — 无施工合同时收入取项目合同额，口径如实标记（不静默混用）")
        void fallback_noConstructionContract_usesProjectContractAmount() {
            BizProject p = project(2L, "CONSTRUCTION", "9000000");
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(20L, null, "LABOR", "1000000", "1200000")));

            ProfitSnapshotService.ProjectForecast f = profitSnapshotService.computeProjectForecast(p);

            assertThat(f.contractIncome()).isEqualByComparingTo("9000000");
            assertThat(f.incomeBasis()).isEqualTo(BizProfitSnapshot.BASIS_PROJECT_CONTRACT_AMOUNT);
            assertThat(f.forecastProfit()).isEqualByComparingTo("7800000");
        }

        @Test
        @DisplayName("回退路径 — 无成本账户时成本退化为已实现支出，cost_basis 标记 FALLBACK（预计利润口径退化可追溯）")
        void fallback_noCostAccount_marksFallbackBasis() {
            BizProject p = project(3L, "CONSTRUCTION", "6000000");
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(constructionContract("6000000", "EFFECTIVE")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            ProfitSnapshotService.ProjectForecast f = profitSnapshotService.computeProjectForecast(p);

            assertThat(f.costBasis()).isEqualTo(BizProfitSnapshot.BASIS_FALLBACK_EXPENSE);
            // totalExpense 300万 → 预计利润 600−300=300万
            assertThat(f.forecastTotalCost()).isEqualByComparingTo("3000000");
            assertThat(f.forecastProfit()).isEqualByComparingTo("3000000");
        }

        @Test
        @DisplayName("边界路径 — 预计亏损为负值如实呈现，不归零不美化")
        void forecast_lossShownAsNegative() {
            BizProject p = project(4L, "CONSTRUCTION", "5000000");
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(constructionContract("5000000", "EFFECTIVE")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(40L, null, "MATERIAL", "5200000", "5800000")));

            ProfitSnapshotService.ProjectForecast f = profitSnapshotService.computeProjectForecast(p);

            assertThat(f.forecastProfit()).isEqualByComparingTo("-800000");
        }

        @Test
        @DisplayName("边界路径 — forecast 为空的账户按已发生成本兜底，不产生 null 求和异常")
        void forecast_nullForecastAmount_fallsBackToActual() {
            BizProject p = project(5L, "CONSTRUCTION", "5000000");
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(constructionContract("5000000", "EFFECTIVE")));
            BizCostAccount a = account(50L, null, "MACHINE", "800000", "800000");
            a.setForecastAmount(null);
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a));

            ProfitSnapshotService.ProjectForecast f = profitSnapshotService.computeProjectForecast(p);

            assertThat(f.forecastTotalCost()).isEqualByComparingTo("800000");
        }
    }

    @Nested
    @DisplayName("generateSnapshot() 快照生成")
    class SnapshotTests {

        @Test
        @DisplayName("正常路径 — 逐项目 + 公司级快照落库，公司级为项目汇总（profit_delta 首期为空）")
        void generate_insertsProjectAndCompanySnapshots() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION", "8000000")));
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(constructionContract("8000000", "EFFECTIVE")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "MATERIAL", "4000000", "5000000")));
            when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            int count = profitSnapshotService.generateSnapshot();

            assertThat(count).isEqualTo(1);
            ArgumentCaptor<BizProfitSnapshot> captor = ArgumentCaptor.forClass(BizProfitSnapshot.class);
            // 1 条项目级 + 1 条公司级
            verify(snapshotMapper, atLeastOnce()).insert(captor.capture());
            assertThat(captor.getAllValues()).hasSize(2);
            BizProfitSnapshot company = captor.getAllValues().stream()
                    .filter(s -> s.getProjectId() == null).findFirst().orElseThrow();
            assertThat(company.getForecastProfit()).isEqualByComparingTo("3000000");
            assertThat(company.getContractIncome()).isEqualByComparingTo("8000000");
            // 首期无上期快照，delta 为空（不伪造 0）
            assertThat(company.getProfitDelta()).isNull();
        }

        @Test
        @DisplayName("正常路径 — 存在上期快照时计算 profit_delta（归因基数）")
        void generate_withPreviousSnapshot_computesDelta() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION", "8000000")));
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(constructionContract("8000000", "EFFECTIVE")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "MATERIAL", "4000000", "5000000")));
            // findSnapshot 调用次序：①上月项目级 ②本月项目级 ③上月公司级 ④本月公司级
            // 上期存在（利润 400万）、本月不存在 → 走 insert，delta = 300万 − 400万
            BizProfitSnapshot prevProject = new BizProfitSnapshot();
            prevProject.setForecastProfit(new BigDecimal("4000000"));
            BizProfitSnapshot prevCompany = new BizProfitSnapshot();
            prevCompany.setForecastProfit(new BigDecimal("4000000"));
            when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(prevProject, null, prevCompany, null);

            profitSnapshotService.generateSnapshot();

            ArgumentCaptor<BizProfitSnapshot> captor = ArgumentCaptor.forClass(BizProfitSnapshot.class);
            verify(snapshotMapper, atLeastOnce()).insert(captor.capture());
            assertThat(captor.getAllValues()).hasSize(2)
                    .allSatisfy(s -> assertThat(s.getProfitDelta()).isEqualByComparingTo("-1000000"));
        }

        @Test
        @DisplayName("幂等路径 — 本月快照已存在时走覆盖更新，不重复插入")
        void generate_existingCurrentSnapshot_updatesInsteadOfInsert() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION", "8000000")));
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(constructionContract("8000000", "EFFECTIVE")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "MATERIAL", "4000000", "5000000")));
            BizProfitSnapshot prevProject = new BizProfitSnapshot();
            prevProject.setForecastProfit(new BigDecimal("4000000"));
            BizProfitSnapshot currentProject = new BizProfitSnapshot();
            currentProject.setId(101L);
            currentProject.setForecastProfit(new BigDecimal("3500000"));
            BizProfitSnapshot prevCompany = new BizProfitSnapshot();
            prevCompany.setForecastProfit(new BigDecimal("4000000"));
            BizProfitSnapshot currentCompany = new BizProfitSnapshot();
            currentCompany.setId(102L);
            // ①上月项目 ②本月项目（已存在）③上月公司 ④本月公司（已存在）
            when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(prevProject, currentProject, prevCompany, currentCompany);

            profitSnapshotService.generateSnapshot();

            verify(snapshotMapper, org.mockito.Mockito.never()).insert(any(BizProfitSnapshot.class));
            verify(snapshotMapper, org.mockito.Mockito.times(2)).updateById(any(BizProfitSnapshot.class));
            // 覆盖写入最新口径值，delta 按上期快照重算
            assertThat(currentProject.getForecastProfit()).isEqualByComparingTo("3000000");
            assertThat(currentProject.getProfitDelta()).isEqualByComparingTo("-1000000");
        }

        @Test
        @DisplayName("边界路径 — 无纳入范围项目时仍生成公司级快照（全 0，不抛异常不伪造）")
        void generate_noProjects_companySnapshotZero() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            int count = profitSnapshotService.generateSnapshot();

            assertThat(count).isZero();
            ArgumentCaptor<BizProfitSnapshot> captor = ArgumentCaptor.forClass(BizProfitSnapshot.class);
            verify(snapshotMapper).insert(captor.capture());
            assertThat(captor.getValue().getProjectId()).isNull();
            assertThat(captor.getValue().getForecastProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("attributeProfitChange() 利润变化归因")
    class AttributionTests {

        @Test
        @DisplayName("正常路径 — 按成本类别分解差额，成本上升呈现为利润负向影响并按绝对值降序")
        @SuppressWarnings("unchecked")
        void attribute_decomposesByCategory() {
            BizProfitSnapshot current = new BizProfitSnapshot();
            current.setForecastProfit(new BigDecimal("3000000"));
            current.setCategoryBreakdown("{\"MATERIAL\":5000000,\"LABOR\":2000000}");
            BizProfitSnapshot prev = new BizProfitSnapshot();
            prev.setForecastProfit(new BigDecimal("4000000"));
            prev.setCategoryBreakdown("{\"MATERIAL\":4200000,\"LABOR\":1900000}");
            // 第一次查本期，第二次查上期
            when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current, prev);

            Map<String, Object> result = profitSnapshotService.attributeProfitChange(1L, "2026-09");

            assertThat(result.get("hasBaseline")).isEqualTo(true);
            assertThat((BigDecimal) result.get("profitDelta")).isEqualByComparingTo("-1000000");
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            assertThat(items).hasSize(2);
            // MATERIAL 成本上升 80万 → 利润影响 −80万，绝对值最大排第一
            assertThat(items.get(0).get("category")).isEqualTo("MATERIAL");
            assertThat((BigDecimal) items.get(0).get("delta")).isEqualByComparingTo("-800000");
            assertThat((BigDecimal) items.get(1).get("delta")).isEqualByComparingTo("-100000");
        }

        @Test
        @DisplayName("边界路径 — 无上期快照时 hasBaseline=false 且 profitDelta 为 null（不伪造对比基期）")
        @SuppressWarnings("unchecked")
        void attribute_noBaseline_markedExplicitly() {
            BizProfitSnapshot current = new BizProfitSnapshot();
            current.setForecastProfit(new BigDecimal("3000000"));
            // ①本期存在 ②上期不存在
            when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(current, (BizProfitSnapshot) null);

            Map<String, Object> result = profitSnapshotService.attributeProfitChange(1L, "2026-09");

            assertThat(result.get("hasBaseline")).isEqualTo(false);
            assertThat(result.get("profitDelta")).isNull();
            assertThat((List<Map<String, Object>>) result.get("items")).isEmpty();
        }

        @Test
        @DisplayName("异常路径 — 本期快照不存在时抛业务异常，不返回空对象伪装成功")
        void attribute_snapshotMissing_throws() {
            when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> profitSnapshotService.attributeProfitChange(1L, "2026-01"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("快照不存在");
        }

        @Test
        @DisplayName("异常路径 — 趋势月数超范围被拒绝")
        void trend_invalidMonths_throws() {
            assertThatThrownBy(() -> profitSnapshotService.getTrend(1L, 0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("1-36");
            assertThatThrownBy(() -> profitSnapshotService.getTrend(1L, 37))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("1-36");
        }
    }
}
