package com.zwinsight.site.service;

import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.dto.ReminderStatsVO;

import java.util.List;

/**
 * 整改催办日志服务接口
 */
public interface ReminderLogService {

    /**
     * 保存催办日志
     *
     * @param reminderLog 催办日志实体
     */
    void saveLog(BizReminderLog reminderLog);

    /**
     * 按 sentAt 降序查询某检查记录的催办历史
     *
     * @param inspectionId 检查记录ID
     * @return 催办日志列表（按发送时间降序）
     */
    List<BizReminderLog> getLogsByInspectionId(Long inspectionId);

    /**
     * 统计项目下催办相关数据
     * <p>包含：超期整改总数、已催办次数、已升级通知次数</p>
     *
     * @param projectId 项目ID
     * @return 催办统计数据
     */
    ReminderStatsVO getStatsByProjectId(Long projectId);
}
