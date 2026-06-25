package com.zwinsight.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 预算校验结果
 */
@Data
@AllArgsConstructor
public class BudgetCheckResult {

    public enum Status {
        /** 通过 */
        PASS,
        /** 警告（允许提交但展示超预算警告） */
        WARN,
        /** 阻止（禁止提交） */
        BLOCK
    }

    private Status status;
    private String message;

    public static BudgetCheckResult pass() {
        return new BudgetCheckResult(Status.PASS, null);
    }

    public static BudgetCheckResult warn(String msg) {
        return new BudgetCheckResult(Status.WARN, msg);
    }

    public static BudgetCheckResult block(String msg) {
        return new BudgetCheckResult(Status.BLOCK, msg);
    }
}
