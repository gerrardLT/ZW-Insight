package com.zwinsight.purchase.portal.context;

/**
 * 供应商安全上下文 - 基于 ThreadLocal 存储当前请求的供应商信息
 * <p>
 * 独立于主系统的 SecurityContextHolder，专用于供应商门户认证链。
 */
public class SupplierSecurityContext {

    private static final ThreadLocal<Long> SUPPLIER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> PHONE = new ThreadLocal<>();
    private static final ThreadLocal<String> SUPPLIER_NAME = new ThreadLocal<>();

    public static void setSupplierId(Long supplierId) {
        SUPPLIER_ID.set(supplierId);
    }

    public static Long getSupplierId() {
        return SUPPLIER_ID.get();
    }

    public static void setPhone(String phone) {
        PHONE.set(phone);
    }

    public static String getPhone() {
        return PHONE.get();
    }

    public static void setSupplierName(String supplierName) {
        SUPPLIER_NAME.set(supplierName);
    }

    public static String getSupplierName() {
        return SUPPLIER_NAME.get();
    }

    /**
     * 清除当前线程上下文（请求结束时调用，防止内存泄漏）
     */
    public static void clear() {
        SUPPLIER_ID.remove();
        PHONE.remove();
        SUPPLIER_NAME.remove();
    }
}
