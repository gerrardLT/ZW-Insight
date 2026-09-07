package com.zwinsight.contract.service;

import com.zwinsight.common.event.contract.ChangeEventApprovedEvent;
import com.zwinsight.common.event.outbox.OutboxEventRecorder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizChangeEvent;
import com.zwinsight.contract.domain.ChangeEventStatus;
import com.zwinsight.contract.mapper.BizChangeEventMapper;
import com.zwinsight.file.service.SerialNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更事件服务单元测试。
 * <p>
 * 覆盖变更主链「登记 → 评估 → 审批 → 传导」的关键约束：
 * </p>
 * <ol>
 *   <li><b>业务事实唯一</b>：同一 sourceType+sourceRef 不得重复登记（防成本双算）</li>
 *   <li><b>状态机护栏</b>：非法流转一律拒绝，终态只读</li>
 *   <li><b>评估完整性</b>：成本影响非零时必须指明受影响账户，否则批准后无法传导（断链）</li>
 *   <li><b>事件投递</b>：状态变更与 Outbox 写入同事务，批准必投递传导事件</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeEventService 变更事件")
class ChangeEventServiceTest {

    @Mock
    private BizChangeEventMapper changeEventMapper;

    @Mock
    private SerialNumberService serialNumberService;

    @Mock
    private OutboxEventRecorder outboxRecorder;

    @InjectMocks
    private ChangeEventService changeEventService;

    private BizChangeEvent draft;

    @BeforeEach
    void setUp() {
        draft = new BizChangeEvent();
        draft.setId(555L);
        draft.setProjectId(100L);
        draft.setEventNumber("CHG20260001");
        draft.setSourceType("FIELD_EVENT");
        draft.setTitle("地下室顶板加厚");
        draft.setStatus(ChangeEventStatus.DRAFT.name());
        draft.setVersion(0);
    }

    private BizChangeEvent.ImpactAssessment assessment(String costDelta, String rationale) {
        BizChangeEvent.ImpactAssessment a = new BizChangeEvent.ImpactAssessment();
        a.setCostDelta(new BigDecimal(costDelta));
        a.setScheduleDelayDays(3);
        a.setRationale(rationale);
        return a;
    }

    private BizChangeEvent.AffectedAccount accountDelta(Long accountId, String type, String amount) {
        BizChangeEvent.AffectedAccount d = new BizChangeEvent.AffectedAccount();
        d.setAccountId(accountId);
        d.setDeltaType(type);
        d.setDeltaAmount(new BigDecimal(amount));
        return d;
    }

    @Nested
    @DisplayName("登记变更事件")
    class Create {

        @Test
        @DisplayName("正常登记：生成编号、置草稿、投递创建事件")
        void createsDraft() {
            when(serialNumberService.generate("CHANGE_EVENT")).thenReturn("CHG20260009");

            BizChangeEvent request = new BizChangeEvent();
            request.setProjectId(100L);
            request.setSourceType("FIELD_EVENT");
            request.setTitle("现场签证：土方外运增加");

            BizChangeEvent created = changeEventService.create(request);

            assertThat(created.getEventNumber()).isEqualTo("CHG20260009");
            assertThat(created.getStatus()).isEqualTo(ChangeEventStatus.DRAFT.name());
            assertThat(created.getCostDelta()).isEqualByComparingTo("0");
            assertThat(created.getScheduleDelayDays()).isZero();
            // 集合字段初始化为空而非 null，避免前端与消费方 NPE
            assertThat(created.getAffectedWbsIds()).isEmpty();
            assertThat(created.getAffectedAccounts()).isEmpty();
            assertThat(created.getSupportingDocs()).isEmpty();

            verify(changeEventMapper).insert(request);
            verify(outboxRecorder).record(eq(ChangeEventEvents.TYPE_CREATED),
                    eq(ChangeEventEvents.AGGREGATE_TYPE), any(), any(), eq(request));
        }

