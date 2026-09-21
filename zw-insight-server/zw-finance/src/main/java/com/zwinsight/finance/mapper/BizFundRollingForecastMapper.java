package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资金滚动预测快照 Mapper
 */
@Mapper
public interface BizFundRollingForecastMapper extends BaseMapper<BizFundRollingForecast> {
}
