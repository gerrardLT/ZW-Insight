package com.zwinsight.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 登录成功事件 - 由 security 模块发布，system 模块监听并落库登录日志（sys_login_log）。
 *
 * <p>security 为底层模块（system 依赖 security），无法反向调用 system 的
 * SysLogService，故沿用仓内事件解耦惯例（参考 {@link LoginLocationNotifyEvent}），
 * 通过 Spring 事件在聚合应用运行时完成登录日志写入。</p>
 */
@Getter
public class LoginSuccessEvent extends ApplicationEvent {

    /**
     * 登录人姓名
     */
    private final String loginName;

    /**
     * 登录账号
     */
    private final String loginAccount;

    /**
     * 客户端 IP
     */
    private final String ipAddress;

    /**
     * 租户ID
     */
    private final Long tenantId;

    public LoginSuccessEvent(Object source, String loginName, String loginAccount,
                             String ipAddress, Long tenantId) {
        super(source);
        this.loginName = loginName;
        this.loginAccount = loginAccount;
        this.ipAddress = ipAddress;
        this.tenantId = tenantId;
    }
}
