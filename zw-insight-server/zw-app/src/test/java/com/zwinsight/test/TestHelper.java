package com.zwinsight.test;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.domain.BaseEntity;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 共享测试工具类 - 提供构建测试数据、SecurityContext 管理、响应构造等通用方法。
 * <p>
 * 典型用法：
 * <pre>{@code
 * // 在每个测试类的 @BeforeEach 中设置安全上下文
 * TestHelper.setSecurityContext(1L, 1L);
 *
 * // 在每个测试类的 @AfterEach 中清理安全上下文
 * TestHelper.clearSecurityContext();
 *
 * // 构建带 BaseEntity 字段的实体
 * TestHelper.fillBaseFields(entity, 1L);
 *
 * // 构建分页结果
 * PageResult<Xxx> pageResult = TestHelper.buildPageResult(List.of(item), 1, 10);
 * }</pre>
 */
public final class TestHelper {

    /** 默认测试租户ID */
    public static final Long DEFAULT_TENANT_ID = 1L;

    /** 默认测试用户ID */
    public static final Long DEFAULT_USER_ID = 1L;

    private TestHelper() {
        // 工具类不允许实例化
    }

    // ============ SecurityContextHolder 管理 ============

    /**
     * 设置安全上下文（租户ID + 用户ID）
     */
    public static void setSecurityContext(Long tenantId, Long userId) {
        SecurityContextHolder.setTenantId(tenantId);
        SecurityContextHolder.setUserId(userId);
    }

    /**
     * 使用默认值设置安全上下文
     */
    public static void setSecurityContext() {
        setSecurityContext(DEFAULT_TENANT_ID, DEFAULT_USER_ID);
    }

    /**
     * 清理安全上下文 ThreadLocal（必须在 @AfterEach 中调用，防止内存泄漏）
     */
    public static void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

    // ============ BaseEntity 字段填充 ============

    /**
     * 为 BaseEntity 填充测试常用字段
     *
     * @param entity   实体对象
     * @param id       主键ID
     * @param tenantId 租户ID
     * @return 填充后的实体
     */
    public static <T extends BaseEntity> T fillBaseFields(T entity, Long id, Long tenantId) {
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setCreatedBy(DEFAULT_USER_ID);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        entity.setVersion(1);
        return entity;
    }

    /**
     * 为 BaseEntity 填充测试常用字段（使用默认租户ID）
     */
    public static <T extends BaseEntity> T fillBaseFields(T entity, Long id) {
        return fillBaseFields(entity, id, DEFAULT_TENANT_ID);
    }

    // ============ R<T> 构建工具 ============

    /**
     * 构建成功响应（无数据）
     */
    public static <T> R<T> okResult() {
        return R.ok();
    }

    /**
     * 构建成功响应（带数据）
     */
    public static <T> R<T> okResult(T data) {
        return R.ok(data);
    }

    /**
     * 构建失败响应
     */
    public static <T> R<T> failResult(String message) {
        return R.fail(message);
    }

    /**
     * 构建失败响应（带状态码）
     */
    public static <T> R<T> failResult(int code, String message) {
        return R.fail(code, message);
    }

    // ============ PageResult<T> 构建工具 ============

    /**
     * 构建分页结果
     *
     * @param records 数据列表
     * @param page    当前页码（从 1 开始）
     * @param size    每页大小
     * @return 分页结果
     */
    public static <T> PageResult<T> buildPageResult(List<T> records, int page, int size) {
        Page<T> iPage = new Page<>(page, size);
        iPage.setRecords(records);
        iPage.setTotal(records.size());
        return PageResult.of(iPage);
    }

    /**
     * 构建空分页结果
     */
    public static <T> PageResult<T> emptyPageResult(int page, int size) {
        return buildPageResult(Collections.emptyList(), page, size);
    }

    /**
     * 构建单条数据的分页结果
     */
    public static <T> PageResult<T> singlePageResult(T record, int page, int size) {
        return buildPageResult(List.of(record), page, size);
    }
}
