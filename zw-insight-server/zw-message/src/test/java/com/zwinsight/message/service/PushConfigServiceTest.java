package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.message.domain.MsgPushConfig;
import com.zwinsight.message.mapper.MsgPushConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推送渠道配置服务单元测试（业务类型唯一性校验）
 */
@ExtendWith(MockitoExtension.class)
class PushConfigServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MsgPushConfig.class);
    }

    @Mock private MsgPushConfigMapper pushConfigMapper;

    @InjectMocks
    private PushConfigService pushConfigService;

    private MsgPushConfig config(Long id, String businessType) {
        MsgPushConfig config = new MsgPushConfig();
        config.setId(id);
        config.setBusinessType(businessType);
        return config;
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(pushConfigMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> pushConfigService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("推送渠道配置不存在");
    }

    @Test
    @DisplayName("按业务类型查询：正常返回 / 不存在抛异常")
    void testGetByBusinessType() {
        when(pushConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config(1L, "APPROVAL"))
                .thenReturn(null);

        assertThat(pushConfigService.getByBusinessType("APPROVAL").getId()).isEqualTo(1L);
        assertThatThrownBy(() -> pushConfigService.getByBusinessType("NONE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到业务类型");
    }

    @Test
    @DisplayName("新增：业务类型重复抛异常且不落库")
    void testSave_duplicateBusinessType() {
        when(pushConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> pushConfigService.save(config(null, "APPROVAL")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
        verify(pushConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增：业务类型唯一时正常落库")
    void testSave_ok() {
        when(pushConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        MsgPushConfig config = config(null, "REMINDER");

        pushConfigService.save(config);

        verify(pushConfigMapper).insert(config);
    }

    @Test
    @DisplayName("更新：不存在抛异常 / 变更为已占用业务类型被拒绝")
    void testUpdate_notFoundAndDuplicate() {
        when(pushConfigMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> pushConfigService.update(config(999L, "X")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("推送渠道配置不存在");

        when(pushConfigMapper.selectById(1L)).thenReturn(config(1L, "APPROVAL"));
        when(pushConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> pushConfigService.update(config(1L, "REMINDER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
        verify(pushConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除：记录不存在抛异常")
    void testDelete_notFound() {
        when(pushConfigMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> pushConfigService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("推送渠道配置不存在");
    }
}
