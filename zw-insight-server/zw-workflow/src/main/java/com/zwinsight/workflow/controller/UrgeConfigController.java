package com.zwinsight.workflow.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.workflow.domain.WfUrgeConfig;
import com.zwinsight.workflow.dto.UrgeConfigRequest;
import com.zwinsight.workflow.service.UrgeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 催办配置接口
 */
@RestController
@RequestMapping("/api/v1/workflow/urge-config")
@RequiredArgsConstructor
public class UrgeConfigController {

    private final UrgeConfigService urgeConfigService;

    /**
     * 获取催办配置
     */
    @GetMapping
    public R<WfUrgeConfig> getConfig() {
        return R.ok(urgeConfigService.getConfig());
    }

    /**
     * 保存/更新催办配置
     */
    @PostMapping
    public R<Void> saveConfig(@RequestBody UrgeConfigRequest request) {
        WfUrgeConfig config = new WfUrgeConfig();
        config.setTimeoutHours(request.getTimeoutHours());
        config.setIntervalHours(request.getIntervalHours());
        config.setMaxUrgeCount(request.getMaxUrgeCount());
        config.setAutoUrgeEnabled(request.getAutoUrgeEnabled());
        urgeConfigService.saveConfig(config);
        return R.ok();
    }
}
