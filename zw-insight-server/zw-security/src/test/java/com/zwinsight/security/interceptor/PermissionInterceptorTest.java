package com.zwinsight.security.interceptor;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.security.Logical;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.security.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口级功能权限拦截器单元测试。
 * <p>
 * 覆盖：开关关闭放行、无注解放行、无 userId 401、SUPER_ADMIN 豁免、
 * 命中权限放行（OR）、缺权限 403、AND 逻辑全满足/缺一。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionInterceptor — 接口级功能权限校验")
class PermissionInterceptorTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private PermissionInterceptor interceptor;

    private static final Long USER_ID = 2001L;

    @BeforeEach
    void setUp() {
        // @Value 字段在单元测试中不会注入，默认开启校验
        ReflectionTestUtils.setField(interceptor, "permissionCheckEnabled", true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    // ==================== 放行场景 ====================

    @Test
    @DisplayName("开关关闭 - 即便标注注解也全局放行")
    void disabled_shouldPass() throws Exception {
        ReflectionTestUtils.setField(interceptor, "permissionCheckEnabled", false);
        boolean result = interceptor.preHandle(request, response, handler("needUserAdd"));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("非 HandlerMethod - 直接放行")
    void notHandlerMethod_shouldPass() throws Exception {
        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("无 @RequiresPermission 注解 - opt-in 放行")
    void noAnnotation_shouldPass() throws Exception {
        boolean result = interceptor.preHandle(request, response, handler("plain"));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMIN - 跳过校验直接放行")
    void superAdmin_shouldPass() throws Exception {
        SecurityContextHolder.setUserId(USER_ID);
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("SUPER_ADMIN"));

        boolean result = interceptor.preHandle(request, response, handler("needUserAdd"));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("命中所需权限（OR）- 放行")
    void hasPermission_shouldPass() throws Exception {
        SecurityContextHolder.setUserId(USER_ID);
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("PROJECT_MANAGER"));
        when(sysUserMapper.selectPermissionsByUserId(USER_ID))
                .thenReturn(List.of("system:user:add", "system:user:edit"));

        boolean result = interceptor.preHandle(request, response, handler("needUserAdd"));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("AND 逻辑 - 全部满足放行")
    void andLogic_allMatched_shouldPass() throws Exception {
        SecurityContextHolder.setUserId(USER_ID);
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("PROJECT_MANAGER"));
        when(sysUserMapper.selectPermissionsByUserId(USER_ID))
                .thenReturn(List.of("system:user:add", "system:user:edit"));

        boolean result = interceptor.preHandle(request, response, handler("needBothAnd"));
        assertThat(result).isTrue();
    }

    // ==================== 拒绝场景 ====================

    @Test
    @DisplayName("无 userId - 返回 401")
    void noUserId_shouldReturn401() throws Exception {
        stubErrorWriter();
        boolean result = interceptor.preHandle(request, response, handler("needUserAdd"));

        assertThat(result).isFalse();
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("缺少所需权限 - 返回 403")
    void missingPermission_shouldReturn403() throws Exception {
        stubErrorWriter();
        SecurityContextHolder.setUserId(USER_ID);
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("VIEWER"));
        when(sysUserMapper.selectPermissionsByUserId(USER_ID)).thenReturn(List.of("system:user:view"));

        boolean result = interceptor.preHandle(request, response, handler("needUserAdd"));

        assertThat(result).isFalse();
        verify(response).setStatus(403);
    }

    @Test
    @DisplayName("AND 逻辑 - 缺一即 403")
    void andLogic_missingOne_shouldReturn403() throws Exception {
        stubErrorWriter();
        SecurityContextHolder.setUserId(USER_ID);
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("VIEWER"));
        when(sysUserMapper.selectPermissionsByUserId(USER_ID)).thenReturn(List.of("system:user:add"));

        boolean result = interceptor.preHandle(request, response, handler("needBothAnd"));

        assertThat(result).isFalse();
        verify(response).setStatus(403);
    }

    // ==================== 辅助 ====================

    private void stubErrorWriter() throws Exception {
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        DemoController bean = new DemoController();
        Method method = DemoController.class.getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    /** 测试用控制器，提供带/不带注解的方法。 */
    static class DemoController {

        @RequiresPermission("system:user:add")
        public void needUserAdd() {
        }

        @RequiresPermission(value = {"system:user:add", "system:user:edit"}, logical = Logical.AND)
        public void needBothAnd() {
        }

        public void plain() {
        }
    }
}
