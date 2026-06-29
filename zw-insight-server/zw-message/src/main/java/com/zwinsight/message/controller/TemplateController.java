package com.zwinsight.message.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.message.domain.MsgTemplate;
import com.zwinsight.message.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 消息模板接口
 */
@RestController("messageTemplateController")
@RequestMapping("/api/v1/message/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public R<PageResult<MsgTemplate>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String templateName) {
        return R.ok(templateService.page(page, size, templateName));
    }

    @GetMapping("/{id}")
    public R<MsgTemplate> getById(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody MsgTemplate template) {
        templateService.save(template);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody MsgTemplate template) {
        template.setId(id);
        templateService.update(template);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }
}
