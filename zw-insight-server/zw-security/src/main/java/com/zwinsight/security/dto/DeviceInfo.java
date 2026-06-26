package com.zwinsight.security.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备信息 VO
 *
 * <p>登录时从请求中捕获的设备信息，用于记录登录设备。</p>
 */
@Data
public class DeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

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
}
