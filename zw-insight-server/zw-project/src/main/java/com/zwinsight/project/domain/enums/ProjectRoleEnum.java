package com.zwinsight.project.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 项目角色枚举
 */
@Getter
@RequiredArgsConstructor
public enum ProjectRoleEnum {

    PROJECT_MANAGER("PROJECT_MANAGER", "项目经理"),
    CONSTRUCTOR("CONSTRUCTOR", "施工员"),
    SAFETY_OFFICER("SAFETY_OFFICER", "安全员"),
    QUALITY_OFFICER("QUALITY_OFFICER", "质量员"),
    MATERIAL_OFFICER("MATERIAL_OFFICER", "材料员"),
    FINANCE_OFFICER("FINANCE_OFFICER", "财务人员"),
    ARCHIVIST("ARCHIVIST", "资料员");

    private final String code;
    private final String label;

    /**
     * 根据 code 获取枚举
     */
    public static ProjectRoleEnum fromCode(String code) {
        for (ProjectRoleEnum role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("无效的项目角色: " + code);
    }

    /**
     * 校验角色代码是否合法
     */
    public static boolean isValid(String code) {
        for (ProjectRoleEnum role : values()) {
            if (role.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
