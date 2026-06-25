package com.zwinsight.site.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.site.domain.BizInspectionDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BizInspectionDetailMapper extends BaseMapper<BizInspectionDetail> {

    /**
     * 逻辑删除指定检查记录下的全部检查明细
     */
    @Update("UPDATE biz_inspection_detail SET deleted = 1 WHERE inspection_id = #{inspectionId} AND deleted = 0")
    int deleteByInspectionId(@Param("inspectionId") Long inspectionId);
}
