package com.zwinsight.finance.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.dto.ReceivableDrillInfoRequest;
import com.zwinsight.finance.service.ReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应收台账接口（V2026_57 只读查询 + V2026_69 下钻 8 级链）
 * <p>查询：台账分页 + 账龄分析（回款风险下钻数据源，驾驶舱资金中心消费）；
 * 下钻：§10 八级链查询与人工登记项维护。</p>
 * <p>权限说明：写接口（drill-info）沿用类级 {@code finance:view} 并以 {@code @OperLog} 强制留痕——
 * 权限目录（{@code 47_V2026_45_2__permission_guard_catalog.sql}）仅登记了 {@code finance:view}，
 * 另标未登记的 {@code finance:manage} 会造成“无法通过角色配置授予”的伪权限
 * （仅 SUPER_ADMIN 靠豁免能调）。如需收紧，先在权限目录登记后再启用，
 * 与 {@code CockpitController} 的 /risk/scan 同处理方式。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/receivable")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class ReceivableController {

    private final ReceivableService receivableService;

    /**
     * 分页查询应收台账（按到期日升序，最紧急在前）
     */
    @GetMapping("/page")
    public R<PageResult<BizReceivable>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(receivableService.page(page, size, projectId, status));
    }

    /**
     * 应收账龄分析（按项目 × 账龄桶汇总 OPEN 余额，项目按逾期余额降序）
     */
    @GetMapping("/aging")
    public R<Map<String, Object>> aging(@RequestParam(required = false) Long projectId) {
        return R.ok(receivableService.aging(projectId));
    }

    /**
     * §10 下钻 8 级链：项目 → 应收款 → 对应工程节点 → 应收日期 → 实际申请日期
     * → 甲方审核状态 → 负责人 → 下一步动作。
     * <p>每级带 {@code registered} 标记，false 即「未登记」（前端显示“未登记”并引导补登），
     * <b>不用默认值冒充已登记</b>。路径为 {@code /drill/{id}} 而非 {@code /{id}/drill-chain}，
     * 避开与 {@code /page}、{@code /aging} 的字面量路径优先级歧义。</p>
     */
    @GetMapping("/drill/{id}")
    public R<Map<String, Object>> drillChain(@PathVariable Long id) {
        return R.ok(receivableService.getDrillChain(id));
    }

    /**
     * 维护下钻信息（§10 第 3/5/6/7/8 级的人工登记项）。
     * <p>字符串字段 null=不修改、空串=清空；日期字段 null=不修改且不支持清空；
     * 甲方审核状态非法值报 400（不静默当作未登记）。</p>
     */
    @PutMapping("/drill/{id}")
    @OperLog(module = "应收台账", operType = "UPDATE", description = "维护应收下钻信息")
    public R<BizReceivable> updateDrillInfo(@PathVariable Long id,
                                           @RequestBody ReceivableDrillInfoRequest request) {
        return R.ok(receivableService.updateDrillInfo(id, request));
    }
}
