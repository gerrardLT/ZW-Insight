package com.zwinsight.site.service;

import com.zwinsight.site.domain.BizReminderConfig;
import com.zwinsight.site.dto.ReminderConfigUpdateRequest;

/**
 * 整改催办配置服务接口
 */
public interface ReminderConfigService {

    /**
     * 获取租户的催办配置
     * <p>如果配置不存在，返回默认值（intervalDays=3, escalationDays=7, longOverdueDays=30, enabled=true）</p>
     *
     * @param tenantId 租户ID
     * @return 催办配置
     */
    BizReminderConfig getConfig(Long tenantId);

    /**
     * 更新租户的催办配置
     * <p>如果配置不存在则新建，存在则更新</p>
     *
     * @param tenantId 租户ID
     * @param request  更新请求
     */
    void updateConfig(Long tenantId, ReminderConfigUpdateRequest request);
}
