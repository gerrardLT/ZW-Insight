package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.dto.BatchOperationRequest;
import com.zwinsight.finance.dto.ContractPayableInfo;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.ContractPayableMapper;
import com.zwinsight.finance.mapper.SettlementDataMapper;
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
 * PaymentApplyService 单元测试
 * <p>审批后生效模式：submit 置 SUBMITTED（不回写），onApproved 回写合同已付与项目支出（含净奖惩额度校验）。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentApplyServiceTest {

    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizOtherContractMapper otherContractMapper;
    @Mock private ContractPayableMapper contractPayableMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private SettlementDataMapper settlementDataMapper;
    @Mock private ApprovalService approvalService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentApplyService paymentApplyService;

    @Nested
    @DisplayName("submit() 提交付款申请")
    class SubmitTests {

        @Test
        @DisplayName("正常路径 — 校验通过后状态置 SUBMITTED，不回写合同/项目")
        void submit_normalPath_statusSubmitted() {
            Long id = 1L;
            Long contractId = 100L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(10L);
            apply.setPaymentAmount(new BigDecimal("30000.00"));
            apply.setStatus("DRAFT");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);
            when(approvalService.startProcess(eq("PAYMENT_APPLY"), eq(id), eq("payment_apply_approval"), anyMap()))
                    .thenReturn("proc-1");

            paymentApplyService.submit(id);

            assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
            assertThat(apply.getWorkflowInstanceId()).isEqualTo("proc-1");
            // 提交时点回填可付快照（详情抽屉数据源，2026-09-15 Phase 1.3 补齐）：净奖惩 0 → 未付 = 100000 - 50000
            assertThat(apply.getCumulativeSettlementSnapshot()).isEqualByComparingTo("100000.00");
            assertThat(apply.getUnpaidAmountSnapshot()).isEqualByComparingTo("50000.00");
            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
            verify(projectMapper, never()).addTotalExpense(anyLong(), any());
        }

        @Test
        @DisplayName("正常路径（含净奖惩） — 快照按可付上限同口径回填")
        void submit_normalPath_snapshotIncludesRewardPunishNet() {
            Long id = 4L;
            Long contractId = 400L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(10L);
            apply.setPaymentAmount(new BigDecimal("30000.00"));
            apply.setStatus("DRAFT");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            // 奖励 2000（净奖惩 +2000）→ 未付快照 = 100000 + 2000 - 50000 = 52000（与 validatePaymentLimit 同口径）
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(new BigDecimal("2000.00"));
            when(approvalService.startProcess(eq("PAYMENT_APPLY"), eq(id), eq("payment_apply_approval"), anyMap()))
                    .thenReturn("proc-4");

            paymentApplyService.submit(id);

            assertThat(apply.getCumulativeSettlementSnapshot()).isEqualByComparingTo("100000.00");
            assertThat(apply.getUnpaidAmountSnapshot()).isEqualByComparingTo("52000.00");
        }

        @Test
        @DisplayName("付款超限 — 快照不回填且不落库")
        void submit_exceedsLimit_snapshotNotFilled() {
            Long id = 5L;
            Long contractId = 500L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setPaymentAmount(new BigDecimal("60000.00"));
            apply.setStatus("DRAFT");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("付款金额不能超过");

            assertThat(apply.getCumulativeSettlementSnapshot()).isNull();
            assertThat(apply.getUnpaidAmountSnapshot()).isNull();
            verify(paymentApplyMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("付款超限（含净奖惩） — 抛 BusinessException")
        void submit_exceedsLimit_throws() {
            Long id = 2L;
            Long contractId = 200L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setPaymentAmount(new BigDecimal("60000.00"));
            apply.setStatus("DRAFT");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            // 处罚 5000（净奖惩 -5000）→ 可付 = 100000 - 5000 - 50000 = 45000 < 60000
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(new BigDecimal("-5000.00"));

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("付款金额不能超过");

            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }

        @Test
        @DisplayName("非 DRAFT/REJECTED 状态 — 抛 BusinessException")
        void submit_nonDraft_throws() {
            Long id = 3L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setStatus("APPROVED");
            when(paymentApplyMapper.selectById(id)).thenReturn(apply);

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿或已驳回状态可提交");
        }
    }

    @Nested
    @DisplayName("onApproved() / onRejected() 审批回调")
    class ApprovalCallbackTests {

        @Test
        @DisplayName("审批通过 — 状态置 APPROVED + 原子回写合同已付与项目支出")
        void onApproved_writesBack() {
            Long id = 1L;
            Long contractId = 100L;
            Long projectId = 10L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(projectId);
            apply.setPaymentAmount(new BigDecimal("30000.00"));
            apply.setStatus("SUBMITTED");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);

            paymentApplyService.onApproved(id);

            assertThat(apply.getStatus()).isEqualTo("APPROVED");
            verify(otherContractMapper).addCumulativePaid(contractId, new BigDecimal("30000.00"));
            verify(projectMapper).addTotalExpense(projectId, new BigDecimal("30000.00"));
        }

        @Test
        @DisplayName("审批通过 — 已生效幂等跳过")
        void onApproved_idempotent() {
            Long id = 1L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setStatus("APPROVED");
            when(paymentApplyMapper.selectById(id)).thenReturn(apply);

            paymentApplyService.onApproved(id);

            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
            verify(projectMapper, never()).addTotalExpense(anyLong(), any());
        }

        @Test
        @DisplayName("审批驳回 — SUBMITTED 置 REJECTED，不回写")
        void onRejected_setsRejected() {
            Long id = 1L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setStatus("SUBMITTED");
            when(paymentApplyMapper.selectById(id)).thenReturn(apply);

            paymentApplyService.onRejected(id);

            assertThat(apply.getStatus()).isEqualTo("REJECTED");
            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("按合同类型路由（采购/劳务/机械/分包）")
    class ContractCategoryRoutingTests {

        @Test
        @DisplayName("PURCHASE 提交 — 读取采购合同可付，不走 biz_other_contract")
        void submit_purchase_routesToPurchaseTable() {
            Long id = 5L;
            Long contractId = 500L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(10L);
            apply.setContractCategory("PURCHASE");
            apply.setPaymentAmount(new BigDecimal("80000.00"));
            apply.setStatus("DRAFT");

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            // 采购合同：累计结算 100000，已付 0 → 可付 100000 ≥ 80000
            when(contractPayableMapper.purchasePayable(contractId))
                    .thenReturn(new ContractPayableInfo(new BigDecimal("100000.00"), BigDecimal.ZERO));
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);
            when(approvalService.startProcess(eq("PAYMENT_APPLY"), eq(id), eq("payment_apply_approval"), anyMap()))
                    .thenReturn("proc-5");

            paymentApplyService.submit(id);

            assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
            verify(contractPayableMapper).purchasePayable(contractId);
            verify(otherContractMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("PURCHASE 审批通过 — 回写采购合同 cumulative_paid + 项目 totalExpense")
        void onApproved_purchase_writesBackPurchaseTable() {
            Long id = 5L;
            Long contractId = 500L;
            Long projectId = 10L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(projectId);
            apply.setContractCategory("PURCHASE");
            apply.setPaymentAmount(new BigDecimal("80000.00"));
            apply.setStatus("SUBMITTED");

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(contractPayableMapper.purchasePayable(contractId))
                    .thenReturn(new ContractPayableInfo(new BigDecimal("100000.00"), BigDecimal.ZERO));
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);

            paymentApplyService.onApproved(id);

            assertThat(apply.getStatus()).isEqualTo("APPROVED");
            verify(contractPayableMapper).addPurchasePaid(contractId, new BigDecimal("80000.00"));
            verify(projectMapper).addTotalExpense(projectId, new BigDecimal("80000.00"));
            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
        }

        @Test
        @DisplayName("合同不存在 — 采购可付为 null 时提交报错")
        void submit_purchaseNotFound_throws() {
            Long id = 6L;
            Long contractId = 600L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setContractCategory("PURCHASE");
            apply.setPaymentAmount(new BigDecimal("1000.00"));
            apply.setStatus("DRAFT");

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(contractPayableMapper.purchasePayable(contractId)).thenReturn(null);

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("关联合同不存在");
        }
    }

    @Nested
    @DisplayName("付款上限边界值")
    class PaymentLimitBoundaryTests {

        private BizPaymentApply buildDraft(Long id, Long contractId, String amount) {
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(10L);
            apply.setPaymentAmount(new BigDecimal(amount));
            apply.setStatus("DRAFT");
            return apply;
        }

        @Test
        @DisplayName("边界：付款金额恰等于可付上限 — 允许提交")
        void submit_amountEqualsLimit_allowed() {
            // 可付 = 100000 - 50000 = 50000，提交恰好 50000
            Long id = 20L;
            Long contractId = 200L;
            BizPaymentApply apply = buildDraft(id, contractId, "50000.00");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);
            when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("p");

            paymentApplyService.submit(id);

            assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("边界：超出可付上限 0.01 元 — 拒绝提交")
        void submit_amountExceedsLimitByOneCent_rejected() {
            Long id = 21L;
            Long contractId = 201L;
            BizPaymentApply apply = buildDraft(id, contractId, "50000.01");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("付款金额不能超过");
            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }

        @Test
        @DisplayName("边界：累计结算/已付均为 null — 视为 0，任意正数付款拒绝")
        void submit_nullSettlementFields_treatedAsZero() {
            Long id = 22L;
            Long contractId = 202L;
            BizPaymentApply apply = buildDraft(id, contractId, "0.01");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(null);
            contract.setCumulativePaid(null);

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(null);

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("付款金额不能超过");
        }

        @Test
        @DisplayName("边界：处罚净额压缩可付至正好等额 — 允许；再多 1 分拒绝")
        void submit_punishmentShrinksLimit_boundaryExact() {
            // 可付 = 100000 + (-40000) - 50000 = 10000
            Long id = 23L;
            Long contractId = 203L;
            BizPaymentApply apply = buildDraft(id, contractId, "10000.00");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId))
                    .thenReturn(new BigDecimal("-40000.00"));
            when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("p");

            paymentApplyService.submit(id);
            assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("并发语义：审批期间额度被占用 — onApproved 重校失败置 REJECTED 不回写")
        void onApproved_limitConsumedDuringApproval_rejectsWithoutWriteback() {
            // 提交时可付 50000；审批期间另一笔已付 30000 生效 → 可付仅剩 20000 < 本笔 50000
            Long id = 24L;
            Long contractId = 204L;
            BizPaymentApply apply = buildDraft(id, contractId, "50000.00");
            apply.setStatus("SUBMITTED");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("80000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);

            paymentApplyService.onApproved(id);

            assertThat(apply.getStatus()).isEqualTo("REJECTED");
            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
            verify(projectMapper, never()).addTotalExpense(anyLong(), any());
        }
    }

    // ============ CRUD 草稿守卫 + onRejected 幂等（P1 FIN-PAY-01~03/08/09/20） ============

    @Nested
    @DisplayName("CRUD 草稿守卫与驳回幂等")
    class CrudGuardTests {

        private BizPaymentApply apply(Long id, String status) {
            BizPaymentApply a = new BizPaymentApply();
            a.setId(id);
            a.setProjectId(10L);
            a.setStatus(status);
            a.setPaymentAmount(new BigDecimal("1000"));
            return a;
        }

        @Test
        @DisplayName("save 置 DRAFT（FIN-PAY-01）")
        void save_setsDraft() {
            BizPaymentApply a = apply(1L, null);
            paymentApplyService.save(a);
            assertThat(a.getStatus()).isEqualTo("DRAFT");
            verify(paymentApplyMapper).insert(a);
        }

        @Test
        @DisplayName("save 金额 null/零/负数 — 拒绝落库（审计缺陷 D3 钉住）")
        void save_invalidAmount_throws() {
            // 范式照抄 ReserveFundApplyServiceTest L155-168（zw-finance 金额守卫负向测试惯例）
            BizPaymentApply nullAmount = apply(10L, null);
            nullAmount.setPaymentAmount(null);
            assertThatThrownBy(() -> paymentApplyService.save(nullAmount))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("付款金额必须大于0");

            BizPaymentApply zero = apply(11L, null);
            zero.setPaymentAmount(BigDecimal.ZERO);
            assertThatThrownBy(() -> paymentApplyService.save(zero))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("付款金额必须大于0");

            BizPaymentApply negative = apply(12L, null);
            negative.setPaymentAmount(new BigDecimal("-100"));
            assertThatThrownBy(() -> paymentApplyService.save(negative))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("付款金额必须大于0");

            verify(paymentApplyMapper, never()).insert(any());
        }

        @Test
        @DisplayName("update 仅 DRAFT 可编辑（FIN-PAY-02/08）")
        void update_draftOnly() {
            when(paymentApplyMapper.selectById(1L)).thenReturn(apply(1L, "DRAFT"));
            BizPaymentApply updated = apply(1L, "DRAFT");
            paymentApplyService.update(updated);
            verify(paymentApplyMapper).updateById(updated);

            when(paymentApplyMapper.selectById(2L)).thenReturn(apply(2L, "SUBMITTED"));
            assertThatThrownBy(() -> paymentApplyService.update(apply(2L, "DRAFT")))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("仅草稿状态可编辑");

            when(paymentApplyMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> paymentApplyService.update(apply(99L, "DRAFT")))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("付款申请不存在");
        }

        @Test
        @DisplayName("delete 仅 DRAFT 可删除（FIN-PAY-03/09）")
        void delete_draftOnly() {
            when(paymentApplyMapper.selectById(1L)).thenReturn(apply(1L, "DRAFT"));
            paymentApplyService.delete(1L);
            verify(paymentApplyMapper).deleteById(1L);

            when(paymentApplyMapper.selectById(2L)).thenReturn(apply(2L, "APPROVED"));
            assertThatThrownBy(() -> paymentApplyService.delete(2L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("仅草稿状态可删除");

            when(paymentApplyMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> paymentApplyService.delete(99L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("付款申请不存在");
        }

        @Test
        @DisplayName("delete E2E_TEST_ 标记数据非草稿放行（E2eTestGuard）")
        void delete_e2eMarkerBypass() {
            BizPaymentApply e2e = apply(3L, "APPROVED");
            e2e.setSupplierName("E2E_TEST_1723900000000_供应商");
            when(paymentApplyMapper.selectById(3L)).thenReturn(e2e);

            paymentApplyService.delete(3L);

            verify(paymentApplyMapper).deleteById(3L);
        }

        @Test
        @DisplayName("onRejected 幂等：非 SUBMITTED 静默返回（FIN-PAY-20）")
        void onRejected_idempotent() {
            when(paymentApplyMapper.selectById(1L)).thenReturn(apply(1L, "REJECTED"));
            paymentApplyService.onRejected(1L);
            verify(paymentApplyMapper, never()).updateById(any());

            when(paymentApplyMapper.selectById(99L)).thenReturn(null);
            paymentApplyService.onRejected(99L);
            verify(paymentApplyMapper, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("分页查询")
    class PageTests {

        @Test
        @DisplayName("分页筛选透传（FIN-PAY-21）")
        void page_delegates() {
            Page<BizPaymentApply> page = new Page<>(1, 10);
            BizPaymentApply a = new BizPaymentApply();
            a.setId(1L);
            page.setRecords(List.of(a));
            page.setTotal(1);
            when(paymentApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            PageResult<BizPaymentApply> result = paymentApplyService.page(1, 10, 5L, 100L, "APPROVED");

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }
    }

    // ============ 批量操作（Phase 1.2：空列表拒绝 / 逐条状态校验 / 整体事务语义） ============

    @Nested
    @DisplayName("batch() 批量操作")
    class BatchTests {

        private BizPaymentApply draft(Long id, Long contractId) {
            BizPaymentApply a = new BizPaymentApply();
            a.setId(id);
            a.setProjectId(10L);
            a.setContractId(contractId);
            a.setStatus("DRAFT");
            a.setPaymentAmount(new BigDecimal("1000"));
            return a;
        }

        private BatchOperationRequest req(String action, List<Long> ids) {
            BatchOperationRequest r = new BatchOperationRequest();
            r.setAction(action);
            r.setIds(ids);
            return r;
        }

        @Test
        @DisplayName("批量删除正常路径：全部 DRAFT，逐条删除并返回条数")
        void batchDelete_normal_deletesAll() {
            when(paymentApplyMapper.selectById(1L)).thenReturn(draft(1L, null));
            when(paymentApplyMapper.selectById(2L)).thenReturn(draft(2L, null));

            int count = paymentApplyService.batch(req("delete", List.of(1L, 2L)));

            assertThat(count).isEqualTo(2);
            verify(paymentApplyMapper).deleteById(1L);
            verify(paymentApplyMapper).deleteById(2L);
        }

        @Test
        @DisplayName("批量删除空列表：拒绝且不执行任何单删")
        void batchDelete_emptyList_throws() {
            assertThatThrownBy(() -> paymentApplyService.batch(req("delete", List.of())))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("ID 列表不能为空");
            verify(paymentApplyMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("批量删除含非 DRAFT：中断并带单据上下文（事务回滚由 @Transactional 保证）")
        void batchDelete_invalidStatus_interruptsWithContext() {
            when(paymentApplyMapper.selectById(1L)).thenReturn(draft(1L, null));
            BizPaymentApply approved = draft(2L, null);
            approved.setStatus("APPROVED");
            when(paymentApplyMapper.selectById(2L)).thenReturn(approved);

            assertThatThrownBy(() -> paymentApplyService.batch(req("delete", List.of(1L, 2L))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("批量删除中断")
                    .hasMessageContaining("ID=2")
                    .hasMessageContaining("仅草稿状态可删除");
        }

        @Test
        @DisplayName("批量提交正常路径：逐条走 submit 校验并启动流程")
        void batchSubmit_normal_submitsAll() {
            BizPaymentApply a = draft(1L, 100L);
            BizPaymentApply b = draft(2L, 100L);
            when(paymentApplyMapper.selectById(1L)).thenReturn(a);
            when(paymentApplyMapper.selectById(2L)).thenReturn(b);

            BizOtherContract contract = new BizOtherContract();
            contract.setId(100L);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(BigDecimal.ZERO);
            when(otherContractMapper.selectById(100L)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(100L)).thenReturn(BigDecimal.ZERO);
            when(approvalService.startProcess(eq("PAYMENT_APPLY"), anyLong(), eq("payment_apply_approval"), anyMap()))
                    .thenReturn("proc-batch");

            int count = paymentApplyService.batch(req("submit", List.of(1L, 2L)));

            assertThat(count).isEqualTo(2);
            assertThat(a.getStatus()).isEqualTo("SUBMITTED");
            assertThat(b.getStatus()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("未知 action：拒绝")
        void batch_unknownAction_throws() {
            // 不打任何 stub：action 校验先于数据库查询（CI 实证 2026-09-16 run 35060597717，
            // 原 selectById stub 未被消费触发 Mockito 严格模式 UnnecessaryStubbingException）
            assertThatThrownBy(() -> paymentApplyService.batch(req("audit", List.of(1L))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不支持的批量操作类型");
        }
    }
}
