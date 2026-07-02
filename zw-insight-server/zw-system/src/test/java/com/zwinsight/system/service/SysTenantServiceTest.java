package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.mapper.SysTenantMenuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysTenantServiceTest {

    @Mock private SysTenantMapper tenantMapper;
    @Mock private SysTenantMenuMapper tenantMenuMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private RedisUtils redisUtils;

    @InjectMocks
    private SysTenantService tenantService;

    @Test
    @DisplayName("根据ID查询：存在返回租户")
    void testGetById_found() {
        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setTenantName("测试租户");
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        SysTenant result = tenantService.getById(1L);

        assertThat(result.getTenantName()).isEqualTo("测试租户");
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(tenantMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenantService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户不存在");
    }

    @Test
    @DisplayName("更新租户：不允许修改编码和密钥")
    void testUpdate_clearsCodeAndKey() {
        SysTenant existing = new SysTenant();
        existing.setId(1L);
        when(tenantMapper.selectById(1L)).thenReturn(existing);

        SysTenant update = new SysTenant();
        update.setId(1L);
        update.setTenantCode("SHOULD_BE_CLEARED");
        update.setSecretKey("SHOULD_BE_CLEARED");
        tenantService.update(update);

        assertThat(update.getTenantCode()).isNull();
        assertThat(update.getSecretKey()).isNull();
    }

    @Test
    @DisplayName("删除租户：同时删除菜单关联")
    void testDelete() {
        SysTenant existing = new SysTenant();
        existing.setId(1L);
        when(tenantMapper.selectById(1L)).thenReturn(existing);

        tenantService.delete(1L);

        verify(tenantMapper).deleteById(1L);
        verify(tenantMenuMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("删除租户：不存在抛异常")
    void testDelete_notFound() {
        when(tenantMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenantService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户不存在");
    }

    @Test
    @DisplayName("续期：天数超范围抛异常")
    void testRenew_invalidDays() {
        assertThatThrownBy(() -> tenantService.renewTenant(1L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("续期天数必须在1-1095之间");
    }

    @Test
    @DisplayName("续期：正常续期")
    void testRenew_ok() {
        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setEndDate(LocalDate.of(2026, 1, 1));
        tenant.setStatus(1);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        tenantService.renewTenant(1L, 30);

        verify(tenantMapper).updateById(argThat(t -> {
            SysTenant st = (SysTenant) t;
            return st.getEndDate().equals(LocalDate.of(2026, 1, 31));
        }));
    }
}
