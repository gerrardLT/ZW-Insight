package com.zwinsight.system.aspect;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysOperLog;
import com.zwinsight.system.service.SysLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 操作日志切面单元测试
 * <p>验证旁路落库行为：成功/失败均写入 sys_oper_log，异常原样上抛，
 * 落库失败不影响业务返回。</p>
 */
@ExtendWith(MockitoExtension.class)
class OperLogAspectTest {

    @Mock
    private SysLogService logService;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private OperLogAspect aspect;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    /** 带注解的目标方法载体 */
    static class AnnotatedTarget {
        @OperLog(module = "测试模块", operType = "INSERT", description = "新增测试")
        public String create(String arg) {
            return "ok";
        }

        @OperLog(module = "测试模块", operType = "DELETE", description = "删除测试")
        public void remove() {
            // 用于模拟业务异常
        }
    }

    private ProceedingJoinPoint buildJoinPoint(String methodName, Class<?>... paramTypes) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method;
        try {
            method = AnnotatedTarget.class.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(AnnotatedTarget.class.getName());
        return pjp;
    }

    @Test
    @DisplayName("成功路径：执行业务并落库，result=success")
    void around_success_savesLog() throws Throwable {
        ProceedingJoinPoint pjp = buildJoinPoint("create", String.class);
        when(pjp.proceed()).thenReturn("ok");
        when(pjp.getArgs()).thenReturn(new Object[]{"param-a"});

        Object result = aspect.around(pjp);

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<SysOperLog> captor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(logService).saveOperLog(captor.capture());
        SysOperLog log = captor.getValue();
        assertThat(log.getModule()).isEqualTo("测试模块");
        assertThat(log.getOperType()).isEqualTo("INSERT");
        assertThat(log.getDescription()).isEqualTo("新增测试");
        assertThat(log.getResult()).isEqualTo("success");
        assertThat(log.getMethodName()).contains("AnnotatedTarget.create");
        assertThat(log.getOperTime()).isNotNull();
        assertThat(log.getParams()).contains("param-a");
    }

    @Test
    @DisplayName("失败路径：业务异常原样上抛，仍落库 error 记录")
    void around_failure_stillSavesLog() throws Throwable {
        ProceedingJoinPoint pjp = buildJoinPoint("remove");
        when(pjp.proceed()).thenThrow(new IllegalStateException("biz error"));
        when(pjp.getArgs()).thenReturn(new Object[]{});

        assertThatThrownBy(() -> aspect.around(pjp))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("biz error");

        ArgumentCaptor<SysOperLog> captor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(logService).saveOperLog(captor.capture());
        SysOperLog log = captor.getValue();
        assertThat(log.getOperType()).isEqualTo("DELETE");
        assertThat(log.getResult()).startsWith("error").contains("biz error");
    }

    @Test
    @DisplayName("用户上下文存在时回填操作人姓名/账号")
    void around_withUserContext_fillsOperName() throws Throwable {
        SecurityContextHolder.setUserId(7L);
        ProceedingJoinPoint pjp = buildJoinPoint("create", String.class);
        when(pjp.proceed()).thenReturn("ok");
        when(pjp.getArgs()).thenReturn(new Object[]{});
        SysUser user = new SysUser();
        user.setRealName("张三");
        user.setUsername("zhangsan");
        when(userMapper.selectById(7L)).thenReturn(user);

        aspect.around(pjp);

        ArgumentCaptor<SysOperLog> captor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(logService).saveOperLog(captor.capture());
        SysOperLog log = captor.getValue();
        assertThat(log.getOperName()).isEqualTo("张三");
        assertThat(log.getOperAccount()).isEqualTo("zhangsan");
    }

    @Test
    @DisplayName("落库失败不影响业务返回值")
    void around_logSaveFailure_businessUnaffected() throws Throwable {
        ProceedingJoinPoint pjp = buildJoinPoint("create", String.class);
        when(pjp.proceed()).thenReturn("ok");
        when(pjp.getArgs()).thenReturn(new Object[]{});
        doThrow(new RuntimeException("db down")).when(logService).saveOperLog(any());

        Object result = aspect.around(pjp);

        assertThat(result).isEqualTo("ok");
    }
}
