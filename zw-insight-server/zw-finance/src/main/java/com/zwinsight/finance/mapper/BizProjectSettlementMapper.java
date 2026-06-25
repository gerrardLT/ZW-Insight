package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.dto.ExpenseContractInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizProjectSettlementMapper extends BaseMapper<BizProjectSettlement> {

    /**
     * 查询指定项目下指定类型的支出合同基本信息
     * 用于生成结算合同明细
     */
    @Select("SELECT id, contract_code AS contractCode, contract_name AS contractName, " +
            "contract_amount AS contractAmount, cumulative_settlement AS cumulativeSettlement, " +
            "cumulative_paid AS cumulativePaid " +
            "FROM ${tableName} " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    List<ExpenseContractInfo> selectExpenseContracts(@Param("projectId") Long projectId,
                                                     @Param("tableName") String tableName);
}
