package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库备份记录实体
 */
@Data
@TableName("sys_backup_record")
public class SysBackupRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 备份文件名 */
    private String fileName;

    /** 文件大小(bytes) */
    private Long fileSize;

    /** 备份耗时(毫秒) */
    private Long durationMs;

    /** MinIO存储路径 */
    private String storagePath;

    /** 备份类型: MANUAL/SCHEDULED */
    private String backupType;

    /** 状态: SUCCESS/FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 操作人ID */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
