package com.zwinsight.system.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户候选选择视图对象（选择器专用轻量对象，杜绝敏感字段泄露）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUserCandidateVO {

    /**
     * 用户ID（保持字符串或序列化安全数字）
     */
    private Long id;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 所属机构/部门名称
     */
    private String orgName;
}
