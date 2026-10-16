package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizChangeVisa;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BizChangeVisaMapper extends BaseMapper<BizChangeVisa> {
    @org.apache.ibatis.annotations.Update("UPDATE biz_change_visa SET status = 'APPROVED' " +
            "WHERE id = #{id} AND deleted = 0 AND status IN ('SUBMITTED', 'REJECTED')")
    int approvePending(@org.apache.ibatis.annotations.Param("id") Long id);

    @org.apache.ibatis.annotations.Update("UPDATE biz_change_visa SET status = #{status} " +
            "WHERE id = #{id} AND deleted = 0 AND status = 'SUBMITTED'")
    int transitionSubmitted(@org.apache.ibatis.annotations.Param("id") Long id,
                            @org.apache.ibatis.annotations.Param("status") String status);

    /**
     * 统计某变更事件当前有效（未删除，不限状态）的关联签证数。
     * 单一生效源判定用：事件审批回写前查——大于 0 则事件侧跳过合同累计回写（由签证写入）。
     */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM biz_change_visa " +
            "WHERE change_event_id = #{eventId} AND deleted = 0")
    int countActiveByChangeEvent(@org.apache.ibatis.annotations.Param("eventId") Long eventId);
}
