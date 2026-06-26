package com.zwinsight.purchase.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.portal.domain.SysSupplierAccount;
import com.zwinsight.purchase.portal.mapper.SysSupplierAccountMapper;
import com.zwinsight.purchase.portal.util.SupplierJwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 供应商认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAuthService {

    private final SysSupplierAccountMapper supplierAccountMapper;
    private final SupplierSmsService smsService;
    private final SupplierJwtUtils supplierJwtUtils;

    /**
     * 发送验证码
     */
    public void sendCode(String phone) {
        // 校验手机号是否已注册为供应商账户
        SysSupplierAccount account = findByPhone(phone);
        if (account == null) {
            throw new BusinessException("该手机号未注册为供应商账户");
        }
        if (account.getStatus() != null && account.getStatus() == 0) {
            throw new BusinessException("该供应商账户已被停用");
        }
        smsService.sendCode(phone);
    }

    /**
     * 验证码登录
     *
     * @param phone 手机号
     * @param code  验证码
     * @return 登录响应（包含 token）
     */
    public Map<String, Object> login(String phone, String code) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException("验证码不能为空");
        }

        // 验证验证码
        if (!smsService.verifyCode(phone, code)) {
            throw new BusinessException("验证码错误或已过期");
        }

        // 查询供应商账户
        SysSupplierAccount account = findByPhone(phone);
        if (account == null) {
            throw new BusinessException("该手机号未注册为供应商账户");
        }
        if (account.getStatus() != null && account.getStatus() == 0) {
            throw new BusinessException("该供应商账户已被停用");
        }

        // 更新最后登录时间
        account.setLastLoginAt(LocalDateTime.now());
        supplierAccountMapper.updateById(account);

        // 生成独立的供应商 JWT
        String token = supplierJwtUtils.generateToken(account.getSupplierId(), account.getPhone(), account.getSupplierName());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("supplierId", account.getSupplierId());
        result.put("supplierName", account.getSupplierName());
        result.put("phone", account.getPhone());
        result.put("expiresIn", supplierJwtUtils.getExpiration() / 1000); // 秒
        return result;
    }

    /**
     * 验证短信验证码（公开报价场景使用）
     * <p>
     * 与 login 不同，此方法不要求手机号已注册为供应商账户，
     * 仅校验验证码是否正确，用于公开询价免登录报价流程。
     *
     * @param phone   手机号
     * @param smsCode 验证码
     * @throws BusinessException 验证码错误时抛出异常
     */
    public void verifyCode(String phone, String smsCode) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        if (smsCode == null || smsCode.isBlank()) {
            throw new BusinessException("验证码不能为空");
        }
        if (!smsService.verifyCode(phone, smsCode)) {
            throw new BusinessException("验证码错误或已过期");
        }
    }

    /**
     * 根据手机号关联或创建临时供应商账户
     * <p>
     * 如果手机号已有对应的供应商账户，直接返回其 supplierId；
     * 如果没有，则自动创建一个临时供应商账户。
     *
     * @param phone 手机号
     * @return 供应商ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long getOrCreateSupplierByPhone(String phone) {
        SysSupplierAccount account = findByPhone(phone);
        if (account != null) {
            return account.getSupplierId();
        }

        // 创建临时供应商账户
        SysSupplierAccount newAccount = new SysSupplierAccount();
        newAccount.setPhone(phone);
        newAccount.setSupplierName("供应商_" + phone.substring(phone.length() - 4));
        newAccount.setStatus(1); // 正常状态
        newAccount.setCreatedAt(LocalDateTime.now());
        supplierAccountMapper.insert(newAccount);

        log.info("自动创建临时供应商账户，手机号: {}, ID: {}", phone, newAccount.getId());
        // 新创建的账户 supplierId 就是自身ID
        if (newAccount.getSupplierId() == null) {
            newAccount.setSupplierId(newAccount.getId());
            supplierAccountMapper.updateById(newAccount);
        }
        return newAccount.getSupplierId();
    }

    /**
     * 获取供应商名称
     *
     * @param supplierId 供应商ID
     * @return 供应商名称
     */
    public String getSupplierName(Long supplierId) {
        LambdaQueryWrapper<SysSupplierAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysSupplierAccount::getSupplierId, supplierId)
                .last("LIMIT 1");
        SysSupplierAccount account = supplierAccountMapper.selectOne(wrapper);
        return account != null ? account.getSupplierName() : "未知供应商";
    }

    private SysSupplierAccount findByPhone(String phone) {
        LambdaQueryWrapper<SysSupplierAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysSupplierAccount::getPhone, phone);
        return supplierAccountMapper.selectOne(wrapper);
    }
}
