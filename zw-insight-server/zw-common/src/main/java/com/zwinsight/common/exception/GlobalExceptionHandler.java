package com.zwinsight.common.exception;

import com.zwinsight.common.reference.ReferenceExistsException;
import com.zwinsight.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 引用存在异常 - 删除操作被引用校验拦截
     */
    @ExceptionHandler(ReferenceExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Map<String, Object>> handleReferenceExistsException(ReferenceExistsException e, HttpServletRequest request) {
        log.warn("引用校验拦截删除 [{}]: {}", request.getRequestURI(), e.getMessage());
        Map<String, Object> data = new HashMap<>();
        data.put("entityName", e.getEntityName());
        data.put("totalCount", e.getTotalCount());
        data.put("references", e.getReferences());
        return new R<>(400, e.getMessage(), data);
    }

    /**
     * 数据权限异常 — 无法获取用户上下文或权限校验失败
     */
    @ExceptionHandler(DataPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleDataPermissionException(DataPermissionException e, HttpServletRequest request) {
        log.warn("数据权限异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 操作二次确认异常 — 返回真实 HTTP 状态码（449 需要二次确认 / 403 密码错误 / 423 临时锁定），
     * 以便前端直接依据 HTTP 状态码区分二次确认场景（449 触发密码输入对话框）。
     */
    @ExceptionHandler(SecondaryConfirmException.class)
    public ResponseEntity<R<Void>> handleSecondaryConfirmException(SecondaryConfirmException e, HttpServletRequest request) {
        log.warn("二次确认拦截 [{}] status={}: {}", request.getRequestURI(), e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(R.fail(e.getStatus(), e.getMessage()));
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return R.fail(400, message);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return R.fail(400, message);
    }

    /**
     * 缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return R.fail(400, "缺少请求参数: " + e.getParameterName());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMethod());
        return R.fail(405, "请求方法不支持: " + e.getMethod());
    }

    /**
     * 资源未找到
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return R.fail(404, "资源未找到");
    }

    /**
     * 文件上传超出 multipart 上限（spring.servlet.multipart.max-file-size，当前 100MB）。
     * 不处理则落入兜底 Exception 返回 500，此处转为 413 + 友好提示（2026-08-14 审计批次 6 收尾项）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public R<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("文件上传超限 [{}]: {}", request.getRequestURI(), e.getMessage());
        return R.fail(413, "文件大小超过上限（最大 100MB），请压缩后重试");
    }

    /**
     * 请求体 JSON 结构或字段类型错误。不应落入 500；调用方应修正请求格式。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e,
                                                           HttpServletRequest request) {
        log.warn("请求体格式错误 [{}]: {}", request.getRequestURI(), e.getMessage());
        return R.fail(400, "请求数据格式错误，请检查字段类型和请求结构");
    }

    /**
     * 数据库唯一键/外键等完整性约束冲突。具体写接口仍应优先做业务预检，
     * 此处仅作并发写入与遗漏场景的统一兜底，避免向用户返回无意义的系统 500。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public R<Void> handleDataIntegrityViolationException(DataIntegrityViolationException e,
                                                           HttpServletRequest request) {
        log.warn("数据完整性冲突 [{}]: {}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return R.fail(409, "数据已存在或仍被其他数据引用，请检查后重试");
    }

    /**
     * 其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return R.fail(500, "系统内部错误，请稍后重试");
    }
}
