package com.zwinsight.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.workflow.domain.WfUrgeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 催办记录 Mapper
 */
@Mapper
public interface WfUrgeRecordMapper extends BaseMapper<WfUrgeRecord> {

    /**
     * 统计指定任务的催办次数
     */
    @Select("SELECT COUNT(*) FROM wf_urge_record WHERE task_id = #{taskId} AND deleted = 0")
    int countByTaskId(@Param("taskId") String taskId);
}
