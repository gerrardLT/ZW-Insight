package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizContractExpiryLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同到期提醒日志 Mapper
 */
@Mapper
public interface BizContractExpiryLogMapper extends BaseMapper<BizContractExpiryLog> {
}
