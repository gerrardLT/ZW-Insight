package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private SysUserService userService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysUserRole.class);
    }

    @Test
    @DisplayName("根据ID查询：返回用户（selectOne 含租户条件）")
    void testGetById() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        SysUser result = userService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("分页查询：有租户上下文时 wrapper 含 tenant_id 过滤（跨租户越权修复 2026-08-14）")
    @SuppressWarnings("unchecked")
    void testPage_withTenantContext_filtersByTenant() {
        SecurityContextHolder.setTenantId(1L);
        try {
            Page<SysUser> page = new Page<>(1, 10);
            page.setRecords(List.of());
            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            userService.page(1, 10, null, null, null, null);

            ArgumentCaptor<LambdaQueryWrapper<SysUser>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(userMapper).selectPage(any(Page.class), captor.capture());
            assertThat(captor.getValue().getSqlSegment()).contains("tenant_id");
        } finally {
            SecurityContextHolder.clear();
        }
    }

    @Test
    @DisplayName("分页查询：无租户上下文时不加租户条件（内部调用零回归）")
    @SuppressWarnings("unchecked")
    void testPage_withoutTenantContext_noTenantFilter() {
        SecurityContextHolder.clear();
        Page<SysUser> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        userService.page(1, 10, null, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper<SysUser>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).doesNotContain("tenant_id");
    }

    @Test
    @DisplayName("新增用户：用户名已存在抛异常")
    void testSave_duplicateUsername() {
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword("123456");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> userService.save(user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    @DisplayName("新增用户：正常保存并加密密码")
    void testSave_ok() {
        SysUser user = new SysUser();
        user.setUsername("newuser");
        user.setPassword("123456");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$encoded");

        userService.save(user);

        assertThat(user.getPassword()).isEqualTo("$2a$encoded");
        verify(userMapper).insert(user);
    }

    @Test
    @DisplayName("更新用户：不存在抛异常")
    void testUpdate_notFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        SysUser update = new SysUser();
        update.setId(999L);

        assertThatThrownBy(() -> userService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("更新用户：密码字段被清空")
    void testUpdate_passwordCleared() {
        SysUser existing = new SysUser();
        existing.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(existing);

        SysUser update = new SysUser();
        update.setId(1L);
        update.setPassword("shouldBeNull");
        userService.update(update);

        assertThat(update.getPassword()).isNull();
        verify(userMapper).updateById(update);
    }

    @Test
    @DisplayName("删除用户：同时删除角色关联")
    void testDelete() {
        userService.delete(1L);

        verify(userMapper).deleteById(1L);
        verify(userRoleMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("分配角色：先删后插")
    void testAssignRoles() {
        userService.assignRoles(1L, List.of(10L, 20L));

        verify(userRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(userRoleMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("重置密码：密码被加密")
    void testResetPassword() {
        when(userMapper.selectById(1L)).thenReturn(new SysUser());
        when(passwordEncoder.encode("newpwd")).thenReturn("$2a$newpwd");

        userService.resetPassword(1L, "newpwd");

        verify(userMapper).updateById(argThat(u ->
                "$2a$newpwd".equals(((SysUser) u).getPassword())));
    }

    @Test
    @DisplayName("重置密码：空密码/用户不存在拒绝（P2 修复）")
    void testResetPassword_guards() {
        assertThatThrownBy(() -> userService.resetPassword(1L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能为空");
        when(userMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> userService.resetPassword(999L, "newpwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
        verify(userMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除用户：管理员（ADMIN/SUPER_ADMIN）不可删除（P1 管理员保护）")
    void testDelete_adminProtected() {
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
        assertThatThrownBy(() -> userService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号不可删除");
        verify(userMapper, never()).deleteById(anyLong());

        when(userMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("ADMIN"));
        assertThatThrownBy(() -> userService.batchDelete(List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号不可删除");
        verify(userMapper, never()).deleteBatchIds(any());
    }

    // ==================== 安全测试场景 ====================

    @Test
    @DisplayName("新增用户：系统当前无密码强度校验，弱密码亦会保存（现状记录）")
    void testSave_weakPassword_currentBehavior_accepted() {
        SysUser user = new SysUser();
        user.setUsername("weakuser");
        user.setPassword("123456");  // 弱密码
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$encoded");

        userService.save(user);

        verify(userMapper).insert(user);
    }

    @Test
    @DisplayName("新增用户：强密码保存成功")
    void testSave_strongPassword_accepted() {
        SysUser user = new SysUser();
        user.setUsername("stronguser");
        user.setPassword("Str0ng!Pass#2024");  // 强密码
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("Str0ng!Pass#2024")).thenReturn("$2a$encoded");

        userService.save(user);

        assertThat(user.getPassword()).startsWith("$2a$");
        verify(userMapper).insert(user);
    }

    @Test
    @DisplayName("批量删除：空列表正常执行")
    void testBatchDelete_emptyList_noException() {
        userService.batchDelete(List.of());

        // 当前实现未对空列表短路，直接透传 mapper
        verify(userMapper).deleteBatchIds(List.of());
    }

    @Test
    @DisplayName("批量删除：多个 ID 正确删除")
    void testBatchDelete_multipleIds() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
        userService.batchDelete(ids);

        verify(userMapper).deleteBatchIds(ids);
        // IN 子句值经参数绑定，不直接出现在 SQL 片段中，需校验参数映射
        verify(userRoleMapper).delete(argThat(wrapper -> {
            LambdaQueryWrapper<SysUserRole> w = (LambdaQueryWrapper<SysUserRole>) wrapper;
            return w.getSqlSegment().contains("user_id")
                && w.getParamNameValuePairs().values().containsAll(ids);
        }));
    }

    // ==================== 分页查询测试 ====================

    @Test
    @DisplayName("分页查询：无参数返回所有")
    void testPage_noParams_returnsAll() {
        Page<SysUser> stubPage = new Page<>(1, 10);
        stubPage.setTotal(50);
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
            .thenReturn(stubPage);

        PageResult<SysUser> result = userService.page(1, 10, null, null, null, null);

        assertThat(result.getTotal()).isEqualTo(50);
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("分页查询：按用户名模糊搜索")
    void testPage_byUsername_like() {
        Page<SysUser> stubPage = new Page<>(1, 10);
        stubPage.setTotal(10);
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
            .thenReturn(stubPage);

        userService.page(1, 10, "admin", null, null, null);

        ArgumentCaptor<LambdaQueryWrapper<SysUser>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("username");
    }

    // ==================== 导入导出测试 ====================

    @Test
    @DisplayName("导入用户：文件读取失败抛出业务异常")
    void testImportUsers_readFailure_throwsBusinessException() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        // getInputStream 声明抛 IOException，service 捕获后包装为 BusinessException
        doThrow(new IOException("bad file")).when(mockFile).getInputStream();

        assertThatThrownBy(() -> userService.importUsers(mockFile))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("文件读取失败");
    }

    @Test
    @DisplayName("批量更新状态：指定用户状态变更")
    void testUpdateStatus_batchUpdate() {
        List<Long> ids = List.of(1L, 2L, 3L);
        userService.updateStatus(ids, 0);  // 停用

        verify(userMapper).update(isNull(), argThat(wrapper -> {
            LambdaUpdateWrapper<SysUser> w = (LambdaUpdateWrapper<SysUser>) wrapper;
            return w.getSqlSegment().contains("IN")
                && w.getSqlSet().contains("status")
                && w.getParamNameValuePairs().values().containsAll(ids);
        }));
    }

    // ==================== 密码加密算法对比测试 ====================

    @Test
    @DisplayName("BCrypt 密码加密：不可逆性验证")
    void testBCrypt_irreversible() {
        // 算法特性测试需用真实编码器（@Mock 实例 encode 返回 null）
        BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder();
        String plainPassword = "TestPassword123!";
        String encoded = realEncoder.encode(plainPassword);

        // 原始密码不应出现在编码后
        assertThat(encoded).doesNotContain(plainPassword);
        // BCrypt 编码格式验证
        assertThat(encoded).startsWith("$2a$")
                         .contains("$")
                         .hasSizeGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("BCrypt 密码匹配：正确密码验证通过")
    void testBCrypt_passwordMatches_success() {
        BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder();
        String plainPassword = "CorrectPassword!";
        String encoded = realEncoder.encode(plainPassword);

        assertThat(realEncoder.matches(plainPassword, encoded)).isTrue();
    }

    // ==================== SQL 注入防御测试 ====================

    @Test
    @DisplayName("SQL 注入：LIKE 查询防止注入")
    void testSqlInjection_prevention() {
        String maliciousInput = "%admin' OR '1'='1";
        
        // 验证 LIKE 条件中应使用预处理语句而非字符串拼接
        // 此处验证框架层面已使用 MyBatis-Plus LambdaQueryWrapper 避免注入
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(true, SysUser::getUsername, maliciousInput);
        
        // 不应直接拼接 SQL（LambdaWrapper 使用参数绑定，恶意片段不会出现在 SQL 片段中）
        String sql = wrapper.getSqlSegment();
        assertThat(sql).doesNotContain("OR '1'='1");
    }
}
