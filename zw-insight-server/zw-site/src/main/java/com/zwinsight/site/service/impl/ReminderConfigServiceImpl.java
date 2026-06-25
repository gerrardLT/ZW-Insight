package com.zwinsight.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizReminderConfig;
import com.zwinsight.site.dto.ReminderConfigUpdateRequest;
import com.zwinsight.site.mapper.BizReminderConfigMapper;
import com.zwinsight.site.service.ReminderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 整改催办配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderConfigServiceImpl implements ReminderConfigService {

    private static final int DEFAULT_INTERVAL_DAYS = 3;
    private static final int DEFAULT_ESCALATION_DAYS = 7;
    private static final int DEFAULT_LONG_OVERDUE_DAYS = 30;
    private static final boolean DEFAULT_ENABLED = true;

    private final BizReminderConfigMapper reminderConfigMapper;

    @Override
    public BizReminderConfig getConfig(Long tenantId) {
        BizReminderConfig config = reminderConfigMapper.selectOne(
                new LambdaQueryWrapper<BizReminderConfig>()
                        .eq(BizReminderConfig::getTenantId, tenantId));

        if (config == null) {
            log.debug("租户[{}]催办配置不存在，返回默认值", tenantId);
            return buildDefaultConfig(tenantId);
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(Long tenantId, ReminderConfigUpdateRequest request) {
        // 业务校验：escalationDays 应小于 longOverdueDays
        if (request.getEscalationDays() >= request.getLongOverdueDays()) {
            throw new BusinessException("升级通知阈值天数必须小于长期超期停止催办天数");
        }

        BizReminderConfig config = reminderConfigMapper.selectOne(
                new LambdaQueryWrapper<BizReminderConfig>()
                        .eq(BizReminderConfig::getTenantId, tenantId));

        if (config == null) {
            // 配置不存在，新建
            config = new BizReminderConfig();
            config.setTenantId(tenantId);
            config.setIntervalDays(request.getIntervalDays());
            config.setEscalationDays(request.getEscalationDays());
            config.setLongOverdueDays(request.getLongOverdueDays());
            config.setEnabled(request.getEnabled());
            reminderConfigMapper.insert(config);
            log.info("租户[{}]催办配置创建成功", tenantId);
        } else {
            // 配置存在，更新
            config.setIntervalDays(request.getIntervalDays());
            config.setEscalationDays(request.getEscalationDays());
            config.setLongOverdueDays(request.getLongOverdueDays());
            config.setEnabled(request.getEnabled());
            reminderConfigMapper.updateById(config);
            log.info("租户[{}]催办配置更新成功", tenantId);
        }
    }

    /**
     * 构建默认催办配置（不持久化）
     */
    private BizReminderConfig buildDefaultConfig(Long tenantId) {
        BizReminderConfig defaultConfig = new BizReminderConfig();
        defaultConfig.setTenantId(tenantId);
        defaultConfig.setIntervalDays(DEFAULT_INTERVAL_DAYS);
        defaultConfig.setEscalationDays(DEFAULT_ESCALATION_DAYS);
        defaultConfig.setLongOverdueDays(DEFAULT_LONG_OVERDUE_DAYS);
        defaultConfig.setEnabled(DEFAULT_ENABLED);
        return defaultConfig;
    }
}
