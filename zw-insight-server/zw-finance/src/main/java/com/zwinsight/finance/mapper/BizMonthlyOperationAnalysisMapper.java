package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.finance.domain.BizMonthlyOperationAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 月度经营分析表 Mapper（V2026_67，资金流转 §9）
 * <p><b>跨表只读聚合说明</b>：CBS 成本账户属 zw-budget 模块、支出合同分属 purchase/labor/
 * machine/subcontract 模块，本模块若注入其 Service/Mapper 会形成 Maven 循环依赖。
 * 参照 {@link ContractPayableMapper} 与 {@code BizProjectMapper.countTenderRegisters} 的
 * 既有惯例，此处以原生 SQL 只读跨表聚合（不写入他模块表），杜绝依赖倒置与 SQL 注入
 * （不使用动态表名拼接）。</p>
 */
@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id")
})
public interface BizMonthlyOperationAnalysisMapper extends BaseMapper<BizMonthlyOperationAnalysis> {

    /**
     * §9 十类归类规则（SQL 侧单一事实源，与 Java 侧 {@code MonthlyAnalysisService.CATEGORY_NAMES}
     * 的中文名一一对应）。
     * <p>归类优先级（命中即停，避免一个账户被计入两类而重复汇总）：
     * 直接费四类由 cost_category 判定 → 招待 → 差旅车辆 → 财税 → 专业服务 → 措施 → 管理 →
     * 未归类。间接费的判定文本取 {@code COALESCE(cost_subcategory, account_name)}
     * （子类未填时降级用账户名，仍无关键词则如实归 UNCLASSIFIED，不猜类）。</p>
     * <p>⚠ 与驾驶舱 §7.2「七类成本结构」是<b>两套不同口径</b>：七类把间接费合成
     * 措施/管理/商务三类，本表按 §9 拆成 措施/管理/招待/差旅车辆/专业服务/财税 六类，
     * 故关键词表不同（例如「检测试验费」在七类归措施、在本表归专业服务），不可互相套用。</p>
     */
    String CATEGORY_CASE_SQL =
            "CASE "
            + "  WHEN cost_category = 'LABOR' THEN 'LABOR' "
            + "  WHEN cost_category = 'MATERIAL' THEN 'MATERIAL' "
            + "  WHEN cost_category = 'MACHINE' THEN 'MACHINE' "
            + "  WHEN cost_category = 'SUBCONTRACT' THEN 'SUBCONTRACT' "
            + "  WHEN COALESCE(cost_subcategory, account_name) REGEXP '招待' THEN 'ENTERTAIN' "
            + "  WHEN COALESCE(cost_subcategory, account_name) REGEXP '差旅|车辆|交通' THEN 'TRAVEL_VEHICLE' "
            + "  WHEN COALESCE(cost_subcategory, account_name) REGEXP '税|财税' THEN 'TAX' "
            + "  WHEN COALESCE(cost_subcategory, account_name) REGEXP '审计|咨询|专家|检测|试验|勘察|设计' THEN 'PROFESSIONAL' "
            + "  WHEN COALESCE(cost_subcategory, account_name) REGEXP '措施|临设|临时设施|安全|文明|防护|脚手架|排水|围挡' THEN 'MEASURE' "
            + "  WHEN COALESCE(cost_subcategory, account_name) REGEXP '管理|工资|房租|水电|生活|办公|会议|保险' THEN 'ADMIN' "
            + "  ELSE 'UNCLASSIFIED' "
            + "END";

    /**
     * 按 §9 十类聚合 CBS 成本账户：预算（baseline）/ 累计发生（actual）/ 预计最终（forecast）/ 账户数。
     *
     * @param projectId 项目ID（必填；本表按项目粒度生成，不做公司级汇总）
     * @return 每行 {categoryCode, budgetAmount, cumulativeOccurred, forecastFinal, accountCount}
     */
    @Select("SELECT " + CATEGORY_CASE_SQL + " AS categoryCode, "
            + "       COALESCE(SUM(baseline_amount), 0) AS budgetAmount, "
            + "       COALESCE(SUM(actual_amount), 0)   AS cumulativeOccurred, "
            + "       COALESCE(SUM(forecast_amount), 0) AS forecastFinal, "
            + "       COUNT(*) AS accountCount "
            + "  FROM biz_cost_account "
            + " WHERE deleted = 0 AND project_id = #{projectId} "
            + " GROUP BY categoryCode")
    List<Map<String, Object>> sumCbsByCategory(@Param("projectId") Long projectId);

