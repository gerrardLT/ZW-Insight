package com.zwinsight.system.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.service.MinioService;
import com.zwinsight.system.domain.SysBackupRecord;
import com.zwinsight.system.mapper.SysBackupRecordMapper;
import com.zwinsight.system.mapper.SysBackupRestoreLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据库备份服务单元测试
 *
 * <p>mysqldump/mysql 为外部进程依赖，单测只覆盖进程启动前的守卫逻辑：
 * 并发保护、记录不存在、进程缺失时的失败落库与异常封装；真实备份链路由生产验证。</p>
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock private SysBackupRecordMapper backupRecordMapper;
    @Mock private SysBackupRestoreLogMapper restoreLogMapper;
    @Mock private MinioService minioService;

    @InjectMocks
    private BackupService backupService;

    @BeforeEach
    void injectConfig() {
        // mysqldump/mysql 指向不存在路径：进程启动即失败，覆盖失败落库分支
        ReflectionTestUtils.setField(backupService, "mysqldumpPath", "/nonexistent/mysqldump");
        ReflectionTestUtils.setField(backupService, "mysqlPath", "/nonexistent/mysql");
        ReflectionTestUtils.setField(backupService, "timeoutSeconds", 60);
        ReflectionTestUtils.setField(backupService, "datasourceUrl", "jdbc:mysql://127.0.0.1:3306/zw_insight");
        ReflectionTestUtils.setField(backupService, "datasourceUsername", "root");
        ReflectionTestUtils.setField(backupService, "datasourcePassword", "secret");
    }

    @Test
    @DisplayName("执行备份：已有备份任务进行中抛 409")
    void testExecuteBackup_concurrentGuard() {
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(backupService, "running");
        running.set(true);
        try {
            assertThatThrownBy(() -> backupService.executeBackup(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已有备份任务进行中");
            verify(backupRecordMapper, never()).insert(any());
        } finally {
            running.set(false);
        }
    }

    @Test
    @DisplayName("执行备份：mysqldump 不可用时失败落库 FAILED 记录并抛 500")
    void testExecuteBackup_processUnavailable_recordsFailure() {
        assertThatThrownBy(() -> backupService.executeBackup(1L, "MANUAL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份失败");

        ArgumentCaptor<SysBackupRecord> captor = ArgumentCaptor.forClass(SysBackupRecord.class);
        verify(backupRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getBackupType()).isEqualTo("MANUAL");

        // 失败后并发锁必须释放，允许下次重试
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(backupService, "running");
        assertThat(running.get()).isFalse();
    }

    @Test
    @DisplayName("定时备份：失败仅记录日志不抛出（不中断调度）")
    void testScheduledBackup_swallowsFailure() {
        assertThatCode(() -> backupService.scheduledBackup()).doesNotThrowAnyException();
        verify(backupRecordMapper).insert(any(SysBackupRecord.class));
    }

    @Test
    @DisplayName("恢复：备份记录不存在抛 404")
    void testRestore_recordNotFound() {
        when(backupRecordMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> backupService.restore(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份记录不存在");
        verify(restoreLogMapper, never()).insert(any());
    }
}
