package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizBankAccountGroup;
import com.zwinsight.finance.service.BankAccountGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 银行账户分组接口（多级树，司库集中管控）
 */
@RestController
@RequestMapping("/api/v1/finance/bank-account-group")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class BankAccountGroupController {

    private final BankAccountGroupService groupService;

    /**
     * 分组树
     */
    @GetMapping("/tree")
    public R<List<BizBankAccountGroup>> tree() {
        return R.ok(groupService.tree());
    }

    /**
     * 列表查询（按名称/编码/层级过滤）
     */
    @GetMapping("/list")
    public R<List<BizBankAccountGroup>> list(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) Integer level) {
        return R.ok(groupService.list(groupName, groupCode, level));
    }

    /**
     * 新增分组
     */
    @PostMapping
    public R<Void> save(@RequestBody BizBankAccountGroup group) {
        groupService.save(group);
        return R.ok();
    }

    /**
     * 更新分组
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizBankAccountGroup group) {
        groupService.update(id, group);
        return R.ok();
    }

    /**
     * 删除分组
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return R.ok();
    }

    /**
     * 启用/停用分组
     */
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        groupService.toggleStatus(id, enabled);
        return R.ok();
    }
}
