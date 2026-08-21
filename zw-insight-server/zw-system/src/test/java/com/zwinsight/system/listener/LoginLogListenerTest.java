package com.zwinsight.system.listener;

import com.zwinsight.common.event.LoginSuccessEvent;
import com.zwinsight.system.domain.SysLoginLog;
import com.zwinsight.system.service.SysLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 登录日志监听器单元测试
 */
@ExtendWith(MockitoExtension.class)
class LoginLogListenerTest {

    @Mock
    private SysLogService logService;

    @InjectMocks
    private LoginLogListener listener;

    @Test
    @DisplayName("登录成功事件：字段完整回填并落库")
    void onLoginSuccess_savesLogWithAllFields() {
        LoginSuccessEvent event = new LoginSuccessEvent(this,
                "张三", "zhangsan", "192.168.1.10", 1L);

        listener.onLoginSuccess(event);

        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(logService).saveLoginLog(captor.capture());
        SysLoginLog log = captor.getValue();
        assertThat(log.getLoginName()).isEqualTo("张三");
        assertThat(log.getLoginAccount()).isEqualTo("zhangsan");
        assertThat(log.getIpAddress()).isEqualTo("192.168.1.10");
        assertThat(log.getTenantId()).isEqualTo(1L);
        assertThat(log.getLoginTime()).isNotNull();
    }

    @Test
    @DisplayName("落库异常不外抛：不影响登录主流程")
    void onLoginSuccess_saveFailure_swallowed() {
        doThrow(new RuntimeException("db down")).when(logService).saveLoginLog(any());
        LoginSuccessEvent event = new LoginSuccessEvent(this,
                "李四", "lisi", "10.0.0.1", 2L);

        assertThatCode(() -> listener.onLoginSuccess(event)).doesNotThrowAnyException();
    }
}
