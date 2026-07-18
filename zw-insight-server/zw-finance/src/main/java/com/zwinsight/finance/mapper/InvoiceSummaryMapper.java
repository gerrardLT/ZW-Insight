package com.zwinsight.finance.mapper;

import com.zwinsight.finance.domain.dto.InvoiceSummaryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 发票汇总查询 Mapper
 * <p>
 * 分别按项目维度聚合已开票、已收票数据，由 Service 层合并为统一汇总视图。
 * 直接使用 SQL 关联 biz_project 获取项目名称，避免跨模块依赖。
 * </p>
 */
@Mapper
public interface InvoiceSummaryMapper {

    /**
     * 按项目汇总已开票（已审批）数据
     */
    @Select("<script>" +
            "SELECT a.project_id AS projectId, " +
            "       p.project_name AS projectName, " +
            "       COUNT(*) AS invoicedCount, " +
            "       COALESCE(SUM(a.invoice_amount), 0) AS invoicedAmount, " +
            "       COALESCE(SUM(a.invoice_amount * a.tax_rate / 100), 0) AS invoicedTaxAmount " +
            "FROM biz_invoice_apply a " +
            "LEFT JOIN biz_project p ON p.id = a.project_id " +
            "WHERE a.status = 'APPROVED' AND a.deleted = 0 " +
            "<if test='projectId != null'> AND a.project_id = #{projectId} </if>" +
            "<if test='startDate != null and startDate != \"\"'> AND a.apply_date &gt;= #{startDate} </if>" +
            "<if test='endDate != null and endDate != \"\"'> AND a.apply_date &lt;= #{endDate} </if>" +
            "GROUP BY a.project_id, p.project_name" +
            "</script>")
    List<InvoiceSummaryDTO> summarizeInvoiced(@Param("projectId") Long projectId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    /**
     * 按项目汇总已收票（已审批）数据
     */
    @Select("<script>" +
            "SELECT r.project_id AS projectId, " +
            "       p.project_name AS projectName, " +
            "       COUNT(*) AS receivedCount, " +
            "       COALESCE(SUM(r.invoice_amount), 0) AS receivedAmount, " +
            "       COALESCE(SUM(r.invoice_amount * r.tax_rate / 100), 0) AS receivedTaxAmount " +
            "FROM biz_invoice_received r " +
            "LEFT JOIN biz_project p ON p.id = r.project_id " +
            "WHERE r.status = 'APPROVED' AND r.deleted = 0 " +
            "<if test='projectId != null'> AND r.project_id = #{projectId} </if>" +
            "<if test='startDate != null and startDate != \"\"'> AND r.invoice_date &gt;= #{startDate} </if>" +
            "<if test='endDate != null and endDate != \"\"'> AND r.invoice_date &lt;= #{endDate} </if>" +
            "GROUP BY r.project_id, p.project_name" +
            "</script>")
    List<InvoiceSummaryDTO> summarizeReceived(@Param("projectId") Long projectId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);
}
