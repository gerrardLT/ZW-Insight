package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BdCompany;
import com.zwinsight.basedata.service.CompanyService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 自持公司接口
 */
@RestController
@RequestMapping("/api/v1/basedata/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public R<PageResult<BdCompany>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) Integer status) {
        return R.ok(companyService.page(page, size, companyName, status));
    }

    @GetMapping("/{id}")
    public R<BdCompany> getById(@PathVariable Long id) {
        return R.ok(companyService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BdCompany company) {
        companyService.save(company);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BdCompany company) {
        company.setId(id);
        companyService.update(company);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return R.ok();
    }
}
