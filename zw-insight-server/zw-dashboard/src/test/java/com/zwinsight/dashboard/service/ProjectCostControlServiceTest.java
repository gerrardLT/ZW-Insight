package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.dashboard.dto.ProjectCostControlDTO;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectWbsNode;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectWbsNodeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectCostControlService} 单元测试（Project Cost 360 成本看板）。
 *
 * <p>覆盖六维金额（Baseline / Current / Commitment / Actual / Forecast / Variance）的
 * 账户明细、项目汇总、费用类别分组三层聚合，以及比率计算的精度与除零安全。
 *
 * <h3>比率语义（易错点，已被本测试钉住）</h3>
 * <p>{@code calculateRate} 返回的是<b>百分数</b>而非小数比率：
 * 先 {@code divide(denominator, 4, HALF_UP)} 再 {@code ×100} 最后 {@code setScale(2, HALF_UP)}。
 * 因此 actual=20 / current=80 得到的是 {@code 25.00}，不是 {@code 0.25}。
 * 分母为 null / 0，或分子为 null 时一律返回 {@code BigDecimal.ZERO}（不抛异常、不产生 NaN）。
 *
 * <h3>MyBatis-Plus lambda cache 引导</h3>
 * <p>本仓库既有统一做法（参见 {@code ProjectDashboardServiceTest}、zw-workflow {@code UrgeServiceTest}）：
 * 纯 Mockito 环境无 SqlSessionFactory 注册实体，凡 {@code LambdaQueryWrapper} 需要立即解析列名
 * （如 {@code .select(...)}）就会抛「can not find lambda cache for this entity」。
 * 本服务的 wrapper 只用 {@code eq/orderByAsc}（列名延迟解析），故引导并非严格必需，
 * 但仍按统一模式注册，避免后续给 wrapper 加 {@code .select()} 时静默炸掉。
 */
@ExtendWith(MockitoExtension.class)
class ProjectCostControlServiceTest {

