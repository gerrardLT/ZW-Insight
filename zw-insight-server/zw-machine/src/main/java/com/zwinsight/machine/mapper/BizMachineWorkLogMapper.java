package com.zwinsight.machine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BizMachineWorkLogMapper extends BaseMapper<BizMachineWorkLog> {

    /**
     * 批量更新工作日志的结算状态
     *
     * @param ids    工作日志ID列表
     * @param status 目标状态（UNSETTLED/SETTLED）
     * @return 影响行数
     */
    int batchUpdateSettlementStatus(@Param("ids") List<Long> ids, @Param("status") String status);
}
