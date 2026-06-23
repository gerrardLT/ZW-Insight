package com.zwinsight.workflow.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.workflow.domain.WfBusinessType;
import com.zwinsight.workflow.service.BusinessTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务类型管理接口
 */
@RestController
@RequestMapping("/api/v1/workflow/business-type")
@RequiredArgsConstructor
public class BusinessTypeController {

    private final BusinessTypeService businessTypeService;

    /**
     * 查询业务类型树
     */
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> getTree() {
        return R.ok(businessTypeService.getTree());
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public R<WfBusinessType> getById(@PathVariable Long id) {
        return R.ok(businessTypeService.getById(id));
    }

    /**
     * 新增业务类型
     */
    @PostMapping
    public R<WfBusinessType> create(@RequestBody WfBusinessType businessType) {
        return R.ok(businessTypeService.create(businessType));
    }

    /**
     * 更新业务类型
     */
    @PutMapping
    public R<Void> update(@RequestBody WfBusinessType businessType) {
        businessTypeService.update(businessType);
        return R.ok();
    }

    /**
     * 删除业务类型
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        businessTypeService.delete(id);
        return R.ok();
    }
}
