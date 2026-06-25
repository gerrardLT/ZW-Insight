package com.zwinsight.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 数据权限异常
 * <p>
 * 当已标注 @DataPermission 注解的方法无法获取用户上下文时抛出，
 * 防止在无有效安全上下文的情况下执行需要数据权限过滤的查询。
 * </p>
 */
@Getter
public class DataPermissionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码（默认 403 — 权限不足）
     */
    private final int code;

    public DataPermissionException(String message) {
        super(message);
        this.code = 403;
    }

    public DataPermissionException(int code, String message) {
        super(message);
        this.code = code;
    }

    public DataPermissionException(String message, Throwable cause) {
        super(message, cause);
        this.code = 403;
    }

    public DataPermissionException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
