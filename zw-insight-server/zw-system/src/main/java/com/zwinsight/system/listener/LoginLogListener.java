package com.zwinsight.system.listener;

import com.zwinsight.common.event.LoginSuccessEvent;
import com.zwinsight.system.domain.SysLoginLog;
import com.zwinsight.system.service.SysLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 登录日志监听器
 * <p>监听 security 模块发布的 {@link LoginSuccessEvent}，落库 sys_login_log。
 * security → system 为反向依赖，通过 Spring 事件解耦（惯例参考
 * LoginLocationNotifyEvent 的 security → message 链路）。落库异常仅记日志，
 * 不影响登录主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogListener {

    private final SysLogService logService;

    @EventListener
    public void onLoginSuccess(LoginSuccessEvent event) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setLoginName(event.getLoginName());
            loginLog.setLoginAccount(event.getLoginAccount());
            loginLog.setIpAddress(event.getIpAddress());
            loginLog.setLoginTime(LocalDateTime.now());
            loginLog.setTenantId(event.getTenantId());
            logService.saveLoginLog(loginLog);
        } catch (Exception e) {
            log.error("保存登录日志失败: account={}", event.getLoginAccount(), e);
        }
    }
}
