package com.zwinsight.file.template;

import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模板管理接口
 */
@RestController("fileTemplateController")
@RequestMapping("/api/v1/file/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 查询模板列表（按模块 + 类型筛选）
     */
    @GetMapping
    public R<List<SysTemplate>> list(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String templateType) {
        return R.ok(templateService.listByModule(moduleCode, templateType));
    }

    /**
     * 创建模板
     */
    @PostMapping
    public R<SysTemplate> create(@RequestBody SysTemplate template) {
        return R.ok(templateService.create(template));
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysTemplate template) {
        templateService.update(id, template);
        return R.ok();
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }

    /**
     * 渲染打印模板
     * 传入数据变量，对模板中的 {{fieldName}} 占位符进行替换
     */
    @PostMapping("/{id}/render")
    public R<String> render(@PathVariable Long id, @RequestBody Map<String, Object> variables) {
        return R.ok(templateService.renderTemplate(id, variables));
    }
}
