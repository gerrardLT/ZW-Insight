package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.dto.FinanceLockCreateRequest;
import com.zwinsight.finance.domain.dto.FinanceLockDTO;
import com.zwinsight.finance.service.FinanceLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 财务封账管理接口
 */
@RestController
@RequestMapping("/api/v1/finance/lock")
@RequiredArgsConstructor
public class FinanceLockController {

    private final FinanceLockService financeLockService;

    /**
     * 创建封账记录
     * <p>季度封账会展开为该季度包含的3个自然月，故返回列表</p>
     *
     * @param request 封账请求，包含 period（YYYY-MM）与 lockType（MONTHLY / QUARTERLY）
     * @return 创建的封账记录列表
     */
    @PostMapping
    public R<List<FinanceLockDTO>> createLock(@RequestBody FinanceLockCreateRequest request) {
        return R.ok(financeLockService.createLock(request.getPeriod(), request.getLockType()));
    }

    /**
     * 解封操作
     *
     * @param id 封账记录ID
     * @return 解封后的记录
     */
    @DeleteMapping("/{id}/unlock")
    public R<FinanceLockDTO> unlock(@PathVariable Long id) {
        return R.ok(financeLockService.unlock(id));
    }

    /**
     * 分页查询封账记录
     *
     * @param pageNum  页码，默认1
     * @param pageSize 每页大小，默认20
     * @return 分页结果
     */
    @GetMapping("/page")
    public R<PageResult<FinanceLockDTO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeLockService.getPage(pageNum, pageSize));
    }

    /**
     * 查询指定年月封账状态
     *
     * @param period 期间，格式 YYYY-MM
     * @return 封账状态：LOCKED / UNLOCKED / null（未封账过）
     */
    @GetMapping("/status")
    public R<String> status(@RequestParam String period) {
        return R.ok(financeLockService.getStatus(period));
    }
}
