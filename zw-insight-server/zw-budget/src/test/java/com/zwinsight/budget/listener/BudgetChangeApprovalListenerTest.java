package com.zwinsight.budget.listener;

import com.zwinsight.budget.service.BudgetChangeService;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * BudgetChangeApprovalListener 单元测试
 * <p>审批事件分发：APPROVED→onApproved，REJECTED→onRejected，其他业务类型忽略。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetChangeApprovalListenerTest {

    @Mock
    private BudgetChangeService budgetChangeService;

    @InjectMocks
    private BudgetChangeApprovalListener listener;

    @Test
    @DisplayName("APPROVED 分发 onApproved")
    void approved_dispatches() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "BUDGET_CHANGE", 1L, "APPROVED"));

        verify(budgetChangeService).onApproved(1L);
        verify(budgetChangeService, never()).onRejected(any());
    }

    @Test
    @DisplayName("REJECTED 分发 onRejected")
    void rejected_dispatches() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "BUDGET_CHANGE", 2L, "REJECTED"));

        verify(budgetChangeService).onRejected(2L);
        verify(budgetChangeService, never()).onApproved(any());
    }

    @Test
    @DisplayName("非预算变更业务类型忽略")
    void otherBusinessType_ignored() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "PAYMENT_APPLY", 3L, "APPROVED"));

        verifyNoInteractions(budgetChangeService);
    }

    @Test
    @DisplayName("其他结果（如 RECALLED）不分发")
    void otherResult_ignored() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "BUDGET_CHANGE", 4L, "RECALLED"));

        verifyNoInteractions(budgetChangeService);
    }
}