    /**
     * 按直接费四类聚合合同的累计已付（累计支付列的唯一权威来源）。
     * <p>口径：{@code cumulative_paid} 由付款申请<b>审批通过</b>时回写
     * （{@code PaymentApplyService.onApproved}），属<b>审批口径</b>而非银行现金口径，
     * 调用方必须标明（本表以 {@code paid_basis=APPROVAL_WRITEBACK} 落库）。</p>
     * <p>表范围与 {@link ContractPayableMapper#sumPayableOutstanding(Long)} 一致：
     * 材料取 biz_purchase_contract（付款回写路由目标），<b>不含 biz_expense_contract</b>
     * （付款链路不回写它，纳入会虚增）。</p>
     *
     * @param projectId 项目ID
     * @return 每行 {categoryCode, cumulativePaid}，仅含 LABOR/MATERIAL/MACHINE/SUBCONTRACT 四类
     */
    @Select("SELECT 'LABOR' AS categoryCode, COALESCE(SUM(cumulative_paid), 0) AS cumulativePaid "
            + "  FROM biz_labor_contract WHERE deleted = 0 AND project_id = #{projectId} "
            + "UNION ALL "
            + "SELECT 'MATERIAL', COALESCE(SUM(cumulative_paid), 0) "
            + "  FROM biz_purchase_contract WHERE deleted = 0 AND project_id = #{projectId} "
            + "UNION ALL "
            + "SELECT 'MACHINE', COALESCE(SUM(cumulative_paid), 0) "
            + "  FROM biz_machine_contract WHERE deleted = 0 AND project_id = #{projectId} "
            + "UNION ALL "
            + "SELECT 'SUBCONTRACT', COALESCE(SUM(cumulative_paid), 0) "
            + "  FROM biz_subcontract WHERE deleted = 0 AND project_id = #{projectId}")
    List<Map<String, Object>> sumContractPaidByCategory(@Param("projectId") Long projectId);

    /**
     * 取上月行的「累计发生」（本月发生 = 本月累计 − 上月累计）。
     * <p>无上月行时返回空列表 → 本月发生置 null 且 {@code occurred_basis=NO_BASELINE}，
     * <b>不得用 0 冒充「本月无发生」</b>。</p>
     *
     * @param projectId    项目ID
     * @param lastMonth    上月 yyyy-MM
     * @return 每行 {categoryCode, cumulativeOccurred}
     */
    @Select("SELECT category_code AS categoryCode, cumulative_occurred AS cumulativeOccurred "
            + "  FROM biz_monthly_operation_analysis "
            + " WHERE deleted = 0 AND project_id = #{projectId} AND analysis_month = #{lastMonth}")
    List<Map<String, Object>> selectLastMonthOccurred(@Param("projectId") Long projectId,
                                                     @Param("lastMonth") String lastMonth);

    /**
     * 已建 CBS 账户的项目ID清单（定时任务遍历用；无账户的项目生成了也全是 0，跳过更省）。
     */
    @Select("SELECT DISTINCT project_id FROM biz_cost_account WHERE deleted = 0 AND project_id IS NOT NULL")
    List<Long> selectProjectIdsWithAccounts();

    /**
     * 合计校验用：某项目某月的累计发生合计（与 CBS actual 总额比对，防归类漏项）。
     */
    @Select("SELECT COALESCE(SUM(cumulative_occurred), 0) FROM biz_monthly_operation_analysis "
            + "WHERE deleted = 0 AND project_id = #{projectId} AND analysis_month = #{month}")
    BigDecimal sumOccurredByMonth(@Param("projectId") Long projectId, @Param("month") String month);
}
