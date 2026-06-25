package com.zwinsight.system.domain.enums;

import lombok.Getter;

/**
 * 租户状态枚举
 */
@Getter
public enum TenantStatusEnum {

    NORMAL(1, "正常"),
    DISABLED(2, "已停用"),
    EXPIRED(3, "已过期");

    /** 状态值 */
    private final int code;

    /** 中文描述 */
    private final String description;

    TenantStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static TenantStatusEnum fromCode(int code) {
        for (TenantStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的租户状态: " + code);
    }
}
