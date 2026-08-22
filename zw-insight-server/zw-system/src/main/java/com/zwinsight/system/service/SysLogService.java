package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysLoginLog;
import com.zwinsight.system.domain.SysOperLog;
import com.zwinsight.system.mapper.SysLoginLogMapper;
import com.zwinsight.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 日志管理服务
 */
@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;

    /**
     * 操作日志分页查询
     */
    public PageResult<SysOperLog> pageOperLogs(int page, int size, String module, String operType) {
        Page<SysOperLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(module), SysOperLog::getModule, module)
                .eq(StrUtil.isNotBlank(operType), SysOperLog::getOperType, operType)
                // 跨租户水平越权修复（2026-08-14）：sys_* 免拦截器过滤，
                // 显式按当前租户条件化过滤（无上下文内部调用零回归）
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysOperLog::getTenantId, SecurityContextHolder.getTenantId())
                .orderByDesc(SysOperLog::getOperTime);
        Page<SysOperLog> result = operLogMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 登录日志分页查询
     */
    public PageResult<SysLoginLog> pageLoginLogs(int page, int size, String loginName) {
        Page<SysLoginLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(loginName), SysLoginLog::getLoginName, loginName)
                // 跨租户水平越权修复（2026-08-14）：同上，按当前租户条件化过滤
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysLoginLog::getTenantId, SecurityContextHolder.getTenantId())
                .orderByDesc(SysLoginLog::getLoginTime);
        Page<SysLoginLog> result = loginLogMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存操作日志（异步）
     * <p>异步线程无请求线程的租户/用户 ThreadLocal 上下文，落库前先从实体字段
     * （调用方在请求线程预填，见 OperLogAspect）恢复上下文，否则 MetaObjectHandler
     * 写防护会拒绝 INSERT；写毕必须清理，防止线程池复用导致上下文串线程。</p>
     */
    @Async
    public void saveOperLog(SysOperLog operLog) {
        SecurityContextHolder.setTenantId(operLog.getTenantId());
        SecurityContextHolder.setUserId(operLog.getCreatedBy());
        try {
            operLogMapper.insert(operLog);
        } finally {
            SecurityContextHolder.clear();
        }
    }

    /**
     * 保存登录日志
     */
    public void saveLoginLog(SysLoginLog loginLog) {
        loginLogMapper.insert(loginLog);
    }

    /**
     * 批量删除操作日志
     */
    public void deleteOperLogs(List<Long> ids) {
        operLogMapper.deleteBatchIds(ids);
    }

    /**
     * 批量删除登录日志
     */
    public void deleteLoginLogs(List<Long> ids) {
        loginLogMapper.deleteBatchIds(ids);
    }
}
