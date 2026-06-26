package com.zwinsight.security.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录设备实体
 *
 * <p>对应表 sys_login_device，用于记录用户在各终端的登录设备及 Token，
 * 支持设备列表查询、远程注销、最大设备数自动淘汰等功能。</p>
 *
 * <p>该表结构独立于 {@code BaseEntity}（不含租户、逻辑删除、乐观锁等列），
 * 因此直接实现 {@link Serializable} 并显式声明各字段。</p>
 */
@Data
@TableName("sys_login_device")
public class SysLoginDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 设备唯一标识
     */
    private String deviceId;

    /**
     * 设备名称（如: iPhone 15 Pro）
     */
    private String deviceName;

    /**
     * 操作系统（iOS/Android/Windows/MacOS）
     */
    private String os;

    /**
     * 登录IP
     */
    private String ipAddress;

    /**
     * IP归属地（省份|城市）
     */
    private String location;

    /**
     * 登录Token
     */
    private String token;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;

    /**
     * 状态: 1=活跃 0=已注销
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
