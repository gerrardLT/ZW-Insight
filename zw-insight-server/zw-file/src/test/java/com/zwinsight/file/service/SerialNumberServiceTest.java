package com.zwinsight.file.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.domain.SerialNumberRule;
import com.zwinsight.file.mapper.SerialNumberRuleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 编号生成服务单元测试（规则校验 + Redis 自增 + 格式化）
 */
@ExtendWith(MockitoExtension.class)
class SerialNumberServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SerialNumberRule.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Mock private SerialNumberRuleMapper ruleMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SerialNumberService serialNumberService;

    @Test
    @DisplayName("生成编号：未配置规则抛异常")
    void testGenerate_ruleNotFound() {
        SecurityContextHolder.setTenantId(9999L);
        when(ruleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> serialNumberService.generate("UNKNOWN"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置编号规则");
    }

    @Test
    @DisplayName("生成编号：前缀+日期+补零序号格式正确")
    void testGenerate_formatsCorrectly() {
        SecurityContextHolder.setTenantId(9999L);
        SerialNumberRule rule = new SerialNumberRule();
        rule.setRulePrefix("HT");
        rule.setDateFormat("yyyyMMdd");
        rule.setSeqLength(4);
        when(ruleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(rule);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(7L);

        String serial = serialNumberService.generate("CONTRACT");

        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(serial).isEqualTo("HT" + datePart + "0007");
    }

    @Test
    @DisplayName("新增规则：业务类型重复抛异常")
    void testSave_duplicate() {
        SecurityContextHolder.setTenantId(9999L);
        when(ruleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        SerialNumberRule rule = new SerialNumberRule();
        rule.setBusinessType("CONTRACT");

        assertThatThrownBy(() -> serialNumberService.save(rule))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("更新规则：不存在抛异常")
    void testUpdate_notFound() {
        when(ruleMapper.selectById(999L)).thenReturn(null);
        SerialNumberRule rule = new SerialNumberRule();
        rule.setId(999L);

        assertThatThrownBy(() -> serialNumberService.update(rule))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编号规则不存在");
    }

    @Test
    @DisplayName("列表/删除：透传 mapper 调用")
    void testListAndDelete() {
        SecurityContextHolder.setTenantId(9999L);
        serialNumberService.list();
        verify(ruleMapper).selectList(any(LambdaQueryWrapper.class));

        serialNumberService.delete(1L);
        verify(ruleMapper).deleteById(1L);
    }
}
