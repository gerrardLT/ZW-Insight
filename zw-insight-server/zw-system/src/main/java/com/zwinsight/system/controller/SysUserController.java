package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.system.service.SysUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 人员管理接口
 */
@RestController
@RequestMapping("/api/v1/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @GetMapping
    public R<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) {
        return R.ok(userService.page(page, size, username, realName, orgId, status));
    }

    @GetMapping("/{id}")
    public R<SysUser> getById(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysUser user) {
        userService.save(user);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        userService.update(user);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        userService.batchDelete(ids);
        return R.ok();
    }

    @PutMapping("/status")
    public R<Void> batchUpdateStatus(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) params.get("ids")).stream()
                .map(Number::longValue).toList();
        Integer status = (Integer) params.get("status");
        userService.updateStatus(ids, status);
        return R.ok();
    }

    @PostMapping("/import")
    public R<Integer> importUsers(@RequestParam("file") MultipartFile file) {
        int count = userService.importUsers(file);
        return R.ok("成功导入 " + count + " 条数据", count);
    }

    @GetMapping("/export")
    public void exportUsers(
            HttpServletResponse response,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) throws IOException {
        userService.exportUsers(response, username, realName, orgId, status);
    }

    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return R.ok();
    }

    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String newPassword = params.get("newPassword");
        userService.resetPassword(id, newPassword);
        return R.ok();
    }
}
