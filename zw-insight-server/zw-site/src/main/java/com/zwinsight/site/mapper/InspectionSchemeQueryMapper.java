package com.zwinsight.site.mapper;

import com.zwinsight.site.dto.InspectionSchemeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 检查方案跨模块查询 Mapper
 * 通过 @Select 直接查询 basedata 的 bd_inspection_scheme 表
 */
@Mapper
public interface InspectionSchemeQueryMapper {

    /**
     * 按检查类型查已启用方案列表（分页由 Service 层控制）
     */
    @Select("SELECT id, scheme_name, scheme_type " +
            "FROM bd_inspection_scheme " +
            "WHERE scheme_type = #{schemeType} AND status = 1 AND deleted = 0 " +
            "ORDER BY created_at DESC " +
            "LIMIT #{offset}, #{limit}")
    List<InspectionSchemeVO> listByType(@Param("schemeType") String schemeType,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 按检查类型统计已启用方案总数
     */
    @Select("SELECT COUNT(*) FROM bd_inspection_scheme " +
            "WHERE scheme_type = #{schemeType} AND status = 1 AND deleted = 0")
    long countByType(@Param("schemeType") String schemeType);

    /**
     * 查单个方案详情（含 content JSON）
     */
    @Select("SELECT id, scheme_name, scheme_type, content, status " +
            "FROM bd_inspection_scheme " +
            "WHERE id = #{schemeId} AND deleted = 0")
    InspectionSchemeQueryMapper.SchemeRawDTO selectSchemeById(@Param("schemeId") Long schemeId);

    /**
     * 方案原始数据 DTO（含 content JSON 字段）
     */
    class SchemeRawDTO {
        private Long id;
        private String schemeName;
        private String schemeType;
        private String content;
        private Integer status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSchemeName() { return schemeName; }
        public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
        public String getSchemeType() { return schemeType; }
        public void setSchemeType(String schemeType) { this.schemeType = schemeType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }
}
