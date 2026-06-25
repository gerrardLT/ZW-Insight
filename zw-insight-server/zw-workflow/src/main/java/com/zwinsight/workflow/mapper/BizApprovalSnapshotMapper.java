package com.zwinsight.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.workflow.domain.BizApprovalSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批数据快照 Mapper
 */
@Mapper
public interface BizApprovalSnapshotMapper extends BaseMapper<BizApprovalSnapshot> {
}
