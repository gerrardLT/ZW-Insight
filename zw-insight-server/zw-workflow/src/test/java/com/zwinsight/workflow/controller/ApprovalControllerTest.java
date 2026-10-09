package com.zwinsight.workflow.controller;

import com.zwinsight.common.exception.BusinessException;
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
    void publicStartCannotBypassBusinessSubmit() {
        assertThatThrownBy(() -> controller.start(null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("禁止直接启动");
        verifyNoInteractions(approvalService);
    }
}
