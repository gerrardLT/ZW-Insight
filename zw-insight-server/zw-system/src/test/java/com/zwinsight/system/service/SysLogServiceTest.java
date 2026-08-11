package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysLoginLog;
import com.zwinsight.system.domain.SysOperLog;
import com.zwinsight.system.mapper.SysLoginLogMapper;
import com.zwinsight.system.mapper.SysOperLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志管理服务单元测试（操作日志 + 登录日志）
 */
@ExtendWith(MockitoExtension.class)
class SysLogServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysOperLog.class);
        TableInfoHelper.initTableInfo(assistant, SysLoginLog.class);
    }

    @Mock private SysOperLogMapper operLogMapper;
    @Mock private SysLoginLogMapper loginLogMapper;

    @InjectMocks
    private SysLogService sysLogService;

    @Test
    @DisplayName("操作日志分页：返回 PageResult 结构")
    void testPageOperLogs_returnsPageResult() {
        Page<SysOperLog> page = new Page<>(1, 10);
        page.setRecords(List.of(new SysOperLog()));
        page.setTotal(1);
        when(operLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<SysOperLog> result = sysLogService.pageOperLogs(1, 10, "合同", "CREATE");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("登录日志分页：返回 PageResult 结构")
    void testPageLoginLogs_returnsPageResult() {
        Page<SysLoginLog> page = new Page<>(1, 10);
        page.setRecords(List.of(new SysLoginLog()));
        page.setTotal(1);
        when(loginLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<SysLoginLog> result = sysLogService.pageLoginLogs(1, 10, "admin");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("保存操作/登录日志：均落库")
    void testSave_logsInserted() {
        SysOperLog operLog = new SysOperLog();
        SysLoginLog loginLog = new SysLoginLog();

        sysLogService.saveOperLog(operLog);
        sysLogService.saveLoginLog(loginLog);

        verify(operLogMapper).insert(operLog);
        verify(loginLogMapper).insert(loginLog);
    }

    @Test
    @DisplayName("批量删除操作/登录日志：按ID集合删除")
    void testDelete_batchIds() {
        sysLogService.deleteOperLogs(List.of(1L, 2L));
        sysLogService.deleteLoginLogs(List.of(3L));

        verify(operLogMapper).deleteBatchIds(List.of(1L, 2L));
        verify(loginLogMapper).deleteBatchIds(List.of(3L));
    }
}
