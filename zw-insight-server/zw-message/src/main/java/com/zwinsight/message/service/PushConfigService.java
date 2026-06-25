package com.zwinsight.message.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgPushConfig;
import com.zwinsight.message.mapper.MsgPushConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 推送渠道配置服务
 */
@Service
@RequiredArgsConstructor
public class PushConfigService {

    private final MsgPushConfigMapper pushConfigMapper;

    /**
     * 分页查询推送渠道配置
     */
    public PageResult<MsgPushConfig> page(int page, int size, String businessType) {
        Page<MsgPushConfig> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<MsgPushConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(businessType), MsgPushConfig::getBusinessType, businessType)
                .orderByDesc(MsgPushConfig::getCreatedAt);
        Page<MsgPushConfig> result = pushConfigMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public MsgPushConfig getById(Long id) {
        MsgPushConfig config = pushConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("推送渠道配置不存在");
        }
        return config;
    }

    /**
     * 根据业务类型查询配置
     */
    public MsgPushConfig getByBusinessType(String businessType) {
        LambdaQueryWrapper<MsgPushConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgPushConfig::getBusinessType, businessType)
                .last("LIMIT 1");
        MsgPushConfig config = pushConfigMapper.selectOne(wrapper);
        if (config == null) {
            throw new BusinessException("未找到业务类型[" + businessType + "]的推送渠道配置");
        }
        return config;
    }

    /**
     * 新增推送渠道配置
     */
    public void save(MsgPushConfig config) {
        // 校验业务类型唯一性
        LambdaQueryWrapper<MsgPushConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgPushConfig::getBusinessType, config.getBusinessType());
        Long count = pushConfigMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("业务类型[" + config.getBusinessType() + "]的推送配置已存在");
        }
        pushConfigMapper.insert(config);
    }

    /**
     * 更新推送渠道配置
     */
    public void update(MsgPushConfig config) {
        MsgPushConfig existing = pushConfigMapper.selectById(config.getId());
        if (existing == null) {
            throw new BusinessException("推送渠道配置不存在");
        }
        // 如果修改了业务类型，校验唯一性
        if (!existing.getBusinessType().equals(config.getBusinessType())) {
            LambdaQueryWrapper<MsgPushConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MsgPushConfig::getBusinessType, config.getBusinessType());
            Long count = pushConfigMapper.selectCount(wrapper);
            if (count > 0) {
                throw new BusinessException("业务类型[" + config.getBusinessType() + "]的推送配置已存在");
            }
        }
        pushConfigMapper.updateById(config);
    }

    /**
     * 删除推送渠道配置
     */
    public void delete(Long id) {
        MsgPushConfig existing = pushConfigMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("推送渠道配置不存在");
        }
        pushConfigMapper.deleteById(id);
    }
}
