package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizEntertainmentDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 招待费专项明细 Mapper（V2026_60）
 * <p>分析口径统一由 {@link #APPROVED_SOURCE_GUARD} 约束：仅统计已生效（APPROVED）报销单的
 * 招待费明细，草稿/驳回单据不计入，避免未生效数据污染分析与预警。</p>
 */
@Mapper
public interface BizEntertainmentDetailMapper extends BaseMapper<BizEntertainmentDetail> {

    /**
     * 报销明细 → 主表已生效守卫（按 source_type 分别关联项目报销/个人报销主表）。
     * <p>不可写成子查询内 {@code d.source_type='PROJECT'} 的形式——那会让个人报销明细
     * 恒被排除（EXISTS 永假）。两张主表雪花 ID 空间独立，必须按来源分别关联。</p>
     */
    String APPROVED_SOURCE_GUARD =
            " AND ( (d.source_type = 'PROJECT' AND EXISTS ("
            + "        SELECT 1 FROM biz_project_reimbursement r "
            + "        WHERE r.id = d.reimbursement_id AND r.deleted = 0 AND r.status = 'APPROVED')) "
            + "    OR (d.source_type = 'PERSONAL' AND EXISTS ("
            + "        SELECT 1 FROM biz_personal_reimbursement r "
            + "        WHERE r.id = d.reimbursement_id AND r.deleted = 0 AND r.status = 'APPROVED')) )";

    /**
     * 招待费分析聚合（docs/资金流转流程.md §6.3 分析维度）。SQL 聚合，不在内存逐条累加。
     *
     * @param projectId  项目ID（可空=全部项目）
     * @param sourceType 报销来源（PROJECT/PERSONAL，可空=两者合计）
     * @return 单行聚合：totalAmount 总额、totalCount 次数、maxSingleAmount 单次最高、
     *         avgPerCapita 人均金额（总额 ÷ 双方人数合计）、noReasonCount 无事由笔数、
     *         noHostCount 无对象笔数、noPreApprovalCount 无事前审批笔数、noInvoiceCount 发票不完整笔数
     */
    @Select("<script>"
            + "SELECT COALESCE(SUM(d.amount), 0) AS totalAmount, "
            + "       COUNT(*) AS totalCount, "
            + "       COALESCE(MAX(d.amount), 0) AS maxSingleAmount, "
            + "       COALESCE(SUM(d.amount) / NULLIF(SUM(COALESCE(e.host_count,0) + COALESCE(e.guest_count,0)), 0), 0) AS avgPerCapita, "
            + "       COALESCE(SUM(CASE WHEN e.entertain_reason IS NULL OR e.entertain_reason = '' THEN 1 ELSE 0 END), 0) AS noReasonCount, "
            + "       COALESCE(SUM(CASE WHEN e.host_company IS NULL OR e.host_company = '' THEN 1 ELSE 0 END), 0) AS noHostCount, "
            + "       COALESCE(SUM(CASE WHEN COALESCE(e.pre_approved, 0) = 0 THEN 1 ELSE 0 END), 0) AS noPreApprovalCount, "
            + "       COALESCE(SUM(CASE WHEN COALESCE(e.has_invoice, 0) = 0 THEN 1 ELSE 0 END), 0) AS noInvoiceCount "
            + "FROM biz_reimbursement_detail d "
            + "JOIN biz_entertainment_detail e ON e.reimbursement_detail_id = d.id AND e.deleted = 0 "
            + "WHERE d.deleted = 0 AND d.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + APPROVED_SOURCE_GUARD
            + "  <if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + "  <if test='sourceType != null'> AND d.source_type = #{sourceType} </if>"
            + "</script>")
    Map<String, Object> analyzeEntertainment(@Param("projectId") Long projectId,
                                            @Param("sourceType") String sourceType);

    /**
     * 按经办人聚合招待费累计金额（§6.3「责任人累计金额」维度，用于高频/高额报销识别）。
     *
     * @param projectId 项目ID（可空=全部项目）
     * @return [{handlerId, handlerName, totalAmount, totalCount}]，按金额降序
     */
    @Select("<script>"
            + "SELECT e.handler_id AS handlerId, e.handler_name AS handlerName, "
            + "       COALESCE(SUM(d.amount), 0) AS totalAmount, COUNT(*) AS totalCount "
            + "FROM biz_reimbursement_detail d "
            + "JOIN biz_entertainment_detail e ON e.reimbursement_detail_id = d.id AND e.deleted = 0 "
            + "WHERE d.deleted = 0 AND d.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + APPROVED_SOURCE_GUARD
            + "  <if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + "GROUP BY e.handler_id, e.handler_name "
            + "ORDER BY totalAmount DESC"
            + "</script>")
    List<Map<String, Object>> sumByHandler(@Param("projectId") Long projectId);

    /**
     * 单笔金额超阈值的招待费笔数（§11「单笔过高」预警数据源）。
     *
     * @param threshold 单笔金额阈值
     * @return 超阈值笔数
     */
    @Select("SELECT COUNT(*) FROM biz_reimbursement_detail d "
            + "JOIN biz_entertainment_detail e ON e.reimbursement_detail_id = d.id AND e.deleted = 0 "
            + "WHERE d.deleted = 0 AND d.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + "  AND d.amount > #{threshold} "
            + APPROVED_SOURCE_GUARD)
    Long countOverThreshold(@Param("threshold") BigDecimal threshold);

    /**
     * 同一经办人在同一天多笔招待的记录数（§11「同一天多笔招待」预警数据源）。
     * <p>注：本查询含 {@code <if>} 动态标签，必须用 {@code <script>} 包裹，
     * 否则标签会被当字面 SQL 送入数据库；反之不含动态标签的查询不得包 script，
     * 此时比较符必须写原字符（写 &amp;gt; 会原样入库导致语法错误）。</p>
     *
     * @param projectId 项目ID（可空=全部项目）
     * @return 命中「同人同日 ≥2 笔」的笔数合计
     */
    @Select("<script>"
            + "SELECT COALESCE(SUM(cnt), 0) FROM ("
            + "  SELECT COUNT(*) AS cnt "
            + "  FROM biz_reimbursement_detail d "
            + "  JOIN biz_entertainment_detail e ON e.reimbursement_detail_id = d.id AND e.deleted = 0 "
            + "  WHERE d.deleted = 0 AND d.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + "  <if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + APPROVED_SOURCE_GUARD
            + "  GROUP BY e.handler_id, e.entertain_date HAVING COUNT(*) >= 2"
            + ") t"
            + "</script>")
    Long countSameHandlerSameDay(@Param("projectId") Long projectId);

    /**
     * 存在已生效招待费明细的项目 ID 列表（风险扫描限定范围用，避免对全部项目做 N+1 分析）。
     *
     * @return 项目 ID 列表（去重；无项目归属的招待费不包含在内）
     */
    @Select("SELECT DISTINCT e.project_id FROM biz_reimbursement_detail d "
            + "JOIN biz_entertainment_detail e ON e.reimbursement_detail_id = d.id AND e.deleted = 0 "
            + "WHERE d.deleted = 0 AND d.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + "  AND e.project_id IS NOT NULL "
            + APPROVED_SOURCE_GUARD)
    List<Long> listProjectIdsWithEntertainment();

    /**
     * 招待费按月聚合（§6.3「月度变化」分析维度，旧实现缺失）。
     * <p>月份归属取报销单业务日期 reimbursement_date（PROJECT/PERSONAL 两类主表分别关联），
     * 主表无该日期时回退明细创建时间——回退仅作兜底，不因此丢数据。</p>
     *
     * @param projectId 项目ID（可空=全部项目）
     * @param fromDate  起始日期（含），用于限定近 N 月
     * @return [{month: 'yyyy-MM', amount, cnt}]，按月份升序
     */
    @Select("<script>"
            + "SELECT DATE_FORMAT(COALESCE(pr.reimbursement_date, pp.reimbursement_date, DATE(d.created_at)), '%Y-%m') AS month, "
            + "       COALESCE(SUM(d.amount),0) AS amount, COUNT(*) AS cnt "
            + "FROM biz_reimbursement_detail d "
            + "JOIN biz_entertainment_detail e ON e.reimbursement_detail_id = d.id AND e.deleted = 0 "
            + "LEFT JOIN biz_project_reimbursement pr ON d.source_type = 'PROJECT' "
            + "       AND pr.id = d.reimbursement_id AND pr.deleted = 0 "
            + "LEFT JOIN biz_personal_reimbursement pp ON d.source_type = 'PERSONAL' "
            + "       AND pp.id = d.reimbursement_id AND pp.deleted = 0 "
            + "WHERE d.deleted = 0 AND d.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + APPROVED_SOURCE_GUARD
            + "  AND COALESCE(pr.reimbursement_date, pp.reimbursement_date, DATE(d.created_at)) &gt;= #{fromDate} "
            + "  <if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + "GROUP BY month ORDER BY month"
            + "</script>")
    List<Map<String, Object>> monthlyTrend(@Param("projectId") Long projectId,
                                          @Param("fromDate") java.time.LocalDate fromDate);

    /**
     * 当月招待费计划限额（§6.3「预算执行率」分母、§11「超月度限额」预警基准）。
     * <p>数据源：月度资金计划科目明细（biz_fund_plan_detail，V2026_58）中
     * 科目 EXP-INDIRECT-ENTERTAIN 的 APPROVED 计划金额。</p>
     * <p><b>故意不用 COALESCE</b>：无计划明细时必须返回 null 而非 0，
     * 否则调用方会把“未编制计划”误判为“限额 0 元 → 任何支出都超限”而大量误报。</p>
     *
     * @param projectId 项目ID（空=公司级计划 project_id IS NULL）
     * @return 计划限额；无计划明细时 null
     */
    @Select("<script>"
            + "SELECT SUM(fd.amount) FROM biz_fund_plan_detail fd "
            + "JOIN biz_fund_monthly_plan p ON p.id = fd.plan_id AND p.deleted = 0 AND p.status = 'APPROVED' "
            + "WHERE fd.deleted = 0 AND fd.direction = 'EXPENSE' "
            + "  AND fd.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + "  AND p.plan_year = #{year} AND p.plan_month = #{month} "
            + "  <choose>"
            + "    <when test='projectId != null'> AND p.project_id = #{projectId}</when>"
            + "    <otherwise> AND p.project_id IS NULL</otherwise>"
            + "  </choose>"
            + "</script>")
    BigDecimal sumEntertainmentPlanLimit(@Param("projectId") Long projectId,
                                        @Param("year") int year,
                                        @Param("month") int month);

    /**
     * 同一经办人单月招待笔数超阈值的命中数（§11「同一经办人频繁报销」预警数据源）。
     *
     * @param projectId   项目ID（可空=全部项目）
     * @param monthPrefix 月份前缀 yyyy-MM
     * @param countLimit  笔数阈值
     * @return 超阈值的（经办人,月份）组合数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM ("
            + "  SELECT e.handler_id "
            + "  FROM biz_reimbursement_detail d "
            + "  JOIN biz_entertainment_detail e ON e.reimbursement_detail_id = d.id AND e.deleted = 0 "
            + "  WHERE d.deleted = 0 AND d.category_code = 'EXP-INDIRECT-ENTERTAIN' "
            + "    AND DATE_FORMAT(e.entertain_date, '%Y-%m') = #{monthPrefix} "
            + "    <if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + APPROVED_SOURCE_GUARD
            + "  GROUP BY e.handler_id HAVING COUNT(*) > #{countLimit}"
            + ") t"
            + "</script>")
    Long countFrequentHandlerInMonth(@Param("projectId") Long projectId,
                                    @Param("monthPrefix") String monthPrefix,
                                    @Param("countLimit") int countLimit);
}
