package com.zwinsight.budget.context;

/**
 * 预算警告线程上下文
 * <p>
 * 使用 ThreadLocal 保存当前请求中的预算警告信息。
 * 在 AOP 切面中设置，在 ResponseBodyAdvice 中读取并写入响应头后清除。
 * </p>
 */
public final class BudgetWarningContext {

    private static final ThreadLocal<String> WARNING_HOLDER = new ThreadLocal<>();

    private BudgetWarningContext() {
        // 工具类禁止实例化
    }

    /**
     * 设置预算警告信息
     */
    public static void setWarning(String msg) {
        WARNING_HOLDER.set(msg);
    }

    /**
     * 获取预算警告信息
     */
    public static String getWarning() {
        return WARNING_HOLDER.get();
    }

    /**
     * 清除线程变量（防止内存泄漏）
     */
    public static void clear() {
        WARNING_HOLDER.remove();
    }
}
