package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizOtherContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface BizOtherContractMapper extends BaseMapper<BizOtherContract> {

    /**
     * 原子累加合同累计已付金额（付款申请审批通过时回写，避免并发丢失更新）
     *
     * @param contractId 合同ID
     * @param amount     本次付款金额
     * @return 影响行数
     */
    @Update("UPDATE biz_other_contract SET cumulative_paid = COALESCE(cumulative_paid, 0) + #{amount} " +
            "WHERE id = #{contractId} AND deleted = 0")
    int addCumulativePaid(@Param("contractId") Long contractId, @Param("amount") BigDecimal amount);
}
