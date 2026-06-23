package com.zwinsight.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.workflow.domain.WfRollbackAction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回滚注册表 Mapper
 */
@Mapper
public interface WfRollbackActionMapper extends BaseMapper<WfRollbackAction> {
}
