package com.zwinsight.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.system.domain.SysBackupRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库备份记录 Mapper
 */
@Mapper
public interface SysBackupRecordMapper extends BaseMapper<SysBackupRecord> {
}
