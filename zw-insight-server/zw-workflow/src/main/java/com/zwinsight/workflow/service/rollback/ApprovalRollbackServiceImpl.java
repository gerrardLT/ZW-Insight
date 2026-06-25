package com.zwinsight.workflow.service.rollback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.workflow.domain.BizApprovalRollbackLog;
import com.zwinsight.workflow.domain.BizApprovalSnapshot;
import com.zwinsight.workflow.dto.RollbackLogQuery;
import com.zwinsight.workflow.dto.RollbackLogVO;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.mapper.BizApprovalRollbackLogMapper;
import com.zwinsight.workflow.mapper.BizApprovalSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 审批数据回滚服务实现
 * <p>
 * 核心流程：
 * 1. 审批提交时 saveSnapshot() 记录原始数据
 * 2. 驳回/撤回时 Flowable 监听器发布 ApprovalRejectEvent
 * 3. 本服务监听事件 → executeRollback()
 * 4. 冲突检测 → 策略执行 → 乐观锁重试 → 记录日志
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRollbackServiceImpl implements ApprovalRollbackService {

    private final BizApprovalSnapshotMapper snapshotMapper;
    private final BizApprovalRollbackLogMapper rollbackLogMapper;
    private final RollbackStrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 乐观锁最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 回滚超时时间（秒）
     */
    private static final int ROLLBACK_TIMEOUT_SECONDS = 5;

    // ===== 子任务5: saveSnapshot =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSnapshot(String workflowInstanceId, String bizType, Long bizId, Map<String, Object> snapshotData) {
        if (snapshotData == null || snapshotData.isEmpty()) {
            log.warn("快照数据为空，跳过保存。workflowInstanceId={}, bizType={}, bizId={}",
                    workflowInstanceId, bizType, bizId);
            return;
        }

        // 删除同一流程实例的旧快照（防止重复提交）
        LambdaQueryWrapper<BizApprovalSnapshot> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(BizApprovalSnapshot::getWorkflowInstanceId, workflowInstanceId);
        snapshotMapper.delete(deleteWrapper);

        // 逐字段保存快照
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> entry : snapshotData.entrySet()) {
            BizApprovalSnapshot snapshot = new BizApprovalSnapshot();
            snapshot.setWorkflowInstanceId(workflowInstanceId);
            snapshot.setBizType(bizType);
            snapshot.setBizId(bizId);
            snapshot.setFieldName(entry.getKey());
            snapshot.setOriginalValue(convertToString(entry.getValue()));
            snapshot.setCreatedAt(now);
            snapshotMapper.insert(snapshot);
        }

        log.info("保存审批快照, workflowInstanceId={}, bizType={}, bizId={}, fields={}",
                workflowInstanceId, bizType, bizId, snapshotData.keySet());
    }

    // ===== 子任务7: executeRollback =====

    @Override
    public RollbackResult executeRollback(String workflowInstanceId) {
        log.info("开始执行回滚, workflowInstanceId={}", workflowInstanceId);

        // 查询快照
        LambdaQueryWrapper<BizApprovalSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizApprovalSnapshot::getWorkflowInstanceId, workflowInstanceId);
        List<BizApprovalSnapshot> snapshots = snapshotMapper.selectList(wrapper);

        if (snapshots.isEmpty()) {
            log.warn("未找到快照数据，无法回滚。workflowInstanceId={}", workflowInstanceId);
            return RollbackResult.failed("未找到快照数据", 0);
        }

        // 获取业务信息
        String bizType = snapshots.get(0).getBizType();
        Long bizId = snapshots.get(0).getBizId();

        // 查找策略
        RollbackStrategy strategy = strategyRegistry.getStrategy(bizType);
        if (strategy == null) {
            String msg = "未找到业务类型 [" + bizType + "] 的回滚策略";
            log.error(msg);
            saveRollbackLog(workflowInstanceId, bizType, bizId, null,
                    BizApprovalRollbackLog.STATUS_FAILED, 0, msg);
            return RollbackResult.failed(msg, 0);
        }

        // 组装快照数据
        Map<String, Object> snapshotData = snapshots.stream()
                .collect(Collectors.toMap(
                        BizApprovalSnapshot::getFieldName,
                        s -> parseValue(s.getOriginalValue()),
                        (v1, v2) -> v2
                ));

        // 子任务8: 冲突检测
        List<String> conflictFields = detectConflicts(strategy, bizId, snapshotData);
        if (!conflictFields.isEmpty()) {
            log.warn("检测到数据冲突, workflowInstanceId={}, conflictFields={}", workflowInstanceId, conflictFields);
            saveRollbackLog(workflowInstanceId, bizType, bizId, toJson(snapshotData),
                    BizApprovalRollbackLog.STATUS_CONFLICT, 0, "冲突字段: " + conflictFields);
            return RollbackResult.conflict(conflictFields);
        }

        // 子任务9 + 12: 带超时和乐观锁重试的执行
        return executeWithTimeoutAndRetry(workflowInstanceId, bizType, bizId, strategy, snapshotData);
    }

    // ===== 子任务8: 冲突检测 =====

    /**
     * 检测冲突：快照原始值与当前DB值不一致时标记冲突
     * <p>
     * 默认返回空列表（无冲突），由具体策略实现可选覆盖 ConflictAware 接口。
     * </p>
     */
    private List<String> detectConflicts(RollbackStrategy strategy, Long bizId, Map<String, Object> snapshotData) {
        if (strategy instanceof ConflictAwareRollbackStrategy conflictAware) {
            return conflictAware.detectConflicts(bizId, snapshotData);
        }
        // 默认不做冲突检测，直接回滚
        return Collections.emptyList();
    }

    // ===== 子任务9 + 12: 乐观锁重试 + 超时检测 =====

    private RollbackResult executeWithTimeoutAndRetry(String workflowInstanceId, String bizType,
                                                      Long bizId, RollbackStrategy strategy,
                                                      Map<String, Object> snapshotData) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        int retryCount = 0;

        try {
            Future<RollbackResult> future = executor.submit(() ->
                    executeWithRetry(workflowInstanceId, bizType, bizId, strategy, snapshotData));

            // 超时控制：5秒
            RollbackResult result = future.get(ROLLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            retryCount = result.getRetryCount();

            // 保存成功日志
            saveRollbackLog(workflowInstanceId, bizType, bizId, toJson(snapshotData),
                    result.getStatus(), retryCount, result.getMessage());

            return result;

        } catch (TimeoutException e) {
            log.error("回滚超时(>{}s), workflowInstanceId={}", ROLLBACK_TIMEOUT_SECONDS, workflowInstanceId);
            String errorMsg = "回滚执行超时，已超过" + ROLLBACK_TIMEOUT_SECONDS + "秒";
            saveRollbackLog(workflowInstanceId, bizType, bizId, toJson(snapshotData),
                    BizApprovalRollbackLog.STATUS_FAILED, retryCount, errorMsg);
            // 通知管理员
            notifyAdmin(workflowInstanceId, bizType, bizId, errorMsg);
            return RollbackResult.failed(errorMsg, retryCount);

        } catch (ExecutionException e) {
            String errorMsg = "回滚执行异常: " + e.getCause().getMessage();
            log.error("回滚执行异常, workflowInstanceId={}", workflowInstanceId, e.getCause());
            saveRollbackLog(workflowInstanceId, bizType, bizId, toJson(snapshotData),
                    BizApprovalRollbackLog.STATUS_FAILED, retryCount, errorMsg);
            return RollbackResult.failed(errorMsg, retryCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "回滚被中断";
            saveRollbackLog(workflowInstanceId, bizType, bizId, toJson(snapshotData),
                    BizApprovalRollbackLog.STATUS_FAILED, retryCount, errorMsg);
            return RollbackResult.failed(errorMsg, retryCount);

        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 乐观锁重试执行回滚
     */
    private RollbackResult executeWithRetry(String workflowInstanceId, String bizType,
                                             Long bizId, RollbackStrategy strategy,
                                             Map<String, Object> snapshotData) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                strategy.rollback(bizId, snapshotData);
                log.info("回滚成功, workflowInstanceId={}, retryCount={}", workflowInstanceId, retryCount);
                RollbackResult result = RollbackResult.success();
                result.setRetryCount(retryCount);
                return result;
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                retryCount++;
                lastException = e;
                log.warn("乐观锁冲突，第{}次重试, workflowInstanceId={}", retryCount, workflowInstanceId);
                if (retryCount < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(100L * retryCount); // 递增退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                break;
            }
        }

        String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
        return RollbackResult.failed("回滚失败(重试" + retryCount + "次): " + errorMsg, retryCount);
    }

    // ===== 子任务11: 监听 ApprovalRejectEvent =====

    @Async
    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        log.info("收到审批驳回/撤回事件, workflowInstanceId={}, bizType={}, rejectType={}",
                event.getWorkflowInstanceId(), event.getBizType(), event.getRejectType());
        try {
            executeRollback(event.getWorkflowInstanceId());
        } catch (Exception e) {
            log.error("事件驱动回滚失败, workflowInstanceId={}", event.getWorkflowInstanceId(), e);
        }
    }

    // ===== 子任务12: 超时通知管理员 =====

    private void notifyAdmin(String workflowInstanceId, String bizType, Long bizId, String errorMsg) {
        log.error("[管理员通知] 回滚超时告警: workflowInstanceId={}, bizType={}, bizId={}, error={}",
                workflowInstanceId, bizType, bizId, errorMsg);
        // 通过站内消息通知系统管理员（userId=1 为默认超级管理员）
        String title = "【回滚超时告警】" + bizType + " 数据回滚失败";
        String content = String.format("业务类型: %s\n业务ID: %d\n流程实例: %s\n错误原因: %s\n请及时处理！",
                bizType, bizId, workflowInstanceId, errorMsg);
        try {
            com.zwinsight.common.event.UrgeNotifyEvent notifyEvent = new com.zwinsight.common.event.UrgeNotifyEvent(
                    this, 1L, title, content, workflowInstanceId, null);
            eventPublisher.publishEvent(notifyEvent);
            log.info("回滚超时告警已发送站内通知: workflowInstanceId={}", workflowInstanceId);
        } catch (Exception e) {
            log.error("发送回滚超时告警通知失败", e);
        }
    }

    // ===== 子任务13: 回滚记录查询 =====

    @Override
    public PageResult<RollbackLogVO> queryRollbackLogs(RollbackLogQuery query) {
        LambdaQueryWrapper<BizApprovalRollbackLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getWorkflowInstanceId() != null && !query.getWorkflowInstanceId().isEmpty()) {
            wrapper.eq(BizApprovalRollbackLog::getWorkflowInstanceId, query.getWorkflowInstanceId());
        }
        if (query.getBizType() != null && !query.getBizType().isEmpty()) {
            wrapper.eq(BizApprovalRollbackLog::getBizType, query.getBizType());
        }
        if (query.getRollbackStatus() != null) {
            wrapper.eq(BizApprovalRollbackLog::getRollbackStatus, query.getRollbackStatus());
        }
        wrapper.orderByDesc(BizApprovalRollbackLog::getCreatedAt);

        Page<BizApprovalRollbackLog> page = new Page<>(query.getPage(), query.getSize());
        IPage<BizApprovalRollbackLog> resultPage = rollbackLogMapper.selectPage(page, wrapper);

        List<RollbackLogVO> voList = resultPage.getRecords().stream()
                .map(this::toRollbackLogVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, resultPage.getTotal(),
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getPages());
    }

    // ===== 子任务14: 冲突确认 =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmConflict(Long rollbackLogId, String resolution) {
        BizApprovalRollbackLog rollbackLog = rollbackLogMapper.selectById(rollbackLogId);
        if (rollbackLog == null) {
            throw new BusinessException("回滚日志不存在: " + rollbackLogId);
        }
        if (!Objects.equals(rollbackLog.getRollbackStatus(), BizApprovalRollbackLog.STATUS_CONFLICT)) {
            throw new BusinessException("当前状态不允许确认冲突，当前状态=" + rollbackLog.getRollbackStatus());
        }

        rollbackLog.setRollbackStatus(BizApprovalRollbackLog.STATUS_SUCCESS);
        rollbackLog.setErrorMsg("冲突已确认: " + resolution);
        rollbackLogMapper.updateById(rollbackLog);

        log.info("冲突确认完成, rollbackLogId={}, resolution={}", rollbackLogId, resolution);
    }

    // ===== 私有辅助方法 =====

    private void saveRollbackLog(String workflowInstanceId, String bizType, Long bizId,
                                  String rollbackFields, int status, int retryCount, String errorMsg) {
        BizApprovalRollbackLog logEntry = new BizApprovalRollbackLog();
        logEntry.setWorkflowInstanceId(workflowInstanceId);
        logEntry.setBizType(bizType);
        logEntry.setBizId(bizId);
        logEntry.setRollbackFields(rollbackFields);
        logEntry.setRollbackStatus(status);
        logEntry.setRetryCount(retryCount);
        logEntry.setErrorMsg(errorMsg);
        logEntry.setCreatedAt(LocalDateTime.now());
        rollbackLogMapper.insert(logEntry);
    }

    private RollbackLogVO toRollbackLogVO(BizApprovalRollbackLog log) {
        RollbackLogVO vo = new RollbackLogVO();
        vo.setId(log.getId());
        vo.setWorkflowInstanceId(log.getWorkflowInstanceId());
        vo.setBizType(log.getBizType());
        vo.setBizId(log.getBizId());
        vo.setRollbackFields(log.getRollbackFields());
        vo.setRollbackStatus(log.getRollbackStatus());
        vo.setStatusDesc(getStatusDesc(log.getRollbackStatus()));
        vo.setRetryCount(log.getRetryCount());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setOperatorId(log.getOperatorId());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 1 -> "成功";
            case 2 -> "失败";
            case 3 -> "冲突待确认";
            default -> "未知";
        };
    }

    private String convertToString(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        return toJson(value);
    }

    private Object parseValue(String value) {
        if (value == null) return null;
        // 尝试解析为 JSON 对象/数组
        try {
            if (value.startsWith("{") || value.startsWith("[")) {
                return objectMapper.readValue(value, new TypeReference<Object>() {});
            }
        } catch (JsonProcessingException ignored) {
            // 非 JSON 格式，返回原始字符串
        }
        return value;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON序列化失败", e);
            return obj.toString();
        }
    }
}
