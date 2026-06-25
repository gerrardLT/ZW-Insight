package com.zwinsight.system.domain.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 租户功能模块枚举
 */
@Getter
public enum TenantModuleEnum {

    TENDER("TENDER", "投标管理", "/api/v1/tender"),
    BUDGET("BUDGET", "预算管理", "/api/v1/budget"),
    PURCHASE("PURCHASE", "采购管理", "/api/v1/purchase"),
    LABOR("LABOR", "劳务管理", "/api/v1/labor"),
    MATERIAL("MATERIAL", "材料管理", "/api/v1/material"),
    MACHINE("MACHINE", "机械管理", "/api/v1/machine"),
    SUBCONTRACT("SUBCONTRACT", "分包管理", "/api/v1/subcontract"),
    SITE("SITE", "现场管理", "/api/v1/site"),
    FINANCE("FINANCE", "财务管理", "/api/v1/finance"),
    HR("HR", "行政人事", "/api/v1/hr"),
    PRICE_COMPARE("PRICE_COMPARE", "三方比价", "/api/v1/price-compare"),
    DASHBOARD("DASHBOARD", "看板分析", "/api/v1/dashboard");

    /** 模块编码 */
    private final String code;

    /** 模块名称 */
    private final String name;

    /** API 路径前缀 */
    private final String apiPrefix;

    TenantModuleEnum(String code, String name, String apiPrefix) {
        this.code = code;
        this.name = name;
        this.apiPrefix = apiPrefix;
    }

    /**
     * 获取所有模块编码
     */
    public static List<String> allCodes() {
        return Arrays.stream(values()).map(TenantModuleEnum::getCode).toList();
    }

    /**
     * 判断模块编码是否合法
     */
    public static boolean isValidCode(String code) {
        return Arrays.stream(values()).anyMatch(m -> m.getCode().equals(code));
    }

    /**
     * 根据 API 路径匹配模块编码
     */
    public static String getModuleCodeByPath(String requestPath) {
        for (TenantModuleEnum module : values()) {
            if (requestPath.startsWith(module.getApiPrefix())) {
                return module.getCode();
            }
        }
        return null;
    }
}
