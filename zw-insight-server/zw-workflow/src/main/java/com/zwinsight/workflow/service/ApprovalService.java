package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.domain.WfApprovalRecord;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.mapper.WfApprovalRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心审批服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final IdentityService identityService;
    private final ApplicationEventPublisher eventPublisher;
    private final WfApprovalRecordMapper approvalRecordMapper;
    private final SysUserMapper sysUserMapper;

    /** 超级管理员角色编码，拥有全部审批操作权限，跳过处理人校验 */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * 流程内部变量，不作为业务数据展示
     *  - businessType/businessId/initiator：审批桥接标准协议字段
     *  - businessTitle：已由 getTaskDetail() 提升为顶层业务摘要，避免在业务详情区重复渲染
     */
    private static final Set<String> INTERNAL_VARIABLES = Set.of("businessType", "businessId", "initiator", "businessTitle");

    /**
     * 发起流程
     *
     * @param businessType 业务类型标识
     * @param businessId   业务ID
     * @param processKey   流程标识
     * @param variables    流程变量
     * @return 流程实例ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String startProcess(String businessType, Long businessId, String processKey, Map<String, Object> variables) {
        Long userId = SecurityContextHolder.getUserId();
        Long tenantId = SecurityContextHolder.getTenantId();

        if (variables == null) {
            variables = new HashMap<>();
        }
        variables.put("businessType", businessType);
        variables.put("businessId", businessId);
        variables.put("initiator", String.valueOf(userId));

        // 设置businessKey为 businessType:businessId
        String businessKey = businessType + ":" + businessId;

        // 记录流程发起人，使 HistoricProcessInstance.startUserId 可用（支持"我发起"查询）
        identityService.setAuthenticatedUserId(String.valueOf(userId));

        ProcessInstance processInstance;
        try {
            processInstance = runtimeService.startProcessInstanceByKeyAndTenantId(
                    processKey, businessKey, variables, String.valueOf(tenantId));
        } finally {
            // 清理线程绑定的发起人，防止线程池复用导致后续流程发起人被污染
            identityService.setAuthenticatedUserId(null);
        }

        log.info("流程发起成功, processInstanceId={}, businessKey={}, userId={}",
                processInstance.getId(), businessKey, userId);

        return processInstance.getId();
    }

    /**
     * 办理（通过）
     *
     * @param taskId    任务ID
     * @param comment   审批意见
     * @param variables 流程变量
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(String taskId, String comment, Map<String, Object> variables) {
        Task task = getTaskById(taskId);
        Long userId = SecurityContextHolder.getUserId();
        assertTaskAssignee(task, userId);

        // 添加审批意见
        if (comment != null && !comment.isEmpty()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }

        // 完成任务
        if (variables != null && !variables.isEmpty()) {
            taskService.complete(taskId, variables);
        } else {
            taskService.complete(taskId);
        }

        // 记录审批记录
        saveApprovalRecord(task, String.valueOf(userId), "APPROVE", comment);

        log.info("任务办理完成, taskId={}, userId={}", taskId, userId);
    }

    /**
     * 退回至上一节点
     *
     * @param taskId  任务ID
     * @param comment 审批意见
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectToPrevious(String taskId, String comment) {
        Task task = getTaskById(taskId);
        Long userId = SecurityContextHolder.getUserId();
        assertTaskAssignee(task, userId);

        // 查找上一个用户任务节点
        List<HistoricActivityInstance> activityInstances = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .activityType("userTask")
                .finished()
                .orderByHistoricActivityInstanceEndTime()
                .desc()
                .list();

        if (activityInstances.isEmpty()) {
            throw new BusinessException("没有可退回的节点");
        }

        // 取最近完成的用户任务节点
        String targetActivityId = activityInstances.get(0).getActivityId();

        // 添加审批意见
        if (comment != null && !comment.isEmpty()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), "【退回】" + comment);
        }

        // 使用 createChangeActivityStateBuilder 退回
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(task.getTaskDefinitionKey(), targetActivityId)
                .changeState();

        // 记录审批记录
        saveApprovalRecord(task, String.valueOf(userId), "REJECT", comment);

        // 发布驳回事件，触发数据回滚
        Map<String, Object> processVariables = runtimeService.getVariables(task.getProcessInstanceId());
        String businessType = (String) processVariables.get("businessType");
        Object businessIdObj = processVariables.get("businessId");
        if (businessType != null && businessIdObj != null) {
            Long businessId = businessIdObj instanceof Long
                    ? (Long) businessIdObj : Long.valueOf(businessIdObj.toString());
            eventPublisher.publishEvent(new ApprovalRejectEvent(
                    this, task.getProcessInstanceId(), businessType, businessId, "REJECT"));
        }

        log.info("任务退回至上一节点, taskId={}, targetActivity={}", taskId, targetActivityId);
    }

    /**
     * 退回至发起人
     *
     * @param taskId  任务ID
     * @param comment 审批意见
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectToStart(String taskId, String comment) {
        Task task = getTaskById(taskId);
        Long userId = SecurityContextHolder.getUserId();
        assertTaskAssignee(task, userId);

        // 查找第一个用户任务节点（发起人节点）
        List<HistoricActivityInstance> activityInstances = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        if (activityInstances.isEmpty()) {
            throw new BusinessException("找不到发起人节点");
        }

        String startActivityId = activityInstances.get(0).getActivityId();

        // 添加审批意见
        if (comment != null && !comment.isEmpty()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), "【退回发起人】" + comment);
        }

        // 退回至发起人节点
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(task.getTaskDefinitionKey(), startActivityId)
                .changeState();

        // 记录审批记录
        saveApprovalRecord(task, String.valueOf(userId), "REJECT_TO_START", comment);

        // 发布驳回事件，触发数据回滚
        Map<String, Object> processVariables = runtimeService.getVariables(task.getProcessInstanceId());
        String businessType = (String) processVariables.get("businessType");
        Object businessIdObj = processVariables.get("businessId");
        if (businessType != null && businessIdObj != null) {
            Long businessId = businessIdObj instanceof Long
                    ? (Long) businessIdObj : Long.valueOf(businessIdObj.toString());
            eventPublisher.publishEvent(new ApprovalRejectEvent(
                    this, task.getProcessInstanceId(), businessType, businessId, "REJECT"));
        }

        log.info("任务退回至发起人, taskId={}, targetActivity={}", taskId, startActivityId);
    }

    /**
     * 终止流程
     *
     * @param taskId  任务ID
     * @param comment 终止原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void terminate(String taskId, String comment) {
        Task task = getTaskById(taskId);
        Long userId = SecurityContextHolder.getUserId();
        assertTaskAssignee(task, userId);

        // 添加审批意见
        if (comment != null && !comment.isEmpty()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), "【终止】" + comment);
        }

        // 获取流程变量（终止前获取，终止后不可用）
        Map<String, Object> processVariables = taskService.getVariables(task.getId());
        String businessType = processVariables != null ? (String) processVariables.get("businessType") : null;
        Object businessIdObj = processVariables != null ? processVariables.get("businessId") : null;

        // 删除流程实例（终止）
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), "终止：" + comment);

        // 记录审批记录
        saveApprovalRecord(task, String.valueOf(userId), "TERMINATE", comment);

        // 发布撤回事件，触发数据回滚
        if (businessType != null && businessIdObj != null) {
            Long businessId = businessIdObj instanceof Long
                    ? (Long) businessIdObj : Long.valueOf(businessIdObj.toString());
            eventPublisher.publishEvent(new ApprovalRejectEvent(
                    this, task.getProcessInstanceId(), businessType, businessId, "WITHDRAW"));
        }

        log.info("流程已终止, taskId={}, processInstanceId={}", taskId, task.getProcessInstanceId());
    }

    /**
     * 按业务单据撤回流程（自动化回收专用，O(1) businessKey 定位）
     * <p>
     * 背景（2026-08-13 二次事故）：高速提交下（~30 笔/秒）基于待办分页扫描的
     * 回收因窗口失配大量失效，20 分钟堆积 7000+ 任务。本方法按
     * businessKey=businessType:businessId 直接定位运行中任务，与提交速率无关。
     * 仅流程发起人可撤回；无运行中流程时幂等返回 false（不报错，清理语义）。
     *
     * @param businessType 业务类型（如 PAYMENT_APPLY）
     * @param businessId   业务单据ID
     * @return true=已撤回；false=无运行中流程
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean withdrawByBusiness(String businessType, Long businessId) {
        String businessKey = businessType + ":" + businessId;
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceBusinessKey(businessKey)
                .listPage(0, 1);
        if (tasks.isEmpty()) {
            return false;
        }
        Task task = tasks.get(0);
        Long userId = SecurityContextHolder.getUserId();

        // 仅发起人可撤回（防越权终止他人流程）
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (instance != null && instance.getStartUserId() != null
                && !instance.getStartUserId().equals(String.valueOf(userId))) {
            throw new BusinessException("仅流程发起人可撤回");
        }

        // 删除流程实例并发布撤回事件（单据置 REJECTED，不回写累计金额）
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), "自动化撤回：" + businessKey);
        saveApprovalRecord(task, String.valueOf(userId), "TERMINATE", "自动化撤回");
        eventPublisher.publishEvent(new ApprovalRejectEvent(
                this, task.getProcessInstanceId(), businessType, businessId, "WITHDRAW"));

        log.info("流程已按业务撤回, businessKey={}, processInstanceId={}", businessKey, task.getProcessInstanceId());
        return true;
    }

    /**
     * 转办
     *
     * @param taskId       任务ID
     * @param targetUserId 目标用户ID
     * @param comment      转办说明
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String taskId, String targetUserId, String comment) {
        Task task = getTaskById(taskId);
        Long userId = SecurityContextHolder.getUserId();
        assertTaskAssignee(task, userId);

        // 添加审批意见
        if (comment != null && !comment.isEmpty()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), "【转办】" + comment);
        }

        // 设置新的处理人
        taskService.setAssignee(taskId, targetUserId);

        // 记录审批记录
        saveApprovalRecord(task, String.valueOf(userId), "TRANSFER", comment);

        log.info("任务转办, taskId={}, fromUser={}, toUser={}", taskId, userId, targetUserId);
    }

    /**
     * 委托
     *
     * @param taskId         任务ID
     * @param delegateUserId 被委托人ID
     * @param comment        委托说明
     */
    @Transactional(rollbackFor = Exception.class)
    public void delegate(String taskId, String delegateUserId, String comment) {
        Task task = getTaskById(taskId);
        Long userId = SecurityContextHolder.getUserId();
        assertTaskAssignee(task, userId);

        // 添加审批意见
        if (comment != null && !comment.isEmpty()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), "【委托】" + comment);
        }

        // 委托任务
        taskService.delegateTask(taskId, delegateUserId);

        // 记录审批记录
        saveApprovalRecord(task, String.valueOf(userId), "DELEGATE", comment);

        log.info("任务委托, taskId={}, fromUser={}, delegateTo={}", taskId, userId, delegateUserId);
    }

    /**
     * 我的待办
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> getMyTodoTasks(Long userId, int page, int size) {
        long count = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(userId))
                .count();

        // includeProcessVariables：随分页查询一次性携带流程变量，
        // 替代原 taskToMap 内逐任务 getVariables 的 N+1 查询
        // （2026-08-13 事故：ACT_RU_TASK 6万+行时 N+1 致 /todo 超时/500）
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(userId))
                .includeProcessVariables()
                .orderByTaskCreateTime()
                .desc()
                .listPage((page - 1) * size, size);

        List<Map<String, Object>> records = tasks.stream()
                .map(this::taskToMap)
                .collect(Collectors.toList());

        // 批量补充发起人姓名（initiator 来自流程变量中的 userId 字符串，批量查询避免 N+1）
        Map<String, String> initiatorNames = lookupRealNames(tasks.stream()
                .map(this::extractInitiator)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        for (int i = 0; i < records.size(); i++) {
            String initiator = extractInitiator(tasks.get(i));
            records.get(i).put("startUserName",
                    initiator != null ? initiatorNames.getOrDefault(initiator, initiator) : null);
        }

        long pages = (count + size - 1) / size;
        return new PageResult<>(records, count, page, size, pages);
    }

    /**
     * 审批详情聚合查询（移动端审批详情页）
     * <p>
     * 一次聚合任务信息、流程信息（名称/发起人/发起时间）、业务数据（流程变量去内部变量）
     * 与审批记录时间线；同时支持运行中任务（待办）与已结束任务（已办详情回看）。
     * </p>
     *
     * @param taskId 任务ID（运行时或历史任务均可）
     * @return 聚合详情 Map
     * @throws BusinessException 任务不存在时
     */
    public Map<String, Object> getTaskDetail(String taskId) {
        // 优先查运行中任务；不在则回退历史任务（已办详情）
        Task runningTask = taskService.createTaskQuery()
                .taskId(taskId).includeProcessVariables().singleResult();
        String processInstanceId;
        String taskName;
        Map<String, Object> processVariables;
        boolean running;
        if (runningTask != null) {
            running = true;
            processInstanceId = runningTask.getProcessInstanceId();
            taskName = runningTask.getName();
            processVariables = runningTask.getProcessVariables() != null
                    ? runningTask.getProcessVariables() : Collections.emptyMap();
        } else {
            HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                    .taskId(taskId).includeProcessVariables().singleResult();
            if (historicTask == null) {
                throw new BusinessException("任务不存在: " + taskId);
            }
            running = false;
            processInstanceId = historicTask.getProcessInstanceId();
            taskName = historicTask.getName();
            processVariables = historicTask.getProcessVariables() != null
                    ? historicTask.getProcessVariables() : Collections.emptyMap();
        }

        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", taskId);
        detail.put("taskName", taskName);
        detail.put("processInstanceId", processInstanceId);
        detail.put("processName", instance != null ? instance.getProcessDefinitionName() : null);
        detail.put("createTime", instance != null ? instance.getStartTime() : null);
        detail.put("status", running ? "pending" : "done");

        // 申请人（发起时经 identityService 记录的 startUserId → sys_user.realName）
        String startUserId = instance != null ? instance.getStartUserId() : null;
        detail.put("startUserName", lookupRealNames(
                startUserId != null ? List.of(startUserId) : Collections.emptyList()).get(startUserId));

        // 业务信息：businessKey 同源变量 + 可选 businessTitle 变量
        String businessType = processVariables.get("businessType") != null
                ? String.valueOf(processVariables.get("businessType")) : null;
        Object businessIdObj = processVariables.get("businessId");
        detail.put("businessType", businessType);
        detail.put("businessId", businessIdObj);
        Object businessTitle = processVariables.get("businessTitle");
        detail.put("businessTitle", businessTitle != null ? String.valueOf(businessTitle)
                : (businessType != null && businessIdObj != null ? businessType + ":" + businessIdObj : null));

        // 业务数据：流程变量去内部变量，前端按 key-value 渲染
        Map<String, Object> businessData = new LinkedHashMap<>();
        processVariables.forEach((k, v) -> {
            if (!INTERNAL_VARIABLES.contains(k) && v != null) {
                businessData.put(k, v);
            }
        });
        detail.put("businessData", businessData);

        // 审批记录时间线（operTime 升序）
        List<WfApprovalRecord> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<WfApprovalRecord>()
                        .eq(WfApprovalRecord::getProcessInstanceId, processInstanceId)
                        .orderByAsc(WfApprovalRecord::getOperTime));
        Map<String, String> assigneeNames = lookupRealNames(records.stream()
                .map(WfApprovalRecord::getAssignee)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        List<Map<String, Object>> recordMaps = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", r.getId());
            m.put("taskName", r.getTaskName());
            m.put("assigneeName", r.getAssigneeName() != null && !r.getAssigneeName().isBlank()
                    ? r.getAssigneeName()
                    : assigneeNames.getOrDefault(r.getAssignee(), r.getAssignee()));
            String[] mapped = mapOperationType(r.getOperationType());
            m.put("result", mapped[0]);
            m.put("resultText", mapped[1]);
            m.put("comment", r.getComment());
            m.put("endTime", r.getOperTime());
            return m;
        }).collect(Collectors.toList());
        detail.put("approvalRecords", recordMaps);

        return detail;
    }

    /**
     * 按流程实例查审批轨迹（单据穿透链 §13 末环，驾驶舱 P2-4）。
     * <p>业务单据（合同/付款申请等）只存 workflowInstanceId，无 taskId 入口；
     * 本方法直接按流程实例聚合流程信息 + wf_approval_record 时间线（与
     * {@link #getTaskDetail} 同一记录映射口径，不另搞一套）。无审批记录时返回空列表
     * +如实提示（可能未经审批直接生效或历史已清理），不伪造轨迹。</p>
     *
     * @param processInstanceId 流程实例ID（单据表 workflow_instance_id 列）
     */
    public Map<String, Object> getApprovalTrace(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new BusinessException("流程实例ID不能为空");
        }
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", processInstanceId);
        result.put("processName", instance != null ? instance.getProcessDefinitionName() : null);
        result.put("startTime", instance != null ? instance.getStartTime() : null);
        result.put("endTime", instance != null ? instance.getEndTime() : null);
        result.put("status", instance == null ? "UNKNOWN"
                : (instance.getEndTime() == null ? "RUNNING" : "COMPLETED"));
        String startUserId = instance != null ? instance.getStartUserId() : null;
        result.put("startUserName", lookupRealNames(
                startUserId != null ? List.of(startUserId) : Collections.emptyList()).get(startUserId));

        List<WfApprovalRecord> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<WfApprovalRecord>()
                        .eq(WfApprovalRecord::getProcessInstanceId, processInstanceId)
                        .orderByAsc(WfApprovalRecord::getOperTime));
        Map<String, String> assigneeNames = lookupRealNames(records.stream()
                .map(WfApprovalRecord::getAssignee)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        List<Map<String, Object>> recordMaps = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("taskName", r.getTaskName());
            m.put("assigneeName", r.getAssigneeName() != null && !r.getAssigneeName().isBlank()
                    ? r.getAssigneeName()
                    : assigneeNames.getOrDefault(r.getAssignee(), r.getAssignee()));
            String[] mapped = mapOperationType(r.getOperationType());
            m.put("result", mapped[0]);
            m.put("resultText", mapped[1]);
            m.put("comment", r.getComment());
            m.put("endTime", r.getOperTime());
            return m;
        }).collect(Collectors.toList());
        result.put("approvalRecords", recordMaps);
        if (instance == null) {
            result.put("note", "流程实例不存在（可能历史已清理或单据未经流程审批），无审批轨迹");
        } else if (recordMaps.isEmpty()) {
            result.put("note", "该流程无审批操作记录（如自动通过/历史记录未落），不伪造轨迹");
        }
        return result;
    }

    /**
     * 我的已办
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> getMyDoneTasks(Long userId, int page, int size) {
        long count = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished()
                .count();

        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .listPage((page - 1) * size, size);

        List<Map<String, Object>> records = tasks.stream()
                .map(this::historicTaskToMap)
                .collect(Collectors.toList());

        long pages = (count + size - 1) / size;
        return new PageResult<>(records, count, page, size, pages);
    }

    /**
     * 我发起的流程（分页）
     * <p>
     * 基于 HistoricProcessInstance.startedBy 查询当前用户发起的全部流程实例，
     * 包含进行中（RUNNING）和已结束（COMPLETED）两种状态。
     * </p>
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> getMyInitiatedProcesses(Long userId, int page, int size) {
        long count = historyService.createHistoricProcessInstanceQuery()
                .startedBy(String.valueOf(userId))
                .count();

        List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                .startedBy(String.valueOf(userId))
                .orderByProcessInstanceStartTime()
                .desc()
                .listPage((page - 1) * size, size);

        List<Map<String, Object>> records = instances.stream()
                .map(this::historicProcessToMap)
                .collect(Collectors.toList());

        long pages = (count + size - 1) / size;
        return new PageResult<>(records, count, page, size, pages);
    }

    /**
     * 批量通过
     *
     * @param taskIds 任务ID列表
     * @param comment 审批意见
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchApprove(List<String> taskIds, String comment) {
        for (String taskId : taskIds) {
            complete(taskId, comment, null);
        }
        log.info("批量通过完成, 数量={}", taskIds.size());
    }

    // ===== 私有方法 =====

    private Task getTaskById(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException("任务不存在或已被处理: " + taskId);
        }
        return task;
    }

    /**
     * 校验当前用户是否为该任务的处理人（assignee）。
     * <p>
     * 仅任务当前处理人可执行办理/退回/终止/转办/委托等操作，防止越权处理他人审批任务。
     * 超级管理员（SUPER_ADMIN）拥有全部审批操作权限，跳过校验。
     * </p>
     *
     * @param task   目标任务
     * @param userId 当前操作用户ID
     */
    private void assertTaskAssignee(Task task, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录，无法执行审批操作");
        }
        // 超级管理员放行
        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(userId);
        if (roleCodes != null && roleCodes.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        String assignee = task.getAssignee();
        if (assignee == null || assignee.isBlank()) {
            // 未签收的候选任务，需先签收后再操作，避免越权处理
            throw new BusinessException(403, "该任务尚未签收，无法直接操作，请先签收任务");
        }
        if (!assignee.equals(String.valueOf(userId))) {
            throw new BusinessException(403, "无权操作他人审批任务");
        }
    }

    private void saveApprovalRecord(Task task, String assignee, String operationType, String comment) {
        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(task.getId());
        record.setTaskName(task.getName());
        record.setAssignee(assignee);
        record.setOperationType(operationType);
        record.setComment(comment);
        record.setOperTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);
    }

    /** 从任务流程变量提取发起人 userId 字符串（可能为 null） */
    private String extractInitiator(Task task) {
        Map<String, Object> vars = task.getProcessVariables();
        Object initiator = vars == null ? null : vars.get("initiator");
        return initiator == null ? null : String.valueOf(initiator);
    }

    /**
     * 批量查询用户真实姓名（userId 字符串 → realName），容忍非法 ID（以原值兜底）。
     */
    private Map<String, String> lookupRealNames(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = userIds.stream().map(id -> {
            try {
                return Long.valueOf(id);
            } catch (NumberFormatException e) {
                return null;
            }
        }).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, String> result = new HashMap<>();
        if (!ids.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(ids);
            for (SysUser u : users) {
                result.put(String.valueOf(u.getId()),
                        u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
        }
        // 非法/未查到的 ID 以原值兜底，不丢失展示
        for (String id : userIds) {
            result.putIfAbsent(id, id);
        }
        return result;
    }

    /** 审批操作类型 → [前端样式类, 展示文案] */
    private String[] mapOperationType(String operationType) {
        if (operationType == null) {
            return new String[]{"info", "已处理"};
        }
        switch (operationType) {
            case "APPROVE": return new String[]{"approved", "已通过"};
            case "REJECT": return new String[]{"rejected", "已退回"};
            case "REJECT_TO_START": return new String[]{"rejected", "退回发起人"};
            case "TERMINATE": return new String[]{"rejected", "已终止"};
            case "TRANSFER": return new String[]{"info", "已转办"};
            case "DELEGATE": return new String[]{"info", "已委托"};
            default: return new String[]{"info", operationType};
        }
    }

    private Map<String, Object> taskToMap(Task task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", task.getId());
        map.put("taskName", task.getName());
        map.put("taskDefinitionKey", task.getTaskDefinitionKey());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("assignee", task.getAssignee());
        map.put("createTime", task.getCreateTime());
        map.put("processDefinitionId", task.getProcessDefinitionId());

        // 流程变量随查询批量携带（includeProcessVariables），不再逐任务查库
        Map<String, Object> processVariables = task.getProcessVariables();
        if (processVariables == null) {
            processVariables = java.util.Collections.emptyMap();
        }
        map.put("businessType", processVariables.get("businessType"));
        map.put("businessId", processVariables.get("businessId"));
        return map;
    }

    private Map<String, Object> historicTaskToMap(HistoricTaskInstance task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", task.getId());
        map.put("taskName", task.getName());
        map.put("taskDefinitionKey", task.getTaskDefinitionKey());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("assignee", task.getAssignee());
        map.put("createTime", task.getCreateTime());
        map.put("endTime", task.getEndTime());
        map.put("processDefinitionId", task.getProcessDefinitionId());
        return map;
    }

    private Map<String, Object> historicProcessToMap(HistoricProcessInstance instance) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("processInstanceId", instance.getId());
        map.put("processDefinitionId", instance.getProcessDefinitionId());
        map.put("processDefinitionKey", instance.getProcessDefinitionKey());
        map.put("processName", instance.getProcessDefinitionName());
        map.put("startTime", instance.getStartTime());
        map.put("endTime", instance.getEndTime());
        map.put("initiator", instance.getStartUserId());
        // 流程状态：结束时间为空表示进行中，否则已完成
        map.put("status", instance.getEndTime() == null ? "RUNNING" : "COMPLETED");

        // 从 businessKey（格式 businessType:businessId）解析业务信息
        String businessKey = instance.getBusinessKey();
        if (businessKey != null && businessKey.contains(":")) {
            String[] parts = businessKey.split(":", 2);
            map.put("businessType", parts[0]);
            map.put("businessId", parts[1]);
        } else {
            map.put("businessType", null);
            map.put("businessId", null);
        }
        return map;
    }
}
