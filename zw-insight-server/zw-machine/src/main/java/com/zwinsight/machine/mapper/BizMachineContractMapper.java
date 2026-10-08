package com.zwinsight.machine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.machine.domain.BizMachineContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizMachineContractMapper extends BaseMapper<BizMachineContract> {

    /** 原子累加合同累计结算（审批通过按明细逐合同调用；支持负数冲销，写法同劳务合同 Mapper） */
    @Update("UPDATE biz_machine_contract SET cumulative_settlement = COALESCE(cumulative_settlement, 0) + #{amount} "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND project_id = #{projectId} "
            + "AND deleted = 0 AND status = 'EFFECTIVE' "
            + "AND COALESCE(cumulative_settlement, 0) + #{amount} >= 0 "
            + "AND COALESCE(cumulative_settlement, 0) + #{amount} <= contract_amount")
    int addSettlement(@Param("id") Long id, @Param("amount") BigDecimal amount,
                      @Param("tenantId") Long tenantId, @Param("projectId") Long projectId);
}
