package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizBalanceReconciliation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 银行存款余额调节表 Mapper
 */
@Mapper
public interface BizBalanceReconciliationMapper extends BaseMapper<BizBalanceReconciliation> {
}
