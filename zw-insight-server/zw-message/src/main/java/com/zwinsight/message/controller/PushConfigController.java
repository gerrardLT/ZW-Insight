package com.zwinsight.message.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.message.domain.MsgPushConfig;
import com.zwinsight.message.service.PushConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 推送渠道配置接口
 */
@RestController
@RequestMapping("/api/v1/message/push-config")
@RequiredArgsConstructor
public class PushConfigController {

    private final PushConfigService pushConfigService;

    /**
     * 分页查询推送渠道配置
     */
    @GetMapping
    public R<PageResult<MsgPushConfig>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String businessType) {
        return R.ok(pushConfigService.page(page, size, businessType));
    }

    /**
     * 根据ID查询详情
     */
    @GetMapping("/{id}")
    public R<MsgPushConfig> getById(@PathVariable Long id) {
        return R.ok(pushConfigService.getById(id));
    }

    /**
     * 根据业务类型查询配置
     */
    @GetMapping("/by-type/{businessType}")
    public R<MsgPushConfig> getByBusinessType(@PathVariable String businessType) {
        return R.ok(pushConfigService.getByBusinessType(businessType));
    }

    /**
     * 新增推送渠道配置
     */
    @PostMapping
    public R<Void> save(@RequestBody MsgPushConfig config) {
        pushConfigService.save(config);
        return R.ok();
    }

    /**
     * 更新推送渠道配置
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody MsgPushConfig config) {
        config.setId(id);
        pushConfigService.update(config);
        return R.ok();
    }

    /**
     * 删除推送渠道配置
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        pushConfigService.delete(id);
        return R.ok();
    }
}
