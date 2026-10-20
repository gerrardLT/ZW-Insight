package com.zwinsight.contract.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.contract.domain.*;
import com.zwinsight.contract.domain.dto.ContractRevisionRequest;
import com.zwinsight.contract.mapper.*;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ContractRevisionServiceTest {
    @Mock ContractRevisionMapper revisions;
    @Mock BizConstructionContractMapper contracts;
    @Mock BizProjectMapper projects;
    @Mock ApprovalService approvals;
    @Mock JdbcTemplate jdbc;
    @Mock com.zwinsight.contract.service.ContractPayTermService payTermService;
    @Mock com.zwinsight.common.revision.ExpenseContractRevisionHandler expenseHandler;
    @Mock List<com.zwinsight.common.revision.ExpenseContractRevisionHandler> expenseHandlers;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks ContractRevisionService service;
    private BizConstructionContract contract() {
        BizConstructionContract c = new BizConstructionContract(); c.setId(1L); c.setProjectId(2L);
        c.setStatus("EFFECTIVE"); c.setVersion(3); c.setContractAmount(new BigDecimal("100")); return c;
    }
    private ContractRevision revision(String status) {
        ContractRevision v = new ContractRevision(); v.setId(4L); v.setContractId(1L); v.setProjectId(2L);
        v.setBaseVersion(3); v.setBeforeAmount(new BigDecimal("100")); v.setAfterAmount(new BigDecimal("120"));
        v.setDeltaAmount(new BigDecimal("20")); v.setStatus(status); return v;
    }
    @org.junit.jupiter.api.BeforeEach void context() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(9999L);
        com.zwinsight.common.config.SecurityContextHolder.setUserId(9L);
        lenient().when(contracts.selectById(1L)).thenReturn(contract());
    }
    @org.junit.jupiter.api.AfterEach void clearContext() { com.zwinsight.common.config.SecurityContextHolder.clear(); }
    private void lock() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L), nullable(Long.class))).thenReturn(List.of(1L));
        when(contracts.selectOne(any(Wrapper.class))).thenReturn(contract());
    }
    @Test void detailFound() { when(revisions.selectById(4L)).thenReturn(revision("DRAFT")); assertEquals(4L, service.detail(4L).getId()); }
    @Test void detailMissing() { assertThrows(RuntimeException.class, () -> service.detail(4L)); }
    @Test void listEmpty() { when(revisions.selectList(any(Wrapper.class))).thenReturn(List.of()); assertTrue(service.list(1L).isEmpty()); }
    @Test void listFailurePropagates() { when(revisions.selectList(any(Wrapper.class))).thenThrow(new IllegalStateException()); assertThrows(IllegalStateException.class, () -> service.list(1L)); }
    @Test void createSnapshotsAndDelta() {
        lock(); when(revisions.selectCount(any(Wrapper.class))).thenReturn(0L);
        service.create(new ContractRevisionRequest(1L, null, 3, new BigDecimal("120"), "修订"));
        ArgumentCaptor<ContractRevision> c = ArgumentCaptor.forClass(ContractRevision.class);
        verify(revisions).insert(c.capture()); assertEquals(new BigDecimal("20"), c.getValue().getDeltaAmount());
        assertNotEquals(c.getValue().getBeforeSnapshot(), c.getValue().getAfterSnapshot()); verifyNoInteractions(projects);
    }
    @Test void staleCreateRejected() { lock(); assertThrows(RuntimeException.class, () -> service.create(new ContractRevisionRequest(1L, null, 2, new BigDecimal("120"), "修订"))); verify(revisions, never()).insert(any(ContractRevision.class)); }
    @Test void submitStartsIndependentProcess() {
        lock(); ContractRevision v = revision("DRAFT"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); when(revisions.updateById(v)).thenReturn(1);
        when(jdbc.queryForList(contains("biz_project_member"), eq(Long.class), eq(2L), eq(9999L), eq(9L))).thenReturn(List.of(10L));
        when(jdbc.queryForList(contains("sys_user_role"), eq(Long.class), eq(9999L), eq(9999L), eq(9L))).thenReturn(List.of(11L));
        service.submit(4L); verify(approvals).startProcess(eq("CONTRACT_REVISION"), eq(4L), eq("contract_revision_approval"), anyMap());
    }
    @Test void submitWrongStateRejected() {
        lock(); ContractRevision v = revision("REJECTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); assertThrows(RuntimeException.class, () -> service.submit(4L));
    }
    @Test void approveOnlyWritesDelta() {
        lock(); ContractRevision v = revision("SUBMITTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v);
        when(contracts.updateContractAmountWithVersion(anyLong(), anyInt(), any(), any(), any(), any())).thenReturn(1);
        when(projects.addContractAmount(2L, new BigDecimal("20"))).thenReturn(1); when(revisions.updateById(v)).thenReturn(1);
        service.approve(4L); assertEquals("APPROVED", v.getStatus()); verify(projects).addContractAmount(2L, new BigDecimal("20"));
    }
    @Test void duplicateApproveDoesNothing() { when(revisions.selectById(4L)).thenReturn(revision("APPROVED")); service.approve(4L); verifyNoInteractions(projects, jdbc); verify(contracts).selectById(1L); }
    @Test void casFailureCannotWriteProject() {
        lock(); ContractRevision v = revision("SUBMITTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); assertThrows(RuntimeException.class, () -> service.approve(4L)); verifyNoInteractions(projects);
    }
    @Test void rejectTerminal() {
        lock(); ContractRevision v = revision("SUBMITTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); when(revisions.updateById(v)).thenReturn(1);
        service.reject(4L); assertEquals("REJECTED", v.getStatus()); verifyNoInteractions(projects);
    }
    @Test void rejectFailurePropagates() {
        lock(); ContractRevision v = revision("SUBMITTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); assertThrows(RuntimeException.class, () -> service.reject(4L));
    }
    @Test void cancelDraftPreservesHistory() {
        lock(); ContractRevision v = revision("DRAFT"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); when(revisions.updateById(v)).thenReturn(1);
        service.cancel(4L); assertEquals("CANCELLED", v.getStatus()); verifyNoInteractions(projects);
    }
    @Test void cancelSubmittedRejected() {
        lock(); ContractRevision v = revision("SUBMITTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); assertThrows(RuntimeException.class, () -> service.cancel(4L));
    }
    @Test void hiddenSourceDeniesListBeforeReadingSnapshots() {
        when(contracts.selectById(1L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.list(1L)); verifyNoInteractions(revisions);
    }
    @Test void hiddenSourceDeniesDetail() {
        when(contracts.selectById(1L)).thenReturn(null); when(revisions.selectById(4L)).thenReturn(revision("DRAFT"));
        assertThrows(RuntimeException.class, () -> service.detail(4L));
    }
    @Test void missingApproversCannotStartProcess() {
        lock(); ContractRevision v = revision("DRAFT"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v);
        assertThrows(RuntimeException.class, () -> service.submit(4L)); verifyNoInteractions(approvals);
    }
    @Test void inactiveProjectManagerCannotStartProcess() {
        lock(); ContractRevision v = revision("DRAFT"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v);
        // 唯一PM成员status=2；只有SQL明确过滤status=1时才模拟排除，否则返回该失效成员，使回归必失败。
        when(jdbc.queryForList(contains("biz_project_member"), eq(Long.class), eq(2L), eq(9999L), eq(9L)))
                .thenAnswer(call -> call.<String>getArgument(0).contains("m.status=1") ? List.of() : List.of(10L));
        when(jdbc.queryForList(contains("sys_user_role"), eq(Long.class), eq(9999L), eq(9999L), eq(9L)))
                .thenReturn(List.of(11L));
        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.submit(4L));
        assertTrue(failure.getMessage().contains("项目负责人")); verifyNoInteractions(approvals);
        verify(revisions, never()).updateById(any(ContractRevision.class));
    }
    @Test void rejectedRetainsOldProcessAndCannotResubmit() {
        lock(); ContractRevision v = revision("REJECTED"); v.setWorkflowInstanceId("existing");
        when(revisions.selectById(4L)).thenReturn(v); when(revisions.selectOne(any(Wrapper.class))).thenReturn(v);
        assertThrows(RuntimeException.class, () -> service.submit(4L)); verifyNoInteractions(approvals);
        assertEquals("existing", v.getWorkflowInstanceId());
    }
    @Test void returnedOldProcessCanFinish() {
        lock(); ContractRevision v = revision("REJECTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v);
        when(contracts.updateContractAmountWithVersion(anyLong(), anyInt(), any(), any(), any(), any())).thenReturn(1);
        when(projects.addContractAmount(2L, new BigDecimal("20"))).thenReturn(1); when(revisions.updateById(v)).thenReturn(1);
        service.approve(4L); assertEquals("APPROVED", v.getStatus()); verifyNoInteractions(approvals);
    }
    @Test void inFlightRejectedBlocksParallelApplication() {
        lock(); when(revisions.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(RuntimeException.class, () -> service.create(new ContractRevisionRequest(1L, null, 3, new BigDecimal("120"), "修订")));
        verify(revisions, never()).insert(any(ContractRevision.class));
    }
    @Test void terminateReturnedProcessReleasesApplication() {
        lock(); ContractRevision v = revision("REJECTED"); when(revisions.selectById(4L)).thenReturn(v);
        when(revisions.selectOne(any(Wrapper.class))).thenReturn(v); when(revisions.updateById(v)).thenReturn(1);
        service.reject(4L, true); assertEquals("CANCELLED", v.getStatus());
    }
    @Test void lowerThanOccurredAmountsRejected() {
        BizConstructionContract c = contract(); c.setCumulativeInvoiceAmount(new BigDecimal("90"));
        assertThrows(RuntimeException.class, () -> ContractRevisionService.validateFloor(c, new BigDecimal("80")));
        c.setCumulativeChangeAmount(new BigDecimal("10")); assertDoesNotThrow(() -> ContractRevisionService.validateFloor(c, new BigDecimal("80")));
    }

    @Test void expenseCreateDispatchesToHandlerWithCategory() {
        when(expenseHandlers.stream()).thenReturn(java.util.stream.Stream.of(expenseHandler));
        when(expenseHandler.category()).thenReturn("PURCHASE");
        when(expenseHandler.lockAndValidate(eq(88L), eq(2), eq(new BigDecimal("120"))))
                .thenReturn(com.zwinsight.common.revision.RevisionContract.builder()
                        .contractId(88L).projectId(3L).version(2)
                        .contractAmount(new BigDecimal("100")).taxRate(new BigDecimal("9"))
                        .snapshot("{}").build());
        when(revisions.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.create(new ContractRevisionRequest(88L, "PURCHASE", 2, new BigDecimal("120"), "修订"));

        ArgumentCaptor<ContractRevision> c = ArgumentCaptor.forClass(ContractRevision.class);
        verify(revisions).insert(c.capture());
        assertEquals("PURCHASE", c.getValue().getContractCategory());
        assertEquals(new BigDecimal("20"), c.getValue().getDeltaAmount());
    }
}
