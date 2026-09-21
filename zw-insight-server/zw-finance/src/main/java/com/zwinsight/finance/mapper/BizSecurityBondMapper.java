package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizSecurityBond;
import org.apache.ibatis.annotations.Mapper;

/**
 * 保证金台账 Mapper
 */
@Mapper
public interface BizSecurityBondMapper extends BaseMapper<BizSecurityBond> {
}
