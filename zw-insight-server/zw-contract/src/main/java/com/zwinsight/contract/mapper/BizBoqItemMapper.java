package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizBoqItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface BizBoqItemMapper extends BaseMapper<BizBoqItem> {

    /**
     * 根据合同ID逻辑删除全部清单条目
     *
     * @param contractId 合同ID
     * @return 受影响行数
     */
    @Update("UPDATE biz_boq_item SET deleted = 1 WHERE contract_id = #{contractId} AND deleted = 0")
    int deleteByContractId(Long contractId);

    /**
     * 原子累加清单条目已完成工程量（产值上报审批通过时回写）
     *
     * @param id       清单条目ID
     * @param quantity 本期完成工程量
     * @return 受影响行数
     */
    @Update("UPDATE biz_boq_item SET completed_quantity = COALESCE(completed_quantity, 0) + #{quantity} " +
            "WHERE id = #{id} AND deleted = 0")
    int addCompletedQuantity(@Param("id") Long id, @Param("quantity") BigDecimal quantity);
}
