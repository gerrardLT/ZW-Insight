package com.zwinsight.system.aspect;

import cn.hutool.json.JSONUtil;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysOperLog;
import com.zwinsight.system.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志AOP切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysLogService logService;
    private final SysUserMapper userMapper;

    @Around("@annotation(com.zwinsight.system.aspect.OperLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperLog operLog = method.getAnnotation(OperLog.class);

        // 执行目标方法
        Object result;
        String resultStr = "success";
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            resultStr = "error: " + e.getMessage();
            throw e;
        } finally {
            try {
                // 构建操作日志
                SysOperLog sysOperLog = new SysOperLog();
                sysOperLog.setModule(operLog.module());
                sysOperLog.setOperType(operLog.operType());
                sysOperLog.setDescription(operLog.description());
                sysOperLog.setMethodName(signature.getDeclaringTypeName() + "." + method.getName());
                sysOperLog.setOperTime(LocalDateTime.now());
                sysOperLog.setResult(resultStr);

                // 获取请求参数
                Object[] args = joinPoint.getArgs();
                try {
                    sysOperLog.setParams(JSONUtil.toJsonStr(args));
                } catch (Exception e) {
                    sysOperLog.setParams("序列化失败");
                }

                // 获取IP地址
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    sysOperLog.setIpAddress(getIpAddress(request));
                }

                // 获取当前用户信息
                Long userId = SecurityContextHolder.getUserId();
                if (userId != null) {
                    SysUser user = userMapper.selectById(userId);
                    if (user != null) {
                        sysOperLog.setOperName(user.getRealName());
                        sysOperLog.setOperAccount(user.getUsername());
                    }
                }

                // 异步保存日志
                logService.saveOperLog(sysOperLog);
            } catch (Exception e) {
                log.error("保存操作日志失败", e);
            }
        }
        return result;
    }

    /**
     * 获取IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
