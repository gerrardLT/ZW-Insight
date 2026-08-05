package com.zwinsight.purchase.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.portal.domain.SysSupplierAccount;
import com.zwinsight.purchase.portal.mapper.SysSupplierAccountMapper;
import com.zwinsight.purchase.portal.util.SupplierJwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SupplierAuthService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SupplierAuthServiceTest {

    @Mock
    private SysSupplierAccountMapper supplierAccountMapper;

    @Mock
    private SupplierSmsService smsService;

    @Mock
    private SupplierJwtUtils supplierJwtUtils;

    @InjectMocks
    private SupplierAuthService authService;

    private SysSupplierAccount account(String phone, Integer status) {
        SysSupplierAccount a = new SysSupplierAccount();
        a.setId(10L);
        a.setSupplierId(200L);
        a.setPhone(phone);
        a.setSupplierName("测试供应商");
        a.setStatus(status);
        return a;
    }

    // ── sendCode ──────────────────────────────────────

    @Test
    @DisplayName("sendCode - 手机号未注册抛异常")
    void sendCode_unregistered_throws() {
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> authService.sendCode("13800138000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册为供应商账户");
        verify(smsService, never()).sendCode(anyString());
    }

    @Test
    @DisplayName("sendCode - 账户停用抛异常")
    void sendCode_disabled_throws() {
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(account("13800138000", 0));

        assertThatThrownBy(() -> authService.sendCode("13800138000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被停用");
    }

    @Test
    @DisplayName("sendCode - 正常账户委托短信服务发送")
    void sendCode_success_delegatesToSms() {
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(account("13800138000", 1));

        authService.sendCode("13800138000");

        verify(smsService).sendCode("13800138000");
    }

    // ── login ──────────────────────────────────────

    @Test
    @DisplayName("login - 手机号或验证码为空抛异常")
    void login_blankInput_throws() {
        assertThatThrownBy(() -> authService.login("", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号不能为空");
        assertThatThrownBy(() -> authService.login("13800138000", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码不能为空");
    }

    @Test
    @DisplayName("login - 验证码错误抛异常")
    void login_wrongCode_throws() {
        when(smsService.verifyCode("13800138000", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("13800138000", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码错误或已过期");
    }

    @Test
    @DisplayName("login - 验证码通过但账户不存在/停用抛异常")
    void login_accountMissingOrDisabled_throws() {
        when(smsService.verifyCode(anyString(), anyString())).thenReturn(true);
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> authService.login("13800138000", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册为供应商账户");

        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(account("13800138000", 0));
        assertThatThrownBy(() -> authService.login("13800138000", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被停用");
    }

    @Test
    @DisplayName("login - 正常登录：更新最后登录时间并返回 token 信息")
    void login_success_returnsTokenPayload() {
        SysSupplierAccount a = account("13800138000", 1);
        when(smsService.verifyCode("13800138000", "123456")).thenReturn(true);
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(a);
        when(supplierJwtUtils.generateToken(200L, "13800138000", "测试供应商")).thenReturn("jwt-token");
        when(supplierJwtUtils.getExpiration()).thenReturn(7200_000L);

        Map<String, Object> result = authService.login("13800138000", "123456");

        assertThat(result.get("token")).isEqualTo("jwt-token");
        assertThat(result.get("supplierId")).isEqualTo(200L);
        assertThat(result.get("supplierName")).isEqualTo("测试供应商");
        assertThat(result.get("expiresIn")).isEqualTo(7200L);
        assertThat(a.getLastLoginAt()).isNotNull();
        verify(supplierAccountMapper).updateById(a);
    }

    // ── verifyCode（公开报价场景）──────────────────────

    @Test
    @DisplayName("verifyCode - 入参为空/验证码错误抛异常，正确则通过（不要求账户注册）")
    void verifyCode_behaviors() {
        assertThatThrownBy(() -> authService.verifyCode(null, "123456"))
                .hasMessageContaining("手机号不能为空");
        assertThatThrownBy(() -> authService.verifyCode("13800138000", null))
                .hasMessageContaining("验证码不能为空");

        when(smsService.verifyCode("13800138000", "000000")).thenReturn(false);
        assertThatThrownBy(() -> authService.verifyCode("13800138000", "000000"))
                .hasMessageContaining("验证码错误或已过期");

        when(smsService.verifyCode("13800138000", "123456")).thenReturn(true);
        authService.verifyCode("13800138000", "123456"); // 不抛异常即通过
        verify(supplierAccountMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    // ── getOrCreateSupplierByPhone ──────────────────────

    @Test
    @DisplayName("getOrCreateSupplierByPhone - 账户已存在直接返回 supplierId")
    void getOrCreate_existing_returnsSupplierId() {
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(account("13800138000", 1));

        assertThat(authService.getOrCreateSupplierByPhone("13800138000")).isEqualTo(200L);
        verify(supplierAccountMapper, never()).insert(any());
    }

    @Test
    @DisplayName("getOrCreateSupplierByPhone - 未注册则创建临时账户并回填 supplierId")
    void getOrCreate_newPhone_createsTempAccount() {
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // 模拟 MyBatis-Plus insert 后回填主键
        doAnswer(inv -> {
            SysSupplierAccount a = inv.getArgument(0);
            a.setId(300L);
            return 1;
        }).when(supplierAccountMapper).insert(any(SysSupplierAccount.class));

        Long supplierId = authService.getOrCreateSupplierByPhone("13812345678");

        assertThat(supplierId).isEqualTo(300L);
        verify(supplierAccountMapper).insert(argThat(a ->
                "供应商_5678".equals(a.getSupplierName()) && Integer.valueOf(1).equals(a.getStatus())));
        verify(supplierAccountMapper).updateById(argThat(a -> Long.valueOf(300L).equals(a.getSupplierId())));
    }

    // ── getSupplierName ──────────────────────────────────

    @Test
    @DisplayName("getSupplierName - 存在返回名称，不存在返回'未知供应商'")
    void getSupplierName_foundAndNotFound() {
        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(account("13800138000", 1));
        assertThat(authService.getSupplierName(200L)).isEqualTo("测试供应商");

        when(supplierAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThat(authService.getSupplierName(999L)).isEqualTo("未知供应商");
    }
}
