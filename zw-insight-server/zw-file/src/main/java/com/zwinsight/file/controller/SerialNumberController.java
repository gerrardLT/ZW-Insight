package com.zwinsight.file.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.file.domain.SerialNumberRule;
import com.zwinsight.file.service.SerialNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 编号规则接口
 */
@RestController
@RequestMapping("/api/v1/file/serial")
@RequiredArgsConstructor
public class SerialNumberController {

    private final SerialNumberService serialNumberService;

    @GetMapping
    public R<List<SerialNumberRule>> list() {
        return R.ok(serialNumberService.list());
    }

    @PostMapping
    public R<Void> save(@RequestBody SerialNumberRule rule) {
        serialNumberService.save(rule);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SerialNumberRule rule) {
        rule.setId(id);
        serialNumberService.update(rule);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        serialNumberService.delete(id);
        return R.ok();
    }

    @PostMapping("/generate/{businessType}")
    public R<String> generate(@PathVariable String businessType) {
        return R.ok(serialNumberService.generate(businessType));
    }
}
