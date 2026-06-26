package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.dto.TaxRateDTO;
import com.zwinsight.finance.domain.dto.TaxRateRequest;
import com.zwinsight.finance.service.TaxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 税率字典管理接口
 */
@RestController
@RequestMapping("/api/v1/finance/tax-rate")
@RequiredArgsConstructor
public class TaxRateController {

    private final TaxRateService taxRateService;

    /**
     * 新增税率
     */
    @PostMapping
    public R<TaxRateDTO> create(@RequestBody TaxRateRequest request) {
        return R.ok(taxRateService.create(request.getName(), request.getRateValue()));
    }

    /**
     * 修改税率
     */
    @PutMapping("/{id}")
    public R<TaxRateDTO> update(@PathVariable Long id, @RequestBody TaxRateRequest request) {
        return R.ok(taxRateService.update(id, request.getName(), request.getRateValue()));
    }

    /**
     * 停用税率（逻辑删除，状态变为 DISABLED）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taxRateService.delete(id);
        return R.ok();
    }

    /**
     * 查询启用状态税率列表（按创建时间升序）
     */
    @GetMapping("/list")
    public R<List<TaxRateDTO>> listEnabled() {
        return R.ok(taxRateService.listEnabled());
    }

    /**
     * 查询全部税率列表（含停用，按创建时间升序）
     */
    @GetMapping("/all")
    public R<List<TaxRateDTO>> listAll() {
        return R.ok(taxRateService.listAll());
    }
}
