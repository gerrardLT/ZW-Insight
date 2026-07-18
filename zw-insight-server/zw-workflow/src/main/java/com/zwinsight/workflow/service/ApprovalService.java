package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
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

        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(userId))
                .orderByTaskCreateTime()
                .desc()
                .listPage((page - 1) * size, size);

        List<Map<String, Object>> records = tasks.stream()
                .map(this::taskToMap)
                .collect(Collectors.toList());

        long pages = (count + size - 1) / size;
        return new PageResult<>(records, count, page, size, pages);
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

    private Map<String, Object> taskToMap(Task task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", task.getId());
        map.put("taskName", task.getName());
        map.put("taskDefinitionKey", task.getTaskDefinitionKey());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("assignee", task.getAssignee());
        map.put("createTime", task.getCreateTime());
        map.put("processDefinitionId", task.getProcessDefinitionId());

        // 获取流程变量中的业务信息
        Map<String, Object> processVariables = taskService.getVariables(task.getId());
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
