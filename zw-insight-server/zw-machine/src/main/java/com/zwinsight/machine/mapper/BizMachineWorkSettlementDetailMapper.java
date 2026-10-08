package com.zwinsight.machine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.machine.domain.BizMachineWorkSettlementDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 机械工作量结算明细 Mapper
 */
@Mapper
public interface BizMachineWorkSettlementDetailMapper extends BaseMapper<BizMachineWorkSettlementDetail> {

    /**
     * 汇总项目所有已审批结算单总金额
     *
     * @param projectId 项目ID
     * @return 累计总金额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM ("
            + "SELECT h.settlement_amount AS amount FROM biz_machine_settlement h "
            + "WHERE h.project_id = #{projectId} AND h.status = 'APPROVED' AND h.deleted = 0 "
            + "UNION ALL SELECT d.subtotal AS amount FROM biz_machine_work_settlement_detail d "
            + "JOIN biz_machine_work_settlement s ON s.id = d.settlement_id AND s.tenant_id = d.tenant_id "
            + "JOIN biz_machine_contract c ON c.id = d.contract_id AND c.tenant_id = d.tenant_id "
            + "AND c.project_id = s.project_id AND c.deleted = 0 "
            + "WHERE s.project_id = #{projectId} AND s.status = 2 AND s.deleted = 0) amounts")
    BigDecimal sumApprovedAmountByProject(@Param("projectId") Long projectId);
}
