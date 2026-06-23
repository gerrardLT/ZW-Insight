package com.zwinsight.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.system.domain.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
