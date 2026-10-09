package com.zwinsight.workflow.controller;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.exception.DataPermissionException;
import com.zwinsight.common.exception.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.zwinsight.workflow.service.ApprovalService;
import com.zwinsight.workflow.service.BusinessDetailService;
import com.zwinsight.workflow.service.UrgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {
    @Mock ApprovalService approvalService;
    @Mock UrgeService urgeService;
    @Mock BusinessDetailService businessDetailService;
    @InjectMocks ApprovalController controller;

    @Test
    void readableTaskAndBusinessReturnHttp200() throws Exception {
        when(approvalService.getTaskDetail("same-tenant")).thenReturn(Map.of("taskId", "same-tenant"));
        when(businessDetailService.getForTask("same-tenant")).thenReturn(Map.of("found", true));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/workflow/approval/detail/same-tenant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("same-tenant"));
        mvc.perform(get("/api/v1/workflow/approval/detail/same-tenant/business"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.found").value(true));
    }

    @Test
    void crossTenantTaskAndBusinessReturnHttp403WithoutData() throws Exception {
        when(approvalService.getTaskDetail("other-tenant"))
                .thenThrow(new DataPermissionException("任务不存在或已被处理"));
        when(businessDetailService.getForTask("other-tenant"))
                .thenThrow(new DataPermissionException("任务不存在或已被处理"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String suffix : new String[]{"", "/business"}) {
            mvc.perform(get("/api/v1/workflow/approval/detail/other-tenant" + suffix))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("任务不存在或已被处理"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void publicStartCannotBypassBusinessSubmit() {
        assertThatThrownBy(() -> controller.start(null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("禁止直接启动");
        verifyNoInteractions(approvalService);
    }
}