        @Test
        @DisplayName("同一来源引用重复登记：拒绝（业务事实唯一，防成本双算）")
        void rejectsDuplicateSourceRef() {
            BizChangeEvent existing = new BizChangeEvent();
            existing.setId(500L);
            existing.setEventNumber("CHG20260005");
            when(changeEventMapper.selectBySourceRef(100L, "FIELD_EVENT", "VISA-88", null))
                    .thenReturn(existing);

            BizChangeEvent request = new BizChangeEvent();
            request.setProjectId(100L);
            request.setSourceType("FIELD_EVENT");
            request.setSourceRef("VISA-88");
            request.setTitle("重复登记的签证");

            assertThatThrownBy(() -> changeEventService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("CHG20260005")
                    .hasMessageContaining("禁止重复录入");

            verify(changeEventMapper, never()).insert(any());
        }

        @Test
        @DisplayName("无来源引用时不做查重（纯人工登记允许同号多单）")
        void skipsDedupWhenNoSourceRef() {
            when(serialNumberService.generate(anyString())).thenReturn("CHG20260010");

            BizChangeEvent request = new BizChangeEvent();
            request.setProjectId(100L);
            request.setSourceType("OWNER_REQUEST");
            request.setTitle("业主口头指令");

            changeEventService.create(request);

            verify(changeEventMapper, never()).selectBySourceRef(anyLong(), anyString(), anyString(), any());
            verify(changeEventMapper).insert(request);
        }

        @Test
        @DisplayName("非法来源类型拒绝（枚举白名单，防脏值入库）")
        void rejectsInvalidSourceType() {
            BizChangeEvent request = new BizChangeEvent();
            request.setProjectId(100L);
            request.setSourceType("NOT_A_TYPE");
            request.setTitle("x");

            assertThatThrownBy(() -> changeEventService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("非法来源类型");
        }

        @Test
        @DisplayName("必填校验：项目/来源/标题缺失一律拒绝")
        void rejectsMissingMandatory() {
            BizChangeEvent noProject = new BizChangeEvent();
            noProject.setSourceType("FIELD_EVENT");
            noProject.setTitle("t");
            assertThatThrownBy(() -> changeEventService.create(noProject))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("项目ID");

            BizChangeEvent noSource = new BizChangeEvent();
            noSource.setProjectId(100L);
            noSource.setTitle("t");
            assertThatThrownBy(() -> changeEventService.create(noSource))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("来源类型");

            BizChangeEvent noTitle = new BizChangeEvent();
            noTitle.setProjectId(100L);
            noTitle.setSourceType("FIELD_EVENT");
            assertThatThrownBy(() -> changeEventService.create(noTitle))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("标题");
        }

        @Test
        @DisplayName("标题超长拒绝（列宽 300）")
        void rejectsOverlongTitle() {
            BizChangeEvent request = new BizChangeEvent();
            request.setProjectId(100L);
            request.setSourceType("FIELD_EVENT");
            request.setTitle("x".repeat(301));

            assertThatThrownBy(() -> changeEventService.create(request))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("300");
        }
    }

    @Nested
    @DisplayName("影响评估")
    class Assessment {

        @Test
        @DisplayName("提交评估：冗余金额到列、置审批中、投递评估事件")
        void submitsAssessment() {
            draft.setStatus(ChangeEventStatus.ASSESSING.name());
            draft.setAffectedAccounts(new ArrayList<>(List.of(accountDelta(1001L, "INCREASE", "50000"))));
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent result = changeEventService.submitAssessment(
                    555L, assessment("50000", "顶板加厚需增加混凝土与钢筋"));

            assertThat(result.getStatus()).isEqualTo(ChangeEventStatus.APPROVING.name());
            // 冗余到列上：列表排序/统计走索引，不必每次解析 JSON
            assertThat(result.getCostDelta()).isEqualByComparingTo("50000");
            assertThat(result.getScheduleDelayDays()).isEqualTo(3);
            assertThat(result.getAssessedAt()).isNotNull();

            verify(outboxRecorder).record(eq(ChangeEventEvents.TYPE_ASSESSED),
                    eq(ChangeEventEvents.AGGREGATE_TYPE), eq(555L), any(), eq(draft));
        }

        @Test
        @DisplayName("成本影响非零但未指明账户：拒绝（否则批准后传导断链）")
        void rejectsCostImpactWithoutAccounts() {
            draft.setStatus(ChangeEventStatus.ASSESSING.name());
            draft.setAffectedAccounts(new ArrayList<>());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.submitAssessment(
                    555L, assessment("50000", "有成本影响")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("受影响的成本账户");

            verify(changeEventMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("零成本影响允许不指明账户（纯工期/范围变更）")
        void allowsZeroCostWithoutAccounts() {
            draft.setStatus(ChangeEventStatus.ASSESSING.name());
            draft.setAffectedAccounts(new ArrayList<>());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent result = changeEventService.submitAssessment(
                    555L, assessment("0", "仅影响工期，无成本变化"));

            assertThat(result.getStatus()).isEqualTo(ChangeEventStatus.APPROVING.name());
            assertThat(result.getCostDelta()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("评估缺理由拒绝：审批人需据此判断")
        void rejectsMissingRationale() {
            draft.setStatus(ChangeEventStatus.ASSESSING.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.submitAssessment(555L, assessment("1000", "  ")))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("评估理由");
        }

        @Test
        @DisplayName("评估缺成本金额拒绝（无影响请显式填 0）")
        void rejectsMissingCostDelta() {
            draft.setStatus(ChangeEventStatus.ASSESSING.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent.ImpactAssessment a = new BizChangeEvent.ImpactAssessment();
            a.setRationale("理由");

            assertThatThrownBy(() -> changeEventService.submitAssessment(555L, a))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("成本影响金额");
        }

        @Test
        @DisplayName("草稿状态不得直接提交评估（须先转入评估中）")
        void rejectsAssessmentFromDraft() {
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.submitAssessment(
                    555L, assessment("1000", "理由")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("工期影响为 null 时归零，不污染统计")
        void nullScheduleDelayBecomesZero() {
            draft.setStatus(ChangeEventStatus.ASSESSING.name());
            draft.setAffectedAccounts(new ArrayList<>());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent.ImpactAssessment a = assessment("0", "无影响");
            a.setScheduleDelayDays(null);

            assertThat(changeEventService.submitAssessment(555L, a).getScheduleDelayDays()).isZero();
        }
    }

    @Nested
    @DisplayName("审批流转")
    class Approval {

        @Test
        @DisplayName("批准：置终态、清驳回原因、投递传导事件（含账户明细）")
        void approvesAndPublishesPropagationEvent() {
            draft.setStatus(ChangeEventStatus.APPROVING.name());
            draft.setVersion(3);
            draft.setAffectedAccounts(new ArrayList<>(List.of(
                    accountDelta(1001L, "INCREASE", "50000"),
                    accountDelta(1002L, "DECREASE", "10000"))));
            draft.setAffectedWbsIds(new ArrayList<>(List.of(11L, 12L)));
            draft.setCostDelta(new BigDecimal("40000"));
            draft.setRejectionReason("上一次退回的原因，批准后应清除");
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent result = changeEventService.approve(555L, "同意");

            assertThat(result.getStatus()).isEqualTo(ChangeEventStatus.APPROVED.name());
            assertThat(result.getApprovedAt()).isNotNull();
            assertThat(result.getRejectionReason()).isNull();

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxRecorder).record(eq(ChangeEventEvents.TYPE_APPROVED),
                    eq(ChangeEventEvents.AGGREGATE_TYPE), eq(555L), eq(3), payloadCaptor.capture());

            ChangeEventApprovedEvent payload = (ChangeEventApprovedEvent) payloadCaptor.getValue();
            assertThat(payload.getEventId()).isEqualTo(555L);
            assertThat(payload.getEventNumber()).isEqualTo("CHG20260001");
            assertThat(payload.getProjectId()).isEqualTo(100L);
            assertThat(payload.getCostDelta()).isEqualByComparingTo("40000");
            assertThat(payload.getScheduleDelayDays()).isZero();
            assertThat(payload.getAffectedAccounts()).hasSize(2);
            assertThat(payload.getAffectedWbsIds()).containsExactly(11L, 12L);
            // 契约转换后方向与金额可直接算出带符号值
            assertThat(payload.sumAccountDeltas()).isEqualByComparingTo("40000");
        }

        @Test
        @DisplayName("已批准的事件不可再批准（终态只读）")
        void rejectsReApproval() {
            draft.setStatus(ChangeEventStatus.APPROVED.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.approve(555L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APPROVED");

            verify(outboxRecorder, never()).record(anyString(), anyString(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("草稿不得直接批准（缺评估依据）")
        void rejectsApprovalFromDraft() {
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.approve(555L, null))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("驳回必须留原因，并投递驳回事件")
        void rejectRequiresReason() {
            draft.setStatus(ChangeEventStatus.APPROVING.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.reject(555L, " "))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("驳回原因");

            BizChangeEvent result = changeEventService.reject(555L, "测算依据不足");
            assertThat(result.getStatus()).isEqualTo(ChangeEventStatus.REJECTED.name());
            assertThat(result.getRejectionReason()).isEqualTo("测算依据不足");
            verify(outboxRecorder).record(eq(ChangeEventEvents.TYPE_REJECTED),
                    eq(ChangeEventEvents.AGGREGATE_TYPE), eq(555L), any(), eq(draft));
        }

        @Test
        @DisplayName("退回重评：审批中 → 评估中，保留事件连续性")
        void reAssessKeepsContinuity() {
            draft.setStatus(ChangeEventStatus.APPROVING.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent result = changeEventService.reAssess(555L, "单价取错，重算");

            assertThat(result.getStatus()).isEqualTo(ChangeEventStatus.ASSESSING.name());
            verify(outboxRecorder).record(eq(ChangeEventEvents.TYPE_REASSESS),
                    eq(ChangeEventEvents.AGGREGATE_TYPE), eq(555L), any(), eq(draft));
        }

        @Test
        @DisplayName("已批准不可作废：须登记反向变更冲销，历史不得抹除")
        void approvedCannotBeCancelled() {
            draft.setStatus(ChangeEventStatus.APPROVED.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            // 状态机层面 APPROVED 无出边，assertTransition 先行拦截
            assertThatThrownBy(() -> changeEventService.cancel(555L, "想作废"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("作废草稿：置终态并投递作废事件")
        void cancelsDraft() {
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent result = changeEventService.cancel(555L, "误报");

            assertThat(result.getStatus()).isEqualTo(ChangeEventStatus.CANCELLED.name());
            assertThat(result.getRejectionReason()).isEqualTo("误报");
            verify(outboxRecorder).record(eq(ChangeEventEvents.TYPE_CANCELLED),
                    eq(ChangeEventEvents.AGGREGATE_TYPE), eq(555L), any(), eq(draft));
        }
    }

    @Nested
    @DisplayName("编辑与删除门禁")
    class EditDeleteGuards {

        @Test
        @DisplayName("审批中不可编辑：防止审批人看到的与落库的不是同一份内容")
        void rejectsEditWhileApproving() {
            draft.setStatus(ChangeEventStatus.APPROVING.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent patch = new BizChangeEvent();
            patch.setTitle("偷偷改标题");

            assertThatThrownBy(() -> changeEventService.update(555L, patch))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不允许编辑");

            verify(changeEventMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("已批准只读：终态不可篡改，保证可追溯")
        void rejectsEditWhenApproved() {
            draft.setStatus(ChangeEventStatus.APPROVED.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.update(555L, new BizChangeEvent()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不允许编辑");
        }

        @Test
        @DisplayName("草稿可编辑，且来源类型不可改（改来源等于换业务事实）")
        void allowsDraftEditButNotSourceType() {
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent patch = new BizChangeEvent();
            patch.setTitle("修正后的标题");
            patch.setCategory("COST_IMPACT");

            BizChangeEvent result = changeEventService.update(555L, patch);
            assertThat(result.getTitle()).isEqualTo("修正后的标题");
            assertThat(result.getCategory()).isEqualTo("COST_IMPACT");

            BizChangeEvent sourcePatch = new BizChangeEvent();
            sourcePatch.setSourceType("DESIGN_CHANGE");
            assertThatThrownBy(() -> changeEventService.update(555L, sourcePatch))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("来源类型不可修改");
        }

        @Test
        @DisplayName("非法影响类别拒绝")
        void rejectsInvalidCategory() {
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            BizChangeEvent patch = new BizChangeEvent();
            patch.setCategory("NOT_A_CATEGORY");

            assertThatThrownBy(() -> changeEventService.update(555L, patch))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("非法影响类别");
        }

        @Test
        @DisplayName("仅草稿/已作废可删除；审批链上的必须留痕")
        void deleteOnlyDraftOrCancelled() {
            draft.setStatus(ChangeEventStatus.APPROVING.name());
            when(changeEventMapper.selectById(555L)).thenReturn(draft);

            assertThatThrownBy(() -> changeEventService.delete(555L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("仅草稿或已作废");

            draft.setStatus(ChangeEventStatus.DRAFT.name());
            changeEventService.delete(555L);
            verify(changeEventMapper).deleteById(555L);
        }

        @Test
        @DisplayName("事件不存在：抛业务异常而非返回 null")
        void missingEventThrows() {
            when(changeEventMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> changeEventService.getById(999L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
        }
    }

    @Nested
    @DisplayName("统计查询")
    class Statistics {

        @Test
        @DisplayName("待处理事件数：null 归零")
        void countOpenNullSafe() {
            when(changeEventMapper.countOpenByProject(100L)).thenReturn(null);
            assertThat(changeEventService.countOpen(100L)).isZero();

            when(changeEventMapper.countOpenByProject(100L)).thenReturn(7L);
            assertThat(changeEventService.countOpen(100L)).isEqualTo(7L);
        }

        @Test
        @DisplayName("已批准累计成本影响：null 归零")
        void sumApprovedNullSafe() {
            when(changeEventMapper.sumApprovedCostDelta(100L)).thenReturn(null);
            assertThat(changeEventService.sumApprovedCostDelta(100L)).isEqualByComparingTo("0");

            when(changeEventMapper.sumApprovedCostDelta(100L)).thenReturn(new BigDecimal("88000"));
            assertThat(changeEventService.sumApprovedCostDelta(100L)).isEqualByComparingTo("88000");
        }

        @Test
        @DisplayName("缺项目ID拒绝统计（避免跨项目串数）")
        void statisticsRequireProjectId() {
            assertThatThrownBy(() -> changeEventService.countOpen(null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("项目ID");
            assertThatThrownBy(() -> changeEventService.sumApprovedCostDelta(null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("项目ID");
        }

        @Test
        @DisplayName("空ID列表返回空集合，不打数据库")
        void listByIdsShortCircuits() {
            assertThat(changeEventService.listByIds(null)).isEmpty();
            assertThat(changeEventService.listByIds(List.of())).isEmpty();
            verify(changeEventMapper, never()).selectList(any());
        }
    }

    @Test
    @DisplayName("resolveCostDelta 优先取冗余列，回落到评估对象，最终归零")
    void resolveCostDeltaFallbackChain() {
        BizChangeEvent e = new BizChangeEvent();
        assertThat(e.resolveCostDelta()).isEqualByComparingTo("0");

        e.setImpactAssessment(assessment("12345", "r"));
        assertThat(e.resolveCostDelta()).isEqualByComparingTo("12345");

        e.setCostDelta(new BigDecimal("999"));
        assertThat(e.resolveCostDelta()).isEqualByComparingTo("999");
    }

    @Test
    @DisplayName("isTerminal 与状态机保持一致")
    void isTerminalMatchesStateMachine() {
        draft.setStatus(ChangeEventStatus.APPROVED.name());
        assertThat(draft.isTerminal()).isTrue();
        draft.setStatus(ChangeEventStatus.DRAFT.name());
        assertThat(draft.isTerminal()).isFalse();
        draft.setStatus("DIRTY_VALUE");
        assertThat(draft.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Outbox 写入使用聚合根版本号作幂等区分（二次流转不会被误判重复）")
    void outboxUsesAggregateVersion() {
        draft.setStatus(ChangeEventStatus.APPROVING.name());
        draft.setVersion(7);
        draft.setAffectedAccounts(new ArrayList<>());
        when(changeEventMapper.selectById(555L)).thenReturn(draft);

        changeEventService.approve(555L, null);

        verify(outboxRecorder).record(eq(ChangeEventEvents.TYPE_APPROVED),
                eq(ChangeEventEvents.AGGREGATE_TYPE), eq(555L), eq(7), any());
    }

    @Test
    @DisplayName("无 sourceRef 时查重传 null 排除ID（更新场景语义）")
    void dedupPassesNullExcludeIdOnCreate() {
        when(serialNumberService.generate(anyString())).thenReturn("CHG20260011");
        when(changeEventMapper.selectBySourceRef(eq(100L), eq("FIELD_EVENT"), eq("VISA-1"), isNull()))
                .thenReturn(null);

        BizChangeEvent request = new BizChangeEvent();
        request.setProjectId(100L);
        request.setSourceType("FIELD_EVENT");
        request.setSourceRef("VISA-1");
        request.setTitle("新签证");

        changeEventService.create(request);

        verify(changeEventMapper).selectBySourceRef(100L, "FIELD_EVENT", "VISA-1", null);
    }
}
