package com.zwinsight.file.template;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 打印模板 Mapper（基于 sys_template 表，承载 PRINT 类型模板的 Thymeleaf 渲染相关数据）
 *
 * <p>复用 {@link SysTemplate} 实体，新增的 engine_type / business_type / data_query_config
 * 字段用于支持打印模板的引擎选择、业务类型过滤与数据查询配置。</p>
 */
@Mapper
public interface PrintTemplateMapper extends BaseMapper<SysTemplate> {
}
