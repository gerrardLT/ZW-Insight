package com.zwinsight.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录成功事件单元测试
 */
class LoginSuccessEventTest {

    @Test
    @DisplayName("构造参数完整保留")
    void constructor_retainsAllFields() {
        Object source = new Object();
        LoginSuccessEvent event = new LoginSuccessEvent(source,
                "张三", "zhangsan", "192.168.1.10", 1L);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getLoginName()).isEqualTo("张三");
        assertThat(event.getLoginAccount()).isEqualTo("zhangsan");
        assertThat(event.getIpAddress()).isEqualTo("192.168.1.10");
        assertThat(event.getTenantId()).isEqualTo(1L);
    }
}
