package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.finance.domain.BizFinanceLock;
import com.zwinsight.finance.domain.dto.FinanceLockDTO;
import com.zwinsight.finance.mapper.BizFinanceLockMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 财务封账服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceLockService {

    private final BizFinanceLockMapper financeLockMapper;
    private final RedisUtils redisUtils;
    private final SysUserMapper sysUserMapper;

    /** Redis 缓存 key 前缀 */
    private static final String REDIS_KEY_PREFIX = "finance:lock:";
    /** Redis 缓存 TTL（秒）：24小时 */
    private static final long REDIS_TTL_SECONDS = 24 * 60 * 60;
    /** 封账状态 */
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_UNLOCKED = "UNLOCKED";
    /** 封账类型 */
    private static final String LOCK_TYPE_MONTHLY = "MONTHLY";
    private static final String LOCK_TYPE_QUARTERLY = "QUARTERLY";
    /** 财务管理员角色编码 */
    private static final String ROLE_FINANCE_ADMIN = "FINANCE_ADMIN";
    /** 期间格式 */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 创建封账记录
     *
     * @param period   封账期间，格式 YYYY-MM
     * @param lockType 封账类型：MONTHLY-月度 / QUARTERLY-季度
     * @return 创建的封账记录列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<FinanceLockDTO> createLock(String period, String lockType) {
        // 角色校验
        checkFinanceAdminRole();

        // 校验封账类型
        if (!LOCK_TYPE_MONTHLY.equals(lockType) && !LOCK_TYPE_QUARTERLY.equals(lockType)) {
            throw new BusinessException(400, "封账类型不合法，需为 MONTHLY 或 QUARTERLY");
        }

        // 解析并校验期间
        YearMonth targetPeriod = parsePeriod(period);
        YearMonth currentPeriod = YearMonth.now();
        if (targetPeriod.isAfter(currentPeriod)) {
            throw new BusinessException(400, "不可对未来期间封账");
        }

        // 确定需要封账的期间列表
        List<String> periodsToLock = resolvePeriodsToLock(period, lockType);

        // 校验所有期间不晚于当前会计期间
        for (String p : periodsToLock) {
            YearMonth ym = parsePeriod(p);
            if (ym.isAfter(currentPeriod)) {
                throw new BusinessException(400, "不可对未来期间封账");
            }
        }

        Long userId = SecurityContextHolder.getUserId();
        LocalDateTime now = LocalDateTime.now();
        List<FinanceLockDTO> results = new ArrayList<>();

        for (String lockPeriod : periodsToLock) {
            // 检查是否已存在 LOCKED 状态记录
            LambdaQueryWrapper<BizFinanceLock> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(BizFinanceLock::getPeriod, lockPeriod)
                    .eq(BizFinanceLock::getStatus, STATUS_LOCKED);
            Long existCount = financeLockMapper.selectCount(existWrapper);
            if (existCount > 0) {
                throw new BusinessException(400, "期间" + lockPeriod + "已封账，不可重复操作");
            }

            // 创建封账记录
            BizFinanceLock lockRecord = new BizFinanceLock();
            lockRecord.setPeriod(lockPeriod);
            lockRecord.setLockType(lockType);
            lockRecord.setStatus(STATUS_LOCKED);
            lockRecord.setLockBy(userId);
            lockRecord.setLockTime(now);
            financeLockMapper.insert(lockRecord);

            // 刷新 Redis 缓存
            refreshRedisCache(lockPeriod, STATUS_LOCKED);

            // 转换 DTO
            results.add(toDTO(lockRecord));
        }

        log.info("封账成功，期间={}，类型={}，操作人={}", periodsToLock, lockType, userId);
        return results;
    }

    /**
     * 解封操作
     *
     * @param id 封账记录ID
     * @return 解封后的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceLockDTO unlock(Long id) {
        // 角色校验
        checkFinanceAdminRole();

        BizFinanceLock lockRecord = financeLockMapper.selectById(id);
        if (lockRecord == null) {
            throw new BusinessException(400, "封账记录不存在");
        }
        if (!STATUS_LOCKED.equals(lockRecord.getStatus())) {
            throw new BusinessException(400, "该记录当前非封账状态，无需解封");
        }

        Long userId = SecurityContextHolder.getUserId();
        LocalDateTime now = LocalDateTime.now();

        // 更新状态为 UNLOCKED
        lockRecord.setStatus(STATUS_UNLOCKED);
        lockRecord.setUnlockBy(userId);
        lockRecord.setUnlockTime(now);
        financeLockMapper.updateById(lockRecord);

        // 刷新 Redis 缓存
        refreshRedisCache(lockRecord.getPeriod(), STATUS_UNLOCKED);

        log.info("解封成功，id={}，期间={}，操作人={}", id, lockRecord.getPeriod(), userId);
        return toDTO(lockRecord);
    }

    /**
     * 分页查询封账记录
     *
     * @param pageNum  页码，默认1
     * @param pageSize 每页大小，默认20，最大100
     * @return 分页结果
     */
    public PageResult<FinanceLockDTO> getPage(int pageNum, int pageSize) {
        // 限制每页最大100条
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageNum < 1) {
            pageNum = 1;
        }

        Page<BizFinanceLock> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BizFinanceLock> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizFinanceLock::getPeriod);

        Page<BizFinanceLock> result = financeLockMapper.selectPage(pageParam, wrapper);

        // 转换为 DTO 分页
        List<FinanceLockDTO> dtoList = result.getRecords().stream()
                .map(this::toDTO)
                .toList();

        PageResult<FinanceLockDTO> pageResult = new PageResult<>();
        pageResult.setRecords(dtoList);
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /**
     * 查询指定年月封账状态
     *
     * @param period 期间，格式 YYYY-MM
     * @return 封账状态：LOCKED / UNLOCKED / null（未封账过）
     */
    public String getStatus(String period) {
        // 先从 Redis 查询
        String redisKey = REDIS_KEY_PREFIX + period;
        try {
            Object cached = redisUtils.get(redisKey);
            if (cached != null) {
                return cached.toString();
            }
        } catch (Exception e) {
            log.warn("Redis 查询封账状态失败，降级到 DB 查询，period={}", period, e);
        }

        // Redis 未命中，查询数据库
        LambdaQueryWrapper<BizFinanceLock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizFinanceLock::getPeriod, period)
                .orderByDesc(BizFinanceLock::getCreatedAt)
                .last("LIMIT 1");
        BizFinanceLock record = financeLockMapper.selectOne(wrapper);

        if (record == null) {
            return null;
        }

        // 回写 Redis 缓存
        try {
            refreshRedisCache(period, record.getStatus());
        } catch (Exception e) {
            log.warn("回写 Redis 缓存失败，period={}", period, e);
        }

        return record.getStatus();
    }

    // ======================== 私有方法 ========================

    /**
     * 校验当前用户是否为财务管理员
     */
    private void checkFinanceAdminRole() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(403, "未登录，无法执行此操作");
        }

        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException(403, "权限不足，需财务管理员角色");
        }

        boolean isFinanceAdmin = roleCodes.stream()
                .anyMatch(code -> ROLE_FINANCE_ADMIN.equals(code) || "ADMIN".equals(code));
        if (!isFinanceAdmin) {
            throw new BusinessException(403, "权限不足，需财务管理员角色");
        }
    }

    /**
     * 解析期间字符串为 YearMonth
     */
    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period, PERIOD_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "期间格式不合法，需为 YYYY-MM 格式");
        }
    }

    /**
     * 根据封账类型确定需要封账的期间列表
     * - MONTHLY: 只返回当前期间
     * - QUARTERLY: 展开为该季度包含的3个自然月
     */
    private List<String> resolvePeriodsToLock(String period, String lockType) {
        if (LOCK_TYPE_MONTHLY.equals(lockType)) {
            return List.of(period);
        }

        // 季度封账：确定该月份所在季度的3个月
        YearMonth ym = parsePeriod(period);
        int month = ym.getMonthValue();
        int year = ym.getYear();

        // 确定季度起始月：Q1(1-3), Q2(4-6), Q3(7-9), Q4(10-12)
        int quarterStartMonth = ((month - 1) / 3) * 3 + 1;

        List<String> periods = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            YearMonth qMonth = YearMonth.of(year, quarterStartMonth + i);
            periods.add(qMonth.format(PERIOD_FORMATTER));
        }
        return periods;
    }

    /**
     * 刷新 Redis 缓存
     */
    private void refreshRedisCache(String period, String status) {
        String redisKey = REDIS_KEY_PREFIX + period;
        try {
            redisUtils.set(redisKey, status, REDIS_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("刷新 Redis 缓存失败，key={}，status={}", redisKey, status, e);
        }
    }

    /**
     * 实体转 DTO
     */
    private FinanceLockDTO toDTO(BizFinanceLock entity) {
        FinanceLockDTO dto = new FinanceLockDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
