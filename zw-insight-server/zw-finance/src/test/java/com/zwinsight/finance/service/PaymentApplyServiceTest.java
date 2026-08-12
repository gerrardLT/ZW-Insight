package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
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
            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
            verify(projectMapper, never()).addTotalExpense(anyLong(), any());
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
}
