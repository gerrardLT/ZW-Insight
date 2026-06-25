package com.zwinsight.workflow.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.workflow.domain.WfDelegateConfig;
import com.zwinsight.workflow.dto.DelegateConfigRequest;
import com.zwinsight.workflow.service.DelegateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批委托/代理接口
 * <p>
 * 支持设置委托期间和代理人，期间内新审批自动转给代理人，委托结束恢复。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/workflow/delegate")
@RequiredArgsConstructor
public class DelegateController {

    private final DelegateService delegateService;

    /**
     * 创建委托配置
     */
    @PostMapping
    public R<Long> createDelegation(@RequestBody DelegateConfigRequest request) {
        Long id = delegateService.createDelegation(
                request.getDelegateId(),
                request.getStartTime(),
                request.getEndTime(),
                request.getReason()
        );
        return R.ok(id);
    }

    /**
     * 取消委托
     */
    @DeleteMapping("/{id}")
    public R<Void> cancelDelegation(@PathVariable Long id) {
        delegateService.cancelDelegation(id);
        return R.ok();
    }

    /**
     * 查询我的委托配置列表（我委托给别人的）
     */
    @GetMapping("/my")
    public R<List<WfDelegateConfig>> getMyDelegations() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(delegateService.getMyDelegations(userId));
    }

    /**
     * 查询当前生效的委托（我的）
     */
    @GetMapping("/active")
    public R<WfDelegateConfig> getActiveDelegation() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(delegateService.getActiveDelegation(userId));
    }

    /**
     * 查询委托给我的（我作为代理人的生效委托列表）
     */
    @GetMapping("/to-me")
    public R<List<WfDelegateConfig>> getDelegationsToMe() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(delegateService.getDelegationsToMe(userId));
    }
}
