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
    @Select("SELECT COALESCE(SUM(s.total_amount), 0) FROM biz_machine_work_settlement s " +
            "WHERE s.project_id = #{projectId} AND s.status = 2 AND s.deleted = 0")
    BigDecimal sumApprovedAmountByProject(@Param("projectId") Long projectId);
}
