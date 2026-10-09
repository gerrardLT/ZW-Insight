package com.zwinsight.hr.listener;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.hr.service.*;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class HrApplyApprovalListenerTest {
    @Mock JdbcTemplate jdbc;
    @Mock EntryApplyService entryApplyService;
    @Mock RegularApplyService regularApplyService;
    @Mock ResignApplyService resignApplyService;
    @Mock SealApplyService sealApplyService;
    @Mock TransferApplyService transferApplyService;
    @Mock VehicleApplyService vehicleApplyService;
    @InjectMocks HrApplyApprovalListener listener;

    @Test
    void fiveTypes_currentDispatches_staleDoesNot() {
        try (var context = mockStatic(SecurityContextHolder.class)) {
            context.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            for (String type : java.util.List.of("REGULAR_APPLY", "RESIGN_APPLY", "SEAL_APPLY", "TRANSFER_APPLY", "VEHICLE_APPLY")) {
                when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(55L), eq(9999L), eq("old"))).thenReturn(0);
                listener.onApprovalReject(new ApprovalRejectEvent(this, "old", type, 55L, "REJECT"));
            }
            verifyNoInteractions(regularApplyService, resignApplyService, sealApplyService, transferApplyService, vehicleApplyService);
            for (String type : java.util.List.of("REGULAR_APPLY", "RESIGN_APPLY", "SEAL_APPLY", "TRANSFER_APPLY", "VEHICLE_APPLY")) {
                when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(55L), eq(9999L), eq("current"))).thenReturn(1);
                listener.onApprovalReject(new ApprovalRejectEvent(this, "current", type, 55L, "REJECT"));
            }
            verify(regularApplyService).onRejected(55L);
            verify(resignApplyService).onRejected(55L);
            verify(sealApplyService).onRejected(55L);
            verify(transferApplyService).onRejected(55L);
            verify(vehicleApplyService).onRejected(55L);
        }
    }
}
