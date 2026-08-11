package com.zwinsight.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.system.domain.SysConfig;
import com.zwinsight.system.domain.SysConfigChangeLog;
import com.zwinsight.system.domain.vo.SysConfigVO;
import com.zwinsight.system.mapper.SysConfigChangeLogMapper;
import com.zwinsight.system.mapper.SysConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统配置服务单元测试（含值范围校验与 Redis 缓存行为）
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysConfig.class);
        TableInfoHelper.initTableInfo(assistant, SysConfigChangeLog.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Mock private SysConfigMapper configMapper;
    @Mock private SysConfigChangeLogMapper changeLogMapper;
    @Mock private RedisUtils redisUtils;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SystemConfigServiceImpl configService;

    private SysConfig config(String key, String value, String valueType, String valueRange) {
        SysConfig config = new SysConfig();
        config.setId(1L);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigName("测试配置");
        config.setConfigGroup("general");
        config.setValueType(valueType);
        config.setValueRange(valueRange);
        config.setDefaultValue("default");
        return config;
    }

    @Test
    @DisplayName("按分组查询：实体映射为 VO")
    void testListByGroup_mapsToVO() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(config("k1", "v1", "STRING", null)));

        List<SysConfigVO> result = configService.listByGroup("general");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfigKey()).isEqualTo("k1");
        assertThat(result.get(0).getDefaultValue()).isEqualTo("default");
    }

    @Test
    @DisplayName("更新配置：成功更新 + 清缓存 + 记录变更日志")
    void testUpdateConfig_ok() {
        SecurityContextHolder.setUserId(9999L);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config("k1", "old", "STRING", null));

        configService.updateConfig("k1", "new");

        verify(configMapper).updateById(any(SysConfig.class));
        verify(redisUtils).delete("sys:config:k1");
        verify(changeLogMapper).insert(any(SysConfigChangeLog.class));
    }

    @Test
    @DisplayName("更新配置：NUMBER 超出范围被拒绝")
    void testUpdateConfig_numberOutOfRange() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config("k1", "5", "NUMBER", "1-10"));

        assertThatThrownBy(() -> configService.updateConfig("k1", "100"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("范围内");
        verify(configMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新配置：NUMBER 非数字被拒绝")
    void testUpdateConfig_numberNotNumeric() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config("k1", "5", "NUMBER", null));

        assertThatThrownBy(() -> configService.updateConfig("k1", "abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须为数字");
    }

    @Test
    @DisplayName("更新配置：BOOLEAN 非法值被拒绝")
    void testUpdateConfig_booleanInvalid() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config("k1", "true", "BOOLEAN", null));

        assertThatThrownBy(() -> configService.updateConfig("k1", "yes"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("true 或 false");
    }

    @Test
    @DisplayName("更新配置：JSON 非法格式被拒绝")
    void testUpdateConfig_jsonInvalid() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config("k1", "{}", "JSON", null));

        assertThatThrownBy(() -> configService.updateConfig("k1", "{bad json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("合法的 JSON");
    }

    @Test
    @DisplayName("更新配置：配置项不存在抛异常")
    void testUpdateConfig_notFound() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> configService.updateConfig("missing", "v"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置项不存在");
    }

    @Test
    @DisplayName("重置默认值：恢复 defaultValue 并记录日志")
    void testResetToDefault() {
        SecurityContextHolder.setUserId(9999L);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config("k1", "custom", "STRING", null));

        configService.resetToDefault("k1");

        verify(configMapper).updateById(any(SysConfig.class));
        verify(redisUtils).delete("sys:config:k1");
        verify(changeLogMapper).insert(any(SysConfigChangeLog.class));
    }

    @Test
    @DisplayName("读取配置值：缓存命中直接返回不查库")
    void testGetConfigValue_cacheHit() {
        when(redisUtils.get("sys:config:k1")).thenReturn("cached");

        assertThat(configService.getConfigValue("k1")).isEqualTo("cached");
        verify(configMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("读取配置值：缓存未命中查库并回写缓存")
    void testGetConfigValue_cacheMiss() {
        when(redisUtils.get("sys:config:k1")).thenReturn(null);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config("k1", "dbValue", "STRING", null));

        assertThat(configService.getConfigValue("k1")).isEqualTo("dbValue");
        verify(redisUtils).set(eq("sys:config:k1"), eq("dbValue"), anyLong());
    }

    @Test
    @DisplayName("类型化读取：Integer 转换成功，非法转换抛异常")
    void testGetConfigValue_typed() {
        when(redisUtils.get("sys:config:num")).thenReturn("42");
        assertThat(configService.getConfigValue("num", Integer.class)).isEqualTo(42);

        when(redisUtils.get("sys:config:bad")).thenReturn("not-a-number");
        assertThatThrownBy(() -> configService.getConfigValue("bad", Integer.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类型转换失败");
    }
}
