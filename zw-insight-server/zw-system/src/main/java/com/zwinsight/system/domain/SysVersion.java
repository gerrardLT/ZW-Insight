package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 系统版本记录实体
 */
@Data
@TableName("sys_version")
public class SysVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 版本号(语义化: x.y.z) */
    private String versionNo;

    /** 发布日期 */
    private LocalDate releaseDate;

    /** 更新日志(Markdown格式) */
    private String changelog;

    /** 操作人ID */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
