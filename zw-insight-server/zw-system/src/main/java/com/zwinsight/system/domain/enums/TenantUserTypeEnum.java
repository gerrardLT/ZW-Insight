package com.zwinsight.system.domain.enums;

import lombok.Getter;

/**
 * 租户用户类型枚举
 */
@Getter
public enum TenantUserTypeEnum {

    TRIAL("TRIAL", "试用版", 30, 10),
    STANDARD("STANDARD", "标准版", 365, 50),
    ENTERPRISE("ENTERPRISE", "企业版", 0, 500);

    /** 枚举值 */
    private final String code;

    /** 中文描述 */
    private final String description;

    /** 默认有效天数（0表示自定义） */
    private final int defaultDays;

    /** 默认最大用户数 */
    private final int defaultMaxUsers;

    TenantUserTypeEnum(String code, String description, int defaultDays, int defaultMaxUsers) {
        this.code = code;
        this.description = description;
        this.defaultDays = defaultDays;
        this.defaultMaxUsers = defaultMaxUsers;
    }

    /**
     * 根据code获取枚举
     */
    public static TenantUserTypeEnum fromCode(String code) {
        for (TenantUserTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的租户类型: " + code);
    }
}
