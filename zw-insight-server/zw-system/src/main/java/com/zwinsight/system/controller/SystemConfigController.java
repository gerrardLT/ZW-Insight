package com.zwinsight.system.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.dto.ConfigUpdateRequest;
import com.zwinsight.system.domain.vo.SysConfigVO;
import com.zwinsight.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置接口
 * <p>
 * 权限要求：仅系统管理员（ADMIN 角色）可访问修改类接口，
 * 由前端菜单权限 + RBAC 角色权限体系控制访问。
 * </p>
 */
@RestController
@RequestMapping("/v1/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 按分组查询配置列表
     */
    @GetMapping("/group/{group}")
    public R<List<SysConfigVO>> listByGroup(@PathVariable String group) {
        return R.ok(systemConfigService.listByGroup(group));
    }

    /**
     * 更新配置值
     */
    @PutMapping
    public R<Void> updateConfig(@RequestBody ConfigUpdateRequest request) {
        systemConfigService.updateConfig(request.getConfigKey(), request.getConfigValue());
        return R.ok();
    }

    /**
     * 批量更新配置
     */
    @PutMapping("/batch")
    public R<Void> batchUpdate(@RequestBody List<ConfigUpdateRequest> requests) {
        systemConfigService.batchUpdate(requests);
        return R.ok();
    }

    /**
     * 恢复默认值
     */
    @PostMapping("/{key}/reset")
    public R<Void> resetToDefault(@PathVariable String key) {
        systemConfigService.resetToDefault(key);
        return R.ok();
    }
}
