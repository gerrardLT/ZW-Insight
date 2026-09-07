package com.zwinsight.contract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.contract.ChangeEventApprovedEvent;
import com.zwinsight.common.event.outbox.OutboxEventRecorder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeEvent;
import com.zwinsight.contract.domain.ChangeEventStatus;
import com.zwinsight.contract.mapper.BizChangeEventMapper;
import com.zwinsight.file.service.SerialNumberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 变更事件服务 —— 变更主链的编排者。
 *
 * <h3>业务定位</h3>
 * <p>
 * 变更主链：<b>现场事件 → Change Event → 影响评估 → 审批 → 预算/合同/收入变更</b>。
 * 本服务只负责「事件自身的生命周期」，跨模块的金额传导通过
 * {@link OutboxEventRecorder} 投递领域事件，由下游 Handler 消费，
 * 避免 zw-contract 直接写 zw-budget 的表（跨模块禁止直接互相修改字段）。
 * </p>
 *
 * <h3>状态机与审批流分离</h3>
 * <ul>
 *   <li>{@code status} 字段：业务状态机（{@link ChangeEventStatus}），决定「事件到哪一步了」</li>
 *   <li>{@code workflowInstanceId}：BPMN 审批流指针，决定「谁来批、几级批」</li>
 * </ul>
 * <p>审批流不得成为业务模型本身：即使没有配置 BPMN，事件仍可走 approve/reject 直接流转。</p>
 *
 * <h3>数据一致性保证</h3>
 * <ul>
 *   <li>所有状态流转走 {@link ChangeEventStatus#assertTransition} 校验，非法流转抛异常</li>
 *   <li>sourceType + sourceRef 查重，同一业务事实不得重复登记</li>
 *   <li>乐观锁 version 由 MyBatis-Plus 自动处理，并发流转只有一个成功</li>
 *   <li>Outbox 事件与状态变更同事务写入，保证「改了状态就一定有事件」</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeEventService {

    /** 编号规则业务类型（对应 serial_number_rule.business_type） */
    private static final String SERIAL_BUSINESS_TYPE = "CHANGE_EVENT";

    /** 聚合根类型（Outbox 事件溯源用） */
    private static final String AGGREGATE_TYPE = "ChangeEvent";

    /** 允许的合法来源类型 */
    private static final List<String> VALID_SOURCE_TYPES = List.of(
            "FIELD_EVENT", "DESIGN_CHANGE", "OWNER_REQUEST", "VARIATION_ORDER", "OTHER");

    /** 允许的影响类别 */
    private static final List<String> VALID_CATEGORIES = List.of(
            "COST_IMPACT", "SCOPE_CHANGE", "SCHEDULE_DELAY", "QUALITY_ISSUE", "OTHER");

    private final BizChangeEventMapper changeEventMapper;
    private final SerialNumberService serialNumberService;
    private final OutboxEventRecorder outboxRecorder;

    // ==================== 查询 ====================

    /**
     * 分页查询变更事件。
     */
    public PageResult<BizChangeEvent> page(int page, int size, Long projectId, String status,
                                           String sourceType, String category, String keyword) {
        Page<BizChangeEvent> pageParam = new Page<>(page, size);
        Page<BizChangeEvent> result = (Page<BizChangeEvent>) changeEventMapper.selectChangeEventPage(
                pageParam, projectId, blankToNull(status), blankToNull(sourceType),
                blankToNull(category), blankToNull(keyword));
        return PageResult.of(result);
    }

    /**
     * 查询详情（不存在抛业务异常，不返回 null，避免前端空指针）。
     */
    public BizChangeEvent getById(Long id) {
        BizChangeEvent event = changeEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException("变更事件不存在：" + id);
        }
        return event;
    }

    /**
     * 项目下待处理（非终态）事件数，用于工作台待办角标。
     */
    public Long countOpen(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        Long count = changeEventMapper.countOpenByProject(projectId);
        return count != null ? count : 0L;
    }

    /**
     * 项目下已批准变更的累计成本影响。
     */
    public BigDecimal sumApprovedCostDelta(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        BigDecimal sum = changeEventMapper.sumApprovedCostDelta(projectId);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    /**
     * 批量查询（供跨模块 Handler 回填展示字段）。
     */
    public List<BizChangeEvent> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return changeEventMapper.selectList(new LambdaQueryWrapper<BizChangeEvent>()
                .in(BizChangeEvent::getId, ids));
    }

    // ==================== 写入：生命周期流转 ====================

    /**
     * 登记变更事件（草稿）。
     * <p>
     * 现场人员用最低成本把「发生了什么」记录下来，此时不要求填影响评估——
     * 影响测算是商务/造价的职责，混在登记环节会导致现场不敢报、报不准。
     * </p>
     *
     * @param request 事件内容（projectId/sourceType/title 必填）
     * @return 已落库的事件（含生成的 eventNumber）
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent create(BizChangeEvent request) {
        if (request.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (StrUtil.isBlank(request.getSourceType())) {
            throw new BusinessException("来源类型不能为空");
        }
        if (!VALID_SOURCE_TYPES.contains(request.getSourceType())) {
            throw new BusinessException("非法来源类型：" + request.getSourceType()
                    + "，允许值 " + VALID_SOURCE_TYPES);
        }
        if (StrUtil.isBlank(request.getTitle())) {
            throw new BusinessException("标题不能为空");
        }
        if (request.getTitle().length() > 300) {
            throw new BusinessException("标题长度不得超过 300 字符");
        }
        if (StrUtil.isNotBlank(request.getCategory()) && !VALID_CATEGORIES.contains(request.getCategory())) {
            throw new BusinessException("非法影响类别：" + request.getCategory()
                    + "，允许值 " + VALID_CATEGORIES);
        }

        // 业务事实去重：同一来源引用不得重复登记（防止一张签证录两遍造成成本双算）
        if (StrUtil.isNotBlank(request.getSourceRef())) {
            BizChangeEvent dup = changeEventMapper.selectBySourceRef(
                    request.getProjectId(), request.getSourceType(), request.getSourceRef(), null);
            if (dup != null) {
                throw new BusinessException("该来源已登记过变更事件[" + dup.getEventNumber()
                        + "]，禁止重复录入；如需补充请在原事件上追加佐证");
            }
        }

        request.setEventNumber(serialNumberService.generate(SERIAL_BUSINESS_TYPE));
        request.setStatus(ChangeEventStatus.DRAFT.name());
        request.setCostDelta(BigDecimal.ZERO);
        request.setScheduleDelayDays(0);
        if (request.getAffectedWbsIds() == null) {
            request.setAffectedWbsIds(new ArrayList<>());
        }
        if (request.getAffectedAccounts() == null) {
            request.setAffectedAccounts(new ArrayList<>());
        }
        if (request.getSupportingDocs() == null) {
            request.setSupportingDocs(new ArrayList<>());
        }

        changeEventMapper.insert(request);

        outboxRecorder.record(ChangeEventEvents.TYPE_CREATED, AGGREGATE_TYPE,
                request.getId(), request.getVersion(), request);

        log.info("变更事件登记成功，id={}, eventNumber={}, projectId={}, sourceType={}",
                request.getId(), request.getEventNumber(), request.getProjectId(), request.getSourceType());
        return request;
    }

    /**
     * 编辑事件内容（仅 DRAFT/ASSESSING 可编辑；终态只读，保证已批准变更可追溯不可篡改）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent update(Long id, BizChangeEvent patch) {
        BizChangeEvent existing = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(existing.getStatus());
        if (current == null || !current.isEditable()) {
            throw new BusinessException("当前状态[" + existing.getStatus() + "]不允许编辑，"
                    + "仅草稿/评估中可修改");
        }

        if (StrUtil.isNotBlank(patch.getTitle())) {
            if (patch.getTitle().length() > 300) {
                throw new BusinessException("标题长度不得超过 300 字符");
            }
            existing.setTitle(patch.getTitle());
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription());
        }
        if (StrUtil.isNotBlank(patch.getCategory())) {
            if (!VALID_CATEGORIES.contains(patch.getCategory())) {
                throw new BusinessException("非法影响类别：" + patch.getCategory());
            }
            existing.setCategory(patch.getCategory());
        }
        if (patch.getAffectedWbsIds() != null) {
            existing.setAffectedWbsIds(patch.getAffectedWbsIds());
        }
        if (patch.getAffectedAccounts() != null) {
            existing.setAffectedAccounts(patch.getAffectedAccounts());
        }
        if (patch.getSupportingDocs() != null) {
            existing.setSupportingDocs(patch.getSupportingDocs());
        }
        // sourceType/sourceRef 不允许改：改了等于换了业务事实来源，应作废重登
        if (StrUtil.isNotBlank(patch.getSourceType())
                && !patch.getSourceType().equals(existing.getSourceType())) {
            throw new BusinessException("来源类型不可修改；如需变更来源请作废本事件后重新登记");
        }

        changeEventMapper.updateById(existing);

        outboxRecorder.record(ChangeEventEvents.TYPE_UPDATED, AGGREGATE_TYPE,
                existing.getId(), existing.getVersion(), existing);

        log.info("变更事件更新成功，id={}, eventNumber={}", id, existing.getEventNumber());
        return existing;
    }

    /**
     * 转入评估中（现场登记完成，交商务测算影响）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent startAssessment(Long id) {
        return transition(id, ChangeEventStatus.ASSESSING, ChangeEventEvents.TYPE_ASSESSING);
    }

    /**
     * 提交影响评估（成本 + 工期 + 理由），并流转到审批中。
     * <p>
     * 评估是变更决策的核心依据，因此成本影响与评估理由为必填；
     * 工期影响允许为 0（很多变更不影响工期，但必须显式确认过）。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent submitAssessment(Long id, BizChangeEvent.ImpactAssessment assessment) {
        BizChangeEvent existing = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(existing.getStatus());
        ChangeEventStatus.assertTransition(current, ChangeEventStatus.APPROVING, existing.getEventNumber());

        if (assessment == null) {
            throw new BusinessException("影响评估不能为空");
        }
        if (assessment.getCostDelta() == null) {
            throw new BusinessException("成本影响金额不能为空（无成本影响请填 0）");
        }
        if (StrUtil.isBlank(assessment.getRationale())) {
            throw new BusinessException("评估理由不能为空，审批人需据此判断");
        }
        if (assessment.getRationale().length() > 2000) {
            throw new BusinessException("评估理由长度不得超过 2000 字符");
        }

        existing.setImpactAssessment(assessment);
        // 冗余到列上：列表排序/统计走索引，避免每次解析 JSON
        existing.setCostDelta(assessment.getCostDelta());
        existing.setScheduleDelayDays(assessment.getScheduleDelayDays() != null
                ? assessment.getScheduleDelayDays() : 0);
        existing.setAssessedBy(currentUserId());
        existing.setAssessedAt(LocalDateTime.now());
        existing.setStatus(ChangeEventStatus.APPROVING.name());

        // 评估阶段必须指明影响哪些成本账户，否则批准后无法传导（断链）
        if (assessment.getCostDelta().signum() != 0 && existing.resolveAffectedAccounts().isEmpty()) {
            throw new BusinessException("成本影响不为 0 时必须指定受影响的成本账户，"
                    + "否则批准后无法传导到 CBS 当前预算");
        }

        changeEventMapper.updateById(existing);

        outboxRecorder.record(ChangeEventEvents.TYPE_ASSESSED, AGGREGATE_TYPE,
                existing.getId(), existing.getVersion(), existing);

        log.info("变更事件影响评估提交，id={}, eventNumber={}, costDelta={}, delayDays={}",
                id, existing.getEventNumber(), existing.getCostDelta(), existing.getScheduleDelayDays());
        return existing;
    }

    /**
     * 批准变更事件（终态）。
     * <p>
     * 批准后由 Outbox 投递 {@code CHANGE_EVENT_APPROVED}，下游 Handler 负责：
     * ① 按 affectedAccounts 调整 CBS current_amount；② 回写合同累计变更金额；
     * ③ 必要时生成预算变更记录。本服务不直接跨模块写数据。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent approve(Long id, String comment) {
        BizChangeEvent event = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(event.getStatus());
        ChangeEventStatus.assertTransition(current, ChangeEventStatus.APPROVED, event.getEventNumber());

        event.setStatus(ChangeEventStatus.APPROVED.name());
        event.setApprovedBy(currentUserId());
        event.setApprovedAt(LocalDateTime.now());
        // 批准不得携带驳回原因，避免历史噪声误导后续查阅
        event.setRejectionReason(null);
        changeEventMapper.updateById(event);

        // 批准是变更主链的关键跃迁，必须投递事件驱动下游传导
        outboxRecorder.record(ChangeEventEvents.TYPE_APPROVED, ChangeEventEvents.AGGREGATE_TYPE,
                event.getId(), event.getVersion(), buildApprovedPayload(event));

        log.info("变更事件已批准，id={}, eventNumber={}, costDelta={}, affectedAccounts={}",
                id, event.getEventNumber(), event.getCostDelta(), event.resolveAffectedAccounts().size());
        return event;
    }

    /**
     * 驳回变更事件（终态）。驳回必须留原因，作为后续复盘与知识沉淀的输入。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent reject(Long id, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("驳回原因不能为空");
        }
        BizChangeEvent event = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(event.getStatus());
        ChangeEventStatus.assertTransition(current, ChangeEventStatus.REJECTED, event.getEventNumber());

        event.setRejectionReason(StrUtil.maxLength(reason, 480));
        event.setStatus(ChangeEventStatus.REJECTED.name());
        event.setApprovedBy(currentUserId());
        event.setApprovedAt(LocalDateTime.now());
        changeEventMapper.updateById(event);

        outboxRecorder.record(ChangeEventEvents.TYPE_REJECTED, AGGREGATE_TYPE,
                event.getId(), event.getVersion(), event);

        log.info("变更事件已驳回，id={}, eventNumber={}, reason={}", id, event.getEventNumber(), reason);
        return event;
    }

    /**
     * 退回重新评估（审批中发现测算有误，回到评估中而非直接驳回，保留事件连续性）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent reAssess(Long id, String reason) {
        BizChangeEvent event = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(event.getStatus());
        ChangeEventStatus.assertTransition(current, ChangeEventStatus.ASSESSING, event.getEventNumber());

        event.setStatus(ChangeEventStatus.ASSESSING.name());
        if (StrUtil.isNotBlank(reason)) {
            event.setRejectionReason(StrUtil.maxLength(reason, 480));
        }
        changeEventMapper.updateById(event);

        outboxRecorder.record(ChangeEventEvents.TYPE_REASSESS, ChangeEventEvents.AGGREGATE_TYPE,
                event.getId(), event.getVersion(), event);

        log.info("变更事件退回重新评估，id={}, eventNumber={}", id, event.getEventNumber());
        return event;
    }

    /**
     * 作废事件（仅草稿/评估中/审批中可作废；已批准的必须走反向变更，不能作废抹除历史）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizChangeEvent cancel(Long id, String reason) {
        BizChangeEvent event = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(event.getStatus());
        ChangeEventStatus.assertTransition(current, ChangeEventStatus.CANCELLED, event.getEventNumber());

        if (current == ChangeEventStatus.APPROVED) {
            throw new BusinessException("已批准的变更事件不可作废，请登记反向变更事件冲销");
        }
        event.setStatus(ChangeEventStatus.CANCELLED.name());
        if (StrUtil.isNotBlank(reason)) {
            event.setRejectionReason(StrUtil.maxLength(reason, 480));
        }
        changeEventMapper.updateById(event);

        outboxRecorder.record(ChangeEventEvents.TYPE_CANCELLED, ChangeEventEvents.AGGREGATE_TYPE,
                event.getId(), event.getVersion(), event);

        log.info("变更事件已作废，id={}, eventNumber={}", id, event.getEventNumber());
        return event;
    }

    /**
     * 删除事件（物理逻辑删除，仅草稿/已作废允许；已进入审批链的必须留痕）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizChangeEvent event = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(event.getStatus());
        if (current != ChangeEventStatus.DRAFT && current != ChangeEventStatus.CANCELLED) {
            throw new BusinessException("仅草稿或已作废的变更事件可删除，当前状态：" + event.getStatus());
        }
        changeEventMapper.deleteById(id);
        log.info("变更事件已删除，id={}, eventNumber={}", id, event.getEventNumber());
    }

    // ==================== 内部工具 ====================

    /**
     * 通用状态流转：校验合法性 → 落状态 → 投递事件。
     *
     * @param id        事件ID
     * @param target    目标状态
     * @param eventType 投递的领域事件类型（取自 {@link ChangeEventEvents}）
     */
    private BizChangeEvent transition(Long id, ChangeEventStatus target, String eventType) {
        BizChangeEvent event = getById(id);
        ChangeEventStatus current = ChangeEventStatus.fromCode(event.getStatus());
        ChangeEventStatus.assertTransition(current, target, event.getEventNumber());

        event.setStatus(target.name());
        changeEventMapper.updateById(event);

        outboxRecorder.record(eventType, ChangeEventEvents.AGGREGATE_TYPE,
                event.getId(), event.getVersion(), event);

        log.info("变更事件状态流转，id={}, eventNumber={}, {}→{}",
                id, event.getEventNumber(), current, target);
        return event;
    }

    /**
     * 构建批准事件的下游传导负载。
     * <p>
     * 使用 zw-common 的共享契约 {@link ChangeEventApprovedEvent}（Published Language）：
     * 消费方（成本账户在 zw-budget、看板在 zw-dashboard）只需依赖 zw-common，
     * 不必反向依赖 zw-contract，模块边界保持单向。
     * </p>
     */
    private ChangeEventApprovedEvent buildApprovedPayload(BizChangeEvent event) {
        ChangeEventApprovedEvent payload = new ChangeEventApprovedEvent();
        payload.setEventId(event.getId());
        payload.setEventNumber(event.getEventNumber());
        payload.setProjectId(event.getProjectId());
        payload.setTenantId(event.getTenantId());
        payload.setCostDelta(event.resolveCostDelta());
        payload.setScheduleDelayDays(event.resolveScheduleDelayDays());
        payload.setApprovedAt(event.getApprovedAt());
        payload.setApprovedBy(event.getApprovedBy());
        payload.setAffectedWbsIds(event.getAffectedWbsIds() != null
                ? new ArrayList<>(event.getAffectedWbsIds())
                : new ArrayList<>());

        // 实体值对象 → 事件契约值对象：两侧解耦，聚合演进不会击穿消费者
        List<ChangeEventApprovedEvent.AccountDelta> deltas = new ArrayList<>();
        for (BizChangeEvent.AffectedAccount src : event.resolveAffectedAccounts()) {
            if (src == null || src.getAccountId() == null) {
                continue;
            }
            ChangeEventApprovedEvent.AccountDelta delta = new ChangeEventApprovedEvent.AccountDelta();
            delta.setAccountId(src.getAccountId());
            delta.setDeltaType(src.getDeltaType());
            delta.setDeltaAmount(src.getDeltaAmount() != null ? src.getDeltaAmount() : BigDecimal.ZERO);
            deltas.add(delta);
        }
        payload.setAffectedAccounts(deltas);
        return payload;
    }

    private Long currentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            // 无上下文（定时任务/内部调用）时不伪造用户，返回 null 由审计侧标记为系统操作
            log.warn("变更事件操作缺少用户上下文，将以系统身份记录");
        }
        return userId;
    }

    private static String blankToNull(String s) {
        return StrUtil.isBlank(s) ? null : s;
    }
}
