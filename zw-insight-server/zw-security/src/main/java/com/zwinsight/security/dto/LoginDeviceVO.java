package com.zwinsight.security.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录设备响应 VO
 *
 * <p>设备管理页面展示的设备记录，{@code isCurrent} 标识是否为当前登录设备。</p>
 */
@Data
public class LoginDeviceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备记录ID
     */
    private Long id;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 操作系统
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
     * 是否为当前登录设备
     */
    private Boolean isCurrent;
}
