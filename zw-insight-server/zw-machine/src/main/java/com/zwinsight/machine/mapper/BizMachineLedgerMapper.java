package com.zwinsight.machine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.machine.domain.BizMachineLedger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BizMachineLedgerMapper extends BaseMapper<BizMachineLedger> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM biz_machine_ledger WHERE id = #{id} "
            + "AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
    BizMachineLedger lockById(@org.apache.ibatis.annotations.Param("id") Long id,
                            @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);
}
