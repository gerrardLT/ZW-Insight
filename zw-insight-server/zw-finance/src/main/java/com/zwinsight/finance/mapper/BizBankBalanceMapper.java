package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizBankBalance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 银行账户余额登记 Mapper
 */
@Mapper
public interface BizBankBalanceMapper extends BaseMapper<BizBankBalance> {
}
