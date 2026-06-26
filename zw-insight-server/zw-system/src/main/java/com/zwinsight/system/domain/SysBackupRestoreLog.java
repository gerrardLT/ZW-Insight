package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 备份恢复操作日志实体
 */
@Data
@TableName("sys_backup_restore_log")
public class SysBackupRestoreLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 源备份记录ID */
    private Long backupId;

    /** 操作人ID */
    private Long operatorId;

    /** 恢复时间 */
    private LocalDateTime restoreTime;

    /** 恢复结果: SUCCESS/FAILED */
    private String result;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
