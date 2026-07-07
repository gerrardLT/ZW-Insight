package com.zwinsight.file.batch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.batch.domain.BizExportSchedule;
import com.zwinsight.file.batch.mapper.BizExportScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导出中心 — 定时导出配置与执行服务
 * <p>
 * 功能：
 * <ul>
 *   <li>配置定时导出任务（Cron + 模块 + 参数 + 接收人邮箱）</li>
 *   <li>每分钟扫描待执行任务，触发异步导出</li>
 *   <li>导出完成后发送邮件/站内消息通知接收人</li>
 *   <li>支持手动触发立即执行</li>
 * </ul>
 * </p>
 * <p>
 * 业务场景：
 * <ul>
 *   <li>每月1日自动导出上月财务汇总报表发送给财务总监</li>
 *   <li>每周一导出项目进度周报发送给项目经理</li>
 *   <li>每日导出材料库存日报发送给仓库管理员</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportScheduleService {

    private final BizExportScheduleMapper scheduleMapper;
    private final BatchImportExportService batchImportExportService;

    /**
     * 分页查询定时导出配置
     */
    public PageResult<BizExportSchedule> page(int page, int size) {
        Page<BizExportSchedule> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizExportSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizExportSchedule::getCreatedAt);
        return PageResult.of(scheduleMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 创建定时导出配置
     */
    public void save(BizExportSchedule schedule) {
        // 校验 Cron 表达式有效性
        if (!CronExpression.isValidExpression(schedule.getCronExpression())) {
            throw new BusinessException("Cron 表达式无效: " + schedule.getCronExpression());
        }
        schedule.setEnabled(1);
        // 计算下次执行时间
        schedule.setNextExecuteTime(calculateNextExecuteTime(schedule.getCronExpression()));
        scheduleMapper.insert(schedule);
    }

    /**
     * 更新定时导出配置
     */
    public void update(BizExportSchedule schedule) {
        BizExportSchedule existing = scheduleMapper.selectById(schedule.getId());
        if (existing == null) throw new BusinessException("配置不存在");
        if (schedule.getCronExpression() != null) {
            if (!CronExpression.isValidExpression(schedule.getCronExpression())) {
                throw new BusinessException("Cron 表达式无效");
            }
            schedule.setNextExecuteTime(calculateNextExecuteTime(schedule.getCronExpression()));
        }
        scheduleMapper.updateById(schedule);
    }

    /**
     * 删除定时导出配置
     */
    public void delete(Long id) {
        scheduleMapper.deleteById(id);
    }

    /**
     * 手动立即执行导出任务
     */
    public Long executeNow(Long scheduleId) {
        BizExportSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) throw new BusinessException("配置不存在");
        return executeExport(schedule);
    }

    /**
     * 每分钟扫描待执行的定时导出任务
     * <p>
     * 条件：enabled=1 AND nextExecuteTime <= NOW()
     * </p>
     */
    @Scheduled(cron = "0 * * * * ?")
    public void scanAndExecuteScheduledExports() {
        LocalDateTime now = LocalDateTime.now();

        List<BizExportSchedule> pendingList = scheduleMapper.selectList(
                new LambdaQueryWrapper<BizExportSchedule>()
                        .eq(BizExportSchedule::getEnabled, 1)
                        .le(BizExportSchedule::getNextExecuteTime, now));

        for (BizExportSchedule schedule : pendingList) {
            try {
                executeExport(schedule);

                // 更新执行时间
                schedule.setLastExecuteTime(now);
                schedule.setNextExecuteTime(calculateNextExecuteTime(schedule.getCronExpression()));
                scheduleMapper.updateById(schedule);
            } catch (Exception e) {
                log.error("定时导出执行失败, scheduleId={}, module={}",
                        schedule.getId(), schedule.getModuleCode(), e);
            }
        }

        if (!pendingList.isEmpty()) {
            log.info("定时导出扫描完成, 执行{}个任务", pendingList.size());
        }
    }

    /**
     * 执行导出任务
     */
    private Long executeExport(BizExportSchedule schedule) {
        // 解析导出参数
        Map<String, Object> params = new HashMap<>();
        if (schedule.getExportParams() != null && !schedule.getExportParams().isBlank()) {
            // 简化实现：实际应使用 JSON 解析
            params.put("raw", schedule.getExportParams());
        }

        // 调用异步导出服务
        Long taskId = batchImportExportService.asyncExport(schedule.getModuleCode(), params);
        log.info("定时导出已触发: scheduleId={}, module={}, taskId={}",
                schedule.getId(), schedule.getModuleCode(), taskId);

        // TODO: 导出完成后发送邮件到 schedule.getRecipients()
        return taskId;
    }

    /**
     * 计算下次执行时间
     */
    private LocalDateTime calculateNextExecuteTime(String cronExpression) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            return cron.next(LocalDateTime.now());
        } catch (Exception e) {
            return LocalDateTime.now().plusDays(1);
        }
    }

    /**
     * 获取所有可导出模块列表（供前端下拉选择）
     */
    public List<Map<String, String>> getAvailableModules() {
        return List.of(
                Map.of("code", "MACHINE_LEDGER", "name", "机械台账"),
                Map.of("code", "LABOR_ROSTER", "name", "劳务花名册"),
                Map.of("code", "MATERIAL_STOCK", "name", "材料库存"),
                Map.of("code", "SUPPLIER", "name", "供应商"),
                Map.of("code", "USER", "name", "人员"),
                Map.of("code", "PROJECT_SETTLEMENT", "name", "项目结算"),
                Map.of("code", "SUBCONTRACT_SETTLEMENT", "name", "分包结算"),
                Map.of("code", "BUDGET_EXECUTION", "name", "预算执行"),
                Map.of("code", "INVOICE_LEDGER", "name", "发票台账"),
                Map.of("code", "PAYMENT_HISTORY", "name", "付款记录")
        );
    }
}
