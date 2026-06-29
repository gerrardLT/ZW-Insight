package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysPost;
import com.zwinsight.system.service.SysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位管理接口
 */
@RestController
@RequestMapping("/api/v1/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService postService;

    @GetMapping
    public R<PageResult<SysPost>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String postName,
            @RequestParam(required = false) Integer status) {
        return R.ok(postService.page(page, size, postName, status));
    }

    @GetMapping("/{id}")
    public R<SysPost> getById(@PathVariable Long id) {
        return R.ok(postService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysPost post) {
        postService.save(post);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysPost post) {
        post.setId(id);
        postService.update(post);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        postService.batchDelete(ids);
        return R.ok();
    }
}
