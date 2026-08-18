package com.zwinsight.finance.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.finance.service.PaymentApplyService;
import com.zwinsight.test.TestSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 付款申请 Controller 测试。
 * <p>回归钉住（2026-08-18 W2 实跑实证）：@PostMapping 与 @PutMapping 叠加在同一方法时
 * Spring 仅注册第一个（启动 WARN「only the first will be used」），前端 PUT 提交恒 405；
 * 已统一改为 @RequestMapping(method={POST,PUT})，本测试断言两种方法均被路由。</p>
 */
@WebMvcTest
@Import({PaymentApplyController.class, TestSecurityConfig.class})
class PaymentApplyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentApplyService paymentApplyService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setTenantId(1L);
        SecurityContextHolder.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("PUT /{id}/submit - 前端提交方法被路由（双注解 bug 回归）")
    void should_route_put_submit() throws Exception {
        mockMvc.perform(put("/api/v1/finance/payment-apply/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(paymentApplyService).submit(1L);
    }

    @Test
    @DisplayName("POST /{id}/submit - API 层提交方法同样被路由")
    void should_route_post_submit() throws Exception {
        mockMvc.perform(post("/api/v1/finance/payment-apply/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(paymentApplyService).submit(1L);
    }
}
