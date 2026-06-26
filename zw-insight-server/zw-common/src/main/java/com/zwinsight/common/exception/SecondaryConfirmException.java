package com.zwinsight.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 操作二次确认异常。
 * <p>
 * 与 {@link BusinessException} 不同，该异常会被全局异常处理器映射为真实的 HTTP 状态码
 * （而非统一包装在 HTTP 200 响应体中），以便前端可直接根据 HTTP 状态码区分二次确认场景：
 * </p>
 * <ul>
 *   <li>449 - 需要二次确认（缺少确认密码）</li>
 *   <li>403 - 确认密码错误</li>
 *   <li>423 - 二次确认能力被临时锁定</li>
 * </ul>
 */
@Getter
public class SecondaryConfirmException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 真实 HTTP 状态码（449 / 403 / 423）。
     */
    private final int status;

    public SecondaryConfirmException(int status, String message) {
        super(message);
        this.status = status;
    }
}