    private static final Long PROJECT_ID = 1L;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizCostAccount.class);
        TableInfoHelper.initTableInfo(assistant, BizProjectWbsNode.class);
        TableInfoHelper.initTableInfo(assistant, BizProject.class);
    }

    @Mock private BizCostAccountMapper costAccountMapper;
    @Mock private BizProjectWbsNodeMapper wbsNodeMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private ProjectCostControlService service;

    // ==================== 构造工具 ====================

    /** 标准样本账户：baseline=100 / current=80 / commitment=60 / actual=20 / forecast=30。 */
    private static BizCostAccount account(Long id, String code, String category) {
        return account(id, code, category, "100", "80", "60", "20", "30");
    }

    private static BizCostAccount account(Long id, String code, String category,
                                          String baseline, String current, String commitment,
                                          String actual, String forecast) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setProjectId(PROJECT_ID);
        a.setAccountCode(code);
        a.setAccountName("账户-" + code);
        a.setCostCategory(category);
        a.setCostSubcategory("子类-" + code);
        a.setBaselineAmount(baseline == null ? null : new BigDecimal(baseline));
        a.setCurrentAmount(current == null ? null : new BigDecimal(current));
        a.setCommitmentAmount(commitment == null ? null : new BigDecimal(commitment));
        a.setActualAmount(actual == null ? null : new BigDecimal(actual));
        a.setForecastAmount(forecast == null ? null : new BigDecimal(forecast));
        a.setStatus("ACTIVE");
        return a;
    }

    private static BizProjectWbsNode wbs(Long id, String code, String name) {
        BizProjectWbsNode n = new BizProjectWbsNode();
        n.setId(id);
        n.setProjectId(PROJECT_ID);
        n.setNodeCode(code);
        n.setNodeName(name);
        return n;
    }

    /** 打桩：项目存在 + N 个账户 + 无 WBS 关联。 */
    private void stubAccounts(BizCostAccount... accounts) {
        when(costAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(accounts));
    }

    // =====================================================================
    // 空数据与零值分支
    // =====================================================================

    @Nested
    @DisplayName("空数据：无账户时三层聚合均为零/空，且不触发多余查询")
    class EmptyTests {

        @Test
        @DisplayName("无账户：totals 全零、accounts/categorySummaries/trends 全空")
        void noAccounts_allZeroAndEmpty() {
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(dto.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(dto.getAccountCount()).isZero();
            assertThat(dto.getAccounts()).isEmpty();
            assertThat(dto.getCategorySummaries()).isEmpty();
            // 趋势当前实现恒为空列表（服务注释：后续可从实际成本流水聚合）
            assertThat(dto.getTrends()).isEmpty();

            ProjectCostControlDTO.CostTotals t = dto.getTotals();
            assertThat(t).isNotNull();
            assertThat(t.getBaselineTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getCurrentTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getCommitmentTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getActualTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getForecastTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getRemainingBudget()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getVarianceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getVarianceRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getUsageRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(t.getCommitmentRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("无账户：不查 WBS 名称（避免无谓的批量查询）")
        void noAccounts_skipsWbsBatchQuery() {
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            service.getCostControl(PROJECT_ID);

            verify(wbsNodeMapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("账户均无 wbsNodeId：同样跳过 WBS 批量查询")
        void accountsWithoutWbsNode_skipsWbsBatchQuery() {
            stubAccounts(account(10L, "CB-001", "LABOR"));

            service.getCostControl(PROJECT_ID);

            verify(wbsNodeMapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("五个金额全为 null：nullToZero 兜底，不抛 NPE")
        void allAmountsNull_treatedAsZero() {
            stubAccounts(account(10L, "CB-001", "LABOR", null, null, null, null, null));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            ProjectCostControlDTO.CostAccountSummary s = dto.getAccounts().get(0);
            assertThat(s.getBaseline()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(s.getCurrent()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(s.getCommitment()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(s.getActual()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(s.getForecast()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(s.getRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(s.getVariance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(s.getUsageRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.getTotals().getCurrentTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // =====================================================================
    // 账户明细
    // =====================================================================

    @Nested
    @DisplayName("账户明细：六维金额 + remaining/variance/usageRate")
    class AccountSummaryTests {

        @Test
        @DisplayName("标准样本：remaining=current-actual=60，variance=current-forecast=50，usageRate=25.00%")
        void standardAccount_computesDerivedFields() {
            stubAccounts(account(10L, "CB-001", "LABOR"));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(dto.getAccountCount()).isEqualTo(1);
            assertThat(dto.getAccounts()).hasSize(1);

            ProjectCostControlDTO.CostAccountSummary s = dto.getAccounts().get(0);
            assertThat(s.getAccountId()).isEqualTo(10L);
            assertThat(s.getCode()).isEqualTo("CB-001");
            assertThat(s.getName()).isEqualTo("账户-CB-001");
            assertThat(s.getCostCategory()).isEqualTo("LABOR");
            assertThat(s.getCostSubcategory()).isEqualTo("子类-CB-001");
            assertThat(s.getStatus()).isEqualTo("ACTIVE");
            assertThat(s.getBaseline()).isEqualByComparingTo("100");
            assertThat(s.getCurrent()).isEqualByComparingTo("80");
            assertThat(s.getCommitment()).isEqualByComparingTo("60");
            assertThat(s.getActual()).isEqualByComparingTo("20");
            assertThat(s.getForecast()).isEqualByComparingTo("30");
            assertThat(s.getRemaining()).isEqualByComparingTo("60");
            assertThat(s.getVariance()).isEqualByComparingTo("50");
            // 百分数语义：20/80 = 0.2500 → ×100 → 25.00
            assertThat(s.getUsageRate()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("超支：forecast > current 时 variance 与 usageRate 可为负/超 100%")
        void overspend_negativeVarianceAndRateAbove100() {
            stubAccounts(account(10L, "CB-001", "MATERIAL",
                    "100", "80", "60", "95", "100"));

            ProjectCostControlDTO.CostAccountSummary s =
                    service.getCostControl(PROJECT_ID).getAccounts().get(0);

            // variance = 80 - 100 = -20（负值即超支预警）
            assertThat(s.getVariance()).isEqualByComparingTo("-20");
            // remaining = 80 - 95 = -15
            assertThat(s.getRemaining()).isEqualByComparingTo("-15");
            // usageRate = 95/80 = 1.1875 → 118.75%，必须允许超过 100%
            assertThat(s.getUsageRate()).isEqualByComparingTo("118.75");
        }

        @Test
        @DisplayName("比率精度：先 4 位 HALF_UP 再 ×100 保留 2 位（1/3 → 33.33，2/3 → 66.67）")
        void ratePrecision_twoStepRounding() {
            stubAccounts(account(10L, "CB-001", "LABOR", "0", "3", "0", "1", "0"));

            // usageRate = 1/3 → divide(4,HALF_UP)=0.3333 → ×100=33.3300 → setScale(2)=33.33
            assertThat(service.getCostControl(PROJECT_ID).getAccounts().get(0).getUsageRate())
                    .isEqualByComparingTo("33.33");

            // 换分子为 2：2/3 → 0.6667 → 66.6700 → 66.67
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, "CB-001", "LABOR", "0", "3", "0", "2", "0")));
            assertThat(service.getCostControl(PROJECT_ID).getAccounts().get(0).getUsageRate())
                    .isEqualByComparingTo("66.67");
        }

        @Test
        @DisplayName("除零安全：current=0 时 usageRate 返回 ZERO，不抛 ArithmeticException")
        void zeroDenominator_returnsZeroNotException() {
            stubAccounts(account(10L, "CB-001", "LABOR", "100", "0", "60", "20", "30"));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(dto.getAccounts().get(0).getUsageRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.getTotals().getUsageRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.getTotals().getVarianceRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.getTotals().getCommitmentRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("WBS 关联：命中时回填 wbsCode/wbsName，且只批量查一次")
        void wbsLinked_fillsCodeAndName() {
            BizCostAccount acc = account(10L, "CB-001", "LABOR");
            acc.setWbsNodeId(55L);
            stubAccounts(acc);
            when(wbsNodeMapper.selectBatchIds(any())).thenReturn(List.of(wbs(55L, "WP-001", "主体结构")));

            ProjectCostControlDTO.CostAccountSummary s =
                    service.getCostControl(PROJECT_ID).getAccounts().get(0);

            assertThat(s.getWbsCode()).isEqualTo("WP-001");
            assertThat(s.getWbsName()).isEqualTo("主体结构");
            verify(wbsNodeMapper, times(1)).selectBatchIds(any());
        }

        @Test
        @DisplayName("WBS 关联但节点已删：wbsCode/wbsName 保持 null，不抛异常")
        void wbsLinkedButNodeMissing_leavesNull() {
            BizCostAccount acc = account(10L, "CB-001", "LABOR");
            acc.setWbsNodeId(55L);
            stubAccounts(acc);
            when(wbsNodeMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

            ProjectCostControlDTO.CostAccountSummary s =
                    service.getCostControl(PROJECT_ID).getAccounts().get(0);

            assertThat(s.getWbsCode()).isNull();
            assertThat(s.getWbsName()).isNull();
        }
    }

    // =====================================================================
    // 项目汇总
    // =====================================================================

    @Nested
    @DisplayName("项目汇总 totals：五维求和 + 三个比率")
    class TotalsTests {

        @Test
        @DisplayName("多账户求和：baseline 150 / current 120 / commitment 80 / actual 30 / forecast 45")
        void multipleAccounts_sumsAllDimensions() {
            stubAccounts(
                    account(10L, "CB-001", "LABOR", "100", "80", "60", "20", "30"),
                    account(11L, "CB-002", "MATERIAL", "50", "40", "20", "10", "15"));

            ProjectCostControlDTO.CostTotals t = service.getCostControl(PROJECT_ID).getTotals();

            assertThat(t.getBaselineTotal()).isEqualByComparingTo("150");
            assertThat(t.getCurrentTotal()).isEqualByComparingTo("120");
            assertThat(t.getCommitmentTotal()).isEqualByComparingTo("80");
            assertThat(t.getActualTotal()).isEqualByComparingTo("30");
            assertThat(t.getForecastTotal()).isEqualByComparingTo("45");
            // remainingBudget = 120 - 30 = 90
            assertThat(t.getRemainingBudget()).isEqualByComparingTo("90");
            // varianceAmount = 120 - 45 = 75
            assertThat(t.getVarianceAmount()).isEqualByComparingTo("75");
            // usageRate = 30/120 = 25.00%
            assertThat(t.getUsageRate()).isEqualByComparingTo("25.00");
            // varianceRate = 75/120 = 62.50%
            assertThat(t.getVarianceRate()).isEqualByComparingTo("62.50");
            // commitmentRate = 80/120 = 0.6667 → 66.67%
            assertThat(t.getCommitmentRate()).isEqualByComparingTo("66.67");
        }

        @Test
        @DisplayName("汇总求和跳过 null 金额，不因单账户缺字段而整体失真")
        void sumSkipsNullAmounts() {
            stubAccounts(
                    account(10L, "CB-001", "LABOR", "100", "80", "60", "20", "30"),
                    account(11L, "CB-002", "MATERIAL", null, null, null, null, null));

            ProjectCostControlDTO.CostTotals t = service.getCostControl(PROJECT_ID).getTotals();

            assertThat(t.getBaselineTotal()).isEqualByComparingTo("100");
            assertThat(t.getCurrentTotal()).isEqualByComparingTo("80");
            assertThat(t.getActualTotal()).isEqualByComparingTo("20");
        }
    }

    // =====================================================================
    // 费用类别分组
    // =====================================================================

    @Nested
    @DisplayName("类别汇总 categorySummaries：分组 / 中文名映射 / 兜底")
    class CategoryTests {

        @Test
        @DisplayName("按类别分组求和，accountCount 为该类别账户数")
        void groupsByCategory() {
            stubAccounts(
                    account(10L, "CB-001", "LABOR", "100", "80", "60", "20", "30"),
                    account(11L, "CB-002", "MATERIAL", "50", "40", "20", "10", "15"),
                    account(12L, "CB-003", "LABOR", "30", "20", "10", "5", "8"));

            List<ProjectCostControlDTO.CategorySummary> cats =
                    service.getCostControl(PROJECT_ID).getCategorySummaries();

            assertThat(cats).hasSize(2);
            ProjectCostControlDTO.CategorySummary labor = cats.stream()
                    .filter(c -> "LABOR".equals(c.getCostCategory())).findFirst().orElseThrow();
            ProjectCostControlDTO.CategorySummary material = cats.stream()
                    .filter(c -> "MATERIAL".equals(c.getCostCategory())).findFirst().orElseThrow();

            assertThat(labor.getAccountCount()).isEqualTo(2);
            assertThat(labor.getBaseline()).isEqualByComparingTo("130");
            assertThat(labor.getCurrent()).isEqualByComparingTo("100");
            assertThat(labor.getCommitment()).isEqualByComparingTo("70");
            assertThat(labor.getActual()).isEqualByComparingTo("25");
            assertThat(labor.getForecast()).isEqualByComparingTo("38");
            // variance = 100 - 38 = 62
            assertThat(labor.getVariance()).isEqualByComparingTo("62");

            assertThat(material.getAccountCount()).isEqualTo(1);
            assertThat(material.getBaseline()).isEqualByComparingTo("50");
            assertThat(material.getVariance()).isEqualByComparingTo("25");
        }

        @Test
        @DisplayName("六类已知类别映射中文名")
        void mapsKnownCategoriesToChineseNames() {
            stubAccounts(
                    account(1L, "C1", "MATERIAL", "1", "1", "1", "1", "1"),
                    account(2L, "C2", "LABOR", "1", "1", "1", "1", "1"),
                    account(3L, "C3", "MACHINE", "1", "1", "1", "1", "1"),
                    account(4L, "C4", "SUBCONTRACT", "1", "1", "1", "1", "1"),
                    account(5L, "C5", "INDIRECT", "1", "1", "1", "1", "1"),
                    account(6L, "C6", "OTHER", "1", "1", "1", "1", "1"));

            List<ProjectCostControlDTO.CategorySummary> cats =
                    service.getCostControl(PROJECT_ID).getCategorySummaries();

            assertThat(cats).extracting(ProjectCostControlDTO.CategorySummary::getCategoryName)
                    .containsExactlyInAnyOrder("材料费", "人工费", "机械费", "分包费", "间接费", "其他费用");
        }

        @Test
        @DisplayName("costCategory 为 null 时归入 OTHER 组，中文名取「其他费用」")
        void nullCategory_groupedAsOther() {
            stubAccounts(account(10L, "CB-001", null));

            List<ProjectCostControlDTO.CategorySummary> cats =
                    service.getCostControl(PROJECT_ID).getCategorySummaries();

            assertThat(cats).hasSize(1);
            assertThat(cats.get(0).getCostCategory()).isEqualTo("OTHER");
            assertThat(cats.get(0).getCategoryName()).isEqualTo("其他费用");
        }

        @Test
        @DisplayName("未知类别：中文名回退为原始 code，不丢分组也不抛异常")
        void unknownCategory_fallsBackToRawCode() {
            stubAccounts(account(10L, "CB-001", "SPECIAL_FEE"));

            List<ProjectCostControlDTO.CategorySummary> cats =
                    service.getCostControl(PROJECT_ID).getCategorySummaries();

            assertThat(cats).hasSize(1);
            assertThat(cats.get(0).getCostCategory()).isEqualTo("SPECIAL_FEE");
            assertThat(cats.get(0).getCategoryName()).isEqualTo("SPECIAL_FEE");
        }

        @Test
        @DisplayName("分组用 LinkedHashMap：输出顺序等于类别首次出现顺序（前端展示稳定）")
        void groupingPreservesFirstAppearanceOrder() {
            stubAccounts(
                    account(10L, "CB-001", "MACHINE", "1", "1", "1", "1", "1"),
                    account(11L, "CB-002", "LABOR", "1", "1", "1", "1", "1"),
                    account(12L, "CB-003", "MACHINE", "1", "1", "1", "1", "1"));

            List<ProjectCostControlDTO.CategorySummary> cats =
                    service.getCostControl(PROJECT_ID).getCategorySummaries();

            assertThat(cats).extracting(ProjectCostControlDTO.CategorySummary::getCostCategory)
                    .containsExactly("MACHINE", "LABOR");
        }
    }

    // =====================================================================
    // 项目信息与 WBS 计数
    // =====================================================================

    @Nested
    @DisplayName("项目信息与 WBS 计数")
    class ProjectInfoTests {

        @Test
        @DisplayName("项目存在：回填 projectName，并透出 wbsCount")
        void projectFound_fillsNameAndWbsCount() {
            BizProject project = new BizProject();
            project.setId(PROJECT_ID);
            project.setProjectName("滨江花园一期");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(7L);
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(dto.getProjectName()).isEqualTo("滨江花园一期");
            assertThat(dto.getWbsCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("项目不存在：服务层不抛异常，projectName 留空（404 由 Controller 负责）")
        void projectMissing_serviceToleratesIt() {
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(dto.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(dto.getProjectName()).isNull();
            // 其余聚合照常产出，不因项目缺失而整体失败
            assertThat(dto.getTotals()).isNotNull();
            assertThat(dto.getAccounts()).isEmpty();
        }

        @Test
        @DisplayName("wbsCount 未打桩时按 0 处理，不产生 NPE")
        void wbsCountDefaultsToZero() {
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThat(service.getCostControl(PROJECT_ID).getWbsCount()).isZero();
        }
    }

    // =====================================================================
    // UI §7.2 七类成本结构映射 + §7.1 预计超支 / 偏差率
    // =====================================================================

    @Nested
    @DisplayName("文档七类口径（材料/分包/人工/机械/措施/管理/商务）与偏差率")
    class DocCategoryTests {

        private BizCostAccount docAccount(Long id, String category, String subcategory,
                                          String baseline, String current, String forecast) {
            BizCostAccount a = account(id, "A" + id, category, baseline, current, "0", "0", forecast);
            a.setCostSubcategory(subcategory);
            return a;
        }

        private ProjectCostControlDTO.DocCategorySummary pick(ProjectCostControlDTO dto, String code) {
            return dto.getDocCategories().stream()
                    .filter(c -> code.equals(c.getCode())).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("结构完整 — 恒返回 7 类 + 未归类共 8 行且顺序与文档一致（无账户也返回，金额为零）")
        void alwaysReturnsSevenPlusOther() {
            stubAccounts();

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(dto.getDocCategories()).hasSize(8);
            assertThat(dto.getDocCategories()).extracting(ProjectCostControlDTO.DocCategorySummary::getCode)
                    .containsExactly("MATERIAL", "SUBCONTRACT", "LABOR", "MACHINE",
                            "MEASURE", "ADMIN", "BUSINESS", "OTHER");
            // 无账户时 riskLevel=INFO（无预算基准），不得给 GREEN 伪装“未超支”
            assertThat(dto.getDocCategories()).allSatisfy(c -> {
                assertThat(c.getRiskLevel()).isEqualTo("INFO");
                assertThat(c.getVarianceRate()).isNull();
                assertThat(c.getAccountCount()).isZero();
                assertThat(c.getCurrent()).isEqualByComparingTo("0");
            });
        }

        @Test
        @DisplayName("四类直接映射 — 由 cost_category 归类（basis=CBS_CATEGORY）并按超支率定风险级")
        void directCategoryMapping() {
            stubAccounts(
                    docAccount(1L, "MATERIAL", "主材", "100", "100", "120"),
                    docAccount(2L, "SUBCONTRACT", "专业分包", "200", "200", "180"),
                    docAccount(3L, "LABOR", "主体劳务", "50", "50", "50"),
                    docAccount(4L, "MACHINE", "大型机械", "30", "30", "30"));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(pick(dto, "MATERIAL").getBasis()).isEqualTo("CBS_CATEGORY");
            assertThat(pick(dto, "MATERIAL").getForecast()).isEqualByComparingTo("120");
            // 材料超支 20/100 = 20% > 10% → RED
            assertThat(pick(dto, "MATERIAL").getRiskLevel()).isEqualTo("RED");
            // 分包 forecast 180 < current 200 → 节约 → GREEN；人工恰好持平也归 GREEN（非超支）
            assertThat(pick(dto, "SUBCONTRACT").getRiskLevel()).isEqualTo("GREEN");
            assertThat(pick(dto, "LABOR").getRiskLevel()).isEqualTo("GREEN");
            assertThat(pick(dto, "LABOR").getVarianceRate()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("间接费按子类关键词细分 — 临设费→措施、招待费→商务、管理费→管理")
        void subcategoryKeywordMapping() {
            stubAccounts(
                    docAccount(1L, "INDIRECT", "临设费", "100", "100", "105"),
                    docAccount(2L, "INDIRECT", "招待费", "20", "20", "28"),
                    docAccount(3L, "INDIRECT", "管理费", "200", "200", "190"));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            assertThat(pick(dto, "MEASURE").getBasis()).isEqualTo("CBS_SUBCATEGORY_KEYWORD");
            assertThat(pick(dto, "MEASURE").getCurrent()).isEqualByComparingTo("100");
            assertThat(pick(dto, "BUSINESS").getCurrent()).isEqualByComparingTo("20");
            assertThat(pick(dto, "ADMIN").getCurrent()).isEqualByComparingTo("200");
            // 商务超支 8/20 = 40% → RED；措施超支 5/100 = 5% → YELLOW（未过 10% 阈值）；管理节约 → GREEN
            assertThat(pick(dto, "BUSINESS").getRiskLevel()).isEqualTo("RED");
            assertThat(pick(dto, "MEASURE").getRiskLevel()).isEqualTo("YELLOW");
            assertThat(pick(dto, "ADMIN").getRiskLevel()).isEqualTo("GREEN");
            // 间接费已全部分流，不得残留在未归类
            assertThat(pick(dto, "OTHER").getAccountCount()).isZero();
        }

        @Test
        @DisplayName("未命中关键词不猜类 — 归 OTHER 且 basis=UNCLASSIFIED（金额去向可追溯）")
        void unclassifiedGoesToOther() {
            stubAccounts(
                    docAccount(1L, "INDIRECT", "某某专项支出", "100", "100", "100"),
                    // 子类为空 → 降级看账户名（“账户-A2”），仍无关键词 → 未归类
                    docAccount(2L, "OTHER", null, "50", "50", "50"));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            ProjectCostControlDTO.DocCategorySummary other = pick(dto, "OTHER");
            assertThat(other.getBasis()).isEqualTo("UNCLASSIFIED");
            assertThat(other.getAccountCount()).isEqualTo(2);
            assertThat(other.getCurrent()).isEqualByComparingTo("150");
            // 关键：未被静默并入措施/管理/商务任意一类
            assertThat(pick(dto, "MEASURE").getAccountCount()).isZero();
            assertThat(pick(dto, "ADMIN").getAccountCount()).isZero();
            assertThat(pick(dto, "BUSINESS").getAccountCount()).isZero();
        }

        @Test
        @DisplayName("§7.1 预计超支 = 预计最终 − 目标成本（正数=超支）；偏差率无预算基准时为 null")
        void totalsForecastOverrunAndNullRate() {
            stubAccounts(
                    docAccount(1L, "MATERIAL", "主材", "1000", "1200", "1600"),
                    docAccount(2L, "LABOR", "主体劳务", "500", "0", "0"));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            // 目标成本（Σ baseline）1500；预计最终（Σ forecast）1600 → 预计超支 +100
            assertThat(dto.getTotals().getBaselineTotal()).isEqualByComparingTo("1500");
            assertThat(dto.getTotals().getForecastTotal()).isEqualByComparingTo("1600");
            assertThat(dto.getTotals().getForecastOverrun()).isEqualByComparingTo("100");
            // MATERIAL：variance = 1200 − 1600 = −400 → 偏差率 −33.33%
            assertThat(dto.getAccounts().stream()
                    .filter(a -> "MATERIAL".equals(a.getCostCategory())).findFirst().orElseThrow()
                    .getVarianceRate()).isEqualByComparingTo("-33.33");
            // LABOR：当前预算为 0 → 偏差率必须 null（不得用 0 冒充“无偏差”）
            assertThat(dto.getAccounts().stream()
                    .filter(a -> "LABOR".equals(a.getCostCategory())).findFirst().orElseThrow()
                    .getVarianceRate()).isNull();
        }

        @Test
        @DisplayName("七类与 CBS 六类并存不互替（两套口径同时返回，金额各自自洽）")
        void docAndCbsCategoriesCoexist() {
            stubAccounts(
                    docAccount(1L, "MATERIAL", "主材", "100", "100", "100"),
                    docAccount(2L, "INDIRECT", "招待费", "20", "20", "20"),
                    docAccount(3L, "INDIRECT", "管理费", "30", "30", "30"));

            ProjectCostControlDTO dto = service.getCostControl(PROJECT_ID);

            // CBS 口径：MATERIAL + INDIRECT 共 2 类
            assertThat(dto.getCategorySummaries())
                    .extracting(ProjectCostControlDTO.CategorySummary::getCostCategory)
                    .containsExactlyInAnyOrder("MATERIAL", "INDIRECT");
            // 文档口径：招待费+管理费 同属 INDIRECT，但分归商务与管理
            assertThat(pick(dto, "BUSINESS").getCurrent()).isEqualByComparingTo("20");
            assertThat(pick(dto, "ADMIN").getCurrent()).isEqualByComparingTo("30");
            // 两套口径总额相等（同一批账户，仅分组方式不同）
            BigDecimal cbsTotal = dto.getCategorySummaries().stream()
                    .map(ProjectCostControlDTO.CategorySummary::getCurrent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal docTotal = dto.getDocCategories().stream()
                    .map(ProjectCostControlDTO.DocCategorySummary::getCurrent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(docTotal).isEqualByComparingTo(cbsTotal);
        }
    }
}
