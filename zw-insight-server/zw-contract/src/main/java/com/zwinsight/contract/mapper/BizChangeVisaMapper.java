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
}
