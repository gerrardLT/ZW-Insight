package com.zwinsight.contract.controller;
import com.zwinsight.contract.service.ContractRevisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ContractRevisionControllerTest {
    ContractRevisionService service = mock(ContractRevisionService.class);
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new ContractRevisionController(service)).build(); }
    @Test void revisionsRequireEditNotAdd() throws Exception {
        Class<ContractRevisionController> type = ContractRevisionController.class;
        // V2026_125：写接口按细粒度修订写码放行（施工保留原写码），不再用模块 view 码
        String[] writeCodes = {"contract:contract:edit", "purchase:contract:edit", "labor:contract:edit",
                "machine:contract:edit", "subcontract:contract:edit"};
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                writeCodes,
                type.getMethod("create", com.zwinsight.contract.domain.dto.ContractRevisionRequest.class)
                        .getAnnotation(com.zwinsight.common.security.RequiresPermission.class).value());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                writeCodes,
                type.getMethod("cancel", Long.class)
                        .getAnnotation(com.zwinsight.common.security.RequiresPermission.class).value());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new String[]{"contract:contract:submit", "purchase:contract:edit", "labor:contract:edit",
                        "machine:contract:edit", "subcontract:contract:edit"},
                type.getMethod("submit", Long.class)
                        .getAnnotation(com.zwinsight.common.security.RequiresPermission.class).value());
    }
    @Test void listContract() throws Exception {
        when(service.list(1L, null)).thenReturn(List.of());
        mvc.perform(get("/api/v1/contract/revision").param("contractId", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }
    @Test void createAndSubmit() throws Exception {
        when(service.create(any())).thenReturn(4L);
        mvc.perform(post("/api/v1/contract/revision").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contractId\":1,\"baseVersion\":3,\"afterAmount\":120,\"reason\":\"revision\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(4));
        mvc.perform(post("/api/v1/contract/revision/4/submit")).andExpect(status().isOk()); verify(service).submit(4L);
    }
    @Test void invalidAmountIs400() throws Exception {
        mvc.perform(post("/api/v1/contract/revision").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contractId\":1,\"baseVersion\":3,\"afterAmount\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
}
