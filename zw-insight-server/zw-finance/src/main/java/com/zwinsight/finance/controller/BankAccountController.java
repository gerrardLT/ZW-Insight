package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 银行账户管理接口
 */
@RestController
@RequestMapping("/api/v1/finance/bank-account")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    /**
     * 分页查询
     */
    @GetMapping
    public R<PageResult<BizBankAccount>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) Long projectId) {
        return R.ok(bankAccountService.page(page, size, accountType, projectId));
    }

    /**
     * 新增
     */
    @PostMapping
    public R<Void> save(@RequestBody BizBankAccount bankAccount) {
        bankAccountService.save(bankAccount);
        return R.ok();
    }

    /**
     * 更新
     */
    @PutMapping
    public R<Void> update(@RequestBody BizBankAccount bankAccount) {
        bankAccountService.update(bankAccount);
        return R.ok();
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bankAccountService.delete(id);
        return R.ok();
    }
}
