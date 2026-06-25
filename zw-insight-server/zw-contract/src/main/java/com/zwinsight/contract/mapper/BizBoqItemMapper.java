package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizBoqItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

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
}
