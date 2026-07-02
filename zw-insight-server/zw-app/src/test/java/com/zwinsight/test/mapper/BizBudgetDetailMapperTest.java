package com.zwinsight.test.mapper;

import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.test.BaseH2MapperTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BizBudgetDetailMapper 自定义 SQL 测试（H2）
 */
class BizBudgetDetailMapperTest extends BaseH2MapperTest {

    @Autowired
    private BizBudgetDetailMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM biz_budget_detail");
        jdbc.execute("DELETE FROM biz_budget");

        // 插入预算主表
        jdbc.execute("INSERT INTO biz_budget (id, project_id, budget_type, status, deleted, version) "
                + "VALUES (100, 1, 'ORIGINAL', 'APPROVED', 0, 0)");
        jdbc.execute("INSERT INTO biz_budget (id, project_id, budget_type, status, deleted, version) "
                + "VALUES (200, 2, 'ORIGINAL', 'APPROVED', 0, 0)");

        // 预算明细 — 项目1 材料类
        jdbc.execute("INSERT INTO biz_budget_detail (id, budget_id, cost_category, budget_total_price, deleted, version) "
                + "VALUES (1001, 100, 'MATERIAL', 50000.00, 0, 0)");
        jdbc.execute("INSERT INTO biz_budget_detail (id, budget_id, cost_category, budget_total_price, deleted, version) "
                + "VALUES (1002, 100, 'MATERIAL', 30000.00, 0, 0)");
        // 预算明细 — 项目1 劳务类
        jdbc.execute("INSERT INTO biz_budget_detail (id, budget_id, cost_category, budget_total_price, deleted, version) "
                + "VALUES (1003, 100, 'LABOR', 20000.00, 0, 0)");
        // 已删除的明细（不应被统计）
        jdbc.execute("INSERT INTO biz_budget_detail (id, budget_id, cost_category, budget_total_price, deleted, version) "
                + "VALUES (1004, 100, 'MATERIAL', 99999.00, 1, 0)");

        // 预算明细 — 项目2 材料类
        jdbc.execute("INSERT INTO biz_budget_detail (id, budget_id, cost_category, budget_total_price, deleted, version) "
                + "VALUES (2001, 200, 'MATERIAL', 10000.00, 0, 0)");
    }

    @Test
    @DisplayName("addBudgetTotalPrice — 累加预算合计金额")
    void should_add_budget_total_price() {
        int rows = mapper.addBudgetTotalPrice(1001L, new BigDecimal("5000.00"));

        assertThat(rows).isEqualTo(1);

        // 验证累加后的值
        BigDecimal total = mapper.sumBudgetTotalPriceByBudgetId(100L);
        // 50000 + 5000 + 30000 + 20000 = 105000
        assertThat(total).isEqualByComparingTo(new BigDecimal("105000.00"));
    }

    @Test
    @DisplayName("addBudgetTotalPrice — 负值调减")
    void should_subtract_budget_total_price() {
        int rows = mapper.addBudgetTotalPrice(1001L, new BigDecimal("-10000.00"));

        assertThat(rows).isEqualTo(1);

        BigDecimal total = mapper.sumBudgetTotalPriceByBudgetId(100L);
        // 50000 - 10000 + 30000 + 20000 = 90000
        assertThat(total).isEqualByComparingTo(new BigDecimal("90000.00"));
    }

    @Test
    @DisplayName("sumBudgetTotalPriceByBudgetId — 汇总某预算下所有明细合计")
    void should_sum_budget_total_price_by_budget_id() {
        BigDecimal total = mapper.sumBudgetTotalPriceByBudgetId(100L);

        // 50000 + 30000 + 20000 = 100000（已删除的 99999 不计入）
        assertThat(total).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("sumBudgetTotalPriceByBudgetId — 无明细时返回 0")
    void should_return_zero_when_no_details() {
        BigDecimal total = mapper.sumBudgetTotalPriceByBudgetId(999L);

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("sumBudgetByProjectAndCategory — 按项目和科目汇总预算")
    void should_sum_budget_by_project_and_category() {
        BigDecimal material = mapper.sumBudgetByProjectAndCategory(1L, "MATERIAL");

        // 项目1 + MATERIAL：50000 + 30000 = 80000
        assertThat(material).isEqualByComparingTo(new BigDecimal("80000.00"));
    }

    @Test
    @DisplayName("sumBudgetByProjectAndCategory — 不同项目隔离")
    void should_isolate_by_project() {
        BigDecimal material = mapper.sumBudgetByProjectAndCategory(2L, "MATERIAL");

        // 项目2 + MATERIAL：10000
        assertThat(material).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("sumBudgetByProjectAndCategory — 不同科目隔离")
    void should_isolate_by_category() {
        BigDecimal labor = mapper.sumBudgetByProjectAndCategory(1L, "LABOR");

        // 项目1 + LABOR：20000
        assertThat(labor).isEqualByComparingTo(new BigDecimal("20000.00"));
    }
}
