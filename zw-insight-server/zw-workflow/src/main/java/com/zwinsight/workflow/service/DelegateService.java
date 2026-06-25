package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfDelegateConfig;
import com.zwinsight.workflow.mapper.WfDelegateConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批委托/代理服务
 * <p>
 * 支持设置委托期间和代理人，期间内新审批自动转给代理人，委托结束自动恢复。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelegateService {

    private final WfDelegateConfigMapper delegateConfigMapper;

    /**
     * 创建委托配置
     *
     * @param delegateId 代理人用户ID
     * @param startTime  委托开始时间
     * @param endTime    委托结束时间
     * @param reason     委托原因
     * @return 委托配置ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createDelegation(Long delegateId, LocalDateTime startTime, LocalDateTime endTime, String reason) {
        Long delegatorId = SecurityContextHolder.getUserId();

        // 参数校验
        if (delegateId == null) {
            throw new BusinessException("代理人不能为空");
        }
        if (delegatorId.equals(delegateId)) {
            throw new BusinessException("不能委托给自己");
        }
        if (startTime == null || endTime == null) {
            throw new BusinessException("委托开始时间和结束时间不能为空");
        }
        if (endTime.isBefore(startTime)) {
            throw new BusinessException("结束时间不能早于开始时间");
        }
        if (endTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException("结束时间不能早于当前时间");
        }

        // 检查是否已有生效中的委托配置
        WfDelegateConfig existing = getActiveDelegation(delegatorId);
        if (existing != null) {
            throw new BusinessException("已有生效中的委托配置，请先取消后再创建新的委托");
        }

        // 检查代理人是否也设置了委托（防止循环委托）
        WfDelegateConfig delegateExisting = getActiveDelegation(delegateId);
        if (delegateExisting != null && delegateExisting.getDelegateId().equals(delegatorId)) {
            throw new BusinessException("检测到循环委托：代理人已将任务委托给您");
        }

        WfDelegateConfig config = new WfDelegateConfig();
        config.setDelegatorId(delegatorId);
        config.setDelegateId(delegateId);
        config.setStartTime(startTime);
        config.setEndTime(endTime);
        config.setStatus("ACTIVE");
        config.setReason(reason);

        delegateConfigMapper.insert(config);
        log.info("创建委托配置成功, delegatorId={}, delegateId={}, startTime={}, endTime={}",
                delegatorId, delegateId, startTime, endTime);

        return config.getId();
    }

    /**
     * 取消委托
     *
     * @param id 委托配置ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelDelegation(Long id) {
        Long userId = SecurityContextHolder.getUserId();

        WfDelegateConfig config = delegateConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("委托配置不存在");
        }
        if (!config.getDelegatorId().equals(userId)) {
            throw new BusinessException("只能取消自己的委托配置");
        }
        if (!"ACTIVE".equals(config.getStatus())) {
            throw new BusinessException("该委托配置已不是生效状态");
        }

        config.setStatus("CANCELLED");
        delegateConfigMapper.updateById(config);
        log.info("委托配置已取消, id={}, delegatorId={}", id, userId);
    }

    /**
     * 查询用户当前生效的委托配置
     *
     * @param delegatorId 委托人用户ID
     * @return 生效中的委托配置，没有则返回null
     */
    public WfDelegateConfig getActiveDelegation(Long delegatorId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WfDelegateConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfDelegateConfig::getDelegatorId, delegatorId)
                .eq(WfDelegateConfig::getStatus, "ACTIVE")
                .le(WfDelegateConfig::getStartTime, now)
                .ge(WfDelegateConfig::getEndTime, now);
        return delegateConfigMapper.selectOne(wrapper);
    }

    /**
     * 查询某用户当前是否有生效的委托配置，如果有则返回代理人ID
     * <p>
     * 该方法供任务分配时调用：当新任务分配给某用户时，如果该用户有生效的委托配置，
     * 则自动将任务转给代理人。
     * </p>
     *
     * @param userId 被分配任务的用户ID
     * @return 代理人ID，如果没有生效的委托则返回null
     */
    public Long findDelegateUser(Long userId) {
        WfDelegateConfig config = getActiveDelegation(userId);
        if (config != null) {
            return config.getDelegateId();
        }
        return null;
    }

    /**
     * 查询我的委托配置列表
     *
     * @param userId 用户ID
     * @return 委托配置列表
     */
    public List<WfDelegateConfig> getMyDelegations(Long userId) {
        LambdaQueryWrapper<WfDelegateConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfDelegateConfig::getDelegatorId, userId)
                .orderByDesc(WfDelegateConfig::getCreatedAt);
        return delegateConfigMapper.selectList(wrapper);
    }

    /**
     * 查询我作为代理人的委托列表
     *
     * @param userId 用户ID（代理人）
     * @return 委托配置列表
     */
    public List<WfDelegateConfig> getDelegationsToMe(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WfDelegateConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfDelegateConfig::getDelegateId, userId)
                .eq(WfDelegateConfig::getStatus, "ACTIVE")
                .le(WfDelegateConfig::getStartTime, now)
                .ge(WfDelegateConfig::getEndTime, now)
                .orderByDesc(WfDelegateConfig::getCreatedAt);
        return delegateConfigMapper.selectList(wrapper);
    }

    /**
     * 定时清理过期的委托配置
     * <p>
     * 将已过期但状态仍为ACTIVE的委托配置标记为EXPIRED。
     * 由定时任务调用。
     * </p>
     *
     * @return 清理的数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireOverdueDelegations() {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<WfDelegateConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WfDelegateConfig::getStatus, "ACTIVE")
                .lt(WfDelegateConfig::getEndTime, now)
                .set(WfDelegateConfig::getStatus, "EXPIRED");

        int count = delegateConfigMapper.update(null, wrapper);
        if (count > 0) {
            log.info("已清理过期委托配置 {} 条", count);
        }
        return count;
    }
}
