package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BdInspectionScheme;
import com.zwinsight.basedata.service.InspectionSchemeService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 检查方案接口
 */
@RestController
@RequestMapping("/api/v1/basedata/inspection-scheme")
@RequiredArgsConstructor
public class InspectionSchemeController {

    private final InspectionSchemeService schemeService;

    @GetMapping
    public R<PageResult<BdInspectionScheme>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String schemeName,
            @RequestParam(required = false) String schemeType,
            @RequestParam(required = false) Integer status) {
        return R.ok(schemeService.page(page, size, schemeName, schemeType, status));
    }

    @GetMapping("/{id}")
    public R<BdInspectionScheme> getById(@PathVariable Long id) {
        return R.ok(schemeService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BdInspectionScheme scheme) {
        schemeService.save(scheme);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BdInspectionScheme scheme) {
        scheme.setId(id);
        schemeService.update(scheme);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        schemeService.delete(id);
        return R.ok();
    }
}
