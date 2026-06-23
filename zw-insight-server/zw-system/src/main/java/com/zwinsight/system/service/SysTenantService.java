package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.system.domain.SysTenantMenu;
import com.zwinsight.system.mapper.SysTenantMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 租户管理服务
 */
@Service
@RequiredArgsConstructor
public class SysTenantService {

    private final SysTenantMapper tenantMapper;
    private final SysTenantMenuMapper tenantMenuMapper;

    /**
     * 分页查询（支持按名称/状态/到期日期范围查询）
     */
    public PageResult<SysTenant> page(int page, int size, String tenantName,
                                       Integer status, LocalDate expireStart, LocalDate expireEnd) {
        Page<SysTenant> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(tenantName), SysTenant::getTenantName, tenantName)
                .eq(status != null, SysTenant::getStatus, status)
                .ge(expireStart != null, SysTenant::getExpireDate, expireStart)
                .le(expireEnd != null, SysTenant::getExpireDate, expireEnd)
                .orderByDesc(SysTenant::getCreatedAt);
        Page<SysTenant> result = tenantMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public SysTenant getById(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        return tenant;
    }

    /**
     * 新增租户（自动生成组织码和密钥）
     */
    public void save(SysTenant tenant) {
        // 生成唯一租户编码（如果未提供）
        if (StrUtil.isBlank(tenant.getTenantCode())) {
            tenant.setTenantCode(generateTenantCode());
        }
        // 检查编码唯一性
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenant::getTenantCode, tenant.getTenantCode());
        if (tenantMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("租户编码已存在");
        }
        // 生成密钥
        if (StrUtil.isBlank(tenant.getSecretKey())) {
            tenant.setSecretKey(generateSecretKey());
        }
        // 默认状态启用
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        tenantMapper.insert(tenant);
    }

    /**
     * 更新租户
     */
    public void update(SysTenant tenant) {
        SysTenant existing = tenantMapper.selectById(tenant.getId());
        if (existing == null) {
            throw new BusinessException("租户不存在");
        }
        // 不允许修改租户编码和密钥
        tenant.setTenantCode(null);
        tenant.setSecretKey(null);
        tenantMapper.updateById(tenant);
    }

    /**
     * 删除租户
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysTenant existing = tenantMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("租户不存在");
        }
        tenantMapper.deleteById(id);
        // 同时删除租户菜单权限关联
        tenantMenuMapper.delete(
                new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, id));
    }

    /**
     * 租户续期
     * 续期逻辑：新到期日 = max(原到期日, 今天) + 续期天数
     */
    public void renew(Long tenantId, Integer durationDays) {
        if (durationDays == null || durationDays <= 0) {
            throw new BusinessException("续期天数必须大于0");
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        // 计算新到期日：max(原到期日, 今天) + 续期天数
        LocalDate baseDate = tenant.getExpireDate();
        LocalDate today = LocalDate.now();
        if (baseDate == null || baseDate.isBefore(today)) {
            baseDate = today;
        }
        LocalDate newExpireDate = baseDate.plusDays(durationDays);

        SysTenant updateTenant = new SysTenant();
        updateTenant.setId(tenantId);
        updateTenant.setExpireDate(newExpireDate);
        tenantMapper.updateById(updateTenant);
    }

    /**
     * 为租户分配功能菜单权限
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePermissions(Long tenantId, List<Long> menuIds) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        // 先删除原有关联
        tenantMenuMapper.delete(
                new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId));
        // 再批量插入新关联
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysTenantMenu tenantMenu = new SysTenantMenu();
                tenantMenu.setTenantId(tenantId);
                tenantMenu.setMenuId(menuId);
                tenantMenuMapper.insert(tenantMenu);
            }
        }
    }

    /**
     * 生成租户编码（格式：T + 时间戳后6位 + 4位随机数）
     */
    private String generateTenantCode() {
        long timestamp = System.currentTimeMillis();
        String timePart = String.valueOf(timestamp).substring(7);
        int random = (int) (Math.random() * 9000) + 1000;
        return "T" + timePart + random;
    }

    /**
     * 生成密钥（UUID去横线）
     */
    private String generateSecretKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
