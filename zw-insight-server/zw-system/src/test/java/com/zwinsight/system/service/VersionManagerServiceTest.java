package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysVersion;
import com.zwinsight.system.mapper.SysVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统版本管理服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class VersionManagerServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysVersion.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Mock private SysVersionMapper versionMapper;

    @InjectMocks
    private VersionManagerService versionManagerService;

    @Test
    @DisplayName("创建版本：非语义化版本号抛 400")
    void testCreate_invalidFormat() {
        assertThatThrownBy(() -> versionManagerService.create("1.2", LocalDate.now(), "log"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本号格式无效");
        verify(versionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建版本：发布日期为空抛 400")
    void testCreate_nullReleaseDate() {
        assertThatThrownBy(() -> versionManagerService.create("1.2.0", null, "log"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发布日期不能为空");
    }

    @Test
    @DisplayName("创建版本：版本号重复抛 409")
    void testCreate_duplicate() {
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> versionManagerService.create("1.2.0", LocalDate.now(), "log"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本号已存在");
    }

    @Test
    @DisplayName("创建版本：成功落库且操作人取自安全上下文")
    void testCreate_ok() {
        SecurityContextHolder.setUserId(9999L);
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        SysVersion created = versionManagerService.create("2.0.1", LocalDate.of(2026, 8, 11), "## 更新");

        ArgumentCaptor<SysVersion> captor = ArgumentCaptor.forClass(SysVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(created.getVersionNo()).isEqualTo("2.0.1");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(9999L);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("版本列表与最新版本：透传 mapper 查询")
    void testListAllAndGetCurrent() {
        SysVersion v = new SysVersion();
        v.setVersionNo("2.0.1");
        when(versionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(v));
        when(versionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(v);

        assertThat(versionManagerService.listAll()).hasSize(1);
        assertThat(versionManagerService.getCurrent().getVersionNo()).isEqualTo("2.0.1");
    }
}
