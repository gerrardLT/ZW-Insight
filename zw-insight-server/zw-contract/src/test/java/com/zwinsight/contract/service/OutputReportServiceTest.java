package com.zwinsight.contract.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.domain.BizOutputReportDetail;
import com.zwinsight.contract.mapper.BizBoqItemMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportDetailMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OutputReportService 单元测试
 * <p>审批后生效模式：submit 置 SUBMITTED（不回写），onApproved 回写合同/项目累计产值 + BOQ 已完成工程量。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputReportServiceTest {

    /** 服务内用 LambdaUpdateWrapper.set(...) 做状态 CAS，纯单测环境需预初始化实体列缓存 */
    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), BizOutputReport.class);
    }

    @Mock private BizOutputReportMapper outputReportMapper;
    @Mock private BizOutputReportDetailMapper reportDetailMapper;
    @Mock private BizConstructionContractMapper contractMapper;
    @Mock private BizBoqItemMapper boqItemMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private ApprovalService approvalService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OutputReportService outputReportService;

    @Nested
    @DisplayName("submit() 提交产值上报")
    class SubmitTests {

        @Test
        @DisplayName("正常路径 — 校验通过后状态置 SUBMITTED，不回写累计产值")
        void submit_normalPath_statusSubmitted() {
            Long id = 1L;
            Long contractId = 100L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setProjectId(10L);
            report.setCurrentOutput(new BigDecimal("20000.00"));
            report.setStatus("DRAFT");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);
            when(approvalService.startProcess(eq("OUTPUT_REPORT"), eq(id), eq("output_report_approval"), anyMap()))
                    .thenReturn("proc-1");

            outputReportService.submit(id);

            assertThat(report.getStatus()).isEqualTo("SUBMITTED");
            assertThat(report.getWorkflowInstanceId()).isEqualTo("proc-1");
            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
            verify(projectMapper, never()).addCumulativeOutput(anyLong(), any());
        }

        @Test
        @DisplayName("累计产值超合同金额 — 抛 BusinessException")
        void submit_exceedsContract_throws() {
            Long id = 2L;
            Long contractId = 200L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setCurrentOutput(new BigDecimal("60000.00"));
            report.setStatus("DRAFT");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);

            assertThatThrownBy(() -> outputReportService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("累计产值不能超过合同金额");

            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }

        @Test
        @DisplayName("提交守卫：不存在/非草稿非驳回/合同不存在拒绝（P1 OUT-04）")
        void submit_guardCases_throws() {
            when(outputReportMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> outputReportService.submit(99L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("产值报告不存在");

            BizOutputReport approved = new BizOutputReport();
            approved.setId(1L);
            approved.setStatus("APPROVED");
            when(outputReportMapper.selectById(1L)).thenReturn(approved);
            assertThatThrownBy(() -> outputReportService.submit(1L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("仅草稿或已驳回状态可提交");

            BizOutputReport draft = new BizOutputReport();
            draft.setId(2L);
            draft.setContractId(100L);
            draft.setStatus("DRAFT");
            when(outputReportMapper.selectById(2L)).thenReturn(draft);
            when(contractMapper.selectById(100L)).thenReturn(null);
            assertThatThrownBy(() -> outputReportService.submit(2L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("关联合同不存在");
        }

        @Test
        @DisplayName("REJECTED 重新提交放行（P1 OUT-05）")
        void submit_rejectedReport_resubmittable() {
            Long id = 3L;
            Long contractId = 300L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setProjectId(10L);
            report.setCurrentOutput(new BigDecimal("10000.00"));
            report.setStatus("REJECTED");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);
            when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-2");

            outputReportService.submit(id);

            assertThat(report.getStatus()).isEqualTo("SUBMITTED");
        }
    }

    @Nested
    @DisplayName("onApproved() / onRejected() 审批回调")
    class ApprovalCallbackTests {

        @Test
        @DisplayName("审批通过 — 回写合同/项目累计产值 + BOQ 已完成工程量")
        void onApproved_writesBackWithBoq() {
            Long id = 1L;
            Long contractId = 100L;
            Long projectId = 10L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setProjectId(projectId);
            report.setCurrentOutput(new BigDecimal("20000.00"));
            report.setStatus("SUBMITTED");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));

            BizOutputReportDetail detail = new BizOutputReportDetail();
            detail.setBoqItemId(9001L);
            detail.setQuantity(new BigDecimal("5"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);
            when(reportDetailMapper.selectList(any())).thenReturn(List.of(detail));
            when(contractMapper.addCumulativeOutputWithinLimit(contractId, new BigDecimal("20000.00"))).thenReturn(1);
            when(projectMapper.addCumulativeOutput(projectId, new BigDecimal("20000.00"))).thenReturn(1);
            when(boqItemMapper.addCompletedQuantity(9001L, new BigDecimal("5"))).thenReturn(1);
            when(outputReportMapper.update(isNull(), any())).thenReturn(1);

            outputReportService.onApproved(id);

            assertThat(report.getStatus()).isEqualTo("APPROVED");
            // 合同累计走带上限的原子累加（额度检查与写入同一条 SQL）
            verify(contractMapper).addCumulativeOutputWithinLimit(contractId, new BigDecimal("20000.00"));
            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
            verify(projectMapper).addCumulativeOutput(projectId, new BigDecimal("20000.00"));
            verify(boqItemMapper).addCompletedQuantity(9001L, new BigDecimal("5"));
        }

        @Test
        @DisplayName("并发越限：带上限的原子累加命中 0 行 — 置 REJECTED+通知，不回写项目与 BOQ")
        void onApproved_concurrentOverLimit_rejectedByAtomicUpdate() {
            BizOutputReport report = new BizOutputReport();
            report.setId(7L);
            report.setContractId(700L);
            report.setProjectId(10L);
            report.setCurrentOutput(new BigDecimal("20000.00"));
            report.setStatus("SUBMITTED");
            report.setCreatedBy(9L);
            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(700L);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));
            when(outputReportMapper.selectById(7L)).thenReturn(report);
            when(contractMapper.selectById(700L)).thenReturn(contract);
            // 读校验通过（70000 ≤ 100000），但另一单已先入账，条件 UPDATE 未命中
            when(contractMapper.addCumulativeOutputWithinLimit(700L, new BigDecimal("20000.00"))).thenReturn(0);

            outputReportService.onApproved(7L);

            assertThat(report.getStatus()).isEqualTo("REJECTED");
            verify(projectMapper, never()).addCumulativeOutput(anyLong(), any());
            verify(boqItemMapper, never()).addCompletedQuantity(anyLong(), any());
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("状态 CAS 落空（并发重复回调）— 抛异常使累加随事务回滚")
        void onApproved_statusCasLost_throws() {
            BizOutputReport report = new BizOutputReport();
            report.setId(8L);
            report.setContractId(800L);
            report.setProjectId(10L);
            report.setCurrentOutput(new BigDecimal("1000.00"));
            report.setStatus("SUBMITTED");
            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(800L);
            contract.setContractAmount(new BigDecimal("100000.00"));
            when(outputReportMapper.selectById(8L)).thenReturn(report);
            when(contractMapper.selectById(800L)).thenReturn(contract);
            when(contractMapper.addCumulativeOutputWithinLimit(800L, new BigDecimal("1000.00"))).thenReturn(1);
            when(projectMapper.addCumulativeOutput(10L, new BigDecimal("1000.00"))).thenReturn(1);
            when(outputReportMapper.update(isNull(), any())).thenReturn(0);

            assertThatThrownBy(() -> outputReportService.onApproved(8L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("重复回调已回滚");
        }

        @Test
        @DisplayName("陈旧回调：状态非 SUBMITTED（如 DRAFT/REJECTED）忽略，不入账")
        void onApproved_nonSubmitted_ignored() {
            BizOutputReport report = new BizOutputReport();
            report.setId(9L);
            report.setStatus("REJECTED");
            when(outputReportMapper.selectById(9L)).thenReturn(report);

            outputReportService.onApproved(9L);

            verify(contractMapper, never()).addCumulativeOutputWithinLimit(anyLong(), any());
            verify(projectMapper, never()).addCumulativeOutput(anyLong(), any());
        }

        @Test
        @DisplayName("审批通过 — 已生效幂等跳过")
        void onApproved_idempotent() {
            Long id = 1L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setStatus("APPROVED");
            when(outputReportMapper.selectById(id)).thenReturn(report);

            outputReportService.onApproved(id);

            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
            verify(projectMapper, never()).addCumulativeOutput(anyLong(), any());
        }

        @Test
        @DisplayName("审批驳回 — SUBMITTED 置 REJECTED，不回写")
        void onRejected_setsRejected() {
            Long id = 1L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setStatus("SUBMITTED");
            when(outputReportMapper.selectById(id)).thenReturn(report);

            outputReportService.onRejected(id);

            assertThat(report.getStatus()).isEqualTo("REJECTED");
            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
        }

        @Test
        @DisplayName("驳回回调守卫：不存在/非 SUBMITTED 不处理（P1 OUT-11）")
        void onRejected_guardCases() {
            when(outputReportMapper.selectById(99L)).thenReturn(null);
            outputReportService.onRejected(99L);
            verify(outputReportMapper, never()).updateById(any());

            BizOutputReport draft = new BizOutputReport();
            draft.setId(1L);
            draft.setStatus("DRAFT");
            when(outputReportMapper.selectById(1L)).thenReturn(draft);
            outputReportService.onRejected(1L);
            verify(outputReportMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("生效前上限复验失败 — 置 REJECTED+通知不回写（P1 OUT-08）")
        void onApproved_limitConsumedDuringApproval_rejectsWithNotify() {
            Long id = 5L;
            Long contractId = 500L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setProjectId(10L);
            report.setCurrentOutput(new BigDecimal("50000.00"));
            report.setStatus("SUBMITTED");
            report.setCreatedBy(9L);
            report.setWorkflowInstanceId("proc-1");

            // 提交时可报 50000；审批期间另一笔已报 40000 生效 → 可报仅剩 10000 < 本笔 50000
            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("90000.00"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);

            outputReportService.onApproved(id);

            assertThat(report.getStatus()).isEqualTo("REJECTED");
            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
            verify(projectMapper, never()).addCumulativeOutput(anyLong(), any());
            // 通知发起人
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("回调：报告不存在仅记日志；合同不存在抛异常（不静默，使审批事务回滚暴露账实分裂）")
        void onApproved_missingRefs_reportLogged_contractThrows() {
            when(outputReportMapper.selectById(99L)).thenReturn(null);
            outputReportService.onApproved(99L);
            verify(outputReportMapper, never()).updateById(any());

            BizOutputReport report = new BizOutputReport();
            report.setId(6L);
            report.setContractId(600L);
            report.setStatus("SUBMITTED");
            when(outputReportMapper.selectById(6L)).thenReturn(report);
            when(contractMapper.selectById(600L)).thenReturn(null);
            assertThatThrownBy(() -> outputReportService.onApproved(6L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无法生效");
            verify(outputReportMapper, never()).updateById(any());
            verify(contractMapper, never()).addCumulativeOutputWithinLimit(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("delete() 删除产值报告（2026-08-21 台账缺口#2 DELETE 通道）")
    class DeleteTests {

        @Test
        @DisplayName("DRAFT 可删 — 先删明细行再删主表")
        void delete_draft_allowed() {
            BizOutputReport report = new BizOutputReport();
            report.setId(1L);
            report.setStatus("DRAFT");
            when(outputReportMapper.selectById(1L)).thenReturn(report);

            outputReportService.delete(1L);

            verify(reportDetailMapper).delete(any());
            verify(outputReportMapper).deleteById(1L);
        }

        @Test
        @DisplayName("APPROVED 无 E2E 标记拒绝 — 明细与主表均不动")
        void delete_approved_rejected() {
            BizOutputReport report = new BizOutputReport();
            report.setId(1L);
            report.setStatus("APPROVED");
            when(outputReportMapper.selectById(1L)).thenReturn(report);

            assertThatThrownBy(() -> outputReportService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿或已驳回状态可删除");

            verify(reportDetailMapper, never()).delete(any());
            verify(outputReportMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("E2E_TEST_ 标记数据非草稿态放行（E2eTestGuard 旁路）")
        void delete_e2eMarkerBypass() {
            BizOutputReport report = new BizOutputReport();
            report.setId(2L);
            report.setStatus("APPROVED");
            report.setReportPeriod("E2E_TEST_1723900000000_2026-08");
            when(outputReportMapper.selectById(2L)).thenReturn(report);

            outputReportService.delete(2L);

            verify(outputReportMapper).deleteById(2L);
        }

        @Test
        @DisplayName("删除已批准（E2E 旁路）— 对称冲销合同/项目累计与 BOQ 完成量，DRAFT 不冲销")
        void delete_approved_reversesCumulatives() {
            BizOutputReport report = new BizOutputReport();
            report.setId(3L);
            report.setStatus("APPROVED");
            report.setContractId(100L);
            report.setProjectId(10L);
            report.setCurrentOutput(new BigDecimal("20000.00"));
            report.setReportPeriod("E2E_TEST_1723900000000_2026-08");
            BizOutputReportDetail detail = new BizOutputReportDetail();
            detail.setBoqItemId(9001L);
            detail.setQuantity(new BigDecimal("5"));
            when(outputReportMapper.selectById(3L)).thenReturn(report);
            when(reportDetailMapper.selectList(any())).thenReturn(List.of(detail));

            outputReportService.delete(3L);

            verify(contractMapper).addCumulativeOutput(100L, new BigDecimal("-20000.00"));
            verify(projectMapper).addCumulativeOutput(10L, new BigDecimal("-20000.00"));
            verify(boqItemMapper).addCompletedQuantity(9001L, new BigDecimal("-5"));
            verify(outputReportMapper).deleteById(3L);
        }

        @Test
        @DisplayName("报告不存在抛异常")
        void delete_notFound() {
            when(outputReportMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> outputReportService.delete(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("产值报告不存在");
        }
    }

    @Nested
    @DisplayName("save() 保存草稿")
    class SaveTests {

        @Test
        @DisplayName("保存草稿 + 明细行持久化（P1 OUT-01）")
        void save_setsDraftAndPersistsDetails() {
            BizOutputReport report = new BizOutputReport();
            report.setContractId(100L);
            report.setCurrentOutput(new BigDecimal("1000"));
            BizOutputReportDetail detail = new BizOutputReportDetail();
            detail.setId(555L);
            report.setDetails(List.of(detail));
            BizConstructionContract c = new BizConstructionContract();
            c.setId(100L);
            when(contractMapper.selectById(100L)).thenReturn(c);

            outputReportService.save(report);

            assertThat(report.getStatus()).isEqualTo("DRAFT");
            verify(outputReportMapper).insert(report);
            verify(reportDetailMapper).insert(detail);
            assertThat(detail.getId()).as("明细 id 应清空重新生成").isNull();
            assertThat(detail.getReportId()).isEqualTo(report.getId());
        }

        @Test
        @DisplayName("保存草稿无明细 — 不插明细行（P1 OUT-01 边界）")
        void save_noDetails_skipsDetailInsert() {
            BizOutputReport report = new BizOutputReport();
            report.setContractId(100L);
            report.setCurrentOutput(new BigDecimal("1000"));
            BizConstructionContract c = new BizConstructionContract();
            c.setId(100L);
            when(contractMapper.selectById(100L)).thenReturn(c);

            outputReportService.save(report);

            assertThat(report.getStatus()).isEqualTo("DRAFT");
            verify(outputReportMapper).insert(report);
            verify(reportDetailMapper, never()).insert(any());
        }

        @Test
        @DisplayName("保存校验：金额非正 / 合同不存在 / 合同不属于该项目均拒绝，且不落库；请求体流程实例被丢弃")
        void save_boundaryRejected() {
            BizOutputReport zero = new BizOutputReport();
            zero.setContractId(100L);
            zero.setCurrentOutput(BigDecimal.ZERO);
            assertThatThrownBy(() -> outputReportService.save(zero))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("必须大于0");

            BizOutputReport noContract = new BizOutputReport();
            noContract.setContractId(404L);
            noContract.setCurrentOutput(new BigDecimal("1"));
            when(contractMapper.selectById(404L)).thenReturn(null);
            assertThatThrownBy(() -> outputReportService.save(noContract))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("关联合同不存在");

            BizConstructionContract c = new BizConstructionContract();
            c.setId(100L);
            c.setProjectId(1L);
            when(contractMapper.selectById(100L)).thenReturn(c);
            BizOutputReport mismatch = new BizOutputReport();
            mismatch.setContractId(100L);
            mismatch.setProjectId(2L);
            mismatch.setCurrentOutput(new BigDecimal("1"));
            assertThatThrownBy(() -> outputReportService.save(mismatch))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不属于该项目");
            verify(outputReportMapper, never()).insert(any(BizOutputReport.class));

            BizOutputReport forged = new BizOutputReport();
            forged.setContractId(100L);
            forged.setProjectId(1L);
            forged.setCurrentOutput(new BigDecimal("1"));
            forged.setWorkflowInstanceId("forged");
            outputReportService.save(forged);
            assertThat(forged.getWorkflowInstanceId()).isNull();
        }
    }
}
