package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.finance.domain.BizFinanceLock;
import com.zwinsight.finance.domain.dto.FinanceLockDTO;
import com.zwinsight.finance.mapper.BizFinanceLockMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
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
 * FinanceLockService 单元测试（测试成熟度 2.1.3 变异补强）
 * <p>
 * 针对 PIT 存活变异逐项补断言：角色校验三分支、封账类型/期间校验、
 * insert/update 字段写入（ArgumentCaptor）、分页参数钳制边界、
 * Redis 缓存读写、DTO 非空转换。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("财务封账服务单元测试")
class FinanceLockServiceTest {

    @Mock
    private BizFinanceLockMapper financeLockMapper;

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private FinanceLockService financeLockService;

    private static final Long USER_ID = 2001L;
    /** 固定的历史期间（不晚于当前会计期间） */
    private static final String PAST_PERIOD = "2025-01";

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 使 LambdaQueryWrapper 在无 Spring 容器时可解析列名（与 FinanceLockPropertyTest 同法）
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizFinanceLock.class);
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private void mockAdminRole() {
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("ADMIN"));
    }

    // ==================== 角色校验（checkFinanceAdminRole 三分支） ====================

    @Test
    @DisplayName("封账：未登录（userId=null）抛 403")
    void createLock_noUser_throws403() {
        SecurityContextHolder.clear();
        assertThatThrownBy(() -> financeLockService.createLock(PAST_PERIOD, "MONTHLY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    @DisplayName("封账：无任何角色抛 403")
    void createLock_emptyRoles_throws403() {
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> financeLockService.createLock(PAST_PERIOD, "MONTHLY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限不足");
    }

    @Test
    @DisplayName("封账：角色非 FINANCE_ADMIN/ADMIN 抛 403")
    void createLock_wrongRole_throws403() {
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("ACCOUNTANT"));
        assertThatThrownBy(() -> financeLockService.createLock(PAST_PERIOD, "MONTHLY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限不足");
    }

    @Test
    @DisplayName("解封：无权限同样抛 403（unlock 入口的角色校验不可移除）")
    void unlock_wrongRole_throws403() {
        when(sysUserMapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("ACCOUNTANT"));
        assertThatThrownBy(() -> financeLockService.unlock(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限不足");
    }

    // ==================== createLock 校验分支 ====================

    @Test
    @DisplayName("封账：非法封账类型抛 400")
    void createLock_invalidLockType_throws400() {
        mockAdminRole();
        assertThatThrownBy(() -> financeLockService.createLock(PAST_PERIOD, "YEARLY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("封账类型不合法");
    }

    @Test
    @DisplayName("封账：未来期间抛 400")
    void createLock_futurePeriod_throws400() {
        mockAdminRole();
        String future = YearMonth.now().plusMonths(1).toString();
        assertThatThrownBy(() -> financeLockService.createLock(future, "MONTHLY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可对未来期间封账");
    }

    @Test
    @DisplayName("封账：期间格式非法抛 400")
    void createLock_badPeriodFormat_throws400() {
        mockAdminRole();
        assertThatThrownBy(() -> financeLockService.createLock("2025/01", "MONTHLY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("期间格式不合法");
    }

    @Test
    @DisplayName("封账：期间已封账抛 400 且不写入")
    void createLock_alreadyLocked_throws400() {
        mockAdminRole();
        when(financeLockMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> financeLockService.createLock(PAST_PERIOD, "MONTHLY"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已封账");
        verify(financeLockMapper, never()).insert(any());
    }

    // ==================== createLock 成功路径（字段写入断言） ====================

    @Test
    @DisplayName("月度封账成功：写入 LOCKED 记录且字段完整，刷新 Redis")
    void createLock_monthly_success_writesAllFields() {
        mockAdminRole();
        when(financeLockMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(financeLockMapper.insert(any(BizFinanceLock.class))).thenReturn(1);

        List<FinanceLockDTO> result = financeLockService.createLock(PAST_PERIOD, "MONTHLY");

        ArgumentCaptor<BizFinanceLock> captor = ArgumentCaptor.forClass(BizFinanceLock.class);
        verify(financeLockMapper).insert(captor.capture());
        BizFinanceLock inserted = captor.getValue();
        assertThat(inserted.getPeriod()).isEqualTo(PAST_PERIOD);
        assertThat(inserted.getLockType()).isEqualTo("MONTHLY");
        assertThat(inserted.getStatus()).isEqualTo("LOCKED");
        assertThat(inserted.getLockBy()).isEqualTo(USER_ID);
        assertThat(inserted.getLockTime()).isNotNull();

        // Redis 缓存刷新（key=finance:lock:<period>, value=LOCKED）
        verify(redisUtils).set(eq("finance:lock:" + PAST_PERIOD), eq("LOCKED"), anyLong());

        // 返回 DTO 非空且字段一致
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isNotNull();
        assertThat(result.get(0).getPeriod()).isEqualTo(PAST_PERIOD);
        assertThat(result.get(0).getStatus()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("季度封账成功：展开为季度内 3 个自然月各写一条")
    void createLock_quarterly_expandsThreePeriods() {
        mockAdminRole();
        when(financeLockMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(financeLockMapper.insert(any(BizFinanceLock.class))).thenReturn(1);

        // 2025-05 属 Q2 → 应封 2025-04/05/06
        List<FinanceLockDTO> result = financeLockService.createLock("2025-05", "QUARTERLY");

        ArgumentCaptor<BizFinanceLock> captor = ArgumentCaptor.forClass(BizFinanceLock.class);
        verify(financeLockMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        List<String> periods = captor.getAllValues().stream().map(BizFinanceLock::getPeriod).toList();
        assertThat(periods).containsExactly("2025-04", "2025-05", "2025-06");
        assertThat(result).hasSize(3);
    }

    // ==================== unlock ====================

    @Test
    @DisplayName("解封：记录不存在抛 400")
    void unlock_notFound_throws400() {
        mockAdminRole();
        when(financeLockMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> financeLockService.unlock(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("封账记录不存在");
    }

    @Test
    @DisplayName("解封：记录非 LOCKED 状态抛 400")
    void unlock_notLocked_throws400() {
        mockAdminRole();
        BizFinanceLock record = new BizFinanceLock();
        record.setStatus("UNLOCKED");
        when(financeLockMapper.selectById(1L)).thenReturn(record);
        assertThatThrownBy(() -> financeLockService.unlock(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非封账状态");
    }

    @Test
    @DisplayName("解封成功：状态置 UNLOCKED 且写入解封人/时间，刷新 Redis")
    void unlock_success_writesUnlockFields() {
        mockAdminRole();
        BizFinanceLock record = new BizFinanceLock();
        record.setPeriod(PAST_PERIOD);
        record.setStatus("LOCKED");
        when(financeLockMapper.selectById(1L)).thenReturn(record);
        when(financeLockMapper.updateById(any(BizFinanceLock.class))).thenReturn(1);

        FinanceLockDTO dto = financeLockService.unlock(1L);

        ArgumentCaptor<BizFinanceLock> captor = ArgumentCaptor.forClass(BizFinanceLock.class);
        verify(financeLockMapper).updateById(captor.capture());
        BizFinanceLock updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("UNLOCKED");
        assertThat(updated.getUnlockBy()).isEqualTo(USER_ID);
        assertThat(updated.getUnlockTime()).isNotNull();

        verify(redisUtils).set(eq("finance:lock:" + PAST_PERIOD), eq("UNLOCKED"), anyLong());

        assertThat(dto).isNotNull();
        assertThat(dto.getStatus()).isEqualTo("UNLOCKED");
    }

    // ==================== getPage 参数钳制与字段回写 ====================

    @SuppressWarnings("unchecked")
    private Page<BizFinanceLock> mockPage(int expectedPageNum, int expectedPageSize) {
        Page<BizFinanceLock> page = new Page<>(expectedPageNum, expectedPageSize);
        BizFinanceLock lock = new BizFinanceLock();
        lock.setPeriod(PAST_PERIOD);
        lock.setStatus("LOCKED");
        page.setRecords(List.of(lock));
        page.setTotal(1L);
        page.setPages(1L);
        when(financeLockMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        return page;
    }

    @Test
    @DisplayName("分页：pageSize 超上限钳制为 100，结果字段完整回写")
    void getPage_pageSizeOver100_clamped() {
        mockPage(1, 100);
        PageResult<FinanceLockDTO> result = financeLockService.getPage(1, 150);

        ArgumentCaptor<Page<BizFinanceLock>> captor = ArgumentCaptor.forClass(Page.class);
        verify(financeLockMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getSize()).isEqualTo(100);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getPage()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(100L);
        assertThat(result.getPages()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("分页：pageSize 小于 1 钳制为默认 20")
    void getPage_pageSizeBelow1_defaults20() {
        mockPage(1, 20);
        financeLockService.getPage(1, 0);
        ArgumentCaptor<Page<BizFinanceLock>> captor = ArgumentCaptor.forClass(Page.class);
        verify(financeLockMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("分页：pageNum 小于 1 钳制为 1")
    void getPage_pageNumBelow1_clampedTo1() {
        mockPage(1, 20);
        financeLockService.getPage(0, 20);
        ArgumentCaptor<Page<BizFinanceLock>> captor = ArgumentCaptor.forClass(Page.class);
        verify(financeLockMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
    }

    // ==================== getStatus 缓存与回源 ====================

    @Test
    @DisplayName("状态查询：Redis 命中直接返回缓存值，不查 DB")
    void getStatus_redisHit_returnsCached() {
        when(redisUtils.get("finance:lock:" + PAST_PERIOD)).thenReturn("LOCKED");

        String status = financeLockService.getStatus(PAST_PERIOD);

        assertThat(status).isEqualTo("LOCKED");
        verify(financeLockMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("状态查询：Redis 未命中回源 DB 并回写缓存")
    void getStatus_redisMiss_dbHit_writesBackCache() {
        when(redisUtils.get(anyString())).thenReturn(null);
        BizFinanceLock record = new BizFinanceLock();
        record.setStatus("LOCKED");
        when(financeLockMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(record);

        String status = financeLockService.getStatus(PAST_PERIOD);

        assertThat(status).isEqualTo("LOCKED");
        verify(redisUtils).set(eq("finance:lock:" + PAST_PERIOD), eq("LOCKED"), anyLong());
    }

    @Test
    @DisplayName("状态查询：DB 无记录返回 null")
    void getStatus_noRecord_returnsNull() {
        when(redisUtils.get(anyString())).thenReturn(null);
        when(financeLockMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(financeLockService.getStatus("2020-01")).isNull();
    }
}
