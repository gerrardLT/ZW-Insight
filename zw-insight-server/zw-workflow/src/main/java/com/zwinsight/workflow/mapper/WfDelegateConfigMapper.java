package com.zwinsight.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.workflow.domain.WfDelegateConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批委托配置 Mapper
 */
@Mapper
public interface WfDelegateConfigMapper extends BaseMapper<WfDelegateConfig> {
}
