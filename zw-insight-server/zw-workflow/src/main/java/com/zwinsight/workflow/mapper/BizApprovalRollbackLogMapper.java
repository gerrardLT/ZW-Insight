package com.zwinsight.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.workflow.domain.BizApprovalRollbackLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批数据回滚日志 Mapper
 */
@Mapper
public interface BizApprovalRollbackLogMapper extends BaseMapper<BizApprovalRollbackLog> {
}
