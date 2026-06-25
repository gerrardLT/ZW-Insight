package com.zwinsight.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 租户功能模块配置请求
 */
@Data
public class TenantModulesRequest {

    /** 功能模块编码列表 */
    private List<String> modules;
}
