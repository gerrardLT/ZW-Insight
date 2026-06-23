package com.zwinsight.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.workflow.domain.WfApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录 Mapper
 */
@Mapper
public interface WfApprovalRecordMapper extends BaseMapper<WfApprovalRecord> {
}
