package com.zwinsight.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.system.domain.SysBackupRestoreLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份恢复操作日志 Mapper
 */
@Mapper
public interface SysBackupRestoreLogMapper extends BaseMapper<SysBackupRestoreLog> {
}
