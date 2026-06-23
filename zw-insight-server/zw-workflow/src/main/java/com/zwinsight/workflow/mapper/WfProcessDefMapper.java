package com.zwinsight.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.workflow.domain.WfProcessDef;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程定义扩展 Mapper
 */
@Mapper
public interface WfProcessDefMapper extends BaseMapper<WfProcessDef> {
}
