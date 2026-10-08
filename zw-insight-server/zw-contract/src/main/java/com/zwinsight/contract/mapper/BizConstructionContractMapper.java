package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.contract.domain.BizConstructionContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizConstructionContractMapper extends BaseMapper<BizConstructionContract> {

    /**
     * 项目最终结算审批通过后，批量将该项目下生效中的施工合同置为已结算
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Update("UPDATE biz_construction_contract SET status = 'SETTLED' " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    int settleByProjectId(@Param("projectId") Long projectId);

    /**
     * 原子累加合同累计产值（产值上报审批通过时回写，避免并发丢失更新）
     *
     * @param contractId 合同ID
     * @param amount     本期产值
     * @return 影响行数
     */
    @Update("UPDATE biz_construction_contract " +
            "SET cumulative_output = COALESCE(cumulative_output, 0) + #{amount} " +
            "WHERE id = #{contractId} AND deleted = 0")
    int addCumulativeOutput(@Param("contractId") Long contractId, @Param("amount") java.math.BigDecimal amount);

    /**
     * 带上限的原子累加合同累计产值：累计+本期 ≤ 合同额+累计变更额 才更新。
     * <p>额度检查与写入在同一条 UPDATE 内完成（行锁串行化），取代"先读上限再加"：
     * 两张产值单并发批准时后到者命中 0 行，不会双双越限。</p>
     *
     * @return 影响行数；0 表示越限或合同已不存在
     */
    @Update("UPDATE biz_construction_contract " +
            "SET cumulative_output = COALESCE(cumulative_output, 0) + #{amount} " +
            "WHERE id = #{contractId} AND deleted = 0 " +
            "AND COALESCE(cumulative_output, 0) + #{amount} <= contract_amount + COALESCE(cumulative_change_amount, 0)")
    int addCumulativeOutputWithinLimit(@Param("contractId") Long contractId, @Param("amount") java.math.BigDecimal amount);

    /**
     * 原子累加合同累计开票金额（开票申请审批通过时回写，避免并发丢失更新）
     *
     * @param contractId 合同ID
     * @param amount     本次开票金额
     * @return 影响行数
     */
    @Update("UPDATE biz_construction_contract " +
            "SET cumulative_invoice_amount = COALESCE(cumulative_invoice_amount, 0) + #{amount} " +
            "WHERE id = #{contractId} AND deleted = 0")
    int addCumulativeInvoiceAmount(@Param("contractId") Long contractId, @Param("amount") java.math.BigDecimal amount);
}
