package com.zwinsight.file.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.domain.SerialNumberRule;
import com.zwinsight.file.mapper.SerialNumberRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 编号生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SerialNumberService {

    private final SerialNumberRuleMapper ruleMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成编号
     * 规则: 前缀 + 日期部分 + 序号
     * Redis key: serial:{tenantId}:{businessType}:{datePart}
     */
    public String generate(String businessType) {
        Long tenantId = SecurityContextHolder.getTenantId();

        // 查询规则
        LambdaQueryWrapper<SerialNumberRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SerialNumberRule::getBusinessType, businessType)
                .eq(SerialNumberRule::getTenantId, tenantId);
        SerialNumberRule rule = ruleMapper.selectOne(wrapper);
        if (rule == null) {
            throw new BusinessException("未配置编号规则: " + businessType);
        }

        // 生成日期部分
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern(rule.getDateFormat()));

        // Redis自增
        String redisKey = String.format("serial:%d:%s:%s", tenantId, businessType, datePart);
        Long seq = stringRedisTemplate.opsForValue().increment(redisKey);

        // 格式化序号
        String seqStr = StrUtil.padPre(String.valueOf(seq), rule.getSeqLength(), '0');

        return rule.getRulePrefix() + datePart + seqStr;
    }

    /**
     * 查询所有规则
     */
    public List<SerialNumberRule> list() {
        Long tenantId = SecurityContextHolder.getTenantId();
        LambdaQueryWrapper<SerialNumberRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SerialNumberRule::getTenantId, tenantId)
                .orderByDesc(SerialNumberRule::getCreatedAt);
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 新增规则
     */
    public void save(SerialNumberRule rule) {
        // 校验业务类型唯一性
        Long tenantId = SecurityContextHolder.getTenantId();
        LambdaQueryWrapper<SerialNumberRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SerialNumberRule::getBusinessType, rule.getBusinessType())
                .eq(SerialNumberRule::getTenantId, tenantId);
        Long count = ruleMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("该业务类型编号规则已存在");
        }
        ruleMapper.insert(rule);
    }

    /**
     * 更新规则
     */
    public void update(SerialNumberRule rule) {
        SerialNumberRule existing = ruleMapper.selectById(rule.getId());
        if (existing == null) {
            throw new BusinessException("编号规则不存在");
        }
        ruleMapper.updateById(rule);
    }

    /**
     * 删除规则
     */
    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }
}
