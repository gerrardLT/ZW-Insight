package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.finance.domain.BizPaymentApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizPaymentApplyMapper extends BaseMapper<BizPaymentApply> {

    /**
     * 已批未付的「剩余未付额」合计（驾驶舱 §9.1「已批未付」卡，口径见资金流转 §3.1）。
     * <p><b>为何用剩余未付额而非 payment_amount 全额</b>：银行流水支持部分勾稽
     * （{@code biz_bank_flow.match_amount}，V2026_56），一笔 100 万申请已实付 60 万时，
     * 若仍按 100 万计入会重复夸大 40 万资金压力。故此处扣减已勾稽合计。</p>
     * <p>勾稽金额口径与 {@code BankFlowService.sumMatchedAmount} 一致：
     * {@code match_amount} 为空时取流水整笔 {@code amount}（兼容存量整笔勾稽）。</p>
     * <p>本方法受类级 {@code @DataPermission} 约束（按 project_id 过滤），
     * 与驾驶舱其他指标的数据可见范围保持一致。</p>
     *
     * @param projectId 项目ID（空=当前用户可见范围内全部）
     * @return 剩余未付额合计（无单据时 0）
     */
    @Select("<script>"
            + "SELECT IFNULL(SUM(p.payment_amount - COALESCE(f.matched, 0)), 0) "
            + "FROM biz_payment_apply p "
            + "LEFT JOIN (SELECT matched_id, SUM(COALESCE(match_amount, amount)) AS matched "
            + "             FROM biz_bank_flow "
            + "            WHERE deleted = 0 AND reconciled = 1 AND matched_type = 'PAYMENT_APPLY' "
            + "            GROUP BY matched_id) f ON f.matched_id = p.id "
            + "WHERE p.deleted = 0 AND p.status = 'APPROVED' AND p.pay_status != 'PAID' "
            + "<if test='projectId != null'> AND p.project_id = #{projectId}</if>"
            + "</script>")
    BigDecimal sumApprovedUnpaidRemaining(@Param("projectId") Long projectId);

    /**
     * 已批未付剩余未付额（限定项目集合）——驾驶舱全局筛选器按「所属公司」维度下钻时使用。
     * <p>口径与 {@link #sumApprovedUnpaidRemaining(Long)} 完全一致（同样扣减银行已勾稽额），
     * 仅把过滤条件换成项目集合。<b>调用方须保证集合非空</b>（空集合会生成 {@code IN ()} 语法错误），
     * Service 层应先判空并直接返回 0。同样受类级 {@code @DataPermission} 约束。</p>
     *
     * @param projectIds 项目ID集合（非空）
     * @return 剩余未付额合计（无单据时 0）
     */
    @Select("<script>"
            + "SELECT IFNULL(SUM(p.payment_amount - COALESCE(f.matched, 0)), 0) "
            + "FROM biz_payment_apply p "
            + "LEFT JOIN (SELECT matched_id, SUM(COALESCE(match_amount, amount)) AS matched "
            + "             FROM biz_bank_flow "
            + "            WHERE deleted = 0 AND reconciled = 1 AND matched_type = 'PAYMENT_APPLY' "
            + "            GROUP BY matched_id) f ON f.matched_id = p.id "
            + "WHERE p.deleted = 0 AND p.status = 'APPROVED' AND p.pay_status != 'PAID' "
            + "AND p.project_id IN <foreach collection='projectIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    BigDecimal sumApprovedUnpaidRemainingByProjects(@Param("projectIds") Collection<Long> projectIds);
}
