package com.zwinsight.site.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.site.domain.BizReminderConfig;
import com.zwinsight.site.dto.ReminderConfigUpdateRequest;
import com.zwinsight.site.service.ReminderConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 整改催办配置管理接口
 */
@RestController
@RequestMapping("/api/v1/site/reminder-config")
@RequiredArgsConstructor
public class ReminderConfigController {

    private final ReminderConfigService reminderConfigService;

    /**
     * 查询当前租户的催办配置
     */
    @GetMapping
    public R<BizReminderConfig> getConfig() {
        Long tenantId = SecurityContextHolder.getTenantId();
        return R.ok(reminderConfigService.getConfig(tenantId));
    }

    /**
     * 更新当前租户的催办配置
     */
    @PutMapping
    public R<Void> updateConfig(@Valid @RequestBody ReminderConfigUpdateRequest request) {
        Long tenantId = SecurityContextHolder.getTenantId();
        reminderConfigService.updateConfig(tenantId, request);
        return R.ok();
    }
}
