package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysTenantMenu;
import com.zwinsight.system.domain.enums.TenantModuleEnum;
import com.zwinsight.system.domain.enums.TenantStatusEnum;
import com.zwinsight.system.domain.enums.TenantUserTypeEnum;
import com.zwinsight.system.dto.TenantCreateRequest;
import com.zwinsight.system.mapper.SysTenantMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 租户管理服务（增强版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysTenantService {

    private final SysTenantMapper tenantMapper;
    private final SysTenantMenuMapper tenantMenuMapper;
    private final SysUserMapper userMapper;
    private final RedisUtils redisUtils;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    // ============ 基础 CRUD ============

    /**
     * 分页查询（支持按名称/状态/到期日期范围查询）
     */
    public PageResult<SysTenant> page(int page, int size, String tenantName,
                                       Integer status, LocalDate expireStart, LocalDate expireEnd) {
        Page<SysTenant> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(tenantName), SysTenant::getTenantName, tenantName)
                .eq(status != null, SysTenant::getStatus, status)
                .ge(expireStart != null, SysTenant::getEndDate, expireStart)
                .le(expireEnd != null, SysTenant::getEndDate, expireEnd)
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

    // ============ 增强功能 ============

    /**
     * 创建租户：自动生成编码 + 初始化管理员 + 设置默认有效期
     */
    @Transactional(rollbackFor = Exception.class)
    public void createTenant(TenantCreateRequest request) {
        // 参数校验
        if (StrUtil.isBlank(request.getTenantName())) {
            throw new BusinessException("租户名称不能为空");
        }
        if (StrUtil.isBlank(request.getContactName())) {
            throw new BusinessException("联系人姓名不能为空");
        }
        if (StrUtil.isBlank(request.getAdminUsername())) {
            throw new BusinessException("管理员用户名不能为空");
        }
        if (StrUtil.isBlank(request.getAdminPassword())) {
            throw new BusinessException("管理员密码不能为空");
        }

        // 解析用户类型
        String userTypeCode = request.getUserType();
        if (StrUtil.isBlank(userTypeCode)) {
            userTypeCode = TenantUserTypeEnum.STANDARD.getCode();
        }
        TenantUserTypeEnum userType = TenantUserTypeEnum.fromCode(userTypeCode);

        // 构建租户实体
        SysTenant tenant = new SysTenant();
        tenant.setTenantCode(generateTenantCode());
        tenant.setTenantName(request.getTenantName());
        tenant.setContactName(request.getContactName());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setAddress(request.getAddress());
        tenant.setUserType(userType.getCode());
        tenant.setStatus(TenantStatusEnum.NORMAL.getCode());
        tenant.setSecretKey(generateSecretKey());

        // 设置有效期
        LocalDate today = LocalDate.now();
        tenant.setStartDate(today);
        int days = userType.getDefaultDays();
        if (userType == TenantUserTypeEnum.ENTERPRISE && request.getCustomDays() != null) {
            days = request.getCustomDays();
        }
        if (days > 0) {
            tenant.setEndDate(today.plusDays(days));
            tenant.setExpireDate(today.plusDays(days));
        }

        // 设置最大用户数
        if (request.getMaxUsers() != null && request.getMaxUsers() > 0) {
            tenant.setMaxUsers(request.getMaxUsers());
        } else {
            tenant.setMaxUsers(userType.getDefaultMaxUsers());
        }

        // 设置功能模块
        if (request.getModules() != null && !request.getModules().isEmpty()) {
            // 校验模块编码合法性
            for (String moduleCode : request.getModules()) {
                if (!TenantModuleEnum.isValidCode(moduleCode)) {
                    throw new BusinessException("无效的模块编码: " + moduleCode);
                }
            }
            tenant.setModules(request.getModules());
        }

        tenantMapper.insert(tenant);

        // 初始化管理员账号
        SysUser adminUser = new SysUser();
        adminUser.setUsername(request.getAdminUsername());
        adminUser.setPassword(PASSWORD_ENCODER.encode(request.getAdminPassword()));
        adminUser.setRealName(request.getContactName());
        adminUser.setPhone(request.getAdminPhone() != null ? request.getAdminPhone() : request.getContactPhone());
        adminUser.setStatus(1);
        adminUser.setTenantId(tenant.getId());
        userMapper.insert(adminUser);

        log.info("创建租户成功: code={}, name={}, userType={}", tenant.getTenantCode(), tenant.getTenantName(), userType.getCode());
    }

    /**
     * 停用租户：更新状态 + 清除该租户所有用户的 Redis Token
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableTenant(Long tenantId) {
        SysTenant tenant = getById(tenantId);
        if (tenant.getStatus() == TenantStatusEnum.DISABLED.getCode()) {
            throw new BusinessException("租户已处于停用状态");
        }

        // 更新状态为已停用
        SysTenant updateTenant = new SysTenant();
        updateTenant.setId(tenantId);
        updateTenant.setStatus(TenantStatusEnum.DISABLED.getCode());
        tenantMapper.updateById(updateTenant);

        // 清除该租户所有用户的 Token
        clearTenantTokens(tenantId);

        log.info("停用租户: id={}, name={}", tenantId, tenant.getTenantName());
    }

    /**
     * 启用租户：恢复正常状态（仅未过期租户可启用）
     */
    public void enableTenant(Long tenantId) {
        SysTenant tenant = getById(tenantId);
        if (tenant.getStatus() == TenantStatusEnum.NORMAL.getCode()) {
            throw new BusinessException("租户已处于正常状态");
        }

        // 检查是否已过期
        if (tenant.getEndDate() != null && tenant.getEndDate().isBefore(LocalDate.now())) {
            throw new BusinessException("租户已过期，请先续期后再启用");
        }

        // 更新状态为正常
        SysTenant updateTenant = new SysTenant();
        updateTenant.setId(tenantId);
        updateTenant.setStatus(TenantStatusEnum.NORMAL.getCode());
        tenantMapper.updateById(updateTenant);

        log.info("启用租户: id={}, name={}", tenantId, tenant.getTenantName());
    }

    /**
     * 续期：续期天数校验（1-1095）+ 有效期从当前 endDate 累加
     */
    public void renewTenant(Long tenantId, Integer days) {
        // 续期天数校验
        if (days == null || days < 1 || days > 1095) {
            throw new BusinessException("续期天数必须在1-1095之间");
        }

        SysTenant tenant = getById(tenantId);

        // 计算新到期日：从当前 endDate 累加（不是从今天）
        LocalDate currentEndDate = tenant.getEndDate();
        if (currentEndDate == null) {
            currentEndDate = LocalDate.now();
        }
        LocalDate newEndDate = currentEndDate.plusDays(days);

        // 更新
        SysTenant updateTenant = new SysTenant();
        updateTenant.setId(tenantId);
        updateTenant.setEndDate(newEndDate);
        updateTenant.setExpireDate(newEndDate);
        // 如果当前是已过期状态且续期后未过期，自动恢复正常
        if (tenant.getStatus() == TenantStatusEnum.EXPIRED.getCode() && newEndDate.isAfter(LocalDate.now())) {
            updateTenant.setStatus(TenantStatusEnum.NORMAL.getCode());
        }
        tenantMapper.updateById(updateTenant);

        log.info("租户续期: id={}, days={}, newEndDate={}", tenantId, days, newEndDate);
    }

    /**
     * 配置功能模块权限
     */
    public void updateModules(Long tenantId, List<String> modules) {
        getById(tenantId); // 确保租户存在

        // 校验模块编码合法性
        if (modules != null) {
            for (String moduleCode : modules) {
                if (!TenantModuleEnum.isValidCode(moduleCode)) {
                    throw new BusinessException("无效的模块编码: " + moduleCode);
                }
            }
        }

        SysTenant updateTenant = new SysTenant();
        updateTenant.setId(tenantId);
        updateTenant.setModules(modules);
        tenantMapper.updateById(updateTenant);

        log.info("更新租户功能模块: id={}, modules={}", tenantId, modules);
    }

    /**
     * 检查用户数上限：活跃用户数 >= max_users 时拒绝
     */
    public void checkUserLimit(Long tenantId) {
        SysTenant tenant = getById(tenantId);
        if (tenant.getMaxUsers() == null || tenant.getMaxUsers() <= 0) {
            return; // 无上限限制
        }

        // 查询该租户活跃用户数
        Long activeUserCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getStatus, 1)
        );

        if (activeUserCount >= tenant.getMaxUsers()) {
            throw new BusinessException("租户用户数已达上限(" + tenant.getMaxUsers() + "人)，无法新增用户");
        }
    }

    // ============ 定时任务 ============

    /**
     * 每天凌晨1点检查到期租户
     * 找到 status=1（正常）且 end_date <= 今天 的租户，将状态改为3（已过期）
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void checkExpiredTenants() {
        log.info("开始执行租户到期检查定时任务...");
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenant::getStatus, TenantStatusEnum.NORMAL.getCode())
                .le(SysTenant::getEndDate, today);

        List<SysTenant> expiredTenants = tenantMapper.selectList(wrapper);

        for (SysTenant tenant : expiredTenants) {
            SysTenant update = new SysTenant();
            update.setId(tenant.getId());
            update.setStatus(TenantStatusEnum.EXPIRED.getCode());
            tenantMapper.updateById(update);

            // 清除已过期租户的 Token
            clearTenantTokens(tenant.getId());

            log.info("租户已过期: id={}, name={}, endDate={}", tenant.getId(), tenant.getTenantName(), tenant.getEndDate());
        }

        log.info("租户到期检查完成，共处理 {} 个到期租户", expiredTenants.size());
    }

    /**
     * 每天上午9点发送续期提醒
     * 到期前15天和7天提醒
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendRenewalReminders() {
        log.info("开始执行续期提醒定时任务...");
        LocalDate today = LocalDate.now();
        LocalDate fifteenDaysLater = today.plusDays(15);
        LocalDate sevenDaysLater = today.plusDays(7);

        // 15天后到期的租户
        List<SysTenant> fifteenDayTenants = tenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getStatus, TenantStatusEnum.NORMAL.getCode())
                        .eq(SysTenant::getEndDate, fifteenDaysLater)
        );
        for (SysTenant tenant : fifteenDayTenants) {
            log.warn("[续期提醒-15天] 租户 '{}' (编码: {}) 将于 {} 到期，请及时续期",
                    tenant.getTenantName(), tenant.getTenantCode(), tenant.getEndDate());
        }

        // 7天后到期的租户
        List<SysTenant> sevenDayTenants = tenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getStatus, TenantStatusEnum.NORMAL.getCode())
                        .eq(SysTenant::getEndDate, sevenDaysLater)
        );
        for (SysTenant tenant : sevenDayTenants) {
            log.warn("[续期提醒-7天] 租户 '{}' (编码: {}) 将于 {} 到期，请尽快续期！",
                    tenant.getTenantName(), tenant.getTenantCode(), tenant.getEndDate());
        }

        log.info("续期提醒完成: 15天提醒 {} 个, 7天提醒 {} 个",
                fifteenDayTenants.size(), sevenDayTenants.size());
    }

    // ============ 旧版兼容方法 ============

    /**
     * 旧版新增租户（保留兼容性）
     */
    public void save(SysTenant tenant) {
        if (StrUtil.isBlank(tenant.getTenantCode())) {
            tenant.setTenantCode(generateTenantCode());
        }
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTenant::getTenantCode, tenant.getTenantCode());
        if (tenantMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("租户编码已存在");
        }
        if (StrUtil.isBlank(tenant.getSecretKey())) {
            tenant.setSecretKey(generateSecretKey());
        }
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        if (StrUtil.isBlank(tenant.getUserType())) {
            tenant.setUserType(TenantUserTypeEnum.STANDARD.getCode());
        }
        if (tenant.getStartDate() == null) {
            tenant.setStartDate(LocalDate.now());
        }
        if (tenant.getEndDate() == null) {
            TenantUserTypeEnum type = TenantUserTypeEnum.fromCode(tenant.getUserType());
            if (type.getDefaultDays() > 0) {
                tenant.setEndDate(tenant.getStartDate().plusDays(type.getDefaultDays()));
            }
        }
        if (tenant.getMaxUsers() == null) {
            TenantUserTypeEnum type = TenantUserTypeEnum.fromCode(tenant.getUserType());
            tenant.setMaxUsers(type.getDefaultMaxUsers());
        }
        tenantMapper.insert(tenant);
    }

    /**
     * 旧版续期（保留兼容性）
     */
    public void renew(Long tenantId, Integer durationDays) {
        renewTenant(tenantId, durationDays);
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
        tenantMenuMapper.delete(
                new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId));
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysTenantMenu tenantMenu = new SysTenantMenu();
                tenantMenu.setTenantId(tenantId);
                tenantMenu.setMenuId(menuId);
                tenantMenuMapper.insert(tenantMenu);
            }
        }
    }

    // ============ 私有方法 ============

    /**
     * 清除指定租户所有用户的 Token
     * 按用户维度精确删除，避免影响其他租户
     */
    private void clearTenantTokens(Long tenantId) {
        // 查询该租户所有用户
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getTenantId, tenantId)
        );
        // 按用户ID逐个删除Token（key格式: token:user:{userId}）
        long deletedCount = 0;
        for (SysUser user : users) {
            String userTokenKey = "token:user:" + user.getId();
            Boolean deleted = redisUtils.delete(userTokenKey);
            if (Boolean.TRUE.equals(deleted)) {
                deletedCount++;
            }
            // 同时清除 refresh token
            String refreshTokenKey = "token:refresh:" + user.getId();
            redisUtils.delete(refreshTokenKey);
        }
        log.info("清除租户Token: tenantId={}, 用户数={}, 删除Token数={}", tenantId, users.size(), deletedCount);
    }

    /**
     * 生成租户编码（格式：T + 时间戳）
     */
    private String generateTenantCode() {
        return "T" + System.currentTimeMillis();
    }

    /**
     * 生成密钥（UUID去横线）
     */
    private String generateSecretKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
