package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
                .orderByDesc(SysLoginLog::getLoginTime);
        Page<SysLoginLog> result = loginLogMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存操作日志（异步）
     */
    @Async
    public void saveOperLog(SysOperLog operLog) {
        operLogMapper.insert(operLog);
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
